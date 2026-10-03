package com.whisk.hackexlogger

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var targetAdapter: TargetAdapter
    private var currentSearchQuery = ""
    private var isPartialVisible = false
    private var searchJob: Job? = null

    private val dataUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            android.util.Log.d("DOSSIER", "MainActivity received broadcast: ${intent.action}")
            if (intent.action == "com.whisk.hackexlogger.DATA_UPDATED") {
                refreshData()
            }
        }
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            try {
                val jsonString = DatabaseManager.exportDataBundle()
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(this, "Export successful", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val jsonString = contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader(Charsets.UTF_8).readText()
                }
                if (jsonString.isNullOrEmpty()) {
                    Toast.makeText(this, "File is empty", Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }
                val result = DatabaseManager.importDataBundle(jsonString)
                refreshData()
                Toast.makeText(
                    this,
                    "Imported ${result.targetCount} targets and ${result.ingestCount} archived ingests.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Import Failed")
                    .setMessage(e.message ?: "Failed to import data.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        DatabaseManager.init(this)
        setupUI()
        
        registerReceiver(dataUpdateReceiver, IntentFilter("com.whisk.hackexlogger.DATA_UPDATED"), RECEIVER_NOT_EXPORTED)

        checkPermissionsAndStart()
        refreshData()
    }

    private fun setupUI() {
        targetAdapter = TargetAdapter(this::onEditTarget, this::onDeleteTarget, this::onTogglePartial)
        findViewById<RecyclerView>(R.id.targetsRecycler).adapter = targetAdapter

        findViewById<Button>(R.id.exportDataBtn).setOnClickListener {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            exportLauncher.launch("target_console_$dateStr.json")
        }

        findViewById<Button>(R.id.importDataBtn).setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }

        findViewById<Button>(R.id.wipeDbBtn).setOnClickListener {
            DatabaseManager.wipeDatabase()
            refreshData()
            Toast.makeText(this, "Database wiped", Toast.LENGTH_SHORT).show()
        }

        val searchInput = findViewById<EditText>(R.id.searchInput)
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentSearchQuery = s?.toString()?.trim()?.lowercase() ?: ""
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(150)
                    refreshData()
                }
            }
        })

        findViewById<Button>(R.id.clearSearchBtn).setOnClickListener {
            searchInput.setText("")
        }
    }

    private fun onTogglePartial() {
        isPartialVisible = !isPartialVisible
        refreshData()
    }

    private fun refreshData() {
        lifecycleScope.launch(Dispatchers.Default) {
            val allRecords = DatabaseManager.getAllRecords()
            var filtered = allRecords

            if (currentSearchQuery.isNotEmpty()) {
                val query = currentSearchQuery.lowercase()
                
                val swMatch = Regex("""^(.*?)\s*(?:lv\.?|lvl\.?)?\s*(\d+)$""", RegexOption.IGNORE_CASE).find(query)
                var swFilteredRecords: List<TargetRecord>? = null
                
                if (swMatch != null) {
                    val swName = swMatch.groupValues[1].trim()
                    val targetLevel = swMatch.groupValues[2].toIntOrNull()
                    
                    if (swName.isNotEmpty() && targetLevel != null) {
                        fun getMatching(levels: List<Int>): List<TargetRecord> {
                            return allRecords.filter { record ->
                                record.downloads.any { (k, v) ->
                                    k.lowercase().contains(swName) && levels.contains(v.level)
                                }
                            }
                        }
                        
                        val exactMatches = getMatching(listOf(targetLevel))
                        if (exactMatches.isNotEmpty()) {
                            swFilteredRecords = exactMatches
                        } else {
                            val plusMinusOne = getMatching(listOf(targetLevel - 1, targetLevel + 1).filter { it >= 1 })
                            if (plusMinusOne.isNotEmpty()) {
                                swFilteredRecords = plusMinusOne
                            } else {
                                val plusMinusTwo = getMatching(listOf(targetLevel - 2, targetLevel + 2).filter { it >= 1 })
                                swFilteredRecords = plusMinusTwo
                            }
                        }
                    }
                }

                val genericFiltered = allRecords.filter { record ->
                    record.ip.lowercase().contains(query) ||
                    record.username?.lowercase()?.contains(query) == true ||
                    record.wallets.any { it.lowercase().contains(query) } ||
                    record.downloads.keys.any { it.lowercase().contains(query) }
                }
                
                filtered = if (swFilteredRecords != null) {
                    (swFilteredRecords + genericFiltered).distinctBy { it.ip }
                } else {
                    genericFiltered
                }
            }

            val fullTargets = filtered.filter { !it.isMasked }.sortedBy { it.ip }
            val partialTargets = filtered.filter { it.isMasked }.sortedBy { it.ip }

            val items = mutableListOf<ListItem>()
            items.add(ListItem.Header("FULL TARGETS (${fullTargets.size})"))
            fullTargets.forEach { items.add(ListItem.Target(it)) }

            val partialHeaderTitle = if (isPartialVisible) {
                "▲ PARTIAL / MASKED TARGETS (${partialTargets.size})"
            } else {
                "▼ PARTIAL / MASKED TARGETS (${partialTargets.size})"
            }
            items.add(ListItem.Header(partialHeaderTitle, isPartialHeader = true, isExpanded = isPartialVisible))
            if (isPartialVisible) {
                partialTargets.forEach { items.add(ListItem.Target(it)) }
            }

            withContext(Dispatchers.Main) {
                targetAdapter.submitList(items)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(dataUpdateReceiver)
    }

    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this) && isAccessibilityServiceEnabled()) {
            startService(Intent(this, OverlayService::class.java))
        }
    }

    private fun checkPermissionsAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant 'Display over other apps' permission", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Please enable the HackExLogger Accessibility Service", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            return
        }

        startService(Intent(this, OverlayService::class.java))
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(this, ScraperAccessibilityService::class.java)
        val enabledServicesSetting = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val stringizer = TextUtils.SimpleStringSplitter(':')
        stringizer.setString(enabledServicesSetting)
        while (stringizer.hasNext()) {
            val componentNameString = stringizer.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName) {
                return true
            }
        }
        return false
    }

    private fun onEditTarget(target: TargetRecord) {
        val input = EditText(this)
        input.setText(target.ip)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Edit IP")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newIp = input.text.toString().trim()
                if (newIp.isNotEmpty() && newIp != target.ip) {
                    DatabaseManager.editTargetIp(target.ip, newIp)
                    refreshData()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun onDeleteTarget(target: TargetRecord) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Target")
            .setMessage("Are you sure you want to delete ${target.ip}?")
            .setPositiveButton("Delete") { _, _ ->
                DatabaseManager.deleteTarget(target.ip)
                refreshData()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
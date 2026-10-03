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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var fullAdapter: TargetAdapter
    private lateinit var partialAdapter: TargetAdapter
    private var currentSearchQuery = ""
    private var isPartialVisible = false

    private val dataUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            android.util.Log.d("DOSSIER", "MainActivity received broadcast: ${intent.action}")
            if (intent.action == "com.whisk.hackexlogger.DATA_UPDATED") {
                refreshData()
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
        fullAdapter = TargetAdapter(this::onEditTarget, this::onDeleteTarget)
        partialAdapter = TargetAdapter(this::onEditTarget, this::onDeleteTarget)

        findViewById<RecyclerView>(R.id.fullTargetsRecycler).adapter = fullAdapter
        findViewById<RecyclerView>(R.id.partialTargetsRecycler).adapter = partialAdapter

        findViewById<Button>(R.id.wipeDbBtn).setOnClickListener {
            DatabaseManager.wipeDatabase()
            refreshData()
            Toast.makeText(this, "Database wiped", Toast.LENGTH_SHORT).show()
        }

        val partialToggle = findViewById<TextView>(R.id.partialTargetsToggle)
        val partialRecycler = findViewById<RecyclerView>(R.id.partialTargetsRecycler)
        partialToggle.setOnClickListener {
            isPartialVisible = !isPartialVisible
            if (isPartialVisible) {
                partialToggle.text = "▲ PARTIAL / MASKED TARGETS"
                partialRecycler.visibility = View.VISIBLE
            } else {
                partialToggle.text = "▼ PARTIAL / MASKED TARGETS"
                partialRecycler.visibility = View.GONE
            }
        }

        val searchInput = findViewById<EditText>(R.id.searchInput)
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentSearchQuery = s?.toString()?.trim()?.lowercase() ?: ""
                refreshData()
            }
        })

        findViewById<Button>(R.id.clearSearchBtn).setOnClickListener {
            searchInput.setText("")
        }
    }

    private fun refreshData() {
        android.util.Log.d("DOSSIER", "Refreshing UI with latest Database records...")
        var allRecords = DatabaseManager.getAllRecords()
        
        if (currentSearchQuery.isNotEmpty()) {
            val query = currentSearchQuery.lowercase()
            
            // 1. Try to parse as "software + level" (e.g. "spam 10", "spam lv10", "spam lv. 10", "spam10")
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
                        // Expand by +/- 1, clamping minimum to 1
                        val plusMinusOne = getMatching(listOf(targetLevel - 1, targetLevel + 1).filter { it >= 1 })
                        if (plusMinusOne.isNotEmpty()) {
                            swFilteredRecords = plusMinusOne
                        } else {
                            // Expand by +/- 2, clamping minimum to 1
                            val plusMinusTwo = getMatching(listOf(targetLevel - 2, targetLevel + 2).filter { it >= 1 })
                            swFilteredRecords = plusMinusTwo // Empty if still nothing found
                        }
                    }
                }
            }

            // 2. Generic string matching (IP, User, Wallet fragments, Software names)
            val genericFiltered = allRecords.filter { record ->
                record.ip.lowercase().contains(query) ||
                record.username?.lowercase()?.contains(query) == true ||
                record.wallets.any { it.lowercase().contains(query) } ||
                record.downloads.keys.any { it.lowercase().contains(query) }
            }
            
            // Combine both filter strategies so typing "192.168" (parsed as sw=192., lvl=168) still works
            allRecords = if (swFilteredRecords != null) {
                (swFilteredRecords + genericFiltered).distinctBy { it.ip }
            } else {
                genericFiltered
            }
        }

        val fullTargets = allRecords.filter { !it.isMasked }.sortedBy { it.ip }
        val partialTargets = allRecords.filter { it.isMasked }.sortedBy { it.ip }

        fullAdapter.submitList(fullTargets)
        partialAdapter.submitList(partialTargets)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(dataUpdateReceiver)
    }

    override fun onResume() {
        super.onResume()
        // Re-check permissions when coming back from settings
        if (Settings.canDrawOverlays(this) && isAccessibilityServiceEnabled()) {
            startService(Intent(this, OverlayService::class.java))
        }
    }

    private fun checkPermissionsAndStart() {
        // 1. Check Overlay Permission
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant 'Display over other apps' permission", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        // 2. Check Accessibility Permission
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Please enable the HackExLogger Accessibility Service", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            return
        }

        // Both granted, start overlay
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
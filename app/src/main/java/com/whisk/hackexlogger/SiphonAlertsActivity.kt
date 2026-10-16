package com.whisk.hackexlogger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class SiphonAlertsActivity : AppCompatActivity() {

    private lateinit var adapter: SiphonAlertAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_siphon_alerts)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.backBtn).setOnClickListener {
            finish()
        }

        adapter = SiphonAlertAdapter(
            onReinstalled = { ip ->
                DatabaseManager.removeSiphonAlert(ip)
                refreshList()
                Toast.makeText(this, "Alert cleared", Toast.LENGTH_SHORT).show()
            },
            onNotFound = { ip ->
                DatabaseManager.removeSiphonAlert(ip)
                DatabaseManager.deleteTarget(ip)
                refreshList()
                Toast.makeText(this, "Target removed from DB", Toast.LENGTH_SHORT).show()
            }
        )

        findViewById<RecyclerView>(R.id.alertsRecycler).adapter = adapter
        refreshList()
    }

    private fun refreshList() {
        val alerts = DatabaseManager.getSiphonAlerts().sorted()
        adapter.submitList(alerts)
    }
}

class SiphonAlertAdapter(
    private val onReinstalled: (String) -> Unit,
    private val onNotFound: (String) -> Unit
) : ListAdapter<String, SiphonAlertAdapter.AlertViewHolder>(StringDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_siphon_alert, parent, false)
        return AlertViewHolder(view, onReinstalled, onNotFound)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AlertViewHolder(
        itemView: View,
        private val onReinstalled: (String) -> Unit,
        private val onNotFound: (String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val ipText: TextView = itemView.findViewById(R.id.ipText)
        private val reinstalledBtn: Button = itemView.findViewById(R.id.reinstalledBtn)
        private val notFoundBtn: Button = itemView.findViewById(R.id.notFoundBtn)

        fun bind(ip: String) {
            ipText.text = ip

            ipText.setOnClickListener {
                val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("IP Address", ip)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(itemView.context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }

            reinstalledBtn.setOnClickListener { onReinstalled(ip) }
            notFoundBtn.setOnClickListener { onNotFound(ip) }
        }
    }

    class StringDiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
    }
}

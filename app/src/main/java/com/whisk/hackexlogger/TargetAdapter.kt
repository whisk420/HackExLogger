package com.whisk.hackexlogger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * Sealed class representing list items displayed in the targets RecyclerView (Headers vs Target Cards).
 */
sealed class ListItem {
    data class Header(
        val title: String,
        val isPartialHeader: Boolean = false,
        val isExpanded: Boolean = false
    ) : ListItem()

    data class Target(
        val target: TargetRecord
    ) : ListItem()
}

/**
 * DiffUtil callback for efficient list item updates and animations in [TargetAdapter].
 */
class ListItemDiffCallback : DiffUtil.ItemCallback<ListItem>() {
    override fun areItemsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
        return when {
            oldItem is ListItem.Header && newItem is ListItem.Header -> oldItem.isPartialHeader == newItem.isPartialHeader
            oldItem is ListItem.Target && newItem is ListItem.Target -> oldItem.target.ip == newItem.target.ip
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
        return oldItem == newItem
    }
}

/**
 * RecyclerView adapter for displaying headers and HackEx target records.
 */
class TargetAdapter(
    private val onEdit: (TargetRecord) -> Unit,
    private val onDelete: (TargetRecord) -> Unit,
    private val onTogglePartial: () -> Unit
) : ListAdapter<ListItem, RecyclerView.ViewHolder>(ListItemDiffCallback()) {

    var currentValidatingIp: String? = null

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_TARGET = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ListItem.Header -> TYPE_HEADER
            is ListItem.Target -> TYPE_TARGET
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            val view = inflater.inflate(R.layout.item_header, parent, false)
            HeaderViewHolder(view, onTogglePartial)
        } else {
            val view = inflater.inflate(R.layout.item_target, parent, false)
            TargetViewHolder(view, onEdit, onDelete)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is ListItem.Target -> (holder as TargetViewHolder).bind(item.target, currentValidatingIp)
        }
    }

    class HeaderViewHolder(
        itemView: View,
        private val onTogglePartial: () -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.headerTitle)

        fun bind(header: ListItem.Header) {
            titleText.text = header.title
            if (header.isPartialHeader) {
                titleText.setTextColor(Color.parseColor("#fbbf24"))
                itemView.setOnClickListener { onTogglePartial() }
            } else {
                titleText.setTextColor(Color.parseColor("#60a5fa"))
                itemView.setOnClickListener(null)
            }
        }
    }

    class TargetViewHolder(
        itemView: View,
        private val onEdit: (TargetRecord) -> Unit,
        private val onDelete: (TargetRecord) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val ipText: TextView = itemView.findViewById(R.id.ipText)
        private val editBtn: Button = itemView.findViewById(R.id.editBtn)
        private val shareBtn: Button = itemView.findViewById(R.id.shareBtn)
        private val deleteBtn: Button = itemView.findViewById(R.id.deleteBtn)
        private val metaText: TextView = itemView.findViewById(R.id.metaText)
        private val walletsText: TextView = itemView.findViewById(R.id.walletsText)
        private val softwareText: TextView = itemView.findViewById(R.id.softwareText)
        private val validatedText: TextView = itemView.findViewById(R.id.validatedText)

        private val softwareIcons = mapOf(
            "Antivirus" to "💉",
            "Spam" to "📧",
            "Rootkit" to "🪱",
            "Firewall" to "🛡️",
            "Bypasser" to "🚪",
            "Password Cracker" to "🔨",
            "Password Encryptor" to "🔑",
            "Proxy" to "🎭",
            "Trace" to "📡",
            "Siphon" to "🩸",
            "Keygen" to "⚙️"
        )

        fun bind(target: TargetRecord, currentValidatingIp: String?) {
            if (target.ip == currentValidatingIp) {
                ipText.text = "👉 ${target.ip}"
                ipText.setTextColor(Color.parseColor("#f59e0b"))
            } else {
                ipText.text = target.ip
                ipText.setTextColor(Color.parseColor("#38bdf8"))
            }

            ipText.setOnClickListener {
                copyToClipboard("IP Address", target.ip)
            }
            editBtn.setOnClickListener { onEdit(target) }
            deleteBtn.setOnClickListener { onDelete(target) }
            shareBtn.setOnClickListener { shareTarget(target) }

            val metaParts = mutableListOf<String>()
            target.username?.let { metaParts.add("USER: $it") }
            target.clan?.let { metaParts.add("CLAN: [$it]") }
            target.level?.let { metaParts.add("LVL: $it") }
            target.hardware?.let { metaParts.add("HW: $it") }
            target.firewall?.let { metaParts.add("🛡️ FW: Lv$it") }
            target.encryptor?.let { metaParts.add("🔑 ENC: Lv$it") }

            metaText.text = metaParts.joinToString(" • ")
            metaText.visibility = if (metaParts.isEmpty()) View.GONE else View.VISIBLE

            if (target.wallets.isNotEmpty()) {
                walletsText.text = target.wallets.joinToString("\n") { "WALLET: $it" }
                walletsText.visibility = View.VISIBLE
            } else {
                walletsText.visibility = View.GONE
            }

            if (target.downloads.isNotEmpty()) {
                val swStr = target.downloads.entries.joinToString("\n") { (name, data) ->
                    val icon = softwareIcons[name] ?: "📦"
                    "$icon $name Lv${data.level}"
                }
                softwareText.text = swStr
                softwareText.visibility = View.VISIBLE
            } else {
                softwareText.visibility = View.GONE
            }

            if (target.lastValidated != null) {
                validatedText.text = "✓ Validated: ${target.lastValidated}"
                validatedText.setTextColor(Color.parseColor("#4ade80"))
            } else {
                validatedText.text = "Never validated"
                validatedText.setTextColor(Color.parseColor("#64748b"))
            }
        }

        private fun copyToClipboard(label: String, text: String) {
            val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(itemView.context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        private fun shareTarget(target: TargetRecord) {
            val sb = java.lang.StringBuilder()
            target.username?.let { sb.append("User: $it\n") }
            sb.append("TARGET: ${target.ip}\n")
            
            if (target.wallets.isNotEmpty()) {
                target.wallets.forEach { wallet ->
                    sb.append("Wallet: $wallet\n")
                }
            }
            
            target.firewall?.let { sb.append("Firewall: Lv.$it\n") }
            target.encryptor?.let { sb.append("Encryptor: Lv.$it\n") }
            
            if (target.downloads.isNotEmpty()) {
                sb.append("\nSoftware:\n")
                target.downloads.forEach { (name, data) ->
                    sb.append("• $name Lv.${data.level}\n")
                }
            }
            
            copyToClipboard("Target Report", sb.toString())
        }
    }
}

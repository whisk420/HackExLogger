package com.whisk.hackexlogger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
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
        private val softwareContainer: LinearLayout = itemView.findViewById(R.id.softwareContainer)
        private val validatedText: TextView = itemView.findViewById(R.id.validatedText)
        private val hardwareText: TextView = itemView.findViewById(R.id.hardwareText)

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

            val metaParts1 = mutableListOf<String>()
            val metaParts2 = mutableListOf<String>()
            
            target.username?.let { metaParts1.add("👤 $it") }
            target.clan?.let { metaParts1.add("👥 [$it]") }
            target.level?.let { metaParts1.add("LVL: $it") }
            
            target.firewall?.let { metaParts2.add("🛡️ FW: Lv$it") }
            target.encryptor?.let { metaParts2.add("🔑 ENC: Lv$it") }

            val fullMeta = mutableListOf<String>()
            if (metaParts1.isNotEmpty()) fullMeta.add(metaParts1.joinToString(" • "))
            if (metaParts2.isNotEmpty()) fullMeta.add(metaParts2.joinToString(" • "))

            metaText.text = fullMeta.joinToString("\n")
            metaText.visibility = if (fullMeta.isEmpty()) View.GONE else View.VISIBLE

            if (target.hardware != null) {
                hardwareText.text = "💻 ${target.hardware}"
                hardwareText.visibility = View.VISIBLE
            } else {
                hardwareText.visibility = View.GONE
            }

            if (target.wallets.isNotEmpty()) {
                walletsText.text = target.wallets.joinToString("\n") { "WALLET: $it" }
                walletsText.visibility = View.VISIBLE
            } else {
                walletsText.visibility = View.GONE
            }

            if (target.downloads.isNotEmpty()) {
                softwareContainer.removeAllViews()
                target.downloads.forEach { (name, data) ->
                    val icon = softwareIcons[name] ?: "📦"
                    
                    val textView = TextView(itemView.context)
                    textView.text = "$icon $name Lv${data.level}"
                    textView.setTextColor(Color.parseColor("#38bdf8"))
                    textView.textSize = 14f
                    textView.setPadding(0, 4, 0, 4)
                    
                    val layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    layoutParams.setMargins(0, 2, 0, 2)
                    textView.layoutParams = layoutParams
                    
                    val backgroundParams = android.graphics.drawable.GradientDrawable()
                    backgroundParams.setColor(Color.parseColor("#1e293b"))
                    backgroundParams.cornerRadius = 8f
                    textView.background = backgroundParams
                    textView.setPadding(16, 8, 16, 8)
                    
                    softwareContainer.addView(textView)
                }
                softwareContainer.visibility = View.VISIBLE
            } else {
                softwareContainer.visibility = View.GONE
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

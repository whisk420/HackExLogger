package com.whisk.hackexlogger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class TargetAdapter(
    private val onEdit: (TargetRecord) -> Unit,
    private val onDelete: (TargetRecord) -> Unit
) : RecyclerView.Adapter<TargetAdapter.TargetViewHolder>() {

    private var targets = listOf<TargetRecord>()

    fun submitList(newTargets: List<TargetRecord>) {
        targets = newTargets
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TargetViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_target, parent, false)
        return TargetViewHolder(view, onEdit, onDelete)
    }

    override fun onBindViewHolder(holder: TargetViewHolder, position: Int) {
        val target = targets[position]
        holder.bind(target)
    }

    override fun getItemCount(): Int = targets.size

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

        fun bind(target: TargetRecord) {
            ipText.text = target.ip

            // Set up clicks
            ipText.setOnClickListener {
                copyToClipboard("IP Address", target.ip)
            }
            editBtn.setOnClickListener { onEdit(target) }
            deleteBtn.setOnClickListener { onDelete(target) }
            shareBtn.setOnClickListener { shareTarget(target) }

            // Metadata Row
            val metaParts = mutableListOf<String>()
            target.username?.let { metaParts.add("USER: $it") }
            target.clan?.let { metaParts.add("CLAN: [$it]") }
            target.level?.let { metaParts.add("LVL: $it") }
            target.hardware?.let { metaParts.add("HW: $it") }
            target.firewall?.let { metaParts.add("🛡️ FW: Lv$it") }
            target.encryptor?.let { metaParts.add("🔑 ENC: Lv$it") }

            metaText.text = metaParts.joinToString(" • ")
            metaText.visibility = if (metaParts.isEmpty()) View.GONE else View.VISIBLE

            // Wallets
            if (target.wallets.isNotEmpty()) {
                walletsText.text = target.wallets.joinToString("\n") { "WALLET: $it" }
                walletsText.visibility = View.VISIBLE
            } else {
                walletsText.visibility = View.GONE
            }

            // Software
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
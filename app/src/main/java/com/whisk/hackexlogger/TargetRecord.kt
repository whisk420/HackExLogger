package com.whisk.hackexlogger

data class SoftwareEntry(
    val level: Int,
    val status: String,
    val lastSeen: String?
)

data class TargetRecord(
    var ip: String,
    var username: String? = null,
    var clan: String? = null,
    var level: Int? = null,
    var hardware: String? = null,
    var firewall: Int? = null,
    var encryptor: Int? = null,
    var wallets: MutableList<String> = mutableListOf(),
    var downloads: MutableMap<String, SoftwareEntry> = mutableMapOf(),
    var lastValidated: String? = null
) {
    val isMasked: Boolean
        get() = ip.contains("xxx")
        
    val isTemporary: Boolean
        get() = ip.startsWith("unknown:")
}
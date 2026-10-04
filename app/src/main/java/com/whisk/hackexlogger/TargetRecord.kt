package com.whisk.hackexlogger

/**
 * Represents software installed or stored on a target device in the HackEx game.
 *
 * @property level Software level in-game.
 * @property status Current status (e.g. "installed", "downloading").
 * @property lastSeen Timestamp string of when this software state was observed.
 */
data class SoftwareEntry(
    val level: Int,
    val status: String,
    val lastSeen: String?
)

/**
 * Data model representing an in-game target entity collected within HackExLogger.
 *
 * Stores target information extracted from game screens and logs, such as virtual IP addresses,
 * player usernames, clan affiliations, hardware setups, defense levels, and associated crypto wallets.
 */
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
    /**
     * True if the target IP address contains masked components (e.g., "192.168.xxx.12").
     */
    val isMasked: Boolean
        get() = ip.contains("xxx")

    /**
     * True if this record was created without a known IP address and assigned a temporary placeholder key.
     */
    val isTemporary: Boolean
        get() = ip.startsWith("unknown:")
}
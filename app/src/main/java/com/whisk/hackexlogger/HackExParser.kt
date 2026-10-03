package com.whisk.hackexlogger

object HackExParser {
    
    private val REGEX_IP = Regex("""\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\b""")
    private val REGEX_MASKED_IP = Regex("""\b(?:\d{1,3}|xxx)\.(?:\d{1,3}|xxx)\.(?:\d{1,3}|xxx)\.(?:\d{1,3}|xxx)\b""")

    data class Software(
        val action: String,
        val level: Int,
        val name: String
    )

    data class ParsedUpdate(
        val ip: String,
        val time: String?,
        val wallet: String? = null,
        val software: Software? = null,
        val isOwnedSoftware: Boolean = false,
        val raw: String
    )

    data class HomeScreen(
        val ip: String,
        val username: String?,
        val clan: String?,
        val level: Int?,
        val hardware: String?,
        val firewall: Int?,
        val encryptor: Int?
    )

    data class SoftwareScreenItem(
        val level: Int,
        val status: String
    )

    data class SoftwareScreen(
        val username: String?,
        val softwareItems: Map<String, SoftwareScreenItem>
    )

    data class WalletScreen(
        val username: String,
        val wallet: String
    )
    
    fun shortenWallet(wallet: String): String {
        if (wallet.length > 10 && !wallet.contains("...")) {
            return wallet.take(6) + "..." + wallet.takeLast(4)
        }
        return wallet
    }
    
    fun extractIp(text: String): String? {
        if (text.contains("[UNKNOWN]")) return null
        
        val ipMatch = REGEX_IP.find(text)
        if (ipMatch != null) return ipMatch.value
        
        val maskedIpMatch = REGEX_MASKED_IP.find(text)
        if (maskedIpMatch != null) return maskedIpMatch.value
        
        return null
    }

    fun parseMyLogs(rawLines: List<String>): List<ParsedUpdate> {
        var currentAccessedIp: String? = null
        val updates = mutableListOf<ParsedUpdate>()

        for (i in rawLines.indices.reversed()) {
            val line = rawLines[i].trim()
            if (line.isEmpty()) continue

            val timeMatch = Regex("""^\[([^\]]+)\]""").find(line)
            val time = timeMatch?.groupValues?.get(1)
            val isAccessed = line.contains("Accessed device at")
            val explicitIp = extractIp(line)

            if (isAccessed && explicitIp != null) {
                currentAccessedIp = explicitIp
                updates.add(ParsedUpdate(ip = explicitIp, time = time, raw = line))
                continue
            }

            var stolenWallet: String? = null
            if (line.contains("Stole") && line.contains("from ")) {
                val wMatch = Regex("""from\s+([a-zA-Z0-9.]{6,})""").find(line)
                if (wMatch != null) stolenWallet = shortenWallet(wMatch.groupValues[1])
            }

            val softMatch = Regex("""(Downloaded|Uploaded|Downloading|Uploading)\s+Lv(\d+)\s+(.+?)(?:\s+(?:from|to)\b|\.\.\.|$)""", RegexOption.IGNORE_CASE).find(line)
            var software: Software? = null
            var isOwned = false

            if (softMatch != null) {
                val action = softMatch.groupValues[1].lowercase()
                software = Software(
                    action = action,
                    level = softMatch.groupValues[2].toIntOrNull() ?: 0,
                    name = softMatch.groupValues[3].trim()
                )
                isOwned = action.startsWith("download")
            }

            val targetIp = explicitIp ?: currentAccessedIp
            if (targetIp != null) {
                updates.add(
                    ParsedUpdate(
                        ip = targetIp,
                        time = time,
                        wallet = if (stolenWallet != null && targetIp == currentAccessedIp) stolenWallet else null,
                        software = software,
                        isOwnedSoftware = isOwned,
                        raw = line
                    )
                )
            }
        }
        return updates
    }

    fun parseVictimLogs(rawLines: List<String>): List<ParsedUpdate> {
        val updates = mutableListOf<ParsedUpdate>()
        var lastAccessIp: String? = null
        var lastAccessEventMinute: Int? = null

        val validLines = rawLines.filter { line ->
            if (line.trim().length <= 1) false
            else Regex("""^\[[0-9]{1,2}-[0-9]{1,2}\s+[0-9]{1,2}:[0-9]{2}\]""").containsMatchIn(line.trim())
        }

        for (i in validLines.indices.reversed()) {
            val line = validLines[i].trim()
            if (line.isEmpty()) continue

            val timeMatch = Regex("""^\[([^\]]+)\]""").find(line)
            val time = timeMatch?.groupValues?.get(1)
            
            var eventMinute: Int? = null
            if (time != null) {
                val timeParts = Regex("""^(\d{1,2})-(\d{1,2})\s+(\d{1,2}):(\d{2})$""").find(time)
                if (timeParts != null) {
                    val mm = timeParts.groupValues[1].toInt()
                    val dd = timeParts.groupValues[2].toInt()
                    val hh = timeParts.groupValues[3].toInt()
                    val min = timeParts.groupValues[4].toInt()
                    eventMinute = (((mm * 31 + dd) * 24 + hh) * 60 + min)
                }
            }

            if (line.contains("[UNKNOWN]")) {
                lastAccessIp = null
                lastAccessEventMinute = null
                continue
            }

            val accessedMatch = Regex("""Device accessed from\s+((?:(?:\d{1,3}|xxx)\.){3}(?:\d{1,3}|xxx))""", RegexOption.IGNORE_CASE).find(line)
            if (accessedMatch != null) {
                val ip = extractIp(accessedMatch.groupValues[1])
                if (ip != null && ip != "[UNKNOWN]") {
                    lastAccessIp = ip
                    lastAccessEventMinute = eventMinute
                    updates.add(ParsedUpdate(ip = ip, time = time, raw = line))
                    continue
                }
            }

            val accessAtMatch = Regex("""Accessed device at\s+((?:(?:\d{1,3}|xxx)\.){3}(?:\d{1,3}|xxx))""", RegexOption.IGNORE_CASE).find(line)
            if (accessAtMatch != null) {
                val ip = extractIp(accessAtMatch.groupValues[1])
                if (ip != null) {
                    lastAccessIp = ip
                    lastAccessEventMinute = eventMinute
                    updates.add(ParsedUpdate(ip = ip, time = time, raw = line))
                    continue
                }
            }

            var action: String? = null
            var level: Int? = null
            var softwareName: String? = null
            var targetIp: String? = null
            var isOwned = false

            val fromMatch = Regex("""(Downloaded|Uploaded|Downloading|Uploading)\s+Lv(\d+)\s+(.+?)\s+from\s+((?:(?:\d{1,3}|xxx)\.){3}(?:\d{1,3}|xxx))""", RegexOption.IGNORE_CASE).find(line)
            val toMatch = Regex("""(Downloaded|Uploaded|Downloading|Uploading)\s+Lv(\d+)\s+(.+?)\s+to\s+((?:(?:\d{1,3}|xxx)\.){3}(?:\d{1,3}|xxx))""", RegexOption.IGNORE_CASE).find(line)
            var softMatch: MatchResult? = null

            if (fromMatch != null) {
                action = fromMatch.groupValues[1].lowercase()
                level = fromMatch.groupValues[2].toIntOrNull()
                softwareName = fromMatch.groupValues[3].trim()
                targetIp = extractIp(fromMatch.groupValues[4])
                isOwned = action.startsWith("download")
            } else if (toMatch != null) {
                action = toMatch.groupValues[1].lowercase()
                level = toMatch.groupValues[2].toIntOrNull()
                softwareName = toMatch.groupValues[3].trim()
                targetIp = extractIp(toMatch.groupValues[4])
                isOwned = action.startsWith("download")
            } else {
                softMatch = Regex("""(Downloaded|Uploaded|Downloading|Uploading)\s+Lv(\d+)\s+(.+?)(?:\s+(?:from|to)\b|\.|\.\.|\s*$)""", RegexOption.IGNORE_CASE).find(line)
                if (softMatch != null) {
                    action = softMatch.groupValues[1].lowercase()
                    level = softMatch.groupValues[2].toIntOrNull()
                    softwareName = softMatch.groupValues[3].trim()
                    isOwned = action.startsWith("download")
                    
                    val ipFromLine = extractIp(line)
                    if (ipFromLine != null && !line.contains("[UNKNOWN]")) {
                        targetIp = ipFromLine
                    }
                }
            }

            if (softMatch == null && fromMatch == null && toMatch == null) {
                val byMatch = Regex("""by\s+((?:(?:\d{1,3}|xxx)\.){3}(?:\d{1,3}|xxx))""", RegexOption.IGNORE_CASE).find(line)
                if (byMatch != null) {
                    targetIp = extractIp(byMatch.groupValues[1])
                }
            }

            val walletMatch = Regex("""(?:Stole\s+\d[\d,]*\s+Crypto\s+from|\d[\d,]*\s+Crypto\s+transferred\s+to)\s+([a-zA-Z0-9.]{6,})""", RegexOption.IGNORE_CASE).find(line)
            if (walletMatch != null) {
                val withinCorrelationWindow = lastAccessEventMinute != null && eventMinute != null &&
                        eventMinute >= lastAccessEventMinute && eventMinute - lastAccessEventMinute <= 2
                
                if (withinCorrelationWindow && lastAccessIp != null) {
                    updates.add(ParsedUpdate(ip = lastAccessIp, time = time, wallet = shortenWallet(walletMatch.groupValues[1]), raw = line))
                }
                continue
            }

            val transferMatch = Regex("""(\d+)\s+Crypto\s+transferred\s+to\s+([a-zA-Z0-9.]{6,})""").find(line)
            if (transferMatch != null) {
                updates.add(ParsedUpdate(ip = targetIp ?: "", time = time, wallet = shortenWallet(transferMatch.groupValues[2]), raw = line))
                continue
            }

            val crackMatch = Regex("""(Cracking|Cracked)\s+password\s+(?:on\s+)?((?:(?:\d{1,3}|xxx)\.){3}(?:\d{1,3}|xxx))""", RegexOption.IGNORE_CASE).find(line)
            if (crackMatch != null) {
                targetIp = extractIp(crackMatch.groupValues[2])
                updates.add(ParsedUpdate(ip = targetIp ?: "", time = time, raw = line))
                continue
            }

            val firewallMatch = Regex("""(Bypassed|Failed to bypass)\s+firewall\s+(?:on\s+)?((?:(?:\d{1,3}|xxx)\.){3}(?:\d{1,3}|xxx))""", RegexOption.IGNORE_CASE).find(line)
            if (firewallMatch != null) {
                targetIp = extractIp(firewallMatch.groupValues[2])
                updates.add(ParsedUpdate(ip = targetIp ?: "", time = time, raw = line))
                continue
            }

            if (softMatch != null || fromMatch != null || toMatch != null) {
                if (targetIp != null && !line.contains("[UNKNOWN]")) {
                    updates.add(
                        ParsedUpdate(
                            ip = targetIp,
                            time = time,
                            software = if (action != null && level != null && softwareName != null) Software(action, level, softwareName) else null,
                            isOwnedSoftware = isOwned,
                            raw = line
                        )
                    )
                }
            }
        }
        return updates
    }

    fun parseHomeScreen(text: String): HomeScreen? {
        val ipMatch = Regex("""IP\s+([0-9]{1,3}(?:\.[0-9]{1,3}|\.xxx){3})""", RegexOption.IGNORE_CASE).find(text)
        if (ipMatch == null) return null
        val ip = ipMatch.groupValues[1].trim()

        val userLineMatch = Regex("""^>\s*(.+?)$""", RegexOption.MULTILINE).find(text)
        var username = userLineMatch?.groupValues?.get(1)?.trim()

        var clan: String? = null
        if (username != null) {
            val clanMatch = Regex("""\[([a-zA-Z0-9_-]{2,6})\]""").find(username)
            if (clanMatch != null) {
                clan = clanMatch.groupValues[1]
                username = username.replace(clanMatch.value, "").trim()
            }
            username = username.removeSuffix("_").trim()
        }
        
        if (clan == null) {
            val genericClanMatch = Regex("""\[([a-zA-Z0-9_-]{2,6})\]""").find(text)
            clan = genericClanMatch?.groupValues?.get(1)?.trim()
        }

        val lvlMatch = Regex("""LVL\s+(\d+)""", RegexOption.IGNORE_CASE).find(text)
        val level = lvlMatch?.groupValues?.get(1)?.toIntOrNull()

        val devMatch = Regex("""DEVICE\s+([^\n\r]+)""", RegexOption.IGNORE_CASE).find(text)
        val device = devMatch?.groupValues?.get(1)?.trim()

        val netMatch = Regex("""NETWORK\s+([^\n\r]+)""", RegexOption.IGNORE_CASE).find(text)
        val network = netMatch?.groupValues?.get(1)?.trim()

        val fwMatch = Regex("""FIREWALL\s+Lv\.?\s*(\d+)""", RegexOption.IGNORE_CASE).find(text)
        val firewall = fwMatch?.groupValues?.get(1)?.toIntOrNull()

        val encMatch = Regex("""ENCRYPTOR\s+Lv\.?\s*(\d+)""", RegexOption.IGNORE_CASE).find(text)
        val encryptor = encMatch?.groupValues?.get(1)?.toIntOrNull()

        val hardware = if (device != null && network != null) "$device • $network" else device ?: network

        return HomeScreen(ip, username, clan, level, hardware, firewall, encryptor)
    }

    fun parseSoftwareScreen(text: String): SoftwareScreen? {
        val userMatch = Regex("""^([^\r\n]+?)['’]s installed software\s*$""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)).find(text)
        val username = userMatch?.groupValues?.get(1)?.trim()

        val softwareItems = mutableMapOf<String, SoftwareScreenItem>()
        val validNames = listOf(
            "Antivirus", "Spam", "Rootkit", "Firewall", "Bypasser",
            "Password Cracker", "Password Encryptor", "Proxy", "Trace",
            "Siphon"
        )

        validNames.forEach { name ->
            val patternStr = "^" + Regex.escape(name) + "$[\\s\\S]*?LVL\\s*(\\d+)"
            val match = Regex(patternStr, setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)).find(text)
            if (match != null) {
                val level = match.groupValues[1].toIntOrNull() ?: 0
                softwareItems[name] = SoftwareScreenItem(level, "installed")
            }
        }

        if (username == null && softwareItems.isEmpty()) return null
        return SoftwareScreen(username, softwareItems)
    }

    fun parseWalletScreen(text: String): WalletScreen? {
        val userMatch = Regex("""^([^\r\n]+?)['’]s wallet\s*$""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)).find(text)
        val username = userMatch?.groupValues?.get(1)?.trim()

        val walletMatch = Regex("""WALLET ADDRESS\s+([a-zA-Z0-9]{20,})""", RegexOption.IGNORE_CASE).find(text)
        val rawWallet = walletMatch?.groupValues?.get(1)?.trim()

        if (username != null && rawWallet != null) {
            return WalletScreen(username, shortenWallet(rawWallet))
        }
        return null
    }
}

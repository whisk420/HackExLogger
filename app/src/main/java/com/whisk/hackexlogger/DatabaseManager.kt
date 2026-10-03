package com.whisk.hackexlogger

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DatabaseManager {
    private const val PREFS_NAME = "HackExDB"
    private const val KEY_TARGETS = "targets"
    
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    private val cachedRecords = mutableListOf<TargetRecord>()
    @Volatile private var isInitialized = false

    fun init(context: Context) {
        if (!isInitialized) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadCacheFromDisk()
            isInitialized = true
        }
    }

    private fun loadCacheFromDisk() {
        val json = prefs.getString(KEY_TARGETS, "[]")
        val type = object : TypeToken<List<TargetRecord>>() {}.type
        val loaded: List<TargetRecord>? = gson.fromJson(json, type)
        synchronized(cachedRecords) {
            cachedRecords.clear()
            if (loaded != null) {
                cachedRecords.addAll(loaded)
            }
        }
    }

    fun getAllRecords(): List<TargetRecord> {
        if (!isInitialized) return emptyList()
        synchronized(cachedRecords) {
            return cachedRecords.map { it.copy(wallets = it.wallets.toMutableList(), downloads = it.downloads.toMutableMap()) }
        }
    }

    private fun saveAllRecords(records: List<TargetRecord>) {
        synchronized(cachedRecords) {
            cachedRecords.clear()
            cachedRecords.addAll(records)
        }
        val json = gson.toJson(records)
        prefs.edit().putString(KEY_TARGETS, json).apply()
    }

    fun wipeDatabase() {
        synchronized(cachedRecords) {
            cachedRecords.clear()
        }
        prefs.edit().remove(KEY_TARGETS).apply()
    }

    fun exportDataBundle(): String {
        val records = getAllRecords()
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val bundle = ConsoleBundle(
            format = "target-intelligence-console",
            version = 1,
            exportedAt = isoFormat.format(Date()),
            targets = records,
            ingests = emptyList()
        )
        val exportGson = GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create()
        return exportGson.toJson(bundle)
    }

    fun importDataBundle(jsonString: String): ImportResult {
        val bundle = try {
            gson.fromJson(jsonString, ConsoleBundle::class.java)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid JSON format: ${e.message}", e)
        }

        if (bundle == null || bundle.format != "target-intelligence-console" || bundle.version != 1) {
            throw IllegalArgumentException("This is not a Target Intelligence Console export.")
        }

        val importedTargets = bundle.targets ?: emptyList()
        val importedIngests = bundle.ingests ?: emptyList()

        val currentRecords = getAllRecords().toMutableList()
        val recordMap = currentRecords.associateBy { it.ip }.toMutableMap()

        for (imported in importedTargets) {
            val ip = imported.ip
            if (ip.isBlank()) continue

            val existing = recordMap[ip]
            if (existing != null) {
                val merged = mergeRecords(existing, imported)
                recordMap[ip] = merged
            } else {
                val previousMatch = recordMap.values.find { candidate ->
                    (imported.username != null && candidate.username != null && candidate.username.equals(imported.username, ignoreCase = true)) ||
                    (imported.wallets.isNotEmpty() && candidate.wallets.any { imported.wallets.contains(it) })
                }

                if (previousMatch != null) {
                    val merged = mergeRecords(previousMatch, imported)
                    merged.ip = if (!imported.isMasked && previousMatch.isMasked) imported.ip else merged.ip
                    recordMap.remove(previousMatch.ip)
                    recordMap[merged.ip] = merged
                } else {
                    recordMap[ip] = imported
                }
            }
        }

        for (ingest in importedIngests) {
            val raw = ingest.raw
            if (raw.isNullOrEmpty()) continue
            when (ingest.type) {
                "logs" -> {
                    val lines = raw.lines()
                    val updates = HackExParser.parseMyLogs(lines).ifEmpty { HackExParser.parseVictimLogs(lines) }
                    applyUpdatesToMap(recordMap, updates)
                }
                "home" -> {
                    val homeScreen = HackExParser.parseHomeScreen(raw)
                    if (homeScreen != null) {
                        applyHomeScreenToMap(recordMap, homeScreen)
                    }
                }
                "software" -> {
                    val softwareScreen = HackExParser.parseSoftwareScreen(raw)
                    if (softwareScreen != null) {
                        applySoftwareScreenToMap(recordMap, softwareScreen)
                    }
                }
            }
        }

        saveAllRecords(recordMap.values.toList())
        reconcileDatabase()

        return ImportResult(importedTargets.size, importedIngests.size)
    }

    fun processUpdates(updates: List<HackExParser.ParsedUpdate>) {
        if (updates.isEmpty()) return
        val currentRecords = getAllRecords().toMutableList()
        val recordMap = currentRecords.associateBy { it.ip }.toMutableMap()
        applyUpdatesToMap(recordMap, updates)
        saveAllRecords(recordMap.values.toList())
        reconcileDatabase()
    }

    private fun applyUpdatesToMap(recordMap: MutableMap<String, TargetRecord>, updates: List<HackExParser.ParsedUpdate>) {
        for (update in updates) {
            val ip = update.ip
            if (ip.isEmpty()) continue

            var record = recordMap[ip]
            if (record == null) {
                record = TargetRecord(ip = ip)
                recordMap[ip] = record
            }

            if (update.wallet != null) {
                val previousOwner = recordMap.values.find { it.ip != ip && it.wallets.contains(update.wallet) }
                if (previousOwner != null) {
                    val merged = mergeRecords(record, previousOwner)
                    merged.ip = ip
                    recordMap.remove(previousOwner.ip)
                    record = merged
                    recordMap[ip] = record
                }
                if (!record.wallets.contains(update.wallet)) {
                    record.wallets.add(update.wallet)
                }
            }

            if (update.software != null && update.isOwnedSoftware) {
                record.downloads[update.software.name] = SoftwareEntry(
                    level = update.software.level,
                    status = update.software.action,
                    lastSeen = update.time
                )
            }
        }
    }

    fun processHomeScreen(homeScreen: HackExParser.HomeScreen) {
        val currentRecords = getAllRecords().toMutableList()
        val recordMap = currentRecords.associateBy { it.ip }.toMutableMap()
        applyHomeScreenToMap(recordMap, homeScreen)
        saveAllRecords(recordMap.values.toList())
        reconcileDatabase()
    }

    private fun applyHomeScreenToMap(recordMap: MutableMap<String, TargetRecord>, homeScreen: HackExParser.HomeScreen) {
        var record = recordMap[homeScreen.ip]
        if (record == null) {
            record = TargetRecord(ip = homeScreen.ip)
        }
        
        homeScreen.username?.let { record.username = it }
        homeScreen.clan?.let { record.clan = it }
        homeScreen.level?.let { record.level = it }
        homeScreen.hardware?.let { record.hardware = it }
        homeScreen.firewall?.let { record.firewall = it }
        homeScreen.encryptor?.let { record.encryptor = it }
        
        recordMap[homeScreen.ip] = record
    }

    fun editTargetIp(oldIp: String, newIp: String) {
        val currentRecords = getAllRecords().toMutableList()
        val existing = currentRecords.find { it.ip == oldIp } ?: return
        
        currentRecords.remove(existing)
        
        val collision = currentRecords.find { it.ip == newIp }
        if (collision != null) {
            currentRecords.remove(collision)
            val merged = mergeRecords(collision, existing)
            merged.ip = newIp
            currentRecords.add(merged)
        } else {
            existing.ip = newIp
            currentRecords.add(existing)
        }
        
        saveAllRecords(currentRecords)
        reconcileDatabase()
    }
    
    fun deleteTarget(ip: String) {
        val currentRecords = getAllRecords().toMutableList()
        currentRecords.removeAll { it.ip == ip }
        saveAllRecords(currentRecords)
    }

    fun processSoftwareScreen(softwareScreen: HackExParser.SoftwareScreen) {
        if (softwareScreen.softwareItems.isEmpty()) return
        val currentRecords = getAllRecords().toMutableList()
        val recordMap = currentRecords.associateBy { it.ip }.toMutableMap()
        applySoftwareScreenToMap(recordMap, softwareScreen)
        saveAllRecords(recordMap.values.toList())
        reconcileDatabase()
    }

    private fun applySoftwareScreenToMap(recordMap: MutableMap<String, TargetRecord>, softwareScreen: HackExParser.SoftwareScreen) {
        if (softwareScreen.softwareItems.isEmpty()) return
        val username = softwareScreen.username
        var targetRecord: TargetRecord? = null
        
        if (username != null) {
            targetRecord = recordMap.values.find { it.username.equals(username, ignoreCase = true) }
        }
        
        if (targetRecord == null) {
            val newIp = "unknown:${username ?: System.currentTimeMillis()}"
            targetRecord = TargetRecord(ip = newIp, username = username)
            recordMap[newIp] = targetRecord
        }
        
        softwareScreen.softwareItems.forEach { (name, item) ->
            targetRecord.downloads[name] = SoftwareEntry(item.level, item.status, null)
        }
    }

    fun processWalletScreen(walletScreen: HackExParser.WalletScreen) {
        val currentRecords = getAllRecords().toMutableList()
        val recordMap = currentRecords.associateBy { it.ip }.toMutableMap()
        
        val username = walletScreen.username
        val wallet = walletScreen.wallet
        
        var targetRecord = recordMap.values.find { it.username.equals(username, ignoreCase = true) }
        
        if (targetRecord == null) {
            val newIp = "unknown:${username}"
            targetRecord = TargetRecord(ip = newIp, username = username)
            recordMap[newIp] = targetRecord
        }

        val previousOwner = recordMap.values.find { it.ip != targetRecord.ip && it.wallets.contains(wallet) }
        if (previousOwner != null) {
            val merged = mergeRecords(targetRecord, previousOwner)
            merged.ip = targetRecord.ip
            recordMap.remove(previousOwner.ip)
            targetRecord = merged
            recordMap[targetRecord.ip] = targetRecord
        }
        
        if (!targetRecord.wallets.contains(wallet)) {
            targetRecord.wallets.add(wallet)
        }
        
        saveAllRecords(recordMap.values.toList())
        reconcileDatabase()
    }

    private fun mergeRecords(base: TargetRecord, incoming: TargetRecord): TargetRecord {
        val baseIsMasked = base.isMasked
        val incomingIsMasked = incoming.isMasked
        val canonicalIp = if ((!incomingIsMasked && baseIsMasked) || (base.isTemporary && !incoming.isTemporary)) incoming.ip else base.ip

        return TargetRecord(
            ip = canonicalIp,
            username = incoming.username ?: base.username,
            clan = incoming.clan ?: base.clan,
            level = incoming.level ?: base.level,
            hardware = incoming.hardware ?: base.hardware,
            firewall = incoming.firewall ?: base.firewall,
            encryptor = incoming.encryptor ?: base.encryptor,
            wallets = (base.wallets + incoming.wallets).distinct().toMutableList(),
            downloads = (base.downloads + incoming.downloads).toMutableMap()
        )
    }

    private fun reconcileDatabase() {
        val records = getAllRecords().toMutableList()
        if (records.size < 2) return

        var changed = false
        var i = 0
        while (i < records.size) {
            var j = i + 1
            while (j < records.size) {
                val recA = records[i]
                val recB = records[j]

                val sharesUser = recA.username != null && recB.username != null && 
                               recA.username.equals(recB.username, ignoreCase = true)
                val sharesWallet = recA.wallets.any { recB.wallets.contains(it) }

                if (sharesUser || sharesWallet) {
                    val merged = mergeRecords(recA, recB)
                    records[i] = merged
                    records.removeAt(j)
                    changed = true
                    j--
                }
                j++
            }
            i++
        }

        if (changed) {
            saveAllRecords(records)
        }
    }
}
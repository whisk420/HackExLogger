package com.whisk.hackexlogger

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object DatabaseManager {
    private const val PREFS_NAME = "HackExDB"
    private const val KEY_TARGETS = "targets"
    
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getAllRecords(): List<TargetRecord> {
        val json = prefs.getString(KEY_TARGETS, "[]")
        val type = object : TypeToken<List<TargetRecord>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    private fun saveAllRecords(records: List<TargetRecord>) {
        prefs.edit().putString(KEY_TARGETS, gson.toJson(records)).apply()
    }

    fun wipeDatabase() {
        prefs.edit().remove(KEY_TARGETS).apply()
    }

    fun processUpdates(updates: List<HackExParser.ParsedUpdate>) {
        if (updates.isEmpty()) return
        
        val currentRecords = getAllRecords().toMutableList()
        val recordMap = currentRecords.associateBy { it.ip }.toMutableMap()

        for (update in updates) {
            val ip = update.ip
            if (ip.isEmpty()) continue

            var record = recordMap[ip]
            if (record == null) {
                record = TargetRecord(ip = ip)
                recordMap[ip] = record
            }

            if (update.wallet != null) {
                // If another record owns this wallet, merge them
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
        
        saveAllRecords(recordMap.values.toList())
        reconcileDatabase()
    }

    fun processHomeScreen(homeScreen: HackExParser.HomeScreen) {
        val currentRecords = getAllRecords().toMutableList()
        val recordMap = currentRecords.associateBy { it.ip }.toMutableMap()
        
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
        saveAllRecords(recordMap.values.toList())
        reconcileDatabase()
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
        
        saveAllRecords(recordMap.values.toList())
        reconcileDatabase()
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

        // Merge if another record owns this wallet
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
                    j-- // Re-check this index since we removed an element
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
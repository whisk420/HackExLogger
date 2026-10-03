package com.whisk.hackexlogger

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Build

class ScraperAccessibilityService : AccessibilityService() {

    private val triggerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("DOSSIER", "Broadcast received in AccessibilityService! Action: ${intent.action}")
            if (intent.action == ACTION_TRIGGER_SCRAPE) {
                scrapeAndParse()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("DOSSIER", "Accessibility Service successfully Connected to OS")
        val filter = IntentFilter(ACTION_TRIGGER_SCRAPE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(triggerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(triggerReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(triggerReceiver)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We only scrape on demand via broadcast, ignore passive events
    }

    override fun onInterrupt() {
    }

    private fun scrapeAndParse() {
        Log.d("DOSSIER", "scrapeAndParse() called. Attempting to get root window...")
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.e("DOSSIER", "ERROR: rootInActiveWindow is null. Cannot scrape.")
            return
        }

        val extractedTextList = mutableListOf<String>()
        extractText(rootNode, extractedTextList)

        val combinedText = extractedTextList.joinToString("\n")
        Log.d("DOSSIER", "Scraped ${extractedTextList.size} text nodes.")
        Log.d("DOSSIER_RAW", "--- RAW SCRAPED TEXT START ---\n$combinedText\n--- RAW SCRAPED TEXT END ---")
        
        // Break the UI text block into actual lines for the parsers
        val allLines = combinedText.split(Regex("""\r?\n"""))

        // Try parsing as HomeScreen
        val homeScreen = HackExParser.parseHomeScreen(combinedText)
        if (homeScreen != null) {
            Log.d("DOSSIER", "Parsed HomeScreen: $homeScreen")
            DatabaseManager.processHomeScreen(homeScreen)
        }

        // Try parsing as Software Screen
        val softwareScreen = HackExParser.parseSoftwareScreen(combinedText)
        if (softwareScreen != null) {
            Log.d("DOSSIER", "Parsed SoftwareScreen: $softwareScreen")
            DatabaseManager.processSoftwareScreen(softwareScreen)
        }

        // Try parsing as Wallet Screen
        val walletScreen = HackExParser.parseWalletScreen(combinedText)
        if (walletScreen != null) {
            Log.d("DOSSIER", "Parsed WalletScreen: $walletScreen")
            DatabaseManager.processWalletScreen(walletScreen)
        }

        // Try parsing as Victim/My logs
        val victimLogs = HackExParser.parseVictimLogs(allLines)
        if (victimLogs.isNotEmpty()) {
            Log.d("DOSSIER", "Parsed ${victimLogs.size} Victim Logs")
            DatabaseManager.processUpdates(victimLogs)
        }

        val myLogs = HackExParser.parseMyLogs(allLines)
        if (myLogs.isNotEmpty()) {
            Log.d("DOSSIER", "Parsed ${myLogs.size} My Logs")
            DatabaseManager.processUpdates(myLogs)
        }
        
        // Notify UI to refresh
        Log.d("DOSSIER", "Sending DATA_UPDATED broadcast to UI")
        val updateIntent = Intent("com.whisk.hackexlogger.DATA_UPDATED")
        updateIntent.setPackage(packageName)
        sendBroadcast(updateIntent)
    }

    private fun extractText(node: AccessibilityNodeInfo, list: MutableList<String>) {
        if (node.text != null) {
            list.add(node.text.toString())
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                extractText(child, list)
            }
        }
    }

    companion object {
        const val ACTION_TRIGGER_SCRAPE = "com.whisk.hackexlogger.TRIGGER_SCRAPE"
    }
}
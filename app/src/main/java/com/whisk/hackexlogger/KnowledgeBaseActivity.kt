package com.whisk.hackexlogger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class KnowledgeBaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_knowledge_base)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<ImageButton>(R.id.backBtn).setOnClickListener {
            finish()
        }

        val container = findViewById<LinearLayout>(R.id.topicsContainer)
        val inflater = LayoutInflater.from(this)

        val topics = getTopics()
        
        for (topic in topics) {
            val view = inflater.inflate(R.layout.item_kb_topic, container, false)
            
            val titleText = view.findViewById<TextView>(R.id.topicTitle)
            val mainText = view.findViewById<TextView>(R.id.topicMainText)
            val expandText = view.findViewById<TextView>(R.id.topicExpandText)
            val expandBtn = view.findViewById<Button>(R.id.toggleExpandBtn)
            val expandLayout = view.findViewById<LinearLayout>(R.id.expandableLayout)
            
            titleText.text = topic.title
            mainText.text = topic.mainText
            expandText.text = topic.expandableText
            
            expandBtn.setOnClickListener {
                if (expandLayout.visibility == View.GONE) {
                    expandLayout.visibility = View.VISIBLE
                    expandBtn.text = "Show Less"
                } else {
                    expandLayout.visibility = View.GONE
                    expandBtn.text = "Read More"
                }
            }
            
            container.addView(view)
        }
    }

    data class Topic(val title: String, val mainText: String, val expandableText: String)

    private fun getTopics(): List<Topic> {
        return listOf(
            Topic(
                title = "Spam Payouts & Target Selection",
                mainText = "Your hourly Spam income depends on your Spam level, the target's level, and the 100 credits/hr hard cap.\n\nThe game rewards you for infecting players close to or above your level, and penalizes hitting lower-level targets.\n\n• Same Level Target: Upgrading your Spam virus increases payout by ~2.00 credits/hr per level.\n• Higher Level Target: Every level the target is above you increases the payout by ~2.50 to 3.30 credits/hr.\n• The 21-Day Window: Spam operates at 100% efficiency for the first 21 days, then slowly decays. Re-upload to restore the rate.",
                expandableText = "HOW THE MATH WORKS\n\nWhen infecting a target below your level, a Level Gap Penalty is subtracted:\nGap = Your Level - Target Level.\n\nPenalty Rate:\n• Level 16 Spam loses ~2.60 credits/hr per gap level.\n• Level 20 Spam loses ~3.30 credits/hr per gap level.\n\nHitting the Cap (100 credits/hr):\n• Level 16 Spam hits ~99-100 credits/hr against targets within 3 levels below you.\n• Level 20 Spam hits the cap against targets within 9-10 levels below you.\n\nPRACTICAL EXAMPLE (You are Level 35):\nInfecting a Level 23 target with Level 16 Spam yields 85 credits/hr.\n• Option A (Upgrade Spam): Upgrade 4 times to Level 20 on the same target = 93 credits/hr (+8 gain).\n• Option B (Pick Higher Target): Keep Level 16 Spam, infect a Level 27 target (+4 levels higher) = 96 credits/hr (+11 gain)."
            ),
            Topic(
                title = "Keygen License Math vs. Store Prices",
                mainText = "Always use Keygen! You should almost never buy software upgrades directly in the Store. Instead, download higher-level apps from victims you hack and unlock them with your Keygen for a massive discount.\n\nKeygen fees are calculated as a percentage of the app's Store price.\n\n• 1-Level Jump (85% Discount): Unlocking a downloaded app 1 level above yours costs just 15% of that level's Store price.\n• Multi-Level Jumps (50% to 85% Discount): As the gap increases, the fee climbs linearly up to 50% at a 20+ level jump.\n• Single big jumps beat small steps: Even at the worst 50% ratio, jumping straight to a high-level app is dramatically cheaper than paying for every level in between.\n\nDEFENSIVE APPS ARE CHEAPER\nDefensive software (Firewall, Encryptor, Antivirus) has a base Store price 15% cheaper than offensive software. Since Keygen fees are a percentage of the Store price, unlocking defensive apps is also 15% cheaper!",
                expandableText = "THE ONLY TIME TO USE THE STORE\nBuy a direct upgrade in the Store only for emergency defense:\n• Upgrading your Firewall directly in the Store immediately cancels all active incoming Bypass and Crack processes against you (and grants a 4-hour protection window).\n• Upgrading your Encryptor invalidates all saved cracked passwords that enemies hold on your wallet.\n\nEXACT KEYGEN MATH\nLet Jump = Downloaded App Level - Your Current App Level\n\nKeygen Fee Ratio:\n• At Jump = 1: Ratio = 15% of Target Level's Store Price\n• At Jump >= 20: Ratio = 50% of Target Level's Store Price\n\nBase Store Price Modifiers:\n• Offensive Apps (Bypasser, Cracker): 1.00x Base Cost\n• Defensive Apps (Firewall, Encryptor, Antivirus): 0.85x Base Cost"
            ),
            Topic(
                title = "How XP, Leveling & Leaderboards Work",
                mainText = "Hacking targets and stealing crypto does not give you XP directly. XP is awarded when you unlock software upgrades, complete Daily Missions, or claim Login Streaks.\n\nFULL XP ON KEYGEN UNLOCKS\nWhen you unlock an app using Keygen, you receive the FULL XP for the level you unlocked, based on the true Store value, not the discounted Keygen price.\n\n• Step-by-Step Upgrades: You earn XP for every individual level.\n• Skipping Levels: You immediately get the big XP drop for the target level, but you skip the smaller XP drops from intermediate levels.\n\nTHE OVER-LEVELING TRAP\nLeveling up your Player Level increases your Cold Storage cap and gives +10 credits/level. But leveling too fast without upgrading your core offense can hurt you:\n• Harder Matchmaking: The scanner matches players roughly within 10 levels of your Player Level.\n• Lower Spam Payouts: Spam payouts are penalized based on the gap between your Player Level and your Target's Level. Higher player level means steeper penalties against low-level victims.",
                expandableText = "SMART LEVELING STRATEGY\nAvoid upgrading apps you don't actively use just for XP. Keep your Player Level lean while pushing your core hacking tools (Bypasser, Cracker, Keygen, Spam) as high as possible.\n\nLEADERBOARD SCORE & REPUTATION\nYour ranking is not based on Player Level alone. It combines Player Level with your Reputation using a sub-linear power curve.\n\nScore = PlayerLevel * (Reputation ^ 0.75)\n\n• Because Reputation is raised to the 0.75 power, a Level 30 player with high Reputation will outrank a Level 45 player whose Reputation is trashed.\n• Enemy viruses on your device continuously drain Reputation.\n• Running Antivirus or a System Purge removes viruses and restores 90% of the drained Reputation (10% is lost permanently)."
            )
        )
    }
}
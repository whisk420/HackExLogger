package com.whisk.hackexlogger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingButton: Button

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        floatingButton = Button(this).apply {
            text = "📸"
            setBackgroundColor(Color.parseColor("#80000000")) // Semi-transparent black background
            setTextColor(Color.WHITE)
            textSize = 20f
            isAllCaps = false
            setPadding(0, 0, 0, 0) // Remove padding to keep it compact
            
            // Make the button circular (requires API 21+)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor("#99000000")) // Semi-transparent black
            }
        }

        val size = (50 * resources.displayMetrics.density).toInt() // 50dp diameter

        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        floatingButton.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingButton, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = Math.abs(event.rawX - initialTouchX)
                    val diffY = Math.abs(event.rawY - initialTouchY)
                    if (diffX < 10 && diffY < 10) {
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }

        floatingButton.setOnClickListener {
            android.util.Log.d("DOSSIER", "Overlay button clicked. Sending broadcast...")
            val intent = Intent("com.whisk.hackexlogger.TRIGGER_SCRAPE")
            intent.setPackage(packageName) // Explicitly route to our own app to avoid implicit broadcast blocks
            sendBroadcast(intent)
            android.util.Log.d("DOSSIER", "Broadcast sent to package: $packageName")
        }

        windowManager.addView(floatingButton, params)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("OverlayServiceChannel", "Overlay Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, "OverlayServiceChannel")
            .setContentTitle("HackEx Harvester")
            .setContentText("Overlay active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingButton.isInitialized) {
            windowManager.removeView(floatingButton)
        }
    }
}
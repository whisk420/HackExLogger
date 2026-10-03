package com.whisk.hackexlogger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    companion object {
        const val ACTION_TOGGLE_VALIDATION = "com.whisk.hackexlogger.TOGGLE_VALIDATION"
        const val ACTION_START_VALIDATION = "com.whisk.hackexlogger.START_VALIDATION"
        const val ACTION_STOP_VALIDATION = "com.whisk.hackexlogger.STOP_VALIDATION"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var cameraFloatingButton: Button
    
    private var isValidating = false
    private var currentValidatingTarget: TargetRecord? = null
    private var validationView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupCameraFloatingButton()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_VALIDATION -> {
                if (isValidating) stopValidationMode() else startValidationMode()
            }
            ACTION_START_VALIDATION -> {
                startValidationMode()
            }
            ACTION_STOP_VALIDATION -> {
                stopValidationMode()
            }
        }
        return START_STICKY
    }

    private fun setupCameraFloatingButton() {
        cameraFloatingButton = Button(this).apply {
            text = "📸"
            setBackgroundColor(Color.parseColor("#80000000"))
            setTextColor(Color.WHITE)
            textSize = 20f
            isAllCaps = false
            setPadding(0, 0, 0, 0)
            
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor("#99000000"))
            }
        }

        val size = (50 * resources.displayMetrics.density).toInt()

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

        cameraFloatingButton.setOnTouchListener { view, event ->
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
                    windowManager.updateViewLayout(cameraFloatingButton, params)
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

        cameraFloatingButton.setOnClickListener {
            val scrapeIntent = Intent(ScraperAccessibilityService.ACTION_TRIGGER_SCRAPE)
            scrapeIntent.setPackage(packageName)
            sendBroadcast(scrapeIntent)
        }

        windowManager.addView(cameraFloatingButton, params)
    }

    private fun startValidationMode() {
        if (isValidating) {
            loadNextValidationTarget()
            return
        }

        if (::cameraFloatingButton.isInitialized) {
            cameraFloatingButton.visibility = View.GONE
        }

        isValidating = true

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_validation, null)
        validationView = view

        val validBtn = view.findViewById<Button>(R.id.overlayValidBtn)
        val invalidBtn = view.findViewById<Button>(R.id.overlayInvalidBtn)
        val closeBtn = view.findViewById<Button>(R.id.overlayCloseBtn)

        validBtn.setOnClickListener {
            currentValidatingTarget?.let { target ->
                DatabaseManager.markValidated(target.ip)
                notifyDataUpdated()
            }
            loadNextValidationTarget()
        }

        invalidBtn.setOnClickListener {
            currentValidatingTarget?.let { target ->
                DatabaseManager.deleteTarget(target.ip)
                notifyDataUpdated()
            }
            loadNextValidationTarget()
        }

        closeBtn.setOnClickListener {
            stopValidationMode()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 100
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        val ipBadge = view.findViewById<TextView>(R.id.overlayIpBadge)
        ipBadge.setOnTouchListener { _, event ->
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
                    windowManager.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(view, params)
        loadNextValidationTarget()
    }

    private fun loadNextValidationTarget() {
        val next = DatabaseManager.getNextRecordToValidate()
        if (next == null) {
            Toast.makeText(this, "All unmasked targets validated!", Toast.LENGTH_SHORT).show()
            stopValidationMode()
            return
        }

        currentValidatingTarget = next

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Target IP", next.ip)
        clipboard.setPrimaryClip(clip)

        validationView?.findViewById<TextView>(R.id.overlayIpBadge)?.text = next.ip
        Toast.makeText(this, "Copied IP: ${next.ip}", Toast.LENGTH_SHORT).show()
    }

    private fun stopValidationMode() {
        isValidating = false
        currentValidatingTarget = null

        validationView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // Ignore if already removed
            }
            validationView = null
        }

        if (::cameraFloatingButton.isInitialized) {
            cameraFloatingButton.visibility = View.VISIBLE
        }
    }

    private fun notifyDataUpdated() {
        val updateIntent = Intent("com.whisk.hackexlogger.DATA_UPDATED")
        updateIntent.setPackage(packageName)
        sendBroadcast(updateIntent)
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
        if (::cameraFloatingButton.isInitialized) {
            try { windowManager.removeView(cameraFloatingButton) } catch (e: Exception) {}
        }
        stopValidationMode()
    }
}
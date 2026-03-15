package com.aautomations.eladtracker

// Main accessibility service — the heart of the tracker.
// Listens for app foreground changes and notification events,
// delegates screenshot capture to ScreenshotHelper, and batches
// all events for upload via EventBatcher.
// Runs as a persistent foreground service.

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.IntentFilter
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class TrackingAccessibilityService : AccessibilityService() {

    private lateinit var batcher: EventBatcher
    private lateinit var screenshotHelper: ScreenshotHelper
    private lateinit var screenReceiver: ScreenReceiver
    private lateinit var networkReceiver: NetworkReceiver

    // Track current foreground app and when it opened (for duration calculation)
    private var currentApp = ""
    private var appStartTime = System.currentTimeMillis()

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("Tracker", "Service connected")

        batcher = EventBatcher(this)
        batcher.start()

        screenshotHelper = ScreenshotHelper(this, this)

        // Register broadcast receivers for screen and network events
        screenReceiver = ScreenReceiver(batcher)
        networkReceiver = NetworkReceiver(batcher)

        val screenFilter = IntentFilter().apply {
            addAction(android.content.Intent.ACTION_SCREEN_ON)
            addAction(android.content.Intent.ACTION_SCREEN_OFF)
            addAction(android.content.Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, screenFilter)

        val networkFilter = IntentFilter(android.net.ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(networkReceiver, networkFilter)

        // startForeground keeps the service alive; low-importance notification hides clutter
        startForeground(1, buildNotification())

        batcher.addEvent("service_start", mapOf("msg" to "Tracker started"))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: return

                // Ignore if same app or system UI overlay
                if (pkg == currentApp) return
                if (pkg.startsWith("com.android.systemui")) return

                // Record how long the previous app was open
                val now = System.currentTimeMillis()
                val duration = now - appStartTime
                batcher.addEvent("app_switch", mapOf(
                    "from_app" to currentApp,
                    "to_app" to pkg,
                    "duration_ms" to duration
                ))

                currentApp = pkg
                appStartTime = now
                screenshotHelper.onAppChanged(pkg)
            }

            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                // Log which app posted a notification (without reading content)
                val pkg = event.packageName?.toString() ?: return
                batcher.addEvent("notification", mapOf("app" to pkg))
            }
        }
    }

    // Build a minimal foreground notification so Android keeps the service alive.
    // IMPORTANCE_MIN = no sound, no heads-up, appears at bottom of shade.
    private fun buildNotification(): Notification {
        val channelId = "tracker_channel"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Tracker", NotificationManager.IMPORTANCE_MIN)
        )
        return Notification.Builder(this, channelId)
            .setContentTitle("System Service")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .build()
    }

    override fun onInterrupt() {
        Log.d("Tracker", "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        batcher.stop()
        screenshotHelper.stop()
        // Unregister receivers safely (they may not be registered if onCreate failed)
        try { unregisterReceiver(screenReceiver) } catch (e: Exception) {}
        try { unregisterReceiver(networkReceiver) } catch (e: Exception) {}
    }
}

package com.aautomations.eladtracker

// NotificationListenerService that monitors active MediaSessions for YouTube.
// When a new video starts (metadata changes), it calls ScreenshotHelper.startVideoSession()
// which takes an immediate screenshot and loops every 10 seconds until the video changes
// or YouTube is backgrounded.
//
// This service must be granted Notification Listener access by the user via Settings
// (Settings → Apps → Special app access → Notification access).

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.util.Log

class MediaSessionMonitorService : NotificationListenerService() {

    companion object {
        private const val TAG = "MediaSessionMonitor"
        private const val YOUTUBE_PKG = "com.google.android.youtube"

        // Singleton reference so TrackingAccessibilityService can pass its ScreenshotHelper.
        // Set when the accessibility service is connected; cleared on destroy.
        var screenshotHelper: ScreenshotHelper? = null
    }

    private var mediaSessionManager: MediaSessionManager? = null

    // Listener that fires whenever the list of active MediaSessions changes.
    // Re-subscribes our callback to any new YouTube session that appears.
    private val sessionChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            Log.d(TAG, "Active sessions changed: ${controllers?.size ?: 0} controllers")
            controllers?.forEach { controller ->
                if (controller.packageName == YOUTUBE_PKG) {
                    attachYouTubeCallback(controller)
                }
            }
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected — subscribing to MediaSessions")

        mediaSessionManager =
            getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return

        val componentName = ComponentName(this, MediaSessionMonitorService::class.java)

        // Subscribe to future session changes
        mediaSessionManager!!.addOnActiveSessionsChangedListener(
            sessionChangedListener,
            componentName
        )

        // Also attach to any YouTube session already active right now
        mediaSessionManager!!.getActiveSessions(componentName)
            .filter { it.packageName == YOUTUBE_PKG }
            .forEach { attachYouTubeCallback(it) }
    }

    // Attaches a MediaController.Callback to the given YouTube controller so we are
    // notified whenever the video metadata (title, duration) changes.
    private fun attachYouTubeCallback(controller: MediaController) {
        Log.d(TAG, "Attaching callback to YouTube MediaController")

        controller.registerCallback(object : MediaController.Callback() {

            // Fires when the currently playing video changes (title, duration, etc.)
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                if (metadata == null) return

                val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?: return  // No title — skip

                // Duration in milliseconds; Shorts are < 60 seconds
                val durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
                val isShorts = durationMs in 1 until 60_000L  // >0 guards against unknown duration

                Log.d(TAG, "YouTube metadata changed — title=\"$title\" duration=${durationMs}ms isShorts=$isShorts")

                // Delegate to ScreenshotHelper if it's been wired up
                screenshotHelper?.startVideoSession(title, isShorts)
                    ?: Log.w(TAG, "screenshotHelper not set — ignoring video session")
            }
        })
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification listener disconnected")

        // Best-effort cleanup; manager reference may already be invalid
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionChangedListener)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove session listener: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionChangedListener)
        } catch (e: Exception) { /* ignore */ }
    }
}

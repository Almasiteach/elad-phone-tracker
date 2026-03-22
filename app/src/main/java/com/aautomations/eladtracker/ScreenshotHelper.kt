package com.aautomations.eladtracker

// Handles screenshot capture via AccessibilityService.takeScreenshot() and uploads
// the result to n8n as a base64-encoded JPEG.
//
// Cadence:
//   - Regular apps: one screenshot every 2 minutes while the app stays foregrounded
//   - WhatsApp: immediate + every 15 seconds while foregrounded
//   - YouTube: handled externally via MediaSessionMonitorService (startVideoSession /
//     stopVideoSession), which takes an immediate shot + every 10s per video session
//   - Screenshots can be globally disabled via the screenshots_enabled SharedPref flag
//
// All uploads include videoTitle, isShorts, sessionFrame, and label fields.

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ScreenshotHelper(
    private val service: AccessibilityService,
    private val context: Context
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Handler runs on main thread — required for takeScreenshot() callback scheduling
    private val handler = Handler(Looper.getMainLooper())
    private var currentApp = ""
    private var regularRunnable: Runnable? = null
    private val deviceName = android.os.Build.MODEL

    // --- YouTube / video session state ---
    private var videoRunnable: Runnable? = null
    private var currentVideoTitle: String? = null
    private var currentVideoIsShorts: Boolean? = null
    private var videoFrameCounter = 0
    private var videoBaseTimestamp = ""  // HH-mm at the moment the video started

    // Check SharedPrefs for the screenshots_enabled flag (default: true/ON)
    fun isScreenshotsEnabled(): Boolean {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(Constants.PREF_SCREENSHOTS_ENABLED, true)
    }

    // Called by TrackingAccessibilityService whenever the foreground app changes.
    // Cancels any in-flight scheduled shots and starts the appropriate cadence.
    // YouTube is excluded from the regular cadence — MediaSession handles it.
    fun onAppChanged(newApp: String) {
        if (!isScreenshotsEnabled()) return
        currentApp = newApp

        // Cancel pending regular runnable for the previous app
        regularRunnable?.let { handler.removeCallbacks(it) }

        when {
            newApp == "com.whatsapp" -> {
                // Immediate first shot, then every 15 seconds indefinitely
                takeAndUpload(newApp, label = "whatsapp")
                scheduleRepeating(newApp, Constants.WHATSAPP_INTERVAL_MS, label = "whatsapp")
            }
            newApp == "com.google.android.youtube" -> {
                // MediaSessionMonitorService handles YouTube screenshots via startVideoSession().
                // Do NOT start a regular cadence here.
            }
            else -> {
                // Standard app: every 2 minutes
                scheduleRepeating(newApp, Constants.SCREENSHOT_INTERVAL_MS, label = "regular")
            }
        }
    }

    // Called by MediaSessionMonitorService when a new YouTube video starts playing.
    // Stops any prior video session, then takes an immediate screenshot and starts
    // the 10-second follow-up loop (runs indefinitely until stopped).
    fun startVideoSession(videoTitle: String, isShorts: Boolean) {
        if (!isScreenshotsEnabled()) return

        // Stop any existing video session loop before starting a new one
        stopVideoSession()

        currentVideoTitle = videoTitle
        currentVideoIsShorts = isShorts
        videoFrameCounter = 0

        // Record HH-mm at session start so all frames share the same base timestamp
        videoBaseTimestamp = SimpleDateFormat("HH-mm", Locale.getDefault()).format(Date())

        val sanitizedTitle = sanitizeTitle(videoTitle)

        // Frame 0: immediate screenshot
        val filename0 = "screenshot_${videoBaseTimestamp}_${sanitizedTitle}.jpg"
        takeAndUploadVideo(filename0, videoTitle, isShorts, sessionFrame = 0)

        // Start looping follow-up shots every VIDEO_FOLLOW_UP_MS (no cap)
        videoRunnable = object : Runnable {
            override fun run() {
                if (!isScreenshotsEnabled()) return
                videoFrameCounter++
                val filename = "screenshot_${videoBaseTimestamp}_${sanitizedTitle}_${videoFrameCounter}.jpg"
                takeAndUploadVideo(filename, videoTitle, isShorts, sessionFrame = videoFrameCounter)
                handler.postDelayed(this, Constants.VIDEO_FOLLOW_UP_MS)
            }
        }
        handler.postDelayed(videoRunnable!!, Constants.VIDEO_FOLLOW_UP_MS)
    }

    // Cancels the running video follow-up loop.
    // Called when YouTube is backgrounded or a new video starts.
    fun stopVideoSession() {
        videoRunnable?.let { handler.removeCallbacks(it) }
        videoRunnable = null
        currentVideoTitle = null
        currentVideoIsShorts = null
        videoFrameCounter = 0
        videoBaseTimestamp = ""
    }

    // Schedules a self-rescheduling runnable at the given interval.
    // Stops automatically when the foreground app changes (currentApp guard).
    private fun scheduleRepeating(appName: String, intervalMs: Long, label: String) {
        val runnable = object : Runnable {
            override fun run() {
                if (currentApp == appName && isScreenshotsEnabled()) {
                    takeAndUpload(appName, label = label)
                    handler.postDelayed(this, intervalMs)
                }
            }
        }
        regularRunnable = runnable
        handler.postDelayed(runnable, intervalMs)
    }

    // Triggers AccessibilityService.takeScreenshot() and dispatches a standard upload on success.
    private fun takeAndUpload(appName: String, label: String) {
        if (!isScreenshotsEnabled()) return
        service.takeScreenshot(
            AccessibilityService.TAKE_SCREENSHOT_HARD_ERROR_IF_INACCESSIBLE,
            context.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val bitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, null)
                    screenshot.hardwareBuffer.close()
                    if (bitmap != null) {
                        uploadScreenshot(
                            bitmap = bitmap,
                            appName = appName,
                            filename = null,       // auto-generated inside uploadScreenshot
                            videoTitle = null,
                            isShorts = null,
                            sessionFrame = 0,
                            label = label
                        )
                    }
                }
                override fun onFailure(errorCode: Int) {
                    Log.e("Screenshot", "takeScreenshot failed: errorCode=$errorCode")
                }
            }
        )
    }

    // Triggers a screenshot specifically for a YouTube video session frame.
    // Uses a pre-computed filename so all frames share the HH-mm base timestamp.
    private fun takeAndUploadVideo(
        filename: String,
        videoTitle: String,
        isShorts: Boolean,
        sessionFrame: Int
    ) {
        if (!isScreenshotsEnabled()) return
        service.takeScreenshot(
            AccessibilityService.TAKE_SCREENSHOT_HARD_ERROR_IF_INACCESSIBLE,
            context.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val bitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, null)
                    screenshot.hardwareBuffer.close()
                    if (bitmap != null) {
                        uploadScreenshot(
                            bitmap = bitmap,
                            appName = "com.google.android.youtube",
                            filename = filename,
                            videoTitle = videoTitle,
                            isShorts = isShorts,
                            sessionFrame = sessionFrame,
                            label = "youtube"
                        )
                    }
                }
                override fun onFailure(errorCode: Int) {
                    Log.e("Screenshot", "takeScreenshot (video) failed: errorCode=$errorCode")
                }
            }
        )
    }

    // Compresses bitmap to JPEG (max 1280px wide, quality 70), base64-encodes it,
    // and POSTs to n8n /phone-screenshot on a background thread.
    // All uploads include videoTitle, isShorts, sessionFrame, and label.
    // If filename is null it is auto-generated from app name + timestamp.
    private fun uploadScreenshot(
        bitmap: Bitmap,
        appName: String,
        filename: String?,
        videoTitle: String?,
        isShorts: Boolean?,
        sessionFrame: Int,
        label: String
    ) {
        // Downscale if wider than 1280px to keep upload size manageable
        val maxWidth = 1280
        val scaled = if (bitmap.width > maxWidth) {
            val ratio = maxWidth.toFloat() / bitmap.width
            Bitmap.createScaledBitmap(bitmap, maxWidth, (bitmap.height * ratio).toInt(), true)
        } else bitmap

        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

        // Build filename: screenshot_{shortAppName}_{YYYY-MM-DD}_{HH-MM-SS}.jpg (if not pre-set)
        val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val timestamp = sdf.format(Date())
        val shortApp = appName.substringAfterLast(".")
        val resolvedFilename = filename ?: "screenshot_${shortApp}_${timestamp}.jpg"
        val isoTimestamp = java.time.Instant.now().toString()

        // HTTP upload runs on a plain thread (OkHttp handles its own I/O threading)
        Thread {
            try {
                val body = JSONObject().apply {
                    put("image", base64)
                    put("app", shortApp)
                    put("filename", resolvedFilename)
                    put("timestamp", isoTimestamp)
                    put("device", deviceName)
                    // Extended fields — null when not applicable
                    put("videoTitle", videoTitle ?: JSONObject.NULL)
                    put("isShorts", if (isShorts != null) isShorts else JSONObject.NULL)
                    put("sessionFrame", sessionFrame)
                    put("label", label)
                }.toString()
                val request = Request.Builder()
                    .url(Constants.SCREENSHOT_URL)
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute().close()
            } catch (e: Exception) {
                Log.e("Screenshot", "Upload failed: ${e.message}")
            }
        }.start()
    }

    // Converts a video title into a safe filename segment:
    // lowercase, spaces → hyphens, strip non-alphanumeric except hyphens, max 40 chars.
    private fun sanitizeTitle(title: String): String {
        return title
            .lowercase(Locale.getDefault())
            .replace(' ', '-')
            .replace(Regex("[^a-z0-9\\-]"), "")
            .take(40)
            .trimEnd('-')
    }

    // Cancel all pending scheduled callbacks on service shutdown
    fun stop() {
        regularRunnable?.let { handler.removeCallbacks(it) }
        stopVideoSession()
    }
}

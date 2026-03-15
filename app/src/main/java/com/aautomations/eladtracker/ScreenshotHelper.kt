package com.aautomations.eladtracker

// Handles screenshot capture via AccessibilityService.takeScreenshot() and uploads
// the result to n8n as a base64-encoded JPEG.
//
// Cadence:
//   - Regular apps: one screenshot every 2 minutes while the app stays foregrounded
//   - Special apps (WhatsApp, YouTube): immediate + 30s follow-up + every 60s thereafter
//   - Screenshots can be globally disabled via the screenshots_enabled SharedPref flag

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
    private var specialRunnable: Runnable? = null
    private var regularRunnable: Runnable? = null
    private val deviceName = android.os.Build.MODEL

    // Check SharedPrefs for the screenshots_enabled flag (default: true/ON)
    fun isScreenshotsEnabled(): Boolean {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(Constants.PREF_SCREENSHOTS_ENABLED, true)
    }

    // Called by TrackingAccessibilityService whenever the foreground app changes.
    // Cancels any in-flight scheduled shots and starts the appropriate cadence.
    fun onAppChanged(newApp: String) {
        if (!isScreenshotsEnabled()) return
        currentApp = newApp

        // Cancel pending runnables for the previous app
        specialRunnable?.let { handler.removeCallbacks(it) }
        regularRunnable?.let { handler.removeCallbacks(it) }

        if (newApp in Constants.SPECIAL_APPS) {
            // Immediate first shot
            takeAndUpload(newApp)

            // Follow-up shot at +30s, then repeat every 60s while still in this app
            specialRunnable = Runnable {
                if (currentApp == newApp && isScreenshotsEnabled()) {
                    takeAndUpload(newApp)
                    scheduleRepeating(newApp, Constants.SCREENSHOT_SPECIAL_INTERVAL_MS)
                }
            }
            handler.postDelayed(specialRunnable!!, Constants.SCREENSHOT_SPECIAL_DELAY_MS)
        } else {
            // Standard app: every 2 minutes
            scheduleRepeating(newApp, Constants.SCREENSHOT_INTERVAL_MS)
        }
    }

    // Schedules a self-rescheduling runnable at the given interval.
    // Stops automatically when the foreground app changes.
    private fun scheduleRepeating(appName: String, intervalMs: Long) {
        val runnable = object : Runnable {
            override fun run() {
                if (currentApp == appName && isScreenshotsEnabled()) {
                    takeAndUpload(appName)
                    handler.postDelayed(this, intervalMs)
                }
            }
        }
        regularRunnable = runnable
        handler.postDelayed(runnable, intervalMs)
    }

    // Triggers AccessibilityService.takeScreenshot() and dispatches upload on success.
    private fun takeAndUpload(appName: String) {
        if (!isScreenshotsEnabled()) return
        service.takeScreenshot(
            AccessibilityService.TAKE_SCREENSHOT_HARD_ERROR_IF_INACCESSIBLE,
            context.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val bitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, null)
                    screenshot.hardwareBuffer.close()
                    if (bitmap != null) {
                        uploadScreenshot(bitmap, appName)
                    }
                }
                override fun onFailure(errorCode: Int) {
                    Log.e("Screenshot", "takeScreenshot failed: errorCode=$errorCode")
                }
            }
        )
    }

    // Compresses bitmap to JPEG (max 1280px wide, quality 70), base64-encodes it,
    // and POSTs to n8n /phone-screenshot on a background thread.
    private fun uploadScreenshot(bitmap: Bitmap, appName: String) {
        // Downscale if wider than 1280px to keep upload size manageable
        val maxWidth = 1280
        val scaled = if (bitmap.width > maxWidth) {
            val ratio = maxWidth.toFloat() / bitmap.width
            Bitmap.createScaledBitmap(bitmap, maxWidth, (bitmap.height * ratio).toInt(), true)
        } else bitmap

        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

        // Build filename: screenshot_{shortAppName}_{YYYY-MM-DD}_{HH-MM-SS}.jpg
        val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val timestamp = sdf.format(Date())
        val shortApp = appName.substringAfterLast(".")
        val filename = "screenshot_${shortApp}_${timestamp}.jpg"
        val isoTimestamp = java.time.Instant.now().toString()

        // HTTP upload runs on a plain thread (OkHttp handles its own I/O threading)
        Thread {
            try {
                val body = JSONObject().apply {
                    put("image", base64)
                    put("app", shortApp)
                    put("filename", filename)
                    put("timestamp", isoTimestamp)
                    put("device", deviceName)
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

    // Cancel all pending scheduled callbacks on service shutdown
    fun stop() {
        specialRunnable?.let { handler.removeCallbacks(it) }
        regularRunnable?.let { handler.removeCallbacks(it) }
    }
}

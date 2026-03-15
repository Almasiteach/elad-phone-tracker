package com.aautomations.eladtracker

// Collects events and uploads to n8n in batches every 2 minutes.
// Also polls the /phone-poll endpoint to refresh settings (screenshots_enabled flag).
// Thread-safe: events list is synchronized; HTTP is done on a background executor.

import android.content.Context
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class EventBatcher(private val context: Context) {

    private val events = mutableListOf<JSONObject>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val executor = Executors.newSingleThreadScheduledExecutor()

    // Use device model name as identifier in all uploads
    private val deviceName = android.os.Build.MODEL

    // Start the periodic flush + poll cycle
    fun start() {
        executor.scheduleAtFixedRate({
            flush()
            pollSettings()
        }, Constants.BATCH_INTERVAL_MS, Constants.BATCH_INTERVAL_MS, TimeUnit.MILLISECONDS)
    }

    // Add an event to the pending queue (thread-safe)
    fun addEvent(type: String, data: Map<String, Any>) {
        synchronized(events) {
            val event = JSONObject().apply {
                put("type", type)
                put("timestamp", java.time.Instant.now().toString())
                data.forEach { (k, v) -> put(k, v) }
            }
            events.add(event)
        }
    }

    // Upload all pending events to n8n and clear the queue.
    // On network failure, events are re-queued to avoid data loss.
    private fun flush() {
        val toSend: List<JSONObject>
        synchronized(events) {
            if (events.isEmpty()) return
            toSend = events.toList()
            events.clear()
        }
        try {
            val body = JSONObject().apply {
                put("events", JSONArray(toSend.map { it }))
                put("device", deviceName)
            }.toString()
            val request = Request.Builder()
                .url(Constants.UPLOAD_URL)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().close()
        } catch (e: Exception) {
            Log.e("EventBatcher", "Upload failed: ${e.message}")
            // Re-add events so they are retried on the next flush
            synchronized(events) { events.addAll(toSend) }
        }
    }

    // Poll n8n for settings updates (mainly screenshots_enabled toggle)
    private fun pollSettings() {
        try {
            val request = Request.Builder().url(Constants.POLL_URL).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return
            val json = JSONObject(body)
            val screenshotsEnabled = json.optBoolean("screenshots_enabled", true)
            val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(Constants.PREF_SCREENSHOTS_ENABLED, screenshotsEnabled).apply()
        } catch (e: Exception) {
            Log.e("EventBatcher", "Poll failed: ${e.message}")
        }
    }

    fun stop() {
        executor.shutdown()
    }
}

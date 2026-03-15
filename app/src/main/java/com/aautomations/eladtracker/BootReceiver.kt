package com.aautomations.eladtracker

// BroadcastReceiver that fires on BOOT_COMPLETED and LOCKED_BOOT_COMPLETED.
// The AccessibilityService will restart automatically if the user has it enabled
// in Settings → Accessibility. We just log here so the boot event appears in logcat.

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BootReceiver", "Boot completed — tracker ready (accessibility service auto-resumes)")
    }
}

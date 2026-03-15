package com.aautomations.eladtracker

// BroadcastReceiver for screen on/off and unlock events.
// Forwards each event to the EventBatcher for inclusion in the next upload batch.

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScreenReceiver(private val batcher: EventBatcher) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> batcher.addEvent("screen_on", emptyMap())
            Intent.ACTION_SCREEN_OFF -> batcher.addEvent("screen_off", emptyMap())
            Intent.ACTION_USER_PRESENT -> batcher.addEvent("screen_unlock", emptyMap())
        }
    }
}

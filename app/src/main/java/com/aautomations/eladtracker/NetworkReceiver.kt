package com.aautomations.eladtracker

// BroadcastReceiver for WiFi connect/disconnect events.
// Logs whether the active network has WiFi transport and sends to EventBatcher.

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class NetworkReceiver(private val batcher: EventBatcher) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)

        // true = connected to WiFi, false = no WiFi (cellular or disconnected)
        val connected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        batcher.addEvent("wifi", mapOf("connected" to connected))
    }
}

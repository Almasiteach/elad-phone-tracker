package com.aautomations.eladtracker

// Minimal one-time setup activity.
// Guides the user to grant the three required permissions:
//   1. Usage Access (PACKAGE_USAGE_STATS) — via Settings
//   2. Accessibility Service — via Settings → Accessibility
//   3. Notification Listener — via Settings → Special app access → Notification access
//      (required for MediaSessionMonitorService to access YouTube MediaSessions)
// Once all are granted the user can close the app; the services run in the background.

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = TextView(this).apply {
            text = "Setup:\n\n" +
                "1. Tap 'Usage Access' → enable for this app\n" +
                "2. Tap 'Accessibility' → enable 'System Service'\n" +
                "3. Tap 'Notification Listener' → enable for this app\n\n" +
                "After all three are enabled you can close this screen.\n" +
                "The tracker runs silently in the background."
            textSize = 16f
            setPadding(40, 60, 40, 20)
        }

        // Button to open USAGE_ACCESS_SETTINGS
        val usageBtn = Button(this).apply {
            setText("1. Usage Access")
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }

        // Button to open ACCESSIBILITY_SETTINGS
        val accessibilityBtn = Button(this).apply {
            setText("2. Accessibility Service")
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        // Button to open NOTIFICATION_LISTENER_SETTINGS so the user can grant
        // MediaSession access for the MediaSessionMonitorService (YouTube tracking)
        val notificationListenerBtn = Button(this).apply {
            setText("3. Notification Listener Access")
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            addView(text)
            addView(usageBtn)
            addView(accessibilityBtn)
            addView(notificationListenerBtn)
        }

        setContentView(layout)
    }
}

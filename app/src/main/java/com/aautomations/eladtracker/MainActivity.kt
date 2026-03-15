package com.aautomations.eladtracker

// Minimal one-time setup activity.
// Guides the user to grant the two required permissions:
//   1. Usage Access (PACKAGE_USAGE_STATS) — via Settings
//   2. Accessibility Service — via Settings → Accessibility
// Once both are granted the user can close the app; the service runs in the background.

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
                "2. Tap 'Accessibility' → enable 'System Service'\n\n" +
                "After both are enabled you can close this screen.\n" +
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

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            addView(text)
            addView(usageBtn)
            addView(accessibilityBtn)
        }

        setContentView(layout)
    }
}

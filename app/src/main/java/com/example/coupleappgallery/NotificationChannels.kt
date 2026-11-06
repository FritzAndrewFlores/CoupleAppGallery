package com.example.coupleappgallery

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/** Single source of truth for the app's notification channel. */
object NotificationChannels {
    const val COUPLE_APP_CHANNEL = "COUPLE_APP_CHANNEL"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                COUPLE_APP_CHANNEL,
                "CoupleApp Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Messages and partner updates"
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }
}

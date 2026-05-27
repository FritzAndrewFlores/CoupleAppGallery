package com.example.coupleappgallery

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    // This triggers when a notification is received while the app is in the foreground,
    // or when a "Data" payload is received while the app is closed.
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // FCM can send "Notification" payloads or "Data" payloads. We check both.
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "New Update"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: "Open the app to see!"

        showNotification(title, body)
    }

    // This triggers if Google assigns the phone a brand new token
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "Refreshed token: $token")

        // Update the token in Firestore so the partner's app knows the new "address"
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            FirebaseFirestore.getInstance().collection("users").document(userId)
                .update("fcmToken", token)
        }
    }

    private fun showNotification(title: String, body: String) {
        createNotificationChannel()

        // Check if Android 13+ Notification permission is granted
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {

            val builder = NotificationCompat.Builder(this, "COUPLE_APP_CHANNEL")
                .setSmallIcon(android.R.drawable.ic_dialog_email) // You can change this to your custom app icon later
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "COUPLE_APP_CHANNEL",
                "CoupleAppUpdates",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
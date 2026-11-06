package com.example.coupleappgallery

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "New Update"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: "Open the app to see!"
        val docId = remoteMessage.data["docId"]
        val route = remoteMessage.data["route"]

        showSystemNotification(this, title, body, docId, route)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "FCM registration token refreshed")

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            FirebaseFirestore.getInstance().collection("users").document(userId)
                .set(mapOf("fcmToken" to token), SetOptions.merge())
                .addOnFailureListener { e ->
                    Log.w("FCM", "Failed to save refreshed FCM token", e)
                }
        }
    }
}

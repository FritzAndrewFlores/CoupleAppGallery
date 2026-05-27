package com.example.coupleappgallery

import com.google.firebase.firestore.FirebaseFirestore

fun sendNotification(
    db: FirebaseFirestore,
    to: String,
    title: String,
    body: String,
    windowMillis: Long = 60_000L
) {
    if (to.isEmpty()) return
    val now = System.currentTimeMillis()
    val cutoff = now - windowMillis
    db.collection("notifications")
        .whereEqualTo("to", to)
        .whereEqualTo("title", title)
        .whereGreaterThan("timestamp", cutoff)
        .limit(1)
        .get()
        .addOnSuccessListener { snap ->
            if (!snap.isEmpty) {
                val existing = snap.documents.first()
                db.collection("notifications").document(existing.id).update(
                    mapOf("body" to body, "timestamp" to now, "read" to false)
                )
            } else {
                db.collection("notifications").add(
                    hashMapOf("to" to to, "title" to title, "body" to body, "read" to false, "timestamp" to now)
                )
            }
        }
}

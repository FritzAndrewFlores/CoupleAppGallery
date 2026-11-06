package com.example.coupleappgallery

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.ConcurrentHashMap

private val recentOutbound = ConcurrentHashMap<String, Long>()
private val recentInbound = ConcurrentHashMap<String, Long>()

private const val MAX_NOTIFICATION_BODY_LEN = 500

/** Tracks the currently visible foreground screen so active chat doesn't spam tray banners. */
object NotificationForegroundTracker {
    @Volatile
    var activeRoute: String? = null
}

/**
 * Creates a notification document for [to].
 * Avoids reading the recipient's notifications collection so Firestore rules can strictly
 * restrict notification reads/updates/deletes to the recipient (`resource.data.to == auth.uid`).
 * Uses a short in-memory guard to suppress accidental duplicate double-taps while still
 * creating new documents for distinct messages so Cloud Functions `onDocumentCreated` fires.
 */
fun sendNotification(
    db: FirebaseFirestore,
    to: String,
    title: String,
    body: String,
    dedupeWindowMillis: Long = 2_500L
) {
    val from = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    if (to.isEmpty() || from.isEmpty() || to == from) return

    val safeTitle = if (title.length > 120) title.take(117) + "..." else title
    val safeBody = if (body.length > MAX_NOTIFICATION_BODY_LEN) {
        body.take(MAX_NOTIFICATION_BODY_LEN - 3) + "..."
    } else {
        body
    }

    val now = System.currentTimeMillis()
    val key = "$to|$safeTitle|$safeBody"
    val lastSent = recentOutbound[key]
    if (lastSent != null && now - lastSent < dedupeWindowMillis) return
    recentOutbound[key] = now

    if (recentOutbound.size > 100) {
        val cutoff = now - 60_000L
        recentOutbound.entries.removeIf { it.value < cutoff }
    }

    val newDoc = hashMapOf(
        "from" to from,
        "to" to to,
        "title" to safeTitle,
        "body" to safeBody,
        "read" to false,
        "timestamp" to now
    )

    db.collection("notifications").add(newDoc)
        .addOnFailureListener { e ->
            Log.e("FS", "sendNotification: add failed", e)
        }
}

/**
 * Resolves the target navigation route for a notification title when no explicit route is supplied.
 */
fun resolveNotificationRoute(title: String, explicitRoute: String? = null): String {
    if (!explicitRoute.isNullOrEmpty()) return explicitRoute
    return when {
        title.contains("Message", ignoreCase = true) ||
            title.contains("💌") ||
            title.contains("New Reaction", ignoreCase = true) ||
            title.contains("Chat", ignoreCase = true) -> "chat"
        else -> "home"
    }
}

/**
 * Shows a system tray notification with:
 * 1. In-memory deduplication across FCM push and foreground Firestore listener within 5s window.
 * 2. Deterministic notification ID so simultaneous arrivals update in-place instead of stacking.
 * 3. PendingIntent that opens MainActivity and routes to the relevant screen when tapped.
 */
fun showSystemNotification(
    context: Context,
    title: String,
    body: String,
    docId: String? = null,
    route: String? = null
) {
    val resolvedRoute = resolveNotificationRoute(title, route)

    // Don't interrupt with a heads-up chat banner when the user is actively viewing ChatScreen
    if (resolvedRoute == "chat" && NotificationForegroundTracker.activeRoute == "chat") {
        return
    }

    val dedupeKey = docId?.takeIf { it.isNotEmpty() } ?: "$title|$body"
    val now = System.currentTimeMillis()
    val lastShown = recentInbound[dedupeKey]
    if (lastShown != null && now - lastShown < 5_000L) {
        return // Suppress duplicate notification
    }
    recentInbound[dedupeKey] = now

    // Housekeeping: purge entries older than 60s
    if (recentInbound.size > 100) {
        val cutoff = now - 60_000L
        recentInbound.entries.removeIf { it.value < cutoff }
    }

    NotificationChannels.ensureChannel(context)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    val notificationId = dedupeKey.hashCode()

    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra("route", resolvedRoute)
    }

    val pendingIntent = PendingIntent.getActivity(
        context,
        notificationId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val builder = NotificationCompat.Builder(context, NotificationChannels.COUPLE_APP_CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_heart)
        .setContentTitle(title)
        .setContentText(body)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)

    NotificationManagerCompat.from(context).notify(notificationId, builder.build())
}

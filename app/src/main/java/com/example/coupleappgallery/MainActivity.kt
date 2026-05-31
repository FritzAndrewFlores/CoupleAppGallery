package com.example.coupleappgallery

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging

// --- 1. THEME ENGINE ---
data class AppTheme(
    val id: String = "forest_green",
    val primary: Color = Color(0xFF2E5948),
    val secondary: Color = Color(0xFF8FBC8F),
    val background: Color = Color(0xFFF4F9F4),
    val surface: Color = Color.White,
    val text: Color = Color(0xFF4A3728),
    val navBar: Color = Color(0xFF2E5948),
    val wallpaperUrl: String = ""
)

// PASTEL & AESTHETIC THEME PRESETS
val appThemes = mapOf(
    "forest_green" to AppTheme("forest_green", Color(0xFF2E5948), Color(0xFF8FBC8F), Color(0xFFF4F9F4), Color.White, Color(0xFF4A3728), Color(0xFF2E5948)),
    "glacier_ice" to AppTheme("glacier_ice", Color(0xFF5D8AA8), Color(0xFFA0C4E3), Color(0xFFF0F8FF), Color(0xFFEBF5FB), Color(0xFF2C3E50), Color(0xFF5D8AA8)),
    "smoke_grey" to AppTheme("smoke_grey", Color(0xFF607D8B), Color(0xFFB0BEC5), Color(0xFFECEFF1), Color.White, Color(0xFF263238), Color(0xFF607D8B)),
    "starry_night" to AppTheme("starry_night", Color(0xFF3949AB), Color(0xFF7986CB), Color(0xFFE8EAF6), Color.White, Color(0xFF1A237E), Color(0xFF303F9F)),
    "autumn_falls" to AppTheme("autumn_falls", Color(0xFFD84315), Color(0xFFFF8A65), Color(0xFFFBE9E7), Color.White, Color(0xFF3E2723), Color(0xFFBF360C)),
    "ocean_blue" to AppTheme("ocean_blue", Color(0xFF0277BD), Color(0xFF4FC3F7), Color(0xFFE1F5FE), Color.White, Color(0xFF01579B), Color(0xFF0277BD)),
    "ruby_red" to AppTheme("ruby_red", Color(0xFFD32F2F), Color(0xFFE57373), Color(0xFFFFEBEE), Color.White, Color(0xFFB71C1C), Color(0xFFC62828)),
    "skies_blue" to AppTheme("skies_blue", Color(0xFF42A5F5), Color(0xFF90CAF9), Color(0xFFE3F2FD), Color.White, Color(0xFF0D47A1), Color(0xFF1E88E5)),
    "rainbow" to AppTheme("rainbow", Color(0xFFBA68C8), Color(0xFFE1BEE7), Color(0xFFF3E5F5), Color.White, Color(0xFF4A148C), Color(0xFF9C27B0)),
    "white" to AppTheme("white", Color(0xFF757575), Color(0xFFBDBDBD), Color(0xFFFAFAFA), Color.White, Color.Black, Color(0xFF616161))
)

class MainActivity : ComponentActivity() {

    private var notificationListener: ListenerRegistration? = null
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Permission Launcher for Android 13+
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        // Ask for notification permission on launch
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        createNotificationChannel()

        setContent {
            val navController = rememberNavController()
            val userId = auth.currentUser?.uid

            // Initialize process-scoped SessionCache for user + partner profile data
            LaunchedEffect(userId) {
                if (userId != null) SessionCache.init(userId) else SessionCache.clear()
            }

            // 2. STATE FOR CURRENT THEME
            var currentTheme by remember { mutableStateOf(appThemes["forest_green"]!!) }

            // 3. LISTEN FOR THEME CHANGES & SET UP NOTIFICATIONS
            DisposableEffect(userId) {
                if (userId == null) return@DisposableEffect onDispose { }
                val themeReg = db.collection("users").document(userId).addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val themeId = snapshot.getString("themeId") ?: "forest_green"
                        if (themeId == "custom") {
                            // Load Custom Colors from DB
                            val primary = Color(snapshot.getLong("themePrimary") ?: 0xFF2E5948)
                            val secondary = Color(snapshot.getLong("themeSecondary") ?: 0xFF8FBC8F)
                            val bg = Color(snapshot.getLong("themeBg") ?: 0xFFF4F9F4)
                            val text = Color(snapshot.getLong("themeText") ?: 0xFF4A3728)
                            val wall = snapshot.getString("themeWallpaper") ?: ""
                            currentTheme = AppTheme("custom", primary, secondary, bg, Color.White, text, primary, wall)
                        } else {
                            // Load Preset
                            currentTheme = appThemes[themeId] ?: appThemes["forest_green"]!!
                        }
                    }
                }

                // START GLOBAL NOTIFICATION LISTENER (Foreground/Minimized)
                startNotificationListener(userId)

                // GET AND SAVE FCM TOKEN (For completely closed app notifications) — one-shot, no cleanup
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                        return@addOnCompleteListener
                    }
                    // Get new FCM registration token
                    val token = task.result
                    // Save token to Firestore user document
                    db.collection("users").document(userId).update("fcmToken", token)
                        .addOnSuccessListener { Log.d("FCM", "Token saved successfully: $token") }
                }
                onDispose { themeReg.remove() }
            }

            NavHost(navController = navController, startDestination = "splash") {
                composable("splash") { SplashScreen(navController) }
                composable("login") { LoginScreen(navController) }

                // PASS 'currentTheme' TO ALL SCREENS
                composable("home") { HomeScreen(navController, currentTheme) }

                composable(
                    "gallery/{folderId}",
                    arguments = listOf(navArgument("folderId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val folderId = backStackEntry.arguments?.getString("folderId") ?: ""
                    GalleryScreen(navController, folderId, currentTheme)
                }

                composable("chat") { ChatScreen(navController, currentTheme) }
                composable("profile") { ProfileScreen(navController, currentTheme) }
                composable("special_days") { SpecialDaysScreen(navController, currentTheme) }

                composable(
                    "special_day_detail/{eventId}",
                    arguments = listOf(navArgument("eventId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val eventId = backStackEntry.arguments?.getString("eventId") ?: ""
                    SpecialDayDetailScreen(navController, eventId, currentTheme)
                }
            }
        }
    }

    // --- LIFECYCLE: ONLINE / OFFLINE LOGIC ---
    override fun onStart() {
        super.onStart()
        updateUserStatus("Online")
    }

    override fun onStop() {
        super.onStop()
        updateUserStatus("Offline")
    }

    private fun updateUserStatus(status: String) {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            db.collection("users").document(userId).update("status", status)
        }
    }

    // --- GLOBAL NOTIFICATION LISTENER ---
    private fun startNotificationListener(userId: String) {
        notificationListener?.remove()
        Log.d("NotifListener", "Registering listener for userId=$userId")
        notificationListener = db.collection("notifications")
            .whereEqualTo("to", userId)
            .whereEqualTo("read", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("NotifListener", "Listener error", error)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    Log.w("NotifListener", "Snapshot was null")
                    return@addSnapshotListener
                }
                Log.d("NotifListener", "Snapshot fired with ${snapshot.documents.size} unread docs")

                snapshot.documents.forEach { doc ->
                    val title = doc.getString("title") ?: "New Activity"
                    val body = doc.getString("body") ?: "Check the app!"
                    val docTo = doc.getString("to") ?: "?"
                    Log.d("NotifListener", "Processing doc.id=${doc.id} to=$docTo title=$title")

                    showSystemNotification(title, body, doc.id.hashCode())

                    db.collection("notifications").document(doc.id).update("read", true)
                        .addOnSuccessListener {
                            Log.d("NotifListener", "Marked read=true for doc.id=${doc.id}")
                        }
                        .addOnFailureListener { e ->
                            Log.e("NotifListener", "FAILED to mark read for doc.id=${doc.id}", e)
                        }
                }
            }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "CoupleAppUpdates"
            val descriptionText = "Messages and Updates"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("COUPLE_APP_CHANNEL", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showSystemNotification(title: String, content: String, id: Int) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            val builder = NotificationCompat.Builder(this, "COUPLE_APP_CHANNEL")
                .setSmallIcon(android.R.drawable.ic_dialog_email) // Replace with your app icon
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            NotificationManagerCompat.from(this).notify(id, builder.build())
        }
    }
}
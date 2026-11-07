package com.example.coupleappgallery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging

// --- 1. THEME ENGINE ---
data class AppTheme(
    val id: String = "forest_green",
    val primary: Color = Color(0xFF2E5948),
    val secondary: Color = Color(0xFF8FBC8F),
    val background: Color = Color(0xFFF4F9F4),
    val surface: Color = Color.White,
    val text: Color = Color(0xFF4A3728),
    val wallpaperUrl: String = ""
)

val DefaultAppTheme = AppTheme(
    id = "forest_green",
    primary = Color(0xFF2E5948),
    secondary = Color(0xFF8FBC8F),
    background = Color(0xFFF4F9F4),
    surface = Color.White,
    text = Color(0xFF4A3728)
)

// PASTEL & AESTHETIC THEME PRESETS
val appThemes = mapOf(
    "forest_green" to DefaultAppTheme,
    "glacier_ice" to AppTheme("glacier_ice", Color(0xFF5D8AA8), Color(0xFFA0C4E3), Color(0xFFF0F8FF), Color(0xFFEBF5FB), Color(0xFF2C3E50)),
    "smoke_grey" to AppTheme("smoke_grey", Color(0xFF607D8B), Color(0xFFB0BEC5), Color(0xFFECEFF1), Color.White, Color(0xFF263238)),
    "starry_night" to AppTheme("starry_night", Color(0xFF3949AB), Color(0xFF7986CB), Color(0xFFE8EAF6), Color.White, Color(0xFF1A237E)),
    "autumn_falls" to AppTheme("autumn_falls", Color(0xFFD84315), Color(0xFFFF8A65), Color(0xFFFBE9E7), Color.White, Color(0xFF3E2723)),
    "ocean_blue" to AppTheme("ocean_blue", Color(0xFF0277BD), Color(0xFF4FC3F7), Color(0xFFE1F5FE), Color.White, Color(0xFF01579B)),
    "ruby_red" to AppTheme("ruby_red", Color(0xFFD32F2F), Color(0xFFE57373), Color(0xFFFFEBEE), Color.White, Color(0xFFB71C1C)),
    "skies_blue" to AppTheme("skies_blue", Color(0xFF42A5F5), Color(0xFF90CAF9), Color(0xFFE3F2FD), Color.White, Color(0xFF0D47A1)),
    "rainbow" to AppTheme("rainbow", Color(0xFFBA68C8), Color(0xFFE1BEE7), Color(0xFFF3E5F5), Color.White, Color(0xFF4A148C)),
    "white" to AppTheme("white", Color(0xFF757575), Color(0xFFBDBDBD), Color(0xFFFAFAFA), Color.White, Color.Black)
)

class MainActivity : ComponentActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Pair(route, uniqueEventId) ensures repeat taps to the same route still trigger navigation
    private var pendingRouteEvent by mutableStateOf<Pair<String, Long>?>(null)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeRouteExtra(intent)
    }

    private fun consumeRouteExtra(intent: Intent?) {
        val route = intent?.getStringExtra("route")
        if (!route.isNullOrEmpty()) {
            intent.removeExtra("route")
            pendingRouteEvent = route to System.nanoTime()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        consumeRouteExtra(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        NotificationChannels.ensureChannel(this)

        // Initialize high-performance dual-tier in-memory & persistent disk cache for Coil
        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
        Coil.setImageLoader(imageLoader)

        // Initialize Firestore high-performance offline persistent cache
        runCatching {
            val firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(100L * 1024 * 1024)
                        .build()
                )
                .build()
            db.firestoreSettings = firestoreSettings
        }

        setContent {
            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            DisposableEffect(currentRoute) {
                NotificationForegroundTracker.activeRoute = currentRoute
                onDispose { }
            }

            var userId by remember { mutableStateOf(auth.currentUser?.uid) }
            DisposableEffect(Unit) {
                val authListener = FirebaseAuth.AuthStateListener { fbAuth ->
                    userId = fbAuth.currentUser?.uid
                }
                auth.addAuthStateListener(authListener)
                onDispose { auth.removeAuthStateListener(authListener) }
            }

            // Initialize process-scoped SessionCache for user + partner profile & theme data
            LaunchedEffect(userId) {
                val uid = userId
                if (uid != null) SessionCache.init(uid) else SessionCache.clear()
            }

            // Route to target screen on both cold-start (once past splash/login) and warm-start taps
            LaunchedEffect(pendingRouteEvent, userId, currentRoute) {
                val event = pendingRouteEvent ?: return@LaunchedEffect
                val uid = userId ?: return@LaunchedEffect
                if (uid.isNotEmpty() && currentRoute != null && currentRoute != "splash" && currentRoute != "login") {
                    val targetRoute = event.first
                    pendingRouteEvent = null
                    if (currentRoute != targetRoute) {
                        navController.navigate(targetRoute) {
                            launchSingleTop = true
                        }
                    }
                }
            }

            // Derive active AppTheme from SessionCache (avoids a duplicate users/{uid} listener)
            val currentTheme = remember(
                userId,
                SessionCache.themeId,
                SessionCache.themePrimary,
                SessionCache.themeSecondary,
                SessionCache.themeBg,
                SessionCache.themeText,
                SessionCache.themeWallpaper
            ) {
                if (userId == null) {
                    DefaultAppTheme
                } else if (SessionCache.themeId == "custom") {
                    val primary = Color(SessionCache.themePrimary)
                    AppTheme(
                        id = "custom",
                        primary = primary,
                        secondary = Color(SessionCache.themeSecondary),
                        background = Color(SessionCache.themeBg),
                        surface = Color.White,
                        text = Color(SessionCache.themeText),
                        wallpaperUrl = SessionCache.themeWallpaper
                    )
                } else {
                    val preset = appThemes[SessionCache.themeId] ?: DefaultAppTheme
                    preset.copy(wallpaperUrl = SessionCache.themeWallpaper)
                }
            }

            // Mark online, register notification listener, and sync FCM token whenever userId becomes active
            DisposableEffect(userId) {
                val uid = userId
                if (uid == null) {
                    return@DisposableEffect onDispose { }
                }

                // Mark user Online immediately on login/sign-up (not just Activity.onStart)
                updateUserStatus("Online", uid)

                val notifReg = createNotificationListener(uid)

                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                        return@addOnCompleteListener
                    }
                    if (auth.currentUser?.uid != uid) return@addOnCompleteListener
                    val token = task.result
                    db.collection("users").document(uid)
                        .set(mapOf("fcmToken" to token), SetOptions.merge())
                        .addOnFailureListener { e ->
                            Log.w("FCM", "Saving FCM token failed", e)
                        }
                }

                onDispose {
                    notifReg.remove()
                }
            }

            NavHost(navController = navController, startDestination = "splash") {
                composable("splash") { SplashScreen(navController, currentTheme) }
                composable("login") { LoginScreen(navController) }
                composable(
                    "home",
                    exitTransition = {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.Start,
                            animationSpec = tween(280, easing = FastOutSlowInEasing)
                        ) { it / 4 } + fadeOut(animationSpec = tween(200))
                    },
                    popEnterTransition = {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(280, easing = FastOutSlowInEasing)
                        ) { it / 4 } + fadeIn(animationSpec = tween(200))
                    }
                ) {
                    HomeScreen(navController, currentTheme)
                }

                composable(
                    "gallery/{folderId}",
                    arguments = listOf(navArgument("folderId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val folderId = backStackEntry.arguments?.getString("folderId") ?: ""
                    GalleryScreen(navController, folderId, currentTheme)
                }

                composable(
                    "chat",
                    enterTransition = {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Start,
                            animationSpec = tween(280, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(200))
                    },
                    exitTransition = {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.Start,
                            animationSpec = tween(280, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(200))
                    },
                    popEnterTransition = {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(280, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(200))
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(280, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(200))
                    }
                ) {
                    ChatScreen(navController, currentTheme)
                }
                composable("profile") { ProfileScreen(navController, currentTheme) }
                composable("settings") { SettingsScreen(navController, currentTheme) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        updateUserStatus("Online")
    }

    override fun onStop() {
        super.onStop()
        NotificationForegroundTracker.activeRoute = null
        updateUserStatus("Offline")
    }

    private fun updateUserStatus(status: String, explicitUid: String? = null) {
        val uid = explicitUid ?: auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .set(mapOf("status" to status), SetOptions.merge())
            .addOnFailureListener { e ->
                Log.w("FS", "updateUserStatus($status) failed", e)
            }
    }

    private fun createNotificationListener(uid: String): ListenerRegistration {
        var isInitialSnapshot = true
        return db.collection("notifications")
            .whereEqualTo("to", uid)
            .whereEqualTo("read", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("NotifListener", "Listener error", error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val addedDocs = snapshot.documentChanges
                    .filter { it.type == DocumentChange.Type.ADDED }
                    .map { it.document }

                if (addedDocs.isEmpty()) {
                    isInitialSnapshot = false
                    return@addSnapshotListener
                }

                if (isInitialSnapshot && addedDocs.size > 3) {
                    // Coalesce offline backlog into a single summary notification instead of spamming banners
                    showSystemNotification(
                        this@MainActivity,
                        "Welcome back 💕",
                        "You have ${addedDocs.size} new updates from your partner!",
                        docId = "summary_${addedDocs.first().id}",
                        route = "home"
                    )
                } else {
                    addedDocs.forEach { doc ->
                        val title = doc.getString("title") ?: "New Activity"
                        val body = doc.getString("body") ?: "Check the app!"
                        showSystemNotification(this@MainActivity, title, body, doc.id)
                    }
                }
                isInitialSnapshot = false

                // Mark unread notifications read in safe 450-op batches
                addedDocs.chunked(450).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { doc -> batch.update(doc.reference, "read", true) }
                    batch.commit().addOnFailureListener { e ->
                        Log.e("NotifListener", "Failed to mark notifications read", e)
                    }
                }
            }
    }
}

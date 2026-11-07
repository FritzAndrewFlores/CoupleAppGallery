package com.example.coupleappgallery

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(navController: NavController, currentTheme: AppTheme) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val storage = remember { FirebaseStorage.getInstance() }
    val userUid = auth.currentUser?.uid ?: ""
    val partnerId = SessionCache.partnerId

    // Theme state
    var selectedThemeId by remember { mutableStateOf(SessionCache.themeId) }
    var customWallpaper by remember { mutableStateOf(SessionCache.themeWallpaper) }
    var chatWallpaper by remember { mutableStateOf(SessionCache.chatWallpaper) }
    var syncWithPartner by remember { mutableStateOf(true) }
    var isUploadingWallpaper by remember { mutableStateOf(false) }
    var isUploadingChatWallpaper by remember { mutableStateOf(false) }

    val initialPrimaryColor = if (SessionCache.themeId == "custom") {
        Color(SessionCache.themePrimary)
    } else {
        (appThemes[SessionCache.themeId] ?: DefaultAppTheme).primary
    }
    var customPrimaryColor by remember { mutableStateOf(initialPrimaryColor) }
    var selectedHue by remember {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialPrimaryColor.toArgb(), hsv)
        mutableFloatStateOf(hsv[0].coerceIn(0f, 359.99f))
    }

    val primaryColor = currentTheme.primary
    val backgroundColor = currentTheme.background
    val surfaceColor = currentTheme.surface
    val textColor = currentTheme.text

    fun applyThemeChanges(themeId: String, primary: Color, wallpaperUrl: String) {
        if (userUid.isEmpty()) return
        scope.launch {
            try {
                val themeFields = mutableMapOf<String, Any>("themeId" to themeId)
                if (themeId == "custom") {
                    val readableText = if (primary.luminance() > 0.65f) Color(0xFF2B2B2B) else primary
                    themeFields["themePrimary"] = primary.toArgb()
                    themeFields["themeSecondary"] = primary.copy(alpha = 0.6f).toArgb()
                    themeFields["themeBg"] = hsvToColor(selectedHue, 0.05f, 0.98f).toArgb()
                    themeFields["themeText"] = readableText.toArgb()
                    themeFields["themeWallpaper"] = wallpaperUrl
                } else {
                    val preset = appThemes[themeId] ?: DefaultAppTheme
                    themeFields["themePrimary"] = preset.primary.toArgb()
                    themeFields["themeSecondary"] = preset.secondary.toArgb()
                    themeFields["themeBg"] = preset.background.toArgb()
                    themeFields["themeText"] = preset.text.toArgb()
                    // Option A: Retain wallpaper without forcing themeId = "custom"
                    themeFields["themeWallpaper"] = wallpaperUrl
                }

                db.collection("users").document(userUid).set(themeFields, SetOptions.merge()).await()

                if (syncWithPartner && partnerId.isNotEmpty()) {
                    runCatching {
                        db.collection("users").document(partnerId).update(themeFields).await()
                    }.onFailure { e ->
                        Log.w("SettingsScreen", "Failed to sync theme with partner", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("SettingsScreen", "Error applying theme", e)
                Toast.makeText(context, "Could not save theme.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val wallpaperLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null && userUid.isNotEmpty()) {
            isUploadingWallpaper = true
            scope.launch {
                try {
                    val wallRef = storage.reference.child("theme_wallpapers/$userUid.jpg")
                    val downloadUrl = uploadCompressedJpeg(context, uri, wallRef)
                    customWallpaper = downloadUrl
                    applyThemeChanges(selectedThemeId, customPrimaryColor, downloadUrl)
                    Toast.makeText(context, "App wallpaper applied! ✨", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("SettingsScreen", "Wallpaper upload failed", e)
                    val errorMsg = when {
                        e.message?.contains("billing account", ignoreCase = true) == true || e.message?.contains("402") == true ->
                            "Storage blocked: Billing account is closed in Firebase Console."
                        else -> "Failed to upload wallpaper: ${e.localizedMessage ?: "Please retry."}"
                    }
                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                } finally {
                    isUploadingWallpaper = false
                }
            }
        }
    }

    val chatWallpaperLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null && userUid.isNotEmpty()) {
            isUploadingChatWallpaper = true
            scope.launch {
                try {
                    val wallRef = storage.reference.child("chat_wallpapers/$userUid.jpg")
                    val downloadUrl = uploadCompressedJpeg(context, uri, wallRef)
                    chatWallpaper = downloadUrl
                    val updates = mapOf("chatWallpaper" to downloadUrl)
                    db.collection("users").document(userUid).set(updates, SetOptions.merge()).await()
                    if (syncWithPartner && partnerId.isNotEmpty()) {
                        runCatching {
                            db.collection("users").document(partnerId).set(updates, SetOptions.merge()).await()
                        }.onFailure { e ->
                            Log.w("SettingsScreen", "Partner chat wallpaper sync failed", e)
                        }
                    }
                    Toast.makeText(context, "Chat wallpaper updated! ✨", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("SettingsScreen", "Chat wallpaper upload failed", e)
                    val errorMsg = when {
                        e.message?.contains("billing account", ignoreCase = true) == true || e.message?.contains("402") == true ->
                            "Storage blocked: Billing account is closed in Firebase Console."
                        else -> "Failed to upload chat wallpaper: ${e.localizedMessage ?: "Please retry."}"
                    }
                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                } finally {
                    isUploadingChatWallpaper = false
                }
            }
        }
    }

    val onRemoveChatWallpaper: () -> Unit = {
        if (userUid.isNotEmpty()) {
            scope.launch {
                try {
                    val updates = mapOf("chatWallpaper" to "")
                    db.collection("users").document(userUid).set(updates, SetOptions.merge()).await()
                    if (syncWithPartner && partnerId.isNotEmpty()) {
                        runCatching {
                            db.collection("users").document(partnerId).set(updates, SetOptions.merge()).await()
                        }
                    }
                    chatWallpaper = ""
                    Toast.makeText(context, "Chat wallpaper removed.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("SettingsScreen", "Error removing chat wallpaper", e)
                    Toast.makeText(context, "Could not remove wallpaper.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("Settings ⚙️", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (currentTheme.id == "custom") Color.White else primaryColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (currentTheme.id == "custom") Color.Black.copy(0.3f) else backgroundColor,
                    titleContentColor = if (currentTheme.id == "custom") Color.White else primaryColor
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---------------------------------------------------------
            // 1. APPEARANCE & THEME CARD
            // ---------------------------------------------------------
            Card(
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                elevation = CardDefaults.cardElevation(2.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = primaryColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Appearance", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textColor)
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Preset Themes", style = MaterialTheme.typography.labelMedium, color = textColor.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(appThemes.keys.toList(), key = { it }) { key ->
                            val theme = appThemes[key] ?: return@items
                            val isSelected = selectedThemeId == key
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable {
                                    selectedThemeId = key
                                    applyThemeChanges(key, theme.primary, customWallpaper)
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(theme.primary)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) primaryColor else Color.LightGray,
                                            shape = CircleShape
                                        )
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.align(Alignment.Center).size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = key.replace('_', ' ').replaceFirstChar { it.uppercase() },
                                    fontSize = 10.sp,
                                    color = textColor
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = textColor.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Custom Theme option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Custom Color Hue", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = textColor)
                            Text("Choose your exact custom primary color", fontSize = 12.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = selectedThemeId == "custom",
                            onCheckedChange = { isCustom ->
                                if (isCustom) {
                                    selectedThemeId = "custom"
                                    applyThemeChanges("custom", customPrimaryColor, customWallpaper)
                                } else {
                                    selectedThemeId = "forest_green"
                                    val defaultTheme = appThemes["forest_green"] ?: DefaultAppTheme
                                    applyThemeChanges("forest_green", defaultTheme.primary, customWallpaper)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = primaryColor
                            )
                        )
                    }

                    AnimatedVisibility(visible = selectedThemeId == "custom") {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            Text("Color Wheel", style = MaterialTheme.typography.labelSmall, color = textColor)
                            Spacer(modifier = Modifier.height(6.dp))
                            HueColorSlider(
                                selectedHue = selectedHue,
                                onHueChanged = { hue ->
                                    val safeHue = hue.coerceIn(0f, 359.99f)
                                    selectedHue = safeHue
                                    customPrimaryColor = hsvToColor(safeHue, 0.7f, 0.5f)
                                    applyThemeChanges("custom", customPrimaryColor, customWallpaper)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = textColor.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Option A: Distinct Wallpaper Management
                    Text("Wallpapers", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = textColor)
                    Spacer(modifier = Modifier.height(8.dp))

                    // 1. Global App Wallpaper
                    Text("App Background Wallpaper", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { wallpaperLauncher.launch("image/*") },
                        enabled = !isUploadingWallpaper,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isUploadingWallpaper) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Image, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (customWallpaper.isEmpty()) "Upload App Wallpaper" else "Change App Wallpaper")
                        }
                    }

                    if (customWallpaper.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("App wallpaper active", fontSize = 12.sp, color = Color.Gray)
                            TextButton(
                                onClick = {
                                    customWallpaper = ""
                                    applyThemeChanges(selectedThemeId, customPrimaryColor, "")
                                }
                            ) {
                                Text("Remove", color = Color.Red, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Chat Screen Wallpaper
                    Text("Chat Screen Wallpaper", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { chatWallpaperLauncher.launch("image/*") },
                        enabled = !isUploadingChatWallpaper,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isUploadingChatWallpaper) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Wallpaper, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (chatWallpaper.isEmpty()) "Upload Chat Wallpaper" else "Change Chat Wallpaper")
                        }
                    }

                    if (chatWallpaper.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Chat wallpaper active", fontSize = 12.sp, color = Color.Gray)
                            TextButton(
                                onClick = { onRemoveChatWallpaper() }
                            ) {
                                Text("Remove", color = Color.Red, fontSize = 12.sp)
                            }
                        }
                    }

                    if (partnerId.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = textColor.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Sync, contentDescription = null, tint = primaryColor, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Sync Theme with Partner", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = textColor)
                                    Text("Updates partner's app theme to match yours", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                            Switch(
                                checked = syncWithPartner,
                                onCheckedChange = { syncWithPartner = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = primaryColor
                                )
                            )
                        }
                    }
                }
            }

            // ---------------------------------------------------------
            // 2. STORAGE & CACHE
            // ---------------------------------------------------------
            Card(
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                elevation = CardDefaults.cardElevation(2.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, tint = primaryColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Media & Storage", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textColor)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Clear temporarily cached pictures and thumbnails to free up device space. Your photos stored in the cloud will stay safe.",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val imageLoader = Coil.imageLoader(context)
                                imageLoader.memoryCache?.clear()
                                imageLoader.diskCache?.clear()
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Local image cache cleared! 🧹", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear Image Cache", color = primaryColor)
                    }
                }
            }

            // ---------------------------------------------------------
            // 3. ABOUT & APP INFO
            // ---------------------------------------------------------
            Card(
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                elevation = CardDefaults.cardElevation(2.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = primaryColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("About", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textColor)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("App Version", fontSize = 14.sp, color = textColor)
                        Text("1.0.0", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Account Signed In", fontSize = 14.sp, color = textColor)
                        Text(
                            text = auth.currentUser?.email?.ifEmpty { "Anonymous" } ?: "Not signed in",
                            fontSize = 13.sp,
                            color = primaryColor
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers & Hue Slider for Custom Theme
// ---------------------------------------------------------------------------
fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val hsv = floatArrayOf(hue.coerceIn(0f, 359.99f), saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f))
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@Composable
fun HueColorSlider(
    selectedHue: Float,
    onHueChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val hueColors = remember {
        listOf(
            Color.Red,
            Color.Yellow,
            Color.Green,
            Color.Cyan,
            Color.Blue,
            Color.Magenta,
            Color.Red
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(hueColors))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 0.999f)
                    onHueChanged(ratio * 360f)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val ratio = (change.position.x / size.width.toFloat()).coerceIn(0f, 0.999f)
                    onHueChanged(ratio * 360f)
                }
            }
    ) {
        val normalizedHue = (selectedHue / 360f).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(24.dp)
                .offset(x = (normalizedHue * 280).dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, Color.DarkGray, CircleShape)
        )
    }
}

package com.example.coupleappgallery

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

// --- DATA MODELS ---
data class UserSummary(val id: String = "", val name: String = "", val email: String = "", val photoUrl: String = "")
data class MoodOption(val emoji: String, val label: String)

// NOTE: appThemes is removed from here because it is already defined in MainActivity.kt or AppTheme.kt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavController, currentTheme: AppTheme) {
    // Pattern D: singletons — retrieve once per composition lifetime
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val storage = remember { FirebaseStorage.getInstance() }
    val user = auth.currentUser
    val context = LocalContext.current

    // User Data
    var displayName by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf("") }
    var currentPartnerId by remember { mutableStateOf("") }
    var partnerName by remember { mutableStateOf("") }
    var myMood by remember { mutableStateOf("😊") }
    var myNote by remember { mutableStateOf("") }

    // Track Changes for History
    var originalMood by remember { mutableStateOf("") }
    var originalNote by remember { mutableStateOf("") }

    // Theme Data
    var selectedThemeId by remember { mutableStateOf(currentTheme.id) }
    var customWallpaper by remember { mutableStateOf(currentTheme.wallpaperUrl) }

    // Advanced Color Picker State
    var customPrimaryColor by remember { mutableStateOf(currentTheme.primary) }
    var selectedHue by remember { mutableFloatStateOf(0f) }

    var isSaving by remember { mutableStateOf(false) }
    var showUserList by remember { mutableStateOf(false) }
    var allUsers by remember { mutableStateOf(listOf<UserSummary>()) }

    // Colors
    val primaryColor = currentTheme.primary
    val backgroundColor = currentTheme.background
    val surfaceColor = currentTheme.surface

    // Pattern C: static list — built once per composition lifetime
    val moods = remember {
        listOf(
            MoodOption("😊", "Happy"), MoodOption("🥰", "Loved"), MoodOption("🥳", "Excited"),
            MoodOption("😎", "Cool"), MoodOption("😴", "Tired"), MoodOption("😤", "Angry"),
            MoodOption("🤯", "Stressed"), MoodOption("🤒", "Sick"), MoodOption("🥺", "Sad"),
            MoodOption("😐", "Neutral")
        )
    }

    // Load Data
    DisposableEffect(Unit) {
        val reg = user?.let {
            db.collection("users").document(it.uid).addSnapshotListener { doc, _ ->
                displayName = doc?.getString("name") ?: ""
                photoUrl = doc?.getString("photoUrl") ?: ""
                currentPartnerId = doc?.getString("partnerId") ?: ""

                val fetchedMood = doc?.getString("mood") ?: "😊"
                val fetchedNote = doc?.getString("loveNote") ?: ""

                myMood = fetchedMood
                myNote = fetchedNote

                if (originalMood.isEmpty()) originalMood = fetchedMood
                if (originalNote.isEmpty()) originalNote = fetchedNote

                if (!isSaving) {
                    selectedThemeId = doc?.getString("themeId") ?: "forest_green"
                    customWallpaper = doc?.getString("themeWallpaper") ?: ""
                    val pColor = doc?.getLong("themePrimary")
                    if (pColor != null) {
                        // Only update custom color picker if we are in custom mode to avoid overwriting user's work
                        if (selectedThemeId == "custom") {
                            customPrimaryColor = Color(pColor)
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(pColor.toInt(), hsv)
                            selectedHue = hsv[0]
                        }
                    }
                }

                if (currentPartnerId.isNotEmpty()) {
                    db.collection("users").document(currentPartnerId).get().addOnSuccessListener { pDoc -> partnerName = pDoc.getString("name") ?: "Partner" }
                }
            }
        }
        onDispose { reg?.remove() }
    }

    LaunchedEffect(showUserList) {
        if (showUserList) {
            db.collection("users").get().addOnSuccessListener { result ->
                val list = mutableListOf<UserSummary>()
                for (doc in result) {
                    if (doc.id != user?.uid) list.add(UserSummary(doc.id, doc.getString("name")?:"No Name", doc.getString("email")?:"", doc.getString("photoUrl")?:""))
                }
                allUsers = list
            }
        }
    }

    // Launchers
    val profilePicLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && user != null) {
            isSaving = true
            val ref = storage.reference.child("profile_pics/${user.uid}.jpg")
            ref.putFile(uri).addOnSuccessListener { ref.downloadUrl.addOnSuccessListener { url ->
                db.collection("users").document(user.uid).update("photoUrl", url.toString()).addOnSuccessListener {
                    photoUrl = url.toString(); isSaving = false; Toast.makeText(context, "Photo Updated!", Toast.LENGTH_SHORT).show()
                    if (currentPartnerId.isNotEmpty()) {
                        sendNotification(db, currentPartnerId, "Photo Updated 📸", "$displayName changed profile pic!")
                    }
                }
            } }
        }
    }

    val wallpaperLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && user != null) {
            isSaving = true
            val ref = storage.reference.child("theme_wallpapers/${user.uid}.jpg")
            ref.putFile(uri).addOnSuccessListener { ref.downloadUrl.addOnSuccessListener { url ->
                customWallpaper = url.toString()
                isSaving = false
                Toast.makeText(context, "Wallpaper Uploaded! Save to apply.", Toast.LENGTH_SHORT).show()
            }}
        }
    }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("My Profile", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = primaryColor, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                // Profile Pic
                Box(modifier = Modifier.size(120.dp).clip(CircleShape).background(Color.LightGray).clickable { profilePicLauncher.launch("image/*") }, contentAlignment = Alignment.Center) {
                    if (photoUrl.isNotEmpty()) AsyncImage(model = photoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(60.dp))
                    if (isSaving) CircularProgressIndicator(color = primaryColor)
                }
                Text("Tap to change", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))

                Spacer(modifier = Modifier.height(24.dp))
                OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Display Name") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, focusedLabelColor = primaryColor))
                Spacer(modifier = Modifier.height(24.dp))

                // --- THEME SELECTOR ---
                Text("App Theme 🎨", fontWeight = FontWeight.Bold, color = primaryColor)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedThemeId == "custom",
                            onClick = { selectedThemeId = "custom" },
                            label = { Text("Custom") },
                            leadingIcon = { Icon(Icons.Default.Image, null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = primaryColor, selectedLabelColor = Color.White)
                        )
                    }
                    items(appThemes.keys.toList(), key = { it }) { themeKey ->
                        val theme = appThemes[themeKey]!!
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(theme.primary)
                                .border(2.dp, if (selectedThemeId == themeKey) Color.Black else Color.Transparent, CircleShape)
                                .clickable { selectedThemeId = themeKey }
                        )
                    }
                }

                // --- ADVANCED COLOR PICKER (Custom Only) ---
                if (selectedThemeId == "custom") {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = surfaceColor), border = BorderStroke(1.dp, primaryColor)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Design Your Theme", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { wallpaperLauncher.launch("image/*") }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = primaryColor)) {
                                Icon(Icons.Default.Image, null); Spacer(modifier = Modifier.width(8.dp)); Text("Upload Wallpaper")
                            }
                            if (customWallpaper.isNotEmpty()) Text("Wallpaper set! ✨", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top=4.dp))

                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Pick Base Color:", fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))

                            // Hue Slider
                            Box(
                                modifier = Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(10.dp))
                                    .background(brush = Brush.horizontalGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                                    .pointerInput(Unit) {
                                        detectTapGestures { offset ->
                                            selectedHue = (offset.x / size.width) * 360f; customPrimaryColor = hsvToColor(selectedHue, 0.8f, 0.9f)
                                        }
                                        detectDragGestures { change, _ ->
                                            selectedHue = (change.position.x / size.width) * 360f; customPrimaryColor = hsvToColor(selectedHue, 0.8f, 0.9f)
                                        }
                                    }
                            )

                            // Shades
                            Spacer(modifier = Modifier.height(12.dp)); Text("Select Shade:", fontSize = 12.sp); Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                // Pattern C: recompute shades only when hue changes
                                val shades = remember(selectedHue) { listOf(hsvToColor(selectedHue, 0.3f, 0.95f), hsvToColor(selectedHue, 0.5f, 0.9f), hsvToColor(selectedHue, 0.8f, 0.9f), hsvToColor(selectedHue, 1.0f, 0.7f), hsvToColor(selectedHue, 1.0f, 0.4f)) }
                                shades.forEach { shade ->
                                    Box(
                                        modifier = Modifier.size(40.dp).clip(CircleShape).background(shade).border(2.dp, if (customPrimaryColor == shade) Color.Black else Color.Transparent, CircleShape).clickable { customPrimaryColor = shade },
                                        contentAlignment = Alignment.Center
                                    ) { if (customPrimaryColor == shade) Icon(Icons.Default.Check, null, tint = if(shade.luminance() > 0.5) Color.Black else Color.White, modifier = Modifier.size(20.dp)) }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Current Mood:", fontWeight = FontWeight.Bold, color = primaryColor)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    items(moods, key = { it.emoji }) { mood ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { myMood = mood.emoji }) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(50.dp).background(if (myMood == mood.emoji) primaryColor.copy(alpha = 0.2f) else Color.Transparent, CircleShape)) { Text(text = mood.emoji, fontSize = 28.sp) }
                            Text(text = mood.label, fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                OutlinedTextField(value = myNote, onValueChange = { myNote = it }, label = { Text("Love Note") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(24.dp))

                if (currentPartnerId.isNotEmpty()) {
                    Text("Linked with $partnerName ❤️", color = primaryColor, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { auth.currentUser?.uid?.let { uid -> db.collection("users").document(uid).update("partnerId", ""); currentPartnerId = "" } }) { Text("Unlink", color = Color.Red) }
                } else {
                    Button(onClick = { showUserList = true }, colors = ButtonDefaults.buttonColors(containerColor = primaryColor)) { Text("Find Partner") }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        user?.let {
                            isSaving = true

                            // Base Data
                            val data = mutableMapOf<String, Any>(
                                "name" to displayName,
                                "mood" to myMood,
                                "loveNote" to myNote,
                                "themeId" to selectedThemeId
                            )

                            // THEME COLOR LOGIC (CRITICAL FIX)
                            if (selectedThemeId == "custom") {
                                // Save Custom Colors
                                data["themePrimary"] = customPrimaryColor.toArgb()
                                data["themeSecondary"] = customPrimaryColor.copy(alpha=0.6f).toArgb()
                                data["themeBg"] = hsvToColor(selectedHue, 0.05f, 0.98f).toArgb()
                                data["themeText"] = customPrimaryColor.toArgb()
                                data["themeWallpaper"] = customWallpaper
                            } else {
                                // FIX: Save Preset Colors Explicitly
                                // This ensures the app updates even if it reads color values from DB
                                val preset = appThemes[selectedThemeId]
                                if (preset != null) {
                                    data["themePrimary"] = preset.primary.toArgb()
                                    data["themeSecondary"] = preset.secondary.toArgb()
                                    data["themeBg"] = preset.background.toArgb()
                                    data["themeText"] = preset.text.toArgb()
                                    data["themeWallpaper"] = "" // Clear wallpaper for preset
                                }
                            }

                            db.collection("users").document(it.uid).update(data)
                                .addOnSuccessListener {
                                    isSaving = false
                                    Toast.makeText(context, "Saved & Synced! 🎨", Toast.LENGTH_SHORT).show()

                                    if (currentPartnerId.isNotEmpty()) {
                                        // A. Mood Change?
                                        if (myMood != originalMood) {
                                            sendNotification(db, currentPartnerId, "Mood Updated $myMood", "$displayName is feeling different!")
                                            originalMood = myMood
                                        }
                                        // B. Note Change?
                                        if (myNote != originalNote) {
                                            sendNotification(db, currentPartnerId, "New Love Note ✏️", "$displayName wrote you a note!")
                                            originalNote = myNote
                                        }
                                        // C. Sync Theme to Partner
                                        val partnerUpdates = mutableMapOf<String, Any>("themeId" to selectedThemeId)
                                        if (selectedThemeId == "custom") {
                                            partnerUpdates["themePrimary"] = customPrimaryColor.toArgb()
                                            partnerUpdates["themeWallpaper"] = customWallpaper
                                        } else {
                                            val preset = appThemes[selectedThemeId]
                                            if (preset != null) partnerUpdates["themePrimary"] = preset.primary.toArgb()
                                        }

                                        db.collection("users").document(currentPartnerId).update(partnerUpdates)
                                        sendNotification(db, currentPartnerId, "Theme Updated 🎨", "$displayName changed the app theme!")
                                    }
                                }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) { Text(if (isSaving) "Saving..." else "Save Changes") }

                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { auth.signOut(); navController.navigate("login") }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.7f)), modifier = Modifier.fillMaxWidth()) { Text("Log Out") }
            }
        }
    }

    if (showUserList) {
        Dialog(onDismissRequest = { showUserList = false }) {
            Card(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                LazyColumn(modifier = Modifier.padding(16.dp)) {
                    items(allUsers, key = { it.id }) { u ->
                        Row(modifier = Modifier.fillMaxWidth().padding(8.dp).clickable { auth.currentUser?.uid?.let { uid -> db.collection("users").document(uid).update("partnerId", u.id); showUserList = false } }, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, null); Spacer(modifier = Modifier.width(8.dp)); Text(u.name, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// Helpers
fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val hsv = floatArrayOf(hue, saturation, value)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

fun Color.luminance(): Float {
    val r = this.red; val g = this.green; val b = this.blue
    return (0.2126f * r + 0.7152f * g + 0.0722f * b)
}
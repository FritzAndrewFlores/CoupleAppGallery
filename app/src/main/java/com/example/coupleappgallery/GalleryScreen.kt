package com.example.coupleappgallery

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.Keep
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FieldValue
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil.Coil
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.PropertyName
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- COLORS ---
val LinedPaperYellow = Color(0xFFFFFDE7) // Creamy Yellow
val InkColor = Color(0xFF3E2723) // Dark Brown Ink

// --- DATA MODELS ---
@Keep
data class GalleryImage(
    val id: String = "",
    val url: String = "",
    val uploaderId: String = "",
    val dateAdded: Long = 0
)

@Keep
data class JournalEntry(
    val id: String = "",
    val title: String = "",
    val titleIcon: String = "📔",
    val mood: String = "😊",
    val text: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val date: Long = 0,
    val prompt: String = ""
)

@Keep
data class PlanItem(
    val id: String = "",
    val task: String = "",
    @field:PropertyName("isChecked")
    @get:PropertyName("isChecked")
    val isChecked: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(navController: NavController, folderId: String, currentTheme: AppTheme) {
    // Pattern D: singletons — retrieve once per composition lifetime
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val myId = auth.currentUser?.uid ?: ""
    var folderName by remember { mutableStateOf("Adventure") }
    var folderParticipants by remember { mutableStateOf(listOf<String>()) }
    var selectedTab by remember { mutableIntStateOf(0) }

    // User Info for Notifications — sourced from SessionCache (single process-scoped listener)
    val partnerId = SessionCache.partnerId
    val myName = SessionCache.userName
    // Only notify partner when this album is actually shared with them
    val notifyPartnerId = if (partnerId.isNotEmpty() && folderParticipants.contains(partnerId)) partnerId else ""

    val primaryColor = currentTheme.primary
    val backgroundColor = currentTheme.background

    DisposableEffect(folderId, myId) {
        if (folderId.isEmpty()) {
            if (navController.currentBackStackEntry?.destination?.route?.startsWith("gallery/") == true) {
                navController.popBackStack()
            }
            return@DisposableEffect onDispose { }
        }
        val reg = db.collection("folders").document(folderId).addSnapshotListener { snap, error ->
            if (error != null) {
                Log.e("FS", "listener@GalleryScreen:folderDoc", error)
                if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED &&
                    navController.currentBackStackEntry?.destination?.route?.startsWith("gallery/") == true
                ) {
                    navController.popBackStack()
                }
                return@addSnapshotListener
            }
            if (snap == null || !snap.exists()) {
                if (navController.currentBackStackEntry?.destination?.route?.startsWith("gallery/") == true) {
                    navController.popBackStack()
                }
                return@addSnapshotListener
            }
            val participants = (snap.get("participants") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            if (myId.isNotEmpty() && !participants.contains(myId)) {
                if (navController.currentBackStackEntry?.destination?.route?.startsWith("gallery/") == true) {
                    navController.popBackStack()
                }
                return@addSnapshotListener
            }
            folderName = snap.getString("name") ?: "Adventure"
            folderParticipants = participants
        }
        onDispose { reg.remove() }
    }

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        if (currentTheme.id == "custom" && currentTheme.wallpaperUrl.isNotEmpty()) {
            AsyncImage(
                model = buildImageRequest(LocalContext.current, currentTheme.wallpaperUrl, cacheKey = "theme_wallpaper"),
                contentDescription = "Background",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text(folderName, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) },
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
                            containerColor = if (currentTheme.id == "custom") Color.Black.copy(0.4f) else backgroundColor,
                            titleContentColor = if (currentTheme.id == "custom") Color.White else primaryColor
                        )
                    )
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = if (currentTheme.id == "custom") Color.Transparent else backgroundColor,
                        contentColor = if (currentTheme.id == "custom") Color.White else primaryColor,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = if (currentTheme.id == "custom") Color.White else primaryColor
                            )
                        }
                    ) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Photos 📸") })
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Journal 📖") })
                        Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Plans ✅") })
                    }
                }
            }
        ) { padding ->
            if (folderId.isNotEmpty()) {
                Box(modifier = Modifier.padding(padding)) {
                    when (selectedTab) {
                        0 -> PhotoSection(folderId, currentTheme, notifyPartnerId, myName, folderName)
                        1 -> JournalSection(folderId, currentTheme, notifyPartnerId, myName, folderName)
                        2 -> PlanSection(folderId, currentTheme, notifyPartnerId, myName, folderName)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// 1. PHOTO SECTION
// ---------------------------------------------------------
@Composable
fun PhotoSection(folderId: String, currentTheme: AppTheme, partnerId: String, myName: String, folderName: String) {
    val db = remember { FirebaseFirestore.getInstance() }
    val storage = remember { FirebaseStorage.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val user = auth.currentUser
    val context = LocalContext.current
    var images by remember { mutableStateOf(listOf<GalleryImage>()) }
    var selectedImage by remember { mutableStateOf<GalleryImage?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && user != null && folderId.isNotEmpty()) {
            isUploading = true
            scope.launch {
                try {
                    val imgRef = db.collection("folders").document(folderId).collection("images").document()
                    val imgId = imgRef.id
                    val base64DataUri = compressToBase64DataUri(context, uri, maxDim = 1200, quality = 75)
                    FolderCoverCache.setCover(folderId, base64DataUri)
                    imgRef.set(GalleryImage(imgId, base64DataUri, user.uid, System.currentTimeMillis())).await()
                    db.collection("folders").document(folderId).update(
                        mapOf(
                            "coverPhotoUrl" to base64DataUri,
                            "photoCount" to FieldValue.increment(1)
                        )
                    ).addOnFailureListener { e -> Log.w("GalleryScreen", "Failed to update folder cover", e) }
                    if (partnerId.isNotEmpty()) {
                        sendNotification(db, partnerId, "New Memory 📸", "$myName added a photo to $folderName")
                    }
                } catch (e: Exception) {
                    Log.e("GalleryScreen", "Photo upload failed", e)
                    Toast.makeText(context, "Photo upload failed: ${e.localizedMessage ?: "Please retry."}", Toast.LENGTH_LONG).show()
                } finally {
                    isUploading = false
                }
            }
        }
    }

    DisposableEffect(folderId) {
        if (folderId.isEmpty()) return@DisposableEffect onDispose { }
        val reg = db.collection("folders").document(folderId).collection("images")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FS", "listener@GalleryScreen:images", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val loaded = snapshot.documents
                        .mapNotNull { doc ->
                            doc.toObject(GalleryImage::class.java)?.let {
                                if (it.id.isEmpty()) it.copy(id = doc.id) else it
                            }
                        }
                        .sortedByDescending { it.dateAdded }
                    images = loaded
                    if (selectedImage != null && loaded.none { it.id == selectedImage?.id }) {
                        selectedImage = null
                    }
                }
            }
        onDispose { reg.remove() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (images.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No photos yet.", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(images, key = { it.id }) { image ->
                    GalleryItem(image, onClick = { selectedImage = image })
                }
            }
        }
        FloatingActionButton(
            onClick = { if (!isUploading) launcher.launch("image/*") },
            containerColor = currentTheme.primary,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            if (isUploading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    }

    selectedImage?.let { activeImage ->
        FullScreenImageDialog(
            imageUrl = activeImage.url,
            onDismiss = { selectedImage = null },
            onDownload = { downloadImage(context, activeImage.url, scope) },
            onDelete = {
                if (activeImage.id.isNotEmpty()) {
                    db.collection("folders").document(folderId).collection("images").document(activeImage.id).delete()
                    val remaining = images.filter { it.id != activeImage.id }
                    val nextCover = remaining.firstOrNull()?.url ?: ""
                    FolderCoverCache.setCover(folderId, nextCover)
                    db.collection("folders").document(folderId).update(
                        mapOf(
                            "photoCount" to remaining.size,
                            "coverPhotoUrl" to nextCover
                        )
                    ).addOnFailureListener { e -> Log.w("GalleryScreen", "Failed to update folder cover on delete", e) }
                    if (activeImage.url.startsWith("http")) {
                        storage.reference.child("folders/$folderId/${activeImage.id}.jpg").delete()
                            .addOnFailureListener { e -> Log.e("GalleryScreen", "Storage delete failed for ${activeImage.id}", e) }
                    }
                }
                if (partnerId.isNotEmpty()) {
                    sendNotification(db, partnerId, "Deleted Memory 🗑️", "$myName removed a photo from $folderName")
                }
                selectedImage = null
            }
        )
    }
}

// ---------------------------------------------------------
// 2. JOURNAL SECTION
// ---------------------------------------------------------
@Composable
fun JournalSection(folderId: String, currentTheme: AppTheme, partnerId: String, myName: String, folderName: String) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    var entries by remember { mutableStateOf(listOf<JournalEntry>()) }
    var showDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<JournalEntry?>(null) }
    var entryToDelete by remember { mutableStateOf<JournalEntry?>(null) }

    DisposableEffect(folderId) {
        if (folderId.isEmpty()) return@DisposableEffect onDispose { }
        val reg = db.collection("folders").document(folderId).collection("journal")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FS", "listener@JournalSection:journal", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    entries = snapshot.documents
                        .mapNotNull { doc ->
                            doc.toObject(JournalEntry::class.java)?.let {
                                if (it.id.isEmpty()) it.copy(id = doc.id) else it
                            }
                        }
                        .sortedByDescending { it.date }
                }
            }
        onDispose { reg.remove() }
    }

    // Pattern B: expensive to construct — hoist out of per-item scope
    val journalDateFmt = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No journal pages yet.", color = Color.Gray)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                items(entries, key = { it.id }) { entry ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = LinedPaperYellow),
                        elevation = CardDefaults.cardElevation(2.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(entry.titleIcon, fontSize = 24.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = entry.title.ifBlank { "Untitled" },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = InkColor,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("Feeling: ${entry.mood}", fontSize = 14.sp, color = InkColor.copy(0.6f))
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            if (entry.prompt.isNotBlank()) {
                                Text(
                                    text = entry.prompt,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = InkColor.copy(0.5f),
                                    fontStyle = FontStyle.Italic
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            Text(text = entry.text, fontFamily = FontFamily.Cursive, fontSize = 22.sp, color = InkColor)
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = journalDateFmt.format(Date(entry.date)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = InkColor.copy(0.5f)
                                    )
                                    Text(
                                        text = "— ${entry.authorName.ifBlank { "Anonymous" }}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = InkColor
                                    )
                                }
                                Row {
                                    IconButton(onClick = { editingEntry = entry; showDialog = true }) {
                                        Icon(Icons.Default.Edit, "Edit", tint = InkColor.copy(0.7f))
                                    }
                                    IconButton(onClick = { entryToDelete = entry }) {
                                        Icon(Icons.Default.Delete, "Delete", tint = Color.Red.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { editingEntry = null; showDialog = true },
            containerColor = currentTheme.primary,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Add, "Write")
        }
    }

    if (showDialog) {
        val currentEditing = editingEntry
        var title by remember(currentEditing?.id) { mutableStateOf(currentEditing?.title ?: "") }
        var selectedIcon by remember(currentEditing?.id) { mutableStateOf(currentEditing?.titleIcon ?: "📔") }
        var selectedMood by remember(currentEditing?.id) { mutableStateOf(currentEditing?.mood ?: "😊") }
        var promptText by remember(currentEditing?.id) {
            mutableStateOf(currentEditing?.prompt ?: "What's your memory with this place?")
        }
        var bodyText by remember(currentEditing?.id) { mutableStateOf(currentEditing?.text ?: "") }
        var signedBy by remember(currentEditing?.id) { mutableStateOf(currentEditing?.authorName ?: myName) }
        val icons = JournalIcons
        val moods = JournalMoods

        val inkFieldColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = InkColor,
            unfocusedBorderColor = InkColor.copy(alpha = 0.4f),
            focusedLabelColor = InkColor,
            unfocusedLabelColor = InkColor.copy(alpha = 0.7f),
            focusedTextColor = InkColor,
            unfocusedTextColor = InkColor,
            cursorColor = InkColor
        )

        Dialog(onDismissRequest = { showDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = LinedPaperYellow),
                modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (currentEditing == null) "New Page 🖋️" else "Edit Page ✏️",
                        fontWeight = FontWeight.Bold,
                        color = InkColor,
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(selectedIcon, fontSize = 28.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Title") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = inkFieldColors
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(icons, key = { it }) { icon ->
                            Text(
                                text = icon,
                                fontSize = 24.sp,
                                modifier = Modifier
                                    .background(
                                        if (selectedIcon == icon) InkColor.copy(alpha = 0.2f) else Color.Transparent,
                                        CircleShape
                                    )
                                    .padding(4.dp)
                                    .clickable { selectedIcon = icon }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(moods, key = { it }) { mood ->
                            Text(
                                text = mood,
                                fontSize = 24.sp,
                                modifier = Modifier
                                    .background(
                                        if (selectedMood == mood) InkColor.copy(alpha = 0.2f) else Color.Transparent,
                                        CircleShape
                                    )
                                    .padding(4.dp)
                                    .clickable { selectedMood = mood }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        label = { Text("Prompt") },
                        textStyle = LocalTextStyle.current.copy(
                            fontStyle = FontStyle.Italic,
                            color = InkColor.copy(0.8f)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        colors = inkFieldColors
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = bodyText,
                        onValueChange = { bodyText = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = InkColor.copy(alpha = 0.03f),
                            unfocusedContainerColor = InkColor.copy(alpha = 0.03f),
                            focusedBorderColor = InkColor.copy(alpha = 0.3f),
                            unfocusedBorderColor = InkColor.copy(alpha = 0.15f),
                            focusedTextColor = InkColor,
                            unfocusedTextColor = InkColor,
                            cursorColor = InkColor
                        ),
                        placeholder = {
                            Text("Write here...", fontFamily = FontFamily.Serif, color = InkColor.copy(0.5f))
                        },
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Serif,
                            fontSize = 16.sp,
                            color = InkColor
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = signedBy,
                        onValueChange = { signedBy = it },
                        label = { Text("Signed By") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = inkFieldColors
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showDialog = false }) { Text("Cancel", color = InkColor) }
                        Button(
                            onClick = {
                                val trimmedBody = bodyText.trim()
                                if (trimmedBody.isNotEmpty() && folderId.isNotEmpty()) {
                                    val id = currentEditing?.id?.takeIf { it.isNotEmpty() }
                                        ?: db.collection("folders").document(folderId).collection("journal").document().id
                                    val entry = JournalEntry(
                                        id = id,
                                        title = title.trim(),
                                        titleIcon = selectedIcon,
                                        mood = selectedMood,
                                        text = trimmedBody,
                                        authorId = currentEditing?.authorId ?: (auth.currentUser?.uid ?: ""),
                                        authorName = signedBy.trim().ifEmpty { myName },
                                        date = currentEditing?.date ?: System.currentTimeMillis(),
                                        prompt = promptText.trim()
                                    )
                                    db.collection("folders").document(folderId).collection("journal").document(id).set(entry)

                                    if (partnerId.isNotEmpty() && currentEditing == null) {
                                        sendNotification(db, partnerId, "New Journal Entry 📖", "$myName wrote in $folderName")
                                    }
                                    showDialog = false
                                }
                            },
                            enabled = bodyText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = InkColor)
                        ) {
                            Text("Save Page", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    entryToDelete?.let { target ->
        AlertDialog(
            containerColor = LinedPaperYellow,
            onDismissRequest = { entryToDelete = null },
            title = { Text("Delete Journal Page?", fontWeight = FontWeight.Bold, color = InkColor) },
            text = {
                Text(
                    "Are you sure you want to delete '${target.title.ifBlank { "Untitled" }}'? This cannot be undone.",
                    color = InkColor
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (target.id.isNotEmpty() && folderId.isNotEmpty()) {
                            db.collection("folders").document(folderId).collection("journal").document(target.id).delete()
                        }
                        entryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text("Cancel", color = InkColor)
                }
            }
        )
    }
}

// ---------------------------------------------------------
// 3. PLAN SECTION
// ---------------------------------------------------------
@Composable
fun PlanSection(folderId: String, currentTheme: AppTheme, partnerId: String, myName: String, folderName: String) {
    val db = remember { FirebaseFirestore.getInstance() }
    var plans by remember { mutableStateOf(listOf<PlanItem>()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var planToDelete by remember { mutableStateOf<PlanItem?>(null) }
    val haptic = LocalHapticFeedback.current

    DisposableEffect(folderId) {
        if (folderId.isEmpty()) return@DisposableEffect onDispose { }
        val reg = db.collection("folders").document(folderId).collection("plans")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FS", "listener@PlanSection:plans", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    plans = snapshot.documents
                        .mapNotNull { doc ->
                            doc.toObject(PlanItem::class.java)?.let {
                                if (it.id.isEmpty()) it.copy(id = doc.id) else it
                            }
                        }
                        .sortedBy { it.isChecked }
                }
            }
        onDispose { reg.remove() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (plans.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No plans added yet.", color = Color.Gray)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                items(plans, key = { it.id }) { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (item.id.isNotEmpty()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    db.collection("folders").document(folderId).collection("plans")
                                        .document(item.id).update("isChecked", !item.isChecked)
                                }
                            }
                            .padding(8.dp)
                    ) {
                        Checkbox(
                            checked = item.isChecked,
                            onCheckedChange = { checked ->
                                if (item.id.isNotEmpty()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    db.collection("folders").document(folderId).collection("plans")
                                        .document(item.id).update("isChecked", checked)
                                }
                            },
                            colors = CheckboxDefaults.colors(checkedColor = currentTheme.primary)
                        )
                        Text(
                            text = item.task,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                            fontFamily = FontFamily.Serif,
                            color = if (item.isChecked) Color.Gray else Color.Black,
                            textDecoration = if (item.isChecked) TextDecoration.LineThrough else null
                        )
                        IconButton(onClick = { planToDelete = item }) {
                            Icon(Icons.Default.Delete, "Delete", tint = Color.LightGray)
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = currentTheme.primary,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Add, "Add")
        }
    }

    planToDelete?.let { target ->
        AlertDialog(
            containerColor = currentTheme.surface,
            onDismissRequest = { planToDelete = null },
            title = { Text("Delete Task?", color = currentTheme.text, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '${target.task}'?", color = currentTheme.text) },
            confirmButton = {
                Button(
                    onClick = {
                        if (target.id.isNotEmpty() && folderId.isNotEmpty()) {
                            db.collection("folders").document(folderId).collection("plans").document(target.id).delete()
                        }
                        planToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { planToDelete = null }) {
                    Text("Cancel", color = currentTheme.text)
                }
            }
        )
    }

    if (showAddDialog) {
        var task by remember { mutableStateOf("") }
        val planTextColor = if (currentTheme.id == "custom") Color(0xFF1F1F1F) else currentTheme.text
        AlertDialog(
            containerColor = currentTheme.surface,
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Plan", color = planTextColor, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = task,
                    onValueChange = { task = it },
                    label = { Text("To Do...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = currentTheme.primary,
                        focusedLabelColor = currentTheme.primary,
                        focusedTextColor = planTextColor,
                        unfocusedTextColor = planTextColor
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = task.trim()
                        if (trimmed.isNotEmpty() && folderId.isNotEmpty()) {
                            val ref = db.collection("folders").document(folderId).collection("plans").document()
                            ref.set(PlanItem(ref.id, trimmed, false))
                            if (partnerId.isNotEmpty()) {
                                sendNotification(db, partnerId, "New Plan Added ✅", "$myName added a task to $folderName")
                            }
                            showAddDialog = false
                        }
                    },
                    enabled = task.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = currentTheme.primary)
                ) {
                    Text("Add", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = planTextColor)
                }
            }
        )
    }
}

// ---------------------------------------------------------
// HELPER FUNCTIONS
// ---------------------------------------------------------
@Composable
fun GalleryItem(image: GalleryImage, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable { onClick() }
    ) {
        AsyncImage(
            model = buildImageRequest(LocalContext.current, image.url, cacheKey = "gallery_${image.id}"),
            contentDescription = "Memory",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun FullScreenImageDialog(
    imageUrl: String,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onDownload()
        } else {
            Toast.makeText(context, "Storage permission is needed to save photos.", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = buildImageRequest(LocalContext.current, imageUrl),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().align(Alignment.Center)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopStart),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
                IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Default.Delete, "Delete", tint = Color.White) }
            }
            Box(modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)) {
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            onDownload()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save", color = Color.White)
                }
            }

            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text("Delete Photo") },
                    text = { Text("Are you sure you want to permanently delete this photo?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteConfirm = false
                                onDelete()
                            }
                        ) {
                            Text("Delete", color = Color.Red)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

fun downloadImage(context: Context, url: String, scope: CoroutineScope) {
    val appContext = context.applicationContext
    scope.launch(Dispatchers.IO) {
        try {
            val loader = Coil.imageLoader(appContext)
            val resolvedModel = resolveImageModel(url)
            val request = ImageRequest.Builder(appContext)
                .data(resolvedModel)
                .allowHardware(false)
                .build()
            val drawable = (loader.execute(request) as? SuccessResult)?.drawable
            val bitmap = (drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                val savedMessage = saveBitmapToGallery(appContext, bitmap)
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, savedMessage, Toast.LENGTH_SHORT).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "Download failed", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("GalleryScreen", "downloadImage failed", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(appContext, "Failed to save image", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun saveBitmapToGallery(context: Context, bitmap: Bitmap): String {
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return "Storage permission required on Android 9 and below"
        }
    }

    val filename = "CoupleApp_${System.currentTimeMillis()}.jpg"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }

    val resolver = context.contentResolver
    val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        ?: return "Failed to create gallery entry"

    return try {
        val compressed = resolver.openOutputStream(imageUri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        } ?: false

        if (!compressed) {
            runCatching { resolver.delete(imageUri, null, null) }
            "Failed to save image"
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val readyValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(imageUri, readyValues, null, null)
            }
            "Saved! 🖼️"
        }
    } catch (e: Exception) {
        Log.e("GalleryScreen", "saveBitmapToGallery failed", e)
        runCatching { resolver.delete(imageUri, null, null) }
        "Failed to save image"
    }
}

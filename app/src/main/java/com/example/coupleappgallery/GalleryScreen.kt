package com.example.coupleappgallery

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- COLORS ---
val LinedPaperYellow = Color(0xFFFFFDE7) // Creamy Yellow
val InkColor = Color(0xFF3E2723) // Dark Brown Ink

// --- DATA MODELS ---
data class GalleryImage(val id: String = "", val url: String = "", val uploaderId: String = "", val dateAdded: Long = 0)
data class JournalEntry(val id: String = "", val title: String = "", val titleIcon: String = "📔", val mood: String = "😊", val text: String = "", val authorId: String = "", val authorName: String = "", val date: Long = 0, val prompt: String = "")
data class PlanItem(val id: String = "", val task: String = "", val isChecked: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(navController: NavController, folderId: String, currentTheme: AppTheme) {
    // Pattern D: singletons — retrieve once per composition lifetime
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    var folderName by remember { mutableStateOf("Adventure") }
    var selectedTab by remember { mutableStateOf(0) }

    // User Info for Notifications
    var partnerId by remember { mutableStateOf("") }
    var myName by remember { mutableStateOf("Me") }

    val primaryColor = currentTheme.primary
    val backgroundColor = currentTheme.background

    LaunchedEffect(folderId) {
        // Get Folder Name
        db.collection("folders").document(folderId).get().addOnSuccessListener {
            folderName = it.getString("name") ?: "Adventure"
        }
        // Get User Info
        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).get().addOnSuccessListener {
                partnerId = it.getString("partnerId") ?: ""
                myName = it.getString("name") ?: "Me"
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        if (currentTheme.id == "custom" && currentTheme.wallpaperUrl.isNotEmpty()) {
            AsyncImage(model = currentTheme.wallpaperUrl, contentDescription = "Background", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text(folderName, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) },
                        navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = if (currentTheme.id == "custom") Color.White else primaryColor) } },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = if (currentTheme.id == "custom") Color.Black.copy(0.4f) else backgroundColor, titleContentColor = if (currentTheme.id == "custom") Color.White else primaryColor)
                    )
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = if (currentTheme.id == "custom") Color.Transparent else backgroundColor,
                        contentColor = if (currentTheme.id == "custom") Color.White else primaryColor,
                        indicator = { tabPositions -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[selectedTab]), color = if (currentTheme.id == "custom") Color.White else primaryColor) }
                    ) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Photos 📸") })
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Journal 📖") })
                        Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Plans ✅") })
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (selectedTab) {
                    0 -> PhotoSection(folderId, currentTheme, partnerId, myName, folderName)
                    1 -> JournalSection(folderId, currentTheme, partnerId, myName, folderName)
                    2 -> PlanSection(folderId, currentTheme, partnerId, myName, folderName)
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
    val db = FirebaseFirestore.getInstance()
    val storage = FirebaseStorage.getInstance()
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser
    val context = LocalContext.current
    var images by remember { mutableStateOf(listOf<GalleryImage>()) }
    var selectedImage by remember { mutableStateOf<GalleryImage?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && user != null) {
            isUploading = true
            val imgId = System.currentTimeMillis().toString()
            val ref = storage.reference.child("folders/$folderId/$imgId.jpg")
            ref.putFile(uri).addOnSuccessListener { ref.downloadUrl.addOnSuccessListener { downloadUrl ->
                db.collection("folders").document(folderId).collection("images").document(imgId).set(GalleryImage(imgId, downloadUrl.toString(), user.uid, System.currentTimeMillis()))

                // NOTIFY PARTNER
                if (partnerId.isNotEmpty()) {
                    sendNotification(db, partnerId, "New Memory 📸", "$myName added a photo to $folderName")
                }

                isUploading = false
            }}
        }
    }

    val scope = rememberCoroutineScope()
    DisposableEffect(folderId) {
        val reg = db.collection("folders").document(folderId).collection("images").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) images = snapshot.documents.mapNotNull { it.toObject(GalleryImage::class.java) }.sortedByDescending { it.dateAdded }
        }
        onDispose { reg.remove() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (images.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No photos yet.", color = Color.Gray) }
        else {
            LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(16.dp), modifier = Modifier.fillMaxSize()) {
                items(images, key = { it.id }) { image -> GalleryItem(image, onClick = { selectedImage = image }) }
            }
        }
        FloatingActionButton(onClick = { launcher.launch("image/*") }, containerColor = currentTheme.primary, contentColor = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            if (isUploading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp)) else Icon(Icons.Default.Add, contentDescription = "Add")
        }
    }
    if (selectedImage != null) FullScreenImageDialog(
        imageUrl = selectedImage!!.url,
        onDismiss = { selectedImage = null },
        onDownload = { downloadImage(context, selectedImage!!.url, scope) },
        onDelete = {
            db.collection("folders").document(folderId).collection("images").document(selectedImage!!.id).delete()
            // NOTIFY PARTNER (DELETE)
            if (partnerId.isNotEmpty()) {
                sendNotification(db, partnerId, "Deleted Memory 🗑️", "$myName removed a photo from $folderName")
            }
            selectedImage = null
        }
    )
}

// ---------------------------------------------------------
// 2. JOURNAL SECTION
// ---------------------------------------------------------
@Composable
fun JournalSection(folderId: String, currentTheme: AppTheme, partnerId: String, myName: String, folderName: String) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    var entries by remember { mutableStateOf(listOf<JournalEntry>()) }
    var showDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<JournalEntry?>(null) }

    DisposableEffect(folderId) {
        val reg = db.collection("folders").document(folderId).collection("journal").addSnapshotListener { snapshot, _ -> if (snapshot != null) entries = snapshot.documents.mapNotNull { it.toObject(JournalEntry::class.java) }.sortedByDescending { it.date } }
        onDispose { reg.remove() }
    }

    // Pattern B: expensive to construct — hoist out of per-item scope
    val journalDateFmt = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            items(entries, key = { it.id }) { entry ->
                Card(colors = CardDefaults.cardColors(containerColor = LinedPaperYellow), elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.titleIcon, fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if(entry.title.isNotEmpty()) entry.title else "Untitled", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = InkColor, modifier = Modifier.weight(1f))
                            Text("Feeling: ${entry.mood}", fontSize = 14.sp, color = InkColor.copy(0.6f))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        if (entry.prompt.isNotEmpty()) { Text(text = entry.prompt, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = InkColor.copy(0.5f), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic); Spacer(modifier = Modifier.height(6.dp)) }
                        Text(text = entry.text, fontFamily = FontFamily.Cursive, fontSize = 22.sp, color = InkColor)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column { Text(text = journalDateFmt.format(Date(entry.date)), style = MaterialTheme.typography.labelSmall, color = InkColor.copy(0.5f)); Text(text = "— ${entry.authorName}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = InkColor) }
                            Row { IconButton(onClick = { editingEntry = entry; showDialog = true }) { Icon(Icons.Default.Edit, "Edit", tint = InkColor.copy(0.7f)) }; IconButton(onClick = { db.collection("folders").document(folderId).collection("journal").document(entry.id).delete() }) { Icon(Icons.Default.Delete, "Delete", tint = Color.Red.copy(alpha = 0.4f)) } }
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { editingEntry = null; showDialog = true }, containerColor = currentTheme.primary, contentColor = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Icon(Icons.Default.Add, "Write") }
    }

    if (showDialog) {
        var title by remember { mutableStateOf(editingEntry?.title ?: "") }
        var selectedIcon by remember { mutableStateOf(editingEntry?.titleIcon ?: "📔") }
        var selectedMood by remember { mutableStateOf(editingEntry?.mood ?: "😊") }
        var promptText by remember { mutableStateOf(editingEntry?.prompt ?: "What's your memory with this place?") }
        var bodyText by remember { mutableStateOf(editingEntry?.text ?: "") }
        var signedBy by remember { mutableStateOf(editingEntry?.authorName ?: myName) }
        // Pattern C: static lists — built once per dialog composition
        val icons = remember { listOf("📔", "🌟", "✈️", "🍽️", "❤️", "🍷", "🏖️", "👶", "🏠") }; val moods = remember { listOf("😊", "🥰", "😂", "🤩", "😴", "😭", "😡") }

        Dialog(onDismissRequest = { showDialog = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = LinedPaperYellow), modifier = Modifier.fillMaxWidth().height(650.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(if (editingEntry == null) "New Page 🖋️" else "Edit Page ✏️", fontWeight = FontWeight.Bold, color = InkColor, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(selectedIcon, fontSize = 28.sp); Spacer(modifier = Modifier.width(8.dp)); OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.weight(1f), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = InkColor, focusedLabelColor = InkColor, focusedTextColor = InkColor)) }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(icons, key = { it }) { icon -> Text(text = icon, fontSize = 24.sp, modifier = Modifier.background(if(selectedIcon == icon) InkColor.copy(alpha=0.2f) else Color.Transparent, CircleShape).padding(4.dp).clickable { selectedIcon = icon }) } }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(moods, key = { it }) { mood -> Text(text = mood, fontSize = 24.sp, modifier = Modifier.background(if(selectedMood == mood) InkColor.copy(alpha=0.2f) else Color.Transparent, CircleShape).padding(4.dp).clickable { selectedMood = mood }) } }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(value = promptText, onValueChange = { promptText = it }, label = { Text("Prompt") }, textStyle = LocalTextStyle.current.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = InkColor.copy(0.7f)), modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = InkColor, focusedLabelColor = InkColor))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = bodyText, onValueChange = { bodyText = it }, modifier = Modifier.weight(1f).fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent), placeholder = { Text("Write here...", fontFamily = FontFamily.Serif, color = InkColor.copy(0.5f)) }, textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Serif, fontSize = 16.sp, color = InkColor))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = signedBy, onValueChange = { signedBy = it }, label = { Text("Signed By") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = InkColor, focusedLabelColor = InkColor))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showDialog = false }) { Text("Cancel", color = InkColor) }
                        Button(onClick = {
                            if (bodyText.isNotEmpty()) {
                                val id = editingEntry?.id ?: System.currentTimeMillis().toString()
                                db.collection("folders").document(folderId).collection("journal").document(id).set(JournalEntry(id, title, selectedIcon, selectedMood, bodyText, editingEntry?.authorId ?: (auth.currentUser?.uid ?: ""), signedBy, editingEntry?.date ?: System.currentTimeMillis(), promptText))

                                // NOTIFY PARTNER
                                if (partnerId.isNotEmpty() && editingEntry == null) {
                                    sendNotification(db, partnerId, "New Journal Entry 📖", "$myName wrote in $folderName")
                                }
                                showDialog = false
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = InkColor)) { Text("Save Page", color = Color.White) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// 3. PLAN SECTION
// ---------------------------------------------------------
@Composable
fun PlanSection(folderId: String, currentTheme: AppTheme, partnerId: String, myName: String, folderName: String) {
    val db = FirebaseFirestore.getInstance()
    var plans by remember { mutableStateOf(listOf<PlanItem>()) }
    var showAddDialog by remember { mutableStateOf(false) }

    DisposableEffect(folderId) {
        val reg = db.collection("folders").document(folderId).collection("plans").addSnapshotListener { snapshot, _ -> if (snapshot != null) plans = snapshot.documents.mapNotNull { it.toObject(PlanItem::class.java) }.sortedBy { it.isChecked } }
        onDispose { reg.remove() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            items(plans, key = { it.id }) { item ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(Color.White.copy(alpha=0.9f), RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).clickable { db.collection("folders").document(folderId).collection("plans").document(item.id).update("isChecked", !item.isChecked) }.padding(8.dp)) {
                    Checkbox(checked = item.isChecked, onCheckedChange = { db.collection("folders").document(folderId).collection("plans").document(item.id).update("isChecked", it) }, colors = CheckboxDefaults.colors(checkedColor = currentTheme.primary))
                    Text(text = item.task, modifier = Modifier.weight(1f).padding(start = 8.dp), fontFamily = FontFamily.Serif, color = if (item.isChecked) Color.Gray else Color.Black, textDecoration = if (item.isChecked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                    IconButton(onClick = { db.collection("folders").document(folderId).collection("plans").document(item.id).delete() }) { Icon(Icons.Default.Delete, "Delete", tint = Color.LightGray) }
                }
            }
        }
        FloatingActionButton(onClick = { showAddDialog = true }, containerColor = currentTheme.primary, contentColor = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Icon(Icons.Default.Add, "Add") }
    }

    if (showAddDialog) {
        var task by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showAddDialog = false }, title = { Text("New Plan") }, text = { OutlinedTextField(value = task, onValueChange = { task = it }, label = { Text("To Do...") }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = currentTheme.primary, focusedLabelColor = currentTheme.primary)) }, confirmButton = { Button(onClick = {
            if (task.isNotEmpty()) {
                val id = System.currentTimeMillis().toString()
                db.collection("folders").document(folderId).collection("plans").document(id).set(PlanItem(id, task, false))
                // NOTIFY PARTNER
                if (partnerId.isNotEmpty()) {
                    sendNotification(db, partnerId, "New Plan Added ✅", "$myName added a task to $folderName")
                }
                showAddDialog = false
            }
        }, colors = ButtonDefaults.buttonColors(containerColor = currentTheme.primary)) { Text("Add") } }, dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel", color = currentTheme.text) } })
    }
}

// ---------------------------------------------------------
// HELPER FUNCTIONS
// ---------------------------------------------------------
@Composable
fun GalleryItem(image: GalleryImage, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable { onClick() }) {
        AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(image.url).crossfade(true).build(), contentDescription = "Memory", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun FullScreenImageDialog(imageUrl: String, onDismiss: () -> Unit, onDownload: () -> Unit, onDelete: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().align(Alignment.Center))
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopStart), horizontalArrangement = Arrangement.SpaceBetween) { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = Color.White) }; IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Color.White) } }
            Box(modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)) { Button(onClick = onDownload, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))) { Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White); Spacer(modifier = Modifier.width(8.dp)); Text("Save", color = Color.White) } }
        }
    }
}

fun downloadImage(context: Context, url: String, scope: CoroutineScope) {
    scope.launch(Dispatchers.IO) {
        val loader = ImageLoader(context); val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
        val result = (loader.execute(request) as? SuccessResult)?.drawable; val bitmap = (result as? BitmapDrawable)?.bitmap
        if (bitmap != null) saveBitmapToGallery(context, bitmap, scope)
    }
}

fun saveBitmapToGallery(context: Context, bitmap: Bitmap, scope: CoroutineScope) {
    val filename = "CoupleApp_${System.currentTimeMillis()}.jpg"
    val contentValues = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, filename); put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg"); put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES) }
    val resolver = context.contentResolver; val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    imageUri?.let { resolver.openOutputStream(it) }?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it); scope.launch(Dispatchers.Main) { Toast.makeText(context, "Saved! 🖼️", Toast.LENGTH_SHORT).show() } }
}
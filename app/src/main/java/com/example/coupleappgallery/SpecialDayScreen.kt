package com.example.coupleappgallery

import android.app.DatePickerDialog
import android.net.Uri
import android.widget.DatePicker
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue

// --- DATA MODEL ---
data class SpecialDay(
    val id: String = "",
    val title: String = "",
    val date: Long = 0,
    val icon: String = "📅",
    val participants: List<String> = emptyList(),
    val color: Long = 0xFFFFF8E7, // Default Cream
    val backgroundUrl: String = "" // Optional background image
)

// --- COLORS PALETTE ---
val EventColors = listOf(
    Color(0xFFFFF8E7), // Cream
    Color(0xFFF8BBD0), // Pink
    Color(0xFFE1BEE7), // Lavender
    Color(0xFFC5CAE9), // Periwinkle
    Color(0xFFB2DFDB), // Teal
    Color(0xFFC8E6C9), // Green
    Color(0xFFFFF9C4), // Yellow
    Color(0xFFFFCCBC)  // Peach
)

// ==========================================
// SCREEN 1: THE LIST
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecialDaysScreen(navController: NavController, currentTheme: AppTheme) {
    var events by remember { mutableStateOf(listOf<SpecialDay>()) }
    var showAddDialog by remember { mutableStateOf(false) }

    // Pattern D: singletons — retrieve once per composition lifetime
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val myId = auth.currentUser?.uid ?: ""
    val partnerId = SessionCache.partnerId
    val myName = SessionCache.userName

    val primaryColor = currentTheme.primary
    val backgroundColor = currentTheme.background

    DisposableEffect(myId) {
        if (myId.isEmpty()) return@DisposableEffect onDispose { }
        // partnerId and myName sourced from SessionCache — no per-screen user fetch needed
        val reg = db.collection("special_days")
            .whereArrayContains("participants", myId)
            .addSnapshotListener { snapshot: QuerySnapshot?, _: FirebaseFirestoreException? ->
                if (snapshot != null) {
                    events = snapshot.documents.mapNotNull { it.toObject(SpecialDay::class.java) }
                        .sortedBy { it.date }
                }
            }
        onDispose { reg.remove() }
    }

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        if (currentTheme.id == "custom" && currentTheme.wallpaperUrl.isNotEmpty()) {
            AsyncImage(
                model = currentTheme.wallpaperUrl,
                contentDescription = "Background",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Special Days 📅", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = if (currentTheme.id == "custom") Color.White else primaryColor)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (currentTheme.id == "custom") Color.Black.copy(0.4f) else backgroundColor,
                        titleContentColor = if (currentTheme.id == "custom") Color.White else primaryColor
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showAddDialog = true }, containerColor = primaryColor, contentColor = Color.White) {
                    Icon(Icons.Default.Add, contentDescription = "Add Event")
                }
            }
        ) { padding ->
            LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
                items(events, key = { it.id }) { event ->
                    EventItem(
                        event = event,
                        db = db,
                        currentTheme = currentTheme,
                        onClick = { navController.navigate("special_day_detail/${event.id}") },
                        onDelete = {
                            db.collection("special_days").document(event.id).delete()
                            if (partnerId.isNotEmpty()) {
                                sendNotification(db, partnerId, "Deleted Event 🗑️", "$myName removed event: ${event.title}")
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        EventDialog(
            title = "New Memory",
            initialName = "",
            initialIcon = "📅",
            initialDate = System.currentTimeMillis(),
            currentTheme = currentTheme,
            onDismiss = { showAddDialog = false },
            onSave = { title, date, icon, color, bgUrl ->
                val id = System.currentTimeMillis().toString()
                val validParticipants = listOfNotNull(myId, partnerId.ifEmpty { null })
                val newEvent = SpecialDay(id, title, date, icon, validParticipants, color, bgUrl)
                db.collection("special_days").document(id).set(newEvent)

                if (partnerId.isNotEmpty()) {
                    sendNotification(db, partnerId, "New Special Day 🎉", "$myName added event: $title")
                }
                showAddDialog = false
            }
        )
    }
}

// --- ITEM COMPONENT (UPDATED VISUALS) ---
@Composable
fun EventItem(event: SpecialDay, db: FirebaseFirestore, currentTheme: AppTheme, onClick: () -> Unit, onDelete: () -> Unit) {
    val diff = event.date - System.currentTimeMillis()
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    val displayText = when {
        days > 0 -> "$days days until"
        days < 0 -> "${days.absoluteValue} days since"
        else -> "Today is the day! 🎉"
    }

    // Determine Colors
    val hasBackground = event.backgroundUrl.isNotEmpty()
    val cardColor = if (hasBackground) Color.Transparent else Color(event.color)

    // Text Color: White if background image exists, otherwise calculated contrast
    val contentColor = if (hasBackground) Color.White else if (Color(event.color).luminance() < 0.5f) Color.White else currentTheme.primary

    var plansPreview by remember { mutableStateOf(listOf<String>()) }

    DisposableEffect(event.id) {
        val reg = db.collection("special_days").document(event.id).collection("plans").limit(3).addSnapshotListener { snap: QuerySnapshot?, _: FirebaseFirestoreException? ->
            if (snap != null) plansPreview = snap.documents.mapNotNull { it.getString("task") }
        }
        onDispose { reg.remove() }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = if (hasBackground) Color.White else cardColor), // White container for image loading
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(120.dp).clickable { onClick() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Background Image Layer
            if (hasBackground) {
                AsyncImage(
                    model = event.backgroundUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Scrim for readability
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
            } else {
                // Solid Color Layer (Applied via Card container, but ensures consistency)
                Box(modifier = Modifier.fillMaxSize().background(cardColor))
            }

            // 2. Content Layer
            Row(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = event.icon, fontSize = 40.sp)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(event.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = contentColor)
                    Text(displayText, style = MaterialTheme.typography.bodyMedium, color = contentColor.copy(alpha = 0.8f))

                    if (plansPreview.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("• " + plansPreview.joinToString(", "), maxLines = 1, fontSize = 10.sp, color = contentColor.copy(alpha = 0.7f))
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = contentColor.copy(alpha = 0.7f))
                }
            }
        }
    }
}

// ==========================================
// SCREEN 2: THE DETAIL VIEW
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecialDayDetailScreen(navController: NavController, eventId: String, currentTheme: AppTheme) {
    // Pattern D: singleton — retrieve once per composition lifetime
    val db = remember { FirebaseFirestore.getInstance() }
    var event by remember { mutableStateOf<SpecialDay?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var showEditDialog by remember { mutableStateOf(false) }

    val primaryColor = currentTheme.primary
    val backgroundColor = currentTheme.background

    DisposableEffect(eventId) {
        val reg = db.collection("special_days").document(eventId).addSnapshotListener { snap, _ ->
            event = snap?.toObject(SpecialDay::class.java)
        }
        onDispose { reg.remove() }
    }

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        if (currentTheme.id == "custom" && currentTheme.wallpaperUrl.isNotEmpty()) {
            AsyncImage(model = currentTheme.wallpaperUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text(event?.title ?: "Loading...", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = if(currentTheme.id=="custom") Color.White else primaryColor) }
                        },
                        actions = {
                            IconButton(onClick = { showEditDialog = true }) {
                                Icon(Icons.Default.Edit, "Edit", tint = if(currentTheme.id=="custom") Color.White else primaryColor)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = if(currentTheme.id=="custom") Color.Black.copy(0.4f) else backgroundColor,
                            titleContentColor = if(currentTheme.id=="custom") Color.White else primaryColor
                        )
                    )
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = if(currentTheme.id=="custom") Color.Transparent else backgroundColor,
                        contentColor = if(currentTheme.id=="custom") Color.White else primaryColor,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[selectedTab]), color = if(currentTheme.id=="custom") Color.White else primaryColor)
                        }
                    ) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Overview") })
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Plans ✅") })
                        Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Memories 📸") })
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                if (event != null) {
                    when (selectedTab) {
                        0 -> OverviewTab(event!!, currentTheme)
                        1 -> EventPlanTab(eventId, currentTheme)
                        2 -> EventPhotoTab(eventId, currentTheme)
                    }
                }
            }
        }
    }

    if (showEditDialog && event != null) {
        EventDialog(
            title = "Edit Event",
            initialName = event!!.title,
            initialIcon = event!!.icon,
            initialDate = event!!.date,
            initialColor = event!!.color,
            initialBgUrl = event!!.backgroundUrl,
            currentTheme = currentTheme,
            onDismiss = { showEditDialog = false },
            onSave = { title, date, icon, color, bgUrl ->
                db.collection("special_days").document(eventId).update(mapOf("title" to title, "date" to date, "icon" to icon, "color" to color, "backgroundUrl" to bgUrl))
                showEditDialog = false
            }
        )
    }
}

// --- TABS ---

@Composable
fun OverviewTab(event: SpecialDay, currentTheme: AppTheme) {
    // Pattern B: expensive to construct — hoist out of recomposition hot path
    val overviewDateFmt = remember { SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()) }
    val diff = event.date - System.currentTimeMillis()
    val days = TimeUnit.MILLISECONDS.toDays(diff)

    // Dynamic styles based on event customization
    val hasBg = event.backgroundUrl.isNotEmpty()
    val textColor = if (hasBg) Color.White else currentTheme.primary

    Box(modifier = Modifier.fillMaxSize()) {
        // Full screen background image if available
        if (hasBg) {
            AsyncImage(model = event.backgroundUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        }

        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(event.icon, fontSize = 100.sp)
            Spacer(modifier = Modifier.height(24.dp))
            Text(event.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = if(hasBg) Color.White else if(currentTheme.id=="custom") Color.White else currentTheme.primary)
            Spacer(modifier = Modifier.height(32.dp))

            Card(colors = CardDefaults.cardColors(containerColor = if(hasBg) Color.White.copy(0.2f) else currentTheme.surface.copy(alpha=0.9f)), elevation = CardDefaults.cardElevation(4.dp)) {
                Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (days > 0) {
                        Text("$days", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, color = if(hasBg) Color.White else currentTheme.primary)
                        Text("DAYS LEFT", style = MaterialTheme.typography.labelLarge, color = if(hasBg) Color.White.copy(0.8f) else Color.Gray)
                    } else if (days < 0) {
                        Text("${days.absoluteValue}", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, color = if(hasBg) Color.White else currentTheme.primary)
                        Text("DAYS SINCE", style = MaterialTheme.typography.labelLarge, color = if(hasBg) Color.White.copy(0.8f) else Color.Gray)
                    } else {
                        Text("TODAY!", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = Color(0xFFFFEB3B))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(overviewDateFmt.format(Date(event.date)), color = if(hasBg) Color.White.copy(0.8f) else Color.Gray)
                }
            }
        }
    }
}

// (EventPlanTab and EventPhotoTab remain mostly unchanged, just ensured imports are clean)
@Composable
fun EventPlanTab(eventId: String, currentTheme: AppTheme) {
    val db = FirebaseFirestore.getInstance()
    var plans by remember { mutableStateOf(listOf<PlanItem>()) }
    var showAddDialog by remember { mutableStateOf(false) }

    DisposableEffect(eventId) {
        val reg = db.collection("special_days").document(eventId).collection("plans")
            .addSnapshotListener { snapshot: QuerySnapshot?, _: FirebaseFirestoreException? ->
                if (snapshot != null) plans = snapshot.documents.mapNotNull { it.toObject(PlanItem::class.java) }.sortedBy { it.isChecked }
            }
        onDispose { reg.remove() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            items(plans, key = { it.id }) { item ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(Color.White.copy(alpha=0.9f), RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)).padding(8.dp).clickable { db.collection("special_days").document(eventId).collection("plans").document(item.id).update("isChecked", !item.isChecked) }) {
                    Checkbox(checked = item.isChecked, onCheckedChange = { db.collection("special_days").document(eventId).collection("plans").document(item.id).update("isChecked", it) }, colors = CheckboxDefaults.colors(checkedColor = currentTheme.primary))
                    Text(text = item.task, modifier = Modifier.weight(1f).padding(start = 8.dp), fontFamily = FontFamily.Serif, color = if (item.isChecked) Color.Gray else Color.Black, textDecoration = if (item.isChecked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                    IconButton(onClick = { db.collection("special_days").document(eventId).collection("plans").document(item.id).delete() }) { Icon(Icons.Default.Delete, "Delete", tint = Color.LightGray) }
                }
            }
        }
        FloatingActionButton(onClick = { showAddDialog = true }, containerColor = currentTheme.primary, contentColor = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Icon(Icons.Default.Add, "Add") }
    }

    if (showAddDialog) {
        var task by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showAddDialog = false }, title = { Text("Plan Item", color = currentTheme.text) }, text = { OutlinedTextField(value = task, onValueChange = { task = it }, label = { Text("To do...") }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = currentTheme.primary, focusedLabelColor = currentTheme.primary)) }, confirmButton = { Button(onClick = { if (task.isNotEmpty()) { val id = System.currentTimeMillis().toString(); db.collection("special_days").document(eventId).collection("plans").document(id).set(PlanItem(id, task, false)); showAddDialog = false } }, colors = ButtonDefaults.buttonColors(containerColor = currentTheme.primary)) { Text("Add") } }, dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel", color = currentTheme.text) } })
    }
}

@Composable
fun EventPhotoTab(eventId: String, currentTheme: AppTheme) {
    val db = FirebaseFirestore.getInstance()
    val storage = FirebaseStorage.getInstance()
    var images by remember { mutableStateOf(listOf<GalleryImage>()) }
    var isUploading by remember { mutableStateOf(false) }
    var selectedImage by remember { mutableStateOf<GalleryImage?>(null) }
    val context = LocalContext.current

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            isUploading = true
            val imgId = System.currentTimeMillis().toString()
            val ref = storage.reference.child("special_days/$eventId/$imgId.jpg")
            ref.putFile(uri).addOnSuccessListener { ref.downloadUrl.addOnSuccessListener { downloadUrl ->
                db.collection("special_days").document(eventId).collection("images").document(imgId).set(GalleryImage(imgId, downloadUrl.toString(), "", System.currentTimeMillis()))
                isUploading = false
            }}
        }
    }

    val scope = rememberCoroutineScope()
    DisposableEffect(eventId) {
        val reg = db.collection("special_days").document(eventId).collection("images")
            .addSnapshotListener { snapshot: QuerySnapshot?, e: FirebaseFirestoreException? ->
                if (e != null) return@addSnapshotListener
                if (snapshot != null) { images = snapshot.documents.mapNotNull { it.toObject(GalleryImage::class.java) }.sortedByDescending { it.dateAdded } }
            }
        onDispose { reg.remove() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (images.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No photos yet.", color = Color.Gray) }
        else {
            LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(images, key = { it.id }) { image -> Card(modifier = Modifier.aspectRatio(1f).clickable { selectedImage = image }, shape = RoundedCornerShape(12.dp)) { AsyncImage(model = image.url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) } }
            }
        }
        FloatingActionButton(onClick = { launcher.launch("image/*") }, containerColor = currentTheme.primary, contentColor = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            if (isUploading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp)) else Icon(Icons.Default.Add, "Add")
        }
    }

    if (selectedImage != null) {
        FullScreenImageDialog(imageUrl = selectedImage!!.url, onDismiss = { selectedImage = null }, onDownload = { downloadImage(context, selectedImage!!.url, scope) }, onDelete = { db.collection("special_days").document(eventId).collection("images").document(selectedImage!!.id).delete(); selectedImage = null })
    }
}

// --- UPDATED EVENT DIALOG ---
@Composable
fun EventDialog(
    title: String,
    initialName: String,
    initialIcon: String,
    initialDate: Long,
    initialColor: Long = EventColors[0].toArgb().toLong(),
    initialBgUrl: String = "",
    currentTheme: AppTheme,
    onDismiss: () -> Unit,
    onSave: (String, Long, String, Long, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedIcon by remember { mutableStateOf(initialIcon) }
    var selectedDate by remember { mutableStateOf(initialDate) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var uploadedBgUrl by remember { mutableStateOf(initialBgUrl) }
    var isUploading by remember { mutableStateOf(false) }

    var dateText by remember { mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(initialDate))) }
    val context = LocalContext.current
    val calendar = Calendar.getInstance().apply { timeInMillis = initialDate }
    val datePickerDialog = DatePickerDialog(context, { _, y, m, d -> calendar.set(y, m, d); selectedDate = calendar.timeInMillis; dateText = "$d/${m + 1}/$y" }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
    // Pattern C: static list — built once per dialog composition
    val icons = remember { listOf("📅", "🎂", "💍", "❤️", "🏡", "✈️", "👶", "🎉", "🎓", "🏥", "🐶", "🚗") }

    // Image Upload Logic
    val storage = FirebaseStorage.getInstance()
    val auth = FirebaseAuth.getInstance()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            isUploading = true
            val userId = auth.currentUser?.uid ?: "unknown"
            val ref = storage.reference.child("event_bgs/${userId}_${System.currentTimeMillis()}.jpg")
            ref.putFile(uri).addOnSuccessListener { ref.downloadUrl.addOnSuccessListener { url ->
                uploadedBgUrl = url.toString()
                isUploading = false
            }}
        }
    }

    AlertDialog(
        containerColor = currentTheme.surface,
        onDismissRequest = onDismiss,
        title = { Text(title, color = currentTheme.text) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Event Name") }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = currentTheme.primary, focusedLabelColor = currentTheme.primary))
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { datePickerDialog.show() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = currentTheme.primary.copy(alpha=0.1f))) { Text(dateText, color = currentTheme.primary) }
                Spacer(modifier = Modifier.height(16.dp))

                // Color Picker
                Text("Pick a Color 🎨", fontSize = 14.sp, color = currentTheme.text)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(EventColors, key = { it.value.toString() }) { color ->
                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(color).border(2.dp, if(selectedColor == color.toArgb().toLong()) currentTheme.primary else Color.Transparent, CircleShape).clickable { selectedColor = color.toArgb().toLong() }) {
                            if (selectedColor == color.toArgb().toLong()) Icon(Icons.Default.Check, null, tint = Color.Black.copy(0.5f), modifier = Modifier.align(Alignment.Center).size(20.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Background Upload
                Button(onClick = { launcher.launch("image/*") }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = currentTheme.primary)) {
                    if(isUploading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    else { Icon(Icons.Default.Image, null); Spacer(modifier = Modifier.width(8.dp)); Text(if(uploadedBgUrl.isEmpty()) "Upload Background Photo" else "Change Photo") }
                }
                if (uploadedBgUrl.isNotEmpty()) Text("Photo attached! 🖼️", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top=4.dp))

                Spacer(modifier = Modifier.height(16.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(6), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(icons, key = { it }) { icon -> Text(icon, fontSize = 24.sp, modifier = Modifier.clickable { selectedIcon = icon }.background(if (selectedIcon == icon) currentTheme.primary.copy(alpha=0.3f) else Color.Transparent, CircleShape).padding(4.dp)) }
                }
            }
        },
        confirmButton = { Button(onClick = { if (name.isNotEmpty()) onSave(name, selectedDate, selectedIcon, selectedColor, uploadedBgUrl) }, colors = ButtonDefaults.buttonColors(containerColor = currentTheme.primary)) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = currentTheme.text) } }
    )
}
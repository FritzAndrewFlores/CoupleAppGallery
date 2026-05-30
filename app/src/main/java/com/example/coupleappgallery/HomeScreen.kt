package com.example.coupleappgallery

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.random.Random

// --- DATA MODELS ---
data class Reaction(val emoji: String, val label: String)
data class ActivityItem(val id: String, val title: String, val body: String, val timestamp: Long)
data class PartnerRequest(val id: String = "", val requesterId: String = "", val emoji: String = "", val label: String = "", val timestamp: Long = 0)
enum class HomeCardType { MOOD, REQUESTS, NOTE, WISHLIST }
data class Particle(val id: Long, val emoji: String, val startX: Float)

// --- EXTENDED CARD STYLES ENGINE ---
data class CardBackground(val id: String, val name: String, val colors: List<Color>, val darkText: Boolean)

val availableCardBackgrounds = listOf(
    // Glassmorphism
    CardBackground("glass_light", "Clear Glass", listOf(Color.White.copy(alpha=0.6f), Color.White.copy(alpha=0.6f)), true),
    CardBackground("glass_dark", "Dark Glass", listOf(Color.Black.copy(alpha=0.6f), Color.Black.copy(alpha=0.6f)), false),

    // Pastels
    CardBackground("pastel_pink", "Pastel Pink", listOf(Color(0xFFFFD1DC), Color(0xFFFFD1DC)), true),
    CardBackground("pastel_orange", "Pastel Orange", listOf(Color(0xFFFFDFBA), Color(0xFFFFDFBA)), true),
    CardBackground("pastel_yellow", "Pastel Yellow", listOf(Color(0xFFFFFFBA), Color(0xFFFFFFBA)), true),
    CardBackground("pastel_green", "Pastel Green", listOf(Color(0xFFBAFFC9), Color(0xFFBAFFC9)), true),
    CardBackground("pastel_blue", "Pastel Blue", listOf(Color(0xFFBAE1FF), Color(0xFFBAE1FF)), true),
    CardBackground("pastel_purple", "Pastel Purple", listOf(Color(0xFFD0BFFF), Color(0xFFD0BFFF)), true),
    CardBackground("pastel_peach", "Pastel Peach", listOf(Color(0xFFFFCCB6), Color(0xFFFFCCB6)), true),
    CardBackground("pastel_mint", "Pastel Mint", listOf(Color(0xFFA2E4B8), Color(0xFFA2E4B8)), true),

    // Solids
    CardBackground("solid_crimson", "Crimson", listOf(Color(0xFFDC143C), Color(0xFFDC143C)), false),
    CardBackground("solid_coral", "Coral", listOf(Color(0xFFFF7F50), Color(0xFFFF7F50)), true),
    CardBackground("solid_gold", "Gold", listOf(Color(0xFFFFD700), Color(0xFFFFD700)), true),
    CardBackground("solid_forest", "Forest", listOf(Color(0xFF228B22), Color(0xFF228B22)), false),
    CardBackground("solid_teal", "Teal", listOf(Color(0xFF008080), Color(0xFF008080)), false),
    CardBackground("solid_navy", "Navy", listOf(Color(0xFF000080), Color(0xFF000080)), false),
    CardBackground("solid_indigo", "Indigo", listOf(Color(0xFF4B0082), Color(0xFF4B0082)), false),
    CardBackground("solid_plum", "Plum", listOf(Color(0xFFDDA0DD), Color(0xFFDDA0DD)), true),
    CardBackground("solid_slate", "Slate", listOf(Color(0xFF708090), Color(0xFF708090)), false),

    // Gradients
    CardBackground("grad_romantic", "Romantic", listOf(Color(0xFF8E24AA), Color(0xFFE91E63), Color(0xFFFF5252)), false),
    CardBackground("grad_sunset", "Sunset", listOf(Color(0xFFFF512F), Color(0xFFF09819)), true),
    CardBackground("grad_ocean", "Ocean", listOf(Color(0xFF2193b0), Color(0xFF6dd5ed)), false),
    CardBackground("grad_aurora", "Aurora", listOf(Color(0xFF00c6ff), Color(0xFF0072ff)), false),
    CardBackground("grad_mango", "Mango", listOf(Color(0xFFFFB88C), Color(0xFFDE6262)), true),
    CardBackground("grad_dusk", "Dusk", listOf(Color(0xFF2C3E50), Color(0xFF3498DB)), false),
    CardBackground("grad_vice", "Miami Vice", listOf(Color(0xFF00B4DB), Color(0xFF0083B0)), false),
    CardBackground("grad_berry", "Berry", listOf(Color(0xFFbc4e9c), Color(0xFFf80759)), false),
    CardBackground("grad_lush", "Lush", listOf(Color(0xFF56AB2F), Color(0xFFA8E063)), true),
    CardBackground("grad_rose", "Rose Water", listOf(Color(0xFFE55D87), Color(0xFF5FC3E4)), false),
    CardBackground("grad_fire", "Fire", listOf(Color(0xFFf46b45), Color(0xFFeea849)), true),
    CardBackground("grad_neon", "Neon", listOf(Color(0xFF00C9FF), Color(0xFF92FE9D)), true),
    CardBackground("grad_space", "Space", listOf(Color(0xFF536976), Color(0xFF292E49)), false),
    CardBackground("grad_magic", "Magic", listOf(Color(0xFFC33764), Color(0xFF1D2671)), false),
    CardBackground("grad_candy", "Candy", listOf(Color(0xFFD38312), Color(0xFFA83279)), false),
    CardBackground("grad_deep", "Deep Space", listOf(Color(0xFF000000), Color(0xFF434343)), false)
)

val FolderColors = listOf(Color(0xFFFFF8E7), Color(0xFFF8BBD0), Color(0xFFE1BEE7), Color(0xFFC5CAE9), Color(0xFFB2DFDB), Color(0xFFC8E6C9), Color(0xFFFFF9C4), Color(0xFFFFCCBC))

fun Modifier.glassCard(opacity: Float = 0.6f) = this
    .clip(RoundedCornerShape(24.dp))
    .background(Color.White.copy(alpha = opacity))
    .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(24.dp))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, currentTheme: AppTheme) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val folders = remember { mutableStateListOf<CoupleFolder>() }

    // UI State
    var showDialog by remember { mutableStateOf(false) }
    var folderToEdit by remember { mutableStateOf<CoupleFolder?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showColorPickerFor by remember { mutableStateOf<HomeCardType?>(null) }
    var showHistorySheet by remember { mutableStateOf(false) }

    // User Data — sourced from SessionCache (single process-scoped listener)
    val profilePicUrl = SessionCache.userPhotoUrl
    val myName = SessionCache.userName
    val partnerMood = SessionCache.partnerMood
    val partnerNote = SessionCache.partnerLoveNote
    val partnerName = SessionCache.partnerName
    val partnerStatus = SessionCache.partnerStatus
    var unreadCount by remember { mutableIntStateOf(0) }
    var historyItems by remember { mutableStateOf(listOf<ActivityItem>()) }
    var activeRequests by remember { mutableStateOf(listOf<PartnerRequest>()) }

    // Card Colors — sourced from SessionCache
    val moodBgId = SessionCache.moodBgId
    val requestBgId = SessionCache.requestBgId
    val noteBgId = SessionCache.noteBgId
    val wishlistBgId = SessionCache.wishlistBgId

    // Pattern A: cache list-find results; only re-search when the bg-id state changes
    val moodBg = remember(moodBgId) { availableCardBackgrounds.find { it.id == moodBgId } ?: availableCardBackgrounds[0] }
    val requestBg = remember(requestBgId) { availableCardBackgrounds.find { it.id == requestBgId } ?: availableCardBackgrounds[0] }
    val noteBg = remember(noteBgId) { availableCardBackgrounds.find { it.id == noteBgId } ?: availableCardBackgrounds[0] }
    val wishlistBg = remember(wishlistBgId) { availableCardBackgrounds.find { it.id == wishlistBgId } ?: availableCardBackgrounds[0] }

    // Particle Animation State
    val particles = remember { mutableStateListOf<Particle>() }
    val triggerParticles: (String) -> Unit = { emoji ->
        coroutineScope.launch {
            val newParticles = (0..4).map { Particle(System.currentTimeMillis() + it, emoji, Random.nextFloat()) }
            particles.addAll(newParticles)
            delay(2000)
            particles.removeAll(newParticles)
        }
    }

    val cardOrder = remember { mutableStateListOf(HomeCardType.WISHLIST, HomeCardType.MOOD, HomeCardType.REQUESTS, HomeCardType.NOTE) }
    val expandedStates = remember { mutableStateMapOf(HomeCardType.MOOD to true, HomeCardType.REQUESTS to true, HomeCardType.NOTE to true, HomeCardType.WISHLIST to true) }

    // Pattern D: singletons — retrieve once per composition lifetime
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val userId = auth.currentUser?.uid ?: ""
    val partnerId = SessionCache.partnerId

    val primaryColor = currentTheme.primary
    val backgroundColor = currentTheme.background
    val surfaceColor = currentTheme.surface
    val textColor = currentTheme.text

    // Pattern C: static lists — built once per composition lifetime
    val reactions = remember { listOf(Reaction("🫂", "Hug"), Reaction("💋", "Kiss"), Reaction("🧸", "Cuddle"), Reaction("❤️", "Love You"), Reaction("🫶", "Heart"), Reaction("😍", "Adore"), Reaction("💆", "Massage"), Reaction("👐", "Care"), Reaction("👂", "Listening"), Reaction("🥺", "Aww"), Reaction("💪", "You Got This"), Reaction("🛡️", "Safe"), Reaction("😂", "Laugh"), Reaction("🔥", "Hot"), Reaction("🎉", "Yay!"), Reaction("😎", "Cool"), Reaction("🥳", "Party"), Reaction("💃", "Dance"), Reaction("☕", "Coffee?"), Reaction("🍵", "Tea?"), Reaction("🍫", "Choco"), Reaction("🍦", "Treat"), Reaction("🍷", "Wine"), Reaction("🍕", "Food"), Reaction("📞", "Call Me"), Reaction("👀", "Look"), Reaction("👍", "Yes"), Reaction("👎", "No"), Reaction("🤫", "Shh"), Reaction("👏", "Clap"), Reaction("🌹", "Flowers"), Reaction("✨", "Magic"), Reaction("💤", "Rest")) }
    val requestOptions = remember { listOf(Reaction("💋", "Kiss"), Reaction("🫂", "Hug"), Reaction("🧸", "Cuddle"), Reaction("💆", "Massage"), Reaction("🤝", "Hold Hand"), Reaction("💆‍♂️", "Head Rub"), Reaction("🦷", "Bite"), Reaction("🛌", "Lay Down"), Reaction("📅", "Date Night"), Reaction("🍿", "Movie"), Reaction("🚶", "Walk"), Reaction("🚗", "Drive"), Reaction("🗣️", "Talk"), Reaction("🎮", "Game"), Reaction("✈️", "Trip"), Reaction("🏞️", "Park"), Reaction("🏖️", "Beach"), Reaction("☕", "Coffee"), Reaction("🧋", "Boba"), Reaction("🍕", "Pizza"), Reaction("🍔", "Burger"), Reaction("🍟", "Fries"), Reaction("🍣", "Sushi"), Reaction("🍦", "Ice Cream"), Reaction("🍩", "Donut"), Reaction("🍫", "Chocolate"), Reaction("🍹", "Drink"), Reaction("🍗", "Chicken"), Reaction("🥞", "Brunch"), Reaction("🥺", "Attention"), Reaction("👂", "Listen"), Reaction("🤫", "Quiet"), Reaction("😴", "Sleep"), Reaction("💤", "Nap"), Reaction("🚿", "Shower"), Reaction("🆘", "Help"), Reaction("💊", "Meds"), Reaction("💨", "Space"), Reaction("🛍️", "Shop"), Reaction("🎁", "Gift"), Reaction("🌹", "Flowers"), Reaction("💅", "Nails"), Reaction("💸", "Spoil Me")) }

    val permissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        createNotificationChannel(context)
    }

    DisposableEffect(userId) {
        if (userId.isEmpty()) return@DisposableEffect onDispose { }
        val reg = db.collection("requests").whereEqualTo("receiverId", userId).addSnapshotListener { snapshot, _ -> if (snapshot != null) { activeRequests = snapshot.documents.mapNotNull { it.toObject(PartnerRequest::class.java) } } }
        onDispose { reg.remove() }
    }
    DisposableEffect(userId) {
        if (userId.isEmpty()) return@DisposableEffect onDispose { }
        val reg = db.collection("chats").whereEqualTo("receiverId", userId).whereEqualTo("isRead", false).addSnapshotListener { s, _ -> if (s != null) unreadCount = s.size() }
        onDispose { reg.remove() }
    }
    DisposableEffect(userId) {
        if (userId.isEmpty()) return@DisposableEffect onDispose { }
        val reg = db.collection("notifications").whereEqualTo("to", userId).addSnapshotListener { s, _ -> if (s != null) { historyItems = s.documents.map { ActivityItem(it.id, it.getString("title")?:"", it.getString("body")?:"", it.getLong("timestamp")?:0L) }.sortedByDescending { it.timestamp } } }
        onDispose { reg.remove() }
    }
    DisposableEffect(userId) {
        if (userId.isEmpty()) return@DisposableEffect onDispose { }
        val reg = db.collection("folders").whereArrayContains("participants", userId).addSnapshotListener { s, _ -> if (s != null) { folders.clear(); folders.addAll(s.documents.mapNotNull { it.toObject(CoupleFolder::class.java) }) } }
        onDispose { reg.remove() }
    }

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        if (currentTheme.id == "custom" && currentTheme.wallpaperUrl.isNotEmpty()) {
            AsyncImage(model = currentTheme.wallpaperUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
        }

        particles.forEach { particle -> FloatingParticle(particle) }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text("Couple Sync", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                    val pulseAlpha by infiniteTransition.animateFloat(initialValue = 0.4f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse), label = "pulseAlpha")
                                    val statusColor = if (partnerStatus == "Online") Color.Green else Color.Gray
                                    Box(modifier = Modifier.size(8.dp).alpha(if(partnerStatus == "Online") pulseAlpha else 1f).background(statusColor, CircleShape))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "$partnerName is $partnerStatus", fontSize = 12.sp, color = if (currentTheme.id == "custom") Color.White.copy(0.8f) else textColor.copy(0.7f))
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = if (currentTheme.id == "custom") Color.Black.copy(0.3f) else backgroundColor.copy(alpha=0.9f), titleContentColor = if (currentTheme.id == "custom") Color.White else primaryColor),
                        actions = {
                            IconButton(onClick = { showHistorySheet = true }) { Icon(Icons.Default.Notifications, "History", tint = if (currentTheme.id == "custom") Color.White else primaryColor) }
                            IconButton(onClick = { navController.navigate("special_days") }) { Icon(Icons.Default.DateRange, "Calendar", tint = if (currentTheme.id == "custom") Color.White else primaryColor) }
                            IconButton(onClick = { navController.navigate("chat") }) {
                                if (unreadCount > 0) BadgedBox(badge = { Badge(containerColor = Color.Red, contentColor = Color.White) { Text("$unreadCount") } }) { Icon(Icons.Default.Email, "Chat", tint = if (currentTheme.id == "custom") Color.White else primaryColor) }
                                else Icon(Icons.Default.Email, "Chat", tint = if (currentTheme.id == "custom") Color.White else primaryColor)
                            }
                            IconButton(onClick = { navController.navigate("profile") }) {
                                if (profilePicUrl.isNotEmpty()) AsyncImage(model = profilePicUrl, contentDescription = "Profile", contentScale = ContentScale.Crop, modifier = Modifier.size(28.dp).clip(CircleShape))
                                else Icon(Icons.Default.Person, "Profile", tint = if (currentTheme.id == "custom") Color.White else primaryColor)
                            }
                        }
                    )
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = if (currentTheme.id == "custom") Color.Transparent else backgroundColor.copy(alpha=0.9f),
                        contentColor = if (currentTheme.id == "custom") Color.White else primaryColor,
                        indicator = { tabPositions -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[selectedTab]), color = if (currentTheme.id == "custom") Color.White else primaryColor) }
                    ) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Albums", fontWeight = FontWeight.Bold) })
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Together 💌", fontWeight = FontWeight.Bold) })
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { folderToEdit = null; showDialog = true }, containerColor = primaryColor, contentColor = surfaceColor) { Icon(Icons.Default.Add, "Add Folder") }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (selectedTab == 0) {
                    // ALBUMS TAB: folder grid only
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(folders.chunked(2), key = { it.first().id }) { rowFolders ->
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                for (folder in rowFolders) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        FolderItem(
                                            folder = folder, currentTheme = currentTheme,
                                            onDelete = { db.collection("folders").document(folder.id).delete(); if (partnerId.isNotEmpty()) sendNotification(db, partnerId, "Deleted Item 🗑️", "$myName deleted folder: ${folder.name}") },
                                            onEdit = { folderToEdit = folder; showDialog = true },
                                            onClick = { navController.navigate("gallery/${folder.id}") }
                                        )
                                    }
                                }
                                if (rowFolders.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        item { Spacer(modifier = Modifier.height(60.dp)) }
                    }
                } else {
                    // TOGETHER TAB: your-status editor on top, then mood / wants / note / wishlist cards
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item { YourStatusCard(currentTheme, db, context) }
                        items(cardOrder, key = { it }) { cardType ->
                            if (cardType == HomeCardType.WISHLIST && activeRequests.isEmpty()) return@items

                            val currentCardBg = when (cardType) {
                                HomeCardType.MOOD -> moodBg
                                HomeCardType.REQUESTS -> requestBg
                                HomeCardType.NOTE -> noteBg
                                HomeCardType.WISHLIST -> wishlistBg
                            }

                            DraggableCardWrapper(
                                type = cardType,
                                isExpanded = expandedStates[cardType] ?: true,
                                onExpandToggle = { expandedStates[cardType] = !(expandedStates[cardType] ?: true) },
                                onMove = { fromIndex, toIndex ->
                                    val from = cardOrder.indexOf(cardType)
                                    val to = (from + toIndex).coerceIn(0, cardOrder.size - 1)
                                    if (from != to) cardOrder.add(to, cardOrder.removeAt(from))
                                },
                                onColorPickClick = { showColorPickerFor = cardType },
                                bg = currentCardBg
                            ) {
                                when (cardType) {
                                    HomeCardType.MOOD -> MoodCard(partnerMood, partnerName, reactions, currentTheme, myName, partnerId, db, context, triggerParticles, moodBg)
                                    HomeCardType.REQUESTS -> RequestCard(requestOptions, partnerId, myName, db, context, triggerParticles, requestBg)
                                    HomeCardType.NOTE -> NoteCard(partnerNote, partnerName, noteBg)
                                    HomeCardType.WISHLIST -> WishlistCard(activeRequests, partnerName, partnerId, myName, db, primaryColor, surfaceColor, triggerParticles, wishlistBg)
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(60.dp)) }
                    }
                }
            }
        }
    }

    // HISTORY BOTTOM SHEET
    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            containerColor = currentTheme.background
        ) {
            HistoryTab(db, userId, currentTheme, primaryColor, textColor, historyItems, context)
        }
    }

    // COLOR PICKER DIALOG
    if (showColorPickerFor != null) {
        val currentSelection = when(showColorPickerFor) {
            HomeCardType.MOOD -> moodBgId
            HomeCardType.REQUESTS -> requestBgId
            HomeCardType.NOTE -> noteBgId
            HomeCardType.WISHLIST -> wishlistBgId
            else -> "glass_light"
        }
        Dialog(onDismissRequest = { showColorPickerFor = null }) {
            Box(modifier = Modifier.fillMaxWidth().height(500.dp).glassCard(0.95f).padding(16.dp)) {
                Column {
                    Text("Select Card Style", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = primaryColor)
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyVerticalGrid(columns = GridCells.Fixed(3), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(availableCardBackgrounds, key = { it.id }) { bg ->
                            val isSelected = bg.id == currentSelection
                            Box(modifier = Modifier.height(60.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(bg.colors)).border(width = if (isSelected) 3.dp else 1.dp, color = if (isSelected) Color.Black else Color.White.copy(alpha=0.5f), shape = RoundedCornerShape(12.dp)).clickable {
                                val field = when(showColorPickerFor) { HomeCardType.MOOD -> "moodBgId"; HomeCardType.REQUESTS -> "requestBgId"; HomeCardType.NOTE -> "noteBgId"; HomeCardType.WISHLIST -> "wishlistBgId"; else -> "" }
                                db.collection("users").document(userId).update(field, bg.id)
                                showColorPickerFor = null
                            }) {
                                Text(bg.name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if(bg.darkText) Color.Black else Color.White, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(4.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        FolderDialog(currentTheme, folderToEdit, { showDialog = false }) { name, icon, date, isShared, color ->
            if (folderToEdit == null) {
                val id = System.currentTimeMillis().toString()
                val validParticipants = if (isShared) listOfNotNull(userId, partnerId.ifEmpty { null }) else listOf(userId)
                db.collection("folders").document(id).set(CoupleFolder(id, name, icon, date, validParticipants, color))
                if (isShared && partnerId.isNotEmpty()) sendNotification(db, partnerId, "New Adventure 📂", "$myName created folder: $name")
            } else {
                db.collection("folders").document(folderToEdit!!.id).update(mapOf("name" to name, "iconName" to icon, "date" to date, "color" to color))
                if (partnerId.isNotEmpty()) sendNotification(db, partnerId, "Updated Folder 📂", "$myName updated folder: $name")
            }
            showDialog = false
        }
    }
}

@Composable
fun FloatingParticle(particle: Particle) {
    val transition = rememberInfiniteTransition(label = "particle")
    val yOffset by transition.animateFloat(initialValue = 500f, targetValue = -500f, animationSpec = infiniteRepeatable(animation = tween(2000, easing = LinearOutSlowInEasing)), label = "y")
    val alpha by transition.animateFloat(initialValue = 1f, targetValue = 0f, animationSpec = infiniteRepeatable(animation = tween(2000, easing = LinearEasing)), label = "alpha")

    Box(modifier = Modifier.fillMaxSize().zIndex(10f)) {
        Text(text = particle.emoji, fontSize = 32.sp, modifier = Modifier.align(Alignment.Center).offset(x = ((particle.startX - 0.5f) * 400).dp, y = yOffset.dp).alpha(alpha))
    }
}

@Composable
fun DraggableCardWrapper(type: HomeCardType, isExpanded: Boolean, onExpandToggle: () -> Unit, onMove: (Int, Int) -> Unit, onColorPickClick: () -> Unit, bg: CardBackground, content: @Composable () -> Unit) {
    var offsetY by remember { mutableFloatStateOf(0f) }
    val animatedOffsetY by animateFloatAsState(targetValue = offsetY, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "dragY")
    val animatedElevation by animateDpAsState(targetValue = if (offsetY.absoluteValue > 0) 12.dp else 0.dp, label = "elevation")

    val (icon, title) = when(type) { HomeCardType.MOOD -> "✨" to "Mood Status"; HomeCardType.REQUESTS -> "👉👈" to "My Wants"; HomeCardType.NOTE -> "💌" to "Love Note"; HomeCardType.WISHLIST -> "🌠" to "Active Wishes" }

    // Use the theme's colors for the header, slightly transparent for contrast
    val headerTextColor = if (bg.darkText) Color.DarkGray else Color.White
    val headerBrush = Brush.linearGradient(bg.colors.map { it.copy(alpha = 0.65f) })

    Box(modifier = Modifier.fillMaxWidth().offset { IntOffset(0, animatedOffsetY.roundToInt()) }.zIndex(if (offsetY.absoluteValue > 0) 1f else 0f).shadow(animatedElevation, RoundedCornerShape(24.dp))
        .pointerInput(Unit) { detectDragGesturesAfterLongPress(onDragEnd = { offsetY = 0f }, onDragCancel = { offsetY = 0f }, onDrag = { change, dragAmount -> change.consume(); offsetY += dragAmount.y; if (offsetY > 180) { onMove(0, 1); offsetY = 0f }; if (offsetY < -180) { onMove(0, -1); offsetY = 0f } }) }) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent), modifier = Modifier.fillMaxWidth()) {
            Column {
                // Header Row utilizing dynamic theme
                Row(modifier = Modifier.fillMaxWidth().background(headerBrush).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.DragHandle, "Drag", tint = headerTextColor, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("$icon $title", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = headerTextColor) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onColorPickClick, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Palette, "Color", tint = headerTextColor, modifier = Modifier.size(16.dp)) }
                        Spacer(modifier = Modifier.width(16.dp))
                        IconButton(onClick = onExpandToggle, modifier = Modifier.size(24.dp)) { Icon(if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, "Expand", tint = headerTextColor) }
                    }
                }
                AnimatedVisibility(visible = isExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) { content() }
            }
        }
    }
}

@Composable
fun WishlistCard(requests: List<PartnerRequest>, partnerName: String, partnerId: String, myName: String, db: FirebaseFirestore, primary: Color, surface: Color, onTrigger: (String) -> Unit, bg: CardBackground) {
    val textCol = if(bg.darkText) Color.DarkGray else Color.White
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)).background(Brush.linearGradient(bg.colors)).padding(16.dp)) {
        Column {
            Text("$partnerName wishes for...", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textCol)
            Spacer(modifier = Modifier.height(12.dp))
            requests.forEach { req ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(if(bg.darkText) Color.Black.copy(0.05f) else Color.White.copy(0.15f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(req.emoji, fontSize = 24.sp); Spacer(modifier = Modifier.width(8.dp)); Text(req.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = textCol) }
                    Button(onClick = { onTrigger(req.emoji); db.collection("requests").document(req.id).delete(); sendNotification(db, partnerId, "Wish Granted! ✅", "$myName gave you: ${req.label} ${req.emoji}") }, colors = ButtonDefaults.buttonColors(containerColor = if(bg.darkText) primary else Color.White), shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp), modifier = Modifier.height(36.dp)) {
                        Text("Give", color = if(bg.darkText) Color.White else primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun YourStatusCard(theme: AppTheme, db: FirebaseFirestore, context: Context) {
    val uid = SessionCache.userId
    var showMoodPicker by remember { mutableStateOf(false) }
    var showNoteEditor by remember { mutableStateOf(false) }
    var noteDraft by remember { mutableStateOf("") }

    // Mood set matches ProfileScreen.kt exactly (10 moods)
    val moods = remember {
        listOf(
            "😊" to "Happy", "🥰" to "Loved", "🥳" to "Excited",
            "😎" to "Cool", "😴" to "Tired", "😤" to "Angry",
            "🤯" to "Stressed", "🤒" to "Sick", "🥺" to "Sad",
            "😐" to "Neutral"
        )
    }

    // Pre-fill noteDraft with the user's current love note when the editor opens
    LaunchedEffect(showNoteEditor) {
        if (showNoteEditor && uid.isNotEmpty()) {
            db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                noteDraft = doc.getString("loveNote") ?: ""
            }
        }
    }

    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).glassCard(0.85f).padding(16.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Your Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = theme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showMoodPicker = true }, modifier = Modifier.weight(1f)) {
                    Text("Set Mood 😊", maxLines = 1)
                }
                OutlinedButton(onClick = { showNoteEditor = true }, modifier = Modifier.weight(1f)) {
                    Text("Love Note ✏️", maxLines = 1)
                }
            }
        }
    }

    // Mood picker dialog
    if (showMoodPicker) {
        Dialog(onDismissRequest = { showMoodPicker = false }) {
            Box(modifier = Modifier.fillMaxWidth().glassCard(0.95f).padding(20.dp)) {
                Column {
                    Text("How are you feeling?", fontWeight = FontWeight.Bold, color = theme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyVerticalGrid(columns = GridCells.Fixed(3), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(moods, key = { it.first }) { pair ->
                            val emoji = pair.first
                            val label = pair.second
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                                if (uid.isNotEmpty()) {
                                    db.collection("users").document(uid).update("mood", emoji)
                                    Toast.makeText(context, "Mood set to $label", Toast.LENGTH_SHORT).show()
                                }
                                showMoodPicker = false
                            }) {
                                Text(emoji, fontSize = 36.sp)
                                Text(label, fontSize = 11.sp, color = Color.DarkGray)
                            }
                        }
                    }
                }
            }
        }
    }

    // Love note editor dialog
    if (showNoteEditor) {
        Dialog(onDismissRequest = { showNoteEditor = false }) {
            Box(modifier = Modifier.fillMaxWidth().glassCard(0.95f).padding(20.dp)) {
                Column {
                    Text("Leave a Love Note", fontWeight = FontWeight.Bold, color = theme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(value = noteDraft, onValueChange = { noteDraft = it }, label = { Text("Your note") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showNoteEditor = false }) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (uid.isNotEmpty() && noteDraft.isNotBlank()) {
                                db.collection("users").document(uid).update("loveNote", noteDraft.trim())
                                Toast.makeText(context, "Love note saved 💌", Toast.LENGTH_SHORT).show()
                            }
                            showNoteEditor = false
                        }) { Text("Save") }
                    }
                }
            }
        }
    }
}

@Composable
fun MoodCard(mood: String, partner: String, reactions: List<Reaction>, theme: AppTheme, myName: String, partnerId: String, db: FirebaseFirestore, context: Context, onTrigger: (String) -> Unit, bg: CardBackground) {
    if (mood.isEmpty()) return
    val moodLabel = when(mood) { "😊" -> "Happy"; "🥰" -> "Loved"; "🥳" -> "Excited"; "😴" -> "Tired"; "😤" -> "Angry"; "🤒" -> "Sick"; "🥺" -> "Sad"; else -> "" }
    var showMenu by remember { mutableStateOf(false) }
    val textCol = if(bg.darkText) Color.DarkGray else Color.White

    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)).background(Brush.linearGradient(bg.colors)).padding(16.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(mood, fontSize = 44.sp); Spacer(modifier = Modifier.width(16.dp)); Column { Text("$partner is feeling...", style = MaterialTheme.typography.labelMedium, color = textCol.copy(alpha=0.8f)); Text(moodLabel, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = textCol) } }
            Spacer(modifier = Modifier.height(16.dp)); HorizontalDivider(color = textCol.copy(0.2f)); Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Respond with:", style = MaterialTheme.typography.labelSmall, color = textCol.copy(alpha=0.8f)); Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.ArrowDropDown, "More", tint = textCol, modifier = Modifier.clickable { showMenu = true })
            }
            if (showMenu) {
                Dialog(onDismissRequest = { showMenu = false }) {
                    Box(modifier = Modifier.fillMaxWidth().height(400.dp).glassCard(0.95f).padding(16.dp)) {
                        Column {
                            Text("Choose Reaction", fontWeight = FontWeight.Bold, color = theme.primary); Spacer(modifier = Modifier.height(16.dp))
                            LazyVerticalGrid(columns = GridCells.Fixed(4), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(reactions, key = { it.label }) { reaction ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onTrigger(reaction.emoji); if (partnerId.isNotEmpty()) { sendNotification(db, partnerId, "Reaction ${reaction.emoji}", "$myName sent a ${reaction.label}!"); Toast.makeText(context, "Sent ${reaction.label}!", Toast.LENGTH_SHORT).show() }; showMenu = false }) { Text(reaction.emoji, fontSize = 32.sp); Text(reaction.label, fontSize = 10.sp, maxLines = 1, color = Color.DarkGray) }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(reactions, key = { it.label }) { reaction -> Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if(bg.darkText) Color.Black.copy(0.05f) else Color.White.copy(alpha=0.25f)).clickable { onTrigger(reaction.emoji); if (partnerId.isNotEmpty()) { sendNotification(db, partnerId, "Reaction ${reaction.emoji}", "$myName sent a ${reaction.label}!"); Toast.makeText(context, "Sent ${reaction.label}!", Toast.LENGTH_SHORT).show() } }.padding(horizontal = 12.dp, vertical = 8.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(reaction.emoji, fontSize = 18.sp); Spacer(modifier = Modifier.width(6.dp)); Text(reaction.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textCol) } } } }
        }
    }
}

@Composable
fun RequestCard(options: List<Reaction>, partnerId: String, myName: String, db: FirebaseFirestore, context: Context, onTrigger: (String) -> Unit, bg: CardBackground) {
    var showMenu by remember { mutableStateOf(false) }
    val textCol = if(bg.darkText) Color.DarkGray else Color.White

    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)).background(Brush.linearGradient(bg.colors)).padding(16.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("I want... 👉👈", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textCol); Spacer(modifier = Modifier.width(8.dp)); Icon(Icons.Default.ArrowDropDown, "More", tint = textCol, modifier = Modifier.clickable { showMenu = true }) }
            if (showMenu) {
                Dialog(onDismissRequest = { showMenu = false }) {
                    Box(modifier = Modifier.fillMaxWidth().height(400.dp).glassCard(0.95f).padding(16.dp)) {
                        Column {
                            Text("Make a Wish", fontWeight = FontWeight.Bold, color = Color.Black); Spacer(modifier = Modifier.height(16.dp))
                            LazyVerticalGrid(columns = GridCells.Fixed(4), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(options, key = { it.label }) { req -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onTrigger(req.emoji); sendRequest(req, partnerId, myName, db, context); showMenu = false }) { Text(req.emoji, fontSize = 32.sp); Text(req.label, fontSize = 10.sp, maxLines = 1, color = Color.Black) } }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(options, key = { it.label }) { req -> Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if(bg.darkText) Color.Black.copy(0.05f) else Color.White.copy(alpha=0.25f)).clickable { onTrigger(req.emoji); sendRequest(req, partnerId, myName, db, context) }.padding(horizontal = 12.dp, vertical = 8.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(req.emoji, fontSize = 18.sp); Spacer(modifier = Modifier.width(6.dp)); Text(req.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textCol) } } }
            }
        }
    }
}

fun sendRequest(req: Reaction, partnerId: String, myName: String, db: FirebaseFirestore, context: Context) {
    if (partnerId.isNotEmpty()) {
        sendNotification(db, partnerId, "Request ${req.emoji}", "$myName wants ${req.label}!")
        val requestData = PartnerRequest(id = System.currentTimeMillis().toString(), requesterId = FirebaseAuth.getInstance().currentUser?.uid ?: "", emoji = req.emoji, label = req.label, timestamp = System.currentTimeMillis())
        db.collection("requests").document(requestData.id).set(hashMapOf("id" to requestData.id, "requesterId" to requestData.requesterId, "receiverId" to partnerId, "emoji" to requestData.emoji, "label" to requestData.label, "timestamp" to requestData.timestamp))
        Toast.makeText(context, "Requested ${req.label}!", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun NoteCard(note: String, partner: String, bg: CardBackground) {
    if (note.isEmpty()) return
    val textCol = if(bg.darkText) Color(0xFF3E2723) else Color.White
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)).background(Brush.linearGradient(bg.colors)).padding(16.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) { Text("Note from $partner ✏️", fontSize = 10.sp, color = textCol.copy(alpha=0.6f)); Spacer(modifier = Modifier.height(8.dp)); Text(note, fontFamily = FontFamily.Cursive, fontSize = 28.sp, lineHeight = 34.sp, color = textCol) }
    }
}

@Composable
fun HistoryTab(db: FirebaseFirestore, userId: String, theme: AppTheme, primary: Color, textC: Color, items: List<ActivityItem>, context: Context) {
    // Pattern B: expensive to construct — hoist out of per-item scope
    val historyDateFmt = remember { SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("History Log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if(theme.id=="custom") Color.White else primary); OutlinedButton(onClick = { db.collection("notifications").whereEqualTo("to", userId).get().addOnSuccessListener { s -> val b = db.batch(); s.documents.forEach { b.delete(it.reference) }; b.commit() } }, border = BorderStroke(1.dp, Color.Red.copy(0.5f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red.copy(0.7f))) { Text("Clear") } }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { items(items, key = { it.id }) { item -> val icon = when { item.title.contains("Message", true) -> "💬"; item.title.contains("Adventure", true) -> "📂"; item.title.contains("Deleted", true) -> "🗑️"; item.title.contains("Photo", true) -> "📸"; item.title.contains("Wish", true) -> "🌠"; else -> "🔔" }; Box(modifier = Modifier.fillMaxWidth().glassCard(0.7f).padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(icon, fontSize = 28.sp); Spacer(modifier = Modifier.width(16.dp)); Column { Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = primary); Text(item.body, style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray); Text(historyDateFmt.format(Date(item.timestamp)), style = MaterialTheme.typography.labelSmall, color = Color.Gray) } } } } }
    }
}

fun createNotificationChannel(context: Context) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { val channel = NotificationChannel("CHAT_CHANNEL", "CoupleAppMessages", NotificationManager.IMPORTANCE_HIGH); (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel) } }

@Composable
fun FolderItem(folder: CoupleFolder, currentTheme: AppTheme, onDelete: () -> Unit, onEdit: () -> Unit, onClick: () -> Unit) {
    val daysDiff = if (folder.date > 0) TimeUnit.MILLISECONDS.toDays(folder.date - System.currentTimeMillis()) else 0
    val timeText = when { folder.date == 0L -> ""; daysDiff > 0 -> "$daysDiff days to go! ⏳"; else -> "${daysDiff.absoluteValue} days ago" }
    val baseColor = if (folder.color != 0L) Color(folder.color) else currentTheme.surface
    val contentColor = if (baseColor.luminance() < 0.5f) Color.White else currentTheme.primary
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.height(160.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(baseColor.copy(alpha=0.7f)).border(1.dp, Color.White.copy(0.6f), RoundedCornerShape(24.dp)).clickable { onClick() }) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(folder.iconName, style = MaterialTheme.typography.displayMedium); Spacer(modifier = Modifier.height(8.dp)); Text(folder.name, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = contentColor, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp)); if (timeText.isNotEmpty()) Text(timeText, style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha=0.7f)) }
        Box(modifier = Modifier.align(Alignment.TopEnd)) { IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Options", tint = contentColor.copy(alpha=0.6f)) }; DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, containerColor = currentTheme.surface) { DropdownMenuItem(text = { Text("Edit", color = currentTheme.text) }, onClick = { showMenu = false; onEdit() }, leadingIcon = { Icon(Icons.Default.Edit, null, tint = currentTheme.primary) }); DropdownMenuItem(text = { Text("Delete", color = Color.Red) }, onClick = { showMenu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }) } }
    }
}

@Composable
fun FolderDialog(currentTheme: AppTheme, folderToEdit: CoupleFolder? = null, onDismiss: () -> Unit, onSave: (String, String, Long, Boolean, Long) -> Unit) {
    var folderName by remember { mutableStateOf(folderToEdit?.name ?: "") }
    var selectedIcon by remember { mutableStateOf(folderToEdit?.iconName ?: "🌲") }
    var selectedDate by remember { mutableStateOf(folderToEdit?.date ?: 0L) }
    var selectedColor by remember { mutableStateOf(if (folderToEdit?.color != 0L) folderToEdit?.color ?: FolderColors[0].toArgb().toLong() else FolderColors[0].toArgb().toLong()) }
    var isShared by remember { mutableStateOf(true) }
    var dateText by remember { mutableStateOf(if (selectedDate > 0) SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(selectedDate)) else "Pick a Date 📅") }
    val context = LocalContext.current
    val calendar = java.util.Calendar.getInstance().apply { if(selectedDate > 0) timeInMillis = selectedDate }
    val datePickerDialog = android.app.DatePickerDialog(context, { _, y, m, d -> calendar.set(y, m, d); selectedDate = calendar.timeInMillis; dateText = "$d/${m + 1}/$y" }, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH), calendar.get(java.util.Calendar.DAY_OF_MONTH))
    // Pattern C: static list — built once per dialog composition
    val icons = remember { listOf("🌲", "⛺", "🏔️", "🛶", "🍂", "🍄", "🔥", "🚲", "🌻", "🌹", "🌸", "🌺", "🌴", "🌵", "🏠", "🏢", "🏰", "🎡", "🏥", "🏫", "🏛️", "⛪", "🌃", "🌉", "📚", "📖", "✏️", "🎓", "🎒", "💻", "🎨", "🍕", "🍔", "🍣", "🍦", "☕", "🍷", "🎂", "🍎", "✈️", "🚗", "🚂", "🗺️", "🏖️", "🚁", "❤️", "💍", "🎁", "🎵", "🎮", "🐾", "👶") }

    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth().height(650.dp).glassCard(0.95f).padding(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (folderToEdit == null) "New Adventure" else "Edit Adventure", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = currentTheme.primary)
                Spacer(modifier = Modifier.height(16.dp)); OutlinedTextField(value = folderName, onValueChange = { folderName = it }, label = { Text("Name") }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = currentTheme.primary, focusedLabelColor = currentTheme.primary)); Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { datePickerDialog.show() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = currentTheme.primary.copy(alpha=0.1f))) { Text(dateText, color = currentTheme.primary) }; Spacer(modifier = Modifier.height(16.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(FolderColors, key = { it.value.toString() }) { color -> Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(color).border(2.dp, if (selectedColor == color.toArgb().toLong()) currentTheme.primary else Color.Transparent, CircleShape).clickable { selectedColor = color.toArgb().toLong() }) { if (selectedColor == color.toArgb().toLong()) Icon(Icons.Default.Check, null, tint = Color.Black.copy(0.5f), modifier = Modifier.align(Alignment.Center).size(20.dp)) } } }; Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.weight(1f)) { LazyVerticalGrid(columns = GridCells.Fixed(5), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(icons, key = { it }) { icon -> Text(icon, modifier = Modifier.clickable { selectedIcon = icon }.padding(4.dp).background(if (selectedIcon == icon) currentTheme.primary.copy(0.3f) else Color.Transparent, CircleShape).padding(4.dp), style = MaterialTheme.typography.headlineSmall) } } }; Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { if (folderName.isNotEmpty()) onSave(folderName, selectedIcon, selectedDate, isShared, selectedColor) }, colors = ButtonDefaults.buttonColors(containerColor = currentTheme.primary), modifier = Modifier.fillMaxWidth()) { Text(if (folderToEdit == null) "Create" else "Save") }
            }
        }
    }
}
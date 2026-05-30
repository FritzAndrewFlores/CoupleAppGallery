package com.example.coupleappgallery

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.clickable

// Data Model with Reaction
data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val text: String = "",
    val timestamp: Long = 0,
    val isRead: Boolean = false,
    val reaction: String = "" // Stores the emoji reaction
)

// --- EXPANDED EMOJI LIST (60+ Options) ---
val ReactionEmojis = listOf(
    // Favorites & Love
    "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
    "🥰", "😍", "😘", "😙", "😚", "💋", "💌", "💍", "🫂",

    // Happy & Fun
    "😂", "🤣", "🥹", "🤩", "😎", "🥳", "🎉", "🔥", "✨", "💃",
    "👍", "👎", "👏", "🙌", "🫶", "🤝", "🫡", "🫣", "🤔", "👀",

    // Sad, Angry & Shocked
    "😭", "😢", "🥺", "😤", "😡", "🤬", "🤯", "😱", "😓", "💀",
    "💩", "🤡", "👻", "👽", "🤖", "👿", "💤", "🤒", "🤕", "🤮",

    // Food & Random
    "🧸", "🌹", "🌻", "☀️", "🌧️", "🌈", "☕", "🥂", "🍻", "🍕",
    "🍔", "🍟", "🍦", "🍫", "🍩", "🍿", "🍆", "🍑", "🍒", "🍓",
    "💅", "👑", "💄", "👠", "🧢", "⚽", "🎮", "🚀", "💯", "✅"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(navController: NavController, currentTheme: AppTheme) {
    // Pattern D: singletons — retrieve once per composition lifetime
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val storage = remember { FirebaseStorage.getInstance() }
    val myId = auth.currentUser?.uid ?: ""
    val context = LocalContext.current

    val partnerId = SessionCache.partnerId
    val partnerName = SessionCache.partnerName
    val partnerPhotoUrl = SessionCache.partnerPhotoUrl
    val myPhotoUrl = SessionCache.userPhotoUrl

    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var text by remember { mutableStateOf("") }
    var isUploadingBg by remember { mutableStateOf(false) }

    // --- REACTION SHEET STATE ---
    var showReactionSheet by remember { mutableStateOf(false) }
    var selectedMessageId by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState()

    val listState = rememberLazyListState()

    // --- COLORS ---
    val primaryColor = currentTheme.primary
    val backgroundColor = currentTheme.background
    val surfaceColor = currentTheme.surface
    val textColor = currentTheme.text
    val secondaryColor = currentTheme.secondary
    // Pattern B: expensive to construct — hoist out of per-message item scope
    val chatTimeFmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    // Wallpaper Launcher
    val wallpaperLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            isUploadingBg = true
            val ref = storage.reference.child("theme_wallpapers/$myId.jpg")
            ref.putFile(uri).addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { url ->
                    val updates = mapOf(
                        "themeId" to "custom",
                        "themeWallpaper" to url.toString(),
                        "themePrimary" to (if(currentTheme.id == "custom") primaryColor.toArgb() else 0xFF2E5948)
                    )
                    db.collection("users").document(myId).update(updates)
                    isUploadingBg = false
                    Toast.makeText(context, "Global Theme Wallpaper Updated! ✨", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Listen for Messages — re-registers when partnerId changes (SessionCache state drives re-key)
    DisposableEffect(myId, partnerId) {
        if (partnerId.isEmpty()) return@DisposableEffect onDispose { }
        val reg = db.collection("chats")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val msgs = snapshot.documents.mapNotNull { it.toObject(ChatMessage::class.java) }
                        .filter { (it.senderId == myId && it.receiverId == partnerId) || (it.senderId == partnerId && it.receiverId == myId) }
                    messages = msgs

                    // Mark as read
                    msgs.filter { it.receiverId == myId && !it.isRead }.forEach { msg ->
                        db.collection("chats").document(msg.id).update("isRead", true)
                    }
                }
            }
        onDispose { reg.remove() }
    }

    // Auto-scroll
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // ROOT BOX
    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {

        // BACKGROUND IMAGE
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
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (partnerPhotoUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = partnerPhotoUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(36.dp).clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Column {
                                Text(partnerName, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                if (isUploadingBg) Text("Updating wallpaper...", fontSize = 10.sp)
                            }
                        }
                    },
                    navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                    actions = { IconButton(onClick = { wallpaperLauncher.launch("image/*") }) { Icon(Icons.Default.Wallpaper, contentDescription = "Background") } },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (currentTheme.id == "custom") Color.Black.copy(0.4f) else primaryColor,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            },
            bottomBar = {
                // INPUT AREA
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (currentTheme.id == "custom") Color.White.copy(alpha = 0.9f) else surfaceColor, CircleShape)
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = text,
                            onValueChange = { text = it },
                            placeholder = { Text("Write a love letter...", fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, color = Color.Gray) },
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = textColor, unfocusedTextColor = textColor
                            ),
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Serif, fontSize = 16.sp)
                        )
                        IconButton(
                            onClick = {
                                if (text.isNotEmpty() && partnerId.isNotEmpty()) {
                                    val id = System.currentTimeMillis().toString()
                                    val msg = ChatMessage(id, myId, partnerId, text, System.currentTimeMillis(), false)
                                    db.collection("chats").document(id).set(msg)
                                    sendNotification(db, partnerId, "New Message 💌", text)
                                    text = ""
                                }
                            },
                            modifier = Modifier.padding(end = 4.dp).size(42.dp).background(primaryColor, CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isMe = msg.senderId == myId
                    val isLastMessageByMe = isMe && msg == messages.lastOrNull { it.senderId == myId }
                    var showDetails by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // PARTNER AVATAR (Left)
                        if (!isMe) {
                            if (partnerPhotoUrl.isNotEmpty()) {
                                AsyncImage(model = partnerPhotoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(28.dp).clip(CircleShape))
                            } else {
                                Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(Color.LightGray))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        // MESSAGE BUBBLE + REACTION
                        Box {
                            Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = if (isMe) primaryColor else (if (currentTheme.id == "custom") Color.White.copy(alpha=0.9f) else surfaceColor)),
                                    shape = if (isMe) RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
                                    elevation = CardDefaults.cardElevation(1.dp),
                                    modifier = Modifier
                                        .widthIn(max = 280.dp)
                                        .combinedClickable(
                                            onClick = { showDetails = !showDetails },
                                            onLongClick = {
                                                selectedMessageId = msg.id
                                                showReactionSheet = true
                                            }
                                        )
                                ) {
                                    Text(
                                        text = msg.text,
                                        modifier = Modifier.padding(12.dp),
                                        color = if (isMe) Color.White else textColor,
                                        fontFamily = FontFamily.Serif,
                                        fontSize = 16.sp
                                    )
                                }

                                // REACTION BADGE
                                if (msg.reaction.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .offset(y = (-8).dp)
                                            .background(if(currentTheme.id=="custom") Color.White.copy(0.9f) else surfaceColor, CircleShape)
                                            .border(1.dp, Color.LightGray.copy(0.3f), CircleShape)
                                            .padding(4.dp)
                                    ) {
                                        Text(msg.reaction, fontSize = 12.sp)
                                    }
                                }

                                // TIMESTAMP & STATUS
                                AnimatedVisibility(visible = showDetails) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = chatTimeFmt.format(Date(msg.timestamp)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (currentTheme.id == "custom") Color.White.copy(0.8f) else Color.Gray,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                        if (isLastMessageByMe) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (msg.isRead) "• Seen" else "• Sent", style = MaterialTheme.typography.labelSmall, color = if (msg.isRead) secondaryColor else Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // MY AVATAR (Right)
                        if (isMe) {
                            Spacer(modifier = Modifier.width(8.dp))
                            if (myPhotoUrl.isNotEmpty()) {
                                AsyncImage(model = myPhotoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(28.dp).clip(CircleShape))
                            } else {
                                Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(primaryColor))
                            }
                        }
                    }
                }
            }
        }
    }

    // --- REACTION BOTTOM SHEET ---
    if (showReactionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showReactionSheet = false },
            sheetState = sheetState,
            containerColor = surfaceColor
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("React to message", fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.padding(bottom = 16.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8), // Denser grid for more emojis
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ReactionEmojis, key = { it }) { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 28.sp,
                            modifier = Modifier
                                .clickable {
                                    if (selectedMessageId.isNotEmpty()) {
                                        db.collection("chats").document(selectedMessageId).update("reaction", emoji)
                                        // Optional: Notify partner of reaction
                                        if (partnerId.isNotEmpty()) {
                                            sendNotification(db, partnerId, "New Reaction $emoji", "Partner reacted to your message!")
                                        }
                                    }
                                    showReactionSheet = false
                                }
                                .padding(4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
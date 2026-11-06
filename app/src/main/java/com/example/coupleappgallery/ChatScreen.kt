package com.example.coupleappgallery

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.Keep
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.PropertyName
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Returns a stable, symmetric conversation ID for two user IDs. */
fun chatIdFor(a: String, b: String) = listOf(a, b).sorted().joinToString("_")

@Keep
data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val text: String = "",
    val timestamp: Long = 0,
    @field:PropertyName("isRead")
    @get:PropertyName("isRead")
    val isRead: Boolean = false,
    val reaction: String = "",
    val chatId: String = ""
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(navController: NavController, currentTheme: AppTheme) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val storage = remember { FirebaseStorage.getInstance() }
    val myId = auth.currentUser?.uid ?: ""
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val partnerId = SessionCache.partnerId
    val partnerName = SessionCache.partnerName
    val partnerPhotoUrl = SessionCache.partnerPhotoUrl
    val myName = SessionCache.userName
    val myPhotoUrl = SessionCache.userPhotoUrl
    val isLinked = partnerId.isNotEmpty()

    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var text by remember { mutableStateOf("") }
    var isUploadingBg by remember { mutableStateOf(false) }
    var hasInitialScrolled by remember(myId, partnerId) { mutableStateOf(false) }

    // Reaction Sheet State
    var showReactionSheet by remember { mutableStateOf(false) }
    var selectedMessage by remember { mutableStateOf<ChatMessage?>(null) }
    val sheetState = rememberModalBottomSheetState()

    val listState = rememberLazyListState()

    // Colors (ensure crisp contrast on white custom-theme bubbles & input field)
    val isCustomTheme = currentTheme.id == "custom"
    val primaryColor = currentTheme.primary
    val backgroundColor = currentTheme.background
    val surfaceColor = currentTheme.surface
    val textColor = currentTheme.text
    val secondaryColor = currentTheme.secondary
    val cardTextOnLightSurface = if (isCustomTheme) Color(0xFF1F1F1F) else textColor

    val chatTimeFmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    val wallpaperLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null && myId.isNotEmpty()) {
            isUploadingBg = true
            scope.launch {
                try {
                    val base64DataUri = compressToBase64DataUri(context, uri, maxDim = 1080, quality = 72)
                    val updates = mapOf(
                        "themeId" to "custom",
                        "themePrimary" to primaryColor.toArgb(),
                        "themeSecondary" to secondaryColor.toArgb(),
                        "themeBg" to backgroundColor.toArgb(),
                        "themeText" to textColor.toArgb(),
                        "themeWallpaper" to base64DataUri
                    )
                    db.collection("users").document(myId).update(updates).await()
                    if (partnerId.isNotEmpty()) {
                        runCatching {
                            db.collection("users").document(partnerId).update(updates).await()
                        }.onFailure { e ->
                            Log.w("ChatScreen", "Partner wallpaper sync failed", e)
                        }
                    }
                    Toast.makeText(context, "Global Theme Wallpaper Updated! ✨", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("ChatScreen", "Wallpaper upload failed", e)
                    Toast.makeText(context, "Wallpaper upload failed.", Toast.LENGTH_SHORT).show()
                } finally {
                    isUploadingBg = false
                }
            }
        }
    }

    // Listen for Messages — scoped to this conversation; re-registers when myId or partnerId changes
    DisposableEffect(myId, partnerId) {
        if (myId.isEmpty() || partnerId.isEmpty()) {
            messages = emptyList()
            return@DisposableEffect onDispose { }
        }
        val conversationId = chatIdFor(myId, partnerId)
        val reg = db.collection("chats")
            .whereEqualTo("chatId", conversationId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FS", "listener@ChatScreen:messages", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val msgs = snapshot.documents
                        .mapNotNull { doc ->
                            doc.toObject(ChatMessage::class.java)?.let {
                                if (it.id.isEmpty()) it.copy(id = doc.id) else it
                            }
                        }
                        .sortedBy { it.timestamp }
                    messages = msgs

                    // Mark unread incoming messages as read in safe 450-item chunks
                    val unread = msgs.filter { it.receiverId == myId && !it.isRead && it.id.isNotEmpty() }
                    if (unread.isNotEmpty()) {
                        unread.chunked(450).forEach { chunk ->
                            val batch = db.batch()
                            chunk.forEach { msg ->
                                batch.update(db.collection("chats").document(msg.id), "isRead", true)
                            }
                            batch.commit().addOnFailureListener { e ->
                                Log.e("ChatScreen", "Failed to mark messages as read", e)
                            }
                        }
                    }
                }
            }
        onDispose { reg.remove() }
    }

    // Smart auto-scroll: jump immediately on initial load; animate only when near bottom or sending own message
    LaunchedEffect(messages.size) {
        if (messages.isEmpty()) return@LaunchedEffect
        val lastIdx = messages.size - 1
        if (!hasInitialScrolled) {
            listState.scrollToItem(lastIdx)
            hasInitialScrolled = true
        } else {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: lastIdx
            val isNearBottom = lastIdx - lastVisible <= 3
            val sentByMe = messages.last().senderId == myId
            if (isNearBottom || sentByMe) {
                listState.animateScrollToItem(lastIdx)
            }
        }
    }

    val lastMyMessageId = remember(messages, myId) {
        messages.lastOrNull { it.senderId == myId }?.id
    }

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        if (isCustomTheme && currentTheme.wallpaperUrl.isNotEmpty()) {
            AsyncImage(
                model = buildImageRequest(context, currentTheme.wallpaperUrl, cacheKey = "theme_wallpaper"),
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
                                    model = buildImageRequest(context, partnerPhotoUrl, cacheKey = "avatar_partner"),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(36.dp).clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Column {
                                Text(
                                    text = if (isLinked) partnerName else "Love Letters",
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                if (isUploadingBg) Text("Updating wallpaper...", fontSize = 10.sp)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            enabled = !isUploadingBg && myId.isNotEmpty(),
                            onClick = { wallpaperLauncher.launch("image/*") }
                        ) {
                            Icon(Icons.Default.Wallpaper, contentDescription = "Background")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isCustomTheme) Color.Black.copy(0.4f) else primaryColor,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            },
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isCustomTheme) Color.White.copy(alpha = 0.92f) else surfaceColor,
                                CircleShape
                            )
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = text,
                            onValueChange = { text = it },
                            enabled = isLinked,
                            placeholder = {
                                Text(
                                    text = if (isLinked) "Write a love letter..." else "Link with your partner in Profile first...",
                                    fontFamily = FontFamily.Serif,
                                    fontStyle = FontStyle.Italic,
                                    color = Color.Gray
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                focusedTextColor = cardTextOnLightSurface,
                                unfocusedTextColor = cardTextOnLightSurface
                            ),
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Serif, fontSize = 16.sp)
                        )
                        IconButton(
                            onClick = {
                                if (!isLinked) {
                                    Toast.makeText(context, "Link with a partner in Profile to send messages!", Toast.LENGTH_SHORT).show()
                                    return@IconButton
                                }
                                val cleanText = text.trim()
                                if (cleanText.isEmpty()) return@IconButton
                                if (cleanText.length > 4000) {
                                    Toast.makeText(context, "Message is too long (max 4,000 characters).", Toast.LENGTH_SHORT).show()
                                    return@IconButton
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                val msgRef = db.collection("chats").document()
                                val id = msgRef.id
                                val msg = ChatMessage(
                                    id = id,
                                    senderId = myId,
                                    receiverId = partnerId,
                                    text = cleanText,
                                    timestamp = System.currentTimeMillis(),
                                    isRead = false,
                                    chatId = chatIdFor(myId, partnerId)
                                )
                                text = ""
                                msgRef.set(msg)
                                    .addOnSuccessListener {
                                        sendNotification(db, partnerId, "New Message 💌", cleanText)
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("ChatScreen", "Failed to send message", e)
                                        text = cleanText
                                        Toast.makeText(context, "Failed to send message.", Toast.LENGTH_SHORT).show()
                                    }
                            },
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(42.dp)
                                .background(if (isLinked) primaryColor else Color.LightGray, CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        ) { padding ->
            if (!isLinked) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCustomTheme) Color.White.copy(alpha = 0.92f) else surfaceColor
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("💞", fontSize = 40.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Connect with Your Partner",
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Share or enter an invite code in My Profile to unlock your private chat.",
                                textAlign = TextAlign.Center,
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { navController.navigate("profile") },
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                            ) {
                                Text("Go to Profile")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val isMe = msg.senderId == myId
                        val isLastMessageByMe = isMe && msg.id == lastMyMessageId
                        var showDetails by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            if (!isMe) {
                                if (partnerPhotoUrl.isNotEmpty()) {
                                    AsyncImage(model = buildImageRequest(context, partnerPhotoUrl, cacheKey = "avatar_partner"), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(28.dp).clip(CircleShape))
                                } else {
                                    Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(Color.LightGray))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Box {
                                Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isMe) primaryColor else (if (isCustomTheme) Color.White.copy(alpha = 0.92f) else surfaceColor)
                                        ),
                                        shape = if (isMe) RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
                                        elevation = CardDefaults.cardElevation(1.dp),
                                        modifier = Modifier
                                            .widthIn(max = 280.dp)
                                            .combinedClickable(
                                                onClick = { showDetails = !showDetails },
                                                onLongClick = {
                                                    selectedMessage = msg
                                                    showReactionSheet = true
                                                }
                                            )
                                    ) {
                                        Text(
                                            text = msg.text,
                                            modifier = Modifier.padding(12.dp),
                                            color = if (isMe) Color.White else cardTextOnLightSurface,
                                            fontFamily = FontFamily.Serif,
                                            fontSize = 16.sp
                                        )
                                    }

                                    if (msg.reaction.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .offset(y = (-8).dp)
                                                .background(if (isCustomTheme) Color.White.copy(0.92f) else surfaceColor, CircleShape)
                                                .border(1.dp, Color.LightGray.copy(0.3f), CircleShape)
                                                .padding(4.dp)
                                        ) {
                                            Text(msg.reaction, fontSize = 12.sp)
                                        }
                                    }

                                    AnimatedVisibility(visible = showDetails) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = chatTimeFmt.format(Date(msg.timestamp)),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isCustomTheme) Color.White.copy(0.8f) else Color.Gray,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                            if (isLastMessageByMe) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    if (msg.isRead) "• Seen" else "• Sent",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (msg.isRead) secondaryColor else Color.Gray,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (isMe) {
                                Spacer(modifier = Modifier.width(8.dp))
                                if (myPhotoUrl.isNotEmpty()) {
                                    AsyncImage(model = buildImageRequest(context, myPhotoUrl, cacheKey = "avatar_me"), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(28.dp).clip(CircleShape))
                                } else {
                                    Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(primaryColor))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Reaction Bottom Sheet (supports toggling reaction off by tapping the active emoji)
    if (showReactionSheet) {
        val targetMsg = selectedMessage
        ModalBottomSheet(
            onDismissRequest = { showReactionSheet = false },
            sheetState = sheetState,
            containerColor = surfaceColor
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "React to message (tap active emoji to remove)",
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ReactionEmojis, key = { it }) { emoji ->
                        val isCurrentReaction = targetMsg?.reaction == emoji
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isCurrentReaction) primaryColor.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (targetMsg != null && targetMsg.id.isNotEmpty()) {
                                        val nextReaction = if (isCurrentReaction) "" else emoji
                                        db.collection("chats").document(targetMsg.id)
                                            .update("reaction", nextReaction)
                                            .addOnSuccessListener {
                                                // Only notify partner when adding a reaction to THEIR message
                                                if (nextReaction.isNotEmpty() && partnerId.isNotEmpty() && targetMsg.senderId == partnerId) {
                                                    sendNotification(db, partnerId, "New Reaction $nextReaction", "$myName reacted to your message!")
                                                }
                                            }
                                    }
                                    showReactionSheet = false
                                }
                                .padding(4.dp)
                        ) {
                            Text(text = emoji, fontSize = 28.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

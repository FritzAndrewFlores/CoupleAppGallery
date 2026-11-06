package com.example.coupleappgallery

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.Keep
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.PropertyName
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

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
    val chatId: String = "",
    val replyToId: String = "",
    val replyToText: String = "",
    val replyToSender: String = "",
    val imageUrl: String = ""
)

/** Positions in a contiguous cluster of messages from the same sender */
enum class BubblePosition {
    SINGLE, FIRST, MIDDLE, LAST
}

fun calculateBubblePosition(
    messages: List<ChatMessage>,
    index: Int
): BubblePosition {
    val current = messages[index]
    val prev = messages.getOrNull(index - 1)
    val next = messages.getOrNull(index + 1)

    val isSameSenderPrev = prev != null &&
            prev.senderId == current.senderId &&
            (current.timestamp - prev.timestamp) < 120_000L
    val isSameSenderNext = next != null &&
            next.senderId == current.senderId &&
            (next.timestamp - current.timestamp) < 120_000L

    return when {
        !isSameSenderPrev && !isSameSenderNext -> BubblePosition.SINGLE
        !isSameSenderPrev && isSameSenderNext -> BubblePosition.FIRST
        isSameSenderPrev && isSameSenderNext -> BubblePosition.MIDDLE
        else -> BubblePosition.LAST
    }
}

private val chatTimeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
private val chatFullDateFormatter = SimpleDateFormat("EEEE, MMM d • h:mm a", Locale.getDefault())
private val chatHeaderDateCache = mutableMapOf<Long, String>()

fun shouldShowDateHeader(prevTimestamp: Long, currentTimestamp: Long): Boolean {
    if (prevTimestamp == 0L) return true
    return (currentTimestamp - prevTimestamp) > 20 * 60 * 1000L
}

fun formatChatDateHeader(timestamp: Long): String {
    return chatHeaderDateCache.getOrPut(timestamp) {
        val messageCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val nowCal = Calendar.getInstance()

        val sameDay = messageCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                messageCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)
        if (sameDay) {
            "Today ${chatTimeFormatter.format(Date(timestamp))}"
        } else {
            val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            val isYesterday = messageCal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
                    messageCal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR)
            if (isYesterday) {
                "Yesterday ${chatTimeFormatter.format(Date(timestamp))}"
            } else {
                chatFullDateFormatter.format(Date(timestamp))
            }
        }
    }
}

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
    val partnerStatus = SessionCache.partnerStatus
    val myName = SessionCache.userName
    val myPhotoUrl = SessionCache.userPhotoUrl
    val isLinked = partnerId.isNotEmpty()

    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var text by remember { mutableStateOf("") }
    var isUploadingBg by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var hasInitialScrolled by remember(myId, partnerId) { mutableStateOf(false) }

    // Option 1: Cupertino Tapback & Quote Reply & Photo Sharing State
    var activeTapbackMessageId by remember { mutableStateOf<String?>(null) }
    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var lightboxImageUrl by remember { mutableStateOf<String?>(null) }

    // Full Reaction Sheet State (fallback for '+' icon in Tapback bar)
    var showFullReactionSheet by remember { mutableStateOf(false) }
    var targetMessageForFullPicker by remember { mutableStateOf<ChatMessage?>(null) }
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

    val chatTimeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    // Photo picker for in-chat image sharing
    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

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

    // Send Message Handler (supports text, photo attachment, and quote replies)
    val onSendMessage: () -> Unit = {
        if (!isLinked) {
            Toast.makeText(context, "Link with a partner in Profile to send messages!", Toast.LENGTH_SHORT).show()
        } else {
            val cleanText = text.trim()
            val imgUri = selectedImageUri
            if (cleanText.isEmpty() && imgUri == null) {
                // Nothing to send
            } else if (cleanText.length > 4000) {
                Toast.makeText(context, "Message is too long (max 4,000 characters).", Toast.LENGTH_SHORT).show()
            } else {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                isSending = true

                scope.launch {
                    try {
                        val uploadedImageUrl = if (imgUri != null) {
                            compressToBase64DataUri(context, imgUri, maxDim = 960, quality = 70)
                        } else {
                            ""
                        }

                        val replyContext = replyingTo
                        val msgRef = db.collection("chats").document()
                        val id = msgRef.id
                        val msg = ChatMessage(
                            id = id,
                            senderId = myId,
                            receiverId = partnerId,
                            text = cleanText,
                            timestamp = System.currentTimeMillis(),
                            isRead = false,
                            chatId = chatIdFor(myId, partnerId),
                            replyToId = replyContext?.id ?: "",
                            replyToText = when {
                                replyContext == null -> ""
                                replyContext.imageUrl.isNotEmpty() -> "📷 Photo"
                                else -> replyContext.text.take(60)
                            },
                            replyToSender = when {
                                replyContext == null -> ""
                                replyContext.senderId == myId -> myName
                                else -> partnerName
                            },
                            imageUrl = uploadedImageUrl
                        )

                        // Clear inputs immediately for snappy UI
                        text = ""
                        selectedImageUri = null
                        replyingTo = null

                        msgRef.set(msg)
                            .addOnSuccessListener {
                                val notifText = when {
                                    uploadedImageUrl.isNotEmpty() && cleanText.isNotEmpty() -> "📷 $cleanText"
                                    uploadedImageUrl.isNotEmpty() -> "$myName sent a photo 📷"
                                    else -> cleanText
                                }
                                sendNotification(db, partnerId, "New Message 💌", notifText)
                            }
                            .addOnFailureListener { e ->
                                Log.e("ChatScreen", "Failed to send message", e)
                                text = cleanText
                                Toast.makeText(context, "Failed to send message.", Toast.LENGTH_SHORT).show()
                            }
                    } catch (e: Exception) {
                        Log.e("ChatScreen", "Error preparing message", e)
                        Toast.makeText(context, "Error sending photo message.", Toast.LENGTH_SHORT).show()
                    } finally {
                        isSending = false
                    }
                }
            }
        }
    }

    // Reaction selection handler
    val onReactionSelected: (ChatMessage, String) -> Unit = { targetMsg, reactionEmoji ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        activeTapbackMessageId = null
        if (targetMsg.id.isNotEmpty()) {
            val nextReaction = if (targetMsg.reaction == reactionEmoji) "" else reactionEmoji
            db.collection("chats").document(targetMsg.id)
                .update("reaction", nextReaction)
                .addOnSuccessListener {
                    if (nextReaction.isNotEmpty() && partnerId.isNotEmpty() && targetMsg.senderId == partnerId) {
                        sendNotification(db, partnerId, "New Reaction $nextReaction", "$myName reacted to your message!")
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                // Tap outside dismisses active floating tapback bar
                if (activeTapbackMessageId != null) {
                    activeTapbackMessageId = null
                }
            }
    ) {
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
                                Box {
                                    AsyncImage(
                                        model = buildImageRequest(context, partnerPhotoUrl, cacheKey = "avatar_partner"),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                    )
                                    if (partnerStatus == "Online") {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .align(Alignment.BottomEnd)
                                                .clip(CircleShape)
                                                .background(Color(0xFF4CAF50))
                                                .border(1.5.dp, Color.White, CircleShape)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Column {
                                Text(
                                    text = if (isLinked) partnerName else "Love Letters",
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                                Text(
                                    text = when {
                                        isUploadingBg -> "Updating wallpaper..."
                                        !isLinked -> "Not linked yet"
                                        partnerStatus == "Online" -> "Active now"
                                        else -> "Couple Chat"
                                    },
                                    fontSize = 11.sp,
                                    color = if (partnerStatus == "Online") Color(0xFF4CAF50) else Color.White.copy(alpha = 0.75f)
                                )
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
                // Option 1: Floating Cupertino Pill Composer
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // 1. Quoted Reply Preview Banner
                    AnimatedVisibility(
                        visible = replyingTo != null,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        replyingTo?.let { replyMsg ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isCustomTheme) Color.White.copy(alpha = 0.94f) else surfaceColor)
                                    .border(0.5.dp, Color.Black.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(primaryColor)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Replying to ${if (replyMsg.senderId == myId) "yourself" else partnerName}",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryColor
                                    )
                                    Text(
                                        text = if (replyMsg.imageUrl.isNotEmpty()) "📷 Photo" else replyMsg.text,
                                        fontSize = 12.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = cardTextOnLightSurface.copy(alpha = 0.75f)
                                    )
                                }
                                IconButton(
                                    onClick = { replyingTo = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Cancel Reply",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 2. Selected Photo Preview Banner
                    AnimatedVisibility(
                        visible = selectedImageUri != null,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        selectedImageUri?.let { uri ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isCustomTheme) Color.White.copy(alpha = 0.94f) else surfaceColor)
                                    .border(0.5.dp, Color.Black.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Selected Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Photo Attached",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryColor
                                    )
                                    Text(
                                        text = "Ready to send to $partnerName",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                                IconButton(
                                    onClick = { selectedImageUri = null },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove photo",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 3. Floating Pill Input Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(26.dp))
                            .clip(RoundedCornerShape(26.dp))
                            .background(if (isCustomTheme) Color.White.copy(alpha = 0.96f) else surfaceColor)
                            .border(0.5.dp, Color.Black.copy(alpha = 0.10f), RoundedCornerShape(26.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // '+' Attachment Button (Cupertino style)
                        IconButton(
                            onClick = {
                                if (isLinked) {
                                    photoPickerLauncher.launch("image/*")
                                } else {
                                    Toast.makeText(context, "Link with partner in Profile first!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.05f))
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Attach Media",
                                tint = primaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Multi-line Text Field
                        TextField(
                            value = text,
                            onValueChange = { text = it },
                            enabled = isLinked && !isSending,
                            placeholder = {
                                Text(
                                    text = if (isLinked) "iMessage" else "Link partner in Profile...",
                                    fontFamily = FontFamily.Serif,
                                    fontStyle = FontStyle.Italic,
                                    color = Color.Gray,
                                    fontSize = 15.sp
                                )
                            },
                            modifier = Modifier.weight(1f),
                            maxLines = 4,
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
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Serif, fontSize = 15.sp)
                        )

                        // Cupertino Circular Arrow Send Button (animated)
                        val canSend = isLinked && (text.isNotBlank() || selectedImageUri != null) && !isSending
                        AnimatedVisibility(
                            visible = canSend,
                            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                            exit = scaleOut(tween(80)) + fadeOut()
                        ) {
                            IconButton(
                                onClick = onSendMessage,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(primaryColor)
                            ) {
                                Icon(
                                    Icons.Default.ArrowUpward,
                                    contentDescription = "Send",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        ) { padding ->
            if (!isLinked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(32.dp),
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    itemsIndexed(messages, key = { _, msg -> msg.id }) { index, msg ->
                        val isMe = msg.senderId == myId
                        val isLastMessageByMe = isMe && msg.id == lastMyMessageId
                        val bubblePosition = calculateBubblePosition(messages, index)
                        val prevMsg = messages.getOrNull(index - 1)
                        val showDateHeader = shouldShowDateHeader(prevMsg?.timestamp ?: 0L, msg.timestamp)
                        val isTapbackActive = activeTapbackMessageId == msg.id

                        var showDetails by remember { mutableStateOf(false) }

                        // 1. Date Header Pill
                        if (showDateHeader) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp, bottom = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = formatChatDateHeader(msg.timestamp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isCustomTheme) Color.White.copy(alpha = 0.85f) else Color.Gray
                                )
                            }
                        }

                        // 2. Cupertino Swipeable Message Item with Tapback & Reaction
                        CupertinoSwipeableMessageItem(
                            msg = msg,
                            isMe = isMe,
                            bubblePosition = bubblePosition,
                            primaryColor = primaryColor,
                            secondaryColor = secondaryColor,
                            surfaceColor = surfaceColor,
                            isCustomTheme = isCustomTheme,
                            cardTextOnLightSurface = cardTextOnLightSurface,
                            partnerPhotoUrl = partnerPhotoUrl,
                            isTapbackActive = isTapbackActive,
                            showDetails = showDetails,
                            isLastMessageByMe = isLastMessageByMe,
                            chatTimeFmt = chatTimeFmt,
                            onToggleDetails = {
                                if (activeTapbackMessageId != null) {
                                    activeTapbackMessageId = null
                                } else {
                                    showDetails = !showDetails
                                }
                            },
                            onDoubleTapHeart = {
                                val nextReaction = if (msg.reaction == "❤️") "" else "❤️"
                                onReactionSelected(msg, nextReaction)
                            },
                            onLongPressTapback = {
                                activeTapbackMessageId = if (activeTapbackMessageId == msg.id) null else msg.id
                            },
                            onReactionSelected = { reaction ->
                                onReactionSelected(msg, reaction)
                            },
                            onOpenFullReactionPicker = {
                                targetMessageForFullPicker = msg
                                showFullReactionSheet = true
                                activeTapbackMessageId = null
                            },
                            onReply = { replyTarget ->
                                replyingTo = replyTarget
                            },
                            onOpenLightbox = { imgUrl ->
                                lightboxImageUrl = imgUrl
                            }
                        )
                    }
                }
            }
        }

        // Fullscreen Photo Lightbox
        lightboxImageUrl?.let { imgUrl ->
            Dialog(
                onDismissRequest = { lightboxImageUrl = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.94f))
                        .clickable { lightboxImageUrl = null }
                ) {
                    AsyncImage(
                        model = buildImageRequest(context, imgUrl),
                        contentDescription = "Full photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    )
                    IconButton(
                        onClick = { lightboxImageUrl = null },
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(16.dp)
                            .align(Alignment.TopEnd)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }

        // Full Reaction Sheet (opened via '+' icon in Tapback Bar)
        if (showFullReactionSheet) {
            val targetMsg = targetMessageForFullPicker
            ModalBottomSheet(
                onDismissRequest = { showFullReactionSheet = false },
                sheetState = sheetState,
                containerColor = surfaceColor
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "React to message",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        fontSize = 16.sp,
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
                                        if (targetMsg != null) {
                                            onReactionSelected(targetMsg, emoji)
                                        }
                                        showFullReactionSheet = false
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
}

/**
 * Cupertino-Style Floating Tapback Reaction Bar
 * Features iconic Apple Tapback emojis (❤️ 👍 👎 😂 ‼️ ❓ +) in a frosted glass floating pill.
 */
@Composable
fun CupertinoTapbackBar(
    currentReaction: String,
    onReactionSelected: (String) -> Unit,
    onMoreClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val tapbackIcons = remember { listOf("❤️", "👍", "👎", "😂", "‼️", "❓") }

    Box(
        modifier = modifier
            .shadow(6.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.96f))
            .border(0.5.dp, Color.Black.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tapbackIcons.forEach { icon ->
                val isSelected = currentReaction == icon
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Color(0xFF007AFF).copy(alpha = 0.20f) else Color.Transparent)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onReactionSelected(icon)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(icon, fontSize = 20.sp)
                }
            }

            // '+' Button for full reaction catalog
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.06f))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onMoreClicked()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "More Reactions",
                    tint = Color.DarkGray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Individual Cupertino-style message item:
 * - Contiguous bubble corner geometry matching iMessage (SINGLE, FIRST, MIDDLE, LAST)
 * - Swipe-to-reply gesture pulling a reply arrow with haptic click
 * - Double-tap to toggle heart reaction
 * - Long-press to display the floating Cupertino Tapback bar
 * - Quoted reply snippet rendering
 * - In-chat photo rendering with lightbox zoom
 * - Attached mini reaction badge
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CupertinoSwipeableMessageItem(
    msg: ChatMessage,
    isMe: Boolean,
    bubblePosition: BubblePosition,
    primaryColor: Color,
    secondaryColor: Color,
    surfaceColor: Color,
    isCustomTheme: Boolean,
    cardTextOnLightSurface: Color,
    partnerPhotoUrl: String,
    isTapbackActive: Boolean,
    showDetails: Boolean,
    isLastMessageByMe: Boolean,
    chatTimeFmt: SimpleDateFormat,
    onToggleDetails: () -> Unit,
    onDoubleTapHeart: () -> Unit,
    onLongPressTapback: () -> Unit,
    onReactionSelected: (String) -> Unit,
    onOpenFullReactionPicker: () -> Unit,
    onReply: (ChatMessage) -> Unit,
    onOpenLightbox: (String) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var swipeOffsetX by remember { mutableFloatStateOf(0f) }
    val animatedSwipeOffset by animateFloatAsState(
        targetValue = swipeOffsetX,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "swipeOffset"
    )

    // Cupertino contiguous tail shapes
    val bubbleShape = remember(isMe, bubblePosition) {
        if (isMe) {
            when (bubblePosition) {
                BubblePosition.SINGLE -> RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
                BubblePosition.FIRST -> RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp)
                BubblePosition.MIDDLE -> RoundedCornerShape(18.dp, 6.dp, 6.dp, 18.dp)
                BubblePosition.LAST -> RoundedCornerShape(18.dp, 6.dp, 4.dp, 18.dp)
            }
        } else {
            when (bubblePosition) {
                BubblePosition.SINGLE -> RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
                BubblePosition.FIRST -> RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
                BubblePosition.MIDDLE -> RoundedCornerShape(6.dp, 18.dp, 18.dp, 6.dp)
                BubblePosition.LAST -> RoundedCornerShape(6.dp, 18.dp, 18.dp, 4.dp)
            }
        }
    }

    val bubbleBrush = remember(isMe, primaryColor, secondaryColor) {
        if (isMe) {
            Brush.verticalGradient(
                listOf(
                    primaryColor,
                    primaryColor.copy(alpha = 0.92f),
                    secondaryColor.copy(alpha = 0.88f)
                )
            )
        } else {
            null
        }
    }

    val bubbleBgColor = if (isMe) {
        primaryColor
    } else {
        if (isCustomTheme) Color.White.copy(alpha = 0.94f) else Color(0xFFE9E9EB)
    }

    val verticalPadding = when (bubblePosition) {
        BubblePosition.SINGLE -> 4.dp
        BubblePosition.FIRST -> 3.dp
        BubblePosition.MIDDLE -> 1.5.dp
        BubblePosition.LAST -> 3.dp
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding)
    ) {
        // Swipe-to-reply background indicator
        if (animatedSwipeOffset > 6f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .size(32.dp)
                    .graphicsLayer(
                        scaleX = (animatedSwipeOffset / 48f).coerceIn(0.4f, 1.15f),
                        scaleY = (animatedSwipeOffset / 48f).coerceIn(0.4f, 1.15f),
                        alpha = (animatedSwipeOffset / 38f).coerceIn(0f, 1f)
                    )
                    .clip(CircleShape)
                    .background(primaryColor.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Reply,
                    contentDescription = "Reply",
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        // Main Message Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedSwipeOffset.roundToInt(), 0) }
                .pointerInput(msg.id) {
                    detectHorizontalDragGestures(
                        onDragStart = {},
                        onDragEnd = {
                            if (swipeOffsetX >= 48f) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onReply(msg)
                            }
                            swipeOffsetX = 0f
                        },
                        onDragCancel = { swipeOffsetX = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            val newOffset = (swipeOffsetX + dragAmount * 0.55f).coerceIn(0f, 68f)
                            if (swipeOffsetX < 48f && newOffset >= 48f) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            swipeOffsetX = newOffset
                        }
                    )
                },
            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            // Partner Avatar (displayed only on the LAST or SINGLE bubble of a cluster)
            if (!isMe) {
                if (bubblePosition == BubblePosition.LAST || bubblePosition == BubblePosition.SINGLE) {
                    if (partnerPhotoUrl.isNotEmpty()) {
                        AsyncImage(
                            model = buildImageRequest(context, partnerPhotoUrl, cacheKey = "avatar_partner"),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.LightGray)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Message Bubble & Floating Tapback Column
            Column(
                horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                // Floating Cupertino Tapback Bar
                AnimatedVisibility(
                    visible = isTapbackActive,
                    enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit = scaleOut(tween(100)) + fadeOut(),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    CupertinoTapbackBar(
                        currentReaction = msg.reaction,
                        onReactionSelected = onReactionSelected,
                        onMoreClicked = onOpenFullReactionPicker
                    )
                }

                Box {
                    Box(
                        modifier = Modifier
                            .clip(bubbleShape)
                            .then(
                                if (bubbleBrush != null) Modifier.background(bubbleBrush)
                                else Modifier.background(bubbleBgColor)
                            )
                            .border(
                                width = if (!isMe && isCustomTheme) 0.5.dp else 0.dp,
                                color = Color.White.copy(alpha = 0.4f),
                                shape = bubbleShape
                            )
                            .combinedClickable(
                                onClick = onToggleDetails,
                                onDoubleClick = onDoubleTapHeart,
                                onLongClick = onLongPressTapback
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column {
                            // Embedded Quoted Reply Snippet
                            if (msg.replyToId.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .padding(bottom = 6.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isMe) Color.White.copy(alpha = 0.22f)
                                            else Color.Black.copy(alpha = 0.07f)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.5.dp)
                                            .height(24.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(if (isMe) Color.White else primaryColor)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = msg.replyToSender.ifEmpty { "Reply" },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isMe) Color.White.copy(alpha = 0.9f) else primaryColor
                                        )
                                        Text(
                                            text = msg.replyToText,
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (isMe) Color.White.copy(alpha = 0.8f) else cardTextOnLightSurface.copy(alpha = 0.75f)
                                        )
                                    }
                                }
                            }

                            // Shared In-Chat Photo
                            if (msg.imageUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = buildImageRequest(context, msg.imageUrl),
                                    contentDescription = "Shared photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 140.dp, max = 240.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onOpenLightbox(msg.imageUrl) }
                                )
                                if (msg.text.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }

                            // Text Content
                            if (msg.text.isNotEmpty()) {
                                Text(
                                    text = msg.text,
                                    color = if (isMe) Color.White else cardTextOnLightSurface,
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 15.5.sp,
                                    lineHeight = 21.sp
                                )
                            }
                        }
                    }

                    // Attached Mini Tapback Reaction Badge (iMessage corner bubble)
                    if (msg.reaction.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(if (isMe) Alignment.BottomStart else Alignment.BottomEnd)
                                .offset(
                                    y = 8.dp,
                                    x = if (isMe) (-4).dp else 4.dp
                                )
                                .shadow(3.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(0.5.dp, Color.LightGray.copy(alpha = 0.5f), CircleShape)
                                .clickable {
                                    onReactionSelected("")
                                }
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(msg.reaction, fontSize = 12.sp)
                        }
                    }
                }

                // Inline Timestamp & iMessage Status
                AnimatedVisibility(visible = showDetails || isLastMessageByMe) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = chatTimeFmt.format(Date(msg.timestamp)),
                            fontSize = 10.5.sp,
                            color = if (isCustomTheme) Color.White.copy(0.75f) else Color.Gray
                        )
                        if (isLastMessageByMe) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (msg.isRead) "• Read" else "• Delivered",
                                fontSize = 10.5.sp,
                                fontWeight = if (msg.isRead) FontWeight.Bold else FontWeight.Normal,
                                color = if (msg.isRead) secondaryColor else Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

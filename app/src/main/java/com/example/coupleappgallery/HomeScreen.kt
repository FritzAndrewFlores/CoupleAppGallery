package com.example.coupleappgallery

import android.app.DatePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.annotation.Keep
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.tasks.await
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.core.content.edit
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.random.Random

// --- DATA MODELS ---
data class ActivityItem(val id: String, val title: String, val body: String, val timestamp: Long)

@Keep
data class PartnerRequest(
    val id: String = "",
    val requesterId: String = "",
    val receiverId: String = "",
    val emoji: String = "",
    val label: String = "",
    val timestamp: Long = 0
)

enum class HomeCardType { MOOD, REQUESTS, NOTE, WISHLIST }

val DefaultHomeCardOrder = listOf(
    HomeCardType.WISHLIST,
    HomeCardType.MOOD,
    HomeCardType.REQUESTS,
    HomeCardType.NOTE
)

fun loadHomeCardOrder(prefs: SharedPreferences, userId: String): List<HomeCardType> {
    val key = if (userId.isNotEmpty()) "card_order_$userId" else "card_order_default"
    val saved = prefs.getString(key, null) ?: return DefaultHomeCardOrder
    val savedList = saved.split(",")
        .mapNotNull { name ->
            try {
                HomeCardType.valueOf(name.trim())
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    val missing = DefaultHomeCardOrder.filterNot { it in savedList }
    val fullList = (savedList + missing).distinct()
    return if (fullList.isNotEmpty()) fullList else DefaultHomeCardOrder
}

fun saveHomeCardOrder(prefs: SharedPreferences, userId: String, order: List<HomeCardType>) {
    val key = if (userId.isNotEmpty()) "card_order_$userId" else "card_order_default"
    val serialized = order.joinToString(",") { it.name }
    prefs.edit { putString(key, serialized) }
}

data class Particle(val id: Long, val emoji: String, val startX: Float)

// --- EXTENDED CARD STYLES ENGINE ---
data class CardBackground(val id: String, val name: String, val colors: List<Color>, val darkText: Boolean)

val availableCardBackgrounds = listOf(
    // Glassmorphism
    CardBackground("glass_light", "Clear Glass", listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.6f)), true),
    CardBackground("glass_dark", "Dark Glass", listOf(Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.6f)), false),

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

val FolderColors = listOf(
    Color(0xFFFFF8E7),
    Color(0xFFF8BBD0),
    Color(0xFFE1BEE7),
    Color(0xFFC5CAE9),
    Color(0xFFB2DFDB),
    Color(0xFFC8E6C9),
    Color(0xFFFFF9C4),
    Color(0xFFFFCCBC)
)

fun Modifier.glassCard(opacity: Float = 0.6f) = this
    .clip(RoundedCornerShape(24.dp))
    .background(Color.White.copy(alpha = opacity))
    .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(24.dp))

@Composable
fun Modifier.bouncyHover(
    haptic: HapticFeedback? = null,
    scaleOnHover: Float = 1.035f,
    scaleOnPress: Float = 0.95f,
    enabled: Boolean = true,
    onStateChange: ((Boolean) -> Unit)? = null
): Modifier {
    var isPointerActive by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val currentEnabled by rememberUpdatedState(enabled)

    LaunchedEffect(enabled) {
        if (!enabled) {
            isPointerActive = false
        }
    }

    val isActive = enabled && (isPointerActive || isHovered || isPressed)

    LaunchedEffect(isActive) {
        if (isActive) {
            haptic?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        onStateChange?.invoke(isActive)
    }

    val targetScale = when {
        !enabled -> 1f
        isPressed -> scaleOnPress
        isActive -> scaleOnHover
        else -> 1f
    }

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bouncyHoverScale"
    )

    return this
        .hoverable(interactionSource, enabled = enabled)
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (!currentEnabled) {
                        if (isPointerActive) isPointerActive = false
                        continue
                    }
                    val change = event.changes.firstOrNull()
                    when (event.type) {
                        PointerEventType.Enter -> isPointerActive = true
                        PointerEventType.Exit -> isPointerActive = false
                        PointerEventType.Press -> isPointerActive = true
                        PointerEventType.Release -> isPointerActive = false
                        PointerEventType.Move -> {
                            if (change != null) {
                                val pos = change.position
                                val inside = pos.x in 0f..size.width.toFloat() && pos.y in 0f..size.height.toFloat()
                                if (inside != isPointerActive) {
                                    isPointerActive = inside
                                }
                            }
                        }
                        else -> {
                            if (change != null && !change.pressed) {
                                isPointerActive = false
                            }
                        }
                    }
                }
            }
        }
        .graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
        }
}

@Composable
fun FloatingFrostedCapsuleTabBar(
    pagerState: androidx.compose.foundation.pager.PagerState,
    currentTheme: AppTheme,
    onTabSelected: (Int) -> Unit
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val isCustom = currentTheme.id == "custom"
    val isDark = currentTheme.primary.luminance() < 0.55f

    val capsuleBg = if (isCustom) {
        Color.Black.copy(alpha = 0.40f)
    } else if (isDark) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }
    val capsuleBorder = if (isCustom) {
        Color.White.copy(alpha = 0.30f)
    } else {
        Color.White.copy(alpha = 0.45f)
    }

    val pillGradient = Brush.horizontalGradient(
        listOf(
            currentTheme.primary,
            currentTheme.secondary
        )
    )
    val pillTextColor = if (currentTheme.primary.luminance() > 0.6f) Color(0xFF1E293B) else Color.White
    val inactiveTextColor = if (isCustom) Color.White.copy(alpha = 0.70f) else currentTheme.text.copy(alpha = 0.65f)

    val isTogether = pagerState.currentPage == 1
    val isDragging = pagerState.isScrollInProgress

    // Spring bouncy progress for indicator pill (smooth drag tracking + bouncy overshoot on tap/release)
    val animatedProgress by animateFloatAsState(
        targetValue = (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, 1f),
        animationSpec = if (isDragging) {
            spring(stiffness = Spring.StiffnessHigh)
        } else {
            spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "tabBarPillProgress"
    )

    // Bouncy scale for tab icons and text (matching the Polaroid/Filmstrip effect)
    val albumsScale by animateFloatAsState(
        targetValue = if (!isTogether) 1.05f else 0.95f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "albumsScale"
    )
    val togetherScale by animateFloatAsState(
        targetValue = if (isTogether) 1.05f else 0.95f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "togetherScale"
    )

    // Smooth color crossfade
    val albumsTextColor by animateColorAsState(
        targetValue = if (!isTogether) pillTextColor else inactiveTextColor,
        animationSpec = tween(220),
        label = "albumsTextColor"
    )
    val togetherTextColor by animateColorAsState(
        targetValue = if (isTogether) pillTextColor else inactiveTextColor,
        animationSpec = tween(220),
        label = "togetherTextColor"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(capsuleBg)
                .border(1.dp, capsuleBorder, RoundedCornerShape(24.dp))
                .padding(4.dp)
        ) {
            val totalWidthPx = constraints.maxWidth.toFloat()
            val tabWidthPx = totalWidthPx / 2f
            val tabWidthDp = with(density) { tabWidthPx.toDp() }

            // Sliding Active Pill Indicator (RenderNode GPU-accelerated spring-bouncy translation)
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = animatedProgress * tabWidthPx
                    }
                    .width(tabWidthDp)
                    .fillMaxHeight()
                    .shadow(elevation = 3.dp, shape = RoundedCornerShape(20.dp), clip = false)
                    .clip(RoundedCornerShape(20.dp))
                    .background(pillGradient)
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tab 0: Albums
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            if (isTogether) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTabSelected(0)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.graphicsLayer {
                            scaleX = albumsScale
                            scaleY = albumsScale
                        }
                    ) {
                        Text(
                            text = "📸",
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Albums",
                            fontFamily = FontFamily.Serif,
                            fontSize = 13.5.sp,
                            fontWeight = if (!isTogether) FontWeight.Bold else FontWeight.Medium,
                            color = albumsTextColor
                        )
                    }
                }

                // Tab 1: Together
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            if (!isTogether) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTabSelected(1)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.graphicsLayer {
                            scaleX = togetherScale
                            scaleY = togetherScale
                        }
                    ) {
                        Text(
                            text = "💌",
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Together",
                            fontFamily = FontFamily.Serif,
                            fontSize = 13.5.sp,
                            fontWeight = if (isTogether) FontWeight.Bold else FontWeight.Medium,
                            color = togetherTextColor
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, currentTheme: AppTheme) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val folders = remember { mutableStateListOf<CoupleFolder>() }
    val albumsListState = rememberLazyListState()
    val togetherListState = rememberLazyListState()

    // UI State
    var showDialog by remember { mutableStateOf(false) }
    var folderToEdit by remember { mutableStateOf<CoupleFolder?>(null) }
    var folderToDelete by remember { mutableStateOf<CoupleFolder?>(null) }
    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    var activeDragCard by remember { mutableStateOf<HomeCardType?>(null) }
    var showColorPickerFor by remember { mutableStateOf<HomeCardType?>(null) }
    var showHistorySheet by remember { mutableStateOf(false) }

    val hapticFeedback = LocalHapticFeedback.current
    LaunchedEffect(pagerState.settledPage) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

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
            val baseId = System.nanoTime()
            val newParticles = (0..4).map { idx ->
                Particle(baseId + idx, emoji, Random.nextFloat())
            }
            particles.addAll(newParticles)
            delay(2000)
            particles.removeAll(newParticles)
        }
    }

    // Pattern D: singletons — retrieve once per composition lifetime
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val storage = remember { FirebaseStorage.getInstance() }
    val userId = auth.currentUser?.uid ?: ""
    val partnerId = SessionCache.partnerId

    val sharedPreferences = remember { context.getSharedPreferences("couple_app_prefs", Context.MODE_PRIVATE) }
    var albumViewMode by remember {
        mutableStateOf(sharedPreferences.getString("album_view_mode", "polaroid") ?: "polaroid")
    }
    val initialOrder = remember(userId) { loadHomeCardOrder(sharedPreferences, userId) }
    val cardOrder = remember(initialOrder) { mutableStateListOf(*initialOrder.toTypedArray()) }
    val initialExpanded = remember(userId) {
        HomeCardType.entries.associateWith { type ->
            sharedPreferences.getBoolean("card_expanded_${userId}_${type.name}", true)
        }
    }
    val expandedStates = remember(initialExpanded) {
        mutableStateMapOf<HomeCardType, Boolean>().apply { putAll(initialExpanded) }
    }

    val primaryColor = currentTheme.primary
    val backgroundColor = currentTheme.background
    val textColor = currentTheme.text

    val reactions = HomeReactions
    val requestOptions = RequestOptions

    DisposableEffect(userId, partnerId) {
        if (userId.isEmpty() || partnerId.isEmpty()) {
            activeRequests = emptyList()
            return@DisposableEffect onDispose { }
        }
        val reg = db.collection("requests")
            .whereEqualTo("receiverId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FS", "listener@HomeScreen:requests", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    activeRequests = snapshot.documents
                        .mapNotNull { doc ->
                            doc.toObject(PartnerRequest::class.java)?.let {
                                if (it.id.isEmpty()) it.copy(id = doc.id) else it
                            }
                        }
                        .filter { it.requesterId == partnerId }
                }
            }
        onDispose { reg.remove() }
    }

    // Uses deployed composite index (receiverId ASC, isRead ASC) so only unread incoming messages are streamed
    DisposableEffect(userId, partnerId) {
        if (userId.isEmpty() || partnerId.isEmpty()) {
            unreadCount = 0
            return@DisposableEffect onDispose { }
        }
        val reg = db.collection("chats")
            .whereEqualTo("receiverId", userId)
            .whereEqualTo("isRead", false)
            .addSnapshotListener { s, error ->
                if (error != null) {
                    Log.e("FS", "listener@HomeScreen:unread", error)
                    return@addSnapshotListener
                }
                if (s != null) {
                    unreadCount = s.documents.count {
                        it.getString("senderId") == partnerId
                    }
                }
            }
        onDispose { reg.remove() }
    }

    DisposableEffect(userId) {
        if (userId.isEmpty()) return@DisposableEffect onDispose { }
        val reg = db.collection("notifications")
            .whereEqualTo("to", userId)
            .addSnapshotListener { s, error ->
                if (error != null) {
                    Log.e("FS", "listener@HomeScreen:notifications", error)
                    return@addSnapshotListener
                }
                if (s != null) {
                    historyItems = s.documents.map {
                        ActivityItem(
                            it.id,
                            it.getString("title") ?: "",
                            it.getString("body") ?: "",
                            it.getLong("timestamp") ?: 0L
                        )
                    }.sortedByDescending { it.timestamp }
                }
            }
        onDispose { reg.remove() }
    }

    DisposableEffect(userId) {
        if (userId.isEmpty()) return@DisposableEffect onDispose { }
        val reg = db.collection("folders")
            .whereArrayContains("participants", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { s, error ->
                if (error != null) {
                    Log.e("FS", "listener@HomeScreen:folders", error)
                    return@addSnapshotListener
                }
                if (s != null) {
                    val loadedFolders = s.documents.mapNotNull { doc ->
                        doc.toObject(CoupleFolder::class.java)?.let {
                            val folder = if (it.id.isEmpty()) it.copy(id = doc.id) else it
                            if (folder.coverPhotoUrl.isNotEmpty()) {
                                FolderCoverCache.setCover(folder.id, folder.coverPhotoUrl)
                            }
                            if (folder.photoCount > 0) {
                                FolderCoverCache.setCount(folder.id, folder.photoCount)
                            }
                            folder
                        }
                    }
                    folders.clear()
                    folders.addAll(loadedFolders)
                }
            }
        onDispose { reg.remove() }
    }

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        if (currentTheme.id == "custom" && currentTheme.wallpaperUrl.isNotEmpty()) {
            AsyncImage(
                model = buildImageRequest(LocalContext.current, currentTheme.wallpaperUrl, cacheKey = "theme_wallpaper"),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
        }

        particles.forEach { particle ->
            key(particle.id) {
                FloatingParticle(particle)
            }
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    "Couple Sync",
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val isOnline = partnerId.isNotEmpty() && partnerStatus == "Online"
                                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                    val pulseAlpha by infiniteTransition.animateFloat(
                                        initialValue = 0.4f,
                                        targetValue = 1f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(1000),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "pulseAlpha"
                                    )
                                    val statusColor = if (isOnline) Color.Green else Color.Gray
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .graphicsLayer {
                                                this.alpha = if (isOnline) pulseAlpha else 1f
                                            }
                                            .background(statusColor, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (partnerId.isEmpty()) "Not linked yet" else "$partnerName is $partnerStatus",
                                        fontSize = 12.sp,
                                        color = if (currentTheme.id == "custom") Color.White.copy(0.8f) else textColor.copy(0.7f)
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = if (currentTheme.id == "custom") Color.Black.copy(0.3f) else backgroundColor.copy(alpha = 0.9f),
                            titleContentColor = if (currentTheme.id == "custom") Color.White else primaryColor
                        ),
                        actions = {
                            val iconTint = if (currentTheme.id == "custom") Color.White else primaryColor
                            IconButton(onClick = { showHistorySheet = true }) {
                                Icon(Icons.Default.Notifications, "History", tint = iconTint)
                            }
                            IconButton(onClick = { navController.navigate("chat") }) {
                                if (unreadCount > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge(containerColor = Color.Red, contentColor = Color.White) {
                                                Text(if (unreadCount > 99) "99+" else "$unreadCount")
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Email, "Chat", tint = iconTint)
                                    }
                                } else {
                                    Icon(Icons.Default.Email, "Chat", tint = iconTint)
                                }
                            }
                            IconButton(onClick = { navController.navigate("profile") }) {
                                if (profilePicUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = buildImageRequest(LocalContext.current, profilePicUrl, cacheKey = "avatar_me"),
                                        contentDescription = "Profile",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(28.dp).clip(CircleShape)
                                    )
                                } else {
                                    Icon(Icons.Default.Person, "Profile", tint = iconTint)
                                }
                            }
                            IconButton(onClick = { navController.navigate("settings") }) {
                                Icon(Icons.Default.Settings, "Settings", tint = iconTint)
                            }
                        }
                    )
                    FloatingFrostedCapsuleTabBar(
                        pagerState = pagerState,
                        currentTheme = currentTheme,
                        onTabSelected = { page ->
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(page)
                            }
                        }
                    )
                }
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = pagerState.currentPage == 0,
                    enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit = scaleOut(spring(dampingRatio = Spring.DampingRatioNoBouncy)) + fadeOut()
                ) {
                    val isFabExpanded by remember { derivedStateOf { !albumsListState.isScrollInProgress } }
                    val fabInteractionSource = remember { MutableInteractionSource() }
                    val isPressed by fabInteractionSource.collectIsPressedAsState()
                    val fabPressScale by animateFloatAsState(
                        targetValue = if (isPressed) 0.90f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "fabPressScale"
                    )
                    val fabPaddingHorizontal by animateDpAsState(
                        targetValue = if (isFabExpanded) 16.dp else 14.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
                        label = "fabPadding"
                    )
                    val fabContentColor = if (primaryColor.luminance() > 0.6f) Color(0xFF1E293B) else Color.White
                    val fabBorderColor = if (currentTheme.id == "custom") Color.White.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.35f)
                    val fabGradient = Brush.linearGradient(
                        listOf(
                            primaryColor,
                            currentTheme.secondary
                        )
                    )

                    Surface(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            folderToEdit = null
                            showDialog = true
                        },
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Transparent,
                        interactionSource = fabInteractionSource,
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = fabPressScale
                                scaleY = fabPressScale
                            }
                            .shadow(
                                elevation = 6.dp,
                                shape = RoundedCornerShape(24.dp),
                                clip = false
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .height(48.dp)
                                .widthIn(min = 48.dp)
                                .background(fabGradient)
                                .border(1.dp, fabBorderColor, RoundedCornerShape(24.dp))
                                .padding(horizontal = fabPaddingHorizontal),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "New Album",
                                    tint = fabContentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                AnimatedVisibility(
                                    visible = isFabExpanded,
                                    enter = fadeIn(animationSpec = tween(180)) + expandHorizontally(
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
                                    ),
                                    exit = fadeOut(animationSpec = tween(120)) + shrinkHorizontally(
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy)
                                    )
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "New Album",
                                            fontFamily = FontFamily.Serif,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = fabContentColor,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "✨",
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    key = { page -> page },
                    userScrollEnabled = (activeDragCard == null),
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    if (page == 0) {
                        // ALBUMS TAB: folder grid
                        LazyColumn(
                            state = albumsListState,
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val anniv = SessionCache.anniversaryDate
                            if (anniv > 0L) {
                                item {
                                    CompactAnniversaryBar(
                                        theme = currentTheme,
                                        anniversaryDate = anniv,
                                        onClick = {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(1)
                                            }
                                        }
                                    )
                                }
                            }
                        if (partnerId.isEmpty()) {
                            item {
                                val bannerColor = if (currentTheme.id == "custom") Color.White else primaryColor
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (currentTheme.id == "custom") Color.Black.copy(alpha = 0.45f)
                                            else primaryColor.copy(alpha = 0.12f)
                                        )
                                        .border(1.dp, bannerColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = "Not linked with a partner yet — new albums won't be shared. Link in Profile.",
                                        fontSize = 13.sp,
                                        color = bannerColor,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        if (folders.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No albums yet. Tap + to start your first adventure!",
                                        color = if (currentTheme.id == "custom") Color.White.copy(alpha = 0.85f) else Color.Gray,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            item {
                                AlbumViewSwitcher(
                                    selectedMode = albumViewMode,
                                    currentTheme = currentTheme,
                                    onModeSelected = { newMode ->
                                        albumViewMode = newMode
                                        sharedPreferences.edit().putString("album_view_mode", newMode).apply()
                                    }
                                )
                            }
                            itemsIndexed(folders.chunked(2), key = { _, row -> row.first().id }) { rowIndex, rowFolders ->
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    for ((colIndex, folder) in rowFolders.withIndex()) {
                                        val globalIndex = rowIndex * 2 + colIndex
                                        Box(modifier = Modifier.weight(1f)) {
                                            FolderItem(
                                                folder = folder,
                                                index = globalIndex,
                                                viewMode = albumViewMode,
                                                currentTheme = currentTheme,
                                                onDelete = { folderToDelete = folder },
                                                onEdit = { folderToEdit = folder; showDialog = true },
                                                onClick = {
                                                    if (folder.id.isNotEmpty()) {
                                                        navController.navigate("gallery/${folder.id}")
                                                    }
                                                }
                                            )
                                        }
                                    }
                                    if (rowFolders.size == 1) Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(60.dp)) }
                    }
                } else {
                    // TOGETHER TAB: Option 1 — Romance Bento Grid
                    LazyColumn(
                        state = togetherListState,
                        userScrollEnabled = (activeDragCard == null),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. Hero Anniversary Banner
                        item(key = "anniversary_hero") {
                            AnniversaryHeroBanner(
                                theme = currentTheme,
                                anniversaryDate = SessionCache.anniversaryDate,
                                onTriggerParticles = triggerParticles,
                                onSetAnniversary = { pickedMillis ->
                                    if (userId.isNotEmpty()) {
                                        db.collection("users").document(userId)
                                            .update("anniversaryDate", pickedMillis)
                                        if (partnerId.isNotEmpty()) {
                                            db.collection("users").document(partnerId)
                                                .update("anniversaryDate", pickedMillis)
                                            sendNotification(
                                                db,
                                                partnerId,
                                                "Anniversary Set 💕",
                                                "$myName updated your relationship anniversary date!"
                                            )
                                        }
                                        Toast.makeText(context, "Anniversary saved! 💕", Toast.LENGTH_SHORT).show()
                                        triggerParticles("💖")
                                    }
                                }
                            )
                        }

                        // 2. Compact "Your Status" Pill Bar
                        item(key = "your_status_pill") {
                            CompactYourStatusPill(
                                theme = currentTheme,
                                db = db,
                                context = context,
                                onTriggerParticles = triggerParticles
                            )
                        }

                        // 3. Asymmetric 2-Column Bento Grid: Partner's Vibe + Love Note Postcard
                        item(key = "bento_partner_grid") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                BentoMoodTile(
                                    modifier = Modifier.weight(1f),
                                    mood = partnerMood,
                                    partner = partnerName,
                                    partnerStatus = partnerStatus,
                                    partnerPhotoUrl = SessionCache.partnerPhotoUrl,
                                    partnerId = partnerId,
                                    reactions = reactions,
                                    theme = currentTheme,
                                    myName = myName,
                                    db = db,
                                    context = context,
                                    onTrigger = triggerParticles,
                                    bg = moodBg,
                                    onColorPickClick = { showColorPickerFor = HomeCardType.MOOD }
                                )

                                BentoNoteTile(
                                    modifier = Modifier.weight(1f),
                                    note = partnerNote,
                                    partner = partnerName,
                                    partnerId = partnerId,
                                    theme = currentTheme,
                                    bg = noteBg,
                                    onColorPickClick = { showColorPickerFor = HomeCardType.NOTE }
                                )
                            }
                        }

                        // 4. Interactive Wants & Requests Ribbon
                        item(key = "bento_wants_ribbon") {
                            BentoRequestRibbon(
                                options = requestOptions,
                                partnerId = partnerId,
                                partnerName = partnerName,
                                myName = myName,
                                theme = currentTheme,
                                db = db,
                                context = context,
                                onTrigger = triggerParticles,
                                bg = requestBg,
                                onColorPickClick = { showColorPickerFor = HomeCardType.REQUESTS }
                            )
                        }

                        // 5. Active Granted Partner Wishes Ribbon (if any active requests from partner)
                        if (activeRequests.isNotEmpty()) {
                            item(key = "bento_active_wishes_ribbon") {
                                BentoWishlistRibbon(
                                    requests = activeRequests,
                                    partnerName = partnerName,
                                    partnerId = partnerId,
                                    myName = myName,
                                    db = db,
                                    theme = currentTheme,
                                    primary = primaryColor,
                                    onTrigger = triggerParticles,
                                    bg = wishlistBg,
                                    onColorPickClick = { showColorPickerFor = HomeCardType.WISHLIST }
                                )
                            }
                        }

                        item { Spacer(modifier = Modifier.height(36.dp)) }
                    }
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
            HistoryTab(db, userId, currentTheme, primaryColor, textColor, historyItems)
        }
    }

    // COLOR PICKER DIALOG (safe local capture prevents C1 double-tap race crash)
    showColorPickerFor?.let { targetCard ->
        val currentSelection = when (targetCard) {
            HomeCardType.MOOD -> moodBgId
            HomeCardType.REQUESTS -> requestBgId
            HomeCardType.NOTE -> noteBgId
            HomeCardType.WISHLIST -> wishlistBgId
        }
        Dialog(onDismissRequest = { showColorPickerFor = null }) {
            Box(modifier = Modifier.fillMaxWidth().height(500.dp).glassCard(0.95f).padding(16.dp)) {
                Column {
                    Text(
                        "Select Card Style",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableCardBackgrounds, key = { it.id }) { bg ->
                            val isSelected = bg.id == currentSelection
                            Box(
                                modifier = Modifier
                                    .height(60.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Brush.linearGradient(bg.colors))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        val field = when (targetCard) {
                                            HomeCardType.MOOD -> "moodBgId"
                                            HomeCardType.REQUESTS -> "requestBgId"
                                            HomeCardType.NOTE -> "noteBgId"
                                            HomeCardType.WISHLIST -> "wishlistBgId"
                                        }
                                        showColorPickerFor = null
                                        if (userId.isNotEmpty()) {
                                            db.collection("users").document(userId).update(field, bg.id)
                                        }
                                    }
                            ) {
                                Text(
                                    bg.name,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (bg.darkText) Color.Black else Color.White,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.align(Alignment.Center).padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        val editingTarget = folderToEdit
        FolderDialog(
            currentTheme = currentTheme,
            folderToEdit = editingTarget,
            onDismiss = { showDialog = false }
        ) { name, icon, date, isShared, color ->
            if (userId.isEmpty()) {
                showDialog = false
                return@FolderDialog
            }
            if (editingTarget == null) {
                val cachedPartnerId = partnerId
                if (isShared && cachedPartnerId.isEmpty()) {
                    db.collection("users").document(userId).get()
                        .addOnSuccessListener { doc ->
                            val freshPartnerId = doc.getString("partnerId") ?: ""
                            val validParticipants = listOfNotNull(userId, freshPartnerId.ifEmpty { null }).distinct()
                            val ref = db.collection("folders").document()
                            val id = ref.id
                            ref.set(
                                CoupleFolder(
                                    id = id,
                                    name = name,
                                    iconName = icon,
                                    date = date,
                                    participants = validParticipants,
                                    color = color,
                                    createdAt = System.currentTimeMillis(),
                                    ownerId = userId
                                )
                            )
                            if (freshPartnerId.isNotEmpty()) {
                                sendNotification(db, freshPartnerId, "New Adventure 📂", "$myName created folder: $name")
                            }
                        }
                } else {
                    val validParticipants = if (isShared) {
                        listOfNotNull(userId, cachedPartnerId.ifEmpty { null }).distinct()
                    } else {
                        listOf(userId)
                    }
                    val ref = db.collection("folders").document()
                    val id = ref.id
                    ref.set(
                        CoupleFolder(
                            id = id,
                            name = name,
                            iconName = icon,
                            date = date,
                            participants = validParticipants,
                            color = color,
                            createdAt = System.currentTimeMillis(),
                            ownerId = userId
                        )
                    )
                    if (isShared && cachedPartnerId.isNotEmpty()) {
                        sendNotification(db, cachedPartnerId, "New Adventure 📂", "$myName created folder: $name")
                    }
                }
            } else {
                val effectiveOwnerId = editingTarget.ownerId.ifEmpty { editingTarget.participants.firstOrNull() ?: userId }
                val updateMap = mutableMapOf<String, Any>(
                    "name" to name,
                    "iconName" to icon,
                    "date" to date,
                    "color" to color
                )
                val nowSharedWithPartner: Boolean
                if (effectiveOwnerId == userId) {
                    updateMap["ownerId"] = effectiveOwnerId
                    val updatedParticipants = if (isShared && partnerId.isNotEmpty()) {
                        listOf(effectiveOwnerId, partnerId).distinct()
                    } else {
                        listOf(effectiveOwnerId)
                    }
                    updateMap["participants"] = updatedParticipants
                    nowSharedWithPartner = partnerId.isNotEmpty() && updatedParticipants.contains(partnerId)
                } else {
                    nowSharedWithPartner = partnerId.isNotEmpty() && editingTarget.participants.contains(partnerId)
                }
                if (editingTarget.id.isNotEmpty()) {
                    db.collection("folders").document(editingTarget.id).update(updateMap)
                }
                if (nowSharedWithPartner) {
                    sendNotification(db, partnerId, "Updated Folder 📂", "$myName updated folder: $name")
                }
            }
            showDialog = false
        }
    }

    folderToDelete?.let { target ->
        val dialogTextColor = if (currentTheme.id == "custom") Color(0xFF1F1F1F) else textColor
        AlertDialog(
            containerColor = currentTheme.surface,
            onDismissRequest = { folderToDelete = null },
            title = { Text("Delete Album?", fontWeight = FontWeight.Bold, color = primaryColor) },
            text = {
                Text(
                    "Are you sure you want to delete '${target.name}'? This will permanently delete all photos, journal entries, and plans inside it.",
                    color = dialogTextColor
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val wasSharedWithPartner = partnerId.isNotEmpty() && target.participants.contains(partnerId)
                        FolderCoverCache.remove(target.id)
                        deleteFolderFully(
                            db = db,
                            storage = storage,
                            folderId = target.id,
                            onFailure = {
                                Toast.makeText(
                                    context,
                                    "Couldn't finish deleting album. Please retry.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                        if (wasSharedWithPartner) {
                            sendNotification(db, partnerId, "Deleted Item 🗑️", "$myName deleted folder: ${target.name}")
                        }
                        folderToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) {
                    Text("Cancel", color = dialogTextColor)
                }
            }
        )
    }
}

@Composable
fun FloatingParticle(particle: Particle) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(particle.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2000, easing = LinearOutSlowInEasing)
        )
    }
    val yOffset = 500f - (1000f * progress.value)
    val alpha = (1f - progress.value).coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxSize().zIndex(10f)) {
        Text(
            text = particle.emoji,
            fontSize = 32.sp,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = ((particle.startX - 0.5f) * 320).dp, y = yOffset.dp)
                .alpha(alpha)
        )
    }
}

// ============================================================================
// OPTION 1: ROMANCE BENTO GRID COMPOSABLES
// ============================================================================

/**
 * Compact "Your Status" Pill Bar
 * Elegant frosted glass capsule displaying your online presence, mood & love note,
 * with quick 1-tap buttons to update either.
 */
@Composable
fun CompactYourStatusPill(
    theme: AppTheme,
    db: FirebaseFirestore,
    context: Context,
    onTriggerParticles: (String) -> Unit
) {
    val uid = SessionCache.userId
    val partnerId = SessionCache.partnerId
    val myName = SessionCache.userName
    val currentMood = SessionCache.userMood
    val currentNote = SessionCache.userLoveNote
    val haptic = LocalHapticFeedback.current

    var showMoodPicker by remember { mutableStateOf(false) }
    var showNoteEditor by remember { mutableStateOf(false) }
    var noteDraft by remember { mutableStateOf("") }

    val isDark = theme.primary.luminance() < 0.55f
    val textColor = if (isDark) Color.White else Color(0xFF1E293B)
    val pillBg = if (isDark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.08f)
    val pillBorder = if (isDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.12f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyHover(haptic = haptic, scaleOnHover = 1.015f, scaleOnPress = 0.985f)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        theme.primary.copy(alpha = 0.90f),
                        theme.secondary.copy(alpha = 0.82f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.40f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Status indicator & current vibe / quote
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (currentMood.isNotEmpty()) {
                            onTriggerParticles(currentMood)
                        }
                        showMoodPicker = true
                    }
                    .padding(end = 8.dp)
            ) {
                // Online dot + Mood icon badge
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(pillBg)
                        .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (currentMood.isNotEmpty()) currentMood else "✨",
                        fontSize = 20.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "YOU",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.3.sp,
                            color = textColor.copy(alpha = 0.85f)
                        )
                    }
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = when {
                            currentNote.isNotEmpty() -> "“$currentNote”"
                            currentMood.isNotEmpty() -> "Feeling $currentMood"
                            else -> "Share your vibe today ✨"
                        },
                        fontFamily = FontFamily.Serif,
                        fontStyle = if (currentNote.isNotEmpty()) FontStyle.Italic else FontStyle.Normal,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right: 2 Compact pill buttons (Mood & Note)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mood Button
                Box(
                    modifier = Modifier
                        .bouncyHover(haptic = haptic, scaleOnHover = 1.08f, scaleOnPress = 0.92f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(pillBg)
                        .border(0.5.dp, pillBorder, RoundedCornerShape(12.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showMoodPicker = true
                        }
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (currentMood.isNotEmpty()) currentMood else "😊", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Mood",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                }

                // Note Button
                Box(
                    modifier = Modifier
                        .bouncyHover(haptic = haptic, scaleOnHover = 1.08f, scaleOnPress = 0.92f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(pillBg)
                        .border(0.5.dp, pillBorder, RoundedCornerShape(12.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            noteDraft = currentNote
                            showNoteEditor = true
                        }
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("💌", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Note",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
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
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.heightIn(max = 360.dp)
                    ) {
                        items(Moods, key = { it.emoji }) { mood ->
                            val emoji = mood.emoji
                            val label = mood.label
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (currentMood == emoji) theme.primary.copy(alpha = 0.15f) else Color.Transparent
                                    )
                                    .clickable {
                                        if (uid.isNotEmpty()) {
                                            db.collection("users").document(uid).update("mood", emoji)
                                            if (partnerId.isNotEmpty()) {
                                                sendNotification(
                                                    db,
                                                    partnerId,
                                                    "Mood Update $emoji",
                                                    "$myName is feeling $label $emoji"
                                                )
                                            }
                                            Toast.makeText(context, "Mood set to $label", Toast.LENGTH_SHORT).show()
                                        }
                                        showMoodPicker = false
                                    }
                                    .padding(6.dp)
                            ) {
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
                    OutlinedTextField(
                        value = noteDraft,
                        onValueChange = { noteDraft = it },
                        label = { Text("Your note") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (currentNote.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    if (uid.isNotEmpty()) {
                                        db.collection("users").document(uid).update("loveNote", "")
                                        Toast.makeText(context, "Love note cleared", Toast.LENGTH_SHORT).show()
                                    }
                                    showNoteEditor = false
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red.copy(alpha = 0.8f))
                            ) {
                                Text("Clear")
                            }
                        }
                        Button(
                            onClick = {
                                if (uid.isNotEmpty()) {
                                    db.collection("users").document(uid).update("loveNote", noteDraft.trim())
                                    if (partnerId.isNotEmpty() && noteDraft.trim().isNotEmpty()) {
                                        sendNotification(
                                            db,
                                            partnerId,
                                            "New Love Note 💌",
                                            "$myName left you a love note!"
                                        )
                                    }
                                    Toast.makeText(context, "Love note saved! 💌", Toast.LENGTH_SHORT).show()
                                }
                                showNoteEditor = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bento Tile: Partner's Vibe (MoodCard)
 * Square/tall bento tile featuring ambient breathing glow, partner photo avatar,
 * large floating mood emoji, mood label, quick reaction pills, and full reaction dialog.
 */
@Composable
fun BentoMoodTile(
    modifier: Modifier = Modifier,
    mood: String,
    partner: String,
    partnerStatus: String,
    partnerPhotoUrl: String,
    partnerId: String,
    reactions: List<Reaction>,
    theme: AppTheme,
    myName: String,
    db: FirebaseFirestore,
    context: Context,
    onTrigger: (String) -> Unit,
    bg: CardBackground,
    onColorPickClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val textCol = if (bg.darkText) Color(0xFF1E293B) else Color.White
    val pillBg = if (bg.darkText) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.20f)
    val cardBrush = Brush.linearGradient(bg.colors)
    val cardShape = RoundedCornerShape(24.dp)

    var showReactionMenu by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "bentoMoodBreathe")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatOffset"
    )

    val moodLabel = remember(mood) { Moods.find { it.emoji == mood }?.label ?: "Feeling $mood" }
    val isOnline = partnerId.isNotEmpty() && partnerStatus == "Online"

    Box(
        modifier = modifier
            .height(250.dp)
            .bouncyHover(haptic = haptic, scaleOnHover = 1.025f, scaleOnPress = 0.975f)
            .clip(cardShape)
            .background(cardBrush)
            .border(1.dp, Color.White.copy(alpha = 0.35f), cardShape)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Partner tag with online indicator + discrete palette icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (partnerPhotoUrl.isNotEmpty()) {
                        AsyncImage(
                            model = buildImageRequest(context, partnerPhotoUrl, cacheKey = "avatar_partner_bento"),
                            contentDescription = partner,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isOnline) Color(0xFF4CAF50) else Color.Gray)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                    }
                    Text(
                        text = "${partner.ifEmpty { "PARTNER" }.uppercase()}'S VIBE",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = textCol.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 100.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .bouncyHover(haptic = haptic, scaleOnHover = 1.15f, scaleOnPress = 0.88f)
                        .clip(CircleShape)
                        .background(pillBg)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onColorPickClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Palette,
                        contentDescription = "Card Theme",
                        tint = textCol,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // Center: Breathing ambient glow + Floating mood emoji & Serif label
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (mood.isNotEmpty()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTrigger(mood)
                        }
                    }
            ) {
                if (partnerId.isNotEmpty() && mood.isNotEmpty()) {
                    Box(contentAlignment = Alignment.Center) {
                        // Ambient breathing glow halo
                        val localDensity = LocalDensity.current
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .graphicsLayer {
                                    scaleX = glowScale
                                    scaleY = glowScale
                                }
                                .clip(CircleShape)
                                .background(
                                    if (bg.darkText) Color.Black.copy(alpha = 0.06f)
                                    else Color.White.copy(alpha = 0.18f)
                                )
                        )
                        Text(
                            text = mood,
                            fontSize = 44.sp,
                            modifier = Modifier.graphicsLayer {
                                translationY = with(localDensity) { floatOffset.dp.toPx() }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = moodLabel,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = textCol,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(pillBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("💭", fontSize = 28.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (partnerId.isEmpty()) "Link partner" else "Waiting for vibe ✨",
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontSize = 12.sp,
                        color = textCol.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Bottom: Quick reaction pills + More button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val quickReactions = reactions.take(2)
                quickReactions.forEach { rx ->
                    Box(
                        modifier = Modifier
                            .bouncyHover(haptic = haptic, scaleOnHover = 1.1f, scaleOnPress = 0.9f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(pillBg)
                            .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTrigger(rx.emoji)
                                if (partnerId.isNotEmpty()) {
                                    sendNotification(
                                        db,
                                        partnerId,
                                        "Reaction ${rx.emoji}",
                                        "$myName sent a ${rx.label}!"
                                    )
                                    Toast.makeText(context, "Sent ${rx.label}!", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(rx.emoji, fontSize = 14.sp)
                    }
                }

                // Dropdown for full reaction menu
                Box(
                    modifier = Modifier
                        .bouncyHover(haptic = haptic, scaleOnHover = 1.1f, scaleOnPress = 0.9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(pillBg)
                        .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showReactionMenu = true
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("…", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textCol)
                }
            }
        }
    }

    // Full reaction picker dialog
    if (showReactionMenu) {
        Dialog(onDismissRequest = { showReactionMenu = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .glassCard(0.95f)
                    .padding(18.dp)
            ) {
                Column {
                    Text(
                        "Send Love to $partner",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = theme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(reactions, key = { it.label }) { reaction ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .bouncyHover(haptic = haptic, scaleOnHover = 1.12f, scaleOnPress = 0.90f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onTrigger(reaction.emoji)
                                        if (partnerId.isNotEmpty()) {
                                            sendNotification(
                                                db,
                                                partnerId,
                                                "Reaction ${reaction.emoji}",
                                                "$myName sent a ${reaction.label}!"
                                            )
                                            Toast.makeText(context, "Sent ${reaction.label}!", Toast.LENGTH_SHORT).show()
                                        }
                                        showReactionMenu = false
                                    }
                                    .padding(4.dp)
                            ) {
                                Text(reaction.emoji, fontSize = 32.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    reaction.label,
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    color = Color.DarkGray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bento Tile: Love Note Postcard (NoteCard)
 * Styled as a romantic physical postcard / parchment letter with a mini postage stamp badge,
 * elegant quotation in italic Serif font, and a delicate sign-off.
 */
@Composable
fun BentoNoteTile(
    modifier: Modifier = Modifier,
    note: String,
    partner: String,
    partnerId: String,
    theme: AppTheme,
    bg: CardBackground,
    onColorPickClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val textCol = if (bg.darkText) Color(0xFF1E293B) else Color.White
    val pillBg = if (bg.darkText) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.20f)
    val cardBrush = Brush.linearGradient(bg.colors)
    val cardShape = RoundedCornerShape(24.dp)

    var showFullLetter by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .height(250.dp)
            .bouncyHover(haptic = haptic, scaleOnHover = 1.025f, scaleOnPress = 0.975f)
            .clip(cardShape)
            .background(cardBrush)
            .border(1.dp, Color.White.copy(alpha = 0.35f), cardShape)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Postage stamp / wax seal badge + discrete palette icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Postage stamp badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(pillBg)
                        .border(0.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text("💌", fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "POSTCARD",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = textCol.copy(alpha = 0.9f)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .bouncyHover(haptic = haptic, scaleOnHover = 1.15f, scaleOnPress = 0.88f)
                        .clip(CircleShape)
                        .background(pillBg)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onColorPickClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Palette,
                        contentDescription = "Card Theme",
                        tint = textCol,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // Body: Frosted parchment area with romantic italic serif quotation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(pillBg)
                    .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                    .clickable {
                        if (note.isNotEmpty()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showFullLetter = true
                        }
                    }
                    .padding(10.dp)
            ) {
                if (note.isNotEmpty()) {
                    Text(
                        text = "“$note”",
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        color = textCol
                    )
                } else {
                    Text(
                        text = if (partnerId.isEmpty()) "Link partner to receive love notes 💌" else "No love note yet... Tap to leave one! 💌",
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontSize = 12.sp,
                        color = textCol.copy(alpha = 0.8f)
                    )
                }
            }

            // Bottom: Sign-off
            Text(
                text = if (partnerId.isNotEmpty()) "— with love, $partner 💕" else "— love note",
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = textCol.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    // Full letter preview dialog
    if (showFullLetter && note.isNotEmpty()) {
        Dialog(onDismissRequest = { showFullLetter = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(cardBrush)
                    .border(1.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💌", fontSize = 38.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "A LETTER FROM ${partner.uppercase()}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = textCol.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "“$note”",
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        lineHeight = 26.sp,
                        textAlign = TextAlign.Center,
                        color = textCol
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "— Forever yours, $partner 💕",
                        fontFamily = FontFamily.Serif,
                        fontSize = 13.5.sp,
                        color = textCol.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

/**
 * Interactive Horizontal Ribbon: Wants & Requests
 * Sleek ribbon card with quick horizontal scrollable wish capsules and 1-tap hint sending.
 */
@Composable
fun BentoRequestRibbon(
    options: List<Reaction>,
    partnerId: String,
    partnerName: String,
    myName: String,
    theme: AppTheme,
    db: FirebaseFirestore,
    context: Context,
    onTrigger: (String) -> Unit,
    bg: CardBackground,
    onColorPickClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val textCol = if (bg.darkText) Color(0xFF1E293B) else Color.White
    val pillBg = if (bg.darkText) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.20f)
    val cardBrush = Brush.linearGradient(bg.colors)
    val cardShape = RoundedCornerShape(24.dp)

    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyHover(haptic = haptic, scaleOnHover = 1.015f, scaleOnPress = 0.985f)
            .clip(cardShape)
            .background(cardBrush)
            .border(1.dp, Color.White.copy(alpha = 0.35f), cardShape)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row: Title & subtitle + palette & more actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("👉👈", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "I WANT...",
                        fontFamily = FontFamily.Serif,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = textCol
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• Drop a sweet hint",
                        fontSize = 11.sp,
                        color = textCol.copy(alpha = 0.75f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Palette button
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .bouncyHover(haptic = haptic, scaleOnHover = 1.15f, scaleOnPress = 0.88f)
                            .clip(CircleShape)
                            .background(pillBg)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onColorPickClick()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = "Card Theme",
                            tint = textCol,
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    // More Wishes button
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .bouncyHover(haptic = haptic, scaleOnHover = 1.15f, scaleOnPress = 0.88f)
                            .clip(CircleShape)
                            .background(pillBg)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showMenu = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "More Wishes",
                            tint = textCol,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Horizontal Ribbon of Wish Capsules
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(options, key = { it.label }) { req ->
                    Box(
                        modifier = Modifier
                            .bouncyHover(haptic = haptic, scaleOnHover = 1.08f, scaleOnPress = 0.92f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(pillBg)
                            .border(0.5.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (sendRequest(req, partnerId, myName, db, context)) {
                                    onTrigger(req.emoji)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(req.emoji, fontSize = 15.sp)
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                req.label,
                                fontFamily = FontFamily.Serif,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = textCol
                            )
                        }
                    }
                }

                item(key = "more_wishes_chip") {
                    Box(
                        modifier = Modifier
                            .bouncyHover(haptic = haptic, scaleOnHover = 1.08f, scaleOnPress = 0.92f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(pillBg)
                            .border(0.5.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showMenu = true
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("✨", fontSize = 15.sp)
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                "+ More",
                                fontFamily = FontFamily.Serif,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = textCol
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMenu) {
        Dialog(onDismissRequest = { showMenu = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .glassCard(0.95f)
                    .padding(18.dp)
            ) {
                Column {
                    Text(
                        "Make a Wish for $partnerName",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF1F1F1F)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(options, key = { it.label }) { req ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .bouncyHover(haptic = haptic, scaleOnHover = 1.12f, scaleOnPress = 0.90f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (sendRequest(req, partnerId, myName, db, context)) {
                                            onTrigger(req.emoji)
                                        }
                                        showMenu = false
                                    }
                                    .padding(4.dp)
                            ) {
                                Text(req.emoji, fontSize = 32.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    req.label,
                                    fontFamily = FontFamily.Serif,
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Partner's Active Granted Wishes Ribbon (WishlistCard)
 * Highlights active pending wishes from partner with a shining "Grant Wish 💕" action.
 */
@Composable
fun BentoWishlistRibbon(
    requests: List<PartnerRequest>,
    partnerName: String,
    partnerId: String,
    myName: String,
    db: FirebaseFirestore,
    theme: AppTheme,
    primary: Color,
    onTrigger: (String) -> Unit,
    bg: CardBackground,
    onColorPickClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val textCol = if (bg.darkText) Color(0xFF1E293B) else Color.White
    val pillBg = if (bg.darkText) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.20f)
    val cardBrush = Brush.linearGradient(bg.colors)
    val cardShape = RoundedCornerShape(24.dp)
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyHover(haptic = haptic, scaleOnHover = 1.015f, scaleOnPress = 0.985f)
            .clip(cardShape)
            .background(cardBrush)
            .border(1.dp, Color.White.copy(alpha = 0.35f), cardShape)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🌠", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${partnerName.uppercase()}'S ACTIVE WISHES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.3.sp,
                        color = textCol.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(pillBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${requests.size}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = textCol
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .bouncyHover(haptic = haptic, scaleOnHover = 1.15f, scaleOnPress = 0.88f)
                        .clip(CircleShape)
                        .background(pillBg)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onColorPickClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Palette,
                        contentDescription = "Card Theme",
                        tint = textCol,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                requests.forEach { req ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(pillBg)
                            .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(req.emoji, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                req.label,
                                fontFamily = FontFamily.Serif,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = textCol
                            )
                        }

                        Box(
                            modifier = Modifier
                                .bouncyHover(haptic = haptic, scaleOnHover = 1.08f, scaleOnPress = 0.92f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (bg.darkText) primary else Color.White)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onTrigger("💖")
                                    db.collection("requests").document(req.id).delete()
                                    sendNotification(
                                        db,
                                        partnerId,
                                        "Wish Granted! 💖",
                                        "$myName granted your wish: ${req.label} ${req.emoji}!"
                                    )
                                    Toast.makeText(context, "Granted ${req.label}!", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "Grant Wish 💕",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (bg.darkText) Color.White else primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DraggableCardWrapper(
    type: HomeCardType,
    isExpanded: Boolean,
    isActiveDrag: Boolean,
    isLifted: Boolean,
    hoverEnabled: Boolean,
    translationY: Float,
    onHeightMeasured: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onExpandToggle: () -> Unit,
    onColorPickClick: () -> Unit,
    bg: CardBackground,
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragDelta by rememberUpdatedState(onDragDelta)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    val animatedAlpha by animateFloatAsState(
        targetValue = if (isLifted) 0.70f else 1f,
        animationSpec = tween(180),
        label = "dragAlpha"
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (isLifted) 1.03f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "dragScale"
    )
    val animatedElevation by animateDpAsState(
        targetValue = if (isLifted) 16.dp else 0.dp,
        animationSpec = tween(180),
        label = "dragElevation"
    )
    val currentScale by rememberUpdatedState(animatedScale)

    val (icon, title) = when (type) {
        HomeCardType.MOOD -> "✨" to "Partner's Mood"
        HomeCardType.REQUESTS -> "👉👈" to "My Wants"
        HomeCardType.NOTE -> "💌" to "Love Note"
        HomeCardType.WISHLIST -> "🌠" to "Active Wishes"
    }

    val headerTextColor = if (bg.darkText) Color(0xFF1E293B) else Color.White
    val pillBg = if (bg.darkText) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.20f)
    val cardBrush = Brush.linearGradient(bg.colors)
    val cardShape = RoundedCornerShape(24.dp)
    val density = LocalDensity.current
    val handleHitWidthPx = with(density) { 72.dp.toPx() }
    val handleHitHeightPx = with(density) { 56.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                onHeightMeasured(coordinates.size.height.toFloat())
            }
            .zIndex(
                when {
                    isActiveDrag -> 10f
                    translationY.absoluteValue > 0.5f -> 1f
                    else -> 0f
                }
            )
            .pointerInput(type) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val isHandleTouch = down.position.x <= handleHitWidthPx && down.position.y <= handleHitHeightPx

                    if (isHandleTouch) {
                        val drag = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                            change.consume()
                        }
                        if (drag != null) {
                            currentOnDragStart()
                            val pointerId = drag.id
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == pointerId }
                                    if (change == null || !change.pressed) {
                                        break
                                    }
                                    val dy = change.positionChange().y
                                    change.consume()
                                    if (dy != 0f) {
                                        currentOnDragDelta(dy)
                                    }
                                }
                            } finally {
                                currentOnDragEnd()
                            }
                        }
                    } else {
                        val drag = awaitLongPressOrCancellation(down.id)
                        if (drag != null) {
                            currentOnDragStart()
                            val pointerId = drag.id
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == pointerId }
                                    if (change == null || !change.pressed) {
                                        break
                                    }
                                    val dy = change.positionChange().y
                                    change.consume()
                                    if (dy != 0f) {
                                        currentOnDragDelta(dy)
                                    }
                                }
                            } finally {
                                currentOnDragEnd()
                            }
                        }
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    this.translationY = translationY
                    scaleX = animatedScale
                    scaleY = animatedScale
                    alpha = animatedAlpha
                    shadowElevation = animatedElevation.toPx()
                    shape = cardShape
                    clip = false
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .bouncyHover(
                        haptic = haptic,
                        scaleOnHover = 1.02f,
                        scaleOnPress = 0.98f,
                        enabled = hoverEnabled && !isActiveDrag
                    )
                    .clip(cardShape)
                    .background(cardBrush)
                    .border(
                        if (isLifted) 1.5.dp else 1.dp,
                        if (isLifted) Color.White.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.35f),
                        cardShape
                    )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header row matching Days Together tracking & pill button style
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Hybrid Drag Handle: Instant drag gesture on the handle icon!
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isLifted) pillBg else Color.Transparent)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.DragHandle,
                                    contentDescription = "Drag to reorder",
                                    tint = if (isLifted) headerTextColor else headerTextColor.copy(alpha = 0.65f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        Text(icon, fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = title.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            color = headerTextColor.copy(alpha = 0.85f)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Palette Button
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .bouncyHover(
                                    haptic = haptic,
                                    scaleOnHover = 1.1f,
                                    scaleOnPress = 0.9f,
                                    enabled = hoverEnabled
                                )
                                .clip(CircleShape)
                                .background(pillBg)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onColorPickClick()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Palette,
                                contentDescription = "Color Theme",
                                tint = headerTextColor,
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        // Expand / Collapse Button
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .bouncyHover(
                                    haptic = haptic,
                                    scaleOnHover = 1.1f,
                                    scaleOnPress = 0.9f,
                                    enabled = hoverEnabled
                                )
                                .clip(CircleShape)
                                .background(pillBg)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onExpandToggle()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = headerTextColor,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    content()
                }
            }
        }
    }
}
}

@Composable
fun WishlistCard(
    requests: List<PartnerRequest>,
    partnerName: String,
    partnerId: String,
    myName: String,
    db: FirebaseFirestore,
    primary: Color,
    onTrigger: (String) -> Unit,
    bg: CardBackground
) {
    val haptic = LocalHapticFeedback.current
    val textCol = if (bg.darkText) Color(0xFF1E293B) else Color.White
    val pillBg = if (bg.darkText) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.20f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 18.dp)
    ) {
        Text(
            "$partnerName's Active Wishes",
            fontFamily = FontFamily.Serif,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = textCol
        )
        Spacer(modifier = Modifier.height(10.dp))
        requests.forEach { req ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .bouncyHover(haptic = haptic, scaleOnHover = 1.025f, scaleOnPress = 0.97f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(pillBg)
                    .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(req.emoji, fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        req.label,
                        fontFamily = FontFamily.Serif,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = textCol
                    )
                }
                Box(
                    modifier = Modifier
                        .bouncyHover(haptic = haptic, scaleOnHover = 1.08f, scaleOnPress = 0.92f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (bg.darkText) primary else Color.White)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTrigger(req.emoji)
                            if (req.id.isNotEmpty()) {
                                db.collection("requests").document(req.id).delete()
                            }
                            if (partnerId.isNotEmpty()) {
                                sendNotification(db, partnerId, "Wish Granted! ✅", "$myName gave you: ${req.label} ${req.emoji}")
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Grant 💕",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        color = if (bg.darkText) Color.White else primary
                    )
                }
            }
        }
    }
}

@Composable
fun CompactAnniversaryBar(
    theme: AppTheme,
    anniversaryDate: Long,
    onClick: () -> Unit
) {
    if (anniversaryDate <= 0L) return
    val daysTogether = remember(anniversaryDate) { DateUtils.calculateDaysTogether(anniversaryDate) }
    val monthsTogether = remember(anniversaryDate) { DateUtils.calculateMonthsTogether(anniversaryDate) }
    val nextMonthsary = remember(anniversaryDate) { DateUtils.calculateNextMonthsary(anniversaryDate) }
    val annualMilestone = remember(anniversaryDate) { DateUtils.calculateNextAnnualAnniversary(anniversaryDate) }

    val barBg = if (theme.id == "custom") {
        Color.Black.copy(alpha = 0.45f)
    } else {
        theme.primary.copy(alpha = 0.12f)
    }
    val borderColor = if (theme.id == "custom") Color.White.copy(alpha = 0.3f) else theme.primary.copy(alpha = 0.25f)

    // Highlight nearest celebration
    val highlightMilestone = when {
        annualMilestone?.isToday == true -> annualMilestone
        nextMonthsary?.isToday == true -> nextMonthsary
        annualMilestone != null && annualMilestone.daysRemaining <= 7 -> annualMilestone
        nextMonthsary != null -> nextMonthsary
        else -> annualMilestone
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(barBg)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = if (daysTogether >= 0) "Day $daysTogether 💕" else "In ${daysTogether.absoluteValue} days 💕",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (theme.id == "custom") Color.White else theme.primary
                )
                if (monthsTogether > 0) {
                    Text(
                        text = "$monthsTogether month${if (monthsTogether != 1) "s" else ""} together",
                        fontSize = 10.5.sp,
                        color = (if (theme.id == "custom") Color.White else theme.primary).copy(alpha = 0.75f)
                    )
                }
            }
            if (highlightMilestone != null) {
                val icon = if (highlightMilestone.title.contains("Anniversary")) "🥂" else "🌙"
                Text(
                    text = if (highlightMilestone.isToday) "🎉 ${highlightMilestone.title}" else "$icon ${highlightMilestone.title} in ${highlightMilestone.daysRemaining}d",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (theme.id == "custom") Color.White.copy(alpha = 0.85f) else theme.text.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun AnniversaryHeroBanner(
    theme: AppTheme,
    anniversaryDate: Long,
    onTriggerParticles: (String) -> Unit,
    onSetAnniversary: (Long) -> Unit
) {
    val context = LocalContext.current
    val hasAnniversary = anniversaryDate > 0L

    if (!hasAnniversary) {
        // Empty State: prompt couple to set their special relationship start date
        val isDark = theme.primary.luminance() < 0.55f
        val textColor = if (isDark) Color.White else Color(0xFF1E293B)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            theme.primary.copy(alpha = 0.85f),
                            theme.secondary.copy(alpha = 0.80f)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("💕", fontSize = 36.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Set Your Anniversary",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Count every magical day together and get automatic countdowns for each anniversary & milestone.",
                    fontSize = 12.sp,
                    color = textColor.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        val cal = Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val picked = Calendar.getInstance().apply {
                                    set(y, m, d, 0, 0, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                onSetAnniversary(picked.timeInMillis)
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = theme.primary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pick Our Date 🗓️", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    } else {
        // Hero State: Active Anniversary configured
        val daysTogether = remember(anniversaryDate) { DateUtils.calculateDaysTogether(anniversaryDate) }
        val monthsTogether = remember(anniversaryDate) { DateUtils.calculateMonthsTogether(anniversaryDate) }
        val breakdown = remember(anniversaryDate) { DateUtils.calculateTimeTogetherBreakdown(anniversaryDate) }
        val nextMonthsary = remember(anniversaryDate) { DateUtils.calculateNextMonthsary(anniversaryDate) }
        val annualMilestone = remember(anniversaryDate) { DateUtils.calculateNextAnnualAnniversary(anniversaryDate) }
        val formattedDate = remember(anniversaryDate) {
            SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(anniversaryDate))
        }

        val infiniteTransition = rememberInfiniteTransition(label = "pulseHeart")
        val heartScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.18f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "heartScale"
        )

        val isDark = theme.primary.luminance() < 0.55f
        val textColor = if (isDark) Color.White else Color(0xFF1E293B)
        val pillBg = if (isDark) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.08f)
        val haptic = LocalHapticFeedback.current
        val coroutineScope = rememberCoroutineScope()
        val counterBounce = remember { Animatable(1f) }

        val openDatePicker = {
            val cal = Calendar.getInstance().apply {
                timeInMillis = anniversaryDate
            }
            DatePickerDialog(
                context,
                { _, y, m, d ->
                    val picked = Calendar.getInstance().apply {
                        set(y, m, d, 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onSetAnniversary(picked.timeInMillis)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            theme.primary.copy(alpha = 0.90f),
                            theme.primary.copy(alpha = 0.72f),
                            theme.secondary.copy(alpha = 0.85f)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top header row: Tag + Edit Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✨", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DAYS TOGETHER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            color = textColor.copy(alpha = 0.85f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(pillBg)
                            .clickable(onClick = openDatePicker),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit anniversary date",
                            tint = textColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Big Hero Counter Row (tap to trigger celebration bounce & haptics!)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTriggerParticles("💖")
                            onTriggerParticles("✨")
                            coroutineScope.launch {
                                counterBounce.animateTo(0.92f, tween(70))
                                counterBounce.animateTo(
                                    1.08f,
                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                                )
                                counterBounce.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy))
                            }
                        }
                        .graphicsLayer {
                            scaleX = counterBounce.value
                            scaleY = counterBounce.value
                        }
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = if (daysTogether >= 0) "Day $daysTogether" else "In ${daysTogether.absoluteValue} Days",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Serif,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "💖",
                        fontSize = 28.sp,
                        modifier = Modifier.graphicsLayer {
                            scaleX = heartScale
                            scaleY = heartScale
                        }
                    )
                }

                // Months Together Pill
                if (monthsTogether > 0) {
                    val durationText = if (breakdown.years > 0) {
                        "${breakdown.years} ${if (breakdown.years == 1) "Year" else "Years"}${if (breakdown.months > 0) ", ${breakdown.months} ${if (breakdown.months == 1) "Month" else "Months"}" else ""} Together"
                    } else {
                        "$monthsTogether ${if (monthsTogether == 1) "Month" else "Months"} Together"
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(pillBg)
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "🗓️ $durationText",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Subtitle: Together since Date (clickable to edit date)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = openDatePicker)
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = "Together since $formattedDate",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor.copy(alpha = 0.85f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Milestone Badges Row: Next Monthsary + Next Annual Anniversary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (nextMonthsary != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (nextMonthsary.isToday) Color(0xFFFFD54F).copy(alpha = 0.35f) else pillBg)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onTriggerParticles(if (nextMonthsary.isToday) "🎉" else "🌙")
                                }
                                .padding(horizontal = 8.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (nextMonthsary.isToday) "🎉 Monthsary!" else "🌙 ${nextMonthsary.title}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (nextMonthsary.isToday) "Today! 🥂" else if (nextMonthsary.daysRemaining == 1L) "tomorrow!" else "in ${nextMonthsary.daysRemaining}d",
                                    fontSize = 11.sp,
                                    color = textColor.copy(alpha = 0.85f),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    if (annualMilestone != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (annualMilestone.isToday) Color(0xFFFFD54F).copy(alpha = 0.35f) else pillBg)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onTriggerParticles(if (annualMilestone.isToday) "🎉" else "🥂")
                                }
                                .padding(horizontal = 8.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (annualMilestone.isToday) "🎉 Today!" else "🥂 ${annualMilestone.title}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (annualMilestone.isToday) "Happy Anniv!" else if (annualMilestone.daysRemaining == 1L) "tomorrow!" else "in ${annualMilestone.daysRemaining}d",
                                    fontSize = 11.sp,
                                    color = textColor.copy(alpha = 0.85f),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun YourStatusCard(theme: AppTheme, db: FirebaseFirestore, context: Context) {
    val uid = SessionCache.userId
    val partnerId = SessionCache.partnerId
    val myName = SessionCache.userName
    val currentMood = SessionCache.userMood
    val currentNote = SessionCache.userLoveNote
    val haptic = LocalHapticFeedback.current

    var showMoodPicker by remember { mutableStateOf(false) }
    var showNoteEditor by remember { mutableStateOf(false) }
    var noteDraft by remember { mutableStateOf("") }

    val isDark = theme.primary.luminance() < 0.55f
    val textColor = if (isDark) Color.White else Color(0xFF1E293B)
    val pillBg = if (isDark) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.08f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyHover(haptic = haptic, scaleOnHover = 1.025f, scaleOnPress = 0.97f)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        theme.primary.copy(alpha = 0.90f),
                        theme.primary.copy(alpha = 0.72f),
                        theme.secondary.copy(alpha = 0.85f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top header row: Tag + Status Pill (matching Days Together)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✨", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "YOUR STATUS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = textColor.copy(alpha = 0.85f)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(pillBg)
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Online",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Serif Quote / Prompt
            if (currentNote.isNotEmpty()) {
                Text(
                    text = "“$currentNote”",
                    fontFamily = FontFamily.Serif,
                    fontSize = 17.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = if (currentMood.isNotEmpty()) "Feeling $currentMood today" else "Share how you feel with your love",
                    fontFamily = FontFamily.Serif,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: Frosted pill buttons matching Days Together with bouncy micro-interactions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Button 1: Mood
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .bouncyHover(haptic = haptic, scaleOnHover = 1.04f, scaleOnPress = 0.94f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(pillBg)
                        .border(0.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showMoodPicker = true
                        }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (currentMood.isNotEmpty()) currentMood else "😊", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (currentMood.isNotEmpty()) "Mood: $currentMood" else "Set Mood",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            color = textColor,
                            maxLines = 1
                        )
                    }
                }

                // Button 2: Love Note
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .bouncyHover(haptic = haptic, scaleOnHover = 1.04f, scaleOnPress = 0.94f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(pillBg)
                        .border(0.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            noteDraft = currentNote
                            showNoteEditor = true
                        }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("💌", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (currentNote.isNotEmpty()) "Edit Note" else "Love Note",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            color = textColor,
                            maxLines = 1
                        )
                    }
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
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.heightIn(max = 360.dp)
                    ) {
                        items(Moods, key = { it.emoji }) { mood ->
                            val emoji = mood.emoji
                            val label = mood.label
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (currentMood == emoji) theme.primary.copy(alpha = 0.15f) else Color.Transparent
                                    )
                                    .clickable {
                                        if (uid.isNotEmpty()) {
                                            db.collection("users").document(uid).update("mood", emoji)
                                            if (partnerId.isNotEmpty()) {
                                                sendNotification(
                                                    db,
                                                    partnerId,
                                                    "Mood Update $emoji",
                                                    "$myName is feeling $label $emoji"
                                                )
                                            }
                                            Toast.makeText(context, "Mood set to $label", Toast.LENGTH_SHORT).show()
                                        }
                                        showMoodPicker = false
                                    }
                                    .padding(6.dp)
                            ) {
                                Text(emoji, fontSize = 36.sp)
                                Text(label, fontSize = 11.sp, color = Color.DarkGray)
                            }
                        }
                    }
                }
            }
        }
    }

    // Love note editor dialog (supports updating AND clearing an existing love note)
    if (showNoteEditor) {
        Dialog(onDismissRequest = { showNoteEditor = false }) {
            Box(modifier = Modifier.fillMaxWidth().glassCard(0.95f).padding(20.dp)) {
                Column {
                    Text("Leave a Love Note", fontWeight = FontWeight.Bold, color = theme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = noteDraft,
                        onValueChange = { noteDraft = it },
                        label = { Text("Your note") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.primary,
                            focusedLabelColor = theme.primary,
                            focusedTextColor = Color(0xFF1F1F1F),
                            unfocusedTextColor = Color(0xFF1F1F1F)
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentNote.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    if (uid.isNotEmpty()) {
                                        db.collection("users").document(uid).update("loveNote", "")
                                        Toast.makeText(context, "Love note cleared", Toast.LENGTH_SHORT).show()
                                    }
                                    showNoteEditor = false
                                }
                            ) {
                                Text("Clear", color = Color.Red)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }
                        Row {
                            TextButton(onClick = { showNoteEditor = false }) {
                                Text("Cancel", color = Color.DarkGray)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val trimmed = noteDraft.trim()
                                    if (uid.isNotEmpty()) {
                                        db.collection("users").document(uid).update("loveNote", trimmed)
                                        if (trimmed.isNotEmpty() && partnerId.isNotEmpty()) {
                                            sendNotification(
                                                db,
                                                partnerId,
                                                "New Love Note 💌",
                                                "$myName left you a love note!"
                                            )
                                        }
                                        Toast.makeText(
                                            context,
                                            if (trimmed.isEmpty()) "Love note cleared" else "Love note saved 💌",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    showNoteEditor = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
                            ) {
                                Text("Save", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MoodCard(
    mood: String,
    partner: String,
    reactions: List<Reaction>,
    theme: AppTheme,
    myName: String,
    partnerId: String,
    db: FirebaseFirestore,
    context: Context,
    onTrigger: (String) -> Unit,
    bg: CardBackground
) {
    val haptic = LocalHapticFeedback.current
    val textCol = if (bg.darkText) Color(0xFF1E293B) else Color.White
    val pillBg = if (bg.darkText) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.20f)

    if (partnerId.isEmpty() || mood.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 18.dp)
        ) {
            Text(
                text = if (partnerId.isEmpty()) {
                    "Link with your partner in Profile to see their mood ✨"
                } else {
                    "$partner hasn't set a mood yet ✨"
                },
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 13.5.sp,
                color = textCol.copy(alpha = 0.85f)
            )
        }
        return
    }

    val moodLabel = remember(mood) { Moods.find { it.emoji == mood }?.label ?: "Feeling $mood" }
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 18.dp)
    ) {
        // Mood Hero Row (matching "Day 487 💖" large Serif style from Days Together)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .bouncyHover(haptic = haptic, scaleOnHover = 1.03f, scaleOnPress = 0.96f)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTrigger(mood)
                }
        ) {
            Text(mood, fontSize = 42.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "$partner is feeling".uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp,
                    color = textCol.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = moodLabel,
                    fontSize = 24.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = textCol
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Subheader row: "RESPOND WITH LOVE" + Circular Action Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💌", fontSize = 13.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "RESPOND WITH LOVE",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    color = textCol.copy(alpha = 0.8f)
                )
            }
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .bouncyHover(haptic = haptic, scaleOnHover = 1.1f, scaleOnPress = 0.9f)
                    .clip(CircleShape)
                    .background(pillBg)
                    .border(0.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMenu = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "More Reactions",
                    tint = textCol,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (showMenu) {
            Dialog(onDismissRequest = { showMenu = false }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .glassCard(0.95f)
                        .padding(18.dp)
                ) {
                    Column {
                        Text(
                            "Choose Reaction",
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = theme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(reactions, key = { it.label }) { reaction ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .bouncyHover(haptic = haptic, scaleOnHover = 1.12f, scaleOnPress = 0.90f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            onTrigger(reaction.emoji)
                                            sendNotification(
                                                db,
                                                partnerId,
                                                "Reaction ${reaction.emoji}",
                                                "$myName sent a ${reaction.label}!"
                                            )
                                            Toast.makeText(context, "Sent ${reaction.label}!", Toast.LENGTH_SHORT).show()
                                            showMenu = false
                                        }
                                        .padding(4.dp)
                                ) {
                                    Text(reaction.emoji, fontSize = 32.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        reaction.label,
                                        fontFamily = FontFamily.Serif,
                                        fontSize = 10.5.sp,
                                        maxLines = 1,
                                        color = Color.DarkGray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Reaction chips: frosted pills matching Days Together with bouncy micro-interaction
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(reactions, key = { it.label }) { reaction ->
                Box(
                    modifier = Modifier
                        .bouncyHover(haptic = haptic, scaleOnHover = 1.08f, scaleOnPress = 0.92f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(pillBg)
                        .border(0.5.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTrigger(reaction.emoji)
                            sendNotification(
                                db,
                                partnerId,
                                "Reaction ${reaction.emoji}",
                                "$myName sent a ${reaction.label}!"
                            )
                            Toast.makeText(context, "Sent ${reaction.label}!", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(reaction.emoji, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            reaction.label,
                            fontFamily = FontFamily.Serif,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = textCol
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RequestCard(
    options: List<Reaction>,
    partnerId: String,
    myName: String,
    db: FirebaseFirestore,
    context: Context,
    onTrigger: (String) -> Unit,
    bg: CardBackground
) {
    var showMenu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val textCol = if (bg.darkText) Color(0xFF1E293B) else Color.White
    val pillBg = if (bg.darkText) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.20f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 18.dp)
    ) {
        // Top row: Serif title & subtitle + circular frosted action button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "I want... 👉👈",
                    fontFamily = FontFamily.Serif,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = textCol
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Drop a sweet hint for your partner",
                    fontSize = 11.5.sp,
                    color = textCol.copy(alpha = 0.8f)
                )
            }
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .bouncyHover(haptic = haptic, scaleOnHover = 1.1f, scaleOnPress = 0.9f)
                    .clip(CircleShape)
                    .background(pillBg)
                    .border(0.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMenu = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "More Wishes",
                    tint = textCol,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (showMenu) {
            Dialog(onDismissRequest = { showMenu = false }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .glassCard(0.95f)
                        .padding(18.dp)
                ) {
                    Column {
                        Text(
                            "Make a Wish",
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF1F1F1F)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(options, key = { it.label }) { req ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .bouncyHover(haptic = haptic, scaleOnHover = 1.12f, scaleOnPress = 0.90f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (sendRequest(req, partnerId, myName, db, context)) {
                                                onTrigger(req.emoji)
                                            }
                                            showMenu = false
                                        }
                                        .padding(4.dp)
                                ) {
                                    Text(req.emoji, fontSize = 32.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        req.label,
                                        fontFamily = FontFamily.Serif,
                                        fontSize = 10.5.sp,
                                        maxLines = 1,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Frosted wish pills matching Days Together with bouncy micro-interaction
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(options, key = { it.label }) { req ->
                Box(
                    modifier = Modifier
                        .bouncyHover(haptic = haptic, scaleOnHover = 1.08f, scaleOnPress = 0.92f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(pillBg)
                        .border(0.5.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (sendRequest(req, partnerId, myName, db, context)) {
                                onTrigger(req.emoji)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(req.emoji, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            req.label,
                            fontFamily = FontFamily.Serif,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = textCol
                        )
                    }
                }
            }
        }
    }
}

fun sendRequest(req: Reaction, partnerId: String, myName: String, db: FirebaseFirestore, context: Context): Boolean {
    val requesterId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    if (partnerId.isEmpty() || requesterId.isEmpty()) {
        Toast.makeText(context, "Link with a partner in Profile first!", Toast.LENGTH_SHORT).show()
        return false
    }
    sendNotification(db, partnerId, "Request ${req.emoji}", "$myName wants ${req.label}!")
    val reqRef = db.collection("requests").document()
    val requestData = PartnerRequest(
        id = reqRef.id,
        requesterId = requesterId,
        receiverId = partnerId,
        emoji = req.emoji,
        label = req.label,
        timestamp = System.currentTimeMillis()
    )
    reqRef.set(requestData)
    Toast.makeText(context, "Requested ${req.label}!", Toast.LENGTH_SHORT).show()
    return true
}

@Composable
fun NoteCard(note: String, partner: String, partnerId: String, bg: CardBackground) {
    val haptic = LocalHapticFeedback.current
    val textCol = if (bg.darkText) Color(0xFF1E293B) else Color.White
    val pillBg = if (bg.darkText) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.20f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 18.dp)
    ) {
        if (partnerId.isEmpty() || note.isEmpty()) {
            Text(
                text = if (partnerId.isEmpty()) {
                    "Link with your partner in Profile to receive love notes 💌"
                } else {
                    "No love note from $partner yet 💌"
                },
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 13.5.sp,
                color = textCol.copy(alpha = 0.85f)
            )
        } else {
            // Tracking subheader matching Days Together
            Text(
                text = "FROM ${partner.uppercase()}",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                color = textCol.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Romantic Serif note in frosted glass container with bouncyHover
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .bouncyHover(haptic = haptic, scaleOnHover = 1.025f, scaleOnPress = 0.97f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(pillBg)
                    .border(0.5.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "“$note”",
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    lineHeight = 28.sp,
                    color = textCol
                )
            }
        }
    }
}

@Composable
fun HistoryTab(
    db: FirebaseFirestore,
    userId: String,
    theme: AppTheme,
    primary: Color,
    textC: Color,
    items: List<ActivityItem>
) {
    val historyDateFmt = remember { SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val headerColor = if (theme.background.luminance() < 0.4f) Color.White else primary
    val dialogTextColor = if (theme.id == "custom") Color(0xFF1F1F1F) else textC

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "History Log",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = headerColor
            )
            OutlinedButton(
                onClick = { showClearConfirm = true },
                enabled = items.isNotEmpty(),
                border = BorderStroke(1.dp, if (items.isNotEmpty()) Color.Red.copy(0.5f) else Color.LightGray),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red.copy(0.7f))
            ) {
                Text("Clear")
            }
        }

        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No activity yet.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    val icon = when {
                        item.title.contains("Message", true) -> "💬"
                        item.title.contains("Adventure", true) || item.title.contains("Folder", true) -> "📂"
                        item.title.contains("Deleted", true) -> "🗑️"
                        item.title.contains("Photo", true) || item.title.contains("Memory", true) -> "📸"
                        item.title.contains("Wish", true) || item.title.contains("Request", true) -> "🌠"
                        item.title.contains("Note", true) -> "💌"
                        else -> "🔔"
                    }
                    Box(modifier = Modifier.fillMaxWidth().glassCard(0.7f).padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(icon, fontSize = 28.sp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = primary
                                )
                                Text(item.body, style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                                Text(
                                    historyDateFmt.format(Date(item.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            containerColor = theme.surface,
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear History Log?", fontWeight = FontWeight.Bold, color = primary) },
            text = { Text("Are you sure you want to clear your entire activity log? This cannot be undone.", color = dialogTextColor) },
            confirmButton = {
                Button(
                    onClick = {
                        if (userId.isNotEmpty()) {
                            db.collection("notifications").whereEqualTo("to", userId).get().addOnSuccessListener { s ->
                                s.documents.chunked(450).forEach { chunk ->
                                    val b = db.batch()
                                    chunk.forEach { b.delete(it.reference) }
                                    b.commit()
                                }
                            }
                        }
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Clear", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel", color = dialogTextColor)
                }
            }
        )
    }
}

@Composable
fun AlbumViewSwitcher(
    selectedMode: String,
    currentTheme: AppTheme,
    onModeSelected: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val isCustom = currentTheme.id == "custom"
    val containerBg = if (isCustom) Color.Black.copy(alpha = 0.35f) else currentTheme.surface.copy(alpha = 0.9f)
    val pillGradient = Brush.horizontalGradient(
        listOf(
            currentTheme.primary,
            currentTheme.secondary
        )
    )
    val activeText = if (currentTheme.primary.luminance() > 0.6f) Color.Black else Color.White
    val inactiveText = if (isCustom) Color.White.copy(alpha = 0.75f) else currentTheme.text.copy(alpha = 0.7f)

    val isFilm = selectedMode == "filmstrip"
    val indicatorProgress by animateFloatAsState(
        targetValue = if (isFilm) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "albumSwitcherProgress"
    )

    val polaroidTextColor by animateColorAsState(
        targetValue = if (!isFilm) activeText else inactiveText,
        animationSpec = tween(220),
        label = "polaroidTextColor"
    )
    val filmTextColor by animateColorAsState(
        targetValue = if (isFilm) activeText else inactiveText,
        animationSpec = tween(220),
        label = "filmTextColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .width(220.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(containerBg)
                .border(
                    1.dp,
                    if (isCustom) Color.White.copy(alpha = 0.2f) else currentTheme.primary.copy(alpha = 0.15f),
                    RoundedCornerShape(17.dp)
                )
                .padding(2.5.dp)
        ) {
            val totalWidthPx = constraints.maxWidth.toFloat()
            val segmentWidthPx = totalWidthPx / 2f
            val segmentWidthDp = with(density) { segmentWidthPx.toDp() }

            // Sliding Active Pill Indicator (RenderNode GPU-accelerated translation)
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = indicatorProgress * segmentWidthPx
                    }
                    .width(segmentWidthDp)
                    .fillMaxHeight()
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(14.dp), clip = false)
                    .clip(RoundedCornerShape(14.dp))
                    .background(pillGradient)
            )

            Row(modifier = Modifier.fillMaxSize()) {
                // Option 0: Polaroid
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            if (isFilm) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onModeSelected("polaroid")
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val scale by animateFloatAsState(
                        targetValue = if (!isFilm) 1.04f else 0.96f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "polaroidScale"
                    )
                    Text(
                        text = "📸 Polaroid",
                        fontSize = 11.5.sp,
                        fontWeight = if (!isFilm) FontWeight.Bold else FontWeight.Medium,
                        color = polaroidTextColor,
                        modifier = Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                    )
                }

                // Option 1: Film Strip
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            if (!isFilm) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onModeSelected("filmstrip")
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val scale by animateFloatAsState(
                        targetValue = if (isFilm) 1.04f else 0.96f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "filmScale"
                    )
                    Text(
                        text = "🎞️ Film Strip",
                        fontSize = 11.5.sp,
                        fontWeight = if (isFilm) FontWeight.Bold else FontWeight.Medium,
                        color = filmTextColor,
                        modifier = Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PolaroidFolderItem(
    folder: CoupleFolder,
    index: Int,
    currentTheme: AppTheme,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onClick: () -> Unit
) {
    val hasDate = folder.date != 0L
    val daysDiff = if (hasDate) DateUtils.calculateDaysDifference(folder.date) else 0L
    val timeText = DateUtils.formatFolderDays(daysDiff, hasDate = hasDate)
    val folderPastel = if (folder.color != 0L) Color(folder.color.toInt()) else currentTheme.secondary
    var showMenu by remember { mutableStateOf(false) }

    val cachedCover = FolderCoverCache.getCover(folder.id) ?: folder.coverPhotoUrl
    var coverPhoto by remember(folder.id) { mutableStateOf(cachedCover) }
    val cachedCount = FolderCoverCache.getCount(folder.id) ?: folder.photoCount
    var photoCount by remember(folder.id) { mutableIntStateOf(cachedCount) }

    LaunchedEffect(folder.id) {
        if (folder.id.isNotEmpty() && coverPhoto.isEmpty()) {
            try {
                val snapshot = FirebaseFirestore.getInstance()
                    .collection("folders").document(folder.id).collection("images")
                    .orderBy("dateAdded", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .await()
                snapshot.documents.firstOrNull()?.let { doc ->
                    val url = doc.getString("url") ?: ""
                    if (url.isNotEmpty()) {
                        coverPhoto = url
                        FolderCoverCache.setCover(folder.id, url)
                    }
                }
            } catch (_: Exception) { }
        } else if (coverPhoto.isNotEmpty()) {
            FolderCoverCache.setCover(folder.id, coverPhoto)
        }
    }

    val tilt = if (index % 2 == 0) -1.8f else 1.8f
    val washiTilt = if (index % 2 == 0) 1.5f else -2.2f

    val haptic = LocalHapticFeedback.current
    var isHoveredOrActive by remember { mutableStateOf(false) }
    val animatedTilt by animateFloatAsState(
        targetValue = if (isHoveredOrActive) 0f else tilt,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "polaroidTilt"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyHover(
                haptic = haptic,
                scaleOnHover = 1.05f,
                scaleOnPress = 0.94f,
                onStateChange = { isHoveredOrActive = it }
            )
            .graphicsLayer {
                rotationZ = animatedTilt
            }
            .padding(vertical = 4.dp)
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFCFAF7)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0x18000000), RoundedCornerShape(12.dp))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 9.dp)
                    .padding(top = 18.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(folderPastel.copy(alpha = 0.35f))
                        .border(0.5.dp, Color(0x15000000), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (coverPhoto.isNotEmpty()) {
                        AsyncImage(
                            model = buildImageRequest(LocalContext.current, coverPhoto, cacheKey = "folder_cover_${folder.id}"),
                            contentDescription = folder.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))
                                    )
                                )
                        )
                        if (photoCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "📸 $photoCount",
                                    color = Color.White,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Text(folder.iconName, fontSize = 34.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "No photos yet",
                                fontSize = 10.sp,
                                color = Color(0xFF7A6F65),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2B221B),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                if (timeText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = timeText,
                        fontSize = 10.5.sp,
                        color = Color(0xFF82766B),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-6).dp)
                .width(44.dp)
                .height(13.dp)
                .graphicsLayer { rotationZ = washiTilt }
                .clip(RoundedCornerShape(2.dp))
                .background(folderPastel.copy(alpha = 0.85f))
                .border(0.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 4.dp)
        ) {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier
                    .bouncyHover(haptic = haptic, scaleOnHover = 1.15f, scaleOnPress = 0.88f)
                    .size(28.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = Color(0xFF5D5248).copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                containerColor = currentTheme.surface
            ) {
                val menuTextColor = if (currentTheme.id == "custom") Color(0xFF1F1F1F) else currentTheme.text
                DropdownMenuItem(
                    text = { Text("Edit", color = menuTextColor) },
                    onClick = { showMenu = false; onEdit() },
                    leadingIcon = { Icon(Icons.Default.Edit, null, tint = currentTheme.primary) }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = Color.Red) },
                    onClick = { showMenu = false; onDelete() },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                )
            }
        }
    }
}

@Composable
fun FilmStripFolderItem(
    folder: CoupleFolder,
    index: Int,
    currentTheme: AppTheme,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onClick: () -> Unit
) {
    val hasDate = folder.date != 0L
    val daysDiff = if (hasDate) DateUtils.calculateDaysDifference(folder.date) else 0L
    val timeText = DateUtils.formatFolderDays(daysDiff, hasDate = hasDate)
    var showMenu by remember { mutableStateOf(false) }

    val cachedCover = FolderCoverCache.getCover(folder.id) ?: folder.coverPhotoUrl
    var coverPhoto by remember(folder.id) { mutableStateOf(cachedCover) }
    val cachedCount = FolderCoverCache.getCount(folder.id) ?: folder.photoCount
    var photoCount by remember(folder.id) { mutableIntStateOf(cachedCount) }

    LaunchedEffect(folder.id) {
        if (folder.id.isNotEmpty() && coverPhoto.isEmpty()) {
            try {
                val snapshot = FirebaseFirestore.getInstance()
                    .collection("folders").document(folder.id).collection("images")
                    .orderBy("dateAdded", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .await()
                snapshot.documents.firstOrNull()?.let { doc ->
                    val url = doc.getString("url") ?: ""
                    if (url.isNotEmpty()) {
                        coverPhoto = url
                        FolderCoverCache.setCover(folder.id, url)
                    }
                }
            } catch (_: Exception) { }
        } else if (coverPhoto.isNotEmpty()) {
            FolderCoverCache.setCover(folder.id, coverPhoto)
        }
    }

    val haptic = LocalHapticFeedback.current

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF191817)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .bouncyHover(haptic = haptic, scaleOnHover = 1.04f, scaleOnPress = 0.95f)
            .border(1.dp, Color(0xFF33312E), RoundedCornerShape(10.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(modifier = Modifier.size(width = 7.dp, height = 6.dp).clip(RoundedCornerShape(1.5.dp)).background(Color(0xFF383533)))
                Box(modifier = Modifier.size(width = 7.dp, height = 6.dp).clip(RoundedCornerShape(1.5.dp)).background(Color(0xFF383533)))
                Text(
                    text = "▶ 35mm • 0${index + 1}A",
                    fontSize = 8.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE0A96D)
                )
                Box(modifier = Modifier.size(width = 7.dp, height = 6.dp).clip(RoundedCornerShape(1.5.dp)).background(Color(0xFF383533)))
                Box(modifier = Modifier.size(width = 7.dp, height = 6.dp).clip(RoundedCornerShape(1.5.dp)).background(Color(0xFF383533)))
            }

            Spacer(modifier = Modifier.height(5.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF262422))
                    .border(0.5.dp, Color(0x33E0A96D), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (coverPhoto.isNotEmpty()) {
                    AsyncImage(
                        model = buildImageRequest(LocalContext.current, coverPhoto, cacheKey = "folder_cover_${folder.id}"),
                        contentDescription = folder.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "🎞️ ISO 400",
                            color = Color(0xFFE0A96D),
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(folder.iconName, fontSize = 34.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "EMPTY REEL",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFA89F91)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.name,
                        color = Color(0xFFF2ECE4),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (timeText.isNotEmpty()) timeText else "No date",
                        color = Color(0xFF9E9486),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier
                            .bouncyHover(haptic = haptic, scaleOnHover = 1.15f, scaleOnPress = 0.88f)
                            .size(26.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = Color(0xFFA89F91),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = currentTheme.surface
                    ) {
                        val menuTextColor = if (currentTheme.id == "custom") Color(0xFF1F1F1F) else currentTheme.text
                        DropdownMenuItem(
                            text = { Text("Edit", color = menuTextColor) },
                            onClick = { showMenu = false; onEdit() },
                            leadingIcon = { Icon(Icons.Default.Edit, null, tint = currentTheme.primary) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = Color.Red) },
                            onClick = { showMenu = false; onDelete() },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(modifier = Modifier.size(width = 7.dp, height = 6.dp).clip(RoundedCornerShape(1.5.dp)).background(Color(0xFF383533)))
                Box(modifier = Modifier.size(width = 7.dp, height = 6.dp).clip(RoundedCornerShape(1.5.dp)).background(Color(0xFF383533)))
                Text(
                    text = if (photoCount > 0) "● ${photoCount} EXP" else "● 0 EXP",
                    fontSize = 8.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF7A7369)
                )
                Box(modifier = Modifier.size(width = 7.dp, height = 6.dp).clip(RoundedCornerShape(1.5.dp)).background(Color(0xFF383533)))
                Box(modifier = Modifier.size(width = 7.dp, height = 6.dp).clip(RoundedCornerShape(1.5.dp)).background(Color(0xFF383533)))
            }
        }
    }
}

@Composable
fun FolderItem(
    folder: CoupleFolder,
    index: Int = 0,
    viewMode: String = "polaroid",
    currentTheme: AppTheme,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onClick: () -> Unit
) {
    AnimatedContent(
        targetState = viewMode,
        transitionSpec = {
            (fadeIn(animationSpec = tween(220, delayMillis = 30)) +
                    scaleIn(initialScale = 0.95f, animationSpec = tween(220, delayMillis = 30)))
                .togetherWith(
                    fadeOut(animationSpec = tween(150)) +
                            scaleOut(targetScale = 0.97f, animationSpec = tween(150))
                )
        },
        label = "folderViewModeMorph"
    ) { mode ->
        if (mode == "filmstrip") {
            FilmStripFolderItem(
                folder = folder,
                index = index,
                currentTheme = currentTheme,
                onDelete = onDelete,
                onEdit = onEdit,
                onClick = onClick
            )
        } else {
            PolaroidFolderItem(
                folder = folder,
                index = index,
                currentTheme = currentTheme,
                onDelete = onDelete,
                onEdit = onEdit,
                onClick = onClick
            )
        }
    }
}

@Composable
fun FolderDialog(
    currentTheme: AppTheme,
    folderToEdit: CoupleFolder? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, Long, Boolean, Long) -> Unit
) {
    val context = LocalContext.current
    val currentUid = SessionCache.userId
    val currentPartnerId = SessionCache.partnerId
    val isPartnerLinked = currentPartnerId.isNotEmpty()
    val isOwner = folderToEdit?.let {
        val owner = it.ownerId.ifEmpty { it.participants.firstOrNull() ?: currentUid }
        owner == currentUid
    } ?: true
    val canChangeSharing = isPartnerLinked && isOwner

    val dialogKey = folderToEdit?.id ?: "new"
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    var folderName by remember(dialogKey) { mutableStateOf(folderToEdit?.name ?: "") }
    var selectedIcon by remember(dialogKey) { mutableStateOf(folderToEdit?.iconName ?: "🌲") }
    var selectedDate by remember(dialogKey) { mutableLongStateOf(folderToEdit?.date ?: 0L) }
    var selectedColor by remember(dialogKey) {
        mutableLongStateOf(
            if (folderToEdit != null && folderToEdit.color != 0L) folderToEdit.color
            else FolderColors[0].toArgb().toLong()
        )
    }
    var isShared by remember(dialogKey, isPartnerLinked) {
        mutableStateOf(
            if (isPartnerLinked) {
                folderToEdit?.participants?.contains(currentPartnerId) ?: true
            } else {
                false
            }
        )
    }
    var dateText by remember(dialogKey) {
        mutableStateOf(
            if (selectedDate != 0L) dateFormatter.format(Date(selectedDate)) else "Pick a Date 📅"
        )
    }
    val icons = FolderIcons

    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(max = 650.dp).glassCard(0.95f).padding(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (folderToEdit == null) "New Adventure" else "Edit Adventure",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = currentTheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = currentTheme.primary,
                        focusedLabelColor = currentTheme.primary,
                        focusedTextColor = Color(0xFF1F1F1F),
                        unfocusedTextColor = Color(0xFF1F1F1F)
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val cal = Calendar.getInstance().apply {
                                if (selectedDate != 0L) timeInMillis = selectedDate
                            }
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val picked = Calendar.getInstance().apply {
                                        set(y, m, d, 0, 0, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }
                                    selectedDate = picked.timeInMillis
                                    dateText = dateFormatter.format(Date(selectedDate))
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = currentTheme.primary.copy(alpha = 0.1f))
                    ) {
                        Text(dateText, color = currentTheme.primary)
                    }
                    if (selectedDate != 0L) {
                        IconButton(
                            onClick = {
                                selectedDate = 0L
                                dateText = "Pick a Date 📅"
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Date", tint = Color.Gray)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Share with partner",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (canChangeSharing) Color(0xFF1F1F1F) else Color.Gray
                        )
                        if (!isPartnerLinked) {
                            Text("No partner linked", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        } else if (!isOwner) {
                            Text("Only the album creator can change sharing", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                    Switch(
                        checked = isShared && isPartnerLinked,
                        onCheckedChange = { if (canChangeSharing) isShared = it },
                        enabled = canChangeSharing,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = currentTheme.primary,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color.LightGray.copy(alpha = 0.5f)
                        )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(FolderColors, key = { it.value.toString() }) { color ->
                        val isColorSelected = selectedColor.toInt() == color.toArgb()
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    2.dp,
                                    if (isColorSelected) currentTheme.primary else Color.Transparent,
                                    CircleShape
                                )
                                .clickable { selectedColor = color.toArgb().toLong() }
                        ) {
                            if (isColorSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    null,
                                    tint = Color.Black.copy(0.6f),
                                    modifier = Modifier.align(Alignment.Center).size(20.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.weight(1f, fill = false).heightIn(max = 200.dp)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(icons, key = { it }) { icon ->
                            Text(
                                icon,
                                modifier = Modifier
                                    .clickable { selectedIcon = icon }
                                    .padding(4.dp)
                                    .background(
                                        if (selectedIcon == icon) currentTheme.primary.copy(0.3f) else Color.Transparent,
                                        CircleShape
                                    )
                                    .padding(4.dp),
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val trimmedName = folderName.trim()
                        if (trimmedName.isNotEmpty()) {
                            onSave(trimmedName, selectedIcon, selectedDate, isShared, selectedColor)
                        }
                    },
                    enabled = folderName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = currentTheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (folderToEdit == null) "Create" else "Save", color = Color.White)
                }
            }
        }
    }
}

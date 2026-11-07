package com.example.coupleappgallery

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.security.SecureRandom
import java.util.Locale

private const val INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
private const val INVITE_CODE_TTL_MS = 7L * 24 * 60 * 60 * 1000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavController, currentTheme: AppTheme) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val storage = remember { FirebaseStorage.getInstance() }
    val user = auth.currentUser
    val userUid = user?.uid ?: ""
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // User Data
    var displayName by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf("") }
    var currentPartnerId by remember { mutableStateOf("") }
    val partnerName = SessionCache.partnerName
    var hasLoadedInitialForm by remember(userUid) { mutableStateOf(false) }

    // Loading / Busy states
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var isSavingName by remember { mutableStateOf(false) }
    var isLoggingOut by remember { mutableStateOf(false) }
    var isUnlinking by remember { mutableStateOf(false) }
    var showUnlinkConfirm by remember { mutableStateOf(false) }

    // Partner Invite Code State
    var myInviteCode by remember { mutableStateOf("") }
    var partnerCodeInput by remember { mutableStateOf("") }
    var isGeneratingCode by remember { mutableStateOf(false) }
    var isLinkingPartner by remember { mutableStateOf(false) }

    val isBusy = isUploadingAvatar || isSavingName || isLoggingOut || isUnlinking

    // Colors
    val primaryColor = currentTheme.primary
    val backgroundColor = currentTheme.background
    val surfaceColor = currentTheme.surface
    val textColor = currentTheme.text

    // Load Data keyed on userUid
    DisposableEffect(userUid) {
        if (userUid.isEmpty()) return@DisposableEffect onDispose { }
        val reg = db.collection("users").document(userUid).addSnapshotListener { doc, error ->
            if (error != null) {
                Log.e("FS", "listener@ProfileScreen:userDoc", error)
                return@addSnapshotListener
            }
            if (doc == null) return@addSnapshotListener

            photoUrl = doc.getString("photoUrl") ?: ""
            currentPartnerId = doc.getString("partnerId") ?: ""
            myInviteCode = doc.getString("inviteCode") ?: ""

            if (!hasLoadedInitialForm) {
                displayName = doc.getString("name") ?: ""
                hasLoadedInitialForm = true
            }
        }
        onDispose { reg.remove() }
    }

    // Avatar picker launcher with auto-upload
    val avatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null && userUid.isNotEmpty()) {
            isUploadingAvatar = true
            scope.launch {
                try {
                    val base64DataUri = compressToBase64DataUri(context, uri, maxDim = 512, quality = 78)
                    photoUrl = base64DataUri
                    db.collection("users").document(userUid)
                        .set(mapOf("photoUrl" to base64DataUri), SetOptions.merge())
                        .await()
                    Toast.makeText(context, "Profile photo updated! 📸", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("ProfileScreen", "Avatar upload failed", e)
                    Toast.makeText(context, "Avatar upload failed: ${e.localizedMessage ?: "Please retry."}", Toast.LENGTH_LONG).show()
                } finally {
                    isUploadingAvatar = false
                }
            }
        }
    }

    fun saveDisplayNameNow() {
        val trimmed = displayName.trim().ifEmpty { "Me" }
        displayName = trimmed
        if (userUid.isEmpty() || isSavingName) return
        isSavingName = true
        focusManager.clearFocus()
        scope.launch {
            try {
                db.collection("users").document(userUid)
                    .set(mapOf("name" to trimmed), SetOptions.merge())
                    .await()
                Toast.makeText(context, "Name updated! ✨", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("ProfileScreen", "Failed to update name", e)
                Toast.makeText(context, "Failed to update name.", Toast.LENGTH_SHORT).show()
            } finally {
                isSavingName = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        if (currentTheme.wallpaperUrl.isNotEmpty()) {
            AsyncImage(
                model = buildImageRequest(LocalContext.current, currentTheme.wallpaperUrl, cacheKey = "theme_wallpaper"),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Profile 👤", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                "Back",
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
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ---------------------------------------------------------
                // 1. AVATAR with instant camera picker
                // ---------------------------------------------------------
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .border(3.dp, primaryColor, CircleShape)
                        .clickable(enabled = !isBusy) { avatarLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (photoUrl.isNotEmpty()) {
                        AsyncImage(
                            model = buildImageRequest(LocalContext.current, photoUrl, cacheKey = "avatar_me"),
                            contentDescription = "Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Avatar",
                            tint = Color.Gray,
                            modifier = Modifier.size(64.dp)
                        )
                    }

                    // Upload overlay spinner or camera icon
                    if (isUploadingAvatar) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(primaryColor)
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Change photo", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ---------------------------------------------------------
                // 2. DISPLAY NAME (Inline save with Done checkmark)
                // ---------------------------------------------------------
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    enabled = !isBusy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { saveDisplayNameNow() }),
                    trailingIcon = {
                        IconButton(onClick = { saveDisplayNameNow() }, enabled = !isBusy) {
                            if (isSavingName) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Check, contentDescription = "Save Name", tint = primaryColor)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        focusedLabelColor = primaryColor,
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ---------------------------------------------------------
                // 3. PARTNER STATUS & CODE LINKING CARD
                // ---------------------------------------------------------
                Card(
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    elevation = CardDefaults.cardElevation(2.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = primaryColor)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Partner Connection", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textColor)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (currentPartnerId.isNotEmpty()) {
                            // Linked State
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(primaryColor.copy(alpha = 0.08f))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Linked with",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray
                                    )
                                    Text(
                                        text = partnerName.ifEmpty { "Partner" },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = primaryColor
                                    )
                                }
                                OutlinedButton(
                                    enabled = !isBusy,
                                    onClick = { showUnlinkConfirm = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red.copy(0.8f))
                                ) {
                                    Text(if (isUnlinking) "Unlinking..." else "Unlink", fontSize = 12.sp)
                                }
                            }
                        } else {
                            // Unlinked State: Code Generation & Redemption
                            Text(
                                text = "Pair with your partner using a 6-character private invite code.",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            if (myInviteCode.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(primaryColor.copy(alpha = 0.1f))
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Your Invite Code", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        Text(
                                            text = myInviteCode,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 22.sp,
                                            letterSpacing = 4.sp,
                                            color = primaryColor
                                        )
                                    }
                                    Row {
                                        IconButton(onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("CoupleApp Invite Code", myInviteCode))
                                            Toast.makeText(context, "Code copied! 📋", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Default.ContentCopy, "Copy", tint = primaryColor)
                                        }
                                        IconButton(
                                            enabled = !isGeneratingCode && !isBusy,
                                            onClick = {
                                                val myUid = auth.currentUser?.uid ?: return@IconButton
                                                isGeneratingCode = true
                                                scope.launch {
                                                    try {
                                                        myInviteCode = generatePartnerInviteCode(db, myUid)
                                                        Toast.makeText(context, "New code generated!", Toast.LENGTH_SHORT).show()
                                                    } catch (e: Exception) {
                                                        Log.e("ProfileScreen", "Code regen failed", e)
                                                        Toast.makeText(context, e.message ?: "Failed to generate code.", Toast.LENGTH_SHORT).show()
                                                    } finally {
                                                        isGeneratingCode = false
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Default.Refresh, "Regenerate", tint = primaryColor)
                                        }
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    enabled = !isGeneratingCode && !isBusy,
                                    onClick = {
                                        val myUid = auth.currentUser?.uid ?: return@OutlinedButton
                                        isGeneratingCode = true
                                        scope.launch {
                                            try {
                                                myInviteCode = generatePartnerInviteCode(db, myUid)
                                            } catch (e: Exception) {
                                                Log.e("ProfileScreen", "Invite code generation failed", e)
                                                Toast.makeText(context, e.message ?: "Failed to generate code.", Toast.LENGTH_SHORT).show()
                                            } finally {
                                                isGeneratingCode = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (isGeneratingCode) "Generating..." else "Generate My Invite Code", color = primaryColor)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = partnerCodeInput,
                                    onValueChange = { raw ->
                                        partnerCodeInput = raw.uppercase(Locale.US)
                                            .filter { it.isLetterOrDigit() }
                                            .take(6)
                                    },
                                    label = { Text("Partner's Code") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = primaryColor,
                                        focusedLabelColor = primaryColor
                                    )
                                )
                                Button(
                                    enabled = partnerCodeInput.length == 6 && !isLinkingPartner && !isGeneratingCode && !isBusy,
                                    onClick = {
                                        val myUid = auth.currentUser?.uid ?: return@Button
                                        isLinkingPartner = true
                                        scope.launch {
                                            try {
                                                val linkedPartnerUid = redeemPartnerInviteCode(db, myUid, partnerCodeInput)
                                                val senderLabel = displayName.ifBlank { "Your partner" }
                                                sendNotification(db, linkedPartnerUid, "Linked 💞", "$senderLabel linked with you!")
                                                partnerCodeInput = ""
                                                Toast.makeText(context, "Linked successfully! 💞", Toast.LENGTH_SHORT).show()
                                            } catch (e: FirebaseFirestoreException) {
                                                Log.e("ProfileScreen", "Invite code redemption rejected", e)
                                                val msg = if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                                                    "Cannot link — code is invalid, expired, or that user is already linked."
                                                } else {
                                                    e.message ?: "Failed to link."
                                                }
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            } catch (e: Exception) {
                                                Log.e("ProfileScreen", "Invite code redemption failed", e)
                                                Toast.makeText(context, e.message ?: "Failed to link.", Toast.LENGTH_LONG).show()
                                            } finally {
                                                isLinkingPartner = false
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                                ) {
                                    Text(if (isLinkingPartner) "..." else "Link")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // ---------------------------------------------------------
                // 4. LOG OUT BUTTON
                // ---------------------------------------------------------
                Button(
                    enabled = !isBusy,
                    onClick = {
                        if (isLoggingOut) return@Button
                        isLoggingOut = true
                        val uid = auth.currentUser?.uid
                        scope.launch {
                            withContext(NonCancellable) {
                                if (!uid.isNullOrEmpty()) {
                                    withTimeoutOrNull(2_500L) {
                                        runCatching {
                                            db.collection("users").document(uid).update(
                                                mapOf(
                                                    "fcmToken" to FieldValue.delete(),
                                                    "status" to "Offline"
                                                )
                                            ).await()
                                        }.onFailure { e ->
                                            Log.w("ProfileScreen", "Failed to clear fcmToken/status on logout", e)
                                        }
                                    }
                                }
                                withTimeoutOrNull(2_500L) {
                                    runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
                                        .onFailure { e -> Log.w("ProfileScreen", "Failed to delete local FCM token", e) }
                                }
                                NotificationManagerCompat.from(context).cancelAll()
                                SessionCache.clear()
                                auth.signOut()
                            }
                            navController.navigate("login") {
                                popUpTo(navController.graph.id) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.8f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(if (isLoggingOut) "Logging out..." else "Log Out", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }

    if (showUnlinkConfirm) {
        AlertDialog(
            onDismissRequest = { if (!isUnlinking) showUnlinkConfirm = false },
            containerColor = surfaceColor,
            title = { Text("Unlink Partner?", color = currentTheme.text) },
            text = {
                Text(
                    "Are you sure you want to unlink from $partnerName? Shared albums will return to their creators, and pending wishes will be cleared.",
                    color = currentTheme.text
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isUnlinking,
                    onClick = {
                        showUnlinkConfirm = false
                        val myUid = auth.currentUser?.uid ?: return@TextButton
                        val oldPartnerId = currentPartnerId
                        if (oldPartnerId.isEmpty() || isUnlinking) return@TextButton
                        isUnlinking = true
                        scope.launch {
                            try {
                                revokeSharedItemsOnUnlink(db, myUid, oldPartnerId)
                                val batch = db.batch()
                                batch.update(db.collection("users").document(myUid), "partnerId", "")
                                batch.update(db.collection("users").document(oldPartnerId), "partnerId", "")
                                batch.commit().await()
                                currentPartnerId = ""
                                Toast.makeText(context, "Unlinked — shared items returned to their creators.", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Log.e("ProfileScreen", "Unlink failed", e)
                                Toast.makeText(context, "Failed to unlink: ${e.localizedMessage ?: "Error"}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isUnlinking = false
                            }
                        }
                    }
                ) {
                    Text("Unlink", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlinkConfirm = false }) {
                    Text("Cancel", color = currentTheme.text)
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Partner Invite Transactions & Revocation
// ---------------------------------------------------------------------------

suspend fun generatePartnerInviteCode(db: FirebaseFirestore, myUid: String): String {
    val rng = SecureRandom()
    repeat(10) {
        val candidate = buildString(6) {
            repeat(6) { append(INVITE_ALPHABET[rng.nextInt(INVITE_ALPHABET.length)]) }
        }
        val created = db.runTransaction { tx ->
            val candidateRef = db.collection("invite_codes").document(candidate)
            if (tx.get(candidateRef).exists()) return@runTransaction ""

            val myRef = db.collection("users").document(myUid)
            val mySnap = tx.get(myRef)
            if ((mySnap.getString("partnerId") ?: "").isNotEmpty()) {
                throw IllegalStateException("You are already linked with a partner.")
            }
            val oldCode = mySnap.getString("inviteCode") ?: ""
            val existingOldCodeRef = if (oldCode.isNotEmpty() && oldCode != candidate) {
                db.collection("invite_codes").document(oldCode).takeIf { tx.get(it).exists() }
            } else {
                null
            }

            existingOldCodeRef?.let { tx.delete(it) }
            tx.set(
                candidateRef,
                mapOf(
                    "code" to candidate,
                    "ownerUid" to myUid,
                    "createdAt" to System.currentTimeMillis()
                )
            )
            tx.set(myRef, mapOf("inviteCode" to candidate), SetOptions.merge())
            candidate
        }.await()
        if (created.isNotEmpty()) return created
    }
    throw IllegalStateException("Could not allocate a unique invite code. Please try again.")
}

suspend fun redeemPartnerInviteCode(db: FirebaseFirestore, myUid: String, rawCode: String): String {
    val code = rawCode.trim().uppercase(Locale.US)
    require(code.length == 6) { "Enter a 6-character invite code." }

    return db.runTransaction { tx ->
        val codeRef = db.collection("invite_codes").document(code)
        val codeSnap = tx.get(codeRef)
        if (!codeSnap.exists()) {
            throw IllegalStateException("Invalid or already-used invite code.")
        }
        val targetUid = codeSnap.getString("ownerUid") ?: ""
        if (targetUid.isEmpty() || targetUid == myUid) {
            throw IllegalStateException("You can't link using your own invite code.")
        }
        val createdAt = codeSnap.getLong("createdAt") ?: 0L
        if (createdAt > 0L && System.currentTimeMillis() - createdAt > INVITE_CODE_TTL_MS) {
            throw IllegalStateException("This invite code has expired. Ask your partner to generate a new one.")
        }

        val myRef = db.collection("users").document(myUid)
        val mySnap = tx.get(myRef)
        if ((mySnap.getString("partnerId") ?: "").isNotEmpty()) {
            throw IllegalStateException("You are already linked with someone.")
        }
        val myOldCode = mySnap.getString("inviteCode") ?: ""
        val existingMyOldCodeRef = if (myOldCode.isNotEmpty() && myOldCode != code) {
            db.collection("invite_codes").document(myOldCode).takeIf { tx.get(it).exists() }
        } else {
            null
        }

        tx.delete(codeRef)
        existingMyOldCodeRef?.let { tx.delete(it) }

        tx.update(myRef, mapOf("partnerId" to targetUid, "inviteCode" to ""))
        tx.update(db.collection("users").document(targetUid), mapOf("partnerId" to myUid, "inviteCode" to ""))
        targetUid
    }.await()
}

suspend fun revokeSharedItemsOnUnlink(db: FirebaseFirestore, myUid: String, exPartnerUid: String) {
    val ops = mutableListOf<(com.google.firebase.firestore.WriteBatch) -> Unit>()

    val sharedFolders = db.collection("folders")
        .whereArrayContains("participants", myUid)
        .get().await()

    for (doc in sharedFolders.documents) {
        val participants = (doc.get("participants") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        if (participants.contains(exPartnerUid)) {
            val owner = doc.getString("ownerId")?.ifEmpty { null } ?: participants.firstOrNull() ?: myUid
            ops.add { batch ->
                batch.update(
                    doc.reference,
                    mapOf(
                        "participants" to listOf(owner),
                        "ownerId" to owner
                    )
                )
            }
        }
    }

    val sentWishes = db.collection("requests")
        .whereEqualTo("requesterId", myUid)
        .whereEqualTo("receiverId", exPartnerUid)
        .get().await()
    for (doc in sentWishes.documents) {
        ops.add { batch -> batch.delete(doc.reference) }
    }

    val receivedWishes = db.collection("requests")
        .whereEqualTo("requesterId", exPartnerUid)
        .whereEqualTo("receiverId", myUid)
        .get().await()
    for (doc in receivedWishes.documents) {
        ops.add { batch -> batch.delete(doc.reference) }
    }

    for (chunk in ops.chunked(450)) {
        val batch = db.batch()
        chunk.forEach { it(batch) }
        batch.commit().await()
    }
}

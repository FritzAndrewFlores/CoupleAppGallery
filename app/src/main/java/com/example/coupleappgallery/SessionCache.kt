package com.example.coupleappgallery

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-scoped in-memory cache for folder cover image models and counts.
 * Prevents re-querying Firestore when switching between tabs (Albums <-> Together).
 */
object FolderCoverCache {
    private val covers = ConcurrentHashMap<String, String>()
    private val counts = ConcurrentHashMap<String, Int>()

    fun getCover(folderId: String): String? = covers[folderId]

    fun setCover(folderId: String, url: String) {
        if (url.isNotEmpty()) {
            covers[folderId] = url
        }
    }

    fun getCount(folderId: String): Int? = counts[folderId]

    fun setCount(folderId: String, count: Int) {
        counts[folderId] = count
    }

    fun remove(folderId: String) {
        covers.remove(folderId)
        counts.remove(folderId)
    }

    fun clear() {
        covers.clear()
        counts.clear()
    }
}

/**
 * Process-scoped cache for the current user's and partner's profile data.
 * Owns ONE Firestore listener on each doc for the entire app session.
 * Screens read these values as Compose state — no need to attach redundant listeners.
 *
 * Initialized in MainActivity after auth. Cleared on sign-out.
 */
object SessionCache {

    // --- Current user fields (mirrors users/{userId} doc) ---
    var userId by mutableStateOf("")
        private set
    var userName by mutableStateOf("Me")
        private set
    var userPhotoUrl by mutableStateOf("")
        private set
    var userMood by mutableStateOf("")
        private set
    var userLoveNote by mutableStateOf("")
        private set
    var userInviteCode by mutableStateOf("")
        private set
    var partnerId by mutableStateOf("")
        private set

    // Theme fields mirrored from users/{userId}
    var themeId by mutableStateOf("forest_green")
        private set
    var themePrimary by mutableIntStateOf(0xFF2E5948.toInt())
        private set
    var themeSecondary by mutableIntStateOf(0xFF8FBC8F.toInt())
        private set
    var themeBg by mutableIntStateOf(0xFFF4F9F4.toInt())
        private set
    var themeText by mutableIntStateOf(0xFF4A3728.toInt())
        private set
    var themeWallpaper by mutableStateOf("")
        private set

    // Card-background prefs read by HomeScreen
    var moodBgId by mutableStateOf("glass_light")
        private set
    var requestBgId by mutableStateOf("grad_romantic")
        private set
    var noteBgId by mutableStateOf("glass_light")
        private set
    var wishlistBgId by mutableStateOf("glass_light")
        private set

    // --- Partner fields (mirrors users/{partnerId} doc) ---
    var partnerName by mutableStateOf("Partner")
        private set
    var partnerPhotoUrl by mutableStateOf("")
        private set
    var partnerMood by mutableStateOf("")
        private set
    var partnerLoveNote by mutableStateOf("")
        private set
    var partnerStatus by mutableStateOf("Offline")
        private set

    // Couple anniversary date (timestamp in millis)
    var anniversaryDate by mutableStateOf(0L)
        private set

    private var userReg: ListenerRegistration? = null
    private var partnerReg: ListenerRegistration? = null
    private var initializedFor: String = ""

    /**
     * Start listening for user and partner data. Safe to call multiple times for the
     * same uid — subsequent calls are no-ops unless a previous listener terminated on error.
     */
    fun init(uid: String) {
        if (uid.isEmpty()) {
            clear()
            return
        }
        if (initializedFor == uid && userReg != null) return
        clear()
        initializedFor = uid
        userId = uid

        val db = FirebaseFirestore.getInstance()
        val userDocRef = db.collection("users").document(uid)
        userReg = userDocRef.addSnapshotListener { doc, error ->
            if (error != null) {
                Log.e("FS", "listener@SessionCache:userDoc", error)
                userReg?.remove()
                userReg = null
                initializedFor = ""
                return@addSnapshotListener
            }
            if (doc == null) return@addSnapshotListener
            if (!doc.exists()) {
                // Self-heal if initial sign-up Firestore write was interrupted
                userDocRef.set(
                    mapOf(
                        "name" to "New User",
                        "photoUrl" to "",
                        "partnerId" to "",
                        "inviteCode" to "",
                        "themeId" to "forest_green"
                    ),
                    SetOptions.merge()
                )
                return@addSnapshotListener
            }

            userName = doc.getString("name") ?: "Me"
            userPhotoUrl = doc.getString("photoUrl") ?: ""
            userMood = doc.getString("mood") ?: ""
            userLoveNote = doc.getString("loveNote") ?: ""
            userInviteCode = doc.getString("inviteCode") ?: ""
            anniversaryDate = doc.getLong("anniversaryDate") ?: 0L
            val newPartnerId = doc.getString("partnerId") ?: ""

            themeId = doc.getString("themeId") ?: "forest_green"
            themePrimary = (doc.getLong("themePrimary") ?: 0xFF2E5948L).toInt()
            themeSecondary = (doc.getLong("themeSecondary") ?: 0xFF8FBC8FL).toInt()
            themeBg = (doc.getLong("themeBg") ?: 0xFFF4F9F4L).toInt()
            themeText = (doc.getLong("themeText") ?: 0xFF4A3728L).toInt()
            themeWallpaper = doc.getString("themeWallpaper") ?: ""

            moodBgId = doc.getString("moodBgId") ?: "glass_light"
            requestBgId = doc.getString("requestBgId") ?: "grad_romantic"
            noteBgId = doc.getString("noteBgId") ?: "glass_light"
            wishlistBgId = doc.getString("wishlistBgId") ?: "glass_light"

            if (newPartnerId != partnerId || (newPartnerId.isNotEmpty() && partnerReg == null)) {
                partnerId = newPartnerId
                partnerReg?.remove()
                partnerReg = null
                partnerName = "Partner"
                partnerPhotoUrl = ""
                partnerMood = ""
                partnerLoveNote = ""
                partnerStatus = "Offline"
                if (newPartnerId.isNotEmpty()) {
                    partnerReg = db.collection("users").document(newPartnerId)
                        .addSnapshotListener { pDoc, pError ->
                            if (pError != null) {
                                Log.e("FS", "listener@SessionCache:partnerDoc", pError)
                                partnerReg?.remove()
                                partnerReg = null
                                return@addSnapshotListener
                            }
                            if (pDoc == null || !pDoc.exists()) return@addSnapshotListener
                            partnerName = pDoc.getString("name") ?: "Partner"
                            partnerPhotoUrl = pDoc.getString("photoUrl") ?: ""
                            partnerMood = pDoc.getString("mood") ?: ""
                            partnerLoveNote = pDoc.getString("loveNote") ?: ""
                            partnerStatus = pDoc.getString("status") ?: "Offline"
                            val pAnniversary = pDoc.getLong("anniversaryDate") ?: 0L
                            if (pAnniversary > 0L && (anniversaryDate == 0L || anniversaryDate != pAnniversary)) {
                                anniversaryDate = pAnniversary
                            }
                        }
                }
            }
        }
    }

    /** Remove both listeners and reset state. Call on sign-out. */
    fun clear() {
        FolderCoverCache.clear()
        userReg?.remove()
        partnerReg?.remove()
        userReg = null
        partnerReg = null
        initializedFor = ""
        userId = ""
        userName = "Me"
        userPhotoUrl = ""
        userMood = ""
        userLoveNote = ""
        userInviteCode = ""
        partnerId = ""
        themeId = "forest_green"
        themePrimary = 0xFF2E5948.toInt()
        themeSecondary = 0xFF8FBC8F.toInt()
        themeBg = 0xFFF4F9F4.toInt()
        themeText = 0xFF4A3728.toInt()
        themeWallpaper = ""
        partnerName = "Partner"
        partnerPhotoUrl = ""
        partnerMood = ""
        partnerLoveNote = ""
        partnerStatus = "Offline"
        anniversaryDate = 0L
        moodBgId = "glass_light"
        requestBgId = "grad_romantic"
        noteBgId = "glass_light"
        wishlistBgId = "glass_light"
    }
}

package com.example.coupleappgallery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Process-scoped cache for the current user's and partner's profile data.
 * Owns ONE Firestore listener on each doc for the entire app session.
 * Screens read these values as Compose state — no need to attach their own listeners.
 *
 * Initialized once in MainActivity after auth. Cleared on sign-out.
 * Not safe for multi-user-per-process scenarios (this app is single-user-per-device).
 */
object SessionCache {

    // --- Current user fields (mirrors users/{userId} doc) ---
    var userId by mutableStateOf("")
        private set
    var userName by mutableStateOf("Me")
        private set
    var userPhotoUrl by mutableStateOf("")
        private set
    var partnerId by mutableStateOf("")
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

    private var userReg: ListenerRegistration? = null
    private var partnerReg: ListenerRegistration? = null
    private var initializedFor: String = ""

    /**
     * Start listening for user and partner data. Safe to call multiple times for the
     * same uid — subsequent calls are no-ops. If called with a different uid (e.g. sign out
     * → sign in), the previous listeners are removed first.
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
        userReg = db.collection("users").document(uid).addSnapshotListener { doc, _ ->
            if (doc == null) return@addSnapshotListener
            userName = doc.getString("name") ?: "Me"
            userPhotoUrl = doc.getString("photoUrl") ?: ""
            val newPartnerId = doc.getString("partnerId") ?: ""
            moodBgId = doc.getString("moodBgId") ?: "glass_light"
            requestBgId = doc.getString("requestBgId") ?: "grad_romantic"
            noteBgId = doc.getString("noteBgId") ?: "glass_light"
            wishlistBgId = doc.getString("wishlistBgId") ?: "glass_light"

            if (newPartnerId != partnerId) {
                partnerId = newPartnerId
                // Re-register partner listener for the new partnerId
                partnerReg?.remove()
                partnerReg = null
                partnerName = "Partner"
                partnerPhotoUrl = ""
                partnerMood = ""
                partnerLoveNote = ""
                partnerStatus = "Offline"
                if (newPartnerId.isNotEmpty()) {
                    partnerReg = db.collection("users").document(newPartnerId)
                        .addSnapshotListener { pDoc, _ ->
                            if (pDoc == null) return@addSnapshotListener
                            partnerName = pDoc.getString("name") ?: "Partner"
                            partnerPhotoUrl = pDoc.getString("photoUrl") ?: ""
                            partnerMood = pDoc.getString("mood") ?: ""
                            partnerLoveNote = pDoc.getString("loveNote") ?: ""
                            partnerStatus = pDoc.getString("status") ?: "Offline"
                        }
                }
            }
        }
    }

    /** Remove both listeners and reset state. Call on sign-out. */
    fun clear() {
        userReg?.remove()
        partnerReg?.remove()
        userReg = null
        partnerReg = null
        initializedFor = ""
        userId = ""
        userName = "Me"
        userPhotoUrl = ""
        partnerId = ""
        partnerName = "Partner"
        partnerPhotoUrl = ""
        partnerMood = ""
        partnerLoveNote = ""
        partnerStatus = "Offline"
        moodBgId = "glass_light"
        requestBgId = "grad_romantic"
        noteBgId = "glass_light"
        wishlistBgId = "glass_light"
    }
}

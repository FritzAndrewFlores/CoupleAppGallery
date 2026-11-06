package com.example.coupleappgallery

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "DeletionHelper"

// ---------------------------------------------------------------------------
// FOLDER FULL DELETE
// Deletes: images / journal / plans subcollection docs, Storage files, folder doc.
// Preserves the parent Firestore doc if Storage listing fails transiently so
// storage.rules participant permissions are never orphaned.
// ---------------------------------------------------------------------------
fun deleteFolderFully(
    db: FirebaseFirestore,
    storage: FirebaseStorage,
    folderId: String,
    onFailure: ((Exception) -> Unit)? = null
) {
    if (folderId.isEmpty()) return
    val folderRef = db.collection("folders").document(folderId)

    deleteSubcollection(db, "folders/$folderId/images") {
        deleteSubcollection(db, "folders/$folderId/journal") {
            deleteSubcollection(db, "folders/$folderId/plans") {
                val storageRef = storage.reference.child("folders/$folderId")
                storageRef.listAll()
                    .addOnSuccessListener { listResult ->
                        val items = listResult.items
                        if (items.isEmpty()) {
                            folderRef.delete()
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "deleteFolderFully: failed to delete folder doc $folderId", e)
                                    onFailure?.invoke(e)
                                }
                            return@addOnSuccessListener
                        }
                        val remaining = AtomicInteger(items.size)
                        val failedStorageCount = AtomicInteger(0)
                        items.forEach { fileRef ->
                            fileRef.delete()
                                .addOnSuccessListener {
                                    if (remaining.decrementAndGet() == 0) {
                                        finishParentDelete(folderRef, failedStorageCount.get(), onFailure)
                                    }
                                }
                                .addOnFailureListener { e ->
                                    val isAlreadyGone = (e as? StorageException)?.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND
                                    if (!isAlreadyGone) {
                                        failedStorageCount.incrementAndGet()
                                        Log.e(TAG, "deleteFolderFully: failed to delete storage file ${fileRef.path}", e)
                                    }
                                    if (remaining.decrementAndGet() == 0) {
                                        finishParentDelete(folderRef, failedStorageCount.get(), onFailure)
                                    }
                                }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "deleteFolderFully: listAll failed for $folderId; proceeding with folder doc delete", e)
                        folderRef.delete()
                            .addOnFailureListener { e2 ->
                                Log.e(TAG, "deleteFolderFully: failed to delete folder doc $folderId", e2)
                                onFailure?.invoke(e2)
                            }
                    }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// SPECIAL DAY FULL DELETE
// Deletes: plans / images subcollection docs, Storage files (including custom
// background image in event_bgs/ if present), and finally the event doc.
// ---------------------------------------------------------------------------
fun deleteSpecialDayFully(
    db: FirebaseFirestore,
    storage: FirebaseStorage,
    eventId: String,
    backgroundUrl: String = "",
    onFailure: ((Exception) -> Unit)? = null
) {
    if (eventId.isEmpty()) return
    val eventRef = db.collection("special_days").document(eventId)

    // Best-effort cleanup of custom event background image if stored in Firebase Storage
    if (backgroundUrl.isNotEmpty() && backgroundUrl.contains("firebasestorage.googleapis.com")) {
        runCatching {
            storage.getReferenceFromUrl(backgroundUrl).delete()
                .addOnFailureListener { e ->
                    Log.w(TAG, "deleteSpecialDayFully: custom background cleanup skipped", e)
                }
        }
    }

    deleteSubcollection(db, "special_days/$eventId/plans") {
        deleteSubcollection(db, "special_days/$eventId/images") {
            val storageRef = storage.reference.child("special_days/$eventId")
            storageRef.listAll()
                .addOnSuccessListener { listResult ->
                    val items = listResult.items
                    if (items.isEmpty()) {
                        eventRef.delete()
                            .addOnFailureListener { e ->
                                Log.e(TAG, "deleteSpecialDayFully: failed to delete event doc $eventId", e)
                                onFailure?.invoke(e)
                            }
                        return@addOnSuccessListener
                    }
                    val remaining = AtomicInteger(items.size)
                    val failedStorageCount = AtomicInteger(0)
                    items.forEach { fileRef ->
                        fileRef.delete()
                            .addOnSuccessListener {
                                if (remaining.decrementAndGet() == 0) {
                                    finishParentDelete(eventRef, failedStorageCount.get(), onFailure)
                                }
                            }
                            .addOnFailureListener { e ->
                                val isAlreadyGone = (e as? StorageException)?.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND
                                if (!isAlreadyGone) {
                                    failedStorageCount.incrementAndGet()
                                    Log.e(TAG, "deleteSpecialDayFully: failed to delete storage file ${fileRef.path}", e)
                                }
                                if (remaining.decrementAndGet() == 0) {
                                    finishParentDelete(eventRef, failedStorageCount.get(), onFailure)
                                }
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "deleteSpecialDayFully: listAll failed for $eventId; proceeding with event doc delete", e)
                    eventRef.delete()
                        .addOnFailureListener { e2 ->
                            Log.e(TAG, "deleteSpecialDayFully: failed to delete event doc $eventId", e2)
                            onFailure?.invoke(e2)
                        }
                }
        }
    }
}

private fun finishParentDelete(
    parentRef: com.google.firebase.firestore.DocumentReference,
    failedStorageCount: Int,
    onFailure: ((Exception) -> Unit)?
) {
    if (failedStorageCount > 0) {
        val err = IllegalStateException("Failed to delete $failedStorageCount media file(s). Please retry.")
        Log.e(TAG, err.message ?: "Storage cleanup incomplete")
        onFailure?.invoke(err)
        return
    }
    parentRef.delete()
        .addOnFailureListener { e ->
            Log.e(TAG, "Failed to delete parent doc ${parentRef.path}", e)
            onFailure?.invoke(e)
        }
}

// ---------------------------------------------------------------------------
// Generic paginated batched subcollection delete.
// ---------------------------------------------------------------------------
private fun deleteSubcollection(
    db: FirebaseFirestore,
    collectionPath: String,
    onComplete: () -> Unit
) {
    val pageLimit = 400L
    fun deletePage() {
        db.collection(collectionPath).limit(pageLimit).get()
            .addOnSuccessListener { snap: QuerySnapshot ->
                if (snap.isEmpty) {
                    onComplete()
                    return@addOnSuccessListener
                }
                val batch = db.batch()
                snap.documents.forEach { batch.delete(it.reference) }
                batch.commit()
                    .addOnSuccessListener {
                        if (snap.size() < pageLimit) onComplete() else deletePage()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "deleteSubcollection: batch commit failed for $collectionPath", e)
                        onComplete()
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "deleteSubcollection: get failed for $collectionPath", e)
                onComplete()
            }
    }
    deletePage()
}

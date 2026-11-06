package com.example.coupleappgallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import coil.request.ImageRequest
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

const val DATA_URI_PREFIX = "data:image/jpeg;base64,"
private const val MAX_DIM = 1200
private const val JPEG_QUALITY = 75
private const val MAX_BYTE_SIZE = 400_000 // 400 KB limit ensures Base64 (~533 KB) easily fits in 1MB Firestore doc
private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

/**
 * Pure Kotlin Base64 encoder used as fallback in JVM host unit tests where android.util.Base64 is unmocked.
 */
fun encodeBase64Kotlin(bytes: ByteArray): String {
    val sb = StringBuilder((bytes.size * 4 + 2) / 3)
    var i = 0
    val len = bytes.size
    while (i < len) {
        val b0 = bytes[i++].toInt() and 0xFF
        val hasB1 = i < len
        val b1 = if (hasB1) bytes[i++].toInt() and 0xFF else 0
        val hasB2 = i < len
        val b2 = if (hasB2) bytes[i++].toInt() and 0xFF else 0

        val n = (b0 shl 16) or (b1 shl 8) or b2
        sb.append(BASE64_ALPHABET[(n shr 18) and 0x3F])
        sb.append(BASE64_ALPHABET[(n shr 12) and 0x3F])
        if (hasB1) {
            sb.append(BASE64_ALPHABET[(n shr 6) and 0x3F])
        } else {
            sb.append('=')
        }
        if (hasB2) {
            sb.append(BASE64_ALPHABET[n and 0x3F])
        } else {
            sb.append('=')
        }
    }
    return sb.toString()
}

/**
 * Pure Kotlin Base64 decoder used as fallback in JVM host unit tests where android.util.Base64 is unmocked.
 */
fun decodeBase64Kotlin(base64Str: String): ByteArray {
    val clean = base64Str.filter { it in BASE64_ALPHABET || it == '=' }
    if (clean.isEmpty()) return ByteArray(0)

    val out = ByteArrayOutputStream()
    var buffer = 0
    var bitsCollected = 0
    for (ch in clean) {
        if (ch == '=') break
        val v = BASE64_ALPHABET.indexOf(ch)
        if (v < 0) continue
        buffer = (buffer shl 6) or v
        bitsCollected += 6
        if (bitsCollected >= 8) {
            bitsCollected -= 8
            out.write((buffer shr bitsCollected) and 0xFF)
        }
    }
    return out.toByteArray()
}

/**
 * Encodes [bytes] to Base64 string safely across Android runtime and JVM unit tests.
 */
fun encodeBase64Safe(bytes: ByteArray): String {
    return try {
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    } catch (_: RuntimeException) {
        encodeBase64Kotlin(bytes)
    }
}

/**
 * Decodes [base64Str] safely across Android runtime and JVM unit tests.
 */
fun decodeBase64Safe(base64Str: String): ByteArray {
    return try {
        android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
    } catch (_: RuntimeException) {
        decodeBase64Kotlin(base64Str)
    }
}

/**
 * Decode [uri] off the main thread, down-sample to max [maxDim] px on the long edge,
 * apply full EXIF orientation (including front-camera horizontal/vertical flips and transposes)
 * and scaling in a single Matrix pass, flatten alpha onto white for PNGs/WebPs, and compress to JPEG.
 * Automatically steps down quality if the compressed output exceeds [MAX_BYTE_SIZE] to guarantee
 * it fits within Firestore's 1 MiB document limit.
 */
suspend fun compressImage(
    context: Context,
    uri: Uri,
    maxDim: Int = MAX_DIM,
    quality: Int = JPEG_QUALITY
): ByteArray = withContext(Dispatchers.IO) {
    // 1. Decode bounds only to compute power-of-2 inSampleSize
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }

    val (w, h) = opts.outWidth to opts.outHeight
    var sampleSize = 1
    if (w > 0 && h > 0) {
        val longest = maxOf(w, h)
        while (longest / (sampleSize * 2) >= maxDim) sampleSize *= 2
    }

    // 2. Decode bitmap with inSampleSize
    val decodeOpts = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val raw = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, decodeOpts)
    } ?: error("Could not decode bitmap from $uri")

    var transformed: Bitmap? = null
    var flattened: Bitmap? = null
    try {
        // 3. Read EXIF orientation (supports rotation + mirrored front-camera selfies)
        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            try {
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
        } ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        var swapsDimensions = false
        var hasExifTransform = true
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
                swapsDimensions = true
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> {
                matrix.postRotate(90f)
                swapsDimensions = true
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
                swapsDimensions = true
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> {
                matrix.postRotate(270f)
                swapsDimensions = true
            }
            else -> hasExifTransform = false
        }

        val orientedW = if (swapsDimensions) raw.height else raw.width
        val orientedH = if (swapsDimensions) raw.width else raw.height
        val longestSide = maxOf(orientedW, orientedH).coerceAtLeast(1)
        val scale = if (longestSide > maxDim) maxDim.toFloat() / longestSide else 1f

        if (scale < 1f) {
            // Protect against rounding down to 0px on extreme panoramic/tall aspect ratios
            val minScaleX = 1f / orientedW.coerceAtLeast(1)
            val minScaleY = 1f / orientedH.coerceAtLeast(1)
            matrix.postScale(maxOf(scale, minScaleX), maxOf(scale, minScaleY))
        }

        val oriented = if (hasExifTransform || scale < 1f) {
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also {
                if (it !== raw) transformed = it
            }
        } else {
            raw
        }

        // 4. Flatten transparency onto white before JPEG encoding so transparent PNGs don't turn black
        val toCompress = if (oriented.hasAlpha()) {
            createBitmap(
                oriented.width.coerceAtLeast(1),
                oriented.height.coerceAtLeast(1)
            ).also { canvasBmp ->
                flattened = canvasBmp
                val canvas = Canvas(canvasBmp)
                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(oriented, 0f, 0f, null)
            }
        } else {
            oriented
        }

        // 5. Compress to JPEG with safety step-down to stay under MAX_BYTE_SIZE
        var currentQuality = quality
        var bytes = ByteArrayOutputStream().use { out ->
            toCompress.compress(Bitmap.CompressFormat.JPEG, currentQuality, out)
            out.toByteArray()
        }

        while (bytes.size > MAX_BYTE_SIZE && currentQuality > 40) {
            currentQuality -= 10
            bytes = ByteArrayOutputStream().use { out ->
                toCompress.compress(Bitmap.CompressFormat.JPEG, currentQuality, out)
                out.toByteArray()
            }
        }

        bytes
    } finally {
        flattened?.let { if (!it.isRecycled) it.recycle() }
        transformed?.let { if (!it.isRecycled) it.recycle() }
        if (!raw.isRecycled) raw.recycle()
    }
}

/**
 * Compresses [uri] and encodes it into a standard Base64 Data URI string
 * (`data:image/jpeg;base64,...`) suitable for direct storage in Firestore documents.
 */
suspend fun compressToBase64DataUri(
    context: Context,
    uri: Uri,
    maxDim: Int = MAX_DIM,
    quality: Int = JPEG_QUALITY
): String = withContext(Dispatchers.IO) {
    val bytes = compressImage(context, uri, maxDim, quality)
    DATA_URI_PREFIX + encodeBase64Safe(bytes)
}

// ============================================================================
// HIGH-PERFORMANCE IN-MEMORY DECODED BYTE CACHE
// ============================================================================

/**
 * High-performance thread-safe LRU cache for decoded Base64 image byte arrays.
 * Prevents redundant CPU-heavy Base64 string decoding and massive garbage collection
 * during fast scrolling in LazyColumn and LazyVerticalGrid.
 * Built with LinkedHashMap so it runs seamlessly on both Android runtime and JVM unit tests.
 */
object Base64ByteCache {
    private val maxBytes: Long = (Runtime.getRuntime().maxMemory() / 8).coerceAtLeast(16L * 1024 * 1024)
    private var currentBytes: Long = 0L

    private val lock = Any()
    private val map = object : LinkedHashMap<String, ByteArray>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean {
            return currentBytes > maxBytes
        }
    }

    fun get(key: String): ByteArray? {
        synchronized(lock) {
            return map[key]
        }
    }

    fun put(key: String, bytes: ByteArray) {
        synchronized(lock) {
            val old = map.put(key, bytes)
            if (old != null) {
                currentBytes -= old.size
            }
            currentBytes += bytes.size
            while (currentBytes > maxBytes && map.isNotEmpty()) {
                val iterator = map.entries.iterator()
                if (iterator.hasNext()) {
                    val entry = iterator.next()
                    currentBytes -= entry.value.size
                    iterator.remove()
                } else {
                    break
                }
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            map.clear()
            currentBytes = 0L
        }
    }
}

/**
 * Resolves an image model for Coil [coil.compose.AsyncImage] or [coil.request.ImageRequest].
 * If [model] is a Base64 Data URI (`data:image/...;base64,...`) or raw Base64 string,
 * it checks [Base64ByteCache] first for an instant O(1) hit before decoding to [ByteArray].
 * If [model] is a network URL or Uri, it returns it directly for backwards compatibility.
 */
fun resolveImageModel(model: Any?, explicitKey: String? = null): Any? {
    if (model == null) return null
    if (model is String) {
        if (model.isBlank()) return null
        if (model.startsWith("data:")) {
            val cacheKey = explicitKey ?: "b64_${model.length}_${model.take(48).hashCode()}"
            val cached = Base64ByteCache.get(cacheKey)
            if (cached != null) return cached

            val commaIndex = model.indexOf(',')
            val base64Data = if (commaIndex != -1) model.substring(commaIndex + 1) else model
            return try {
                val decoded = decodeBase64Safe(base64Data)
                Base64ByteCache.put(cacheKey, decoded)
                decoded
            } catch (e: Exception) {
                Log.w("ImageUtils", "Failed to decode base64 data URI", e)
                model
            }
        }
        // Check if it's a raw base64 string without data: prefix
        if (!model.startsWith("http://") && !model.startsWith("https://") &&
            !model.startsWith("content://") && !model.startsWith("file://") &&
            !model.startsWith("android.resource://") && model.length > 100
        ) {
            val cacheKey = explicitKey ?: "raw_${model.length}_${model.take(48).hashCode()}"
            val cached = Base64ByteCache.get(cacheKey)
            if (cached != null) return cached

            return try {
                val decoded = decodeBase64Safe(model)
                Base64ByteCache.put(cacheKey, decoded)
                decoded
            } catch (_: Exception) {
                model
            }
        }
    }
    return model
}

/**
 * Creates an optimized [ImageRequest] configured with memory and disk caching for Base64 Data URIs and URLs.
 * If [cacheKey] is provided, it is used for explicit deterministic memory/disk cache lookups.
 */
fun buildImageRequest(context: Context, model: Any?, cacheKey: String? = null): ImageRequest? {
    if (model == null) return null
    val effectiveKey = cacheKey ?: when {
        model is String && model.startsWith("data:") ->
            "b64_${model.length}_${model.take(48).hashCode()}"
        model is String && model.isNotEmpty() ->
            "str_${model.hashCode()}"
        else -> null
    }
    val resolved = resolveImageModel(model, effectiveKey) ?: return null
    return ImageRequest.Builder(context)
        .data(resolved)
        .apply {
            if (effectiveKey != null) {
                memoryCacheKey(effectiveKey)
                diskCacheKey(effectiveKey)
            }
        }
        .crossfade(true)
        .build()
}

/**
 * Backwards-compatibility wrapper for existing callers.
 */
suspend fun compressForUpload(context: Context, uri: Uri): ByteArray =
    compressImage(context, uri, maxDim = 1600, quality = 82)

/**
 * Backwards-compatibility wrapper for Firebase Storage upload.
 */
suspend fun uploadCompressedJpeg(
    context: Context,
    uri: Uri,
    storageRef: StorageReference
): String {
    val bytes = compressForUpload(context, uri)
    val metadata = StorageMetadata.Builder()
        .setContentType("image/jpeg")
        .build()
    return storageRef.putBytes(bytes, metadata).await()
        .storage.downloadUrl.await().toString()
}

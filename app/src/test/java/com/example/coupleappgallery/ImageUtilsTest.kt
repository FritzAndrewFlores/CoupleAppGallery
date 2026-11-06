package com.example.coupleappgallery

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageUtilsTest {

    @Test
    fun resolveImageModel_handlesNullAndBlank() {
        assertNull(resolveImageModel(null))
        assertNull(resolveImageModel(""))
        assertNull(resolveImageModel("   "))
    }

    @Test
    fun resolveImageModel_preservesNetworkUrls() {
        val httpUrl = "http://example.com/photo.jpg"
        val httpsUrl = "https://firebasestorage.googleapis.com/v0/b/app/photo.jpg"
        assertEquals(httpUrl, resolveImageModel(httpUrl))
        assertEquals(httpsUrl, resolveImageModel(httpsUrl))
    }

    @Test
    fun resolveImageModel_preservesContentAndFileUrls() {
        val contentUrl = "content://media/external/images/media/42"
        val fileUrl = "file:///storage/emulated/0/Download/photo.jpg"
        assertEquals(contentUrl, resolveImageModel(contentUrl))
        assertEquals(fileUrl, resolveImageModel(fileUrl))
    }

    @Test
    fun resolveImageModel_decodesBase64DataUri() {
        val originalBytes = "Hello Antigravity Pair Programming!".toByteArray(Charsets.UTF_8)
        val base64 = encodeBase64Safe(originalBytes)
        val dataUri = "$DATA_URI_PREFIX$base64"

        val resolved = resolveImageModel(dataUri)
        assertNotNull(resolved)
        assertTrue("Resolved model must be a ByteArray", resolved is ByteArray)
        assertArrayEquals(originalBytes, resolved as ByteArray)
    }

    @Test
    fun encodeAndDecode_roundTripsCorrectly() {
        val testPayload = ByteArray(256) { it.toByte() }
        val encoded = encodeBase64Safe(testPayload)
        val decoded = decodeBase64Safe(encoded)
        assertArrayEquals(testPayload, decoded)
    }

    @Test
    fun resolveImageModel_preservesNonStringObjects() {
        val number = 12345
        assertEquals(number, resolveImageModel(number))
    }
}

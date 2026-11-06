package com.example.coupleappgallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FolderCoverCacheTest {

    @Before
    fun setUp() {
        FolderCoverCache.clear()
    }

    @Test
    fun getCover_returnsNullWhenEmpty() {
        assertNull(FolderCoverCache.getCover("folder_1"))
        assertNull(FolderCoverCache.getCount("folder_1"))
    }

    @Test
    fun setCoverAndCount_storesAndRetrievesValues() {
        val testUrl = "data:image/jpeg;base64,sampleBase64Data"
        FolderCoverCache.setCover("folder_1", testUrl)
        FolderCoverCache.setCount("folder_1", 5)

        assertEquals(testUrl, FolderCoverCache.getCover("folder_1"))
        assertEquals(5, FolderCoverCache.getCount("folder_1"))
    }

    @Test
    fun remove_removesSpecificFolder() {
        FolderCoverCache.setCover("folder_1", "url_1")
        FolderCoverCache.setCover("folder_2", "url_2")

        FolderCoverCache.remove("folder_1")

        assertNull(FolderCoverCache.getCover("folder_1"))
        assertEquals("url_2", FolderCoverCache.getCover("folder_2"))
    }

    @Test
    fun clear_removesAllEntries() {
        FolderCoverCache.setCover("folder_1", "url_1")
        FolderCoverCache.setCover("folder_2", "url_2")

        FolderCoverCache.clear()

        assertNull(FolderCoverCache.getCover("folder_1"))
        assertNull(FolderCoverCache.getCover("folder_2"))
    }
}

package com.example.coupleappgallery

import com.google.firebase.firestore.util.CustomClassMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreModelMappingTest {

    @Test
    fun chatMessage_roundTripPreservesIsReadAndWorksWithoutIdField() {
        val original = ChatMessage(
            id = "msg_1",
            senderId = "u1",
            receiverId = "u2",
            text = "Hello love",
            timestamp = 1700000000000L,
            isRead = true,
            reaction = "❤️",
            chatId = "u1_u2"
        )

        @Suppress("UNCHECKED_CAST")
        val serialized = CustomClassMapper.convertToPlainJavaTypes(original) as Map<String, Any?>
        assertEquals(true, serialized["isRead"])
        assertFalse(serialized.containsKey("read"))

        // Deserializing when "id" IS in map
        val fromWithId = CustomClassMapper.convertToCustomClass(serialized, ChatMessage::class.java, null)
        assertEquals(original, fromWithId)

        // Deserializing when "id" is NOT in map (as created by ChatScreen .add(hashMapOf(...)))
        val withoutId = serialized.toMutableMap().apply { remove("id") }
        val fromWithoutId = CustomClassMapper.convertToCustomClass(withoutId, ChatMessage::class.java, null)
        assertEquals("", fromWithoutId.id)
        assertTrue(fromWithoutId.isRead)
        assertEquals("❤️", fromWithoutId.reaction)
    }

    @Test
    fun planItem_roundTripPreservesIsCheckedBoolean() {
        val item = PlanItem(id = "p1", task = "Book dinner", isChecked = true)

        @Suppress("UNCHECKED_CAST")
        val serialized = CustomClassMapper.convertToPlainJavaTypes(item) as Map<String, Any?>
        assertEquals(true, serialized["isChecked"])
        assertFalse(serialized.containsKey("checked"))

        val restored = CustomClassMapper.convertToCustomClass(serialized, PlanItem::class.java, null)
        assertEquals(item, restored)
    }

    @Test
    fun allOtherFirestoreModels_roundTripCleanly() {
        val folder = CoupleFolder(
            id = "f1",
            name = "Kyoto Trip",
            iconName = "✈️",
            date = 1710000000000L,
            participants = listOf("u1", "u2"),
            color = 0xFFFFF8E7,
            createdAt = 1700000000000L,
            ownerId = "u1",
            coverPhotoUrl = "data:image/jpeg;base64,abc",
            photoCount = 5
        )
        val folderMap = CustomClassMapper.convertToPlainJavaTypes(folder)
        assertEquals(folder, CustomClassMapper.convertToCustomClass(folderMap, CoupleFolder::class.java, null))

        val image = GalleryImage(id = "img1", url = "https://example.com/1.jpg", uploaderId = "u1", dateAdded = 12345L)
        val imgMap = CustomClassMapper.convertToPlainJavaTypes(image)
        assertEquals(image, CustomClassMapper.convertToCustomClass(imgMap, GalleryImage::class.java, null))

        val journal = JournalEntry(
            id = "j1",
            title = "Sunset",
            titleIcon = "🌅",
            mood = "🥰",
            text = "Best evening ever",
            authorId = "u1",
            authorName = "Alex",
            date = 98765L,
            prompt = "Favorite moment?"
        )
        val journalMap = CustomClassMapper.convertToPlainJavaTypes(journal)
        assertEquals(journal, CustomClassMapper.convertToCustomClass(journalMap, JournalEntry::class.java, null))

        val request = PartnerRequest(
            id = "r1",
            requesterId = "u1",
            receiverId = "u2",
            emoji = "🫂",
            label = "Hugs",
            timestamp = 55555L
        )
        val reqMap = CustomClassMapper.convertToPlainJavaTypes(request)
        assertEquals(request, CustomClassMapper.convertToCustomClass(reqMap, PartnerRequest::class.java, null))
    }

    @Test
    fun resolveNotificationRoute_distinguishesChatReactionsFromHomeMoodReactions() {
        assertEquals("chat", resolveNotificationRoute("New Reaction ❤️"))
        assertEquals("chat", resolveNotificationRoute("New Message 💌"))
        assertEquals("home", resolveNotificationRoute("Reaction 🫂"))
        assertEquals("home", resolveNotificationRoute("New Special Day 🎉"))
        assertEquals("home", resolveNotificationRoute("Updated Event 📅"))
        assertEquals("home", resolveNotificationRoute("New Adventure 📂"))
        assertEquals("profile", resolveNotificationRoute("New Adventure 📂", explicitRoute = "profile"))
    }

    @Test
    fun chatIdFor_isSymmetric() {
        assertEquals(chatIdFor("uidB", "uidA"), chatIdFor("uidA", "uidB"))
        assertEquals("uidA_uidB", chatIdFor("uidB", "uidA"))
    }
}

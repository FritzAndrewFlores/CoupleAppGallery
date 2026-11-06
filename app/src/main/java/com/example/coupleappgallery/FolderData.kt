package com.example.coupleappgallery

import androidx.annotation.Keep

@Keep
data class CoupleFolder(
    val id: String = "",
    val name: String = "",
    val iconName: String = "🌲",
    val date: Long = 0,
    val participants: List<String> = emptyList(),
    val color: Long = 0xFFFFF8E7, // Default Creamy Pastel color
    val createdAt: Long = 0,
    val ownerId: String = "",
    val coverPhotoUrl: String = "",
    val photoCount: Int = 0
)

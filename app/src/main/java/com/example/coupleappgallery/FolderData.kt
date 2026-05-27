package com.example.coupleappgallery

// Updated with 'color' field (stores color as a Long number)
data class CoupleFolder(
    val id: String = "",
    val name: String = "",
    val iconName: String = "🌲",
    val date: Long = 0,
    val participants: List<String> = emptyList(),
    val color: Long = 0xFFFFF8E7 // Default Creamy Pastel color
)
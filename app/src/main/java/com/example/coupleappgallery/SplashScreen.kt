package com.example.coupleappgallery

import android.view.animation.OvershootInterpolator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(navController: NavController) {
    val scale = remember { Animatable(0f) }
    val auth = FirebaseAuth.getInstance()

    // 🎨 Use Default Theme (Forest Green) for Splash
    // Since we haven't loaded the user's preference from DB yet
    val defaultTheme = appThemes["forest_green"]!!
    val primaryColor = defaultTheme.primary
    val backgroundColor = defaultTheme.background

    // Animation Effect
    LaunchedEffect(Unit) {
        // 1. Animate the Heart (Pop effect)
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 800,
                easing = { OvershootInterpolator(2f).getInterpolation(it) }
            )
        )

        // 2. Wait for 2 seconds
        delay(2000L)

        // 3. Decide where to go (Login vs Home)
        if (auth.currentUser != null) {
            navController.navigate("home") {
                popUpTo("splash") { inclusive = true } // Remove splash from back history
            }
        } else {
            navController.navigate("login") {
                popUpTo("splash") { inclusive = true }
            }
        }
    }

    // UI Design
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // THE HEART ICON 💚
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Logo",
                tint = primaryColor,
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale.value)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Couple Gallery",
                color = primaryColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                modifier = Modifier.scale(scale.value)
            )
        }
    }
}
package com.example.coupleappgallery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun LoginScreen(navController: NavController) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoginMode by remember { mutableStateOf(true) }

    val auth = FirebaseAuth.getInstance()

    // 🎨 Use Default Theme (Forest Green) since user isn't logged in yet
    val defaultTheme = appThemes["forest_green"]!!
    val primaryColor = defaultTheme.primary
    val backgroundColor = defaultTheme.background
    val textColor = defaultTheme.text

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 🖋️ Title
            Text(
                text = "Couple Sync 🌲",
                style = MaterialTheme.typography.displayMedium,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = primaryColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isLoginMode) "Welcome back, traveler." else "Start your journey together.",
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Serif,
                color = textColor
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 🟩 Inputs
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    focusedLabelColor = primaryColor,
                    cursorColor = primaryColor,
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    focusedLabelColor = primaryColor,
                    cursorColor = primaryColor,
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor
                )
            )

            if (errorMessage.isNotEmpty()) {
                Text(text = errorMessage, color = Color.Red, modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 🟢 Main Button
            Button(
                onClick = {
                    if (email.isNotEmpty() && password.isNotEmpty()) {
                        if (isLoginMode) {
                            // LOG IN
                            auth.signInWithEmailAndPassword(email, password)
                                .addOnSuccessListener { navController.navigate("home") { popUpTo("login") { inclusive = true } } }
                                .addOnFailureListener { e -> errorMessage = "Login failed: ${e.message}" }
                        } else {
                            // SIGN UP
                            auth.createUserWithEmailAndPassword(email, password)
                                .addOnSuccessListener { authResult ->
                                    val userMap = hashMapOf(
                                        "email" to email,
                                        "name" to "New User",
                                        "photoUrl" to "",
                                        "partnerId" to "",
                                        "themeId" to "forest_green" // Set Default Theme on SignUp
                                    )
                                    val db = FirebaseFirestore.getInstance()
                                    db.collection("users").document(authResult.user!!.uid).set(userMap)
                                        .addOnSuccessListener {
                                            navController.navigate("home") { popUpTo("login") { inclusive = true } }
                                        }
                                }
                                .addOnFailureListener { e -> errorMessage = "Sign up failed: ${e.message}" }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isLoginMode) "Log In" else "Create Account",
                    fontFamily = FontFamily.Serif,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 🔄 Toggle Button
            TextButton(onClick = { isLoginMode = !isLoginMode }) {
                Text(
                    text = if (isLoginMode) "New here? Create Account" else "Already have an account? Log In",
                    color = textColor,
                    fontFamily = FontFamily.Serif
                )
            }
        }
    }
}
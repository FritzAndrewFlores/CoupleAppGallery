package com.example.coupleappgallery

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.navigation.NavController
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun LoginScreen(navController: NavController) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoginMode by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    val primaryColor = DefaultAppTheme.primary
    val backgroundColor = DefaultAppTheme.background
    val textColor = DefaultAppTheme.text

    fun navigateToHomeSafely() {
        if (navController.currentBackStackEntry?.destination?.route == "login") {
            navController.navigate("home") {
                popUpTo("login") { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    fun Context.findActivity(): Activity? {
        var currentContext = this
        while (currentContext is ContextWrapper) {
            if (currentContext is Activity) return currentContext
            currentContext = currentContext.baseContext
        }
        return null
    }

    suspend fun signInWithGoogle(context: Context) {
        val targetContext = context.findActivity() ?: context
        val credentialManager = CredentialManager.create(targetContext)

        // Use GetSignInWithGoogleOption for explicit button clicks, which triggers
        // the standard Google account picker allowing user to select any Google account.
        val signInOption = GetSignInWithGoogleOption.Builder(
            serverClientId = GOOGLE_WEB_CLIENT_ID
        ).build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        try {
            val result = try {
                credentialManager.getCredential(
                    request = request,
                    context = targetContext
                )
            } catch (_: NoCredentialException) {
                // Secondary fallback using GetGoogleIdOption
                val fallbackOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(GOOGLE_WEB_CLIENT_ID)
                    .setAutoSelectEnabled(false)
                    .build()
                val fallbackRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(fallbackOption)
                    .build()
                credentialManager.getCredential(
                    request = fallbackRequest,
                    context = targetContext
                )
            }

            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken

                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(firebaseCredential).await()
                val user = authResult.user
                val uid = user?.uid

                if (!uid.isNullOrEmpty()) {
                    val userDocRef = db.collection("users").document(uid)
                    val existingDoc = userDocRef.get().await()

                    if (!existingDoc.exists()) {
                        val initialData = hashMapOf(
                            "email" to (user.email ?: ""),
                            "name" to (user.displayName ?: "New User"),
                            "photoUrl" to (user.photoUrl?.toString() ?: ""),
                            "partnerId" to "",
                            "inviteCode" to "",
                            "themeId" to "forest_green"
                        )
                        userDocRef.set(initialData, SetOptions.merge()).await()
                    } else {
                        val currentName = existingDoc.getString("name") ?: ""
                        val currentPhoto = existingDoc.getString("photoUrl") ?: ""
                        val updates = mutableMapOf<String, Any>()
                        if (currentName.isEmpty() || currentName == "New User") {
                            user.displayName?.takeIf { it.isNotEmpty() }?.let { updates["name"] = it }
                        }
                        if (currentPhoto.isEmpty()) {
                            user.photoUrl?.toString()?.takeIf { it.isNotEmpty() }?.let { updates["photoUrl"] = it }
                        }
                        if (updates.isNotEmpty()) {
                            userDocRef.update(updates).await()
                        }
                    }
                    navigateToHomeSafely()
                } else {
                    errorMessage = "Google Sign-In failed: Unable to obtain user ID."
                }
            } else {
                errorMessage = "Unexpected credential received from Google."
            }
        } catch (_: GetCredentialCancellationException) {
            // User cancelled the bottom sheet picker; no error banner needed
            Log.d("LoginScreen", "Google Sign-In cancelled by user")
        } catch (e: NoCredentialException) {
            Log.e("LoginScreen", "NoCredentialException during Google Sign-In", e)
            errorMessage = "No compatible Google account found. Please check Google Play Services in Settings > Passwords & Accounts."
        } catch (e: GoogleIdTokenParsingException) {
            Log.e("LoginScreen", "GoogleIdTokenParsingException", e)
            errorMessage = "Invalid Google Sign-In response. Please try again."
        } catch (e: GetCredentialException) {
            Log.e("LoginScreen", "GetCredentialException: ${e.type}", e)
            errorMessage = "Google Sign-In: ${e.message ?: e.type ?: "Could not authenticate."}"
        } catch (e: Exception) {
            Log.e("LoginScreen", "Google Sign-In general failure", e)
            errorMessage = "Authentication failed: ${e.localizedMessage ?: "Please try again."}"
        } finally {
            isLoading = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorMessage = "" },
                label = { Text("Email") },
                singleLine = true,
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
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
                onValueChange = { password = it; errorMessage = "" },
                label = { Text("Password") },
                singleLine = true,
                enabled = !isLoading,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
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
                Text(
                    text = errorMessage,
                    color = Color.Red,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                enabled = !isLoading,
                onClick = {
                    val trimmedEmail = email.trim()
                    if (trimmedEmail.isEmpty() || password.isEmpty()) {
                        errorMessage = "Please enter both email and password."
                        return@Button
                    }
                    if (!isLoginMode && password.length < 6) {
                        errorMessage = "Password must be at least 6 characters."
                        return@Button
                    }

                    isLoading = true
                    errorMessage = ""

                    if (isLoginMode) {
                        auth.signInWithEmailAndPassword(trimmedEmail, password)
                            .addOnSuccessListener {
                                isLoading = false
                                navigateToHomeSafely()
                            }
                            .addOnFailureListener { e ->
                                isLoading = false
                                errorMessage = "Login failed: ${e.localizedMessage ?: "Invalid credentials"}"
                            }
                    } else {
                        auth.createUserWithEmailAndPassword(trimmedEmail, password)
                            .addOnSuccessListener { authResult ->
                                val uid = authResult.user?.uid
                                if (uid.isNullOrEmpty()) {
                                    isLoading = false
                                    errorMessage = "Account setup failed. Please try logging in."
                                    return@addOnSuccessListener
                                }
                                val userMap = hashMapOf(
                                    "email" to trimmedEmail,
                                    "name" to "New User",
                                    "photoUrl" to "",
                                    "partnerId" to "",
                                    "inviteCode" to "",
                                    "themeId" to "forest_green"
                                )
                                db.collection("users").document(uid)
                                    .set(userMap, SetOptions.merge())
                                    .addOnSuccessListener {
                                        isLoading = false
                                        navigateToHomeSafely()
                                    }
                                    .addOnFailureListener { e ->
                                        Log.w("LoginScreen", "Initial profile write queued/deferred", e)
                                        isLoading = false
                                        navigateToHomeSafely()
                                    }
                            }
                            .addOnFailureListener { e ->
                                isLoading = false
                                errorMessage = "Sign up failed: ${e.localizedMessage ?: "Unable to create account"}"
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text(
                        text = if (isLoginMode) "Log In" else "Create Account",
                        fontFamily = FontFamily.Serif,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Divider with "OR"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = textColor.copy(alpha = 0.2f))
                Text(
                    text = "or",
                    color = textColor.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = textColor.copy(alpha = 0.2f))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Google Sign-In Button
            OutlinedButton(
                enabled = !isLoading,
                onClick = {
                    isLoading = true
                    errorMessage = ""
                    coroutineScope.launch {
                        signInWithGoogle(context)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, textColor.copy(alpha = 0.3f)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    GoogleIcon(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Continue with Google",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF3C4043)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                enabled = !isLoading,
                onClick = {
                    isLoginMode = !isLoginMode
                    errorMessage = ""
                }
            ) {
                Text(
                    text = if (isLoginMode) "New here? Create Account" else "Already have an account? Log In",
                    color = textColor,
                    fontFamily = FontFamily.Serif
                )
            }
        }
    }
}

/**
 * Renders the official 4-color Google "G" logo vector cleanly without external image assets.
 */
@Composable
fun GoogleIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val cx = width / 2f
        val cy = height / 2f

        // Draw 4 Google Brand colored sectors
        val blue = Color(0xFF4285F4)
        val green = Color(0xFF34A853)
        val yellow = Color(0xFFFBBC05)
        val red = Color(0xFFEA4335)

        // Yellow segment (left bottom)
        drawPath(
            path = Path().apply {
                moveTo(cx, cy)
                arcTo(
                    rect = androidx.compose.ui.geometry.Rect(0f, 0f, width, height),
                    startAngleDegrees = 140f,
                    sweepAngleDegrees = 80f,
                    forceMoveTo = false
                )
                close()
            },
            color = yellow
        )

        // Red segment (top)
        drawPath(
            path = Path().apply {
                moveTo(cx, cy)
                arcTo(
                    rect = androidx.compose.ui.geometry.Rect(0f, 0f, width, height),
                    startAngleDegrees = 220f,
                    sweepAngleDegrees = 95f,
                    forceMoveTo = false
                )
                close()
            },
            color = red
        )

        // Green segment (bottom)
        drawPath(
            path = Path().apply {
                moveTo(cx, cy)
                arcTo(
                    rect = androidx.compose.ui.geometry.Rect(0f, 0f, width, height),
                    startAngleDegrees = 45f,
                    sweepAngleDegrees = 95f,
                    forceMoveTo = false
                )
                close()
            },
            color = green
        )

        // Blue segment (right + horizontal bar)
        drawPath(
            path = Path().apply {
                moveTo(cx, cy)
                arcTo(
                    rect = androidx.compose.ui.geometry.Rect(0f, 0f, width, height),
                    startAngleDegrees = 315f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                close()
            },
            color = blue
        )

        // Center cutout to make it a ring
        drawCircle(
            color = Color.White,
            radius = width * 0.28f,
            center = Offset(cx, cy)
        )

        // Blue crossbar
        drawRect(
            color = blue,
            topLeft = Offset(cx, cy - height * 0.12f),
            size = androidx.compose.ui.geometry.Size(width * 0.5f, height * 0.24f)
        )
    }
}

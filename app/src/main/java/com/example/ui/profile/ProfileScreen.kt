package com.example.ui.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GameType
import com.example.model.UserProfile
import com.example.services.AuthService
import com.example.services.FirestoreService
import com.example.ui.components.DailyCountdownBadge
import com.example.ui.legal.PrivacyPolicyDialog
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    user: UserProfile,
    authService: AuthService,
    firestoreService: FirestoreService,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val totalGamesPlayed = firestoreService.getLocalTotalGamesPlayed()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }

    var showPasswordReauthDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var passwordReauthError by remember { mutableStateOf<String?>(null) }
    var isPasswordReauthLoading by remember { mutableStateOf(false) }

    var showGoogleReauthDialog by remember { mutableStateOf(false) }
    var isGoogleReauthLoading by remember { mutableStateOf(false) }
    var googleReauthError by remember { mutableStateOf<String?>(null) }

    fun executeAccountDeletion() {
        isDeleting = true
        coroutineScope.launch {
            val result = authService.performAccountDeletion(context, firestoreService)
            isDeleting = false
            when (result) {
                is AuthService.DeleteAccountResult.Success -> {
                    Toast.makeText(context, "Account deleted successfully.", Toast.LENGTH_SHORT).show()
                    onSignOut()
                }
                is AuthService.DeleteAccountResult.RequiresPasswordReauth -> {
                    passwordInput = ""
                    passwordReauthError = null
                    showPasswordReauthDialog = true
                }
                is AuthService.DeleteAccountResult.RequiresGoogleReauth -> {
                    googleReauthError = null
                    showGoogleReauthDialog = true
                }
                is AuthService.DeleteAccountResult.Failure -> {
                    Toast.makeText(context, result.errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        containerColor = CyberDarkBg
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PLAYER PROFILE",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                }
            }

            // User Identity Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(NeonCyan.copy(alpha = 0.2f), CircleShape)
                                .border(2.dp, NeonCyan, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Avatar",
                                tint = NeonCyan,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Text(
                            text = user.displayName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )

                        if (user.email.isNotBlank()) {
                            Text(
                                text = user.email,
                                fontSize = 14.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // High Scores Overview
            item {
                Text(
                    text = "PERSONAL BEST RECORDS",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = NeonGold,
                    letterSpacing = 1.sp
                )
            }

            items(GameType.entries.size) { index ->
                val game = GameType.entries[index]
                val best = firestoreService.getLocalBestScore(game)

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = game.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Daily Arena Game #${index + 1}",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }

                        Text(
                            text = if (best > 0) "$best pts" else "No Score Yet",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = if (best > 0) NeonCyan else TextMuted
                        )
                    }
                }
            }

            // Account Actions
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "ACCOUNT ACTIONS",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = NeonCyan,
                    letterSpacing = 1.sp
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Switch User Button
                    OutlinedButton(
                        onClick = {
                            authService.signOut()
                            onSignOut()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = CyberSurface,
                            contentColor = NeonCyan
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("switch_user_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwitchAccount,
                                contentDescription = "Switch User",
                                tint = NeonCyan
                            )
                            Text(
                                text = "SWITCH USER",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Privacy Policy Button
                    OutlinedButton(
                        onClick = { showPrivacyPolicyDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = CyberSurface,
                            contentColor = NeonCyan
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("profile_privacy_policy_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PrivacyTip,
                                contentDescription = "Privacy Policy",
                                tint = NeonCyan
                            )
                            Text(
                                text = "PRIVACY POLICY",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Log Out Button
                    OutlinedButton(
                        onClick = {
                            authService.signOut()
                            onSignOut()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = CyberSurface,
                            contentColor = NeonRed
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonRed.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("log_out_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Log Out",
                                tint = NeonRed
                            )
                            Text(
                                text = "LOG OUT",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Delete Account Button
                    OutlinedButton(
                        onClick = { showDeleteConfirmDialog = true },
                        enabled = !isDeleting,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = CyberSurface,
                            contentColor = Color(0xFFEF4444)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("delete_account_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = "Delete Account",
                                tint = Color(0xFFEF4444)
                            )
                            Text(
                                text = "DELETE ACCOUNT",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Delete Account Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = "Delete your account?",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Deleting your account is permanent. All your personal data, high scores, and leaderboard rankings will be permanently removed, and you will be signed out. This action cannot be undone.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        executeAccountDeletion()
                    },
                    enabled = !isDeleting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_delete_account_button")
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = "Delete Account",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false },
                    enabled = !isDeleting,
                    modifier = Modifier.testTag("cancel_delete_account_button")
                ) {
                    Text(
                        text = "Cancel",
                        color = TextMuted,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            containerColor = CyberSurface,
            shape = RoundedCornerShape(18.dp)
        )
    }

    // Password Re-authentication Dialog
    if (showPasswordReauthDialog) {
        AlertDialog(
            onDismissRequest = { if (!isPasswordReauthLoading) showPasswordReauthDialog = false },
            title = {
                Text(
                    text = "Confirm Password",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "For security reasons, please re-enter your password to confirm account deletion.",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            passwordReauthError = null
                        },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    tint = TextMuted
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = CyberBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reauth_password_input")
                    )
                    if (passwordReauthError != null) {
                        Text(
                            text = passwordReauthError!!,
                            color = NeonRed,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (passwordInput.isBlank()) {
                            passwordReauthError = "Please enter your password"
                            return@Button
                        }
                        isPasswordReauthLoading = true
                        passwordReauthError = null
                        coroutineScope.launch {
                            val reauthRes = authService.reauthenticateWithEmail(passwordInput)
                            if (reauthRes.isSuccess) {
                                val user = authService.getFirebaseCurrentUser()
                                if (user != null) {
                                    val delRes = authService.executeDeletion(user, user.uid, firestoreService)
                                    isPasswordReauthLoading = false
                                    showPasswordReauthDialog = false
                                    if (delRes is AuthService.DeleteAccountResult.Success) {
                                        Toast.makeText(context, "Account deleted successfully.", Toast.LENGTH_SHORT).show()
                                        onSignOut()
                                    } else if (delRes is AuthService.DeleteAccountResult.Failure) {
                                        Toast.makeText(context, delRes.errorMessage, Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    isPasswordReauthLoading = false
                                    showPasswordReauthDialog = false
                                    onSignOut()
                                }
                            } else {
                                isPasswordReauthLoading = false
                                passwordReauthError = reauthRes.exceptionOrNull()?.localizedMessage
                                    ?: "Verification failed. Please check your password."
                            }
                        }
                    },
                    enabled = !isPasswordReauthLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_reauth_button")
                ) {
                    if (isPasswordReauthLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = "Verify & Delete",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPasswordReauthDialog = false },
                    enabled = !isPasswordReauthLoading,
                    modifier = Modifier.testTag("cancel_reauth_button")
                ) {
                    Text(
                        text = "Cancel",
                        color = TextMuted,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            containerColor = CyberSurface,
            shape = RoundedCornerShape(18.dp)
        )
    }

    // Google Re-authentication Dialog
    if (showGoogleReauthDialog) {
        AlertDialog(
            onDismissRequest = { if (!isGoogleReauthLoading) showGoogleReauthDialog = false },
            title = {
                Text(
                    text = "Verify Google Account",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "For security reasons, please re-authenticate with your Google account to confirm account deletion.",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    if (googleReauthError != null) {
                        Text(
                            text = googleReauthError!!,
                            color = NeonRed,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isGoogleReauthLoading = true
                        googleReauthError = null
                        coroutineScope.launch {
                            val reauthRes = authService.reauthenticateWithGoogle(context)
                            if (reauthRes.isSuccess) {
                                val user = authService.getFirebaseCurrentUser()
                                if (user != null) {
                                    val delRes = authService.executeDeletion(user, user.uid, firestoreService)
                                    isGoogleReauthLoading = false
                                    showGoogleReauthDialog = false
                                    if (delRes is AuthService.DeleteAccountResult.Success) {
                                        Toast.makeText(context, "Account deleted successfully.", Toast.LENGTH_SHORT).show()
                                        onSignOut()
                                    } else if (delRes is AuthService.DeleteAccountResult.Failure) {
                                        Toast.makeText(context, delRes.errorMessage, Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    isGoogleReauthLoading = false
                                    showGoogleReauthDialog = false
                                    onSignOut()
                                }
                            } else {
                                isGoogleReauthLoading = false
                                googleReauthError = reauthRes.exceptionOrNull()?.localizedMessage
                                    ?: "Google re-authentication failed."
                            }
                        }
                    },
                    enabled = !isGoogleReauthLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("google_reauth_button")
                ) {
                    if (isGoogleReauthLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = "Verify with Google",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showGoogleReauthDialog = false },
                    enabled = !isGoogleReauthLoading
                ) {
                    Text(
                        text = "Cancel",
                        color = TextMuted,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            containerColor = CyberSurface,
            shape = RoundedCornerShape(18.dp)
        )
    }

    if (showPrivacyPolicyDialog) {
        PrivacyPolicyDialog(onDismiss = { showPrivacyPolicyDialog = false })
    }
}

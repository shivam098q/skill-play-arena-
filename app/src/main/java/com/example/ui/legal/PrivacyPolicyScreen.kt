package com.example.ui.legal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*

@Composable
fun PrivacyPolicyDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = CyberSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(NeonCyan.copy(alpha = 0.15f), CircleShape)
                                .border(1.5.dp, NeonCyan, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PrivacyTip,
                                contentDescription = "Privacy Policy Icon",
                                tint = NeonCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "PRIVACY POLICY",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                            Text(
                                text = "SkillPlay Arena • v1.0",
                                fontSize = 11.sp,
                                color = NeonCyan
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("close_privacy_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = CyberBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Content
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Highlights Overview Card
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = CyberDarkBg),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "DATA SAFETY HIGHLIGHTS",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = NeonCyan,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "• Passwords are encrypted & never stored in plain-text\n• Game scores & rankings are stored securely in Cloud Firestore\n• Rewarded video ads are served via Google AdMob SDK\n• You have full right to permanently delete your account & data instantly",
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }

                    // Section 1: Introduction
                    item {
                        PolicySectionCard(
                            title = "1. Introduction & Overview",
                            body = "SkillPlay Arena (\"we\", \"our\", or \"us\") respects your privacy. This policy outlines our collection, usage, and safeguarding of personal and gameplay information for the mobile application com.aistudio.skillplayarena.vxqzr."
                        )
                    }

                    // Section 2: Information Collected
                    item {
                        PolicySectionCard(
                            title = "2. Information We Collect",
                            body = "A. Account Information: When you sign up via Email or Google Sign-In, we store your Display Name, Email address, and generated User ID (UID).\n\n" +
                                    "B. Gameplay & Tournament Data: Daily game attempts, scores submitted across all 4 arcade games (Tap Speed, Car Endless, Flappy, Jump & Run), completion timestamps, and leaderboard standings.\n\n" +
                                    "C. Device & Diagnostics: OS version, device model, and network connectivity state to ensure anti-cheat compliance.\n\n" +
                                    "D. Advertising Data: Google Advertising ID (GAID) collected by Google AdMob to deliver opt-in rewarded video ads."
                        )
                    }

                    // Section 3: How We Use Information
                    item {
                        PolicySectionCard(
                            title = "3. How We Use Your Data",
                            body = "• Enforce the 1 Official Daily Attempt rule fairly for all players worldwide.\n" +
                                    "• Synchronize your tournament high scores and live global leaderboards.\n" +
                                    "• Provide in-game powerups and revivals when you choose to watch Rewarded Ads.\n" +
                                    "• Protect against unauthorized access, bots, and clock-tampering."
                        )
                    }

                    // Section 4: Third-Party Service Providers
                    item {
                        PolicySectionCard(
                            title = "4. Third-Party Services (Google LLC)",
                            body = "We use the following Google Cloud services, which process data under Google's Privacy Policies:\n\n" +
                                    "• Firebase Authentication: Player account verification and credential security.\n" +
                                    "• Cloud Firestore: Encrypted cloud database for leaderboard and player records.\n" +
                                    "• Google Mobile Ads (AdMob): Sponsored rewarded video advertisements.\n" +
                                    "• Google Play Services: Core Android runtime libraries and safety checks."
                        )
                    }

                    // Section 5: Data Retention & Complete Account Deletion
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = CyberDarkBg),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonGold.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteForever,
                                        contentDescription = null,
                                        tint = NeonGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "5. Right to Delete Your Data",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonGold
                                    )
                                }
                                Text(
                                    text = "In compliance with Google Play Developer Policies:\n" +
                                            "• In-App Deletion: Go to Profile -> Delete Account. Your authentication record, Firestore player doc, and high scores will be immediately and permanently erased.\n" +
                                            "• Email / Web Request: Anyone who uninstalled the app can request account deletion via email to kashyap10102007@gmail.com.",
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    lineHeight = 18.sp
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                data = Uri.parse("mailto:kashyap10102007@gmail.com")
                                                putExtra(Intent.EXTRA_SUBJECT, "Account and Data Deletion Request - SkillPlay Arena")
                                                putExtra(Intent.EXTRA_TEXT, "Dear Support,\n\nPlease delete my SkillPlay Arena account and all associated data.\n\nRegistered Email: \nPlayer Name: ")
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Deletion Email", "kashyap10102007@gmail.com")
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Email copied! Email: kashyap10102007@gmail.com", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGold),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGold.copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                        .testTag("request_deletion_email_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = NeonGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "EMAIL ACCOUNT DELETION REQUEST",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonGold
                                    )
                                }
                            }
                        }
                    }

                    // Section 6: Children's Privacy
                    item {
                        PolicySectionCard(
                            title = "6. Children's Privacy (COPPA)",
                            body = "SkillPlay Arena is intended for general audiences aged 13 and above. We do not knowingly collect personal identifiable information from children under 13. Any such data found will be permanently expunged immediately."
                        )
                    }

                    // Section 7: Security & Encryption
                    item {
                        PolicySectionCard(
                            title = "7. Security Measures",
                            body = "All network traffic is encrypted via Transport Layer Security (TLS/HTTPS). User records and attempts are guarded by strict zero-trust Firestore security rules."
                        )
                    }

                    // Section 8: Contact Developer
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = CyberDarkBg),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "8. Developer & Contact Info",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Developer: SkillPlay Arena Team\n" +
                                            "Package: com.aistudio.skillplayarena.vxqzr\n" +
                                            "Email: kashyap10102007@gmail.com\n" +
                                            "Effective Date: October 15, 2026",
                                    fontSize = 13.sp,
                                    color = TextSecondary,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = CyberBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Footer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Contact Email", "kashyap10102007@gmail.com")
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Email copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = NeonCyan
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("copy_email_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "COPY EMAIL",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = CyberDarkBg
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("dismiss_privacy_dialog_button")
                    ) {
                        Text(
                            text = "CLOSE",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PolicySectionCard(
    title: String,
    body: String
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CyberDarkBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = body,
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 18.sp
            )
        }
    }
}

package com.example.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.DailyCountdownBadge
import com.example.ui.legal.PrivacyPolicyDialog
import com.example.ui.theme.*

data class RuleItem(
    val title: String,
    val subtitle: String,
    val description: String,
    val icon: ImageVector,
    val accentColor: Color
)

@Composable
fun RulesScreen() {
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    val rules = listOf(
        RuleItem(
            title = "1 Official Attempt Per Game Per Day",
            subtitle = "Daily Reset at 12:00 AM IST (Asia/Kolkata)",
            description = "Every player receives exactly one competitive score-submission opportunity for each of the 4 arcade games daily. Once submitted, your score locks until the midnight IST reset.",
            icon = Icons.Default.AccessTime,
            accentColor = NeonCyan
        ),
        RuleItem(
            title = "Strategic Rewarded Ads",
            subtitle = "Up to 3 Ads per Game Run",
            description = "Watch up to 3 optional sponsored ads to boost your game run:\n• Tap Speed: 1st (+20s), 2nd (+20s), 3rd (+10s)\n• Endless Games: Revives player from crash point with a 2.5s invulnerable shield.",
            icon = Icons.Default.PlayArrow,
            accentColor = NeonGold
        ),
        RuleItem(
            title = "Fair-Play Anti-Cheat Engine",
            subtitle = "Server-Enforced Validation",
            description = "All attempts use synchronized date keys and atomic Firestore transactions. Client device clock changes cannot grant extra daily attempts or bypass locks.",
            icon = Icons.Default.Security,
            accentColor = NeonGreen
        ),
        RuleItem(
            title = "Live Global Leaderboards",
            subtitle = "Top 50 Ranks per Game",
            description = "Scores submitted officially are ranked live across all players worldwide with instant podium medals for 1st, 2nd, and 3rd place.",
            icon = Icons.Default.EmojiEvents,
            accentColor = NeonPurple
        ),
        RuleItem(
            title = "Account & Cloud Sync",
            subtitle = "Cross-Session Continuity",
            description = "Sign in via Google One-Tap or Email/Password to preserve your lifetime high scores and competition statistics permanently.",
            icon = Icons.Default.CloudSync,
            accentColor = NeonOrange
        )
    )

    Scaffold(
        containerColor = CyberDarkBg
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ARENA RULES",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )
                        Text(
                            text = "Official Fair-Play Guidelines",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            items(rules.size) { index ->
                val rule = rules[index]
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(rule.accentColor.copy(alpha = 0.15f), CircleShape)
                                    .border(1.dp, rule.accentColor.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = rule.icon,
                                    contentDescription = rule.title,
                                    tint = rule.accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = rule.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = rule.subtitle,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = rule.accentColor
                                )
                            }
                        }

                        Text(
                            text = rule.description,
                            fontSize = 13.sp,
                            color = TextSecondary,
                            lineHeight = 19.sp
                        )
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PrivacyTip,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Legal & Data Safety",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Text(
                            text = "Learn how your tournament attempts, high scores, and advertising identifiers are protected and managed under our official privacy guidelines.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            lineHeight = 18.sp
                        )

                        OutlinedButton(
                            onClick = { showPrivacyPolicyDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = NeonCyan
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("rules_privacy_policy_button")
                        ) {
                            Text(
                                text = "VIEW PRIVACY POLICY",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showPrivacyPolicyDialog) {
        PrivacyPolicyDialog(onDismiss = { showPrivacyPolicyDialog = false })
    }
}

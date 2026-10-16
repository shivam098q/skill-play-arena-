package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.services.FirestoreService
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun DailyCountdownBadge(
    modifier: Modifier = Modifier
) {
    var millisRemaining by remember { mutableLongStateOf(FirestoreService.getMillisUntilMidnightIst()) }

    LaunchedEffect(Unit) {
        while (true) {
            millisRemaining = FirestoreService.getMillisUntilMidnightIst()
            delay(1000L)
        }
    }

    val hours = (millisRemaining / (1000 * 60 * 60)) % 24
    val minutes = (millisRemaining / (1000 * 60)) % 60
    val seconds = (millisRemaining / 1000) % 60

    val timeFormatted = String.format(Locale.US, "%02dh %02dm %02ds", hours, minutes, seconds)

    Surface(
        modifier = modifier,
        color = CyberSurfaceVariant.copy(alpha = 0.85f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AccessTime,
                contentDescription = "Reset Timer",
                tint = NeonCyan,
                modifier = Modifier.size(16.dp)
            )
            Column {
                Text(
                    text = "DAILY RESET (IST)",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = timeFormatted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = NeonCyan
                )
            }
        }
    }
}

@Composable
fun NetworkStatusBanner(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !isOnline,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = Color(0xFFD97706)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = "Offline",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Offline Mode active. Scores cached safely & will sync automatically.",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun RewardedAdSimulationDialog(
    rewardTitle: String,
    onRewardEarned: () -> Unit,
    onDismiss: () -> Unit
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var secondsLeft by remember { mutableIntStateOf(3) }
    var isDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        for (i in 3 downTo 1) {
            secondsLeft = i
            progress = (4 - i) / 3f
            delay(1000L)
        }
        progress = 1f
        isDone = true
        delay(300L)
        onRewardEarned()
    }

    Dialog(onDismissRequest = {}) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, NeonGold),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(NeonGold.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Rewarded Ad",
                        tint = NeonGold,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "Watching Sponsored Ad",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimary
                )

                Text(
                    text = "Granting Reward: $rewardTitle",
                    fontSize = 14.sp,
                    color = NeonCyan,
                    textAlign = TextAlign.Center
                )

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = NeonGold,
                    trackColor = CyberSurfaceVariant
                )

                Text(
                    text = if (isDone) "Reward Unlocked!" else "Finishing in $secondsLeft seconds...",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun AttemptLockedDialog(
    gameTitle: String,
    finalScore: Long,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonRed.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(NeonRed.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = NeonRed,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Text(
                    text = "Daily Attempt Complete",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = TextPrimary
                )

                Text(
                    text = "You have used today's 1 official attempt for $gameTitle.\n\nToday's Score: $finalScore pts",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                DailyCountdownBadge()

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Close",
                        color = Color(0xFF002026),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

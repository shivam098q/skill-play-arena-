package com.example.ui.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GameType
import com.example.model.LeaderboardEntry
import com.example.model.UserProfile
import com.example.services.FirestoreService
import com.example.ui.components.DailyCountdownBadge
import com.example.ui.theme.*

@Composable
fun LeaderboardScreen(
    user: UserProfile,
    firestoreService: FirestoreService
) {
    var selectedGame by remember { mutableStateOf(GameType.TAP_SPEED) }
    val leaderboardEntries by firestoreService.getLeaderboardFlow(selectedGame)
        .collectAsState(initial = emptyList())

    val top1 = leaderboardEntries.getOrNull(0)
    val top2 = leaderboardEntries.getOrNull(1)
    val top3 = leaderboardEntries.getOrNull(2)
    val rest = if (leaderboardEntries.size > 3) leaderboardEntries.drop(3) else emptyList()

    val myScore = firestoreService.getLocalBestScore(selectedGame)

    Scaffold(
        containerColor = CyberDarkBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Leaderboard,
                        contentDescription = "Leaderboard",
                        tint = NeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "LEADERBOARDS",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                }
            }

            // Game Tabs
            ScrollableTabRow(
                selectedTabIndex = GameType.entries.indexOf(selectedGame),
                containerColor = CyberSurface,
                contentColor = NeonCyan,
                edgePadding = 16.dp,
                divider = {}
            ) {
                GameType.entries.forEach { game ->
                    Tab(
                        selected = selectedGame == game,
                        onClick = { selectedGame = game },
                        text = {
                            Text(
                                text = game.title,
                                fontWeight = if (selectedGame == game) FontWeight.Black else FontWeight.Normal,
                                color = if (selectedGame == game) NeonCyan else TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top 3 Podium
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = CyberSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "TOP 3 CHAMPIONS",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonGold,
                                letterSpacing = 1.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                // 2nd Place
                                PodiumSlot(
                                    entry = top2,
                                    rank = 2,
                                    badgeColor = SilverMedal,
                                    height = 100.dp
                                )

                                // 1st Place (Elevated)
                                PodiumSlot(
                                    entry = top1,
                                    rank = 1,
                                    badgeColor = GoldTrophy,
                                    height = 125.dp
                                )

                                // 3rd Place
                                PodiumSlot(
                                    entry = top3,
                                    rank = 3,
                                    badgeColor = BronzeMedal,
                                    height = 85.dp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    Text(
                        text = "GLOBAL RANKINGS (TOP 50)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp
                    )
                }

                // Ranks 4 to 50
                items(rest) { entry ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CyberSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    color = CyberSurfaceVariant,
                                    shape = CircleShape,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "#${entry.rank}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                Column {
                                    Text(
                                        text = entry.displayName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Official Daily Score",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                            }

                            Text(
                                text = "${entry.score} pts",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = NeonCyan
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }

            // Pinned Current User Card at bottom
            val userEntry = leaderboardEntries.find { it.uid == user.uid }
            val myRank = userEntry?.rank ?: if (myScore > 0) {
                val higherCount = leaderboardEntries.count { it.score > myScore }
                higherCount + 1
            } else null

            Surface(
                color = CyberSurfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "YOUR PERSONAL BEST",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = NeonGold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
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
                                    .size(36.dp)
                                    .background(NeonCyan.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "You",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = user.displayName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (myRank != null) "Rank #$myRank" else "Unranked",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (myRank != null) NeonCyan else TextMuted
                                )
                            }
                        }

                        Text(
                            text = if (myScore > 0) "$myScore pts" else "0 pts",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = if (myScore > 0) NeonCyan else TextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PodiumSlot(
    entry: LeaderboardEntry?,
    rank: Int,
    badgeColor: Color,
    height: androidx.compose.ui.unit.Dp
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Box(
            modifier = Modifier
                .size(if (rank == 1) 48.dp else 40.dp)
                .background(badgeColor.copy(alpha = 0.2f), CircleShape)
                .border(1.5.dp, badgeColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = "Rank $rank",
                tint = badgeColor,
                modifier = Modifier.size(if (rank == 1) 26.dp else 20.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = entry?.displayName ?: "Champion",
            fontSize = if (rank == 1) 13.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = "${entry?.score ?: 0} pts",
            fontSize = if (rank == 1) 14.sp else 12.sp,
            fontWeight = FontWeight.Black,
            color = badgeColor
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Pedestal Box
        Box(
            modifier = Modifier
                .width(84.dp)
                .height(height)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(
                    brush = Brush.verticalGradient(
                        listOf(badgeColor.copy(alpha = 0.35f), CyberSurfaceVariant)
                    )
                )
                .border(
                    1.dp,
                    badgeColor.copy(alpha = 0.5f),
                    RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "#$rank",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = badgeColor
            )
        }
    }
}

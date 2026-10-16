package com.example.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AttemptStatus
import com.example.model.GameType
import com.example.model.UserProfile
import com.example.services.FirestoreService
import com.example.ui.components.AttemptLockedDialog
import com.example.ui.components.DailyCountdownBadge
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    user: UserProfile,
    firestoreService: FirestoreService,
    onSelectGame: (GameType) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val attemptStatuses = remember { mutableStateMapOf<String, AttemptStatus>() }
    var lockedGameTitle by remember { mutableStateOf<String?>(null) }
    var lockedGameScore by remember { mutableLongStateOf(0L) }

    fun refreshStatuses() {
        coroutineScope.launch(Dispatchers.IO) {
            GameType.entries.forEach { game ->
                launch {
                    val status = firestoreService.checkDailyAttemptStatus(user.uid, game)
                    attemptStatuses[game.id] = status
                }
            }
        }
    }

    LaunchedEffect(user.uid) {
        refreshStatuses()
    }

    Scaffold(
        containerColor = CyberDarkBg
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Header Row
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
                                .size(44.dp)
                                .background(NeonCyan.copy(alpha = 0.15f), CircleShape)
                                .border(1.5.dp, NeonCyan, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Avatar",
                                tint = NeonCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Welcome back,",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = user.displayName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                        }
                    }

                    DailyCountdownBadge()
                }
            }

            // Hero Banner Card with Golden Championship Trophy
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.linearGradient(
                                    listOf(
                                        CyberSurfaceVariant,
                                        CyberSurface
                                    )
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(NeonGreen, CircleShape)
                                    )
                                    Text(
                                        text = "DAILY ARENA ACTIVE",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = NeonGreen,
                                        letterSpacing = 1.sp
                                    )
                                }
                                Text(
                                    text = "1 Attempt. Maximum Glory.",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Play your official score attempt per game each day. Strategic rewarded ads can grant game-changing revives & extra time!",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    lineHeight = 16.sp
                                )
                            }

                            // Championship Trophy Art
                            Box(
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.dp, NeonGold.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_arena_trophy),
                                    contentDescription = "Daily Arena Trophy",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }

            // Section Title
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SELECT YOUR GAME",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NeonCyan,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "2x2 ARENA GRID",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                }
            }

            // 2x2 Grid - Row 1: Tap Speed + Car Endless
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GameCard(
                        game = GameType.TAP_SPEED,
                        status = attemptStatuses[GameType.TAP_SPEED.id] ?: AttemptStatus.NotPlayed,
                        bestScore = firestoreService.getLocalBestScore(GameType.TAP_SPEED),
                        onSelectGame = onSelectGame,
                        onShowLockedDialog = { title, score ->
                            lockedGameTitle = title
                            lockedGameScore = score
                        },
                        modifier = Modifier.weight(1f)
                    )
                    GameCard(
                        game = GameType.CAR_ENDLESS,
                        status = attemptStatuses[GameType.CAR_ENDLESS.id] ?: AttemptStatus.NotPlayed,
                        bestScore = firestoreService.getLocalBestScore(GameType.CAR_ENDLESS),
                        onSelectGame = onSelectGame,
                        onShowLockedDialog = { title, score ->
                            lockedGameTitle = title
                            lockedGameScore = score
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 2x2 Grid - Row 2: Flappy + Jump & Run
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GameCard(
                        game = GameType.FLAPPY,
                        status = attemptStatuses[GameType.FLAPPY.id] ?: AttemptStatus.NotPlayed,
                        bestScore = firestoreService.getLocalBestScore(GameType.FLAPPY),
                        onSelectGame = onSelectGame,
                        onShowLockedDialog = { title, score ->
                            lockedGameTitle = title
                            lockedGameScore = score
                        },
                        modifier = Modifier.weight(1f)
                    )
                    GameCard(
                        game = GameType.JUMP_RUN,
                        status = attemptStatuses[GameType.JUMP_RUN.id] ?: AttemptStatus.NotPlayed,
                        bestScore = firestoreService.getLocalBestScore(GameType.JUMP_RUN),
                        onSelectGame = onSelectGame,
                        onShowLockedDialog = { title, score ->
                            lockedGameTitle = title
                            lockedGameScore = score
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (lockedGameTitle != null) {
        AttemptLockedDialog(
            gameTitle = lockedGameTitle!!,
            finalScore = lockedGameScore,
            onDismiss = { lockedGameTitle = null }
        )
    }
}

@Composable
fun GameCard(
    game: GameType,
    status: AttemptStatus,
    bestScore: Long,
    onSelectGame: (GameType) -> Unit,
    onShowLockedDialog: (String, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val isPlayed = status is AttemptStatus.AlreadyPlayed
    val completedScore = (status as? AttemptStatus.AlreadyPlayed)?.finalScore ?: 0L

    val imageRes = when (game) {
        GameType.TAP_SPEED -> R.drawable.img_tap_speed
        GameType.CAR_ENDLESS -> R.drawable.img_car_endless
        GameType.FLAPPY -> R.drawable.img_flappy
        GameType.JUMP_RUN -> R.drawable.img_jump_run
    }

    val tagline = when (game) {
        GameType.TAP_SPEED -> "Tap fast, go faster!"
        GameType.CAR_ENDLESS -> "Dodge traffic, keep going!"
        GameType.FLAPPY -> "Tap to fly, avoid pipes!"
        GameType.JUMP_RUN -> "Jump, run, collect coins!"
    }

    val accentColor = when (game) {
        GameType.TAP_SPEED -> NeonCyan
        GameType.CAR_ENDLESS -> NeonGold
        GameType.FLAPPY -> NeonGreen
        GameType.JUMP_RUN -> NeonOrange
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isPlayed) CyberBorder else accentColor.copy(alpha = 0.45f)
        ),
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable {
                if (isPlayed) {
                    onShowLockedDialog(game.title, completedScore)
                } else {
                    onSelectGame(game)
                }
            }
            .testTag("game_card_${game.id}")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Visual Preview Thumbnail with overlay badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(105.dp)
            ) {
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = game.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Gradient overlay at bottom of thumbnail for contrast
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.3f),
                                    Color.Transparent,
                                    CyberSurface
                                )
                            )
                        )
                )

                // Status Badge in top right corner
                Surface(
                    color = if (isPlayed) CyberSurfaceVariant.copy(alpha = 0.92f) else NeonCyan.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        0.8.dp,
                        if (isPlayed) CyberBorder else NeonCyan
                    ),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlayed) Icons.Default.Lock else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isPlayed) TextMuted else Color(0xFF050B18),
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = if (isPlayed) "DONE" else "READY",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isPlayed) TextMuted else Color(0xFF050B18)
                        )
                    }
                }
            }

            // Card Body Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = game.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = tagline,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = if (isPlayed) "Today: $completedScore pts" else if (bestScore > 0) "Best: $bestScore pts" else "Daily: 1 Attempt",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPlayed) NeonGreen else NeonCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Action Button: PLAY or LOCKED
                Button(
                    onClick = {
                        if (isPlayed) {
                            onShowLockedDialog(game.title, completedScore)
                        } else {
                            onSelectGame(game)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlayed) CyberSurfaceVariant else NeonCyan,
                        contentColor = if (isPlayed) TextSecondary else Color(0xFF050B18)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .testTag("play_button_${game.id}")
                ) {
                    Text(
                        text = if (isPlayed) "🔒 LOCKED" else "▶ PLAY",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}




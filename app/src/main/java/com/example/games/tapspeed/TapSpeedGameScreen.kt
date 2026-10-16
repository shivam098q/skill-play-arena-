package com.example.games.tapspeed

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.GameAttempt
import com.example.model.GameType
import com.example.model.UserProfile
import com.example.services.AdMobConfig
import com.example.services.AdMobService
import com.example.services.FirestoreService
import com.example.ui.components.RewardedAdSimulationDialog
import com.example.ui.theme.*
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class TapGamePhase {
    NOT_STARTED,
    COUNTDOWN,
    ACTIVE_PLAYING,
    POST_AD_READY,
    SESSION_FINISHED
}

/**
 * Returns the exact bonus duration for the Tap Speed Challenge:
 * Ad #1: 30 seconds
 * Ad #2: 20 seconds
 * Ad #3: 10 seconds
 */
fun getTapBonusSeconds(adIndex: Int): Int = when (adIndex) {
    0 -> 30
    1 -> 20
    2 -> 10
    else -> 0
}

@Composable
fun AdMobBannerView(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        color = CyberDarkBg
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                try {
                    AdView(ctx).apply {
                        setAdSize(AdSize.BANNER)
                        // Standard Google AdMob test banner ad unit ID
                        adUnitId = "ca-app-pub-3940256099942544/6300978111"
                        loadAd(AdRequest.Builder().build())
                    }
                } catch (e: Exception) {
                    android.view.View(ctx)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapSpeedGameScreen(
    user: UserProfile,
    attempt: GameAttempt,
    adMobService: AdMobService,
    firestoreService: FirestoreService,
    onBack: () -> Unit,
    onScoreSubmitted: (Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var phase by remember { mutableStateOf(TapGamePhase.NOT_STARTED) }
    var countdownNumber by remember { mutableIntStateOf(3) }
    var secondsLeft by remember { mutableIntStateOf(60) }
    var nextSessionSeconds by remember { mutableIntStateOf(60) }
    var baseScore by remember { mutableLongStateOf(0L) }
    var adsWatchedCount by remember { mutableIntStateOf(0) }
    var totalBonusSeconds by remember { mutableLongStateOf(0L) }
    var isSubmitting by remember { mutableStateOf(false) }

    var isSimulatingAd by remember { mutableStateOf(false) }
    var adBonusPending by remember { mutableIntStateOf(0) }

    // Tap animations
    var buttonPressed by remember { mutableStateOf(false) }
    val tapScale by animateFloatAsState(
        targetValue = if (buttonPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tap_scale"
    )

    // Pulsing outer ring
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    // Vibrator helper
    fun triggerHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                v.vibrate(25)
            }
        } catch (_: Exception) {}
    }

    // Countdown and session transition helper
    fun startCountdown(durationSeconds: Int) {
        phase = TapGamePhase.COUNTDOWN
        coroutineScope.launch {
            for (c in 3 downTo 1) {
                countdownNumber = c
                triggerHaptic()
                delay(1000L)
            }
            secondsLeft = durationSeconds
            phase = TapGamePhase.ACTIVE_PLAYING
        }
    }

    // Timer loop for active session
    LaunchedEffect(phase) {
        if (phase == TapGamePhase.ACTIVE_PLAYING) {
            while (phase == TapGamePhase.ACTIVE_PLAYING && secondsLeft > 0) {
                delay(1000L)
                if (secondsLeft > 1) {
                    secondsLeft -= 1
                } else {
                    secondsLeft = 0
                    phase = TapGamePhase.SESSION_FINISHED
                    triggerHaptic()
                    break
                }
            }
        }
    }

    fun submitScore() {
        if (isSubmitting) return
        isSubmitting = true
        coroutineScope.launch {
            val finalScore = baseScore
            firestoreService.submitFinalScore(
                attemptId = attempt.attemptId,
                uid = user.uid,
                displayName = user.displayName,
                gameType = GameType.TAP_SPEED,
                baseScore = baseScore,
                rewardAdsWatched = adsWatchedCount,
                rewardBonus = totalBonusSeconds,
                finalScore = finalScore
            )
            isSubmitting = false
            onScoreSubmitted(finalScore)
        }
    }

    fun onAdRewardCompleted(bonus: Int) {
        adsWatchedCount += 1
        totalBonusSeconds += bonus
        nextSessionSeconds = bonus
        secondsLeft = bonus
        phase = TapGamePhase.POST_AD_READY
    }

    fun handleWatchAd() {
        if (adsWatchedCount >= 3) return
        val bonus = getTapBonusSeconds(adsWatchedCount)
        if (bonus <= 0) return

        val activity = context as? Activity
        if (activity != null && adMobService.isAdAvailable()) {
            adMobService.showRewardedAd(
                activity = activity,
                onRewardEarned = {
                    onAdRewardCompleted(bonus)
                },
                onAdDismissed = {},
                onAdFailed = {
                    // Fallback to simulated ad dialog
                    adBonusPending = bonus
                    isSimulatingAd = true
                }
            )
        } else {
            // Simulated ad dialog
            adBonusPending = bonus
            isSimulatingAd = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CyberDarkBg)
            )
        },
        bottomBar = {
            AdMobBannerView(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            )
        },
        containerColor = CyberDarkBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Stats Top Bar (Score & Time Left)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Score Box (Keeps accumulated score)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "SCORE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        Text(
                            text = "$baseScore",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NeonCyan
                        )
                    }
                }

                // Timer Box (Shows current session time remaining)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (secondsLeft <= 10 && phase == TapGamePhase.ACTIVE_PLAYING) NeonRed else CyberBorder
                    ),
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "TIME LEFT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        Text(
                            text = "${secondsLeft}s",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (secondsLeft <= 10 && phase == TapGamePhase.ACTIVE_PLAYING) NeonRed else NeonGold
                        )
                    }
                }
            }

            // Central Tap Target Area with Dynamic Status / Countdown
            Box(
                modifier = Modifier
                    .size(260.dp),
                contentAlignment = Alignment.Center
            ) {
                // Glowing outer pulse aura
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .scale(if (phase == TapGamePhase.ACTIVE_PLAYING) (1f + (pulseAlpha * 0.1f)) else 1f)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    when (phase) {
                                        TapGamePhase.ACTIVE_PLAYING -> NeonCyan.copy(alpha = pulseAlpha)
                                        TapGamePhase.COUNTDOWN -> NeonGold.copy(alpha = 0.35f)
                                        else -> NeonCyan.copy(alpha = 0.12f)
                                    },
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // Main Click Button
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .scale(tapScale)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = when (phase) {
                                    TapGamePhase.ACTIVE_PLAYING -> listOf(
                                        Color(0xFF00E5FF),
                                        Color(0xFF0052D4)
                                    )
                                    TapGamePhase.COUNTDOWN -> listOf(
                                        Color(0xFFFFB800),
                                        Color(0xFFD97706)
                                    )
                                    TapGamePhase.SESSION_FINISHED -> listOf(
                                        Color(0xFF334155),
                                        Color(0xFF1E293B)
                                    )
                                    else -> listOf(
                                        Color(0xFF0099B8),
                                        Color(0xFF003882)
                                    )
                                }
                            )
                        )
                        .border(
                            4.dp,
                            if (phase == TapGamePhase.COUNTDOWN) NeonGold else Color.White.copy(alpha = 0.7f),
                            CircleShape
                        )
                        .testTag("tap_button")
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = phase == TapGamePhase.ACTIVE_PLAYING
                        ) {
                            triggerHaptic()
                            buttonPressed = true
                            baseScore += 1
                            coroutineScope.launch {
                                delay(60L)
                                buttonPressed = false
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    when (phase) {
                        TapGamePhase.COUNTDOWN -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "$countdownNumber",
                                    fontSize = 72.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Text(
                                    text = "GET READY!",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                        TapGamePhase.NOT_STARTED, TapGamePhase.POST_AD_READY -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Ready",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(46.dp)
                                )
                                Text(
                                    text = "READY",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White.copy(alpha = 0.9f),
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                        TapGamePhase.SESSION_FINISHED -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ElectricBolt,
                                    contentDescription = "Finished",
                                    tint = NeonGold,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = "TIME'S UP!",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = NeonGold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                        TapGamePhase.ACTIVE_PLAYING -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ElectricBolt,
                                    contentDescription = "Tap",
                                    tint = Color.White,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = "TAP!",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Actions & Dynamic Phase Controls
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (phase) {
                    TapGamePhase.NOT_STARTED -> {
                        // Clear START button when entering from Home screen
                        Button(
                            onClick = { startCountdown(60) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("start_game_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color(0xFF002026),
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "START",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF002026),
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }

                    TapGamePhase.POST_AD_READY -> {
                        // START button after watching rewarded ad, allowing player to get ready
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Ad completed! Press START when you are ready.",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = NeonCyan,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { startCountdown(nextSessionSeconds) },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("start_game_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color(0xFF002026),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "START",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF002026),
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                        }
                    }

                    TapGamePhase.COUNTDOWN -> {
                        // Countdown banner during 3, 2, 1
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = CyberSurfaceVariant.copy(alpha = 0.6f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "GET READY... $countdownNumber",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonCyan
                                )
                            }
                        }
                    }

                    TapGamePhase.ACTIVE_PLAYING -> {
                        // Gameplay encouragement during active tapping
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = CyberSurfaceVariant.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "TAP AS FAST AS YOU CAN!",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    TapGamePhase.SESSION_FINISHED -> {
                        // Rewarded Ad Options & Final Score Submission
                        if (adsWatchedCount < 3) {
                            val nextBonus = getTapBonusSeconds(adsWatchedCount)
                            Button(
                                onClick = { handleWatchAd() },
                                enabled = !isSubmitting,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyberSurfaceVariant
                                ),
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonGold),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("watch_ad_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Ad",
                                        tint = NeonGold
                                    )
                                    Text(
                                        text = "WATCH AD TO GET +${nextBonus} SEC",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }

                        // Submit Final Score Button (only available after a session finishes)
                        Button(
                            onClick = { submitScore() },
                            enabled = !isSubmitting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("submit_final_score_button")
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color(0xFF002026),
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = "Submit",
                                        tint = Color(0xFF002026)
                                    )
                                    Text(
                                        text = "SUBMIT FINAL SCORE",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = Color(0xFF002026)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Fallback Simulated Ad Dialog
    if (isSimulatingAd) {
        RewardedAdSimulationDialog(
            rewardTitle = "+${adBonusPending}s Extension",
            onRewardEarned = {
                isSimulatingAd = false
                onAdRewardCompleted(adBonusPending)
            },
            onDismiss = { isSimulatingAd = false }
        )
    }
}


package com.example.games.carendless

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.GameAttempt
import com.example.model.GameType
import com.example.model.UserProfile
import com.example.services.AdMobConfig
import com.example.services.AdMobService
import com.example.services.FirestoreService
import com.example.ui.components.RewardedAdSimulationDialog
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

// --- Game Models ---

data class EnemyVehicle(
    val id: Long,
    var lane: Int,              // Lane 0, 1, 2, 3
    var yRatio: Float,          // Vertical position [-0.10f .. 1.2f]
    val speed: Float,           // Downward speed per frame
    val primaryColor: Color,    // Car paint
    val accentColor: Color,     // Trim
    var passed: Boolean = false // Score bonus when player overtakes
)

enum class CarGameState {
    PLAYING,
    GAME_OVER
}

/**
 * Calculates a smooth, continuous speed progression based strictly on survival time.
 * - 0–10s: normal starting speed (1.00x - ~1.27x)
 * - 10–20s: slightly faster (~1.27x - ~1.59x)
 * - 20–30s: faster (~1.59x - ~1.95x, ~2x base)
 * - 30–45s: noticeably faster (~1.95x - ~2.57x)
 * - 45–60s: faster again (~2.57x - ~3.29x)
 * - 60s+: continues to scale smoothly up to reaction limits (~4.5x)
 */
fun calculateCarEndlessSpeedProgression(survivalSeconds: Float): Float {
    val t = survivalSeconds.coerceAtLeast(0f)
    return (1.0f + (t * 0.025f) + (t * t * 0.00022f)).coerceAtMost(4.5f)
}

@Composable
fun CarEndlessGameScreen(
    user: UserProfile,
    attempt: GameAttempt,
    adMobService: AdMobService,
    firestoreService: FirestoreService,
    onBack: () -> Unit,
    onScoreSubmitted: (Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Sound & Haptic FX
    val toneGenerator = remember {
        try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 60)
        } catch (_: Exception) {
            null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                toneGenerator?.release()
            } catch (_: Exception) {}
        }
    }

    fun playSfx(toneType: Int, durationMs: Int = 80) {
        try {
            toneGenerator?.startTone(toneType, durationMs)
        } catch (_: Exception) {}
    }

    fun triggerHaptic(durationMs: Long = 25L) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    // High score loaded from local cache for record keeping
    var bestScore by remember {
        mutableLongStateOf(firestoreService.getLocalBestScore(GameType.CAR_ENDLESS))
    }

    // Game State
    var gameState by remember { mutableStateOf(CarGameState.PLAYING) }
    var gameLoopKey by remember { mutableIntStateOf(0) }

    // Road Layout: Classic vertical road, slightly wider and comfortably centered
    val roadWidthRatio = 0.78f // 78% of screen width: spacious 4 lanes without being an extreme highway
    val laneCount = 4

    // Player position: Lower-middle area (0.68f) leaving ample room ahead for approaching traffic
    var playerRoadX by remember { mutableFloatStateOf(0.50f) }
    var targetPlayerRoadX by remember { mutableFloatStateOf(0.50f) }
    val playerYRatio = 0.68f // Lower-middle area as requested

    // Scoring & Statistics (Internal logic strictly preserved)
    var distanceTravelled by remember { mutableLongStateOf(0L) }
    var carsPassedCount by remember { mutableLongStateOf(0L) }
    val currentScore by remember {
        derivedStateOf { distanceTravelled + (carsPassedCount * 50L) }
    }

    LaunchedEffect(currentScore) {
        if (currentScore > bestScore) {
            bestScore = currentScore
        }
    }

    // Traffic List
    val trafficList = remember { mutableStateListOf<EnemyVehicle>() }

    // Visual animation & time progression states
    var roadDashOffset by remember { mutableFloatStateOf(0f) }
    var adsWatchedCount by remember { mutableIntStateOf(0) }
    var isInvulnerable by remember { mutableStateOf(false) }
    var shieldSecondsRemaining by remember { mutableIntStateOf(0) }
    var isSubmitting by remember { mutableStateOf(false) }
    var isSimulatingAd by remember { mutableStateOf(false) }
    var survivalFrames by remember { mutableLongStateOf(0L) }
    var distanceFraction by remember { mutableFloatStateOf(0f) }

    // Clean Game Reset (Play Again)
    fun restartGame() {
        trafficList.clear()
        playerRoadX = 0.50f
        targetPlayerRoadX = 0.50f
        distanceTravelled = 0L
        carsPassedCount = 0L
        survivalFrames = 0L
        distanceFraction = 0f
        isInvulnerable = false
        shieldSecondsRemaining = 0
        gameState = CarGameState.PLAYING
        gameLoopKey++
    }

    // Revive after watching rewarded ad: Resumes EXACTLY where game ended + 5-second shield
    fun revivePlayer() {
        adsWatchedCount += 1

        // Clear immediate hazardous vehicles directly overlapping the player
        trafficList.removeAll { abs(it.yRatio - playerYRatio) < 0.25f }

        // Keep player approximately at current road position
        playerRoadX = playerRoadX.coerceIn(0.12f, 0.88f)
        targetPlayerRoadX = playerRoadX

        // Grant 5-second shield protection
        isInvulnerable = true
        shieldSecondsRemaining = 5
        gameState = CarGameState.PLAYING
        gameLoopKey++
        playSfx(ToneGenerator.TONE_PROP_BEEP2, 180)

        coroutineScope.launch {
            for (sec in 5 downTo 1) {
                shieldSecondsRemaining = sec
                delay(1000L)
            }
            shieldSecondsRemaining = 0
            isInvulnerable = false
        }
    }

    fun handleWatchAd() {
        if (adsWatchedCount >= AdMobConfig.MAX_REWARD_ADS_PER_SESSION) return
        val activity = context as? Activity
        if (activity != null && adMobService.isAdAvailable()) {
            adMobService.showRewardedAd(
                activity = activity,
                onRewardEarned = { revivePlayer() },
                onAdDismissed = {},
                onAdFailed = { isSimulatingAd = true }
            )
        } else {
            isSimulatingAd = true
        }
    }

    // Submit Final Score to Firebase Firestore Leaderboard
    fun submitScore() {
        if (isSubmitting) return
        isSubmitting = true
        coroutineScope.launch {
            val finalScore = currentScore
            firestoreService.submitFinalScore(
                attemptId = attempt.attemptId,
                uid = user.uid,
                displayName = user.displayName,
                gameType = GameType.CAR_ENDLESS,
                baseScore = distanceTravelled,
                rewardAdsWatched = adsWatchedCount,
                rewardBonus = carsPassedCount * 50L,
                finalScore = finalScore
            )
            isSubmitting = false
            onScoreSubmitted(finalScore)
        }
    }

    // Primary Game Engine Loop
    LaunchedEffect(gameState, gameLoopKey) {
        if (gameState != CarGameState.PLAYING) return@LaunchedEffect

        var lastSpawnFrame = survivalFrames

        while (gameState == CarGameState.PLAYING) {
            delay(16L) // ~60 FPS
            survivalFrames++

            // 1. Smooth, continuous speed progression based strictly on survival time
            val survivalSeconds = survivalFrames / 60.0f
            val speedProgression = calculateCarEndlessSpeedProgression(survivalSeconds)

            // 2. Smooth Player Steering (unchanged, strictly preserved)
            playerRoadX += (targetPlayerRoadX - playerRoadX) * 0.24f

            // 3. Road Distance and Visual Speed Progression
            distanceFraction += speedProgression
            if (distanceFraction >= 1.0f) {
                val distanceIncrement = distanceFraction.toLong()
                distanceTravelled += distanceIncrement
                distanceFraction -= distanceIncrement
            }
            val baseDashSpeed = 0.038f * speedProgression
            roadDashOffset = (roadDashOffset + baseDashSpeed) % 1.0f

            // 4. Move Traffic & Collision Detection
            val enemyIterator = trafficList.iterator()
            while (enemyIterator.hasNext()) {
                val enemy = enemyIterator.next()
                enemy.yRatio += enemy.speed * speedProgression

                // Score for overtakes
                if (!enemy.passed && enemy.yRatio > playerYRatio) {
                    enemy.passed = true
                    carsPassedCount += 1L
                    playSfx(ToneGenerator.TONE_PROP_ACK, 40)
                }

                // Collision check (only active when not invulnerable)
                if (!isInvulnerable && abs(enemy.yRatio - playerYRatio) < 0.075f) {
                    val laneCenterNormalized = (enemy.lane + 0.5f) / laneCount.toFloat()
                    val horizontalDist = abs(laneCenterNormalized - playerRoadX)

                    // Collision threshold
                    if (horizontalDist < 0.14f) {
                        playSfx(ToneGenerator.TONE_PROP_NACK, 260)
                        triggerHaptic(140L)
                        gameState = CarGameState.GAME_OVER
                        break
                    }
                }

                // Remove vehicles that scrolled past bottom
                if (enemy.yRatio > 1.20f) {
                    enemyIterator.remove()
                }
            }

            if (gameState == CarGameState.GAME_OVER) break

            // 5. Traffic Spawner: Scales interval with speed while keeping fair lanes and safe reaction distance
            val spawnInterval = (60f / Math.sqrt(speedProgression.toDouble())).toInt().coerceIn(20, 65)
            if (survivalFrames - lastSpawnFrame >= spawnInterval) {
                lastSpawnFrame = survivalFrames

                // Check which lanes are occupied at the top entrance (yRatio < 0.22f)
                val occupiedLanesAtTop = trafficList.filter { it.yRatio < 0.22f }.map { it.lane }.toSet()

                // Also ensure we never block all 4 lanes simultaneously across the approaching zone
                val occupiedLanesInUpperRoad = trafficList.filter { it.yRatio in -0.15f..0.35f }.map { it.lane }.toSet()

                // Available lanes must be clear at the top and leave at least one guaranteed open lane
                val availableLanes = (0 until laneCount).filter { lane ->
                    lane !in occupiedLanesAtTop && (occupiedLanesInUpperRoad.size < 3 || lane !in occupiedLanesInUpperRoad)
                }

                if (availableLanes.isNotEmpty()) {
                    val chosenLane = availableLanes.random()

                    // Classic vibrant car palettes
                    val palette = listOf(
                        Pair(Color(0xFF29B6F6), Color(0xFF0288D1)), // Light Blue
                        Pair(Color(0xFFFFCA28), Color(0xFFF57F17)), // Amber Yellow
                        Pair(Color(0xFF66BB6A), Color(0xFF2E7D32)), // Vibrant Green
                        Pair(Color(0xFFAB47BC), Color(0xFF6A1B9A)), // Purple
                        Pair(Color(0xFFFF7043), Color(0xFFD84315))  // Orange
                    ).random()

                    val speedVariation = 0.0072f + (Random.nextFloat() * 0.0035f)

                    trafficList.add(
                        EnemyVehicle(
                            id = Random.nextLong(),
                            lane = chosenLane,
                            yRatio = -0.10f, // Spawns well above the visible screen
                            speed = speedVariation,
                            primaryColor = palette.first,
                            accentColor = palette.second
                        )
                    )
                }
            }
        }
    }

    // --- SCREEN LAYOUT ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)) // Cyber slate background
    ) {
        // 1. Gameplay Canvas: Classic vertical road + traffic + player car
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Direct touch drag & tap steering
                .pointerInput(gameState) {
                    if (gameState == CarGameState.PLAYING) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val roadWidthPx = size.width * roadWidthRatio
                            val deltaRatio = dragAmount.x / roadWidthPx
                            targetPlayerRoadX = (targetPlayerRoadX + deltaRatio).coerceIn(0.12f, 0.88f)
                        }
                    }
                }
                .pointerInput(gameState) {
                    if (gameState == CarGameState.PLAYING) {
                        detectTapGestures { offset ->
                            val roadW = size.width * roadWidthRatio
                            val roadLeft = size.width * ((1f - roadWidthRatio) / 2f)
                            if (offset.x in roadLeft..(roadLeft + roadW)) {
                                val tappedRatio = ((offset.x - roadLeft) / roadW).coerceIn(0.12f, 0.88f)
                                targetPlayerRoadX = tappedRatio
                            }
                        }
                    }
                }
                .testTag("car_endless_canvas")
        ) {
            val canvasW = size.width
            val canvasH = size.height

            val roadWPx = canvasW * roadWidthRatio
            val roadLeftPx = (canvasW - roadWPx) / 2f
            val roadRightPx = roadLeftPx + roadWPx
            val laneWPx = roadWPx / laneCount.toFloat()

            // 1. Road Shoulders / Verges (Dark asphalt / cyber curbs)
            drawRect(
                color = Color(0xFF131822),
                topLeft = Offset(0f, 0f),
                size = Size(canvasW, canvasH)
            )

            // 2. Main Asphalt Road Surface (Classic top-down road)
            drawRect(
                color = Color(0xFF1E2430),
                topLeft = Offset(roadLeftPx, 0f),
                size = Size(roadWPx, canvasH)
            )

            // 3. Road Side Curbs (Left & Right solid yellow boundaries)
            drawLine(
                color = Color(0xFFFFB300),
                start = Offset(roadLeftPx, 0f),
                end = Offset(roadLeftPx, canvasH),
                strokeWidth = 3.5.dp.toPx()
            )
            drawLine(
                color = Color(0xFFFFB300),
                start = Offset(roadRightPx, 0f),
                end = Offset(roadRightPx, canvasH),
                strokeWidth = 3.5.dp.toPx()
            )

            // 4. White Dashed Lane Dividers (Scrolling vertically)
            val dashLength = 32.dp.toPx()
            val dashGap = 24.dp.toPx()
            val totalPeriod = dashLength + dashGap
            val offsetPx = roadDashOffset * totalPeriod

            for (laneIndex in 1 until laneCount) {
                val lineXPx = roadLeftPx + (laneIndex * laneWPx)
                var currentY = -totalPeriod + offsetPx

                while (currentY < canvasH + totalPeriod) {
                    val startY = currentY.coerceAtLeast(0f)
                    val endY = (currentY + dashLength).coerceAtMost(canvasH)

                    if (endY > startY) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.80f),
                            start = Offset(lineXPx, startY),
                            end = Offset(lineXPx, endY),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                    currentY += totalPeriod
                }
            }

            // Standard car dimensions for classic top-down styling
            val carWidth = laneWPx * 0.65f
            val carHeight = carWidth * 1.85f

            // 5. Approaching Traffic Vehicles (Classic top-down style)
            for (enemy in trafficList) {
                val enemyCenterXPx = roadLeftPx + ((enemy.lane + 0.5f) * laneWPx)
                val enemyCenterYPx = enemy.yRatio * canvasH

                // Only draw if on or near screen
                if (enemyCenterYPx in (-carHeight)..(canvasH + carHeight)) {
                    drawClassicTopDownCar(
                        centerX = enemyCenterXPx,
                        centerY = enemyCenterYPx,
                        width = carWidth,
                        height = carHeight,
                        bodyColor = enemy.primaryColor,
                        accentColor = enemy.accentColor,
                        facingDown = true // Headlights facing toward player
                    )
                }
            }

            // 6. Player Car (Classic top-down car in lower-middle position)
            val playerCenterXPx = roadLeftPx + (playerRoadX * roadWPx)
            val playerCenterYPx = playerYRatio * canvasH

            drawClassicTopDownCar(
                centerX = playerCenterXPx,
                centerY = playerCenterYPx,
                width = carWidth,
                height = carHeight,
                bodyColor = Color(0xFFE53935), // Classic vibrant red sports car
                accentColor = Color(0xFFB71C1C),
                facingDown = false // Headlights facing forward (upward)
            )

            // 7. 5-Second Invulnerability Forcefield Shield Effect
            if (isInvulnerable) {
                val shieldRadius = (carHeight * 0.68f) + (sin(distanceTravelled * 0.2) * 2.5f).toFloat()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF00E5FF).copy(alpha = 0.35f),
                            Color(0xFF18FFFF).copy(alpha = 0.16f),
                            Color(0xFF00B0FF).copy(alpha = 0.02f)
                        ),
                        center = Offset(playerCenterXPx, playerCenterYPx),
                        radius = shieldRadius
                    ),
                    radius = shieldRadius,
                    center = Offset(playerCenterXPx, playerCenterYPx)
                )
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = 0.85f),
                    radius = shieldRadius,
                    center = Offset(playerCenterXPx, playerCenterYPx),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }
        }

        // 2. CLEAN TOP HUD: ONLY "SCORE: [value]"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Unobtrusive minimal back button
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(38.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Shield status badge (shown only when 5-second shield is active)
            if (isInvulnerable) {
                Surface(
                    color = Color.Black.copy(alpha = 0.60f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, NeonCyan)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Shield",
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "SHIELD: ${shieldSecondsRemaining}s",
                            color = NeonCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // CLEAN SCORE DISPLAY: "SCORE: 0"
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                    .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .testTag("car_score_display")
            ) {
                Text(
                    text = "SCORE: $currentScore",
                    color = NeonCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }

        // 3. CIRCULAR ARROW CONTROLS (Kept from latest version)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular LEFT Arrow Button
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFF0F172A).copy(alpha = 0.88f), CircleShape)
                    .border(2.dp, NeonCyan, CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 32.dp)
                    ) {
                        targetPlayerRoadX = (targetPlayerRoadX - 0.25f).coerceIn(0.12f, 0.88f)
                        triggerHaptic(15L)
                    }
                    .testTag("circular_steer_left"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Steer Left",
                    tint = NeonCyan,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Circular RIGHT Arrow Button
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFF0F172A).copy(alpha = 0.88f), CircleShape)
                    .border(2.dp, NeonCyan, CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 32.dp)
                    ) {
                        targetPlayerRoadX = (targetPlayerRoadX + 0.25f).coerceIn(0.12f, 0.88f)
                        triggerHaptic(15L)
                    }
                    .testTag("circular_steer_right"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Steer Right",
                    tint = NeonCyan,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }

    // --- GAME OVER SCREEN ---
    if (gameState == CarGameState.GAME_OVER && !isSubmitting) {
        Dialog(onDismissRequest = {}) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                border = androidx.compose.foundation.BorderStroke(2.dp, NeonRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .testTag("car_game_over_card")
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Crashed Icon Header
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(NeonRed.copy(alpha = 0.2f), CircleShape)
                            .border(1.5.dp, NeonRed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = "Crashed",
                            tint = NeonRed,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Text(
                        text = "GAME OVER",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = NeonRed,
                        letterSpacing = 1.sp
                    )

                    // Results Summary Card
                    Surface(
                        color = CyberSurfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "FINAL SCORE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "$currentScore",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                color = NeonCyan
                            )
                            Text(
                                text = "Distance: ${distanceTravelled}m • Overtakes: $carsPassedCount",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                            if (currentScore >= bestScore && currentScore > 0) {
                                Surface(
                                    color = NeonGold.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Text(
                                        text = "★ NEW BEST RECORD! ★",
                                        color = NeonGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 1. WATCH ADS & GET CHANCE (Resumes from exact crash spot with 5s shield)
                    val adsLeft = AdMobConfig.MAX_REWARD_ADS_PER_SESSION - adsWatchedCount
                    if (adsLeft > 0) {
                        Button(
                            onClick = { handleWatchAd() },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("watch_ads_get_chance_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Ad",
                                    tint = Color.Black
                                )
                                Text(
                                    text = "WATCH ADS & GET CHANCE ($adsLeft/3)",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // 2. SUBMIT FINAL SCORE
                    Button(
                        onClick = { submitScore() },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("submit_final_score_button")
                    ) {
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
                                color = Color(0xFF002026),
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // 3. Play Again Option
                    OutlinedButton(
                        onClick = { restartGame() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("play_again_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Play Again",
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "PLAY AGAIN",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Exit Button
                    TextButton(onClick = onBack) {
                        Text(text = "Exit to Arena", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // Rewarded Ad Simulation Dialog (Fallback for testing)
    if (isSimulatingAd) {
        RewardedAdSimulationDialog(
            rewardTitle = "Revive with 5s Shield",
            onRewardEarned = {
                isSimulatingAd = false
                revivePlayer()
            },
            onDismiss = { isSimulatingAd = false }
        )
    }
}

// --- Classic Top-Down Vehicle Rendering (Restored from previous style) ---

/**
 * Draws the classic top-down arcade car with original shape, proportions, windshield,
 * wheels, and headlights.
 */
fun DrawScope.drawClassicTopDownCar(
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    bodyColor: Color,
    accentColor: Color,
    facingDown: Boolean
) {
    val left = centerX - (width / 2f)
    val top = centerY - (height / 2f)

    // 1. Soft Ground Drop Shadow
    drawRoundRect(
        color = Color(0x66000000),
        topLeft = Offset(left + 2.dp.toPx(), top + 3.dp.toPx()),
        size = Size(width, height),
        cornerRadius = CornerRadius(width * 0.22f, width * 0.22f)
    )

    // 2. 4 Corner Wheels (Black rubber tires)
    val tireW = width * 0.16f
    val tireH = height * 0.20f
    val tireColor = Color(0xFF15181E)

    // Front tires
    drawRoundRect(
        color = tireColor,
        topLeft = Offset(left - (tireW * 0.35f), top + (height * 0.12f)),
        size = Size(tireW, tireH),
        cornerRadius = CornerRadius(3f, 3f)
    )
    drawRoundRect(
        color = tireColor,
        topLeft = Offset(left + width - (tireW * 0.65f), top + (height * 0.12f)),
        size = Size(tireW, tireH),
        cornerRadius = CornerRadius(3f, 3f)
    )

    // Rear tires
    drawRoundRect(
        color = tireColor,
        topLeft = Offset(left - (tireW * 0.35f), top + (height * 0.68f)),
        size = Size(tireW, tireH),
        cornerRadius = CornerRadius(3f, 3f)
    )
    drawRoundRect(
        color = tireColor,
        topLeft = Offset(left + width - (tireW * 0.65f), top + (height * 0.68f)),
        size = Size(tireW, tireH),
        cornerRadius = CornerRadius(3f, 3f)
    )

    // 3. Rounded Rect Car Body (Previous car shape and style)
    drawRoundRect(
        color = bodyColor,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(width * 0.24f, width * 0.24f)
    )

    // Body contour border
    drawRoundRect(
        color = accentColor,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(width * 0.24f, width * 0.24f),
        style = Stroke(width = 1.2.dp.toPx())
    )

    // 4. Cockpit Cabin / Windshields
    val cabinW = width * 0.66f
    val cabinH = height * 0.45f
    val cabinLeft = left + (width * 0.17f)
    val cabinTop = top + (height * 0.27f)

    // Dark tinted cabin window
    drawRoundRect(
        color = Color(0xFF0F172A),
        topLeft = Offset(cabinLeft, cabinTop),
        size = Size(cabinW, cabinH),
        cornerRadius = CornerRadius(6f, 6f)
    )

    // Roof Center Panel
    val roofInset = 3.dp.toPx()
    drawRoundRect(
        color = bodyColor,
        topLeft = Offset(cabinLeft + roofInset, cabinTop + (cabinH * 0.24f)),
        size = Size(cabinW - (roofInset * 2), cabinH * 0.52f),
        cornerRadius = CornerRadius(3f, 3f)
    )

    // Windshield reflection glare
    drawLine(
        color = Color.White.copy(alpha = 0.40f),
        start = Offset(cabinLeft + 2.dp.toPx(), cabinTop + 2.dp.toPx()),
        end = Offset(cabinLeft + (cabinW * 0.45f), cabinTop + (cabinH * 0.22f)),
        strokeWidth = 1.2.dp.toPx()
    )

    // 5. Headlights & Taillights
    val lightW = width * 0.22f
    val lightH = height * 0.08f
    val headlightColor = Color(0xFFFFEE58) // Warm Yellow
    val taillightColor = Color(0xFFFF1744) // Bright Red

    if (facingDown) {
        // Traffic oncoming: Headlights at bottom (facing down toward player)
        drawRoundRect(
            color = headlightColor,
            topLeft = Offset(left + (width * 0.10f), top + height - lightH - 2.dp.toPx()),
            size = Size(lightW, lightH),
            cornerRadius = CornerRadius(3f, 3f)
        )
        drawRoundRect(
            color = headlightColor,
            topLeft = Offset(left + width - (width * 0.10f) - lightW, top + height - lightH - 2.dp.toPx()),
            size = Size(lightW, lightH),
            cornerRadius = CornerRadius(3f, 3f)
        )

        // Taillights at top
        drawRoundRect(
            color = taillightColor,
            topLeft = Offset(left + (width * 0.14f), top + 2.dp.toPx()),
            size = Size(lightW, lightH * 0.6f),
            cornerRadius = CornerRadius(2f, 2f)
        )
        drawRoundRect(
            color = taillightColor,
            topLeft = Offset(left + width - (width * 0.14f) - lightW, top + 2.dp.toPx()),
            size = Size(lightW, lightH * 0.6f),
            cornerRadius = CornerRadius(2f, 2f)
        )
    } else {
        // Player car: Headlights at top (facing forward)
        drawRoundRect(
            color = headlightColor,
            topLeft = Offset(left + (width * 0.10f), top + 2.dp.toPx()),
            size = Size(lightW, lightH),
            cornerRadius = CornerRadius(3f, 3f)
        )
        drawRoundRect(
            color = headlightColor,
            topLeft = Offset(left + width - (width * 0.10f) - lightW, top + 2.dp.toPx()),
            size = Size(lightW, lightH),
            cornerRadius = CornerRadius(3f, 3f)
        )

        // Taillights at bottom
        drawRoundRect(
            color = taillightColor,
            topLeft = Offset(left + (width * 0.14f), top + height - (lightH * 0.6f) - 2.dp.toPx()),
            size = Size(lightW, lightH * 0.6f),
            cornerRadius = CornerRadius(2f, 2f)
        )
        drawRoundRect(
            color = taillightColor,
            topLeft = Offset(left + width - (width * 0.14f) - lightW, top + height - (lightH * 0.6f) - 2.dp.toPx()),
            size = Size(lightW, lightH * 0.6f),
            cornerRadius = CornerRadius(2f, 2f)
        )
    }
}

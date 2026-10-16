package com.example.games.flappy

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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

// --- Models ---

enum class FlappyState {
    READY,      // Floating bobbing, waiting for first tap
    PLAYING,    // Physics active, obstacles moving
    PAUSED,     // Paused
    GAME_OVER   // Collided
}

data class FlappyPillar(
    val id: Long,
    var xRatio: Float,
    val topHeightRatio: Float,
    val gapRatio: Float,
    var passed: Boolean = false
)

data class FlappyCoin(
    val id: Long,
    var xRatio: Float,
    var yRatio: Float,
    var collected: Boolean = false
)

data class FlappyParticle(
    var xRatio: Float,
    var yRatio: Float,
    var vx: Float,
    var vy: Float,
    var alpha: Float = 1f,
    val color: Color,
    val sizePx: Float,
    var life: Int = 20
)

data class FloatingText(
    var xRatio: Float,
    var yRatio: Float,
    var text: String,
    var alpha: Float = 1f,
    var life: Int = 25
)

@Composable
fun FlappyGameScreen(
    user: UserProfile,
    attempt: GameAttempt,
    adMobService: AdMobService,
    firestoreService: FirestoreService,
    onBack: () -> Unit,
    onScoreSubmitted: (Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Sound & Haptics
    val toneGen = remember {
        try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 60)
        } catch (_: Exception) {
            null
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            toneGen?.release()
        }
    }

    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun playSfx(toneType: Int, durationMs: Int = 50) {
        try {
            toneGen?.startTone(toneType, durationMs)
        } catch (_: Exception) {}
    }

    fun triggerHaptic(durationMs: Long = 15L) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    // Run key to ensure clean restarts without duplicate loops
    var runKey by remember { mutableIntStateOf(0) }
    var gameState by remember { mutableStateOf(FlappyState.READY) }

    // Bird state (slightly smaller proportions for natural mobile navigation)
    val birdXRatio = 0.25f
    var birdYRatio by remember { mutableFloatStateOf(0.44f) }
    var birdVelocity by remember { mutableFloatStateOf(0f) }
    var birdRotation by remember { mutableFloatStateOf(0f) }
    var wingFlapFrame by remember { mutableIntStateOf(0) }

    // Game objects
    val pillars = remember { mutableStateListOf<FlappyPillar>() }
    val coins = remember { mutableStateListOf<FlappyCoin>() }
    val particles = remember { mutableStateListOf<FlappyParticle>() }
    val floatingTexts = remember { mutableStateListOf<FloatingText>() }

    // Internal score, coins & stats for competition & leaderboard
    var score by remember { mutableLongStateOf(0L) }
    var coinsCount by remember { mutableIntStateOf(0) }
    var gatesCleared by remember { mutableIntStateOf(0) }
    val bestScore = remember { firestoreService.getLocalBestScore(GameType.FLAPPY) }

    // Rewarded ad continue & 5-second shield state
    var adsWatchedCount by remember { mutableIntStateOf(0) }
    var isInvulnerable by remember { mutableStateOf(false) }
    var shieldSecondsRemaining by remember { mutableIntStateOf(0) }
    var isSubmitting by remember { mutableStateOf(false) }
    var isSimulatingAd by remember { mutableStateOf(false) }

    // Scenery parallax offsets
    var bgGroundOffset by remember { mutableFloatStateOf(0f) }
    var bgCloudsOffset by remember { mutableFloatStateOf(0f) }
    var bgCityOffset by remember { mutableFloatStateOf(0f) }

    // Constants & Physics
    val groundYRatio = 0.86f // Maximized full-screen vertical playable range
    val birdRadiusRatio = 0.026f // Slightly smaller bird
    val gravity = 0.00068f
    val flapImpulse = -0.0135f

    // Smooth, gradual difficulty scaling based on score
    val progressFactor = (score / 120f).coerceIn(0f, 1f)
    val obstacleSpeed = 0.0055f + (progressFactor * 0.0035f) // from 0.0055f to 0.0090f
    val pillarGapRatio = 0.32f - (progressFactor * 0.09f) // from 0.32f down to 0.23f
    val spawnIntervalTicks = (125 - (progressFactor * 35).toInt()).coerceAtLeast(88)

    // Action: Flap
    fun flap() {
        if (gameState == FlappyState.READY) {
            gameState = FlappyState.PLAYING
            birdVelocity = flapImpulse
            wingFlapFrame = 12
            playSfx(ToneGenerator.TONE_PROP_BEEP, 35)
            triggerHaptic(12L)
            return
        }

        if (gameState == FlappyState.PLAYING) {
            birdVelocity = flapImpulse
            wingFlapFrame = 12
            playSfx(ToneGenerator.TONE_PROP_BEEP, 35)
            triggerHaptic(12L)

            // Spawn gentle flap puff particles behind bird
            particles.add(
                FlappyParticle(
                    xRatio = birdXRatio - 0.026f,
                    yRatio = birdYRatio + 0.012f,
                    vx = -0.0028f + (Random.nextFloat() * 0.001f),
                    vy = (Random.nextFloat() - 0.5f) * 0.002f,
                    alpha = 0.65f,
                    color = Color.White.copy(alpha = 0.7f),
                    sizePx = 6f,
                    life = 14
                )
            )
        }
    }

    // Action: Restart Run from scratch
    fun restartGame() {
        pillars.clear()
        coins.clear()
        particles.clear()
        floatingTexts.clear()
        score = 0L
        coinsCount = 0
        gatesCleared = 0
        birdYRatio = 0.44f
        birdVelocity = 0f
        birdRotation = 0f
        isInvulnerable = false
        shieldSecondsRemaining = 0
        gameState = FlappyState.READY
        runKey++
    }

    // Action: Revive via Rewarded Ad (Resumes from EXACT same position with 5s shield)
    fun revivePlayer() {
        adsWatchedCount += 1

        // Maintain exact player position, nudging slightly if ground collision occurred
        birdYRatio = birdYRatio.coerceIn(0.16f, groundYRatio - 0.12f)
        birdVelocity = 0f
        birdRotation = 0f

        // Remove any pipe directly overlapping the bird's resume point to prevent trap
        pillars.removeAll { p ->
            val pLeft = p.xRatio
            val pRight = p.xRatio + 0.14f
            pRight >= (birdXRatio - 0.06f) && pLeft <= (birdXRatio + 0.06f)
        }

        // Grant 5-second shield protection
        isInvulnerable = true
        shieldSecondsRemaining = 5
        gameState = FlappyState.PLAYING

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

    // Score submission uses existing Firestore service and rules
    fun submitScore() {
        if (isSubmitting) return
        isSubmitting = true
        coroutineScope.launch {
            val finalScore = score
            firestoreService.submitFinalScore(
                attemptId = attempt.attemptId,
                uid = user.uid,
                displayName = user.displayName,
                gameType = GameType.FLAPPY,
                baseScore = score,
                rewardAdsWatched = adsWatchedCount,
                rewardBonus = (coinsCount * 2).toLong(),
                finalScore = finalScore
            )
            isSubmitting = false
            onScoreSubmitted(finalScore)
        }
    }

    // Helper: Spawns intentional reachable coin patterns
    fun spawnCoinPattern(anchorX: Float, gapTopY: Float, gapH: Float) {
        val gapCenterY = gapTopY + (gapH * 0.5f)

        // 1. Primary coin(s) in/near the pipe gap
        coins.add(
            FlappyCoin(
                id = Random.nextLong(),
                xRatio = anchorX + 0.065f, // exactly center of pipe width
                yRatio = gapCenterY.coerceIn(0.18f, groundYRatio - 0.12f)
            )
        )

        // 2. Extra coin sequences between pipe gaps (smooth arcs, horizontal lines, reachable waves)
        val patternType = Random.nextInt(4)
        when (patternType) {
            0 -> {
                // Arc sequence of 3 coins entering the gap
                for (i in 1..3) {
                    val cx = anchorX - (i * 0.08f)
                    if (cx > 0.35f) {
                        val offsetY = sin(i * 0.8f) * 0.045f
                        coins.add(
                            FlappyCoin(
                                id = Random.nextLong(),
                                xRatio = cx,
                                yRatio = (gapCenterY + offsetY).coerceIn(0.18f, groundYRatio - 0.12f)
                            )
                        )
                    }
                }
            }
            1 -> {
                // Trailing trail of 3 coins behind the pipe
                for (i in 1..3) {
                    val cx = anchorX + 0.14f + (i * 0.075f)
                    coins.add(
                        FlappyCoin(
                            id = Random.nextLong(),
                            xRatio = cx,
                            yRatio = (gapCenterY + ((i - 2) * 0.025f)).coerceIn(0.20f, groundYRatio - 0.12f)
                        )
                    )
                }
            }
            2 -> {
                // Pair of coins inside and just after the gap
                coins.add(
                    FlappyCoin(
                        id = Random.nextLong(),
                        xRatio = anchorX + 0.02f,
                        yRatio = (gapTopY + gapH * 0.35f).coerceIn(0.18f, groundYRatio - 0.12f)
                    )
                )
                coins.add(
                    FlappyCoin(
                        id = Random.nextLong(),
                        xRatio = anchorX + 0.11f,
                        yRatio = (gapTopY + gapH * 0.65f).coerceIn(0.18f, groundYRatio - 0.12f)
                    )
                )
            }
            3 -> {
                // Gentle sinusoidal wave of 4 coins along flight path
                for (i in 1..4) {
                    val cx = anchorX + 0.13f + (i * 0.07f)
                    val waveY = gapCenterY + (sin(i * 1.1f) * 0.05f)
                    coins.add(
                        FlappyCoin(
                            id = Random.nextLong(),
                            xRatio = cx,
                            yRatio = waveY.coerceIn(0.18f, groundYRatio - 0.12f)
                        )
                    )
                }
            }
        }
    }

    // --- Core Game Loop ---
    LaunchedEffect(runKey, gameState) {
        if (gameState == FlappyState.READY) {
            var readyTicks = 0L
            while (gameState == FlappyState.READY) {
                delay(16L)
                readyTicks++
                // Gentle floating bob in ready mode
                birdYRatio = 0.44f + (sin(readyTicks * 0.08f) * 0.02f)
                birdRotation = sin(readyTicks * 0.08f) * 5f

                // Drifting background
                bgCloudsOffset = (bgCloudsOffset + 0.0006f) % 1f
                bgCityOffset = (bgCityOffset + 0.0012f) % 1f
                bgGroundOffset = (bgGroundOffset + 0.003f) % 1f
            }
        }

        if (gameState == FlappyState.PLAYING) {
            var tickCounter = 0L
            while (gameState == FlappyState.PLAYING) {
                delay(16L) // ~60 FPS
                tickCounter++

                if (wingFlapFrame > 0) wingFlapFrame--

                // 1. Physics: Gravity & Motion
                birdVelocity = (birdVelocity + gravity).coerceIn(-0.016f, 0.019f)
                birdYRatio = (birdYRatio + birdVelocity).coerceIn(0.01f, groundYRatio + 0.02f)

                // Rotation: tilts up on flap, tilts down smoothly on drop
                val targetAngle = if (birdVelocity < 0f) {
                    (birdVelocity / flapImpulse * -24f).coerceIn(-25f, 0f)
                } else {
                    (birdVelocity / 0.019f * 65f).coerceIn(0f, 65f)
                }
                birdRotation += (targetAngle - birdRotation) * 0.22f

                // 2. Parallax background scrolling
                bgCloudsOffset = (bgCloudsOffset + obstacleSpeed * 0.15f) % 1f
                bgCityOffset = (bgCityOffset + obstacleSpeed * 0.4f) % 1f
                bgGroundOffset = (bgGroundOffset + obstacleSpeed) % 1f

                // 3. Ground & Ceiling Collision
                if (!isInvulnerable) {
                    if (birdYRatio >= (groundYRatio - birdRadiusRatio * 0.8f)) {
                        playSfx(ToneGenerator.TONE_PROP_NACK, 200)
                        triggerHaptic(100L)
                        gameState = FlappyState.GAME_OVER
                        break
                    }
                }

                // 4. Update Pillars & Collision Check
                val pillarIterator = pillars.iterator()
                while (pillarIterator.hasNext()) {
                    val p = pillarIterator.next()
                    p.xRatio -= obstacleSpeed

                    // Score increment on clearing gate
                    if (!p.passed && p.xRatio < birdXRatio) {
                        p.passed = true
                        gatesCleared += 1
                        score += 1
                        playSfx(ToneGenerator.TONE_PROP_ACK, 40)
                    }

                    // Precise hitbox check for smaller bird
                    val pLeft = p.xRatio
                    val pRight = p.xRatio + 0.13f
                    val birdHitboxX1 = birdXRatio - 0.018f
                    val birdHitboxX2 = birdXRatio + 0.018f

                    if (!isInvulnerable && birdHitboxX2 > pLeft && birdHitboxX1 < pRight) {
                        val topThreshold = p.topHeightRatio + 0.008f
                        val bottomThreshold = (p.topHeightRatio + p.gapRatio) - 0.008f

                        val hitTop = (birdYRatio - 0.016f) < topThreshold
                        val hitBottom = (birdYRatio + 0.016f) > bottomThreshold

                        if (hitTop || hitBottom) {
                            playSfx(ToneGenerator.TONE_PROP_NACK, 220)
                            triggerHaptic(120L)
                            gameState = FlappyState.GAME_OVER
                            break
                        }
                    }

                    // Remove off-screen pipes
                    if (p.xRatio < -0.22f) {
                        pillarIterator.remove()
                    }
                }

                if (gameState == FlappyState.GAME_OVER) break

                // 5. Update Coins (High Frequency Collectibles)
                val coinIterator = coins.iterator()
                while (coinIterator.hasNext()) {
                    val c = coinIterator.next()
                    c.xRatio -= obstacleSpeed

                    // Collection Check (slightly smaller coin and bird hitboxes)
                    if (!c.collected) {
                        val dx = abs(c.xRatio - birdXRatio)
                        val dy = abs(c.yRatio - birdYRatio)
                        if (dx < 0.040f && dy < 0.042f) {
                            c.collected = true
                            coinsCount += 1
                            score += 10
                            playSfx(ToneGenerator.TONE_CDMA_PIP, 45)
                            triggerHaptic(15L)

                            // Golden sparkle particles
                            repeat(6) {
                                val angle = Random.nextFloat() * 6.28f
                                val spd = 0.003f + (Random.nextFloat() * 0.0035f)
                                particles.add(
                                    FlappyParticle(
                                        xRatio = c.xRatio,
                                        yRatio = c.yRatio,
                                        vx = kotlin.math.cos(angle) * spd,
                                        vy = kotlin.math.sin(angle) * spd,
                                        color = if (it % 2 == 0) NeonGold else Color(0xFFFFEB3B),
                                        sizePx = 5.5f,
                                        life = 18
                                    )
                                )
                            }

                            // Floating +10 indicator
                            floatingTexts.add(
                                FloatingText(
                                    xRatio = c.xRatio,
                                    yRatio = c.yRatio,
                                    text = "+10"
                                )
                            )
                        }
                    }

                    if (c.collected || c.xRatio < -0.15f) {
                        coinIterator.remove()
                    }
                }

                // 6. Update Particles
                val partIter = particles.iterator()
                while (partIter.hasNext()) {
                    val part = partIter.next()
                    part.xRatio += part.vx
                    part.yRatio += part.vy
                    part.life--
                    part.alpha = (part.life / 20f).coerceIn(0f, 1f)
                    if (part.life <= 0) partIter.remove()
                }

                // 7. Update Floating Text Popups
                val floatIter = floatingTexts.iterator()
                while (floatIter.hasNext()) {
                    val ft = floatIter.next()
                    ft.yRatio -= 0.0018f
                    ft.life--
                    ft.alpha = (ft.life / 25f).coerceIn(0f, 1f)
                    if (ft.life <= 0) floatIter.remove()
                }

                // 8. Obstacle & Coin Spawner
                if (tickCounter % spawnIntervalTicks == 0L) {
                    val maxTopH = groundYRatio - pillarGapRatio - 0.12f
                    val topH = (0.13f + (Random.nextFloat() * (maxTopH - 0.13f))).coerceIn(0.12f, maxTopH)

                    val newPillarX = 1.12f
                    pillars.add(
                        FlappyPillar(
                            id = Random.nextLong(),
                            xRatio = newPillarX,
                            topHeightRatio = topH,
                            gapRatio = pillarGapRatio
                        )
                    )

                    // Spawn rich, reachable coin patterns for this pillar section
                    spawnCoinPattern(
                        anchorX = newPillarX,
                        gapTopY = topH,
                        gapH = pillarGapRatio
                    )
                }
            }
        }
    }

    // --- FULL-SCREEN GAMEPLAY LAYOUT ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberDarkBg)
            .pointerInput(Unit) {
                detectTapGestures { flap() }
            }
    ) {
        // 1. Full Screen Canvas (Game World)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height
            val groundY = canvasH * groundYRatio

            // 1. Sky Gradient Background
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1976D2), // Radiant Sky Blue
                        Color(0xFF42A5F5),
                        Color(0xFF90CAF9),
                        Color(0xFFE1F5FE)
                    ),
                    startY = 0f,
                    endY = groundY
                ),
                size = Size(canvasW, groundY)
            )

            // Sun glow
            drawCircle(
                color = Color(0xFFFFF9C4).copy(alpha = 0.5f),
                radius = 44.dp.toPx(),
                center = Offset(canvasW * 0.82f, canvasH * 0.16f)
            )
            drawCircle(
                color = Color(0xFFFFF176),
                radius = 26.dp.toPx(),
                center = Offset(canvasW * 0.82f, canvasH * 0.16f)
            )

            // 2. Parallax Drifting Clouds
            val cloudBaseY = canvasH * 0.25f
            for (i in -1..2) {
                val cloudX = ((i * 0.45f) - bgCloudsOffset + 1f) % 1.5f * canvasW
                drawCircle(
                    color = Color.White.copy(alpha = 0.75f),
                    radius = 26.dp.toPx(),
                    center = Offset(cloudX, cloudBaseY)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.75f),
                    radius = 35.dp.toPx(),
                    center = Offset(cloudX + 26.dp.toPx(), cloudBaseY - 6.dp.toPx())
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.75f),
                    radius = 24.dp.toPx(),
                    center = Offset(cloudX + 50.dp.toPx(), cloudBaseY + 2.dp.toPx())
                )
            }

            // 3. Parallax Skyline Silhouettes
            val cityBaseY = groundY - 22.dp.toPx()
            for (i in -1..4) {
                val cityX = ((i * 0.3f) - bgCityOffset + 1.2f) % 1.5f * canvasW
                drawRect(
                    color = Color(0xFF90CAF9).copy(alpha = 0.45f),
                    topLeft = Offset(cityX, cityBaseY - 32.dp.toPx()),
                    size = Size(30.dp.toPx(), 40.dp.toPx())
                )
                drawRect(
                    color = Color(0xFF81D4FA).copy(alpha = 0.5f),
                    topLeft = Offset(cityX + 22.dp.toPx(), cityBaseY - 46.dp.toPx()),
                    size = Size(24.dp.toPx(), 54.dp.toPx())
                )
            }

            // 4. Draw Pipes (Obstacles)
            val pWidth = canvasW * 0.13f
            val capH = 22.dp.toPx()
            val capExtend = 3.5.dp.toPx()

            pillars.forEach { p ->
                val px = p.xRatio * canvasW
                val topH = p.topHeightRatio * canvasH
                val bottomY = (p.topHeightRatio + p.gapRatio) * canvasH
                val bottomH = groundY - bottomY

                // TOP PILLAR SHAFT
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF2E7D32), Color(0xFF4CAF50), Color(0xFF81C784), Color(0xFF1B5E20)),
                        startX = px,
                        endX = px + pWidth
                    ),
                    topLeft = Offset(px, 0f),
                    size = Size(pWidth, topH),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                // Top Pillar Cap
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF1B5E20), Color(0xFF66BB6A), Color(0xFF1B5E20)),
                        startX = px - capExtend,
                        endX = px + pWidth + capExtend
                    ),
                    topLeft = Offset(px - capExtend, topH - capH),
                    size = Size(pWidth + (capExtend * 2), capH),
                    cornerRadius = CornerRadius(5f, 5f)
                )

                // BOTTOM PILLAR SHAFT
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF2E7D32), Color(0xFF4CAF50), Color(0xFF81C784), Color(0xFF1B5E20)),
                        startX = px,
                        endX = px + pWidth
                    ),
                    topLeft = Offset(px, bottomY),
                    size = Size(pWidth, bottomH.coerceAtLeast(0f)),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                // Bottom Pillar Cap
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF1B5E20), Color(0xFF66BB6A), Color(0xFF1B5E20)),
                        startX = px - capExtend,
                        endX = px + pWidth + capExtend
                    ),
                    topLeft = Offset(px - capExtend, bottomY),
                    size = Size(pWidth + (capExtend * 2), capH),
                    cornerRadius = CornerRadius(5f, 5f)
                )
            }

            // 5. Draw Collectible Gold Coins (Smaller, elegant coins matching reference)
            val coinRadius = 8.5.dp.toPx()
            coins.forEach { c ->
                if (!c.collected) {
                    val cx = c.xRatio * canvasW
                    val cy = c.yRatio * canvasH

                    // Outer gold gradient body
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFFF59D), Color(0xFFFFD54F), Color(0xFFFF8F00)),
                            center = Offset(cx - 2f, cy - 2f),
                            radius = coinRadius
                        ),
                        radius = coinRadius,
                        center = Offset(cx, cy)
                    )
                    // Coin outer rim border
                    drawCircle(
                        color = Color(0xFFE65100),
                        radius = coinRadius,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                    // Inner engraved ring
                    drawCircle(
                        color = Color(0xFFFFECB3).copy(alpha = 0.85f),
                        radius = coinRadius * 0.62f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1f.dp.toPx())
                    )
                    // Inner center emblem star
                    drawCircle(
                        color = Color(0xFFFF8F00),
                        radius = coinRadius * 0.22f,
                        center = Offset(cx, cy)
                    )
                }
            }

            // 6. Draw Scrolling Ground Layer
            val groundH = canvasH - groundY
            // Grass upper strip
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF7CB342), Color(0xFF558B2F)),
                    startY = groundY,
                    endY = groundY + 12.dp.toPx()
                ),
                topLeft = Offset(0f, groundY),
                size = Size(canvasW, 12.dp.toPx())
            )
            // Dirt lower stratum
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF8D6E63), Color(0xFF5D4037), Color(0xFF3E2723)),
                    startY = groundY + 12.dp.toPx(),
                    endY = canvasH
                ),
                topLeft = Offset(0f, groundY + 12.dp.toPx()),
                size = Size(canvasW, groundH - 12.dp.toPx())
            )

            // Ground scrolling texture dashes
            val markSpacing = 24.dp.toPx()
            val totalMarks = (canvasW / markSpacing).toInt() + 2
            for (i in 0 until totalMarks) {
                val markX = ((i * markSpacing) - (bgGroundOffset * canvasW) % markSpacing + canvasW) % canvasW
                drawRoundRect(
                    color = Color(0xFF4E342E),
                    topLeft = Offset(markX, groundY + 15.dp.toPx()),
                    size = Size(12.dp.toPx(), 3.5.dp.toPx()),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }

            // 7. Draw Flappy Bird Character (Slightly smaller, perfectly scaled)
            val birdX = birdXRatio * canvasW
            val birdY = birdYRatio * canvasH
            val birdRadius = 12.5.dp.toPx()

            rotate(degrees = birdRotation, pivot = Offset(birdX, birdY)) {
                val wingAngle = if (wingFlapFrame > 0) -28f else 12f

                // Bird Body
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFFF176), Color(0xFFFFCA28), Color(0xFFFFA000)),
                        center = Offset(birdX - 3f, birdY - 3f),
                        radius = birdRadius
                    ),
                    radius = birdRadius,
                    center = Offset(birdX, birdY)
                )
                // Body outline
                drawCircle(
                    color = Color(0xFFE65100),
                    radius = birdRadius,
                    center = Offset(birdX, birdY),
                    style = Stroke(width = 1.3.dp.toPx())
                )

                // Creamy belly
                drawRoundRect(
                    color = Color(0xFFFFF9C4),
                    topLeft = Offset(birdX - birdRadius * 0.7f, birdY + birdRadius * 0.1f),
                    size = Size(birdRadius * 1.05f, birdRadius * 0.72f),
                    cornerRadius = CornerRadius(birdRadius * 0.5f, birdRadius * 0.5f)
                )

                // Tail feathers
                val tailPath = Path().apply {
                    moveTo(birdX - birdRadius, birdY - 1.5f)
                    lineTo(birdX - birdRadius - 7.5.dp.toPx(), birdY - 6.dp.toPx())
                    lineTo(birdX - birdRadius - 6.dp.toPx(), birdY + 3.dp.toPx())
                    close()
                }
                drawPath(tailPath, color = Color(0xFFFF8F00))

                // Flapping Wing
                rotate(degrees = wingAngle, pivot = Offset(birdX - 4.dp.toPx(), birdY)) {
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFFFFF59D), Color(0xFFFFB300))
                        ),
                        topLeft = Offset(birdX - 10.dp.toPx(), birdY - 4.5.dp.toPx()),
                        size = Size(11.5.dp.toPx(), 8.dp.toPx()),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                    drawRoundRect(
                        color = Color(0xFFE65100),
                        topLeft = Offset(birdX - 10.dp.toPx(), birdY - 4.5.dp.toPx()),
                        size = Size(11.5.dp.toPx(), 8.dp.toPx()),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // Eye
                val eyeCenterX = birdX + 5.dp.toPx()
                val eyeCenterY = birdY - 3.8.dp.toPx()
                val eyeRadius = 4.dp.toPx()

                // White sclera
                drawCircle(
                    color = Color.White,
                    radius = eyeRadius,
                    center = Offset(eyeCenterX, eyeCenterY)
                )
                drawCircle(
                    color = Color(0xFF263238),
                    radius = eyeRadius,
                    center = Offset(eyeCenterX, eyeCenterY),
                    style = Stroke(width = 0.9.dp.toPx())
                )
                // Black pupil
                drawCircle(
                    color = Color.Black,
                    radius = 2.dp.toPx(),
                    center = Offset(eyeCenterX + 0.9.dp.toPx(), eyeCenterY)
                )
                // White specular catchlight
                drawCircle(
                    color = Color.White,
                    radius = 0.8.dp.toPx(),
                    center = Offset(eyeCenterX + 1.6.dp.toPx(), eyeCenterY - 0.8.dp.toPx())
                )

                // Rosy Cheek
                drawCircle(
                    color = Color(0xFFFF5252).copy(alpha = 0.45f),
                    radius = 2.4.dp.toPx(),
                    center = Offset(birdX + 2.dp.toPx(), birdY + 3.8.dp.toPx())
                )

                // Beak
                val beakPath = Path().apply {
                    moveTo(birdX + birdRadius - 1.5.dp.toPx(), birdY - 1.5.dp.toPx())
                    lineTo(birdX + birdRadius + 8.5.dp.toPx(), birdY + 1.5.dp.toPx())
                    lineTo(birdX + birdRadius - 0.8.dp.toPx(), birdY + 5.5.dp.toPx())
                    close()
                }
                drawPath(beakPath, color = Color(0xFFFF6D00))
                drawLine(
                    color = Color(0xFFBF360C),
                    start = Offset(birdX + birdRadius - 1.dp.toPx(), birdY + 2.dp.toPx()),
                    end = Offset(birdX + birdRadius + 7.5.dp.toPx(), birdY + 2.dp.toPx()),
                    strokeWidth = 1f.dp.toPx()
                )
            }

            // 8. 5-Second Invulnerability Shield Effect around Bird
            if (isInvulnerable) {
                val shieldRadius = birdRadius * 2.3f
                // Glowing pulsating protective bubble
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF00E5FF).copy(alpha = 0.35f),
                            Color(0xFF18FFFF).copy(alpha = 0.18f),
                            Color(0xFF00B0FF).copy(alpha = 0.05f)
                        ),
                        center = Offset(birdX, birdY),
                        radius = shieldRadius
                    ),
                    radius = shieldRadius,
                    center = Offset(birdX, birdY)
                )
                // Shield outer energy ring
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = 0.85f),
                    radius = shieldRadius,
                    center = Offset(birdX, birdY),
                    style = Stroke(width = 2.dp.toPx())
                )
                // Concentric inner ring
                drawCircle(
                    color = Color.White.copy(alpha = 0.6f),
                    radius = shieldRadius * 0.75f,
                    center = Offset(birdX, birdY),
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // 9. Floating Particles
            particles.forEach { part ->
                drawCircle(
                    color = part.color.copy(alpha = part.alpha),
                    radius = part.sizePx,
                    center = Offset(part.xRatio * canvasW, part.yRatio * canvasH)
                )
            }
        }

        // 2. MINIMAL GAME HUD (NO dashboard cards, NO large titles)
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
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape)
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
                    color = Color.Black.copy(alpha = 0.5f),
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

            // SMALL COIN COUNTER HUD (Exact reference style: 🪙 count)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.38f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag("flappy_coin_counter")
            ) {
                // Small Gold Coin Icon
                Box(
                    modifier = Modifier
                        .size(17.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFFFFF59D), Color(0xFFFFD54F), Color(0xFFFF8F00))
                            ),
                            CircleShape
                        )
                        .border(1.dp, Color(0xFFE65100), CircleShape)
                )
                // Collected Coin Count (Updates immediately on collection)
                Text(
                    text = "$coinsCount",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // Ready state prompt (subtle floating prompt)
        if (gameState == FlappyState.READY) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 50.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "TAP SCREEN TO FLY",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }

    // --- GAME OVER SCREEN ---
    if (gameState == FlappyState.GAME_OVER && !isSubmitting) {
        Dialog(onDismissRequest = {}) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                border = androidx.compose.foundation.BorderStroke(2.dp, NeonRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .testTag("flappy_game_over_card")
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Game Over Header
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(NeonRed.copy(alpha = 0.2f), CircleShape)
                            .border(1.5.dp, NeonRed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Game Over",
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

                    // Results Summary
                    Surface(
                        color = CyberSurfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Coins collected
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Coins Collected", color = TextSecondary, fontSize = 13.sp)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .background(NeonGold, CircleShape)
                                            .border(1.dp, Color(0xFFFFA000), CircleShape)
                                    )
                                    Text(
                                        text = "$coinsCount",
                                        color = NeonGold,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }

                            HorizontalDivider(color = CyberBorder, thickness = 0.8.dp)

                            // Final Score
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Final Score", color = TextSecondary, fontSize = 13.sp)
                                Text(
                                    text = "$score pts",
                                    color = NeonCyan,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }

                            HorizontalDivider(color = CyberBorder, thickness = 0.8.dp)

                            // Best Record
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Best Record", color = TextSecondary, fontSize = 13.sp)
                                Text(
                                    text = "${maxOf(bestScore, score)} pts",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // 1. WATCH AD TO CONTINUE (Rewinded / Revived at EXACT same position with 5s shield)
                    val adsLeft = AdMobConfig.MAX_REWARD_ADS_PER_SESSION - adsWatchedCount
                    if (adsLeft > 0) {
                        Button(
                            onClick = { handleWatchAd() },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("watch_ad_continue_button")
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
                                    text = "WATCH AD TO CONTINUE ($adsLeft/3 left)",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // 2. SUBMIT FINAL SCORE (Submits score once to existing leaderboard)
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

                    // 3. PLAY AGAIN
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

    // Rewarded Ad Simulation Dialog (Fallback for development/emulator)
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

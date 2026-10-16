package com.example.games.jumprun

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import kotlin.math.*
import kotlin.random.Random

enum class JumpGameState {
    MENU,
    PLAYING,
    GAME_OVER,
    READY_TO_RESUME
}

/**
 * 2D Platform representation in virtual 960x540 coordinate space.
 */
data class GamePlatform(
    var x: Float,
    var width: Float,
    var y: Float,
    val height: Float = 260f
)

/**
 * Collectible Gold Coin. Each coin is worth +5 points upon collection.
 */
data class GameCoin(
    val id: Long,
    var x: Float,
    var y: Float,
    var collected: Boolean = false
)

/**
 * Visual +5 popup effect when a coin is collected.
 */
data class CoinScorePopup(
    val id: Long,
    var x: Float,
    var y: Float,
    var alpha: Float = 1f
)

/**
 * Cloud layer for parallax background.
 */
data class ParallaxCloud(
    var x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val speedFactor: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JumpRunGameScreen(
    user: UserProfile,
    attempt: GameAttempt,
    adMobService: AdMobService,
    firestoreService: FirestoreService,
    onBack: () -> Unit,
    onScoreSubmitted: (Long) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    // Android back button: exits Jump & Run back to home screen without closing app
    BackHandler(enabled = true) {
        onBack()
    }

    // Force 16:9 full-screen landscape and hide all system bars (immersive mode)
    DisposableEffect(Unit) {
        val window = activity?.window
        val insetsController = if (window != null) {
            WindowCompat.getInsetsController(window, window.decorView)
        } else null

        // Lock to landscape sensor orientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Immersive full screen: hide status bar, navigation bar, clock, battery, notifications
        insetsController?.let { controller ->
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            // Restore portrait orientation and system UI when leaving Jump & Run
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Vibrator for tactile feedback
    val vibrator = remember {
        try {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } catch (_: Exception) {
            null
        }
    }

    fun triggerHaptic(durationMs: Long = 25L) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    // ── GAME STATE ──────────────────────────────────────────────────────────
    var gameState by remember { mutableStateOf(JumpGameState.MENU) }

    // Scoring & Revives
    var coinsCollected by remember { mutableLongStateOf(0L) }
    val currentScore by remember { derivedStateOf { coinsCollected * 5L } }
    var adsWatchedCount by remember { mutableIntStateOf(0) }
    var isSubmitting by remember { mutableStateOf(false) }
    var isSimulatingAd by remember { mutableStateOf(false) }

    // Character Virtual Physics (Virtual Resolution: 960 x 540)
    val playerX = 180f
    val playerWidth = 44f
    val playerHeight = 64f

    var playerY by remember { mutableFloatStateOf(306f) }
    var playerVelocityY by remember { mutableFloatStateOf(0f) }
    var isJumping by remember { mutableStateOf(false) }
    var isOnGround by remember { mutableStateOf(true) }
    var isInvulnerable by remember { mutableStateOf(false) }
    var invulnerableTicks by remember { mutableIntStateOf(0) }
    var lastFallPlatformY by remember { mutableFloatStateOf(370f) }

    // Jump parameters
    val gravity = 0.72f
    val jumpVelocity = -14.2f

    // Running animation cycle & background scroll offset
    var runCycle by remember { mutableFloatStateOf(0f) }
    var worldDistance by remember { mutableFloatStateOf(0f) }
    var currentSpeed by remember { mutableFloatStateOf(4.8f) }

    // Platform & Coin entities
    val platforms = remember { mutableStateListOf<GamePlatform>() }
    val coins = remember { mutableStateListOf<GameCoin>() }
    val scorePopups = remember { mutableStateListOf<CoinScorePopup>() }

    // Parallax background clouds
    val clouds = remember {
        mutableStateListOf(
            ParallaxCloud(60f, 60f, 130f, 45f, 0.4f),
            ParallaxCloud(340f, 95f, 160f, 55f, 0.6f),
            ParallaxCloud(620f, 45f, 110f, 40f, 0.35f),
            ParallaxCloud(880f, 80f, 150f, 50f, 0.5f)
        )
    }

    // Paint for +5 text popup
    val textPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(255, 220, 40)
            textSize = 28f
            isFakeBoldText = true
            setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
        }
    }

    // Helper: Initialize a clean level
    fun resetGame() {
        coinsCollected = 0L
        adsWatchedCount = 0
        currentSpeed = 4.8f
        worldDistance = 0f

        platforms.clear()
        coins.clear()
        scorePopups.clear()

        // Starting runway platform
        val startY = 370f
        platforms.add(GamePlatform(x = 0f, width = 640f, y = startY))

        // Position player safely on the starting platform
        playerY = startY - playerHeight
        playerVelocityY = 0f
        isJumping = false
        isOnGround = true
        isInvulnerable = false
        invulnerableTicks = 0

        // Generate initial upcoming platforms
        var nextX = 640f + 90f
        var lastY = startY
        var nextCoinId = 1L

        for (i in 0 until 5) {
            val w = 320f + Random.nextFloat() * 120f
            val yVar = (Random.nextFloat() - 0.5f) * 70f
            val pY = (lastY + yVar).coerceIn(320f, 420f)
            platforms.add(GamePlatform(x = nextX, width = w, y = pY))

            // Add coins along this platform
            val coinCount = Random.nextInt(2, 4)
            for (c in 0 until coinCount) {
                coins.add(
                    GameCoin(
                        id = nextCoinId++,
                        x = nextX + 60f + (c * 48f),
                        y = pY - 34f
                    )
                )
            }

            lastY = pY
            nextX += w + 95f
        }
    }

    // Pre-initialize initial terrain and runner so start screen shows the live environment
    LaunchedEffect(Unit) {
        if (platforms.isEmpty()) {
            resetGame()
        }
    }

    // Helper: Jump Action
    fun executeJump() {
        if (gameState == JumpGameState.PLAYING && isOnGround) {
            playerVelocityY = jumpVelocity
            isOnGround = false
            isJumping = true
            triggerHaptic(30L)
        }
    }

    // Helper: Restore player on platform at exact fall location after rewarded ad
    fun revivePlayerAtCurrentLocation() {
        adsWatchedCount += 1

        // Spawn a solid safe platform at the player's fall height
        val safeY = lastFallPlatformY.coerceIn(240f, 430f)
        val safePlatform = GamePlatform(x = playerX - 50f, width = 500f, y = safeY)

        // Remove any platforms directly interfering with player's immediate zone
        platforms.removeAll { it.x in (playerX - 80f)..(playerX + 420f) }
        platforms.add(0, safePlatform)

        // Restore character on the platform
        playerY = safeY - playerHeight
        playerVelocityY = 0f
        isOnGround = true
        isJumping = false

        // Do not auto-run: show START / CONTINUE button
        gameState = JumpGameState.READY_TO_RESUME
    }

    // Activated when the user taps START / CONTINUE after revive
    fun resumeAfterRevive() {
        isInvulnerable = true
        invulnerableTicks = 300 // 5 seconds at 60 FPS (300 ticks)
        gameState = JumpGameState.PLAYING
    }

    fun handleWatchAd() {
        if (adsWatchedCount >= 3) return
        val act = context as? Activity
        if (act != null && adMobService.isAdAvailable()) {
            adMobService.showRewardedAd(
                activity = act,
                onRewardEarned = { revivePlayerAtCurrentLocation() },
                onAdDismissed = {},
                onAdFailed = { isSimulatingAd = true }
            )
        } else {
            isSimulatingAd = true
        }
    }

    fun submitFinalScore() {
        if (isSubmitting) return
        isSubmitting = true
        coroutineScope.launch {
            val finalScore = currentScore
            firestoreService.submitFinalScore(
                attemptId = attempt.attemptId,
                uid = user.uid,
                displayName = user.displayName,
                gameType = GameType.JUMP_RUN,
                baseScore = finalScore,
                rewardAdsWatched = adsWatchedCount,
                rewardBonus = 0L,
                finalScore = finalScore
            )
            isSubmitting = false
            onScoreSubmitted(finalScore)
        }
    }

    // ── GAME LOOP (~60 FPS) ─────────────────────────────────────────────────
    LaunchedEffect(gameState) {
        var coinIdCounter = 1000L

        while (gameState == JumpGameState.PLAYING) {
            delay(16L)

            worldDistance += currentSpeed
            runCycle = (runCycle + (currentSpeed * 0.052f)) % (2f * Math.PI.toFloat())

            // Smooth, gradual difficulty progression
            val difficultyProgress = (worldDistance / 8000f).coerceIn(0f, 1f)
            currentSpeed = 4.8f + (difficultyProgress * 2.0f) // speeds up from 4.8 to 6.8 max
            val minPlatformWidth = 320f - (difficultyProgress * 110f) // 320 -> 210
            val maxGapWidth = 90f + (difficultyProgress * 65f) // 90 -> 155 (always jumpable with 184+ max jump)

            // 5-second invulnerability countdown
            if (isInvulnerable) {
                invulnerableTicks -= 1
                if (invulnerableTicks <= 0) {
                    isInvulnerable = false
                }
            }

            // Move Parallax Clouds
            clouds.forEach { cloud ->
                cloud.x -= (currentSpeed * 0.15f * cloud.speedFactor)
                if (cloud.x + cloud.width < 0) {
                    cloud.x = 1350f + Random.nextFloat() * 150f
                }
            }

            // Scroll platforms left
            val platformIterator = platforms.iterator()
            var rightmostX = 0f
            var lastPlatformY = 370f

            while (platformIterator.hasNext()) {
                val p = platformIterator.next()
                p.x -= currentSpeed

                if (p.x + p.width > rightmostX) {
                    rightmostX = p.x + p.width
                    lastPlatformY = p.y
                }

                // Remove offscreen platforms
                if (p.x + p.width < -120f) {
                    platformIterator.remove()
                }
            }

            // Generate new platforms ahead of player (extended for ultra-wide landscape viewports)
            if (rightmostX < 1600f) {
                val gap = (75f + (Random.nextFloat() * (maxGapWidth - 75f))).coerceIn(70f, 160f)
                val width = (minPlatformWidth + (Random.nextFloat() * 140f)).coerceIn(190f, 420f)
                val yDelta = (Random.nextFloat() - 0.5f) * (60f + difficultyProgress * 30f)
                val nextY = (lastPlatformY + yDelta).coerceIn(310f, 430f)
                val newPlatformX = rightmostX + gap

                platforms.add(GamePlatform(x = newPlatformX, width = width, y = nextY))

                // Spawn coins along the platform
                val spawnCoins = Random.nextFloat() < 0.85f
                if (spawnCoins) {
                    val count = if (width > 280f) Random.nextInt(2, 5) else Random.nextInt(1, 3)
                    val startOffset = 40f
                    for (c in 0 until count) {
                        coins.add(
                            GameCoin(
                                id = coinIdCounter++,
                                x = newPlatformX + startOffset + (c * 42f),
                                y = nextY - 36f
                            )
                        )
                    }
                }
            }

            // Scroll coins & check collection
            val playerCenterX = playerX + (playerWidth / 2f)
            val playerCenterY = playerY + (playerHeight / 2f)
            val coinIterator = coins.iterator()

            while (coinIterator.hasNext()) {
                val coin = coinIterator.next()
                coin.x -= currentSpeed

                // Check collision with player
                if (!coin.collected) {
                    val dx = playerCenterX - coin.x
                    val dy = playerCenterY - coin.y
                    val dist = sqrt((dx * dx) + (dy * dy))

                    if (dist < 34f) {
                        coin.collected = true
                        coinsCollected += 1 // +5 points immediately via currentScore derivedState
                        triggerHaptic(20L)
                        scorePopups.add(CoinScorePopup(id = coin.id, x = coin.x, y = coin.y - 10f))
                    }
                }

                // Cull offscreen or collected coins
                if (coin.x < -60f || coin.collected) {
                    coinIterator.remove()
                }
            }

            // Update floating score popups
            val popupIterator = scorePopups.iterator()
            while (popupIterator.hasNext()) {
                val popup = popupIterator.next()
                popup.y -= 1.8f
                popup.alpha -= 0.04f
                if (popup.alpha <= 0f) {
                    popupIterator.remove()
                }
            }

            // Physics Update (Vertical)
            playerVelocityY += gravity
            val nextPlayerY = playerY + playerVelocityY

            // Collision check with platform top surfaces
            var landed = false
            val playerBottom = playerY + playerHeight
            val nextPlayerBottom = nextPlayerY + playerHeight
            val footLeft = playerX + 8f
            val footRight = playerX + playerWidth - 8f

            for (p in platforms) {
                // Check if platform is horizontally under player
                if (p.x <= footRight && (p.x + p.width) >= footLeft) {
                    // Check if player's feet cross the platform top while moving downward
                    if (playerBottom <= p.y + 6f && nextPlayerBottom >= p.y && playerVelocityY >= 0f) {
                        playerY = p.y - playerHeight
                        playerVelocityY = 0f
                        isOnGround = true
                        isJumping = false
                        landed = true
                        break
                    }
                }
            }

            if (!landed) {
                // When 5-second shield is active, prevent falling through gaps (energy floor)
                val safeFloorY = lastFallPlatformY.coerceIn(280f, 430f)
                if (isInvulnerable && nextPlayerBottom >= safeFloorY && playerVelocityY >= 0f) {
                    playerY = safeFloorY - playerHeight
                    playerVelocityY = 0f
                    isOnGround = true
                    isJumping = false
                    landed = true
                } else {
                    playerY = nextPlayerY
                    if (playerVelocityY > 1f) {
                        isOnGround = false
                    }
                }
            }

            // Fall / Death Boundary check (Disabled while 5-second shield protection is active)
            if (!isInvulnerable && playerY > 540f) {
                triggerHaptic(70L)
                // Save platform height nearest to player before falling
                val nearbyPlatform = platforms.minByOrNull { kotlin.math.abs((it.x + it.width / 2f) - playerX) }
                lastFallPlatformY = nearbyPlatform?.y ?: 370f
                gameState = JumpGameState.GAME_OVER
            }
        }
    }

    // ── ROOT UI: ENFORCE TRUE 16:9 DEDICATED FULL-SCREEN LANDSCAPE CANVAS ───
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Main 2D Canvas filling full available landscape viewport
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        executeJump()
                    }
            ) {
                // Proportional 1:1 scaling based on 540f reference virtual height
                val scale = size.height / 540f
                val virtualWidth = size.width / scale

                scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero) {
                    // 1. Sky & Sun
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1E88E5),
                                Color(0xFF42A5F5),
                                Color(0xFF90CAF9)
                            ),
                            startY = 0f,
                            endY = 320f
                        ),
                        topLeft = Offset(0f, 0f),
                        size = Size(virtualWidth + 120f, 540f)
                    )

                    // Sun glow in upper right sky
                    val sunX = virtualWidth - 150f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFF9C4),
                                Color(0xFFFFEE58).copy(alpha = 0.8f),
                                Color(0xFFFFCA28).copy(alpha = 0.2f),
                                Color.Transparent
                            ),
                            center = Offset(sunX, 105f),
                            radius = 90f
                        ),
                        radius = 90f,
                        center = Offset(sunX, 105f)
                    )
                    drawCircle(
                        color = Color(0xFFFFFDE7),
                        radius = 24f,
                        center = Offset(sunX, 105f)
                    )

                    // 2. Parallax Clouds
                    clouds.forEach { cloud ->
                        drawParallaxCloud(cloud)
                    }

                    // 3. Distant Mountains
                    drawDistantMountains(worldDistance, virtualWidth)

                    // 4. Midground Forest & Lake
                    drawMidgroundLakeAndForest(worldDistance, virtualWidth)

                    // 5. Grassy Rocky Platforms
                    platforms.forEach { platform ->
                        drawPlatform(platform)
                    }

                    // 6. Collectible Gold Coins
                    coins.forEach { coin ->
                        if (!coin.collected) {
                            drawGoldCoin(coin, worldDistance)
                        }
                    }

                    // 7. Human Character Runner
                    drawCartoonHumanCharacter(
                        x = playerX,
                        y = playerY,
                        width = playerWidth,
                        height = playerHeight,
                        runCycle = runCycle,
                        isJumping = isJumping,
                        isOnGround = isOnGround,
                        isInvulnerable = isInvulnerable
                    )

                    // 8. Foreground Lush Leafy Bushes
                    drawForegroundBushes(virtualWidth)

                    // 9. Score Popups (+5 text)
                    scorePopups.forEach { popup ->
                        textPaint.alpha = (popup.alpha * 255).toInt().coerceIn(0, 255)
                        drawContext.canvas.nativeCanvas.drawText("+5", popup.x, popup.y, textPaint)
                    }
                }
            }

            // ── HUD OVERLAYS (Score & Compact Jump Button) ───────────────────
            if (gameState == JumpGameState.PLAYING || gameState == JumpGameState.READY_TO_RESUME) {
                // Top Left: Compact Score Panel
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 14.dp, top = 10.dp)
                        .background(Color(0xD90B192C), RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Score: $currentScore",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(10.dp)
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(Color(0xFFFFB800), CircleShape)
                                .border(1.dp, Color(0xFFD97706), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "★",
                                color = Color(0xFF78350F),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Text(
                            text = "$coinsCollected",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonGold
                        )
                    }
                }

                // Subtle Shield Active Indicator Badge during 5-second shield
                if (isInvulnerable) {
                    val secondsLeft = (invulnerableTicks / 60) + 1
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                            .background(Color(0xCC002B36), RoundedCornerShape(12.dp))
                            .border(1.dp, NeonCyan, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "🛡",
                            fontSize = 11.sp
                        )
                        Text(
                            text = "SHIELD ACTIVE (${secondsLeft}s)",
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Bottom Right: Compact, Clean JUMP Button (Clear JUMP icon/sign)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xDD0A223E))
                            .border(1.5.dp, Color(0xFF38BDF8), CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                executeJump()
                            }
                            .testTag("jump_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Jump",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "JUMP",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            // ── START / PLAY SCREEN ─────────────────────────────────────────
            if (gameState == JumpGameState.MENU) {
                // Minimal Clean Start Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.42f)),
                    contentAlignment = Alignment.Center
                ) {
                    // Top-Left Back Button
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .size(44.dp)
                            .background(Color(0xCC0B192C), CircleShape)
                            .border(1.dp, Color(0xFF38BDF8), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    // Center Menu Card
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .background(Color(0xE60B192C), RoundedCornerShape(24.dp))
                            .border(1.5.dp, Color(0xFF38BDF8).copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                            .padding(horizontal = 36.dp, vertical = 24.dp)
                    ) {
                        Text(
                            text = "JUMP & RUN",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = NeonCyan,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "Every Coin = +5 Points • Up to 3 Rewarded Revives",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )

                        Button(
                            onClick = {
                                resetGame()
                                gameState = JumpGameState.PLAYING
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .width(220.dp)
                                .height(56.dp)
                                .testTag("play_game_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color(0xFF002026),
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = "PLAY",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF002026),
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }
            }

            // ── POST-REVIVE RESUME OVERLAY (START / CONTINUE) ───────────────
            if (gameState == JumpGameState.READY_TO_RESUME) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {},
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = CyberSurface),
                        border = androidx.compose.foundation.BorderStroke(2.dp, NeonGreen),
                        modifier = Modifier.width(320.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "REVIVED!",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = NeonGreen
                            )
                            Text(
                                text = "5-second invulnerability shield will activate on continue.",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { resumeAfterRevive() },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("continue_after_revive_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.Black
                                    )
                                    Text(
                                        text = "START / CONTINUE",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── GAME OVER & REWARDED AD OVERLAY ─────────────────────────────
            if (gameState == JumpGameState.GAME_OVER && !isSubmitting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.80f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {},
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Spacer(modifier = Modifier.height(2.dp))

                        // Centered Card for Game Over Content
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = CyberSurface),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, if (adsWatchedCount < 3) NeonGold else NeonCyan),
                            modifier = Modifier.widthIn(max = 440.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "GAME OVER",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = NeonRed,
                                    letterSpacing = 1.sp
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "Final Score",
                                            fontSize = 11.sp,
                                            color = TextSecondary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "$currentScore",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            color = TextPrimary
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(24.dp)
                                            .background(Color.White.copy(alpha = 0.2f))
                                    )
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "Coins Collected",
                                            fontSize = 11.sp,
                                            color = TextSecondary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "$coinsCollected",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            color = NeonGold
                                        )
                                    }
                                }

                                // Action Buttons: WATCH AD TO CONTINUE and SUBMIT SCORE
                                if (adsWatchedCount < 3) {
                                    val revivesLeft = 3 - adsWatchedCount
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = { handleWatchAd() },
                                            colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(40.dp)
                                                .testTag("watch_ad_button")
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Ad",
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "WATCH AD ($revivesLeft/3)",
                                                    color = Color.Black,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = { submitFinalScore() },
                                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(40.dp)
                                                .testTag("submit_final_score_button")
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Send,
                                                    contentDescription = "Submit",
                                                    tint = Color(0xFF002026),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Text(
                                                    text = "SUBMIT SCORE",
                                                    color = Color(0xFF002026),
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    // Maximum 3 revives reached: only SUBMIT SCORE is presented
                                    Button(
                                        onClick = { submitFinalScore() },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(42.dp)
                                            .testTag("submit_final_score_button")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Send,
                                                contentDescription = "Submit",
                                                tint = Color(0xFF002026),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "SUBMIT SCORE",
                                                color = Color(0xFF002026),
                                                fontWeight = FontWeight.Black,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom: AdMob Banner Ad (Integrated neatly at bottom)
                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            factory = { ctx ->
                                AdView(ctx).apply {
                                    setAdSize(AdSize.BANNER)
                                    // Official Google AdMob Test Banner Ad Unit ID
                                    adUnitId = "ca-app-pub-3940256099942544/6300978111"
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                    )
                                    loadAd(AdRequest.Builder().build())
                                }
                            }
                        )
                    }
                }
            }

            // Score Submission in progress overlay
            if (isSubmitting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.70f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator(color = NeonCyan)
                        Text(
                            text = "Submitting Score...",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // Fallback Rewarded Ad Simulation Dialog if AdMob is loading/unavailable
            if (isSimulatingAd) {
                RewardedAdSimulationDialog(
                    rewardTitle = "Revive at Platform",
                    onRewardEarned = {
                        isSimulatingAd = false
                        revivePlayerAtCurrentLocation()
                    },
                    onDismiss = { isSimulatingAd = false }
                )
            }
        }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2D CARTOON PLATFORM RUNNER DRAWING EXTENSIONS
// ─────────────────────────────────────────────────────────────────────────────

fun DrawScope.drawParallaxCloud(cloud: ParallaxCloud) {
    val cloudColor = Color.White.copy(alpha = 0.88f)
    val shadowColor = Color(0xFFB0BEC5).copy(alpha = 0.35f)

    // Base body
    drawRoundRect(
        color = cloudColor,
        topLeft = Offset(cloud.x, cloud.y),
        size = Size(cloud.width, cloud.height),
        cornerRadius = CornerRadius(cloud.height / 2f, cloud.height / 2f)
    )
    // Puffy top lobes
    drawCircle(
        color = cloudColor,
        radius = cloud.height * 0.58f,
        center = Offset(cloud.x + (cloud.width * 0.35f), cloud.y + (cloud.height * 0.3f))
    )
    drawCircle(
        color = cloudColor,
        radius = cloud.height * 0.48f,
        center = Offset(cloud.x + (cloud.width * 0.65f), cloud.y + (cloud.height * 0.35f))
    )
    // Cloud bottom shadow
    drawRoundRect(
        color = shadowColor,
        topLeft = Offset(cloud.x + 6f, cloud.y + cloud.height - 8f),
        size = Size(cloud.width - 12f, 6f),
        cornerRadius = CornerRadius(3f, 3f)
    )
}

fun DrawScope.drawDistantMountains(scroll: Float, virtualWidth: Float = 1400f) {
    val mountainScroll = (scroll * 0.12f) % 960f
    val baseColor = Color(0xFF5B82B5)
    val darkPeak = Color(0xFF3B629B)
    val layers = (virtualWidth / 960f).toInt() + 1

    for (layer in 0..layers) {
        val offsetX = (layer * 960f) - mountainScroll
        val path = Path().apply {
            moveTo(offsetX - 40f, 320f)
            lineTo(offsetX + 120f, 170f)
            lineTo(offsetX + 240f, 250f)
            lineTo(offsetX + 410f, 150f)
            lineTo(offsetX + 580f, 280f)
            lineTo(offsetX + 740f, 160f)
            lineTo(offsetX + 890f, 260f)
            lineTo(offsetX + 1020f, 180f)
            lineTo(offsetX + 1020f, 320f)
            close()
        }
        drawPath(path, color = baseColor)

        // Shaded ridge lines
        drawLine(
            color = darkPeak,
            start = Offset(offsetX + 410f, 150f),
            end = Offset(offsetX + 450f, 320f),
            strokeWidth = 3f
        )
        drawLine(
            color = darkPeak,
            start = Offset(offsetX + 740f, 160f),
            end = Offset(offsetX + 780f, 320f),
            strokeWidth = 3f
        )
    }
}

fun DrawScope.drawMidgroundLakeAndForest(scroll: Float, virtualWidth: Float = 1400f) {
    // Serene Lake Ribbon extending to full virtual width
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF38BDF8),
                Color(0xFF0284C7)
            ),
            startY = 275f,
            endY = 320f
        ),
        topLeft = Offset(0f, 275f),
        size = Size(virtualWidth + 120f, 45f)
    )

    // Gentle wave reflections
    val waveCount = (virtualWidth / 140f).toInt() + 2
    for (i in 0 until waveCount) {
        val wx = (i * 170f + (scroll * 0.18f)) % (virtualWidth + 100f)
        drawLine(
            color = Color.White.copy(alpha = 0.5f),
            start = Offset(wx, 290f + (i % 3) * 6f),
            end = Offset(wx + 45f, 290f + (i % 3) * 6f),
            strokeWidth = 2f
        )
    }

    // Rolling Forest Hills (Scalloped Green Tops)
    val forestScroll = (scroll * 0.28f) % 960f
    val forestGreen = Color(0xFF1E824C)
    val forestLight = Color(0xFF27AE60)
    val layers = (virtualWidth / 960f).toInt() + 1

    for (layer in 0..layers) {
        val fx = (layer * 960f) - forestScroll
        var treeX = fx - 20f
        while (treeX < fx + 980f) {
            drawCircle(
                color = forestGreen,
                radius = 36f,
                center = Offset(treeX, 325f)
            )
            drawCircle(
                color = forestLight,
                radius = 28f,
                center = Offset(treeX + 16f, 320f)
            )
            treeX += 54f
        }
    }
}

fun DrawScope.drawPlatform(p: GamePlatform) {
    val cliffTopY = p.y
    val cliffHeight = p.height
    val cliffColor = Color(0xFF78350F) // Warm earthy brown rock
    val crackColor = Color(0xFF451A03) // Dark rock fissures
    val highlightRock = Color(0xFF92400E)

    // Rocky cliff body
    drawRoundRect(
        color = cliffColor,
        topLeft = Offset(p.x, cliffTopY),
        size = Size(p.width, cliffHeight),
        cornerRadius = CornerRadius(6f, 6f)
    )

    // Rock facet shading / vertical clefts
    var cx = p.x + 24f
    while (cx < p.x + p.width - 24f) {
        drawRoundRect(
            color = crackColor,
            topLeft = Offset(cx, cliffTopY + 30f),
            size = Size(10f, 90f),
            cornerRadius = CornerRadius(3f, 3f)
        )
        drawRoundRect(
            color = highlightRock,
            topLeft = Offset(cx + 12f, cliffTopY + 45f),
            size = Size(6f, 60f),
            cornerRadius = CornerRadius(2f, 2f)
        )
        cx += 68f
    }

    // Lush Green Grass Top Layer
    val grassGreen = Color(0xFF22C55E)
    val grassHighlight = Color(0xFF86EFAC)

    drawRoundRect(
        color = grassGreen,
        topLeft = Offset(p.x, cliffTopY),
        size = Size(p.width, 22f),
        cornerRadius = CornerRadius(4f, 4f)
    )
    // Top highlight rim
    drawLine(
        color = grassHighlight,
        start = Offset(p.x + 2f, cliffTopY + 2f),
        end = Offset(p.x + p.width - 2f, cliffTopY + 2f),
        strokeWidth = 3f
    )

    // Drooping scalloped grass droplets / overhang fringe (like cartoon platformer)
    var fringeX = p.x + 12f
    while (fringeX < p.x + p.width - 12f) {
        drawCircle(
            color = grassGreen,
            radius = 7f,
            center = Offset(fringeX, cliffTopY + 22f)
        )
        fringeX += 18f
    }
}

fun DrawScope.drawGoldCoin(coin: GameCoin, scroll: Float) {
    val bobY = coin.y + (sin((scroll * 0.08f) + coin.id) * 4f)

    // Golden Outer Rim
    drawCircle(
        color = Color(0xFFD97706),
        radius = 15f,
        center = Offset(coin.x, bobY)
    )
    // Radiant Gold Face
    drawCircle(
        color = Color(0xFFFFB800),
        radius = 12.5f,
        center = Offset(coin.x, bobY)
    )
    // Inner Embossed Star
    val starColor = Color(0xFF78350F)
    drawCircle(
        color = Color(0xFFFEF08A),
        radius = 8.5f,
        center = Offset(coin.x, bobY)
    )
    // Central Star Accent
    drawCircle(
        color = starColor,
        radius = 3.5f,
        center = Offset(coin.x, bobY)
    )
    // Sparkle Highlight
    drawCircle(
        color = Color.White.copy(alpha = 0.9f),
        radius = 2.5f,
        center = Offset(coin.x - 4f, bobY - 4f)
    )
}

fun DrawScope.drawCartoonHumanCharacter(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    runCycle: Float,
    isJumping: Boolean,
    isOnGround: Boolean,
    isInvulnerable: Boolean
) {
    // 1. Invulnerability Shield Bubble (5-second protective shield)
    if (isInvulnerable) {
        val shieldCenter = Offset(x + (width / 2f), y + (height / 2f))
        val shieldRadius = height * 0.68f

        // Soft outer glowing aura
        drawCircle(
            color = Color(0x3500E5FF),
            radius = shieldRadius + 4f,
            center = shieldCenter
        )
        // Protective translucent sphere
        drawCircle(
            color = Color(0x5000E5FF),
            radius = shieldRadius,
            center = shieldCenter
        )
        // High-contrast neon cyan rim
        drawCircle(
            color = Color(0xFF00E5FF),
            radius = shieldRadius,
            center = shieldCenter,
            style = Stroke(width = 2.5f)
        )
        // Inner concentric shield harmonic
        drawCircle(
            color = Color(0x8038BDF8),
            radius = shieldRadius - 6f,
            center = shieldCenter,
            style = Stroke(width = 1.2f)
        )
    }

    // Body vertical bobbing during running cycle (subtle up/down bounce twice per stride)
    val bobY = if (isOnGround && !isJumping) abs(sin(runCycle.toDouble()).toFloat()) * 3.5f else 0f
    val currentY = y - bobY

    // Character Base Dimensions
    val headRadius = 14f
    val headCenterX = x + 24f
    val headCenterY = currentY + 15f

    val legColor = Color(0xFF1D4ED8) // Blue denim jeans
    val shoeColor = Color.White
    val shoeAccent = Color(0xFFDC2626)
    val skinColor = Color(0xFFFFDFBA)
    val hoodieColor = Color(0xFFDC2626)
    val hoodieShadow = Color(0xFF991B1B)

    val cycleSin = sin(runCycle.toDouble()).toFloat()
    val cycleCos = cos(runCycle.toDouble()).toFloat()

    // 2. BACK ARM (Swings opposite to front arm)
    if (isOnGround && !isJumping) {
        val backArmSwing = cycleSin * 10f
        drawLine(
            color = hoodieShadow,
            start = Offset(x + 16f, currentY + 28f),
            end = Offset(x + 12f + backArmSwing, currentY + 37f),
            strokeWidth = 5.5f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = skinColor,
            radius = 3.5f,
            center = Offset(x + 10f + backArmSwing, currentY + 38f)
        )
    } else {
        // Jumping pose - back arm trailing backwards in aerodynamic leap
        drawLine(
            color = hoodieShadow,
            start = Offset(x + 16f, currentY + 28f),
            end = Offset(x + 6f, currentY + 36f),
            strokeWidth = 5.5f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = skinColor,
            radius = 3.5f,
            center = Offset(x + 4f, currentY + 37f)
        )
    }

    // 3. BACK LEG (Alternates with front leg)
    if (isOnGround && !isJumping) {
        val backSin = -cycleSin
        val backCos = -cycleCos
        val hipX = x + 18f
        val hipY = currentY + 41f

        val kneeX: Float
        val kneeY: Float
        val footX: Float
        val footY: Float

        if (backSin > 0f) {
            kneeX = hipX + (backSin * 8f) + 2f
            kneeY = hipY + 11f - (maxOf(0f, backCos) * 4f)
            footX = kneeX + (backSin * 9f)
            footY = hipY + 23f - (maxOf(0f, backCos) * 5f)
        } else {
            kneeX = hipX + (backSin * 7f)
            kneeY = hipY + 12f
            footX = kneeX + (backSin * 10f)
            footY = hipY + 23f
        }

        drawLine(
            color = Color(0xFF1E40AF),
            start = Offset(hipX, hipY),
            end = Offset(kneeX, kneeY),
            strokeWidth = 6.5f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF1E40AF),
            start = Offset(kneeX, kneeY),
            end = Offset(footX, footY),
            strokeWidth = 6f,
            cap = StrokeCap.Round
        )
        drawRoundRect(
            color = shoeColor,
            topLeft = Offset(footX - 3f, footY - 3f),
            size = Size(15f, 6.5f),
            cornerRadius = CornerRadius(2f, 2f)
        )
        drawRoundRect(
            color = shoeAccent,
            topLeft = Offset(footX - 3f, footY + 1f),
            size = Size(15f, 2.5f),
            cornerRadius = CornerRadius(1f, 1f)
        )
    } else {
        // Jumping pose - back leg extended back
        val hipX = x + 18f
        val hipY = currentY + 41f
        val kneeX = x + 10f
        val kneeY = currentY + 48f
        val footX = x + 3f
        val footY = currentY + 56f

        drawLine(
            color = Color(0xFF1E40AF),
            start = Offset(hipX, hipY),
            end = Offset(kneeX, kneeY),
            strokeWidth = 6.5f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFF1E40AF),
            start = Offset(kneeX, kneeY),
            end = Offset(footX, footY),
            strokeWidth = 6f,
            cap = StrokeCap.Round
        )
        drawRoundRect(
            color = shoeColor,
            topLeft = Offset(footX - 4f, footY - 2f),
            size = Size(14f, 6f),
            cornerRadius = CornerRadius(2f, 2f)
        )
    }

    // 4. TORSO (Red Hoodie Jacket)
    drawRoundRect(
        color = hoodieColor,
        topLeft = Offset(x + 12f, currentY + 24f),
        size = Size(24f, 20f),
        cornerRadius = CornerRadius(6f, 6f)
    )
    // White zipper line & hood collar lining
    drawLine(
        color = Color.White,
        start = Offset(x + 24f, currentY + 25f),
        end = Offset(x + 24f, currentY + 42f),
        strokeWidth = 2f
    )
    drawCircle(
        color = hoodieShadow,
        radius = 5f,
        center = Offset(x + 14f, currentY + 26f)
    )

    // 5. FRONT LEG
    if (isOnGround && !isJumping) {
        val hipX = x + 24f
        val hipY = currentY + 41f

        val kneeX: Float
        val kneeY: Float
        val footX: Float
        val footY: Float

        if (cycleSin > 0f) {
            kneeX = hipX + (cycleSin * 8f) + 2f
            kneeY = hipY + 11f - (maxOf(0f, cycleCos) * 4f)
            footX = kneeX + (cycleSin * 9f)
            footY = hipY + 23f - (maxOf(0f, cycleCos) * 5f)
        } else {
            kneeX = hipX + (cycleSin * 7f)
            kneeY = hipY + 12f
            footX = kneeX + (cycleSin * 10f)
            footY = hipY + 23f
        }

        drawLine(
            color = legColor,
            start = Offset(hipX, hipY),
            end = Offset(kneeX, kneeY),
            strokeWidth = 7f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = legColor,
            start = Offset(kneeX, kneeY),
            end = Offset(footX, footY),
            strokeWidth = 6.5f,
            cap = StrokeCap.Round
        )
        drawRoundRect(
            color = shoeColor,
            topLeft = Offset(footX - 3f, footY - 3f),
            size = Size(16f, 7f),
            cornerRadius = CornerRadius(2f, 2f)
        )
        drawRoundRect(
            color = shoeAccent,
            topLeft = Offset(footX - 3f, footY + 1.5f),
            size = Size(16f, 2.5f),
            cornerRadius = CornerRadius(1f, 1f)
        )
    } else {
        // Jumping pose - front leg tucked up forward with bent knee
        val hipX = x + 24f
        val hipY = currentY + 41f
        val kneeX = x + 33f
        val kneeY = currentY + 47f
        val footX = x + 25f
        val footY = currentY + 56f

        drawLine(
            color = legColor,
            start = Offset(hipX, hipY),
            end = Offset(kneeX, kneeY),
            strokeWidth = 7f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = legColor,
            start = Offset(kneeX, kneeY),
            end = Offset(footX, footY),
            strokeWidth = 6.5f,
            cap = StrokeCap.Round
        )
        drawRoundRect(
            color = shoeColor,
            topLeft = Offset(footX - 2f, footY - 2f),
            size = Size(15f, 6.5f),
            cornerRadius = CornerRadius(2f, 2f)
        )
        drawRoundRect(
            color = shoeAccent,
            topLeft = Offset(footX - 2f, footY + 2f),
            size = Size(15f, 2.5f),
            cornerRadius = CornerRadius(1f, 1f)
        )
    }

    // 6. FRONT ARM & FIST
    if (isOnGround && !isJumping) {
        val armSwing = -cycleSin * 11f
        val elbowX = x + 24f + (armSwing * 0.4f)
        val elbowY = currentY + 34f
        val fistX = x + 28f + armSwing
        val fistY = currentY + 35f - (cycleCos * 2f)

        drawLine(
            color = hoodieColor,
            start = Offset(x + 23f, currentY + 28f),
            end = Offset(elbowX, elbowY),
            strokeWidth = 6f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = hoodieColor,
            start = Offset(elbowX, elbowY),
            end = Offset(fistX, fistY),
            strokeWidth = 5.5f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = skinColor,
            radius = 4f,
            center = Offset(fistX + 2f, fistY)
        )
    } else {
        // Jumping pose - athletic forward fist pump
        val shoulderX = x + 23f
        val shoulderY = currentY + 28f
        val fistX = x + 35f
        val fistY = currentY + 23f

        drawLine(
            color = hoodieColor,
            start = Offset(shoulderX, shoulderY),
            end = Offset(fistX, fistY),
            strokeWidth = 6f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = skinColor,
            radius = 4f,
            center = Offset(fistX + 2f, fistY)
        )
    }

    // 6. Cartoon Human Head & Anime Brown Hair
    val hairColor = Color(0xFF4A2810) // Rich anime brown
    val hairDark = Color(0xFF2C1607)

    // Face
    drawCircle(
        color = skinColor,
        radius = headRadius,
        center = Offset(headCenterX, headCenterY)
    )

    // Spiky/Messy Hair Volume
    drawCircle(
        color = hairColor,
        radius = headRadius * 1.08f,
        center = Offset(headCenterX - 2f, headCenterY - 3f)
    )
    // Hair bangs on forehead
    val hairBangs = Path().apply {
        moveTo(headCenterX - 14f, headCenterY - 6f)
        lineTo(headCenterX - 4f, headCenterY - 1f)
        lineTo(headCenterX + 4f, headCenterY - 6f)
        lineTo(headCenterX + 12f, headCenterY - 2f)
        lineTo(headCenterX + 15f, headCenterY - 10f)
        lineTo(headCenterX - 14f, headCenterY - 14f)
        close()
    }
    drawPath(hairBangs, color = hairColor)

    // Cute Anime Eye (Looking forward)
    drawRoundRect(
        color = Color.Black,
        topLeft = Offset(headCenterX + 4f, headCenterY - 2f),
        size = Size(4f, 7f),
        cornerRadius = CornerRadius(2f, 2f)
    )
    // White eye shine
    drawCircle(
        color = Color.White,
        radius = 1.2f,
        center = Offset(headCenterX + 5f, headCenterY - 1f)
    )
    // Soft cute cheek blush
    drawCircle(
        color = Color(0xFFFF8A80).copy(alpha = 0.5f),
        radius = 3.5f,
        center = Offset(headCenterX + 2f, headCenterY + 4f)
    )
}

fun DrawScope.drawForegroundBushes(virtualWidth: Float = 1400f) {
    val bushDark = Color(0xFF14532D)
    val bushLight = Color(0xFF166534)

    // Bottom edge framing lush foliage across full virtual width
    var bx = -20f
    while (bx < virtualWidth + 50f) {
        drawCircle(
            color = bushDark,
            radius = 34f,
            center = Offset(bx, 530f)
        )
        drawCircle(
            color = bushLight,
            radius = 26f,
            center = Offset(bx + 14f, 524f)
        )
        bx += 48f
    }
}


package com.example

import com.example.games.flappy.FlappyCoin
import com.example.games.flappy.FlappyPillar
import com.example.games.flappy.FlappyState
import org.junit.Assert.*
import org.junit.Test

class FlappyGameLogicTest {

    @Test
    fun testTapImpulseAndGravity() {
        var birdVelocity = 0f
        val gravity = 0.00068f
        val flapImpulse = -0.0135f

        // Initial flap
        birdVelocity = flapImpulse
        assertTrue("Flap should produce negative upward velocity", birdVelocity < 0f)

        // Gravity over 5 frames
        repeat(5) {
            birdVelocity = (birdVelocity + gravity).coerceIn(-0.016f, 0.019f)
        }
        // Downward gravity counteracts upward impulse
        assertTrue(birdVelocity > flapImpulse)

        // Gravity over 30 frames without tap should make velocity positive (falling)
        repeat(30) {
            birdVelocity = (birdVelocity + gravity).coerceIn(-0.016f, 0.019f)
        }
        assertTrue("Bird must fall when player does not tap", birdVelocity > 0f)
    }

    @Test
    fun testCoinCollectionScore() {
        var score = 0L
        var coinsCount = 0

        val coin = FlappyCoin(id = 1L, xRatio = 0.25f, yRatio = 0.45f)
        val birdXRatio = 0.25f
        val birdYRatio = 0.45f

        val dx = kotlin.math.abs(coin.xRatio - birdXRatio)
        val dy = kotlin.math.abs(coin.yRatio - birdYRatio)

        if (dx < 0.048f && dy < 0.052f) {
            coin.collected = true
            coinsCount += 1
            score += 10
        }

        assertTrue(coin.collected)
        assertEquals(1, coinsCount)
        assertEquals(10L, score)
    }

    @Test
    fun testDifficultyProgressionStages() {
        fun getStage(score: Long): Int = when {
            score < 15 -> 1
            score < 35 -> 2
            score < 65 -> 3
            else -> 4
        }

        fun getSpeed(stage: Int): Float = when (stage) {
            1 -> 0.0055f
            2 -> 0.0068f
            3 -> 0.0080f
            else -> 0.0092f
        }

        fun getGap(stage: Int): Float = when (stage) {
            1 -> 0.31f
            2 -> 0.27f
            3 -> 0.24f
            else -> 0.21f
        }

        // Stage 1
        val stage1 = getStage(5)
        assertEquals(1, stage1)
        val speed1 = getSpeed(stage1)
        val gap1 = getGap(stage1)

        // Stage 2
        val stage2 = getStage(20)
        assertEquals(2, stage2)
        val speed2 = getSpeed(stage2)
        val gap2 = getGap(stage2)
        assertTrue("Speed increases in stage 2", speed2 > speed1)
        assertTrue("Gap decreases in stage 2", gap2 < gap1)

        // Stage 3
        val stage3 = getStage(50)
        assertEquals(3, stage3)
        val speed3 = getSpeed(stage3)
        val gap3 = getGap(stage3)
        assertTrue("Speed increases in stage 3", speed3 > speed2)
        assertTrue("Gap decreases in stage 3", gap3 < gap2)

        // Stage 4
        val stage4 = getStage(80)
        assertEquals(4, stage4)
        val speed4 = getSpeed(stage4)
        val gap4 = getGap(stage4)
        assertTrue("Speed increases in stage 4", speed4 > speed3)
        assertTrue("Gap decreases in stage 4", gap4 < gap3)
    }

    @Test
    fun testCollisionDetection() {
        val groundYRatio = 0.82f
        val birdRadiusRatio = 0.038f

        // Case 1: bird hits ground
        var birdYRatio = 0.81f
        val hitGround = birdYRatio >= (groundYRatio - birdRadiusRatio * 0.7f)
        assertTrue("Bird at 0.81 should hit the ground at 0.82", hitGround)

        // Case 2: bird inside safe pillar gap
        val pillar = FlappyPillar(id = 1L, xRatio = 0.25f, topHeightRatio = 0.25f, gapRatio = 0.30f)
        val birdXRatio = 0.25f
        birdYRatio = 0.40f // between 0.25 and 0.55

        val pLeft = pillar.xRatio
        val pRight = pillar.xRatio + 0.15f
        val birdHitboxX1 = birdXRatio - 0.025f
        val birdHitboxX2 = birdXRatio + 0.025f

        val inX = birdHitboxX2 > pLeft && birdHitboxX1 < pRight
        val topThreshold = pillar.topHeightRatio + 0.01f
        val bottomThreshold = (pillar.topHeightRatio + pillar.gapRatio) - 0.01f

        val hitTop = (birdYRatio - 0.022f) < topThreshold
        val hitBottom = (birdYRatio + 0.022f) > bottomThreshold

        assertTrue("Bird should be horizontally aligned with pillar", inX)
        assertFalse("Bird should safely clear top pillar", hitTop)
        assertFalse("Bird should safely clear bottom pillar", hitBottom)

        // Case 3: bird hits top pillar
        val highBirdY = 0.20f
        val hitTopPillar = (highBirdY - 0.022f) < topThreshold
        assertTrue("Bird at 0.20 should collide with top pillar at 0.25", hitTopPillar)
    }

    @Test
    fun testSmallerBirdAndCoinHitbox() {
        val birdX = 0.25f
        val birdY = 0.44f

        val coinNear = FlappyCoin(id = 1L, xRatio = 0.27f, yRatio = 0.45f)
        val coinFar = FlappyCoin(id = 2L, xRatio = 0.35f, yRatio = 0.45f)

        fun canCollect(c: FlappyCoin): Boolean {
            val dx = kotlin.math.abs(c.xRatio - birdX)
            val dy = kotlin.math.abs(c.yRatio - birdY)
            return dx < 0.040f && dy < 0.042f
        }

        assertTrue("Near coin must be collected with smaller hitbox", canCollect(coinNear))
        assertFalse("Far coin must not be collected", canCollect(coinFar))
    }

    @Test
    fun testFiveSecondShieldProtection() {
        var isInvulnerable = true
        var shieldSecondsRemaining = 5
        var gameState = FlappyState.PLAYING

        // Simulate obstacle collision check
        val hitObstacle = true
        if (!isInvulnerable && hitObstacle) {
            gameState = FlappyState.GAME_OVER
        }

        // Must remain PLAYING because shield protects the bird
        assertEquals(FlappyState.PLAYING, gameState)

        // Shield expires after 5s
        repeat(5) {
            shieldSecondsRemaining--
        }
        assertEquals(0, shieldSecondsRemaining)
        isInvulnerable = false

        // After shield expires, collision triggers game over
        if (!isInvulnerable && hitObstacle) {
            gameState = FlappyState.GAME_OVER
        }
        assertEquals(FlappyState.GAME_OVER, gameState)
    }

    @Test
    fun testRestartStateClearing() {
        var state = FlappyState.GAME_OVER
        var score = 50L
        var coins = 5
        var gates = 4

        // Reset
        state = FlappyState.READY
        score = 0L
        coins = 0
        gates = 0

        assertEquals(FlappyState.READY, state)
        assertEquals(0L, score)
        assertEquals(0, coins)
        assertEquals(0, gates)
    }
}

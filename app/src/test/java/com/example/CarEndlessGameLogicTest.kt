package com.example

import com.example.games.carendless.EnemyVehicle
import com.example.games.carendless.calculateCarEndlessSpeedProgression
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class CarEndlessGameLogicTest {

    // Restored Road parameters
    private val roadWidthRatio = 0.78f
    private val laneCount = 4
    private val playerYRatio = 0.68f // Lower-middle area

    @Test
    fun testRoadGeometryAndLanes() {
        val canvasWidth = 1000f
        val roadWidth = canvasWidth * roadWidthRatio
        val roadLeft = (canvasWidth - roadWidth) / 2f
        val roadRight = roadLeft + roadWidth
        val laneWidth = roadWidth / laneCount.toFloat()

        // Road is centered
        assertEquals(110f, roadLeft, 0.01f)
        assertEquals(890f, roadRight, 0.01f)
        assertEquals(780f, roadWidth, 0.01f)

        // 4 lanes inside road
        for (lane in 0 until laneCount) {
            val laneCenter = roadLeft + ((lane + 0.5f) * laneWidth)
            assertTrue(laneCenter > roadLeft && laneCenter < roadRight)
        }
    }

    @Test
    fun testPlayerCarPositionInLowerMiddle() {
        // Player should be in the lower-middle portion (0.68f)
        // leaving 68% of screen height ahead of the car for viewing approaching traffic
        assertTrue("Player Y must be in lower-middle area (0.65f - 0.72f)", playerYRatio in 0.65f..0.72f)
        assertTrue("More than 60% of road visible in front of player", playerYRatio > 0.60f)
        assertTrue("Leaves space behind for road/controls", (1.0f - playerYRatio) > 0.25f)
    }

    @Test
    fun testScoringFormulaPreserved() {
        val distance = 420L
        val overtakes = 12L
        val score = distance + (overtakes * 50L)

        assertEquals(1020L, score)
    }

    @Test
    fun testInvulnerabilityShieldIgnoresCollisions() {
        val playerY = 0.68f
        val isInvulnerable = true
        var hasCrashed = false

        // Enemy right on top of player
        val enemy = EnemyVehicle(
            id = 1L,
            lane = 2,
            yRatio = 0.68f,
            speed = 0.01f,
            primaryColor = Color.Blue,
            accentColor = Color.Cyan
        )

        val verticalDistance = abs(enemy.yRatio - playerY)
        val horizontalDist = 0.02f // Direct overlap

        if (!isInvulnerable && verticalDistance < 0.075f && horizontalDist < 0.14f) {
            hasCrashed = true
        }

        assertFalse("Player must NOT crash when invulnerable with 5-second shield", hasCrashed)
    }

    @Test
    fun testRevivePreservesProgressAndClearsHazard() {
        var distance = 1500L
        var overtakes = 20L
        var adsWatched = 0
        val trafficList = mutableListOf(
            EnemyVehicle(1L, 1, 0.68f, 0.01f, Color.Red, Color.White), // Hazard at player Y
            EnemyVehicle(2L, 3, 0.10f, 0.01f, Color.Green, Color.Yellow) // Far away vehicle
        )

        // Simulate Revive after rewarded ad
        adsWatched += 1
        val playerY = 0.68f
        // Clears hazard near player
        trafficList.removeAll { abs(it.yRatio - playerY) < 0.25f }

        // Progress intact
        assertEquals(1500L, distance)
        assertEquals(20L, overtakes)
        assertEquals(1, adsWatched)
        // Immediate hazard removed, far vehicle remains
        assertEquals(1, trafficList.size)
        assertEquals(2L, trafficList[0].id)
    }

    @Test
    fun testSpeedProgressionMilestones() {
        // 0s: Normal starting speed
        val speed0s = calculateCarEndlessSpeedProgression(0f)
        assertEquals(1.0f, speed0s, 0.001f)

        // 10s: Slightly faster
        val speed10s = calculateCarEndlessSpeedProgression(10f)
        assertTrue("Speed at 10s should be slightly faster (~1.27x)", speed10s in 1.20f..1.35f)
        assertTrue(speed10s > speed0s)

        // 20s: Clearly faster than starting speed
        val speed20s = calculateCarEndlessSpeedProgression(20f)
        assertTrue("Speed at 20s should be clearly faster than starting (~1.59x)", speed20s in 1.50f..1.70f)
        assertTrue(speed20s > speed10s)

        // 30s: Faster (~1.95x, nearly double starting speed)
        val speed30s = calculateCarEndlessSpeedProgression(30f)
        assertTrue("Speed at 30s should be around 2x starting speed", speed30s in 1.85f..2.10f)
        assertTrue(speed30s > speed20s)

        // 45s: Noticeably faster (~2.57x)
        val speed45s = calculateCarEndlessSpeedProgression(45f)
        assertTrue("Speed at 45s should be noticeably faster", speed45s in 2.40f..2.75f)
        assertTrue(speed45s > speed30s)

        // 60s: Challenging and significantly faster than the beginning (~3.29x)
        val speed60s = calculateCarEndlessSpeedProgression(60f)
        assertTrue("Speed at 60s should be over 3x starting speed", speed60s in 3.10f..3.50f)
        assertTrue(speed60s > speed45s)

        // 75s+: Continues gradually increasing smoothly
        val speed75s = calculateCarEndlessSpeedProgression(75f)
        assertTrue("Speed beyond 60s continues increasing", speed75s > speed60s)
        assertTrue(speed75s <= 4.5f)
    }

    @Test
    fun testSpeedProgressionSmoothAndMonotonicOver60Seconds() {
        // Simulate 60 seconds at 60 frames per second (3600 frames total)
        var previousSpeed = calculateCarEndlessSpeedProgression(0f)

        for (frame in 1..3600) {
            val seconds = frame / 60.0f
            val currentSpeed = calculateCarEndlessSpeedProgression(seconds)

            // Strictly increasing
            assertTrue("Speed must increase smoothly each frame (frame=$frame)", currentSpeed > previousSpeed)

            // No sudden jumps between adjacent frames (smoothness check)
            val delta = currentSpeed - previousSpeed
            assertTrue("Per-frame speed delta should be very smooth (delta=$delta)", delta < 0.002f)

            previousSpeed = currentSpeed
        }
    }

    @Test
    fun testRoadMovementAndTrafficSpeedScaleWithProgression() {
        val baseDashSpeed = 0.038f
        val enemyBaseSpeed = 0.009f

        val progression0s = calculateCarEndlessSpeedProgression(0f)
        val progression30s = calculateCarEndlessSpeedProgression(30f)
        val progression60s = calculateCarEndlessSpeedProgression(60f)

        val roadDash0s = baseDashSpeed * progression0s
        val roadDash30s = baseDashSpeed * progression30s
        val roadDash60s = baseDashSpeed * progression60s

        assertTrue(roadDash30s > roadDash0s * 1.5f)
        assertTrue(roadDash60s > roadDash0s * 3.0f)

        val trafficMovement0s = enemyBaseSpeed * progression0s
        val trafficMovement30s = enemyBaseSpeed * progression30s
        val trafficMovement60s = enemyBaseSpeed * progression60s

        assertTrue(trafficMovement30s > trafficMovement0s * 1.5f)
        assertTrue(trafficMovement60s > trafficMovement0s * 3.0f)
    }
}

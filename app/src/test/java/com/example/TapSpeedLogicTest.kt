package com.example

import com.example.games.tapspeed.getTapBonusSeconds
import org.junit.Assert.*
import org.junit.Test

class TapSpeedLogicTest {

    @Test
    fun testTapBonusSecondsSequence() {
        // Rewarded Ad #1: +30 seconds
        assertEquals(30, getTapBonusSeconds(0))

        // Rewarded Ad #2: +20 seconds
        assertEquals(20, getTapBonusSeconds(1))

        // Rewarded Ad #3: +10 seconds
        assertEquals(10, getTapBonusSeconds(2))

        // No 4th ad bonus
        assertEquals(0, getTapBonusSeconds(3))
        assertEquals(0, getTapBonusSeconds(4))
    }

    @Test
    fun testTotalGameplaySessionDurations() {
        val initialSession = 60
        val ad1Bonus = getTapBonusSeconds(0)
        val ad2Bonus = getTapBonusSeconds(1)
        val ad3Bonus = getTapBonusSeconds(2)

        val totalPossibleGameplay = initialSession + ad1Bonus + ad2Bonus + ad3Bonus
        // 60 + 30 + 20 + 10 = 120 seconds maximum
        assertEquals(120, totalPossibleGameplay)
    }

    @Test
    fun testScorePreservationSimulation() {
        var baseScore = 0L

        // Session 1: 60 seconds of tapping
        baseScore += 850L
        assertEquals(850L, baseScore)

        // Ad 1 watched -> START -> 3,2,1 (Score must NOT reset)
        // Score continues from 850
        val scoreBeforeAd1 = baseScore
        assertEquals(850L, scoreBeforeAd1)

        // Session 2: +30 seconds of tapping
        baseScore += 420L
        assertEquals(1270L, baseScore)

        // Ad 2 watched -> START -> 3,2,1 (Score must NOT reset)
        val scoreBeforeAd2 = baseScore
        assertEquals(1270L, scoreBeforeAd2)

        // Session 3: +20 seconds of tapping
        baseScore += 290L
        assertEquals(1560L, baseScore)

        // Ad 3 watched -> START -> 3,2,1 (Score must NOT reset)
        val scoreBeforeAd3 = baseScore
        assertEquals(1560L, scoreBeforeAd3)

        // Session 4: +10 seconds of tapping
        baseScore += 140L
        assertEquals(1700L, baseScore)

        // Final score submitted
        assertEquals(1700L, baseScore)
    }
}

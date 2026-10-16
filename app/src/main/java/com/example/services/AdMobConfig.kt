package com.example.services

object AdMobConfig {
    // Official Google AdMob Test Rewarded Video Ad Unit ID
    const val REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    
    // Limits
    const val MAX_REWARD_ADS_PER_SESSION = 3

    // Tap Speed Challenge bonuses (seconds)
    val TAP_SPEED_BONUS_SECONDS = listOf(20, 20, 10)

    fun getTapSpeedBonusSeconds(adIndex: Int): Int {
        return if (adIndex in TAP_SPEED_BONUS_SECONDS.indices) {
            TAP_SPEED_BONUS_SECONDS[adIndex]
        } else {
            10
        }
    }
}

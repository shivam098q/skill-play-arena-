package com.example.model

enum class GameType(
    val id: String,
    val title: String,
    val description: String,
    val baseDurationSeconds: Int
) {
    TAP_SPEED(
        id = "tap_speed",
        title = "Tap Speed Challenge",
        description = "Rapid tapping precision sprint with dynamic combo multiplier.",
        baseDurationSeconds = 60
    ),
    CAR_ENDLESS(
        id = "car_endless",
        title = "Car Endless Road",
        description = "Dodge highway traffic across 4 lanes and collect golden coins.",
        baseDurationSeconds = 0
    ),
    FLAPPY(
        id = "flappy",
        title = "Flappy Challenge",
        description = "Precision physics flight through shifting obstacle gates.",
        baseDurationSeconds = 0
    ),
    JUMP_RUN(
        id = "jump_run",
        title = "Jump & Run",
        description = "High-speed platform runner dodging hazardous terrain.",
        baseDurationSeconds = 0
    );

    companion object {
        fun fromId(id: String): GameType {
            return entries.firstOrNull { it.id == id } ?: TAP_SPEED
        }
    }
}

data class UserProfile(
    val uid: String = "",
    val displayName: String = "Player",
    val email: String = "",
    val phoneNumber: String = "",
    val photoURL: String? = null,
    val provider: String = "password",
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis(),
    val gamesPlayed: Long = 0L,
    val bestScores: Map<String, Long> = emptyMap()
)

data class GameAttempt(
    val attemptId: String = "",
    val uid: String = "",
    val gameId: String = "",
    val dateKey: String = "",
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long = 0L,
    val baseScore: Long = 0L,
    val rewardAdsWatched: Int = 0,
    val rewardTimeOrRevives: Long = 0L,
    val finalScore: Long = 0L,
    val submitted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class LeaderboardEntry(
    val uid: String = "",
    val displayName: String = "Player",
    val photoURL: String? = null,
    val score: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis(),
    val rank: Int = 0
)

sealed interface AttemptStatus {
    data object NotPlayed : AttemptStatus
    data class InProgress(val attempt: GameAttempt) : AttemptStatus
    data class AlreadyPlayed(val finalScore: Long, val finishedAt: Long) : AttemptStatus
}

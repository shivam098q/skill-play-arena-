package com.example.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.model.AttemptStatus
import com.example.model.GameAttempt
import com.example.model.GameType
import com.example.model.LeaderboardEntry
import com.example.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class FirestoreService(private val context: Context) {
    private val db: FirebaseFirestore = FirebaseManager.firestore
    private val prefs: SharedPreferences =
        context.getSharedPreferences("skillplay_cache", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "FirestoreService"

        fun getServerDateKey(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Kolkata")
            }
            return sdf.format(Date())
        }

        fun getMillisUntilMidnightIst(): Long {
            val tz = TimeZone.getTimeZone("Asia/Kolkata")
            val cal = Calendar.getInstance(tz)
            val now = cal.timeInMillis
            cal.set(Calendar.HOUR_OF_DAY, 24)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return maxOf(0L, cal.timeInMillis - now)
        }
    }

    private fun getAttemptDocId(uid: String, gameId: String, dateKey: String): String {
        return "${uid}_${gameId}_${dateKey}"
    }

    suspend fun checkDailyAttemptStatus(uid: String, gameType: GameType): AttemptStatus {
        val dateKey = getServerDateKey()
        val docId = getAttemptDocId(uid, gameType.id, dateKey)

        // Check local cache first for instant UI response
        val localSubmitted = prefs.getBoolean("${docId}_submitted", false)
        val localScore = prefs.getLong("${docId}_score", 0L)
        val localTime = prefs.getLong("${docId}_time", 0L)
        if (localSubmitted) {
            return AttemptStatus.AlreadyPlayed(localScore, localTime)
        }

        return try {
            val doc = db.collection("gameAttempts").document(docId).get().await()
            if (doc.exists()) {
                val submitted = doc.getBoolean("submitted") ?: false
                val score = doc.getLong("finalScore") ?: 0L
                val finishedAt = doc.getLong("finishedAt") ?: System.currentTimeMillis()
                if (submitted) {
                    prefs.edit()
                        .putBoolean("${docId}_submitted", true)
                        .putLong("${docId}_score", score)
                        .putLong("${docId}_time", finishedAt)
                        .apply()
                    AttemptStatus.AlreadyPlayed(score, finishedAt)
                } else {
                    val attempt = GameAttempt(
                        attemptId = docId,
                        uid = uid,
                        gameId = gameType.id,
                        dateKey = dateKey,
                        startedAt = doc.getLong("startedAt") ?: System.currentTimeMillis(),
                        rewardAdsWatched = doc.getLong("rewardAdsWatched")?.toInt() ?: 0,
                        submitted = false
                    )
                    AttemptStatus.InProgress(attempt)
                }
            } else {
                AttemptStatus.NotPlayed
            }
        } catch (e: Exception) {
            Log.w(TAG, "checkDailyAttemptStatus error: ${e.message}")
            if (localSubmitted) {
                AttemptStatus.AlreadyPlayed(localScore, localTime)
            } else {
                AttemptStatus.NotPlayed
            }
        }
    }

    suspend fun startDailyAttempt(uid: String, gameType: GameType): Result<GameAttempt> {
        val dateKey = getServerDateKey()
        val docId = getAttemptDocId(uid, gameType.id, dateKey)

        val newAttempt = GameAttempt(
            attemptId = docId,
            uid = uid,
            gameId = gameType.id,
            dateKey = dateKey,
            startedAt = System.currentTimeMillis(),
            rewardAdsWatched = 0,
            submitted = false
        )

        return try {
            val docRef = db.collection("gameAttempts").document(docId)
            val snapshot = docRef.get().await()
            if (snapshot.exists() && snapshot.getBoolean("submitted") == true) {
                Result.failure(IllegalStateException("Already played today! Resets at 12:00 AM IST."))
            } else {
                if (!snapshot.exists()) {
                    docRef.set(newAttempt).await()
                }
                Result.success(newAttempt)
            }
        } catch (e: Exception) {
            Log.w(TAG, "startDailyAttempt remote error, using local fallback", e)
            Result.success(newAttempt)
        }
    }

    suspend fun submitFinalScore(
        attemptId: String,
        uid: String,
        displayName: String,
        gameType: GameType,
        baseScore: Long,
        rewardAdsWatched: Int,
        rewardBonus: Long,
        finalScore: Long
    ): Result<Unit> {
        val dateKey = getServerDateKey()
        val now = System.currentTimeMillis()

        // Cache locally first
        prefs.edit()
            .putBoolean("${attemptId}_submitted", true)
            .putLong("${attemptId}_score", finalScore)
            .putLong("${attemptId}_time", now)
            .apply()

        // Cache best score locally
        val prevBest = prefs.getLong("best_${gameType.id}", 0L)
        if (finalScore > prevBest) {
            prefs.edit().putLong("best_${gameType.id}", finalScore).apply()
        }
        val totalPlayed = prefs.getLong("total_games_played", 0L) + 1
        prefs.edit().putLong("total_games_played", totalPlayed).apply()

        return try {
            val attemptRef = db.collection("gameAttempts").document(attemptId)
            val userRef = db.collection("users").document(uid)
            val leaderboardRef = db.collection("leaderboards")
                .document(gameType.id)
                .collection("players")
                .document(uid)

            val attemptData = mapOf(
                "attemptId" to attemptId,
                "uid" to uid,
                "gameId" to gameType.id,
                "dateKey" to dateKey,
                "finishedAt" to now,
                "baseScore" to baseScore,
                "rewardAdsWatched" to rewardAdsWatched,
                "rewardTimeOrRevives" to rewardBonus,
                "finalScore" to finalScore,
                "submitted" to true
            )

            attemptRef.set(attemptData, SetOptions.merge()).await()

            // Update user profile
            val userDoc = userRef.get().await()
            val currentBest = (userDoc.get("bestScores.${gameType.id}") as? Number)?.toLong() ?: 0L
            val newBest = maxOf(currentBest, finalScore)

            val userUpdates = mutableMapOf<String, Any>(
                "gamesPlayed" to ((userDoc.getLong("gamesPlayed") ?: 0L) + 1),
                "lastLoginAt" to now,
                "bestScores.${gameType.id}" to newBest
            )
            userRef.set(userUpdates, SetOptions.merge()).await()

            // Update leaderboard if higher
            val lbDoc = leaderboardRef.get().await()
            val existingLbScore = lbDoc.getLong("score") ?: 0L
            if (finalScore > existingLbScore || !lbDoc.exists()) {
                val lbData = mapOf(
                    "uid" to uid,
                    "displayName" to displayName,
                    "score" to finalScore,
                    "updatedAt" to now
                )
                leaderboardRef.set(lbData, SetOptions.merge()).await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "submitFinalScore remote failed, cached locally", e)
            Result.success(Unit)
        }
    }

    fun getLeaderboardFlow(gameType: GameType): Flow<List<LeaderboardEntry>> = callbackFlow {
        val query = db.collection("leaderboards")
            .document(gameType.id)
            .collection("players")
            .orderBy("score", Query.Direction.DESCENDING)
            .limit(50)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Leaderboard listener error: ${error.message}")
                trySend(getFallbackLeaderboard(gameType))
                return@addSnapshotListener
            }

            if (snapshot != null && !snapshot.isEmpty) {
                val entries = snapshot.documents.mapIndexed { index, doc ->
                    LeaderboardEntry(
                        uid = doc.getString("uid") ?: doc.id,
                        displayName = doc.getString("displayName") ?: "Pro Player",
                        photoURL = doc.getString("photoURL"),
                        score = doc.getLong("score") ?: 0L,
                        updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                        rank = index + 1
                    )
                }
                trySend(entries)
            } else {
                trySend(getFallbackLeaderboard(gameType))
            }
        }

        awaitClose { listener.remove() }
    }

    suspend fun syncUserProfile(user: UserProfile) {
        try {
            val userRef = db.collection("users").document(user.uid)
            val doc = userRef.get().await()
            if (!doc.exists()) {
                userRef.set(user).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "displayName" to user.displayName,
                    "email" to user.email,
                    "lastLoginAt" to System.currentTimeMillis()
                )
                if (user.phoneNumber.isNotBlank()) {
                    updates["phoneNumber"] = user.phoneNumber
                }
                userRef.update(updates).await()
            }
        } catch (e: Exception) {
            Log.w(TAG, "syncUserProfile failed: ${e.message}")
        }
    }

    suspend fun fetchUserProfile(uid: String): UserProfile? {
        return try {
            val doc = db.collection("users").document(uid).get().await()
            if (doc.exists()) {
                UserProfile(
                    uid = doc.getString("uid") ?: uid,
                    displayName = doc.getString("displayName") ?: "Player",
                    email = doc.getString("email") ?: "",
                    phoneNumber = doc.getString("phoneNumber") ?: "",
                    photoURL = doc.getString("photoURL"),
                    provider = doc.getString("provider") ?: "password",
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                    lastLoginAt = doc.getLong("lastLoginAt") ?: System.currentTimeMillis()
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchUserProfile error: ${e.message}")
            null
        }
    }

    suspend fun deleteUserData(uid: String): Result<Unit> {
        return try {
            // 1. Delete user profile document
            db.collection("users").document(uid).delete().await()

            // 2. Delete user's leaderboard scores across all games
            for (gameType in GameType.entries) {
                try {
                    db.collection("leaderboards")
                        .document(gameType.id)
                        .collection("players")
                        .document(uid)
                        .delete()
                        .await()
                } catch (e: Exception) {
                    Log.w(TAG, "Leaderboard delete warning for ${gameType.id}: ${e.message}")
                }
            }

            // 3. Delete user's game attempt records
            try {
                val attemptsSnapshot = db.collection("gameAttempts")
                    .whereEqualTo("uid", uid)
                    .get()
                    .await()
                for (doc in attemptsSnapshot.documents) {
                    doc.reference.delete().await()
                }
            } catch (e: Exception) {
                Log.w(TAG, "gameAttempts delete warning: ${e.message}")
            }

            // 4. Clear local preferences / cache
            prefs.edit().clear().apply()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deleteUserData failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun getLocalBestScore(gameType: GameType): Long {
        return prefs.getLong("best_${gameType.id}", 0L)
    }

    fun getLocalTotalGamesPlayed(): Long {
        return prefs.getLong("total_games_played", 0L)
    }

    private fun getFallbackLeaderboard(gameType: GameType): List<LeaderboardEntry> {
        val now = System.currentTimeMillis()
        val baseScores = when (gameType) {
            GameType.TAP_SPEED -> listOf(782L, 745L, 712L, 680L, 642L, 610L, 570L, 530L, 490L, 450L)
            GameType.CAR_ENDLESS -> listOf(1480L, 1320L, 1190L, 1050L, 940L, 880L, 760L, 690L, 610L, 520L)
            GameType.FLAPPY -> listOf(84L, 76L, 71L, 65L, 58L, 52L, 47L, 41L, 36L, 30L)
            GameType.JUMP_RUN -> listOf(2450L, 2210L, 2040L, 1890L, 1720L, 1550L, 1380L, 1200L, 1050L, 890L)
        }
        val names = listOf(
            "AeroStriker", "NeonValkyrie", "ApexKashyap", "QuantumBlade", "CyberWolf",
            "TitanPulse", "VortexRider", "ShadowStrike", "HyperFalcon", "IronGlitch"
        )
        return baseScores.mapIndexed { index, score ->
            LeaderboardEntry(
                uid = "bot_$index",
                displayName = names.getOrElse(index) { "Challenger_${index + 1}" },
                score = score,
                updatedAt = now - (index * 3600000L),
                rank = index + 1
            )
        }
    }
}

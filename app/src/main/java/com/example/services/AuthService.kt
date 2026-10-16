package com.example.services

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.example.model.UserProfile
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthService(private val context: Context) {
    private val auth: FirebaseAuth = FirebaseManager.auth
    private val firestoreService = FirestoreService(context)
    private val sessionPrefs = context.getSharedPreferences("skillplay_auth_session", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "AuthService"
        // From google-services.json oauth_client
        const val WEB_CLIENT_ID = "704241725277-7i3j5fp0128klf0sunsi6toogbrbe2s4.apps.googleusercontent.com"
    }

    sealed class DeleteAccountResult {
        object Success : DeleteAccountResult()
        object RequiresPasswordReauth : DeleteAccountResult()
        object RequiresGoogleReauth : DeleteAccountResult()
        data class Failure(val errorMessage: String) : DeleteAccountResult()
    }

    fun getFirebaseCurrentUser(): FirebaseUser? = auth.currentUser

    val currentUserFlow: Flow<UserProfile?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                if (user.isAnonymous) {
                    signOut()
                    trySend(null)
                    return@AuthStateListener
                }
                val profile = mapFirebaseUserToProfile(user)
                persistSession(profile)
                trySend(profile)
            } else {
                val persisted = getPersistedSession()
                if (persisted != null) {
                    trySend(persisted)
                } else {
                    trySend(null)
                }
            }
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    private fun persistSession(profile: UserProfile) {
        sessionPrefs.edit()
            .putString("uid", profile.uid)
            .putString("displayName", profile.displayName)
            .putString("email", profile.email)
            .putString("phoneNumber", profile.phoneNumber)
            .putString("photoURL", profile.photoURL ?: "")
            .putString("provider", profile.provider)
            .putLong("createdAt", profile.createdAt)
            .putLong("lastLoginAt", profile.lastLoginAt)
            .apply()
    }

    private fun getPersistedSession(): UserProfile? {
        val uid = sessionPrefs.getString("uid", null) ?: return null
        if (uid.isBlank() || uid.startsWith("offline_guest_")) {
            sessionPrefs.edit().clear().apply()
            return null
        }
        val provider = sessionPrefs.getString("provider", "password") ?: "password"
        if (provider == "anonymous") {
            sessionPrefs.edit().clear().apply()
            return null
        }
        return UserProfile(
            uid = uid,
            displayName = sessionPrefs.getString("displayName", "Player") ?: "Player",
            email = sessionPrefs.getString("email", "") ?: "",
            phoneNumber = sessionPrefs.getString("phoneNumber", "") ?: "",
            photoURL = sessionPrefs.getString("photoURL", null)?.takeIf { it.isNotBlank() },
            provider = provider,
            createdAt = sessionPrefs.getLong("createdAt", System.currentTimeMillis()),
            lastLoginAt = sessionPrefs.getLong("lastLoginAt", System.currentTimeMillis()),
            gamesPlayed = firestoreService.getLocalTotalGamesPlayed()
        )
    }

    fun getCurrentUser(): UserProfile? {
        val user = auth.currentUser
        if (user != null) {
            if (user.isAnonymous) {
                signOut()
                return null
            }
            val profile = mapFirebaseUserToProfile(user)
            persistSession(profile)
            return profile
        }
        return getPersistedSession()
    }

    private fun mapFirebaseUserToProfile(user: FirebaseUser): UserProfile {
        val cachedPhone = sessionPrefs.getString("phoneNumber", "") ?: ""
        val cachedName = sessionPrefs.getString("displayName", "") ?: ""
        val displayName = when {
            !user.displayName.isNullOrBlank() -> user.displayName!!
            cachedName.isNotBlank() -> cachedName
            !user.email.isNullOrBlank() -> user.email!!.substringBefore("@")
            else -> "Player"
        }
        return UserProfile(
            uid = user.uid,
            displayName = displayName,
            email = user.email ?: "",
            phoneNumber = user.phoneNumber ?: cachedPhone,
            photoURL = user.photoUrl?.toString(),
            provider = user.providerData.firstOrNull()?.providerId ?: "password",
            createdAt = user.metadata?.creationTimestamp ?: System.currentTimeMillis(),
            lastLoginAt = user.metadata?.lastSignInTimestamp ?: System.currentTimeMillis(),
            gamesPlayed = firestoreService.getLocalTotalGamesPlayed()
        )
    }

    suspend fun signInWithEmail(email: String, pass: String): Result<UserProfile> {
        return try {
            val result = auth.signInWithEmailAndPassword(email.trim(), pass).await()
            val user = result.user ?: throw IllegalStateException("User is null")
            var profile = mapFirebaseUserToProfile(user)
            // Fetch any remote profile data such as phone number and custom display name
            val remoteProfile = firestoreService.fetchUserProfile(user.uid)
            if (remoteProfile != null) {
                profile = profile.copy(
                    displayName = if (remoteProfile.displayName.isNotBlank()) remoteProfile.displayName else profile.displayName,
                    phoneNumber = if (remoteProfile.phoneNumber.isNotBlank()) remoteProfile.phoneNumber else profile.phoneNumber
                )
            }
            persistSession(profile)
            firestoreService.syncUserProfile(profile)
            Result.success(profile)
        } catch (e: Exception) {
            Log.e(TAG, "signInWithEmail failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun signUpWithEmail(
        name: String,
        phoneNumber: String,
        email: String,
        pass: String
    ): Result<UserProfile> {
        return try {
            val cleanName = name.trim().take(12)
            val cleanPhone = phoneNumber.trim()
            val cleanEmail = email.trim()
            val result = auth.createUserWithEmailAndPassword(cleanEmail, pass).await()
            val user = result.user ?: throw IllegalStateException("User is null")
            val changeReq = userProfileChangeRequest {
                displayName = cleanName
            }
            user.updateProfile(changeReq).await()
            val profile = mapFirebaseUserToProfile(user).copy(
                displayName = cleanName,
                phoneNumber = cleanPhone
            )
            persistSession(profile)
            firestoreService.syncUserProfile(profile)
            Result.success(profile)
        } catch (e: Exception) {
            Log.e(TAG, "signUpWithEmail failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun signUpWithEmail(name: String, email: String, pass: String): Result<UserProfile> {
        return signUpWithEmail(name = name, phoneNumber = "", email = email, pass = pass)
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email.trim()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reauthenticateWithEmail(password: String): Result<Unit> {
        return try {
            val user = auth.currentUser ?: throw IllegalStateException("No authenticated user")
            val email = user.email ?: throw IllegalStateException("User email is not available")
            val credential = EmailAuthProvider.getCredential(email, password)
            user.reauthenticate(credential).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "reauthenticateWithEmail failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun reauthenticateWithGoogle(context: Context): Result<Unit> {
        return try {
            val user = auth.currentUser ?: throw IllegalStateException("No authenticated user")
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response: GetCredentialResponse = credentialManager.getCredential(
                context = context,
                request = request
            )

            val credential = response.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                user.reauthenticate(authCredential).await()
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Failed to obtain Google credentials"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "reauthenticateWithGoogle error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun performAccountDeletion(
        context: Context,
        firestoreService: FirestoreService
    ): DeleteAccountResult {
        val user = auth.currentUser ?: return DeleteAccountResult.Failure("No user is currently signed in.")
        val uid = user.uid
        val provider = user.providerData.firstOrNull { it.providerId != "firebase" }?.providerId ?: "password"

        val lastSignIn = user.metadata?.lastSignInTimestamp ?: 0L
        val isRecent = (System.currentTimeMillis() - lastSignIn) < 3 * 60 * 1000L

        if (!isRecent) {
            return if (provider == "google.com") {
                DeleteAccountResult.RequiresGoogleReauth
            } else {
                DeleteAccountResult.RequiresPasswordReauth
            }
        }

        return executeDeletion(user, uid, firestoreService)
    }

    suspend fun executeDeletion(
        user: FirebaseUser,
        uid: String,
        firestoreService: FirestoreService
    ): DeleteAccountResult {
        return try {
            // 1. Delete user-related Firestore data while authenticated
            firestoreService.deleteUserData(uid)

            // 2. Delete Firebase Auth account
            user.delete().await()

            // 3. Clear local auth session state
            signOut()

            DeleteAccountResult.Success
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            val provider = user.providerData.firstOrNull { it.providerId != "firebase" }?.providerId ?: "password"
            if (provider == "google.com") {
                DeleteAccountResult.RequiresGoogleReauth
            } else {
                DeleteAccountResult.RequiresPasswordReauth
            }
        } catch (e: Exception) {
            Log.e(TAG, "executeDeletion error: ${e.message}", e)
            DeleteAccountResult.Failure(e.localizedMessage ?: "Failed to delete account.")
        }
    }

    suspend fun signInWithGoogle(context: Context): Result<UserProfile> {
        return try {
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response: GetCredentialResponse = credentialManager.getCredential(
                context = context,
                request = request
            )

            val credential = response.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(authCredential).await()
                val user = authResult.user ?: throw IllegalStateException("User null after Google sign in")
                val profile = mapFirebaseUserToProfile(user)
                firestoreService.syncUserProfile(profile)
                Result.success(profile)
            } else {
                Result.failure(IllegalStateException("Unsupported credential type"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google sign in error: ${e.message}")
            Result.failure(e)
        }
    }

    fun signOut() {
        try {
            auth.signOut()
            sessionPrefs.edit().clear().apply()
        } catch (e: Exception) {
            Log.e(TAG, "Sign out error", e)
            sessionPrefs.edit().clear().apply()
        }
    }
}

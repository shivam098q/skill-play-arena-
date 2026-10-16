package com.example.services

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

object FirebaseManager {
    private var isInitialized = false

    fun ensureInitialized(context: Context) {
        if (!isInitialized) {
            try {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    FirebaseApp.initializeApp(context)
                }
                // Configure offline persistence
                val db = FirebaseFirestore.getInstance()
                val settings = FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .build()
                db.firestoreSettings = settings
                isInitialized = true
            } catch (e: Exception) {
                // If already initialized or config loaded from google-services.json
                isInitialized = true
            }
        }
    }

    val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()
}

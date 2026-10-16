package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.games.carendless.CarEndlessGameScreen
import com.example.games.flappy.FlappyGameScreen
import com.example.games.jumprun.JumpRunGameScreen
import com.example.games.tapspeed.TapSpeedGameScreen
import com.example.model.GameAttempt
import com.example.model.GameType
import com.example.model.UserProfile
import com.example.services.AdMobService
import com.example.services.AuthService
import com.example.services.FirebaseManager
import com.example.services.FirestoreService
import com.example.services.NetworkMonitor
import com.example.ui.auth.AuthScreen
import com.example.ui.components.NetworkStatusBanner
import com.example.ui.home.HomeScreen
import com.example.ui.leaderboard.LeaderboardScreen
import com.example.ui.profile.ProfileScreen
import com.example.ui.rules.RulesScreen
import com.example.ui.theme.CyberDarkBg
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

enum class MainDestination(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.SportsEsports),
    LEADERBOARD("Ranks", Icons.Default.Leaderboard),
    RULES("Rules", Icons.Default.Gavel),
    PROFILE("Profile", Icons.Default.Person)
}

class MainActivity : ComponentActivity() {
    private lateinit var adMobService: AdMobService
    private lateinit var authService: AuthService
    private lateinit var firestoreService: FirestoreService
    private lateinit var networkMonitor: NetworkMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        FirebaseManager.ensureInitialized(this)
        adMobService = AdMobService(this)
        authService = AuthService(this)
        firestoreService = FirestoreService(this)
        networkMonitor = NetworkMonitor(this)

        setContent {
            MyApplicationTheme {
                val coroutineScope = rememberCoroutineScope()
                val isOnline by networkMonitor.isOnline.collectAsState(initial = true)
                var sessionAuthenticated by remember { mutableStateOf(false) }
                var currentUser by remember { mutableStateOf<UserProfile?>(null) }

                // Observe auth state changes
                LaunchedEffect(Unit) {
                    authService.currentUserFlow.collect { user ->
                        if (sessionAuthenticated) {
                            currentUser = user
                        }
                    }
                }

                // Active Game State
                var activeGameType by remember { mutableStateOf<GameType?>(null) }
                var activeAttempt by remember { mutableStateOf<GameAttempt?>(null) }
                var currentTab by remember { mutableStateOf(MainDestination.HOME) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (!sessionAuthenticated || currentUser == null) Color.White else CyberDarkBg
                ) {
                    if (!sessionAuthenticated || currentUser == null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AuthScreen(
                                authService = authService,
                                onAuthSuccess = {
                                    sessionAuthenticated = true
                                    currentUser = authService.getCurrentUser()
                                }
                            )
                            NetworkStatusBanner(
                                isOnline = isOnline,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .align(Alignment.TopCenter)
                            )
                        }
                    } else if (activeGameType == GameType.JUMP_RUN && activeAttempt != null) {
                        // Full-screen edge-to-edge presentation for Jump & Run
                        JumpRunGameScreen(
                            user = currentUser!!,
                            attempt = activeAttempt!!,
                            adMobService = adMobService,
                            firestoreService = firestoreService,
                            onBack = {
                                activeGameType = null
                                activeAttempt = null
                            },
                            onScoreSubmitted = { score ->
                                Toast.makeText(
                                    this@MainActivity,
                                    "Official Score $score Submitted!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                activeGameType = null
                                activeAttempt = null
                                currentTab = MainDestination.LEADERBOARD
                            }
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                        ) {
                            NetworkStatusBanner(isOnline = isOnline)

                            if (activeGameType != null && activeAttempt != null) {
                                // Active Game View
                                when (activeGameType!!) {
                                    GameType.TAP_SPEED -> {
                                        TapSpeedGameScreen(
                                            user = currentUser!!,
                                            attempt = activeAttempt!!,
                                            adMobService = adMobService,
                                            firestoreService = firestoreService,
                                            onBack = {
                                                activeGameType = null
                                                activeAttempt = null
                                            },
                                            onScoreSubmitted = { score ->
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "Official Score $score Submitted!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                activeGameType = null
                                                activeAttempt = null
                                                currentTab = MainDestination.LEADERBOARD
                                            }
                                        )
                                    }
                                    GameType.CAR_ENDLESS -> {
                                        CarEndlessGameScreen(
                                            user = currentUser!!,
                                            attempt = activeAttempt!!,
                                            adMobService = adMobService,
                                            firestoreService = firestoreService,
                                            onBack = {
                                                activeGameType = null
                                                activeAttempt = null
                                            },
                                            onScoreSubmitted = { score ->
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "Official Score $score Submitted!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                activeGameType = null
                                                activeAttempt = null
                                                currentTab = MainDestination.LEADERBOARD
                                            }
                                        )
                                    }
                                    GameType.FLAPPY -> {
                                        FlappyGameScreen(
                                            user = currentUser!!,
                                            attempt = activeAttempt!!,
                                            adMobService = adMobService,
                                            firestoreService = firestoreService,
                                            onBack = {
                                                activeGameType = null
                                                activeAttempt = null
                                            },
                                            onScoreSubmitted = { score ->
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "Official Score $score Submitted!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                activeGameType = null
                                                activeAttempt = null
                                                currentTab = MainDestination.LEADERBOARD
                                            }
                                        )
                                    }
                                    GameType.JUMP_RUN -> {
                                        // Handled above in root full-screen branch
                                    }
                                }
                            } else {
                            // Primary App Navigation
                            Scaffold(
                                bottomBar = {
                                    NavigationBar(
                                        containerColor = CyberSurface,
                                        contentColor = NeonCyan,
                                        modifier = Modifier.navigationBarsPadding()
                                    ) {
                                        MainDestination.entries.forEach { dest ->
                                            val selected = currentTab == dest
                                            NavigationBarItem(
                                                selected = selected,
                                                onClick = { currentTab = dest },
                                                icon = {
                                                    Icon(
                                                        imageVector = dest.icon,
                                                        contentDescription = dest.title,
                                                        tint = if (selected) NeonCyan else TextSecondary
                                                    )
                                                },
                                                label = {
                                                    Text(
                                                        text = dest.title,
                                                        color = if (selected) NeonCyan else TextSecondary
                                                    )
                                                },
                                                colors = NavigationBarItemDefaults.colors(
                                                    indicatorColor = NeonCyan.copy(alpha = 0.2f)
                                                ),
                                                modifier = Modifier.testTag("nav_item_${dest.name.lowercase()}")
                                            )
                                        }
                                    }
                                },
                                containerColor = CyberDarkBg
                            ) { innerPadding ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding)
                                ) {
                                    when (currentTab) {
                                        MainDestination.HOME -> {
                                            HomeScreen(
                                                user = currentUser!!,
                                                firestoreService = firestoreService,
                                                onSelectGame = { gameType ->
                                                    coroutineScope.launch {
                                                        val result = firestoreService.startDailyAttempt(
                                                            currentUser!!.uid,
                                                            gameType
                                                        )
                                                        result.fold(
                                                            onSuccess = { attempt ->
                                                                activeAttempt = attempt
                                                                activeGameType = gameType
                                                            },
                                                            onFailure = { err ->
                                                                Toast.makeText(
                                                                    this@MainActivity,
                                                                    err.localizedMessage ?: "Failed to start attempt",
                                                                    Toast.LENGTH_LONG
                                                                ).show()
                                                            }
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                        MainDestination.LEADERBOARD -> {
                                            LeaderboardScreen(
                                                user = currentUser!!,
                                                firestoreService = firestoreService
                                            )
                                        }
                                        MainDestination.RULES -> {
                                            RulesScreen()
                                        }
                                        MainDestination.PROFILE -> {
                                            ProfileScreen(
                                                user = currentUser!!,
                                                authService = authService,
                                                firestoreService = firestoreService,
                                                onSignOut = {
                                                    sessionAuthenticated = false
                                                    currentUser = null
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}

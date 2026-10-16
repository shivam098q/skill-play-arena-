package com.example.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.services.AuthService
import com.example.ui.legal.PrivacyPolicyDialog
import kotlinx.coroutines.launch

// Color tokens for reference layout
private val HeaderBlueDark = Color(0xFF071739)
private val HeaderBlueMid = Color(0xFF0C2B68)
private val HeaderBlueBright = Color(0xFF154AA5)
private val ButtonBlueStart = Color(0xFF0F3485)
private val ButtonBlueEnd = Color(0xFF2563EB)
private val AccentGoldBright = Color(0xFFFACC15)
private val AccentGoldWarm = Color(0xFFFFD54F)
private val AccentGoldDeep = Color(0xFFF59E0B)
private val TextNavyDark = Color(0xFF0F172A)
private val TextSlateMuted = Color(0xFF64748B)
private val InputBorderLight = Color(0xFFE2E8F0)
private val InputBorderFocused = Color(0xFF1D4ED8)
private val IconBlueNavy = Color(0xFF1E40AF)
private val CardWhite = Color(0xFFFFFFFF)
private val PageBackground = Color(0xFFFFFFFF)
private val ErrorRed = Color(0xFFDC2626)
private val SuccessGreen = Color(0xFF16A34A)

@Composable
fun AuthScreen(
    authService: AuthService,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Login, 1: Sign Up
    var displayName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }
    var resetSuccessMessage by remember { mutableStateOf<String?>(null) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = selectedTab == 1) {
        selectedTab = 0
        errorMessage = null
    }

    fun formatAuthError(error: Throwable, isGoogle: Boolean = false): String {
        val msg = error.message.orEmpty()
        return when {
            isGoogle && (msg.contains("No credentials available", ignoreCase = true)) -> {
                "No Google account detected. Please ensure a Google account is logged into your device or sign in with Email."
            }
            isGoogle && (msg.contains("cancelled", ignoreCase = true) || msg.contains("canceled", ignoreCase = true) || error is androidx.credentials.exceptions.GetCredentialCancellationException) -> {
                "Google Sign-In canceled or authorization pending. If you just added the SHA-1 fingerprint in Firebase, please wait 5-10 minutes for Google servers to sync, or log in with Email/Password."
            }
            isGoogle -> {
                "Google sign in failed: ${error.localizedMessage ?: "Please try again or use Email."}"
            }
            msg.contains("invalid", ignoreCase = true) && (msg.contains("credential", ignoreCase = true) || msg.contains("password", ignoreCase = true) || msg.contains("auth", ignoreCase = true)) -> {
                "Invalid email or password. Please check your credentials or tap 'Sign Up' if you are new."
            }
            msg.contains("no user record", ignoreCase = true) || msg.contains("user-not-found", ignoreCase = true) -> {
                "No account found with this email. Tap 'Sign Up' below to create your account."
            }
            msg.contains("email-already-in-use", ignoreCase = true) || msg.contains("already exists", ignoreCase = true) -> {
                "This email is already registered. Please log in instead or tap 'Forgot Password?'."
            }
            msg.contains("network", ignoreCase = true) -> {
                "Network connection error. Please check your internet connection."
            }
            else -> {
                error.localizedMessage ?: "Authentication failed. Please check your details and try again."
            }
        }
    }

    fun handleAuth() {
        errorMessage = null
        if (selectedTab == 0) {
            // Sign In
            val cleanEmail = email.trim()
            if (cleanEmail.isBlank() || password.isBlank()) {
                errorMessage = "Please enter email and password."
                return
            }
            isLoading = true
            coroutineScope.launch {
                val result = authService.signInWithEmail(cleanEmail, password)
                isLoading = false
                result.fold(
                    onSuccess = { onAuthSuccess() },
                    onFailure = { errorMessage = formatAuthError(it) }
                )
            }
        } else {
            // Sign Up
            val cleanName = displayName.trim()
            if (cleanName.isBlank()) {
                errorMessage = "User Name is required."
                return
            }
            if (cleanName.length > 12) {
                errorMessage = "User Name cannot exceed 12 characters."
                return
            }
            val cleanPhone = phoneNumber.trim()
            if (cleanPhone.isBlank()) {
                errorMessage = "Phone number is required."
                return
            }
            if (cleanPhone.length < 10) {
                errorMessage = "Please enter a valid 10-digit phone number."
                return
            }
            val cleanEmail = email.trim()
            if (cleanEmail.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
                errorMessage = "Please enter a valid email address."
                return
            }
            if (password.length < 6) {
                errorMessage = "Password must be at least 6 characters."
                return
            }
            isLoading = true
            coroutineScope.launch {
                val result = authService.signUpWithEmail(cleanName, cleanPhone, cleanEmail, password)
                isLoading = false
                result.fold(
                    onSuccess = { onAuthSuccess() },
                    onFailure = { errorMessage = formatAuthError(it) }
                )
            }
        }
    }

    fun handleGoogleSignIn() {
        errorMessage = null
        isLoading = true
        coroutineScope.launch {
            val result = authService.signInWithGoogle(context)
            isLoading = false
            result.fold(
                onSuccess = { onAuthSuccess() },
                onFailure = { errorMessage = formatAuthError(it, isGoogle = true) }
            )
        }
    }

    val inputColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = InputBorderFocused,
        unfocusedBorderColor = InputBorderLight,
        focusedContainerColor = CardWhite,
        unfocusedContainerColor = CardWhite,
        focusedTextColor = TextNavyDark,
        unfocusedTextColor = TextNavyDark,
        focusedLabelColor = InputBorderFocused,
        unfocusedLabelColor = TextSlateMuted,
        cursorColor = InputBorderFocused
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(CardWhite)
            .imePadding()
    ) {
        val minCardHeight = maxHeight

        // Dark blue top fill behind status bar and header area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            HeaderBlueDark,
                            HeaderBlueMid
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // =========================================================================
            // 1. HEADER SECTION (Dark Blue Gradient, 3D Gift Box, Floating Coins, Title, Tagline)
            // =========================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                HeaderBlueDark,
                                HeaderBlueMid,
                                HeaderBlueBright
                            )
                        )
                    )
            ) {
                // Background decorative sparkle dots and floating light rings
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                ) {
                    // Soft glowing ambient circles
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f),
                        radius = size.width * 0.45f,
                        center = Offset(size.width * 0.85f, size.height * 0.25f)
                    )
                    drawCircle(
                        color = AccentGoldWarm.copy(alpha = 0.08f),
                        radius = size.width * 0.35f,
                        center = Offset(size.width * 0.15f, size.height * 0.70f)
                    )

                    // Sparkle dots around the top area
                    drawCircle(
                        color = AccentGoldBright.copy(alpha = 0.6f),
                        radius = 3.5.dp.toPx(),
                        center = Offset(size.width * 0.20f, size.height * 0.32f)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.7f),
                        radius = 2.5.dp.toPx(),
                        center = Offset(size.width * 0.82f, size.height * 0.22f)
                    )
                    drawCircle(
                        color = AccentGoldBright.copy(alpha = 0.5f),
                        radius = 3.dp.toPx(),
                        center = Offset(size.width * 0.76f, size.height * 0.58f)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.6f),
                        radius = 2.dp.toPx(),
                        center = Offset(size.width * 0.12f, size.height * 0.60f)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 18.dp, bottom = 32.dp, start = 20.dp, end = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Central 3D gift box illustration with floating 3D golden coins
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(126.dp)
                    ) {
                        // Ambient radial glow behind the gift box
                        Box(
                            modifier = Modifier
                                .size(118.dp)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            AccentGoldBright.copy(alpha = 0.35f),
                                            Color.Transparent
                                        )
                                    ),
                                    shape = CircleShape
                                )
                        )

                        // 3D Gift box image
                        Image(
                            painter = painterResource(id = R.drawable.img_auth_gift_box),
                            contentDescription = "3D Gift Box Illustration",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(20.dp))
                        )

                        // Floating 3D golden coin - Top Left
                        Surface(
                            shape = CircleShape,
                            color = AccentGoldDeep,
                            border = BorderStroke(1.5.dp, Color.White),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.TopStart)
                                .offset(x = 6.dp, y = 8.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "★",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Floating 3D golden coin - Bottom Right
                        Surface(
                            shape = CircleShape,
                            color = AccentGoldBright,
                            border = BorderStroke(1.5.dp, Color.White),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .size(26.dp)
                                .align(Alignment.BottomEnd)
                                .offset(x = (-4).dp, y = (-6).dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$",
                                    color = HeaderBlueDark,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        // Floating 3D golden coin - Top Right
                        Surface(
                            shape = CircleShape,
                            color = AccentGoldWarm,
                            border = BorderStroke(1.dp, Color.White),
                            modifier = Modifier
                                .size(18.dp)
                                .align(Alignment.TopEnd)
                                .offset(x = (-10).dp, y = 14.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = HeaderBlueDark,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Brand Title: "Skill Play Arena" (Bold White with Gold accent)
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(color = Color.White)) {
                                append("Skill Play ")
                            }
                            withStyle(style = SpanStyle(color = AccentGoldBright)) {
                                append("Arena")
                            }
                        },
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Tagline below brand title: "Earn More, Reward More" flanked by clean horizontal lines with dots
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        // Left dot
                        Box(
                            modifier = Modifier
                                .size(4.5.dp)
                                .background(AccentGoldBright, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        // Left line
                        Box(
                            modifier = Modifier
                                .width(34.dp)
                                .height(1.5.dp)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            AccentGoldBright.copy(alpha = 0.3f),
                                            AccentGoldBright
                                        )
                                    )
                                )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Tagline Text
                        Text(
                            text = "Earn More, Reward More",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.6.sp,
                            color = AccentGoldWarm
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Right line
                        Box(
                            modifier = Modifier
                                .width(34.dp)
                                .height(1.5.dp)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            AccentGoldBright,
                                            AccentGoldBright.copy(alpha = 0.3f)
                                        )
                                    )
                                )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        // Right dot
                        Box(
                            modifier = Modifier
                                .size(4.5.dp)
                                .background(AccentGoldBright, CircleShape)
                        )
                    }
                }
            }

            // =========================================================================
            // 2. LOGIN CARD SECTION (Bottom Sheet Overlay / Card with rounded top corners)
            // =========================================================================
            Surface(
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = CardWhite,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = minCardHeight)
                    .offset(y = (-16).dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 26.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp)
                        .widthIn(max = 480.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    // Heading: "Welcome Back"
                    Text(
                        text = if (selectedTab == 0) "Welcome Back" else "Create Account",
                        fontSize = 25.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextNavyDark
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Subheading: "Login to your account to continue"
                    Text(
                        text = if (selectedTab == 0) {
                            "Login to your account to continue"
                        } else {
                            "Sign up for your Skill Play Arena account"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextSlateMuted
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    // Extra fields for Sign Up mode (preserves account creation)
                    if (selectedTab == 1) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { if (it.length <= 12) displayName = it },
                            label = { Text("User Name") },
                            placeholder = { Text("Enter user name (max 12 chars)", color = TextSlateMuted.copy(alpha = 0.7f)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "User Name",
                                    tint = IconBlueNavy
                                )
                            },
                            trailingIcon = {
                                Text(
                                    text = "${displayName.length}/12",
                                    color = if (displayName.length == 12) SuccessGreen else TextSlateMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                            },
                            singleLine = true,
                            colors = inputColors,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("display_name_input"),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it.filter { ch -> ch.isDigit() }.take(10) },
                            label = { Text("Phone Number") },
                            placeholder = { Text("10-digit mobile number", color = TextSlateMuted.copy(alpha = 0.7f)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = "Phone Number",
                                    tint = IconBlueNavy
                                )
                            },
                            trailingIcon = {
                                Text(
                                    text = "${phoneNumber.length}/10",
                                    color = if (phoneNumber.length == 10) SuccessGreen else TextSlateMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                            },
                            singleLine = true,
                            colors = inputColors,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("phone_number_input"),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = ImeAction.Next
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Input Field 1: "Email or Phone Number" with envelope icon on left
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = {
                            Text(if (selectedTab == 0) "Email or Phone Number" else "Email Address")
                        },
                        placeholder = {
                            Text(
                                text = if (selectedTab == 0) "Email or Phone Number" else "Enter your email address",
                                color = TextSlateMuted.copy(alpha = 0.6f)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Email or Phone Number",
                                tint = IconBlueNavy
                            )
                        },
                        singleLine = true,
                        colors = inputColors,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("email_input"),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Input Field 2: "Password" with lock icon on left and visibility toggle on right
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        placeholder = {
                            Text("Password", color = TextSlateMuted.copy(alpha = 0.6f))
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Password",
                                tint = IconBlueNavy
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle password visibility",
                                    tint = IconBlueNavy
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        colors = inputColors,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input"),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            handleAuth()
                        })
                    )

                    // Action Link: "Forgot Password?" aligned to right below password field
                    if (selectedTab == 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            TextButton(
                                onClick = {
                                    resetEmail = email
                                    resetSuccessMessage = null
                                    showForgotPasswordDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Forgot Password?",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = InputBorderFocused
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Error Message Display
                    if (errorMessage != null) {
                        Surface(
                            color = ErrorRed.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = ErrorRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Primary Action Button: Full-width blue "Login" button with right arrow icon
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            handleAuth()
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            disabledContainerColor = InputBorderLight
                        ),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .shadow(3.dp, RoundedCornerShape(14.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(ButtonBlueStart, ButtonBlueEnd)
                                ),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .testTag("auth_submit_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = if (selectedTab == 0) "Login" else "Sign Up",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = AccentGoldBright,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Sign Up Link at the bottom: "Don't have an account? Sign Up ->"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (selectedTab == 0) {
                                "Don't have an account? "
                            } else {
                                "Already have an account? "
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextNavyDark
                        )
                        Text(
                            text = if (selectedTab == 0) "Sign Up ->" else "Login ->",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = InputBorderFocused,
                            modifier = Modifier
                                .clickable {
                                    selectedTab = if (selectedTab == 0) 1 else 0
                                    errorMessage = null
                                }
                                .padding(vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Privacy Policy link
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "By continuing, you agree to our ",
                            fontSize = 12.sp,
                            color = TextSlateMuted
                        )
                        Text(
                            text = "Privacy Policy",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = InputBorderFocused,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier
                                .clickable { showPrivacyPolicyDialog = true }
                                .padding(vertical = 4.dp)
                                .testTag("auth_privacy_policy_link")
                        )
                    }
                }
            }
        }
    }

    // Forgot Password Dialog (Clean White & Royal Blue Theme)
    if (showForgotPasswordDialog) {
        Dialog(onDismissRequest = { showForgotPasswordDialog = false }) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, InputBorderLight),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Reset Password",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextNavyDark
                    )

                    Text(
                        text = "Enter your registered email address to receive password reset instructions.",
                        fontSize = 13.sp,
                        color = TextSlateMuted,
                        textAlign = TextAlign.Center
                    )

                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text("Email") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Email",
                                tint = IconBlueNavy
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = inputColors
                    )

                    if (resetSuccessMessage != null) {
                        Text(
                            text = resetSuccessMessage!!,
                            color = SuccessGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TextButton(
                            onClick = { showForgotPasswordDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Cancel",
                                color = TextSlateMuted,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = {
                                if (resetEmail.isNotBlank()) {
                                    coroutineScope.launch {
                                        val res = authService.sendPasswordReset(resetEmail)
                                        res.fold(
                                            onSuccess = {
                                                resetSuccessMessage = "Reset link sent! Check your inbox."
                                            },
                                            onFailure = {
                                                resetSuccessMessage = it.localizedMessage ?: "Failed to send reset link."
                                            }
                                        )
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = InputBorderFocused),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Send",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPrivacyPolicyDialog) {
        PrivacyPolicyDialog(onDismiss = { showPrivacyPolicyDialog = false })
    }
}

@Composable
private fun GoogleLogoIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.22f
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
        val arcSize = Size(diameter, diameter)

        // Red top arc
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = -145f,
            sweepAngle = 105f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Yellow left arc
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 140f,
            sweepAngle = 75f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Green bottom arc
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 45f,
            sweepAngle = 95f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Blue right arc
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -10f,
            sweepAngle = 55f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )
        // Blue horizontal crossbar of the "G"
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(size.width * 0.50f, size.height * 0.50f),
            end = Offset(size.width * 0.94f, size.height * 0.50f),
            strokeWidth = strokeWidth * 0.95f,
            cap = StrokeCap.Butt
        )
    }
}

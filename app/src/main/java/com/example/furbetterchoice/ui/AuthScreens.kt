package com.example.furbetterchoice.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.furbetterchoice.R
import com.example.furbetterchoice.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
private fun AuthBackground() {
    Box(modifier = Modifier.fillMaxSize()) {
        // Soft Top Green Vertical Gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF479C73).copy(alpha = 0.42f),
                            Color(0xFF479C73).copy(alpha = 0.22f),
                            Color(0xFF479C73).copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )
        // Subtle Ambient Decorative Orb (Top Right)
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.TopEnd)
                .offset(x = 70.dp, y = (-40).dp)
                .clip(CircleShape)
                .background(Color(0xFF479C73).copy(alpha = 0.10f))
        )
        // Subtle Ambient Decorative Orb (Bottom Left)
        Box(
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-60).dp, y = 50.dp)
                .clip(CircleShape)
                .background(Color(0xFF479C73).copy(alpha = 0.07f))
        )
    }
}

// ==========================================
// 1. LOGIN SCREEN
// ==========================================
@Composable
fun LoginScreen(
    onNavigateToSignUp: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onLoginSuccess: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    var showWelcomeOverlay by remember { mutableStateOf(false) }
    var loggedInUserName by remember { mutableStateOf("") }

    val isDarkMode = isSystemInDarkTheme()
    val boxColor = if (isDarkMode) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f) else Color(0xFFE4EAE8)
    val highlightColor = if (isDarkMode) MaterialTheme.colorScheme.outline.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.7f)
    val boxBorder = BorderStroke(
        width = 1.dp,
        brush = Brush.horizontalGradient(listOf(boxColor, highlightColor, boxColor))
    )

    val auth = remember {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    var showHeader by remember { mutableStateOf(false) }
    var showCard by remember { mutableStateOf(false) }
    var showFooter by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(80)
        showHeader = true
        delay(120)
        showCard = true
        delay(120)
        showFooter = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AuthBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (onBack != null) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    CustomBackButton(onClick = onBack)
                }
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                Spacer(modifier = Modifier.height(28.dp))
            }

            StaggeredReveal(visible = showHeader) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = Color(0xFF479C73),
                        modifier = Modifier
                            .size(76.dp)
                            .subtleCardShadow(borderRadius = 28.dp),
                        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.35f))
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Image(
                                painter = painterResource(R.drawable.logo_transparent),
                                contentDescription = "FurBetterChoice Logo",
                                modifier = Modifier.size(56.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "Welcome Back",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 26.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Sign in to access your pet profiles & saved foods",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.5.sp
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            StaggeredReveal(visible = showCard) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .subtleCardShadow(borderRadius = 32.dp),
                    shape = RoundedCornerShape(32.dp),
                    color = boxColor,
                    border = boxBorder
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp)
                    ) {
                        Text(
                            text = "Email Address",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.5.sp
                            ),
                            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
                        )
                        AuthOutlinedTextField(
                            value = email,
                            onValueChange = { email = it; errorMessage = null },
                            label = "Enter your email",
                            leadingIcon = Icons.Default.Email,
                            isError = errorMessage != null && email.isBlank(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Password",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.5.sp
                                )
                            )
                            Text(
                                text = "Forgot Password?",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color(0xFF479C73),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                modifier = Modifier.bouncyClickable(onClick = onNavigateToForgotPassword)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        AuthOutlinedTextField(
                            value = password,
                            onValueChange = { password = it; errorMessage = null },
                            label = "Enter your password",
                            isPassword = true,
                            leadingIcon = Icons.Default.Lock,
                            isError = errorMessage != null && password.isBlank(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )

                        AnimatedVisibility(visible = errorMessage != null) {
                            errorMessage?.let { msg ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 14.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFF7A1129).copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, Color(0xFF7A1129).copy(alpha = 0.35f))
                                ) {
                                    Text(
                                        text = msg,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFF7A1129),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp
                                        ),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(22.dp))

                        Button(
                            onClick = {
                                if (auth == null) {
                                    errorMessage = "Authentication unavailable"
                                    return@Button
                                }
                                if (email.isBlank() || password.isBlank()) {
                                    errorMessage = "Please enter your email and password"
                                    return@Button
                                }
                                isLoading = true
                                auth.signInWithEmailAndPassword(email.trim(), password)
                                    .addOnCompleteListener { task ->
                                        isLoading = false
                                        if (task.isSuccessful) {
                                            onLoginSuccess()
                                        } else {
                                            errorMessage = when {
                                                task.exception?.message?.contains("password", ignoreCase = true) == true -> "Incorrect password. Please try again."
                                                task.exception?.message?.contains("no user", ignoreCase = true) == true -> "No account found with this email."
                                                else -> "Login failed. Please check your credentials."
                                            }
                                        }
                                    }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .subtleCardShadow(borderRadius = 100.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF479C73)),
                            shape = RoundedCornerShape(100.dp),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Text(
                                    text = "Log In",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 15.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            StaggeredReveal(visible = showFooter) {
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = boxColor.copy(alpha = 0.7f),
                    border = boxBorder,
                    modifier = Modifier.bouncyClickable(onClick = onNavigateToSignUp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Don't have an account? ",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Text(
                            text = "Sign Up",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.5.sp,
                                color = Color(0xFF479C73),
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }
                }
            }
        }

        // Welcome Back Overlay after successful login
        if (showWelcomeOverlay) {
            WelcomeBackOverlay(
                userName = loggedInUserName,
                onDismiss = {
                    showWelcomeOverlay = false
                    onLoginSuccess()
                }
            )
        }
    }
}

@Composable
fun WelcomeBackOverlay(
    userName: String,
    onDismiss: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.85f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "welcomeScale"
    )

    LaunchedEffect(Unit) {
        isVisible = true
        delay(3000)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .scale(scale)
                .clickable(enabled = false) { onDismiss() },
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp,
            border = BorderStroke(
                1.dp,
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        MaterialTheme.colorScheme.primary
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Image(
                            painter = painterResource(R.drawable.logo_transparent),
                            contentDescription = "FurBetterChoice Logo",
                            modifier = Modifier.size(52.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = if (userName.isNotBlank()) "Welcome back, $userName!" else "Welcome back!",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 22.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Redirecting you to homepage...",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Tap anywhere to continue",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

// ==========================================
// 2. SIGN UP SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    onNavigateToLogin: () -> Unit,
    onRegistrationSuccess: (email: String) -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var birthday by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }

    val isDarkMode = isSystemInDarkTheme()
    val boxColor = if (isDarkMode) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f) else Color(0xFFE4EAE8)
    val highlightColor = if (isDarkMode) MaterialTheme.colorScheme.outline.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.7f)
    val boxBorder = BorderStroke(
        width = 1.dp,
        brush = Brush.horizontalGradient(listOf(boxColor, highlightColor, boxColor))
    )

    val auth = remember {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    var showContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80)
        showContent = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AuthBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                CustomBackButton(onClick = onBack)
            }

            Spacer(modifier = Modifier.height(8.dp))

            StaggeredReveal(visible = showContent) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Create Account",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 26.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Join FurBetterChoice to find the best food for your pets",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .subtleCardShadow(borderRadius = 32.dp),
                        shape = RoundedCornerShape(32.dp),
                        color = boxColor,
                        border = boxBorder
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            AuthOutlinedTextField(
                                value = name,
                                onValueChange = { name = it; errorMessage = null },
                                label = "Full Name",
                                leadingIcon = Icons.Default.Person
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            AuthOutlinedTextField(
                                value = email,
                                onValueChange = { email = it; errorMessage = null },
                                label = "Email Address",
                                leadingIcon = Icons.Default.Email,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            AuthOutlinedTextField(
                                value = birthday,
                                onValueChange = {},
                                label = "Date of Birth",
                                leadingIcon = Icons.Default.DateRange,
                                readOnly = true,
                                onClick = { showDatePicker = true }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            AuthOutlinedTextField(
                                value = password,
                                onValueChange = { password = it; errorMessage = null },
                                label = "Password",
                                isPassword = true,
                                leadingIcon = Icons.Default.Lock,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                            )

                            PasswordRequirementsView(password)

                            Spacer(modifier = Modifier.height(10.dp))

                            AuthOutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it; errorMessage = null },
                                label = "Confirm Password",
                                isPassword = true,
                                leadingIcon = Icons.Default.Lock,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                            )

                            if (errorMessage != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFF7A1129).copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, Color(0xFF7A1129).copy(alpha = 0.35f))
                                ) {
                                    Text(
                                        text = errorMessage!!,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFF7A1129),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp
                                        ),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = {
                                    if (auth == null) {
                                        errorMessage = "Authentication service unavailable"
                                        return@Button
                                    }
                                    if (name.isBlank() || email.isBlank() || password.isBlank()) {
                                        errorMessage = "Please fill in all required fields"
                                        return@Button
                                    }
                                    if (password != confirmPassword) {
                                        errorMessage = "Passwords do not match"
                                        return@Button
                                    }
                                    isLoading = true
                                    auth.createUserWithEmailAndPassword(email.trim(), password)
                                        .addOnCompleteListener { task ->
                                            isLoading = false
                                            if (task.isSuccessful) {
                                                val user = auth.currentUser
                                                val uid = user?.uid
                                                if (uid != null) {
                                                    val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                                    val userMap = hashMapOf(
                                                        "userId" to uid,
                                                        "displayName" to name.trim(),
                                                        "email" to email.trim(),
                                                        "createdAt" to com.google.firebase.Timestamp.now()
                                                    )
                                                    firestore.collection("users")
                                                        .document(uid)
                                                        .set(userMap, com.google.firebase.firestore.SetOptions.merge())
                                                }
                                                user?.updateProfile(userProfileChangeRequest {
                                                    displayName = name.trim()
                                                })
                                                user?.sendEmailVerification()
                                                onRegistrationSuccess(email.trim())
                                            } else {
                                                errorMessage = task.exception?.message ?: "Registration failed"
                                            }
                                        }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .subtleCardShadow(borderRadius = 100.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF479C73)),
                                shape = RoundedCornerShape(100.dp),
                                enabled = !isLoading
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    Text(
                                        text = "Sign Up",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 15.sp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = boxColor.copy(alpha = 0.7f),
                        border = boxBorder,
                        modifier = Modifier.bouncyClickable(onClick = onNavigateToLogin)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Already have an account? ",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Text(
                                text = "Log In",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.5.sp,
                                    color = Color(0xFF479C73),
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        if (showDatePicker) {
            val calendar = remember { Calendar.getInstance() }
            val currentYear = remember { calendar.get(Calendar.YEAR) }
            val todayMillis = remember { System.currentTimeMillis() }

            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = try {
                    if (birthday.isNotBlank()) {
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(birthday)?.time ?: todayMillis
                    } else {
                        todayMillis
                    }
                } catch (e: Exception) {
                    todayMillis
                },
                selectableDates = object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                        return utcTimeMillis <= todayMillis
                    }
                    override fun isSelectableYear(year: Int): Boolean {
                        return year <= currentYear
                    }
                }
            )

            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            birthday = formatter.format(Date(millis))
                        }
                        showDatePicker = false
                    }) {
                        Text("OK", color = Color(0xFF479C73), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                DatePicker(
                    state = datePickerState,
                    colors = DatePickerDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = Color(0xFF479C73),
                        headlineContentColor = MaterialTheme.colorScheme.onSurface,
                        selectedDayContainerColor = Color(0xFF479C73),
                        selectedDayContentColor = Color.White,
                        todayContentColor = Color(0xFF479C73),
                        todayDateBorderColor = Color(0xFF479C73)
                    )
                )
            }
        }
    }
}

// ==========================================
// 3. EMAIL VERIFICATION SCREEN
// ==========================================
@Composable
fun EmailVerificationScreen(
    email: String,
    onVerified: () -> Unit,
    onBackToLogin: () -> Unit
) {
    var resendTimer by remember { mutableIntStateOf(60) }
    var canResend by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }

    val isDarkMode = isSystemInDarkTheme()
    val boxColor = if (isDarkMode) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f) else Color(0xFFE4EAE8)
    val highlightColor = if (isDarkMode) MaterialTheme.colorScheme.outline.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.7f)
    val boxBorder = BorderStroke(
        width = 1.dp,
        brush = Brush.horizontalGradient(listOf(boxColor, highlightColor, boxColor))
    )

    val auth = remember {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val user = auth?.currentUser
                user?.reload()?.addOnCompleteListener {
                    if (user.isEmailVerified) {
                        onVerified()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(canResend, resendTimer) {
        if (!canResend && resendTimer > 0) {
            delay(1000)
            resendTimer -= 1
            if (resendTimer == 0) {
                canResend = true
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AuthBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .subtleCardShadow(borderRadius = 32.dp),
                shape = RoundedCornerShape(32.dp),
                color = boxColor,
                border = boxBorder
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF479C73).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = Color(0xFF479C73),
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Verify Your Email",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 24.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "We sent a verification link to:\n$email\nPlease check your inbox and click the link to verify your account.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Button(
                        onClick = {
                            isChecking = true
                            val user = auth?.currentUser
                            user?.reload()?.addOnCompleteListener {
                                isChecking = false
                                if (user != null && user.isEmailVerified) {
                                    onVerified()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .subtleCardShadow(borderRadius = 100.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF479C73)),
                        shape = RoundedCornerShape(100.dp)
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Text(
                                "I've Verified My Email",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    TextButton(
                        onClick = {
                            if (canResend) {
                                auth?.currentUser?.sendEmailVerification()
                                canResend = false
                                resendTimer = 60
                            }
                        },
                        enabled = canResend
                    ) {
                        Text(
                            text = if (canResend) "Resend Verification Email" else "Resend email in ${resendTimer}s",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = if (canResend) Color(0xFF479C73) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(onClick = onBackToLogin) {
                        Text(
                            "Back to Log In",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. FORGOT PASSWORD SCREEN
// ==========================================
@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var isSubmitted by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val isDarkMode = isSystemInDarkTheme()
    val boxColor = if (isDarkMode) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f) else Color(0xFFE4EAE8)
    val highlightColor = if (isDarkMode) MaterialTheme.colorScheme.outline.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.7f)
    val boxBorder = BorderStroke(
        width = 1.dp,
        brush = Brush.horizontalGradient(listOf(boxColor, highlightColor, boxColor))
    )

    val auth = remember {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    var showContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80)
        showContent = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AuthBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                CustomBackButton(onClick = onBack)
            }

            Spacer(modifier = Modifier.height(28.dp))

            StaggeredReveal(visible = showContent) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Forgot Password",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 26.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Enter your registered email address and we'll send you a link to reset your password.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .subtleCardShadow(borderRadius = 32.dp),
                        shape = RoundedCornerShape(32.dp),
                        color = boxColor,
                        border = boxBorder
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (isSubmitted) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF479C73),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Reset Link Sent",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 18.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Check your email inbox for instructions to reset your password.",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                AuthOutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it; errorMessage = null },
                                    label = "Email Address",
                                    leadingIcon = Icons.Default.Email,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                                )

                                if (errorMessage != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = errorMessage!!,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFF7A1129),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(22.dp))

                                Button(
                                    onClick = {
                                        if (email.isBlank()) {
                                            errorMessage = "Please enter your email address"
                                            return@Button
                                        }
                                        isLoading = true
                                        auth?.sendPasswordResetEmail(email.trim())
                                            ?.addOnCompleteListener { task ->
                                                isLoading = false
                                                if (task.isSuccessful) {
                                                    isSubmitted = true
                                                } else {
                                                    errorMessage = task.exception?.message ?: "Failed to send reset link"
                                                }
                                            }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .subtleCardShadow(borderRadius = 100.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF479C73)),
                                    shape = RoundedCornerShape(100.dp),
                                    enabled = !isLoading
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            modifier = Modifier.size(22.dp),
                                            strokeWidth = 2.5.dp
                                        )
                                    } else {
                                        Text(
                                            "Send Reset Link",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 15.sp
                                            )
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

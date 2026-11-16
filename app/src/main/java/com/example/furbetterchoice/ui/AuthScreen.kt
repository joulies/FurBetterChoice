package com.example.furbetterchoice.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.furbetterchoice.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.delay

@Composable
fun AuthDialog(
    onDismiss: () -> Unit,
    onAuthSuccess: () -> Unit,
) {
    var isSignUp by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Email verification step state (no OTP typing, verification via link)
    var isVerificationStep by remember { mutableStateOf(false) }
    var resendTimer by remember { mutableIntStateOf(60) }
    var canResend by remember { mutableStateOf(false) }
    var isVerifiedSuccess by remember { mutableStateOf(false) }

    val auth = remember {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    // Resend email countdown timer effect
    LaunchedEffect(isVerificationStep, canResend, resendTimer) {
        if (isVerificationStep && !canResend && resendTimer > 0) {
            delay(1000)
            resendTimer -= 1
            if (resendTimer == 0) {
                canResend = true
            }
        }
    }

    // Border and field styling matching navigation bar aesthetics
    val textFieldShape = RoundedCornerShape(28.dp)
    val fieldBorderColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AccentMediumGreen,
        unfocusedBorderColor = GrayLight,
        focusedLabelColor = AccentMediumGreen,
        unfocusedLabelColor = GrayMedium
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .subtleCardShadow(borderRadius = 32.dp),
            shape = RoundedCornerShape(32.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(GrayLight, Color.White, GrayLight)))
        ) {
            Box(modifier = Modifier.padding(24.dp)) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextDarkGreen)
                }

                AnimatedContent(
                    targetState = isVerificationStep,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(350)) + slideInVertically(animationSpec = tween(350)) { it / 4 } togetherWith
                                fadeOut(animationSpec = tween(350)) + slideOutVertically(animationSpec = tween(350)) { -it / 4 })
                            .using(SizeTransform(clip = false))
                    },
                    label = "authStepAnimation"
                ) { verificationMode ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))

                        if (verificationMode) {
                            // --- EMAIL VERIFICATION SENT STEP (LINK BASED) ---
                            Icon(
                                imageVector = if (isVerifiedSuccess) Icons.Default.CheckCircle else Icons.Default.Email,
                                contentDescription = null,
                                tint = AccentMediumGreen,
                                modifier = Modifier.size(64.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = if (isVerifiedSuccess) "Email Verified!" else "Verify Your Email",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextDarkGreen,
                                    fontSize = 24.sp,
                                ),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = if (isVerifiedSuccess)
                                    "Your email address has been successfully verified."
                                else
                                    "Email verification has been sent to\n${email.trim()}.\nPlease check your inbox and click the verification link.",
                                fontSize = 14.sp,
                                color = GrayMedium,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            if (errorMessage != null) {
                                Text(
                                    text = errorMessage!!,
                                    color = Color.Red,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                )
                            }

                            if (!isVerifiedSuccess) {
                                Button(
                                    onClick = {
                                        if (auth == null) {
                                            errorMessage = "Authentication unavailable"
                                            return@Button
                                        }
                                        isLoading = true
                                        val currentUser = auth.currentUser
                                        currentUser?.reload()?.addOnCompleteListener { task ->
                                            isLoading = false
                                            if (task.isSuccessful && currentUser.isEmailVerified) {
                                                isVerifiedSuccess = true
                                            } else {
                                                errorMessage = "Email not verified yet. Please click the link sent to your email."
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentMediumGreen),
                                    shape = RoundedCornerShape(28.dp),
                                    enabled = !isLoading,
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                    } else {
                                        Text("I've Verified My Email", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                if (canResend) {
                                    TextButton(
                                        onClick = {
                                            auth?.currentUser?.sendEmailVerification()
                                                ?.addOnCompleteListener {
                                                    resendTimer = 60
                                                    canResend = false
                                                    errorMessage = "Verification email resent successfully!"
                                                }
                                        }
                                    ) {
                                        Text("Resend Verification Email", color = AccentMediumGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                } else {
                                    Text(
                                        text = "Resend verification email in ${resendTimer}s",
                                        fontSize = 13.sp,
                                        color = GrayMedium
                                    )
                                }
                            } else {
                                LaunchedEffect(Unit) {
                                    delay(1500)
                                    onAuthSuccess()
                                }
                                CircularProgressIndicator(color = AccentMediumGreen, modifier = Modifier.size(36.dp))
                            }

                        } else {
                            // --- STANDARD SIGN IN / SIGN UP FORM STEP ---
                            Text(
                                text = if (isSignUp) "Create Account" else "Welcome Back",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextDarkGreen,
                                    fontSize = 24.sp,
                                ),
                            )
                            Text(
                                text = if (isSignUp) "Sign up to track and save products" else "Sign in to access your saved choices",
                                fontSize = 13.sp,
                                color = GrayMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            if (isSignUp) {
                                AuthOutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it; errorMessage = null },
                                    label = "Enter your full name"
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            AuthOutlinedTextField(
                                value = email,
                                onValueChange = { email = it; errorMessage = null },
                                label = "Enter your email address",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            AuthOutlinedTextField(
                                value = password,
                                onValueChange = { password = it; errorMessage = null },
                                label = "Enter your password",
                                isPassword = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                            )

                            if (isSignUp) {
                                Spacer(modifier = Modifier.height(12.dp))

                                AuthOutlinedTextField(
                                    value = confirmPassword,
                                    onValueChange = { confirmPassword = it; errorMessage = null },
                                    label = "Re-enter your password",
                                    isPassword = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                                )
                            }

                            if (errorMessage != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = errorMessage!!,
                                    color = Color.Red,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    if (auth == null) {
                                        errorMessage = "Authentication is currently unavailable"
                                        return@Button
                                    }
                                    if (email.isBlank() || password.isBlank()) {
                                        errorMessage = "Please fill all fields"
                                        return@Button
                                    }
                                    if (isSignUp && name.isBlank()) {
                                        errorMessage = "Please enter your name"
                                        return@Button
                                    }
                                    if (isSignUp && confirmPassword.isBlank()) {
                                        errorMessage = "Please confirm your password"
                                        return@Button
                                    }
                                    if (isSignUp && password != confirmPassword) {
                                        errorMessage = "Passwords do not match"
                                        return@Button
                                    }
                                    isLoading = true
                                    if (isSignUp) {
                                        auth.createUserWithEmailAndPassword(email.trim(), password)
                                            .addOnCompleteListener { task ->
                                                if (task.isSuccessful) {
                                                    val profileUpdates = userProfileChangeRequest {
                                                        displayName = name.trim()
                                                    }
                                                    val createdUser = task.result?.user
                                                    createdUser?.updateProfile(profileUpdates)
                                                        ?.addOnCompleteListener {
                                                            // Send Firebase Email Verification link
                                                            createdUser.sendEmailVerification()
                                                                .addOnCompleteListener {
                                                                    isLoading = false
                                                                    isVerificationStep = true
                                                                }
                                                        }
                                                } else {
                                                    isLoading = false
                                                    errorMessage = task.exception?.localizedMessage ?: "Sign up failed"
                                                }
                                            }
                                    } else {
                                        auth.signInWithEmailAndPassword(email.trim(), password)
                                            .addOnCompleteListener { task ->
                                                isLoading = false
                                                if (task.isSuccessful) {
                                                    onAuthSuccess()
                                                } else {
                                                    errorMessage = task.exception?.localizedMessage ?: "Authentication failed"
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
                                enabled = !isLoading,
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                } else {
                                    Text(if (isSignUp) "Sign Up" else "Sign In", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = if (isSignUp) "Already have an account? Sign In" else "Don't have an account? Sign Up",
                                fontSize = 14.sp,
                                color = AccentMediumGreen,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clickable { isSignUp = !isSignUp; errorMessage = null }
                                    .padding(8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

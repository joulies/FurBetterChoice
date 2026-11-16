package com.example.furbetterchoice.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.furbetterchoice.R
import com.example.furbetterchoice.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onNavigateToLogin: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val isUserLoggedIn by viewModel.isUserLoggedIn.collectAsState()
    
    // Theme-aware colors for settings boxes
    val boxColor = if (isDarkMode) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    } else {
        Color(0xFFE4EAE8)
    }
    
    val highlightColor = if (isDarkMode) {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    } else {
        Color.White.copy(alpha = 0.6f)
    }

    // Sign out confirmation dialog state
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    if (showSignOutDialog) {
        SignOutConfirmationDialog(
            onDismiss = { showSignOutDialog = false },
            onConfirm = {
                showSignOutDialog = false
                viewModel.logout()
                onSignOut()
            }
        )
    }

    // Staggered reveal flags
    var showHeader by remember { mutableStateOf(false) }
    var showAuthPill by remember { mutableStateOf(false) }
    var showGeneral by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    LaunchedEffect(isUserLoggedIn) {
        delay(100)
        showHeader = true
        delay(150)
        showAuthPill = true
        delay(150)
        showGeneral = true
        delay(150)
        showInfo = true
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top Green Fading Gradient Accent
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF479C73).copy(alpha = 0.38f),
                            Color(0xFF479C73).copy(alpha = 0.22f),
                            Color(0xFF479C73).copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp)
        ) {
        StaggeredReveal(visible = showHeader) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 20.sp
                )
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // Separate Top Pill Button for Log In / Sign Up or Sign Out
        StaggeredReveal(visible = showAuthPill) {
            Surface(
                shape = RoundedCornerShape(36.dp),
                color = boxColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .subtleCardShadow(borderRadius = 36.dp),
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(boxColor, highlightColor, boxColor)
                    )
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bouncyClickable {
                            if (isUserLoggedIn) {
                                showSignOutDialog = true
                            } else {
                                onNavigateToLogin()
                            }
                        }
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = if (isUserLoggedIn) "Account Active" else "Log In / Sign Up",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = if (isUserLoggedIn) "Tap to sign out" else "Optional: sync choices & save pets",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        StaggeredReveal(visible = showGeneral) {
            Column {
                Text(
                    text = "General",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
                )

                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = boxColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .subtleCardShadow(borderRadius = 32.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            colors = listOf(boxColor, highlightColor, boxColor)
                        )
                    )
                ) {
                    Column {
                        SettingToggleItem(
                            title = "Night Mode",
                            icon = Icons.Default.Settings,
                            isChecked = isDarkMode,
                            onCheckedChange = { viewModel.toggleDarkMode(it) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        StaggeredReveal(visible = showInfo) {
            Column {
                Text(
                    text = "Information",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
                )

                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = boxColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .subtleCardShadow(borderRadius = 32.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            colors = listOf(boxColor, highlightColor, boxColor)
                        )
                    )
                ) {
                    SettingNavigationItem(
                        title = "About FurBetterChoice",
                        icon = Icons.Default.Info,
                        onClick = { showAboutDialog = true }
                    )
                }
            }
        }
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
}
}

@Composable
fun SettingNavigationItem(title: String, icon: ImageVector, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable { onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun SettingToggleItem(title: String, icon: ImageVector, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.LightGray.copy(alpha = 0.4f)
            )
        )
    }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .padding(16.dp)
                .subtleCardShadow(borderRadius = 28.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xFF479C73).copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_paw),
                        contentDescription = null,
                        tint = Color(0xFF479C73),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "FurBetterChoice",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                Text(
                    text = "Version 1.0.0 (Build 100)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "FurBetterChoice helps pet parents make healthier, nutritional choices for their dogs and cats through smart food analysis and personal pet management.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .subtleCardShadow(borderRadius = 100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF479C73)),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = "Close",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

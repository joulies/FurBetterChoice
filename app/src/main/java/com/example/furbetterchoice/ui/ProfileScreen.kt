package com.example.furbetterchoice.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.furbetterchoice.R
import com.example.furbetterchoice.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class AvatarPreset(val id: String, val name: String, val species: String, val iconRes: Int)

val AVATAR_PRESETS = listOf(
    AvatarPreset("dog_1", "Buddy Dog", "Dog", R.drawable.ic_nav_pets),
    AvatarPreset("dog_2", "Max Dog", "Dog", R.drawable.ic_nav_pets),
    AvatarPreset("dog_3", "Luna Dog", "Dog", R.drawable.ic_nav_pets),
    AvatarPreset("cat_1", "Oliver Cat", "Cat", R.drawable.ic_nav_pets),
    AvatarPreset("cat_2", "Milo Cat", "Cat", R.drawable.ic_nav_pets),
    AvatarPreset("cat_3", "Bella Cat", "Cat", R.drawable.ic_nav_pets)
)

@Composable
fun ProfileStatsCard(
    modifier: Modifier = Modifier,
    count: Int,
    title: String,
    accentColor: Color,
    boxColor: Color,
    boxBorder: BorderStroke,
    iconRes: Int,
    onClick: () -> Unit
) {
    val solidBaseColor = boxColor.copy(alpha = 1f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .subtleCardShadow(borderRadius = 100.dp)
            .bouncyClickable(onClick = onClick),
        shape = RoundedCornerShape(100.dp),
        color = solidBaseColor
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = true
                    shape = RoundedCornerShape(100.dp)
                }
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val bgGradient = Brush.linearGradient(
                        colorStops = arrayOf(
                            0.0f to accentColor.copy(alpha = 0.34f),
                            0.52f to accentColor.copy(alpha = 0.16f),
                            1.0f to accentColor.copy(alpha = 0.28f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(w, h)
                    )
                    val rightOrbCenter = Offset(w * 0.85f, h * 0.24f)
                    val rightOrbRadius = w * 0.45f
                    val rightOrbBrush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.34f),
                            accentColor.copy(alpha = 0.12f),
                            accentColor.copy(alpha = 0f)
                        ),
                        center = rightOrbCenter,
                        radius = rightOrbRadius.coerceAtLeast(1f)
                    )
                    val leftOrbCenter = Offset(w * 0.18f, h * 0.82f)
                    val leftOrbRadius = w * 0.42f
                    val leftOrbBrush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.26f),
                            accentColor.copy(alpha = 0.08f),
                            accentColor.copy(alpha = 0f)
                        ),
                        center = leftOrbCenter,
                        radius = leftOrbRadius.coerceAtLeast(1f)
                    )

                    onDrawBehind {
                        drawRect(color = solidBaseColor)
                        drawRect(brush = bgGradient)
                        drawCircle(
                            brush = rightOrbBrush,
                            radius = rightOrbRadius,
                            center = rightOrbCenter
                        )
                        drawCircle(
                            brush = leftOrbBrush,
                            radius = leftOrbRadius,
                            center = leftOrbCenter
                        )
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon Box
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = solidBaseColor.copy(alpha = 0.88f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(accentColor.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Number + Title
                Column {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = accentColor
                        )
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: AppViewModel,
    onNavigateToSaved: () -> Unit = {},
    onNavigateToPets: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {}
) {
    val isDarkMode = isSystemInDarkTheme()
    val boxColor = if (isDarkMode) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else Color(0xFFE4EAE8)
    val highlightColor = if (isDarkMode) MaterialTheme.colorScheme.outline.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.6f)
    val boxBorder = BorderStroke(
        width = 1.dp,
        brush = Brush.horizontalGradient(
            colors = listOf(boxColor, highlightColor, boxColor)
        )
    )

    val userProfile by viewModel.currentUserProfile.collectAsState()
    val pets by viewModel.pets.collectAsState()
    val savedProducts by viewModel.savedProducts.collectAsState()
    val isUserLoggedIn by viewModel.isUserLoggedIn.collectAsState()

    var showAvatarPicker by remember { mutableStateOf(false) }
    var showNameChangeDialog by remember { mutableStateOf(false) }
    var nameChangeError by remember { mutableStateOf<String?>(null) }

    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()

    // Staggered reveal flags
    var showHeader by remember { mutableStateOf(false) }
    var showProfileCard by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }

    LaunchedEffect(isUserLoggedIn) {
        showHeader = false
        showProfileCard = false
        showStats = false
        kotlinx.coroutines.delay(60)
        showHeader = true
        kotlinx.coroutines.delay(100)
        showProfileCard = true
        kotlinx.coroutines.delay(100)
        showStats = true
    }

    val infiniteTransition = rememberInfiniteTransition(label = "profileAmbient")
    val floatOrbY1 by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 11f,
        animationSpec = infiniteRepeatable(
            animation = tween(5200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatOrbY1"
    )
    val floatOrbY2 by infiniteTransition.animateFloat(
        initialValue = 13f,
        targetValue = -9f,
        animationSpec = infiniteRepeatable(
            animation = tween(6400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatOrbY2"
    )
    val floatOrbY3 by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(4600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatOrbY3"
    )

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Subtle Top Green Fading Gradient Wash
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF479C73).copy(alpha = 0.24f),
                            Color(0xFF479C73).copy(alpha = 0.12f),
                            Color(0xFF479C73).copy(alpha = 0.04f),
                            Color.Transparent
                        )
                    )
                )
        )

        // --- Subtle, Randomly Sized Radial Gradients Across Background ---
        // Gradient 1: Large soft emerald mist (235dp) - Top Start
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-55).dp, y = 24.dp)
                .graphicsLayer { translationY = floatOrbY1 * 0.7f }
                .size(235.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF479C73).copy(alpha = 0.15f),
                            Color(0xFF479C73).copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Gradient 2: Medium-small warm gold glow (110dp) - Top End
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 22.dp, y = 78.dp)
                .graphicsLayer { translationY = floatOrbY2 * 0.6f }
                .size(110.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFD29524).copy(alpha = 0.14f),
                            Color(0xFFD29524).copy(alpha = 0.04f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Gradient 3: Wide subtle gold-emerald haze (195dp) - Upper-Mid End
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 50.dp, y = (-115).dp)
                .graphicsLayer { translationY = floatOrbY3 * 0.8f }
                .size(195.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFD29524).copy(alpha = 0.12f),
                            Color(0xFF479C73).copy(alpha = 0.04f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Gradient 4: Small soft sage glow (85dp) - Mid Start
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-18).dp, y = (-35).dp)
                .graphicsLayer { translationY = floatOrbY2 * (-0.5f) }
                .size(85.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF479C73).copy(alpha = 0.13f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Gradient 5: Extra-large subtle ambient bloom (265dp) - Lower-Mid Start
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-70).dp, y = (-150).dp)
                .graphicsLayer { translationY = floatOrbY1 * (-0.8f) }
                .size(265.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF479C73).copy(alpha = 0.14f),
                            Color(0xFF479C73).copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Gradient 6: Medium warm amber bloom (155dp) - Lower End
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 35.dp, y = (-190).dp)
                .graphicsLayer { translationY = floatOrbY3 * 0.7f }
                .size(155.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFD29524).copy(alpha = 0.13f),
                            Color(0xFFD29524).copy(alpha = 0.04f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Gradient 7: Compact soft mint glow (125dp) - Bottom Center
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(x = 18.dp, y = (-55).dp)
                .graphicsLayer { translationY = floatOrbY2 * 0.5f }
                .size(125.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF479C73).copy(alpha = 0.11f),
                            Color.Transparent
                        )
                    )
                )
        )

        // --- Randomly Sized Solid Circles Strictly in Lower Parts ---
        // Lower Solid Circle 1: 27dp soft emerald circle (Bottom Start)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 28.dp, y = (-135).dp)
                .graphicsLayer {
                    translationY = floatOrbY1
                    translationX = floatOrbY2 * 0.35f
                }
                .size(27.dp)
                .clip(CircleShape)
                .background(Color(0xFF479C73).copy(alpha = 0.14f))
        )

        // Lower Solid Circle 2: 13dp warm gold dot (Bottom End)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-42).dp, y = (-165).dp)
                .graphicsLayer {
                    translationY = floatOrbY2 * (-0.8f)
                    translationX = floatOrbY3 * 0.3f
                }
                .size(13.dp)
                .clip(CircleShape)
                .background(Color(0xFFD29524).copy(alpha = 0.18f))
        )

        // Lower Solid Circle 3: 19dp emerald accent dot (Bottom End lower)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-22).dp, y = (-92).dp)
                .graphicsLayer {
                    translationY = floatOrbY3
                }
                .size(19.dp)
                .clip(CircleShape)
                .background(Color(0xFF479C73).copy(alpha = 0.13f))
        )

        // Lower Solid Circle 4: 9dp tiny burgundy/rose accent dot (Bottom Start lower)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 68.dp, y = (-84).dp)
                .graphicsLayer {
                    translationY = floatOrbY2 * 0.65f
                }
                .size(9.dp)
                .clip(CircleShape)
                .background(Color(0xFF7A1129).copy(alpha = 0.16f))
        )

        // Lower Solid Circle 5: 34dp ultra-soft solid circle (Bottom Center-Left)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(x = (-54).dp, y = (-112).dp)
                .graphicsLayer {
                    translationY = floatOrbY1 * (-0.6f)
                }
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0xFFD29524).copy(alpha = 0.10f))
        )

        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                coroutineScope.launch {
                    isRefreshing = true
                    viewModel.refreshProfileData()
                    isRefreshing = false
                }
            },
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .zIndex(100f),
                    color = Color(0xFF479C73),
                    containerColor = MaterialTheme.colorScheme.surface
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // Title Header
                StaggeredReveal(visible = showHeader) {
                    Text(
                        text = "My Profile",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 20.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!isUserLoggedIn) {
                    Spacer(modifier = Modifier.height(30.dp))
                    StaggeredReveal(visible = showProfileCard) {
                        EmptyProfileLoginPrompt(onNavigateToLogin = onNavigateToLogin)
                    }
                    Spacer(modifier = Modifier.height(140.dp))
                } else {
                    // Header Profile Card
                    StaggeredReveal(visible = showProfileCard) {
                        val accentGreen = Color(0xFF479C73)
                        val accentGold = Color(0xFFD29524)
                        val solidBaseColor = boxColor.copy(alpha = 1f)

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .subtleCardShadow(borderRadius = 32.dp),
                            shape = RoundedCornerShape(32.dp),
                            color = solidBaseColor
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        clip = true
                                        shape = RoundedCornerShape(32.dp)
                                    }
                                    .drawWithCache {
                                        val w = size.width
                                        val h = size.height
                                        val bgGradient = Brush.linearGradient(
                                            colorStops = arrayOf(
                                                0.0f to accentGreen.copy(alpha = 0.36f),
                                                0.55f to accentGreen.copy(alpha = 0.17f),
                                                1.0f to accentGold.copy(alpha = 0.25f)
                                            ),
                                            start = Offset(0f, 0f),
                                            end = Offset(w, h)
                                        )
                                        val topRightOrbCenter = Offset(w * 0.86f, h * 0.18f)
                                        val topRightOrbRadius = w * 0.44f
                                        val topRightOrbBrush = Brush.radialGradient(
                                            colors = listOf(
                                                accentGold.copy(alpha = 0.28f),
                                                accentGold.copy(alpha = 0.10f),
                                                accentGold.copy(alpha = 0f)
                                            ),
                                            center = topRightOrbCenter,
                                            radius = topRightOrbRadius.coerceAtLeast(1f)
                                        )
                                        val bottomLeftOrbCenter = Offset(w * 0.14f, h * 0.84f)
                                        val bottomLeftOrbRadius = w * 0.46f
                                        val bottomLeftOrbBrush = Brush.radialGradient(
                                            colors = listOf(
                                                accentGreen.copy(alpha = 0.34f),
                                                accentGreen.copy(alpha = 0.12f),
                                                accentGreen.copy(alpha = 0f)
                                            ),
                                            center = bottomLeftOrbCenter,
                                            radius = bottomLeftOrbRadius.coerceAtLeast(1f)
                                        )
                                        val centerOrbCenter = Offset(w * 0.50f, h * 0.40f)
                                        val centerOrbRadius = w * 0.36f
                                        val centerOrbBrush = Brush.radialGradient(
                                            colors = listOf(
                                                accentGreen.copy(alpha = 0.18f),
                                                accentGreen.copy(alpha = 0f)
                                            ),
                                            center = centerOrbCenter,
                                            radius = centerOrbRadius.coerceAtLeast(1f)
                                        )

                                        onDrawBehind {
                                            drawRect(color = solidBaseColor)
                                            drawRect(brush = bgGradient)
                                            drawCircle(
                                                brush = topRightOrbBrush,
                                                radius = topRightOrbRadius,
                                                center = topRightOrbCenter
                                            )
                                            drawCircle(
                                                brush = bottomLeftOrbBrush,
                                                radius = bottomLeftOrbRadius,
                                                center = bottomLeftOrbCenter
                                            )
                                            drawCircle(
                                                brush = centerOrbBrush,
                                                radius = centerOrbRadius,
                                                center = centerOrbCenter
                                            )
                                        }
                                    }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Avatar Picture Frame
                                    val currentAvatarId = userProfile?.avatarId ?: "dog_1"
                                    val currentAvatarPreset = AVATAR_PRESETS.find { it.id == currentAvatarId } ?: AVATAR_PRESETS.first()

                                    Box(
                                        contentAlignment = Alignment.BottomEnd,
                                        modifier = Modifier.bouncyClickable { showAvatarPicker = true }
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(92.dp),
                                            shape = CircleShape,
                                            color = solidBaseColor.copy(alpha = 0.90f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        if (currentAvatarPreset.species == "Dog") Color(0xFF479C73).copy(alpha = 0.16f)
                                                        else Color(0xFF7A1229).copy(alpha = 0.16f)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    painter = painterResource(currentAvatarPreset.iconRes),
                                                    contentDescription = currentAvatarPreset.name,
                                                    tint = if (currentAvatarPreset.species == "Dog") Color(0xFF479C73) else Color(0xFF7A1229),
                                                    modifier = Modifier.size(48.dp)
                                                )
                                            }
                                        }

                                    // Edit Badge
                                    Surface(
                                        modifier = Modifier.size(32.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        shadowElevation = 4.dp
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Change Avatar",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Display Name
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = userProfile?.displayName.takeUnless { it.isNullOrBlank() } ?: "Pet Parent",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = {
                                            val (canChange, days) = viewModel.canChangeDisplayName()
                                            if (!canChange) {
                                                nameChangeError = "You can change your name again in $days day(s). Names can only be changed once every 2 weeks."
                                            } else {
                                                nameChangeError = null
                                                showNameChangeDialog = true
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Name",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                if (nameChangeError != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF7A1129).copy(alpha = 0.12f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF7A1129), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = nameChangeError!!,
                                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF7A1129), fontSize = 11.sp),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = userProfile?.email.takeUnless { it.isNullOrBlank() } ?: "Pet Parent Account",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Quick Stats Section (Side-by-Side Cards in Same Row)
                    StaggeredReveal(visible = showStats) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // My Pets Box (Green with Paw Icon R.drawable.ic_paw)
                            ProfileStatsCard(
                                modifier = Modifier.weight(1f),
                                count = pets.size,
                                title = "My Pets",
                                accentColor = Color(0xFF479C73),
                                boxColor = boxColor,
                                boxBorder = boxBorder,
                                iconRes = R.drawable.ic_paw,
                                onClick = onNavigateToPets
                            )

                            // Saved Box (Red with Saved Icon R.drawable.ic_nav_saved)
                            ProfileStatsCard(
                                modifier = Modifier.weight(1f),
                                count = savedProducts.size,
                                title = "Saved",
                                accentColor = Color(0xFF7A1229),
                                boxColor = boxColor,
                                boxBorder = boxBorder,
                                iconRes = R.drawable.ic_nav_saved,
                                onClick = onNavigateToSaved
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(120.dp))
                }
            }
        }
    }

    if (showAvatarPicker) {
        AvatarPickerDialog(
            currentAvatarId = userProfile?.avatarId ?: "dog_1",
            onDismiss = { showAvatarPicker = false },
            onSelect = { avatarId ->
                viewModel.updateUserAvatar(avatarId)
                showAvatarPicker = false
            }
        )
    }

    if (showNameChangeDialog) {
        NameChangeDialog(
            initialName = userProfile?.displayName ?: "",
            onDismiss = { showNameChangeDialog = false },
            onConfirm = { newName ->
                viewModel.updateDisplayName(newName) { success, error ->
                    if (success) {
                        showNameChangeDialog = false
                        nameChangeError = null
                    } else {
                        nameChangeError = error
                    }
                }
            }
        )
    }
}

@Composable
fun NameChangeDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var nameText by remember { mutableStateOf(initialName) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .padding(16.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    })
                }
                .subtleCardShadow(borderRadius = 28.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, Color(0xFF479C73).copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Change Display Name",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "New Display Name",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.5.sp
                        ),
                        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
                    )
                    // Name Input Field matching Login page style
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .subtleCardShadow(borderRadius = 100.dp)
                    ) {
                        OutlinedTextField(
                            value = nameText,
                            onValueChange = { nameText = it },
                            placeholder = { Text("Enter new name...", fontSize = 12.5.sp) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                            singleLine = true,
                            shape = RoundedCornerShape(100.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF479C73),
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                cursorColor = Color(0xFF479C73)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2-Week Reminder Notice Box
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF7A1129).copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, Color(0xFF7A1129).copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF7A1129),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Reminder: You can only change your display name once every 2 weeks.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Cancel Gray Pill
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .subtleCardShadow(borderRadius = 100.dp)
                            .bouncyClickable { onDismiss() },
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "Cancel",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 12.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Confirm Green Pill
                    Surface(
                        modifier = Modifier
                            .weight(1.3f)
                            .height(44.dp)
                            .subtleCardShadow(borderRadius = 100.dp)
                            .bouncyClickable(enabled = nameText.isNotBlank()) {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                onConfirm(nameText.trim())
                            },
                        shape = RoundedCornerShape(100.dp),
                        color = if (nameText.isNotBlank()) Color(0xFF479C73) else Color(0xFF479C73).copy(alpha = 0.5f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "Confirm Changes",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 12.5.sp),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AvatarPickerDialog(
    currentAvatarId: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Choose Your Avatar",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AVATAR_PRESETS.chunked(3).forEach { rowPresets ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowPresets.forEach { preset ->
                                val isSelected = preset.id == currentAvatarId
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .bouncyClickable { onSelect(preset.id) },
                                    shape = CircleShape,
                                    color = if (isSelected) Color(0xFF479C73).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    border = BorderStroke(
                                        2.dp,
                                        if (isSelected) Color(0xFF479C73) else Color.Transparent
                                    )
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(preset.iconRes),
                                            contentDescription = preset.name,
                                            tint = if (preset.species == "Dog") Color(0xFF479C73) else Color(0xFF7A1129),
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                TextButton(onClick = onDismiss) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun EmptyProfileLoginPrompt(onNavigateToLogin: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .subtleCardShadow(borderRadius = 32.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color(0xFF479C73),
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Track Pets & Saved Foods",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Log in or create an account to manage your pet profiles, save food products, and get personalized recommendations.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onNavigateToLogin,
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF479C73)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                ) {
                Text("Log In or Sign Up", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
            }
        }
    }
}

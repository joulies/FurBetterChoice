package com.example.furbetterchoice.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.furbetterchoice.R
import com.example.furbetterchoice.api.Pet
import com.example.furbetterchoice.api.Product
import com.example.furbetterchoice.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay

@Composable
fun MainDashboard(
    viewModel: AppViewModel,
    onScanClick: () -> Unit,
    onSeeAllClick: () -> Unit = {},
    onProductClick: (String) -> Unit = {}
) {
    val recentlyViewed by viewModel.recentlyViewed.collectAsState()
    val savedProducts by viewModel.savedProducts.collectAsState()
    val pets by viewModel.pets.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()

    val auth = remember {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            null
        }
    }
    val user = auth?.currentUser
    val userName = user?.displayName ?: user?.email?.substringBefore("@") ?: ""

    val boxColor = if (isDarkMode) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else Color(0xFFE4EAE8)
    val highlightColor = if (isDarkMode) MaterialTheme.colorScheme.outline.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.6f)
    val boxBorder = BorderStroke(1.dp, Brush.horizontalGradient(listOf(boxColor, highlightColor, boxColor)))

    // Staggered reveal flags
    var showHeader by remember { mutableStateOf(false) }
    var showScan by remember { mutableStateOf(false) }
    var showRecent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        showHeader = true
        delay(150)
        showScan = true
        delay(150)
        showRecent = true
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top Green Fading Gradient Accent (Starts at very top status bar and fades away in Did You Know facts)
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Compact Premium Header Card
            StaggeredReveal(visible = showHeader) {
                HomeHeader(userName)
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Tagline Section (Plain text, reduced font size, no background surface)
            Text(
                text = "Making better food choices for your pets, every day.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Orbital Scan Cluster (Fixed Bounds - Zero Shifting)
            StaggeredReveal(visible = showScan) {
                OrbitalScanSection(
                    savedCount = savedProducts.size,
                    petsCount = pets.size,
                    scannedCount = recentlyViewed.size,
                    boxColor = boxColor,
                    boxBorder = boxBorder,
                    onScanClick = onScanClick
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Frameless Auto-Cycling Did You Know Fact Ticker (Moved before Recently Scanned)
            DidYouKnowFactTicker()

            Spacer(modifier = Modifier.height(16.dp))

            // Revamped Recently Scanned Horizontal Section
            StaggeredReveal(visible = showRecent) {
                RecentScannedSection(recentlyViewed, onSeeAllClick, onProductClick, boxColor, boxBorder)
            }

            Spacer(modifier = Modifier.height(120.dp))
        }
    }
}

@Composable
fun HomeHeader(userName: String = "") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo only (no square background)
        Image(
            painter = painterResource(R.drawable.logo_transparent),
            contentDescription = "FurBetterChoice Logo",
            modifier = Modifier.size(60.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (userName.isNotEmpty()) "Hello, $userName!" else "FurBetterChoice",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Welcome back!",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun OrbitalScanSection(
    savedCount: Int,
    petsCount: Int,
    scannedCount: Int,
    boxColor: Color,
    boxBorder: BorderStroke,
    onScanClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbitPulse")

    // Breathing pulse for outer ring
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Continuous floating/oscillating Y offsets & rotations for asymmetrical satellites
    val floatY1 by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY1"
    )
    val floatY2 by infiniteTransition.animateFloat(
        initialValue = 10f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY2"
    )
    val floatY3 by infiniteTransition.animateFloat(
        initialValue = -7f,
        targetValue = 9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY3"
    )
    val floatY4 by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2100, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY4"
    )

    // Slow 2D drifting motion for asymmetrical particle orbs
    val orbY1 by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbY1"
    )
    val orbY2 by infiniteTransition.animateFloat(
        initialValue = 14f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbY2"
    )
    val orbY3 by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2900, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbY3"
    )

    val rot1 by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rot1"
    )
    val rot4 by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = -3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2300, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rot4"
    )

    // Livelier, non-circling organic multi-axis drift for the Scan Now button gradient (lighter FEDIAF palette)
    val driftX1 by infiniteTransition.animateFloat(
        initialValue = 0.16f,
        targetValue = 0.84f,
        animationSpec = infiniteRepeatable(
            animation = tween(7200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftX1"
    )
    val driftY1 by infiniteTransition.animateFloat(
        initialValue = 0.80f,
        targetValue = 0.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(9100, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftY1"
    )
    val driftX2 by infiniteTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(10600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftX2"
    )
    val driftY2 by infiniteTransition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.84f,
        animationSpec = infiniteRepeatable(
            animation = tween(8300, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftY2"
    )
    val fluidScanPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fluidScanPulse"
    )

    // Fixed Outer Container (310.dp) - Zero Shifting for Content Below
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(310.dp),
        contentAlignment = Alignment.Center
    ) {
        // Slow Drifting Ambient Particle Orbs (Asymmetrical Sizes)
        // Orb 1: Medium Green Orb (22dp)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = (-60).dp, y = 16.dp)
                .graphicsLayer { translationY = orbY1; translationX = orbY2 * 0.5f }
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(0xFF479C73).copy(alpha = 0.22f))
        )

        // Orb 2: Small Burgundy Orb (10dp)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-24).dp, y = (-40).dp)
                .graphicsLayer { translationY = orbY2; translationX = orbY1 * 0.4f }
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(0xFF7A1129).copy(alpha = 0.25f))
        )

        // Orb 3: Large Green Soft Glowing Orb (28dp)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 36.dp, y = (-50).dp)
                .graphicsLayer { translationY = orbY3; translationX = orbY1 * (-0.3f) }
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFF479C73).copy(alpha = 0.15f))
        )

        // Orb 4: Tiny Burgundy Orb (7dp)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-40).dp, y = (-10).dp)
                .graphicsLayer { translationY = orbY1 * 0.7f }
                .size(7.dp)
                .clip(CircleShape)
                .background(Color(0xFF7A1129).copy(alpha = 0.35f))
        )

        // Orb 5: Compact Green Orb (14dp)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 18.dp, y = (-20).dp)
                .graphicsLayer { translationY = orbY2 * (-0.6f) }
                .size(14.dp)
                .clip(CircleShape)
                .background(Color(0xFF479C73).copy(alpha = 0.20f))
        )

        // Outer Breathing Ring
        Box(
            modifier = Modifier
                .size(200.dp)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
                .clip(CircleShape)
                .background(Color(0xFF479C73).copy(alpha = pulseAlpha))
        )

        // Central Scan Button with slow, smooth non-circling organic FEDIAF gradient
        Surface(
            modifier = Modifier
                .size(190.dp)
                .graphicsLayer {
                    translationY = floatY1 * 0.4f
                }
                .subtleCardShadow(borderRadius = 100.dp)
                .clip(CircleShape)
                .bouncyClickable(onClick = onScanClick),
            shape = CircleShape,
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val w = size.width
                        val h = size.height

                        // Lighter fresh emerald base
                        drawRect(color = Color(0xFF398C66))

                        // Softly shifting diagonal wash (no circular spinning)
                        drawRect(
                            brush = Brush.linearGradient(
                                colorStops = arrayOf(
                                    0.0f to Color(0xFF327F5C),
                                    0.48f to Color(0xFF56B083),
                                    1.0f to Color(0xFFCEA043).copy(alpha = 0.86f)
                                ),
                                start = Offset(w * (0.1f + 0.3f * driftX1), h * (0.1f + 0.25f * driftY2)),
                                end = Offset(w * (0.9f - 0.25f * driftX2), h * (0.9f - 0.25f * driftY1))
                            )
                        )

                        // Organic wandering warm gold cloud (lighter & brighter)
                        val goldCenter = Offset(w * driftX1, h * driftY1)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0.0f to Color(0xFFECB850).copy(alpha = 0.52f + 0.14f * fluidScanPulse),
                                    0.45f to Color(0xFFDDA234).copy(alpha = 0.26f),
                                    1.0f to Color.Transparent
                                ),
                                center = goldCenter,
                                radius = w * 0.78f
                            ),
                            radius = w * 0.78f,
                            center = goldCenter
                        )

                        // Counter-drifting light sage/emerald cloud
                        val sageCenter = Offset(w * driftX2, h * driftY2)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0.0f to Color(0xFF72D4A1).copy(alpha = 0.54f),
                                    0.50f to Color(0xFF56B083).copy(alpha = 0.28f),
                                    1.0f to Color.Transparent
                                ),
                                center = sageCenter,
                                radius = w * 0.82f
                            ),
                            radius = w * 0.82f,
                            center = sageCenter
                        )

                        // Third light amber-emerald mist for natural randomness
                        val mistCenter = Offset(
                            x = w * (1f - driftY1 * 0.75f),
                            y = h * (0.2f + driftX1 * 0.6f)
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0.0f to Color(0xFF479F74).copy(alpha = 0.44f + 0.12f * (1f - fluidScanPulse)),
                                    0.55f to Color(0xFFDEB04A).copy(alpha = 0.18f),
                                    1.0f to Color.Transparent
                                ),
                                center = mistCenter,
                                radius = w * 0.70f
                            ),
                            radius = w * 0.70f,
                            center = mistCenter
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.scan_now),
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_barcode),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.95f),
                        modifier = Modifier.size(width = 80.dp, height = 24.dp)
                    )
                }
            }
        }

        // Asymmetrical Orbiting Satellite 1: Top-Left Instant Scan Quality Badge (Compact Pill)
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 6.dp, y = 24.dp)
                .graphicsLayer {
                    translationY = floatY1
                    rotationZ = rot1
                }
                .subtleCardShadow(borderRadius = 100.dp),
            shape = RoundedCornerShape(100.dp),
            color = boxColor,
            border = boxBorder
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_green),
                    contentDescription = null,
                    tint = Color(0xFF479C73),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Instant Scan",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF479C73),
                        fontSize = 11.sp
                    )
                )
            }
        }

        // Asymmetrical Orbiting Satellite 2: Top-Right Saved Foods (Prominent Rounded Card)
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-4).dp, y = 14.dp)
                .graphicsLayer {
                    translationY = floatY2
                }
                .subtleCardShadow(borderRadius = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = boxColor,
            border = boxBorder
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF479C73).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_nav_saved),
                        contentDescription = null,
                        tint = Color(0xFF479C73),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$savedCount Saved",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp
                    )
                )
            }
        }

        // Asymmetrical Orbiting Satellite 3: Bottom-Left Registered Pets (Medium Pill)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 8.dp, y = (-16).dp)
                .graphicsLayer {
                    translationY = floatY3
                }
                .subtleCardShadow(borderRadius = 100.dp),
            shape = RoundedCornerShape(100.dp),
            color = boxColor,
            border = boxBorder
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF7A1129).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_nav_pets),
                        contentDescription = null,
                        tint = Color(0xFF7A1129),
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$petsCount Pets",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp
                    )
                )
            }
        }

        // Asymmetrical Orbiting Satellite 4: Bottom-Right Total Scanned (Asymmetric Slender Badge)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-8).dp, y = (-20).dp)
                .graphicsLayer {
                    translationY = floatY4
                    rotationZ = rot4
                }
                .subtleCardShadow(borderRadius = 20.dp),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 10.dp, bottomEnd = 20.dp, bottomStart = 10.dp),
            color = boxColor,
            border = boxBorder
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_barcode),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$scannedCount Scanned",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

val PET_NUTRITION_FACTS = listOf(
    "Dry Matter Basis reveals true protein levels by eliminating water weight.",
    "Wet food provides crucial moisture that supports cat urinary tract health.",
    "High ash content in kibble often indicates high bone and mineral content.",
    "Omega-3 fatty acids from fish oil promote shiny coats and joint mobility.",
    "First 3 ingredients make up the vast majority of a pet food formula.",
    "Chewing dry kibble helps reduce plaque buildup on your pet's teeth."
)

@Composable
fun DidYouKnowFactTicker() {
    var factIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(4500)
            factIndex = (factIndex + 1) % PET_NUTRITION_FACTS.size
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable { factIndex = (factIndex + 1) % PET_NUTRITION_FACTS.size }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = Color(0xFF479C73).copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("💡", fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Did You Know?",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF479C73),
                    fontSize = 10.sp
                )
            )

            AnimatedContent(
                targetState = PET_NUTRITION_FACTS[factIndex],
                transitionSpec = {
                    (slideInVertically { height -> height } + fadeIn(animationSpec = tween(500)))
                        .togetherWith(slideOutVertically { height -> -height } + fadeOut(animationSpec = tween(500)))
                        .using(SizeTransform(clip = false))
                },
                label = "factTransition"
            ) { factText ->
                Text(
                    text = factText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

fun Modifier.fadingEdges(leftFade: androidx.compose.ui.unit.Dp = 16.dp, rightFade: androidx.compose.ui.unit.Dp = 16.dp) = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val leftPx = leftFade.toPx()
        val rightPx = rightFade.toPx()
        val w = size.width

        if (leftPx > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startX = 0f,
                    endX = leftPx
                ),
                blendMode = BlendMode.DstIn
            )
        }
        if (rightPx > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startX = w - rightPx,
                    endX = w
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }

@Composable
fun RecentScannedSection(
    recentlyViewed: List<Product>,
    onSeeAllClick: () -> Unit,
    onProductClick: (String) -> Unit,
    boxColor: Color,
    boxBorder: BorderStroke
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recently Scanned",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.ExtraBold
                )
            )

            TextButton(onClick = onSeeAllClick) {
                Text(
                    text = "See All",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (recentlyViewed.isEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .subtleCardShadow(borderRadius = 28.dp),
                shape = RoundedCornerShape(28.dp),
                color = boxColor,
                border = boxBorder
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_barcode),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "No products scanned yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fadingEdges(16.dp, 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    recentlyViewed.take(6).forEach { product ->
                    val code = product.barcode.ifEmpty { product.productId }

                    Surface(
                        modifier = Modifier
                            .width(170.dp)
                            .subtleCardShadow(borderRadius = 24.dp)
                            .bouncyClickable {
                                if (code.isNotEmpty()) onProductClick(code)
                            },
                        shape = RoundedCornerShape(24.dp),
                        color = boxColor,
                        border = boxBorder
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ) {
                                if (!product.imageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = product.imageUrl,
                                        contentDescription = product.productName,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_barcode),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = product.productName.ifEmpty { "Pet Food" },
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = product.brand.ifEmpty { "Brand" },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
}

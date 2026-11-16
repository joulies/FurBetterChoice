package com.example.furbetterchoice.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.furbetterchoice.R
import com.example.furbetterchoice.api.ContentModerationFilter
import com.example.furbetterchoice.api.ModerationResult
import com.example.furbetterchoice.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Applies a smooth, persistent drop shadow to any box/card container.
 * Uses drawWithCache so Paint, BlurMaskFilter, and Path are cached across animation frames,
 * keeping shadows visible, unclipped, and 60/120fps smooth during tab and card animations.
 */
fun Modifier.subtleShadow(
    borderRadius: Dp = 28.dp,
    color: Color = Color.Black.copy(alpha = 0.08f),
    blurRadius: Dp = 12.dp,
    offsetY: Dp = 4.dp,
    offsetX: Dp = 0.dp,
    spread: Dp = 0.dp
) = this.drawWithCache {
    val hasShadow = color.alpha > 0f
    val shadowColor = color.toArgb()
    val blurPx = blurRadius.toPx()
    val spreadPx = spread.toPx()
    val offsetXPx = offsetX.toPx()
    val offsetYPx = offsetY.toPx()
    val leftPixel = offsetXPx - spreadPx
    val topPixel = offsetYPx - spreadPx
    val rightPixel = size.width + offsetXPx + spreadPx
    val bottomPixel = size.height + offsetYPx + spreadPx
    val rxPixel = borderRadius.toPx().coerceAtLeast(0f)

    val paint = Paint().apply {
        val frameworkPaint = asFrameworkPaint()
        frameworkPaint.isAntiAlias = true
        frameworkPaint.color = shadowColor
        if (blurPx > 0f) {
            frameworkPaint.maskFilter = android.graphics.BlurMaskFilter(
                blurPx.coerceAtLeast(1f),
                android.graphics.BlurMaskFilter.Blur.NORMAL
            )
        }
    }

    val innerPath = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(0f, 0f, size.width, size.height),
                cornerRadius = CornerRadius(rxPixel, rxPixel)
            )
        )
    }

    onDrawBehind {
        if (!hasShadow) return@onDrawBehind
        drawIntoCanvas { canvas ->
            canvas.save()
            canvas.clipPath(innerPath, clipOp = ClipOp.Difference)
            canvas.drawRoundRect(
                leftPixel,
                topPixel,
                rightPixel,
                bottomPixel,
                rxPixel,
                rxPixel,
                paint
            )
            canvas.restore()
        }
    }
}

val LocalShimmerProgress = compositionLocalOf { -1f }

/**
 * A fluid, smooth, high-performance shimmer skeleton loading effect.
 * Animates a brilliant specular highlight top-to-bottom across a clean light gray base.
 */
fun Modifier.shimmerSkeleton(
    shape: Shape = RoundedCornerShape(16.dp),
    baseColor: Color? = null,
    highlightColor: Color? = null
): Modifier = composed {
    // True light gray base as requested (never dark gray)
    val actualBase = baseColor ?: Color(0xFFEDEDED)
    val actualHighlight = highlightColor ?: Color.White

    val sharedProgress = LocalShimmerProgress.current
    val progress = if (sharedProgress >= -0.5f) {
        sharedProgress
    } else {
        val transition = rememberInfiniteTransition(label = "shimmerSkeleton")
        val localProgress by transition.animateFloat(
            initialValue = -0.3f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 850, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmerProgress"
        )
        localProgress
    }

    this
        .clip(shape)
        .drawBehind {
            val h = size.height
            val w = size.width
            val currentY = progress * h
            val bandHeight = (h * 0.9f).coerceIn(120f, 400f)

            val brush = Brush.linearGradient(
                colors = listOf(
                    actualBase,
                    actualBase,
                    actualHighlight.copy(alpha = 0.88f),
                    actualHighlight,
                    actualHighlight.copy(alpha = 0.88f),
                    actualBase,
                    actualBase
                ),
                start = Offset(w * 0.15f, currentY - bandHeight),
                end = Offset(w * 0.40f, currentY + bandHeight)
            )

            drawRect(brush = brush)
        }
}

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    baseColor: Color? = null,
    highlightColor: Color? = null
) {
    Box(
        modifier = modifier.shimmerSkeleton(
            shape = shape,
            baseColor = baseColor,
            highlightColor = highlightColor
        )
    )
}

@Composable
fun SkeletonText(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    Box(
        modifier = modifier
            .height(height)
            .shimmerSkeleton(shape = shape)
    )
}

@Composable
fun CustomBackButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .size(44.dp)
            .subtleCardShadow(borderRadius = 100.dp)
            .bouncyClickable(onClick = onClick),
        shape = CircleShape,
        color = Color(0xFF7A1129)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun Modifier.subtleCardShadow(
    borderRadius: Dp = 28.dp,
    isDark: Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f
): Modifier {
    val alpha = if (isDark) 0.16f else 0.07f
    return this.subtleShadow(
        borderRadius = borderRadius,
        color = Color.Black.copy(alpha = alpha),
        blurRadius = 12.dp,
        offsetY = 4.dp
    )
}

@Composable
fun FloatingBottomNav(
    currentRoute: String?,
    isDark: Boolean = false,
    isShrunk: Boolean = false,
    onExpand: () -> Unit = {},
    onNavigate: (String) -> Unit
) {
    val navBgColor = Color(0xFF7A1129) // HEX 7A1129

    var isNavPressed by remember { mutableStateOf(false) }
    
    // Scale logic: 1.0 (normal) or 0.85 (shrunk). Pressed state pushes it to 1.1.
    val targetScale = when {
        isNavPressed -> 1.1f
        isShrunk -> 0.85f
        else -> 1f
    }
    
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = if (isNavPressed) {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        } else {
            tween(durationMillis = 400, easing = EaseOutCubic)
        },
        label = "navScale"
    )

    val shadowBlur by animateDpAsState(
        targetValue = 12.dp * scale,
        animationSpec = tween(300),
        label = "navShadowBlur"
    )
    val shadowOffsetY by animateDpAsState(
        targetValue = 4.dp * scale,
        animationSpec = tween(300),
        label = "navShadowOffset"
    )
    val shadowAlpha = if (isDark) 0.18f else 0.07f

    val items = remember {
        listOf(
            NavItemData("home", R.drawable.ic_nav_home, "Home"),
            NavItemData("profile", R.drawable.ic_nav_pets, "Profile"),
            NavItemData("feedback", R.drawable.ic_nav_feedback, "Community"),
            NavItemData("settings", R.drawable.ic_nav_settings, "Settings")
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.70f)
                .height(51.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    clip = false
                }
                .subtleShadow(
                    borderRadius = 26.dp,
                    color = Color.Black.copy(alpha = shadowAlpha),
                    blurRadius = shadowBlur,
                    offsetY = shadowOffsetY
                )
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitFirstDown(requireUnconsumed = false)
                            isNavPressed = true
                            onExpand() // Reset shrinkage state on click
                            waitForUpOrCancellation()
                            isNavPressed = false
                        }
                    }
                },
            shape = RoundedCornerShape(26.dp),
            color = navBgColor,
            shadowElevation = 8.dp,
            border = BorderStroke(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.2f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                items.forEach { item ->
                    NavItem(
                        modifier = Modifier.weight(1f),
                        iconRes = item.iconRes,
                        label = item.label,
                        isSelected = currentRoute == item.route,
                        onClick = { onNavigate(item.route) }
                    )
                }
            }
        }
    }
}

data class NavItemData(val route: String, val iconRes: Int, val label: String)

@Composable
fun NavItem(
    modifier: Modifier = Modifier,
    iconRes: Int,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val selectionProgress by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(durationMillis = 360, easing = EaseInOutCubic),
        label = "navSelectionProgress"
    )

    val animatedColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.52f),
        animationSpec = tween(durationMillis = 360, easing = EaseInOutCubic),
        label = "navItemColor"
    )

    val iconScale = 1.0f + (0.14f * selectionProgress)
    val iconTranslateY = -1.5f * selectionProgress

    Box(
        modifier = modifier
            .fillMaxHeight()
            .bouncyClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = animatedColor,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                    translationY = iconTranslateY
                }
        )
    }
}

// Reusable Bouncy Effect Modifier
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
) = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        clip = false
    }.clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClick = onClick
    )
}

@Composable
fun CustomConfirmationDialog(
    title: String,
    message: String,
    confirmText: String = "Remove",
    icon: ImageVector? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(300),
        label = "alpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        isVisible = true
    }

    fun dismissWithAnimation() {
        isVisible = false
        scope.launch {
            delay(250)
            onDismiss()
        }
    }

    val isDarkMode = isSystemInDarkTheme()
    val boxColor = if (isDarkMode) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else Color(0xFFFFFEFE)

    Dialog(
        onDismissRequest = { dismissWithAnimation() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f * alpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { dismissWithAnimation() },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .scale(scale)
                    .graphicsLayer(alpha = alpha)
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (icon != null) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF7A1129).copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = Color(0xFF7A1129),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Cancel Button (Gray Pill)
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .bouncyClickable { dismissWithAnimation() },
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
                        
                        // Action Button (Red Pill - Color(0xFF7A1129))
                        Surface(
                            modifier = Modifier
                                .weight(1.2f)
                                .height(42.dp)
                                .bouncyClickable {
                                    isVisible = false
                                    scope.launch {
                                        delay(250)
                                        onConfirm()
                                    }
                                },
                            shape = RoundedCornerShape(100.dp),
                            color = Color(0xFF7A1129)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    confirmText,
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
}

@Composable
fun StaggeredReveal(
    visible: Boolean,
    delayMillis: Int = 0,
    content: @Composable () -> Unit
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(800, delayMillis = delayMillis, easing = LinearOutSlowInEasing),
        label = "alpha"
    )
    
    val translationY by animateFloatAsState(
        targetValue = if (visible) 0f else 80f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "translationY"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.95f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha
            this.translationY = translationY
            this.scaleX = scale
            this.scaleY = scale
            this.clip = false
            this.compositingStrategy = if (alpha in 0.01f..0.99f) {
                CompositingStrategy.ModulateAlpha
            } else {
                CompositingStrategy.Auto
            }
        }
    ) {
        content()
    }
}

@Composable
fun LoginRequiredDialog(
    onDismiss: () -> Unit,
    onLoginClick: () -> Unit,
    actionText: String = "perform this action"
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Image(
                                painter = painterResource(R.drawable.logo_transparent),
                                contentDescription = null,
                                modifier = Modifier.size(46.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Login Required",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "You need to be logged in to $actionText.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onLoginClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Log In or Sign Up", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(onClick = onDismiss) {
                        Text("Maybe Later", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * Highlights any flagged bad words inside an [OutlinedTextField] in real time
 * with a red pill background and bold red text while preserving cursor mapping.
 */
object ModerationVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val result = ContentModerationFilter.analyze(raw)
        if (!result.containsBadWords || result.highlightRanges.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val styled = buildAnnotatedString {
            append(raw)
            for (range in result.highlightRanges) {
                val start = range.first.coerceIn(0, raw.length)
                val end = (range.last + 1).coerceIn(start, raw.length)
                if (start < end) {
                    addStyle(
                        style = SpanStyle(
                            color = Color(0xFFD32F2F),
                            background = Color(0x33E53935),
                            fontWeight = FontWeight.Bold
                        ),
                        start = start,
                        end = end
                    )
                }
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

@Composable
fun ModerationWarningBanner(
    moderation: ModerationResult,
    modifier: Modifier = Modifier
) {
    if (!moderation.containsBadWords) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFFEBEE),
        border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = Color(0xFFD32F2F),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Inappropriate language detected (${moderation.matchedWords.joinToString(", ")}). Remove highlighted words to submit.",
                color = Color(0xFFB71C1C),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 15.sp
            )
        }
    }
}


package com.example.furbetterchoice.ui

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.example.furbetterchoice.api.ProductReview
import com.example.furbetterchoice.api.IngredientAnalyzer
import com.example.furbetterchoice.api.IngredientExplanation
import com.example.furbetterchoice.api.Pet
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.furbetterchoice.R
import com.example.furbetterchoice.api.AafcoAssessmentEngine
import com.example.furbetterchoice.api.AafcoAssessmentResult
import com.example.furbetterchoice.api.AafcoNutrientResult
import com.example.furbetterchoice.api.AafcoResultStatus
import com.example.furbetterchoice.api.AssessmentStandard
import com.example.furbetterchoice.api.ContentModerationFilter
import com.example.furbetterchoice.api.EnergyExtractionResult
import com.example.furbetterchoice.api.EnergySource
import com.example.furbetterchoice.api.FediafMerMode
import com.example.furbetterchoice.api.DailyFeedingEstimate
import com.example.furbetterchoice.api.NfeCarbResult
import com.example.furbetterchoice.api.NutritionAssessmentEngine
import com.example.furbetterchoice.api.NutritionEnergyHelper
import com.example.furbetterchoice.api.NutritionStatus
import com.example.furbetterchoice.api.Nutriments
import com.example.furbetterchoice.api.PetAssessmentFilter
import com.example.furbetterchoice.api.Product
import com.example.furbetterchoice.api.ProductPetFilterSpec
import com.example.furbetterchoice.api.ScoringSystem
import com.example.furbetterchoice.api.ScoreResult
import com.example.furbetterchoice.api.NutritionUtils
import com.example.furbetterchoice.api.RecommendedNutrition
import com.example.furbetterchoice.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToInt

enum class ProductTab(val label: String) {
    DESCRIPTION("Description"),
    INGREDIENTS("Ingredients"),
    ALTERNATIVES("Alternative Products"),
    REVIEWS("Reviews")
}

fun isValidAttribute(value: String?): Boolean {
    if (value.isNullOrBlank()) return false
    val lower = value.trim().lowercase()
    return lower != "not specified" &&
           lower != "not_specified" &&
           lower != "unknown" &&
           lower != "n/a" &&
           lower != "none" &&
           lower != "null"
}

fun formatLifeStageForSpecies(lifeStage: String?, species: String?): String {
    val raw = lifeStage?.trim().orEmpty()
    if (raw.isEmpty()) return ""
    val lowerStage = raw.lowercase(Locale.ROOT)
    val lowerSpecies = species?.trim()?.lowercase(Locale.ROOT).orEmpty()
    val isCat = lowerSpecies.contains("cat") || lowerSpecies.contains("feline")
    val isDog = lowerSpecies.contains("dog") || lowerSpecies.contains("canine")

    val isYoungStage = lowerStage.contains("puppy") ||
        lowerStage.contains("kitten") ||
        lowerStage.contains("growth") ||
        lowerStage.contains("junior")

    return when {
        isYoungStage -> when {
            isCat -> "Kitten"
            isDog -> "Puppy"
            lowerStage.contains("kitten") && !lowerStage.contains("puppy") -> "Kitten"
            else -> "Puppy"
        }
        lowerStage.contains("senior") -> "Senior"
        lowerStage.contains("adult") -> "Adult"
        else -> raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductAnalysisScreen(
    barcode: String,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onSuggestProduct: () -> Unit,
    onProductClick: (String) -> Unit = {},
    onCompareClick: (String) -> Unit = {},
    onNavigateToLogin: () -> Unit = {}
) {
    val isDarkMode by viewModel.isDarkMode.collectAsState()

    val boxColor = if (isDarkMode) Color(0xFF1E2422) else Color(0xFFFFFEFE)
    val highlightColor = if (isDarkMode) MaterialTheme.colorScheme.outline.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.7f)
    val boxBorder = BorderStroke(1.dp, Brush.horizontalGradient(listOf(boxColor, highlightColor, boxColor)))

    var product by remember(barcode) { mutableStateOf(viewModel.productDetailsCache.value[barcode]) }
    var alternatives by remember(barcode) { mutableStateOf(viewModel.alternativesCache.value[barcode] ?: emptyList()) }
    var isLoading by remember { mutableStateOf(product == null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()
    var isScanSuccessfulShown by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    
    // Tab State
    var selectedTab by remember { mutableStateOf(ProductTab.DESCRIPTION) }

    // Staggered reveal flags
    var showTopBar by remember { mutableStateOf(product != null) }
    var showHero by remember { mutableStateOf(product != null) }
    var showTabs by remember { mutableStateOf(product != null) }
    var showTabContent by remember { mutableStateOf(product != null) }

    var selectedNutrient by remember { mutableStateOf("Protein") }

    // Save & Review notification state
    var showSaveToast by remember { mutableStateOf<String?>(null) }
    var showReviewToast by remember { mutableStateOf(false) }
    var showDeleteReviewToast by remember { mutableStateOf(false) }

    LaunchedEffect(showDeleteReviewToast) {
        if (showDeleteReviewToast) {
            delay(2500)
            showDeleteReviewToast = false
        }
    }

    LaunchedEffect(barcode) {
        val cachedProd = viewModel.productDetailsCache.value[barcode]
        if (cachedProd != null) {
            product = cachedProd
            val cachedAlts = viewModel.alternativesCache.value[barcode] ?: emptyList()
            alternatives = cachedAlts
            isLoading = false
            showTopBar = true
            showHero = true
            showTabs = true
            showTabContent = true
            val targetId = cachedProd.productId.ifEmpty { barcode }
            if (viewModel.productReviews.value[targetId] == null) {
                launch {
                    viewModel.loadReviewsForProduct(targetId, cachedProd.barcode)
                }
            }
            if (cachedAlts.isEmpty()) {
                launch {
                    try {
                        val speciesQuery = cachedProd.species.ifBlank { cachedProd.pet }
                        val excludeCode = cachedProd.barcode.ifBlank { cachedProd.productId.ifBlank { barcode } }
                        val fetchedAlts = viewModel.fetchAlternatives(speciesQuery, excludeCode)
                        alternatives = fetchedAlts
                        viewModel.cacheAlternatives(barcode, fetchedAlts)
                    } catch (e: Exception) {
                        Log.e("ProductAnalysis", "Error fetching alternatives for cached product", e)
                    }
                }
            }
            return@LaunchedEffect
        }

        isLoading = true
        errorMsg = null
        isScanSuccessfulShown = false
        
        showTopBar = false
        showHero = false
        showTabs = false
        showTabContent = false
        
        try {
            val result = viewModel.fetchProduct(barcode)
            val p = result.getOrNull()

            if (p != null) {
                product = p
                viewModel.cacheProductDetails(barcode, p)
                isLoading = false

                // Fetch alternatives asynchronously so the product screen opens immediately
                launch {
                    try {
                        val speciesQuery = p.species.ifBlank { p.pet }
                        val excludeCode = p.barcode.ifBlank { p.productId.ifBlank { barcode } }
                        val altList = viewModel.fetchAlternatives(speciesQuery, excludeCode)
                        alternatives = altList
                        viewModel.cacheAlternatives(barcode, altList)
                    } catch (e: Exception) {
                        Log.e("ProductAnalysis", "Error fetching alternatives", e)
                    }
                }
                
                showTopBar = true
                delay(100)
                showHero = true
                delay(100)
                showTabs = true
                delay(100)
                showTabContent = true
                
            } else {
                val exception = result.exceptionOrNull()
                if (exception != null) {
                    errorMsg = "Network or database error. Please check your connection and try again."
                } else {
                    errorMsg = "We couldn't find product '$barcode' in our database. You can try scanning again or suggest a product via our feedback form."
                }
                isLoading = false
            }
        } catch (e: Exception) {
            Log.e("ProductAnalysis", "Error fetching product from Firestore", e)
            errorMsg = "Network error. Please check your connection."
            isLoading = false
        }
    }

    LaunchedEffect(showSaveToast) {
        if (showSaveToast != null) {
            delay(2500)
            showSaveToast = null
        }
    }

    LaunchedEffect(showReviewToast) {
        if (showReviewToast) {
            delay(2500)
            showReviewToast = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            FriendlyLoadingScreen()
        }
        
        AnimatedVisibility(
            visible = isScanSuccessfulShown,
            enter = fadeIn(),
            exit = scaleOut(targetScale = 0.8f) + fadeOut()
        ) {
            ScanSuccessfulOverlay()
        }

        if (errorMsg != null) {
            ErrorScreen(errorMsg!!, onBack, onSuggestProduct)
        } else {
            product?.let { p ->
                Box(modifier = Modifier.fillMaxSize()) {
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

                    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            coroutineScope.launch {
                                isRefreshing = true
                                try {
                                    val refreshedResult = viewModel.fetchProduct(barcode)
                                    val refreshedProduct = refreshedResult.getOrNull()
                                    if (refreshedProduct != null) {
                                        product = refreshedProduct
                                        viewModel.cacheProductDetails(barcode, refreshedProduct)
                                        val speciesQuery = refreshedProduct.species.ifBlank { refreshedProduct.pet }
                                        val excludeCode = refreshedProduct.barcode.ifBlank { refreshedProduct.productId.ifBlank { barcode } }
                                        val refreshedAlts = viewModel.fetchAlternatives(speciesQuery, excludeCode)
                                        alternatives = refreshedAlts
                                        viewModel.cacheAlternatives(barcode, refreshedAlts)
                                        val targetId = refreshedProduct.productId.ifEmpty { barcode }
                                        viewModel.loadReviewsForProduct(targetId, refreshedProduct.barcode)
                                    }
                                    delay(350L)
                                } catch (e: Exception) {
                                    Log.e("ProductAnalysis", "Error refreshing product", e)
                                } finally {
                                    isRefreshing = false
                                }
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
                                    .padding(top = 8.dp)
                                    .zIndex(100f),
                                color = Color(0xFF479C73),
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            ProductAnalysisContentBody(
                                product = p,
                                barcode = barcode,
                                alternatives = alternatives,
                                viewModel = viewModel,
                                selectedTab = selectedTab,
                                onTabSelected = { selectedTab = it },
                                selectedNutrient = selectedNutrient,
                                onNutrientClick = { selectedNutrient = if (selectedNutrient == it) "" else it },
                                boxColor = boxColor,
                                boxBorder = boxBorder,
                                showHero = showHero,
                                showTabs = showTabs,
                                showTabContent = showTabContent,
                                onProductClick = onProductClick,
                                onCompareClick = onCompareClick,
                                onNavigateToLogin = onNavigateToLogin,
                                onReviewSubmitted = { showReviewToast = true },
                                onDeleteReview = { showDeleteReviewToast = true },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(top = 72.dp)
                            )

                            // Pinned Top Bar (below pull-to-refresh indicator zIndex)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                            ) {
                                StaggeredReveal(visible = showTopBar) {
                                    ProductTopBar(
                                        onBack = onBack,
                                        product = p,
                                        viewModel = viewModel,
                                        onSaved = { name -> showSaveToast = name },
                                        onCompareClick = {
                                            val code = p.barcode.ifEmpty { p.productId }
                                            if (code.isNotEmpty()) {
                                                onCompareClick(code)
                                            }
                                        },
                                        onNavigateToLogin = onNavigateToLogin
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Save Confirmation Toast
        AnimatedVisibility(
            visible = showSaveToast != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(100.dp),
                shadowElevation = 8.dp,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Product has been added to saved",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Reviewed Successfully Toast
        AnimatedVisibility(
            visible = showReviewToast,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
        ) {
            Surface(
                color = Color(0xFF479C73),
                shape = RoundedCornerShape(100.dp),
                shadowElevation = 8.dp,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Reviewed successfully",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Comment Deleted Toast
        AnimatedVisibility(
            visible = showDeleteReviewToast,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(100.dp),
                shadowElevation = 8.dp,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Comment deleted",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun ProductAnalysisContentBody(
    product: Product,
    barcode: String,
    alternatives: List<Product>,
    viewModel: AppViewModel,
    selectedTab: ProductTab,
    onTabSelected: (ProductTab) -> Unit,
    selectedNutrient: String,
    onNutrientClick: (String) -> Unit,
    boxColor: Color,
    boxBorder: BorderStroke,
    modifier: Modifier = Modifier,
    showHero: Boolean = true,
    showTabs: Boolean = true,
    showTabContent: Boolean = true,
    isEditModeEnabled: Boolean = false,
    lowConfidenceNutrientKeys: Set<String> = emptySet(),
    onEditProductInfo: () -> Unit = {},
    onEditNutrients: (String?) -> Unit = {},
    onEditIngredients: () -> Unit = {},
    onProductClick: (String) -> Unit = {},
    onCompareClick: (String) -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    onReviewSubmitted: () -> Unit = {},
    onDeleteReview: () -> Unit = {},
    bottomSpacerHeight: Dp = 120.dp
) {
    Column(modifier = modifier) {
        AnimatedVisibility(
            visible = isEditModeEnabled,
            enter = fadeIn(tween(220)) + expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFFFF8E1),
                border = BorderStroke(1.dp, Color(0xFFD29524).copy(alpha = 0.55f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = Color(0xFFD29524),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "AI can make mistakes. Please carefully double-check the product details, ingredients, and nutrient percentages below before saving.",
                        fontSize = 12.sp,
                        lineHeight = 16.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF5D3D00)
                    )
                }
            }
        }

        StaggeredReveal(visible = showHero) {
            ProductHeroHeader(
                product = product,
                boxColor = boxColor,
                boxBorder = boxBorder,
                isEditModeEnabled = isEditModeEnabled,
                onEditProductInfo = onEditProductInfo
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        StaggeredReveal(visible = showTabs) {
            ProductTabsHeader(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                boxColor = boxColor,
                boxBorder = boxBorder
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        StaggeredReveal(visible = showTabContent) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState.ordinal > initialState.ordinal) {
                        (slideInHorizontally(animationSpec = tween(350, easing = EaseOutCubic)) { width -> width } + fadeIn(animationSpec = tween(250)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(350, easing = EaseInCubic)) { width -> -width } + fadeOut(animationSpec = tween(200)))
                    } else {
                        (slideInHorizontally(animationSpec = tween(350, easing = EaseOutCubic)) { width -> -width } + fadeIn(animationSpec = tween(250)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(350, easing = EaseInCubic)) { width -> width } + fadeOut(animationSpec = tween(200)))
                    }.using(SizeTransform(clip = false))
                },
                label = "TabTransition"
            ) { targetTab ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 8.dp)
                ) {
                    when (targetTab) {
                        ProductTab.DESCRIPTION -> {
                            DescriptionTabContent(
                                product = product,
                                viewModel = viewModel,
                                selectedNutrient = selectedNutrient,
                                onNutrientClick = onNutrientClick,
                                boxColor = boxColor,
                                boxBorder = boxBorder,
                                isEditModeEnabled = isEditModeEnabled,
                                lowConfidenceNutrientKeys = lowConfidenceNutrientKeys,
                                onEditNutrients = onEditNutrients,
                                onNavigateToLogin = onNavigateToLogin
                            )
                        }
                        ProductTab.INGREDIENTS -> {
                            IngredientsTabContent(
                                product = product,
                                viewModel = viewModel,
                                boxColor = boxColor,
                                boxBorder = boxBorder,
                                isEditModeEnabled = isEditModeEnabled,
                                onEditIngredients = onEditIngredients
                            )
                        }
                        ProductTab.ALTERNATIVES -> {
                            AlternativesTabContent(
                                currentProduct = product,
                                alternatives = alternatives,
                                viewModel = viewModel,
                                boxColor = boxColor,
                                boxBorder = boxBorder,
                                onProductClick = onProductClick,
                                onCompareClick = onCompareClick
                            )
                        }
                        ProductTab.REVIEWS -> {
                            ReviewsTabContent(
                                productId = product.productId.ifEmpty { barcode },
                                viewModel = viewModel,
                                boxColor = boxColor,
                                boxBorder = boxBorder,
                                onNavigateToLogin = onNavigateToLogin,
                                onReviewSubmitted = onReviewSubmitted,
                                onDeleteReview = onDeleteReview
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(bottomSpacerHeight))
    }
}

@Composable
fun LoginRequiredDialog(
    onDismiss: () -> Unit,
    onLoginClick: () -> Unit
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
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.FavoriteBorder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "To use this feature you must log in",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onLoginClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = "Log In",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProductTopBar(
    onBack: () -> Unit,
    product: Product?,
    viewModel: AppViewModel,
    onSaved: (String) -> Unit,
    onCompareClick: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val savedProducts by viewModel.savedProducts.collectAsState()
    val isSaved = remember(savedProducts, product) {
        savedProducts.any { it.barcode == product?.barcode }
    }

    val isUserLoggedIn by viewModel.isUserLoggedIn.collectAsState()
    var showLoginPrompt by remember { mutableStateOf(false) }

    if (showLoginPrompt) {
        LoginRequiredDialog(
            onDismiss = { showLoginPrompt = false },
            onLoginClick = {
                showLoginPrompt = false
                onNavigateToLogin()
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CustomBackButton(onClick = onBack)

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "Compare" Button in Top Row (placed next to Save/Heart Button)
            Surface(
                modifier = Modifier
                    .bouncyClickable { onCompareClick() },
                shape = RoundedCornerShape(100.dp),
                color = Color(0xFF479C73),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Compare",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 12.5.sp
                        )
                    )
                }
            }

            // Save (Heart) Button
            IconButton(
                onClick = { 
                    if (isUserLoggedIn) {
                        product?.let { p ->
                            val alreadySaved = viewModel.savedProducts.value.any { it.barcode == p.barcode }
                            viewModel.toggleSaveProduct(p)
                            if (!alreadySaved) {
                                onSaved(p.productName ?: "Product")
                            }
                        }
                    } else {
                        showLoginPrompt = true
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = if (isSaved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Save",
                    tint = if (isSaved) Color.Red else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ProductHeroHeader(
    product: Product,
    boxColor: Color,
    boxBorder: BorderStroke,
    isEditModeEnabled: Boolean = false,
    onEditProductInfo: () -> Unit = {}
) {
    val infoModeration = remember(product.productName, product.brands, product.foodType) {
        ContentModerationFilter.analyze(listOf(product.productName, product.brands, product.foodType).joinToString(" "))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Product Image Presentation
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .subtleCardShadow(borderRadius = 28.dp),
            shape = RoundedCornerShape(28.dp),
            color = boxColor,
            border = boxBorder
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!product.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = product.imageUrl,
                        contentDescription = product.productName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_barcode),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                }

                if (isEditModeEnabled) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .bouncyClickable { onEditProductInfo() },
                        shape = RoundedCornerShape(100.dp),
                        color = Color(0xFF479C73),
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Edit Product Details",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Edit Info",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Product Name + optional inline pencil edit button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = product.productName.ifEmpty { "Unknown Product" },
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    color = if (infoModeration.containsBadWords) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onBackground
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (isEditModeEnabled) {
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = onEditProductInfo,
                    modifier = Modifier
                        .size(30.dp)
                        .background(Color(0xFF479C73).copy(alpha = 0.14f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit Product Info",
                        tint = Color(0xFF479C73),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        if (isEditModeEnabled && infoModeration.containsBadWords) {
            Spacer(modifier = Modifier.height(8.dp))
            ModerationWarningBanner(moderation = infoModeration)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Sub-header Row: Brand • Pet Type • Life Stage in ONE single row
        val validBrand = product.brands.takeIf { isValidAttribute(it) }
        val resolvedSpecies = product.species.ifBlank { product.pet }
        val validSpecies = resolvedSpecies.takeIf { isValidAttribute(it) }
        val validLifeStage = product.lifeStage
            .takeIf { isValidAttribute(it) }
            ?.let { formatLifeStageForSpecies(it, resolvedSpecies) }

        val subHeaderDetails = listOfNotNull(validBrand, validSpecies, validLifeStage)

        if (subHeaderDetails.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                subHeaderDetails.forEachIndexed { index, text ->
                    if (index > 0) {
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = if (index == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(
                            1.dp,
                            if (index == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    ) {
                        Text(
                            text = text,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AttributeChip(text: String) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@Composable
fun ProductTabsHeader(
    selectedTab: ProductTab,
    onTabSelected: (ProductTab) -> Unit,
    boxColor: Color,
    boxBorder: BorderStroke
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .subtleCardShadow(borderRadius = 100.dp),
        shape = RoundedCornerShape(100.dp),
        color = boxColor,
        border = boxBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductTab.entries.forEach { tab ->
                val isSelected = selectedTab == tab
                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    animationSpec = tween(300, easing = EaseOutCubic),
                    label = "tabBg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(300, easing = EaseOutCubic),
                    label = "tabText"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(backgroundColor)
                        .bouncyClickable { onTabSelected(tab) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp,
                            letterSpacing = (-0.2).sp,
                            color = textColor
                        ),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun DescriptionTabContent(
    product: Product,
    viewModel: AppViewModel,
    selectedNutrient: String,
    onNutrientClick: (String) -> Unit,
    boxColor: Color,
    boxBorder: BorderStroke,
    isEditModeEnabled: Boolean = false,
    lowConfidenceNutrientKeys: Set<String> = emptySet(),
    onEditNutrients: (String?) -> Unit = {},
    onNavigateToLogin: () -> Unit = {}
) {
    var selectedStandard by remember { mutableStateOf(AssessmentStandard.FEDIAF) }
    val shineRippleAnim = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    val pets by viewModel.pets.collectAsState()
    val selectedPetId by viewModel.selectedAssessmentPetId.collectAsState()
    val selectedMerMode by viewModel.selectedMerMode.collectAsState()

    val filterSpec = remember(product) {
        PetAssessmentFilter.buildFilterSpec(product)
    }
    val eligiblePets = remember(pets, product) {
        PetAssessmentFilter.filterPetsForProduct(pets, product)
    }
    val activePet = remember(eligiblePets, selectedPetId) {
        val explicit = selectedPetId?.let { id ->
            eligiblePets.firstOrNull { it.petId == id || it.id == id }
        }
        explicit ?: eligiblePets.firstOrNull()
    }

    val assessment = remember(product, activePet, selectedMerMode) {
        viewModel.getNutritionAssessment(
            product = product,
            userPetType = activePet?.type,
            userPetBirthday = activePet?.birthday,
            userPetAgeYears = activePet?.age,
            userPetBreed = activePet?.breed,
            merMode = selectedMerMode
        )
    }
    val aafcoAssessment = remember(product, activePet) {
        viewModel.getAafcoAssessment(
            product = product,
            userPetType = activePet?.type,
            userPetAgeYears = activePet?.age,
            userPetBirthday = activePet?.birthday,
            userPetBreed = activePet?.breed
        )
    }
    val shineProgressProvider: () -> Float = remember(shineRippleAnim) { { shineRippleAnim.value } }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Standard Switcher: FEDIAF Assessment vs AAFCO Assessment
        NutritionStandardSelector(
            selectedStandard = selectedStandard,
            onStandardSelected = { newStandard ->
                if (newStandard != selectedStandard) {
                    selectedStandard = newStandard
                    coroutineScope.launch {
                        shineRippleAnim.snapTo(0.01f)
                        shineRippleAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = 1350,
                                easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1.0f)
                            )
                        )
                        shineRippleAnim.snapTo(0f)
                    }
                }
            },
            boxColor = boxColor,
            boxBorder = boxBorder
        )

        if (pets.isNotEmpty() || selectedStandard == AssessmentStandard.FEDIAF) {
            Spacer(modifier = Modifier.height(10.dp))
            AssessmentContextControls(
                eligiblePets = eligiblePets,
                allPetsCount = pets.size,
                filterSpec = filterSpec,
                activePet = activePet,
                onSelectPet = { pet ->
                    val id = pet.petId.ifBlank { pet.id }
                    viewModel.selectAssessmentPet(id)
                },
                showMerMode = selectedStandard == AssessmentStandard.FEDIAF,
                selectedMerMode = selectedMerMode,
                onSelectMerMode = { viewModel.setFediafMerMode(it) },
                boxColor = boxColor,
                boxBorder = boxBorder
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Top Overview Card Crossfade (lightweight, hardware-accelerated)
        AnimatedContent(
            targetState = selectedStandard,
            transitionSpec = {
                fadeIn(animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing))
                    .togetherWith(fadeOut(animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)))
                    .using(SizeTransform(clip = false))
            },
            label = "standardOverviewSmoothCrossfade"
        ) { currentStandard ->
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (currentStandard == AssessmentStandard.FEDIAF) {
                    FediafNutritionCheckCard(
                        assessment = assessment,
                        selectedNutrient = selectedNutrient,
                        onNutrientClick = onNutrientClick,
                        boxColor = boxColor,
                        shineProgressProvider = shineProgressProvider,
                        isEditModeEnabled = isEditModeEnabled,
                        lowConfidenceNutrientKeys = lowConfidenceNutrientKeys,
                        onEditNutrients = onEditNutrients
                    )
                } else {
                    AafcoNutritionOverviewCard(
                        assessment = aafcoAssessment,
                        selectedNutrient = selectedNutrient,
                        onNutrientClick = onNutrientClick,
                        boxColor = boxColor,
                        shineProgressProvider = shineProgressProvider,
                        isEditModeEnabled = isEditModeEnabled,
                        lowConfidenceNutrientKeys = lowConfidenceNutrientKeys,
                        onEditNutrients = onEditNutrients
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val nfeCarbs = remember(product) { NutritionEnergyHelper.calculateEstimatedCarbsNfe(product) }
        val dailyFeeding = remember(product, activePet, selectedMerMode, assessment.isTreatOrComplementary) {
            if (activePet != null && !assessment.isTreatOrComplementary) {
                NutritionEnergyHelper.calculateEstimatedDailyFeeding(product, activePet, selectedMerMode)
            } else {
                null
            }
        }
        if (nfeCarbs != null || dailyFeeding != null) {
            EstimatedCarbsAndFeedingCard(
                nfeCarbs = nfeCarbs,
                dailyFeeding = dailyFeeding,
                boxColor = boxColor,
                boxBorder = boxBorder
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Brand History & Packaging/Environmental Information Card (stays mounted once — avoids duplicate layout/recomposition on tab switch)
        BrandHistoryCard(
            product = product,
            viewModel = viewModel,
            boxColor = boxColor
        )

        if (!isEditModeEnabled) {
            Spacer(modifier = Modifier.height(16.dp))
            CommunityLabelTrustCard(
                product = product,
                viewModel = viewModel,
                boxColor = boxColor,
                boxBorder = boxBorder,
                onNavigateToLogin = onNavigateToLogin
            )
        }
    }
}

private fun Modifier.shiningRippleOverlay(
    shineProgressProvider: () -> Float,
    borderRadius: androidx.compose.ui.unit.Dp = 28.dp
): Modifier = this
    .clip(RoundedCornerShape(borderRadius))
    .drawWithCache {
    val w = size.width
    val h = size.height
    val maxDim = kotlin.math.hypot(w.toDouble(), h.toDouble()).toFloat().coerceAtLeast(1f)
    val rippleCenter = Offset(w * 0.5f, h * 0.06f)

    val brightWhite = Color(0xFFFFFFFF)
    val luminousYellow = Color(0xFFFFF176)
    val warmGold = Color(0xFFFFD54F)

    onDrawWithContent {
        drawContent()
        val shineProgress = shineProgressProvider()
        if (shineProgress > 0f && shineProgress < 1f) {
            val currentRadius = (maxDim * 1.18f * shineProgress).coerceAtLeast(1f)
            val envelope = kotlin.math.sin(shineProgress * Math.PI).toFloat().coerceIn(0f, 1f)

            val rippleBrush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to brightWhite.copy(alpha = 0.48f * envelope),
                    0.38f to luminousYellow.copy(alpha = 0.58f * envelope),
                    0.68f to brightWhite.copy(alpha = 0.85f * envelope),
                    0.86f to warmGold.copy(alpha = 0.54f * envelope),
                    1.0f to warmGold.copy(alpha = 0f)
                ),
                center = rippleCenter,
                radius = currentRadius
            )

            val sweepStart = Offset(w * (-0.45f + 1.9f * shineProgress), 0f)
            val sweepEnd = Offset(w * (0.15f + 1.9f * shineProgress), h)
            val shimmerBandBrush = Brush.linearGradient(
                colorStops = arrayOf(
                    0.0f to brightWhite.copy(alpha = 0f),
                    0.30f to luminousYellow.copy(alpha = 0.45f * envelope),
                    0.50f to brightWhite.copy(alpha = 0.78f * envelope),
                    0.70f to warmGold.copy(alpha = 0.45f * envelope),
                    1.0f to brightWhite.copy(alpha = 0f)
                ),
                start = sweepStart,
                end = sweepEnd
            )

            drawCircle(
                brush = rippleBrush,
                radius = currentRadius,
                center = rippleCenter
            )
            drawRect(brush = shimmerBandBrush)
        }
    }
}

@Composable
fun BrandHistoryCard(
    product: Product,
    viewModel: AppViewModel,
    boxColor: Color
) {
    val brandName = product.brands
    val displayBrand = brandName.ifBlank { "this brand" }
    var isHistoryExpanded by remember { mutableStateOf(false) }
    var historyText by remember(product.brandHistory) { mutableStateOf(product.brandHistory) }
    var isLoadingHistory by remember { mutableStateOf(false) }

    LaunchedEffect(isHistoryExpanded, brandName, product.brandHistory) {
        if (product.brandHistory.isNotBlank()) {
            historyText = product.brandHistory
            isLoadingHistory = false
        } else if (isHistoryExpanded && historyText.isBlank()) {
            isLoadingHistory = true
            val fetched = viewModel.getBrandHistory(brandName)
            if (!fetched.isNullOrBlank()) {
                historyText = fetched
            }
            isLoadingHistory = false
        }
    }

    val emptyStateText = "We haven't added $displayBrand's eco-friendliness history yet. Check back soon as we continue expanding our sustainability records."
    val textToShow = historyText.takeIf { it.isNotBlank() } ?: emptyStateText

    val materialValue = product.packagingMaterial.trim().takeIf { isValidAttribute(it) } ?: "Information Unavailable"
    val typeValue = product.packagingType.trim().takeIf { isValidAttribute(it) } ?: "Information Unavailable"
    val recyclabilityValue = product.packagingRecyclability.trim().takeIf { isValidAttribute(it) } ?: "Information Unavailable"
    val biodegradabilityValue = product.packagingBiodegradability.trim().takeIf { isValidAttribute(it) } ?: "Information Unavailable"

    val brandGreen = Color(0xFF479C73)
    val solidBaseColor = boxColor.copy(alpha = 1f)

    val recyclabilityColor = when (recyclabilityValue.lowercase(Locale.ROOT)) {
        "recyclable" -> Color(0xFF479C73)
        "difficult to recycle" -> Color(0xFFD97706)
        "not recyclable" -> Color(0xFFE53935)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val biodegradabilityColor = when (biodegradabilityValue.lowercase(Locale.ROOT)) {
        "biodegradable" -> Color(0xFF479C73)
        "non-biodegradable" -> GrayMedium
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .subtleCardShadow(borderRadius = 28.dp),
        shape = RoundedCornerShape(28.dp),
        color = solidBaseColor
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                .graphicsLayer {
                    clip = true
                    shape = RoundedCornerShape(28.dp)
                }
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    // Only green gradient to highlight greenery & eco-friendliness
                    val bgGradient = Brush.linearGradient(
                        colorStops = arrayOf(
                            0.0f to brandGreen.copy(alpha = 0.34f),
                            0.50f to brandGreen.copy(alpha = 0.16f),
                            1.0f to brandGreen.copy(alpha = 0.26f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(w, h)
                    )
                    val topRightCircleCenter = Offset(w * 0.88f, h * 0.14f)
                    val topRightCircleRadius = w * 0.44f
                    val topRightCircleBrush = Brush.radialGradient(
                        colors = listOf(
                            brandGreen.copy(alpha = 0.34f),
                            brandGreen.copy(alpha = 0.12f),
                            brandGreen.copy(alpha = 0f)
                        ),
                        center = topRightCircleCenter,
                        radius = topRightCircleRadius.coerceAtLeast(1f)
                    )

                    val bottomLeftCircleCenter = Offset(w * 0.12f, h * 0.86f)
                    val bottomLeftCircleRadius = w * 0.46f
                    val bottomLeftCircleBrush = Brush.radialGradient(
                        colors = listOf(
                            brandGreen.copy(alpha = 0.28f),
                            brandGreen.copy(alpha = 0.10f),
                            brandGreen.copy(alpha = 0f)
                        ),
                        center = bottomLeftCircleCenter,
                        radius = bottomLeftCircleRadius.coerceAtLeast(1f)
                    )

                    val upperLeftCircleCenter = Offset(w * 0.24f, h * 0.34f)
                    val upperLeftCircleRadius = w * 0.28f
                    val upperLeftCircleBrush = Brush.radialGradient(
                        colors = listOf(
                            brandGreen.copy(alpha = 0.20f),
                            brandGreen.copy(alpha = 0f)
                        ),
                        center = upperLeftCircleCenter,
                        radius = upperLeftCircleRadius.coerceAtLeast(1f)
                    )

                    val midRightCircleCenter = Offset(w * 0.84f, h * 0.62f)
                    val midRightCircleRadius = w * 0.32f
                    val midRightCircleBrush = Brush.radialGradient(
                        colors = listOf(
                            brandGreen.copy(alpha = 0.22f),
                            brandGreen.copy(alpha = 0f)
                        ),
                        center = midRightCircleCenter,
                        radius = midRightCircleRadius.coerceAtLeast(1f)
                    )

                    onDrawBehind {
                        drawRect(color = solidBaseColor)
                        drawRect(brush = bgGradient)
                        drawCircle(
                            brush = topRightCircleBrush,
                            radius = topRightCircleRadius,
                            center = topRightCircleCenter
                        )
                        drawCircle(
                            brush = bottomLeftCircleBrush,
                            radius = bottomLeftCircleRadius,
                            center = bottomLeftCircleCenter
                        )
                        drawCircle(
                            brush = upperLeftCircleBrush,
                            radius = upperLeftCircleRadius,
                            center = upperLeftCircleCenter
                        )
                        drawCircle(
                            brush = midRightCircleBrush,
                            radius = midRightCircleRadius,
                            center = midRightCircleCenter
                        )
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Expandable Header: Brand Eco-Friendliness History (Centered, Larger Single-Line Font, No Icon)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .bouncyClickable { isHistoryExpanded = !isHistoryExpanded }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Brand Eco-Friendliness History",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.2).sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (isHistoryExpanded) "Tap here to hide" else "Tap here to view",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            color = brandGreen,
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Expandable Fixed-Height Scrollable Brand History Box
                AnimatedVisibility(
                    visible = isHistoryExpanded,
                    enter = expandVertically(animationSpec = tween(260, easing = FastOutSlowInEasing)) + fadeIn(tween(200)),
                    exit = shrinkVertically(animationSpec = tween(220, easing = FastOutLinearInEasing)) + fadeOut(tween(150))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(145.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = solidBaseColor.copy(alpha = 0.88f),
                            border = BorderStroke(1.dp, brandGreen.copy(alpha = 0.28f))
                        ) {
                            if (isLoadingHistory) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(22.dp),
                                            color = brandGreen,
                                            strokeWidth = 2.5.dp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Loading history for $displayBrand...",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = textToShow,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = brandGreen.copy(alpha = 0.22f))
                Spacer(modifier = Modifier.height(16.dp))

                // Packaging and Environmental Information Section Title (Centered, No Icon)
                Text(
                    text = "Packaging and Environmental Information",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Row 1: Packaging Material & Packaging Type
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PackagingAttributeCell(
                        label = "Packaging Material",
                        value = materialValue,
                        indicatorColor = null,
                        isUnavailable = materialValue == "Information Unavailable",
                        baseSurfaceColor = solidBaseColor,
                        modifier = Modifier.weight(1f)
                    )
                    PackagingAttributeCell(
                        label = "Packaging Type",
                        value = typeValue,
                        indicatorColor = null,
                        isUnavailable = typeValue == "Information Unavailable",
                        baseSurfaceColor = solidBaseColor,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Row 2: Recyclability & Biodegradability
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PackagingAttributeCell(
                        label = "Recyclability",
                        value = recyclabilityValue,
                        indicatorColor = if (recyclabilityValue != "Information Unavailable") recyclabilityColor else null,
                        isUnavailable = recyclabilityValue == "Information Unavailable",
                        baseSurfaceColor = solidBaseColor,
                        modifier = Modifier.weight(1f)
                    )
                    PackagingAttributeCell(
                        label = "Biodegradability",
                        value = biodegradabilityValue,
                        indicatorColor = if (biodegradabilityValue != "Information Unavailable") biodegradabilityColor else null,
                        isUnavailable = biodegradabilityValue == "Information Unavailable",
                        baseSurfaceColor = solidBaseColor,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Positive & Persuasive Proper Waste Disposal Guide
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = solidBaseColor.copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, brandGreen.copy(alpha = 0.28f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(brandGreen.copy(alpha = 0.08f))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Every Mindful Disposal Makes a Difference",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = brandGreen
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Emptying, rinsing, and sorting this package into the proper recycling or waste bin helps reduce landfill waste and keeps our communities cleaner and safer for every pet and planet.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                lineHeight = 15.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PackagingAttributeCell(
    label: String,
    value: String,
    indicatorColor: Color?,
    isUnavailable: Boolean,
    baseSurfaceColor: Color,
    modifier: Modifier = Modifier
) {
    val borderStroke = if (indicatorColor != null && !isUnavailable) {
        BorderStroke(1.dp, indicatorColor.copy(alpha = 0.32f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
    }

    val valueFontSize = when {
        value.length >= 17 -> 10.5.sp
        value.length >= 14 -> 11.sp
        else -> 12.sp
    }

    Surface(
        modifier = modifier.height(74.dp),
        shape = RoundedCornerShape(16.dp),
        color = baseSurfaceColor.copy(alpha = 0.88f),
        border = borderStroke
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (indicatorColor != null && !isUnavailable) {
                        indicatorColor.copy(alpha = 0.12f)
                    } else {
                        Color.Transparent
                    }
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = valueFontSize,
                    lineHeight = 14.sp,
                    fontWeight = if (isUnavailable) FontWeight.Normal else FontWeight.Bold,
                    color = when {
                        isUnavailable -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        indicatorColor != null -> indicatorColor
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun BrandHistoryDialog(
    brandName: String,
    productBrandHistory: String = "",
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val displayBrand = brandName.ifBlank { "this brand" }
    var historyText by remember { mutableStateOf(productBrandHistory) }
    var isLoadingHistory by remember { mutableStateOf(productBrandHistory.isBlank()) }

    LaunchedEffect(brandName, productBrandHistory) {
        if (productBrandHistory.isNotBlank()) {
            historyText = productBrandHistory
            isLoadingHistory = false
        } else {
            isLoadingHistory = true
            val fetched = viewModel.getBrandHistory(brandName)
            if (!fetched.isNullOrBlank()) {
                historyText = fetched
            }
            isLoadingHistory = false
        }
    }

    val emptyStateText = "Oops! Looks like we haven't gotten $displayBrand's eco-friendly history yet. Check back later!"

    val textToShow = historyText.takeIf { !it.isNullOrBlank() } ?: emptyStateText

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$displayBrand History",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 350.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    if (isLoadingHistory) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = Color(0xFF479C73),
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Fetching brand history for $displayBrand...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = textToShow,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 22.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF479C73)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun IngredientsTabContent(
    product: Product,
    viewModel: AppViewModel,
    boxColor: Color,
    boxBorder: BorderStroke,
    isEditModeEnabled: Boolean = false,
    onEditIngredients: () -> Unit = {}
) {
    val accentGreen = Color(0xFF479C73)
    val accentGold = Color(0xFFD29524)
    val solidBaseColor = boxColor.copy(alpha = 1f)

    val ingredientsText = product.ingredientsText
    val ingredientsModeration = remember(ingredientsText) {
        ContentModerationFilter.analyze(ingredientsText)
    }
    val species = product.species
    val pets by viewModel.pets.collectAsState()

    val prodSpeciesLower = species.lowercase().trim()
    val isProductCat = prodSpeciesLower.contains("cat")
    val isProductDog = prodSpeciesLower.contains("dog")

    val explainedIngredients = remember(ingredientsText, species) {
        IngredientAnalyzer.analyzeIngredients(ingredientsText, species)
    }

    val petAllergyMap = remember(pets, explainedIngredients, prodSpeciesLower) {
        explainedIngredients.associate { item ->
            val allergicPet = pets.firstOrNull { pet ->
                val petType = pet.type.lowercase().trim()
                val isPetCat = petType.contains("cat")
                val isPetDog = petType.contains("dog")

                val speciesMatches = when {
                    isProductCat -> isPetCat
                    isProductDog -> isPetDog
                    else -> true
                }

                if (!speciesMatches || pet.allergies.isBlank()) {
                    false
                } else {
                    val tokens = pet.allergies.lowercase()
                        .split(",", ";", "\n", "/")
                        .map { it.trim() }
                        .filter { it.length >= 2 }
                    tokens.any { token ->
                        item.name.lowercase().contains(token) || token.contains(item.name.lowercase())
                    }
                }
            }
            item.name to allergicPet?.name
        }
    }

    var expandedIngredientName by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .subtleCardShadow(borderRadius = 28.dp),
        shape = RoundedCornerShape(28.dp),
        color = solidBaseColor
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    clip = true
                    shape = RoundedCornerShape(28.dp)
                }
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val bgGradient = Brush.linearGradient(
                        colorStops = arrayOf(
                            0.0f to accentGreen.copy(alpha = 0.35f),
                            0.50f to accentGreen.copy(alpha = 0.15f),
                            1.0f to accentGold.copy(alpha = 0.28f)
                        ),
                        start = Offset(w, 0f),
                        end = Offset(0f, h)
                    )

                    val topLeftCircleCenter = Offset(w * 0.14f, h * 0.15f)
                    val topLeftCircleRadius = w * 0.42f
                    val topLeftCircleBrush = Brush.radialGradient(
                        colors = listOf(
                            accentGold.copy(alpha = 0.30f),
                            accentGold.copy(alpha = 0.11f),
                            accentGold.copy(alpha = 0f)
                        ),
                        center = topLeftCircleCenter,
                        radius = topLeftCircleRadius.coerceAtLeast(1f)
                    )

                    val bottomRightCircleCenter = Offset(w * 0.86f, h * 0.84f)
                    val bottomRightCircleRadius = w * 0.45f
                    val bottomRightCircleBrush = Brush.radialGradient(
                        colors = listOf(
                            accentGreen.copy(alpha = 0.32f),
                            accentGreen.copy(alpha = 0.12f),
                            accentGreen.copy(alpha = 0f)
                        ),
                        center = bottomRightCircleCenter,
                        radius = bottomRightCircleRadius.coerceAtLeast(1f)
                    )

                    val midLeftCircleCenter = Offset(w * 0.16f, h * 0.54f)
                    val midLeftCircleRadius = w * 0.28f
                    val midLeftCircleBrush = Brush.radialGradient(
                        colors = listOf(
                            accentGreen.copy(alpha = 0.22f),
                            accentGreen.copy(alpha = 0f)
                        ),
                        center = midLeftCircleCenter,
                        radius = midLeftCircleRadius.coerceAtLeast(1f)
                    )

                    val upperRightCircleCenter = Offset(w * 0.84f, h * 0.38f)
                    val upperRightCircleRadius = w * 0.30f
                    val upperRightCircleBrush = Brush.radialGradient(
                        colors = listOf(
                            accentGold.copy(alpha = 0.22f),
                            accentGold.copy(alpha = 0f)
                        ),
                        center = upperRightCircleCenter,
                        radius = upperRightCircleRadius.coerceAtLeast(1f)
                    )

                    onDrawBehind {
                        drawRect(color = solidBaseColor)
                        drawRect(brush = bgGradient)
                        drawCircle(brush = topLeftCircleBrush, radius = topLeftCircleRadius, center = topLeftCircleCenter)
                        drawCircle(brush = bottomRightCircleBrush, radius = bottomRightCircleRadius, center = bottomRightCircleCenter)
                        drawCircle(brush = midLeftCircleBrush, radius = midLeftCircleRadius, center = midLeftCircleCenter)
                        drawCircle(brush = upperRightCircleBrush, radius = upperRightCircleRadius, center = upperRightCircleCenter)
                    }
                }
        ) {
            Column(modifier = Modifier.padding(vertical = 20.dp)) {
                // Header
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ingredients Breakdown",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "Nutritional role and safety evaluation",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            )
                        }

                        if (isEditModeEnabled) {
                            IconButton(
                                onClick = onEditIngredients,
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(accentGreen.copy(alpha = 0.14f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Edit Ingredients",
                                    tint = accentGreen,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }

                    if (isEditModeEnabled && ingredientsModeration.containsBadWords) {
                        Spacer(modifier = Modifier.height(10.dp))
                        ModerationWarningBanner(moderation = ingredientsModeration)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Key Evaluated Ingredients:",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (explainedIngredients.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No detailed ingredients list available for this product.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        explainedIngredients.forEach { item ->
                            val isExpanded = expandedIngredientName.equals(item.name, ignoreCase = true)
                            val allergicPetName = petAllergyMap[item.name]

                            IngredientIntegratedItem(
                                item = item,
                                allergicPetName = allergicPetName,
                                isExpanded = isExpanded,
                                accentGreen = accentGreen,
                                accentGold = accentGold,
                                onClick = {
                                    expandedIngredientName = if (isExpanded) null else item.name
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Click an ingredient to expand for more details.",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun IngredientIntegratedItem(
    item: IngredientExplanation,
    allergicPetName: String?,
    isExpanded: Boolean,
    accentGreen: Color,
    accentGold: Color,
    onClick: () -> Unit
) {
    val isAllergic = !allergicPetName.isNullOrBlank()

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "ingredientInlineChevron"
    )

    val cleanGoodFor = remember(item.goodFor) {
        item.goodFor
            ?.replace("Good for:", "", ignoreCase = true)
            ?.replace("Good for", "", ignoreCase = true)
            ?.trim()
            ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            ?.ifBlank { null }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isAllergic) {
                    Modifier
                        .padding(horizontal = 8.dp, vertical = 1.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFE53935).copy(alpha = 0.20f),
                                    Color(0xFFE53935).copy(alpha = 0.08f),
                                    Color(0xFFE53935).copy(alpha = 0.20f)
                                )
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            1.dp,
                            Color(0xFFE53935).copy(alpha = 0.55f),
                            shape = RoundedCornerShape(12.dp)
                        )
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(
                    horizontal = if (isAllergic) 12.dp else 20.dp,
                    vertical = 3.5.dp
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Text(
                    text = item.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 16.sp,
                        fontWeight = if (isExpanded) FontWeight.Bold else FontWeight.Medium,
                        color = if (isAllergic) {
                            Color(0xFFD32F2F)
                        } else if (isExpanded) {
                            accentGreen
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                    )
                )

                val descText = cleanGoodFor ?: item.purpose.take(60).let { if (it.length >= 60) "$it..." else it }
                if (descText.isNotBlank()) {
                    Text(
                        text = descText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.5.sp,
                            lineHeight = 13.5.sp,
                            fontWeight = FontWeight.Normal,
                            color = if (isAllergic) Color(0xFFE53935).copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse ingredient details" else "Expand ingredient details",
                tint = if (isAllergic) Color(0xFFD32F2F) else if (isExpanded) accentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(chevronRotation)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
            exit = shrinkVertically(animationSpec = tween(200, easing = FastOutLinearInEasing)) + fadeOut(tween(140))
        ) {
            val isDark = isSystemInDarkTheme()
            val translucentPanelBg = if (isDark) {
                Color.White.copy(alpha = 0.05f)
            } else {
                Color.White.copy(alpha = 0.20f)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(translucentPanelBg)
            ) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = accentGreen.copy(alpha = 0.22f)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Top Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "INGREDIENT DETAILS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                letterSpacing = 0.6.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    // Notice if pet has a recorded allergy (no emoji, single line, smaller font)
                    if (!allergicPetName.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFE53935).copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "$allergicPetName is allergic to ${item.name}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD32F2F)
                                    )
                                )
                            }
                        }
                    }

                    // What it does for your pet (Justified)
                    if (item.purpose.isNotBlank()) {
                        Column {
                            Text(
                                text = "What it does for your pet",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = accentGreen
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.purpose,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 17.sp,
                                    textAlign = TextAlign.Justify
                                )
                            )
                        }
                    }

                    // Nutritional Considerations (Justified)
                    if (item.risks.isNotBlank()) {
                        Column {
                            Text(
                                text = "Nutritional considerations",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = accentGold
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.risks,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 17.sp,
                                    textAlign = TextAlign.Justify
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlternativesTabContent(
    currentProduct: Product,
    alternatives: List<Product>,
    viewModel: AppViewModel? = null,
    boxColor: Color,
    boxBorder: BorderStroke,
    onProductClick: (String) -> Unit,
    onCompareClick: (String) -> Unit = {}
) {
    AlternativesRevampedContent(
        currentProduct = currentProduct,
        alternatives = alternatives,
        viewModel = viewModel,
        boxColor = boxColor,
        boxBorder = boxBorder,
        onProductClick = onProductClick,
        onCompareClick = onCompareClick
    )
}

@Composable
fun ReviewsTabContent(
    productId: String,
    viewModel: AppViewModel,
    boxColor: Color,
    boxBorder: BorderStroke,
    onNavigateToLogin: () -> Unit = {},
    onReviewSubmitted: () -> Unit = {},
    onDeleteReview: () -> Unit = {}
) {
    LaunchedEffect(productId) {
        if (productId.isNotEmpty()) {
            viewModel.loadReviewsForProduct(productId)
        }
    }

    val reviewsMap by viewModel.productReviews.collectAsState()
    val realReviews = reviewsMap[productId] ?: emptyList()

    val auth = remember {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            null
        }
    }
    val currentUser = auth?.currentUser

    var selectedStarFilter by remember { mutableStateOf<Int?>(null) }
    var editingReviewKey by remember { mutableStateOf<String?>(null) }

    val displayedReviews = if (selectedStarFilter != null) {
        realReviews.filter { it.rating == selectedStarFilter }
    } else {
        realReviews
    }

    val avgRating = if (realReviews.isNotEmpty()) {
        realReviews.map { it.rating }.average()
    } else 0.0

    Column(modifier = Modifier.fillMaxWidth()) {
        if (realReviews.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .subtleCardShadow(borderRadius = 28.dp),
                shape = RoundedCornerShape(28.dp),
                color = boxColor,
                border = boxBorder
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = String.format("%.1f", avgRating),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 38.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            repeat(5) { index ->
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (index < avgRating.toInt()) Color(0xFFFFA000) else GrayMedium.copy(alpha = 0.4f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Based on ${realReviews.size} review${if (realReviews.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        )
                    }

                    Column(
                        modifier = Modifier.width(170.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        (5 downTo 1).forEach { stars ->
                            val count = realReviews.count { it.rating == stars }
                            val fraction = if (realReviews.isNotEmpty()) count.toFloat() / realReviews.size else 0f
                            val isSelected = selectedStarFilter == stars
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable {
                                        selectedStarFilter = if (isSelected) null else stars
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "$stars★",
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(fraction)
                                            .clip(CircleShape)
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFFFA000))
                                    )
                                }
                                Text(
                                    "$count",
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Write Review Section
        ReviewsFormSection(
            productId = productId,
            viewModel = viewModel,
            boxColor = boxColor,
            boxBorder = boxBorder,
            onNavigateToLogin = onNavigateToLogin,
            onReviewSubmitted = onReviewSubmitted
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Reviews List Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (selectedStarFilter != null) "Customer Reviews (${selectedStarFilter}★)" else "Customer Reviews",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            if (selectedStarFilter != null) {
                TextButton(onClick = { selectedStarFilter = null }) {
                    Text("Show All (${realReviews.size})", fontSize = 12.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (realReviews.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .subtleCardShadow(borderRadius = 24.dp),
                shape = RoundedCornerShape(24.dp),
                color = boxColor,
                border = boxBorder
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No reviews yet",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Be the first to rate and review this product!",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }
        } else if (displayedReviews.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .subtleCardShadow(borderRadius = 24.dp),
                shape = RoundedCornerShape(24.dp),
                color = boxColor,
                border = boxBorder
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No $selectedStarFilter-star reviews yet",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(onClick = { selectedStarFilter = null }) {
                        Text("Show all reviews")
                    }
                }
            }
        } else {
            val sortedReviews = remember(displayedReviews, currentUser?.uid) {
                displayedReviews.asReversed().sortedByDescending { review ->
                    currentUser != null && review.userId == currentUser.uid
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                sortedReviews.forEachIndexed { index, review ->
                    val reviewKey = "${review.userId}_${review.timestamp?.time ?: index}"
                    val isOwner = currentUser != null && review.userId == currentUser.uid
                    val isEditingThis = editingReviewKey == reviewKey
                    val ownerAccentGreen = Color(0xFF479C73)

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .subtleCardShadow(borderRadius = 24.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = if (isOwner) boxColor.copy(alpha = 1f) else boxColor,
                        border = if (isOwner) BorderStroke(1.5.dp, ownerAccentGreen.copy(alpha = 0.65f)) else boxBorder
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isOwner) {
                                        Modifier.background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    ownerAccentGreen.copy(alpha = 0.14f),
                                                    ownerAccentGreen.copy(alpha = 0.04f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Top Row: Avatar, Name + Stars/Rating badge, and 3-Dot Menu on the right
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        modifier = Modifier.size(40.dp),
                                        shape = CircleShape,
                                        color = if (isOwner) ownerAccentGreen.copy(alpha = 0.22f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        border = if (isOwner) BorderStroke(1.dp, ownerAccentGreen.copy(alpha = 0.5f)) else null
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = review.userName.take(1).uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                color = if (isOwner) ownerAccentGreen else MaterialTheme.colorScheme.primary,
                                                fontSize = 15.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = review.userName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (isOwner) {
                                                Surface(
                                                    shape = RoundedCornerShape(100.dp),
                                                    color = ownerAccentGreen.copy(alpha = 0.18f),
                                                    border = BorderStroke(1.dp, ownerAccentGreen.copy(alpha = 0.45f))
                                                ) {
                                                    Text(
                                                        text = "Your Review",
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = ownerAccentGreen
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            repeat(5) { starIndex ->
                                                Icon(
                                                    imageVector = Icons.Rounded.Star,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp),
                                                    tint = if (starIndex < review.rating) Color(0xFFFFA000) else GrayMedium.copy(alpha = 0.4f)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(100.dp),
                                                color = getRatingColor(review.rating).copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = getRatingLabel(review.rating),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }

                                if (isOwner) {
                                    var menuExpanded by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(
                                            onClick = { menuExpanded = true },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "Options",
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = menuExpanded,
                                            onDismissRequest = { menuExpanded = false },
                                            shape = RoundedCornerShape(20.dp),
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            tonalElevation = 6.dp
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Edit Comment", fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                                                onClick = {
                                                    menuExpanded = false
                                                    editingReviewKey = reviewKey
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete Comment", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    menuExpanded = false
                                                    viewModel.deleteProductReview(productId, review.userId)
                                                    onDeleteReview()
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Bold Gray Edge-to-Edge Divider Line
                            HorizontalDivider(
                                color = GrayMedium.copy(alpha = 0.5f),
                                thickness = 1.5.dp,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            if (isEditingThis) {
                                var inlineRating by remember(review) { mutableIntStateOf(review.rating) }
                                var inlineComment by remember(review) { mutableStateOf(review.comment) }

                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Edit Rating: ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Row {
                                            repeat(5) { idx ->
                                                val starIdx = idx + 1
                                                Icon(
                                                    imageVector = Icons.Rounded.Star,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clickable { inlineRating = starIdx },
                                                    tint = if (starIdx <= inlineRating) Color(0xFFFFA000) else GrayMedium.copy(alpha = 0.4f)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Your Comment",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 12.sp
                                        ),
                                        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .subtleCardShadow(borderRadius = 20.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = inlineComment,
                                            onValueChange = { inlineComment = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(20.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF479C73),
                                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                                focusedContainerColor = Color.Transparent,
                                                unfocusedContainerColor = Color.Transparent,
                                                cursorColor = Color(0xFF479C73)
                                            )
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(onClick = { editingReviewKey = null }) {
                                            Text("Cancel", fontSize = 12.sp)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                val updated = review.copy(rating = inlineRating, comment = inlineComment.trim())
                                                viewModel.addProductReview(productId, updated)
                                                editingReviewKey = null
                                                onReviewSubmitted()
                                            },
                                            modifier = Modifier.subtleCardShadow(borderRadius = 100.dp),
                                            shape = RoundedCornerShape(100.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF479C73))
                                        ) {
                                            Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = review.comment,
                                    fontSize = 13.sp,
                                    color = if (isOwner) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isOwner) FontWeight.Medium else FontWeight.Normal,
                                    lineHeight = 18.sp
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

fun getRatingLabel(rating: Int): String {
    return when (rating) {
        1 -> "Terrible"
        2 -> "Fair"
        3 -> "Not Bad"
        4 -> "Good"
        5 -> "Excellent"
        else -> "Not Bad"
    }
}

fun getRatingColor(rating: Int): Color {
    return when (rating) {
        1 -> Color(0xFFE53935)
        2 -> Color(0xFFFB8C00)
        3 -> Color(0xFFFBC02D)
        4 -> Color(0xFF7CB342)
        5 -> Color(0xFF43A047)
        else -> Color(0xFFFFA000)
    }
}

@Composable
fun ReviewsFormSection(
    productId: String,
    viewModel: AppViewModel,
    boxColor: Color,
    boxBorder: BorderStroke,
    onNavigateToLogin: () -> Unit = {},
    onReviewSubmitted: () -> Unit = {}
) {
    val isUserLoggedIn by viewModel.isUserLoggedIn.collectAsState()

    val auth = remember {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            null
        }
    }
    val currentUser = auth?.currentUser

    var userRating by remember { mutableIntStateOf(5) }
    var reviewComment by remember { mutableStateOf("") }
    var isFormExpanded by remember { mutableStateOf(false) }

    if (isUserLoggedIn && currentUser != null) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .subtleCardShadow(borderRadius = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = boxColor,
            border = boxBorder
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
            ) {
            if (!isFormExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isFormExpanded = true }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Give your thoughts here",
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { isFormExpanded = true },
                        modifier = Modifier.subtleCardShadow(borderRadius = 100.dp),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF479C73))
                    ) {
                        Text("Write Review", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            } else {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Write a Review",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Rating: ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(4.dp))
                            repeat(5) { index ->
                                val starIndex = index + 1
                                val isSelected = starIndex <= userRating
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clickable { userRating = starIndex },
                                    tint = if (isSelected) Color(0xFFFFA000) else GrayMedium.copy(alpha = 0.4f)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = getRatingColor(userRating).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, getRatingColor(userRating).copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = getRatingLabel(userRating),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Your Review",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.5.sp
                        ),
                        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
                    )
                    val reviewModeration = remember(reviewComment) {
                        ContentModerationFilter.analyze(reviewComment)
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .subtleCardShadow(borderRadius = 20.dp)
                    ) {
                        OutlinedTextField(
                            value = reviewComment,
                            onValueChange = { reviewComment = it },
                            placeholder = { Text("Share your experience with this product...", fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            isError = reviewModeration.containsBadWords,
                            visualTransformation = ModerationVisualTransformation,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (reviewModeration.containsBadWords) Color(0xFFD32F2F) else Color(0xFF479C73),
                                unfocusedBorderColor = if (reviewModeration.containsBadWords) Color(0xFFD32F2F) else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                errorBorderColor = Color(0xFFD32F2F),
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                cursorColor = Color(0xFF479C73)
                            )
                        )
                    }
                    if (reviewModeration.containsBadWords) {
                        Spacer(modifier = Modifier.height(8.dp))
                        ModerationWarningBanner(moderation = reviewModeration)
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { isFormExpanded = false }) {
                            Text("Cancel", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (reviewComment.isNotBlank() && !reviewModeration.containsBadWords) {
                                    val displayName = currentUser.displayName ?: currentUser.email?.substringBefore("@") ?: "Pet Parent"
                                    val newReview = ProductReview(
                                        userId = currentUser.uid,
                                        userName = displayName,
                                        rating = userRating,
                                        comment = reviewComment.trim()
                                    )
                                    viewModel.addProductReview(productId, newReview)
                                    reviewComment = ""
                                    userRating = 5
                                    isFormExpanded = false
                                    onReviewSubmitted()
                                }
                            },
                            enabled = reviewComment.isNotBlank() && !reviewModeration.containsBadWords,
                            modifier = Modifier.subtleCardShadow(borderRadius = 100.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF479C73)),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Text(
                                text = "Submit Review",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
            }
        }
    } else {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .subtleCardShadow(borderRadius = 24.dp)
                .bouncyClickable { onNavigateToLogin() },
            shape = RoundedCornerShape(24.dp),
            color = boxColor,
            border = boxBorder
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Sign in to share your review and rating for this product.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onNavigateToLogin,
                    modifier = Modifier.subtleCardShadow(borderRadius = 100.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF479C73))
                ) {
                    Text("Log In to Write a Review", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun FriendlyLoadingScreen() {
    val messages = listOf(
        "Scanning nutrition details...",
        "Evaluating FEDIAF & AAFCO goals...",
        "Analyzing ingredient safety...",
        "Finding smart alternatives..."
    )
    var currentMessageIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1800)
            currentMessageIndex = (currentMessageIndex + 1) % messages.size
        }
    }

    // Single shared transition for all skeleton elements - maximizes 120fps smoothness & eliminates lag
    val infiniteTransition = rememberInfiniteTransition(label = "screenSkeletonShimmer")
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )

    CompositionLocalProvider(LocalShimmerProgress provides shimmerProgress) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Background subtle top gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF479C73).copy(alpha = 0.15f),
                                Color(0xFF479C73).copy(alpha = 0.05f),
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
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Top Navigation Bar Skeleton
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SkeletonBox(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape
                    )
                    SkeletonBox(
                        modifier = Modifier
                            .width(140.dp)
                            .height(26.dp),
                        shape = RoundedCornerShape(100.dp)
                    )
                    SkeletonBox(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Hero Product Card Skeleton (Light surface with high-performance elevation)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Brand Capsule
                        SkeletonBox(
                            modifier = Modifier
                                .width(88.dp)
                                .height(22.dp),
                            shape = RoundedCornerShape(100.dp)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Large Product Image
                        SkeletonBox(
                            modifier = Modifier.size(136.dp),
                            shape = RoundedCornerShape(24.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Title line 1 & 2
                        SkeletonBox(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(20.dp),
                            shape = RoundedCornerShape(100.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SkeletonBox(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .height(14.dp),
                            shape = RoundedCornerShape(100.dp)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Tags row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SkeletonBox(
                                modifier = Modifier
                                    .width(68.dp)
                                    .height(26.dp),
                                shape = RoundedCornerShape(100.dp)
                            )
                            SkeletonBox(
                                modifier = Modifier
                                    .width(82.dp)
                                    .height(26.dp),
                                shape = RoundedCornerShape(100.dp)
                            )
                            SkeletonBox(
                                modifier = Modifier
                                    .width(74.dp)
                                    .height(26.dp),
                                shape = RoundedCornerShape(100.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Nutrition Score Banner
                        SkeletonBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(68.dp),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Tabs Capsule Skeleton
                SkeletonBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(100.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Evaluation Content Card Skeleton
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header of assessment
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SkeletonBox(
                                modifier = Modifier
                                    .width(150.dp)
                                    .height(22.dp),
                                shape = RoundedCornerShape(100.dp)
                            )
                            SkeletonBox(
                                modifier = Modifier
                                    .width(64.dp)
                                    .height(22.dp),
                                shape = RoundedCornerShape(100.dp)
                            )
                        }

                        // 4 Nutrient Item Skeletons
                        repeat(4) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SkeletonBox(
                                    modifier = Modifier.size(36.dp),
                                    shape = CircleShape
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    SkeletonBox(
                                        modifier = Modifier
                                            .fillMaxWidth(0.5f)
                                            .height(14.dp),
                                        shape = RoundedCornerShape(100.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    SkeletonBox(
                                        modifier = Modifier
                                            .fillMaxWidth(0.32f)
                                            .height(11.dp),
                                        shape = RoundedCornerShape(100.dp)
                                    )
                                }
                                SkeletonBox(
                                    modifier = Modifier
                                        .width(58.dp)
                                        .height(24.dp),
                                    shape = RoundedCornerShape(100.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Dynamic Informative Status Pill
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Crossfade(
                                targetState = messages[currentMessageIndex],
                                label = "skeletonMsgFade"
                            ) { msg ->
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun ScanSuccessfulOverlay() {
    var isVisible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.5f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    LaunchedEffect(Unit) {
        isVisible = true
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .scale(scale)
                .width(280.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Product Found!",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Product details are ready",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ErrorScreen(errorMsg: String, onBack: () -> Unit, onSuggestProduct: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_barcode),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = errorMsg,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onBackground,
                lineHeight = 24.sp
            )
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Try Again", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onSuggestProduct,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Suggest a Product", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RatingSection(title: String, result: ScoreResult, color: Color, boxColor: Color, boxBorder: BorderStroke) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .subtleCardShadow(borderRadius = 28.dp),
            shape = RoundedCornerShape(28.dp),
            color = boxColor,
            border = boxBorder
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            result.rating,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                        )
                        Text(
                            "Score: ${result.percentage}%",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(color.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${result.percentage}",
                            style = MaterialTheme.typography.titleLarge.copy(color = color, fontWeight = FontWeight.Bold)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    result.interpretation,
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(8.dp))
                
                result.criteriaScores.forEach { (name, score) ->
                    CriteriaRow(name, score, color)
                }
            }
        }
    }
}

@Composable
fun CriteriaRow(name: String, score: Int, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Row {
            repeat(5) { index ->
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (index < score) color else GrayMedium.copy(alpha = 0.4f)
                )
            }
        }
    }
}

data class ParsedNutrient(
    val key: String,
    val name: String,
    val valueDisplay: String,
    val numericValue: Double?
)

fun parseNutrientEntry(rawKey: String, rawValue: Any?): ParsedNutrient {
    if (rawValue == null) {
        return ParsedNutrient(
            key = rawKey,
            name = formatNutrientLabel(rawKey),
            valueDisplay = "N/A",
            numericValue = null
        )
    }

    if (rawValue is Map<*, *>) {
        val nameStr = rawValue["name"]?.toString()?.takeIf { it.isNotBlank() } ?: formatNutrientLabel(rawKey)
        val unitStr = rawValue["unit"]?.toString()?.trim() ?: ""
        val valObj = rawValue["value"] ?: rawValue["val"] ?: rawValue["amount"]

        val numVal = when (valObj) {
            is Number -> valObj.toDouble()
            is String -> valObj.replace("[^0-9.]".toRegex(), "").toDoubleOrNull()
            else -> null
        }

        val valStr = when {
            valObj is Number -> {
                val d = valObj.toDouble()
                if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
            }
            valObj != null -> valObj.toString()
            else -> ""
        }

        val formattedValueDisplay = when {
            valStr.isNotEmpty() && unitStr.isNotEmpty() -> {
                if (valStr.endsWith(unitStr)) valStr else "$valStr $unitStr".replace(" %", "%")
            }
            valStr.isNotEmpty() -> valStr
            else -> "N/A"
        }

        return ParsedNutrient(
            key = rawKey,
            name = nameStr,
            valueDisplay = formattedValueDisplay,
            numericValue = numVal
        )
    }

    if (rawValue is Number) {
        val d = rawValue.toDouble()
        val formattedNum = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
        return ParsedNutrient(
            key = rawKey,
            name = formatNutrientLabel(rawKey),
            valueDisplay = "$formattedNum%",
            numericValue = d
        )
    }

    if (rawValue is String) {
        val numVal = rawValue.replace("[^0-9.]".toRegex(), "").toDoubleOrNull()
        return ParsedNutrient(
            key = rawKey,
            name = formatNutrientLabel(rawKey),
            valueDisplay = rawValue,
            numericValue = numVal
        )
    }

    return ParsedNutrient(
        key = rawKey,
        name = formatNutrientLabel(rawKey),
        valueDisplay = rawValue.toString(),
        numericValue = null
    )
}

fun formatNutrientLabel(rawKey: String): String {
    val key = rawKey.trim()
        .replace("-", " ")
        .replace("_", " ")
    
    return key.split(" ")
        .filter { it.isNotEmpty() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
        }
}

fun extractNumericValue(value: Any?): Double? {
    if (value == null) return null
    return when (value) {
        is Number -> value.toDouble()
        is String -> {
            val cleaned = value.replace("[^0-9.]".toRegex(), "")
            cleaned.toDoubleOrNull()
        }
        else -> null
    }
}

@Composable
private fun PartialDashBadge(
    tint: Color,
    size: androidx.compose.ui.unit.Dp = 18.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(tint.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(size * 0.55f)
                .height(2.2.dp)
                .background(tint, RoundedCornerShape(100.dp))
        )
    }
}

@Composable
fun FediafNutritionCheckCard(
    assessment: com.example.furbetterchoice.api.NutritionAssessment,
    selectedNutrient: String = "",
    onNutrientClick: (String) -> Unit = {},
    boxColor: Color,
    shineProgressProvider: () -> Float = { 0f },
    isEditModeEnabled: Boolean = false,
    lowConfidenceNutrientKeys: Set<String> = emptySet(),
    onEditNutrients: (String?) -> Unit = {}
) {
    var showDmHelp by remember { mutableStateOf(false) }
    val accentGreen = Color(0xFF479C73)
    val accentGold = Color(0xFFD29524)
    val solidBaseColor = boxColor.copy(alpha = 1f)

    // Build integrated expandable list: primary threshold nutrients + listed Fiber/Ash + Ca:P Ratio
    val integratedItems = remember(assessment) {
        val items = assessment.nutrientAssessments.filter { item ->
            when (item.nutrientKey) {
                "crudeProtein", "crudeFat", "calcium", "phosphorus", "taurine" -> true
                "crudeFiber", "ash" -> item.asFedValue != null
                "moisture" -> false
                else -> false
            }
        }.toMutableList()

        val caItem = assessment.nutrientAssessments.find { it.nutrientKey == "calcium" }
        val pItem = assessment.nutrientAssessments.find { it.nutrientKey == "phosphorus" }
        if (assessment.caRatioText != null || caItem != null || pItem != null) {
            val minStr = String.format(Locale.US, "%.2f", assessment.caRatioMin)
            val maxStr = String.format(Locale.US, "%.2f", assessment.caRatioMax)
            val ratioThresholdText = "$minStr : 1 to $maxStr : 1 (N)"
            val ratioExplanation = when {
                assessment.isTreatOrComplementary ->
                    "Complementary food / treat: Ca:P ratio is shown for reference only."
                assessment.caRatio == null ->
                    "Both Calcium and Phosphorus must be listed on the label to check their balance."
                assessment.caRatioStatus == NutritionStatus.MEETS ->
                    "Great! A ${String.format(Locale.US, "%.2f", assessment.caRatio)} : 1 ratio is safely within the $minStr:1 to $maxStr:1 range."
                assessment.caRatioStatus == NutritionStatus.ABOVE_MAXIMUM ->
                    "Warning: A ${String.format(Locale.US, "%.2f", assessment.caRatio)} : 1 ratio exceeds the FEDIAF nutritional maximum of $maxStr:1."
                else ->
                    "Warning: A ${String.format(Locale.US, "%.2f", assessment.caRatio)} : 1 ratio is below the FEDIAF minimum of $minStr:1."
            }
            items.add(
                com.example.furbetterchoice.api.NutrientAssessment(
                    nutrientKey = "calciumPhosphorusRatio",
                    name = "Ca : P Ratio",
                    asFedValue = assessment.caRatio,
                    asFedUnit = ":1",
                    dryMatterValue = assessment.caRatio,
                    referenceThresholdText = ratioThresholdText,
                    status = assessment.caRatioStatus,
                    resultText = assessment.caRatioText ?: "Not listed",
                    explanation = ratioExplanation,
                    minThreshold = assessment.caRatioMin,
                    maxThreshold = assessment.caRatioMax,
                    isNutritionalMaximum = true
                )
            )
        }
        items
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .subtleCardShadow(borderRadius = 28.dp),
        shape = RoundedCornerShape(28.dp),
        color = solidBaseColor
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    clip = true
                    shape = RoundedCornerShape(28.dp)
                }
                .shiningRippleOverlay(shineProgressProvider = shineProgressProvider)
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    // Angle: Top-Right to Bottom-Left (distinct from BrandHistoryCard & AAFCO)
                    val bgGradient = Brush.linearGradient(
                        colorStops = arrayOf(
                            0.0f to accentGreen.copy(alpha = 0.35f),
                            0.50f to accentGreen.copy(alpha = 0.15f),
                            1.0f to accentGold.copy(alpha = 0.28f)
                        ),
                        start = Offset(w, 0f),
                        end = Offset(0f, h)
                    )

                    val topLeftCircleCenter = Offset(w * 0.14f, h * 0.15f)
                    val topLeftCircleRadius = w * 0.42f
                    val topLeftCircleBrush = Brush.radialGradient(
                        colors = listOf(
                            accentGold.copy(alpha = 0.30f),
                            accentGold.copy(alpha = 0.11f),
                            accentGold.copy(alpha = 0f)
                        ),
                        center = topLeftCircleCenter,
                        radius = topLeftCircleRadius.coerceAtLeast(1f)
                    )

                    val bottomRightCircleCenter = Offset(w * 0.86f, h * 0.84f)
                    val bottomRightCircleRadius = w * 0.45f
                    val bottomRightCircleBrush = Brush.radialGradient(
                        colors = listOf(
                            accentGreen.copy(alpha = 0.32f),
                            accentGreen.copy(alpha = 0.12f),
                            accentGreen.copy(alpha = 0f)
                        ),
                        center = bottomRightCircleCenter,
                        radius = bottomRightCircleRadius.coerceAtLeast(1f)
                    )

                    val midLeftCircleCenter = Offset(w * 0.16f, h * 0.54f)
                    val midLeftCircleRadius = w * 0.28f
                    val midLeftCircleBrush = Brush.radialGradient(
                        colors = listOf(
                            accentGreen.copy(alpha = 0.22f),
                            accentGreen.copy(alpha = 0f)
                        ),
                        center = midLeftCircleCenter,
                        radius = midLeftCircleRadius.coerceAtLeast(1f)
                    )

                    val upperRightCircleCenter = Offset(w * 0.84f, h * 0.38f)
                    val upperRightCircleRadius = w * 0.30f
                    val upperRightCircleBrush = Brush.radialGradient(
                        colors = listOf(
                            accentGold.copy(alpha = 0.22f),
                            accentGold.copy(alpha = 0f)
                        ),
                        center = upperRightCircleCenter,
                        radius = upperRightCircleRadius.coerceAtLeast(1f)
                    )

                    onDrawBehind {
                        drawRect(color = solidBaseColor)
                        drawRect(brush = bgGradient)
                        drawCircle(
                            brush = topLeftCircleBrush,
                            radius = topLeftCircleRadius,
                            center = topLeftCircleCenter
                        )
                        drawCircle(
                            brush = bottomRightCircleBrush,
                            radius = bottomRightCircleRadius,
                            center = bottomRightCircleCenter
                        )
                        drawCircle(
                            brush = midLeftCircleBrush,
                            radius = midLeftCircleRadius,
                            center = midLeftCircleCenter
                        )
                        drawCircle(
                            brush = upperRightCircleBrush,
                            radius = upperRightCircleRadius,
                            center = upperRightCircleCenter
                        )
                    }
                }
        ) {
            Column(modifier = Modifier.padding(vertical = 20.dp)) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "FEDIAF Assessment",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            AnimatedContent(
                                targetState = "${assessment.targetPetType} · ${assessment.targetLifeStage} (${assessment.merMode.displayName})",
                                transitionSpec = {
                                    fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                                },
                                label = "fediafContextSubtitle"
                            ) { subtitle ->
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isEditModeEnabled) {
                                IconButton(
                                    onClick = { onEditNutrients(null) },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(accentGreen.copy(alpha = 0.14f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Edit Nutrients",
                                        tint = accentGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            IconButton(onClick = { showDmHelp = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = "Dry Matter Info",
                                    tint = accentGreen,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    if (assessment.isSeniorMappedToAdult && assessment.seniorMappingExplanation != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = solidBaseColor.copy(alpha = 0.88f),
                            border = BorderStroke(1.dp, accentGreen.copy(alpha = 0.28f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = accentGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = assessment.seniorMappingExplanation,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (assessment.isTreatOrComplementary && assessment.treatExemptionMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = solidBaseColor.copy(alpha = 0.88f),
                            border = BorderStroke(1.dp, accentGold.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = accentGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = assessment.treatExemptionMessage,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Compact Expandable Guide: What is FEDIAF?
                    StandardGuideExpandableBanner(
                        standard = AssessmentStandard.FEDIAF,
                        baseSurfaceColor = solidBaseColor
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Overall Status Banner (Icon inline beside headline; dash for partial)
                    val (bannerBg, iconColor) = when (assessment.overallStatus) {
                        NutritionStatus.MEETS -> BgPastelGreen to TextDarkGreen
                        NutritionStatus.BELOW -> Color(0xFFFFF3E0) to Color(0xFFE65100)
                        NutritionStatus.ABOVE_MAXIMUM -> Color(0xFFFFEBEE) to Color(0xFFC62828)
                        NutritionStatus.INFORMATIONAL -> solidBaseColor.copy(alpha = 0.88f) to MaterialTheme.colorScheme.onSurfaceVariant
                        NutritionStatus.INSUFFICIENT_DATA -> solidBaseColor.copy(alpha = 0.88f) to MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = bannerBg
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                when (assessment.overallStatus) {
                                    NutritionStatus.MEETS -> {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = iconColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    NutritionStatus.BELOW, NutritionStatus.ABOVE_MAXIMUM -> {
                                        Icon(
                                            imageVector = Icons.Rounded.Warning,
                                            contentDescription = null,
                                            tint = iconColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    else -> {
                                        PartialDashBadge(tint = iconColor, size = 18.dp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = assessment.overallHeadline,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = iconColor
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = assessment.overallMessage,
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp,
                                textAlign = TextAlign.Justify,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Integrated Expandable Key Evaluated Nutrients List
                    Text(
                        text = "Key Evaluated Nutrients (Dry Matter Basis):",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    integratedItems.forEach { item ->
                        val isExpanded = selectedNutrient.equals(item.nutrientKey, ignoreCase = true) ||
                            selectedNutrient.equals(item.name, ignoreCase = true)

                        FediafIntegratedNutrientItem(
                            item = item,
                            isExpanded = isExpanded,
                            solidBaseColor = solidBaseColor,
                            accentGreen = accentGreen,
                            accentGold = accentGold,
                            isLowConfidence = lowConfidenceNutrientKeys.contains(item.nutrientKey),
                            isEditModeEnabled = isEditModeEnabled,
                            onEditClick = { onEditNutrients(item.nutrientKey) },
                            onClick = { onNutrientClick(item.name) }
                        )
                    }

                    if (assessment.energyResult.source != EnergySource.UNAVAILABLE) {
                        val isEnergyExpanded = selectedNutrient.equals("metabolizableEnergy", ignoreCase = true) ||
                            selectedNutrient.equals("Metabolizable Energy", ignoreCase = true)

                        MetabolizableEnergyIntegratedItem(
                            energyResult = assessment.energyResult,
                            standardLabel = "FEDIAF",
                            isExpanded = isEnergyExpanded,
                            accentGreen = accentGreen,
                            onClick = { onNutrientClick("Metabolizable Energy") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Click a nutrient to expand for more details.",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                )
            }
        }
    }

    if (showDmHelp) {
        DryMatterHelpDialog(
            standard = AssessmentStandard.FEDIAF,
            onDismiss = { showDmHelp = false }
        )
    }
}

@Composable
private fun FediafIntegratedNutrientItem(
    item: com.example.furbetterchoice.api.NutrientAssessment,
    isExpanded: Boolean,
    solidBaseColor: Color,
    accentGreen: Color,
    accentGold: Color,
    isLowConfidence: Boolean = false,
    isEditModeEnabled: Boolean = false,
    onEditClick: () -> Unit = {},
    onClick: () -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "fediafInlineChevron"
    )

    val isRatio = item.nutrientKey == "calciumPhosphorusRatio" || item.asFedUnit == ":1"
    val minRefNumber = item.minThreshold ?: remember(item.referenceThresholdText) {
        Regex("""(\d+(?:\.\d+)?)""").find(item.referenceThresholdText)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }
    val hasStandardGoal = isRatio || item.minThreshold != null || item.maxThreshold != null
    val hasProductValue = item.asFedValue != null || item.dryMatterValue != null
    val isEvaluatedAgainstGoal = item.status == NutritionStatus.MEETS ||
        item.status == NutritionStatus.BELOW ||
        item.status == NutritionStatus.ABOVE_MAXIMUM

    val pctOfMinimum = remember(item.dryMatterValue, minRefNumber, isRatio, isEvaluatedAgainstGoal) {
        if (!isEvaluatedAgainstGoal) {
            null
        } else if (!isRatio && item.dryMatterValue != null && minRefNumber != null && minRefNumber > 0.0) {
            (item.dryMatterValue / minRefNumber) * 100.0
        } else if (isRatio && item.dryMatterValue != null) {
            item.dryMatterValue * 100.0
        } else {
            null
        }
    }

    // Concise right-side value without any "(... as fed)" text
    val conciseValueText = remember(item, isRatio) {
        when {
            isRatio -> if (item.dryMatterValue != null) String.format(Locale.US, "%.2f : 1", item.dryMatterValue) else "Not listed"
            item.dryMatterValue != null -> "${String.format(Locale.US, "%.1f", item.dryMatterValue)}% DM"
            item.asFedValue != null -> "${String.format(Locale.US, "%.1f", item.asFedValue)}${item.asFedUnit}"
            else -> "Not listed"
        }
    }

    val dryMatterDisplay = when {
        item.dryMatterValue != null -> {
            if (isRatio) String.format(Locale.US, "%.2f : 1", item.dryMatterValue)
            else "${String.format(Locale.US, "%.1f", item.dryMatterValue)}% DM"
        }
        item.asFedValue != null -> "Moisture (%) Not Provided"
        else -> "Not Provided"
    }

    val onLabelDisplay = if (item.asFedValue != null) {
        if (isRatio) String.format(Locale.US, "%.2f : 1", item.asFedValue)
        else "${String.format(Locale.US, "%.1f", item.asFedValue)}${item.asFedUnit}"
    } else {
        "Not Provided"
    }

    val per1000KcalDisplay = remember(item.energyBasisValue, item.energyBasisUnit) {
        if (item.energyBasisValue != null && !item.energyBasisUnit.isNullOrBlank()) {
            String.format(Locale.US, "%.1f %s", item.energyBasisValue, item.energyBasisUnit)
        } else {
            null
        }
    }

    val fediafGoalDisplay = remember(item, isRatio, minRefNumber) {
        fun fmtGoal(v: Double): String = if (v < 1.0 && (v * 100.0) % 10.0 != 0.0) {
            String.format(Locale.US, "%.2f", v)
        } else {
            String.format(Locale.US, "%.1f", v)
        }
        when {
            isRatio -> item.referenceThresholdText
            item.minThreshold != null && item.maxThreshold != null -> {
                val nTag = if (item.isNutritionalMaximum) " (N)" else ""
                "${fmtGoal(item.minThreshold)}% – ${fmtGoal(item.maxThreshold)}%$nTag"
            }
            minRefNumber != null -> "At least ${fmtGoal(minRefNumber)}%"
            item.nutrientKey == "crudeFiber" -> "No Universal Minimum"
            item.nutrientKey == "ash" -> "No Universal Limit"
            else -> "Informational Only"
        }
    }

    val (badgeText, badgeBg, badgeTextColor) = when (item.status) {
        NutritionStatus.MEETS -> Triple("✓ Meets Standard", BgPastelGreen, TextDarkGreen)
        NutritionStatus.BELOW -> Triple("! Below Minimum", Color(0xFFFFF3E0), Color(0xFFE65100))
        NutritionStatus.ABOVE_MAXIMUM -> Triple("! Above Safe Limit", Color(0xFFFFEBEE), Color(0xFFC62828))
        NutritionStatus.INFORMATIONAL -> Triple("— Info", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        NutritionStatus.INSUFFICIENT_DATA -> Triple("— Not Listed", MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), GrayMedium)
    }

    val statusAccentColor = when (item.status) {
        NutritionStatus.MEETS -> accentGreen
        NutritionStatus.BELOW -> Color(0xFFE65100)
        NutritionStatus.ABOVE_MAXIMUM -> Color(0xFFC62828)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val (whatItDoes, whyFediafSetsGoal) = remember(item) {
        val functionText = when (item.nutrientKey) {
            "crudeProtein" -> "Builds and repairs muscles, organs, skin, coat, and supports a strong immune system."
            "crudeFat" -> "Provides concentrated daily energy, supports healthy skin and a shiny coat, and helps absorb vitamins."
            "calcium" -> "Keeps bones and teeth strong and supports healthy muscle and nerve function."
            "phosphorus" -> "Works together with calcium to build strong bones and teeth and helps cells produce energy."
            "taurine" -> "An essential heart and eye nutrient for cats that prevents serious heart and vision problems."
            "crudeFiber" -> "Supports healthy digestion, gut motility, and firm stool quality."
            "ash" -> "Represents the total mineral content (such as calcium, phosphorus, and trace minerals) in the food."
            "calciumPhosphorusRatio" -> "Measures the balance between calcium and phosphorus so both minerals can be absorbed properly."
            else -> item.explanation
        }
        val reasonText = when (item.nutrientKey) {
            "crudeProtein" -> "FEDIAF sets a minimum protein level so pets get enough building blocks for healthy muscles and organs."
            "crudeFat" -> "FEDIAF sets a minimum fat level to ensure enough daily calories and essential fatty acids."
            "calcium" -> "FEDIAF sets calcium minimums and nutritional maximums (N) to prevent weak bones or skeletal developmental issues."
            "phosphorus" -> "FEDIAF sets a minimum phosphorus level (and a nutritional maximum for adult cats) to support bone strength and kidney safety."
            "taurine" -> "Cats cannot make enough taurine on their own, so FEDIAF requires it in complete cat foods."
            "crudeFiber" -> "FEDIAF does not define a single universal minimum fibre percentage because ideal fibre levels depend on the pet's digestive needs and formula type."
            "ash" -> "Crude ash measures combined total minerals. FEDIAF evaluates individual essential minerals like Calcium and Phosphorus directly rather than setting one universal limit on total ash."
            "calciumPhosphorusRatio" -> "FEDIAF recommends keeping calcium within its safe ratio range relative to phosphorus (${item.referenceThresholdText})."
            else -> "Evaluated against FEDIAF nutritional guidelines on a dry matter basis."
        }
        functionText to reasonText
    }

    val missingGoalOrInfoReason = remember(item, hasStandardGoal, hasProductValue, pctOfMinimum, isRatio, whyFediafSetsGoal) {
        when {
            !hasStandardGoal -> "Why there is no minimum goal" to whyFediafSetsGoal
            pctOfMinimum == null && !hasProductValue -> {
                val reason = if (isRatio) {
                    "Both Calcium (%) and Phosphorus (%) must be provided on the product's nutrition label to calculate their balance and compare against the ${item.referenceThresholdText} FEDIAF goal."
                } else {
                    "This product's nutrition label does not list ${item.name}, so Dry Matter basis and minimum goal coverage cannot be calculated."
                }
                "Why this could not be evaluated" to reason
            }
            pctOfMinimum == null && item.dryMatterValue == null -> {
                "Why minimum goal coverage is unavailable" to
                    "The label lists ${item.name}, but Moisture (%) is not provided on the package, so water cannot be removed to calculate Dry Matter basis or minimum goal coverage."
            }
            pctOfMinimum == null -> {
                "Why minimum goal coverage is unavailable" to
                    "The product's target life stage is not specified on the label, so life-stage-specific FEDIAF minimum goal coverage cannot be finalized."
            }
            else -> null
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                when (item.status) {
                    NutritionStatus.MEETS -> {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = accentGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    NutritionStatus.BELOW -> {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = accentGold,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    NutritionStatus.ABOVE_MAXIMUM -> {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = Color(0xFFC62828),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    else -> {
                        PartialDashBadge(tint = GrayMedium, size = 15.dp)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.name,
                    fontSize = 13.sp,
                    fontWeight = if (isExpanded) FontWeight.Bold else FontWeight.Medium,
                    color = if (isExpanded) accentGreen else MaterialTheme.colorScheme.onSurface
                )
                if (isLowConfidence) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFFF8E1),
                        border = BorderStroke(1.dp, accentGold.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "Verify",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF9A6700),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conciseValueText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isExpanded) accentGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isEditModeEnabled && item.nutrientKey != "calciumPhosphorusRatio") {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit ${item.name}",
                            tint = accentGreen,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse nutrient details" else "Expand nutrient details",
                    tint = if (isExpanded) accentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(chevronRotation)
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
            exit = shrinkVertically(animationSpec = tween(200, easing = FastOutLinearInEasing)) + fadeOut(tween(140))
        ) {
            ExpandedNutrientBreakdownPanel(
                standardLabel = "FEDIAF",
                badgeText = badgeText,
                badgeBg = badgeBg,
                badgeTextColor = badgeTextColor,
                statusAccentColor = statusAccentColor,
                accentGreen = accentGreen,
                hasStandardGoal = hasStandardGoal,
                hasProductValue = hasProductValue,
                dryMatterDisplay = dryMatterDisplay,
                goalDisplay = fediafGoalDisplay,
                onLabelDisplay = onLabelDisplay,
                labelMissingStatusText = "Not Provided",
                percentageOfMinimum = pctOfMinimum,
                per1000KcalDisplay = per1000KcalDisplay,
                whatItDoes = whatItDoes,
                whyStandardSetsGoal = whyFediafSetsGoal,
                missingGoalOrInfoReason = missingGoalOrInfoReason
            )
        }
    }
}

@Composable
private fun CenteredRoundedMetricTile(
    label: String,
    value: String,
    valueColor: Color,
    borderColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = fillColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ExpandedNutrientBreakdownPanel(
    standardLabel: String,
    badgeText: String,
    badgeBg: Color,
    badgeTextColor: Color,
    statusAccentColor: Color,
    accentGreen: Color,
    hasStandardGoal: Boolean,
    hasProductValue: Boolean,
    dryMatterDisplay: String,
    goalDisplay: String,
    onLabelDisplay: String,
    labelMissingStatusText: String = "Not Provided",
    percentageOfMinimum: Double?,
    per1000KcalDisplay: String? = null,
    whatItDoes: String,
    whyStandardSetsGoal: String,
    missingGoalOrInfoReason: Pair<String, String>? = null,
    additionalNote: String? = null
) {
    val isDark = isSystemInDarkTheme()
    val showOfMinimumGoal = hasStandardGoal && percentageOfMinimum != null

    val targetProgress = remember(percentageOfMinimum, showOfMinimumGoal) {
        if (showOfMinimumGoal && percentageOfMinimum != null) {
            (percentageOfMinimum / 100.0).toFloat().coerceIn(0.06f, 1f)
        } else {
            null
        }
    }
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress ?: 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "nutrientGoalProgress"
    )

    // Translucent frosted wash so the underlying green/gold assessment gradient shines through
    val translucentPanelBg = if (isDark) {
        Color.White.copy(alpha = 0.05f)
    } else {
        Color.White.copy(alpha = 0.20f)
    }
    val tileFillColor = if (isDark) {
        Color.White.copy(alpha = 0.06f)
    } else {
        Color.White.copy(alpha = 0.28f)
    }
    val standardBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val goalBorderColor = accentGreen.copy(alpha = 0.42f)
    val hairlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(translucentPanelBg)
    ) {
        HorizontalDivider(
            thickness = 1.dp,
            color = accentGreen.copy(alpha = 0.22f)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            // Top Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NUTRIENT BREAKDOWN",
                    fontSize = 10.sp,
                    letterSpacing = 0.6.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = badgeText,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeTextColor
                    )
                }
            }

            if (targetProgress != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(accentGreen.copy(alpha = 0.16f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(100.dp))
                            .background(statusAccentColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Adaptive Centered Rounded-Border Metric Layout
            when {
                // Case 1: Full evaluated nutrient -> 2x2 centered rounded-border grid
                showOfMinimumGoal && percentageOfMinimum != null -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CenteredRoundedMetricTile(
                                label = "Dry Matter Basis",
                                value = dryMatterDisplay,
                                valueColor = MaterialTheme.colorScheme.onSurface,
                                borderColor = standardBorderColor,
                                fillColor = tileFillColor,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                            CenteredRoundedMetricTile(
                                label = "$standardLabel Goal",
                                value = goalDisplay,
                                valueColor = accentGreen,
                                borderColor = goalBorderColor,
                                fillColor = tileFillColor,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CenteredRoundedMetricTile(
                                label = "On Label (As Sold)",
                                value = onLabelDisplay,
                                valueColor = MaterialTheme.colorScheme.onSurface,
                                borderColor = standardBorderColor,
                                fillColor = tileFillColor,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                            CenteredRoundedMetricTile(
                                label = "Of Minimum Goal",
                                value = String.format(Locale.US, "%.0f%%", percentageOfMinimum),
                                valueColor = statusAccentColor,
                                borderColor = statusAccentColor.copy(alpha = 0.42f),
                                fillColor = tileFillColor,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }

                // Case 2: No universal minimum / informational nutrient -> Of Minimum Goal removed!
                !hasStandardGoal -> {
                    if (hasProductValue) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CenteredRoundedMetricTile(
                                    label = "Dry Matter Basis",
                                    value = dryMatterDisplay,
                                    valueColor = MaterialTheme.colorScheme.onSurface,
                                    borderColor = standardBorderColor,
                                    fillColor = tileFillColor,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                                CenteredRoundedMetricTile(
                                    label = "On Label (As Sold)",
                                    value = onLabelDisplay,
                                    valueColor = MaterialTheme.colorScheme.onSurface,
                                    borderColor = standardBorderColor,
                                    fillColor = tileFillColor,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                            CenteredRoundedMetricTile(
                                label = "$standardLabel Goal",
                                value = goalDisplay,
                                valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                borderColor = standardBorderColor,
                                fillColor = tileFillColor,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CenteredRoundedMetricTile(
                                label = "On Label (As Sold)",
                                value = labelMissingStatusText,
                                valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                borderColor = standardBorderColor,
                                fillColor = tileFillColor,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                            CenteredRoundedMetricTile(
                                label = "$standardLabel Goal",
                                value = goalDisplay,
                                valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                borderColor = standardBorderColor,
                                fillColor = tileFillColor,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }

                // Case 3: Standard goal exists, but nutrient is NOT provided on label -> clean 2-tile row, Of Minimum Goal removed!
                !hasProductValue -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CenteredRoundedMetricTile(
                            label = "On Label (As Sold)",
                            value = labelMissingStatusText,
                            valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            borderColor = standardBorderColor,
                            fillColor = tileFillColor,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                        CenteredRoundedMetricTile(
                            label = "$standardLabel Goal",
                            value = goalDisplay,
                            valueColor = accentGreen,
                            borderColor = goalBorderColor,
                            fillColor = tileFillColor,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }

                // Case 4: Label value exists & goal exists, but Moisture (%) / Life Stage / Unit prevented evaluation -> 2 + 1 layout, Of Minimum Goal removed!
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CenteredRoundedMetricTile(
                                label = "On Label (As Sold)",
                                value = onLabelDisplay,
                                valueColor = MaterialTheme.colorScheme.onSurface,
                                borderColor = standardBorderColor,
                                fillColor = tileFillColor,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                            CenteredRoundedMetricTile(
                                label = "$standardLabel Goal",
                                value = goalDisplay,
                                valueColor = accentGreen,
                                borderColor = goalBorderColor,
                                fillColor = tileFillColor,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                        CenteredRoundedMetricTile(
                            label = "Dry Matter Basis",
                            value = dryMatterDisplay,
                            valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            borderColor = standardBorderColor,
                            fillColor = tileFillColor,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (!per1000KcalDisplay.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                CenteredRoundedMetricTile(
                    label = "Per 1,000 kcal ME",
                    value = per1000KcalDisplay,
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    borderColor = standardBorderColor,
                    fillColor = tileFillColor,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                thickness = 1.dp,
                color = hairlineColor
            )

            // Educational & Reason Descriptions (Justified Alignment)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (missingGoalOrInfoReason != null) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = missingGoalOrInfoReason.first,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!hasStandardGoal) accentGreen else statusAccentColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = missingGoalOrInfoReason.second,
                            fontSize = 11.5.sp,
                            lineHeight = 16.5.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Justify,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "What it does for your pet",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentGreen
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = whatItDoes,
                        fontSize = 11.5.sp,
                        lineHeight = 16.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Justify,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Avoid repeating whyStandardSetsGoal if missingGoalOrInfoReason already explained why there is no minimum goal
                if (hasStandardGoal) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Why $standardLabel sets this goal",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentGreen
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = whyStandardSetsGoal,
                            fontSize = 11.5.sp,
                            lineHeight = 16.5.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Justify,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (!additionalNote.isNullOrBlank()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Note",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentGreen
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = additionalNote,
                            fontSize = 11.5.sp,
                            lineHeight = 16.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Justify,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            thickness = 1.dp,
            color = accentGreen.copy(alpha = 0.22f)
        )
    }
}

@Composable
fun StandardGuideExpandableBanner(
    standard: AssessmentStandard,
    baseSurfaceColor: Color = Color(0xFFFFFEFE)
) {
    var expanded by remember { mutableStateOf(false) }
    val accentGreen = Color(0xFF479C73)

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "guideChevronRotation"
    )
    val borderColor by animateColorAsState(
        targetValue = if (expanded) accentGreen.copy(alpha = 0.40f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
        animationSpec = tween(250),
        label = "guideBorderColor"
    )
    val containerColor by animateColorAsState(
        targetValue = if (expanded) baseSurfaceColor.copy(alpha = 0.94f)
        else baseSurfaceColor.copy(alpha = 0.88f),
        animationSpec = tween(250),
        label = "guideContainerColor"
    )

    val isFediaf = standard == AssessmentStandard.FEDIAF
    val titleText = if (isFediaf) "What is FEDIAF?" else "What is AAFCO?"
    val whatIsItText = if (isFediaf) {
        "FEDIAF (European Pet Food Industry Federation) establishes nutritional guidelines for dog and cat foods in Europe, developed in collaboration with independent veterinary nutritionists."
    } else {
        "AAFCO (Association of American Feed Control Officials) establishes nutritional standards for pet foods in the United States, Canada, and international markets."
    }
    val purposeText = if (isFediaf) {
        "Its purpose is to define the required minimum and maximum levels of protein, fat, and essential minerals needed to support daily health, growth, and maintenance."
    } else {
        "Its purpose is to define the minimum and maximum nutrient levels a pet food must meet to be labeled as complete and balanced for a specific life stage."
    }

    Surface(
        modifier = Modifier
            .then(if (expanded) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
            .subtleCardShadow(borderRadius = 14.dp)
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = titleText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse explanation" else "Expand explanation",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(15.dp)
                        .rotate(chevronRotation)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(250, easing = FastOutSlowInEasing)) + fadeIn(tween(200)),
                exit = shrinkVertically(animationSpec = tween(200, easing = FastOutLinearInEasing)) + fadeOut(tween(150))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                    Column {
                        Text(
                            text = if (isFediaf) "What is FEDIAF?" else "What is AAFCO?",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = whatIsItText,
                            fontSize = 11.sp,
                            lineHeight = 14.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column {
                        Text(
                            text = "What is its purpose?",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = purposeText,
                            fontSize = 11.sp,
                            lineHeight = 14.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DryMatterHelpDialog(
    standard: AssessmentStandard = AssessmentStandard.FEDIAF,
    onDismiss: () -> Unit
) {
    val isFediaf = standard == AssessmentStandard.FEDIAF
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
                    .fillMaxWidth(0.88f)
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "About Dry Matter Basis",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Why do we remove water (Dry Matter)?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Wet food contains significantly more moisture than dry food. Converting nutrient values to a Dry Matter (moisture-removed) basis allows products with different moisture contents to be compared accurately against ${if (isFediaf) "FEDIAF" else "AAFCO"} standards.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Example Calculation:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "30% protein on label + 10% moisture = 90% Dry Matter\n30 ÷ 90 × 100 = 33.3% protein on a Dry Matter basis.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Got it", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun NutritionStandardSelector(
    selectedStandard: AssessmentStandard,
    onStandardSelected: (AssessmentStandard) -> Unit,
    boxColor: Color,
    boxBorder: BorderStroke
) {
    val isDark = isSystemInDarkTheme()
    val accentGreen = Color(0xFF479C73)
    val unselectedDarkGray = if (isDark) Color(0xFF7B8380) else Color(0xFF4A524F)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(
            AssessmentStandard.FEDIAF to "FEDIAF Assessment",
            AssessmentStandard.AAFCO to "AAFCO Assessment"
        ).forEach { (standard, label) ->
            val isSelected = selectedStandard == standard
            val textColor by animateColorAsState(
                targetValue = if (isSelected) accentGreen else unselectedDarkGray,
                animationSpec = tween(250, easing = EaseOutCubic),
                label = "standardTabTextColor"
            )
            val indicatorWidth by animateDpAsState(
                targetValue = if (isSelected) 64.dp else 0.dp,
                animationSpec = tween(250, easing = EaseOutCubic),
                label = "standardTabIndicatorWidth"
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onStandardSelected(standard) }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = Poppins,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        color = textColor
                    ),
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .width(indicatorWidth)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(accentGreen)
                )
            }
        }
    }
}

@Composable
fun AafcoNutritionOverviewCard(
    assessment: AafcoAssessmentResult,
    selectedNutrient: String = "",
    onNutrientClick: (String) -> Unit = {},
    boxColor: Color,
    shineProgressProvider: () -> Float = { 0f },
    isEditModeEnabled: Boolean = false,
    lowConfidenceNutrientKeys: Set<String> = emptySet(),
    onEditNutrients: (String?) -> Unit = {}
) {
    var showDmHelp by remember { mutableStateOf(false) }
    val accentGreen = Color(0xFF479C73)
    val accentGold = Color(0xFFD29524)
    val solidBaseColor = boxColor.copy(alpha = 1f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .subtleCardShadow(borderRadius = 28.dp),
        shape = RoundedCornerShape(28.dp),
        color = solidBaseColor
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    clip = true
                    shape = RoundedCornerShape(28.dp)
                }
                .shiningRippleOverlay(shineProgressProvider = shineProgressProvider)
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    // Angle: Bottom-Left to Top-Right (Green dominant, warm Gold secondary accent)
                    val bgGradient = Brush.linearGradient(
                        colorStops = arrayOf(
                            0.0f to accentGreen.copy(alpha = 0.36f),
                            0.62f to accentGreen.copy(alpha = 0.18f),
                            1.0f to accentGold.copy(alpha = 0.22f)
                        ),
                        start = Offset(0f, h),
                        end = Offset(w, 0f)
                    )

                    val topLeftCircleCenter = Offset(w * 0.16f, h * 0.22f)
                    val topLeftCircleRadius = w * 0.45f
                    val topLeftCircleBrush = Brush.radialGradient(
                        colors = listOf(
                            accentGreen.copy(alpha = 0.34f),
                            accentGreen.copy(alpha = 0.12f),
                            accentGreen.copy(alpha = 0f)
                        ),
                        center = topLeftCircleCenter,
                        radius = topLeftCircleRadius.coerceAtLeast(1f)
                    )

                    val bottomRightCircleCenter = Offset(w * 0.82f, h * 0.82f)
                    val bottomRightCircleRadius = w * 0.44f
                    val bottomRightCircleBrush = Brush.radialGradient(
                        colors = listOf(
                            accentGreen.copy(alpha = 0.32f),
                            accentGreen.copy(alpha = 0.11f),
                            accentGreen.copy(alpha = 0f)
                        ),
                        center = bottomRightCircleCenter,
                        radius = bottomRightCircleRadius.coerceAtLeast(1f)
                    )

                    val midLeftCircleCenter = Offset(w * 0.28f, h * 0.68f)
                    val midLeftCircleRadius = w * 0.32f
                    val midLeftCircleBrush = Brush.radialGradient(
                        colors = listOf(
                            accentGreen.copy(alpha = 0.22f),
                            accentGreen.copy(alpha = 0f)
                        ),
                        center = midLeftCircleCenter,
                        radius = midLeftCircleRadius.coerceAtLeast(1f)
                    )

                    val topRightCircleCenter = Offset(w * 0.88f, h * 0.16f)
                    val topRightCircleRadius = w * 0.34f
                    val topRightCircleBrush = Brush.radialGradient(
                        colors = listOf(
                            accentGold.copy(alpha = 0.22f),
                            accentGold.copy(alpha = 0.08f),
                            accentGold.copy(alpha = 0f)
                        ),
                        center = topRightCircleCenter,
                        radius = topRightCircleRadius.coerceAtLeast(1f)
                    )

                    onDrawBehind {
                        drawRect(color = solidBaseColor)
                        drawRect(brush = bgGradient)
                        drawCircle(
                            brush = topLeftCircleBrush,
                            radius = topLeftCircleRadius,
                            center = topLeftCircleCenter
                        )
                        drawCircle(
                            brush = bottomRightCircleBrush,
                            radius = bottomRightCircleRadius,
                            center = bottomRightCircleCenter
                        )
                        drawCircle(
                            brush = midLeftCircleBrush,
                            radius = midLeftCircleRadius,
                            center = midLeftCircleCenter
                        )
                        drawCircle(
                            brush = topRightCircleBrush,
                            radius = topRightCircleRadius,
                            center = topRightCircleCenter
                        )
                    }
                }
        ) {
            Column(modifier = Modifier.padding(vertical = 20.dp)) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AAFCO Assessment",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            AnimatedContent(
                                targetState = "${assessment.lifeStageCategory.displayName} · ${assessment.profileType.displayName}",
                                transitionSpec = {
                                    fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                                },
                                label = "aafcoContextSubtitle"
                            ) { subtitle ->
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isEditModeEnabled) {
                                IconButton(
                                    onClick = { onEditNutrients(null) },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(accentGreen.copy(alpha = 0.14f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Edit Nutrients",
                                        tint = accentGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            IconButton(onClick = { showDmHelp = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = "AAFCO & Dry Matter Info",
                                    tint = accentGreen,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // Senior Life-Stage Mapping Notice (Phase 3 requirement)
                    if (assessment.isSeniorMappedToAdultMaintenance && assessment.seniorMappingExplanation != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = solidBaseColor.copy(alpha = 0.88f),
                            border = BorderStroke(1.dp, accentGreen.copy(alpha = 0.28f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = accentGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = assessment.seniorMappingExplanation,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (assessment.isTreatOrComplementary && assessment.treatExemptionMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = solidBaseColor.copy(alpha = 0.88f),
                            border = BorderStroke(1.dp, accentGold.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = accentGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = assessment.treatExemptionMessage,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Compact Expandable Guide: What is AAFCO?
                    StandardGuideExpandableBanner(
                        standard = AssessmentStandard.AAFCO,
                        baseSurfaceColor = solidBaseColor
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Overall Status Banner (Icon inline beside headline; dash for partial)
                    val (bannerBg, iconColor) = when (assessment.overallStatus) {
                        AafcoResultStatus.MEETS_REFERENCE -> BgPastelGreen to TextDarkGreen
                        AafcoResultStatus.BELOW_REFERENCE -> Color(0xFFFFF3E0) to Color(0xFFE65100)
                        AafcoResultStatus.ABOVE_MAXIMUM -> Color(0xFFFFEBEE) to Color(0xFFC62828)
                        AafcoResultStatus.INSUFFICIENT_INFORMATION -> solidBaseColor.copy(alpha = 0.88f) to MaterialTheme.colorScheme.onSurfaceVariant
                        AafcoResultStatus.NOT_APPLICABLE -> solidBaseColor.copy(alpha = 0.88f) to MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = bannerBg
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                when (assessment.overallStatus) {
                                    AafcoResultStatus.MEETS_REFERENCE -> {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = iconColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    AafcoResultStatus.BELOW_REFERENCE, AafcoResultStatus.ABOVE_MAXIMUM -> {
                                        Icon(
                                            imageVector = Icons.Rounded.Warning,
                                            contentDescription = null,
                                            tint = iconColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    else -> {
                                        PartialDashBadge(tint = iconColor, size = 18.dp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = assessment.overallHeadline,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = iconColor
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = assessment.overallSummary,
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp,
                                textAlign = TextAlign.Justify,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Integrated Expandable Key Evaluated Nutrients List
                    Text(
                        text = "Key Evaluated Nutrients (Dry Matter Basis):",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    assessment.nutrientResults.forEach { item ->
                        val isExpanded = selectedNutrient.equals(item.nutrientKey, ignoreCase = true) ||
                            selectedNutrient.equals(item.nutrientName, ignoreCase = true)

                        AafcoIntegratedNutrientItem(
                            item = item,
                            isExpanded = isExpanded,
                            solidBaseColor = solidBaseColor,
                            accentGreen = accentGreen,
                            accentGold = accentGold,
                            isLowConfidence = lowConfidenceNutrientKeys.contains(item.nutrientKey),
                            isEditModeEnabled = isEditModeEnabled,
                            onEditClick = { onEditNutrients(item.nutrientKey) },
                            onClick = { onNutrientClick(item.nutrientName) }
                        )
                    }

                    if (assessment.energyResult.source != EnergySource.UNAVAILABLE) {
                        val isEnergyExpanded = selectedNutrient.equals("metabolizableEnergy", ignoreCase = true) ||
                            selectedNutrient.equals("Metabolizable Energy", ignoreCase = true)

                        MetabolizableEnergyIntegratedItem(
                            energyResult = assessment.energyResult,
                            standardLabel = "AAFCO",
                            isExpanded = isEnergyExpanded,
                            accentGreen = accentGreen,
                            onClick = { onNutrientClick("Metabolizable Energy") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Click a nutrient to expand for more details.",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                )
            }
        }
    }

    if (showDmHelp) {
        DryMatterHelpDialog(
            standard = AssessmentStandard.AAFCO,
            onDismiss = { showDmHelp = false }
        )
    }
}

@Composable
private fun AafcoIntegratedNutrientItem(
    item: AafcoNutrientResult,
    isExpanded: Boolean,
    solidBaseColor: Color,
    accentGreen: Color,
    accentGold: Color,
    isLowConfidence: Boolean = false,
    isEditModeEnabled: Boolean = false,
    onEditClick: () -> Unit = {},
    onClick: () -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "aafcoInlineChevron"
    )

    val (badgeText, badgeBg, badgeTextColor) = when (item.status) {
        AafcoResultStatus.MEETS_REFERENCE -> Triple("✓ Meets Standard", BgPastelGreen, TextDarkGreen)
        AafcoResultStatus.BELOW_REFERENCE -> Triple("! Below Minimum", Color(0xFFFFF3E0), Color(0xFFE65100))
        AafcoResultStatus.ABOVE_MAXIMUM -> Triple("! Above Safe Limit", Color(0xFFFFEBEE), Color(0xFFC62828))
        AafcoResultStatus.INSUFFICIENT_INFORMATION -> Triple("— Not Listed", MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), GrayMedium)
        AafcoResultStatus.NOT_APPLICABLE -> Triple("— Info", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    val statusAccentColor = when (item.status) {
        AafcoResultStatus.MEETS_REFERENCE -> accentGreen
        AafcoResultStatus.BELOW_REFERENCE -> Color(0xFFE65100)
        AafcoResultStatus.ABOVE_MAXIMUM -> Color(0xFFC62828)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val hasStandardGoal = (item.requiredMinimum != null || item.maximumLimit != null) &&
        item.status != AafcoResultStatus.NOT_APPLICABLE
    val hasProductValue = item.asFedValue != null || item.dryMatterValue != null
    val isEvaluatedAgainstGoal = item.status == AafcoResultStatus.MEETS_REFERENCE ||
        item.status == AafcoResultStatus.BELOW_REFERENCE ||
        item.status == AafcoResultStatus.ABOVE_MAXIMUM
    val evaluatedPctOfMinimum = if (isEvaluatedAgainstGoal) item.percentageOfMinimum else null

    val conciseValueText = remember(item) {
        when {
            item.unit == ":1" -> if (item.dryMatterValue != null) String.format(Locale.US, "%.2f : 1", item.dryMatterValue) else "Not listed"
            item.dryMatterValue != null -> "${String.format(Locale.US, "%.1f", item.dryMatterValue)}% DM"
            item.productValueDisplay.contains("Incompatible", ignoreCase = true) -> "Incompatible unit"
            item.asFedValue != null -> "${String.format(Locale.US, "%.1f", item.asFedValue)}${item.asFedUnit}"
            else -> "Not listed"
        }
    }

    val dryMatterDisplay = when {
        item.dryMatterValue != null -> {
            if (item.unit == ":1") String.format(Locale.US, "%.2f : 1", item.dryMatterValue)
            else "${String.format(Locale.US, "%.1f", item.dryMatterValue)}% DM"
        }
        item.productValueDisplay.contains("Incompatible", ignoreCase = true) -> "Incompatible Unit"
        item.asFedValue != null -> "Moisture (%) Not Provided"
        else -> "Not Provided"
    }

    val onLabelDisplay = if (item.asFedValue != null) {
        if (item.unit == ":1") String.format(Locale.US, "%.2f : 1", item.asFedValue)
        else "${String.format(Locale.US, "%.1f", item.asFedValue)}${item.asFedUnit}"
    } else {
        "Not Provided"
    }

    val labelMissingStatusText = if (item.isListedInIngredientsOnly) {
        "Ingredients Only"
    } else {
        "Not Provided"
    }

    val missingGoalOrInfoReason = remember(item, hasStandardGoal, hasProductValue, evaluatedPctOfMinimum) {
        when {
            !hasStandardGoal -> "Why there is no minimum goal" to item.referenceReason
            evaluatedPctOfMinimum == null && !hasProductValue -> {
                val explanation = if (item.assessmentExplanation.isNotBlank()) {
                    buildString {
                        append(item.assessmentExplanation)
                        if (!item.assessmentExplanation.contains("cannot", ignoreCase = true)) {
                            append(" Without a declared percentage on the label, Dry Matter basis and minimum goal coverage cannot be calculated.")
                        }
                    }
                } else {
                    "This product's nutrition label does not list ${item.nutrientName}, so Dry Matter basis and minimum goal coverage cannot be calculated."
                }
                "Why this could not be evaluated" to explanation
            }
            evaluatedPctOfMinimum == null -> {
                val explanation = item.assessmentExplanation.ifBlank {
                    item.moreInfoNeededText ?: "Additional label information is required to calculate minimum goal coverage."
                }
                "Why minimum goal coverage is unavailable" to explanation
            }
            else -> null
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                when (item.status) {
                    AafcoResultStatus.MEETS_REFERENCE -> {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = accentGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    AafcoResultStatus.BELOW_REFERENCE -> {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = accentGold,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    AafcoResultStatus.ABOVE_MAXIMUM -> {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = Color(0xFFC62828),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    else -> {
                        PartialDashBadge(tint = GrayMedium, size = 15.dp)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.nutrientName,
                    fontSize = 13.sp,
                    fontWeight = if (isExpanded) FontWeight.Bold else FontWeight.Medium,
                    color = if (isExpanded) accentGreen else MaterialTheme.colorScheme.onSurface
                )
                if (isLowConfidence) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFFF8E1),
                        border = BorderStroke(1.dp, accentGold.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "Verify",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF9A6700),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conciseValueText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isExpanded) accentGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isEditModeEnabled && item.nutrientKey != "calciumPhosphorusRatio") {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit ${item.nutrientName}",
                            tint = accentGreen,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse nutrient details" else "Expand nutrient details",
                    tint = if (isExpanded) accentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(chevronRotation)
                )
            }
        }

        val per1000KcalDisplay = remember(item.energyBasisValue, item.energyBasisUnit) {
            if (item.energyBasisValue != null && !item.energyBasisUnit.isNullOrBlank()) {
                String.format(Locale.US, "%.1f %s", item.energyBasisValue, item.energyBasisUnit)
            } else {
                null
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
            exit = shrinkVertically(animationSpec = tween(200, easing = FastOutLinearInEasing)) + fadeOut(tween(140))
        ) {
            ExpandedNutrientBreakdownPanel(
                standardLabel = "AAFCO",
                badgeText = badgeText,
                badgeBg = badgeBg,
                badgeTextColor = badgeTextColor,
                statusAccentColor = statusAccentColor,
                accentGreen = accentGreen,
                hasStandardGoal = hasStandardGoal,
                hasProductValue = hasProductValue,
                dryMatterDisplay = dryMatterDisplay,
                goalDisplay = item.referenceDisplay,
                onLabelDisplay = onLabelDisplay,
                labelMissingStatusText = labelMissingStatusText,
                percentageOfMinimum = evaluatedPctOfMinimum,
                per1000KcalDisplay = per1000KcalDisplay,
                whatItDoes = item.functionDescription,
                whyStandardSetsGoal = item.referenceReason,
                missingGoalOrInfoReason = missingGoalOrInfoReason,
                additionalNote = if (evaluatedPctOfMinimum != null) item.verificationNote else null
            )
        }
    }
}

@Composable
private fun AssessmentContextControls(
    eligiblePets: List<Pet>,
    allPetsCount: Int,
    filterSpec: ProductPetFilterSpec,
    activePet: Pet?,
    onSelectPet: (Pet) -> Unit,
    showMerMode: Boolean,
    selectedMerMode: FediafMerMode,
    onSelectMerMode: (FediafMerMode) -> Unit,
    boxColor: Color,
    boxBorder: BorderStroke
) {
    val accentGreen = Color(0xFF479C73)
    val listState = rememberLazyListState()
    val activePetKey = activePet?.let { it.petId.ifBlank { it.id } }

    LaunchedEffect(activePetKey, eligiblePets) {
        if (activePet != null && eligiblePets.isNotEmpty()) {
            val idx = eligiblePets.indexOfFirst { pet ->
                val key = pet.petId.ifBlank { pet.id }
                (key.isNotBlank() && key == activePetKey) || (key.isBlank() && pet == activePet)
            }
            if (idx >= 0) {
                listState.animateScrollToItem(idx)
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .subtleCardShadow(borderRadius = 22.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        shape = RoundedCornerShape(22.dp),
        color = boxColor,
        border = boxBorder
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            if (allPetsCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_paw),
                            contentDescription = null,
                            tint = accentGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Evaluating for",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = accentGreen.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, accentGreen.copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = filterSpec.filterBadgeLabel,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accentGreen,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                AnimatedContent(
                    targetState = eligiblePets.isNotEmpty(),
                    transitionSpec = {
                        (fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                            expandVertically(tween(260, easing = FastOutSlowInEasing)))
                            .togetherWith(
                                fadeOut(tween(180, easing = FastOutLinearInEasing)) +
                                    shrinkVertically(tween(220, easing = FastOutLinearInEasing))
                            )
                    },
                    label = "petSelectorContentTransition"
                ) { hasEligiblePets ->
                    if (hasEligiblePets) {
                        LazyRow(
                            state = listState,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            itemsIndexed(
                                items = eligiblePets,
                                key = { index, pet ->
                                    pet.petId.ifBlank { pet.id }.ifBlank { "pet_$index" }
                                }
                            ) { _, pet ->
                                val petKey = pet.petId.ifBlank { pet.id }
                                val isSelected = (petKey.isNotBlank() && petKey == activePetKey) ||
                                    (petKey.isBlank() && pet == activePet)
                                AssessmentPetSelectorChip(
                                    pet = pet,
                                    isSelected = isSelected,
                                    accentGreen = accentGreen,
                                    onClick = { onSelectPet(pet) },
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = tween(240, easing = FastOutSlowInEasing),
                                        fadeOutSpec = tween(180, easing = FastOutLinearInEasing),
                                        placementSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                )
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = accentGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = filterSpec.emptyMatchHint,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showMerMode,
                enter = expandVertically(
                    animationSpec = tween(260, easing = FastOutSlowInEasing)
                ) + fadeIn(
                    animationSpec = tween(220, delayMillis = 40, easing = FastOutSlowInEasing)
                ),
                exit = shrinkVertically(
                    animationSpec = tween(220, easing = FastOutLinearInEasing)
                ) + fadeOut(
                    animationSpec = tween(150, easing = FastOutLinearInEasing)
                )
            ) {
                var showActivityLevelHelp by remember { mutableStateOf(false) }

                if (showActivityLevelHelp) {
                    ActivityLevelHelpDialog(onDismiss = { showActivityLevelHelp = false })
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    if (allPetsCount > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Activity Level",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Surface(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .bouncyClickable { showActivityLevelHelp = true },
                            shape = CircleShape,
                            color = accentGreen.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, accentGreen.copy(alpha = 0.35f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = "About Activity Level",
                                    tint = accentGreen,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FediafMerMode.entries.forEach { mode ->
                            val isSelected = mode == selectedMerMode
                            val bgColor by animateColorAsState(
                                targetValue = if (isSelected) accentGreen else MaterialTheme.colorScheme.surface,
                                animationSpec = tween(240, easing = EaseOutCubic),
                                label = "merBgColor"
                            )
                            val borderColor by animateColorAsState(
                                targetValue = if (isSelected) accentGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                                animationSpec = tween(240, easing = EaseOutCubic),
                                label = "merBorderColor"
                            )
                            val contentColor by animateColorAsState(
                                targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                animationSpec = tween(240, easing = EaseOutCubic),
                                label = "merContentColor"
                            )

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .bouncyClickable { onSelectMerMode(mode) },
                                shape = RoundedCornerShape(100.dp),
                                color = bgColor,
                                border = BorderStroke(1.dp, borderColor)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode.displayName,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = contentColor,
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
}

@Composable
fun ActivityLevelHelpDialog(
    onDismiss: () -> Unit
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
                    .fillMaxWidth(0.88f)
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "What is Activity Level?",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Why does your pet's daily routine matter?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Pets who are less active burn fewer calories each day, so they eat smaller meals to stay at a healthy weight. Even though they eat less food, they still need the same daily amount of essential nutrients (like protein, vitamins, and minerals)—meaning their food needs to pack slightly more nutrition into every bite.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.5.sp,
                            lineHeight = 17.5.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "• Standard Activity",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Best for playful, active dogs and cats who get regular daily walks, outdoor play, or have normal energy needs.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 16.sp
                                )
                            }

                            Column {
                                Text(
                                    text = "• Lower Activity / Indoor",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Best for indoor-only cats, calmer or less active dogs, senior pets, or spayed/neutered pets who eat smaller portions.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Got it", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AssessmentPetSelectorChip(
    pet: Pet,
    isSelected: Boolean,
    accentGreen: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val petInfo = remember(pet) { PetAssessmentFilter.classifyPet(pet) }
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) accentGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
        animationSpec = tween(240, easing = EaseOutCubic),
        label = "petChipBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) accentGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.20f),
        animationSpec = tween(240, easing = EaseOutCubic),
        label = "petChipBorderColor"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 1.5.dp else 1.dp,
        animationSpec = tween(220, easing = EaseOutCubic),
        label = "petChipBorderWidth"
    )
    val nameColor by animateColorAsState(
        targetValue = if (isSelected) accentGreen else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(240, easing = EaseOutCubic),
        label = "petChipNameColor"
    )
    val subtitleColor by animateColorAsState(
        targetValue = if (isSelected) accentGreen.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(240, easing = EaseOutCubic),
        label = "petChipSubtitleColor"
    )
    val avatarBg by animateColorAsState(
        targetValue = if (isSelected) accentGreen.copy(alpha = 0.22f) else accentGreen.copy(alpha = 0.12f),
        animationSpec = tween(240, easing = EaseOutCubic),
        label = "petChipAvatarBg"
    )

    val subtitleText = remember(petInfo) {
        if (petInfo.ageDisplay.isNotEmpty()) "${petInfo.displayLabel} · ${petInfo.ageDisplay}"
        else petInfo.displayLabel
    }

    Surface(
        modifier = modifier.bouncyClickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.size(30.dp),
                shape = CircleShape,
                color = avatarBg
            ) {
                if (pet.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = pet.imageUrl,
                        contentDescription = pet.petName.ifBlank { pet.type },
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_paw),
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = accentGreen
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = pet.petName.ifBlank { pet.type },
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = nameColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitleText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = subtitleColor,
                    maxLines = 1
                )
            }

            AnimatedVisibility(
                visible = isSelected,
                enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeIn(tween(180)),
                exit = scaleOut(tween(140)) + fadeOut(tween(120))
            ) {
                Surface(
                    modifier = Modifier.size(18.dp),
                    shape = CircleShape,
                    color = accentGreen
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatExactEnergyValue(value: Double): String {
    if (!value.isFinite()) return "-"
    val formatter = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US))
    return formatter.format(value)
}

@Composable
private fun MetabolizableEnergyIntegratedItem(
    energyResult: EnergyExtractionResult,
    standardLabel: String,
    isExpanded: Boolean,
    accentGreen: Color,
    onClick: () -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "energyInlineChevron"
    )

    val isLabelDeclared = energyResult.source == EnergySource.LABEL_DECLARED
    val isEstimated = energyResult.source == EnergySource.ESTIMATED_MODIFIED_ATWATER
    val isServingOnly = energyResult.source == EnergySource.NON_CONVERTIBLE_UNIT

    val conciseValueText = remember(energyResult) {
        when (energyResult.source) {
            EnergySource.LABEL_DECLARED -> {
                val unit = energyResult.rawUnit?.takeIf { it.isNotBlank() } ?: "kcal/kg"
                val exactVal = energyResult.rawValue ?: energyResult.asFedKcalPerKg
                if (exactVal != null && exactVal.isFinite()) {
                    "${formatExactEnergyValue(exactVal)} $unit"
                } else {
                    "Not listed"
                }
            }
            EnergySource.ESTIMATED_MODIFIED_ATWATER -> {
                val estVal = energyResult.asFedKcalPerKg ?: energyResult.rawValue
                if (estVal != null && estVal.isFinite()) {
                    "${String.format(Locale.US, "%,d", estVal.roundToInt())} kcal/kg (est.)"
                } else {
                    "Estimated"
                }
            }
            EnergySource.NON_CONVERTIBLE_UNIT -> {
                val rawVal = energyResult.rawValue
                val unit = energyResult.rawUnit?.takeIf { it.isNotBlank() } ?: "serving"
                if (rawVal != null && rawVal.isFinite()) {
                    "${formatExactEnergyValue(rawVal)} $unit"
                } else {
                    unit
                }
            }
            EnergySource.UNAVAILABLE -> "Not listed"
        }
    }

    val (badgeText, badgeBg, badgeTextColor) = when {
        isLabelDeclared && energyResult.isScaledForThreshold ->
            Triple("✓ From Label • Adjusted", BgPastelGreen, TextDarkGreen)
        isLabelDeclared ->
            Triple("✓ From Label", BgPastelGreen, TextDarkGreen)
        isEstimated ->
            Triple("— Info (Estimated)", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        else ->
            Triple("— Info", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    val statusAccentColor = if (isLabelDeclared) accentGreen else MaterialTheme.colorScheme.onSurfaceVariant

    val dryMatterDisplay = remember(energyResult) {
        val dm = energyResult.dryMatterKcalPerKg?.takeIf { it.isFinite() }
        when {
            isServingOnly -> "Serving Unit Only"
            dm != null -> "${String.format(Locale.US, "%,d", dm.roundToInt())} kcal/kg DM"
            else -> "Moisture (%) Not Provided"
        }
    }

    val onLabelDisplay = remember(energyResult) {
        when (energyResult.source) {
            EnergySource.LABEL_DECLARED -> {
                val unit = energyResult.rawUnit?.takeIf { it.isNotBlank() } ?: "kcal/kg"
                val exactVal = energyResult.rawValue ?: energyResult.asFedKcalPerKg
                val asFed = energyResult.asFedKcalPerKg?.takeIf { it.isFinite() }
                if (exactVal != null && exactVal.isFinite()) {
                    val exactStr = "${formatExactEnergyValue(exactVal)} $unit"
                    if (!unit.equals("kcal/kg", ignoreCase = true) && asFed != null) {
                        "$exactStr (${String.format(Locale.US, "%,d", asFed.roundToInt())} kcal/kg)"
                    } else {
                        exactStr
                    }
                } else {
                    "Not Provided"
                }
            }
            EnergySource.ESTIMATED_MODIFIED_ATWATER -> {
                val estVal = energyResult.asFedKcalPerKg?.takeIf { it.isFinite() }
                if (estVal != null) {
                    "Not Listed (Est. ${String.format(Locale.US, "%,d", estVal.roundToInt())} kcal/kg)"
                } else {
                    "Not Listed"
                }
            }
            EnergySource.NON_CONVERTIBLE_UNIT -> {
                val rawVal = energyResult.rawValue?.takeIf { it.isFinite() }
                val unit = energyResult.rawUnit?.takeIf { it.isNotBlank() } ?: "serving"
                if (rawVal != null) "${formatExactEnergyValue(rawVal)} $unit" else unit
            }
            EnergySource.UNAVAILABLE -> "Not Provided"
        }
    }

    val goalDisplay = remember(energyResult, standardLabel) {
        val dm = energyResult.dryMatterKcalPerKg?.takeIf { it.isFinite() }
        when {
            isLabelDeclared && energyResult.isScaledForThreshold && dm != null ->
                "Targets Adjusted (${String.format(Locale.US, "%,d", dm.roundToInt())} vs 4,000 kcal/kg DM)"
            isLabelDeclared ->
                "Informational (4,000 kcal/kg DM Base)"
            else ->
                "Informational Only"
        }
    }

    val infoReason = remember(energyResult, standardLabel) {
        val dm = energyResult.dryMatterKcalPerKg?.takeIf { it.isFinite() }
        when {
            isLabelDeclared && energyResult.isScaledForThreshold && dm != null ->
                "Why $standardLabel targets are adjusted" to
                    "Calories come directly from the product label (${String.format(Locale.US, "%,d", dm.roundToInt())} kcal/kg DM). $standardLabel nutrient targets are adjusted relative to the standard 4,000 kcal/kg DM reference base."
            isLabelDeclared ->
                "How calories are used" to
                    "Calories come directly from the product label. Standard $standardLabel nutrient targets apply."
            isEstimated ->
                "Why this is informational" to
                    "Calories are not listed on the label, so this value is estimated from protein, fat, and carbohydrates for information only. Standard $standardLabel nutrient targets are unchanged."
            else ->
                "Why this is informational" to
                    (energyResult.nonConvertibleReason ?: "Calories are listed per serving unit (${energyResult.rawUnit ?: "serving"}), which cannot be converted to kcal/kg without serving weight in grams. Standard $standardLabel nutrient targets remain unchanged.")
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (isLabelDeclared) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = accentGreen,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    PartialDashBadge(tint = GrayMedium, size = 15.dp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Metabolizable Energy",
                    fontSize = 13.sp,
                    fontWeight = if (isExpanded) FontWeight.Bold else FontWeight.Medium,
                    color = if (isExpanded) accentGreen else MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conciseValueText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isExpanded) accentGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse energy details" else "Expand energy details",
                    tint = if (isExpanded) accentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(chevronRotation)
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
            exit = shrinkVertically(animationSpec = tween(200, easing = FastOutLinearInEasing)) + fadeOut(tween(140))
        ) {
            ExpandedNutrientBreakdownPanel(
                standardLabel = standardLabel,
                badgeText = badgeText,
                badgeBg = badgeBg,
                badgeTextColor = badgeTextColor,
                statusAccentColor = statusAccentColor,
                accentGreen = accentGreen,
                hasStandardGoal = false,
                hasProductValue = true,
                dryMatterDisplay = dryMatterDisplay,
                goalDisplay = goalDisplay,
                onLabelDisplay = onLabelDisplay,
                percentageOfMinimum = null,
                whatItDoes = "Measures the usable calorie density in the food that fuels your pet's daily activity, growth, and body functions.",
                whyStandardSetsGoal = infoReason.second,
                missingGoalOrInfoReason = infoReason
            )
        }
    }
}

@Composable
private fun EstimatedCarbsAndFeedingCard(
    nfeCarbs: NfeCarbResult?,
    dailyFeeding: DailyFeedingEstimate?,
    boxColor: Color,
    boxBorder: BorderStroke
) {
    val accentGreen = Color(0xFF479C73)
    val accentGold = Color(0xFFD29524)
    val solidBaseColor = boxColor.copy(alpha = 1f)
    var showFormulaHelp by remember { mutableStateOf(false) }

    if (showFormulaHelp) {
        CarbsAndFeedingFormulaDialog(
            nfeCarbs = nfeCarbs,
            dailyFeeding = dailyFeeding,
            onDismiss = { showFormulaHelp = false }
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .subtleCardShadow(borderRadius = 28.dp),
        shape = RoundedCornerShape(28.dp),
        color = solidBaseColor,
        border = boxBorder
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    clip = true
                    shape = RoundedCornerShape(28.dp)
                }
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val bgGradient = Brush.linearGradient(
                        colorStops = arrayOf(
                            0.0f to accentGreen.copy(alpha = 0.35f),
                            0.50f to accentGreen.copy(alpha = 0.15f),
                            1.0f to accentGold.copy(alpha = 0.28f)
                        ),
                        start = Offset(w, 0f),
                        end = Offset(0f, h)
                    )

                    val topLeftCircleCenter = Offset(w * 0.14f, h * 0.15f)
                    val topLeftCircleRadius = w * 0.42f
                    val topLeftCircleBrush = Brush.radialGradient(
                        colors = listOf(
                            accentGold.copy(alpha = 0.30f),
                            accentGold.copy(alpha = 0.11f),
                            accentGold.copy(alpha = 0f)
                        ),
                        center = topLeftCircleCenter,
                        radius = topLeftCircleRadius.coerceAtLeast(1f)
                    )

                    val bottomRightCircleCenter = Offset(w * 0.86f, h * 0.84f)
                    val bottomRightCircleRadius = w * 0.45f
                    val bottomRightCircleBrush = Brush.radialGradient(
                        colors = listOf(
                            accentGreen.copy(alpha = 0.32f),
                            accentGreen.copy(alpha = 0.12f),
                            accentGreen.copy(alpha = 0f)
                        ),
                        center = bottomRightCircleCenter,
                        radius = bottomRightCircleRadius.coerceAtLeast(1f)
                    )

                    val midLeftCircleCenter = Offset(w * 0.16f, h * 0.54f)
                    val midLeftCircleRadius = w * 0.28f
                    val midLeftCircleBrush = Brush.radialGradient(
                        colors = listOf(
                            accentGreen.copy(alpha = 0.22f),
                            accentGreen.copy(alpha = 0f)
                        ),
                        center = midLeftCircleCenter,
                        radius = midLeftCircleRadius.coerceAtLeast(1f)
                    )

                    val upperRightCircleCenter = Offset(w * 0.84f, h * 0.38f)
                    val upperRightCircleRadius = w * 0.30f
                    val upperRightCircleBrush = Brush.radialGradient(
                        colors = listOf(
                            accentGold.copy(alpha = 0.22f),
                            accentGold.copy(alpha = 0f)
                        ),
                        center = upperRightCircleCenter,
                        radius = upperRightCircleRadius.coerceAtLeast(1f)
                    )

                    onDrawBehind {
                        drawRect(color = solidBaseColor)
                        drawRect(brush = bgGradient)
                        drawCircle(
                            brush = topLeftCircleBrush,
                            radius = topLeftCircleRadius,
                            center = topLeftCircleCenter
                        )
                        drawCircle(
                            brush = bottomRightCircleBrush,
                            radius = bottomRightCircleRadius,
                            center = bottomRightCircleCenter
                        )
                        drawCircle(
                            brush = midLeftCircleBrush,
                            radius = midLeftCircleRadius,
                            center = midLeftCircleCenter
                        )
                        drawCircle(
                            brush = upperRightCircleBrush,
                            radius = upperRightCircleRadius,
                            center = upperRightCircleCenter
                        )
                    }
                }
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Carbohydrates & Portion Estimate",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = { showFormulaHelp = true },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = "Calculation Formulas",
                            tint = accentGreen,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                if (nfeCarbs != null) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = solidBaseColor.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, accentGold.copy(alpha = 0.32f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Estimated Carbs (NFE)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = when {
                                        nfeCarbs.isDeclaredOnLabel -> "Declared on product label"
                                        nfeCarbs.usedDefaultAsh && nfeCarbs.usedDefaultFiber -> "Estimated using typical ash & fiber"
                                        nfeCarbs.usedDefaultAsh -> "Estimated using typical ash"
                                        else -> "Estimated from guaranteed analysis"
                                    },
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = String.format(Locale.US, "%.1f%% As-Fed", nfeCarbs.asFedCarbsPercent),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = accentGold
                                )
                                nfeCarbs.dryMatterCarbsPercent?.let { dm ->
                                    Text(
                                        text = String.format(Locale.US, "%.1f%% DM", dm),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (dailyFeeding != null) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = solidBaseColor.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, accentGreen.copy(alpha = 0.32f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Daily Portion for ${dailyFeeding.petName} (${String.format(Locale.US, "%.1f", dailyFeeding.petWeightKg)} kg)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${dailyFeeding.dailyKcalNeeded.roundToInt()} kcal/day target" +
                                        if (dailyFeeding.isEnergyEstimated) " • est. food kcal" else "",
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "~${dailyFeeding.dailyGrams.roundToInt()} g/day",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = accentGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CarbsAndFeedingFormulaDialog(
    nfeCarbs: NfeCarbResult?,
    dailyFeeding: DailyFeedingEstimate?,
    onDismiss: () -> Unit
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
                    .fillMaxWidth(0.88f)
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "How Estimates Are Calculated",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (nfeCarbs != null) {
                        Text(
                            text = "Estimated Carbohydrates (NFE)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFFD29524)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pet food labels rarely list carbohydrates directly. Nitrogen-Free Extract (NFE) estimates digestible carbs from the remaining percentage after protein, fat, fiber, ash, and moisture.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                lineHeight = 16.5.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFD29524).copy(alpha = 0.12f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "NFE (%) = 100 - (Protein + Fat + Fiber + Ash + Moisture)",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Dry Matter Carbs (%) = NFE (%) ÷ (100 - Moisture) × 100",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    if (dailyFeeding != null) {
                        Text(
                            text = "Daily Feeding Portion (FEDIAF MER)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF479C73)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Estimated using your pet's body weight, FEDIAF Maintenance Energy Requirement (MER) equations, and the food's calorie density.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                lineHeight = 16.5.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF479C73).copy(alpha = 0.12f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "MER Formula: ${dailyFeeding.formulaLabel}",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Daily Portion (g) = (Daily kcal Needed ÷ ${dailyFeeding.productKcalPerKgAsFed.roundToInt()} kcal/kg) × 1,000",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Text(
                        text = "Note: Actual feeding amounts vary with activity level, treats, and body condition. Consult your veterinarian for individualized portions.",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Got it", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityLabelTrustCard(
    product: Product,
    viewModel: AppViewModel,
    boxColor: Color,
    boxBorder: BorderStroke,
    onNavigateToLogin: () -> Unit
) {
    val targetId = product.productId.ifBlank { product.barcode }
    val barcode = product.barcode
    val trustMap by viewModel.productTrustCache.collectAsState()
    val isUserLoggedIn by viewModel.isUserLoggedIn.collectAsState()
    val trustSummary = trustMap[targetId] ?: trustMap[barcode]

    LaunchedEffect(targetId, barcode) {
        if (targetId.isNotBlank()) {
            viewModel.loadProductTrust(targetId, barcode)
        }
    }

    var isSubmitting by remember { mutableStateOf(false) }
    var statusFeedback by remember { mutableStateOf<String?>(null) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }

    if (showLoginDialog) {
        LoginRequiredDialog(
            onDismiss = { showLoginDialog = false },
            onLoginClick = {
                showLoginDialog = false
                onNavigateToLogin()
            }
        )
    }

    if (showReportDialog) {
        ReportLabelMistakeDialog(
            productName = product.productName,
            onDismiss = { showReportDialog = false },
            onSubmit = { reason, details ->
                isSubmitting = true
                viewModel.reportProductMistake(targetId, reason, details, barcode) { res ->
                    isSubmitting = false
                    showReportDialog = false
                    statusFeedback = if (res.isSuccess) {
                        "Thank you! Mistake report submitted."
                    } else {
                        res.exceptionOrNull()?.message ?: "Could not submit report."
                    }
                }
            }
        )
    }

    val accentGreen = Color(0xFF479C73)
    val accentAmber = Color(0xFFD29524)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .subtleCardShadow(borderRadius = 24.dp),
        shape = RoundedCornerShape(24.dp),
        color = boxColor,
        border = boxBorder
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Community Label Verification",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    val verCount = trustSummary?.verificationCount ?: 0
                    val repCount = trustSummary?.reportCount ?: 0
                    Text(
                        text = when {
                            verCount > 0 && repCount > 0 -> "$verCount confirmed accurate • $repCount flagged for review"
                            verCount > 0 -> "Confirmed accurate by $verCount community member${if (verCount == 1) "" else "s"}"
                            repCount > 0 -> "$repCount mistake report${if (repCount == 1) "" else "s"} submitted"
                            else -> "Help keep label nutrition & ingredients accurate"
                        },
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val alreadyVerified = trustSummary?.userHasVerified == true
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .bouncyClickable {
                            if (!isUserLoggedIn) {
                                showLoginDialog = true
                            } else if (!isSubmitting && !alreadyVerified) {
                                isSubmitting = true
                                viewModel.confirmProductAccuracy(targetId, barcode) { res ->
                                    isSubmitting = false
                                    statusFeedback = if (res.isSuccess) {
                                        "Thanks for confirming this label!"
                                    } else {
                                        res.exceptionOrNull()?.message ?: "Could not confirm."
                                    }
                                }
                            }
                        },
                    shape = RoundedCornerShape(100.dp),
                    color = if (alreadyVerified) accentGreen else accentGreen.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, accentGreen.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = if (alreadyVerified) Color.White else accentGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (alreadyVerified) "Confirmed Accurate" else "Confirm Accuracy",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (alreadyVerified) Color.White else accentGreen,
                            maxLines = 1
                        )
                    }
                }

                val alreadyReported = trustSummary?.userHasReported == true
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .bouncyClickable {
                            if (!isUserLoggedIn) {
                                showLoginDialog = true
                            } else if (!isSubmitting) {
                                showReportDialog = true
                            }
                        },
                    shape = RoundedCornerShape(100.dp),
                    color = accentAmber.copy(alpha = if (alreadyReported) 0.24f else 0.12f),
                    border = BorderStroke(1.dp, accentAmber.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = accentAmber,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (alreadyReported) "Mistake Flagged" else "Report Mistake",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentAmber,
                            maxLines = 1
                        )
                    }
                }
            }

            if (!statusFeedback.isNullOrBlank()) {
                Text(
                    text = statusFeedback ?: "",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentGreen
                )
            }
        }
    }
}

@Composable
private fun ReportLabelMistakeDialog(
    productName: String,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, details: String) -> Unit
) {
    val reasons = listOf(
        "Wrong Nutrient Percentages",
        "Incorrect or Outdated Ingredients",
        "Wrong Product Photo / Brand",
        "Duplicate Catalog Entry"
    )
    var selectedReason by remember { mutableStateOf(reasons.first()) }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Report Label Mistake",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Help improve ${productName.ifBlank { "this product" }} by telling us what needs correction:",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                reasons.forEach { reason ->
                    val selected = selectedReason == reason
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = reason },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) Color(0xFFD29524).copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(
                            1.dp,
                            if (selected) Color(0xFFD29524) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )
                    ) {
                        Text(
                            text = reason,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { if (it.length <= 500) details = it },
                    label = { Text("Details (optional)", fontSize = 12.sp) },
                    placeholder = { Text("e.g., Crude Protein on bag is 28%, not 24%", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedReason, details) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD29524)),
                shape = RoundedCornerShape(100.dp)
            ) {
                Text("Submit Report", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun NutrientBar(label: String, value: Double, maxValue: Double, color: Color) {
    val progress = if (maxValue > 0) (value / maxValue).toFloat().coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "barAnimation"
    )

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${String.format("%.1f", value)}%", fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(6.dp))
            )
        }
    }
}

private data class EnrichedAlternative(
    val product: Product,
    val score: Int,
    val assessment: com.example.furbetterchoice.api.NutritionAssessment,
    val proteinDm: Double?,
    val fatDm: Double?,
    val fiberDm: Double?,
    val ashDm: Double?,
    val caPRatio: Double?,
    val advantages: List<String>,
    val isAllergenSafeForPet: Boolean,
    val matchesLifeStage: Boolean
)

@Composable
private fun AlternativesRevampedContent(
    currentProduct: Product,
    alternatives: List<Product>,
    viewModel: AppViewModel?,
    boxColor: Color,
    boxBorder: BorderStroke,
    onProductClick: (String) -> Unit,
    onCompareClick: (String) -> Unit
) {
    val accentGreen = Color(0xFF479C73)
    val accentGold = Color(0xFFD29524)
    val solidBaseColor = boxColor.copy(alpha = 1f)

    val petsState = viewModel?.pets?.collectAsState()
    val pets = petsState?.value ?: emptyList()

    val currentSpecies = remember(currentProduct) {
        currentProduct.species.ifBlank { currentProduct.pet }.trim().ifBlank { "Pet" }
    }
    val prodSpeciesLower = currentSpecies.lowercase()
    val isCatProduct = prodSpeciesLower.contains("cat")
    val isDogProduct = prodSpeciesLower.contains("dog")

    // Filter user pets strictly by the scanned product's species
    val matchingPets = remember(pets, isCatProduct, isDogProduct) {
        pets.filter { pet ->
            val petType = pet.type.lowercase().trim()
            when {
                isCatProduct -> petType.contains("cat")
                isDogProduct -> petType.contains("dog")
                else -> true
            }
        }
    }

    // Selected pet for safety filtering
    var selectedPetForSafety by remember { mutableStateOf<Pet?>(null) }

    val enrichedAlternatives by produceState<List<EnrichedAlternative>>(
        initialValue = emptyList(),
        alternatives,
        currentProduct
    ) {
        value = withContext(Dispatchers.Default) {
            val currentAssessment = com.example.furbetterchoice.api.NutritionAssessmentEngine.assess(currentProduct)
            val currentScore = ScoringSystem.calculateNutritionalScore(currentProduct, currentAssessment).percentage
            val currentProteinDm = extractNutrientVal(currentAssessment, "protein", isDryMatter = true)
            val currentBarcode = currentProduct.barcode.ifBlank { currentProduct.productId }

            alternatives
                .filter { alt ->
                    val altCode = alt.barcode.ifBlank { alt.productId }
                    altCode.isBlank() || !altCode.equals(currentBarcode, ignoreCase = true)
                }
                .distinctBy { it.barcode.ifBlank { it.productId.ifBlank { it.productName ?: "" } } }
                .map { alt ->
                    val altAssess = com.example.furbetterchoice.api.NutritionAssessmentEngine.assess(alt)
                    val altScore = ScoringSystem.calculateNutritionalScore(alt, altAssess).percentage
                    val protDm = extractNutrientVal(altAssess, "protein", isDryMatter = true)
                    val fatDm = extractNutrientVal(altAssess, "fat", isDryMatter = true)
                    val fibDm = extractNutrientVal(altAssess, "fiber", isDryMatter = true)
                    val ashDm = extractNutrientVal(altAssess, "ash", isDryMatter = true)
                    val cap = extractNutrientVal(altAssess, "ca:p", isDryMatter = true)

                    val sameStage = currentProduct.lifeStage.isNotBlank() &&
                        formatLifeStageForSpecies(alt.lifeStage, alt.species.ifBlank { alt.pet }).equals(
                            formatLifeStageForSpecies(currentProduct.lifeStage, currentSpecies),
                            ignoreCase = true
                        )

                    val reasons = mutableListOf<String>()
                    if (altAssess.overallStatus == com.example.furbetterchoice.api.NutritionStatus.MEETS) {
                        reasons.add("Meets FEDIAF")
                    }
                    if (protDm != null && currentProteinDm != null && protDm > currentProteinDm + 1.0) {
                        reasons.add("+${String.format(java.util.Locale.US, "%.1f%%", protDm - currentProteinDm)} Protein (DM)")
                    } else if (protDm != null && protDm >= 28.0) {
                        reasons.add("${String.format(java.util.Locale.US, "%.0f%%", protDm)} Protein (DM)")
                    }
                    if (altScore > currentScore) {
                        reasons.add("+${altScore - currentScore} Higher Score")
                    }
                    if (sameStage) {
                        reasons.add("Matches ${formatLifeStageForSpecies(alt.lifeStage, alt.species.ifBlank { alt.pet })}")
                    }
                    if (alt.packagingRecyclability.equals("Recyclable", ignoreCase = true)) {
                        reasons.add("Recyclable Pack")
                    }
                    if (reasons.isEmpty()) {
                        reasons.add("Balanced ${currentSpecies.replaceFirstChar { it.uppercase() }} Formula")
                    }

                    EnrichedAlternative(
                        product = alt,
                        score = altScore,
                        assessment = altAssess,
                        proteinDm = protDm,
                        fatDm = fatDm,
                        fiberDm = fibDm,
                        ashDm = ashDm,
                        caPRatio = cap,
                        advantages = reasons.take(3),
                        isAllergenSafeForPet = true,
                        matchesLifeStage = sameStage
                    )
                }
                .sortedWith(
                    compareByDescending<EnrichedAlternative> {
                        it.assessment.overallStatus == com.example.furbetterchoice.api.NutritionStatus.MEETS
                    }
                        .thenByDescending { it.score }
                        .thenByDescending { it.proteinDm ?: 0.0 }
                )
        }
    }

    // Available Brands from enriched alternatives
    val availableBrands = remember(enrichedAlternatives) {
        val brandsSet = sortedSetOf<String>()
        enrichedAlternatives.forEach { alt ->
            val b = alt.product.brands?.trim()
            if (!b.isNullOrBlank()) brandsSet.add(b)
        }
        listOf("All Brands") + brandsSet.toList()
    }
    var selectedBrand by remember { mutableStateOf("All Brands") }
    var brandSearchQuery by remember { mutableStateOf("") }
    var isBrandSearchExpanded by remember { mutableStateOf(false) }

    val displayedBrands = remember(availableBrands, brandSearchQuery) {
        if (brandSearchQuery.isBlank()) {
            availableBrands
        } else {
            availableBrands.filter { brand ->
                brand == "All Brands" || brand.contains(brandSearchQuery.trim(), ignoreCase = true)
            }
        }
    }

    // Species-tailored Life Stages
    val availableLifeStages = remember(isCatProduct) {
        if (isCatProduct) {
            listOf("All Stages", "Kitten", "Adult Cat", "Senior Cat")
        } else {
            listOf("All Stages", "Puppy", "Adult Dog", "Senior Dog")
        }
    }
    var selectedLifeStage by remember { mutableStateOf("All Stages") }

    // Filter alternatives based on brand, brand search query, life stage, and selected pet safety
    val filteredAlternatives = remember(
        enrichedAlternatives,
        selectedBrand,
        brandSearchQuery,
        selectedLifeStage,
        selectedPetForSafety
    ) {
        enrichedAlternatives.filter { alt ->
            // Brand filter + search query
            val brandMatches = when {
                selectedBrand != "All Brands" -> alt.product.brands?.equals(selectedBrand, ignoreCase = true) == true
                brandSearchQuery.isNotBlank() -> alt.product.brands?.contains(brandSearchQuery.trim(), ignoreCase = true) == true
                else -> true
            }

            // Life Stage filter
            val stageMatches = if (selectedLifeStage == "All Stages") true else {
                val stageKey = when (selectedLifeStage) {
                    "Kitten" -> "kitten"
                    "Puppy" -> "puppy"
                    "Adult Cat", "Adult Dog" -> "adult"
                    "Senior Cat", "Senior Dog" -> "senior"
                    else -> selectedLifeStage.lowercase()
                }
                val formatted = formatLifeStageForSpecies(alt.product.lifeStage, alt.product.species.ifBlank { alt.product.pet }).lowercase()
                formatted.contains(stageKey) || (alt.product.lifeStage ?: "").lowercase().contains(stageKey)
            }

            // Pet Safety filter
            val petToFilter = selectedPetForSafety
            val petSafeMatches = if (petToFilter == null) true else {
                val allergies = petToFilter.allergies
                    .split(",", ";", "\n", "/")
                    .map { it.trim().lowercase() }
                    .filter { it.isNotBlank() && it != "none" }
                if (allergies.isEmpty()) true else {
                    val ingLower = (alt.product.ingredientsText ?: "").lowercase()
                    allergies.none { allergy -> ingLower.contains(allergy) }
                }
            }

            brandMatches && stageMatches && petSafeMatches
        }
    }

    val isAnyFilterActive = selectedBrand != "All Brands" || brandSearchQuery.isNotBlank() || selectedLifeStage != "All Stages" || selectedPetForSafety != null
    val currentBarcode = currentProduct.barcode.ifBlank { currentProduct.productId }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Compact Smarter Alternatives Banner (No species picks or lifestage pills)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .subtleCardShadow(borderRadius = 20.dp),
            shape = RoundedCornerShape(20.dp),
            color = solidBaseColor
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        clip = true
                        shape = RoundedCornerShape(20.dp)
                    }
                    .drawWithCache {
                        val w = size.width
                        val h = size.height
                        val bgGradient = Brush.linearGradient(
                            colorStops = arrayOf(
                                0.0f to accentGreen.copy(alpha = 0.34f),
                                0.6f to accentGreen.copy(alpha = 0.16f),
                                1.0f to accentGold.copy(alpha = 0.22f)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(w, h)
                        )
                        val orb1Center = Offset(w * 0.88f, h * 0.25f)
                        val orb1Radius = w * 0.38f
                        val orb1Brush = Brush.radialGradient(
                            colors = listOf(accentGreen.copy(alpha = 0.24f), Color.Transparent),
                            center = orb1Center,
                            radius = orb1Radius.coerceAtLeast(1f)
                        )
                        onDrawBehind {
                            drawRect(brush = bgGradient)
                            drawCircle(brush = orb1Brush, radius = orb1Radius, center = orb1Center)
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 11.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Smarter Alternatives",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = "Ranked by nutrition and quality score",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        modifier = Modifier.subtleCardShadow(borderRadius = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${filteredAlternatives.size}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = accentGreen
                                )
                            )
                            Text(
                                text = "Found",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 2. Compact Selectors: Preferred Brand (with Search) & Life Stage
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .subtleCardShadow(borderRadius = 20.dp),
            shape = RoundedCornerShape(20.dp),
            color = boxColor,
            border = boxBorder
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Preferred Brand Row + Search Option
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Preferred Brand",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Surface(
                            modifier = Modifier.clickable {
                                isBrandSearchExpanded = !isBrandSearchExpanded
                                if (!isBrandSearchExpanded) {
                                    brandSearchQuery = ""
                                }
                            },
                            shape = RoundedCornerShape(100.dp),
                            color = if (isBrandSearchExpanded || brandSearchQuery.isNotBlank()) accentGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            border = BorderStroke(
                                1.dp,
                                if (isBrandSearchExpanded || brandSearchQuery.isNotBlank()) accentGreen.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = "Search Brand",
                                    modifier = Modifier.size(12.dp),
                                    tint = if (isBrandSearchExpanded || brandSearchQuery.isNotBlank()) accentGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (isBrandSearchExpanded) "Close" else "Search",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isBrandSearchExpanded || brandSearchQuery.isNotBlank()) accentGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = isBrandSearchExpanded,
                        enter = expandVertically(tween(220, easing = EaseOutCubic)) + fadeIn(tween(180)),
                        exit = shrinkVertically(tween(200, easing = EaseInCubic)) + fadeOut(tween(150))
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp),
                            shape = RoundedCornerShape(100.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, accentGreen.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = accentGreen
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (brandSearchQuery.isEmpty()) {
                                        Text(
                                            text = "Search brand name...",
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                    BasicTextField(
                                        value = brandSearchQuery,
                                        onValueChange = { query ->
                                            brandSearchQuery = query
                                            if (query.isNotBlank() && selectedBrand != "All Brands" && !selectedBrand.contains(query.trim(), ignoreCase = true)) {
                                                selectedBrand = "All Brands"
                                            }
                                        },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        cursorBrush = SolidColor(accentGreen),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (brandSearchQuery.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Clear brand search",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { brandSearchQuery = "" },
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        displayedBrands.forEach { brand ->
                            val isSelected = selectedBrand.equals(brand, ignoreCase = true)
                            Surface(
                                modifier = Modifier
                                    .clickable {
                                        selectedBrand = brand
                                        if (brand == "All Brands") {
                                            brandSearchQuery = ""
                                        }
                                    },
                                shape = RoundedCornerShape(100.dp),
                                color = if (isSelected) accentGreen else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) accentGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                )
                            ) {
                                Text(
                                    text = brand,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        fontSize = 10.5.sp,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                // Compact Life Stage Row
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = "Life Stage",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        availableLifeStages.forEach { stage ->
                            val isSelected = selectedLifeStage.equals(stage, ignoreCase = true)
                            Surface(
                                modifier = Modifier
                                    .clickable { selectedLifeStage = stage },
                                shape = RoundedCornerShape(100.dp),
                                color = if (isSelected) accentGreen else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) accentGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                )
                            ) {
                                Text(
                                    text = stage,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        fontSize = 10.5.sp,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. "Safe for [Pet Name]" Section (Strictly listing user's matching dogs or cats)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .subtleCardShadow(borderRadius = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = boxColor,
            border = boxBorder
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(28.dp),
                            shape = CircleShape,
                            color = accentGreen.copy(alpha = 0.15f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Favorite,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = accentGreen
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isCatProduct) "Safe for Your Cats" else if (isDogProduct) "Safe for Your Dogs" else "Safe for Your Pets",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    if (selectedPetForSafety != null) {
                        Surface(
                            modifier = Modifier.clickable { selectedPetForSafety = null },
                            shape = RoundedCornerShape(100.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "Clear",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                if (matchingPets.isEmpty()) {
                    Text(
                        text = if (isCatProduct) {
                            "No cats registered in your profile. Add your cat in Profile to evaluate personalized allergen safety."
                        } else if (isDogProduct) {
                            "No dogs registered in your profile. Add your dog in Profile to evaluate personalized allergen safety."
                        } else {
                            "No pets registered in your profile. Add your pet in Profile to check allergen safety."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    )
                } else {
                    Text(
                        text = "Tap a pet to filter alternatives that are safe from their recorded allergies:",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.5.sp
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        matchingPets.forEach { pet ->
                            val isSelected = selectedPetForSafety?.id == pet.id
                            val allergies = pet.allergies
                                .split(",", ";", "\n", "/")
                                .map { it.trim() }
                                .filter { it.isNotBlank() && !it.equals("none", ignoreCase = true) }

                            Surface(
                                modifier = Modifier
                                    .subtleCardShadow(borderRadius = 18.dp)
                                    .clickable {
                                        selectedPetForSafety = if (isSelected) null else pet
                                    },
                                shape = RoundedCornerShape(18.dp),
                                color = if (isSelected) accentGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) accentGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Pet Avatar or paw placeholder
                                    Surface(
                                        modifier = Modifier.size(36.dp),
                                        shape = CircleShape,
                                        color = accentGreen.copy(alpha = 0.15f)
                                    ) {
                                        if (!pet.imageUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = pet.imageUrl,
                                                contentDescription = pet.name,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        } else {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_paw),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = accentGreen
                                                )
                                            }
                                        }
                                    }

                                    Column {
                                        Text(
                                            text = pet.name,
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = if (isSelected) accentGreen else MaterialTheme.colorScheme.onSurface
                                            )
                                        )

                                        val allergySubtitle = if (allergies.isEmpty()) {
                                            "No allergies"
                                        } else {
                                            "Avoids: ${allergies.joinToString(", ").take(22)}"
                                        }
                                        Text(
                                            text = allergySubtitle,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 10.5.sp,
                                                color = if (isSelected) accentGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = accentGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Header for Ranked Alternatives
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_leaf),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = accentGreen
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Recommended Formulas",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
            if (isAnyFilterActive) {
                TextButton(
                    onClick = {
                        selectedBrand = "All Brands"
                        brandSearchQuery = ""
                        isBrandSearchExpanded = false
                        selectedLifeStage = "All Stages"
                        selectedPetForSafety = null
                    }
                ) {
                    Text("Reset Filters", fontSize = 12.sp, color = accentGreen, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (filteredAlternatives.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .subtleCardShadow(borderRadius = 24.dp),
                shape = RoundedCornerShape(24.dp),
                color = boxColor,
                border = boxBorder
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (enrichedAlternatives.isEmpty()) {
                            "No alternative ${currentSpecies.lowercase()} products found yet"
                        } else {
                            "No alternatives match the selected criteria"
                        },
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (enrichedAlternatives.isEmpty()) {
                            "Try pulling down to refresh or check back soon."
                        } else {
                            "Try clearing or loosening your Brand, Life Stage, or Pet Allergy filter."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (isAnyFilterActive) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                selectedBrand = "All Brands"
                                brandSearchQuery = ""
                                isBrandSearchExpanded = false
                                selectedLifeStage = "All Stages"
                                selectedPetForSafety = null
                            },
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentGreen)
                        ) {
                            Text("Reset All Filters", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                filteredAlternatives.take(15).forEachIndexed { index, item ->
                    RevampedAlternativeCard(
                        rank = index + 1,
                        item = item,
                        currentBarcode = currentBarcode,
                        boxColor = boxColor,
                        boxBorder = boxBorder,
                        onProductClick = onProductClick,
                        onCompareClick = onCompareClick
                    )
                }
            }
        }
    }
}

@Composable
private fun RevampedAlternativeCard(
    rank: Int,
    item: EnrichedAlternative,
    currentBarcode: String,
    boxColor: Color,
    boxBorder: BorderStroke,
    onProductClick: (String) -> Unit,
    onCompareClick: (String) -> Unit
) {
    val product = item.product
    val accentGreen = Color(0xFF479C73)
    val accentGold = Color(0xFFD29524)
    val isTopPick = rank == 1

    val scoreColor = when {
        item.score >= 80 -> accentGreen
        item.score >= 50 -> Color(0xFFFFA000)
        else -> Color(0xFFE53935)
    }

    val altCode = product.barcode.ifBlank { product.productId }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .subtleCardShadow(borderRadius = 28.dp)
            .bouncyClickable {
                if (altCode.isNotBlank()) onProductClick(altCode)
            },
        shape = RoundedCornerShape(28.dp),
        color = boxColor,
        border = if (isTopPick) BorderStroke(1.5.dp, accentGreen.copy(alpha = 0.55f)) else boxBorder
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Rank & Compliance Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = if (isTopPick) accentGold.copy(alpha = 0.2f) else accentGreen.copy(alpha = 0.14f),
                        border = BorderStroke(
                            1.dp,
                            if (isTopPick) accentGold.copy(alpha = 0.5f) else accentGreen.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            text = if (isTopPick) "#1 TOP PICK" else "#$rank RECOMMENDED",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isTopPick) Color(0xFFB8790E) else accentGreen
                        )
                    }

                    if (product.lifeStage.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = formatLifeStageForSpecies(product.lifeStage, product.species.ifBlank { product.pet }),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Nutritional Score Pill
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = scoreColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, scoreColor.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "Score ${item.score}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = scoreColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Product Row (Image + Title/Brand)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(68.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    if (!product.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = product.imageUrl,
                            contentDescription = product.productName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                painter = painterResource(R.drawable.ic_barcode),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.productName?.ifBlank { "Unnamed Product" } ?: "Unnamed Product",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = listOfNotNull(
                            product.brands.takeIf { it.isNotBlank() },
                            product.foodType.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }
                        ).joinToString(" • ").ifBlank { "Verified Pet Formula" },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dry Matter Macro Mini-Pills Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AlternativeMacroMiniPill(
                    label = "Protein (DM)",
                    value = item.proteinDm?.let { String.format(java.util.Locale.US, "%.1f%%", it) } ?: "Not listed",
                    modifier = Modifier.weight(1f)
                )
                AlternativeMacroMiniPill(
                    label = "Fat (DM)",
                    value = item.fatDm?.let { String.format(java.util.Locale.US, "%.1f%%", it) } ?: "Not listed",
                    modifier = Modifier.weight(1f)
                )
                AlternativeMacroMiniPill(
                    label = "Fiber (DM)",
                    value = item.fiberDm?.let { String.format(java.util.Locale.US, "%.1f%%", it) } ?: "Not listed",
                    modifier = Modifier.weight(1f)
                )
            }

            if (item.advantages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item.advantages.forEach { reason ->
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = accentGreen.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = "✓ $reason",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentGreen
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons: Quick Compare & View Analysis
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val compareArg = if (currentBarcode.isNotBlank() && altCode.isNotBlank()) {
                    "$currentBarcode,$altCode"
                } else {
                    currentBarcode.ifBlank { altCode }
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clickable { onCompareClick(compareArg) },
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Compare",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clickable {
                            if (altCode.isNotBlank()) onProductClick(altCode)
                        },
                    shape = RoundedCornerShape(100.dp),
                    color = accentGreen
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "View Analysis",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlternativeMacroMiniPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ProductAnalysisScreenPreview() {
    val sampleNutritionMap = mapOf(
        "Calcium" to mapOf("name" to "Calcium", "unit" to "%", "value" to 1.0),
        "CrudeFat" to mapOf("name" to "Crude Fat", "unit" to "%", "value" to 10.0),
        "MetabolizableEnergy" to mapOf("name" to "Metabolizable Energy", "unit" to "kcal/kg", "value" to 3400.0),
        "CrudeProtein" to mapOf("name" to "Crude Protein", "unit" to "%", "value" to 30.0),
        "Ash" to mapOf("name" to "Ash", "unit" to "%", "value" to 12.0),
        "CrudeFiber" to mapOf("name" to "Crude Fiber", "unit" to "%", "value" to 4.0),
        "Moisture" to mapOf("name" to "Moisture", "unit" to "%", "value" to 10.0),
        "Phosphorus" to mapOf("name" to "Phosphorus", "unit" to "%", "value" to 0.8)
    )

    val sampleProduct = Product(
        productId = "P0001",
        barcode = "4806538750242",
        productName = "Premium Dog Kibble",
        brands = "FurBetter Choice",
        species = "Dog",
        lifeStage = "Adult",
        foodType = "Dry Food",
        imageUrl = null,
        categoriesTags = listOf("en:dog-food"),
        nutriscoreGrade = "A",
        ecoscoreGrade = "A",
        nutriments = Nutriments(
            protein = 30.0,
            fat = 10.0,
            fiber = 4.0,
            ash = 12.0,
            moisture = 10.0,
            rawNutritionMap = sampleNutritionMap
        ),
        packagingText = null,
        packagingTags = listOf("en:recyclable"),
        labelsTags = listOf("en:organic"),
        ingredientsText = "Fresh Chicken, Rice, Corn, Fish Meal, Vitamins & Minerals."
    )

    val sampleAlternatives = listOf(
        sampleProduct.copy(barcode = "111", productName = "Organic Chicken Formula", brand = "PurePaw"),
        sampleProduct.copy(barcode = "222", productName = "Wild Duck Kibble", brand = "NatureSelect")
    )

    val viewModel = AppViewModel().apply {
        cacheProductDetails("4806538750242", sampleProduct)
        cacheAlternatives("4806538750242", sampleAlternatives)
    }

    FurBetterChoiceTheme {
        ProductAnalysisScreen(
            barcode = "4806538750242",
            viewModel = viewModel,
            onBack = {},
            onHome = {},
            onSuggestProduct = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AssessmentContextControlsPreview() {
    val sampleCats = listOf(
        Pet(petId = "1", petName = "Mochi", type = "Cat", age = 0, birthday = "2026-07-15"),
        Pet(petId = "2", petName = "Luna", type = "Cat", age = 3, birthday = "2023-05-01"),
        Pet(petId = "3", petName = "Shadow", type = "Cat", age = 9, birthday = "2017-03-10")
    )
    val allStagesCatProduct = Product(
        productId = "P_ALL",
        productName = "Complete Cat Feast",
        pet = "Cat",
        lifeStage = "All Life Stages"
    )
    val kittenOnlyProduct = Product(
        productId = "P_KIT",
        productName = "Kitten Starter",
        pet = "Cat",
        lifeStage = "Kitten"
    )
    val adultDogProduct = Product(
        productId = "P_DOG",
        productName = "Adult Dog Kibble",
        pet = "Dog",
        lifeStage = "Adult"
    )

    FurBetterChoiceTheme {
        val boxColor = Color(0xFFE4EAE8)
        val boxBorder = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AssessmentContextControls(
                eligiblePets = PetAssessmentFilter.filterPetsForProduct(sampleCats, allStagesCatProduct),
                allPetsCount = sampleCats.size,
                filterSpec = PetAssessmentFilter.buildFilterSpec(allStagesCatProduct),
                activePet = sampleCats[1],
                onSelectPet = {},
                showMerMode = true,
                selectedMerMode = FediafMerMode.STANDARD,
                onSelectMerMode = {},
                boxColor = boxColor,
                boxBorder = boxBorder
            )
            AssessmentContextControls(
                eligiblePets = PetAssessmentFilter.filterPetsForProduct(sampleCats, kittenOnlyProduct),
                allPetsCount = sampleCats.size,
                filterSpec = PetAssessmentFilter.buildFilterSpec(kittenOnlyProduct),
                activePet = sampleCats[0],
                onSelectPet = {},
                showMerMode = false,
                selectedMerMode = FediafMerMode.STANDARD,
                onSelectMerMode = {},
                boxColor = boxColor,
                boxBorder = boxBorder
            )
            AssessmentContextControls(
                eligiblePets = PetAssessmentFilter.filterPetsForProduct(sampleCats, adultDogProduct),
                allPetsCount = sampleCats.size,
                filterSpec = PetAssessmentFilter.buildFilterSpec(adultDogProduct),
                activePet = null,
                onSelectPet = {},
                showMerMode = true,
                selectedMerMode = FediafMerMode.LOWER_ACTIVITY,
                onSelectMerMode = {},
                boxColor = boxColor,
                boxBorder = boxBorder
            )
        }
    }
}

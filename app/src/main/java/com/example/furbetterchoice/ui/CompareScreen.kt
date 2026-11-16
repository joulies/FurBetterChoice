package com.example.furbetterchoice.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.furbetterchoice.R
import com.example.furbetterchoice.api.AafcoAssessmentEngine
import com.example.furbetterchoice.api.AafcoAssessmentResult
import com.example.furbetterchoice.api.AafcoResultStatus
import com.example.furbetterchoice.api.AssessmentStandard
import com.example.furbetterchoice.api.EnergySource
import com.example.furbetterchoice.api.FediafMerMode
import com.example.furbetterchoice.api.IngredientAnalyzer
import com.example.furbetterchoice.api.IngredientQualitySummary
import com.example.furbetterchoice.api.NutritionAssessment
import com.example.furbetterchoice.api.NutritionAssessmentEngine
import com.example.furbetterchoice.api.NutritionEnergyHelper
import com.example.furbetterchoice.api.NutritionStatus
import com.example.furbetterchoice.api.Pet
import com.example.furbetterchoice.api.Product
import com.example.furbetterchoice.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

enum class CompareNutrientBasis(val shortLabel: String, val headerLabel: String) {
    DRY_MATTER("Dry Matter", "Dry Matter"),
    AS_FED("As-Fed", "As-Fed"),
    PER_1000_KCAL("1,000 kcal", "Per 1,000 kcal ME")
}

private enum class ComparisonPillTone {
    GREEN,
    AMBER,
    RED,
    NEUTRAL
}

@Composable
fun CompareScreen(
    barcodesParam: String,
    viewModel: AppViewModel,
    onBackClick: () -> Unit,
    onProductClick: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER")
    onAddProductClick: (String) -> Unit = {}
) {
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val pets by viewModel.pets.collectAsState()
    val selectedPetId by viewModel.selectedAssessmentPetId.collectAsState()
    val selectedMerMode by viewModel.selectedMerMode.collectAsState()
    val activePet = remember(pets, selectedPetId) {
        selectedPetId?.let { id -> pets.firstOrNull { it.petId == id || it.id == id } } ?: pets.firstOrNull()
    }
    val boxColor = if (isDarkMode) Color(0xFF1E2422) else Color(0xFFFFFEFE)
    val highlightColor = if (isDarkMode) MaterialTheme.colorScheme.outline.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.7f)
    val boxBorder = BorderStroke(1.dp, Brush.horizontalGradient(listOf(boxColor, highlightColor, boxColor)))

    var rawBarcodes by remember(barcodesParam) {
        mutableStateOf(barcodesParam.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct())
    }

    // Cache loaded products so removing/adding an item animates smoothly without triggering a full-screen reload
    val loadedProductsCache = remember { mutableStateMapOf<String, Product>() }
    val slotOrder = remember { mutableStateListOf<String>() }

    var isInitialLoading by remember { mutableStateOf(rawBarcodes.isNotEmpty()) }
    var selectedStandard by remember { mutableStateOf(AssessmentStandard.FEDIAF) }
    var selectedBasis by remember { mutableStateOf(CompareNutrientBasis.DRY_MATTER) }
    var showProductPicker by remember { mutableStateOf(false) }

    LaunchedEffect(rawBarcodes) {
        if (rawBarcodes.isEmpty()) {
            isInitialLoading = false
            return@LaunchedEffect
        }
        val missing = rawBarcodes.filter { !loadedProductsCache.containsKey(it) }
        if (missing.isNotEmpty()) {
            if (loadedProductsCache.isEmpty()) {
                isInitialLoading = true
            }
            val fetched = viewModel.fetchComparisonProducts(missing)
            fetched.forEach { p ->
                val key = p.barcode.ifEmpty { p.productId }
                if (key.isNotEmpty()) {
                    loadedProductsCache[key] = p
                    if (!slotOrder.contains(key)) {
                        slotOrder.add(key)
                    }
                }
            }
            if (fetched.size == 1 && missing.size == 1) {
                val reqKey = missing.first()
                if (!loadedProductsCache.containsKey(reqKey)) {
                    loadedProductsCache[reqKey] = fetched.first()
                    if (!slotOrder.contains(reqKey)) {
                        slotOrder.add(reqKey)
                    }
                }
            }
        }
        rawBarcodes.forEach { code ->
            if (loadedProductsCache.containsKey(code) && !slotOrder.contains(code)) {
                slotOrder.add(code)
            }
        }
        isInitialLoading = false
    }

    val comparisonProducts = remember(rawBarcodes, loadedProductsCache.size) {
        rawBarcodes.mapNotNull { loadedProductsCache[it] }
    }

    // Smooth staggered entrance triggers
    var showHeader by remember { mutableStateOf(false) }
    var showCards by remember { mutableStateOf(false) }
    var showMatrix by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(50)
        showHeader = true
        delay(90)
        showCards = true
        delay(90)
        showMatrix = true
    }

    fun removeProduct(barcode: String) {
        rawBarcodes = rawBarcodes.filter { it != barcode }
    }

    fun addProductInFlow(product: Product) {
        val code = product.barcode.ifBlank { product.productId }.trim()
        if (code.isBlank() || rawBarcodes.contains(code) || rawBarcodes.size >= 3) return
        loadedProductsCache[code] = product
        if (!slotOrder.contains(code)) {
            slotOrder.add(code)
        }
        rawBarcodes = (rawBarcodes + code).distinct().take(3)
        showProductPicker = false
    }

    val screenState = when {
        isInitialLoading -> "LOADING"
        comparisonProducts.isEmpty() -> "EMPTY"
        else -> "CONTENT"
    }

    if (showProductPicker) {
        CompareProductPickerDialog(
            viewModel = viewModel,
            alreadySelectedBarcodes = rawBarcodes.toSet(),
            activePet = activePet,
            onDismiss = { showProductPicker = false },
            onSelectProduct = { addProductInFlow(it) }
        )
    }

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

        Scaffold(
            topBar = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding(),
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CustomBackButton(onClick = onBackClick)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Product Comparison",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = if (activePet != null) {
                                    "Evaluating for ${activePet.petName} (${activePet.type})"
                                } else {
                                    "Evaluating side-by-side nutrition"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            AnimatedContent(
                targetState = screenState,
                transitionSpec = {
                    (fadeIn(tween(320, easing = EaseOutCubic)) + scaleIn(tween(320, easing = EaseOutCubic), initialScale = 0.97f))
                        .togetherWith(fadeOut(tween(220, easing = EaseInCubic)))
                },
                label = "compareScreenState"
            ) { state ->
                when (state) {
                    "LOADING" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Loading comparison data...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    "EMPTY" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_barcode),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(72.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No products selected for comparison",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Select up to 3 saved, recent, or catalog food items to compare them side-by-side.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(
                                    onClick = { showProductPicker = true },
                                    shape = RoundedCornerShape(100.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF479C73))
                                ) {
                                    Text("Add Product to Compare", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 16.dp)
                                .animateContentSize(animationSpec = tween(320, easing = EaseInOutCubic))
                        ) {
                            // Header Slots Carousel with Smooth Exit Animations
                            StaggeredReveal(visible = showHeader) {
                                ComparisonHeaderSlots(
                                    slotOrder = slotOrder,
                                    activeBarcodes = rawBarcodes.toSet(),
                                    productsMap = loadedProductsCache,
                                    maxSlots = 3,
                                    boxColor = boxColor,
                                    boxBorder = boxBorder,
                                    onRemoveProduct = { removeProduct(it) },
                                    onAddSlotClick = { showProductPicker = true },
                                    onProductClick = onProductClick
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Species Mismatch Warning Banner
                            val speciesWarning = remember(comparisonProducts) {
                                val speciesSet = comparisonProducts.mapNotNull { p ->
                                    val spec = (p.pet.ifEmpty { p.species }).lowercase()
                                    val tags = p.categoriesTags?.map { it.lowercase() } ?: emptyList()
                                    when {
                                        spec.contains("cat") || tags.any { it.contains("cat") } -> "Cat"
                                        spec.contains("dog") || tags.any { it.contains("dog") } -> "Dog"
                                        else -> null
                                    }
                                }.toSet()

                                if (speciesSet.contains("Cat") && speciesSet.contains("Dog")) {
                                    "Pet Type Mismatch: You are comparing Dog food and Cat food. Cats and dogs have fundamentally different nutritional requirements."
                                } else {
                                    null
                                }
                            }

                            AnimatedVisibility(
                                visible = speciesWarning != null && showHeader,
                                enter = expandVertically(tween(300, easing = EaseOutCubic)) + fadeIn(tween(240)),
                                exit = shrinkVertically(tween(260, easing = EaseInCubic)) + fadeOut(tween(180))
                            ) {
                                Column {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        shape = RoundedCornerShape(20.dp),
                                        color = Color(0xFFD97706).copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = speciesWarning ?: "",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFFD97706),
                                                    fontSize = 12.sp
                                                )
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }

                            // FEDIAF vs AAFCO Switcher
                            StaggeredReveal(visible = showHeader) {
                                CompareStandardSwitcherCard(
                                    selectedStandard = selectedStandard,
                                    onSelectStandard = { selectedStandard = it },
                                    selectedMerMode = selectedMerMode,
                                    onSelectMerMode = { viewModel.setFediafMerMode(it) },
                                    boxColor = boxColor,
                                    boxBorder = boxBorder
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Overall Verdict Card with Smooth Collapse when < 2 products
                            AnimatedVisibility(
                                visible = comparisonProducts.size >= 2 && showCards,
                                enter = expandVertically(tween(340, easing = EaseOutCubic)) + fadeIn(tween(280)),
                                exit = shrinkVertically(tween(280, easing = EaseInCubic)) + fadeOut(tween(200))
                            ) {
                                Column {
                                    ComparisonVerdictCard(
                                        products = comparisonProducts,
                                        selectedStandard = selectedStandard,
                                        activePet = activePet,
                                        merMode = selectedMerMode,
                                        boxColor = boxColor
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }

                            // 3-Way Nutrient Basis Toggle: Dry Matter | As-Fed | Per 1,000 kcal
                            StaggeredReveal(visible = showCards) {
                                NutrientBasisToggleCard(
                                    selectedBasis = selectedBasis,
                                    onBasisSelected = { selectedBasis = it },
                                    boxColor = boxColor,
                                    boxBorder = boxBorder
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Side-by-Side Comparison Matrix
                            StaggeredReveal(visible = showMatrix) {
                                ComparisonMatrixCard(
                                    products = comparisonProducts,
                                    selectedStandard = selectedStandard,
                                    basis = selectedBasis,
                                    isDarkMode = isDarkMode,
                                    activePet = activePet,
                                    pets = pets,
                                    merMode = selectedMerMode,
                                    boxColor = boxColor,
                                    boxBorder = boxBorder
                                )
                            }

                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareStandardSwitcherCard(
    selectedStandard: AssessmentStandard,
    onSelectStandard: (AssessmentStandard) -> Unit,
    selectedMerMode: FediafMerMode,
    onSelectMerMode: (FediafMerMode) -> Unit,
    boxColor: Color,
    boxBorder: BorderStroke
) {
    val accentGreen = Color(0xFF479C73)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .subtleCardShadow(borderRadius = 24.dp),
        shape = RoundedCornerShape(24.dp),
        color = boxColor,
        border = boxBorder
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Assessment Standard",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(modifier = Modifier.padding(3.dp)) {
                        AssessmentStandard.entries.forEach { standard ->
                            val isSelected = selectedStandard == standard
                            Surface(
                                modifier = Modifier.bouncyClickable { onSelectStandard(standard) },
                                shape = RoundedCornerShape(100.dp),
                                color = if (isSelected) accentGreen else Color.Transparent
                            ) {
                                Text(
                                    text = standard.displayName,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (selectedStandard == AssessmentStandard.FEDIAF) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Activity Level (MER)",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FediafMerMode.entries.forEach { mode ->
                            val isSelected = selectedMerMode == mode
                            Surface(
                                modifier = Modifier.clickable { onSelectMerMode(mode) },
                                shape = RoundedCornerShape(100.dp),
                                color = if (isSelected) accentGreen.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) accentGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                                )
                            ) {
                                Text(
                                    text = if (mode == FediafMerMode.STANDARD) "Standard" else "Indoor / Lower",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) accentGreen else MaterialTheme.colorScheme.onSurfaceVariant
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
fun ComparisonHeaderSlots(
    slotOrder: List<String>,
    activeBarcodes: Set<String>,
    productsMap: Map<String, Product>,
    maxSlots: Int = 3,
    boxColor: Color,
    boxBorder: BorderStroke,
    onRemoveProduct: (String) -> Unit,
    onAddSlotClick: () -> Unit,
    onProductClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 10.dp)
            .animateContentSize(animationSpec = tween(320, easing = EaseInOutCubic)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        slotOrder.forEach { keyCode ->
            val product = productsMap[keyCode] ?: return@forEach
            val isVisible = activeBarcodes.contains(keyCode)

            key(keyCode) {
                AnimatedVisibility(
                    visible = isVisible,
                    enter = expandHorizontally(
                        animationSpec = tween(320, easing = EaseOutCubic),
                        expandFrom = Alignment.Start
                    ) + fadeIn(tween(260)) + scaleIn(tween(320, easing = EaseOutCubic), initialScale = 0.88f),
                    exit = shrinkHorizontally(
                        animationSpec = tween(290, easing = EaseInOutCubic),
                        shrinkTowards = Alignment.Start
                    ) + fadeOut(tween(200)) + scaleOut(tween(280, easing = EaseInOutCubic), targetScale = 0.88f)
                ) {
                    Row {
                        Surface(
                            modifier = Modifier
                                .width(142.dp)
                                .subtleCardShadow(borderRadius = 24.dp)
                                .bouncyClickable { onProductClick(keyCode) },
                            shape = RoundedCornerShape(24.dp),
                            color = boxColor,
                            border = boxBorder
                        ) {
                            Box(modifier = Modifier.padding(12.dp)) {
                                IconButton(
                                    onClick = { onRemoveProduct(keyCode) },
                                    modifier = Modifier
                                        .size(24.dp)
                                        .align(Alignment.TopEnd)
                                        .background(Color.Black.copy(alpha = 0.1f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Surface(
                                        modifier = Modifier.size(56.dp),
                                        shape = RoundedCornerShape(16.dp),
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
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(
                                        text = product.brands.takeIf { it.isNotEmpty() } ?: "Brand",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFF7A1129),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = product.productName.takeIf { it.isNotEmpty() } ?: "Product",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                }
            }
        }

        // Add Slot Button if < maxSlots
        AnimatedVisibility(
            visible = activeBarcodes.size < maxSlots,
            enter = expandHorizontally(tween(300, easing = EaseOutCubic)) + fadeIn(tween(250)),
            exit = shrinkHorizontally(tween(250, easing = EaseInCubic)) + fadeOut(tween(180))
        ) {
            Surface(
                modifier = Modifier
                    .width(135.dp)
                    .height(145.dp)
                    .subtleCardShadow(borderRadius = 24.dp)
                    .bouncyClickable(onClick = onAddSlotClick),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(Color(0xFF479C73), Color(0xFF479C73))))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF479C73).copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Product",
                            tint = Color(0xFF479C73),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Add Product",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF479C73)
                        )
                    )
                }
            }
        }
    }
}

private data class VerdictCandidate(
    val product: Product,
    val isCompleteAndSafeLimit: Boolean,
    val meetsOverall: Boolean,
    val meetsCount: Int,
    val failsCount: Int,
    val evaluatedCount: Int
)

@Composable
fun ComparisonVerdictCard(
    products: List<Product>,
    selectedStandard: AssessmentStandard = AssessmentStandard.FEDIAF,
    activePet: Pet? = null,
    merMode: FediafMerMode = FediafMerMode.STANDARD,
    boxColor: Color
) {
    val evaluated = remember(products, selectedStandard, activePet, merMode) {
        products.map { p ->
            if (selectedStandard == AssessmentStandard.FEDIAF) {
                val assess = NutritionAssessmentEngine.assess(
                    product = p,
                    userPetType = activePet?.type,
                    userPetBirthday = activePet?.birthday,
                    userPetAgeYears = activePet?.age,
                    userPetBreed = activePet?.breed,
                    merMode = merMode
                )
                VerdictCandidate(
                    product = p,
                    isCompleteAndSafeLimit = !assess.isTreatOrComplementary && assess.overallStatus != NutritionStatus.ABOVE_MAXIMUM,
                    meetsOverall = assess.overallStatus == NutritionStatus.MEETS,
                    meetsCount = assess.nutrientAssessments.count { it.status == NutritionStatus.MEETS },
                    failsCount = assess.nutrientAssessments.count { it.status == NutritionStatus.BELOW || it.status == NutritionStatus.ABOVE_MAXIMUM },
                    evaluatedCount = assess.nutrientAssessments.size
                )
            } else {
                val aafco = AafcoAssessmentEngine.assess(
                    product = p,
                    userPetType = activePet?.type,
                    userPetAgeYears = activePet?.age,
                    userPetBirthday = activePet?.birthday,
                    userPetBreed = activePet?.breed
                )
                VerdictCandidate(
                    product = p,
                    isCompleteAndSafeLimit = !aafco.isTreatOrComplementary && aafco.overallStatus != AafcoResultStatus.ABOVE_MAXIMUM,
                    meetsOverall = aafco.overallStatus == AafcoResultStatus.MEETS_REFERENCE,
                    meetsCount = aafco.meetsCount,
                    failsCount = aafco.belowCount + aafco.aboveMaxCount,
                    evaluatedCount = aafco.evaluatedNutrientsCount
                )
            }
        }.sortedWith(
            compareByDescending<VerdictCandidate> { it.isCompleteAndSafeLimit }
                .thenByDescending { it.meetsOverall }
                .thenByDescending { it.meetsCount }
                .thenBy { it.failsCount }
                .thenByDescending { it.evaluatedCount }
        )
    }

    val winner = evaluated.firstOrNull() ?: return
    val runnerUp = evaluated.getOrNull(1)

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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .animateContentSize(tween(300, easing = EaseInOutCubic)),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        color = solidBaseColor.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, accentGreen.copy(alpha = 0.32f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Winner",
                                tint = accentGreen,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = solidBaseColor.copy(alpha = 0.88f),
                            border = BorderStroke(1.dp, accentGreen.copy(alpha = 0.30f))
                        ) {
                            Text(
                                text = "Recommended Choice (${selectedStandard.displayName})",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = accentGreen,
                                    fontSize = 10.5.sp
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = winner.product.productName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        val petLabel = activePet?.petName ?: "your pet"
                        val reasonText = when {
                            winner.meetsOverall && winner.isCompleteAndSafeLimit ->
                                "Meets ${selectedStandard.displayName} nutritional standards for $petLabel."
                            runnerUp != null && winner.meetsCount > runnerUp.meetsCount ->
                                "Meets more evaluated ${selectedStandard.displayName} nutrient targets (${winner.meetsCount} vs ${runnerUp.meetsCount}) than alternatives."
                            else ->
                                "Provides optimal ${selectedStandard.displayName} nutrient adequacy for $petLabel."
                        }

                        Text(
                            text = reasonText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NutrientBasisToggleCard(
    selectedBasis: CompareNutrientBasis,
    onBasisSelected: (CompareNutrientBasis) -> Unit,
    boxColor: Color,
    boxBorder: BorderStroke
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .subtleCardShadow(borderRadius = 28.dp),
        shape = RoundedCornerShape(28.dp),
        color = boxColor,
        border = boxBorder
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column {
                Text(
                    text = "Nutrient Basis",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Text(
                    text = when (selectedBasis) {
                        CompareNutrientBasis.DRY_MATTER -> "Dry Matter removes moisture for fair wet vs dry kibble comparison."
                        CompareNutrientBasis.AS_FED -> "As-Fed shows raw percentages printed on the package label."
                        CompareNutrientBasis.PER_1000_KCAL -> "Per 1,000 kcal ME normalizes nutrients by caloric density."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                shape = RoundedCornerShape(100.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompareNutrientBasis.entries.forEach { basis ->
                        val isSelected = selectedBasis == basis
                        val bgColor by animateColorAsState(
                            targetValue = if (isSelected) Color(0xFF479C73) else Color.Transparent,
                            animationSpec = tween(250, easing = EaseInOutCubic),
                            label = "basisBg"
                        )
                        val txtColor by animateColorAsState(
                            targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = tween(250, easing = EaseInOutCubic),
                            label = "basisTxt"
                        )
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .bouncyClickable { onBasisSelected(basis) },
                            shape = RoundedCornerShape(100.dp),
                            color = bgColor
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = basis.shortLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = txtColor,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isCleanPackagingField(value: String): Boolean {
    val v = value.trim()
    return v.isNotEmpty() &&
        !v.equals("unknown", ignoreCase = true) &&
        !v.equals("n/a", ignoreCase = true) &&
        !v.equals("none", ignoreCase = true) &&
        !v.equals("null", ignoreCase = true) &&
        !v.equals("information unavailable", ignoreCase = true)
}

private fun resolvePackagingMaterial(product: Product): String {
    if (isCleanPackagingField(product.packagingMaterial)) return product.packagingMaterial.trim()
    val combined = ((product.packagingText ?: "") + " " + (product.packagingTags?.joinToString(" ") ?: "")).lowercase(Locale.ROOT)
    return when {
        combined.contains("aluminum") || combined.contains("aluminium") || combined.contains("metal") || combined.contains("tin") || combined.contains("can") -> "Metal / Can"
        combined.contains("cardboard") || combined.contains("paper") -> "Paper / Cardboard"
        combined.contains("glass") -> "Glass"
        combined.contains("plastic") || combined.contains("pouch") -> "Plastic"
        else -> "Not Specified"
    }
}

private fun resolvePackagingRecyclability(product: Product): String {
    if (isCleanPackagingField(product.packagingRecyclability)) return product.packagingRecyclability.trim()
    val combined = ((product.packagingText ?: "") + " " + (product.packagingTags?.joinToString(" ") ?: "")).lowercase(Locale.ROOT)
    return when {
        combined.contains("non-recyclable") || combined.contains("not recyclable") -> "Not Recyclable"
        combined.contains("recyclable") || combined.contains("recycle") -> "Recyclable"
        else -> "Not Specified"
    }
}

private fun resolvePackagingBiodegradability(product: Product): String {
    if (isCleanPackagingField(product.packagingBiodegradability)) return product.packagingBiodegradability.trim()
    val combined = ((product.packagingText ?: "") + " " + (product.packagingTags?.joinToString(" ") ?: "")).lowercase(Locale.ROOT)
    return when {
        combined.contains("non-biodegradable") || combined.contains("not biodegradable") -> "Non-Biodegradable"
        combined.contains("biodegradable") || combined.contains("compostable") -> "Biodegradable"
        else -> "Not Specified"
    }
}

internal data class SpeciesAllergenMatch(
    val petName: String,
    val petType: String,
    val allergens: List<String>
)

private data class AssessedComparisonItem(
    val product: Product,
    val fediaf: NutritionAssessment,
    val aafco: AafcoAssessmentResult,
    val productSpecies: String?,
    val allergenMatches: List<SpeciesAllergenMatch>,
    val hasSpeciesAllergyProfiles: Boolean,
    val ingredientQuality: IngredientQualitySummary,
    val primaryProtein: String,
    val ingredientNames: List<String>,
    val resolvedLifeStage: String,
    val packagingMaterial: String,
    val recyclability: String,
    val biodegradability: String
)

internal fun resolveComparisonProductSpecies(product: Product): String? {
    val explicitSpec = product.species.ifBlank { product.pet }.lowercase(Locale.ROOT)
    val explicitCat = explicitSpec.contains("cat") || explicitSpec.contains("feline")
    val explicitDog = explicitSpec.contains("dog") || explicitSpec.contains("canine")
    if (explicitCat && !explicitDog) return "Cat"
    if (explicitDog && !explicitCat) return "Dog"
    if (explicitCat) return null

    val tags = product.categoriesTags?.joinToString(" ")?.lowercase(Locale.ROOT).orEmpty()
    val nameText = "${product.productName} ${product.brands}".lowercase(Locale.ROOT)
    val isCat = tags.contains("cat") || tags.contains("feline") || nameText.contains("cat ") || nameText.contains("kitten")
    val isDog = tags.contains("dog") || tags.contains("canine") || nameText.contains("dog ") || nameText.contains("puppy")
    return when {
        isCat && !isDog -> "Cat"
        isDog && !isCat -> "Dog"
        else -> null
    }
}

internal fun applicablePetsForProductAllergyCheck(
    product: Product,
    pets: List<Pet>,
    activePet: Pet?
): List<Pet> {
    val productSpecies = resolveComparisonProductSpecies(product)
    val allPets = (listOfNotNull(activePet) + pets).distinctBy { it.petId.ifBlank { "${it.id}_${it.petName}_${it.type}" } }
    val candidates = when (productSpecies) {
        "Cat" -> {
            val catPets = allPets.filter {
                it.type.contains("cat", ignoreCase = true) || it.type.contains("feline", ignoreCase = true)
            }
            if (activePet != null && (activePet.type.contains("cat", ignoreCase = true) || activePet.type.contains("feline", ignoreCase = true))) {
                listOf(activePet)
            } else {
                catPets
            }
        }
        "Dog" -> {
            val dogPets = allPets.filter {
                it.type.contains("dog", ignoreCase = true) || it.type.contains("canine", ignoreCase = true)
            }
            if (activePet != null && (activePet.type.contains("dog", ignoreCase = true) || activePet.type.contains("canine", ignoreCase = true))) {
                listOf(activePet)
            } else {
                dogPets
            }
        }
        else -> listOfNotNull(activePet)
    }

    return candidates.filter { pet ->
        pet.allergies.isNotBlank() &&
            !pet.allergies.equals("none", ignoreCase = true) &&
            !pet.allergies.equals("n/a", ignoreCase = true)
    }
}

internal fun detectSpeciesMatchedAllergens(
    product: Product,
    pets: List<Pet>,
    activePet: Pet?
): List<SpeciesAllergenMatch> {
    val candidates = applicablePetsForProductAllergyCheck(product, pets, activePet)
    if (candidates.isEmpty()) return emptyList()

    return candidates.mapNotNull { pet ->
        val matched = detectPetAllergensInProduct(product, pet)
        if (matched.isEmpty()) null
        else SpeciesAllergenMatch(
            petName = pet.petName.ifBlank { pet.type.ifBlank { "Pet" } },
            petType = pet.type,
            allergens = matched
        )
    }
}

private fun detectPetAllergensInProduct(product: Product, pet: Pet?): List<String> {
    if (pet == null || pet.allergies.isBlank()) return emptyList()
    val allergies = pet.allergies
        .split(",", ";", "\n", "/")
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotBlank() && it != "none" && it != "n/a" }
    if (allergies.isEmpty()) return emptyList()

    val ingredientsLower = product.ingredientsText.lowercase(Locale.ROOT)
    val analyzed = IngredientAnalyzer.analyzeIngredients(product.ingredientsText, product.species.ifBlank { product.pet })
    val groups = analyzed.mapNotNull { it.allergenGroup?.lowercase(Locale.ROOT) }.toSet()

    return allergies.filter { allergen ->
        val boundaryMatch = Regex("""(?<![a-z0-9])${Regex.escape(allergen)}(?![a-z0-9])""")
            .containsMatchIn(ingredientsLower)
        val groupMatch = groups.any { grp ->
            grp == allergen ||
                (allergen in setOf("chicken", "turkey", "duck", "poultry") && grp == "poultry") ||
                (allergen in setOf("salmon", "tuna", "fish", "seafood", "shrimp") && grp == "fish") ||
                (allergen in setOf("wheat", "corn", "barley", "oat", "oats", "grain", "gluten") && grp == "grain") ||
                (allergen in setOf("milk", "cheese", "whey", "casein", "dairy") && grp == "dairy") ||
                (allergen in setOf("soy", "soybean") && grp == "soy")
        }
        boundaryMatch || groupMatch
    }.map { it.replaceFirstChar { c -> c.titlecase(Locale.US) } }.distinct()
}

private fun resolveComparisonLifeStage(product: Product): String {
    val raw = product.lifeStage.trim()
    val speciesRaw = product.pet.ifBlank { product.species }.lowercase()
    val tags = product.categoriesTags?.map { it.lowercase() } ?: emptyList()
    val isCat = speciesRaw.contains("cat") || tags.any { it.contains("cat") }
    val combined = (raw + " " + tags.joinToString(" ")).lowercase()

    return when {
        raw.isNotBlank() && !raw.equals("Unknown", ignoreCase = true) && !raw.equals("Not Specified", ignoreCase = true) -> {
            when {
                raw.contains("puppy", ignoreCase = true) -> "Puppy"
                raw.contains("kitten", ignoreCase = true) -> "Kitten"
                raw.contains("growth", ignoreCase = true) || raw.contains("junior", ignoreCase = true) -> if (isCat) "Kitten" else "Puppy"
                raw.contains("senior", ignoreCase = true) || raw.contains("mature", ignoreCase = true) -> if (isCat) "Senior Cat" else "Senior Dog"
                raw.contains("adult", ignoreCase = true) -> if (isCat) "Adult Cat" else "Adult Dog"
                raw.contains("all", ignoreCase = true) -> "All Life Stages"
                else -> raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            }
        }
        combined.contains("kitten") -> "Kitten"
        combined.contains("puppy") -> "Puppy"
        combined.contains("senior") -> if (isCat) "Senior Cat" else "Senior Dog"
        combined.contains("adult") -> if (isCat) "Adult Cat" else "Adult Dog"
        else -> "Not Specified"
    }
}

@Composable
private fun ComparisonValuePill(
    text: String,
    tone: ComparisonPillTone,
    isDarkMode: Boolean,
    badgeText: String? = null,
    modifier: Modifier = Modifier
) {
    val (fill, border, textColor) = when (tone) {
        ComparisonPillTone.GREEN -> Triple(
            if (isDarkMode) Color(0xFF479C73).copy(alpha = 0.24f) else Color(0xFFDDF3E8),
            Color(0xFF479C73).copy(alpha = 0.65f),
            if (isDarkMode) Color(0xFF7CE0B0) else Color(0xFF236B48)
        )
        ComparisonPillTone.AMBER -> Triple(
            if (isDarkMode) Color(0xFFD97706).copy(alpha = 0.22f) else Color(0xFFFFF3E0),
            Color(0xFFD97706).copy(alpha = 0.65f),
            if (isDarkMode) Color(0xFFFBBF24) else Color(0xFFB45309)
        )
        ComparisonPillTone.RED -> Triple(
            if (isDarkMode) Color(0xFFE53935).copy(alpha = 0.22f) else Color(0xFFFFEBEE),
            Color(0xFFE53935).copy(alpha = 0.65f),
            if (isDarkMode) Color(0xFFFCA5A5) else Color(0xFFC62828)
        )
        ComparisonPillTone.NEUTRAL -> Triple(
            if (isDarkMode) Color(0xFF2D3331) else Color(0xFFE7EBEA),
            if (isDarkMode) Color(0xFF47504D) else Color(0xFFCBD2D0),
            if (isDarkMode) Color(0xFFB8C0BD) else Color(0xFF56605C)
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(100.dp),
        color = fill,
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (tone != ComparisonPillTone.NEUTRAL) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = textColor
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (badgeText != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                )
            }
        }
    }
}

private data class ResolvedNutrientCell(
    val displayText: String,
    val tone: ComparisonPillTone,
    val badgeText: String?
)

private fun resolveNutrientCell(
    item: AssessedComparisonItem,
    standard: AssessmentStandard,
    basis: CompareNutrientBasis,
    aliases: List<String>
): ResolvedNutrientCell {
    val normAliases = aliases.map { NutritionEnergyHelper.normalizeKey(it) }.toSet()

    val fediafEntry = item.fediaf.nutrientAssessments.firstOrNull { na ->
        val k = NutritionEnergyHelper.normalizeKey(na.nutrientKey)
        val n = NutritionEnergyHelper.normalizeKey(na.name)
        k in normAliases || n in normAliases || normAliases.any { a -> k.contains(a) || n.contains(a) }
    }
    val aafcoEntry = item.aafco.nutrientResults.firstOrNull { na ->
        val k = NutritionEnergyHelper.normalizeKey(na.nutrientKey)
        val n = NutritionEnergyHelper.normalizeKey(na.nutrientName)
        k in normAliases || n in normAliases || normAliases.any { a -> k.contains(a) || n.contains(a) }
    }

    val (tone, badge) = if (standard == AssessmentStandard.FEDIAF) {
        when (fediafEntry?.status) {
            NutritionStatus.MEETS -> ComparisonPillTone.GREEN to "Meets"
            NutritionStatus.BELOW -> ComparisonPillTone.AMBER to "Low"
            NutritionStatus.ABOVE_MAXIMUM -> ComparisonPillTone.RED to "High"
            else -> ComparisonPillTone.NEUTRAL to null
        }
    } else {
        when (aafcoEntry?.status) {
            AafcoResultStatus.MEETS_REFERENCE -> ComparisonPillTone.GREEN to "Meets"
            AafcoResultStatus.BELOW_REFERENCE -> ComparisonPillTone.AMBER to "Low"
            AafcoResultStatus.ABOVE_MAXIMUM -> ComparisonPillTone.RED to "High"
            else -> ComparisonPillTone.NEUTRAL to null
        }
    }

    var asFedVal = if (standard == AssessmentStandard.AAFCO) aafcoEntry?.asFedValue ?: fediafEntry?.asFedValue else fediafEntry?.asFedValue ?: aafcoEntry?.asFedValue
    var asFedUnit = if (standard == AssessmentStandard.AAFCO) aafcoEntry?.asFedUnit ?: fediafEntry?.asFedUnit ?: "%" else fediafEntry?.asFedUnit ?: aafcoEntry?.asFedUnit ?: "%"
    var dmVal = if (standard == AssessmentStandard.AAFCO) aafcoEntry?.dryMatterValue ?: fediafEntry?.dryMatterValue else fediafEntry?.dryMatterValue ?: aafcoEntry?.dryMatterValue
    var dmUnit = if (standard == AssessmentStandard.AAFCO) aafcoEntry?.unit ?: asFedUnit else asFedUnit
    var energyVal = if (standard == AssessmentStandard.AAFCO) aafcoEntry?.energyBasisValue ?: fediafEntry?.energyBasisValue else fediafEntry?.energyBasisValue ?: aafcoEntry?.energyBasisValue
    var energyUnit = if (standard == AssessmentStandard.AAFCO) aafcoEntry?.energyBasisUnit ?: fediafEntry?.energyBasisUnit else fediafEntry?.energyBasisUnit ?: aafcoEntry?.energyBasisUnit

    if (asFedVal == null && dmVal == null) {
        val raw = NutritionEnergyHelper.extractRawNutrientValue(item.product, aliases)
        if (raw != null && raw.value > 0.0) {
            asFedVal = raw.value
            asFedUnit = raw.unit.ifBlank { "%" }
            dmUnit = asFedUnit
            val dmPct = item.fediaf.dryMatterPercent
            if (dmPct != null && dmPct > 0.0 && asFedUnit in setOf("%", "mg/kg", "IU/kg")) {
                dmVal = (raw.value / dmPct) * 100.0
            }
            val meDm = item.fediaf.energyResult.dryMatterKcalPerKg
            val pair = NutritionEnergyHelper.calculatePer1000Kcal(dmVal, dmUnit, meDm)
            energyVal = pair?.first
            energyUnit = pair?.second
        }
    }

    fun fmtVal(v: Double, unit: String): String {
        val u = unit.trim()
        return when {
            u == "%" -> if (v < 1.0) String.format(Locale.US, "%.2f%%", v) else String.format(Locale.US, "%.1f%%", v)
            u.contains("1000 kcal", ignoreCase = true) -> {
                val shortUnit = if (u.startsWith("mg", ignoreCase = true)) "mg" else if (u.startsWith("IU", ignoreCase = true)) "IU" else "g"
                if (v < 10.0) String.format(Locale.US, "%.1f %s", v, shortUnit) else String.format(Locale.US, "%.0f %s", v, shortUnit)
            }
            else -> if (v < 10.0) String.format(Locale.US, "%.2f %s", v, u) else String.format(Locale.US, "%.0f %s", v, u)
        }
    }

    val text = when (basis) {
        CompareNutrientBasis.DRY_MATTER -> when {
            dmVal != null -> fmtVal(dmVal, dmUnit)
            asFedVal != null -> "${fmtVal(asFedVal, asFedUnit)} (AF)"
            else -> "-"
        }
        CompareNutrientBasis.AS_FED -> asFedVal?.let { fmtVal(it, asFedUnit) } ?: "-"
        CompareNutrientBasis.PER_1000_KCAL -> energyVal?.let { fmtVal(it, energyUnit ?: "g/1000 kcal ME") } ?: "-"
    }

    return ResolvedNutrientCell(displayText = text, tone = tone, badgeText = badge)
}

@Composable
fun ComparisonMatrixCard(
    products: List<Product>,
    selectedStandard: AssessmentStandard = AssessmentStandard.FEDIAF,
    basis: CompareNutrientBasis = CompareNutrientBasis.DRY_MATTER,
    isDarkMode: Boolean = isSystemInDarkTheme(),
    activePet: Pet? = null,
    pets: List<Pet> = emptyList(),
    merMode: FediafMerMode = FediafMerMode.STANDARD,
    boxColor: Color,
    boxBorder: BorderStroke
) {
    val assessedList = remember(products, activePet, pets, merMode) {
        products.map { p ->
            val fediaf = NutritionAssessmentEngine.assess(
                product = p,
                userPetType = activePet?.type,
                userPetBirthday = activePet?.birthday,
                userPetAgeYears = activePet?.age,
                userPetBreed = activePet?.breed,
                merMode = merMode
            )
            val aafco = AafcoAssessmentEngine.assess(
                product = p,
                userPetType = activePet?.type,
                userPetAgeYears = activePet?.age,
                userPetBirthday = activePet?.birthday,
                userPetBreed = activePet?.breed
            )
            val species = p.species.ifBlank { p.pet }
            val analyzed = IngredientAnalyzer.analyzeIngredients(p.ingredientsText, species)
            val primaryProtein = analyzed.firstOrNull {
                it.category in setOf("Animal Protein", "Marine Protein")
            }?.name ?: analyzed.firstOrNull()?.name ?: "Not Specified"
            val ingredientNames = analyzed.map { it.name }.filter { it.isNotBlank() }
            val applicableAllergyPets = applicablePetsForProductAllergyCheck(p, pets, activePet)

            AssessedComparisonItem(
                product = p,
                fediaf = fediaf,
                aafco = aafco,
                productSpecies = resolveComparisonProductSpecies(p),
                allergenMatches = detectSpeciesMatchedAllergens(p, pets, activePet),
                hasSpeciesAllergyProfiles = applicableAllergyPets.isNotEmpty(),
                ingredientQuality = IngredientAnalyzer.evaluateIngredientQualitySummary(p.ingredientsText),
                primaryProtein = primaryProtein,
                ingredientNames = ingredientNames,
                resolvedLifeStage = resolveComparisonLifeStage(p),
                packagingMaterial = resolvePackagingMaterial(p),
                recyclability = resolvePackagingRecyclability(p),
                biodegradability = resolvePackagingBiodegradability(p)
            )
        }
    }

    val sharedIngredients = remember(assessedList) {
        if (assessedList.size < 2) {
            emptySet()
        } else {
            val sets = assessedList.map { item -> item.ingredientNames.map { it.lowercase(Locale.ROOT) }.toSet() }
            sets.reduce { acc, set -> acc.intersect(set) }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .subtleCardShadow(borderRadius = 28.dp)
            .animateContentSize(animationSpec = tween(320, easing = EaseInOutCubic)),
        shape = RoundedCornerShape(28.dp),
        color = boxColor,
        border = boxBorder
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Nutritional Matrix",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Standard Compliance Status Row (FEDIAF or AAFCO)
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = "${selectedStandard.displayName} Standard Status",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.height(5.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(tween(300, easing = EaseInOutCubic)),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    assessedList.forEach { item ->
                        val (statusText, tone) = if (selectedStandard == AssessmentStandard.FEDIAF) {
                            when {
                                item.fediaf.isTreatOrComplementary -> "Treat" to ComparisonPillTone.NEUTRAL
                                item.fediaf.overallStatus == NutritionStatus.MEETS -> "Meets" to ComparisonPillTone.GREEN
                                item.fediaf.overallStatus == NutritionStatus.BELOW -> "Below Min" to ComparisonPillTone.AMBER
                                item.fediaf.overallStatus == NutritionStatus.ABOVE_MAXIMUM -> "Above Limit" to ComparisonPillTone.RED
                                else -> "Partial" to ComparisonPillTone.NEUTRAL
                            }
                        } else {
                            when {
                                item.aafco.isTreatOrComplementary -> "Treat" to ComparisonPillTone.NEUTRAL
                                item.aafco.overallStatus == AafcoResultStatus.MEETS_REFERENCE -> "Meets" to ComparisonPillTone.GREEN
                                item.aafco.overallStatus == AafcoResultStatus.BELOW_REFERENCE -> "Below Min" to ComparisonPillTone.AMBER
                                item.aafco.overallStatus == AafcoResultStatus.ABOVE_MAXIMUM -> "Above Limit" to ComparisonPillTone.RED
                                else -> "Partial" to ComparisonPillTone.NEUTRAL
                            }
                        }
                        ComparisonValuePill(
                            text = statusText,
                            tone = tone,
                            isDarkMode = isDarkMode,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Product Details (Brand & Life Stage)
            MatrixCategoryHeader("Product Details")
            Spacer(modifier = Modifier.height(10.dp))

            Column(modifier = Modifier.padding(vertical = 5.dp)) {
                Text(
                    text = "Brand",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.height(5.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    assessedList.forEach { item ->
                        ComparisonValuePill(
                            text = item.product.brands.ifBlank { "Not Specified" },
                            tone = ComparisonPillTone.NEUTRAL,
                            isDarkMode = isDarkMode,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(vertical = 5.dp)) {
                Text(
                    text = "Life Stage",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.height(5.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    assessedList.forEach { item ->
                        ComparisonValuePill(
                            text = item.resolvedLifeStage,
                            tone = ComparisonPillTone.NEUTRAL,
                            isDarkMode = isDarkMode,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Nutrients Breakdown (Target-compliance highlighting across all 3 bases)
            MatrixCategoryHeader("Nutrient Breakdown (${basis.headerLabel})")
            Spacer(modifier = Modifier.height(10.dp))

            val nutrientRows = listOf(
                "Crude Protein" to listOf("crudeProtein", "protein", "proteins", "crude_protein"),
                "Crude Fat" to listOf("crudeFat", "fat", "fats", "crude_fat"),
                "Calcium" to listOf("calcium", "ca"),
                "Phosphorus" to listOf("phosphorus", "p"),
                "Taurine" to listOf("taurine"),
                "Omega-3" to listOf("omega3", "omega_3", "omega-3", "alphaLinolenicAcid", "epaDha"),
                "Omega-6" to listOf("omega6", "omega_6", "omega-6", "linoleicAcid"),
                "Sodium" to listOf("sodium", "na"),
                "Crude Fiber" to listOf("crudeFiber", "fiber", "fibers", "crude_fiber"),
                "Crude Ash" to listOf("ash", "crudeAsh", "crude_ash")
            )

            nutrientRows.forEach { (label, aliases) ->
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(tween(300, easing = EaseInOutCubic)),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        assessedList.forEach { item ->
                            val cell = resolveNutrientCell(item, selectedStandard, basis, aliases)
                            ComparisonValuePill(
                                text = cell.displayText,
                                tone = cell.tone,
                                isDarkMode = isDarkMode,
                                badgeText = cell.badgeText,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Estimated Carbohydrates (NFE) Row
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Text(
                    text = "Estimated Carbs (NFE)",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    assessedList.forEach { item ->
                        val carbs = NutritionEnergyHelper.calculateEstimatedCarbsNfe(item.product)
                        val meDm = item.fediaf.energyResult.dryMatterKcalPerKg
                        val text = when {
                            carbs == null -> "-"
                            basis == CompareNutrientBasis.AS_FED -> String.format(Locale.US, "%.1f%%", carbs.asFedCarbsPercent)
                            basis == CompareNutrientBasis.DRY_MATTER -> carbs.dryMatterCarbsPercent?.let {
                                String.format(Locale.US, "%.1f%%", it)
                            } ?: String.format(Locale.US, "%.1f%% (AF)", carbs.asFedCarbsPercent)
                            else -> {
                                val pair = NutritionEnergyHelper.calculatePer1000Kcal(carbs.dryMatterCarbsPercent, "%", meDm)
                                pair?.let { String.format(Locale.US, "%.1f g", it.first) } ?: "-"
                            }
                        }
                        ComparisonValuePill(
                            text = text,
                            tone = ComparisonPillTone.NEUTRAL,
                            isDarkMode = isDarkMode,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Ca : P Ratio Row
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Text(
                    text = "Ca : P Ratio",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    assessedList.forEach { item ->
                        val (ratioText, tone, badge) = if (selectedStandard == AssessmentStandard.FEDIAF) {
                            val txt = item.fediaf.caRatio?.let { String.format(Locale.US, "%.2f : 1", it) } ?: "-"
                            val t = when (item.fediaf.caRatioStatus) {
                                NutritionStatus.MEETS -> ComparisonPillTone.GREEN to "Balanced"
                                NutritionStatus.BELOW -> ComparisonPillTone.AMBER to "Low"
                                NutritionStatus.ABOVE_MAXIMUM -> ComparisonPillTone.RED to "High"
                                else -> ComparisonPillTone.NEUTRAL to null
                            }
                            Triple(txt, t.first, t.second)
                        } else {
                            val txt = item.aafco.caPRatio?.let { String.format(Locale.US, "%.2f : 1", it) } ?: "-"
                            val t = when (item.aafco.caPRatioStatus) {
                                AafcoResultStatus.MEETS_REFERENCE -> ComparisonPillTone.GREEN to "Balanced"
                                AafcoResultStatus.BELOW_REFERENCE -> ComparisonPillTone.AMBER to "Low"
                                AafcoResultStatus.ABOVE_MAXIMUM -> ComparisonPillTone.RED to "High"
                                else -> ComparisonPillTone.NEUTRAL to null
                            }
                            Triple(txt, t.first, t.second)
                        }
                        ComparisonValuePill(
                            text = ratioText,
                            tone = tone,
                            isDarkMode = isDarkMode,
                            badgeText = badge,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Metabolizable Energy (kcal/kg) Row
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                val energyIsDm = basis != CompareNutrientBasis.AS_FED
                Text(
                    text = "Metabolizable Energy (${if (energyIsDm) "kcal/kg DM" else "kcal/kg As-Fed"})",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    assessedList.forEach { item ->
                        val er = item.fediaf.energyResult
                        val kcal = if (energyIsDm) er.dryMatterKcalPerKg else er.asFedKcalPerKg
                        val text = when {
                            kcal != null && kcal.isFinite() -> "${kcal.roundToInt()} kcal${if (er.isEstimated) " (est.)" else ""}"
                            er.source == EnergySource.NON_CONVERTIBLE_UNIT -> er.rawUnit ?: "Serving"
                            else -> "-"
                        }
                        ComparisonValuePill(
                            text = text,
                            tone = if (er.source == EnergySource.LABEL_DECLARED && kcal != null) ComparisonPillTone.GREEN else ComparisonPillTone.NEUTRAL,
                            isDarkMode = isDarkMode,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Key Ingredients, Primary Protein, Shared vs Unique, and Additive Alerts
            MatrixCategoryHeader("Key Ingredients & Quality Diff")
            Spacer(modifier = Modifier.height(10.dp))

            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = "Primary Protein Source",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.height(5.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    assessedList.forEach { item ->
                        ComparisonValuePill(
                            text = item.primaryProtein,
                            tone = if (item.ingredientQuality.hasNamedAnimalProteinInTop3) ComparisonPillTone.GREEN else ComparisonPillTone.NEUTRAL,
                            isDarkMode = isDarkMode,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(tween(300, easing = EaseInOutCubic)),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                assessedList.forEach { item ->
                    val uniqueNames = item.ingredientNames.filter { it.lowercase(Locale.ROOT) !in sharedIngredients }
                    val additiveAlerts = buildList {
                        if (item.ingredientQuality.syntheticAdditiveCount > 0) {
                            add("${item.ingredientQuality.syntheticAdditiveCount} synthetic additive(s)")
                        }
                        if (item.ingredientQuality.unnamedByProductCount > 0) {
                            add("${item.ingredientQuality.unnamedByProductCount} unnamed by-product(s)")
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val ingredientsPreview = item.product.ingredientsText
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .take(4)
                                .joinToString("\n• ", prefix = "• ")
                                .ifEmpty { "Ingredients not specified" }

                            Text(
                                text = ingredientsPreview,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (assessedList.size >= 2 && item.ingredientNames.isNotEmpty()) {
                                val sharedCount = item.ingredientNames.count { it.lowercase(Locale.ROOT) in sharedIngredients }
                                Text(
                                    text = "$sharedCount shared • ${uniqueNames.size} unique",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF479C73)
                                )
                            }

                            if (additiveAlerts.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFE53935).copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "Alert: ${additiveAlerts.joinToString(", ")}",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE53935)
                                    )
                                }
                            } else if (item.ingredientQuality.totalCount > 0) {
                                Text(
                                    text = "No synthetic additives flagged",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Species-matched Allergen Warning inside Ingredients Section
                            if (item.allergenMatches.isNotEmpty()) {
                                val warningText = item.allergenMatches.joinToString("; ") { match ->
                                    "${match.petName}: ${match.allergens.joinToString(", ")}"
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFE53935).copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Warning,
                                            contentDescription = null,
                                            tint = Color(0xFFE53935),
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = "Allergen ($warningText)",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE53935)
                                        )
                                    }
                                }
                            } else if (item.hasSpeciesAllergyProfiles) {
                                Text(
                                    text = "No recorded ${item.productSpecies?.lowercase(Locale.ROOT)?.plus(" ") ?: ""}allergens",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF479C73)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Eco-Friendliness (placed below Ingredients section)
            MatrixCategoryHeader("Eco-Friendliness")
            Spacer(modifier = Modifier.height(10.dp))

            Column(modifier = Modifier.padding(vertical = 5.dp)) {
                Text(
                    text = "Packaging Material",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.height(5.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    assessedList.forEach { item ->
                        val matLower = item.packagingMaterial.lowercase(Locale.ROOT)
                        val isEcoMaterial = matLower.contains("paper") || matLower.contains("cardboard") || matLower.contains("glass") || matLower.contains("metal") || matLower.contains("can")
                        ComparisonValuePill(
                            text = item.packagingMaterial,
                            tone = if (isEcoMaterial) ComparisonPillTone.GREEN else ComparisonPillTone.NEUTRAL,
                            isDarkMode = isDarkMode,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(vertical = 5.dp)) {
                Text(
                    text = "Recyclability",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.height(5.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    assessedList.forEach { item ->
                        val isRecyclable = item.recyclability.equals("Recyclable", ignoreCase = true)
                        ComparisonValuePill(
                            text = item.recyclability,
                            tone = if (isRecyclable) ComparisonPillTone.GREEN else ComparisonPillTone.NEUTRAL,
                            isDarkMode = isDarkMode,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(vertical = 5.dp)) {
                Text(
                    text = "Biodegradability",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.height(5.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    assessedList.forEach { item ->
                        val isBiodegradable = item.biodegradability.equals("Biodegradable", ignoreCase = true)
                        ComparisonValuePill(
                            text = item.biodegradability,
                            tone = if (isBiodegradable) ComparisonPillTone.GREEN else ComparisonPillTone.NEUTRAL,
                            isDarkMode = isDarkMode,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareProductPickerDialog(
    viewModel: AppViewModel,
    alreadySelectedBarcodes: Set<String>,
    activePet: Pet?,
    onDismiss: () -> Unit,
    onSelectProduct: (Product) -> Unit
) {
    val savedProducts by viewModel.savedProducts.collectAsState()
    val recentProducts by viewModel.recentlyViewed.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf("All") }
    var catalogResults by remember { mutableStateOf<List<Product>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        isSearching = true
        delay(220)
        catalogResults = viewModel.searchCatalogProducts(
            query = searchQuery,
            speciesFilter = activePet?.type
        )
        isSearching = false
    }

    val displayedProducts = remember(selectedSource, searchQuery, savedProducts, recentProducts, catalogResults, alreadySelectedBarcodes) {
        val base = when (selectedSource) {
            "Saved" -> savedProducts
            "Recent" -> recentProducts
            "Catalog" -> catalogResults
            else -> (savedProducts + recentProducts + catalogResults)
        }
        base
            .distinctBy { it.barcode.ifBlank { it.productId } }
            .filter { p ->
                val code = p.barcode.ifBlank { p.productId }
                val notAlreadyAdded = code.isNotBlank() && !alreadySelectedBarcodes.contains(code)
                val matchesQuery = searchQuery.isBlank() ||
                    p.productName.contains(searchQuery, ignoreCase = true) ||
                    p.brands.contains(searchQuery, ignoreCase = true) ||
                    code.contains(searchQuery, ignoreCase = true)
                notAlreadyAdded && matchesQuery
            }
            .take(30)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.78f)
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Add Product to Compare",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Choose from Saved, Recent, or Catalog",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search product", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(100.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("All", "Saved", "Recent", "Catalog").forEach { source ->
                            val isSelected = selectedSource == source
                            Surface(
                                modifier = Modifier.clickable { selectedSource = source },
                                shape = RoundedCornerShape(100.dp),
                                color = if (isSelected) Color(0xFF479C73) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            ) {
                                Text(
                                    text = source,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (isSearching && displayedProducts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF479C73))
                        }
                    } else if (displayedProducts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No matching products available to add.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(displayedProducts, key = { it.barcode.ifBlank { it.productId } }) { item ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .bouncyClickable { onSelectProduct(item) },
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(48.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surface
                                        ) {
                                            if (!item.imageUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = item.imageUrl,
                                                    contentDescription = item.productName,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_barcode),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(20.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.productName.ifBlank { "Pet Food" },
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = listOfNotNull(
                                                    item.brands.takeIf { it.isNotBlank() },
                                                    item.species.ifBlank { item.pet }.takeIf { it.isNotBlank() }
                                                ).joinToString(" • ").ifBlank { item.barcode },
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(100.dp),
                                            color = Color(0xFF479C73).copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "+ Add",
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF479C73)
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

fun extractNutrientVal(assessment: NutritionAssessment, key: String, isDryMatter: Boolean): Double? {
    val found = assessment.nutrientAssessments.firstOrNull {
        it.nutrientKey.contains(key, ignoreCase = true) || it.name.contains(key, ignoreCase = true)
    }
    return if (isDryMatter) found?.dryMatterValue ?: found?.asFedValue else found?.asFedValue
}

@Composable
fun MatrixCategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp
        )
    )
}

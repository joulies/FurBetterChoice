package com.example.furbetterchoice.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import com.example.furbetterchoice.api.Product
import com.example.furbetterchoice.ui.theme.*
import kotlinx.coroutines.launch

sealed class DeleteType {
    data class Single(val product: Product) : DeleteType()
    object Selected : DeleteType()
}

@Composable
fun FilterPillButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.bouncyClickable(onClick = onClick),
        shape = RoundedCornerShape(100.dp),
        color = if (isSelected) Color(0xFF479C73) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(
            1.dp,
            if (isSelected) Color(0xFF479C73) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
fun SavedFilterDialog(
    selectedSpeciesSet: Set<String>,
    selectedLifeStageSet: Set<String>,
    selectedBrandSet: Set<String>,
    availableBrands: List<String>,
    onDismiss: () -> Unit,
    onApply: (species: Set<String>, lifeStage: Set<String>, brand: Set<String>) -> Unit,
    onReset: () -> Unit
) {
    var tempSpeciesSet by remember { mutableStateOf(selectedSpeciesSet) }
    var tempLifeStageSet by remember { mutableStateOf(selectedLifeStageSet) }
    var tempBrandSet by remember { mutableStateOf(selectedBrandSet) }

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
                modifier = Modifier.padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filter Products",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
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

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Pet (Species) Section
                    Column {
                        Text(
                            text = "Pet (Species)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Dog", "Cat").forEach { species ->
                                val isSelected = tempSpeciesSet.contains(species)
                                FilterPillButton(
                                    label = species,
                                    isSelected = isSelected,
                                    onClick = {
                                        tempSpeciesSet = if (isSelected) tempSpeciesSet - species else tempSpeciesSet + species
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Life Stage Section
                    Column {
                        Text(
                            text = "Life Stage",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            listOf("Puppy/Kitten", "Adult", "Senior").forEach { stage ->
                                val isSelected = tempLifeStageSet.contains(stage)
                                FilterPillButton(
                                    label = stage,
                                    isSelected = isSelected,
                                    onClick = {
                                        tempLifeStageSet = if (isSelected) tempLifeStageSet - stage else tempLifeStageSet + stage
                                    }
                                )
                            }
                        }
                    }

                    if (availableBrands.isNotEmpty()) {
                        HorizontalDivider()

                        // Brand Section
                        Column {
                            Text(
                                text = "Brand",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                availableBrands.forEach { brand ->
                                    val isSelected = tempBrandSet.contains(brand)
                                    FilterPillButton(
                                        label = brand,
                                        isSelected = isSelected,
                                        onClick = {
                                            tempBrandSet = if (isSelected) tempBrandSet - brand else tempBrandSet + brand
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            tempSpeciesSet = emptySet()
                            tempLifeStageSet = emptySet()
                            tempBrandSet = emptySet()
                            onReset()
                        }
                    ) {
                        Text(
                            text = "Reset All",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            onApply(tempSpeciesSet, tempLifeStageSet, tempBrandSet)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF479C73)),
                        shape = RoundedCornerShape(100.dp)
                    ) {
                        Text(
                            text = "Apply Filters",
                            fontSize = 12.5.sp,
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
fun EmptySavedView(
    isUserLoggedIn: Boolean = true,
    onNavigateToLogin: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color(0xFF7A1129)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isUserLoggedIn) "Your list is empty" else "Sign in to save products",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isUserLoggedIn) "Products you save will appear here" else "Keep track of your favorite pet foods across devices.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        if (!isUserLoggedIn) {
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onNavigateToLogin,
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Log In or Sign Up", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SavedProductCard(
    product: Product,
    isSelectionMode: Boolean = false,
    isCompareSelectMode: Boolean = false,
    isSelected: Boolean = false,
    isAlreadyCompared: Boolean = false,
    jiggleAngleProvider: () -> Float = { 0f },
    boxColor: Color,
    boxBorder: BorderStroke,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onCompareClick: () -> Unit = {}
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected || (isCompareSelectMode && isAlreadyCompared)) Color(0xFF479C73).copy(alpha = 0.18f) else boxColor,
        animationSpec = tween(300, easing = EaseInOutCubic),
        label = "bgColor"
    )
    
    val currentBorder = if (isSelected || isAlreadyCompared) {
        BorderStroke(2.dp, Color(0xFF479C73))
    } else {
        boxBorder
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .subtleCardShadow(borderRadius = 32.dp)
            .graphicsLayer {
                rotationZ = if (isSelectionMode) jiggleAngleProvider() else 0f
            }
            .pointerInput(product.barcode.ifEmpty { product.productId }) {
                detectTapGestures(
                    onLongPress = { currentOnLongClick() },
                    onTap = { currentOnClick() }
                )
            },
        shape = RoundedCornerShape(32.dp),
        color = backgroundColor,
        border = currentBorder
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!product.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = product.imageUrl,
                    contentDescription = product.productName,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_barcode),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    product.productName.ifEmpty { "Unknown Product" }, 
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    product.brands.ifEmpty { "Unknown Brand" }, 
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isCompareSelectMode || isAlreadyCompared || !isSelectionMode) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                    if (isCompareSelectMode) {
                        Surface(
                            modifier = Modifier.bouncyClickable { onCompareClick() },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isAlreadyCompared) Color(0xFF479C73) else Color(0xFF479C73).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFF479C73).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = if (isAlreadyCompared) "Selected • Tap to Unselect" else "Tap to Select",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isAlreadyCompared) Color.White else Color(0xFF479C73)
                            )
                        }
                    } else if (isAlreadyCompared) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF479C73).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFF479C73).copy(alpha = 0.4f))
                        ) {
                            Text(
                                "In Comparison",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF479C73)
                            )
                        }
                    } else {
                        Surface(
                            modifier = Modifier
                                .bouncyClickable { onCompareClick() },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF479C73).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFF479C73).copy(alpha = 0.4f))
                        ) {
                            Text(
                                "Compare",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(
    viewModel: AppViewModel,
    compareBarcodesParam: String? = null,
    onBack: () -> Unit = {},
    onProductClick: (String) -> Unit = {},
    onCompareClick: (String) -> Unit = {},
    onNavigateToLogin: () -> Unit = {}
) {
    val savedProducts by viewModel.savedProducts.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val isUserLoggedIn by viewModel.isUserLoggedIn.collectAsState()
    
    val boxColor = if (isDarkMode) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else Color(0xFFE4EAE8)
    val highlightColor = if (isDarkMode) MaterialTheme.colorScheme.outline.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.6f)
    val boxBorder = BorderStroke(1.dp, Brush.horizontalGradient(listOf(boxColor, highlightColor, boxColor)))

    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()

    val isCompareSelectMode = compareBarcodesParam != null
    val initialCompareList = remember(compareBarcodesParam) {
        (compareBarcodesParam ?: "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
    var compareSelectionList by remember(compareBarcodesParam) {
        mutableStateOf(initialCompareList)
    }
    var showCompareScanner by remember { mutableStateOf(false) }

    fun toggleCompareItem(code: String) {
        if (code.isBlank()) return
        compareSelectionList = if (compareSelectionList.contains(code)) {
            compareSelectionList.filter { it != code }
        } else if (compareSelectionList.size < 3) {
            compareSelectionList + code
        } else {
            compareSelectionList
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedBarcodes by remember { mutableStateOf(setOf<String>()) }
    
    var showDeleteConfirmation by remember { mutableStateOf<DeleteType?>(null) }

    var showFilterDialog by remember { mutableStateOf(false) }
    var selectedSpeciesSet by remember { mutableStateOf(setOf<String>()) }
    var selectedLifeStageSet by remember { mutableStateOf(setOf<String>()) }
    var selectedBrandSet by remember { mutableStateOf(setOf<String>()) }

    val jiggleAngleProvider: () -> Float = if (isSelectionMode) {
        val infiniteTransition = rememberInfiniteTransition(label = "jiggle")
        val jiggleState = infiniteTransition.animateFloat(
            initialValue = -1.5f,
            targetValue = 1.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(120, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "jiggleAngle"
        )
        val provider: () -> Float = { jiggleState.value }
        provider
    } else {
        { 0f }
    }

    val availableBrands = remember(savedProducts) {
        savedProducts.mapNotNull { it.brands.takeIf { b -> b.isNotBlank() } }.flatMap { it.split(",") }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(10)
    }

    val filteredProducts = remember(savedProducts, searchQuery, selectedSpeciesSet, selectedLifeStageSet, selectedBrandSet) {
        savedProducts.filter { product ->
            val pName = product.productName ?: ""
            val pBrands = product.brands ?: ""
            val matchesSearch = searchQuery.isBlank() ||
                pName.contains(searchQuery, ignoreCase = true) ||
                pBrands.contains(searchQuery, ignoreCase = true)

            val species = ((product.pet ?: "").ifEmpty { product.species ?: "" }).lowercase()
            val tags = product.categoriesTags?.map { (it ?: "").lowercase() } ?: emptyList()

            val matchesSpecies = if (selectedSpeciesSet.isEmpty()) true else selectedSpeciesSet.any { selected ->
                val sLower = selected.lowercase()
                species.contains(sLower) || tags.any { it.contains(sLower) }
            }

            val matchesBrand = if (selectedBrandSet.isEmpty()) true else selectedBrandSet.any { brand ->
                pBrands.contains(brand, ignoreCase = true)
            }

            val lifeStage = (product.lifeStage ?: "").lowercase()
            val matchesLifeStage = if (selectedLifeStageSet.isEmpty()) true else selectedLifeStageSet.any { stage ->
                when (stage) {
                    "Puppy/Kitten" -> lifeStage.contains("puppy") || lifeStage.contains("kitten") || lifeStage.contains("junior")
                    "Adult" -> lifeStage.contains("adult")
                    "Senior" -> lifeStage.contains("senior")
                    else -> true
                }
            }

            matchesSearch && matchesSpecies && matchesBrand && matchesLifeStage
        }
    }

    val showHeader = true
    val showSearch = true
    val showList = true

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
                .padding(top = 16.dp)
        ) {
            StaggeredReveal(visible = showHeader) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CustomBackButton(onClick = onBack)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = when {
                                isCompareSelectMode -> "Select Product to Compare"
                                isSelectionMode -> "${selectedBarcodes.size} Selected"
                                else -> "Saved Products"
                            },
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 20.sp
                            )
                        )
                    }

                    if (savedProducts.isNotEmpty() && !isCompareSelectMode) {
                        if (!isSelectionMode) {
                            IconButton(onClick = { isSelectionMode = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check_rectangle),
                                    contentDescription = "Select Mode",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        if (selectedBarcodes.size == filteredProducts.size) {
                                            selectedBarcodes = emptySet()
                                            isSelectionMode = false
                                        } else {
                                            selectedBarcodes = filteredProducts.mapNotNull { it.barcode.ifEmpty { it.productId } }.toSet()
                                        }
                                    },
                                    shape = RoundedCornerShape(100.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(
                                        text = if (selectedBarcodes.size == filteredProducts.size) "Deselect All" else "Select All",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Button(
                                    onClick = {
                                        isSelectionMode = false
                                        selectedBarcodes = emptySet()
                                    },
                                    shape = RoundedCornerShape(100.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(
                                        text = "Cancel",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Search Bar & Filter Button
            StaggeredReveal(visible = showSearch) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    if (!isSelectionMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .subtleCardShadow(borderRadius = 100.dp),
                                shape = RoundedCornerShape(100.dp),
                                color = boxColor,
                                border = boxBorder
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                    placeholder = { Text("Search saved products...", fontSize = 12.5.sp) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                    shape = RoundedCornerShape(100.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedBorderColor = Color.Transparent
                                    ),
                                    singleLine = true
                                )
                            }

                            val hasActiveFilters = selectedSpeciesSet.isNotEmpty() || selectedLifeStageSet.isNotEmpty() || selectedBrandSet.isNotEmpty()

                            Surface(
                                modifier = Modifier
                                    .size(52.dp)
                                    .subtleCardShadow(borderRadius = 100.dp)
                                    .bouncyClickable { showFilterDialog = true },
                                shape = CircleShape,
                                color = if (hasActiveFilters) Color(0xFF479C73).copy(alpha = 0.15f) else boxColor,
                                border = if (hasActiveFilters) BorderStroke(1.dp, Color(0xFF479C73)) else boxBorder
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_filter),
                                        contentDescription = "Filter",
                                        tint = if (hasActiveFilters) Color(0xFF479C73) else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(100.dp),
                            color = boxColor,
                            border = boxBorder
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (selectedBarcodes.size in 2..3) {
                                    Button(
                                        onClick = {
                                            val param = selectedBarcodes.joinToString(",")
                                            onCompareClick(param)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF479C73)),
                                        shape = RoundedCornerShape(100.dp),
                                        modifier = Modifier.height(40.dp)
                                    ) {
                                        Text("Compare (${selectedBarcodes.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(1.dp))
                                }

                                Button(
                                    onClick = { showDeleteConfirmation = DeleteType.Selected },
                                    enabled = selectedBarcodes.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7A1129)),
                                    shape = RoundedCornerShape(100.dp),
                                    modifier = Modifier.height(40.dp)
                                ) {
                                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete (${selectedBarcodes.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isCompareSelectMode) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF479C73).copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, Color(0xFF479C73).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Select or Unselect (${compareSelectionList.size}/3)",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF479C73)
                                )
                            )
                            Text(
                                text = "Tap saved items to toggle, or scan a barcode to compare.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Surface(
                            modifier = Modifier.bouncyClickable {
                                if (compareSelectionList.size < 3) {
                                    showCompareScanner = true
                                }
                            },
                            shape = RoundedCornerShape(100.dp),
                            color = if (compareSelectionList.size < 3) Color(0xFF479C73) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_barcode),
                                    contentDescription = "Scan Product",
                                    tint = if (compareSelectionList.size < 3) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(width = 18.dp, height = 13.dp)
                                )
                                Text(
                                    text = "Scan",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (compareSelectionList.size < 3) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    coroutineScope.launch {
                        isRefreshing = true
                        viewModel.refreshSavedProductsData()
                        isRefreshing = false
                    }
                },
                state = pullToRefreshState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                indicator = {
                    androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                        state = pullToRefreshState,
                        isRefreshing = isRefreshing,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .zIndex(100f),
                        color = Color(0xFF479C73),
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                }
            ) {
                StaggeredReveal(visible = showList) {
                    if (savedProducts.isEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 60.dp)
                        ) {
                            item {
                                EmptySavedView(
                                    isUserLoggedIn = isUserLoggedIn,
                                    onNavigateToLogin = onNavigateToLogin
                                )
                            }
                        }
                    } else if (filteredProducts.isEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 120.dp)
                        ) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text("No products matching filters", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 130.dp)
                        ) {
                            items(
                                items = filteredProducts,
                                key = { it.barcode.ifEmpty { it.productId } },
                                contentType = { "saved_product_card" }
                            ) { product ->
                                val code = product.barcode.ifEmpty { product.productId }
                                val isSelected = selectedBarcodes.contains(code)
                                val isAlreadyCompared = if (isCompareSelectMode) {
                                    compareSelectionList.contains(code)
                                } else {
                                    initialCompareList.contains(code)
                                }

                                SavedProductCard(
                                    product = product,
                                    isSelectionMode = isSelectionMode,
                                    isCompareSelectMode = isCompareSelectMode,
                                    isSelected = isSelected,
                                    isAlreadyCompared = isAlreadyCompared,
                                    jiggleAngleProvider = jiggleAngleProvider,
                                    boxColor = boxColor,
                                    boxBorder = boxBorder,
                                    onClick = {
                                        if (isCompareSelectMode) {
                                            toggleCompareItem(code)
                                        } else if (isSelectionMode) {
                                            val currentSelected = selectedBarcodes.contains(code)
                                            val newSet = if (currentSelected) selectedBarcodes - code else selectedBarcodes + code
                                            selectedBarcodes = newSet
                                            if (newSet.isEmpty()) {
                                                isSelectionMode = false
                                            }
                                        } else {
                                            onProductClick(code)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isCompareSelectMode) {
                                            isSelectionMode = true
                                            selectedBarcodes = selectedBarcodes + code
                                        }
                                    },
                                    onCompareClick = {
                                        if (isCompareSelectMode) {
                                            toggleCompareItem(code)
                                        } else {
                                            onCompareClick(code)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Confirmation Dialogs
            when (val currentDeleteType = showDeleteConfirmation) {
                is DeleteType.Single -> {
                    CustomConfirmationDialog(
                        title = "Remove from Saved",
                        message = "Are you sure you want to remove this product?",
                        confirmText = "Remove",
                        icon = Icons.Default.Delete,
                        onDismiss = { showDeleteConfirmation = null },
                        onConfirm = {
                            viewModel.removeSavedProduct(currentDeleteType.product.barcode ?: "")
                            showDeleteConfirmation = null
                        }
                    )
                }
                is DeleteType.Selected -> {
                    CustomConfirmationDialog(
                        title = "Delete Selected",
                        message = "Remove ${selectedBarcodes.size} products from your saved list?",
                        confirmText = "Remove",
                        icon = Icons.Default.Delete,
                        onDismiss = { showDeleteConfirmation = null },
                        onConfirm = {
                            viewModel.deleteSelectedProducts(selectedBarcodes)
                            selectedBarcodes = emptySet()
                            isSelectionMode = false
                            showDeleteConfirmation = null
                        }
                    )
                }
                null -> {}
            }
        }

        AnimatedVisibility(
            visible = isCompareSelectMode,
            enter = slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = tween(280, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(240)),
            exit = slideOutVertically(
                targetOffsetY = { it / 2 },
                animationSpec = tween(220, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .zIndex(50f)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .subtleCardShadow(borderRadius = 100.dp),
                shape = RoundedCornerShape(100.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, Color(0xFF479C73).copy(alpha = 0.45f)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${compareSelectionList.size} of 3 Selected",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = if (compareSelectionList.isEmpty()) "Select up to 3 products" else "Ready to update comparison",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    Button(
                        onClick = {
                            onCompareClick(compareSelectionList.joinToString(","))
                        },
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF479C73)),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Text(
                            text = if (compareSelectionList.isEmpty()) "Apply" else "Compare (${compareSelectionList.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    if (showCompareScanner) {
        Dialog(
            onDismissRequest = { showCompareScanner = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            ProductScanner(
                onBarcodeScanned = { scannedCode ->
                    showCompareScanner = false
                    val trimmed = scannedCode.trim()
                    if (trimmed.isNotEmpty()) {
                        val updated = (compareSelectionList + trimmed)
                            .filter { it.isNotEmpty() }
                            .distinct()
                            .take(3)
                        compareSelectionList = updated
                        onCompareClick(updated.joinToString(","))
                    }
                },
                onClose = { showCompareScanner = false }
            )
        }
    }

    if (showFilterDialog) {
        SavedFilterDialog(
            selectedSpeciesSet = selectedSpeciesSet,
            selectedLifeStageSet = selectedLifeStageSet,
            selectedBrandSet = selectedBrandSet,
            availableBrands = availableBrands,
            onDismiss = { showFilterDialog = false },
            onApply = { species, lifeStage, brand ->
                selectedSpeciesSet = species
                selectedLifeStageSet = lifeStage
                selectedBrandSet = brand
            },
            onReset = {
                selectedSpeciesSet = emptySet()
                selectedLifeStageSet = emptySet()
                selectedBrandSet = emptySet()
            }
        )
    }
}

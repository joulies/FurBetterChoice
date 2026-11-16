package com.example.furbetterchoice.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.furbetterchoice.R
import com.example.furbetterchoice.api.Product
import com.example.furbetterchoice.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentHistoryScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onProductClick: (String) -> Unit
) {
    val recentlyViewed by viewModel.recentlyViewed.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    
    val boxColor = if (isDarkMode) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else Color(0xFFFFFEFE)
    val highlightColor = if (isDarkMode) MaterialTheme.colorScheme.outline.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.6f)
    val boxBorder = BorderStroke(1.dp, Brush.horizontalGradient(listOf(boxColor, highlightColor, boxColor)))

    val availableCategories = listOf("All", "Dogs", "Cats")
    var selectedCategory by remember { mutableStateOf("All") }

    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()

    val filteredProducts = remember(recentlyViewed, selectedCategory) {
        recentlyViewed.filter { product ->
            when (selectedCategory) {
                "Dogs" -> {
                    val species = (product.pet.ifEmpty { product.species }).lowercase()
                    val tags = product.categoriesTags?.map { it.lowercase() } ?: emptyList()
                    species.contains("dog") || tags.any { it.contains("dog") }
                }
                "Cats" -> {
                    val species = (product.pet.ifEmpty { product.species }).lowercase()
                    val tags = product.categoriesTags?.map { it.lowercase() } ?: emptyList()
                    species.contains("cat") || tags.any { it.contains("cat") }
                }
                else -> true
            }
        }
    }

    // Staggered reveal flags
    var showList by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        showList = true
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
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text("Scan History", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    Box(modifier = Modifier.padding(start = 12.dp)) {
                        CustomBackButton(onClick = onBack)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(52.dp)
                    .subtleCardShadow(borderRadius = 100.dp),
                shape = RoundedCornerShape(100.dp),
                color = boxColor,
                border = boxBorder
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                ) {
                    availableCategories.forEach { category ->
                        val isSelected = selectedCategory == category
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(2.dp),
                            shape = RoundedCornerShape(100.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            border = if (isSelected) null else BorderStroke(1.dp, Brush.horizontalGradient(listOf(Color.Transparent, highlightColor.copy(alpha = 0.3f), Color.Transparent))),
                            onClick = { selectedCategory = category }
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = category,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    coroutineScope.launch {
                        isRefreshing = true
                        viewModel.refreshRecentHistoryData()
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
                if (recentlyViewed.isEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 120.dp)
                    ) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("No scan history yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else if (filteredProducts.isEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 120.dp)
                    ) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("No scanned products found in $selectedCategory", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    StaggeredReveal(visible = showList) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 120.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredProducts, key = { it.barcode.ifEmpty { it.productId } }) { product ->
                                HistoryCard(
                                    product = product,
                                    boxColor = boxColor,
                                    boxBorder = boxBorder,
                                    onClick = { product.barcode?.let { onProductClick(it) } }
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
fun HistoryCard(product: Product, boxColor: Color, boxBorder: BorderStroke, onClick: () -> Unit) {
    val date = remember { SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date()) }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .subtleCardShadow(borderRadius = 32.dp)
            .bouncyClickable(onClick = onClick),
        shape = RoundedCornerShape(32.dp),
        color = boxColor,
        border = boxBorder
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
                    product.productName ?: "Unknown Product", 
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    date, 
                    fontSize = 11.sp, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

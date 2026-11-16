package com.example.furbetterchoice.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.furbetterchoice.R
import com.example.furbetterchoice.api.*
import com.example.furbetterchoice.ui.theme.TextDarkGreen
import kotlinx.coroutines.delay

@Composable
fun SuitableFoodScreen(
    petId: String,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onProductClick: (String) -> Unit
) {
    val pet = viewModel.pets.collectAsState().value.find { it.id == petId }
    val cache by viewModel.recommendationsCache.collectAsState()
    val apiService = remember { PetFoodApiService.create(useGeneralFoodDb = false) }
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    
    val boxColor = if (isDarkMode) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else Color(0xFFFFFEFE)
    val highlightColor = if (isDarkMode) MaterialTheme.colorScheme.outline.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.6f)
    val boxBorder = BorderStroke(1.dp, Brush.horizontalGradient(listOf(boxColor, highlightColor, boxColor)))

    var isLoading by remember { mutableStateOf(cache[petId] == null) }
    var rankedProducts by remember { mutableStateOf(cache[petId] ?: emptyList()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(petId) {
        if (pet == null) {
            errorMsg = "Pet not found"
            isLoading = false
            return@LaunchedEffect
        }

        if (cache[petId] != null) {
            rankedProducts = cache[petId]!!
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true
        delay(1500) // Artificial delay for smooth transition and "AI-like" processing feel
        
        try {
            val species = pet.type
            val category = if (species.lowercase().contains("cat")) "en:cat-food" else "en:dog-food"

            val apiProducts = try {
                val response = apiService.searchProducts(category, pageSize = 20)
                if (response.isSuccessful) response.body()?.products ?: emptyList() else emptyList()
            } catch (e: Exception) {
                emptyList()
            }

            val firestoreProducts = try {
                viewModel.productRepository.getAlternatives(species, null)
            } catch (e: Exception) {
                emptyList()
            }

            val combinedProducts = (apiProducts + firestoreProducts)
                .distinctBy { p -> p.barcode.ifEmpty { p.productId.ifEmpty { p.productName } } }

            val ranked = FoodRecommender.rankProducts(pet, combinedProducts)
                .filter { it.suitabilityScore > 0 }
                .take(15)

            if (ranked.isNotEmpty()) {
                rankedProducts = ranked
                viewModel.setRecommendations(petId, ranked)
            } else {
                errorMsg = "No suitable food recommendations found for ${pet.name}"
            }
            isLoading = false
        } catch (e: Exception) {
            errorMsg = "Failed to load food data"
            isLoading = false
        }
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

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CustomBackButton(onClick = onBack)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        "Food Recommendations",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    pet?.let {
                        Text(
                            "Best choices for ${it.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isLoading) {
                RecommendationLoadingState(pet?.name ?: "Pet")
            } else if (errorMsg != null) {
                ErrorState(errorMsg!!, onBack)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    itemsIndexed(rankedProducts) { index, ranked ->
                        RankedFoodItem(
                            ranked = ranked,
                            index = index,
                            boxColor = boxColor,
                            boxBorder = boxBorder,
                            onClick = { 
                                val targetId = ranked.product.barcode.ifEmpty { ranked.product.productId }
                                if (targetId.isNotEmpty()) {
                                    onProductClick(targetId) 
                                }
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        }
    }
}

@Composable
fun RecommendationLoadingState(petName: String) {
    val messages = listOf(
        "Checking pet profile & allergies...",
        "Evaluating FEDIAF nutritional guidelines for $petName...",
        "Checking Dry Matter protein & fat targets...",
        "Scanning for allergen safety...",
        "Ranking top FEDIAF-compliant choices..."
    )
    var msgIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            msgIndex = (msgIndex + 1) % messages.size
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(scale)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_pets),
                    contentDescription = null,
                    modifier = Modifier.size(50.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Crossfade(targetState = messages[msgIndex], label = "msgFade") { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(horizontal = 40.dp)
                )
            }
        }
    }
}

@Composable
fun RankedFoodItem(
    ranked: RankedProduct,
    index: Int,
    boxColor: Color,
    boxBorder: BorderStroke,
    onClick: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    val offsetY by animateDpAsState(
        targetValue = if (isVisible) 0.dp else 50.dp,
        animationSpec = tween(500, delayMillis = index * 100, easing = EaseOutCubic),
        label = "offset"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(500, delayMillis = index * 100),
        label = "alpha"
    )

    val (rankColor, rankBadgeSize, cardPadding) = when (index) {
        0 -> Triple(Color(0xFFFFD700), 56.dp, 24.dp) // Gold, Huge
        1 -> Triple(Color(0xFFC0C0C0), 48.dp, 20.dp) // Silver, Large
        2 -> Triple(Color(0xFFCD7F32), 44.dp, 18.dp) // Bronze, Medium
        else -> Triple(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), 40.dp, 16.dp) // Gray, Standard
    }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .subtleCardShadow(borderRadius = 32.dp)
            .offset(y = offsetY)
            .graphicsLayer {
                this.alpha = alpha
                clip = false
            }
            .bouncyClickable(onClick = onClick),
        shape = RoundedCornerShape(32.dp),
        color = boxColor,
        border = boxBorder
    ) {
        Row(
            modifier = Modifier.padding(cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank Badge
            Box(
                modifier = Modifier
                    .size(rankBadgeSize)
                    .background(
                        rankColor.copy(alpha = 0.1f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "#${index + 1}",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = rankColor
                    )
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            AsyncImage(
                model = ranked.product.imageUrl.takeIf { !it.isNullOrBlank() } ?: R.drawable.ic_paw,
                contentDescription = null,
                modifier = Modifier
                    .size(if (index == 0) 90.dp else 70.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    ranked.product.productName.ifEmpty { ranked.product.brand.ifEmpty { "Pet Food Product" } },
                    style = if (index == 0) MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                            else MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                Text(
                    ranked.product.brands.ifEmpty { "Recommended Brand" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Star,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${ranked.suitabilityScore}% Match",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                if (ranked.matchReasons.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        ranked.matchReasons.take(2).joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorState(msg: String, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(msg, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onBack) {
                Text("Go Back")
            }
        }
    }
}

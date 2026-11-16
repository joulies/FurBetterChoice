package com.example.furbetterchoice.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.example.furbetterchoice.R
import com.example.furbetterchoice.api.Pet
import com.example.furbetterchoice.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPetsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit = {},
    onFindFood: (String) -> Unit = {},
    onNavigateToLogin: () -> Unit = {}
) {
    val pets by viewModel.pets.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val isUserLoggedIn by viewModel.isUserLoggedIn.collectAsState()
    
    val boxColor = if (isDarkMode) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else Color(0xFFE4EAE8)
    val highlightColor = if (isDarkMode) MaterialTheme.colorScheme.outline.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.6f)
    val boxBorder = BorderStroke(1.dp, Brush.horizontalGradient(listOf(boxColor, highlightColor, boxColor)))

    var selectedCategory by remember { mutableStateOf("All") }
    var selectedPetForProfile by remember { mutableStateOf<Pet?>(null) }
    var selectedPetForEdit by remember { mutableStateOf<Pet?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var showLoginPrompt by remember { mutableStateOf(false) }
    
    var showSuccessOverlay by remember { mutableStateOf(false) }
    var petToDelete by remember { mutableStateOf<Pet?>(null) }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedPetIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()

    val infiniteTransition = rememberInfiniteTransition(label = "jiggle")
    val jiggleAngle by infiniteTransition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(120, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "jiggleAngle"
    )

    val filteredPets = remember(pets, selectedCategory) {
        if (selectedCategory == "All") pets
        else pets.filter { (it.type ?: "").equals(selectedCategory.dropLast(1), ignoreCase = true) }
    }

    // Staggered reveal flags
    var showHeader by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var showAddCTA by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        showHeader = true
        delay(150)
        showFilter = true
        delay(150)
        showAddCTA = true
        delay(150)
        showGrid = true
    }

    LaunchedEffect(showSuccessOverlay) {
        if (showSuccessOverlay) {
            delay(1000)
            showSuccessOverlay = false
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
                        CustomBackButton(onClick = {
                            if (isSelectionMode) {
                                isSelectionMode = false
                                selectedPetIds = emptySet()
                            } else {
                                onBack()
                            }
                        })
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isSelectionMode) "${selectedPetIds.size} Selected" else "My Pets",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 20.sp
                            )
                        )
                    }

                    if (pets.isNotEmpty()) {
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
                                        if (selectedPetIds.size == filteredPets.size) {
                                            selectedPetIds = emptySet()
                                            isSelectionMode = false
                                        } else {
                                            selectedPetIds = filteredPets.map { it.id.ifEmpty { it.petId } }.toSet()
                                        }
                                    },
                                    shape = RoundedCornerShape(100.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(
                                        text = if (selectedPetIds.size == filteredPets.size) "Deselect All" else "Select All",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Button(
                                    onClick = {
                                        isSelectionMode = false
                                        selectedPetIds = emptySet()
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

            // Merged Container Card (Upper: Category Filters, Lower: Add Friend / Delete Pill)
            StaggeredReveal(visible = showFilter || showAddCTA) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .subtleCardShadow(borderRadius = 28.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = boxColor,
                    border = boxBorder
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Upper Row: Category Filters ("All", "Dogs", "Cats")
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(100.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(3.dp)
                            ) {
                                listOf("All", "Dogs", "Cats").forEach { category ->
                                    val isSelected = selectedCategory == category
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        shape = RoundedCornerShape(100.dp),
                                        color = if (isSelected) Color(0xFF479C73) else Color.Transparent,
                                        onClick = { selectedCategory = category }
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = category,
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 12.5.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Lower Row: Add New Fur Friend / Delete Selected Pill
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(100.dp),
                            color = if (isSelectionMode) Color(0xFF7A1129).copy(alpha = 0.15f) else Color(0xFF479C73).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, if (isSelectionMode) Color(0xFF7A1129) else Color(0xFF479C73).copy(alpha = 0.4f)),
                            onClick = {
                                if (isSelectionMode) {
                                    if (selectedPetIds.isNotEmpty()) {
                                        showDeleteConfirmation = true
                                    }
                                } else {
                                    if (isUserLoggedIn) {
                                        isCreatingNew = true
                                    } else {
                                        showLoginPrompt = true
                                    }
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(if (isSelectionMode) Color(0xFF7A1129).copy(alpha = 0.2f) else Color(0xFF479C73).copy(alpha = 0.2f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isSelectionMode) Icons.Default.Delete else Icons.Default.Add,
                                            contentDescription = null,
                                            tint = if (isSelectionMode) Color(0xFF7A1129) else Color(0xFF479C73),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = if (isSelectionMode) "Delete Selected (${selectedPetIds.size})" else "Add New Fur Friend",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isSelectionMode) Color(0xFF7A1129) else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }

                                if (isSelectionMode) {
                                    Button(
                                        onClick = {
                                            if (selectedPetIds.isNotEmpty()) {
                                                showDeleteConfirmation = true
                                            }
                                        },
                                        enabled = selectedPetIds.isNotEmpty(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7A1129)),
                                        shape = RoundedCornerShape(100.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Delete", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    coroutineScope.launch {
                        isRefreshing = true
                        viewModel.refreshPetsData()
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
                StaggeredReveal(visible = showGrid) {
                    if (filteredPets.isEmpty()) {
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 120.dp)
                        ) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text(
                                        "No $selectedCategory found.",
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 120.dp)
                        ) {
                            items(filteredPets, key = { it.id.ifEmpty { it.petId } }) { pet ->
                                val petKey = pet.id.ifEmpty { pet.petId }
                                val isSelected = selectedPetIds.contains(petKey)
                                PetGridCard(
                                    modifier = Modifier.animateItem(),
                                    pet = pet,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = isSelected,
                                    jiggleAngle = jiggleAngle,
                                    boxColor = boxColor,
                                    boxBorder = boxBorder,
                                    onClick = {
                                        if (isSelectionMode) {
                                            val currentSelected = selectedPetIds.contains(petKey)
                                            val newSet = if (currentSelected) selectedPetIds - petKey else selectedPetIds + petKey
                                            selectedPetIds = newSet
                                            if (newSet.isEmpty()) {
                                                isSelectionMode = false
                                            }
                                        } else {
                                            selectedPetForProfile = pet
                                        }
                                    },
                                    onLongClick = {
                                        isSelectionMode = true
                                        selectedPetIds = selectedPetIds + petKey
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Success Overlay
        if (showSuccessOverlay) {
            CelebratoryOverlay(message = "Pet Added Successfully!")
        }
    }

    if (showLoginPrompt) {
        LoginRequiredDialog(
            onDismiss = { showLoginPrompt = false },
            onLoginClick = {
                showLoginPrompt = false
                onNavigateToLogin()
            },
            actionText = "add a pet profile"
        )
    }

    if (isCreatingNew) {
        PetCreationDialog(
            onDismiss = { isCreatingNew = false },
            boxColor = boxColor,
            boxBorder = boxBorder,
            onConfirm = { 
                viewModel.addPet(it)
                isCreatingNew = false
                showSuccessOverlay = true
            }
        )
    }

    if (showSuccessOverlay) {
        CelebratoryOverlay(message = "Pet Added Successfully!")
    }

    if (selectedPetForProfile != null) {
        PetProfileDialog(
            pet = selectedPetForProfile!!,
            boxColor = boxColor,
            boxBorder = boxBorder,
            onDismiss = { selectedPetForProfile = null },
            onEdit = {
                selectedPetForEdit = selectedPetForProfile
                selectedPetForProfile = null
            },
            onDelete = {
                petToDelete = selectedPetForProfile
                selectedPetForProfile = null
            },
            onFindFood = {
                selectedPetForProfile?.id?.let { onFindFood(it) }
                selectedPetForProfile = null
            }
        )
    }

    if (selectedPetForEdit != null) {
        PetCreationDialog(
            existingPet = selectedPetForEdit,
            boxColor = boxColor,
            boxBorder = boxBorder,
            onDismiss = { selectedPetForEdit = null },
            onConfirm = {
                viewModel.updatePet(it)
                selectedPetForEdit = null
            }
        )
    }

    if (petToDelete != null) {
        CustomConfirmationDialog(
            title = "Remove Pet Profile",
            message = "Are you sure you want to remove ${petToDelete?.name}? This action cannot be undone.",
            confirmText = "Remove",
            icon = Icons.Default.Delete,
            onDismiss = { petToDelete = null },
            onConfirm = {
                viewModel.deletePet(petToDelete!!.id)
                petToDelete = null
            }
        )
    }

    if (showDeleteConfirmation) {
        CustomConfirmationDialog(
            title = "Delete Selected Pets",
            message = "Remove ${selectedPetIds.size} selected pets from your list?",
            confirmText = "Remove",
            icon = Icons.Default.Delete,
            onDismiss = { showDeleteConfirmation = false },
            onConfirm = {
                selectedPetIds.forEach { viewModel.deletePet(it) }
                isSelectionMode = false
                selectedPetIds = emptySet()
                showDeleteConfirmation = false
            }
        )
    }
}

@Composable
fun CelebratoryOverlay(message: String) {
    var isVisible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(300),
        label = "alpha"
    )
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

    val isDarkMode = isSystemInDarkTheme()
    val boxColor = if (isDarkMode) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else Color(0xFFE4EAE8)
    val highlightColor = if (isDarkMode) MaterialTheme.colorScheme.outline.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.6f)
    val boxBorder = BorderStroke(1.dp, Brush.horizontalGradient(listOf(boxColor, highlightColor, boxColor)))

    Dialog(
        onDismissRequest = { }, 
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f * alpha)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.7f) // consistent small size
                    .scale(scale)
                    .graphicsLayer(alpha = alpha),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                border = boxBorder, // gradient border
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun PetGridCard(
    modifier: Modifier = Modifier,
    pet: Pet, 
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    jiggleAngle: Float = 0f,
    boxColor: Color,
    boxBorder: BorderStroke,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val isCat = remember(pet) { (pet.type ?: "").equals("Cat", ignoreCase = true) }
    val speciesColor = if (isCat) Color(0xFF7A1129) else Color(0xFF479C73)

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) speciesColor.copy(alpha = 0.22f) else boxColor,
        animationSpec = tween(300),
        label = "bgColor"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .subtleCardShadow(borderRadius = 28.dp)
            .graphicsLayer {
                rotationZ = if (isSelectionMode) jiggleAngle else 0f
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongClick() },
                    onTap = { onClick() }
                )
            },
        shape = RoundedCornerShape(28.dp),
        color = backgroundColor,
        border = if (isSelected) BorderStroke(2.dp, speciesColor) else boxBorder
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Picture area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                if (pet.imageUri != null) {
                    AsyncImage(
                        model = pet.imageUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            pet.name.take(1).uppercase(),
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = speciesColor
                            )
                        )
                    }
                }
                
                // Type badge on card
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopEnd)
                        .background(speciesColor, RoundedCornerShape(100.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(pet.type, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            
            // Footer: Centered Bold Name with Species Color Coding
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = pet.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = speciesColor
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

fun calculateAgeString(birthday: String, ageYears: Long = 0): String {
    if (birthday.isNotBlank()) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val birthDate = sdf.parse(birthday) ?: return if (ageYears > 0) "$ageYears y old" else "Age unknown"
            val birthCal = Calendar.getInstance().apply { time = birthDate }
            val nowCal = Calendar.getInstance()
            
            var years = nowCal.get(Calendar.YEAR) - birthCal.get(Calendar.YEAR)
            var months = nowCal.get(Calendar.MONTH) - birthCal.get(Calendar.MONTH)
            val days = nowCal.get(Calendar.DAY_OF_MONTH) - birthCal.get(Calendar.DAY_OF_MONTH)
            
            if (months < 0 || (months == 0 && days < 0)) {
                years--
                months += 12
            }
            
            return when {
                years > 0 -> "$years y ${months % 12} m old"
                months > 0 -> "$months m old"
                else -> "Newborn"
            }
        } catch (e: Exception) {
        }
    }
    return if (ageYears > 0) "$ageYears y old" else "Age unknown"
}

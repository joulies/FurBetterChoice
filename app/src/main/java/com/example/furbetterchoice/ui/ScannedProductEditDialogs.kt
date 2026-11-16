package com.example.furbetterchoice.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.furbetterchoice.api.CloudinaryUploadService
import com.example.furbetterchoice.api.ContentModerationFilter
import com.example.furbetterchoice.api.LabelAiCorrectionService
import com.example.furbetterchoice.api.NutrientValue
import com.example.furbetterchoice.api.Product
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun EditScannedProductInfoDialog(
    product: Product,
    pendingFrontPhotoUri: String? = null,
    pendingNutritionPhotoUri: String? = null,
    pendingIngredientsPhotoUri: String? = null,
    pendingBarcodePhotoUri: String? = null,
    onDismiss: () -> Unit,
    onApply: (productName: String, brand: String, pet: String, lifeStage: String, foodType: String, imageUrl: String, nutritionPhotoUrl: String, ingredientsPhotoUrl: String, barcodePhotoUrl: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var productName by remember(product) { mutableStateOf(product.productName) }
    var brand by remember(product) { mutableStateOf(product.brands) }
    var pet by remember(product) {
        mutableStateOf(
            when {
                product.species.contains("cat", ignoreCase = true) -> "Cat"
                product.species.contains("dog", ignoreCase = true) -> "Dog"
                else -> product.species.ifBlank { "Dog" }
            }
        )
    }
    var lifeStage by remember(product) { mutableStateOf(product.lifeStage.ifBlank { "Adult" }) }
    var foodType by remember(product) { mutableStateOf(product.foodType.ifBlank { "Dry Food" }) }

    var frontPhotoUrl by remember(product) { mutableStateOf(product.imageUrl.orEmpty()) }
    var nutritionPhotoUrl by remember(product) { mutableStateOf(product.nutritionPhotoUrl.orEmpty()) }
    var ingredientsPhotoUrl by remember(product) { mutableStateOf(product.ingredientsPhotoUrl.orEmpty()) }
    var barcodePhotoUrl by remember(product) { mutableStateOf(product.barcodePhotoUrl.orEmpty()) }

    // Pending local URIs for each slot before Cloudinary upload (pre-seeded with auto-captured URIs if not yet uploaded)
    val pendingLocalUris = remember(
        product,
        pendingFrontPhotoUri,
        pendingNutritionPhotoUri,
        pendingIngredientsPhotoUri,
        pendingBarcodePhotoUri
    ) {
        mutableStateMapOf<String, Uri>().apply {
            if (frontPhotoUrl.isBlank() && !pendingFrontPhotoUri.isNullOrBlank()) {
                put("Front Photo", Uri.parse(pendingFrontPhotoUri))
            }
            if (nutritionPhotoUrl.isBlank() && !pendingNutritionPhotoUri.isNullOrBlank()) {
                put("Nutrition Label", Uri.parse(pendingNutritionPhotoUri))
            }
            if (ingredientsPhotoUrl.isBlank() && !pendingIngredientsPhotoUri.isNullOrBlank()) {
                put("Ingredients List", Uri.parse(pendingIngredientsPhotoUri))
            }
            if (barcodePhotoUrl.isBlank() && !pendingBarcodePhotoUri.isNullOrBlank()) {
                put("Barcode Photo", Uri.parse(pendingBarcodePhotoUri))
            }
        }
    }
    var activeUploadingSlot by remember { mutableStateOf<String?>(null) }
    var isApplyingChanges by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    var pickingSlot by remember { mutableStateOf<String?>(null) }

    val multiPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(4)
    ) { uris ->
        if (uris.isNotEmpty()) {
            uploadError = null
            val slots = listOf("Front Photo", "Nutrition Label", "Ingredients List", "Barcode Photo")
            uris.forEachIndexed { index, uri ->
                val slot = slots.getOrNull(index) ?: return@forEachIndexed
                pendingLocalUris[slot] = uri
            }
        }
    }

    val singlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val slot = pickingSlot
        if (uri != null && slot != null) {
            pendingLocalUris[slot] = uri
            uploadError = null
        }
        pickingSlot = null
    }

    fun uploadSlot(slotName: String) {
        val targetUri = pendingLocalUris[slotName] ?: return
        activeUploadingSlot = slotName
        uploadError = null
        coroutineScope.launch {
            val res = CloudinaryUploadService.uploadImageUri(context, targetUri)
            activeUploadingSlot = null
            res.onSuccess { secureUrl ->
                when (slotName) {
                    "Front Photo" -> frontPhotoUrl = secureUrl
                    "Nutrition Label" -> nutritionPhotoUrl = secureUrl
                    "Ingredients List" -> ingredientsPhotoUrl = secureUrl
                    "Barcode Photo" -> barcodePhotoUrl = secureUrl
                }
                pendingLocalUris.remove(slotName)
            }
            res.onFailure { err ->
                uploadError = "Cloudinary upload failed ($slotName, Max 1MB): ${err.message}"
            }
        }
    }

    fun handleApply() {
        isApplyingChanges = true
        uploadError = null
        coroutineScope.launch {
            var finalFront = frontPhotoUrl
            var finalNutrition = nutritionPhotoUrl
            var finalIngredients = ingredientsPhotoUrl
            var finalBarcode = barcodePhotoUrl
            var hasFailure = false

            // Auto-upload any remaining pending local photos
            for ((slot, uri) in pendingLocalUris.toMap()) {
                val res = CloudinaryUploadService.uploadImageUri(context, uri)
                res.onSuccess { secureUrl ->
                    when (slot) {
                        "Front Photo" -> finalFront = secureUrl
                        "Nutrition Label" -> finalNutrition = secureUrl
                        "Ingredients List" -> finalIngredients = secureUrl
                        "Barcode Photo" -> finalBarcode = secureUrl
                    }
                    pendingLocalUris.remove(slot)
                }.onFailure { err ->
                    hasFailure = true
                    uploadError = "Upload failed ($slot): ${err.message}"
                }
                if (hasFailure) break
            }

            isApplyingChanges = false
            if (hasFailure) return@launch

            frontPhotoUrl = finalFront
            nutritionPhotoUrl = finalNutrition
            ingredientsPhotoUrl = finalIngredients
            barcodePhotoUrl = finalBarcode

            onApply(
                productName.trim(),
                brand.trim(),
                pet.trim(),
                lifeStage.trim(),
                foodType.trim(),
                finalFront.trim(),
                finalNutrition.trim(),
                finalIngredients.trim(),
                finalBarcode.trim()
            )
        }
    }

    val uploadedPhotos = listOfNotNull(
        if (frontPhotoUrl.isNotBlank()) Triple("Front Photo", frontPhotoUrl, "front") else null,
        if (nutritionPhotoUrl.isNotBlank()) Triple("Nutrition Label", nutritionPhotoUrl, "nutrition") else null,
        if (ingredientsPhotoUrl.isNotBlank()) Triple("Ingredients List", ingredientsPhotoUrl, "ingredients") else null,
        if (barcodePhotoUrl.isNotBlank()) Triple("Barcode Photo", barcodePhotoUrl, "barcode") else null
    )
    val uploadedCount = uploadedPhotos.size

    val nameMod = remember(productName) { ContentModerationFilter.analyze(productName) }
    val brandMod = remember(brand) { ContentModerationFilter.analyze(brand) }
    val typeMod = remember(foodType) { ContentModerationFilter.analyze(foodType) }
    val hasBadWords = nameMod.containsBadWords || brandMod.containsBadWords || typeMod.containsBadWords
    val combinedBadWords = remember(nameMod, brandMod, typeMod) {
        (nameMod.matchedWords + brandMod.matchedWords + typeMod.matchedWords).distinct()
    }
    val productNameValidationError = remember(productName) {
        if (productName.isBlank()) null else LabelAiCorrectionService.validateProductOrBrandName(productName, "Product Name")
    }
    val brandValidationError = remember(brand) {
        if (brand.isBlank()) null else LabelAiCorrectionService.validateProductOrBrandName(brand, "Brand Name")
    }

    val lifeStageOptions = remember(pet) {
        val young = if (pet.equals("Cat", ignoreCase = true)) "Kitten" else "Puppy"
        listOf("Adult", young, "Senior", "All Life Stages")
    }
    val foodTypeOptions = listOf("Dry Food", "Wet Food", "Treat")

    val slotsInfo = listOf(
        Triple("Front Photo", frontPhotoUrl, { url: String -> frontPhotoUrl = url }),
        Triple("Nutrition Label", nutritionPhotoUrl, { url: String -> nutritionPhotoUrl = url }),
        Triple("Ingredients List", ingredientsPhotoUrl, { url: String -> ingredientsPhotoUrl = url }),
        Triple("Barcode Photo", barcodePhotoUrl, { url: String -> barcodePhotoUrl = url })
    )

    val hasFrontPhoto = frontPhotoUrl.isNotBlank() || pendingLocalUris["Front Photo"] != null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                            tint = Color(0xFF479C73),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Edit Product Photos & Details",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                if (hasBadWords) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFFFEBEE),
                        border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Warning, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Inappropriate language detected (${combinedBadWords.joinToString(", ")}). Remove highlighted words to apply.",
                                color = Color(0xFFB71C1C),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (!hasFrontPhoto) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFF8E1),
                        border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Warning, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Product Front Photo is required! Max 1MB per photo (up to 3 uploads).",
                                color = Color(0xFFE65100),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // 1. Horizontal Scrollable View of the 3 Photo Slots (Front Photo, Nutrition Label, Ingredients List)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Product Photos (Swipe to view all 4)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF479C73))
                    )
                    Text(
                        text = "$uploadedCount / 4 uploaded",
                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(slotsInfo) { (slotName, currentUrl, setUrl) ->
                        val pendingUri = pendingLocalUris[slotName]
                        val isUploading = activeUploadingSlot == slotName

                        Surface(
                            modifier = Modifier.width(260.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, Color(0xFF479C73).copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (slotName == "Front Photo") "$slotName *" else slotName,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    if (currentUrl.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(100.dp),
                                            color = Color(0xFF2E7D32).copy(alpha = 0.15f)
                                        ) {
                                            Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("Uploaded ✓", fontSize = 9.5.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black.copy(alpha = 0.05f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (pendingUri != null) {
                                        AsyncImage(
                                            model = pendingUri,
                                            contentDescription = "Pending Preview",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Surface(
                                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                                            shape = RoundedCornerShape(100.dp),
                                            color = Color(0xFFFF8F00)
                                        ) {
                                            Text("Preview (Max 1MB)", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    } else if (currentUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = currentUrl,
                                            contentDescription = slotName,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Rounded.Edit, contentDescription = null, tint = Color(0xFF479C73), modifier = Modifier.size(24.dp))
                                            Text("Tap to add photo", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }

                                if (isUploading) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = Color(0xFF479C73))
                                        Text("Uploading (<1MB)...", fontSize = 10.5.sp, color = Color(0xFF479C73))
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            pickingSlot = slotName
                                            singlePickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        enabled = !isUploading && !isApplyingChanges,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(100.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                    ) {
                                        Text(if (pendingUri != null || currentUrl.isNotBlank()) "Change" else "Select", fontSize = 11.sp)
                                    }

                                    if (pendingUri != null) {
                                        Button(
                                            onClick = { uploadSlot(slotName) },
                                            enabled = !isUploading && !isApplyingChanges,
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF479C73)),
                                            modifier = Modifier.weight(1.2f),
                                            shape = RoundedCornerShape(100.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                        ) {
                                            Text("Upload", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    } else if (currentUrl.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = { setUrl("") },
                                            enabled = !isUploading && !isApplyingChanges,
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F).copy(alpha = 0.1f), contentColor = Color(0xFFD32F2F)),
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(100.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                        ) {
                                            Text("Remove", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Bulk Pick Button
                Button(
                    onClick = {
                        multiPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pick Up to 4 Photos at Once", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                if (!uploadError.isNullOrBlank()) {
                    Text(uploadError.orEmpty(), fontSize = 11.5.sp, color = Color(0xFFD32F2F), fontWeight = FontWeight.SemiBold)
                }

                OutlinedTextField(
                    value = productName,
                    onValueChange = { productName = it },
                    label = { Text("Product Name *") },
                    singleLine = true,
                    isError = nameMod.containsBadWords || productName.isBlank() || productNameValidationError != null,
                    supportingText = productNameValidationError?.let { msg -> { Text(msg) } },
                    visualTransformation = ModerationVisualTransformation,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Brand *") },
                    singleLine = true,
                    isError = brandMod.containsBadWords || brandValidationError != null,
                    supportingText = brandValidationError?.let { msg -> { Text(msg) } },
                    visualTransformation = ModerationVisualTransformation,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                Text(
                    text = "Target Pet",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Dog", "Cat").forEach { option ->
                        FilterChip(
                            selected = pet.equals(option, ignoreCase = true),
                            onClick = {
                                pet = option
                                if (option == "Cat" && lifeStage.equals("Puppy", ignoreCase = true)) {
                                    lifeStage = "Kitten"
                                } else if (option == "Dog" && lifeStage.equals("Kitten", ignoreCase = true)) {
                                    lifeStage = "Puppy"
                                }
                            },
                            label = { Text(option) }
                        )
                    }
                }

                Text(
                    text = "Life Stage",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    lifeStageOptions.forEach { option ->
                        FilterChip(
                            selected = lifeStage.equals(option, ignoreCase = true),
                            onClick = { lifeStage = option },
                            label = { Text(option) }
                        )
                    }
                }

                Text(
                    text = "Food Type",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    foodTypeOptions.forEach { option ->
                        FilterChip(
                            selected = foodType.equals(option, ignoreCase = true),
                            onClick = { foodType = option },
                            label = { Text(option) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, enabled = !isApplyingChanges) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { handleApply() },
                        enabled = !hasBadWords &&
                            productName.isNotBlank() &&
                            productNameValidationError == null &&
                            brandValidationError == null &&
                            hasFrontPhoto &&
                            !isApplyingChanges &&
                            activeUploadingSlot == null,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF479C73)),
                        shape = RoundedCornerShape(100.dp)
                    ) {
                        if (isApplyingChanges) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Uploading & Saving...", color = Color.White, fontWeight = FontWeight.Bold)
                        } else {
                            Text("Apply Changes", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private data class NutrientEditFieldSpec(
    val key: String,
    val label: String,
    val maxAllowed: Double = 100.0
)

private val EDITABLE_NUTRIENT_FIELDS = listOf(
    NutrientEditFieldSpec("crudeProtein", "Crude Protein (%)", 100.0),
    NutrientEditFieldSpec("crudeFat", "Crude Fat (%)", 100.0),
    NutrientEditFieldSpec("crudeFiber", "Crude Fiber (%)", 100.0),
    NutrientEditFieldSpec("moisture", "Moisture (%)", 100.0),
    NutrientEditFieldSpec("ash", "Crude Ash (%)", 100.0),
    NutrientEditFieldSpec("calcium", "Calcium (%)", 25.0),
    NutrientEditFieldSpec("phosphorus", "Phosphorus (%)", 25.0),
    NutrientEditFieldSpec("taurine", "Taurine (%)", 15.0),
    NutrientEditFieldSpec("omega3", "Omega-3 (%)", 15.0),
    NutrientEditFieldSpec("omega6", "Omega-6 (%)", 20.0),
    NutrientEditFieldSpec("sodium", "Sodium (%)", 5.0)
)

@Composable
fun EditScannedNutrientsDialog(
    product: Product,
    lowConfidenceKeys: Set<String> = emptySet(),
    focusNutrientKey: String? = null,
    referencePhotoUrl: String? = null,
    onDismiss: () -> Unit,
    onApply: (Map<String, NutrientValue>, Double?, String?) -> Unit
) {
    fun initialTextForKey(key: String): String {
        val nv = product.nutrition[key]
            ?: product.nutrition.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
            ?: return ""
        return if (nv.value > 0.0) {
            if (nv.value % 1.0 == 0.0) nv.value.toInt().toString()
            else String.format(Locale.US, "%.2f", nv.value).trimEnd('0').trimEnd('.')
        } else ""
    }

    val fieldValues = remember(product) {
        mutableStateMapOf<String, String>().apply {
            EDITABLE_NUTRIENT_FIELDS.forEach { spec ->
                this[spec.key] = initialTextForKey(spec.key)
            }
        }
    }
    var meText by remember(product) {
        mutableStateOf(
            product.metabolizableEnergy?.takeIf { it > 0.0 }?.let {
                if (it % 1.0 == 0.0) it.toInt().toString() else String.format(Locale.US, "%.1f", it)
            }.orEmpty()
        )
    }
    var meUnit by remember(product) {
        mutableStateOf(product.metabolizableEnergyUnit?.ifBlank { "kcal/kg" } ?: "kcal/kg")
    }

    val fieldErrors = remember(fieldValues.toMap(), meText) {
        val errors = mutableMapOf<String, String>()
        EDITABLE_NUTRIENT_FIELDS.forEach { spec ->
            val raw = fieldValues[spec.key].orEmpty()
            if (raw.isNotBlank()) {
                val parsed = raw.toDoubleOrNull()
                if (parsed == null || !parsed.isFinite() || parsed <= 0.0 || parsed > spec.maxAllowed) {
                    errors[spec.key] = "Enter a valid number > 0 and <= ${spec.maxAllowed.toInt()}%"
                }
            }
        }
        if (meText.isNotBlank()) {
            val parsedMe = meText.toDoubleOrNull()
            if (parsedMe == null || !parsedMe.isFinite() || parsedMe <= 0.0 || parsedMe > 10000.0) {
                errors["metabolizableEnergy"] = "Enter a valid number (> 0)"
            }
        }
        errors
    }

    val orderedFields = remember(focusNutrientKey) {
        if (focusNutrientKey.isNullOrBlank()) EDITABLE_NUTRIENT_FIELDS
        else EDITABLE_NUTRIENT_FIELDS.sortedByDescending {
            it.key.equals(focusNutrientKey, ignoreCase = true) ||
                it.label.contains(focusNutrientKey, ignoreCase = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.86f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Edit Nutrients (Numbers Only)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Only numeric values (%) are accepted. Leave blank if not on the bag.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (!referencePhotoUrl.isNullOrBlank()) {
                    ReferenceLabelPhotoBanner(
                        photoUrl = referencePhotoUrl,
                        label = "Captured Nutrition Label (tap to zoom)"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    orderedFields.forEach { spec ->
                        val isLowConf = lowConfidenceKeys.contains(spec.key)
                        val isFocused = !focusNutrientKey.isNullOrBlank() &&
                            (spec.key.equals(focusNutrientKey, ignoreCase = true) ||
                                spec.label.contains(focusNutrientKey, ignoreCase = true))
                        val err = fieldErrors[spec.key]

                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (isLowConf) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFFF8E1),
                                    border = BorderStroke(1.dp, Color(0xFFD29524).copy(alpha = 0.5f)),
                                    modifier = Modifier.padding(bottom = 4.dp)
                                ) {
                                    Text(
                                        text = "AI uncertain — please verify against package",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF9A6700),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = fieldValues[spec.key].orEmpty(),
                                onValueChange = { input ->
                                    fieldValues[spec.key] = filterStrictDecimalInput(input)
                                },
                                label = { Text(spec.label) },
                                placeholder = { Text("e.g. 26.5") },
                                singleLine = true,
                                isError = err != null,
                                supportingText = err?.let { msg -> { Text(msg) } },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (isFocused || isLowConf) Color(0xFFD29524) else Color(0xFF479C73),
                                    unfocusedBorderColor = if (isLowConf) Color(0xFFD29524) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = "Metabolizable Energy (Optional)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = meText,
                            onValueChange = { meText = filterStrictDecimalInput(it) },
                            label = { Text("Energy ($meUnit)") },
                            placeholder = { Text("e.g. 3650") },
                            singleLine = true,
                            isError = fieldErrors.containsKey("metabolizableEnergy"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("kcal/kg", "kcal/100g", "kcal/cup").forEach { unitOpt ->
                                    FilterChip(
                                        selected = meUnit.equals(unitOpt, ignoreCase = true),
                                        onClick = { meUnit = unitOpt },
                                        label = { Text(unitOpt, fontSize = 10.5.sp) }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val updatedMap = LinkedHashMap<String, NutrientValue>()
                            // Preserve non-standard keys if any existed
                            product.nutrition.forEach { (k, nv) ->
                                if (EDITABLE_NUTRIENT_FIELDS.none { it.key.equals(k, ignoreCase = true) } &&
                                    nv.value.isFinite() && nv.value > 0.0
                                ) {
                                    updatedMap[k] = nv
                                }
                            }
                            EDITABLE_NUTRIENT_FIELDS.forEach { spec ->
                                val parsed = fieldValues[spec.key].orEmpty().toDoubleOrNull()
                                if (parsed != null && parsed.isFinite() && parsed > 0.0) {
                                    val displayName = spec.label.substringBefore(" (").trim()
                                    updatedMap[spec.key] = NutrientValue(
                                        name = displayName,
                                        value = parsed,
                                        unit = "%"
                                    )
                                }
                            }
                            val parsedMe = meText.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
                            onApply(updatedMap, parsedMe, if (parsedMe != null) meUnit else null)
                        },
                        enabled = fieldErrors.isEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF479C73)),
                        shape = RoundedCornerShape(100.dp)
                    ) {
                        Text("Apply Nutrients", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IngredientInputWithAddButton(
    ingredientList: SnapshotStateList<String>,
    modifier: Modifier = Modifier
) {
    var currentInput by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editingText by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf<String?>(null) }

    fun addCurrent() {
        val filtered = LabelAiCorrectionService.filterIngredientInputText(currentInput).trim()
        if (filtered.isNotBlank()) {
            val validationError = LabelAiCorrectionService.validateManualIngredientInput(filtered)
            if (validationError != null) {
                inputError = validationError
                return
            }
            val validatedParts = LabelAiCorrectionService.extractValidatedIngredientsList(filtered)
                .ifEmpty {
                    LabelAiCorrectionService.splitIngredientsPreservingParentheses(filtered)
                        .map { LabelAiCorrectionService.filterIngredientInputText(it.trim()) }
                        .filter { it.isNotBlank() }
                }
            for (part in validatedParts) {
                if (ingredientList.none { it.equals(part, ignoreCase = true) }) {
                    ingredientList.add(part)
                }
            }
            currentInput = ""
            inputError = null
        }
    }

    fun applyInlineEdit() {
        val idx = editingIndex ?: return
        val cleaned = LabelAiCorrectionService.filterIngredientInputText(editingText).trim()
        if (idx in ingredientList.indices && cleaned.isNotBlank()) {
            val validationError = LabelAiCorrectionService.validateManualIngredientInput(cleaned)
            if (validationError != null) {
                editError = validationError
                return
            }
            val sanitized = LabelAiCorrectionService.sanitizeIngredientsString(cleaned)
                .ifBlank { cleaned }
            ingredientList[idx] = sanitized
        }
        editingIndex = null
        editingText = ""
        editError = null
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .subtleCardShadow(borderRadius = 100.dp),
            shape = RoundedCornerShape(100.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            OutlinedTextField(
                value = currentInput,
                onValueChange = {
                    currentInput = LabelAiCorrectionService.filterIngredientInputText(it)
                    if (inputError != null) inputError = null
                },
                placeholder = {
                    Text(
                        "e.g. Chicken Meal",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                singleLine = true,
                isError = inputError != null,
                trailingIcon = {
                    IconButton(
                        onClick = { addCurrent() },
                        enabled = currentInput.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Add Ingredient",
                            tint = if (currentInput.isNotBlank()) Color(0xFF479C73) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { addCurrent() }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                    focusedBorderColor = Color(0xFF479C73),
                    errorBorderColor = Color(0xFFD32F2F),
                    cursorColor = Color(0xFF479C73)
                ),
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(100.dp)
            )
        }

        if (inputError != null) {
            Text(
                text = inputError!!,
                fontSize = 11.sp,
                color = Color(0xFFD32F2F),
                modifier = Modifier.padding(start = 10.dp)
            )
        }

        // Inline Edit Box when the user taps the pencil icon on any ingredient
        val activeEditIdx = editingIndex
        if (activeEditIdx != null && activeEditIdx in ingredientList.indices) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .subtleCardShadow(borderRadius = 100.dp),
                shape = RoundedCornerShape(100.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.2.dp, Color(0xFF479C73))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = editingText,
                        onValueChange = {
                            editingText = LabelAiCorrectionService.filterIngredientInputText(it)
                            if (editError != null) editError = null
                        },
                        singleLine = true,
                        isError = editError != null,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { applyInlineEdit() }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = Color(0xFF479C73)
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(100.dp)
                    )
                    IconButton(
                        onClick = { applyInlineEdit() },
                        enabled = editingText.trim().isNotBlank(),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Save",
                            tint = Color(0xFF479C73),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            editingIndex = null
                            editingText = ""
                            editError = null
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Cancel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            if (editError != null) {
                Text(
                    text = editError!!,
                    fontSize = 11.sp,
                    color = Color(0xFFD32F2F),
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }

        if (ingredientList.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ingredientList.forEachIndexed { index, item ->
                    val isBeingEdited = editingIndex == index
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = if (isBeingEdited) Color(0xFFC8E6C9) else Color(0xFFE8F5E9),
                        border = BorderStroke(
                            if (isBeingEdited) 1.5.dp else 1.dp,
                            if (isBeingEdited) Color(0xFF2E7D32) else Color(0xFF479C73).copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            Text(
                                text = item,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1B5E20)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            // Pencil Edit button for every ingredient
                            IconButton(
                                onClick = {
                                    editingIndex = index
                                    editingText = item
                                    editError = null
                                },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Edit $item",
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(13.5.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            // Remove button
                            IconButton(
                                onClick = {
                                    if (editingIndex == index) {
                                        editingIndex = null
                                        editingText = ""
                                        editError = null
                                    } else if (editingIndex != null && index < editingIndex!!) {
                                        editingIndex = editingIndex!! - 1
                                    }
                                    ingredientList.removeAt(index)
                                },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Remove $item",
                                    tint = Color(0xFFD32F2F),
                                    modifier = Modifier.size(14.dp)
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
fun EditScannedIngredientsDialog(
    product: Product,
    referencePhotoUrl: String? = null,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    val ingredientList = remember(product) {
        mutableStateListOf<String>().apply {
            if (product.ingredientsText.isNotBlank()) {
                val parts = LabelAiCorrectionService.splitIngredientsPreservingParentheses(product.ingredientsText)
                    .map { LabelAiCorrectionService.filterIngredientInputText(it.trim()) }
                    .filter { it.isNotBlank() }
                addAll(parts.distinctBy { it.lowercase() })
            }
        }
    }

    val fullIngredientsText = remember(ingredientList.toList()) {
        ingredientList.joinToString(", ")
    }
    val moderation = remember(fullIngredientsText) { ContentModerationFilter.analyze(fullIngredientsText) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Edit Ingredients",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Type an ingredient and tap + to add, or tap the pencil icon to edit any item.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                ModerationWarningBanner(moderation = moderation)

                if (!referencePhotoUrl.isNullOrBlank()) {
                    ReferenceLabelPhotoBanner(
                        photoUrl = referencePhotoUrl,
                        label = "Captured Ingredients Label (tap to zoom)"
                    )
                }

                IngredientInputWithAddButton(ingredientList = ingredientList)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val cleaned = LabelAiCorrectionService.filterIngredientInputText(fullIngredientsText.trim())
                            onApply(cleaned)
                        },
                        enabled = !moderation.containsBadWords,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF479C73)),
                        shape = RoundedCornerShape(100.dp)
                    ) {
                        Text("Apply Ingredients", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Enforces strict decimal input (digits 0-9 and at most one decimal dot).
 * Letters, spaces, '%' signs, and symbols are stripped immediately.
 */
fun filterStrictDecimalInput(raw: String): String {
    val sb = StringBuilder(raw.length)
    var seenDot = false
    for (ch in raw) {
        when {
            ch in '0'..'9' -> sb.append(ch)
            ch == '.' && !seenDot -> {
                if (sb.isEmpty()) sb.append('0')
                sb.append('.')
                seenDot = true
            }
        }
    }
    return sb.toString()
}

@Composable
private fun ReferenceLabelPhotoBanner(
    photoUrl: String,
    label: String
) {
    var showZoomed by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showZoomed = true },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, Color(0xFF479C73).copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = photoUrl,
                contentDescription = label,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Compare AI values directly against your captured photo.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showZoomed) {
        Dialog(
            onDismissRequest = { showZoomed = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .clickable { showZoomed = false }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { showZoomed = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

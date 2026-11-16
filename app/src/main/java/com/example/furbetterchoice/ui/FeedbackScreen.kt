package com.example.furbetterchoice.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.furbetterchoice.R
import com.example.furbetterchoice.api.CloudinaryUploadService
import com.example.furbetterchoice.api.LabelAiCorrectionService
import com.example.furbetterchoice.api.NutrientValue
import com.example.furbetterchoice.api.Product
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

private sealed class BarcodeDbCheckState {
    object Idle : BarcodeDbCheckState()
    data class Checking(val barcode: String) : BarcodeDbCheckState()
    data class ExistsInDb(val barcode: String) : BarcodeDbCheckState()
    data class VerifiedNew(val barcode: String) : BarcodeDbCheckState()
}

private sealed class BarcodePhotoVerifyState {
    object None : BarcodePhotoVerifyState()
    object Verifying : BarcodePhotoVerifyState()
    object Matched : BarcodePhotoVerifyState()
    object NoBarcodeFound : BarcodePhotoVerifyState()
    data class Mismatch(val detectedBarcode: String) : BarcodePhotoVerifyState()
}

private data class CommunityNutrientSpec(
    val key: String,
    val label: String,
    val maxAllowed: Double = 100.0
)

private val CORE_COMMUNITY_NUTRIENTS = listOf(
    CommunityNutrientSpec("crudeProtein", "Crude Protein"),
    CommunityNutrientSpec("crudeFat", "Crude Fat"),
    CommunityNutrientSpec("crudeFiber", "Crude Fiber"),
    CommunityNutrientSpec("moisture", "Moisture")
)

private val FEDIAF_AAFCO_EXTRA_NUTRIENTS = listOf(
    CommunityNutrientSpec("calcium", "Calcium", maxAllowed = 25.0),
    CommunityNutrientSpec("phosphorus", "Phosphorus", maxAllowed = 25.0),
    CommunityNutrientSpec("taurine", "Taurine", maxAllowed = 15.0),
    CommunityNutrientSpec("ash", "Crude Ash", maxAllowed = 100.0),
    CommunityNutrientSpec("omega3", "Omega-3", maxAllowed = 15.0),
    CommunityNutrientSpec("omega6", "Omega-6", maxAllowed = 20.0),
    CommunityNutrientSpec("sodium", "Sodium", maxAllowed = 5.0)
)

private val ALL_COMMUNITY_NUTRIENTS = CORE_COMMUNITY_NUTRIENTS + FEDIAF_AAFCO_EXTRA_NUTRIENTS

private suspend fun verifyBarcodeInImageUri(
    context: Context,
    imageUri: Uri,
    expectedBarcode: String
): BarcodePhotoVerifyState {
    return try {
        val inputImage = InputImage.fromFilePath(context, imageUri)
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_ITF
            )
            .build()
        val scanner = BarcodeScanning.getClient(options)
        val barcodes = suspendCancellableCoroutine<List<Barcode>> { cont ->
            scanner.process(inputImage)
                .addOnSuccessListener { result ->
                    scanner.close()
                    if (cont.isActive) cont.resume(result ?: emptyList())
                }
                .addOnFailureListener {
                    scanner.close()
                    if (cont.isActive) cont.resume(emptyList())
                }
        }
        val detectedValues = barcodes.mapNotNull { it.rawValue?.trim() }.filter { it.isNotEmpty() }
        if (detectedValues.isEmpty()) {
            BarcodePhotoVerifyState.NoBarcodeFound
        } else {
            val cleanExpected = expectedBarcode.trim().trimStart('0')
            val isMatch = detectedValues.any { raw ->
                raw == expectedBarcode.trim() ||
                    (cleanExpected.isNotEmpty() && raw.trimStart('0') == cleanExpected)
            }
            if (isMatch) {
                BarcodePhotoVerifyState.Matched
            } else {
                BarcodePhotoVerifyState.Mismatch(detectedValues.first())
            }
        }
    } catch (_: Exception) {
        BarcodePhotoVerifyState.NoBarcodeFound
    }
}

@Composable
private fun CommunityNutrientPillField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier,
    unitText: String = "%",
    placeholderText: String = "0.0",
    maxLength: Int = 6
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 3.dp)
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .subtleCardShadow(borderRadius = 100.dp),
            shape = RoundedCornerShape(100.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { raw ->
                    if (raw.length <= maxLength && raw.count { it == '.' } <= 1 && raw.all { it.isDigit() || it == '.' }) {
                        onValueChange(raw)
                    }
                },
                placeholder = {
                    Text(
                        placeholderText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                },
                trailingIcon = {
                    Text(
                        text = unitText,
                        fontWeight = FontWeight.Bold,
                        color = if (isError) MaterialTheme.colorScheme.error else Color(0xFF479C73),
                        fontSize = if (unitText.length > 2) 10.5.sp else 13.sp,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(100.dp),
                singleLine = true,
                isError = isError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                    },
                    focusedBorderColor = if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color(0xFF479C73)
                    },
                    errorBorderColor = MaterialTheme.colorScheme.error,
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    errorContainerColor = Color.Transparent
                ),
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

@Composable
fun FeedbackScreen(
    viewModel: AppViewModel,
    paddingValues: PaddingValues = PaddingValues(0.dp),
    initialTab: Int = 0,
    onNavigateToLogin: () -> Unit = {}
) {
    val isDark by viewModel.isDarkMode.collectAsState()
    val boxColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f) else Color(0xFFE4EAE8)
    var showSuccessOverlay by remember { mutableStateOf(false) }

    LaunchedEffect(showSuccessOverlay) {
        if (showSuccessOverlay) {
            delay(2200)
            showSuccessOverlay = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Community Workspace",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Help fellow pet parents discover safer, healthier pet food",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ContributeProductSection(
                viewModel = viewModel,
                boxColor = boxColor,
                isDarkMode = isDark,
                onNavigateToLogin = onNavigateToLogin,
                onSuccess = { showSuccessOverlay = true }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        AnimatedVisibility(
            visible = showSuccessOverlay,
            enter = fadeIn(tween(220)) + scaleIn(
                initialScale = 0.82f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.9f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF479C73)),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Success",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Product Saved!",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your contribution is now live in the database.",
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun ContributeProductSection(
    viewModel: AppViewModel,
    boxColor: Color,
    isDarkMode: Boolean,
    onNavigateToLogin: () -> Unit,
    onSuccess: () -> Unit
) {
    val fieldBg = MaterialTheme.colorScheme.surface
    val fieldBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    val isUserLoggedIn by viewModel.isUserLoggedIn.collectAsState()

    var barcode by remember { mutableStateOf("") }
    var barcodeCheckState by remember { mutableStateOf<BarcodeDbCheckState>(BarcodeDbCheckState.Idle) }
    var productName by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    val ingredientList = remember { mutableStateListOf<String>() }

    val nutrientInputs = remember {
        mutableStateMapOf(
            "crudeProtein" to "",
            "crudeFat" to "",
            "crudeFiber" to "",
            "moisture" to "",
            "calcium" to "",
            "phosphorus" to "",
            "taurine" to "",
            "ash" to "",
            "omega3" to "",
            "omega6" to "",
            "sodium" to ""
        )
    }
    var metabolizableEnergyInput by remember { mutableStateOf("") }

    var frontBagPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var nutrientsPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var ingredientsPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var barcodePhotoUri by remember { mutableStateOf<Uri?>(null) }
    var barcodePhotoVerifyState by remember { mutableStateOf<BarcodePhotoVerifyState>(BarcodePhotoVerifyState.None) }

    var activePhotoSlot by remember { mutableStateOf<String?>(null) }
    var showPhotoSourceDialogForSlot by remember { mutableStateOf<String?>(null) }

    var isSubmitting by remember { mutableStateOf(false) }
    var showSubmitConfirmDialog by remember { mutableStateOf(false) }
    var showLoginRequiredDialog by remember { mutableStateOf(false) }
    var uploadProgressText by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showBarcodeScannerDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun saveBitmapToCacheUri(bitmap: Bitmap, slot: String): Uri? {
        return try {
            val file = File(context.cacheDir, "contrib_${slot}_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            Uri.fromFile(file)
        } catch (_: Exception) {
            null
        }
    }

    fun assignPhotoToSlot(slot: String?, uri: Uri?) {
        if (slot == null || uri == null) return
        errorMessage = null
        when (slot) {
            "front" -> frontBagPhotoUri = uri
            "nutrients" -> nutrientsPhotoUri = uri
            "ingredients" -> ingredientsPhotoUri = uri
            "barcode" -> {
                barcodePhotoUri = uri
                barcodePhotoVerifyState = BarcodePhotoVerifyState.Verifying
                val currentBarcode = barcode
                scope.launch {
                    barcodePhotoVerifyState = verifyBarcodeInImageUri(context, uri, currentBarcode)
                }
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        assignPhotoToSlot(activePhotoSlot, uri)
    }

    val cameraPreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null && activePhotoSlot != null) {
            val uri = saveBitmapToCacheUri(bitmap, activePhotoSlot!!)
            assignPhotoToSlot(activePhotoSlot, uri)
        }
    }

    LaunchedEffect(barcode, barcodePhotoUri) {
        val uri = barcodePhotoUri
        if (uri != null && barcode.isNotBlank()) {
            barcodePhotoVerifyState = BarcodePhotoVerifyState.Verifying
            barcodePhotoVerifyState = verifyBarcodeInImageUri(context, uri, barcode)
        } else if (uri == null) {
            barcodePhotoVerifyState = BarcodePhotoVerifyState.None
        }
    }

    val productNameValidationError = remember(productName) {
        if (productName.isBlank()) null
        else LabelAiCorrectionService.validateProductOrBrandName(productName, "Product name")
    }
    val brandValidationError = remember(brand) {
        if (brand.isBlank()) null
        else LabelAiCorrectionService.validateProductOrBrandName(brand, "Brand name")
    }

    val nutrientFieldErrors = remember(nutrientInputs.toMap(), metabolizableEnergyInput) {
        val errors = mutableMapOf<String, String>()
        ALL_COMMUNITY_NUTRIENTS.forEach { spec ->
            val raw = nutrientInputs[spec.key]?.trim().orEmpty()
            if (raw.isNotEmpty()) {
                val parsed = raw.toDoubleOrNull()
                if (parsed == null || parsed <= 0.0 || parsed > spec.maxAllowed) {
                    errors[spec.key] = "Invalid ${spec.label} (must be > 0 and <= ${spec.maxAllowed.toInt()}%)"
                }
            }
        }
        val meRaw = metabolizableEnergyInput.trim()
        if (meRaw.isNotEmpty()) {
            val parsedMe = meRaw.toDoubleOrNull()
            if (parsedMe == null || !parsedMe.isFinite() || parsedMe <= 0.0 || parsedMe > 10000.0) {
                errors["metabolizableEnergy"] = "Invalid Metabolizable Energy (1 - 10,000 kcal/kg)"
            }
        }
        errors
    }

    val coreNutrientSum = remember(
        nutrientInputs["crudeProtein"],
        nutrientInputs["crudeFat"],
        nutrientInputs["crudeFiber"],
        nutrientInputs["moisture"]
    ) {
        listOf("crudeProtein", "crudeFat", "crudeFiber", "moisture").sumOf { key ->
            nutrientInputs[key]?.trim()?.toDoubleOrNull() ?: 0.0
        }
    }
    val ashVal = nutrientInputs["ash"]?.trim()?.toDoubleOrNull() ?: 0.0
    val hasNutrientSumOverflow = coreNutrientSum > 100.0 || (coreNutrientSum + ashVal) > 105.0
    val allRequiredNutrientsFilled = CORE_COMMUNITY_NUTRIENTS.all { spec ->
        val raw = nutrientInputs[spec.key]?.trim().orEmpty()
        val parsed = raw.toDoubleOrNull()
        parsed != null && parsed > 0.0 && parsed <= spec.maxAllowed
    } && nutrientFieldErrors.isEmpty() && !hasNutrientSumOverflow

    val isBarcodeVerifiedNew = barcodeCheckState is BarcodeDbCheckState.VerifiedNew
    val allPhotosAttached = frontBagPhotoUri != null &&
        nutrientsPhotoUri != null &&
        ingredientsPhotoUri != null &&
        barcodePhotoUri != null
    val isBarcodePhotoMatched = barcodePhotoVerifyState is BarcodePhotoVerifyState.Matched

    val canSubmit = isBarcodeVerifiedNew &&
        productName.trim().isNotEmpty() &&
        productNameValidationError == null &&
        brand.trim().isNotEmpty() &&
        brandValidationError == null &&
        ingredientList.isNotEmpty() &&
        allRequiredNutrientsFilled &&
        allPhotosAttached &&
        isBarcodePhotoMatched &&
        !isSubmitting

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .subtleCardShadow(borderRadius = 24.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = boxColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. BARCODE * (single, non-editable scan button)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RequiredFieldLabel("Barcode")

                val scanInteractionSource = remember { MutableInteractionSource() }
                val isScanPressed by scanInteractionSource.collectIsPressedAsState()
                val scanButtonScale by animateFloatAsState(
                    targetValue = if (isScanPressed) 0.97f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "scanBarcodeButtonScale"
                )

                if (barcode.isBlank()) {
                    Button(
                        onClick = {
                            errorMessage = null
                            showBarcodeScannerDialog = true
                        },
                        interactionSource = scanInteractionSource,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .scale(scanButtonScale),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF479C73))
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_barcode),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Scan Product Barcode",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .subtleCardShadow(borderRadius = 20.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = fieldBg,
                        border = fieldBorder
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF479C73).copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_barcode),
                                        contentDescription = null,
                                        tint = Color(0xFF479C73),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Scanned Barcode",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = barcode,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    errorMessage = null
                                    showBarcodeScannerDialog = true
                                },
                                interactionSource = scanInteractionSource,
                                modifier = Modifier
                                    .height(36.dp)
                                    .scale(scanButtonScale),
                                shape = RoundedCornerShape(100.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF479C73)),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = "Rescan",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Minimal barcode status: only "Product already exists" or "Product is new"
                AnimatedContent(
                    targetState = barcodeCheckState,
                    transitionSpec = {
                        (fadeIn(tween(180)) + expandVertically(tween(180)))
                            .togetherWith(fadeOut(tween(140)) + shrinkVertically(tween(140)))
                            .using(SizeTransform(clip = false))
                    },
                    label = "barcodeStatusAnim"
                ) { state ->
                    when (state) {
                        is BarcodeDbCheckState.Idle -> Spacer(modifier = Modifier.height(0.dp))
                        is BarcodeDbCheckState.Checking -> {
                            Row(
                                modifier = Modifier.padding(start = 8.dp, top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF479C73)
                                )
                                Text(
                                    text = "Checking barcode...",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        is BarcodeDbCheckState.ExistsInDb -> {
                            Row(
                                modifier = Modifier.padding(start = 8.dp, top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Product already exists",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        is BarcodeDbCheckState.VerifiedNew -> {
                            Row(
                                modifier = Modifier.padding(start = 8.dp, top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF479C73),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Product is new",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF479C73)
                                )
                            }
                        }
                    }
                }
            }

            // Informational showcase visible until a new barcode is scanned & verified
            AnimatedVisibility(
                visible = !isBarcodeVerifiedNew,
                enter = fadeIn(tween(220)) + expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
                exit = fadeOut(tween(160)) + shrinkVertically(tween(180))
            ) {
                CommunityWorkspaceInfoSection(
                    isDarkMode = isDarkMode
                )
            }

            // Show remaining form fields only when barcode is verified new
            AnimatedVisibility(
                visible = isBarcodeVerifiedNew,
                enter = fadeIn(tween(220)) + expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
                exit = fadeOut(tween(160)) + shrinkVertically(tween(180))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 2. PRODUCT NAME *
                    Column {
                        RequiredFieldLabel("Product Name")
                        RoundedFadingTextField(
                            value = productName,
                            onValueChange = {
                                productName = it
                                errorMessage = null
                            },
                            placeholderText = "e.g., Adult Chicken & Brown Rice",
                            fieldBg = fieldBg,
                            fieldBorder = fieldBorder
                        )
                        if (productNameValidationError != null) {
                            Text(
                                text = productNameValidationError,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 10.dp, top = 3.dp)
                            )
                        }
                    }

                    // 3. BRAND NAME *
                    Column {
                        RequiredFieldLabel("Brand Name")
                        RoundedFadingTextField(
                            value = brand,
                            onValueChange = {
                                brand = it
                                errorMessage = null
                            },
                            placeholderText = "e.g., Royal Canin, Purina, Pedigree",
                            fieldBg = fieldBg,
                            fieldBorder = fieldBorder
                        )
                        if (brandValidationError != null) {
                            Text(
                                text = brandValidationError,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 10.dp, top = 3.dp)
                            )
                        }
                    }

                    // 4. INGREDIENT LIST *
                    Column {
                        RequiredFieldLabel("Ingredient List")
                        IngredientInputWithAddButton(ingredientList = ingredientList)
                    }

                    // 5. NUTRIENTS *
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RequiredFieldLabel("Nutrients")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CommunityNutrientPillField(
                                label = "Crude Protein",
                                value = nutrientInputs["crudeProtein"].orEmpty(),
                                onValueChange = {
                                    nutrientInputs["crudeProtein"] = it
                                    errorMessage = null
                                },
                                isError = nutrientFieldErrors.containsKey("crudeProtein") || hasNutrientSumOverflow,
                                modifier = Modifier.weight(1f)
                            )
                            CommunityNutrientPillField(
                                label = "Crude Fat",
                                value = nutrientInputs["crudeFat"].orEmpty(),
                                onValueChange = {
                                    nutrientInputs["crudeFat"] = it
                                    errorMessage = null
                                },
                                isError = nutrientFieldErrors.containsKey("crudeFat") || hasNutrientSumOverflow,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CommunityNutrientPillField(
                                label = "Crude Fiber",
                                value = nutrientInputs["crudeFiber"].orEmpty(),
                                onValueChange = {
                                    nutrientInputs["crudeFiber"] = it
                                    errorMessage = null
                                },
                                isError = nutrientFieldErrors.containsKey("crudeFiber") || hasNutrientSumOverflow,
                                modifier = Modifier.weight(1f)
                            )
                            CommunityNutrientPillField(
                                label = "Moisture",
                                value = nutrientInputs["moisture"].orEmpty(),
                                onValueChange = {
                                    nutrientInputs["moisture"] = it
                                    errorMessage = null
                                },
                                isError = nutrientFieldErrors.containsKey("moisture") || hasNutrientSumOverflow,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "FEDIAF & AAFCO Nutrients (If Listed on Label)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF479C73),
                            modifier = Modifier.padding(start = 6.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CommunityNutrientPillField(
                                label = "Calcium",
                                value = nutrientInputs["calcium"].orEmpty(),
                                onValueChange = {
                                    nutrientInputs["calcium"] = it
                                    errorMessage = null
                                },
                                isError = nutrientFieldErrors.containsKey("calcium"),
                                modifier = Modifier.weight(1f)
                            )
                            CommunityNutrientPillField(
                                label = "Phosphorus",
                                value = nutrientInputs["phosphorus"].orEmpty(),
                                onValueChange = {
                                    nutrientInputs["phosphorus"] = it
                                    errorMessage = null
                                },
                                isError = nutrientFieldErrors.containsKey("phosphorus"),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CommunityNutrientPillField(
                                label = "Taurine",
                                value = nutrientInputs["taurine"].orEmpty(),
                                onValueChange = {
                                    nutrientInputs["taurine"] = it
                                    errorMessage = null
                                },
                                isError = nutrientFieldErrors.containsKey("taurine"),
                                modifier = Modifier.weight(1f)
                            )
                            CommunityNutrientPillField(
                                label = "Crude Ash",
                                value = nutrientInputs["ash"].orEmpty(),
                                onValueChange = {
                                    nutrientInputs["ash"] = it
                                    errorMessage = null
                                },
                                isError = nutrientFieldErrors.containsKey("ash") || hasNutrientSumOverflow,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CommunityNutrientPillField(
                                label = "Omega-3",
                                value = nutrientInputs["omega3"].orEmpty(),
                                onValueChange = {
                                    nutrientInputs["omega3"] = it
                                    errorMessage = null
                                },
                                isError = nutrientFieldErrors.containsKey("omega3"),
                                modifier = Modifier.weight(1f)
                            )
                            CommunityNutrientPillField(
                                label = "Omega-6",
                                value = nutrientInputs["omega6"].orEmpty(),
                                onValueChange = {
                                    nutrientInputs["omega6"] = it
                                    errorMessage = null
                                },
                                isError = nutrientFieldErrors.containsKey("omega6"),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CommunityNutrientPillField(
                                label = "Sodium",
                                value = nutrientInputs["sodium"].orEmpty(),
                                onValueChange = {
                                    nutrientInputs["sodium"] = it
                                    errorMessage = null
                                },
                                isError = nutrientFieldErrors.containsKey("sodium"),
                                modifier = Modifier.weight(1f)
                            )
                            CommunityNutrientPillField(
                                label = "Metabolizable Energy",
                                value = metabolizableEnergyInput,
                                onValueChange = {
                                    metabolizableEnergyInput = it
                                    errorMessage = null
                                },
                                isError = nutrientFieldErrors.containsKey("metabolizableEnergy"),
                                unitText = "kcal/kg",
                                placeholderText = "3500",
                                maxLength = 7,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (hasNutrientSumOverflow) {
                            Text(
                                text = "Total cannot exceed 100% for core nutrients (or 105% with Ash).",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 10.dp, top = 2.dp)
                            )
                        } else if (nutrientFieldErrors.isNotEmpty()) {
                            Text(
                                text = nutrientFieldErrors.values.first(),
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 10.dp, top = 2.dp)
                            )
                        }
                    }

                    // 6. PHOTO ATTACHMENT *
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RequiredFieldLabel("Photo Attachment")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CommunityPhotoSlotCard(
                                modifier = Modifier.weight(1f),
                                title = "Front Bag",
                                photoUri = frontBagPhotoUri,
                                onAddClick = { showPhotoSourceDialogForSlot = "front" },
                                onRemoveClick = { frontBagPhotoUri = null }
                            )
                            CommunityPhotoSlotCard(
                                modifier = Modifier.weight(1f),
                                title = "Nutrients",
                                photoUri = nutrientsPhotoUri,
                                onAddClick = { showPhotoSourceDialogForSlot = "nutrients" },
                                onRemoveClick = { nutrientsPhotoUri = null }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CommunityPhotoSlotCard(
                                modifier = Modifier.weight(1f),
                                title = "Ingredients",
                                photoUri = ingredientsPhotoUri,
                                onAddClick = { showPhotoSourceDialogForSlot = "ingredients" },
                                onRemoveClick = { ingredientsPhotoUri = null }
                            )
                            CommunityPhotoSlotCard(
                                modifier = Modifier.weight(1f),
                                title = "Barcode",
                                photoUri = barcodePhotoUri,
                                badgeState = barcodePhotoVerifyState,
                                onAddClick = { showPhotoSourceDialogForSlot = "barcode" },
                                onRemoveClick = {
                                    barcodePhotoUri = null
                                    barcodePhotoVerifyState = BarcodePhotoVerifyState.None
                                }
                            )
                        }

                        when (val verifyState = barcodePhotoVerifyState) {
                            is BarcodePhotoVerifyState.NoBarcodeFound -> {
                                Text(
                                    text = "No readable barcode detected in Barcode photo.",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                            is BarcodePhotoVerifyState.Mismatch -> {
                                Text(
                                    text = "Barcode photo (${verifyState.detectedBarcode}) does not match scanned barcode ($barcode).",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                            else -> {}
                        }
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val submitScale by animateFloatAsState(
                        targetValue = if (canSubmit) 1f else 0.99f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "submitScale"
                    )

                    Button(
                        onClick = {
                            if (!isSubmitting) {
                                errorMessage = null
                                if (!isUserLoggedIn) {
                                    showLoginRequiredDialog = true
                                } else {
                                    showSubmitConfirmDialog = true
                                }
                            }
                        },
                        enabled = canSubmit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .scale(submitScale),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF479C73),
                            disabledContainerColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = uploadProgressText ?: "Saving...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        } else {
                            Text(
                                text = "Submit Product",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSubmitConfirmDialog) {
        var potentialDuplicates by remember { mutableStateOf<List<Product>>(emptyList()) }
        LaunchedEffect(showSubmitConfirmDialog, brand, productName, barcode) {
            potentialDuplicates = viewModel.findPotentialDuplicateProducts(
                brand = brand,
                productName = productName,
                excludeBarcode = barcode
            )
        }

        AlertDialog(
            onDismissRequest = { showSubmitConfirmDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Confirm Submission",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Are you sure you want to submit this product to the live database?",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (potentialDuplicates.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFD29524).copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, Color(0xFFD29524).copy(alpha = 0.45f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Possible Similar Product Found",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFFB27912)
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                val preview = potentialDuplicates.take(2).joinToString("\n") { dup ->
                                    "• ${dup.brands.ifBlank { "Brand" }} - ${dup.productName} (${dup.barcode.ifBlank { dup.productId }})"
                                }
                                Text(
                                    text = "$preview\nPlease verify this is a distinct package/barcode before proceeding.",
                                    fontSize = 11.sp,
                                    lineHeight = 14.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = if (isDarkMode) Color(0xFF3A1A1A) else Color(0xFFFFEBEE),
                        border = BorderStroke(
                            1.dp,
                            if (isDarkMode) Color(0xFFEF5350).copy(alpha = 0.55f) else Color(0xFFE53935).copy(alpha = 0.45f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = if (isDarkMode) Color(0xFFFCA5A5) else Color(0xFFD32F2F),
                                modifier = Modifier.size(19.dp)
                            )
                            Column {
                                Text(
                                    text = "Submitter Responsibility Warning",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = if (isDarkMode) Color(0xFFFCA5A5) else Color(0xFFB71C1C)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "You are solely responsible for the accuracy and authenticity of the information and photos you submit. Fake inputs or fraudulent submissions are strictly prohibited.",
                                    fontSize = 11.5.sp,
                                    lineHeight = 15.5.sp,
                                    color = if (isDarkMode) Color(0xFFFECACA) else Color(0xFF7F1D1D)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSubmitConfirmDialog = false
                        if (isSubmitting) return@Button
                        isSubmitting = true
                        errorMessage = null

                        val finalProductName = productName.trim()
                        val finalBrand = brand.trim()
                        val finalBarcode = barcode.trim()
                        val finalIngredients = ingredientList.joinToString(", ")
                        val finalNutrients = mutableMapOf<String, NutrientValue>()

                        ALL_COMMUNITY_NUTRIENTS.forEach { spec ->
                            val parsed = nutrientInputs[spec.key]?.trim()?.toDoubleOrNull()
                            if (parsed != null && parsed > 0.0) {
                                finalNutrients[spec.key] = NutrientValue(
                                    name = spec.label,
                                    value = parsed,
                                    unit = "%"
                                )
                            }
                        }
                        val finalMe = metabolizableEnergyInput.trim().toDoubleOrNull()
                            ?.takeIf { it.isFinite() && it > 0.0 }

                        scope.launch {
                            val uploadedUrls = mutableMapOf<String, String>()
                            val slotsToUpload = listOf(
                                "front" to frontBagPhotoUri,
                                "nutrients" to nutrientsPhotoUri,
                                "ingredients" to ingredientsPhotoUri,
                                "barcode" to barcodePhotoUri
                            )
                            for ((index, pair) in slotsToUpload.withIndex()) {
                                val (slotKey, slotUri) = pair
                                if (slotUri == null) {
                                    isSubmitting = false
                                    uploadProgressText = null
                                    errorMessage = "Please attach all 4 required photos."
                                    return@launch
                                }
                                uploadProgressText = "Uploading photo ${index + 1}/4..."
                                val result = CloudinaryUploadService.uploadImageUri(context, slotUri)
                                val url = result.getOrNull()
                                if (url.isNullOrBlank()) {
                                    isSubmitting = false
                                    uploadProgressText = null
                                    errorMessage = result.exceptionOrNull()?.message
                                        ?: "Failed to upload $slotKey photo."
                                    return@launch
                                }
                                uploadedUrls[slotKey] = url
                            }

                            uploadProgressText = "Saving product..."

                            val submitResult = viewModel.submitCommunityContribution(
                                barcode = finalBarcode,
                                productName = finalProductName,
                                brand = finalBrand,
                                ingredients = finalIngredients,
                                nutrition = finalNutrients,
                                metabolizableEnergy = finalMe,
                                metabolizableEnergyUnit = if (finalMe != null) "kcal/kg" else null,
                                productPhotoUrl = uploadedUrls["front"] ?: "",
                                nutritionPhotoUrl = uploadedUrls["nutrients"] ?: "",
                                ingredientsPhotoUrl = uploadedUrls["ingredients"] ?: "",
                                barcodePhotoUrl = uploadedUrls["barcode"] ?: ""
                            )

                            isSubmitting = false
                            uploadProgressText = null
                            if (submitResult.isSuccess) {
                                barcode = ""
                                barcodeCheckState = BarcodeDbCheckState.Idle
                                productName = ""
                                brand = ""
                                ingredientList.clear()
                                nutrientInputs.keys.toList().forEach { key ->
                                    nutrientInputs[key] = ""
                                }
                                metabolizableEnergyInput = ""
                                frontBagPhotoUri = null
                                nutrientsPhotoUri = null
                                ingredientsPhotoUri = null
                                barcodePhotoUri = null
                                barcodePhotoVerifyState = BarcodePhotoVerifyState.None
                                onSuccess()
                            } else {
                                errorMessage = submitResult.exceptionOrNull()?.message
                                    ?: "Failed to submit product."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF479C73)),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text("Proceed", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showSubmitConfirmDialog = false },
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showLoginRequiredDialog) {
        LoginRequiredDialog(
            onDismiss = { showLoginRequiredDialog = false },
            onLoginClick = {
                showLoginRequiredDialog = false
                onNavigateToLogin()
            },
            actionText = "submit a new product to the Community Workspace"
        )
    }

    if (showPhotoSourceDialogForSlot != null) {
        val targetSlot = showPhotoSourceDialogForSlot!!
        AlertDialog(
            onDismissRequest = { showPhotoSourceDialogForSlot = null },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Attach Photo",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Choose how to provide this photo:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        activePhotoSlot = targetSlot
                        showPhotoSourceDialogForSlot = null
                        cameraPreviewLauncher.launch(null)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF479C73)),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text("Take Photo", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        activePhotoSlot = targetSlot
                        showPhotoSourceDialogForSlot = null
                        photoPickerLauncher.launch("image/*")
                    },
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text("Gallery", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showBarcodeScannerDialog) {
        Dialog(
            onDismissRequest = { showBarcodeScannerDialog = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                ProductScanner(
                    onBarcodeScanned = { scannedCode ->
                        val cleaned = scannedCode.trim()
                        barcode = cleaned
                        showBarcodeScannerDialog = false
                        barcodeCheckState = BarcodeDbCheckState.Checking(cleaned)
                        scope.launch {
                            val existing = viewModel.checkBarcodeExistsInDatabase(cleaned)
                            barcodeCheckState = if (existing != null) {
                                BarcodeDbCheckState.ExistsInDb(cleaned)
                            } else {
                                BarcodeDbCheckState.VerifiedNew(cleaned)
                            }
                        }
                    },
                    onClose = { showBarcodeScannerDialog = false }
                )
            }
        }
    }
}

@Composable
private fun CommunityPhotoSlotCard(
    modifier: Modifier = Modifier,
    title: String,
    photoUri: Uri?,
    badgeState: BarcodePhotoVerifyState = BarcodePhotoVerifyState.None,
    onAddClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "photoSlotPressScale"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            badgeState is BarcodePhotoVerifyState.Mismatch ||
                badgeState is BarcodePhotoVerifyState.NoBarcodeFound -> MaterialTheme.colorScheme.error
            photoUri != null -> Color(0xFF479C73)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        },
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "photoBorderColor"
    )

    Surface(
        modifier = modifier
            .height(96.dp)
            .scale(pressScale)
            .subtleCardShadow(borderRadius = 18.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor),
        interactionSource = interactionSource,
        onClick = { if (photoUri == null) onAddClick() }
    ) {
        if (photoUri == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF479C73).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add $title",
                        tint = Color(0xFF479C73),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = photoUri,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        when (badgeState) {
                            is BarcodePhotoVerifyState.Matched -> {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Matched",
                                    tint = Color(0xFF6EE7B7),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            is BarcodePhotoVerifyState.Mismatch,
                            is BarcodePhotoVerifyState.NoBarcodeFound -> {
                                Icon(
                                    imageVector = Icons.Rounded.Warning,
                                    contentDescription = "Mismatch",
                                    tint = Color(0xFFFCA5A5),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            is BarcodePhotoVerifyState.Verifying -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = Color.White
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Attached",
                                    tint = Color(0xFF6EE7B7),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
                IconButton(
                    onClick = onRemoveClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(22.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove $title",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CommunityWorkspaceInfoSection(
    isDarkMode: Boolean
) {
    val accentGreen = Color(0xFF479C73)
    val accentGold = Color(0xFFD29524)
    val solidBaseColor = if (isDarkMode) Color(0xFF1E2422) else Color(0xFFFFFEFE)
    val innerPanelColor = if (isDarkMode) solidBaseColor.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.88f)

    // Matches the FEDIAF / AAFCO card UI & layered green-gold bokeh background design
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(accentGreen.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_paw),
                            contentDescription = null,
                            tint = accentGreen,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "What is Community Workspace?",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = "Built by pet parents, for pet parents",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = accentGreen,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                // Overview Inner Panel (matches FEDIAF inner Surface)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = innerPanelColor,
                    border = BorderStroke(1.dp, accentGreen.copy(alpha = 0.22f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = accentGreen,
                            modifier = Modifier
                                .padding(top = 1.dp)
                                .size(18.dp)
                        )
                        Text(
                            text = "Community Workspace is a collaborative pet food catalog where you can add missing dog and cat food products. Whenever you find a bag or can that isn't in our database yet, scanning its barcode here lets you contribute its label details so every pet owner can make safer feeding choices.",
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Why Your Contribution Matters Panel
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = innerPanelColor,
                    border = BorderStroke(1.dp, accentGreen.copy(alpha = 0.20f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Why Your Contribution Matters",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        CommunityBenefitRow(
                            badgeText = "1",
                            title = "Instant Access for Every Pet Owner",
                            description = "Submitted products automatically reflect in the live database right away—helping others scan, compare, and evaluate the same food."
                        )

                        CommunityBenefitRow(
                            badgeText = "2",
                            title = "Smarter Allergen & Nutrient Checks",
                            description = "Recording the ingredient list and Guaranteed Analysis enables AAFCO/FEDIAF nutrient grading and flags ingredients your pet may be allergic to."
                        )

                        CommunityBenefitRow(
                            badgeText = "3",
                            title = "Verified & Authentic Catalog",
                            description = "Every submission verifies that the scanned barcode matches the barcode photo and checks for valid ingredients and nutrients—keeping fake products out."
                        )
                    }
                }

                // Checklist Panel
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = innerPanelColor,
                    border = BorderStroke(1.dp, accentGold.copy(alpha = 0.28f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "What You'll Need Ready",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "• Product barcode (tap Scan Product Barcode above to unlock the form)\n• Product name, brand, ingredients & 4 core nutrients\n• 4 package photos: Front Bag, Nutrients, Ingredients & Barcode",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityBenefitRow(
    badgeText: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0xFF479C73).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = badgeText,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF479C73)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


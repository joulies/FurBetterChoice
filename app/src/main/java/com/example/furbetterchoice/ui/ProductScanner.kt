package com.example.furbetterchoice.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.furbetterchoice.api.ContentModerationFilter
import com.example.furbetterchoice.api.LabelAiCorrectionService
import com.example.furbetterchoice.api.LabelTextLanguageFilter
import com.example.furbetterchoice.api.NutrientValue
import com.example.furbetterchoice.api.Product
import com.example.furbetterchoice.ui.theme.FurBetterChoiceTheme
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

private val ZOOM_PRESETS = listOf(
    "1x" to 0.0f,
    "1.5x" to 0.32f,
    "2x" to 0.58f
)

@OptIn(ExperimentalGetImage::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductScanner(
    viewModel: AppViewModel,
    onClose: () -> Unit,
    onProductClick: (String) -> Unit = {},
    onCompareClick: (String) -> Unit = {},
    onNavigateToLogin: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isInspectionMode = LocalInspectionMode.current
    val coroutineScope = rememberCoroutineScope()

    val session by viewModel.scanSessionState.collectAsState()
    val isUserLoggedIn by viewModel.isUserLoggedIn.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            if (isInspectionMode) true
            else ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!isInspectionMode && !hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var isTorchOn by remember { mutableStateOf(false) }
    var zoomPresetIndex by remember { mutableIntStateOf(0) }
    var guideRectInPreviewState by remember { mutableStateOf<Rect?>(null) }
    var livePreviewNutrients by remember(session.scannedBarcode) {
        mutableStateOf<Map<String, NutrientValue>>(emptyMap())
    }
    var livePreviewIngredientsText by remember(session.scannedBarcode) {
        mutableStateOf("")
    }

    val previewWindowRectRef = remember { AtomicReference<Rect?>(null) }
    val guideWindowRectRef = remember { AtomicReference<Rect?>(null) }
    val previewPixelSizeRef = remember { AtomicReference(IntSize.Zero) }
    val latestGuideBoxRef = remember { AtomicReference<Rect?>(null) }
    val latestIsAiProcessing by rememberUpdatedState(session.isAiProcessing)

    val recomputeGuideBoxInPreview: () -> Unit = {
        val pRect = previewWindowRectRef.get()
        val gRect = guideWindowRectRef.get()
        if (pRect != null && gRect != null && !pRect.isEmpty && !gRect.isEmpty) {
            val left = (gRect.left - pRect.left).coerceAtLeast(0)
            val top = (gRect.top - pRect.top).coerceAtLeast(0)
            val right = (gRect.right - pRect.left).coerceAtMost(pRect.width())
            val bottom = (gRect.bottom - pRect.top).coerceAtMost(pRect.height())
            if (right > left && bottom > top) {
                val rect = Rect(left, top, right, bottom)
                latestGuideBoxRef.set(rect)
                guideRectInPreviewState = rect
            }
        }
    }

    // Sheet & Product Analysis Display States
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    var selectedTab by remember { mutableStateOf(ProductTab.DESCRIPTION) }
    var selectedNutrient by remember { mutableStateOf("Protein") }

    // Edit Dialogs & Save Confirmation States
    var showEditInfoDialog by remember { mutableStateOf(false) }
    var editNutrientsFocusKey by remember { mutableStateOf<String?>(null) }
    var showEditNutrientsDialog by remember { mutableStateOf(false) }
    var showEditIngredientsDialog by remember { mutableStateOf(false) }
    var showSaveConfirmationDialog by remember { mutableStateOf(false) }
    var showLoginRequiredDialog by remember { mutableStateOf(false) }
    var loginRequiredActionText by remember { mutableStateOf("save scanned products permanently to the database") }
    var showSaveSuccessBanner by remember { mutableStateOf(false) }
    var showSavedToProfileToast by remember { mutableStateOf(false) }

    val savedProducts by viewModel.savedProducts.collectAsState()

    // Reset sheet to peeked state whenever a new barcode is scanned so user sees the peek header first
    LaunchedEffect(session.scannedBarcode) {
        if (session.scannedBarcode.isNotBlank()) {
            runCatching { sheetState.partialExpand() }
        }
    }

    // CameraX Controller
    val cameraController = remember(isInspectionMode) {
        if (isInspectionMode) null
        else LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS or CameraController.IMAGE_CAPTURE)
            isTapToFocusEnabled = true
        }
    }

    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val sharedTextClient = remember(isInspectionMode) {
        if (isInspectionMode) null else TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { cameraController?.clearImageAnalysisAnalyzer() }
            runCatching { cameraController?.unbind() }
            runCatching { sharedTextClient?.close() }
            runCatching { captureExecutor.shutdown() }
            runCatching { analysisExecutor.shutdown() }
        }
    }

    // Toggle torch cleanly without exposing Guava ListenableFuture to Compose preview compiler
    LaunchedEffect(isTorchOn, cameraController) {
        val controller = cameraController ?: return@LaunchedEffect
        runCatching {
            CameraController::class.java
                .getMethod("enableTorch", Boolean::class.javaPrimitiveType)
                .invoke(controller, isTorchOn)
        }
    }

    // Apply 1x / 1.5x / 2x linear zoom cleanly via reflection
    LaunchedEffect(zoomPresetIndex, cameraController) {
        val controller = cameraController ?: return@LaunchedEffect
        val linearZoom = ZOOM_PRESETS.getOrNull(zoomPresetIndex)?.second ?: 0f
        runCatching {
            CameraController::class.java
                .getMethod("setLinearZoom", Float::class.javaPrimitiveType)
                .invoke(controller, linearZoom)
        }
    }

    // High-res still capture button action (strictly cropped to the exact green targeting frame + Multimodal Gemini Vision)
    val triggerHighResCapture: () -> Unit = {
        val controller = cameraController
        val step = session.scanStep
        if (controller != null && !session.isAiProcessing &&
            (step == SmartScanStep.SCANNING_FRONT_OF_BAG ||
                step == SmartScanStep.SCANNING_NUTRIENTS ||
                step == SmartScanStep.SCANNING_INGREDIENTS)
        ) {
            viewModel.setAiProcessing(true, "Scanning...")
            controller.takePicture(
                captureExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    @OptIn(ExperimentalGetImage::class)
                    override fun onCaptureSuccess(imageProxy: ImageProxy) {
                        val mediaImage = imageProxy.image
                        if (mediaImage == null) {
                            imageProxy.close()
                            viewModel.setAiProcessing(false, "Could not capture image. Please try again.")
                            return
                        }
                        val rotation = imageProxy.imageInfo.rotationDegrees
                        val rotatedWidth = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
                        val rotatedHeight = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height
                        val pSize = previewPixelSizeRef.get()
                        val imageGuideBox = LabelTextLanguageFilter.mapPreviewRectToImageRect(
                            guideRectInPreview = latestGuideBoxRef.get(),
                            previewWidth = pSize.width,
                            previewHeight = pSize.height,
                            imageWidth = rotatedWidth,
                            imageHeight = rotatedHeight
                        )
                        if (imageGuideBox == null || imageGuideBox.isEmpty) {
                            imageProxy.close()
                            viewModel.setAiProcessing(
                                false,
                                "Align the label inside the green frame and tap Capture."
                            )
                            return
                        }
                        val croppedBitmap = extractRotatedAndCroppedBitmap(imageProxy, imageGuideBox)
                        if (croppedBitmap == null) {
                            imageProxy.close()
                            viewModel.setAiProcessing(
                                false,
                                "Could not crop green frame. Please try again."
                            )
                            return
                        }
                        // Strict rule: run OCR and Gemini Vision ONLY on the bitmap cropped strictly to the green frame!
                        val croppedInputImage = InputImage.fromBitmap(croppedBitmap, 0)
                        val croppedBounds = Rect(0, 0, croppedBitmap.width, croppedBitmap.height)
                        val recognizer = sharedTextClient
                        if (recognizer == null) {
                            imageProxy.close()
                            viewModel.setAiProcessing(false)
                            return
                        }
                        coroutineScope.launch(Dispatchers.Default) {
                            try {
                                val visionText = recognizer.process(croppedInputImage).await()
                                val croppedBlocks = LabelTextLanguageFilter.filterBlocksInsideGuideBox(
                                    visionText,
                                    croppedBounds
                                )
                                if (croppedBlocks.isEmpty()) {
                                    viewModel.setAiProcessing(
                                        false,
                                        "No text found inside the green frame. Center the label inside the box and tap Capture."
                                    )
                                    return@launch
                                }
                                val langFiltered = LabelTextLanguageFilter.prioritizeEnglishBlocks(croppedBlocks)
                                when (step) {
                                    SmartScanStep.SCANNING_FRONT_OF_BAG -> {
                                        val frontRes = LabelAiCorrectionService.parseAndCorrectFrontOfBag(
                                            rawEnglishText = langFiltered.prioritizedText,
                                            croppedBitmap = croppedBitmap
                                        )
                                        viewModel.applyFrontOfBagScanResult(
                                            context, frontRes, langFiltered.detectedPrimaryLanguage, langFiltered.isEnglishPrioritized, croppedBitmap
                                        )
                                    }
                                    SmartScanStep.SCANNING_NUTRIENTS -> {
                                        val nutRes = LabelAiCorrectionService.parseAndCorrectNutrients(
                                            rawEnglishText = langFiltered.prioritizedText,
                                            croppedBitmap = croppedBitmap
                                        )
                                        viewModel.applyScannedNutrientsResult(
                                            context, nutRes, langFiltered.detectedPrimaryLanguage, langFiltered.isEnglishPrioritized, croppedBitmap
                                        )
                                    }
                                    SmartScanStep.SCANNING_INGREDIENTS -> {
                                        val ingRes = LabelAiCorrectionService.autocorrectIngredients(
                                            rawEnglishText = langFiltered.prioritizedText,
                                            croppedBitmap = croppedBitmap
                                        )
                                        viewModel.applyScannedIngredientsResult(
                                            context, ingRes, langFiltered.detectedPrimaryLanguage, langFiltered.isEnglishPrioritized, croppedBitmap
                                        )
                                    }
                                    else -> viewModel.setAiProcessing(false)
                                }
                            } catch (e: Exception) {
                                Log.e("ProductScanner", "High-res OCR failed", e)
                                viewModel.setAiProcessing(false, "Could not read label clearly. Hold steady or turn on Flash.")
                            } finally {
                                imageProxy.close()
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e("ProductScanner", "Image capture error", exception)
                        viewModel.setAiProcessing(false, "Capture failed. Hold steady and try again.")
                    }
                }
            )
        }
    }

    // Bind CameraX & Switch between Barcode Scanner and Live OCR Text Recognition
    // Uses direct ImageAnalysis.Analyzer with InputImage.fromMediaImage so barcode rescanning
    // ("Scan Another Barcode") always works immediately without exiting the camera, and
    // strictly enforces that ONLY barcodes/text inside the green frame (imageGuideBox) are valid.
    if (hasCameraPermission && cameraController != null) {
        val currentStep = session.scanStep
        val isSheetExpanded = sheetState.currentValue == SheetValue.Expanded

        DisposableEffect(cameraController, lifecycleOwner, currentStep, isSheetExpanded, session.scannedBarcode) {
            runCatching { cameraController.bindToLifecycle(lifecycleOwner) }
            val mainExecutor = ContextCompat.getMainExecutor(context)

            if (currentStep == SmartScanStep.BARCODE_SCANNING) {
                val hasScannedBarcode = AtomicBoolean(false)
                val barcodeClient = BarcodeScanning.getClient(
                    BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(
                            Barcode.FORMAT_EAN_13,
                            Barcode.FORMAT_EAN_8,
                            Barcode.FORMAT_UPC_A,
                            Barcode.FORMAT_UPC_E,
                            Barcode.FORMAT_CODE_128,
                            Barcode.FORMAT_CODE_39,
                            Barcode.FORMAT_QR_CODE
                        )
                        .build()
                )
                cameraController.setImageAnalysisAnalyzer(analysisExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage == null || hasScannedBarcode.get()) {
                        imageProxy.close()
                        return@setImageAnalysisAnalyzer
                    }
                    val rotation = imageProxy.imageInfo.rotationDegrees
                    val rotatedWidth = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
                    val rotatedHeight = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height
                    val pSize = previewPixelSizeRef.get()
                    val imageGuideBox = LabelTextLanguageFilter.mapPreviewRectToImageRect(
                        guideRectInPreview = latestGuideBoxRef.get(),
                        previewWidth = pSize.width,
                        previewHeight = pSize.height,
                        imageWidth = rotatedWidth,
                        imageHeight = rotatedHeight
                    )
                    if (imageGuideBox == null || imageGuideBox.isEmpty) {
                        imageProxy.close()
                        return@setImageAnalysisAnalyzer
                    }
                    val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
                    barcodeClient.process(inputImage)
                        .addOnSuccessListener(analysisExecutor) { barcodes ->
                            if (hasScannedBarcode.get()) return@addOnSuccessListener
                            // Strict rule: only accept a barcode whose bounding box is inside the green frame!
                            val barcodeValue = barcodes.firstOrNull { barcode ->
                                !barcode.rawValue.isNullOrBlank() &&
                                    LabelTextLanguageFilter.isBarcodeInsideGuideBox(
                                        barcode.boundingBox,
                                        imageGuideBox
                                    )
                            }?.rawValue?.trim()
                            if (!barcodeValue.isNullOrEmpty() && hasScannedBarcode.compareAndSet(false, true)) {
                                val barcodeBitmap = extractRotatedAndCroppedBitmap(imageProxy, imageGuideBox)
                                mainExecutor.execute {
                                    runCatching { cameraController.clearImageAnalysisAnalyzer() }
                                    viewModel.startBarcodeScanSession(context, barcodeValue, barcodeBitmap)
                                }
                            }
                        }
                        .addOnCompleteListener(analysisExecutor) {
                            imageProxy.close()
                        }
                }
                onDispose {
                    runCatching { cameraController.clearImageAnalysisAnalyzer() }
                    runCatching { barcodeClient.close() }
                }
            } else if (!isSheetExpanded &&
                sharedTextClient != null &&
                (currentStep == SmartScanStep.SCANNING_FRONT_OF_BAG ||
                    currentStep == SmartScanStep.SCANNING_NUTRIENTS ||
                    currentStep == SmartScanStep.SCANNING_INGREDIENTS)
            ) {
                val textClient = sharedTextClient
                val frameBuffer = ArrayDeque<String>()
                val hasDispatchedConsensus = AtomicBoolean(false)
                val cooldownUntilMs = AtomicLong(0L)
                var lastAnalysisMs = 0L

                cameraController.setImageAnalysisAnalyzer(analysisExecutor) { imageProxy ->
                    val now = System.currentTimeMillis()
                    val mediaImage = imageProxy.image
                    if (mediaImage == null ||
                        now - lastAnalysisMs < 420L ||
                        now < cooldownUntilMs.get() ||
                        hasDispatchedConsensus.get() ||
                        latestIsAiProcessing
                    ) {
                        imageProxy.close()
                        return@setImageAnalysisAnalyzer
                    }
                    val rotation = imageProxy.imageInfo.rotationDegrees
                    val rotatedWidth = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
                    val rotatedHeight = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height
                    val pSize = previewPixelSizeRef.get()
                    val imageGuideBox = LabelTextLanguageFilter.mapPreviewRectToImageRect(
                        guideRectInPreview = latestGuideBoxRef.get(),
                        previewWidth = pSize.width,
                        previewHeight = pSize.height,
                        imageWidth = rotatedWidth,
                        imageHeight = rotatedHeight
                    )
                    if (imageGuideBox == null || imageGuideBox.isEmpty) {
                        imageProxy.close()
                        return@setImageAnalysisAnalyzer
                    }
                    lastAnalysisMs = now
                    val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
                    textClient.process(inputImage)
                        .addOnSuccessListener(mainExecutor) { visionText ->
                            if (hasDispatchedConsensus.get() || latestIsAiProcessing) return@addOnSuccessListener
                            // Strict rule: filterBlocksInsideGuideBox keeps ONLY words strictly inside imageGuideBox
                            val cropped = LabelTextLanguageFilter.filterBlocksInsideGuideBox(
                                visionText,
                                imageGuideBox
                            )
                            if (cropped.isEmpty()) return@addOnSuccessListener

                            val rawFrame = cropped.joinToString("\n") { it.text }
                            val sectionSplit = LabelAiCorrectionService.classifyAndSplitSections(rawFrame)
                            val looksRelevant = when (currentStep) {
                                SmartScanStep.SCANNING_NUTRIENTS -> {
                                    if (sectionSplit.nutrientSectionText.isNotBlank()) {
                                        val fastLocal = LabelAiCorrectionService.parseNutrientsLocally(
                                            sectionSplit.nutrientSectionText
                                        )
                                        if (fastLocal.nutrition.isNotEmpty()) {
                                            livePreviewNutrients = livePreviewNutrients + fastLocal.nutrition
                                            true
                                        } else {
                                            false
                                        }
                                    } else {
                                        false
                                    }
                                }
                                SmartScanStep.SCANNING_INGREDIENTS -> {
                                    if (sectionSplit.ingredientSectionText.isNotBlank()) {
                                        val fastIng = LabelAiCorrectionService.autocorrectIngredientsLocally(
                                            sectionSplit.ingredientSectionText
                                        )
                                        val validatedList = LabelAiCorrectionService.extractValidatedIngredientsList(
                                            fastIng.ingredientsText
                                        )
                                        if (validatedList.size >= 3) {
                                            livePreviewIngredientsText = fastIng.ingredientsText
                                            true
                                        } else {
                                            false
                                        }
                                    } else {
                                        false
                                    }
                                }
                                SmartScanStep.SCANNING_FRONT_OF_BAG ->
                                    rawFrame.length >= 10
                                else -> false
                            }
                            if (!looksRelevant) return@addOnSuccessListener

                            frameBuffer.addLast(rawFrame)
                            if (frameBuffer.size > 4) frameBuffer.removeFirst()

                            if (frameBuffer.size >= 3 && hasDispatchedConsensus.compareAndSet(false, true)) {
                                val consensusFrames = frameBuffer.toList()
                                frameBuffer.clear()
                                val previewFallbackBitmap = extractRotatedAndCroppedBitmap(imageProxy, imageGuideBox)
                                val qualityHint = previewFallbackBitmap?.let(::assessFrameQualityHint)
                                if (qualityHint != null) {
                                    cooldownUntilMs.set(System.currentTimeMillis() + 1200L)
                                    hasDispatchedConsensus.set(false)
                                    viewModel.setAiProcessing(false, qualityHint)
                                    return@addOnSuccessListener
                                }
                                coroutineScope.launch {
                                    try {
                                        viewModel.setAiProcessing(true, "Capturing label...")
                                        val hiResBitmap = captureHighResCroppedBitmapAsync(
                                            controller = cameraController,
                                            executor = captureExecutor,
                                            guideRectInPreview = latestGuideBoxRef.get(),
                                            previewSize = previewPixelSizeRef.get()
                                        )
                                        val finalBitmap = hiResBitmap ?: previewFallbackBitmap
                                        val allFrames = if (hiResBitmap != null) {
                                            val hiResLines = runCatching {
                                                val hiResVision = textClient.process(InputImage.fromBitmap(hiResBitmap, 0)).await()
                                                LabelTextLanguageFilter.filterBlocksInsideGuideBox(
                                                    hiResVision,
                                                    Rect(0, 0, hiResBitmap.width, hiResBitmap.height)
                                                ).joinToString("\n") { it.text }
                                            }.getOrDefault("")
                                            if (hiResLines.isNotBlank()) consensusFrames + hiResLines else consensusFrames
                                        } else {
                                            consensusFrames
                                        }
                                        val mergedRaw = LabelAiCorrectionService.mergeConsensusFrames(allFrames)
                                        if (mergedRaw.isBlank()) {
                                            hasDispatchedConsensus.set(false)
                                            viewModel.setAiProcessing(false)
                                            return@launch
                                        }
                                        val langFiltered = LabelTextLanguageFilter.prioritizeEnglishBlocks(
                                            listOf(com.example.furbetterchoice.api.TextBlockCandidate(mergedRaw, null))
                                        )
                                        when (currentStep) {
                                            SmartScanStep.SCANNING_FRONT_OF_BAG -> {
                                                val frontRes = LabelAiCorrectionService.parseAndCorrectFrontOfBag(
                                                    rawEnglishText = langFiltered.prioritizedText,
                                                    croppedBitmap = finalBitmap
                                                )
                                                viewModel.applyFrontOfBagScanResult(
                                                    context, frontRes, langFiltered.detectedPrimaryLanguage, langFiltered.isEnglishPrioritized, finalBitmap
                                                )
                                            }
                                            SmartScanStep.SCANNING_NUTRIENTS -> {
                                                val nutRes = LabelAiCorrectionService.parseAndCorrectNutrients(
                                                    rawEnglishText = langFiltered.prioritizedText,
                                                    croppedBitmap = finalBitmap
                                                )
                                                viewModel.applyScannedNutrientsResult(
                                                    context, nutRes, langFiltered.detectedPrimaryLanguage, langFiltered.isEnglishPrioritized, finalBitmap
                                                )
                                            }
                                            SmartScanStep.SCANNING_INGREDIENTS -> {
                                                val ingRes = LabelAiCorrectionService.autocorrectIngredients(
                                                    rawEnglishText = langFiltered.prioritizedText,
                                                    croppedBitmap = finalBitmap
                                                )
                                                viewModel.applyScannedIngredientsResult(
                                                    context, ingRes, langFiltered.detectedPrimaryLanguage, langFiltered.isEnglishPrioritized, finalBitmap
                                                )
                                            }
                                            else -> viewModel.setAiProcessing(false)
                                        }
                                    } finally {
                                        cooldownUntilMs.set(System.currentTimeMillis() + 1400L)
                                        hasDispatchedConsensus.set(false)
                                    }
                                }
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                }
                onDispose {
                    runCatching { cameraController.clearImageAnalysisAnalyzer() }
                }
            } else {
                cameraController.clearImageAnalysisAnalyzer()
                onDispose { }
            }
        }
    }

    // Back button collapses expanded sheet first, otherwise closes scanner
    BackHandler {
        if (sheetState.currentValue == SheetValue.Expanded && session.workingProduct != null) {
            coroutineScope.launch { sheetState.partialExpand() }
        } else {
            viewModel.resetScanSession()
            onClose()
        }
    }

    val workingProduct = session.workingProduct
    val hasSheet = workingProduct != null &&
        session.scanStep != SmartScanStep.PROMPT_NEW_PRODUCT &&
        session.scanStep != SmartScanStep.CHOOSING_FIRST_TARGET
    // User can only edit and submit when scanning a new product that does not exist in the database
    val isEditableNewProduct = session.baseDatabaseProduct == null && !session.isSavedToDatabase
    val isProductInDatabase = session.baseDatabaseProduct != null || session.isSavedToDatabase
    val isSavedToProfile = remember(savedProducts, workingProduct) {
        if (workingProduct == null) false
        else savedProducts.any { saved ->
            (workingProduct.barcode.isNotBlank() && saved.barcode == workingProduct.barcode) ||
                (workingProduct.productId.isNotBlank() && saved.productId == workingProduct.productId)
        }
    }
    var maxSheetOffsetPx by remember { mutableFloatStateOf(0f) }
    val swipeExpandProgress by remember {
        derivedStateOf {
            val offset = runCatching { sheetState.requireOffset() }.getOrElse { Float.NaN }
            if (!offset.isNaN() && offset > maxSheetOffsetPx) {
                maxSheetOffsetPx = offset
            }
            when {
                offset.isNaN() || maxSheetOffsetPx <= 1f -> {
                    if (sheetState.currentValue == SheetValue.Expanded ||
                        sheetState.targetValue == SheetValue.Expanded
                    ) 1f else 0f
                }
                else -> (1f - (offset / maxSheetOffsetPx)).coerceIn(0f, 1f)
            }
        }
    }
    val isSheetFullyExpanded = (sheetState.currentValue == SheetValue.Expanded &&
        sheetState.targetValue == SheetValue.Expanded) || swipeExpandProgress >= 0.98f
    val sheetTopCornerRadius by animateDpAsState(
        targetValue = if (isSheetFullyExpanded) 0.dp else 28.dp,
        animationSpec = tween(220),
        label = "sheetTopCornerRadius"
    )

    val boxColor = if (isDarkMode) Color(0xFF1E2422) else Color(0xFFFFFEFE)
    val highlightColor = if (isDarkMode) MaterialTheme.colorScheme.outline.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.7f)
    val boxBorder = BorderStroke(1.dp, Brush.horizontalGradient(listOf(boxColor, highlightColor, boxColor)))

    val allTextModeration = remember(
        workingProduct?.productName,
        workingProduct?.brands,
        workingProduct?.foodType,
        workingProduct?.ingredientsText
    ) {
        if (workingProduct == null) com.example.furbetterchoice.api.ModerationResult.CLEAN
        else ContentModerationFilter.analyze(
            listOf(
                workingProduct.productName,
                workingProduct.brands,
                workingProduct.foodType,
                workingProduct.ingredientsText
            ).joinToString(" ")
        )
    }

    // Persistent BottomSheetScaffold so PreviewView is never unmounted/remounted when hasSheet changes
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = if (hasSheet) 118.dp else 0.dp,
            sheetSwipeEnabled = hasSheet,
            sheetShape = RoundedCornerShape(topStart = sheetTopCornerRadius, topEnd = sheetTopCornerRadius),
            sheetContainerColor = MaterialTheme.colorScheme.background,
            sheetContentColor = MaterialTheme.colorScheme.onBackground,
            sheetDragHandle = if (hasSheet) {
                {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isSheetFullyExpanded) Modifier.statusBarsPadding() else Modifier)
                            .padding(top = 10.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(44.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(100.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                        )
                    }
                }
            } else null,
            sheetContent = {
                if (hasSheet && workingProduct != null) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        ScannedSheetPeekHeader(
                            session = session,
                            swipeExpandProgress = swipeExpandProgress,
                            showSaveToProfileButton = isProductInDatabase,
                            isSavedToProfile = isSavedToProfile,
                            onToggleSaveToProfile = {
                                if (!isUserLoggedIn) {
                                    loginRequiredActionText = "save products to your profile"
                                    showLoginRequiredDialog = true
                                } else {
                                    val alreadySaved = isSavedToProfile
                                    viewModel.toggleSaveProduct(workingProduct)
                                    if (!alreadySaved) {
                                        showSavedToProfileToast = true
                                    }
                                }
                            },
                            onCompareClick = {
                                val code = workingProduct.barcode.ifBlank {
                                    workingProduct.productId.ifBlank { session.scannedBarcode }
                                }
                                if (code.isNotBlank()) {
                                    onCompareClick(code)
                                }
                            },
                            onRetrySave = {
                                if (!isUserLoggedIn) {
                                    loginRequiredActionText = "save scanned products permanently to the database"
                                    showLoginRequiredDialog = true
                                } else {
                                    viewModel.saveScannedProductToDatabase(context) { res ->
                                        if (res.isSuccess) showSaveSuccessBanner = true
                                    }
                                }
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        Box(modifier = Modifier.weight(1f)) {
                            ProductAnalysisContentBody(
                                product = workingProduct,
                                barcode = workingProduct.barcode.ifBlank { session.scannedBarcode },
                                alternatives = session.alternatives,
                                viewModel = viewModel,
                                selectedTab = selectedTab,
                                onTabSelected = { selectedTab = it },
                                selectedNutrient = selectedNutrient,
                                onNutrientClick = { selectedNutrient = if (selectedNutrient == it) "" else it },
                                boxColor = boxColor,
                                boxBorder = boxBorder,
                                isEditModeEnabled = isEditableNewProduct,
                                lowConfidenceNutrientKeys = if (isEditableNewProduct) session.lowConfidenceNutrientKeys else emptySet(),
                                onEditProductInfo = {
                                    if (isEditableNewProduct) showEditInfoDialog = true
                                },
                                onEditNutrients = { key ->
                                    if (isEditableNewProduct) {
                                        editNutrientsFocusKey = key
                                        showEditNutrientsDialog = true
                                    }
                                },
                                onEditIngredients = {
                                    if (isEditableNewProduct) showEditIngredientsDialog = true
                                },
                                onProductClick = onProductClick,
                                onCompareClick = onCompareClick,
                                onNavigateToLogin = onNavigateToLogin,
                                bottomSpacerHeight = 24.dp,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(top = 12.dp)
                            )
                        }

                        // Only show the Edit/Submit to Database bottom bar when scanning a NEW product not in the database
                        if (isEditableNewProduct) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 4.dp,
                                shadowElevation = 8.dp
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .navigationBarsPadding()
                                        .padding(horizontal = 20.dp, vertical = 12.dp)
                                ) {
                                    if (allTextModeration.containsBadWords) {
                                        ModerationWarningBanner(
                                            moderation = allTextModeration,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            if (!isUserLoggedIn) {
                                                loginRequiredActionText = "save scanned products permanently to the database"
                                                showLoginRequiredDialog = true
                                            } else if (workingProduct.imageUrl.isNullOrBlank() && session.pendingFrontPhotoUri.isNullOrBlank()) {
                                                showEditInfoDialog = true
                                            } else {
                                                showSaveConfirmationDialog = true
                                            }
                                        },
                                        enabled = !session.isSaving && !allTextModeration.containsBadWords,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp),
                                        shape = RoundedCornerShape(100.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF479C73)
                                        )
                                    ) {
                                        if (session.isSaving) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Saving to Database...", color = Color.White, fontWeight = FontWeight.Bold)
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Save Product to Database",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(1.dp))
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                ScannerCameraViewport(
                    hasCameraPermission = hasCameraPermission,
                    isInspectionMode = isInspectionMode,
                    cameraController = cameraController,
                    session = session,
                    livePreviewNutrients = livePreviewNutrients,
                    livePreviewIngredientsText = livePreviewIngredientsText,
                    guideRectInPreview = guideRectInPreviewState,
                    isTorchOn = isTorchOn,
                    onToggleTorch = { isTorchOn = !isTorchOn },
                    onSelectEntryMode = { mode -> viewModel.selectScannerEntryMode(mode) },
                    onProceedToScanNewProduct = { viewModel.proceedToScanNewProduct() },
                    onOpenNutrientEditor = { key ->
                        if (isEditableNewProduct && !session.isAiProcessing) {
                            editNutrientsFocusKey = key
                            showEditNutrientsDialog = true
                        }
                    },
                    onClose = {
                        viewModel.resetScanSession()
                        onClose()
                    },
                    onPreviewBoundsChanged = { rect, size ->
                        previewWindowRectRef.set(rect)
                        previewPixelSizeRef.set(size)
                        recomputeGuideBoxInPreview()
                    },
                    onGuideFrameBoundsChanged = { rect ->
                        guideWindowRectRef.set(rect)
                        recomputeGuideBoxInPreview()
                    },
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onRescanBarcode = { viewModel.resetScanSession(keepEntryMode = true) },
                    onSelectStep = { step, msg -> viewModel.setScanStep(step, msg) },
                    onCaptureNow = triggerHighResCapture
                )
            }
        }

        // Save to database success toast
        AnimatedVisibility(
            visible = showSaveSuccessBanner,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 68.dp)
        ) {
            LaunchedEffect(showSaveSuccessBanner) {
                if (showSaveSuccessBanner) {
                    kotlinx.coroutines.delay(3000)
                    showSaveSuccessBanner = false
                }
            }
            Surface(
                color = Color(0xFF2E7D32),
                shape = RoundedCornerShape(100.dp),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Product permanently saved to database!",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Save to My Saved (Profile) toast
        AnimatedVisibility(
            visible = showSavedToProfileToast,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
        ) {
            LaunchedEffect(showSavedToProfileToast) {
                if (showSavedToProfileToast) {
                    kotlinx.coroutines.delay(2500)
                    showSavedToProfileToast = false
                }
            }
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
    }

    // Pencil Edit Dialogs (only for new products not in database)
    if (showEditInfoDialog && workingProduct != null && isEditableNewProduct) {
        EditScannedProductInfoDialog(
            product = workingProduct,
            pendingFrontPhotoUri = session.pendingFrontPhotoUri,
            pendingNutritionPhotoUri = session.pendingNutritionPhotoUri,
            pendingIngredientsPhotoUri = session.pendingIngredientsPhotoUri,
            pendingBarcodePhotoUri = session.pendingBarcodePhotoUri,
            onDismiss = { showEditInfoDialog = false },
            onApply = { name, brand, pet, lifeStage, foodType, frontPhoto, nutPhoto, ingPhoto, barcodePhoto, pkgType, pkgMaterial, pkgRecyclability, pkgBiodegradability ->
                viewModel.updateScannedProductInfo(
                    context, name, brand, pet, lifeStage, foodType,
                    frontPhoto, nutPhoto, ingPhoto, barcodePhoto,
                    pkgType, pkgMaterial, pkgRecyclability, pkgBiodegradability
                )
                showEditInfoDialog = false
            }
        )
    }

    if (showEditNutrientsDialog && workingProduct != null && isEditableNewProduct) {
        EditScannedNutrientsDialog(
            product = workingProduct,
            lowConfidenceKeys = session.lowConfidenceNutrientKeys,
            focusNutrientKey = editNutrientsFocusKey,
            referencePhotoUrl = workingProduct.nutritionPhotoUrl?.takeIf { it.isNotBlank() }
                ?: session.pendingNutritionPhotoUri,
            onDismiss = { showEditNutrientsDialog = false },
            onApply = { nutritionMap, me, meUnit ->
                viewModel.updateScannedNutrients(context, nutritionMap, me, meUnit)
                showEditNutrientsDialog = false
            }
        )
    }

    if (showEditIngredientsDialog && workingProduct != null && isEditableNewProduct) {
        EditScannedIngredientsDialog(
            product = workingProduct,
            referencePhotoUrl = workingProduct.ingredientsPhotoUrl?.takeIf { it.isNotBlank() }
                ?: session.pendingIngredientsPhotoUri,
            onDismiss = { showEditIngredientsDialog = false },
            onApply = { newIngredients ->
                viewModel.updateScannedIngredients(context, newIngredients)
                showEditIngredientsDialog = false
            }
        )
    }

    if (showSaveConfirmationDialog && workingProduct != null && isEditableNewProduct) {
        var potentialDuplicates by remember(workingProduct) { mutableStateOf<List<Product>>(emptyList()) }
        LaunchedEffect(showSaveConfirmationDialog, workingProduct.productName, workingProduct.brands) {
            potentialDuplicates = if (workingProduct.productName.isNotBlank()) {
                viewModel.findPotentialDuplicateProducts(
                    brand = workingProduct.brands,
                    productName = workingProduct.productName,
                    excludeBarcode = workingProduct.barcode
                )
            } else {
                emptyList()
            }
        }
        SaveProductConfirmationDialog(
            baseProduct = session.baseDatabaseProduct,
            workingProduct = workingProduct,
            lowConfidenceKeys = session.lowConfidenceNutrientKeys,
            potentialDuplicates = potentialDuplicates,
            hasPendingFrontPhoto = !session.pendingFrontPhotoUri.isNullOrBlank(),
            hasPendingNutritionPhoto = !session.pendingNutritionPhotoUri.isNullOrBlank(),
            hasPendingIngredientsPhoto = !session.pendingIngredientsPhotoUri.isNullOrBlank(),
            hasPendingBarcodePhoto = !session.pendingBarcodePhotoUri.isNullOrBlank(),
            isSaving = session.isSaving,
            onRecheck = { showSaveConfirmationDialog = false },
            onConfirmSave = {
                viewModel.saveScannedProductToDatabase(context) { res ->
                    showSaveConfirmationDialog = false
                    if (res.isSuccess) {
                        showSaveSuccessBanner = true
                    }
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
            actionText = loginRequiredActionText
        )
    }
}

private fun extractRotatedAndCroppedBitmap(
    imageProxy: ImageProxy,
    cropRect: Rect?
): Bitmap? {
    return runCatching {
        val rawBitmap = imageProxy.toBitmap()
        val rotation = imageProxy.imageInfo.rotationDegrees
        val rotated = if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true).also {
                if (it != rawBitmap) rawBitmap.recycle()
            }
        } else {
            rawBitmap
        }
        if (cropRect != null && !cropRect.isEmpty) {
            val left = cropRect.left.coerceIn(0, rotated.width - 1)
            val top = cropRect.top.coerceIn(0, rotated.height - 1)
            val width = cropRect.width().coerceAtMost(rotated.width - left)
            val height = cropRect.height().coerceAtMost(rotated.height - top)
            if (width > 16 && height > 16) {
                Bitmap.createBitmap(rotated, left, top, width, height).also {
                    if (it != rotated) rotated.recycle()
                }
            } else {
                rotated
            }
        } else {
            rotated
        }
    }.getOrNull()
}

private fun assessFrameQualityHint(bitmap: Bitmap): String? {
    val w = bitmap.width
    val h = bitmap.height
    if (w < 32 || h < 32) return null
    val stepX = (w / 24).coerceAtLeast(1)
    val stepY = (h / 24).coerceAtLeast(1)
    var totalLuma = 0.0
    var glareCount = 0
    var sampleCount = 0
    var y = 0
    while (y < h) {
        var x = 0
        while (x < w) {
            val pixel = bitmap.getPixel(x, y)
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val luma = 0.299 * r + 0.587 * g + 0.114 * b
            totalLuma += luma
            if (luma > 246.0) glareCount++
            sampleCount++
            x += stepX
        }
        y += stepY
    }
    if (sampleCount == 0) return null
    val avgLuma = totalLuma / sampleCount
    val glareRatio = glareCount.toDouble() / sampleCount
    return when {
        avgLuma < 38.0 -> "Too dark inside frame — tap Flash for a clearer read."
        glareRatio > 0.55 -> "Strong glare detected — tilt the bag slightly."
        else -> null
    }
}

private suspend fun captureHighResCroppedBitmapAsync(
    controller: LifecycleCameraController,
    executor: java.util.concurrent.Executor,
    guideRectInPreview: Rect?,
    previewSize: IntSize
): Bitmap? = suspendCancellableCoroutine { cont ->
    controller.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                val bmp = runCatching {
                    val rotation = imageProxy.imageInfo.rotationDegrees
                    val rotatedWidth = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
                    val rotatedHeight = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height
                    val imageGuideBox = LabelTextLanguageFilter.mapPreviewRectToImageRect(
                        guideRectInPreview = guideRectInPreview,
                        previewWidth = previewSize.width,
                        previewHeight = previewSize.height,
                        imageWidth = rotatedWidth,
                        imageHeight = rotatedHeight
                    )
                    if (imageGuideBox != null && !imageGuideBox.isEmpty) {
                        extractRotatedAndCroppedBitmap(imageProxy, imageGuideBox)
                    } else {
                        null
                    }
                }.getOrNull()
                imageProxy.close()
                if (cont.isActive) cont.resume(bmp)
            }

            override fun onError(exception: ImageCaptureException) {
                if (cont.isActive) cont.resume(null)
            }
        }
    )
}

@Composable
private fun ScannerCameraViewport(
    hasCameraPermission: Boolean,
    isInspectionMode: Boolean,
    cameraController: LifecycleCameraController?,
    session: ScanSessionState,
    livePreviewNutrients: Map<String, NutrientValue> = emptyMap(),
    livePreviewIngredientsText: String = "",
    guideRectInPreview: Rect?,
    isTorchOn: Boolean,
    onToggleTorch: () -> Unit,
    onSelectEntryMode: (ScannerEntryMode) -> Unit,
    onProceedToScanNewProduct: () -> Unit,
    onOpenNutrientEditor: (String) -> Unit,
    onClose: () -> Unit,
    onPreviewBoundsChanged: (Rect, IntSize) -> Unit,
    onGuideFrameBoundsChanged: (Rect) -> Unit,
    onRequestPermission: () -> Unit,
    onRescanBarcode: () -> Unit,
    onSelectStep: (SmartScanStep, String) -> Unit,
    onCaptureNow: () -> Unit
) {
    val density = LocalDensity.current
    val infiniteTransition = rememberInfiniteTransition(label = "scannerInfinite")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserSweep"
    )
    val cornerPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.76f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cornerPulse"
    )

    val isFrontBagFixed = session.scanStep == SmartScanStep.SCANNING_FRONT_OF_BAG

    // User-resizable frame dimensions (fixed size when scanning Front Bag)
    val defaultWidthDp = when (session.scanStep) {
        SmartScanStep.BARCODE_SCANNING -> 284f
        SmartScanStep.SCANNING_FRONT_OF_BAG -> 270f
        else -> 320f
    }
    val defaultHeightDp = when (session.scanStep) {
        SmartScanStep.BARCODE_SCANNING -> 150f
        SmartScanStep.SCANNING_FRONT_OF_BAG -> 320f
        else -> 196f
    }

    var customWidthDp by remember(session.scanStep == SmartScanStep.BARCODE_SCANNING) {
        mutableFloatStateOf(defaultWidthDp)
    }
    var customHeightDp by remember(session.scanStep == SmartScanStep.BARCODE_SCANNING) {
        mutableFloatStateOf(defaultHeightDp)
    }

    // Fluid, bouncy spring animations for frame sizing (fixed portrait dimensions for Front Bag)
    val targetFrameWidth by animateDpAsState(
        targetValue = if (isFrontBagFixed) 270.dp else customWidthDp.coerceIn(140f, 388f).dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "frameWidth"
    )
    val targetFrameHeight by animateDpAsState(
        targetValue = if (isFrontBagFixed) 320.dp else customHeightDp.coerceIn(80f, 420f).dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "frameHeight"
    )

    var showDoneYellowGlow by remember { mutableStateOf(false) }
    LaunchedEffect(session.scanDoneNonce) {
        if (session.scanDoneNonce > 0L) {
            showDoneYellowGlow = true
            kotlinx.coroutines.delay(1450L)
            showDoneYellowGlow = false
        }
    }

    val frameAccentColor by animateColorAsState(
        targetValue = when {
            showDoneYellowGlow -> Color(0xFFFFD54F)
            session.isAiProcessing -> Color(0xFFD29524)
            session.scanStep == SmartScanStep.BARCODE_SCANNING -> Color(0xFF479C73)
            session.scanStep == SmartScanStep.SCANNING_NUTRIENTS -> Color(0xFF479C73)
            session.scanStep == SmartScanStep.SCANNING_INGREDIENTS -> Color(0xFF63B88C)
            else -> Color(0xFF479C73)
        },
        animationSpec = tween(260),
        label = "frameAccentColor"
    )

    val frameGlowAlpha by animateFloatAsState(
        targetValue = if (showDoneYellowGlow) 0.34f else 0f,
        animationSpec = tween(260),
        label = "frameGlowAlpha"
    )

    val showLaserSweep = !session.isAiProcessing && !showDoneYellowGlow &&
        (session.scanStep == SmartScanStep.BARCODE_SCANNING ||
            session.scanStep == SmartScanStep.SCANNING_FRONT_OF_BAG ||
            session.scanStep == SmartScanStep.SCANNING_NUTRIENTS ||
            session.scanStep == SmartScanStep.SCANNING_INGREDIENTS)

    val isNewProductActive = session.isNewProductFlow ||
        session.entryMode == ScannerEntryMode.SCAN_NEW_PRODUCT ||
        session.scanStep == SmartScanStep.SCANNING_NUTRIENTS ||
        session.scanStep == SmartScanStep.SCANNING_INGREDIENTS ||
        session.scanStep == SmartScanStep.CHOOSING_FIRST_TARGET

    val isStepSwitchingLocked = session.isAiProcessing ||
        session.scanStep == SmartScanStep.FETCHING_PRODUCT

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F110D))
    ) {
        if (hasCameraPermission && !isInspectionMode && cameraController != null) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coords ->
                        val b = coords.boundsInWindow()
                        onPreviewBoundsChanged(
                            Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt()),
                            coords.size
                        )
                    },
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        this.controller = cameraController
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                }
            )
        } else if (!hasCameraPermission) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Camera permission is required to scan barcodes and pet-food labels.",
                    color = Color(0xFFEAF5EE),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF479C73))
                ) {
                    Text("Grant Camera Permission", color = Color.White)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1F221B))
                    .onGloballyPositioned { coords ->
                        val b = coords.boundsInWindow()
                        onPreviewBoundsChanged(
                            Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt()),
                            coords.size
                        )
                    }
            )
        }

        // Dimmed Outside-Frame Cutout Overlay so only the inside of the targeting frame stays bright
        if (hasCameraPermission) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            ) {
                drawRect(color = Color(0xFF0F110D).copy(alpha = 0.58f))
                val rect = guideRectInPreview
                if (rect != null &&
                    !rect.isEmpty &&
                    session.scanStep != SmartScanStep.CHOOSING_FIRST_TARGET &&
                    session.scanStep != SmartScanStep.PROMPT_NEW_PRODUCT
                ) {
                    val cornerPx = 22.dp.toPx()
                    drawRoundRect(
                        color = Color.Transparent,
                        topLeft = Offset(rect.left.toFloat(), rect.top.toFloat()),
                        size = Size(rect.width().toFloat(), rect.height().toFloat()),
                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                        blendMode = BlendMode.Clear
                    )
                }
            }
        }

        // Clean Non-Overlapping Vertical Layout:
        // 1. Minimalist Top Bar (Back left, Scan Barcode / Scan New Product vertically aligned on same row, Flash icon right)
        // 2. Weight(1f) Center Viewport with Green Frame placed higher + Instruction Text + Centered Capture Button + Vertical Nutrient List
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 1. Minimalist Camera Top Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Single Horizontal Row: Back Button (Left) | "Scan Barcode" / "Scan New Product" (Center) | Flash Symbol (Right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Left: Back Button
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFEAF5EE),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Top Center: "Scan Barcode" or "Scan New Product" aligned on the exact same row
                    val headerTitle = if (isNewProductActive) "Scan New Product" else "Scan Barcode"
                    Text(
                        text = headerTitle,
                        color = Color(0xFFEAF5EE),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = !isStepSwitchingLocked) {
                                val nextMode = if (isNewProductActive) {
                                    ScannerEntryMode.SCAN_BARCODE
                                } else {
                                    ScannerEntryMode.SCAN_NEW_PRODUCT
                                }
                                onSelectEntryMode(nextMode)
                            }
                    )

                    // Top Right: Flash Icon Symbol
                    IconButton(
                        onClick = onToggleTorch,
                        modifier = Modifier.size(40.dp)
                    ) {
                        FlashSymbolIcon(
                            isTorchOn = isTorchOn,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Subtitle action directly below the header row
                AnimatedVisibility(
                    visible = isNewProductActive || session.scannedBarcode.isNotBlank(),
                    enter = fadeIn(tween(180)) + scaleIn(
                        initialScale = 0.9f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                    ),
                    exit = fadeOut(tween(140))
                ) {
                    Row(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clickable(enabled = !isStepSwitchingLocked) { onRescanBarcode() }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = Color(0xFF63B88C).copy(alpha = if (isStepSwitchingLocked) 0.45f else 1f),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "New Barcode",
                            color = Color(0xFF63B88C).copy(alpha = if (isStepSwitchingLocked) 0.45f else 1f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                AnimatedVisibility(
                    visible = !isNewProductActive &&
                        session.scannedBarcode.isBlank() &&
                        session.scanStep == SmartScanStep.BARCODE_SCANNING,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(140))
                ) {
                    Text(
                        text = "Tap here to scan new product",
                        color = Color(0xFFBFE3D1).copy(alpha = 0.82f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .clickable(enabled = !isStepSwitchingLocked) {
                                onSelectEntryMode(ScannerEntryMode.SCAN_NEW_PRODUCT)
                            }
                    )
                }

                // Simple Text-Only Step Switcher (Color-coded, locked while scanning/loading)
                AnimatedVisibility(
                    visible = session.scannedBarcode.isNotBlank() &&
                        session.scanStep != SmartScanStep.BARCODE_SCANNING &&
                        session.scanStep != SmartScanStep.FETCHING_PRODUCT &&
                        session.scanStep != SmartScanStep.PROMPT_NEW_PRODUCT &&
                        session.scanStep != SmartScanStep.CHOOSING_FIRST_TARGET,
                    enter = fadeIn(tween(220)),
                    exit = fadeOut(tween(160))
                ) {
                    Column(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        FreeOrderLabelStepBar(
                            currentStep = session.scanStep,
                            capturedNutrientCount = session.capturedNutrientKeys.size,
                            hasIngredients = session.workingProduct?.ingredientsText?.isNotBlank() == true,
                            hasFrontPhoto = session.workingProduct?.imageUrl?.isNotBlank() == true || !session.pendingFrontPhotoUri.isNullOrBlank(),
                            isLocked = isStepSwitchingLocked,
                            onSelectStep = onSelectStep
                        )
                    }
                }
            }

            // 2. Viewport Area (Frame positioned slightly higher for comfortable scanning & large resizing)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                AnimatedContent(
                    targetState = session.scanStep == SmartScanStep.PROMPT_NEW_PRODUCT ||
                        session.scanStep == SmartScanStep.CHOOSING_FIRST_TARGET,
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(
                            initialScale = 0.88f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )).togetherWith(
                            fadeOut(tween(150)) + scaleOut(targetScale = 0.92f)
                        )
                    },
                    label = "centerScannerStepContent"
                ) { showNewBarcodePrompt ->
                    if (showNewBarcodePrompt) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            NewBarcodeDetectedPromptCard(
                                onProceed = onProceedToScanNewProduct,
                                onRescanBarcode = onRescanBarcode
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .animateContentSize(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Top
                        ) {
                            // Placed slightly higher on the screen
                            Spacer(modifier = Modifier.height(18.dp))

                            // Targeting Frame (fixed for Front Bag, drag-resizable for barcode/labels)
                            Box(
                                modifier = Modifier
                                    .size(targetFrameWidth, targetFrameHeight)
                                    .onGloballyPositioned { coords ->
                                        val b = coords.boundsInWindow()
                                        onGuideFrameBoundsChanged(
                                            Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
                                        )
                                    }
                                    .then(
                                        if (!isFrontBagFixed) {
                                            Modifier.pointerInput(session.scanStep) {
                                                var dragSignX = 1f
                                                var dragSignY = 1f
                                                detectDragGestures(
                                                    onDragStart = { startOffset ->
                                                        dragSignX = if (startOffset.x < size.width / 2f) -1f else 1f
                                                        dragSignY = if (startOffset.y < size.height / 2f) -1f else 1f
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        val dxDp = with(density) { dragAmount.x.toDp().value } * dragSignX * 1.8f
                                                        val dyDp = with(density) { dragAmount.y.toDp().value } * dragSignY * 1.8f
                                                        customWidthDp = (customWidthDp + dxDp).coerceIn(140f, 388f)
                                                        customHeightDp = (customHeightDp + dyDp).coerceIn(80f, 420f)
                                                    }
                                                )
                                            }
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clip(RoundedCornerShape(22.dp))
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val cornerRadius = 22.dp.toPx()
                                    val strokeWidth = 2.dp.toPx()
                                    val bracketLen = 28.dp.toPx()
                                    val bracketStroke = 4.5.dp.toPx()

                                    // Outer border (glows yellow when a scan finishes)
                                    if (frameGlowAlpha > 0.01f) {
                                        drawRoundRect(
                                            color = Color(0xFFFFD54F).copy(alpha = frameGlowAlpha),
                                            cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                                        )
                                        drawRoundRect(
                                            color = Color(0xFFFFD54F).copy(alpha = 0.95f),
                                            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                                        )
                                    } else {
                                        drawRoundRect(
                                            color = Color(0xFFBFE3D1).copy(alpha = 0.55f),
                                            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                                        )
                                    }

                                    // Glowing corner brackets
                                    val bracketColor = if (showDoneYellowGlow) Color(0xFFFFD54F) else frameAccentColor.copy(alpha = cornerPulseAlpha)
                                    val w = size.width
                                    val h = size.height
                                    val inset = 3.dp.toPx()

                                    // Top-Left
                                    drawLine(bracketColor, Offset(inset, inset + bracketLen), Offset(inset, inset), bracketStroke, StrokeCap.Round)
                                    drawLine(bracketColor, Offset(inset, inset), Offset(inset + bracketLen, inset), bracketStroke, StrokeCap.Round)
                                    // Top-Right
                                    drawLine(bracketColor, Offset(w - inset - bracketLen, inset), Offset(w - inset, inset), bracketStroke, StrokeCap.Round)
                                    drawLine(bracketColor, Offset(w - inset, inset), Offset(w - inset, inset + bracketLen), bracketStroke, StrokeCap.Round)
                                    // Bottom-Left
                                    drawLine(bracketColor, Offset(inset, h - inset - bracketLen), Offset(inset, h - inset), bracketStroke, StrokeCap.Round)
                                    drawLine(bracketColor, Offset(inset, h - inset), Offset(inset + bracketLen, h - inset), bracketStroke, StrokeCap.Round)
                                    // Bottom-Right
                                    drawLine(bracketColor, Offset(w - inset - bracketLen, h - inset), Offset(w - inset, h - inset), bracketStroke, StrokeCap.Round)
                                    drawLine(bracketColor, Offset(w - inset, h - inset - bracketLen), Offset(w - inset, h - inset), bracketStroke, StrokeCap.Round)

                                    // Midpoint edge grip ticks only when frame is resizable
                                    if (!isFrontBagFixed) {
                                        val gripHalf = 12.dp.toPx()
                                        val gripColor = Color(0xFFEAF5EE).copy(alpha = 0.75f)
                                        drawLine(gripColor, Offset(w / 2f - gripHalf, inset), Offset(w / 2f + gripHalf, inset), 3.dp.toPx(), StrokeCap.Round)
                                        drawLine(gripColor, Offset(w / 2f - gripHalf, h - inset), Offset(w / 2f + gripHalf, h - inset), 3.dp.toPx(), StrokeCap.Round)
                                        drawLine(gripColor, Offset(inset, h / 2f - gripHalf), Offset(inset, h / 2f + gripHalf), 3.dp.toPx(), StrokeCap.Round)
                                        drawLine(gripColor, Offset(w - inset, h / 2f - gripHalf), Offset(w - inset, h / 2f + gripHalf), 3.dp.toPx(), StrokeCap.Round)
                                    }

                                    // Animated Laser Sweep Line
                                    if (showLaserSweep) {
                                        val y = h * laserProgress
                                        drawLine(
                                            brush = Brush.horizontalGradient(
                                                listOf(
                                                    Color.Transparent,
                                                    frameAccentColor.copy(alpha = 0.95f),
                                                    Color(0xFFEAF5EE).copy(alpha = 0.95f),
                                                    frameAccentColor.copy(alpha = 0.95f),
                                                    Color.Transparent
                                                )
                                            ),
                                            start = Offset(16.dp.toPx(), y),
                                            end = Offset(w - 16.dp.toPx(), y),
                                            strokeWidth = 2.5.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }

                                // Centered "Done!" Message inside the frame with brief yellow glow
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = showDoneYellowGlow && !session.isAiProcessing && session.scanStep != SmartScanStep.FETCHING_PRODUCT,
                                    enter = fadeIn(tween(180)) + scaleIn(
                                        initialScale = 0.82f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    ),
                                    exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.92f),
                                    modifier = Modifier.align(Alignment.Center)
                                ) {
                                    val doneTitle = when (session.lastCompletedTarget) {
                                        SmartScanStep.SCANNING_NUTRIENTS -> "Nutrients Done!"
                                        SmartScanStep.SCANNING_INGREDIENTS -> "Ingredients Done!"
                                        SmartScanStep.SCANNING_FRONT_OF_BAG -> "Front Bag Done!"
                                        else -> "Done!"
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(100.dp),
                                        color = Color(0xFF151A16).copy(alpha = 0.88f),
                                        border = BorderStroke(1.5.dp, Color(0xFFFFD54F))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = "Done",
                                                tint = Color(0xFFFFD54F),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = doneTitle,
                                                color = Color(0xFFFFF8E1),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                }

                                // Simplified Loading Overlay ("Scanning...")
                                if (session.isAiProcessing || session.scanStep == SmartScanStep.FETCHING_PRODUCT) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFF0F110D).copy(alpha = 0.65f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator(
                                                color = Color(0xFFEAF5EE),
                                                strokeWidth = 2.5.dp,
                                                modifier = Modifier.size(28.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Scanning...",
                                                color = Color(0xFFFFD54F),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Smaller, clean instruction text
                            val promptText = when {
                                session.isAiProcessing || session.scanStep == SmartScanStep.FETCHING_PRODUCT ->
                                    "Scanning..."
                                session.scanStep != SmartScanStep.BARCODE_SCANNING &&
                                    !session.statusMessage.isNullOrBlank() -> session.statusMessage
                                session.scanStep == SmartScanStep.BARCODE_SCANNING ->
                                    "Align product barcode inside the frame"
                                session.scanStep == SmartScanStep.SCANNING_FRONT_OF_BAG ->
                                    "Fit the front of the bag inside the box"
                                session.scanStep == SmartScanStep.SCANNING_NUTRIENTS ->
                                    "Fit Guaranteed Analysis inside the box"
                                session.scanStep == SmartScanStep.SCANNING_INGREDIENTS ->
                                    "Fit the Ingredients list inside the box"
                                else ->
                                    "Swipe up the sheet below to review FEDIAF/AAFCO & edit"
                            }

                            val promptTextColor by animateColorAsState(
                                targetValue = when {
                                    session.isAiProcessing || session.scanStep == SmartScanStep.FETCHING_PRODUCT ->
                                        Color(0xFFFFD54F)
                                    promptText.contains("Done", ignoreCase = true) ||
                                        promptText.contains("captured", ignoreCase = true) ||
                                        promptText.contains("ready", ignoreCase = true) ||
                                        promptText.contains("found", ignoreCase = true) -> Color(0xFF63B88C)
                                    else -> Color(0xFFEAF5EE)
                                },
                                animationSpec = tween(200),
                                label = "promptTextColor"
                            )

                            Text(
                                text = promptText,
                                color = promptTextColor,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                            )

                            // Centered "Capture" Button with bouncy entrance
                            AnimatedVisibility(
                                visible = session.scanStep == SmartScanStep.SCANNING_FRONT_OF_BAG ||
                                    session.scanStep == SmartScanStep.SCANNING_NUTRIENTS ||
                                    session.scanStep == SmartScanStep.SCANNING_INGREDIENTS,
                                enter = fadeIn(tween(180)) + scaleIn(
                                    initialScale = 0.85f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ),
                                exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.85f)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = onCaptureNow,
                                        enabled = !session.isAiProcessing,
                                        modifier = Modifier.height(38.dp),
                                        shape = RoundedCornerShape(100.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF479C73)),
                                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 0.dp)
                                    ) {
                                        Text(
                                            text = "Capture",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            // AI Accuracy Warning + Quick Redo Buttons when both Nutrients & Ingredients are done
                            val bothSectionsScanned = session.capturedNutrientKeys.isNotEmpty() &&
                                session.workingProduct?.ingredientsText?.isNotBlank() == true
                            AnimatedVisibility(
                                visible = bothSectionsScanned &&
                                    session.baseDatabaseProduct == null &&
                                    session.scanStep == SmartScanStep.REVIEWING_RESULT,
                                enter = fadeIn(tween(220)) + expandVertically(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ),
                                exit = fadeOut(tween(160)) + shrinkVertically(tween(160))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color(0xFFFFF8E1).copy(alpha = 0.96f),
                                        border = BorderStroke(1.dp, Color(0xFFFFD54F))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Warning,
                                                contentDescription = null,
                                                tint = Color(0xFFD29524),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "AI can make mistakes. Please check the scanned product details, nutrients, and ingredients carefully for any errors before saving.",
                                                fontSize = 11.5.sp,
                                                lineHeight = 16.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF5D3D00)
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                onSelectStep(
                                                    SmartScanStep.SCANNING_NUTRIENTS,
                                                    "Fit Guaranteed Analysis inside the box"
                                                )
                                            },
                                            modifier = Modifier.weight(1f).height(38.dp),
                                            shape = RoundedCornerShape(100.dp),
                                            border = BorderStroke(1.dp, Color(0xFF63B88C)),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Refresh,
                                                contentDescription = null,
                                                tint = Color(0xFF63B88C),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Redo Nutrients",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFEAF5EE)
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                onSelectStep(
                                                    SmartScanStep.SCANNING_INGREDIENTS,
                                                    "Fit the Ingredients list inside the box"
                                                )
                                            },
                                            modifier = Modifier.weight(1f).height(38.dp),
                                            shape = RoundedCornerShape(100.dp),
                                            border = BorderStroke(1.dp, Color(0xFF63B88C)),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Refresh,
                                                contentDescription = null,
                                                tint = Color(0xFF63B88C),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Redo Ingredients",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFEAF5EE)
                                            )
                                        }
                                    }
                                }
                            }

                            // Real-Time Nutrient Indicator Placed Below the Green Frame
                            AnimatedVisibility(
                                visible = session.scanStep == SmartScanStep.SCANNING_NUTRIENTS,
                                enter = fadeIn(tween(220)) + scaleIn(
                                    initialScale = 0.92f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ),
                                exit = fadeOut(tween(160))
                            ) {
                                Column(
                                    modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val combinedNutrition = remember(
                                        livePreviewNutrients,
                                        session.workingProduct?.nutrition
                                    ) {
                                        livePreviewNutrients + session.workingProduct?.nutrition.orEmpty()
                                    }
                                    val combinedKeys = remember(
                                        livePreviewNutrients.keys,
                                        session.capturedNutrientKeys
                                    ) {
                                        session.capturedNutrientKeys + livePreviewNutrients.keys
                                    }
                                    RealtimeNutrientVerticalIndicator(
                                        nutrition = combinedNutrition,
                                        capturedKeys = combinedKeys,
                                        lowConfidenceKeys = session.lowConfidenceNutrientKeys,
                                        onNutrientClick = onOpenNutrientEditor
                                    )
                                }
                            }

                            // Real-Time Ingredient Indicator Placed Below the Green Frame
                            AnimatedVisibility(
                                visible = session.scanStep == SmartScanStep.SCANNING_INGREDIENTS,
                                enter = fadeIn(tween(220)) + scaleIn(
                                    initialScale = 0.92f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ),
                                exit = fadeOut(tween(160))
                            ) {
                                Column(
                                    modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val displayIngredients = session.workingProduct?.ingredientsText
                                        ?.takeIf { it.isNotBlank() }
                                        ?: livePreviewIngredientsText
                                    RealtimeIngredientVerticalIndicator(
                                        ingredientsText = displayIngredients
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
private fun FlashSymbolIcon(
    isTorchOn: Boolean,
    modifier: Modifier = Modifier
) {
    val iconColor by animateColorAsState(
        targetValue = if (isTorchOn) Color(0xFFFFD54F) else Color(0xFFEAF5EE),
        animationSpec = tween(180),
        label = "flashIconColor"
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val boltPath = Path().apply {
            moveTo(w * 0.58f, h * 0.06f)
            lineTo(w * 0.22f, h * 0.54f)
            lineTo(w * 0.48f, h * 0.54f)
            lineTo(w * 0.38f, h * 0.94f)
            lineTo(w * 0.78f, h * 0.44f)
            lineTo(w * 0.52f, h * 0.44f)
            close()
        }
        drawPath(
            path = boltPath,
            color = iconColor
        )
        if (!isTorchOn) {
            drawPath(
                path = boltPath,
                color = iconColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 1.4.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

@Composable
private fun NewBarcodeDetectedPromptCard(
    onProceed: () -> Unit,
    onRescanBarcode: () -> Unit
) {
    Surface(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .fillMaxWidth(0.84f),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF1F221B).copy(alpha = 0.96f),
        border = BorderStroke(1.2.dp, Color(0xFF479C73).copy(alpha = 0.8f)),
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "New Barcode Detected!",
                color = Color(0xFFEAF5EE),
                fontSize = 15.5.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onProceed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF479C73))
            ) {
                Text(
                    text = "Proceed",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Text(
                text = "Scan Another Barcode",
                color = Color(0xFFBFE3D1),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { onRescanBarcode() }
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun FreeOrderLabelStepBar(
    currentStep: SmartScanStep,
    capturedNutrientCount: Int,
    hasIngredients: Boolean,
    hasFrontPhoto: Boolean,
    isLocked: Boolean,
    onSelectStep: (SmartScanStep, String) -> Unit
) {
    val tabs = listOf(
        Triple(
            SmartScanStep.SCANNING_NUTRIENTS,
            "Nutrients",
            "Fit Guaranteed Analysis inside the box"
        ),
        Triple(
            SmartScanStep.SCANNING_INGREDIENTS,
            "Ingredients",
            "Fit the Ingredients list inside the box"
        ),
        Triple(
            SmartScanStep.SCANNING_FRONT_OF_BAG,
            "Front Bag",
            "Fit the front of the bag inside the box"
        ),
        Triple(
            SmartScanStep.REVIEWING_RESULT,
            "Review",
            "Swipe up the sheet below to review FEDIAF/AAFCO & save."
        )
    )

    val rowAlpha = if (isLocked) 0.45f else 1f

    // Compact full-width text-only step tabs (no background/outline, no horizontal carousel)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = rowAlpha }
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { (step, title, msg) ->
            val selected = currentStep == step
            val isCompleted = (step == SmartScanStep.SCANNING_NUTRIENTS && capturedNutrientCount > 0) ||
                (step == SmartScanStep.SCANNING_INGREDIENTS && hasIngredients) ||
                (step == SmartScanStep.SCANNING_FRONT_OF_BAG && hasFrontPhoto)

            val textColor by animateColorAsState(
                targetValue = when {
                    selected -> Color(0xFF63B88C)
                    isCompleted -> Color(0xFF6EE7B7)
                    else -> Color(0xFFEAF5EE).copy(alpha = 0.72f)
                },
                animationSpec = tween(180),
                label = "stepTextColor"
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !isLocked) { onSelectStep(step, msg) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = textColor,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                    }
                    Text(
                        text = title,
                        color = textColor,
                        fontSize = 11.sp,
                        fontWeight = if (selected || isCompleted) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .width(if (selected) 24.dp else 0.dp)
                        .height(2.dp)
                        .background(
                            if (selected) Color(0xFF63B88C) else Color.Transparent,
                            RoundedCornerShape(100.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun RealtimeNutrientVerticalIndicator(
    nutrition: Map<String, NutrientValue>,
    capturedKeys: Set<String>,
    lowConfidenceKeys: Set<String>,
    onNutrientClick: (String) -> Unit
) {
    val labelMap = remember {
        mapOf(
            "crudeProtein" to "Crude Protein",
            "crudeFat" to "Crude Fat",
            "crudeFiber" to "Crude Fiber",
            "moisture" to "Moisture",
            "calcium" to "Calcium",
            "phosphorus" to "Phosphorus",
            "ash" to "Ash",
            "taurine" to "Taurine",
            "omega3" to "Omega-3",
            "omega6" to "Omega-6",
            "sodium" to "Sodium"
        )
    }

    val scannedItems = remember(nutrition, capturedKeys, lowConfidenceKeys) {
        capturedKeys.map { key ->
            val nv = nutrition[key]
                ?: nutrition.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
            val displayName = nv?.name?.takeIf { it.isNotBlank() }
                ?: labelMap[key]
                ?: key
            val formattedVal = nv?.value?.takeIf { it > 0.0 }?.let { v ->
                val numStr = if (v % 1.0 == 0.0) v.toInt().toString()
                else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
                "$numStr${nv.unit.ifBlank { "%" }}"
            }.orEmpty()
            val isLowConf = lowConfidenceKeys.contains(key)
            Triple(key, "$displayName $formattedVal".trim(), isLowConf)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            .padding(horizontal = 16.dp)
    ) {
        val count = scannedItems.size
        Text(
            text = "[$count] nutrients scanned",
            color = if (count > 0) Color(0xFF63B88C) else Color(0xFFC3C8BB),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        scannedItems.forEach { (key, lineText, isLowConf) ->
            Text(
                text = lineText,
                color = if (isLowConf) Color(0xFFFFD54F) else Color(0xFFEAF5EE),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clickable { onNutrientClick(key) }
                    .padding(horizontal = 8.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
private fun RealtimeIngredientVerticalIndicator(
    ingredientsText: String
) {
    val validatedIngredients = remember(ingredientsText) {
        LabelAiCorrectionService.extractValidatedIngredientsList(ingredientsText)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            .padding(horizontal = 16.dp)
    ) {
        val count = validatedIngredients.size
        Text(
            text = "[$count] ingredients scanned",
            color = if (count > 0) Color(0xFF63B88C) else Color(0xFFC3C8BB),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        validatedIngredients.take(6).forEach { item ->
            Text(
                text = item,
                color = Color(0xFFEAF5EE),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp)
            )
        }
        if (count > 6) {
            Text(
                text = "+${count - 6} more",
                color = Color(0xFFBFE3D1).copy(alpha = 0.8f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatTwoLineStatusMessage(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed.contains('\n')) return trimmed
    // Split the second sentence onto a second line after the first '!', '.', or '—'
    val firstBreak = Regex("""^(.*?[!.]\s+|.*?\s+—\s+)(.+)$""").find(trimmed)
    return if (firstBreak != null) {
        val line1 = firstBreak.groupValues[1].trim().trimEnd('—').trim()
        val line2 = firstBreak.groupValues[2].trim()
        "$line1\n$line2"
    } else {
        trimmed
    }
}

@Composable
private fun ScannedSheetPeekHeader(
    session: ScanSessionState,
    swipeExpandProgress: Float,
    showSaveToProfileButton: Boolean = false,
    isSavedToProfile: Boolean = false,
    onToggleSaveToProfile: () -> Unit = {},
    onCompareClick: () -> Unit = {},
    onRetrySave: () -> Unit
) {
    // Smoothly sync the fade of the two-line status message directly to the user's swipe gesture
    val statusAlpha = (1f - (swipeExpandProgress * 2.1f)).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (showSaveToProfileButton) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .clickable { onCompareClick() },
                    shape = RoundedCornerShape(100.dp),
                    color = Color(0xFF479C73),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "Compare",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 11.5.sp
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Save to My Saved (Heart) Button in Profile Tab
                IconButton(
                    onClick = onToggleSaveToProfile,
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isSavedToProfile) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (isSavedToProfile) "Remove from Saved" else "Save to My Saved",
                        tint = if (isSavedToProfile) Color.Red else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        // Smoothly fade out the two-line centered status message in sync with the swipe gesture
        if (!session.statusMessage.isNullOrBlank() && statusAlpha > 0.01f) {
            val formattedMessage = remember(session.statusMessage) {
                formatTwoLineStatusMessage(session.statusMessage)
            }
            Text(
                text = formattedMessage,
                fontSize = 11.5.sp,
                lineHeight = 15.5.sp,
                color = Color(0xFF479C73),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = statusAlpha
                        translationY = -(1f - statusAlpha) * 10f
                    }
                    .padding(vertical = (4f * statusAlpha).dp)
            )
        }

        if (!session.saveError.isNullOrBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFFEBEE),
                border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = session.saveError,
                        color = Color(0xFFB71C1C),
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRetrySave) {
                        Text("Retry Save", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                    }
                }
            }
        }
    }
}

@Composable
fun SaveProductConfirmationDialog(
    baseProduct: Product?,
    workingProduct: Product,
    lowConfidenceKeys: Set<String>,
    potentialDuplicates: List<Product> = emptyList(),
    hasPendingFrontPhoto: Boolean = false,
    hasPendingNutritionPhoto: Boolean = false,
    hasPendingIngredientsPhoto: Boolean = false,
    hasPendingBarcodePhoto: Boolean = false,
    isSaving: Boolean,
    onRecheck: () -> Unit,
    onConfirmSave: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Dialog(
        onDismissRequest = { if (!isSaving) onRecheck() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 620.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header + Barcode Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Confirm Save to Database",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    if (workingProduct.barcode.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = Color(0xFF479C73).copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = workingProduct.barcode,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF479C73),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Compact User Responsibility & Anti-Fraud Warning
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isDark) Color(0xFF3A1A1A) else Color(0xFFFFEBEE),
                    border = BorderStroke(
                        1.dp,
                        if (isDark) Color(0xFFEF5350).copy(alpha = 0.5f) else Color(0xFFE53935).copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFFFCA5A5) else Color(0xFFD32F2F),
                            modifier = Modifier
                                .padding(top = 1.dp)
                                .size(16.dp)
                        )
                        Text(
                            text = "You are responsible for submitting accurate product info and photos. Fake or fraudulent entries are prohibited.",
                            fontSize = 11.sp,
                            lineHeight = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color(0xFFFECACA) else Color(0xFF7F1D1D)
                        )
                    }
                }

                // Compact Scrollable Summary
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Product Details Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = workingProduct.productName.ifBlank { "Unnamed Product" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            val meta = listOfNotNull(
                                workingProduct.brands.takeIf { it.isNotBlank() },
                                workingProduct.species.takeIf { it.isNotBlank() },
                                workingProduct.lifeStage.takeIf { it.isNotBlank() },
                                workingProduct.foodType.takeIf { it.isNotBlank() }
                            ).joinToString(" · ")
                            if (meta.isNotBlank()) {
                                Text(
                                    text = meta,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (baseProduct != null) {
                                Text(
                                    text = "Updating existing record",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF479C73)
                                )
                            }
                        }
                    }

                    if (potentialDuplicates.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = if (isDark) Color(0xFF3A2C12) else Color(0xFFFFF8E1),
                            border = BorderStroke(
                                1.dp,
                                if (isDark) Color(0xFFFFB74D).copy(alpha = 0.45f) else Color(0xFFD29524).copy(alpha = 0.55f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Possible Similar Product Found",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color(0xFFFFCC80) else Color(0xFF8D5B00)
                                )
                                Text(
                                    text = "A similar item already exists (${potentialDuplicates.take(2).joinToString("; ") { "${it.brands} ${it.productName}".trim() }}). Confirm only if this is a distinct package or barcode.",
                                    fontSize = 10.5.sp,
                                    lineHeight = 14.sp,
                                    color = if (isDark) Color(0xFFFFE0B2) else Color(0xFF6D4C00)
                                )
                            }
                        }
                    }

                    // Nutrients Summary Card (compact 2-column grid)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Nutrients",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF479C73)
                            )
                            val nutrientPairs = buildList {
                                workingProduct.nutrition.forEach { (key, nv) ->
                                    add(
                                        Triple(
                                            nv.name.ifBlank { key },
                                            "${nv.value}${nv.unit.ifBlank { "%" }}",
                                            lowConfidenceKeys.contains(key)
                                        )
                                    )
                                }
                                workingProduct.metabolizableEnergy?.let { me ->
                                    add(
                                        Triple(
                                            "Energy",
                                            "${me.toInt()} ${workingProduct.metabolizableEnergyUnit ?: "kcal/kg"}",
                                            false
                                        )
                                    )
                                }
                            }
                            if (nutrientPairs.isEmpty()) {
                                Text(
                                    text = "No nutrients entered",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                nutrientPairs.chunked(2).forEach { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowItems.forEach { (name, value, isAmber) ->
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = name,
                                                    fontSize = 11.sp,
                                                    color = if (isAmber) Color(0xFFD29524) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = value,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isAmber) Color(0xFFD29524) else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                        if (rowItems.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Ingredients Summary Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Ingredients",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF479C73)
                            )
                            Text(
                                text = workingProduct.ingredientsText.ifBlank { "No ingredients entered" },
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = if (workingProduct.ingredientsText.isBlank()) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Attached Photos Row
                    val photoStatuses = listOf(
                        "Front" to (!workingProduct.imageUrl.isNullOrBlank() || hasPendingFrontPhoto),
                        "Nutrients" to (!workingProduct.nutritionPhotoUrl.isNullOrBlank() || hasPendingNutritionPhoto),
                        "Ingredients" to (!workingProduct.ingredientsPhotoUrl.isNullOrBlank() || hasPendingIngredientsPhoto),
                        "Barcode" to (!workingProduct.barcodePhotoUrl.isNullOrBlank() || hasPendingBarcodePhoto)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        photoStatuses.forEach { (label, attached) ->
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(100.dp),
                                color = if (attached) {
                                    Color(0xFF479C73).copy(alpha = 0.14f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                }
                            ) {
                                Text(
                                    text = if (attached) "$label ✓" else label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = if (attached) Color(0xFF479C73) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onRecheck,
                        enabled = !isSaving,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(100.dp)
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Recheck", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }

                    Button(
                        onClick = onConfirmSave,
                        enabled = !isSaving,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF479C73))
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text("Confirm & Save", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Lightweight barcode-only scanner overload used by FeedbackScreen and SavedScreen
 * when they only need a single scanned barcode string.
 */
@Composable
fun ProductScanner(
    onBarcodeScanned: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isInspectionMode = LocalInspectionMode.current
    val currentOnBarcodeScanned by rememberUpdatedState(onBarcodeScanned)
    var hasCameraPermission by remember {
        mutableStateOf(
            if (isInspectionMode) true
            else ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!isInspectionMode && !hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission && !isInspectionMode) {
            val cameraController = remember {
                LifecycleCameraController(context).apply {
                    cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                }
            }

            DisposableEffect(cameraController, lifecycleOwner) {
                cameraController.bindToLifecycle(lifecycleOwner)
                val hasScanned = AtomicBoolean(false)
                val analysisExecutor = Executors.newSingleThreadExecutor()
                val mainExecutor = ContextCompat.getMainExecutor(context)
                val barcodeScanner = BarcodeScanning.getClient(
                    BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                        .build()
                )
                cameraController.setImageAnalysisAnalyzer(
                    analysisExecutor,
                    MlKitAnalyzer(
                        listOf(barcodeScanner),
                        CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED,
                        mainExecutor
                    ) { result ->
                        if (hasScanned.get()) return@MlKitAnalyzer
                        val barcodeValue = result?.getValue(barcodeScanner)?.firstOrNull()?.rawValue?.trim()
                        if (!barcodeValue.isNullOrEmpty() && hasScanned.compareAndSet(false, true)) {
                            runCatching { cameraController.clearImageAnalysisAnalyzer() }
                            currentOnBarcodeScanned(barcodeValue)
                        }
                    }
                )
                onDispose {
                    runCatching { cameraController.clearImageAnalysisAnalyzer() }
                    runCatching { cameraController.unbind() }
                    runCatching { barcodeScanner.close() }
                    runCatching { analysisExecutor.shutdown() }
                }
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        this.controller = cameraController
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                }
            )
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(250.dp, 150.dp),
                color = Color.Transparent,
                border = BorderStroke(2.dp, Color.White.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(12.dp)
            ) {}
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .padding(16.dp)
                .statusBarsPadding()
                .align(Alignment.TopEnd)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ProductScannerPreview() {
    FurBetterChoiceTheme {
        val previewVm = remember { AppViewModel() }
        ProductScanner(
            viewModel = previewVm,
            onClose = {}
        )
    }
}

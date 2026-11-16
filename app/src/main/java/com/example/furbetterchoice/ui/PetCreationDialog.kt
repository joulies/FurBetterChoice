package com.example.furbetterchoice.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.example.furbetterchoice.api.Breeds
import com.example.furbetterchoice.api.Pet
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RequiredFieldLabel(label: String) {
    Row(
        modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        )
        Text(
            text = " *",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color(0xFFE53935)
            )
        )
    }
}

@Composable
fun RoundedFadingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholderText: String,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    fieldBg: Color,
    fieldBorder: BorderStroke
) {
    Surface(
        modifier = modifier
            .height(50.dp)
            .subtleCardShadow(borderRadius = 100.dp),
        shape = RoundedCornerShape(100.dp),
        color = fieldBg
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterStart
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    AnimatedVisibility(
                        visible = value.isEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Text(
                            placeholderText,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                    }
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(100.dp),
                enabled = enabled,
                readOnly = readOnly,
                trailingIcon = trailingIcon,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                    focusedBorderColor = Color(0xFF479C73),
                    disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    cursorColor = Color(0xFF479C73)
                ),
                singleLine = singleLine
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetCreationDialog(
    existingPet: Pet? = null,
    boxColor: Color,
    boxBorder: BorderStroke,
    onDismiss: () -> Unit,
    onConfirm: (Pet) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val today = remember { sdf.format(Date()) }
    
    var name by remember { mutableStateOf(existingPet?.name ?: "") }
    var petType by remember { mutableStateOf(existingPet?.type ?: "Dog") }
    var sex by remember { mutableStateOf(existingPet?.sex?.ifEmpty { "Male" } ?: "Male") }
    var breed by remember { mutableStateOf(existingPet?.breed ?: "") }
    var birthday by remember { mutableStateOf(existingPet?.birthday ?: today) }
    
    // Parse weight numbers only
    var weight by remember {
        mutableStateOf(existingPet?.weight?.filter { it.isDigit() || it == '.' } ?: "")
    }
    var imageUri by remember { mutableStateOf<Uri?>(existingPet?.imageUri?.toUri()) }
    
    val presetAllergies = remember { listOf("Chicken", "Beef", "Dairy", "Grain", "Soy", "Salmon", "Eggs", "None") }
    var selectedAllergiesSet by remember {
        mutableStateOf(
            existingPet?.allergies?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        )
    }
    var customAllergies by remember {
        mutableStateOf(
            existingPet?.allergies?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() && !presetAllergies.contains(it) }?.joinToString(", ") ?: ""
        )
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showBreedPicker by remember { mutableStateOf(false) }
    var showAllergyPicker by remember { mutableStateOf(false) }
    var showExitWarning by remember { mutableStateOf(false) }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageUri = uri
    }

    // Colors matching bottom navigation bar and Login page form design
    val navRedColor = Color(0xFF7A1129)
    val navGreenColor = Color(0xFF479C73)

    // Login-page form field styling: clean surface background + outline border + subtle shadow
    val fieldBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    val fieldBg = MaterialTheme.colorScheme.surface

    // Form Dirty Tracker for Exit Warning
    val isFormDirty = remember(name, petType, sex, breed, birthday, weight, imageUri, selectedAllergiesSet, customAllergies) {
        val origName = existingPet?.name ?: ""
        val origType = existingPet?.type ?: "Dog"
        val origSex = existingPet?.sex?.ifEmpty { "Male" } ?: "Male"
        val origBreed = existingPet?.breed ?: ""
        val origBirthday = existingPet?.birthday ?: today
        val origWeight = existingPet?.weight?.filter { it.isDigit() || it == '.' } ?: ""
        val origImage = existingPet?.imageUri

        name != origName ||
        petType != origType ||
        sex != origSex ||
        breed != origBreed ||
        birthday != origBirthday ||
        weight != origWeight ||
        imageUri?.toString() != origImage ||
        selectedAllergiesSet.isNotEmpty() ||
        customAllergies.isNotBlank()
    }

    fun handleAttemptExit() {
        focusManager.clearFocus()
        keyboardController?.hide()
        if (isFormDirty && existingPet == null) {
            showExitWarning = true
        } else {
            onDismiss()
        }
    }

    // Combined allergy string preview
    val combinedAllergies = remember(selectedAllergiesSet, customAllergies) {
        val list = selectedAllergiesSet.filter { it != "None" }.toMutableList()
        if (customAllergies.isNotBlank()) {
            list.addAll(customAllergies.split(",").map { it.trim() }.filter { it.isNotBlank() })
        }
        if (list.isEmpty() && selectedAllergiesSet.contains("None")) "None" else list.distinct().joinToString(", ")
    }

    // Spring animation for fluid dialog entrance
    var isVisible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.82f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "scale"
    )

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val calculatedAge = remember(birthday) {
        try {
            val birthDate = sdf.parse(birthday) ?: Date()
            val birthCal = Calendar.getInstance().apply { time = birthDate }
            val nowCal = Calendar.getInstance()
            
            var years = nowCal.get(Calendar.YEAR) - birthCal.get(Calendar.YEAR)
            var months = nowCal.get(Calendar.MONTH) - birthCal.get(Calendar.MONTH)
            val days = nowCal.get(Calendar.DAY_OF_MONTH) - birthCal.get(Calendar.DAY_OF_MONTH)
            
            if (months < 0 || (months == 0 && days < 0)) {
                years--
                months += 12
            }
            
            when {
                years > 0 -> "$years y ${months % 12} m old"
                months > 0 -> "$months m old"
                else -> "Newborn"
            }
        } catch (e: Exception) {
            "Select birthday"
        }
    }

    Dialog(
        onDismissRequest = { handleAttemptExit() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.52f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        handleAttemptExit()
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.88f)
                    .scale(scale)
                    .subtleCardShadow(borderRadius = 32.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        })
                    },
                shape = RoundedCornerShape(32.dp),
                color = boxColor,
                border = boxBorder
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    // Title Bar with Close Action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (existingPet == null) "Add New Fur Friend" else "Edit Pet Profile",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        IconButton(
                            onClick = { handleAttemptExit() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 1. Photo Avatar Uploader (92dp Big & Middle)
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .subtleCardShadow(borderRadius = 100.dp)
                                .clip(CircleShape)
                                .background(fieldBg)
                                .border(fieldBorder, CircleShape)
                                .clickable {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    imagePickerLauncher.launch("image/*")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (imageUri != null) {
                                AsyncImage(
                                    model = imageUri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = navGreenColor,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }

                        // 2. Pet Name Input (Very Rounded with Fading Placeholder & Red *)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            RequiredFieldLabel("Pet Name")
                            Spacer(modifier = Modifier.height(4.dp))
                            RoundedFadingTextField(
                                value = name,
                                onValueChange = { name = it },
                                placeholderText = "e.g. Milo",
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                }),
                                fieldBg = fieldBg,
                                fieldBorder = fieldBorder,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // 3. ROW 1: Pet Type (Left Stacked) & Sex (Right Stacked Choices)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Left Column: Pet Type (Stacked Dog/Cat)
                            Column(modifier = Modifier.weight(1f)) {
                                RequiredFieldLabel("Pet Type")
                                Spacer(modifier = Modifier.height(4.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("Dog", "Cat").forEach { type ->
                                        val isSelected = petType == type
                                        val pillColor = if (isSelected) {
                                            if (type == "Dog") navGreenColor else navRedColor
                                        } else fieldBg

                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(36.dp)
                                                .subtleCardShadow(borderRadius = 100.dp)
                                                .bouncyClickable {
                                                    focusManager.clearFocus()
                                                    keyboardController?.hide()
                                                    petType = type
                                                    breed = ""
                                                },
                                            shape = RoundedCornerShape(100.dp),
                                            color = pillColor,
                                            border = if (isSelected) BorderStroke(1.dp, pillColor) else fieldBorder
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = type,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        fontSize = 11.5.sp,
                                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Right Column: Sex (Vertically Stacked Choice Pills - Male/Female Only)
                            Column(modifier = Modifier.weight(1f)) {
                                RequiredFieldLabel("Sex")
                                Spacer(modifier = Modifier.height(4.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("Male", "Female").forEach { option ->
                                        val isSelected = sex == option
                                        val isFemale = option == "Female"
                                        val pillColor = if (isSelected) {
                                            if (isFemale) navGreenColor else navRedColor
                                        } else fieldBg

                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(36.dp)
                                                .subtleCardShadow(borderRadius = 100.dp)
                                                .bouncyClickable {
                                                    focusManager.clearFocus()
                                                    keyboardController?.hide()
                                                    sex = option
                                                },
                                            shape = RoundedCornerShape(100.dp),
                                            color = pillColor,
                                            border = if (isSelected) BorderStroke(1.dp, pillColor) else fieldBorder
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(horizontal = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = option,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        fontSize = 11.sp,
                                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 4. ROW 2: Breed (Left) & Birthday (Right)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Breed Field
                            Column(modifier = Modifier.weight(1f)) {
                                RequiredFieldLabel("Breed")
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .subtleCardShadow(borderRadius = 100.dp)
                                        .bouncyClickable {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            showBreedPicker = true
                                        },
                                    shape = RoundedCornerShape(100.dp),
                                    color = fieldBg,
                                    border = fieldBorder
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (breed.isNotBlank()) breed else "Select Breed",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = 12.sp,
                                                color = if (breed.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                                fontWeight = if (breed.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(Icons.Default.Search, contentDescription = null, tint = navGreenColor, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            // Birthday Field
                            Column(modifier = Modifier.weight(1f)) {
                                RequiredFieldLabel("Birthday")
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .subtleCardShadow(borderRadius = 100.dp)
                                        .bouncyClickable {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            showDatePicker = true
                                        },
                                    shape = RoundedCornerShape(100.dp),
                                    color = fieldBg,
                                    border = fieldBorder
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = calculatedAge,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 11.5.sp
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(Icons.Default.DateRange, contentDescription = null, tint = navGreenColor, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        // 5. ROW 3: Weight (Left - Numbers Only with Fixed Bold kg Suffix) & Allergies (Right)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Weight Field
                            Column(modifier = Modifier.weight(1f)) {
                                RequiredFieldLabel("Weight")
                                Spacer(modifier = Modifier.height(4.dp))
                                RoundedFadingTextField(
                                    value = weight,
                                    onValueChange = { newValue ->
                                        val filtered = newValue.filter { it.isDigit() || it == '.' }
                                        if (filtered.count { it == '.' } <= 1) {
                                            weight = filtered
                                        }
                                    },
                                    placeholderText = "Weight",
                                    trailingIcon = {
                                        Text(
                                            "kg",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = navGreenColor,
                                            modifier = Modifier.padding(end = 12.dp)
                                        )
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                    }),
                                    fieldBg = fieldBg,
                                    fieldBorder = fieldBorder,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Allergies Searchable / Typable Field
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Allergies",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .subtleCardShadow(borderRadius = 100.dp)
                                        .bouncyClickable {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            showAllergyPicker = true
                                        },
                                    shape = RoundedCornerShape(100.dp),
                                    color = fieldBg,
                                    border = fieldBorder
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (combinedAllergies.isNotBlank()) combinedAllergies else "Select Allergies",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = 12.sp,
                                                color = if (combinedAllergies.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                                fontWeight = if (combinedAllergies.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(Icons.Default.Search, contentDescription = null, tint = navGreenColor, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val parsedWeight = weight.toDoubleOrNull()
                    val isFormValid = name.isNotBlank() && breed.isNotBlank() && parsedWeight != null && parsedWeight > 0.0

                    // Save Button
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .subtleCardShadow(borderRadius = 100.dp),
                        shape = RoundedCornerShape(100.dp),
                        color = if (isFormValid) navGreenColor else navGreenColor.copy(alpha = 0.5f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .bouncyClickable(enabled = isFormValid) {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    val formattedWeight = if (weight.isNotBlank()) "$weight kg" else ""
                                    onConfirm(
                                        Pet(
                                            id = existingPet?.id ?: UUID.randomUUID().toString(),
                                            name = name,
                                            type = petType,
                                            breed = breed,
                                            birthday = birthday,
                                            imageUri = imageUri?.toString(),
                                            sex = sex,
                                            weight = formattedWeight,
                                            allergies = combinedAllergies
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (existingPet == null) "Save Fur Friend" else "Save Changes",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = Color.White
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    if (showExitWarning) {
        CustomConfirmationDialog(
            title = "Unsaved Changes",
            message = "All the information you entered will not be saved.",
            confirmText = "Discard & Exit",
            icon = Icons.Default.Close,
            onDismiss = { showExitWarning = false },
            onConfirm = {
                showExitWarning = false
                onDismiss()
            }
        )
    }

    if (showBreedPicker) {
        BreedPickerDialog(
            petType = petType,
            onDismiss = { showBreedPicker = false },
            onBreedSelected = { 
                breed = it
                showBreedPicker = false
            }
        )
    }

    if (showAllergyPicker) {
        AllergyPickerDialog(
            presetAllergies = presetAllergies,
            selectedSet = selectedAllergiesSet,
            customText = customAllergies,
            onDismiss = { showAllergyPicker = false },
            onApply = { newSet, newCustom ->
                selectedAllergiesSet = newSet
                customAllergies = newCustom
                showAllergyPicker = false
            }
        )
    }

    if (showDatePicker) {
        val todayMillis = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        calendar.add(Calendar.YEAR, -50)
        val fiftyYearsAgoMillis = calendar.timeInMillis

        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = try {
                sdf.parse(birthday)?.time ?: todayMillis
            } catch (e: Exception) {
                todayMillis
            },
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis in (fiftyYearsAgoMillis..todayMillis)
                }
                override fun isSelectableYear(year: Int): Boolean {
                    return year in (currentYear - 50)..currentYear
                }
            }
        )
        
        DatePickerDialog(
            modifier = Modifier.scale(0.88f),
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            birthday = sdf.format(Date(it))
                        }
                        showDatePicker = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = navGreenColor)
                ) { 
                    Text("OK", fontWeight = FontWeight.Bold) 
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) { 
                    Text("Cancel") 
                }
            },
            colors = DatePickerDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            DatePicker(
                state = datePickerState,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = navGreenColor,
                    headlineContentColor = MaterialTheme.colorScheme.onSurface,
                    selectedDayContainerColor = navGreenColor,
                    selectedDayContentColor = Color.White,
                    todayContentColor = navGreenColor,
                    todayDateBorderColor = navGreenColor
                )
            )
        }
    }
}

@Composable
fun AllergyPickerDialog(
    presetAllergies: List<String>,
    selectedSet: Set<String>,
    customText: String,
    onDismiss: () -> Unit,
    onApply: (Set<String>, String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var searchQuery by remember { mutableStateOf("") }
    var tempSet by remember { mutableStateOf(selectedSet) }
    var tempCustom by remember { mutableStateOf(customText) }

    val filteredPresets = presetAllergies.filter { it.contains(searchQuery, ignoreCase = true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.78f)
                .subtleCardShadow(borderRadius = 28.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    })
                },
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, Color(0xFF479C73).copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Allergies", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .subtleCardShadow(borderRadius = 100.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search allergies...", fontSize = 12.sp) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(100.dp),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF479C73)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF479C73),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = Color(0xFF479C73)
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Preset Options",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filteredPresets.forEach { item ->
                            val isSelected = tempSet.contains(item)
                            Surface(
                                modifier = Modifier
                                    .subtleCardShadow(borderRadius = 100.dp)
                                    .bouncyClickable {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        if (item == "None") {
                                            tempSet = setOf("None")
                                            tempCustom = ""
                                        } else {
                                            val newSet = tempSet - "None"
                                            tempSet = if (isSelected) newSet - item else newSet + item
                                        }
                                    },
                                shape = RoundedCornerShape(100.dp),
                                color = if (isSelected) Color(0xFF479C73) else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF479C73) else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = item,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.5.sp,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                        }
                    }

                    if (!tempSet.contains("None")) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Other Allergies",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.padding(start = 6.dp)
                        )
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .subtleCardShadow(borderRadius = 100.dp)
                        ) {
                            OutlinedTextField(
                                value = tempCustom,
                                onValueChange = { tempCustom = it },
                                placeholder = { Text("Type custom allergies (e.g. Wheat, Salmon)", fontSize = 12.sp) },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                }),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(100.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF479C73),
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    cursorColor = Color(0xFF479C73)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onApply(tempSet, tempCustom)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .subtleCardShadow(borderRadius = 100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF479C73)),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text("Apply Allergies", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun BreedPickerDialog(
    petType: String,
    onDismiss: () -> Unit,
    onBreedSelected: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var searchQuery by remember { mutableStateOf("") }
    val breeds = if (petType == "Dog") Breeds.dogBreeds else Breeds.catBreeds
    val filteredBreeds = breeds.filter { it.contains(searchQuery, ignoreCase = true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.78f)
                .subtleCardShadow(borderRadius = 28.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    })
                },
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, Color(0xFF479C73).copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Breed", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .subtleCardShadow(borderRadius = 100.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search breeds...", fontSize = 12.sp) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(100.dp),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF479C73)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF479C73),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = Color(0xFF479C73)
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(filteredBreeds) { b ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bouncyClickable {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    onBreedSelected(b)
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Transparent
                        ) {
                            Text(
                                text = b,
                                modifier = Modifier.padding(vertical = 9.dp, horizontal = 10.dp),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp)
                            )
                        }
                    }
                }
            }
        }
    }
}

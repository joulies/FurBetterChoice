package com.example.furbetterchoice.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.furbetterchoice.R
import com.example.furbetterchoice.api.Pet

@Composable
fun PetProfileDialog(
    pet: Pet,
    boxColor: Color,
    boxBorder: BorderStroke,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onFindFood: () -> Unit
) {
    // Entrance scale animation
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

    val age = remember(pet) { calculateAgeString(pet.birthday, pet.age) }
    val cardBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val cardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .scale(scale),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, Color(0xFF479C73).copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Hero Header with Image
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
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
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    pet.name.take(1).uppercase(),
                                    style = MaterialTheme.typography.displayMedium.copy(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                        
                        // Gradient Overlay for readability
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                                        startY = 40f
                                    )
                                )
                        )
                        
                        // Close Button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(28.dp)
                                .align(Alignment.TopEnd)
                                .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        
                        // Pet Name & Badges
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = pet.name,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                BadgeTag(text = pet.type, containerColor = Color(0xFF479C73))
                                if (pet.sex.isNotBlank()) {
                                    BadgeTag(text = pet.sex, containerColor = Color.White.copy(alpha = 0.25f), textColor = Color.White)
                                }
                                if (pet.breed.isNotBlank()) {
                                    BadgeTag(text = pet.breed, containerColor = Color.White.copy(alpha = 0.2f), textColor = Color.White)
                                }
                            }
                        }
                    }
                    
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        // 3-Column Stats Card (Age, Sex, Weight)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = cardBg,
                            border = cardBorder,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "Age",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                        color = Color(0xFF479C73)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = age,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp
                                        )
                                    )
                                }

                                VerticalDivider(modifier = Modifier.height(28.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "Sex",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                        color = Color(0xFF479C73)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = pet.sex.ifEmpty { "Male" },
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp
                                        )
                                    )
                                }

                                VerticalDivider(modifier = Modifier.height(28.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "Weight",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                        color = Color(0xFF479C73)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = pet.weight.ifEmpty { "N/A" },
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Allergies Section
                        val hasAllergies = pet.allergies.isNotBlank() && pet.allergies != "None"
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (hasAllergies) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f) else cardBg,
                            border = if (hasAllergies) BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)) else cardBorder,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (hasAllergies) Icons.Default.Warning else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (hasAllergies) MaterialTheme.colorScheme.error else Color(0xFF479C73),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Allergies",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (hasAllergies) MaterialTheme.colorScheme.error else Color(0xFF479C73)
                                        )
                                    )
                                    Text(
                                        text = if (hasAllergies) pet.allergies else "No known allergies",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Find Food Recommendation CTA
                        Surface(
                            onClick = onFindFood,
                            shape = RoundedCornerShape(100.dp),
                            color = Color(0xFF479C73).copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, Color(0xFF479C73).copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_paw),
                                    contentDescription = null,
                                    tint = Color(0xFF479C73),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Food Recommendations for ${pet.name}",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = Color(0xFF479C73)
                                    )
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Action Buttons
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .bouncyClickable { onDelete() },
                                shape = RoundedCornerShape(100.dp),
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, Color(0xFF7A1129).copy(alpha = 0.5f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFF7A1129))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Remove", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp), color = Color(0xFF7A1129))
                                    }
                                }
                            }
                            
                            Surface(
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(40.dp)
                                    .bouncyClickable { onEdit() },
                                shape = RoundedCornerShape(100.dp),
                                color = Color(0xFF479C73),
                                border = BorderStroke(1.dp, Color(0xFF479C73))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Edit Profile", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp), color = Color.White)
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

@Composable
fun BadgeTag(text: String, containerColor: Color, textColor: Color = Color.White) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 10.5.sp),
            color = textColor
        )
    }
}

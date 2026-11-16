package com.example.furbetterchoice.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.furbetterchoice.ui.theme.*

@Composable
fun AuthOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isError: Boolean = false,
    readOnly: Boolean = false,
    onClick: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(100.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .subtleCardShadow(borderRadius = 100.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 13.5.sp
                        )
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                singleLine = true,
                readOnly = readOnly,
                isError = isError,
                shape = RoundedCornerShape(100.dp),
                visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = keyboardOptions,
                leadingIcon = if (leadingIcon != null) {
                    {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            tint = Color(0xFF479C73),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else null,
                trailingIcon = {
                    if (isPassword) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = Color(0xFF479C73).copy(alpha = 0.12f),
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .bouncyClickable { passwordVisible = !passwordVisible }
                        ) {
                            Text(
                                text = if (passwordVisible) "Hide" else "Show",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color(0xFF479C73),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    } else {
                        trailingIcon?.invoke()
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF479C73),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                    errorBorderColor = Color(0xFF7A1129),
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = Color(0xFF479C73)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (onClick != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(onClick = onClick)
            )
        }
    }
}

@Composable
fun PasswordRequirementsView(password: String) {
    val hasMinLength = password.length >= 8
    val hasUppercase = password.any { it.isUpperCase() }
    val hasLowercase = password.any { it.isLowerCase() }
    val hasDigit = password.any { it.isDigit() }
    val hasSymbol = password.any { !it.isLetterOrDigit() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        RequirementRow("At least 8 characters", hasMinLength)
        RequirementRow("At least 1 uppercase letter", hasUppercase)
        RequirementRow("At least 1 lowercase letter", hasLowercase)
        RequirementRow("At least 1 number", hasDigit)
        RequirementRow("At least 1 symbol/special character", hasSymbol)
    }
}

@Composable
fun RequirementRow(text: String, met: Boolean) {
    val tint by animateColorAsState(
        targetValue = if (met) Color(0xFF479C73) else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(300),
        label = "reqColor"
    )
    val scale by animateFloatAsState(
        targetValue = if (met) 1.1f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "reqScale"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .scale(scale),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.5.sp,
                color = tint,
                fontWeight = if (met) FontWeight.SemiBold else FontWeight.Normal
            )
        )
    }
}

@Composable
fun SignOutConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Sign Out",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Text(
                "Are you sure you want to sign out of FurBetterChoice?",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7A1129)),
                shape = RoundedCornerShape(100.dp)
            ) {
                Text(
                    "Confirm Sign Out",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

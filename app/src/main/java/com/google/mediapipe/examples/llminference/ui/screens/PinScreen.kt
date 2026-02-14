package com.google.mediapipe.examples.llminference.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mediapipe.examples.llminference.settings.AppPreferences
import kotlinx.coroutines.delay

@Composable
fun PinScreen(
    onPinVerified: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs = remember { AppPreferences(context) }

    var pin by remember { mutableStateOf("") }
    var isSettingPin by remember { mutableStateOf(!prefs.isPinSet) }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirming by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSuccess by remember { mutableStateOf(false) }

    // Zoom transition
    val zoomScale = remember { Animatable(1f) }

    // Shake animation for errors
    val shakeOffset = remember { Animatable(0f) }

    val maxPinLength = 4

    fun haptic() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun onNumberPressed(number: Int) {
        if (pin.length < maxPinLength) {
            haptic()
            pin += number.toString()
            errorMessage = null
        }
    }

    fun onDeletePressed() {
        if (pin.isNotEmpty()) {
            haptic()
            pin = pin.dropLast(1)
        }
    }

    // Auto-submit when PIN is complete
    LaunchedEffect(pin) {
        if (pin.length == maxPinLength) {
            delay(200) // Brief pause for visual feedback
            if (isSettingPin) {
                if (!isConfirming) {
                    confirmPin = pin
                    pin = ""
                    isConfirming = true
                } else {
                    if (pin == confirmPin) {
                        prefs.pin = pin
                        prefs.isPinSet = true
                        showSuccess = true
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        delay(300)
                        // Zoom transition
                        zoomScale.animateTo(
                            targetValue = 20f,
                            animationSpec = tween(500, easing = FastOutSlowInEasing)
                        )
                        onPinVerified()
                    } else {
                        // PINs don't match
                        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                        shakeOffset.animateTo(0f, animationSpec = keyframes {
                            durationMillis = 400
                            (-20f) at 50
                            20f at 100
                            (-15f) at 150
                            15f at 200
                            (-10f) at 250
                            10f at 300
                            0f at 400
                        })
                        errorMessage = "PINs don't match. Try again."
                        pin = ""
                        confirmPin = ""
                        isConfirming = false
                    }
                }
            } else {
                // MVP: Any PIN works
                showSuccess = true
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                delay(300)
                zoomScale.animateTo(
                    targetValue = 20f,
                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                )
                onPinVerified()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .graphicsLayer {
                scaleX = zoomScale.value
                scaleY = zoomScale.value
                alpha = if (zoomScale.value > 5f) 0f else 1f
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
                .graphicsLayer { translationX = shakeOffset.value },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Icon
            Icon(
                if (showSuccess) Icons.Default.CheckCircle else Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = if (showSuccess) MaterialTheme.colorScheme.tertiary
                       else MaterialTheme.colorScheme.primary
            )

            // Title
            Text(
                text = when {
                    showSuccess -> "Welcome!"
                    isSettingPin && isConfirming -> "Confirm PIN"
                    isSettingPin -> "Create PIN"
                    else -> "Enter PIN"
                },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Subtitle
            Text(
                text = when {
                    showSuccess -> "Access granted"
                    isSettingPin && isConfirming -> "Re-enter your 4-digit PIN"
                    isSettingPin -> "Set a 4-digit PIN to secure your data"
                    else -> "Enter your 4-digit PIN"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // PIN dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(maxPinLength) { index ->
                    PinDot(filled = index < pin.length)
                }
            }

            // Error message
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Number pad
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Row 1-3
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    NumberButton(1) { onNumberPressed(1) }
                    NumberButton(2) { onNumberPressed(2) }
                    NumberButton(3) { onNumberPressed(3) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    NumberButton(4) { onNumberPressed(4) }
                    NumberButton(5) { onNumberPressed(5) }
                    NumberButton(6) { onNumberPressed(6) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    NumberButton(7) { onNumberPressed(7) }
                    NumberButton(8) { onNumberPressed(8) }
                    NumberButton(9) { onNumberPressed(9) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    // Empty space
                    Box(modifier = Modifier.size(72.dp))
                    NumberButton(0) { onNumberPressed(0) }
                    // Delete button
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .clickable { onDeletePressed() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PinDot(filled: Boolean) {
    val scale = remember { Animatable(if (filled) 0.5f else 1f) }

    LaunchedEffect(filled) {
        if (filled) {
            scale.animateTo(1.3f, animationSpec = tween(100))
            scale.animateTo(1f, animationSpec = tween(100))
        }
    }

    Box(
        modifier = Modifier
            .size(16.dp)
            .scale(scale.value)
            .clip(CircleShape)
            .then(
                if (filled) Modifier.background(MaterialTheme.colorScheme.primary)
                else Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
    )
}

@Composable
private fun NumberButton(number: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number.toString(),
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

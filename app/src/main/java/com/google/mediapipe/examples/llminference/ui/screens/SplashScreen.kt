package com.google.mediapipe.examples.llminference.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mediapipe.examples.llminference.data.DemoDataSeeder
import com.google.mediapipe.examples.llminference.data.DemoGalleryExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    // Logo animations
    val logoScale = remember { Animatable(0.3f) }
    val logoAlpha = remember { Animatable(0f) }
    val subtitleAlpha = remember { Animatable(0f) }

    // Loading dots animation
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dot1Alpha = infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600), repeatMode = RepeatMode.Reverse
        ), label = "dot1"
    )
    val dot2Alpha = infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200), repeatMode = RepeatMode.Reverse
        ), label = "dot2"
    )
    val dot3Alpha = infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400), repeatMode = RepeatMode.Reverse
        ), label = "dot3"
    )

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            DemoDataSeeder.seedIfNeeded(appContext)
            DemoDataSeeder.backfillVisitSummariesIfNeeded(appContext)
            DemoDataSeeder.seedFourManualDemoPatientsIfNeeded(appContext)
            DemoGalleryExport.exportOnceIfNeeded(appContext)
        }
        // Animate logo in (parallel coroutines inside LaunchedEffect scope)
        launch {
            logoScale.animateTo(1f, animationSpec = tween(800, easing = FastOutSlowInEasing))
        }
        launch {
            logoAlpha.animateTo(1f, animationSpec = tween(600))
        }
        delay(500)
        subtitleAlpha.animateTo(1f, animationSpec = tween(400))
        delay(2000)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo icon
            Text(
                text = "🏥",
                fontSize = 72.sp,
                modifier = Modifier
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // App name
            Text(
                text = "MedGemma",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = "AI-Powered Medical Assistant",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(subtitleAlpha.value)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Loading ellipses
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.alpha(subtitleAlpha.value)
            ) {
                LoadingDot(alpha = dot1Alpha.value)
                LoadingDot(alpha = dot2Alpha.value)
                LoadingDot(alpha = dot3Alpha.value)
            }
        }

        // Bottom branding
        Text(
            text = "Powered by MedGemma on-device",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .alpha(subtitleAlpha.value)
        )
    }
}

@Composable
private fun LoadingDot(alpha: Float) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .alpha(alpha)
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.small
            )
    )
}

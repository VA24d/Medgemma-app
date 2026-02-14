package com.google.mediapipe.examples.llminference

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun WaitingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val tokenManager = remember { com.google.mediapipe.examples.llminference.settings.TokenManager(context) }
    
    // If token exists, skip this screen and go directly to download
    LaunchedEffect(Unit) {
        if (tokenManager.hasToken()) {
            // Token exists, proceed immediately
            onFinished()
        } else {
            // No token, show brief waiting message then proceed to OAuth
            delay(2000)
            onFinished()
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text(
            text = if (tokenManager.hasToken()) "Loading with your token..." else "Preparing model...",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp)
        )
    }
}

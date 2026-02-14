package com.google.mediapipe.examples.llminference

import android.graphics.Bitmap
import java.util.UUID

/**
 * Used to represent a ChatMessage
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val rawMessage: String = "",
    val author: String,
    val images: List<Bitmap> = emptyList(),
    val isLoading: Boolean = false,
    val isThinking: Boolean = false
) {
    val message: String
        get() = rawMessage
    val isFromUser: Boolean
        get() = author == USER_PREFIX
    val isEmpty: Boolean
        get() = rawMessage.isEmpty()
}

package com.google.mediapipe.examples.llminference

import androidx.compose.runtime.toMutableStateList

const val USER_PREFIX = "user"
const val MODEL_PREFIX = "model"
const val THINKING_MARKER_END = "<unused95>"
const val THINKING_MARKER_START = "<unused94>thought"


/** Management of the message queue. */
class UiState(
    supportsThinking: Boolean = false,
    messages: List<ChatMessage> = emptyList()
)  {
    var supportsThinking: Boolean = supportsThinking
    private val _messages: MutableList<ChatMessage> = messages.toMutableStateList()
    val messages: List<ChatMessage> = _messages.asReversed()
    private var _currentMessageId = ""

    // Tracks whether the model is currently inside a <unused94>…<unused95> block.
    // Used to suppress thinking content when supportsThinking = false.
    private var _insideThinkingBlock = false

    /** Creates a new loading message. */
    fun createLoadingMessage() {
        _insideThinkingBlock = false
        val chatMessage = ChatMessage(author = MODEL_PREFIX, isLoading = true, isThinking = supportsThinking)
        _messages.add(chatMessage)
        _currentMessageId = chatMessage.id
    }

    /**
     * Appends the specified text to the current message.
     * When [supportsThinking] is false uses a state machine to suppress the entire
     * <unused94>thought>…<unused95> block (not just the marker tokens themselves).
     */
    fun appendMessage(text: String) {
        val index = _messages.indexOfFirst { it.id == _currentMessageId }

        if (!supportsThinking) {
            // ── Thinking-disabled path: state-machine suppression ──────────────
            when {
                // Receiving the end marker: exit thinking block, keep only the suffix
                text.contains(THINKING_MARKER_END) -> {
                    _insideThinkingBlock = false
                    val suffix = text.substringAfter(THINKING_MARKER_END)
                        .replace(THINKING_MARKER_END, "").replace(THINKING_MARKER_START, "")
                        .replace("thought>", "")
                    if (suffix.isNotBlank()) {
                        _messages[index] = _messages[index].copy(
                            rawMessage = _messages[index].rawMessage + suffix,
                            isLoading = false,
                            isThinking = false
                        )
                    } else {
                        _messages[index] = _messages[index].copy(isLoading = false, isThinking = false)
                    }
                }
                // Receiving the start marker: enter thinking block
                text.contains(THINKING_MARKER_START) || text.contains("thought>") -> {
                    _insideThinkingBlock = true
                    // discard
                }
                // Inside thinking block: discard silently
                _insideThinkingBlock -> { /* discard */ }
                // Normal token outside a thinking block
                else -> {
                    appendToMessage(_currentMessageId, text)
                }
            }
        } else {
            // ── Thinking-enabled path: original bubble-split logic ──────────────
            if (text.contains(THINKING_MARKER_END)) {
                val thinkingEnd = text.indexOf(THINKING_MARKER_END) + THINKING_MARKER_END.length
                val prefix = text.substring(0, thinkingEnd)
                val suffix = text.substring(thinkingEnd)
                appendToMessage(_currentMessageId, prefix)
                if (_messages[index].isEmpty) {
                    _messages[index] = _messages[index].copy(isThinking = false)
                    appendToMessage(_currentMessageId, suffix)
                } else {
                    val message = ChatMessage(
                        rawMessage = suffix,
                        author = MODEL_PREFIX,
                        isLoading = true,
                        isThinking = false
                    )
                    _messages.add(message)
                    _currentMessageId = message.id
                }
            } else {
                appendToMessage(_currentMessageId, text)
            }
        }
    }

    private fun appendToMessage(id: String, suffix: String) : Int {
        val index = _messages.indexOfFirst { it.id == id }
        val newText = suffix.replace(THINKING_MARKER_END, "").replace(THINKING_MARKER_START, "")
        _messages[index] = _messages[index].copy(
            rawMessage = _messages[index].rawMessage + newText,
            isLoading = false
        )
        return index
    }

    /** Creates a new message with the specified text and author. */
    fun addMessage(text: String, author: String, images: List<android.graphics.Bitmap> = emptyList()) {
        val chatMessage = ChatMessage(
            rawMessage = text,
            author = author,
            images = images
        )
        _messages.add(chatMessage)
        _currentMessageId = chatMessage.id
    }

    /** Clear all messages. */
    fun clearMessages() {
        _messages.clear()
    }
}

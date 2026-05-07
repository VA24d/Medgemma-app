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
        get() {
            // Strip thinking blocks:
            // Model uses <unused94>thought...content...<unused95>response format
            val thinkPatterns = listOf(
                Regex("""<unused94>thought[\s\S]*?<unused95>"""),
                Regex("""<unused94>[\s\S]*?<unused95>"""),
                Regex("""<think>[\s\S]*?</think>""")
            )
            var cleaned = rawMessage
            for (pattern in thinkPatterns) {
                cleaned = pattern.replace(cleaned, "")
            }
            cleaned = cleaned.trim()
            // Strip unclosed thinking tags at the start (still streaming)
            if (cleaned.startsWith("<unused94>thought")) {
                cleaned = cleaned.removePrefix("<unused94>thought").trim()
            }
            if (cleaned.startsWith("<unused94>")) {
                cleaned = cleaned.removePrefix("<unused94>").trim()
            }
            if (cleaned.startsWith("<unused95>")) {
                cleaned = cleaned.removePrefix("<unused95>").trim()
            }
            if (cleaned.startsWith("<think>")) {
                cleaned = cleaned.removePrefix("<think>").trim()
            }
            cleaned = ChatMessage.stripPlaintextThinkingProcessSection(cleaned)
            return cleaned
        }
    val isFromUser: Boolean
        get() = author == USER_PREFIX
    val isEmpty: Boolean
        get() = rawMessage.isEmpty()

    companion object {
        /**
         * Some runs still emit a Markdown "Thinking process:" preamble even when native skip-thinking
         * is on — strip that block before display (does not remove legitimate clinical prose elsewhere).
         */
        internal fun stripPlaintextThinkingProcessSection(text: String): String {
            var s = text
            val block = Regex(
                """(?is)(^|\n)\s*(#{1,3}\s*)?\*{0,2}\s*Thinking\s*Process\*{0,2}\s*:([\s\S]*?)(?=\n\s*\n\s*(?:[^\d\s\n#*]|[#]{1,3}\s+\S|\*\*\s*\S))"""
            )
            while (true) {
                val m = block.find(s) ?: break
                s = s.removeRange(m.range).trimStart()
            }
            return s.trim()
        }
    }
}

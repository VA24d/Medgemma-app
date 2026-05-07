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
            cleaned = ChatMessage.stripAssistantPlanningNoise(cleaned)
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

        /**
         * Removes **planning / chain-of-thought** openers ("Okay, I need to provide…", "I should cover…")
         * at the **start** of a reply. Does **not** remove normal pleasantries like "Okay, Bhaskar! I can
         * explain…" (no "I need to" / cover-thesis phrasing).
         * Also drops lone outline index lines ("4.") and trailing `].` decode garbage.
         */
        internal fun stripAssistantPlanningNoise(text: String): String {
            var s = text.trimEnd()
            s = s.replace(Regex("""\]\s*\.?\s*$"""), "").trimEnd()

            val lines = s.lines().toMutableList()
            // Only strong planning markers (follow-up turns often leak these, not the friendly "Okay, name!" line).
            val planningLine = listOf(
                Regex("""(?i)^okay,\s*i\s+need\s+to\b"""),
                Regex("""(?i)^i\s+need\s+to\s+(provide|explain|cover|write|give|break|clarify|describe|answer|list|detail|go through|begin|break down|start by|keep)\b"""),
                Regex("""(?i)^i\s+should\s+cover\b"""),
                Regex("""(?i)^i\s+should\s+start\s+by\s+(outlining|explaining|listing|with)\b"""),
                Regex("""(?i)^let me(?:\s+first)?\s+(start|explain|break|walk|clarify|outline|list|see if i)\b"""),
                Regex("""(?i)^here'?s (what|how) i(\s+will|\s+need to|\s+am going to|\s+should|\s+plan to)\b"""),
                Regex("""(?i)^first,\s+i\s+(need\s+to|should|will|am going to)\b"""),
                Regex("""(?i)^i(\s+am)?\s+going to\s+(provide|explain|cover|break|walk|start)\b"""),
            )
            var guard = 0
            while (lines.isNotEmpty() && guard++ < 24) {
                val line = lines.first().trim()
                if (line.isEmpty()) {
                    lines.removeAt(0)
                    continue
                }
                if (line.matches(Regex("""^\d+\.\s*$"""))) {
                    lines.removeAt(0)
                    continue
                }
                val isPlanning = planningLine.any { it.containsMatchIn(line) }
                if (isPlanning) {
                    lines.removeAt(0)
                    continue
                }
                break
            }
            return lines.joinToString("\n").trim()
        }
    }
}

package com.google.mediapipe.examples.llminference.cloud

object CloudThinkingStrip {
    private const val THINK_START = "<unused94>"
    private const val THINK_END = "<unused95>"

    fun stripFull(text: String): String {
        var result = text.replace(Regex("<unused94>thought>[\\s\\S]*?<unused95>"), "")
        result = result.replace(THINK_START, "").replace(THINK_END, "").replace("thought>", "").trim()
        return result
    }
}

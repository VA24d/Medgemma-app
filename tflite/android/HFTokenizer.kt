package com.google.mediapipe.examples.llminference

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File

/**
 * Simple tokenizer that parses HuggingFace tokenizer.json format.
 * Supports encode (text → token IDs) and decode (token IDs → text).
 */
class HFTokenizer(tokenizerPath: String) {

    companion object {
        private const val TAG = "HFTokenizer"
        const val BOS_TOKEN_ID = 2   // <bos>
        const val EOS_TOKEN_ID = 1   // <eos>
        const val PAD_TOKEN_ID = 0   // <pad>
        const val START_OF_TURN_ID = 105  // <start_of_turn>
        const val END_OF_TURN_ID = 106    // <end_of_turn>
    }

    // Vocab: token string → id
    private val tokenToId: Map<String, Int>
    // Reverse vocab: id → token string
    private val idToToken: Map<Int, String>
    // Special tokens that must be matched as whole strings before regular tokenization
    private val specialTokens: List<Pair<String, Int>>  // sorted longest-first

    init {
        val file = File(tokenizerPath)
        require(file.exists()) { "Tokenizer file not found: $tokenizerPath" }

        val json = file.readText()
        val tokenizerData = Gson().fromJson(json, TokenizerJson::class.java)

        // Build vocab from model and added_tokens
        val vocab = mutableMapOf<String, Int>()
        val specials = mutableListOf<Pair<String, Int>>()

        // Try added_tokens first (these have explicit IDs)
        tokenizerData.addedTokens?.forEach { token ->
            vocab[token.content] = token.id
            // Collect non-trivial special tokens for pre-splitting
            if (token.special == true && token.content.length > 1 &&
                !token.content.startsWith("<pad>") &&
                !token.content.startsWith("<eos>") &&
                !token.content.startsWith("<bos>") &&
                !token.content.startsWith("<unk>")) {
                specials.add(token.content to token.id)
            }
        }

        // Then try model.vocab (map of token → id)
        if (tokenizerData.model?.vocabMap != null) {
            tokenizerData.model.vocabMap.forEach { (token, id) ->
                vocab[token] = id
            }
        }

        tokenToId = vocab
        idToToken = vocab.entries.associate { (k, v) -> v to k }
        // Sort longest-first so longer matches take priority
        specialTokens = specials.sortedByDescending { it.first.length }

        Log.i(TAG, "Loaded tokenizer with ${tokenToId.size} tokens, ${specialTokens.size} special tokens")
        Log.i(TAG, "Special tokens: ${specialTokens.map { "${it.first}=${it.second}" }}")
    }

    /**
     * Tokenize text that may contain special tokens (e.g., <start_of_turn>, <end_of_turn>).
     * Special tokens from added_tokens are matched FIRST as literal strings,
     * then each remaining text segment is tokenized with greedy longest-match.
     */
    fun encode(text: String): List<Int> {
        if (text.isEmpty()) return listOf(BOS_TOKEN_ID)

        val tokens = mutableListOf(BOS_TOKEN_ID)

        // Split text on special tokens, preserving order
        val segments = mutableListOf<Any>() // String (regular text) or Int (special token ID)
        var remaining = text
        while (remaining.isNotEmpty()) {
            var earliestIdx = remaining.length
            var matchedToken: Pair<String, Int>? = null

            for (st in specialTokens) {
                val idx = remaining.indexOf(st.first)
                if (idx in 0 until earliestIdx) {
                    earliestIdx = idx
                    matchedToken = st
                }
            }

            if (matchedToken != null) {
                if (earliestIdx > 0) {
                    segments.add(remaining.substring(0, earliestIdx))
                }
                segments.add(matchedToken.second)
                remaining = remaining.substring(earliestIdx + matchedToken.first.length)
            } else {
                segments.add(remaining)
                remaining = ""
            }
        }

        // Tokenize each segment
        for (segment in segments) {
            when (segment) {
                is Int -> tokens.add(segment) // special token ID
                is String -> tokens.addAll(encodeRegularText(segment))
            }
        }

        return tokens
    }

    /**
     * Greedy longest-match tokenization for regular (non-special) text.
     */
    private fun encodeRegularText(text: String): List<Int> {
        if (text.isEmpty()) return emptyList()

        val tokens = mutableListOf<Int>()
        var remaining = text

        // Replace spaces with the SentencePiece space marker (▁ = U+2581)
        remaining = remaining.replace(" ", "▁")
        if (!remaining.startsWith("▁") && !remaining.startsWith("\n")) {
            remaining = "▁$remaining"
        }

        var i = 0
        while (i < remaining.length) {
            var bestMatch = ""
            var bestId = -1

            // Try longest match first (greedy tokenization)
            val maxLen = minOf(remaining.length - i, 32) // cap token length
            for (len in maxLen downTo 1) {
                val candidate = remaining.substring(i, i + len)
                val id = tokenToId[candidate]
                if (id != null) {
                    bestMatch = candidate
                    bestId = id
                    break
                }
            }

            if (bestId >= 0) {
                tokens.add(bestId)
                i += bestMatch.length
            } else {
                // Unknown character — try single-byte fallback
                val charStr = remaining[i].toString()
                val id = tokenToId[charStr] ?: tokenToId["<unk>"] ?: 3
                tokens.add(id)
                i++
            }
        }

        return tokens
    }

    /**
     * Decode token IDs back to text.
     */
    fun decode(ids: List<Int>): String {
        val sb = StringBuilder()
        for (id in ids) {
            if (id == BOS_TOKEN_ID || id == EOS_TOKEN_ID || id == PAD_TOKEN_ID) continue
            val token = idToToken[id] ?: ""
            sb.append(token)
        }
        // Replace SentencePiece space marker back to space
        return sb.toString().replace("▁", " ").trimStart()
    }

    fun decodeToken(id: Int): String {
        if (id == BOS_TOKEN_ID || id == EOS_TOKEN_ID || id == PAD_TOKEN_ID) return ""
        val token = idToToken[id] ?: ""
        return token.replace("▁", " ")
    }

    // JSON data classes for parsing tokenizer.json
    private data class TokenizerJson(
        @SerializedName("model") val model: ModelSection?,
        @SerializedName("added_tokens") val addedTokens: List<AddedToken>?
    )

    private data class ModelSection(
        @SerializedName("vocab") val vocabMap: Map<String, Int>?,
        @SerializedName("type") val type: String?
    )

    private data class AddedToken(
        @SerializedName("id") val id: Int,
        @SerializedName("content") val content: String,
        @SerializedName("special") val special: Boolean? = false
    )
}

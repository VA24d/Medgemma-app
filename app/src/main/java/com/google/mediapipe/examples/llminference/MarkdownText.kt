package com.google.mediapipe.examples.llminference

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.google.mediapipe.examples.llminference.settings.LocalModelFiles
import com.google.mediapipe.examples.llminference.utils.LanguageExtension

// ──────────────────────────────────────────────
// Block-level AST
// ──────────────────────────────────────────────

private sealed class MarkdownBlock {
    data class Heading(val text: String, val level: Int) : MarkdownBlock()
    data class Bullet(val text: String, val indent: Int = 0) : MarkdownBlock()
    data class Ordered(val text: String, val number: Int) : MarkdownBlock()
    data class Code(val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class HRule(val unused: Unit = Unit) : MarkdownBlock()
    object BlankLine : MarkdownBlock()
}

// ──────────────────────────────────────────────
// Parser
// ──────────────────────────────────────────────

private fun parseMarkdown(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()

    // Strip outermost ```markdown … ``` or ``` … ``` wrappers that some models
    // emit around their entire response. We want to render the content, not a
    // giant code block. Also handles the incomplete-stream case where only the
    // opening fence has arrived (no closing fence yet).
    val stripped = markdown.trim()
    val unwrapped = run {
        // Complete fence: ```lang\n...\n```
        val completeFence = Regex("""^```[a-zA-Z]*\n([\s\S]*?)\n```\s*$""")
        val complete = completeFence.matchEntire(stripped)
        if (complete != null) {
            complete.groupValues[1]
        } else {
            // Incomplete stream: starts with ```lang\n but no closing fence yet
            val openFence = Regex("""^```[a-zA-Z]*\n([\s\S]*)$""")
            val open = openFence.matchEntire(stripped)
            if (open != null) open.groupValues[1] else stripped
        }
    }

    val lines = unwrapped.lines()
    var i = 0
    var inCode = false
    val codeBuf = StringBuilder()

    while (i < lines.size) {
        val raw = lines[i]
        val trimmed = raw.trimStart()

        // ── Code fence ──
        if (trimmed.startsWith("```")) {
            if (inCode) {
                blocks += MarkdownBlock.Code(codeBuf.toString().trimEnd('\n'))
                codeBuf.clear()
                inCode = false
            } else {
                inCode = true
            }
            i++; continue
        }
        if (inCode) { codeBuf.append(raw).append("\n"); i++; continue }

        // ── Headings ──
        if (trimmed.startsWith("### ")) { blocks += MarkdownBlock.Heading(trimmed.removePrefix("### "), 3); i++; continue }
        if (trimmed.startsWith("## "))  { blocks += MarkdownBlock.Heading(trimmed.removePrefix("## "), 2); i++; continue }
        if (trimmed.startsWith("# "))   { blocks += MarkdownBlock.Heading(trimmed.removePrefix("# "), 1); i++; continue }

        // ── Horizontal rule ──
        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            blocks += MarkdownBlock.HRule()
            i++; continue
        }

        // ── Bullet ──
        val bulletIndent = (raw.length - trimmed.length) / 2
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            blocks += MarkdownBlock.Bullet(trimmed.drop(2).trimStart(), bulletIndent)
            i++; continue
        }

        // ── Ordered list ──
        val orderedMatch = Regex("""^(\d+)\.\s+(.+)""").find(trimmed)
        if (orderedMatch != null) {
            val num = orderedMatch.groupValues[1].toIntOrNull() ?: 1
            blocks += MarkdownBlock.Ordered(orderedMatch.groupValues[2], num)
            i++; continue
        }

        // ── Blank line ──
        if (trimmed.isBlank()) {
            if (blocks.isNotEmpty() && blocks.last() !is MarkdownBlock.BlankLine) {
                blocks += MarkdownBlock.BlankLine
            }
            i++; continue
        }

        // ── Paragraph (merge consecutive lines) ──
        val last = blocks.lastOrNull()
        if (last is MarkdownBlock.Paragraph) {
            blocks[blocks.lastIndex] = MarkdownBlock.Paragraph(last.text + " " + trimmed)
        } else {
            blocks += MarkdownBlock.Paragraph(trimmed)
        }
        i++
    }

    // Unclosed code block
    if (inCode && codeBuf.isNotEmpty()) blocks += MarkdownBlock.Code(codeBuf.toString().trimEnd('\n'))

    // Strip leading/trailing blank lines
    return blocks.dropWhile { it is MarkdownBlock.BlankLine }
                 .dropLastWhile { it is MarkdownBlock.BlankLine }
}

// ──────────────────────────────────────────────
// Inline markdown → AnnotatedString
// ──────────────────────────────────────────────

private fun buildInline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        when {
            // ***bold italic***
            text.startsWith("***", i) -> {
                val end = text.indexOf("***", i + 3)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 3, end))
                    }
                    i = end + 3
                } else append(text[i++])
            }
            // **bold**
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else append(text[i++])
            }
            // *italic* (not **)
            ch == '*' && !text.startsWith("**", i) -> {
                val end = text.indexOf('*', i + 1)
                if (end != -1 && !text.startsWith("**", end)) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else append(text[i++])
            }
            // `inline code`
            ch == '`' && !text.startsWith("```", i) -> {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else append(text[i++])
            }
            else -> append(text[i++])
        }
    }
}

// ──────────────────────────────────────────────
// Composable
// ──────────────────────────────────────────────

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.Unspecified,
) {
    val context = LocalContext.current
    
    val processedMarkdown = remember(markdown) {
        val isVernacularEnabled = LocalModelFiles.isLanguageExtensionEnabled(context)
        LanguageExtension.applyVernacular(markdown, isVernacularEnabled)
    }

    val blocks = remember(processedMarkdown) { parseMarkdown(processedMarkdown) }
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        blocks.forEach { block ->
            when (block) {
                // ── Heading ──
                is MarkdownBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                    val topPad = if (block.level == 1) 6.dp else 4.dp
                    Text(
                        text = buildInline(block.text),
                        style = style,
                        color = textColor,
                        modifier = Modifier.padding(top = topPad)
                    )
                }

                // ── Bullet ──
                is MarkdownBlock.Bullet -> {
                    Row(modifier = Modifier.padding(start = (block.indent * 12).dp)) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                            modifier = Modifier.padding(end = 6.dp, top = 1.dp)
                        )
                        Text(
                            text = buildInline(block.text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                        )
                    }
                }

                // ── Ordered ──
                is MarkdownBlock.Ordered -> {
                    Row {
                        Text(
                            text = "${block.number}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                            modifier = Modifier.widthIn(min = 24.dp).padding(end = 6.dp, top = 1.dp)
                        )
                        Text(
                            text = buildInline(block.text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                        )
                    }
                }

                // ── Code block ──
                is MarkdownBlock.Code -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(codeBackground, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        )
                    }
                }

                // ── Horizontal rule ──
                is MarkdownBlock.HRule -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }

                // ── Blank line ──
                MarkdownBlock.BlankLine -> {
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // ── Paragraph ──
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = buildInline(block.text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                    )
                }
            }
        }
    }
}

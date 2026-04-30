package com.wizaird.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Simple Markdown text renderer for Compose.
 * Supports: headers (# ## ###), **bold**, *italic*, `code`, ~~strikethrough~~,
 * bullet points (- *), numbered lists (1. 2.), and [links](url).
 */
@Composable
fun MarkdownText(
    markdown: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE
) {
    // Parse the entire markdown into a single AnnotatedString
    val annotatedText = parseMarkdown(markdown, style)
    
    // Use Text which properly handles maxLines with text wrapping
    Text(
        text = annotatedText,
        style = style,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

private fun parseMarkdown(markdown: String, baseStyle: androidx.compose.ui.text.TextStyle): AnnotatedString {
    return buildAnnotatedString {
        val lines = markdown.lines()
        var inCodeBlock = false
        var listCounter = 0
        var previousWasBullet = false
        
        lines.forEachIndexed { index, line ->
            val trimmedLine = line.trimStart()
            val indent = line.length - trimmedLine.length
            
            when {
                // Code block: ```
                trimmedLine.startsWith("```") -> {
                    inCodeBlock = !inCodeBlock
                    // Don't render the ``` markers
                    previousWasBullet = false
                }
                // Inside code block
                inCodeBlock -> {
                    withStyle(SpanStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        background = Color(0x20FFFFFF)
                    )) {
                        append(line)
                    }
                    previousWasBullet = false
                }
                // Header 1: # text
                trimmedLine.startsWith("# ") && !trimmedLine.startsWith("## ") -> {
                    val content = trimmedLine.substring(2)
                    withStyle(SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = (baseStyle.fontSize.value * 1.5f).sp
                    )) {
                        append(parseInlineMarkdown(content))
                    }
                    previousWasBullet = false
                }
                // Header 2: ## text
                trimmedLine.startsWith("## ") && !trimmedLine.startsWith("### ") -> {
                    val content = trimmedLine.substring(3)
                    withStyle(SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = (baseStyle.fontSize.value * 1.3f).sp
                    )) {
                        append(parseInlineMarkdown(content))
                    }
                    previousWasBullet = false
                }
                // Header 3: ### text
                trimmedLine.startsWith("### ") -> {
                    val content = trimmedLine.substring(4)
                    withStyle(SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = (baseStyle.fontSize.value * 1.15f).sp
                    )) {
                        append(parseInlineMarkdown(content))
                    }
                    previousWasBullet = false
                }
                // Numbered list: 1. item or 1) item
                trimmedLine.matches(Regex("^\\d+[.)].+")) -> {
                    val dotIndex = trimmedLine.indexOfFirst { it == '.' || it == ')' }
                    val number = trimmedLine.substring(0, dotIndex)
                    val content = trimmedLine.substring(dotIndex + 1).trim()
                    append(" ".repeat(indent))
                    append("$number. ")
                    append(parseInlineMarkdown(content))
                    // Add small spacing after list items
                    if (index < lines.lastIndex) {
                        val nextLine = lines[index + 1].trimStart()
                        if (nextLine.matches(Regex("^\\d+[.)].+")) || 
                            nextLine.startsWith("* ") || 
                            nextLine.startsWith("- ")) {
                            append("\n")
                        }
                    }
                    previousWasBullet = true
                }
                // Bullet point: * item or - item
                trimmedLine.startsWith("* ") || trimmedLine.startsWith("- ") -> {
                    val content = trimmedLine.substring(2)
                    append(" ".repeat(indent))
                    append("• ")
                    append(parseInlineMarkdown(content))
                    // Add small spacing after list items
                    if (index < lines.lastIndex) {
                        val nextLine = lines[index + 1].trimStart()
                        if (nextLine.matches(Regex("^\\d+[.)].+")) || 
                            nextLine.startsWith("* ") || 
                            nextLine.startsWith("- ")) {
                            append("\n")
                        }
                    }
                    previousWasBullet = true
                }
                // Blockquote: > text
                trimmedLine.startsWith("> ") -> {
                    val content = trimmedLine.substring(2)
                    withStyle(SpanStyle(
                        fontStyle = FontStyle.Italic,
                        color = Color(0xFFAAAAAA)
                    )) {
                        append("│ ")
                        append(parseInlineMarkdown(content))
                    }
                    previousWasBullet = false
                }
                // Horizontal rule: --- or ***
                trimmedLine.matches(Regex("^[-*]{3,}$")) -> {
                    append("─".repeat(20))
                    previousWasBullet = false
                }
                // Regular line
                else -> {
                    append(parseInlineMarkdown(line))
                    previousWasBullet = false
                }
            }
            
            // Add newline between lines (but not after the last line)
            if (index < lines.lastIndex) {
                append("\n")
            }
        }
    }
}

private fun parseInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // Bold: **text**
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Strikethrough: ~~text~~
                text.startsWith("~~", i) -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Italic: *text* or _text_ (but not **)
                (text.startsWith("*", i) && !text.startsWith("**", i)) || text.startsWith("_", i) -> {
                    val marker = text[i]
                    val end = text.indexOf(marker, i + 1)
                    if (end != -1 && (marker != '*' || !text.startsWith("**", end))) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Code: `text`
                text.startsWith("`", i) && !text.startsWith("```", i) -> {
                    val end = text.indexOf("`", i + 1)
                    if (end != -1) {
                        withStyle(SpanStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            background = Color(0x20FFFFFF)
                        )) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Link: [text](url)
                text.startsWith("[", i) -> {
                    val textEnd = text.indexOf("]", i + 1)
                    if (textEnd != -1 && textEnd + 1 < text.length && text[textEnd + 1] == '(') {
                        val urlEnd = text.indexOf(")", textEnd + 2)
                        if (urlEnd != -1) {
                            val linkText = text.substring(i + 1, textEnd)
                            // Just show the link text (we can't make it clickable in this simple implementation)
                            withStyle(SpanStyle(
                                color = Color(0xFF6B9BD1),
                                textDecoration = TextDecoration.Underline
                            )) {
                                append(linkText)
                            }
                            i = urlEnd + 1
                        } else {
                            append(text[i])
                            i++
                        }
                    } else {
                        append(text[i])
                        i++
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}

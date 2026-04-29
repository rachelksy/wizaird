package com.wizaird.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Simple Markdown text renderer for Compose.
 * Supports: **bold**, *italic*, `code`, and bullet points.
 */
@Composable
fun MarkdownText(
    markdown: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier
) {
    val lines = markdown.lines()
    
    Column(modifier = modifier) {
        lines.forEachIndexed { index, line ->
            when {
                // Bullet point: * item or - item
                line.trimStart().startsWith("* ") || line.trimStart().startsWith("- ") -> {
                    val indent = line.takeWhile { it == ' ' }.length
                    val content = line.trimStart().substring(2) // Remove "* " or "- "
                    Row(modifier = Modifier.fillMaxWidth().padding(start = (indent * 4).dp, bottom = 6.dp)) {
                        BasicText(
                            text = "• ",
                            style = style
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        BasicText(
                            text = parseInlineMarkdown(content, style),
                            style = style,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                // Regular line
                else -> {
                    BasicText(
                        text = parseInlineMarkdown(line, style),
                        style = style,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun parseInlineMarkdown(text: String, baseStyle: androidx.compose.ui.text.TextStyle): AnnotatedString {
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
                // Italic: *text* (but not **)
                text.startsWith("*", i) && !text.startsWith("**", i) -> {
                    val end = text.indexOf("*", i + 1)
                    if (end != -1 && !text.startsWith("**", end)) {
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
                text.startsWith("`", i) -> {
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
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}

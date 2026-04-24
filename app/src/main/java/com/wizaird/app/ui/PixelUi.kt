package com.wizaird.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wizaird.app.ui.theme.Ink
import com.wizaird.app.ui.theme.Paper
import com.wizaird.app.ui.theme.PixelFont

fun pixelStyle(size: Int, color: Color = Ink) = TextStyle(
    fontFamily = PixelFont,
    fontSize = size.sp,
    lineHeight = (size * 1.8f).sp,
    color = color,
    letterSpacing = 0.sp,
    baselineShift = BaselineShift(0.2f),
    platformStyle = PlatformTextStyle(
        includeFontPadding = false
    ),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both
    )
)

fun DrawScope.drawPixelBorder(
    top: Boolean = false,
    bottom: Boolean = false,
    left: Boolean = false,
    all: Boolean = true
) {
    val stroke = 3f
    val c = Ink
    if (all || top)    drawLine(c, Offset(0f, 0f), Offset(size.width, 0f), stroke)
    if (all || bottom) drawLine(c, Offset(0f, size.height), Offset(size.width, size.height), stroke)
    if (all || left)   drawLine(c, Offset(0f, 0f), Offset(0f, size.height), stroke)
    if (all)           drawLine(c, Offset(size.width, 0f), Offset(size.width, size.height), stroke)
}

val PixelSize = 3.dp  // one pixel block — controls border thickness and corner cut size

// Pixel-art box with square-cut corners.
// Fill drawn behind content; border + corners drawn ON TOP of content
// so they always show regardless of what color children have.
@Composable
fun PixelBox(
    modifier: Modifier = Modifier,
    fillColor: Color,
    borderColor: Color = Ink,
    cutColor: Color = Paper,
    px: Dp = PixelSize,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            // Draw fill behind content
            .drawBehind {
                drawRect(fillColor, Offset.Zero, size)
            }
            // Draw border + corners ON TOP of content
            .drawWithContent {
                drawContent()  // draw children first
                val pxPx = px.toPx()
                val w = size.width
                val h = size.height

                // Border edges
                drawRect(borderColor, Offset(pxPx * 2, 0f), Size(w - pxPx * 4, pxPx))           // top
                drawRect(borderColor, Offset(pxPx * 2, h - pxPx), Size(w - pxPx * 4, pxPx))     // bottom
                drawRect(borderColor, Offset(0f, pxPx * 2), Size(pxPx, h - pxPx * 4))           // left
                drawRect(borderColor, Offset(w - pxPx, pxPx * 2), Size(pxPx, h - pxPx * 4))    // right

                // Staircase step border squares (inner corner pixels)
                drawRect(borderColor, Offset(pxPx, pxPx), Size(pxPx, pxPx))                     // top-left
                drawRect(borderColor, Offset(w - pxPx * 2, pxPx), Size(pxPx, pxPx))             // top-right
                drawRect(borderColor, Offset(pxPx, h - pxPx * 2), Size(pxPx, pxPx))             // bottom-left
                drawRect(borderColor, Offset(w - pxPx * 2, h - pxPx * 2), Size(pxPx, pxPx))    // bottom-right

                // Corner cut squares (transparent gaps)
                drawRect(cutColor, Offset(0f, 0f), Size(pxPx * 2, pxPx))                        // top-left row 1
                drawRect(cutColor, Offset(0f, pxPx), Size(pxPx, pxPx))                          // top-left row 2
                drawRect(cutColor, Offset(w - pxPx * 2, 0f), Size(pxPx * 2, pxPx))             // top-right row 1
                drawRect(cutColor, Offset(w - pxPx, pxPx), Size(pxPx, pxPx))                   // top-right row 2
                drawRect(cutColor, Offset(0f, h - pxPx), Size(pxPx * 2, pxPx))                 // bottom-left row 1
                drawRect(cutColor, Offset(0f, h - pxPx * 2), Size(pxPx, pxPx))                 // bottom-left row 2
                drawRect(cutColor, Offset(w - pxPx * 2, h - pxPx), Size(pxPx * 2, pxPx))      // bottom-right row 1
                drawRect(cutColor, Offset(w - pxPx, h - pxPx * 2), Size(pxPx, pxPx))          // bottom-right row 2
            }
            .padding(px),
        content = content
    )
}

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
import com.wizaird.app.ui.theme.LocalWizairdColors
import com.wizaird.app.ui.theme.Paper
import com.wizaird.app.ui.theme.PixelFont
import com.wizaird.app.ui.theme.PixeloidFont

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

fun minecraftStyle(size: Int, color: Color = Ink) = TextStyle(
    fontFamily = PixeloidFont,
    fontSize = size.sp,
    lineHeight = (size * 1.4f).sp,
    color = color,
    letterSpacing = 0.sp,
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
    right: Boolean = false,
    all: Boolean = false,
    color: Color = Ink
) {
    val stroke = 3f
    if (all || top)    drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), stroke)
    if (all || bottom) drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), stroke)
    if (all || left)   drawLine(color, Offset(0f, 0f), Offset(0f, size.height), stroke)
    if (all || right)  drawLine(color, Offset(size.width, 0f), Offset(size.width, size.height), stroke)
}

val PixelSize = 3.dp  // one pixel block — controls border thickness and corner cut size

// Pixel-art box with square-cut corners.
// Fill drawn behind content; border + corners drawn ON TOP of content
// so they always show regardless of what color children have.
@Composable
fun PixelBox(
    modifier: Modifier = Modifier,
    fillColor: Color,
    borderColor: Color? = null,   // null = use theme ink
    cutColor: Color? = null,      // null = use theme background
    px: Dp = PixelSize,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = LocalWizairdColors.current
    val resolvedBorder = borderColor ?: colors.border
    val resolvedCut    = cutColor    ?: colors.background

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

                // Border edges (start at 3px from corners for 3-step staircase)
                drawRect(resolvedBorder, Offset(pxPx * 3, 0f), Size(w - pxPx * 6, pxPx))           // top
                drawRect(resolvedBorder, Offset(pxPx * 3, h - pxPx), Size(w - pxPx * 6, pxPx))     // bottom
                drawRect(resolvedBorder, Offset(0f, pxPx * 3), Size(pxPx, h - pxPx * 6))           // left
                drawRect(resolvedBorder, Offset(w - pxPx, pxPx * 3), Size(pxPx, h - pxPx * 6))    // right

                // Staircase step border squares — 3-step corner (3 pixels per corner)
                // Top-left
                drawRect(resolvedBorder, Offset(pxPx * 2, pxPx), Size(pxPx, pxPx))
                drawRect(resolvedBorder, Offset(pxPx, pxPx * 2), Size(pxPx, pxPx))
                // Top-right
                drawRect(resolvedBorder, Offset(w - pxPx * 3, pxPx), Size(pxPx, pxPx))
                drawRect(resolvedBorder, Offset(w - pxPx * 2, pxPx * 2), Size(pxPx, pxPx))
                // Bottom-left
                drawRect(resolvedBorder, Offset(pxPx * 2, h - pxPx * 2), Size(pxPx, pxPx))
                drawRect(resolvedBorder, Offset(pxPx, h - pxPx * 3), Size(pxPx, pxPx))
                // Bottom-right
                drawRect(resolvedBorder, Offset(w - pxPx * 3, h - pxPx * 2), Size(pxPx, pxPx))
                drawRect(resolvedBorder, Offset(w - pxPx * 2, h - pxPx * 3), Size(pxPx, pxPx))

                // Corner cut squares — 3-step staircase gaps
                // Top-left
                drawRect(resolvedCut, Offset(0f, 0f), Size(pxPx * 3, pxPx))                        // row 0: 3px wide
                drawRect(resolvedCut, Offset(0f, pxPx), Size(pxPx * 2, pxPx))                      // row 1: 2px wide
                drawRect(resolvedCut, Offset(0f, pxPx * 2), Size(pxPx, pxPx))                      // row 2: 1px wide
                // Top-right
                drawRect(resolvedCut, Offset(w - pxPx * 3, 0f), Size(pxPx * 3, pxPx))             // row 0: 3px wide
                drawRect(resolvedCut, Offset(w - pxPx * 2, pxPx), Size(pxPx * 2, pxPx))           // row 1: 2px wide
                drawRect(resolvedCut, Offset(w - pxPx, pxPx * 2), Size(pxPx, pxPx))               // row 2: 1px wide
                // Bottom-left
                drawRect(resolvedCut, Offset(0f, h - pxPx), Size(pxPx * 3, pxPx))                 // row 0: 3px wide
                drawRect(resolvedCut, Offset(0f, h - pxPx * 2), Size(pxPx * 2, pxPx))             // row 1: 2px wide
                drawRect(resolvedCut, Offset(0f, h - pxPx * 3), Size(pxPx, pxPx))                 // row 2: 1px wide
                // Bottom-right
                drawRect(resolvedCut, Offset(w - pxPx * 3, h - pxPx), Size(pxPx * 3, pxPx))      // row 0: 3px wide
                drawRect(resolvedCut, Offset(w - pxPx * 2, h - pxPx * 2), Size(pxPx * 2, pxPx))  // row 1: 2px wide
                drawRect(resolvedCut, Offset(w - pxPx, h - pxPx * 3), Size(pxPx, pxPx))          // row 2: 1px wide
            }
            .padding(px * 2),
        content = content
    )
}

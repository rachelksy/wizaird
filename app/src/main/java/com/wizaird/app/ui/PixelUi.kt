package com.wizaird.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wizaird.app.ui.theme.SecondaryIcon
import com.wizaird.app.ui.theme.LocalWizairdColors
import com.wizaird.app.ui.theme.Paper
import com.wizaird.app.ui.theme.PixelFont
import com.wizaird.app.ui.theme.PixeloidFont

fun pixelStyle(size: Int, color: Color = SecondaryIcon) = TextStyle(
    fontFamily = PixelFont,
    fontSize = size.sp,
    lineHeight = (size * 1f).sp,
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

fun minecraftStyle(size: Int, color: Color = SecondaryIcon) = TextStyle(
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
    color: Color = SecondaryIcon
) {
    val stroke = PixelSize.toPx()
    if (all || top)    drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), stroke)
    if (all || bottom) drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), stroke)
    if (all || left)   drawLine(color, Offset(0f, 0f), Offset(0f, size.height), stroke)
    if (all || right)  drawLine(color, Offset(size.width, 0f), Offset(size.width, size.height), stroke)
}

val PixelSize = 2.dp  // one pixel block — controls border thickness and corner cut size

// Draws a pixel-art button (corner-cut, no border) with a pixel arrow icon.
// direction: 1f = right arrow (send), -1f = left arrow (back)
fun Modifier.drawPixelArrowButton(
    fillColor: Color,
    cutColor: Color,
    arrowColor: Color,
    direction: Float = 1f
): Modifier = this.drawBehind {
    val p = PixelSize.toPx()
    val w = size.width
    val h = size.height

    // Fill
    drawRect(fillColor)

    // Cut corners — R=8 pixel-circle staircase (cuts: 5,3,2,1,1 blocks per row)
    // Top-left
    drawRect(cutColor, Offset(0f,      0f),      Size(p * 5, p))
    drawRect(cutColor, Offset(0f,      p),       Size(p * 3, p))
    drawRect(cutColor, Offset(0f,      p * 2),   Size(p * 2, p))
    drawRect(cutColor, Offset(0f,      p * 3),   Size(p,     p))
    drawRect(cutColor, Offset(0f,      p * 4),   Size(p,     p))
    // Top-right
    drawRect(cutColor, Offset(w-p*5,   0f),      Size(p * 5, p))
    drawRect(cutColor, Offset(w-p*3,   p),       Size(p * 3, p))
    drawRect(cutColor, Offset(w-p*2,   p * 2),   Size(p * 2, p))
    drawRect(cutColor, Offset(w-p,     p * 3),   Size(p,     p))
    drawRect(cutColor, Offset(w-p,     p * 4),   Size(p,     p))
    // Bottom-left
    drawRect(cutColor, Offset(0f,      h-p),     Size(p * 5, p))
    drawRect(cutColor, Offset(0f,      h-p*2),   Size(p * 3, p))
    drawRect(cutColor, Offset(0f,      h-p*3),   Size(p * 2, p))
    drawRect(cutColor, Offset(0f,      h-p*4),   Size(p,     p))
    drawRect(cutColor, Offset(0f,      h-p*5),   Size(p,     p))
    // Bottom-right
    drawRect(cutColor, Offset(w-p*5,   h-p),     Size(p * 5, p))
    drawRect(cutColor, Offset(w-p*3,   h-p*2),   Size(p * 3, p))
    drawRect(cutColor, Offset(w-p*2,   h-p*3),   Size(p * 2, p))
    drawRect(cutColor, Offset(w-p,     h-p*4),   Size(p,     p))
    drawRect(cutColor, Offset(w-p,     h-p*5),   Size(p,     p))

    // Arrow — tip points in `direction` (right = 1f, left = -1f)
    val cy = h / 2f
    if (direction > 0f) {
        // Right-pointing arrow (send)
        val cx = w / 2f + p - 1.dp.toPx()
        drawRect(arrowColor, Offset(cx - p * 3, cy - p / 2f), Size(p * 4, p))  // shaft
        drawRect(arrowColor, Offset(cx - p,     cy - p * 2),  Size(p, p))
        drawRect(arrowColor, Offset(cx,         cy - p),       Size(p, p))
        drawRect(arrowColor, Offset(cx + p,     cy - p / 2f),  Size(p, p))
        drawRect(arrowColor, Offset(cx - p,     cy + p),       Size(p, p))
        drawRect(arrowColor, Offset(cx,         cy),           Size(p, p))
        drawRect(arrowColor, Offset(cx + p,     cy - p / 2f),  Size(p, p))
    } else {
        // Left-pointing arrow (back)
        val cx = w / 2f - p + 1.dp.toPx()
        drawRect(arrowColor, Offset(cx - p,     cy - p / 2f), Size(p * 4, p))  // shaft
        drawRect(arrowColor, Offset(cx,         cy - p * 2),  Size(p, p))
        drawRect(arrowColor, Offset(cx - p,     cy - p),       Size(p, p))
        drawRect(arrowColor, Offset(cx - p * 2, cy - p / 2f),  Size(p, p))
        drawRect(arrowColor, Offset(cx,         cy + p),       Size(p, p))
        drawRect(arrowColor, Offset(cx - p,     cy),           Size(p, p))
        drawRect(arrowColor, Offset(cx - p * 2, cy - p / 2f),  Size(p, p))
    }
}

// Draws a pixel-art circle on a square box using the R=10 staircase corner cuts.
// Use on a Box with equal width and height (e.g. size(48.dp)).
fun Modifier.drawPixelCircle(
    fillColor: Color,
    borderColor: Color,
    cutColor: Color
): Modifier = this.drawBehind {
    val p = PixelSize.toPx()
    val w = size.width
    val h = size.height

    // Fill entire square first
    drawRect(fillColor)

    // Border — straight edges between the corner curves
    drawRect(borderColor, Offset(p*7,   0f),    Size(w - p*14, p))   // top
    drawRect(borderColor, Offset(p*7,   h - p), Size(w - p*14, p))   // bottom
    drawRect(borderColor, Offset(0f,    p*7),   Size(p, h - p*14))   // left
    drawRect(borderColor, Offset(w - p, p*7),   Size(p, h - p*14))   // right

    // Corner border steps — top-left
    drawRect(borderColor, Offset(p*7, p*0), Size(p,   p))
    drawRect(borderColor, Offset(p*5, p*1), Size(p*2, p))
    drawRect(borderColor, Offset(p*3, p*2), Size(p*2, p))
    drawRect(borderColor, Offset(p*2, p*3), Size(p,   p))
    drawRect(borderColor, Offset(p*2, p*4), Size(p,   p))
    drawRect(borderColor, Offset(p*1, p*5), Size(p,   p))
    drawRect(borderColor, Offset(p*1, p*6), Size(p,   p))
    drawRect(borderColor, Offset(p*0, p*7), Size(p,   p))
    // Corner border steps — top-right
    drawRect(borderColor, Offset(w-p*8, p*0), Size(p,   p))
    drawRect(borderColor, Offset(w-p*7, p*1), Size(p*2, p))
    drawRect(borderColor, Offset(w-p*5, p*2), Size(p*2, p))
    drawRect(borderColor, Offset(w-p*3, p*3), Size(p,   p))
    drawRect(borderColor, Offset(w-p*3, p*4), Size(p,   p))
    drawRect(borderColor, Offset(w-p*2, p*5), Size(p,   p))
    drawRect(borderColor, Offset(w-p*2, p*6), Size(p,   p))
    drawRect(borderColor, Offset(w-p*1, p*7), Size(p,   p))
    // Corner border steps — bottom-left
    drawRect(borderColor, Offset(p*7, h-p*1), Size(p,   p))
    drawRect(borderColor, Offset(p*5, h-p*2), Size(p*2, p))
    drawRect(borderColor, Offset(p*3, h-p*3), Size(p*2, p))
    drawRect(borderColor, Offset(p*2, h-p*4), Size(p,   p))
    drawRect(borderColor, Offset(p*2, h-p*5), Size(p,   p))
    drawRect(borderColor, Offset(p*1, h-p*6), Size(p,   p))
    drawRect(borderColor, Offset(p*1, h-p*7), Size(p,   p))
    drawRect(borderColor, Offset(p*0, h-p*8), Size(p,   p))
    // Corner border steps — bottom-right
    drawRect(borderColor, Offset(w-p*8, h-p*1), Size(p,   p))
    drawRect(borderColor, Offset(w-p*7, h-p*2), Size(p*2, p))
    drawRect(borderColor, Offset(w-p*5, h-p*3), Size(p*2, p))
    drawRect(borderColor, Offset(w-p*3, h-p*4), Size(p,   p))
    drawRect(borderColor, Offset(w-p*3, h-p*5), Size(p,   p))
    drawRect(borderColor, Offset(w-p*2, h-p*6), Size(p,   p))
    drawRect(borderColor, Offset(w-p*2, h-p*7), Size(p,   p))
    drawRect(borderColor, Offset(w-p*1, h-p*8), Size(p,   p))

    // Corner cuts — erase fill outside the curve
    // Top-left
    drawRect(cutColor, Offset(0f, p*0), Size(p*7, p))
    drawRect(cutColor, Offset(0f, p*1), Size(p*5, p))
    drawRect(cutColor, Offset(0f, p*2), Size(p*3, p))
    drawRect(cutColor, Offset(0f, p*3), Size(p*2, p))
    drawRect(cutColor, Offset(0f, p*4), Size(p*2, p))
    drawRect(cutColor, Offset(0f, p*5), Size(p*1, p))
    drawRect(cutColor, Offset(0f, p*6), Size(p*1, p))
    // Top-right
    drawRect(cutColor, Offset(w-p*7, p*0), Size(p*7, p))
    drawRect(cutColor, Offset(w-p*5, p*1), Size(p*5, p))
    drawRect(cutColor, Offset(w-p*3, p*2), Size(p*3, p))
    drawRect(cutColor, Offset(w-p*2, p*3), Size(p*2, p))
    drawRect(cutColor, Offset(w-p*2, p*4), Size(p*2, p))
    drawRect(cutColor, Offset(w-p*1, p*5), Size(p*1, p))
    drawRect(cutColor, Offset(w-p*1, p*6), Size(p*1, p))
    // Bottom-left
    drawRect(cutColor, Offset(0f, h-p*1), Size(p*7, p))
    drawRect(cutColor, Offset(0f, h-p*2), Size(p*5, p))
    drawRect(cutColor, Offset(0f, h-p*3), Size(p*3, p))
    drawRect(cutColor, Offset(0f, h-p*4), Size(p*2, p))
    drawRect(cutColor, Offset(0f, h-p*5), Size(p*2, p))
    drawRect(cutColor, Offset(0f, h-p*6), Size(p*1, p))
    drawRect(cutColor, Offset(0f, h-p*7), Size(p*1, p))
    // Bottom-right
    drawRect(cutColor, Offset(w-p*7, h-p*1), Size(p*7, p))
    drawRect(cutColor, Offset(w-p*5, h-p*2), Size(p*5, p))
    drawRect(cutColor, Offset(w-p*3, h-p*3), Size(p*3, p))
    drawRect(cutColor, Offset(w-p*2, h-p*4), Size(p*2, p))
    drawRect(cutColor, Offset(w-p*2, h-p*5), Size(p*2, p))
    drawRect(cutColor, Offset(w-p*1, h-p*6), Size(p*1, p))
    drawRect(cutColor, Offset(w-p*1, h-p*7), Size(p*1, p))
}

enum class PixelCornerStyle { Cut, Rounded, Rounded8, Circle }

// Pixel-art box with corner style:
//   Cut     — uniform 3-step staircase (diagonal look, used for input bar / buttons)
//   Rounded — non-uniform curve derived from a circle (5,3,2,1 block cuts), used for chat bubble
// Fill drawn behind content; border + corners drawn ON TOP of content
// so they always show regardless of what color children have.
//
// speechTail: if true, draws a 5-block-tall pixel triangle tail at the bottom-left,
//             making the box look like a speech bubble.
@Composable
fun PixelBox(
    modifier: Modifier = Modifier,
    fillColor: Color,
    borderColor: Color? = null,   // null = use theme secondaryIcon
    cutColor: Color? = null,      // null = use theme background
    px: Dp = PixelSize,
    cornerStyle: PixelCornerStyle = PixelCornerStyle.Cut,
    speechTail: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = LocalWizairdColors.current
    val resolvedBorder = borderColor ?: colors.border
    val resolvedCut    = cutColor    ?: colors.background

    Box(
        modifier = modifier
            .drawBehind {
                drawRect(fillColor, Offset.Zero, size)
            }
            .drawWithContent {
                drawContent()
                val p = px.toPx()
                val w = size.width
                val h = size.height

                if (cornerStyle == PixelCornerStyle.Rounded8) {
                    // ── R=8 pixel-circle corners — same 1-block-per-row approach as Rounded ──
                    // Raw staircase from pixelcorners.lukeb.co.uk (radius=8, multiplier=1 ÷ 4):
                    //   row 0 (y=0..1p): cut = 5p  → border at x=5p
                    //   row 1 (y=1..2p): cut = 3p  → border at x=3p
                    //   row 2 (y=2..3p): cut = 2p  → border at x=2p
                    //   row 3 (y=3..5p): cut = 1p  → border at x=1p  (2 rows tall)
                    //   row 5+:          no cut     → straight left/right edge

                    // Straight border edges
                    drawRect(resolvedBorder, Offset(p*5,   0f),    Size(w - p*10, p))   // top
                    drawRect(resolvedBorder, Offset(p*5,   h - p), Size(w - p*10, p))   // bottom
                    drawRect(resolvedBorder, Offset(0f,    p*5),   Size(p, h - p*10))   // left
                    drawRect(resolvedBorder, Offset(w - p, p*5),   Size(p, h - p*10))   // right

                    // Corner border steps — top-left
                    drawRect(resolvedBorder, Offset(p*5, p*0), Size(p,   p  ))  // row0 vertical
                    drawRect(resolvedBorder, Offset(p*3, p*1), Size(p*2, p  ))  // row0→1 horizontal + row1 vertical
                    drawRect(resolvedBorder, Offset(p*2, p*2), Size(p,   p  ))  // row1→2 horizontal + row2 vertical
                    drawRect(resolvedBorder, Offset(p*1, p*3), Size(p,   p  ))  // row2→3 horizontal + row3 vertical
                    drawRect(resolvedBorder, Offset(p*1, p*4), Size(p,   p  ))  // row4 vertical (cut unchanged)
                    drawRect(resolvedBorder, Offset(p*0, p*5), Size(p,   p  ))  // row4→5 horizontal — connects to left edge
                    // Corner border steps — top-right
                    drawRect(resolvedBorder, Offset(w-p*6, p*0), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p*5, p*1), Size(p*2, p))
                    drawRect(resolvedBorder, Offset(w-p*3, p*2), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p*2, p*3), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p*2, p*4), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p*1, p*5), Size(p,   p))
                    // Corner border steps — bottom-left
                    drawRect(resolvedBorder, Offset(p*5, h-p*1), Size(p,   p))
                    drawRect(resolvedBorder, Offset(p*3, h-p*2), Size(p*2, p))
                    drawRect(resolvedBorder, Offset(p*2, h-p*3), Size(p,   p))
                    drawRect(resolvedBorder, Offset(p*1, h-p*4), Size(p,   p))
                    drawRect(resolvedBorder, Offset(p*1, h-p*5), Size(p,   p))
                    drawRect(resolvedBorder, Offset(p*0, h-p*6), Size(p,   p))
                    // Corner border steps — bottom-right
                    drawRect(resolvedBorder, Offset(w-p*6, h-p*1), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p*5, h-p*2), Size(p*2, p))
                    drawRect(resolvedBorder, Offset(w-p*3, h-p*3), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p*2, h-p*4), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p*2, h-p*5), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p*1, h-p*6), Size(p,   p))

                    // Corner cuts — erase fill outside the curve
                    // Top-left
                    drawRect(resolvedCut, Offset(0f,  p*0), Size(p*5, p))
                    drawRect(resolvedCut, Offset(0f,  p*1), Size(p*3, p))
                    drawRect(resolvedCut, Offset(0f,  p*2), Size(p*2, p))
                    drawRect(resolvedCut, Offset(0f,  p*3), Size(p*1, p))
                    drawRect(resolvedCut, Offset(0f,  p*4), Size(p*1, p))
                    // Top-right
                    drawRect(resolvedCut, Offset(w-p*5, p*0), Size(p*5, p))
                    drawRect(resolvedCut, Offset(w-p*3, p*1), Size(p*3, p))
                    drawRect(resolvedCut, Offset(w-p*2, p*2), Size(p*2, p))
                    drawRect(resolvedCut, Offset(w-p*1, p*3), Size(p*1, p))
                    drawRect(resolvedCut, Offset(w-p*1, p*4), Size(p*1, p))
                    // Bottom-left
                    drawRect(resolvedCut, Offset(0f,  h-p*1), Size(p*5, p))
                    drawRect(resolvedCut, Offset(0f,  h-p*2), Size(p*3, p))
                    drawRect(resolvedCut, Offset(0f,  h-p*3), Size(p*2, p))
                    drawRect(resolvedCut, Offset(0f,  h-p*4), Size(p*1, p))
                    drawRect(resolvedCut, Offset(0f,  h-p*5), Size(p*1, p))
                    // Bottom-right
                    drawRect(resolvedCut, Offset(w-p*5, h-p*1), Size(p*5, p))
                    drawRect(resolvedCut, Offset(w-p*3, h-p*2), Size(p*3, p))
                    drawRect(resolvedCut, Offset(w-p*2, h-p*3), Size(p*2, p))
                    drawRect(resolvedCut, Offset(w-p*1, h-p*4), Size(p*1, p))
                    drawRect(resolvedCut, Offset(w-p*1, h-p*5), Size(p*1, p))

                } else if (cornerStyle == PixelCornerStyle.Circle) {
                    // Full pixel circle — Bresenham r=15, center=(16,16), 32x32 block grid.
                    // Cut table covers ALL rows — no straight section, true circle shape.
                    // Verified by printing the actual pixel grid.

                    // Corner border steps — top-left
                    drawRect(resolvedBorder, Offset(p*13, p* 1), Size(p*3, p))
                    drawRect(resolvedBorder, Offset(p*10, p* 2), Size(p*3, p))
                    drawRect(resolvedBorder, Offset(p* 8, p* 3), Size(p*2, p))
                    drawRect(resolvedBorder, Offset(p* 7, p* 4), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 6, p* 5), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 5, p* 6), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 4, p* 7), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 3, p* 8), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 3, p* 9), Size(p,   p))
                    drawRect(resolvedBorder, Offset(p* 2, p*10), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 2, p*11), Size(p,   p))
                    drawRect(resolvedBorder, Offset(p* 2, p*12), Size(p,   p))
                    drawRect(resolvedBorder, Offset(p* 1, p*13), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 1, p*14), Size(p,   p))
                    drawRect(resolvedBorder, Offset(p* 1, p*15), Size(p,   p))
                    drawRect(resolvedBorder, Offset(p* 1, p*16), Size(p,   p))
                    // Corner border steps — top-right
                    drawRect(resolvedBorder, Offset(w-p*16, p* 1), Size(p*3, p))
                    drawRect(resolvedBorder, Offset(w-p*13, p* 2), Size(p*3, p))
                    drawRect(resolvedBorder, Offset(w-p*10, p* 3), Size(p*2, p))
                    drawRect(resolvedBorder, Offset(w-p* 8, p* 4), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 7, p* 5), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 6, p* 6), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 5, p* 7), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 4, p* 8), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 4, p* 9), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p* 3, p*10), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 3, p*11), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p* 3, p*12), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p* 2, p*13), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 2, p*14), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p* 2, p*15), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p* 2, p*16), Size(p,   p))
                    // Corner border steps — bottom-left
                    drawRect(resolvedBorder, Offset(p*13, h-p* 2), Size(p*3, p))
                    drawRect(resolvedBorder, Offset(p*10, h-p* 3), Size(p*3, p))
                    drawRect(resolvedBorder, Offset(p* 8, h-p* 4), Size(p*2, p))
                    drawRect(resolvedBorder, Offset(p* 7, h-p* 5), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 6, h-p* 6), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 5, h-p* 7), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 4, h-p* 8), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 3, h-p* 9), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 3, h-p*10), Size(p,   p))
                    drawRect(resolvedBorder, Offset(p* 2, h-p*11), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 2, h-p*12), Size(p,   p))
                    drawRect(resolvedBorder, Offset(p* 2, h-p*13), Size(p,   p))
                    drawRect(resolvedBorder, Offset(p* 1, h-p*14), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 1, h-p*15), Size(p,   p))
                    drawRect(resolvedBorder, Offset(p* 1, h-p*16), Size(p,   p))
                    drawRect(resolvedBorder, Offset(p* 1, h-p*17), Size(p,   p))
                    // Corner border steps — bottom-right
                    drawRect(resolvedBorder, Offset(w-p*16, h-p* 2), Size(p*3, p))
                    drawRect(resolvedBorder, Offset(w-p*13, h-p* 3), Size(p*3, p))
                    drawRect(resolvedBorder, Offset(w-p*10, h-p* 4), Size(p*2, p))
                    drawRect(resolvedBorder, Offset(w-p* 8, h-p* 5), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 7, h-p* 6), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 6, h-p* 7), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 5, h-p* 8), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 4, h-p* 9), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 4, h-p*10), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p* 3, h-p*11), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 3, h-p*12), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p* 3, h-p*13), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p* 2, h-p*14), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 2, h-p*15), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p* 2, h-p*16), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p* 2, h-p*17), Size(p,   p))

                    // Corner cuts — top-left
                    drawRect(resolvedCut, Offset(0f, p* 0), Size(p*16, p))
                    drawRect(resolvedCut, Offset(0f, p* 1), Size(p*13, p))
                    drawRect(resolvedCut, Offset(0f, p* 2), Size(p*10, p))
                    drawRect(resolvedCut, Offset(0f, p* 3), Size(p* 8, p))
                    drawRect(resolvedCut, Offset(0f, p* 4), Size(p* 7, p))
                    drawRect(resolvedCut, Offset(0f, p* 5), Size(p* 6, p))
                    drawRect(resolvedCut, Offset(0f, p* 6), Size(p* 5, p))
                    drawRect(resolvedCut, Offset(0f, p* 7), Size(p* 4, p))
                    drawRect(resolvedCut, Offset(0f, p* 8), Size(p* 3, p))
                    drawRect(resolvedCut, Offset(0f, p* 9), Size(p* 3, p))
                    drawRect(resolvedCut, Offset(0f, p*10), Size(p* 2, p))
                    drawRect(resolvedCut, Offset(0f, p*11), Size(p* 2, p))
                    drawRect(resolvedCut, Offset(0f, p*12), Size(p* 2, p))
                    drawRect(resolvedCut, Offset(0f, p*13), Size(p* 1, p))
                    drawRect(resolvedCut, Offset(0f, p*14), Size(p* 1, p))
                    drawRect(resolvedCut, Offset(0f, p*15), Size(p* 1, p))
                    drawRect(resolvedCut, Offset(0f, p*16), Size(p* 1, p))
                    // Corner cuts — top-right
                    drawRect(resolvedCut, Offset(w-p*16, p* 0), Size(p*16, p))
                    drawRect(resolvedCut, Offset(w-p*13, p* 1), Size(p*13, p))
                    drawRect(resolvedCut, Offset(w-p*10, p* 2), Size(p*10, p))
                    drawRect(resolvedCut, Offset(w-p* 8, p* 3), Size(p* 8, p))
                    drawRect(resolvedCut, Offset(w-p* 7, p* 4), Size(p* 7, p))
                    drawRect(resolvedCut, Offset(w-p* 6, p* 5), Size(p* 6, p))
                    drawRect(resolvedCut, Offset(w-p* 5, p* 6), Size(p* 5, p))
                    drawRect(resolvedCut, Offset(w-p* 4, p* 7), Size(p* 4, p))
                    drawRect(resolvedCut, Offset(w-p* 3, p* 8), Size(p* 3, p))
                    drawRect(resolvedCut, Offset(w-p* 3, p* 9), Size(p* 3, p))
                    drawRect(resolvedCut, Offset(w-p* 2, p*10), Size(p* 2, p))
                    drawRect(resolvedCut, Offset(w-p* 2, p*11), Size(p* 2, p))
                    drawRect(resolvedCut, Offset(w-p* 2, p*12), Size(p* 2, p))
                    drawRect(resolvedCut, Offset(w-p* 1, p*13), Size(p* 1, p))
                    drawRect(resolvedCut, Offset(w-p* 1, p*14), Size(p* 1, p))
                    drawRect(resolvedCut, Offset(w-p* 1, p*15), Size(p* 1, p))
                    drawRect(resolvedCut, Offset(w-p* 1, p*16), Size(p* 1, p))
                    // Corner cuts — bottom-left
                    drawRect(resolvedCut, Offset(0f, h-p* 1), Size(p*16, p))
                    drawRect(resolvedCut, Offset(0f, h-p* 2), Size(p*13, p))
                    drawRect(resolvedCut, Offset(0f, h-p* 3), Size(p*10, p))
                    drawRect(resolvedCut, Offset(0f, h-p* 4), Size(p* 8, p))
                    drawRect(resolvedCut, Offset(0f, h-p* 5), Size(p* 7, p))
                    drawRect(resolvedCut, Offset(0f, h-p* 6), Size(p* 6, p))
                    drawRect(resolvedCut, Offset(0f, h-p* 7), Size(p* 5, p))
                    drawRect(resolvedCut, Offset(0f, h-p* 8), Size(p* 4, p))
                    drawRect(resolvedCut, Offset(0f, h-p* 9), Size(p* 3, p))
                    drawRect(resolvedCut, Offset(0f, h-p*10), Size(p* 3, p))
                    drawRect(resolvedCut, Offset(0f, h-p*11), Size(p* 2, p))
                    drawRect(resolvedCut, Offset(0f, h-p*12), Size(p* 2, p))
                    drawRect(resolvedCut, Offset(0f, h-p*13), Size(p* 2, p))
                    drawRect(resolvedCut, Offset(0f, h-p*14), Size(p* 1, p))
                    drawRect(resolvedCut, Offset(0f, h-p*15), Size(p* 1, p))
                    drawRect(resolvedCut, Offset(0f, h-p*16), Size(p* 1, p))
                    drawRect(resolvedCut, Offset(0f, h-p*17), Size(p* 1, p))
                    // Corner cuts — bottom-right
                    drawRect(resolvedCut, Offset(w-p*16, h-p* 1), Size(p*16, p))
                    drawRect(resolvedCut, Offset(w-p*13, h-p* 2), Size(p*13, p))
                    drawRect(resolvedCut, Offset(w-p*10, h-p* 3), Size(p*10, p))
                    drawRect(resolvedCut, Offset(w-p* 8, h-p* 4), Size(p* 8, p))
                    drawRect(resolvedCut, Offset(w-p* 7, h-p* 5), Size(p* 7, p))
                    drawRect(resolvedCut, Offset(w-p* 6, h-p* 6), Size(p* 6, p))
                    drawRect(resolvedCut, Offset(w-p* 5, h-p* 7), Size(p* 5, p))
                    drawRect(resolvedCut, Offset(w-p* 4, h-p* 8), Size(p* 4, p))
                    drawRect(resolvedCut, Offset(w-p* 3, h-p* 9), Size(p* 3, p))
                    drawRect(resolvedCut, Offset(w-p* 3, h-p*10), Size(p* 3, p))
                    drawRect(resolvedCut, Offset(w-p* 2, h-p*11), Size(p* 2, p))
                    drawRect(resolvedCut, Offset(w-p* 2, h-p*12), Size(p* 2, p))
                    drawRect(resolvedCut, Offset(w-p* 2, h-p*13), Size(p* 2, p))
                    drawRect(resolvedCut, Offset(w-p* 1, h-p*14), Size(p* 1, p))
                    drawRect(resolvedCut, Offset(w-p* 1, h-p*15), Size(p* 1, p))
                    drawRect(resolvedCut, Offset(w-p* 1, h-p*16), Size(p* 1, p))
                    drawRect(resolvedCut, Offset(w-p* 1, h-p*17), Size(p* 1, p))

                } else if (cornerStyle == PixelCornerStyle.Cut) {
                    // ── Uniform 3-step staircase corners ──────────────────────────
                    // Border straight edges
                    drawRect(resolvedBorder, Offset(p * 3, 0f),       Size(w - p * 6, p))
                    drawRect(resolvedBorder, Offset(p * 3, h - p),    Size(w - p * 6, p))
                    drawRect(resolvedBorder, Offset(0f,    p * 3),    Size(p, h - p * 6))
                    drawRect(resolvedBorder, Offset(w - p, p * 3),    Size(p, h - p * 6))
                    // Border corner steps
                    drawRect(resolvedBorder, Offset(p * 2,     p),         Size(p, p)) // TL
                    drawRect(resolvedBorder, Offset(p,         p * 2),     Size(p, p))
                    drawRect(resolvedBorder, Offset(w - p * 3, p),         Size(p, p)) // TR
                    drawRect(resolvedBorder, Offset(w - p * 2, p * 2),     Size(p, p))
                    drawRect(resolvedBorder, Offset(p * 2,     h - p * 2), Size(p, p)) // BL
                    drawRect(resolvedBorder, Offset(p,         h - p * 3), Size(p, p))
                    drawRect(resolvedBorder, Offset(w - p * 3, h - p * 2), Size(p, p)) // BR
                    drawRect(resolvedBorder, Offset(w - p * 2, h - p * 3), Size(p, p))
                    // Corner cuts
                    drawRect(resolvedCut, Offset(0f,       0f),       Size(p * 3, p))
                    drawRect(resolvedCut, Offset(0f,       p),        Size(p * 2, p))
                    drawRect(resolvedCut, Offset(0f,       p * 2),    Size(p,     p))
                    drawRect(resolvedCut, Offset(w - p*3,  0f),       Size(p * 3, p))
                    drawRect(resolvedCut, Offset(w - p*2,  p),        Size(p * 2, p))
                    drawRect(resolvedCut, Offset(w - p,    p * 2),    Size(p,     p))
                    drawRect(resolvedCut, Offset(0f,       h - p),    Size(p * 3, p))
                    drawRect(resolvedCut, Offset(0f,       h - p*2),  Size(p * 2, p))
                    drawRect(resolvedCut, Offset(0f,       h - p*3),  Size(p,     p))
                    drawRect(resolvedCut, Offset(w - p*3,  h - p),    Size(p * 3, p))
                    drawRect(resolvedCut, Offset(w - p*2,  h - p*2),  Size(p * 2, p))
                    drawRect(resolvedCut, Offset(w - p,    h - p*3),  Size(p,     p))

                } else {
                    // ── Rounded corners — pixel-circle curve, R=10 blocks ──────────
                    // Cut widths per row (computed from circle equation, 1 block = px):
                    //   row 0: cut=7  border at x=7
                    //   row 1: cut=5  border at x=5
                    //   row 2: cut=3  border at x=3
                    //   row 3: cut=2  border at x=2
                    //   row 4: cut=2  border at x=2
                    //   row 5: cut=1  border at x=1
                    //   row 6: cut=1  border at x=1
                    //   row 7+: no cut → straight left/right edge

                    // Straight border edges
                    drawRect(resolvedBorder, Offset(p*7,   0f),    Size(w - p*14, p))   // top
                    drawRect(resolvedBorder, Offset(p*7,   h - p), Size(w - p*14, p))   // bottom
                    drawRect(resolvedBorder, Offset(0f,    p*7),   Size(p, h - p*14))   // left
                    drawRect(resolvedBorder, Offset(w - p, p*7),   Size(p, h - p*14))   // right

                    // Corner border steps.
                    // Each step = vertical block (right edge of cut) + horizontal span (top of next ledge).
                    // cuts: [7,5,3,2,2,1,1,0] for rows 0-7
                    //
                    // Top-left
                    drawRect(resolvedBorder, Offset(p*7, p*0), Size(p,   p  ))  // row0 vertical
                    drawRect(resolvedBorder, Offset(p*5, p*1), Size(p*2, p  ))  // row0→1 horizontal + row1 vertical
                    drawRect(resolvedBorder, Offset(p*3, p*2), Size(p*2, p  ))  // row1→2 horizontal + row2 vertical
                    drawRect(resolvedBorder, Offset(p*2, p*3), Size(p,   p  ))  // row2→3 horizontal + row3 vertical
                    drawRect(resolvedBorder, Offset(p*2, p*4), Size(p,   p  ))  // row4 vertical (cut unchanged)
                    drawRect(resolvedBorder, Offset(p*1, p*5), Size(p,   p  ))  // row4→5 horizontal + row5 vertical
                    drawRect(resolvedBorder, Offset(p*1, p*6), Size(p,   p  ))  // row6 vertical (cut unchanged)
                    drawRect(resolvedBorder, Offset(p*0, p*7), Size(p,   p  ))  // row6→7 horizontal — connects to left edge
                    // Top-right
                    drawRect(resolvedBorder, Offset(w-p*8, p*0), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p*7, p*1), Size(p*2, p))
                    drawRect(resolvedBorder, Offset(w-p*5, p*2), Size(p*2, p))
                    drawRect(resolvedBorder, Offset(w-p*3, p*3), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p*3, p*4), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p*2, p*5), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p*2, p*6), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p*1, p*7), Size(p,   p))
                    // Bottom-left
                    drawRect(resolvedBorder, Offset(p*7, h-p*1), Size(p,   p))
                    drawRect(resolvedBorder, Offset(p*5, h-p*2), Size(p*2, p))
                    drawRect(resolvedBorder, Offset(p*3, h-p*3), Size(p*2, p))
                    drawRect(resolvedBorder, Offset(p*2, h-p*4), Size(p,   p))
                    drawRect(resolvedBorder, Offset(p*2, h-p*5), Size(p,   p))
                    drawRect(resolvedBorder, Offset(p*1, h-p*6), Size(p,   p))
                    drawRect(resolvedBorder, Offset(p*1, h-p*7), Size(p,   p))
                    drawRect(resolvedBorder, Offset(p*0, h-p*8), Size(p,   p))
                    // Bottom-right
                    drawRect(resolvedBorder, Offset(w-p*8, h-p*1), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p*7, h-p*2), Size(p*2, p))
                    drawRect(resolvedBorder, Offset(w-p*5, h-p*3), Size(p*2, p))
                    drawRect(resolvedBorder, Offset(w-p*3, h-p*4), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p*3, h-p*5), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p*2, h-p*6), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p*2, h-p*7), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p*1, h-p*8), Size(p,   p))

                    // Corner cuts — erase fill outside the curve
                    // Top-left
                    drawRect(resolvedCut, Offset(0f,  p*0), Size(p*7, p))
                    drawRect(resolvedCut, Offset(0f,  p*1), Size(p*5, p))
                    drawRect(resolvedCut, Offset(0f,  p*2), Size(p*3, p))
                    drawRect(resolvedCut, Offset(0f,  p*3), Size(p*2, p))
                    drawRect(resolvedCut, Offset(0f,  p*4), Size(p*2, p))
                    drawRect(resolvedCut, Offset(0f,  p*5), Size(p*1, p))
                    drawRect(resolvedCut, Offset(0f,  p*6), Size(p*1, p))
                    // Top-right
                    drawRect(resolvedCut, Offset(w - p*7, p*0), Size(p*7, p))
                    drawRect(resolvedCut, Offset(w - p*5, p*1), Size(p*5, p))
                    drawRect(resolvedCut, Offset(w - p*3, p*2), Size(p*3, p))
                    drawRect(resolvedCut, Offset(w - p*2, p*3), Size(p*2, p))
                    drawRect(resolvedCut, Offset(w - p*2, p*4), Size(p*2, p))
                    drawRect(resolvedCut, Offset(w - p*1, p*5), Size(p*1, p))
                    drawRect(resolvedCut, Offset(w - p*1, p*6), Size(p*1, p))
                    // Bottom-left
                    drawRect(resolvedCut, Offset(0f,  h - p*1), Size(p*7, p))
                    drawRect(resolvedCut, Offset(0f,  h - p*2), Size(p*5, p))
                    drawRect(resolvedCut, Offset(0f,  h - p*3), Size(p*3, p))
                    drawRect(resolvedCut, Offset(0f,  h - p*4), Size(p*2, p))
                    drawRect(resolvedCut, Offset(0f,  h - p*5), Size(p*2, p))
                    drawRect(resolvedCut, Offset(0f,  h - p*6), Size(p*1, p))
                    drawRect(resolvedCut, Offset(0f,  h - p*7), Size(p*1, p))
                    // Bottom-right
                    drawRect(resolvedCut, Offset(w - p*7, h - p*1), Size(p*7, p))
                    drawRect(resolvedCut, Offset(w - p*5, h - p*2), Size(p*5, p))
                    drawRect(resolvedCut, Offset(w - p*3, h - p*3), Size(p*3, p))
                    drawRect(resolvedCut, Offset(w - p*2, h - p*4), Size(p*2, p))
                    drawRect(resolvedCut, Offset(w - p*2, h - p*5), Size(p*2, p))
                    drawRect(resolvedCut, Offset(w - p*1, h - p*6), Size(p*1, p))
                    drawRect(resolvedCut, Offset(w - p*1, h - p*7), Size(p*1, p))
                }

                // ── Speech bubble tail ────────────────────────────────────────────
                // An 8-block-tall pixel staircase triangle centered on the bubble.
                // Each row narrows by 1 block on each side going downward:
                //   row 0 (base): 15p wide
                //   row 1:        13p wide
                //   row 2:        11p wide
                //   row 3:         9p wide
                //   row 4:         7p wide
                //   row 5:         5p wide
                //   row 6:         3p wide
                //   row 7 (tip):   1p wide
                // Centered horizontally: tailX = center - 7.5p (half of 15p base)
                if (speechTail) {
                    val p = px.toPx()
                    val tailX = size.width / 2f - p * 7.5f
                    val tailY = size.height - p - 1f        // overlap bubble bottom by 1 raw pixel to kill the seam

                    // Fill drawn AFTER the border pass so it paints over the bubble's
                    // bottom border line — eliminating the seam at the connection point
                    drawRect(fillColor, Offset(tailX,       tailY + p * 0), Size(p * 15, p))
                    drawRect(fillColor, Offset(tailX + p,   tailY + p * 1), Size(p * 13, p))
                    drawRect(fillColor, Offset(tailX + p*2, tailY + p * 2), Size(p * 11, p))
                    drawRect(fillColor, Offset(tailX + p*3, tailY + p * 3), Size(p *  9, p))
                    drawRect(fillColor, Offset(tailX + p*4, tailY + p * 4), Size(p *  7, p))
                    drawRect(fillColor, Offset(tailX + p*5, tailY + p * 5), Size(p *  5, p))
                    drawRect(fillColor, Offset(tailX + p*6, tailY + p * 6), Size(p *  3, p))

                    // Border lines drawn on top of fill
                    // Left border
                    drawRect(resolvedBorder, Offset(tailX,       tailY + p * 0), Size(p, p))
                    drawRect(resolvedBorder, Offset(tailX + p,   tailY + p * 1), Size(p, p))
                    drawRect(resolvedBorder, Offset(tailX + p*2, tailY + p * 2), Size(p, p))
                    drawRect(resolvedBorder, Offset(tailX + p*3, tailY + p * 3), Size(p, p))
                    drawRect(resolvedBorder, Offset(tailX + p*4, tailY + p * 4), Size(p, p))
                    drawRect(resolvedBorder, Offset(tailX + p*5, tailY + p * 5), Size(p, p))
                    drawRect(resolvedBorder, Offset(tailX + p*6, tailY + p * 6), Size(p, p))
                    // Right border
                    drawRect(resolvedBorder, Offset(tailX + p*14, tailY + p * 0), Size(p, p))
                    drawRect(resolvedBorder, Offset(tailX + p*13, tailY + p * 1), Size(p, p))
                    drawRect(resolvedBorder, Offset(tailX + p*12, tailY + p * 2), Size(p, p))
                    drawRect(resolvedBorder, Offset(tailX + p*11, tailY + p * 3), Size(p, p))
                    drawRect(resolvedBorder, Offset(tailX + p*10, tailY + p * 4), Size(p, p))
                    drawRect(resolvedBorder, Offset(tailX + p* 9, tailY + p * 5), Size(p, p))
                    drawRect(resolvedBorder, Offset(tailX + p* 8, tailY + p * 6), Size(p, p))
                    // Center tip block
                    drawRect(resolvedBorder, Offset(tailX + p*7,  tailY + p * 6), Size(p, p))
                }
            }
            .padding(px * 2),
        content = content
    )
}

// ── 40dp pixel-circle icon button ────────────────────────────────
@Composable
fun PixelCircleIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fillColor: Color = LocalWizairdColors.current.secondarySurface,
    cutColor: Color = LocalWizairdColors.current.secondarySurface,
    iconTint: Color = LocalWizairdColors.current.secondaryIcon
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .drawPixelCircle(
                fillColor   = fillColor,
                borderColor = Color.Transparent,
                cutColor    = cutColor
            )
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(iconTint),
            modifier = Modifier.size(20.dp)
        )
    }
}

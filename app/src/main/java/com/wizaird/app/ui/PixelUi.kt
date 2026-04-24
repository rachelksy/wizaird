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

    // Cut corners — 3-step staircase
    drawRect(cutColor, Offset(0f,         0f),         Size(p * 3, p))
    drawRect(cutColor, Offset(0f,         p),          Size(p * 2, p))
    drawRect(cutColor, Offset(0f,         p * 2),      Size(p,     p))
    drawRect(cutColor, Offset(w - p * 3,  0f),         Size(p * 3, p))
    drawRect(cutColor, Offset(w - p * 2,  p),          Size(p * 2, p))
    drawRect(cutColor, Offset(w - p,      p * 2),      Size(p,     p))
    drawRect(cutColor, Offset(0f,         h - p),      Size(p * 3, p))
    drawRect(cutColor, Offset(0f,         h - p * 2),  Size(p * 2, p))
    drawRect(cutColor, Offset(0f,         h - p * 3),  Size(p,     p))
    drawRect(cutColor, Offset(w - p * 3,  h - p),      Size(p * 3, p))
    drawRect(cutColor, Offset(w - p * 2,  h - p * 2),  Size(p * 2, p))
    drawRect(cutColor, Offset(w - p,      h - p * 3),  Size(p,     p))

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

enum class PixelCornerStyle { Cut, Rounded }

// Pixel-art box with corner style:
//   Cut     — uniform 3-step staircase (diagonal look, used for input bar / buttons)
//   Rounded — non-uniform curve derived from a circle (5,3,2,1 block cuts), used for chat bubble
// Fill drawn behind content; border + corners drawn ON TOP of content
// so they always show regardless of what color children have.
@Composable
fun PixelBox(
    modifier: Modifier = Modifier,
    fillColor: Color,
    borderColor: Color? = null,   // null = use theme ink
    cutColor: Color? = null,      // null = use theme background
    px: Dp = PixelSize,
    cornerStyle: PixelCornerStyle = PixelCornerStyle.Cut,
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

                if (cornerStyle == PixelCornerStyle.Cut) {
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
            }
            .padding(px * 2),
        content = content
    )
}

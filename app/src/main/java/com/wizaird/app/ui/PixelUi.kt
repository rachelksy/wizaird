package com.wizaird.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wizaird.app.ui.theme.Coral
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

// Press overlay color — drawn over the pixel shape on tap
private val PressOverlay = Color.Black.copy(alpha = 0.15f)

// Draws the press overlay row-by-row using the given cut table,
// painted ON TOP of content so child draws don't cover it.
private fun androidx.compose.ui.graphics.drawscope.ContentDrawScope.drawPressOverlay(
    cuts: FloatArray,
    fullCircle: Boolean = false
) {
    drawContent()
    val p = PixelSize.toPx()
    val w = size.width
    val h = size.height
    val c = PressOverlay
    val n = cuts.size

    if (fullCircle) {
        // Full circle: top staircase rows, straight middle band, bottom staircase rows.
        // Top half: rows 0..n-1 drawn from top downward
        for (i in 0 until n) {
            val cut = cuts[i] * p
            val rowTop = i * p
            val rowBot = minOf((i + 1) * p, h / 2f)
            if (rowBot > rowTop) drawRect(c, Offset(cut, rowTop), Size(w - cut * 2, rowBot - rowTop))
        }
        // Middle band: if the staircase rows don't reach h/2, fill the gap with no cut (full width)
        val staircaseHeight = n * p
        val midTop = staircaseHeight
        val midBot = h - staircaseHeight
        if (midBot > midTop) drawRect(c, Offset(0f, midTop), Size(w, midBot - midTop))
        // Bottom half: rows 0..n-1 drawn from bottom upward (mirror)
        for (i in 0 until n) {
            val cut = cuts[i] * p
            val rowBot = h - i * p
            val rowTop = maxOf(h - (i + 1) * p, h / 2f)
            if (rowBot > rowTop) drawRect(c, Offset(cut, rowTop), Size(w - cut * 2, rowBot - rowTop))
        }
    } else {
        // Has straight edges: top staircase, straight middle, bottom staircase — no overlap.
        for (i in 0 until n) {
            val cut = cuts[i] * p
            drawRect(c, Offset(cut, i * p), Size(w - cut * 2, p))
        }
        val straightTop = n * p
        val straightBot = h - n * p
        if (straightBot > straightTop) drawRect(c, Offset(0f, straightTop), Size(w, straightBot - straightTop))
        for (i in 0 until n) {
            val cut = cuts[i] * p
            drawRect(c, Offset(cut, h - (i + 1) * p), Size(w - cut * 2, p))
        }
    }
}

// For drawPixelCircle back buttons (40dp) — cuts: 7,5,3,2,2,1,1
@Composable
fun Modifier.pixelCircleClickable(
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val cuts = floatArrayOf(7f, 5f, 3f, 2f, 2f, 1f, 1f)
    return this
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
        .drawWithContent { if (pressed) drawPressOverlay(cuts) else drawContent() }
}

// For small icons that should stay at their natural layout size but show a larger
// press overlay matching the send button shape (pixelRounded8, 32dp).
// The icon stays exactly where layout puts it; the overlay expands outward from centre.
@Composable
fun Modifier.pixelRounded8ClickableOversize(
    interactionSource: MutableInteractionSource,
    overlayDp: Dp = 32.dp,
    onClick: () -> Unit
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val cuts = floatArrayOf(5f, 3f, 2f, 1f, 1f)
    return this
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
        .drawWithContent {
            drawContent()
            if (pressed) {
                val p = PixelSize.toPx()
                val overlayPx = overlayDp.toPx()
                // Centre the overlay on the icon
                val ox = (size.width  - overlayPx) / 2f
                val oy = (size.height - overlayPx) / 2f
                val c = PressOverlay
                val w = overlayPx
                val h = overlayPx
                // R=8 staircase: cuts 5,3,2,1,1
                for (i in cuts.indices) {
                    val cut = cuts[i] * p
                    drawRect(c, Offset(ox + cut, oy + i * p), Size(w - cut * 2, p))
                }
                val straightTop = cuts.size * p
                val straightBot = h - cuts.size * p
                if (straightBot > straightTop) {
                    drawRect(c, Offset(ox, oy + straightTop), Size(w, straightBot - straightTop))
                }
                for (i in cuts.indices) {
                    val cut = cuts[i] * p
                    drawRect(c, Offset(ox + cut, oy + h - (i + 1) * p), Size(w - cut * 2, p))
                }
            }
        }
}

// For PixelCornerStyle.Circle (64dp AgentScrollBar) — cuts: 16,13,10,8,7,6,5,4,3,3,2,2,2,1,1,1,1
@Composable
fun Modifier.pixelLargeCircleClickable(
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val cuts = floatArrayOf(16f,13f,10f,8f,7f,6f,5f,4f,3f,3f,2f,2f,2f,1f,1f,1f,1f)
    return this
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
        .drawWithContent { if (pressed) drawPressOverlay(cuts, fullCircle = true) else drawContent() }
}

// For PixelCornerStyle.XLargeCircle (80dp)
// cuts: 16,13,11,9,8,7,6,5,4,3,3,2,2,1,1,1 — matches PixelBox draw and PixelXLargeCircleShape exactly
@Composable
fun Modifier.pixelXLargeCircleClickable(
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val cuts = floatArrayOf(16f,13f,11f,9f,8f,7f,6f,5f,4f,3f,3f,2f,2f,1f,1f,1f)
    return this
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
        .drawWithContent { if (pressed) drawPressOverlay(cuts, fullCircle = true) else drawContent() }
}

// For PixelCornerStyle.Rounded8 / drawPixelArrowButton — cuts: 5,3,2,1,1
@Composable
fun Modifier.pixelRounded8Clickable(
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val cuts = floatArrayOf(5f, 3f, 2f, 1f, 1f)
    return this
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
        .drawWithContent { if (pressed) drawPressOverlay(cuts) else drawContent() }
}

// For PixelCornerStyle.Rounded (R=10) — cuts: 7,5,3,2,2,1,1
@Composable
fun Modifier.pixelRoundedClickable(
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val cuts = floatArrayOf(7f, 5f, 3f, 2f, 2f, 1f, 1f)
    return this
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
        .drawWithContent { if (pressed) drawPressOverlay(cuts) else drawContent() }
}

// ── Pixel clip shapes — exact same coordinates as the draw functions ──────────
// clip(PixelCircleButtonShape) or clip(PixelRounded8Shape) before clickable
// ensures the ripple is bounded by the real pixel outline, not a rectangle.

// Matches drawPixelCircle exactly — derived directly from its cut rects.
// Left-edge x per row from top: 7p,5p,3p,2p,2p,1p,1p,0 (then straight to bottom mirror)
object PixelCircleButtonShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = with(density) { PixelSize.toPx() }
        val w = size.width
        val h = size.height
        val path = Path().apply {
            // Start top-left, go clockwise
            // Top-left corner steps (left edge x at each row: 7,5,3,2,2,1,1,0)
            moveTo(p*7, 0f)
            lineTo(w-p*7, 0f)   // top edge
            // Top-right corner (mirror of top-left)
            lineTo(w-p*5, 0f);  lineTo(w-p*5, p*1)
            lineTo(w-p*3, p*1); lineTo(w-p*3, p*2)
            lineTo(w-p*2, p*2); lineTo(w-p*2, p*4)
            lineTo(w-p*1, p*4); lineTo(w-p*1, p*6)
            lineTo(w-p*0, p*6); lineTo(w-p*0, p*7)
            // Right edge
            lineTo(w, h-p*7)
            // Bottom-right corner
            lineTo(w-p*0, h-p*6); lineTo(w-p*1, h-p*6)
            lineTo(w-p*1, h-p*4); lineTo(w-p*2, h-p*4)
            lineTo(w-p*2, h-p*2); lineTo(w-p*3, h-p*2)
            lineTo(w-p*3, h-p*1); lineTo(w-p*5, h-p*1)
            lineTo(w-p*5, h-p*0); lineTo(w-p*7, h)
            // Bottom edge
            lineTo(p*7, h)
            // Bottom-left corner
            lineTo(p*5, h); lineTo(p*5, h-p*1)
            lineTo(p*3, h-p*1); lineTo(p*3, h-p*2)
            lineTo(p*2, h-p*2); lineTo(p*2, h-p*4)
            lineTo(p*1, h-p*4); lineTo(p*1, h-p*6)
            lineTo(0f,  h-p*6); lineTo(0f,  h-p*7)
            // Left edge
            lineTo(0f, p*7)
            // Top-left corner
            lineTo(0f,  p*6);  lineTo(p*1, p*6)
            lineTo(p*1, p*4);  lineTo(p*2, p*4)
            lineTo(p*2, p*2);  lineTo(p*3, p*2)
            lineTo(p*3, p*1);  lineTo(p*5, p*1)
            lineTo(p*5, 0f);   lineTo(p*7, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

// Matches drawPixelArrowButton / PixelCornerStyle.Rounded8 exactly.
// Left-edge x per row from top: 5p,3p,2p,1p,1p,0 (then straight)
object PixelRounded8Shape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = with(density) { PixelSize.toPx() }
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(p*5, 0f)
            lineTo(w-p*5, 0f)   // top edge
            // Top-right
            lineTo(w-p*3, 0f);  lineTo(w-p*3, p*1)
            lineTo(w-p*2, p*1); lineTo(w-p*2, p*2)
            lineTo(w-p*1, p*2); lineTo(w-p*1, p*4)
            lineTo(w-p*0, p*4); lineTo(w,     p*5)
            // Right edge
            lineTo(w, h-p*5)
            // Bottom-right
            lineTo(w,     h-p*4); lineTo(w-p*1, h-p*4)
            lineTo(w-p*1, h-p*2); lineTo(w-p*2, h-p*2)
            lineTo(w-p*2, h-p*1); lineTo(w-p*3, h-p*1)
            lineTo(w-p*3, h);     lineTo(w-p*5, h)
            // Bottom edge
            lineTo(p*5, h)
            // Bottom-left
            lineTo(p*3, h);    lineTo(p*3, h-p*1)
            lineTo(p*2, h-p*1); lineTo(p*2, h-p*2)
            lineTo(p*1, h-p*2); lineTo(p*1, h-p*4)
            lineTo(0f,  h-p*4); lineTo(0f,  h-p*5)
            // Left edge
            lineTo(0f, p*5)
            // Top-left
            lineTo(0f,  p*4);  lineTo(p*1, p*4)
            lineTo(p*1, p*2);  lineTo(p*2, p*2)
            lineTo(p*2, p*1);  lineTo(p*3, p*1)
            lineTo(p*3, 0f);   lineTo(p*5, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}


// Matches PixelCornerStyle.Rounded (R=10) — cut table: [7,5,3,2,2,1,1] then straight.
// Use clip(PixelRoundedShape) when the PixelBox is drawn over a non-background surface
// (e.g. inside a Dialog scrim) so the corner cuts are physically clipped rather than
// painted with cutColor.
object PixelRoundedShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = with(density) { PixelSize.toPx() }
        val w = size.width
        val h = size.height
        // cuts[i] = how many p-units are cut from each side at row i from top/bottom
        val cuts = intArrayOf(7, 5, 3, 2, 2, 1, 1)
        val n = cuts.size
        val path = Path().apply {
            // Top edge
            moveTo(p * cuts[0], 0f)
            lineTo(w - p * cuts[0], 0f)
            // Top-right staircase
            for (i in 0 until n - 1) {
                lineTo(w - p * cuts[i],     p * i)
                lineTo(w - p * cuts[i + 1], p * i)
                lineTo(w - p * cuts[i + 1], p * (i + 1))
            }
            lineTo(w - p * cuts[n - 1], p * (n - 1))
            // Right straight edge
            lineTo(w, p * n)
            lineTo(w, h - p * n)
            // Bottom-right staircase
            lineTo(w - p * cuts[n - 1], h - p * (n - 1))
            for (i in n - 1 downTo 1) {
                lineTo(w - p * cuts[i],     h - p * i)
                lineTo(w - p * cuts[i - 1], h - p * i)
                lineTo(w - p * cuts[i - 1], h - p * (i - 1))
            }
            // Bottom edge
            lineTo(w - p * cuts[0], h)
            lineTo(p * cuts[0], h)
            // Bottom-left staircase
            for (i in 0 until n - 1) {
                lineTo(p * cuts[i],     h - p * i)
                lineTo(p * cuts[i + 1], h - p * i)
                lineTo(p * cuts[i + 1], h - p * (i + 1))
            }
            lineTo(p * cuts[n - 1], h - p * (n - 1))
            // Left straight edge
            lineTo(0f, h - p * n)
            lineTo(0f, p * n)
            // Top-left staircase
            lineTo(p * cuts[n - 1], p * (n - 1))
            for (i in n - 1 downTo 1) {
                lineTo(p * cuts[i],     p * i)
                lineTo(p * cuts[i - 1], p * i)
                lineTo(p * cuts[i - 1], p * (i - 1))
            }
            close()
        }
        return Outline.Generic(path)
    }
}

// Matches PixelCornerStyle.Circle (64dp) — cut table: 16,13,10,8,7,6,5,4,3,3,2,2,2,1,1,1,1
object PixelLargeCircleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = with(density) { PixelSize.toPx() }
        val w = size.width
        val h = size.height
        val cuts = intArrayOf(16,13,10,8,7,6,5,4,3,3,2,2,2,1,1,1,1)
        val n = cuts.size
        val path = Path().apply {
            moveTo(p * cuts[0], 0f)
            // Top edge
            lineTo(w - p * cuts[0], 0f)
            // Top-right staircase (downward)
            for (i in 0 until n - 1) {
                lineTo(w - p * cuts[i],     p * i)
                lineTo(w - p * cuts[i + 1], p * i)
                lineTo(w - p * cuts[i + 1], p * (i + 1))
            }
            lineTo(w - p * cuts[n - 1], p * (n - 1))
            // Right edge
            lineTo(w, p * n)
            lineTo(w, h - p * n)
            // Bottom-right staircase (downward from bottom)
            lineTo(w - p * cuts[n - 1], h - p * (n - 1))
            for (i in n - 1 downTo 1) {
                lineTo(w - p * cuts[i],     h - p * i)
                lineTo(w - p * cuts[i - 1], h - p * i)
                lineTo(w - p * cuts[i - 1], h - p * (i - 1))
            }
            // Bottom edge
            lineTo(w - p * cuts[0], h)
            lineTo(p * cuts[0], h)
            // Bottom-left staircase
            for (i in 0 until n - 1) {
                lineTo(p * cuts[i],     h - p * i)
                lineTo(p * cuts[i + 1], h - p * i)
                lineTo(p * cuts[i + 1], h - p * (i + 1))
            }
            lineTo(p * cuts[n - 1], h - p * (n - 1))
            // Left edge
            lineTo(0f, h - p * n)
            lineTo(0f, p * n)
            // Top-left staircase
            lineTo(p * cuts[n - 1], p * (n - 1))
            for (i in n - 1 downTo 1) {
                lineTo(p * cuts[i],     p * i)
                lineTo(p * cuts[i - 1], p * i)
                lineTo(p * cuts[i - 1], p * (i - 1))
            }
            close()
        }
        return Outline.Generic(path)
    }
}

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

enum class PixelCornerStyle { Cut, Rounded, Rounded8, Circle, XLargeCircle }

// Clip shape for PixelCornerStyle.XLargeCircle (80dp FAB).
// Cut table derived DIRECTLY from the drawRect(resolvedCut, ...) calls in the
// XLargeCircle branch of PixelBox — so the clip boundary is pixel-perfect with
// what is actually painted:
//   row  0: cut=16   row  1: cut=13   row  2: cut=11   row  3: cut=9
//   row  4: cut=8    row  5: cut=7    row  6: cut=6    row  7: cut=5
//   row  8: cut=4    row  9: cut=3    row 10: cut=3    row 11: cut=2
//   row 12: cut=2    row 13: cut=1    row 14: cut=1    row 15: cut=1
// 16 staircase rows top and bottom; straight edges in between.
object PixelXLargeCircleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = with(density) { PixelSize.toPx() }
        val w = size.width
        val h = size.height
        // cuts[i] = how many p-units are cut from each side at row i from the top/bottom
        val cuts = intArrayOf(16, 13, 11, 9, 8, 7, 6, 5, 4, 3, 3, 2, 2, 1, 1, 1)
        val n = cuts.size  // 16 staircase rows
        val path = Path().apply {
            // Top edge — starts at the first cut inset
            moveTo(p * cuts[0], 0f)
            lineTo(w - p * cuts[0], 0f)
            // Top-right staircase: step inward row by row going down
            for (i in 0 until n - 1) {
                lineTo(w - p * cuts[i],     p * i)
                lineTo(w - p * cuts[i + 1], p * i)
                lineTo(w - p * cuts[i + 1], p * (i + 1))
            }
            lineTo(w - p * cuts[n - 1], p * (n - 1))
            // Right straight edge
            lineTo(w, p * n)
            lineTo(w, h - p * n)
            // Bottom-right staircase: step outward row by row going up from bottom
            lineTo(w - p * cuts[n - 1], h - p * (n - 1))
            for (i in n - 1 downTo 1) {
                lineTo(w - p * cuts[i],     h - p * i)
                lineTo(w - p * cuts[i - 1], h - p * i)
                lineTo(w - p * cuts[i - 1], h - p * (i - 1))
            }
            // Bottom edge
            lineTo(w - p * cuts[0], h)
            lineTo(p * cuts[0], h)
            // Bottom-left staircase
            for (i in 0 until n - 1) {
                lineTo(p * cuts[i],     h - p * i)
                lineTo(p * cuts[i + 1], h - p * i)
                lineTo(p * cuts[i + 1], h - p * (i + 1))
            }
            lineTo(p * cuts[n - 1], h - p * (n - 1))
            // Left straight edge
            lineTo(0f, h - p * n)
            lineTo(0f, p * n)
            // Top-left staircase
            lineTo(p * cuts[n - 1], p * (n - 1))
            for (i in n - 1 downTo 1) {
                lineTo(p * cuts[i],     p * i)
                lineTo(p * cuts[i - 1], p * i)
                lineTo(p * cuts[i - 1], p * (i - 1))
            }
            close()
        }
        return Outline.Generic(path)
    }
}

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

                } else if (cornerStyle == PixelCornerStyle.XLargeCircle) {
                    // ── 80dp pixel circle — Bresenham midpoint r=20, 8-way symmetric
                    // Cut table: [16,13,11,9,8,7,6,5,4,3,3,2,2,1,1,1,0,0,0,0,0]
                    // 45-degree symmetry: steps top→45° = [3,2,2,1,1,1] mirror gaps 45°→side

                    // Corner border steps — top-left
                    drawRect(resolvedBorder, Offset(p*13, p* 1), Size(p*3, p))
                    drawRect(resolvedBorder, Offset(p*11, p* 2), Size(p*2, p))
                    drawRect(resolvedBorder, Offset(p* 9, p* 3), Size(p*2, p))
                    drawRect(resolvedBorder, Offset(p* 8, p* 4), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 7, p* 5), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 6, p* 6), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 5, p* 7), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 4, p* 8), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 3, p* 9), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 3, p*10), Size(p,   p))
                    drawRect(resolvedBorder, Offset(p* 2, p*11), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 2, p*12), Size(p,   p))
                    drawRect(resolvedBorder, Offset(p* 1, p*13), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 1, p*14), Size(p,   p))
                    drawRect(resolvedBorder, Offset(p* 1, p*15), Size(p,   p))
                    drawRect(resolvedBorder, Offset(p* 0, p*16), Size(p*1, p))
                    // Corner border steps — top-right
                    drawRect(resolvedBorder, Offset(w-p*16, p* 1), Size(p*3, p))
                    drawRect(resolvedBorder, Offset(w-p*13, p* 2), Size(p*2, p))
                    drawRect(resolvedBorder, Offset(w-p*11, p* 3), Size(p*2, p))
                    drawRect(resolvedBorder, Offset(w-p* 9, p* 4), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 8, p* 5), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 7, p* 6), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 6, p* 7), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 5, p* 8), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 4, p* 9), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 4, p*10), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p* 3, p*11), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 3, p*12), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p* 2, p*13), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 2, p*14), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p* 2, p*15), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p* 1, p*16), Size(p*1, p))
                    // Corner border steps — bottom-left
                    drawRect(resolvedBorder, Offset(p*13, h-p* 2), Size(p*3, p))
                    drawRect(resolvedBorder, Offset(p*11, h-p* 3), Size(p*2, p))
                    drawRect(resolvedBorder, Offset(p* 9, h-p* 4), Size(p*2, p))
                    drawRect(resolvedBorder, Offset(p* 8, h-p* 5), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 7, h-p* 6), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 6, h-p* 7), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 5, h-p* 8), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 4, h-p* 9), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 3, h-p*10), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 3, h-p*11), Size(p,   p))
                    drawRect(resolvedBorder, Offset(p* 2, h-p*12), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 2, h-p*13), Size(p,   p))
                    drawRect(resolvedBorder, Offset(p* 1, h-p*14), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(p* 1, h-p*15), Size(p,   p))
                    drawRect(resolvedBorder, Offset(p* 1, h-p*16), Size(p,   p))
                    drawRect(resolvedBorder, Offset(p* 0, h-p*17), Size(p*1, p))
                    // Corner border steps — bottom-right
                    drawRect(resolvedBorder, Offset(w-p*16, h-p* 2), Size(p*3, p))
                    drawRect(resolvedBorder, Offset(w-p*13, h-p* 3), Size(p*2, p))
                    drawRect(resolvedBorder, Offset(w-p*11, h-p* 4), Size(p*2, p))
                    drawRect(resolvedBorder, Offset(w-p* 9, h-p* 5), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 8, h-p* 6), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 7, h-p* 7), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 6, h-p* 8), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 5, h-p* 9), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 4, h-p*10), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 4, h-p*11), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p* 3, h-p*12), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 3, h-p*13), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p* 2, h-p*14), Size(p*1, p))
                    drawRect(resolvedBorder, Offset(w-p* 2, h-p*15), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p* 2, h-p*16), Size(p,   p))
                    drawRect(resolvedBorder, Offset(w-p* 1, h-p*17), Size(p*1, p))

                    // Corner cuts — top-left
                    drawRect(resolvedCut, Offset(0f, p* 0), Size(p*16, p))
                    drawRect(resolvedCut, Offset(0f, p* 1), Size(p*13, p))
                    drawRect(resolvedCut, Offset(0f, p* 2), Size(p*11, p))
                    drawRect(resolvedCut, Offset(0f, p* 3), Size(p* 9, p))
                    drawRect(resolvedCut, Offset(0f, p* 4), Size(p* 8, p))
                    drawRect(resolvedCut, Offset(0f, p* 5), Size(p* 7, p))
                    drawRect(resolvedCut, Offset(0f, p* 6), Size(p* 6, p))
                    drawRect(resolvedCut, Offset(0f, p* 7), Size(p* 5, p))
                    drawRect(resolvedCut, Offset(0f, p* 8), Size(p* 4, p))
                    drawRect(resolvedCut, Offset(0f, p* 9), Size(p* 3, p))
                    drawRect(resolvedCut, Offset(0f, p*10), Size(p* 3, p))
                    drawRect(resolvedCut, Offset(0f, p*11), Size(p* 2, p))
                    drawRect(resolvedCut, Offset(0f, p*12), Size(p* 2, p))
                    drawRect(resolvedCut, Offset(0f, p*13), Size(p* 1, p))
                    drawRect(resolvedCut, Offset(0f, p*14), Size(p* 1, p))
                    drawRect(resolvedCut, Offset(0f, p*15), Size(p* 1, p))
                    // Corner cuts — top-right
                    drawRect(resolvedCut, Offset(w-p*16, p* 0), Size(p*16, p))
                    drawRect(resolvedCut, Offset(w-p*13, p* 1), Size(p*13, p))
                    drawRect(resolvedCut, Offset(w-p*11, p* 2), Size(p*11, p))
                    drawRect(resolvedCut, Offset(w-p* 9, p* 3), Size(p* 9, p))
                    drawRect(resolvedCut, Offset(w-p* 8, p* 4), Size(p* 8, p))
                    drawRect(resolvedCut, Offset(w-p* 7, p* 5), Size(p* 7, p))
                    drawRect(resolvedCut, Offset(w-p* 6, p* 6), Size(p* 6, p))
                    drawRect(resolvedCut, Offset(w-p* 5, p* 7), Size(p* 5, p))
                    drawRect(resolvedCut, Offset(w-p* 4, p* 8), Size(p* 4, p))
                    drawRect(resolvedCut, Offset(w-p* 3, p* 9), Size(p* 3, p))
                    drawRect(resolvedCut, Offset(w-p* 3, p*10), Size(p* 3, p))
                    drawRect(resolvedCut, Offset(w-p* 2, p*11), Size(p* 2, p))
                    drawRect(resolvedCut, Offset(w-p* 2, p*12), Size(p* 2, p))
                    drawRect(resolvedCut, Offset(w-p* 1, p*13), Size(p* 1, p))
                    drawRect(resolvedCut, Offset(w-p* 1, p*14), Size(p* 1, p))
                    drawRect(resolvedCut, Offset(w-p* 1, p*15), Size(p* 1, p))
                    // Corner cuts — bottom-left
                    drawRect(resolvedCut, Offset(0f, h-p* 1), Size(p*16, p))
                    drawRect(resolvedCut, Offset(0f, h-p* 2), Size(p*13, p))
                    drawRect(resolvedCut, Offset(0f, h-p* 3), Size(p*11, p))
                    drawRect(resolvedCut, Offset(0f, h-p* 4), Size(p* 9, p))
                    drawRect(resolvedCut, Offset(0f, h-p* 5), Size(p* 8, p))
                    drawRect(resolvedCut, Offset(0f, h-p* 6), Size(p* 7, p))
                    drawRect(resolvedCut, Offset(0f, h-p* 7), Size(p* 6, p))
                    drawRect(resolvedCut, Offset(0f, h-p* 8), Size(p* 5, p))
                    drawRect(resolvedCut, Offset(0f, h-p* 9), Size(p* 4, p))
                    drawRect(resolvedCut, Offset(0f, h-p*10), Size(p* 3, p))
                    drawRect(resolvedCut, Offset(0f, h-p*11), Size(p* 3, p))
                    drawRect(resolvedCut, Offset(0f, h-p*12), Size(p* 2, p))
                    drawRect(resolvedCut, Offset(0f, h-p*13), Size(p* 2, p))
                    drawRect(resolvedCut, Offset(0f, h-p*14), Size(p* 1, p))
                    drawRect(resolvedCut, Offset(0f, h-p*15), Size(p* 1, p))
                    drawRect(resolvedCut, Offset(0f, h-p*16), Size(p* 1, p))
                    // Corner cuts — bottom-right
                    drawRect(resolvedCut, Offset(w-p*16, h-p* 1), Size(p*16, p))
                    drawRect(resolvedCut, Offset(w-p*13, h-p* 2), Size(p*13, p))
                    drawRect(resolvedCut, Offset(w-p*11, h-p* 3), Size(p*11, p))
                    drawRect(resolvedCut, Offset(w-p* 9, h-p* 4), Size(p* 9, p))
                    drawRect(resolvedCut, Offset(w-p* 8, h-p* 5), Size(p* 8, p))
                    drawRect(resolvedCut, Offset(w-p* 7, h-p* 6), Size(p* 7, p))
                    drawRect(resolvedCut, Offset(w-p* 6, h-p* 7), Size(p* 6, p))
                    drawRect(resolvedCut, Offset(w-p* 5, h-p* 8), Size(p* 5, p))
                    drawRect(resolvedCut, Offset(w-p* 4, h-p* 9), Size(p* 4, p))
                    drawRect(resolvedCut, Offset(w-p* 3, h-p*10), Size(p* 3, p))
                    drawRect(resolvedCut, Offset(w-p* 3, h-p*11), Size(p* 3, p))
                    drawRect(resolvedCut, Offset(w-p* 2, h-p*12), Size(p* 2, p))
                    drawRect(resolvedCut, Offset(w-p* 2, h-p*13), Size(p* 2, p))
                    drawRect(resolvedCut, Offset(w-p* 1, h-p*14), Size(p* 1, p))
                    drawRect(resolvedCut, Offset(w-p* 1, h-p*15), Size(p* 1, p))
                    drawRect(resolvedCut, Offset(w-p* 1, h-p*16), Size(p* 1, p))

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

// ── Reusable pixel rounded-rectangle buttons ─────────────────────
// All three sizes use PixelCornerStyle.Rounded8 and pixelRounded8Clickable
// so the press overlay is clipped to the pixel shape, not a rectangle.
//
// Variants:
//   primary   — filled with Coral, white text
//   secondary — filled with secondaryButton, secondaryIcon text
//
// Sizes:
//   Small  — compact, used for inline actions like "+ NEW"
//   Medium — used for selector chips (AI provider, dark mode)
//   Large  — used for full-width action buttons (CANCEL / SAVE)

@Composable
fun PixelButtonSmall(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    cutColor: Color? = null
) {
    val colors = LocalWizairdColors.current
    val fillColor  = if (primary) Coral else colors.secondaryButton
    val textColor  = if (primary) colors.secondaryIcon else colors.secondaryIcon
    val interactionSource = remember { MutableInteractionSource() }
    PixelBox(
        modifier = modifier
            .pixelRounded8Clickable(interactionSource = interactionSource, onClick = onClick),
        fillColor = fillColor,
        borderColor = fillColor,
        cutColor = cutColor,
        cornerStyle = PixelCornerStyle.Rounded8
    ) {
        Text(
            label,
            style = pixelStyle(10, textColor),
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .offset(y = (-2).dp)
        )
    }
}

@Composable
fun PixelButtonMedium(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true
) {
    val colors = LocalWizairdColors.current
    val fillColor  = if (primary) Coral else colors.secondaryButton
    val textColor  = if (primary) colors.secondaryIcon else colors.secondaryIcon
    val interactionSource = remember { MutableInteractionSource() }
    PixelBox(
        modifier = modifier
            .pixelRounded8Clickable(interactionSource = interactionSource, onClick = onClick),
        fillColor = fillColor,
        borderColor = fillColor,
        cornerStyle = PixelCornerStyle.Rounded8
    ) {
        Text(
            label,
            style = pixelStyle(12, textColor),
            modifier = Modifier
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .offset(y = (-2).dp)
        )
    }
}

@Composable
fun PixelButtonLarge(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true
) {
    val colors = LocalWizairdColors.current
    val fillColor  = if (primary) Coral else colors.secondaryButton
    val textColor  = if (primary) colors.secondaryIcon else colors.secondaryIcon
    val interactionSource = remember { MutableInteractionSource() }
    PixelBox(
        modifier = modifier
            .pixelRounded8Clickable(interactionSource = interactionSource, onClick = onClick),
        fillColor = fillColor,
        borderColor = fillColor,
        cornerStyle = PixelCornerStyle.Rounded8
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                style = pixelStyle(12, textColor),
                modifier = Modifier.offset(y = (-2).dp)
            )
        }
    }
}

// ── 40dp pixel-circle icon button ────────────────────────────────
@Composable
fun PixelCircleIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fillColor: Color = LocalWizairdColors.current.secondarySurface,
    borderColor: Color = Color.Transparent,
    iconTint: Color = LocalWizairdColors.current.secondaryIcon
) {
    val interactionSource = remember { MutableInteractionSource() }
    PixelBox(
        modifier = modifier
            .size(40.dp)
            .pixelCircleClickable(interactionSource = interactionSource, onClick = onClick),
        fillColor = fillColor,
        borderColor = borderColor,
        cornerStyle = PixelCornerStyle.Rounded
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
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
}

package com.wizaird.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.wizaird.app.ui.theme.Ink
import com.wizaird.app.ui.theme.PixelFont

fun pixelStyle(size: Int, color: Color = Ink) = TextStyle(
    fontFamily = PixelFont,
    fontSize = size.sp,
    color = color,
    letterSpacing = 0.5.sp
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

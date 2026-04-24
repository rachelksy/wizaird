package com.wizaird.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.wizaird.app.R

// ── Raw palette ──────────────────────────────────────────────────
val Paper     = Color(0xFFF2E6C7)
val PaperDk   = Color(0xFFD9C59A)
val Ink       = Color(0xFF2A1F14)
val InkSoft   = Color(0xFF5B4326)
val Forest    = Color(0xFF2F5D3A)
val ForestDk  = Color(0xFF1A3A23)
val Coral     = Color(0xFFE0563A)
val CoralDk   = Color(0xFFA6361E)
val Gold      = Color(0xFFE5B14A)
val Bubble    = Color(0xFFFFFAED)
val NightBg   = Color(0xFF1B1A2E)

// Dark-mode raw values
val NightSurface  = Color(0xFF111111)   // main background
val NightSurfaceDk= Color(0xFF0A0A0A)   // darker variant (stat bar empty cells, etc.)
val NightInk      = Color(0xFFE8DFC8)   // primary text
val NightInkSoft  = Color(0xFF9A8F7A)   // secondary text
val NightBubble   = Color(0xFF1A1A1A)   // chat bubble / input background
val NightForest   = Color(0xFF3A7A4A)   // accent green (slightly brighter for dark bg)
val NightBorder   = Color(0xFF666666)   // line borders only

val PixelFont = FontFamily(Font(R.font.vt323))

// ── Theme tokens ─────────────────────────────────────────────────
@Immutable
data class WizairdColors(
    val background: Color,
    val backgroundDark: Color,
    val ink: Color,
    val inkSoft: Color,
    val bubble: Color,
    val forest: Color,
    val forestDk: Color,
    val coral: Color,
    val gold: Color,
    val border: Color,
    val isDark: Boolean
)

val LightColors = WizairdColors(
    background  = Paper,
    backgroundDark = PaperDk,
    ink         = Ink,
    inkSoft     = InkSoft,
    bubble      = Bubble,
    forest      = Forest,
    forestDk    = ForestDk,
    coral       = Coral,
    gold        = Gold,
    border      = Ink,
    isDark      = false
)

val DarkColors = WizairdColors(
    background  = NightSurface,
    backgroundDark = NightSurfaceDk,
    ink         = NightInk,
    inkSoft     = NightInkSoft,
    bubble      = NightBubble,
    forest      = NightForest,
    forestDk    = ForestDk,
    coral       = Coral,
    gold        = Gold,
    border      = NightBorder,
    isDark      = true
)

val LocalWizairdColors = staticCompositionLocalOf { LightColors }

@Composable
fun WizairdTheme(
    darkMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (darkMode) DarkColors else LightColors
    CompositionLocalProvider(LocalWizairdColors provides colors) {
        content()
    }
}

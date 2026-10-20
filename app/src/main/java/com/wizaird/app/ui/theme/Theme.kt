package com.wizaird.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.wizaird.app.R

val PixelFont      = FontFamily(Font(R.font.vt323))
val MinecraftFont  = FontFamily(Font(R.font.macs_minecraft))
val MinecraftBoldFont = FontFamily(Font(R.font.macs_minecraft_bold))
val PixeloidFont   = FontFamily(Font(R.font.pixeloid_sans))

// ── Theme tokens ─────────────────────────────────────────────────
@Immutable
data class WizairdColors(
    val background: Color,        // main screen background
    val backgroundDark: Color,    // darker variant (empty cells, etc.)
    val secondarySurface: Color,  // bubble, input, header, inactive button surfaces
    val secondaryButton: Color,   // muted/inactive button fill
    val tertiarySurface: Color,   // note cards and settings buttons
    val secondaryIcon: Color,     // primary icon and text color
    val secondaryIconSoft: Color, // muted/secondary text color
    val textHigh: Color,          // high-emphasis text
    val textLow: Color,           // low-emphasis text
    val textXLow: Color,          // extra-low-emphasis text
    val userBubble: Color,        // user chat bubble fill — lighter than secondaryButton
    val forest: Color,
    val forestDk: Color,
    val coral: Color,
    val onAccent: Color,          // text and icons drawn on a coral fill (dark in both themes: light text fails contrast)
    val gold: Color,
    val border: Color,
    val warn: Color,              // warning/destructive action color
    val isDark: Boolean
)

val LightColors = WizairdColors(
    background        = Color(0xFFF2E6C7),
    backgroundDark    = Color(0xFFD9C59A),
    secondarySurface  = Color(0xFFFFFAED),
    secondaryButton   = Color(0xFFD9C59A),
    tertiarySurface   = Color(0xFFEBD9B4),
    secondaryIcon     = Color(0xFF2A1F14),
    secondaryIconSoft = Color(0xFF5B4326),
    textHigh          = Color(0xFF2A1F14),
    textLow           = Color(0xFF5B4326),
    textXLow          = Color(0xFF8A7055),
    userBubble        = Color(0xFFEBD9B4),
    forest            = Color(0xFF2F5D3A),
    forestDk          = Color(0xFF1A3A23),
    coral             = Color(0xFFE0563A),
    onAccent          = Color(0xFF1C140C),  // 4.8:1 on coral (the regular text brown is only 4.3:1)
    gold              = Color(0xFFE5B14A),
    border            = Color(0xFF2A1F14),
    warn              = Color(0xFF991B1B),  // error red (Tailwind red-800): 6.7:1 on the paper background, clearly apart from coral
    isDark            = false
)

val DarkColors = WizairdColors(
    background        = Color(0xFF111111),
    backgroundDark    = Color(0xFF0A0A0A),
    secondarySurface  = Color(0xFF222222),
    secondaryButton   = Color(0xFF555555),
    tertiarySurface   = Color(0xFF444444),
    secondaryIcon     = Color(0xFFE8DFC8),
    secondaryIconSoft = Color(0xFF9A8F7A),
    textHigh          = Color(0xFFE8DFC8),
    textLow           = Color(0xFFA9A088),
    textXLow          = Color(0xFF666666),
    userBubble        = Color(0xFF444444),
    forest            = Color(0xFF3A7A4A),
    forestDk          = Color(0xFF1A3A23),
    coral             = Color(0xFFE0563A),
    onAccent          = Color(0xFF111111),  // 5.0:1 on coral (the light text color is only 2.8:1)
    gold              = Color(0xFFE5B14A),
    border            = Color(0xFF666666),
    warn              = Color(0xFFF87171),  // error red for dark backgrounds (Tailwind red-400)
    isDark            = true
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

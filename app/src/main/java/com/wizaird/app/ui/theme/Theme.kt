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
val Paper            = Color(0xFFF2E6C7)
val PaperDk          = Color(0xFFD9C59A)
val SecondaryIcon    = Color(0xFF2A1F14)
val SecondaryIconSoft= Color(0xFF5B4326)
val Forest           = Color(0xFF2F5D3A)
val ForestDk         = Color(0xFF1A3A23)
val Coral            = Color(0xFFE0563A)
val CoralDk          = Color(0xFFA6361E)
val Gold             = Color(0xFFE5B14A)
val Bubble           = Color(0xFFFFFAED)

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
    val secondaryIcon: Color,     // primary icon and text color
    val secondaryIconSoft: Color, // muted/secondary text color
    val textHigh: Color,          // high-emphasis text
    val textLow: Color,           // low-emphasis text
    val textXLow: Color,          // extra-low-emphasis text
    val forest: Color,
    val forestDk: Color,
    val coral: Color,
    val gold: Color,
    val border: Color,
    val isDark: Boolean
)

val LightColors = WizairdColors(
    background        = Color(0xFFF2E6C7),
    backgroundDark    = Color(0xFFD9C59A),
    secondarySurface  = Color(0xFFFFFAED),
    secondaryButton   = Color(0xFFD9C59A),
    secondaryIcon     = Color(0xFF2A1F14),
    secondaryIconSoft = Color(0xFF5B4326),
    textHigh          = Color(0xFF111111),
    textLow           = Color(0xFF666666),
    textXLow          = Color(0xFF999999),
    forest            = Color(0xFF2F5D3A),
    forestDk          = Color(0xFF1A3A23),
    coral             = Color(0xFFE0563A),
    gold              = Color(0xFFE5B14A),
    border            = Color(0xFF2A1F14),
    isDark            = false
)

val DarkColors = WizairdColors(
    background        = Color(0xFF111111),
    backgroundDark    = Color(0xFF0A0A0A),
    secondarySurface  = Color(0xFF222222),
    secondaryButton   = Color(0xFF555555),
    secondaryIcon     = Color(0xFFE8DFC8),
    secondaryIconSoft = Color(0xFF9A8F7A),
    textHigh          = Color(0xFFE8DFC8),
    textLow           = Color(0xFF9A8F7A),
    textXLow          = Color(0xFF666666),
    forest            = Color(0xFF3A7A4A),
    forestDk          = Color(0xFF1A3A23),
    coral             = Color(0xFFE0563A),
    gold              = Color(0xFFE5B14A),
    border            = Color(0xFF666666),
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

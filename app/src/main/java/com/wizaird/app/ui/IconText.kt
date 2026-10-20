package com.wizaird.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * An icon followed by text, with the icon centred on the letters rather than on the text box.
 *
 * The pixel fonts leave more room above or below the letters than the other side, so centring on
 * the text box (Alignment.CenterVertically) puts icons a pixel or two off, by a different amount
 * per font and size. This measures the font's capital height and the text's baseline instead.
 *
 * [fontRes] is the font file [style] uses: R.font.vt323 for pixelStyle, R.font.pixeloid_sans for minecraftStyle.
 */
@Composable
fun IconText(
    iconAsset: String,
    text: String,
    style: TextStyle,
    fontRes: Int,
    iconSize: Dp,
    tint: Color,
    modifier: Modifier = Modifier,
    gap: Dp = 6.dp,
    maxLines: Int = 1,
    contentDescription: String? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    // Height of a capital letter in px for this font and size
    val capHeightPx = remember(fontRes, style.fontSize, density) {
        val paint = android.graphics.Paint().apply {
            typeface = ResourcesCompat.getFont(context, fontRes)
            textSize = with(density) { style.fontSize.toPx() }
        }
        val bounds = android.graphics.Rect()
        paint.getTextBounds("H", 0, 1, bounds)
        bounds.height().toFloat()
    }
    val gapPx = with(density) { gap.roundToPx() }

    Layout(
        modifier = modifier,
        content = {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("file:///android_asset/pixelarticons/$iconAsset")
                    .build(),
                imageLoader = rememberSvgLoader(),
                contentDescription = contentDescription,
                colorFilter = ColorFilter.tint(tint),
                modifier = Modifier
            )
            Text(text, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        }
    ) { measurables, constraints ->
        val icon = measurables[0].measure(androidx.compose.ui.unit.Constraints.fixed(iconSize.roundToPx(), iconSize.roundToPx()))
        val textMaxWidth = (constraints.maxWidth - icon.width - gapPx).coerceAtLeast(0)
        val textPlaceable = measurables[1].measure(constraints.copy(minWidth = 0, maxWidth = textMaxWidth, minHeight = 0))
        val baseline = textPlaceable[FirstBaseline].takeIf { it != androidx.compose.ui.layout.AlignmentLine.Unspecified }
            ?: textPlaceable.height
        // Icon centre on the middle of the capital letters
        val iconTop = (baseline - capHeightPx / 2f - icon.height / 2f).roundToInt()
        val top = min(0, iconTop)
        val height = max(textPlaceable.height, iconTop + icon.height) - top
        // At least the width it was given (e.g. weight(1f)), so the content stays left-aligned
        val width = (icon.width + gapPx + textPlaceable.width).coerceIn(constraints.minWidth, constraints.maxWidth)
        layout(width, height.coerceAtLeast(constraints.minHeight)) {
            icon.placeRelative(0, iconTop - top)
            textPlaceable.placeRelative(icon.width + gapPx, -top)
        }
    }
}

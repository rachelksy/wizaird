package com.wizaird.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.wizaird.app.data.AiSettings
import com.wizaird.app.data.saveSettings
import com.wizaird.app.data.settingsFlow
import com.wizaird.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun AppSettingsScreen(
    onBack: () -> Unit,
    initialDarkMode: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by settingsFlow(context).collectAsState(initial = AiSettings(darkMode = initialDarkMode))

    var darkMode by remember(saved) { mutableStateOf(saved.darkMode) }

    val svgLoader = remember {
        ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
    }

    // Preview the theme live as the selection changes
    WizairdTheme(darkMode = darkMode) {
        val colors = LocalWizairdColors.current
        val isCoverScreen = rememberIsCoverScreen()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Status bar space
                if (!isCoverScreen) {
                    Spacer(modifier = Modifier.height(48.dp))
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val backInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .drawPixelCircle(
                                fillColor   = colors.secondaryButton,
                                borderColor = Color.Transparent,
                                cutColor    = colors.background
                            )
                            .pixelCircleClickable(interactionSource = backInteraction) { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .drawPixelArrowButton(
                                    fillColor  = colors.secondaryButton,
                                    cutColor   = colors.secondaryButton,
                                    arrowColor = colors.secondaryIcon,
                                    direction  = -1f
                                )
                        )
                    }
                    Text(
                        "APP SETTINGS",
                        style = pixelStyle(14, colors.secondaryIcon),
                        modifier = Modifier
                            .weight(1f)
                            .offset(y = (-2).dp)
                    )
                    PixelButtonSmall(
                        label = "SAVE",
                        primary = true,
                        onClick = {
                            scope.launch {
                                saveSettings(
                                    context,
                                    saved.copy(darkMode = darkMode)
                                )
                                onBack()
                            }
                        }
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Mode section
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "MODE",
                            style = pixelStyle(12, colors.textXLow),
                            modifier = Modifier.offset(x = 4.dp, y = (-2).dp)
                        )

                        // Section container — tertiarySurface (light beige, same as settings buttons)
                        PixelBox(
                            modifier = Modifier.fillMaxWidth(),
                            fillColor = colors.tertiarySurface,
                            borderColor = Color.Transparent,
                            cornerStyle = PixelCornerStyle.Rounded8
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp)
                            ) {
                                // Light mode row
                                ModeOptionRow(
                                    label = "LIGHT MODE",
                                    iconPath = "file:///android_asset/pixelarticons/sun.svg",
                                    isSelected = !darkMode,
                                    svgLoader = svgLoader,
                                    onClick = { darkMode = false },
                                    colors = colors
                                )

                                // Divider with 4dp spacing above and below, 35% alpha textXLow
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(colors.textXLow.copy(alpha = 0.35f))
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                // Dark mode row
                                ModeOptionRow(
                                    label = "DARK MODE",
                                    iconPath = "file:///android_asset/pixelarticons/moon.svg",
                                    isSelected = darkMode,
                                    svgLoader = svgLoader,
                                    onClick = { darkMode = true },
                                    colors = colors
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeOptionRow(
    label: String,
    iconPath: String,
    isSelected: Boolean,
    svgLoader: ImageLoader,
    onClick: () -> Unit,
    colors: WizairdColors
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pixelRounded8Clickable(interactionSource = interactionSource, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Leading icon in textXLow
        AsyncImage(
            model = ImageRequest.Builder(context).data(iconPath).build(),
            imageLoader = svgLoader,
            contentDescription = label,
            colorFilter = ColorFilter.tint(colors.textXLow),
            modifier = Modifier.size(20.dp)
        )

        // Label — same color as screen header (secondaryIcon)
        Text(
            label,
            style = pixelStyle(12, colors.secondaryIcon),
            modifier = Modifier
                .weight(1f)
                .offset(y = (-2).dp)
        )

        // Pixel check if selected
        if (isSelected) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("file:///android_asset/pixelarticons/check.svg")
                    .build(),
                imageLoader = svgLoader,
                contentDescription = "Selected",
                colorFilter = ColorFilter.tint(colors.secondaryIcon),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

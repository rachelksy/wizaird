package com.wizaird.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

    // Preview the theme live as the toggle changes
    WizairdTheme(darkMode = darkMode) {
        val colors = LocalWizairdColors.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.height(48.dp))

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
                                borderColor = androidx.compose.ui.graphics.Color.Transparent,
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
                    // Dark mode toggle
                    SettingsField(label = "DARK MODE") {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(false to "LIGHT", true to "DARK").forEach { (value, label) ->
                                PixelButtonMedium(
                                    label = label,
                                    primary = darkMode == value,
                                    onClick = { darkMode = value }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

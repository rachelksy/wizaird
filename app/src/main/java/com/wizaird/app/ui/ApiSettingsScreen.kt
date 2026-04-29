package com.wizaird.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
fun ApiSettingsScreen(
    onBack: () -> Unit,
    initialDarkMode: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by settingsFlow(context).collectAsState(initial = AiSettings(darkMode = initialDarkMode))

    var provider    by remember(saved) { mutableStateOf(saved.provider) }
    var apiKey      by remember(saved) { mutableStateOf(saved.apiKey) }
    var model       by remember(saved) { mutableStateOf(saved.model) }
    var temperature by remember(saved) { mutableStateOf(saved.temperature) }

    val colors = LocalWizairdColors.current
    val providers = listOf("openai", "gemini", "claude", "custom")

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
                    "API SETTINGS",
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
                                saved.copy(
                                    provider = provider,
                                    apiKey = apiKey,
                                    model = model,
                                    temperature = temperature
                                )
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
                // Provider selector
                SettingsField(label = "AI PROVIDER") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        providers.forEach { p ->
                            PixelButtonMedium(
                                label = p.uppercase(),
                                primary = provider == p,
                                onClick = { provider = p }
                            )
                        }
                    }
                }

                SettingsField(label = "API KEY") {
                    PixelTextInput(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        placeholder = "sk-...",
                        isPassword = true,
                        cornerStyle = PixelCornerStyle.Rounded8
                    )
                }

                SettingsField(label = "MODEL") {
                    PixelTextInput(
                        value = model,
                        onValueChange = { model = it },
                        placeholder = "gpt-4o-mini",
                        cornerStyle = PixelCornerStyle.Rounded8
                    )
                }

                SettingsField(label = "TEMPERATURE  ${String.format("%.2f", temperature)}") {
                    val steps = 10
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(steps) { i ->
                            val filled = i < (temperature * steps).toInt()
                            val cellColor = if (filled) Coral else colors.secondaryButton
                            Box(
                                modifier = Modifier
                                    .size(24.dp, 16.dp)
                                    .background(cellColor)
                                    .clickable { temperature = (i + 1).toFloat() / steps }
                            )
                        }
                    }
                }
            }
        }
    }
}

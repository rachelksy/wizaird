package com.wizaird.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.wizaird.app.data.AiSettings
import com.wizaird.app.data.saveSettings
import com.wizaird.app.data.settingsFlow
import com.wizaird.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, initialDarkMode: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by settingsFlow(context).collectAsState(initial = AiSettings(darkMode = initialDarkMode))

    var provider    by remember(saved) { mutableStateOf(saved.provider) }
    var apiKey      by remember(saved) { mutableStateOf(saved.apiKey) }
    var model       by remember(saved) { mutableStateOf(saved.model) }
    var temperature by remember(saved) { mutableStateOf(saved.temperature) }
    var darkMode    by remember(saved) { mutableStateOf(saved.darkMode) }

    // Preview the theme live as the toggle changes
    WizairdTheme(darkMode = darkMode) {
        val colors = LocalWizairdColors.current
        val providers = listOf("openai", "gemini", "claude", "custom")

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.height(48.dp))

                // Header — same size as AppHeader so the screen transition doesn't jump
                /*
                PixelBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp),
                    fillColor = colors.secondarySurface,
                    cornerStyle = PixelCornerStyle.Rounded
                ) {
                */
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
                        Text("SETTINGS", style = pixelStyle(14, colors.secondaryIcon), modifier = Modifier.offset(y = (-2).dp))
                    }
                // } // end PixelBox

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

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PixelButtonLarge(
                            label = "CANCEL",
                            primary = false,
                            modifier = Modifier.weight(1f),
                            onClick = onBack
                        )
                        PixelButtonLarge(
                            label = "SAVE",
                            primary = true,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                scope.launch {
                                    saveSettings(context, AiSettings(provider, apiKey, model, temperature, darkMode))
                                    onBack()
                                }
                            }
                        )
                    }
                }
            }
        }
    } // end WizairdTheme
}

@Composable
fun SettingsField(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = LocalWizairdColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = pixelStyle(12, colors.secondaryIconSoft), modifier = Modifier.offset(y = (-2).dp))
        content()
    }
}

@Composable
fun PixelTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isPassword: Boolean = false,
    cornerStyle: PixelCornerStyle = PixelCornerStyle.Rounded
) {
    val colors = LocalWizairdColors.current
    PixelBox(
        modifier = Modifier.fillMaxWidth(),
        fillColor = colors.secondarySurface,
        cornerStyle = cornerStyle
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = pixelStyle(12, colors.secondaryIcon),
            cursorBrush = SolidColor(colors.secondaryIcon),
            singleLine = true,
            visualTransformation = if (isPassword)
                PasswordVisualTransformation()
            else
                VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .offset(y = (-2).dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = pixelStyle(12, colors.secondaryIconSoft), modifier = Modifier.offset(y = (-2).dp))
                    inner()
                }
            }
        )
    }
}




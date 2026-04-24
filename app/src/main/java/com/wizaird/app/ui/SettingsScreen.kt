package com.wizaird.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by settingsFlow(context).collectAsState(initial = AiSettings())

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
                PixelStatusBar()

                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.background)
                        .drawBehind { drawPixelBorder(bottom = true, color = colors.border) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(colors.forest)
                            .drawBehind { drawPixelBorder(color = colors.border) }
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("←", style = pixelStyle(12, Color.White))
                    }
                    Text("⚙ SETTINGS", style = pixelStyle(12, colors.ink))
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
                                val active = provider == p
                                Box(
                                    modifier = Modifier
                                        .background(if (active) colors.forest else colors.background)
                                        .drawBehind { drawPixelBorder(color = colors.border) }
                                        .clickable { provider = p }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        p.uppercase(),
                                        style = pixelStyle(12, if (active) Color.White else colors.ink)
                                    )
                                }
                            }
                        }
                    }

                    SettingsField(label = "API KEY") {
                        PixelTextInput(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            placeholder = "sk-...",
                            isPassword = true
                        )
                    }

                    SettingsField(label = "MODEL") {
                        PixelTextInput(
                            value = model,
                            onValueChange = { model = it },
                            placeholder = "gpt-4o-mini"
                        )
                    }

                    SettingsField(label = "TEMPERATURE  ${String.format("%.2f", temperature)}") {
                        val steps = 10
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(steps) { i ->
                                val filled = i < (temperature * steps).toInt()
                                Box(
                                    modifier = Modifier
                                        .size(24.dp, 16.dp)
                                        .background(if (filled) colors.forest else colors.backgroundDark)
                                        .drawBehind { drawPixelBorder(color = colors.border) }
                                        .clickable { temperature = (i + 1).toFloat() / steps }
                                )
                            }
                        }
                    }

                    // Dark mode toggle
                    SettingsField(label = "DARK MODE") {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(false to "LIGHT", true to "DARK").forEach { (value, label) ->
                                val active = darkMode == value
                                Box(
                                    modifier = Modifier
                                        .background(if (active) colors.forest else colors.background)
                                        .drawBehind { drawPixelBorder(color = colors.border) }
                                        .clickable { darkMode = value }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        label,
                                        style = pixelStyle(12, if (active) Color.White else colors.ink)
                                    )
                                }
                            }
                        }
                    }

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PixelActionButton(
                            label = "CANCEL",
                            color = colors.backgroundDark,
                            textColor = colors.ink,
                            modifier = Modifier.weight(1f),
                            onClick = onBack
                        )
                        PixelActionButton(
                            label = "SAVE",
                            color = colors.forest,
                            textColor = Color.White,
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
fun SettingsField(label: String, content: @Composable () -> Unit) {
    val colors = LocalWizairdColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = pixelStyle(12, colors.inkSoft))
        content()
    }
}

@Composable
fun PixelTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isPassword: Boolean = false
) {
    val colors = LocalWizairdColors.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = pixelStyle(12, colors.ink),
        cursorBrush = SolidColor(colors.ink),
        singleLine = true,
        visualTransformation = if (isPassword)
            PasswordVisualTransformation()
        else
            VisualTransformation.None,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bubble)
            .drawBehind { drawPixelBorder(color = colors.border) }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, style = pixelStyle(12, colors.inkSoft))
            inner()
        }
    )
}

@Composable
fun PixelActionButton(
    label: String,
    color: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = LocalWizairdColors.current
    Box(
        modifier = modifier
            .background(color)
            .drawBehind { drawPixelBorder(color = colors.border) }
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = pixelStyle(12, textColor))
    }
}

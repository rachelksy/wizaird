package com.wizaird.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.wizaird.app.ui.theme.*

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAccountSettings: () -> Unit,
    onAppSettings: () -> Unit,
    onApiSettings: () -> Unit
) {
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
                    "SETTINGS",
                    style = pixelStyle(14, colors.secondaryIcon),
                    modifier = Modifier
                        .weight(1f)
                        .offset(y = (-2).dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Account Settings Section
                SettingsSectionCard(
                    label = "ACCOUNT SETTINGS",
                    onClick = onAccountSettings
                )

                // App Settings Section
                SettingsSectionCard(
                    label = "APP SETTINGS",
                    onClick = onAppSettings
                )

                // API Settings Section
                SettingsSectionCard(
                    label = "API SETTINGS",
                    onClick = onApiSettings
                )
            }
        }
    }
}

@Composable
fun SettingsSectionCard(
    label: String,
    onClick: () -> Unit
) {
    val colors = LocalWizairdColors.current
    val interaction = remember { MutableInteractionSource() }

    PixelBox(
        modifier = Modifier
            .fillMaxWidth()
            .pixelRoundedClickable(interactionSource = interaction) { onClick() },
        fillColor = colors.secondarySurface,
        borderColor = androidx.compose.ui.graphics.Color.Transparent,
        cornerStyle = PixelCornerStyle.Rounded
    ) {
        Text(
            text = label,
            style = pixelStyle(12, colors.secondaryIcon),
            modifier = Modifier
                .padding(vertical = 16.dp, horizontal = 12.dp)
                .offset(y = (-2).dp)
        )
    }
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
    cornerStyle: PixelCornerStyle = PixelCornerStyle.Rounded,
    textStyle: androidx.compose.ui.text.TextStyle? = null
) {
    val colors = LocalWizairdColors.current
    val resolvedTextStyle = textStyle ?: pixelStyle(12, colors.secondaryIcon)
    val placeholderStyle = textStyle?.copy(color = colors.secondaryIconSoft) ?: pixelStyle(12, colors.secondaryIconSoft)
    
    PixelBox(
        modifier = Modifier.fillMaxWidth(),
        fillColor = colors.secondarySurface,
        cornerStyle = cornerStyle
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = resolvedTextStyle,
            cursorBrush = SolidColor(colors.secondaryIcon),
            singleLine = true,
            visualTransformation = if (isPassword)
                PasswordVisualTransformation()
            else
                VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = placeholderStyle)
                    inner()
                }
            }
        )
    }
}




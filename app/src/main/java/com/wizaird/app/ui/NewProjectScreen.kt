package com.wizaird.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.wizaird.app.ui.theme.*

@Composable
fun NewProjectScreen(onBack: () -> Unit) {
    val colors = LocalWizairdColors.current

    var projectName by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(48.dp))

            // Header — same style as SettingsScreen
            PixelBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp),
                fillColor = colors.secondarySurface,
                cornerStyle = PixelCornerStyle.Rounded8
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(start = 12.dp, end = 12.dp),
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
                                cutColor    = colors.secondarySurface
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
                        "NEW PROJECT",
                        style = pixelStyle(12, colors.secondaryIcon),
                        modifier = Modifier.offset(y = (-2).dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Project name
                SettingsField(label = "PROJECT NAME") {
                    PixelTextInput(
                        value = projectName,
                        onValueChange = { projectName = it },
                        placeholder = "MY PROJECT"
                    )
                }

                // System instructions sent to the AI
                SettingsField(label = "AI INSTRUCTIONS") {
                    PixelBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        fillColor = colors.secondarySurface,
                        cornerStyle = PixelCornerStyle.Rounded8
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = instructions,
                            onValueChange = { instructions = it },
                            textStyle = pixelStyle(12, colors.secondaryIcon),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.secondaryIcon),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .offset(y = (-2).dp),
                            decorationBox = { inner ->
                                androidx.compose.foundation.layout.Box(
                                    contentAlignment = Alignment.TopStart
                                ) {
                                    if (instructions.isEmpty()) {
                                        Text(
                                            "YOU ARE A HELPFUL WIZARD...",
                                            style = pixelStyle(12, colors.secondaryIconSoft),
                                            modifier = Modifier.offset(y = (-2).dp)
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                    }
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PixelActionButton(
                        label = "CANCEL",
                        color = colors.secondaryButton,
                        textColor = colors.secondaryIcon,
                        modifier = Modifier.weight(1f),
                        onClick = onBack
                    )
                    PixelActionButton(
                        label = "CREATE",
                        color = Coral,
                        textColor = SecondaryIcon,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            // TODO: persist project
                            onBack()
                        }
                    )
                }
            }
        }
    }
}

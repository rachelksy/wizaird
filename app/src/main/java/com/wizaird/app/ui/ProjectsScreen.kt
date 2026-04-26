package com.wizaird.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wizaird.app.ui.theme.*

@Composable
fun ProjectsScreen(onBack: () -> Unit, onNewProject: () -> Unit) {
    val colors = LocalWizairdColors.current

    // Placeholder projects list
    val projects = remember {
        listOf(
            "DUNGEON MASTER" to 12,
            "CODE REVIEWER" to 5,
        )
    }

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
                    // Back button
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
                        "PROJECTS",
                        style = pixelStyle(12, colors.secondaryIcon),
                        modifier = Modifier
                            .weight(1f)
                            .offset(y = (-2).dp)
                    )
                    // "+ NEW" button
                    val newInteraction = remember { MutableInteractionSource() }
                    PixelBox(
                        fillColor = Coral,
                        borderColor = Coral,
                        cutColor = colors.secondarySurface,
                        cornerStyle = PixelCornerStyle.Rounded8,
                        modifier = Modifier.pixelRounded8Clickable(interactionSource = newInteraction) { onNewProject() }
                    ) {
                        Text(
                            "+ NEW",
                            style = pixelStyle(10, SecondaryIcon),
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .offset(y = (-2).dp)
                        )
                    }
                }
            }

            // Project list
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                projects.forEach { (name, chatCount) ->
                    ProjectCard(name = name, chatCount = chatCount, onClick = { /* TODO */ })
                }
            }
        }
    }
}

@Composable
fun ProjectCard(name: String, chatCount: Int, onClick: () -> Unit) {
    val colors = LocalWizairdColors.current
    val cardInteraction = remember { MutableInteractionSource() }
    PixelBox(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .pixelRounded8Clickable(interactionSource = cardInteraction) { onClick() },
        fillColor = colors.secondarySurface,
        cornerStyle = PixelCornerStyle.Rounded8
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                name,
                style = pixelStyle(12, colors.secondaryIcon),
                modifier = Modifier.offset(y = (-2).dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "$chatCount CHATS",
                style = pixelStyle(6, colors.secondaryIconSoft),
                modifier = Modifier.offset(y = (-2).dp)
            )
        }
    }
}

package com.wizaird.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wizaird.app.data.projectsFlow
import com.wizaird.app.ui.theme.*

// Placeholder data class for a chat entry
data class Chat(
    val id: String,
    val title: String,
    val preview: String
)

@Composable
fun ProjectScreen(
    projectId: String,
    onBack: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current

    val projects by projectsFlow(context).collectAsState(initial = emptyList())
    val project = projects.firstOrNull { it.id == projectId }
    val projectName = project?.name?.ifEmpty { "UNNAMED PROJECT" } ?: "UNNAMED PROJECT"

    // Placeholder: empty chat list for now
    val chats = remember { emptyList<Chat>() }

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
                    projectName,
                    style = pixelStyle(14, colors.secondaryIcon),
                    modifier = Modifier
                        .weight(1f)
                        .offset(y = (-2).dp)
                )

                PixelCircleIconButton(
                    iconRes = com.wizaird.app.R.drawable.ic_settings_cog,
                    contentDescription = "Project Settings",
                    fillColor = colors.secondaryButton,
                    onClick = onSettingsClick
                )
            }

            // Chat list or blank state
            if (chats.isEmpty()) {
                // Blank state
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "NO CHATS YET",
                            style = pixelStyle(14, colors.secondaryIcon)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "TAP",
                                style = pixelStyle(10, colors.secondaryIconSoft),
                                modifier = Modifier.offset(y = (-2).dp)
                            )
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(
                                    id = com.wizaird.app.R.drawable.ic_comment
                                ),
                                contentDescription = null,
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(colors.secondaryIconSoft),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "TO START A NEW CHAT",
                                style = pixelStyle(10, colors.secondaryIconSoft),
                                modifier = Modifier.offset(y = (-2).dp)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
                ) {
                    items(chats) { chat ->
                        ChatListItem(chat = chat)
                    }
                }
            }
        }

        // FAB — 80dp pixel circle primary button, bottom-right
        val fabInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
                .navigationBarsPadding()
                .size(80.dp)
                .pixelXLargeCircleClickable(interactionSource = fabInteraction) {
                    // TODO: navigate to new chat
                },
            contentAlignment = Alignment.Center
        ) {
            PixelBox(
                modifier = Modifier.fillMaxSize(),
                fillColor = Coral,
                borderColor = Coral,
                cornerStyle = PixelCornerStyle.XLargeCircle
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(
                            id = com.wizaird.app.R.drawable.ic_comment
                        ),
                        contentDescription = "New Chat",
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(SecondaryIcon),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatListItem(chat: Chat) {
    val colors = LocalWizairdColors.current
    val interaction = remember { MutableInteractionSource() }
    PixelBox(
        modifier = Modifier
            .fillMaxWidth()
            .pixelRoundedClickable(interactionSource = interaction, onClick = {}),
        fillColor = colors.secondarySurface,
        cornerStyle = PixelCornerStyle.Rounded
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = chat.title.uppercase(),
                style = pixelStyle(12, colors.secondaryIcon),
                modifier = Modifier.offset(y = (-2).dp)
            )
            Text(
                text = chat.preview,
                style = minecraftStyle(11, colors.secondaryIconSoft),
                maxLines = 2
            )
        }
    }
}

package com.wizaird.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.wizaird.app.data.NoteData
import com.wizaird.app.data.notesFlow
import com.wizaird.app.data.projectsFlow
import com.wizaird.app.ui.theme.*

enum class ProjectTab { CHATS, NOTES }

// Placeholder data class for a chat entry
data class Chat(
    val id: String,
    val title: String,
    val createdAt: String   // formatted date + time string, e.g. "Nov 12, 2024  •  9:41 AM"
)

@Composable
fun ProjectScreen(
    projectId: String,
    onBack: () -> Unit,
    onSettingsClick: () -> Unit,
    onNewChatClick: () -> Unit = {},
    onChatClick: (String) -> Unit = {},
    onNoteClick: (String) -> Unit = {},
    onNewNoteClick: () -> Unit = {},
    initialTab: ProjectTab = ProjectTab.CHATS
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current

    val projects by projectsFlow(context).collectAsState(initial = emptyList())
    val project = projects.firstOrNull { it.id == projectId }
    val projectName = project?.name?.ifEmpty { "UNNAMED PROJECT" } ?: "UNNAMED PROJECT"

    var activeTab by remember { mutableStateOf(initialTab) }

    // Placeholder chat list — replace with real data once AI wiring is done
    val chats = remember {
        listOf(
            Chat(
                id = "1",
                title = "Brainstorming session",
                createdAt = "Nov 12, 2024  •  9:41 AM"
            ),
            Chat(
                id = "2",
                title = "Feature planning",
                createdAt = "Nov 14, 2024  •  2:15 PM"
            ),
            Chat(
                id = "3",
                title = "Bug investigation",
                createdAt = "Nov 18, 2024  •  11:03 AM"
            )
        )
    }

    // Notes — live from DataStore, filtered to this project
    val notes by notesFlow(context, projectId).collectAsState(initial = emptyList())

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

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = com.wizaird.app.R.drawable.ic_folder),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colors.secondaryIcon),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        projectName,
                        style = pixelStyle(14, colors.secondaryIcon),
                        modifier = Modifier.offset(y = (-2).dp)
                    )
                }

                PixelCircleIconButton(
                    iconRes = com.wizaird.app.R.drawable.ic_settings_2,
                    contentDescription = "Project Settings",
                    fillColor = colors.secondaryButton,
                    onClick = onSettingsClick
                )
            }

            // Tab row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                val svgLoader = remember {
                    ImageLoader.Builder(context)
                        .components { add(SvgDecoder.Factory()) }
                        .build()
                }
                listOf(ProjectTab.CHATS to "Chats", ProjectTab.NOTES to "Notes").forEach { (tab, label) ->
                    val isActive = activeTab == tab
                    val tabInteraction = remember { MutableInteractionSource() }
                    val iconColor = if (isActive) colors.textHigh else colors.textXLow
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(IntrinsicSize.Max)
                    ) {
                        PixelBox(
                            modifier = Modifier
                                .pixelRounded8Clickable(
                                    interactionSource = tabInteraction
                                ) { activeTab = tab },
                            fillColor = androidx.compose.ui.graphics.Color.Transparent,
                            borderColor = androidx.compose.ui.graphics.Color.Transparent,
                            cornerStyle = PixelCornerStyle.Rounded8
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                if (tab == ProjectTab.CHATS) {
                                    androidx.compose.foundation.Image(
                                        painter = painterResource(id = com.wizaird.app.R.drawable.ic_comment),
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(iconColor),
                                        modifier = Modifier.size(14.dp)
                                    )
                                } else {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data("file:///android_asset/pixelarticons/sticky-note-text.svg")
                                            .build(),
                                        imageLoader = svgLoader,
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(iconColor),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = label,
                                    style = pixelStyle(12, iconColor),
                                    modifier = Modifier.offset(y = (-2).dp)
                                )
                            }
                        }
                        if (isActive) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .height(4.dp)
                                    .background(Coral)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }

            // Tab content
            when (activeTab) {
                ProjectTab.CHATS -> {
                    // Chat list or blank state
                    if (chats.isEmpty()) {
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
                                ChatListItem(
                                    chat = chat,
                                    onClick = { onChatClick(chat.id) }
                                )
                            }
                        }
                    }
                }
                ProjectTab.NOTES -> {
                    if (notes.isEmpty()) {
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
                                    "NO NOTES YET",
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
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data("file:///android_asset/pixelarticons/sticky-note-text.svg")
                                            .build(),
                                        imageLoader = remember {
                                            ImageLoader.Builder(context)
                                                .components { add(SvgDecoder.Factory()) }
                                                .build()
                                        },
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(colors.secondaryIconSoft),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        "TO CREATE A NEW NOTE",
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
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
                        ) {
                            items(notes) { note ->
                                NoteListItem(
                                    note = note,
                                    onClick = { onNoteClick(note.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // FAB — switches based on active tab
        val fabInteraction = remember { MutableInteractionSource() }
        PixelBox(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
                .navigationBarsPadding()
                .size(80.dp)
                .clip(PixelXLargeCircleShape)
                .pixelXLargeCircleClickable(interactionSource = fabInteraction) {
                    if (activeTab == ProjectTab.CHATS) onNewChatClick()
                    else onNewNoteClick()
                },
            fillColor = if (activeTab == ProjectTab.CHATS) Coral else colors.secondaryButton,
            borderColor = androidx.compose.ui.graphics.Color.Transparent,
            cornerStyle = PixelCornerStyle.XLargeCircle
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (activeTab == ProjectTab.CHATS) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(
                            id = com.wizaird.app.R.drawable.ic_comment
                        ),
                        contentDescription = "New Chat",
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(colors.secondaryIcon),
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data("file:///android_asset/pixelarticons/sticky-note-text.svg")
                            .build(),
                        imageLoader = remember {
                            ImageLoader.Builder(context)
                                .components { add(SvgDecoder.Factory()) }
                                .build()
                        },
                        contentDescription = "New Note",
                        colorFilter = ColorFilter.tint(colors.secondaryIcon),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatListItem(chat: Chat, onClick: () -> Unit = {}) {
    val colors = LocalWizairdColors.current
    val interaction = remember { MutableInteractionSource() }
    PixelBox(
        modifier = Modifier
            .fillMaxWidth()
            .pixelRoundedClickable(interactionSource = interaction, onClick = onClick),
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
                text = chat.createdAt,
                style = pixelStyle(8, colors.secondaryIconSoft),
                modifier = Modifier.offset(y = (-2).dp)
            )
        }
    }
}

@Composable
fun NoteListItem(note: NoteData, onClick: () -> Unit = {}) {
    val colors = LocalWizairdColors.current
    val interaction = remember { MutableInteractionSource() }
    PixelBox(
        modifier = Modifier
            .fillMaxWidth()
            .pixelRoundedClickable(interactionSource = interaction, onClick = onClick),
        fillColor = colors.userBubble,
        borderColor = androidx.compose.ui.graphics.Color.Transparent,
        cornerStyle = PixelCornerStyle.Rounded
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Date — pixel font, small, on top
            Text(
                text = note.createdAt,
                style = pixelStyle(8, colors.secondaryIconSoft),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.offset(y = (-2).dp)
            )
            // Body — minecraft font, same size as bubble text, max 3 lines
            Text(
                text = note.body,
                style = minecraftStyle(12, colors.secondaryIcon),
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.offset(y = (-2).dp)
            )
        }
    }
}

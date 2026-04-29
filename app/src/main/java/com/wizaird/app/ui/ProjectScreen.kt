package com.wizaird.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.wizaird.app.data.NoteData
import com.wizaird.app.data.StoredInsight
import com.wizaird.app.data.chatsFlow
import com.wizaird.app.data.deleteInsight
import com.wizaird.app.data.deleteNote
import com.wizaird.app.data.deleteProject
import com.wizaird.app.data.notesFlow
import com.wizaird.app.data.projectsFlow
import com.wizaird.app.data.storedInsightsFlow
import com.wizaird.app.ui.theme.*
import kotlinx.coroutines.launch

enum class ProjectTab { CHATS, NOTES, INSIGHTS }

@Composable
fun ProjectScreen(
    projectId: String,
    onBack: () -> Unit,
    onSettingsClick: () -> Unit,
    onNewChatClick: () -> Unit = {},
    onChatClick: (String) -> Unit = {},
    onNoteClick: (String) -> Unit = {},
    onNewNoteClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    initialTab: ProjectTab = ProjectTab.CHATS
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val scope = rememberCoroutineScope()

    val projects by projectsFlow(context).collectAsState(initial = emptyList())
    val project = projects.firstOrNull { it.id == projectId }
    val projectName = project?.name?.ifEmpty { "UNNAMED PROJECT" } ?: "UNNAMED PROJECT"

    var activeTab by remember { mutableStateOf(initialTab) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var menuButtonHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    val svgLoader = remember {
        ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
    }

    // Chats — live from DataStore, filtered to this project
    val chats by chatsFlow(context, projectId).collectAsState(initial = emptyList())

    // Notes — live from DataStore, filtered to this project
    val notes by notesFlow(context, projectId).collectAsState(initial = emptyList())

    // Insights — live from DataStore, filtered to this project
    val insights by storedInsightsFlow(context, projectId).collectAsState(initial = emptyList())

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

                Box(
                    modifier = Modifier.onGloballyPositioned { menuButtonHeightPx = it.size.height }
                ) {
                    PixelCircleIconButton(
                        iconRes = com.wizaird.app.R.drawable.ic_more_vertical,
                        contentDescription = "More options",
                        fillColor = colors.secondaryButton,
                        onClick = { showMenu = true }
                    )

                    if (showMenu) {
                        val shadowDp = 8.dp
                        val offsetX = with(density) { shadowDp.roundToPx() }
                        val offsetY = menuButtonHeightPx + with(density) { 8.dp.roundToPx() }
                        Popup(
                            alignment = Alignment.TopEnd,
                            offset = IntOffset(x = offsetX, y = offsetY),
                            onDismissRequest = { showMenu = false },
                            properties = PopupProperties(focusable = false)
                        ) {
                            // Outer box adds right padding = shadow spread so the popup's
                            // layout width includes the shadow, keeping the visual right
                            // edge aligned with the button while the shadow bleeds outward.
                            Box(modifier = Modifier.padding(end = shadowDp)) {
                            Box(
                                modifier = Modifier
                                    .graphicsLayer {
                                        compositingStrategy = CompositingStrategy.Offscreen
                                        shadowElevation = 8.dp.toPx()
                                        shape = PixelRounded8Shape
                                        clip = true
                                    }
                                    .drawBehind {
                                        val p = PixelSize.toPx()
                                        val w = size.width
                                        val h = size.height
                                        val fill = colors.userBubble
                                        val cut = Color.Transparent

                                        drawRect(fill)

                                        // Top-left
                                        drawRect(cut, Offset(0f, p*0), Size(p*5, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(0f, p*1), Size(p*3, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(0f, p*2), Size(p*2, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(0f, p*3), Size(p*1, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(0f, p*4), Size(p*1, p), blendMode = BlendMode.Clear)
                                        // Top-right
                                        drawRect(cut, Offset(w-p*5, p*0), Size(p*5, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(w-p*3, p*1), Size(p*3, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(w-p*2, p*2), Size(p*2, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(w-p*1, p*3), Size(p*1, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(w-p*1, p*4), Size(p*1, p), blendMode = BlendMode.Clear)
                                        // Bottom-left
                                        drawRect(cut, Offset(0f, h-p*1), Size(p*5, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(0f, h-p*2), Size(p*3, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(0f, h-p*3), Size(p*2, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(0f, h-p*4), Size(p*1, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(0f, h-p*5), Size(p*1, p), blendMode = BlendMode.Clear)
                                        // Bottom-right
                                        drawRect(cut, Offset(w-p*5, h-p*1), Size(p*5, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(w-p*3, h-p*2), Size(p*3, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(w-p*2, h-p*3), Size(p*2, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(w-p*1, h-p*4), Size(p*1, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(w-p*1, h-p*5), Size(p*1, p), blendMode = BlendMode.Clear)
                                    }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .width(IntrinsicSize.Max)
                                        .padding(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(0.dp)
                                ) {
                                    // Settings option
                                    val settingsInteraction = remember { MutableInteractionSource() }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pixelRounded8Clickable(
                                                interactionSource = settingsInteraction,
                                                onClick = {
                                                    showMenu = false
                                                    onSettingsClick()
                                                }
                                            )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data("file:///android_asset/pixelarticons/settings-2.svg")
                                                    .build(),
                                                imageLoader = svgLoader,
                                                contentDescription = "Settings",
                                                colorFilter = ColorFilter.tint(colors.secondaryIcon),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                "Settings",
                                                style = pixelStyle(10, colors.secondaryIcon),
                                                modifier = Modifier.offset(y = (-2).dp)
                                            )
                                        }
                                    }

                                    // Bookmark option
                                    val bookmarkInteraction = remember { MutableInteractionSource() }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pixelRounded8Clickable(
                                                interactionSource = bookmarkInteraction,
                                                onClick = { showMenu = false }
                                            )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data("file:///android_asset/pixelarticons/bookmark.svg")
                                                    .build(),
                                                imageLoader = svgLoader,
                                                contentDescription = "Bookmark",
                                                colorFilter = ColorFilter.tint(colors.secondaryIcon),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                "Bookmark",
                                                style = pixelStyle(10, colors.secondaryIcon),
                                                modifier = Modifier.offset(y = (-2).dp)
                                            )
                                        }
                                    }

                                    // Delete option
                                    val deleteInteraction = remember { MutableInteractionSource() }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pixelRounded8Clickable(
                                                interactionSource = deleteInteraction,
                                                onClick = {
                                                    showMenu = false
                                                    showDeleteDialog = true
                                                }
                                            )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data("file:///android_asset/pixelarticons/delete.svg")
                                                    .build(),
                                                imageLoader = svgLoader,
                                                contentDescription = "Delete",
                                                colorFilter = ColorFilter.tint(colors.coral),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                "Delete",
                                                style = pixelStyle(10, colors.coral),
                                                modifier = Modifier.offset(y = (-2).dp)
                                            )
                                        }
                                    }
                                }
                            }
                            } // end shadow Box wrapper
                        }
                    }
                }
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
                listOf(
                    ProjectTab.CHATS to "Chats",
                    ProjectTab.NOTES to "Notes",
                    ProjectTab.INSIGHTS to "Insights"
                ).forEach { (tab, label) ->
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
                                when (tab) {
                                    ProjectTab.CHATS -> {
                                        androidx.compose.foundation.Image(
                                            painter = painterResource(id = com.wizaird.app.R.drawable.ic_comment),
                                            contentDescription = null,
                                            colorFilter = ColorFilter.tint(iconColor),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    ProjectTab.NOTES -> {
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
                                    ProjectTab.INSIGHTS -> {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data("file:///android_asset/pixelarticons/lightbulb-off.svg")
                                                .build(),
                                            imageLoader = svgLoader,
                                            contentDescription = null,
                                            colorFilter = ColorFilter.tint(iconColor),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
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
                ProjectTab.INSIGHTS -> {
                    if (insights.isEmpty()) {
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
                                    "NO INSIGHTS YET",
                                    style = pixelStyle(14, colors.secondaryIcon)
                                )
                                Text(
                                    "INSIGHTS WILL APPEAR HERE AS THEY'RE GENERATED",
                                    style = pixelStyle(10, colors.secondaryIconSoft),
                                    modifier = Modifier.offset(y = (-2).dp)
                                )
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
                            items(insights) { insight ->
                                InsightListItem(
                                    insight = insight
                                )
                            }
                        }
                    }
                }
            }
        }

        // FAB — switches based on active tab (only show for CHATS and NOTES)
        if (activeTab == ProjectTab.CHATS || activeTab == ProjectTab.NOTES) {
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

        // Delete confirmation dialog
        if (showDeleteDialog) {
            PixelConfirmationDialog(
                title = "DELETE PROJECT",
                message = "Are you sure you want to delete \"$projectName\"? This will also delete all chats and notes in this project. This action cannot be undone.",
                confirmLabel = "DELETE",
                cancelLabel = "CANCEL",
                isDestructive = true,
                onConfirm = {
                    showDeleteDialog = false
                    scope.launch {
                        deleteProject(context, projectId)
                        onBack()
                    }
                },
                onDismiss = {
                    showDeleteDialog = false
                }
            )
        }
    }
}

@Composable
fun ChatListItem(chat: com.wizaird.app.data.ChatData, onClick: () -> Unit = {}) {
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
                text = chat.formattedCreatedAt(),
                style = pixelStyle(8, colors.secondaryIconSoft),
                modifier = Modifier.offset(y = (-2).dp)
            )
        }
    }
}

@Composable
fun NoteListItem(note: NoteData, onClick: () -> Unit = {}, onDelete: () -> Unit = {}) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    val svgLoader = remember {
        ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
    }
    
    Box {
        PixelBox(
            modifier = Modifier
                .fillMaxWidth()
                .pixelRoundedClickable(interactionSource = interaction, onClick = onClick),
            fillColor = colors.userBubble,
            borderColor = androidx.compose.ui.graphics.Color.Transparent,
            cornerStyle = PixelCornerStyle.Rounded
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 12.dp, end = 10.dp, bottom = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 32.dp), // Leave space for the options button
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
                
                // More options button - offset so icon sits at top-right edge
                val moreInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 7.dp, y = (-7).dp) // Offset to position icon at edge
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .pixelRounded8Clickable(
                                interactionSource = moreInteraction,
                                onClick = { showMenu = true }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("file:///android_asset/pixelarticons/more-vertical.svg")
                                .build(),
                            imageLoader = svgLoader,
                            contentDescription = "More options",
                            colorFilter = ColorFilter.tint(colors.secondaryIconSoft),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    // Options menu popup - positioned relative to button
                    if (showMenu) {
                        val density = LocalDensity.current
                        val offsetY = with(density) { (32 + 4).dp.roundToPx() } // Button height + 4dp gap
                        Popup(
                            alignment = Alignment.TopEnd,
                            offset = IntOffset(x = 0, y = offsetY),
                            onDismissRequest = { showMenu = false },
                            properties = PopupProperties(focusable = false)
                        ) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                            shadowElevation = 8.dp.toPx()
                            shape = PixelRounded8Shape
                            clip = true
                        }
                        .drawBehind {
                            val p = PixelSize.toPx()
                            val w = size.width
                            val h = size.height
                            val fill = colors.userBubble
                            val cut = Color.Transparent

                            drawRect(fill)

                            // Top-left
                            drawRect(cut, Offset(0f, p*0), Size(p*5, p), blendMode = BlendMode.Clear)
                            drawRect(cut, Offset(0f, p*1), Size(p*3, p), blendMode = BlendMode.Clear)
                            drawRect(cut, Offset(0f, p*2), Size(p*2, p), blendMode = BlendMode.Clear)
                            drawRect(cut, Offset(0f, p*3), Size(p*1, p), blendMode = BlendMode.Clear)
                            drawRect(cut, Offset(0f, p*4), Size(p*1, p), blendMode = BlendMode.Clear)
                            // Top-right
                            drawRect(cut, Offset(w-p*5, p*0), Size(p*5, p), blendMode = BlendMode.Clear)
                            drawRect(cut, Offset(w-p*3, p*1), Size(p*3, p), blendMode = BlendMode.Clear)
                            drawRect(cut, Offset(w-p*2, p*2), Size(p*2, p), blendMode = BlendMode.Clear)
                            drawRect(cut, Offset(w-p*1, p*3), Size(p*1, p), blendMode = BlendMode.Clear)
                            drawRect(cut, Offset(w-p*1, p*4), Size(p*1, p), blendMode = BlendMode.Clear)
                            // Bottom-left
                            drawRect(cut, Offset(0f, h-p*1), Size(p*5, p), blendMode = BlendMode.Clear)
                            drawRect(cut, Offset(0f, h-p*2), Size(p*3, p), blendMode = BlendMode.Clear)
                            drawRect(cut, Offset(0f, h-p*3), Size(p*2, p), blendMode = BlendMode.Clear)
                            drawRect(cut, Offset(0f, h-p*4), Size(p*1, p), blendMode = BlendMode.Clear)
                            drawRect(cut, Offset(0f, h-p*5), Size(p*1, p), blendMode = BlendMode.Clear)
                            // Bottom-right
                            drawRect(cut, Offset(w-p*5, h-p*1), Size(p*5, p), blendMode = BlendMode.Clear)
                            drawRect(cut, Offset(w-p*3, h-p*2), Size(p*3, p), blendMode = BlendMode.Clear)
                            drawRect(cut, Offset(w-p*2, h-p*3), Size(p*2, p), blendMode = BlendMode.Clear)
                            drawRect(cut, Offset(w-p*1, h-p*4), Size(p*1, p), blendMode = BlendMode.Clear)
                            drawRect(cut, Offset(w-p*1, h-p*5), Size(p*1, p), blendMode = BlendMode.Clear)
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .width(IntrinsicSize.Max)
                            .padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        // Delete option
                        val deleteInteraction = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pixelRounded8Clickable(
                                    interactionSource = deleteInteraction,
                                    onClick = {
                                        showMenu = false
                                        showDeleteDialog = true
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data("file:///android_asset/pixelarticons/delete.svg")
                                        .build(),
                                    imageLoader = svgLoader,
                                    contentDescription = "Delete",
                                    colorFilter = ColorFilter.tint(colors.coral),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "Delete",
                                    style = pixelStyle(10, colors.coral),
                                    modifier = Modifier.offset(y = (-2).dp)
                                )
                            }
                        }
                    }
                }
                        }
                    }
                }
            }
        }
        
        // Delete confirmation dialog
        if (showDeleteDialog) {
            PixelConfirmationDialog(
                title = "DELETE NOTE",
                message = "Are you sure you want to delete this note? This action cannot be undone.",
                confirmLabel = "DELETE",
                cancelLabel = "CANCEL",
                isDestructive = true,
                onConfirm = {
                    showDeleteDialog = false
                    scope.launch {
                        deleteNote(context, note.id)
                    }
                },
                onDismiss = {
                    showDeleteDialog = false
                }
            )
        }
    }
}

@Composable
fun InsightListItem(insight: StoredInsight) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val scope = rememberCoroutineScope()
    
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    val svgLoader = remember {
        ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
    }
    
    Box {
        PixelBox(
            modifier = Modifier.fillMaxWidth(),
            fillColor = colors.secondarySurface,
            borderColor = androidx.compose.ui.graphics.Color.Transparent,
            cornerStyle = PixelCornerStyle.Rounded
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 12.dp, end = 10.dp, bottom = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 32.dp), // Leave space for the options button
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Date — pixel font, small, on top
                    Text(
                        text = insight.formattedCreatedAt(),
                        style = pixelStyle(8, colors.secondaryIconSoft),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.offset(y = (-2).dp)
                    )
                    // Insight text — minecraft font, with markdown support
                    MarkdownText(
                        markdown = insight.text,
                        style = minecraftStyle(12, colors.secondaryIcon),
                        modifier = Modifier.offset(y = (-2).dp)
                    )
                }
                
                // More options button - offset so icon sits at top-right edge
                val moreInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 7.dp, y = (-7).dp) // Offset to position icon at edge
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .pixelRounded8Clickable(
                                interactionSource = moreInteraction,
                                onClick = { showMenu = true }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("file:///android_asset/pixelarticons/more-vertical.svg")
                                .build(),
                            imageLoader = svgLoader,
                            contentDescription = "More options",
                            colorFilter = ColorFilter.tint(colors.secondaryIconSoft),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    // Options menu popup - positioned relative to button
                    if (showMenu) {
                        val density = LocalDensity.current
                        val offsetY = with(density) { (32 + 4).dp.roundToPx() } // Button height + 4dp gap
                        Popup(
                            alignment = Alignment.TopEnd,
                            offset = IntOffset(x = 0, y = offsetY),
                            onDismissRequest = { showMenu = false },
                            properties = PopupProperties(focusable = false)
                        ) {
                            Box(
                                modifier = Modifier
                                    .graphicsLayer {
                                        compositingStrategy = CompositingStrategy.Offscreen
                                        shadowElevation = 8.dp.toPx()
                                        shape = PixelRounded8Shape
                                        clip = true
                                    }
                                    .drawBehind {
                                        val p = PixelSize.toPx()
                                        val w = size.width
                                        val h = size.height
                                        val fill = colors.secondarySurface
                                        val cut = Color.Transparent

                                        drawRect(fill)

                                        // Top-left
                                        drawRect(cut, Offset(0f, p*0), Size(p*5, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(0f, p*1), Size(p*3, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(0f, p*2), Size(p*2, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(0f, p*3), Size(p*1, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(0f, p*4), Size(p*1, p), blendMode = BlendMode.Clear)
                                        // Top-right
                                        drawRect(cut, Offset(w-p*5, p*0), Size(p*5, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(w-p*3, p*1), Size(p*3, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(w-p*2, p*2), Size(p*2, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(w-p*1, p*3), Size(p*1, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(w-p*1, p*4), Size(p*1, p), blendMode = BlendMode.Clear)
                                        // Bottom-left
                                        drawRect(cut, Offset(0f, h-p*1), Size(p*5, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(0f, h-p*2), Size(p*3, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(0f, h-p*3), Size(p*2, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(0f, h-p*4), Size(p*1, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(0f, h-p*5), Size(p*1, p), blendMode = BlendMode.Clear)
                                        // Bottom-right
                                        drawRect(cut, Offset(w-p*5, h-p*1), Size(p*5, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(w-p*3, h-p*2), Size(p*3, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(w-p*2, h-p*3), Size(p*2, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(w-p*1, h-p*4), Size(p*1, p), blendMode = BlendMode.Clear)
                                        drawRect(cut, Offset(w-p*1, h-p*5), Size(p*1, p), blendMode = BlendMode.Clear)
                                    }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .width(IntrinsicSize.Max)
                                        .padding(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(0.dp)
                                ) {
                                    // Delete option
                                    val deleteInteraction = remember { MutableInteractionSource() }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pixelRounded8Clickable(
                                                interactionSource = deleteInteraction,
                                                onClick = {
                                                    showMenu = false
                                                    showDeleteDialog = true
                                                }
                                            )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data("file:///android_asset/pixelarticons/delete.svg")
                                                    .build(),
                                                imageLoader = svgLoader,
                                                contentDescription = "Delete",
                                                colorFilter = ColorFilter.tint(colors.coral),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                "Delete",
                                                style = pixelStyle(10, colors.coral),
                                                modifier = Modifier.offset(y = (-2).dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Delete confirmation dialog
        if (showDeleteDialog) {
            PixelConfirmationDialog(
                title = "DELETE INSIGHT",
                message = "Are you sure you want to delete this insight? This action cannot be undone.",
                confirmLabel = "DELETE",
                cancelLabel = "CANCEL",
                isDestructive = true,
                onConfirm = {
                    showDeleteDialog = false
                    scope.launch {
                        deleteInsight(context, insight.id)
                    }
                },
                onDismiss = {
                    showDeleteDialog = false
                }
            )
        }
    }
}

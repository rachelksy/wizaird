package com.wizaird.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.wizaird.app.data.NoteData
import com.wizaird.app.data.StoredInsight
import com.wizaird.app.data.GlossaryWord
import com.wizaird.app.data.GlossarySortOrder
import com.wizaird.app.data.chatsFlow
import com.wizaird.app.data.deleteInsight
import com.wizaird.app.data.deleteNote
import com.wizaird.app.data.deleteProject
import com.wizaird.app.data.deleteGlossaryWord
import com.wizaird.app.data.notesFlow
import com.wizaird.app.data.projectsFlow
import com.wizaird.app.data.storedInsightsFlow
import com.wizaird.app.data.searchGlossaryWords
import com.wizaird.app.ui.theme.*
import kotlinx.coroutines.launch

enum class ProjectTab { CHATS, NOTES, INSIGHTS, GLOSSARY }

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
    onInsightChatClick: (String) -> Unit = {}, // New callback for insight -> chat
    onNewGlossaryWordClick: () -> Unit = {}, // New callback for adding glossary word
    onEditGlossaryWordClick: (String) -> Unit = {}, // New callback for editing glossary word
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

    // Glossary — search and sort state
    var glossarySearchQuery by remember { mutableStateOf("") }
    var glossarySortOrder by remember { mutableStateOf(GlossarySortOrder.DATE_DESC) }
    var showSortBottomSheet by remember { mutableStateOf(false) }
    
    // Glossary words — live from DataStore with search and sort
    val glossaryWords by searchGlossaryWords(context, projectId, glossarySearchQuery, glossarySortOrder)
        .collectAsState(initial = emptyList())

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
            val tabScrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(tabScrollState)
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
                    ProjectTab.INSIGHTS to "Insights",
                    ProjectTab.NOTES to "Notes",
                    ProjectTab.GLOSSARY to "Glossary"
                ).forEachIndexed { _, (tab, label) ->
                    val isActive = activeTab == tab
                    val tabInteraction = remember { MutableInteractionSource() }
                    val iconColor = if (isActive) colors.textHigh else colors.textXLow
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(IntrinsicSize.Max)
                    ) {
                        // Scroll to start when Chats is active, scroll to end when Glossary is active
                        LaunchedEffect(isActive) {
                            if (isActive) {
                                when (tab) {
                                    ProjectTab.CHATS -> tabScrollState.animateScrollTo(0)
                                    ProjectTab.GLOSSARY -> tabScrollState.animateScrollTo(tabScrollState.maxValue)
                                    else -> {}
                                }
                            }
                        }
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
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                when (tab) {
                                    ProjectTab.CHATS -> {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data("file:///android_asset/pixelarticons/message.svg")
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
                                    ProjectTab.GLOSSARY -> {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data("file:///android_asset/pixelarticons/notebook.svg")
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
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data("file:///android_asset/pixelarticons/message.svg")
                                            .build(),
                                        imageLoader = svgLoader,
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
                                    insight = insight,
                                    onChatClick = { onInsightChatClick(insight.id) },
                                    onNewGlossaryWordClick = onNewGlossaryWordClick
                                )
                            }
                        }
                    }
                }
                ProjectTab.GLOSSARY -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // Search bar and sort button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            // Search bar
                            PixelBox(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 40.dp), // Leave space for sort button with ripple
                                fillColor = colors.tertiarySurface,
                                borderColor = androidx.compose.ui.graphics.Color.Transparent,
                                cornerStyle = PixelCornerStyle.Rounded8
                            ) {
                                androidx.compose.foundation.text.BasicTextField(
                                    value = glossarySearchQuery,
                                    onValueChange = { glossarySearchQuery = it },
                                    textStyle = minecraftStyle(12, colors.textHigh),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    decorationBox = { innerTextField ->
                                        if (glossarySearchQuery.isEmpty()) {
                                            Text(
                                                "Search",
                                                style = minecraftStyle(12, colors.textXLow)
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                            }
                            
                            // Sort button - aligned to the right with ripple
                            val sortInteraction = remember { MutableInteractionSource() }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .size(40.dp) // Fixed size for consistent alignment
                                    .pixelRounded8ClickableOversize(interactionSource = sortInteraction) {
                                        showSortBottomSheet = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data("file:///android_asset/pixelarticons/arrow-down-wide-narrow.svg")
                                        .build(),
                                    imageLoader = svgLoader,
                                    contentDescription = "Sort",
                                    colorFilter = ColorFilter.tint(colors.secondaryIconSoft),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        
                        // Word list or empty state
                        if (glossaryWords.isEmpty()) {
                            if (glossarySearchQuery.isEmpty()) {
                                // No words yet - centered
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
                                            "NO WORDS YET",
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
                                                    .data("file:///android_asset/pixelarticons/notebook.svg")
                                                    .build(),
                                                imageLoader = svgLoader,
                                                contentDescription = null,
                                                colorFilter = ColorFilter.tint(colors.secondaryIconSoft),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                "TO ADD A WORD",
                                                style = pixelStyle(10, colors.secondaryIconSoft),
                                                modifier = Modifier.offset(y = (-2).dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                // No results found - 40px below search field
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Spacer(modifier = Modifier.height(40.dp))
                                    Text(
                                        "NO RESULTS FOUND",
                                        style = pixelStyle(14, colors.textLow)
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
                                contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)
                            ) {
                                items(glossaryWords) { word ->
                                    GlossaryWordListItem(
                                        word = word,
                                        onEdit = { onEditGlossaryWordClick(word.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // FAB — switches based on active tab (only show for CHATS, NOTES, and GLOSSARY)
        if (activeTab == ProjectTab.CHATS || activeTab == ProjectTab.NOTES || activeTab == ProjectTab.GLOSSARY) {
            val fabInteraction = remember { MutableInteractionSource() }
            PixelBox(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
                    .navigationBarsPadding()
                    .size(80.dp)
                    .clip(PixelXLargeCircleShape)
                    .pixelXLargeCircleClickable(interactionSource = fabInteraction) {
                        when (activeTab) {
                            ProjectTab.CHATS -> onNewChatClick()
                            ProjectTab.NOTES -> onNewNoteClick()
                            ProjectTab.GLOSSARY -> onNewGlossaryWordClick()
                            else -> {}
                        }
                    },
                fillColor = if (activeTab == ProjectTab.CHATS) Coral else colors.secondaryButton,
                borderColor = androidx.compose.ui.graphics.Color.Transparent,
                cornerStyle = PixelCornerStyle.XLargeCircle
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when (activeTab) {
                        ProjectTab.CHATS -> {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data("file:///android_asset/pixelarticons/message.svg")
                                    .build(),
                                imageLoader = svgLoader,
                                contentDescription = "New Chat",
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(colors.secondaryIcon),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        ProjectTab.NOTES -> {
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
                        ProjectTab.GLOSSARY -> {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data("file:///android_asset/pixelarticons/notebook.svg")
                                    .build(),
                                imageLoader = svgLoader,
                                contentDescription = "New Word",
                                colorFilter = ColorFilter.tint(colors.secondaryIcon),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        else -> {}
                    }
                }
            }
        }

        // Sort bottom sheet for glossary
        if (showSortBottomSheet) {
            GlossarySortBottomSheet(
                currentSortOrder = glossarySortOrder,
                onSortSelected = { newOrder ->
                    glossarySortOrder = newOrder
                    showSortBottomSheet = false
                },
                onDismiss = { showSortBottomSheet = false }
            )
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
                style = pixelStyle(12, colors.secondaryIcon).copy(
                    lineHeight = (12 * 1.6f).sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
    var showMoveDialog by remember { mutableStateOf(false) }
    
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
                        // Move to option
                        val moveInteraction = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pixelRounded8Clickable(
                                    interactionSource = moveInteraction,
                                    onClick = {
                                        showMenu = false
                                        showMoveDialog = true
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = painterResource(id = com.wizaird.app.R.drawable.ic_folder),
                                    contentDescription = "Move to",
                                    colorFilter = ColorFilter.tint(colors.secondaryIcon),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "Move to",
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
        
        // Move to project dialog
        if (showMoveDialog) {
            MoveNoteDialog(
                currentProjectId = note.projectId,
                onMove = { targetProjectId ->
                    showMoveDialog = false
                    scope.launch {
                        com.wizaird.app.data.moveNoteToProject(context, note.id, targetProjectId)
                    }
                },
                onDismiss = {
                    showMoveDialog = false
                }
            )
        }
    }
}

@Composable
fun InsightListItem(
    insight: StoredInsight,
    onChatClick: () -> Unit = {},
    onNewGlossaryWordClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val scope = rememberCoroutineScope()
    
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    val cardInteraction = remember { MutableInteractionSource() }
    
    val svgLoader = remember {
        ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
    }
    
    Box {
        PixelBox(
            modifier = Modifier
                .fillMaxWidth()
                .pixelRoundedClickable(
                    interactionSource = cardInteraction,
                    onClick = { showPreview = true }
                ),
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
                    // Insight text — minecraft font, with markdown support, max 5 lines
                    MarkdownText(
                        markdown = insight.text,
                        style = minecraftStyle(12, colors.secondaryIcon),
                        modifier = Modifier.offset(y = (-2).dp),
                        maxLines = 5
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
                                    // Move to option
                                    val moveInteraction = remember { MutableInteractionSource() }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pixelRounded8Clickable(
                                                interactionSource = moveInteraction,
                                                onClick = {
                                                    showMenu = false
                                                    showMoveDialog = true
                                                }
                                            )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            androidx.compose.foundation.Image(
                                                painter = painterResource(id = com.wizaird.app.R.drawable.ic_folder),
                                                contentDescription = "Move to",
                                                colorFilter = ColorFilter.tint(colors.secondaryIcon),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                "Move to",
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
        
        // Move to project dialog
        if (showMoveDialog) {
            MoveInsightDialog(
                currentProjectId = insight.projectId,
                onMove = { targetProjectId ->
                    showMoveDialog = false
                    scope.launch {
                        com.wizaird.app.data.moveInsightToProject(context, insight.id, targetProjectId)
                    }
                },
                onDismiss = {
                    showMoveDialog = false
                }
            )
        }
        
        // Preview overlay
        if (showPreview) {
            InsightPreviewOverlay(
                insight = insight,
                onDismiss = { showPreview = false },
                onChatClick = {
                    showPreview = false
                    onChatClick()
                },
                onNewGlossaryWordClick = onNewGlossaryWordClick
            )
        }
    }
}

@Composable
fun InsightPreviewOverlay(
    insight: StoredInsight,
    onDismiss: () -> Unit,
    onChatClick: () -> Unit = {},
    onNewGlossaryWordClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    
    var copied by remember { mutableStateOf(false) }
    var noteSaved by remember { mutableStateOf(false) }
    var showToast by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf("") }
    var isToastLoading by remember { mutableStateOf(false) }
    
    // Auto-hide toast after 2 seconds (only when not loading)
    LaunchedEffect(showToast, isToastLoading) {
        if (showToast && !isToastLoading) {
            kotlinx.coroutines.delay(2000)
            showToast = false
        }
    }
    
    val svgLoader = remember {
        ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
    }
    
    // Full-screen dialog
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 48.dp)
                .padding(8.dp)
        ) {
            PixelBox(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(PixelRoundedShape),
                fillColor = colors.secondarySurface,
                borderColor = colors.border,
                cutColor = colors.secondarySurface,
                cornerStyle = PixelCornerStyle.Rounded
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Fixed header with date and close button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Date on the left
                        Text(
                            text = insight.formattedCreatedAt(),
                            style = pixelStyle(10, colors.secondaryIconSoft),
                            modifier = Modifier.offset(y = (-2).dp)
                        )
                        
                        // Close button (X) - using existing button pattern
                        val closeInteraction = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .pixelRounded8Clickable(
                                    interactionSource = closeInteraction,
                                    onClick = onDismiss
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .drawBehind {
                                        val p = PixelSize.toPx()
                                        val color = colors.secondaryIcon
                                        
                                        // Draw X with diagonal pixel steps
                                        // Top-left to bottom-right diagonal
                                        drawRect(color, Offset(p * 1, p * 1), Size(p, p))
                                        drawRect(color, Offset(p * 2, p * 2), Size(p, p))
                                        drawRect(color, Offset(p * 3, p * 3), Size(p, p))
                                        drawRect(color, Offset(p * 4, p * 4), Size(p, p))
                                        drawRect(color, Offset(p * 5, p * 5), Size(p, p))
                                        
                                        // Top-right to bottom-left diagonal
                                        drawRect(color, Offset(p * 5, p * 1), Size(p, p))
                                        drawRect(color, Offset(p * 4, p * 2), Size(p, p))
                                        drawRect(color, Offset(p * 3, p * 3), Size(p, p))
                                        drawRect(color, Offset(p * 2, p * 4), Size(p, p))
                                        drawRect(color, Offset(p * 1, p * 5), Size(p, p))
                                    }
                            )
                        }
                    }
                    
                    // Scrollable content area
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .padding(bottom = 16.dp)
                        ) {
                            SelectableMarkdownText(
                                markdown = insight.text,
                                style = minecraftStyle(14, colors.textHigh).copy(
                                    lineHeight = (14 * 1.6f).sp
                                ),
                                modifier = Modifier.offset(y = (-2).dp),
                                onAddToGlossary = { selectedText ->
                                    toastMessage = "ADDING TO GLOSSARY"
                                    showToast = true
                                    isToastLoading = true
                                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        val definition = com.wizaird.app.data.generateGlossaryDefinition(
                                            context = context,
                                            highlightedTerm = selectedText,
                                            contextText = insight.text
                                        )
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            isToastLoading = false
                                            showToast = false
                                            GlossaryNavigationData.setPendingData(
                                                term = definition.term,
                                                explanation = definition.definition,
                                                aliases = definition.aliases
                                            )
                                            onDismiss()
                                            onNewGlossaryWordClick()
                                        }
                                    }
                                }
                            )
                        }
                    }
                    
                    // Action icons row
                    Row(
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Copy icon
                        val copyInteraction = remember { MutableInteractionSource() }
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("file:///android_asset/pixelarticons/copy.svg")
                                .build(),
                            imageLoader = svgLoader,
                            contentDescription = "Copy",
                            colorFilter = ColorFilter.tint(if (copied) colors.textHigh else colors.textXLow),
                            modifier = Modifier
                                .size(20.dp)
                                .pixelRounded8ClickableOversize(
                                    interactionSource = copyInteraction
                                ) {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(insight.text))
                                    copied = true
                                    toastMessage = "COPIED TO CLIPBOARD"
                                    showToast = true
                                    scope.launch {
                                        kotlinx.coroutines.delay(2000)
                                        copied = false
                                    }
                                }
                        )

                        // Create note icon
                        val noteInteraction = remember { MutableInteractionSource() }
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("file:///android_asset/pixelarticons/sticky-note-text.svg")
                                .build(),
                            imageLoader = svgLoader,
                            contentDescription = "Save to note",
                            colorFilter = ColorFilter.tint(if (noteSaved) colors.textHigh else colors.textXLow),
                            modifier = Modifier
                                .size(20.dp)
                                .pixelRounded8ClickableOversize(
                                    interactionSource = noteInteraction
                                ) {
                                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        // Create a new note with the insight text
                                        val note = com.wizaird.app.data.newNote(insight.projectId).copy(
                                            body = insight.text
                                        )
                                        com.wizaird.app.data.upsertNote(context, note)
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            noteSaved = true
                                            toastMessage = "NOTE CREATED"
                                            showToast = true
                                            scope.launch {
                                                kotlinx.coroutines.delay(2000)
                                                noteSaved = false
                                            }
                                        }
                                    }
                                }
                        )
                        
                        // Chat icon
                        val chatInteraction = remember { MutableInteractionSource() }
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("file:///android_asset/pixelarticons/message.svg")
                                .build(),
                            imageLoader = svgLoader,
                            contentDescription = "Start chat",
                            colorFilter = ColorFilter.tint(colors.textXLow),
                            modifier = Modifier
                                .size(20.dp)
                                .pixelRounded8ClickableOversize(
                                    interactionSource = chatInteraction
                                ) {
                                    onChatClick()
                                }
                        )
                    }
                }
            }
            
            // Toast overlay
            if (showToast) {
                PixelToast(
                    message = toastMessage,
                    visible = showToast,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    isLoading = isToastLoading
                )
            }
        }
    }
}

@Composable
fun MoveNoteDialog(
    currentProjectId: String,
    onMove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    
    val projects by projectsFlow(context).collectAsState(initial = emptyList())
    val availableProjects = projects.filter { it.id != currentProjectId }
    
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        PixelBox(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(PixelRoundedShape),
            fillColor = colors.secondarySurface,
            borderColor = colors.border,
            cutColor = colors.secondarySurface,
            cornerStyle = PixelCornerStyle.Rounded
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "MOVE TO",
                    style = pixelStyle(12, colors.textHigh),
                    modifier = Modifier.offset(y = (-2).dp)
                )
                
                // Project list
                if (availableProjects.isEmpty()) {
                    Text(
                        text = "No other projects available",
                        style = minecraftStyle(14, colors.textLow).copy(
                            lineHeight = (14 * 1.6f).sp
                        ),
                        modifier = Modifier.offset(y = (-2).dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableProjects.forEach { project ->
                            val isSelected = selectedProjectId == project.id
                            val projectInteraction = remember { MutableInteractionSource() }
                            
                            PixelBox(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pixelRoundedClickable(
                                        interactionSource = projectInteraction,
                                        onClick = { selectedProjectId = project.id }
                                    ),
                                fillColor = if (isSelected) colors.userBubble else colors.background,
                                borderColor = if (isSelected) Coral else androidx.compose.ui.graphics.Color.Transparent,
                                cornerStyle = PixelCornerStyle.Rounded
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    androidx.compose.foundation.Image(
                                        painter = painterResource(id = com.wizaird.app.R.drawable.ic_folder),
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(colors.secondaryIcon),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = project.name.ifEmpty { "UNNAMED PROJECT" },
                                        style = pixelStyle(10, colors.secondaryIcon),
                                        modifier = Modifier.offset(y = (-2).dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PixelButtonLarge(
                        label = "CANCEL",
                        primary = false,
                        modifier = Modifier.weight(1f),
                        cutColor = colors.secondarySurface,
                        onClick = onDismiss
                    )
                    PixelButtonLarge(
                        label = "MOVE",
                        primary = true,
                        modifier = Modifier.weight(1f),
                        cutColor = colors.secondarySurface,
                        onClick = {
                            selectedProjectId?.let { onMove(it) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MoveInsightDialog(
    currentProjectId: String,
    onMove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    
    val projects by projectsFlow(context).collectAsState(initial = emptyList())
    val availableProjects = projects.filter { it.id != currentProjectId }
    
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        PixelBox(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(PixelRoundedShape),
            fillColor = colors.secondarySurface,
            borderColor = colors.border,
            cutColor = colors.secondarySurface,
            cornerStyle = PixelCornerStyle.Rounded
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "MOVE TO",
                    style = pixelStyle(12, colors.textHigh),
                    modifier = Modifier.offset(y = (-2).dp)
                )
                
                // Project list
                if (availableProjects.isEmpty()) {
                    Text(
                        text = "No other projects available",
                        style = minecraftStyle(14, colors.textLow).copy(
                            lineHeight = (14 * 1.6f).sp
                        ),
                        modifier = Modifier.offset(y = (-2).dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableProjects.forEach { project ->
                            val isSelected = selectedProjectId == project.id
                            val projectInteraction = remember { MutableInteractionSource() }
                            
                            PixelBox(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pixelRoundedClickable(
                                        interactionSource = projectInteraction,
                                        onClick = { selectedProjectId = project.id }
                                    ),
                                fillColor = if (isSelected) colors.userBubble else colors.background,
                                borderColor = if (isSelected) Coral else androidx.compose.ui.graphics.Color.Transparent,
                                cornerStyle = PixelCornerStyle.Rounded
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    androidx.compose.foundation.Image(
                                        painter = painterResource(id = com.wizaird.app.R.drawable.ic_folder),
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(colors.secondaryIcon),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = project.name.ifEmpty { "UNNAMED PROJECT" },
                                        style = pixelStyle(10, colors.secondaryIcon),
                                        modifier = Modifier.offset(y = (-2).dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PixelButtonLarge(
                        label = "CANCEL",
                        primary = false,
                        modifier = Modifier.weight(1f),
                        cutColor = colors.secondarySurface,
                        onClick = onDismiss
                    )
                    PixelButtonLarge(
                        label = "MOVE",
                        primary = true,
                        modifier = Modifier.weight(1f),
                        cutColor = colors.secondarySurface,
                        onClick = {
                            selectedProjectId?.let { onMove(it) }
                        }
                    )
                }
            }
        }
    }
}


@Composable
fun GlossaryWordListItem(
    word: GlossaryWord,
    onDelete: () -> Unit = {},
    onEdit: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    
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
                    // Word — minecraft font, bold, 2sp bigger than explanation
                    Text(
                        text = word.word,
                        style = minecraftStyle(14, colors.secondaryIcon).copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.offset(y = (-2).dp)
                    )
                    // Explanation — minecraft font, no max lines
                    Text(
                        text = word.explanation,
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
                                    // Edit option
                                    val editInteraction = remember { MutableInteractionSource() }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pixelRounded8Clickable(
                                                interactionSource = editInteraction,
                                                onClick = {
                                                    showMenu = false
                                                    onEdit()
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
                                                    .data("file:///android_asset/pixelarticons/pen-square.svg")
                                                    .build(),
                                                imageLoader = svgLoader,
                                                contentDescription = "Edit",
                                                colorFilter = ColorFilter.tint(colors.secondaryIcon),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                "Edit",
                                                style = pixelStyle(10, colors.secondaryIcon),
                                                modifier = Modifier.offset(y = (-2).dp)
                                            )
                                        }
                                    }
                                    
                                    // Move to option
                                    val moveInteraction = remember { MutableInteractionSource() }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pixelRounded8Clickable(
                                                interactionSource = moveInteraction,
                                                onClick = {
                                                    showMenu = false
                                                    showMoveDialog = true
                                                }
                                            )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            androidx.compose.foundation.Image(
                                                painter = painterResource(id = com.wizaird.app.R.drawable.ic_folder),
                                                contentDescription = "Move to",
                                                colorFilter = ColorFilter.tint(colors.secondaryIcon),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                "Move to",
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
                        }
                    }
                }
            }
        }
        
        // Delete confirmation dialog
        if (showDeleteDialog) {
            PixelConfirmationDialog(
                title = "DELETE WORD",
                message = "Are you sure you want to delete \"${word.word}\"? This action cannot be undone.",
                confirmLabel = "DELETE",
                cancelLabel = "CANCEL",
                isDestructive = true,
                onConfirm = {
                    showDeleteDialog = false
                    scope.launch {
                        deleteGlossaryWord(context, word.id)
                    }
                },
                onDismiss = {
                    showDeleteDialog = false
                }
            )
        }
        
        // Move to project dialog
        if (showMoveDialog) {
            MoveGlossaryWordDialog(
                currentProjectId = word.projectId,
                onMove = { targetProjectId ->
                    showMoveDialog = false
                    scope.launch {
                        com.wizaird.app.data.moveGlossaryWordToProject(context, word.id, targetProjectId)
                    }
                },
                onDismiss = {
                    showMoveDialog = false
                }
            )
        }
    }
}

@Composable
fun MoveGlossaryWordDialog(
    currentProjectId: String,
    onMove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    
    val projects by projectsFlow(context).collectAsState(initial = emptyList())
    val availableProjects = projects.filter { it.id != currentProjectId }
    
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        PixelBox(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(PixelRoundedShape),
            fillColor = colors.secondarySurface,
            borderColor = colors.border,
            cutColor = colors.secondarySurface,
            cornerStyle = PixelCornerStyle.Rounded
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "MOVE TO",
                    style = pixelStyle(12, colors.textHigh),
                    modifier = Modifier.offset(y = (-2).dp)
                )
                
                // Project list
                if (availableProjects.isEmpty()) {
                    Text(
                        text = "No other projects available",
                        style = minecraftStyle(14, colors.textLow).copy(
                            lineHeight = (14 * 1.6f).sp
                        ),
                        modifier = Modifier.offset(y = (-2).dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableProjects.forEach { project ->
                            val isSelected = selectedProjectId == project.id
                            val projectInteraction = remember { MutableInteractionSource() }
                            
                            PixelBox(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pixelRoundedClickable(
                                        interactionSource = projectInteraction,
                                        onClick = { selectedProjectId = project.id }
                                    ),
                                fillColor = if (isSelected) colors.userBubble else colors.background,
                                borderColor = if (isSelected) Coral else androidx.compose.ui.graphics.Color.Transparent,
                                cornerStyle = PixelCornerStyle.Rounded
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    androidx.compose.foundation.Image(
                                        painter = painterResource(id = com.wizaird.app.R.drawable.ic_folder),
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(colors.secondaryIcon),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = project.name.ifEmpty { "UNNAMED PROJECT" },
                                        style = pixelStyle(10, colors.secondaryIcon),
                                        modifier = Modifier.offset(y = (-2).dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PixelButtonLarge(
                        label = "CANCEL",
                        primary = false,
                        modifier = Modifier.weight(1f),
                        cutColor = colors.secondarySurface,
                        onClick = onDismiss
                    )
                    PixelButtonLarge(
                        label = "MOVE",
                        primary = true,
                        modifier = Modifier.weight(1f),
                        cutColor = colors.secondarySurface,
                        onClick = {
                            selectedProjectId?.let { onMove(it) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun GlossarySortBottomSheet(
    currentSortOrder: GlossarySortOrder,
    onSortSelected: (GlossarySortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    
    val svgLoader = remember {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }

    // Scrim background with darkened overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        // Bottom sheet with upper rounded pixel corners (R=10)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawBehind {
                    val p = PixelSize.toPx()
                    val w = size.width
                    val h = size.height
                    val fill = colors.secondaryButton

                    // Fill the sheet
                    drawRect(fill)

                    // Top-left corner cuts (R=10 pixel corners)
                    drawRect(Color.Transparent, Offset(0f, p*0), Size(p*7, p), blendMode = BlendMode.Clear)
                    drawRect(Color.Transparent, Offset(0f, p*1), Size(p*5, p), blendMode = BlendMode.Clear)
                    drawRect(Color.Transparent, Offset(0f, p*2), Size(p*3, p), blendMode = BlendMode.Clear)
                    drawRect(Color.Transparent, Offset(0f, p*3), Size(p*2, p), blendMode = BlendMode.Clear)
                    drawRect(Color.Transparent, Offset(0f, p*4), Size(p*2, p), blendMode = BlendMode.Clear)
                    drawRect(Color.Transparent, Offset(0f, p*5), Size(p*1, p), blendMode = BlendMode.Clear)
                    drawRect(Color.Transparent, Offset(0f, p*6), Size(p*1, p), blendMode = BlendMode.Clear)
                    
                    // Top-right corner cuts (R=10 pixel corners)
                    drawRect(Color.Transparent, Offset(w-p*7, p*0), Size(p*7, p), blendMode = BlendMode.Clear)
                    drawRect(Color.Transparent, Offset(w-p*5, p*1), Size(p*5, p), blendMode = BlendMode.Clear)
                    drawRect(Color.Transparent, Offset(w-p*3, p*2), Size(p*3, p), blendMode = BlendMode.Clear)
                    drawRect(Color.Transparent, Offset(w-p*2, p*3), Size(p*2, p), blendMode = BlendMode.Clear)
                    drawRect(Color.Transparent, Offset(w-p*2, p*4), Size(p*2, p), blendMode = BlendMode.Clear)
                    drawRect(Color.Transparent, Offset(w-p*1, p*5), Size(p*1, p), blendMode = BlendMode.Clear)
                    drawRect(Color.Transparent, Offset(w-p*1, p*6), Size(p*1, p), blendMode = BlendMode.Clear)
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // Prevent clicks from passing through to scrim
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Date added (desc) option
                val dateInteraction = remember { MutableInteractionSource() }
                val isDateSelected = currentSortOrder == GlossarySortOrder.DATE_DESC
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .pixelRoundedClickable(
                            interactionSource = dateInteraction,
                            onClick = { onSortSelected(GlossarySortOrder.DATE_DESC) }
                        ),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Date added (newest first)",
                            style = pixelStyle(14, colors.textLow),
                            modifier = Modifier.offset(y = (-2).dp)
                        )
                        if (isDateSelected) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data("file:///android_asset/pixelarticons/check.svg")
                                    .build(),
                                imageLoader = svgLoader,
                                contentDescription = "Selected",
                                colorFilter = ColorFilter.tint(Coral),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Alphabetical option
                val alphaInteraction = remember { MutableInteractionSource() }
                val isAlphaSelected = currentSortOrder == GlossarySortOrder.ALPHABETICAL
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .pixelRoundedClickable(
                            interactionSource = alphaInteraction,
                            onClick = { onSortSelected(GlossarySortOrder.ALPHABETICAL) }
                        ),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Alphabetical (A-Z)",
                            style = pixelStyle(14, colors.textLow),
                            modifier = Modifier.offset(y = (-2).dp)
                        )
                        if (isAlphaSelected) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data("file:///android_asset/pixelarticons/check.svg")
                                    .build(),
                                imageLoader = svgLoader,
                                contentDescription = "Selected",
                                colorFilter = ColorFilter.tint(Coral),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

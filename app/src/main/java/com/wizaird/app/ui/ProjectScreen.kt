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
import com.wizaird.app.data.Concept
import com.wizaird.app.data.ConceptFilter
import com.wizaird.app.data.ConceptSortOrder
import com.wizaird.app.data.chatsFlow
import com.wizaird.app.data.deleteInsight
import com.wizaird.app.data.deleteNote
import com.wizaird.app.data.deleteProject
import com.wizaird.app.data.deleteConcept
import com.wizaird.app.data.copyConceptToProject
import com.wizaird.app.data.setNoteConcepts
import com.wizaird.app.data.notesFlow
import com.wizaird.app.data.projectsFlow
import com.wizaird.app.data.storedInsightsFlow
import com.wizaird.app.data.conceptsFlow
import com.wizaird.app.ui.theme.*
import kotlinx.coroutines.launch

enum class ProjectTab { CHATS, CONCEPTS, INSIGHTS, NOTES }

// Helper function to strip HTML tags from rich text notes for preview display
private fun stripHtml(html: String): String {
    return try {
        android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_COMPACT).toString()
    } catch (e: Exception) {
        html // Fallback to original if parsing fails
    }
}

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
    onNewConceptClick: () -> Unit = {},
    onConceptClick: (String) -> Unit = {},      // open the concept page
    onEditConceptClick: (String) -> Unit = {},  // open the concept edit screen
    onNoteConceptClick: (String) -> Unit = onConceptClick, // tag pill on a note card
    onInsightConceptClick: (String) -> Unit = onConceptClick, // concept pill on an insight
    onChatConceptClick: (String) -> Unit = onConceptClick, // concept pill on a chat card
    onInsightNewConceptClick: () -> Unit = onNewConceptClick, // "Add to concepts" from an insight
    initialTab: ProjectTab = ProjectTab.CHATS
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val isCoverScreen = rememberIsCoverScreen()
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

    // Concepts — search, sort and ghost filter state
    var conceptSearchQuery by remember { mutableStateOf("") }
    var conceptSortOrder by remember { mutableStateOf(ConceptSortOrder.DATE_DESC) }
    var conceptFilter by remember { mutableStateOf(ConceptFilter.ALL) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showSortBottomSheet by remember { mutableStateOf(false) }
    
    // Concepts — live from DataStore with search, sort and filter
    val concepts by remember(projectId, conceptSearchQuery, conceptSortOrder, conceptFilter) {
        conceptsFlow(context, projectId, conceptSearchQuery, conceptSortOrder, conceptFilter)
    }.collectAsState(initial = emptyList())

    // All concepts in the project, for the tag pills on note cards
    val allConcepts by remember(projectId) { conceptsFlow(context, projectId) }.collectAsState(initial = emptyList())
    val conceptsById = remember(allConcepts) { allConcepts.associateBy { it.id } }
    // Concepts linked to each chat, for the pills on chat cards
    // (Not shown on chat cards for now — links are managed in chat settings)
    val conceptsByChat = remember(allConcepts) {
        allConcepts
            .flatMap { c -> c.sources.mapNotNull { it.chatId }.distinct().map { it to c } }
            .groupBy({ it.first }, { it.second })
    }
    // Concepts added from each insight, for the pills on insight cards
    val conceptsByInsight = remember(allConcepts) {
        allConcepts
            .flatMap { c -> c.sources.mapNotNull { it.insightId }.distinct().map { it to c } }
            .groupBy({ it.first }, { it.second })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Status bar space - not needed on cover screen
            if (!isCoverScreen) {
                Spacer(modifier = Modifier.height(48.dp))
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

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
                        style = pixelStyle(14, colors.secondaryIcon).copy(lineHeight = 20.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
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
                    ProjectTab.CONCEPTS to "Concepts",
                    ProjectTab.CHATS to "Chats",
                    ProjectTab.INSIGHTS to "Insights",
                    ProjectTab.NOTES to "Notes"
                ).forEachIndexed { _, (tab, label) ->
                    val isActive = activeTab == tab
                    val tabInteraction = remember { MutableInteractionSource() }
                    val iconColor = if (isActive) colors.textHigh else colors.textLow
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(IntrinsicSize.Max)
                    ) {
                        // Scroll to start when Concepts (first tab) is active, scroll to end when Notes (last tab) is active
                        LaunchedEffect(isActive) {
                            if (isActive) {
                                when (tab) {
                                    ProjectTab.CONCEPTS -> tabScrollState.animateScrollTo(0)
                                    ProjectTab.NOTES -> tabScrollState.animateScrollTo(tabScrollState.maxValue)
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
                                    ProjectTab.CONCEPTS -> {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data("file:///android_asset/pixelarticons/book-open.svg")
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
                                    onClick = { onChatClick(chat.id) },
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
                                    onClick = { onNoteClick(note.id) },
                                    conceptsById = conceptsById,
                                    onConceptClick = onNoteConceptClick
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
                                    onNewConceptClick = onInsightNewConceptClick,
                                    concepts = conceptsByInsight[insight.id] ?: emptyList(),
                                    onConceptClick = onInsightConceptClick
                                )
                            }
                        }
                    }
                }
                ProjectTab.CONCEPTS -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // Search, ghost filter and sort — one row, all the same height
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Search bar
                            PixelBox(
                                modifier = Modifier.weight(1f),
                                fillColor = colors.tertiarySurface,
                                borderColor = androidx.compose.ui.graphics.Color.Transparent,
                                cornerStyle = PixelCornerStyle.Rounded8
                            ) {
                                androidx.compose.foundation.text.BasicTextField(
                                    value = conceptSearchQuery,
                                    onValueChange = { conceptSearchQuery = it },
                                    textStyle = minecraftStyle(12, colors.textHigh),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    decorationBox = { innerTextField ->
                                        if (conceptSearchQuery.isEmpty()) {
                                            Text(
                                                "Search",
                                                style = minecraftStyle(12, colors.textXLow)
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                            }

                            // Ghost filter — opens a bottom sheet like sort
                            Box(modifier = Modifier.fillMaxHeight()) {
                                val filterInteraction = remember { MutableInteractionSource() }
                                PixelBox(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .pixelRounded8Clickable(interactionSource = filterInteraction) {
                                            showFilterMenu = true
                                        },
                                    fillColor = colors.secondaryButton,
                                    borderColor = androidx.compose.ui.graphics.Color.Transparent,
                                    cornerStyle = PixelCornerStyle.Rounded8
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            if (conceptFilter == ConceptFilter.HIDE_GHOSTS) "Hide ghosts" else "All",
                                            style = minecraftStyle(12, colors.secondaryIcon)
                                        )
                                        androidx.compose.foundation.Image(
                                            painter = painterResource(id = com.wizaird.app.R.drawable.ic_chevron_down),
                                            contentDescription = "Filter",
                                            colorFilter = ColorFilter.tint(colors.secondaryIcon),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }

                            // Sort button
                            val sortInteraction = remember { MutableInteractionSource() }
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(36.dp)
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
                        
                        // Concept list or empty state
                        if (concepts.isEmpty()) {
                            if (conceptSearchQuery.isEmpty() && conceptFilter == ConceptFilter.ALL) {
                                // No concepts yet - centered
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
                                            "NO CONCEPTS YET",
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
                                                    .data("file:///android_asset/pixelarticons/book-open.svg")
                                                    .build(),
                                                imageLoader = svgLoader,
                                                contentDescription = null,
                                                colorFilter = ColorFilter.tint(colors.secondaryIconSoft),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                "TO ADD A CONCEPT",
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
                                items(concepts, key = { it.id }) { concept ->
                                    ConceptListItem(
                                        concept = concept,
                                        onClick = { onConceptClick(concept.id) },
                                        onEdit = { onEditConceptClick(concept.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // FAB — switches based on active tab (only show for CHATS, NOTES, and CONCEPTS)
        if (activeTab == ProjectTab.CHATS || activeTab == ProjectTab.NOTES || activeTab == ProjectTab.CONCEPTS) {
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
                            ProjectTab.CONCEPTS -> onNewConceptClick()
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
                        ProjectTab.CONCEPTS -> {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data("file:///android_asset/pixelarticons/book-open.svg")
                                    .build(),
                                imageLoader = svgLoader,
                                contentDescription = "New Concept",
                                colorFilter = ColorFilter.tint(colors.secondaryIcon),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        else -> {}
                    }
                }
            }
        }

        // Sort bottom sheet for concepts
        if (showSortBottomSheet) {
            val sortOptions = listOf(
                ConceptSortOrder.DATE_DESC to "Date added (newest first)",
                ConceptSortOrder.ALPHABETICAL to "Alphabetical (A-Z)"
            )
            PixelOptionsBottomSheet(
                options = sortOptions.map { it.second },
                selectedIndex = sortOptions.indexOfFirst { it.first == conceptSortOrder },
                onSelect = { index ->
                    conceptSortOrder = sortOptions[index].first
                    showSortBottomSheet = false
                },
                onDismiss = { showSortBottomSheet = false }
            )
        }

        // Ghost filter bottom sheet for concepts
        if (showFilterMenu) {
            val filterOptions = listOf(
                ConceptFilter.ALL to "All",
                ConceptFilter.HIDE_GHOSTS to "Hide ghosts"
            )
            PixelOptionsBottomSheet(
                options = filterOptions.map { it.second },
                selectedIndex = filterOptions.indexOfFirst { it.first == conceptFilter },
                onSelect = { index ->
                    conceptFilter = filterOptions[index].first
                    showFilterMenu = false
                },
                onDismiss = { showFilterMenu = false }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatListItem(
    chat: com.wizaird.app.data.ChatData,
    onClick: () -> Unit = {},
    concepts: List<Concept> = emptyList(),  // concepts linked to this chat
    onConceptClick: (String) -> Unit = {}
) {
    val colors = LocalWizairdColors.current
    val interaction = remember { MutableInteractionSource() }
    PixelBox(
        modifier = Modifier
            .fillMaxWidth()
            .pixelRoundedClickable(interactionSource = interaction, onClick = onClick),
        fillColor = colors.secondarySurface,
        borderColor = Color.Transparent,
        cornerStyle = PixelCornerStyle.Rounded
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Title in the body font (concept cards use the caps pixel font)
            Text(
                text = chat.title,
                style = minecraftStyle(14, colors.secondaryIcon).copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteListItem(
    note: NoteData,
    onClick: () -> Unit = {},
    onDelete: () -> Unit = {},
    conceptsById: Map<String, Concept> = emptyMap(),
    onConceptClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }

    // Tagged concepts that still exist
    val taggedConcepts = note.conceptIds.mapNotNull { conceptsById[it] }
    
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
                    .padding(start = 14.dp, top = 10.dp, end = 8.dp, bottom = 10.dp)
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
                        text = stripHtml(note.body),
                        style = minecraftStyle(12, colors.secondaryIcon),
                        maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.offset(y = (-2).dp)
                    )
                    // Concept tags — tap a pill to open the concept
                    if (taggedConcepts.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            taggedConcepts.forEach { concept ->
                                ConceptPill(
                                    label = concept.name,
                                    ghost = concept.isGhost,
                                    onClick = { onConceptClick(concept.id) }
                                )
                            }
                        }
                    }
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
                        // Tag concepts option
                        PixelOptionsMenuItem("Tag concepts", iconAsset = "bookmark.svg", onClick = {
                            showMenu = false
                            showTagDialog = true
                        })

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
        
        // Tag concepts dialog
        if (showTagDialog) {
            TagConceptsDialog(
                projectId = note.projectId,
                initialSelection = note.conceptIds,
                onSave = { ids ->
                    showTagDialog = false
                    scope.launch { setNoteConcepts(context, note.id, ids) }
                },
                onDismiss = { showTagDialog = false }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InsightListItem(
    insight: StoredInsight,
    onChatClick: () -> Unit = {},
    onNewConceptClick: () -> Unit = {},
    concepts: List<Concept> = emptyList(),  // concepts added from this insight
    onConceptClick: (String) -> Unit = {}
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
                    .padding(start = 14.dp, top = 10.dp, end = 8.dp, bottom = 10.dp)
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
                    // Concepts added from this insight
                    if (concepts.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            concepts.forEach { concept ->
                                ConceptPill(
                                    label = concept.name,
                                    ghost = concept.isGhost,
                                    onClick = { onConceptClick(concept.id) }
                                )
                            }
                        }
                    }
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
                onNewConceptClick = onNewConceptClick,
                concepts = concepts,
                onConceptClick = onConceptClick
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InsightPreviewOverlay(
    insight: StoredInsight,
    onDismiss: () -> Unit,
    onChatClick: () -> Unit = {},
    onNewConceptClick: () -> Unit = {},
    concepts: List<Concept> = emptyList(),  // concepts added from this insight
    onConceptClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    
    var copied by remember { mutableStateOf(false) }
    var noteSaved by remember { mutableStateOf(false) }
    var showThinking by remember { mutableStateOf(false) }
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
                                onAddToConcepts = { selectedText ->
                                    ConceptNavigationData.setPendingData(
                                        name = selectedText,
                                        contextText = insight.text,
                                        source = insight.id?.let { com.wizaird.app.data.ConceptSource(type = com.wizaird.app.data.ConceptSourceType.INSIGHT, insightId = it, snippet = com.wizaird.app.data.buildSnippet(insight.text, selectedText)) }
                                    )
                                    onDismiss()
                                    onNewConceptClick()
                                }
                            )
                        }
                        // Concepts added from this insight — tap to open the concept
                        if (concepts.isNotEmpty()) {
                            FlowRow(
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                concepts.forEach { concept ->
                                    ConceptPill(
                                        label = concept.name,
                                        ghost = concept.isGhost,
                                        onClick = {
                                            onDismiss()
                                            onConceptClick(concept.id)
                                        }
                                    )
                                }
                            }
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

                        // Thinking icon — only interactive when thinking content exists
                        val hasThinking = insight.thinkingContent != null
                        val thinkingInteraction = remember { MutableInteractionSource() }
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("file:///android_asset/pixelarticons/ai-app-mac.svg")
                                .build(),
                            imageLoader = svgLoader,
                            contentDescription = if (hasThinking) "View AI reasoning" else "No thought process captured",
                            colorFilter = ColorFilter.tint(
                                if (hasThinking) colors.textXLow
                                else colors.textXLow.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .size(20.dp)
                                .then(
                                    if (hasThinking) Modifier.pixelRounded8ClickableOversize(
                                        interactionSource = thinkingInteraction
                                    ) { showThinking = true }
                                    else Modifier
                                )
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

    // Thinking overlay
    if (showThinking) {
        ThinkingOverlay(
            thinkingContent = insight.thinkingContent ?: "No thought process was captured for this insight.",
            onDismiss = { showThinking = false }
        )
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
fun ConceptListItem(
    concept: Concept,
    onClick: () -> Unit = {},
    onEdit: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCopyDialog by remember { mutableStateOf(false) }

    // Ghost concepts use the muted text color
    val nameColor = if (concept.isGhost) colors.textXLow else colors.secondaryIcon
    val textColor = if (concept.isGhost) colors.textXLow else colors.secondaryIcon
    
    Box {
        PixelBox(
            modifier = Modifier
                .fillMaxWidth()
                .pixelRoundedClickable(interactionSource = interaction, onClick = onClick),
            // Full concepts: filled card with a soft stroke. Ghosts: the fill at 35%, no stroke.
            fillColor = if (concept.isGhost) colors.secondarySurface.copy(alpha = 0.35f) else colors.secondarySurface,
            borderColor = if (concept.isGhost) androidx.compose.ui.graphics.Color.Transparent else colors.textLow,
            cornerStyle = PixelCornerStyle.Rounded
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 10.dp, end = 8.dp, bottom = 10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 32.dp), // Leave space for the options button
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Name — caps pixel font (chat cards use the body font)
                    Text(
                        text = concept.name,
                        style = pixelStyle(12, nameColor).copy(lineHeight = (12 * 1.6f).sp),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.offset(y = (-2).dp)
                    )
                    // Preview — the brief description, max 3 lines
                    val summary = concept.intro
                    Text(
                        text = summary.ifBlank { "No description or article" },
                        style = minecraftStyle(12, when {
                            concept.isGhost -> colors.textXLow
                            summary.isBlank() -> colors.secondaryIconSoft
                            else -> textColor
                        }),
                        maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.offset(y = (-2).dp)
                    )
                }
                
                // More options button - offset so icon sits at top-right edge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 7.dp, y = (-7).dp) // Offset to position icon at edge
                ) {
                    MoreOptionsButton(onClick = { showMenu = true })
                    if (showMenu) {
                        PixelOptionsMenu(fillColor = colors.secondarySurface, onDismiss = { showMenu = false }) {
                            PixelOptionsMenuItem("Edit", iconAsset = "pen-square.svg", onClick = {
                                showMenu = false
                                onEdit()
                            })
                            PixelOptionsMenuItem("Copy to", iconAsset = "copy.svg", onClick = {
                                showMenu = false
                                showCopyDialog = true
                            })
                            PixelOptionsMenuItem("Delete", iconAsset = "delete.svg", tint = colors.coral, onClick = {
                                showMenu = false
                                showDeleteDialog = true
                            })
                        }
                    }
                }
            }
        }
        
        // Delete confirmation dialog
        if (showDeleteDialog) {
            PixelConfirmationDialog(
                title = "DELETE CONCEPT",
                message = "Are you sure you want to delete \"${concept.name}\"? It will also be removed from tagged notes. This action cannot be undone.",
                confirmLabel = "DELETE",
                cancelLabel = "CANCEL",
                isDestructive = true,
                onConfirm = {
                    showDeleteDialog = false
                    scope.launch {
                        deleteConcept(context, concept.id)
                    }
                },
                onDismiss = {
                    showDeleteDialog = false
                }
            )
        }
        
        // Copy to project dialog
        if (showCopyDialog) {
            CopyConceptDialog(
                currentProjectId = concept.projectId,
                onCopy = { targetProjectId ->
                    showCopyDialog = false
                    scope.launch {
                        copyConceptToProject(context, concept.id, targetProjectId)
                    }
                },
                onDismiss = {
                    showCopyDialog = false
                }
            )
        }
    }
}

/** Bottom sheet frame: dark scrim + sheet with rounded pixel top corners. Back or tapping the scrim closes it. */
@Composable
fun PixelBottomSheet(
    onDismiss: () -> Unit,
    fillColor: Color? = null,  // null = secondaryButton (sort/filter sheets)
    content: @Composable ColumnScope.() -> Unit
) {
    // System Back closes the sheet instead of leaving the screen
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    val colors = LocalWizairdColors.current

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
                    val fill = fillColor ?: colors.secondaryButton

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
                content()
            }
        }
    }
}

/** Bottom sheet with a single-choice list (selected row gets a check). Used for concept sort and filter. */
@Composable
fun PixelOptionsBottomSheet(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val svgLoader = remember {
        ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
    }
    PixelBottomSheet(onDismiss = onDismiss) {
        options.forEachIndexed { index, label ->
            val optionInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .pixelRoundedClickable(
                        interactionSource = optionInteraction,
                        onClick = { onSelect(index) }
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
                        text = label,
                        style = pixelStyle(14, colors.textLow),
                        modifier = Modifier.offset(y = (-2).dp)
                    )
                    if (index == selectedIndex) {
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

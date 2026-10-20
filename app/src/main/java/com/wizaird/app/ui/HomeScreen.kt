package com.wizaird.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.wizaird.app.data.AiSettings
import com.wizaird.app.data.ChatData
import com.wizaird.app.data.ChatMessage
import com.wizaird.app.data.MessageSender
import com.wizaird.app.data.Project
import com.wizaird.app.data.askAi
import com.wizaird.app.data.projectsFlow
import com.wizaird.app.data.settingsFlow
import com.wizaird.app.data.shouldGenerateNewInsight
import com.wizaird.app.data.InsightGeneration
import com.wizaird.app.data.InsightResult
import com.wizaird.app.data.upsertProject
import com.wizaird.app.data.upsertChat
import com.wizaird.app.data.storedInsightsFlow
import com.wizaird.app.data.storedInsightFlow
import com.wizaird.app.data.setInsightPinned
import com.wizaird.app.data.conceptsFlow
import kotlinx.coroutines.flow.map
import com.wizaird.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun HomeScreen(
    onSettingsClick: () -> Unit,
    onProjectsClick: () -> Unit = {},
    onNewProjectClick: () -> Unit = {},
    onProjectClick: (String) -> Unit = {},
    onChatCreated: (projectId: String, chatId: String) -> Unit = { _, _ -> },
    onNewConceptClick: (String) -> Unit = {},
    onConceptClick: (projectId: String, conceptId: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by settingsFlow(context).collectAsState(initial = AiSettings())
    val colors = LocalWizairdColors.current
    val isCoverScreen = rememberIsCoverScreen()
    val projects by projectsFlow(context).collectAsState(initial = emptyList())
    var activeProjectIndex by rememberSaveable { mutableIntStateOf(0) }
    val activeProject = projects.getOrNull(activeProjectIndex)

    var bubbleText by remember { mutableStateOf("") }
    var bubbleThinking by remember { mutableStateOf<String?>(null) }
    var inputText by remember { mutableStateOf("") }
    
    // Insights generate in the background (InsightGeneration), so switching projects doesn't cancel them.
    // The bubble only shows the active project's state.
    val generatingIds by InsightGeneration.generating.collectAsState()
    val isLoading = activeProject?.id?.let { it in generatingIds } ?: false
    val currentProjectId by rememberUpdatedState(activeProject?.id)

    // Type-out of a freshly finished insight; cancelled when the user switches projects
    var typingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // The bubble is a pager over every insight stored for the project (oldest first),
    // with the live bubble (loader / type-out / latest insight) as the last page.
    val history by remember(activeProject?.id) {
        activeProject?.id?.let { id -> storedInsightsFlow(context, id).map { it.reversed() } }
            ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    // While the latest stored insight is shown (or typing out) it's the live page, so it isn't listed twice
    val liveIsLatest = bubbleText.isNotEmpty() && history.lastOrNull()?.text?.startsWith(bubbleText) == true
    val olderInsights = if (liveIsLatest) history.dropLast(1) else history
    val pageCount = olderInsights.size + 1
    // Looping: the pager has a copy of the last card before the first and of the first after the last.
    // Landing on a copy snaps to the real card, so swiping past either end wraps around.
    val looping = pageCount > 1
    val virtualCount = if (looping) pageCount + 2 else 1
    fun realOf(virtual: Int) = if (looping) (virtual - 1 + pageCount) % pageCount else 0
    fun virtualOf(real: Int) = if (looping) real + 1 else 0
    fun pageKey(virtual: Int): Any {
        val id = olderInsights.getOrNull(realOf(virtual))?.id ?: "live"
        return when {
            looping && virtual == 0 -> "head-$id"
            looping && virtual == virtualCount - 1 -> "tail-$id"
            else -> id
        }
    }
    val pagerState = key(activeProject?.id) {
        rememberPagerState(initialPage = virtualOf(pageCount - 1)) { virtualCount }
    }
    // Only link to the latest insight when the live bubble is fully showing it
    val liveInsightId = history.lastOrNull()?.takeIf { it.text == bubbleText }?.id

    // Follow the live page as pages come and go (new insight, regenerate) unless the user swiped back
    var stickToLatest by remember(activeProject?.id) { mutableStateOf(true) }
    var jumpToLatest by remember { mutableIntStateOf(0) }
    LaunchedEffect(pagerState, pagerState.settledPage) {
        val settled = pagerState.settledPage
        stickToLatest = realOf(settled) == pageCount - 1
        // Settled on an end copy: jump to the real card it mirrors
        if (looping && (settled == 0 || settled == virtualCount - 1)) {
            pagerState.scrollToPage(virtualOf(realOf(settled)))
        }
    }
    LaunchedEffect(pagerState, pageCount, jumpToLatest) {
        if (stickToLatest) pagerState.scrollToPage(virtualOf(pageCount - 1))
    }

    // The page the user is on, for the input bar
    val currentStored = olderInsights.getOrNull(realOf(pagerState.currentPage))
    val displayedText = currentStored?.text ?: bubbleText
    val displayedLoading = isLoading && currentStored == null
    val displayedInsightId = currentStored?.id ?: liveInsightId

    fun generateInsightForProject(project: Project) {
        stickToLatest = true
        jumpToLatest++
        typingJob?.cancel()
        bubbleText = ""
        InsightGeneration.start(context, project)
    }

    // Show the result when the active project's insight finishes
    LaunchedEffect(Unit) {
        InsightGeneration.finished.collect { (projectId, result) ->
            if (projectId != currentProjectId) return@collect
            typingJob?.cancel()
            when (result) {
                is InsightResult.Success -> {
                    bubbleThinking = result.thinkingContent
                    typingJob = scope.launch {
                        val insight = result.insight
                        for (i in insight.indices) {
                            bubbleText = insight.substring(0, i + 1)
                            delay(4)
                        }
                    }
                }
                is InsightResult.Error -> bubbleText = "My scroll is torn! ${result.message}"
            }
        }
    }

    // Auto-generate insight when active project changes or on first load
    LaunchedEffect(activeProject?.id, projects.size) {
        // Reset state immediately so stale content from the previous project
        // never bleeds through while the new one loads.
        typingJob?.cancel()
        bubbleText = ""
        bubbleThinking = null

        if (projects.isEmpty()) {
            // No projects yet
            bubbleText = "Create a project to start your learning journey!"
        } else {
            activeProject?.let { project ->
                val running = project.id in InsightGeneration.generating.value
                if (running) {
                    // Still generating in the background; the bubble shows the loader
                } else if (shouldGenerateNewInsight(project)) {
                    generateInsightForProject(project)
                } else {
                    // Show existing insight
                    bubbleText = project.lastInsightText.ifBlank {
                        "Welcome back! Let me think of something new to teach you..."
                    }
                    // Load thinking content for the current insight if available
                    val lastInsightId = project.insightHistory.lastOrNull()?.id
                    if (lastInsightId != null) {
                        bubbleThinking = com.wizaird.app.data.storedInsightsFlow(context, project.id)
                            .first()
                            .firstOrNull { it.id == lastInsightId }
                            ?.thinkingContent
                    }
                }
            }
        }
    }

    val bobY = 0f

    var showToast by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf("") }
    var isToastLoading by remember { mutableStateOf(false) }

    // Auto-hide toast after 2 seconds (only when not loading)
    LaunchedEffect(showToast, isToastLoading) {
        if (showToast && !isToastLoading) {
            delay(2000)
            showToast = false
        }
    }

    // One bubble "card" per insight; swiping slides the next card in to replace it
    val insightPage: @Composable (Int) -> Unit = { virtualPage ->
        val page = realOf(virtualPage)
        val stored = olderInsights.getOrNull(page)
        val pageLoading = stored == null && isLoading
        ChatBubble(
            text = stored?.text ?: bubbleText,
            loading = pageLoading,
            thinkingContent = if (stored == null) bubbleThinking else stored.thinkingContent,
            projectName = activeProject?.name,
            projectId = activeProject?.id,
            insightId = stored?.id ?: liveInsightId,
            positionLabel = if (pageCount > 1 && !pageLoading) "${page + 1}/$pageCount" else null,
            onRegenerate = {
                activeProject?.let { project ->
                    println("User triggered manual regeneration")
                    generateInsightForProject(project)
                }
            },
            onProjectClick = {
                activeProject?.let { project -> onProjectClick(project.id) }
            },
            onShowToast = { message ->
                if (message.isEmpty()) {
                    showToast = false
                    isToastLoading = false
                } else {
                    toastMessage = message
                    showToast = true
                    isToastLoading = message == "ADDING TO CONCEPTS"
                }
            },
            onNewConceptClick = { projectIdFromBubble ->
                onNewConceptClick(projectIdFromBubble)
            },
            onConceptClick = { conceptId ->
                activeProject?.let { project -> onConceptClick(project.id, conceptId) }
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        if (isCoverScreen) {
            // Cover screen layout: content doesn't move, keyboard overlays
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp)
            ) {
                AppHeader(onSettingsClick = onSettingsClick, onProjectsClick = onProjectsClick)
                AgentScrollBar(
                    onNewProjectClick = onNewProjectClick,
                    onProjectClick = onProjectClick,
                    activeProjectIndex = activeProjectIndex,
                    onActiveProjectChange = { activeProjectIndex = it }
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Bubble with extra 8dp height extending into wizard space
                    // On cover screen, make it scrollable and add bottom padding for input bar
                    HorizontalPager(
                        state = pagerState,
                        key = { page -> pageKey(page) },
                        // Cards span the screen edge to edge so the incoming one slides in from off-screen
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        pageSpacing = 12.dp,
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            // Add bottom padding to account for overlaying input bar
                            .padding(bottom = 84.dp)
                            .layout { measurable, constraints ->
                                // Measure 24dp wider (undoing the column's side padding) and 8dp taller
                                val extraW = 24.dp.roundToPx()
                                val placeable = measurable.measure(
                                    constraints.copy(
                                        minWidth = constraints.minWidth + extraW,
                                        maxWidth = constraints.maxWidth + extraW,
                                        maxHeight = constraints.maxHeight + 8.dp.roundToPx()
                                    )
                                )
                                // Layout with original size so it extends sideways and downward
                                layout(constraints.maxWidth, constraints.maxHeight) {
                                    placeable.place(-extraW / 2, 0)
                                }
                            }
                    ) { page -> insightPage(page) }
                }
            }
            
            // Input bar positioned at bottom with imePadding - moves up with keyboard
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(colors.background)
                    .imePadding()
            ) {
                PixelInputBar(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = if (!displayedLoading && displayedText.isNotEmpty()) 
                        "Ask the wizaird about the insight" 
                        else 
                        "Ask the wizaird...",
                    onSubmit = {
                        val q = inputText.trim()
                        if (q.isNotEmpty() && activeProject != null) {
                            inputText = ""
                            
                            scope.launch {
                                // Check if there's an insight to include
                                val hasInsight = !displayedLoading && displayedText.isNotEmpty()
                                val currentInsightId = displayedInsightId
                                
                                // Build messages list
                                val messages = mutableListOf<ChatMessage>()
                                
                                // If there's an insight, add it as first AI message
                                if (hasInsight) {
                                    messages.add(ChatMessage(
                                        sender = MessageSender.AI,
                                        text = displayedText
                                    ))
                                }
                                
                                // Add user message
                                messages.add(ChatMessage(
                                    sender = MessageSender.USER,
                                    text = q
                                ))
                                
                                // Create chat (title will be generated after AI responds)
                                val chat = ChatData(
                                    projectId = activeProject.id,
                                    title = "Generated Title",
                                    messages = messages,
                                    insightId = if (hasInsight) currentInsightId else null
                                )
                                
                                // Save and navigate
                                upsertChat(context, chat)
                                onChatCreated(activeProject.id, chat.id)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                )
                Spacer(modifier = Modifier.height(20.dp).navigationBarsPadding())
            }
        } else {
            // Full screen layout: normal behavior with imePadding on whole column
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                Spacer(modifier = Modifier.height(48.dp))
                AppHeader(onSettingsClick = onSettingsClick, onProjectsClick = onProjectsClick)
                // StatStrip() // ── commented out; re-enable to show HP/XP bars ──
                AgentScrollBar(
                    onNewProjectClick = onNewProjectClick,
                    onProjectClick = onProjectClick,
                    activeProjectIndex = activeProjectIndex,
                    onActiveProjectChange = { activeProjectIndex = it }
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Bubble with extra 8dp height extending into wizard space
                    HorizontalPager(
                        state = pagerState,
                        key = { page -> pageKey(page) },
                        // Cards span the screen edge to edge so the incoming one slides in from off-screen
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        pageSpacing = 12.dp,
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .layout { measurable, constraints ->
                                // Measure 24dp wider (undoing the column's side padding) and 8dp taller
                                val extraW = 24.dp.roundToPx()
                                val placeable = measurable.measure(
                                    constraints.copy(
                                        minWidth = constraints.minWidth + extraW,
                                        maxWidth = constraints.maxWidth + extraW,
                                        maxHeight = constraints.maxHeight + 8.dp.roundToPx()
                                    )
                                )
                                // Layout with original size so it extends sideways and downward
                                layout(constraints.maxWidth, constraints.maxHeight) {
                                    placeable.place(-extraW / 2, 0)
                                }
                            }
                    ) { page -> insightPage(page) }

                    // Wizard overlaps the bubble from below
                    WizardCharacter(
                        bobOffsetY = bobY,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    PixelInputBar(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = if (!displayedLoading && displayedText.isNotEmpty()) 
                            "Ask the wizaird about the insight" 
                            else 
                            "Ask the wizaird...",
                        onSubmit = {
                            val q = inputText.trim()
                            if (q.isNotEmpty() && activeProject != null) {
                                inputText = ""
                                
                                scope.launch {
                                    // Check if there's an insight to include
                                    val hasInsight = !displayedLoading && displayedText.isNotEmpty()
                                    val currentInsightId = displayedInsightId
                                    
                                    // Build messages list
                                    val messages = mutableListOf<ChatMessage>()
                                    
                                    // If there's an insight, add it as first AI message
                                    if (hasInsight) {
                                        messages.add(ChatMessage(
                                            sender = MessageSender.AI,
                                            text = displayedText
                                        ))
                                    }
                                    
                                    // Add user message
                                    messages.add(ChatMessage(
                                        sender = MessageSender.USER,
                                        text = q
                                    ))
                                    
                                    // Create chat (title will be generated after AI responds)
                                    val chat = ChatData(
                                        projectId = activeProject.id,
                                        title = "Generated Title",
                                        messages = messages,
                                        insightId = if (hasInsight) currentInsightId else null
                                    )
                                    
                                    // Save and navigate
                                    upsertChat(context, chat)
                                    onChatCreated(activeProject.id, chat.id)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp).navigationBarsPadding())
                }
            }
        }
        
        // Toast overlay
        PixelToast(
            message = toastMessage,
            visible = showToast,
            modifier = Modifier.align(Alignment.BottomCenter),
            isLoading = isToastLoading
        )
    }
}

// ── Status bar ───────────────────────────────────────────────────
@Composable
fun PixelStatusBar() {
    val colors = LocalWizairdColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(colors.background)
            .drawBehind { drawPixelBorder(bottom = true, color = colors.border) }
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("9:30", style = pixelStyle(8, colors.secondaryIcon), modifier = Modifier.offset(y = (-2).dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // signal bars
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                listOf(4.dp, 6.dp, 8.dp, 10.dp).forEach { h ->
                    Box(modifier = Modifier.width(2.dp).height(h).background(colors.secondaryIcon))
                }
            }
            // battery
            Box(
                modifier = Modifier
                    .width(16.dp).height(8.dp)
                    .border(2.dp, colors.border)
                    .padding(1.dp)
            ) {
                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(0.7f).background(colors.forest))
            }
        }
    }
}

// ── App header ───────────────────────────────────────────────────
@Composable
fun AppHeader(onSettingsClick: () -> Unit, onProjectsClick: () -> Unit = {}) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val gifLoader = remember {
        ImageLoader.Builder(context)
            .components { add(GifDecoder.Factory()) }
            .build()
    }
    /*
    PixelBox(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 0.dp),
        fillColor = colors.secondarySurface,
        cornerStyle = PixelCornerStyle.Rounded
    ) {
    */
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Rabbit GIF replacing the W badge
            /*
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data("file:///android_asset/rabbit right.gif")
                        .build(),
                    imageLoader = gifLoader,
                    contentDescription = "Rabbit",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            */
            Column(
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Text("RACHEL", style = pixelStyle(16, colors.secondaryIcon), modifier = Modifier.offset(y = (-2).dp))
                Text("LV.3 APPRENTICE", style = pixelStyle(8, colors.secondaryIconSoft), modifier = Modifier.offset(y = (-2).dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Folder icon in 40dp pixel circle
            PixelCircleIconButton(
                iconRes = com.wizaird.app.R.drawable.ic_folder,
                contentDescription = "Projects",
                fillColor = colors.secondaryButton,
                onClick = { onProjectsClick() }
            )
            // Settings icon in 40dp pixel circle
            PixelCircleIconButton(
                iconRes = com.wizaird.app.R.drawable.ic_settings_cog,
                contentDescription = "Settings",
                fillColor = colors.secondaryButton,
                onClick = { onSettingsClick() }
            )
        }
    }
    // } // end PixelBox
}

// ── Agent scroll bar ─────────────────────────────────────────────
// Horizontally scrollable row of pixel rounded-square agent avatars.
// Index 0 is always the "+ NEW" button; indices 1..n map to real projects.
@Composable
fun AgentScrollBar(
    onNewProjectClick: () -> Unit = {},
    onProjectClick: (String) -> Unit = {},
    activeProjectIndex: Int = 0,
    onActiveProjectChange: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val isCoverScreen = rememberIsCoverScreen()
    val projects by projectsFlow(context).collectAsState(initial = emptyList())

    // Build a combined list: null = add button, non-null = project
    val items: List<Project?> = listOf(null) + projects

    // activeIndex in items = activeProjectIndex + 1 (offset by the add button at 0)
    val activeItemIndex = activeProjectIndex + 1

    val inactiveBorder = if (colors.isDark) colors.border else Color(0xFF999999)
    
    // Use smaller circles on cover screen to match header buttons
    val circleSize = if (isCoverScreen) 40.dp else 64.dp
    val cornerStyle = if (isCoverScreen) PixelCornerStyle.Rounded else PixelCornerStyle.Circle
    val clipShape = if (isCoverScreen) PixelCircleButtonShape else PixelLargeCircleShape
    val fontSize = if (isCoverScreen) 14 else 20

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, project ->
            val isAddButton = project == null
            val isActive = index == activeItemIndex
            val interaction = remember { MutableInteractionSource() }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PixelBox(
                    modifier = Modifier
                        .size(circleSize)
                        .then(
                            if (isCoverScreen) {
                                // Use pixelCircleClickable for 40dp circles
                                if (isAddButton)
                                    Modifier.pixelCircleClickable(interactionSource = interaction) { onNewProjectClick() }
                                else
                                    Modifier.pixelCircleClickable(interactionSource = interaction) { onActiveProjectChange(index - 1) }
                            } else {
                                // Use pixelLargeCircleClickable for 64dp circles
                                if (isAddButton)
                                    Modifier.pixelLargeCircleClickable(interactionSource = interaction) { onNewProjectClick() }
                                else
                                    Modifier.pixelLargeCircleClickable(interactionSource = interaction) { onActiveProjectChange(index - 1) }
                            }
                        ),
                    fillColor = if (isAddButton) colors.secondaryButton else if (!isAddButton && project != null && project.picturePath.isNotEmpty() && File(project.picturePath).exists()) Color.Transparent else colors.secondarySurface,
                    borderColor = if (isActive) colors.textHigh else if (isAddButton) Color.Transparent else inactiveBorder,
                    cornerStyle = cornerStyle
                ) {
                    if (isAddButton) {
                        // Pixel plus icon - scale down on cover screen
                        val iconSize = if (isCoverScreen) 10.dp else 14.dp
                        val iconThickness = if (isCoverScreen) 1.5.dp else 2.dp
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(modifier = Modifier.width(iconSize).height(iconThickness).background(colors.secondaryIcon))
                            Box(modifier = Modifier.width(iconThickness).height(iconSize).background(colors.secondaryIcon))
                        }
                    } else if (project!!.picturePath.isNotEmpty() && File(project.picturePath).exists()) {
                        // Project profile photo
                        val imageLoader = remember {
                            ImageLoader.Builder(context)
                                .components { add(GifDecoder.Factory()) }
                                .allowHardware(false)
                                .build()
                        }
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(File(project.picturePath))
                                .allowHardware(false)
                                .build(),
                            imageLoader = imageLoader,
                            contentDescription = project.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .requiredSize(circleSize)
                                .clip(clipShape)
                        )
                    } else {
                        // Fallback: first letter of project name
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = project!!.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                style = pixelStyle(fontSize, colors.secondaryIcon),
                                modifier = Modifier.offset(y = (-2).dp)
                            )
                        }
                    }
                }
                // Active indicator bar
                if (isActive && !isAddButton) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .height(4.dp)
                            .background(colors.coral)
                    )
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

// ── HP/XP stat strip ─────────────────────────────────────────────
@Composable
fun StatStrip() {
    val colors = LocalWizairdColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StatBar(label = "HP", value = 8, max = 10, color = colors.coral)
        StatBar(label = "XP", value = 6, max = 10, color = colors.gold)
    }
}

@Composable
fun StatBar(label: String, value: Int, max: Int, color: Color) {
    val colors = LocalWizairdColors.current
    val filled = (value.toFloat() / max * 10).toInt()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = pixelStyle(7, colors.secondaryIcon), modifier = Modifier.width(22.dp).offset(y = (-2).dp))
        Row(
            modifier = Modifier.background(colors.secondaryIcon).padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            repeat(10) { i ->
                Box(modifier = Modifier.size(8.dp).background(if (i < filled) color else colors.backgroundDark))
            }
        }
        Text("$value/$max", style = pixelStyle(6, colors.secondaryIconSoft), modifier = Modifier.offset(y = (-2).dp))
    }
}

// ── Chat bubble ──────────────────────────────────────────────────
@Composable
fun ChatBubble(
    text: String, 
    loading: Boolean,
    thinkingContent: String? = null, 
    projectName: String? = null,
    projectId: String? = null,
    insightId: String? = null,
    positionLabel: String? = null,  // e.g. "3/12" when browsing insight history
    onRegenerate: () -> Unit = {},
    onProjectClick: () -> Unit = {},
    onShowToast: (String) -> Unit = {},
    onNewConceptClick: (String) -> Unit = {}, // Changed to accept projectId
    onConceptClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val isCoverScreen = rememberIsCoverScreen()
    
    // Use rememberUpdatedState to always have the latest projectId value
    val currentProjectId by rememberUpdatedState(projectId)
    
    var copied by remember { mutableStateOf(false) }
    var showThinking by remember { mutableStateOf(false) }
    var dotCount by remember { mutableIntStateOf(1) }

    // The insight shown (null while loading or when the bubble isn't showing a saved insight)
    // and the concepts linked to it, for the concept icon and tags
    val insight by remember(insightId) {
        insightId?.let { storedInsightFlow(context, it) } ?: kotlinx.coroutines.flow.flowOf(null)
    }.collectAsState(initial = null)
    val linkedConcepts by remember(projectId, insightId) {
        conceptsFlow(context, projectId ?: "").map { all ->
            all.filter { c -> c.sources.any { it.insightId != null && it.insightId == insightId } }
        }
    }.collectAsState(initial = emptyList())
    val extraction = rememberConceptExtractionUi(insightId)
    // Older insights carry their reasoning in storage
    val thinking = thinkingContent ?: insight?.thinkingContent
    LaunchedEffect(loading) {
        if (loading) {
            while (true) {
                delay(400)
                dotCount = (dotCount % 3) + 1
            }
        }
    }

    // Outer box adds bottom space for the tail and draws it via drawWithContent AFTER the bubble
    val tailColor = colors.secondarySurface
    val borderColor = colors.border
    Box(
        modifier = modifier
            // On cover screen: no tail, no bottom padding for it
            .then(if (!isCoverScreen) Modifier.padding(bottom = 22.dp) else Modifier)
            .then(if (!isCoverScreen) Modifier.drawWithContent {
                drawContent()  // bubble first
                val p = PixelSize.toPx()
                val tailX = size.width / 2f - p * 7.5f
                val tailY = size.height - p - 1f  // overlap bubble's bottom border row to kill seam
                // Fill
                drawRect(tailColor, Offset(tailX,       tailY + p * 0), Size(p * 15, p))
                drawRect(tailColor, Offset(tailX + p,   tailY + p * 1), Size(p * 13, p))
                drawRect(tailColor, Offset(tailX + p*2, tailY + p * 2), Size(p * 11, p))
                drawRect(tailColor, Offset(tailX + p*3, tailY + p * 3), Size(p *  9, p))
                drawRect(tailColor, Offset(tailX + p*4, tailY + p * 4), Size(p *  7, p))
                drawRect(tailColor, Offset(tailX + p*5, tailY + p * 5), Size(p *  5, p))
                drawRect(tailColor, Offset(tailX + p*6, tailY + p * 6), Size(p *  3, p))
                // Left border
                drawRect(borderColor, Offset(tailX,       tailY + p * 0), Size(p, p))
                drawRect(borderColor, Offset(tailX + p,   tailY + p * 1), Size(p, p))
                drawRect(borderColor, Offset(tailX + p*2, tailY + p * 2), Size(p, p))
                drawRect(borderColor, Offset(tailX + p*3, tailY + p * 3), Size(p, p))
                drawRect(borderColor, Offset(tailX + p*4, tailY + p * 4), Size(p, p))
                drawRect(borderColor, Offset(tailX + p*5, tailY + p * 5), Size(p, p))
                drawRect(borderColor, Offset(tailX + p*6, tailY + p * 6), Size(p, p))
                // Right border
                drawRect(borderColor, Offset(tailX + p*14, tailY + p * 0), Size(p, p))
                drawRect(borderColor, Offset(tailX + p*13, tailY + p * 1), Size(p, p))
                drawRect(borderColor, Offset(tailX + p*12, tailY + p * 2), Size(p, p))
                drawRect(borderColor, Offset(tailX + p*11, tailY + p * 3), Size(p, p))
                drawRect(borderColor, Offset(tailX + p*10, tailY + p * 4), Size(p, p))
                drawRect(borderColor, Offset(tailX + p* 9, tailY + p * 5), Size(p, p))
                drawRect(borderColor, Offset(tailX + p* 8, tailY + p * 6), Size(p, p))
                // Tip
                drawRect(borderColor, Offset(tailX + p*7,  tailY + p * 6), Size(p, p))
            } else Modifier)
    ) {
    PixelBox(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        fillColor = colors.secondarySurface,
        cornerStyle = PixelCornerStyle.Rounded,
        speechTail = false
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Bubble header ──
            if (!projectName.isNullOrEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = if (isCoverScreen) 6.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val projectNameInteraction = remember { MutableInteractionSource() }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 2.dp)
                            .pixelRounded8ClickableExpanded(
                                interactionSource = projectNameInteraction,
                                expandHDp = 4.dp
                            ) { onProjectClick() }
                    ) {
                        Image(
                            painter = painterResource(id = com.wizaird.app.R.drawable.ic_folder),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colors.textLow),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = projectName.uppercase(),
                            style = pixelStyle(12, colors.textLow).copy(lineHeight = 18.sp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.offset(y = (-2).dp)
                        )
                    }
                    
                    // Heart pin button — pins this insight to the top of the project's insight list
                    val shownInsight = insight
                    if (!loading && shownInsight != null) {
                        val isPinned = shownInsight.pinned
                        val svgLoader = remember {
                            ImageLoader.Builder(context)
                                .components { add(SvgDecoder.Factory()) }
                                .build()
                        }
                        val heartInteraction = remember { MutableInteractionSource() }
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(if (isPinned) "file:///android_asset/pixelarticons/heart-filled.svg" else "file:///android_asset/pixelarticons/heart.svg")
                                .build(),
                            imageLoader = svgLoader,
                            contentDescription = if (isPinned) "Unpin insight" else "Pin insight",
                            colorFilter = ColorFilter.tint(if (isPinned) colors.coral else colors.textXLow),
                            modifier = Modifier
                                .size(20.dp)
                                .pixelRounded8ClickableOversize(
                                    interactionSource = heartInteraction
                                ) { scope.launch { setInsightPinned(context, shownInsight.id, !isPinned) } }
                        )
                    }
                }
            }

            // Bubble text content
            if (loading) {
                val coffeeLoader = remember {
                    ImageLoader.Builder(context)
                        .components { add(GifDecoder.Factory()) }
                        .build()
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("file:///android_asset/coffee.gif")
                                .build(),
                            imageLoader = coffeeLoader,
                            contentDescription = "Loading",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.width(120.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Please wait while I think of what to teach you today...",
                            style = pixelStyle(14, colors.textHigh).copy(
                                lineHeight = (14 * 1.6f).sp
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .widthIn(max = 240.dp)
                                .offset(y = (-2).dp)
                        )
                    }
                }
            } else {
                Column(modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
                    .verticalScroll(rememberScrollState())
                ) {
                    SelectableMarkdownText(
                        markdown = text,
                        style = minecraftStyle(14, colors.textHigh),
                        modifier = Modifier.fillMaxWidth(),
                        onAddToConcepts = { selectedText ->
                            ConceptNavigationData.setPendingData(
                                name = selectedText,
                                contextText = text,
                                source = insightId?.let { com.wizaird.app.data.ConceptSource(type = com.wizaird.app.data.ConceptSourceType.INSIGHT, insightId = it, snippet = com.wizaird.app.data.buildSnippet(text, selectedText)) }
                            )
                            currentProjectId?.let { onNewConceptClick(it) }
                        }
                    )
                    // Concepts linked to this insight, then the extraction pill
                    insight?.let {
                        InsightConceptTags(
                            insight = it,
                            concepts = linkedConcepts,
                            ui = extraction,
                            onConceptClick = onConceptClick,
                            // 20dp above (text) and below (the area's 12dp + 8dp, before the icon row), so taps don't collide
                            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
                        )
                    }
                }
            }

            // Action icons row
            if (!loading) {
                val svgLoader = remember {
                    ImageLoader.Builder(context)
                        .components { add(SvgDecoder.Factory()) }
                        .build()
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = if (isCoverScreen) 8.dp else 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                                clipboardManager.setText(AnnotatedString(text))
                                copied = true
                                onShowToast("COPIED TO CLIPBOARD")
                                scope.launch {
                                    delay(2000)
                                    copied = false
                                }
                            }
                    )

                    // Thinking icon — only interactive when thinking content exists
                    if (!loading) {
                        val hasThinking = thinking != null
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

                    // Practice icon — disabled until the bubble shows a saved insight
                    PracticeIcon(
                        enabled = insight != null && !loading,
                        onClick = {
                            insight?.let { PracticeLauncher.request(it.projectId, com.wizaird.app.data.originFromInsight(it)) }
                        }
                    )

                    // Concept extraction icon — disabled until the bubble shows a saved insight
                    ExtractConceptsIcon(insight = insight, ui = extraction)

                    val zapInteraction = remember { MutableInteractionSource() }
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data("file:///android_asset/pixelarticons/zap.svg")
                            .build(),
                        imageLoader = svgLoader,
                        contentDescription = "Regenerate",
                        colorFilter = ColorFilter.tint(colors.textXLow),
                        modifier = Modifier
                            .size(20.dp)
                            .pixelRounded8ClickableOversize(
                                interactionSource = zapInteraction
                            ) { onRegenerate() }
                    )

                    // Position in the insight history (swipe to browse)
                    if (positionLabel != null) {
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = positionLabel,
                            style = pixelStyle(10, colors.textXLow),
                            modifier = Modifier.offset(y = (-2).dp)
                        )
                    }
                }
            }
        }
    }
    } // end outer tail Box

    // Thinking overlay
    if (showThinking) {
        ThinkingOverlay(
            thinkingContent = thinking ?: "No thought process was captured for this insight.",
            onDismiss = { showThinking = false }
        )
    }

    insight?.let { ConceptExtractionHost(insight = it, ui = extraction, onToast = onShowToast) }
}

// ── Wizard character (GIF via Coil) ──────────────────────────────
@Composable
fun WizardCharacter(bobOffsetY: Float, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(GifDecoder.Factory()) }
            .build()
    }
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data("file:///android_asset/wizard-cropped.gif")
            .build(),
        imageLoader = imageLoader,
        contentDescription = "Wizard",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .height(120.dp)
            .wrapContentWidth()
            .offset(y = bobOffsetY.dp)
    )
}

// ── Pixel text input ─────────────────────────────────────────────
@Composable
fun PixelInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    placeholder: String = "ASK THE WIZAIRD..."
) {
    val colors = LocalWizairdColors.current
    PixelBox(
        modifier = modifier,
        fillColor = colors.secondarySurface,
        cornerStyle = PixelCornerStyle.Rounded
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = minecraftStyle(14, colors.secondaryIcon),
                cursorBrush = SolidColor(colors.secondaryIcon),
                singleLine = false,
                maxLines = 10,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 4.dp, top = 3.dp, bottom = 3.dp)
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(placeholder.uppercase(), style = pixelStyle(12, colors.textXLow), modifier = Modifier.offset(y = (-2).dp))
                        }
                        inner()
                    }
                }
            )
            // Send button — pixel-rounded, centered, arrow icon
            val bubbleColor = colors.secondarySurface
            val sendInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .align(Alignment.Bottom)
                    .padding(all = 3.dp)
                    .size(32.dp)
                    .drawPixelArrowButton(
                        fillColor  = colors.coral,
                        cutColor   = bubbleColor,
                        arrowColor = colors.onAccent,
                        direction  = 1f
                    )
                    .pixelRounded8Clickable(interactionSource = sendInteraction, onClick = onSubmit)
            )
        }
    }
}

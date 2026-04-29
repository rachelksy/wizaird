package com.wizaird.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.wizaird.app.data.AiSettings
import com.wizaird.app.data.Project
import com.wizaird.app.data.askAi
import com.wizaird.app.data.projectsFlow
import com.wizaird.app.data.settingsFlow
import com.wizaird.app.data.shouldGenerateNewInsight
import com.wizaird.app.data.generateInsight
import com.wizaird.app.data.InsightResult
import com.wizaird.app.data.upsertProject
import com.wizaird.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun HomeScreen(
    onSettingsClick: () -> Unit,
    onProjectsClick: () -> Unit = {},
    onNewProjectClick: () -> Unit = {},
    onProjectClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by settingsFlow(context).collectAsState(initial = AiSettings())
    val colors = LocalWizairdColors.current
    val projects by projectsFlow(context).collectAsState(initial = emptyList())
    var activeProjectIndex by remember { mutableIntStateOf(0) }
    val activeProject = projects.getOrNull(activeProjectIndex)

    var bubbleText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    
    // Track current generation job to allow cancellation
    var currentGenerationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Function to generate insight for a project
    fun generateInsightForProject(project: Project) {
        // Cancel any existing generation
        currentGenerationJob?.cancel()
        
        currentGenerationJob = scope.launch {
            isLoading = true
            bubbleText = ""
            
            println("HomeScreen: Starting insight generation for project: ${project.name}")
            val result = generateInsight(context, project, settings)
            
            when (result) {
                is InsightResult.Success -> {
                    println("HomeScreen: Insight generation successful")
                    isLoading = false
                    // Type-out effect
                    val insight = result.insight
                    for (i in insight.indices) {
                        bubbleText = insight.substring(0, i + 1)
                        delay(18)
                    }
                }
                is InsightResult.Error -> {
                    println("HomeScreen: Insight generation failed - ${result.message}")
                    isLoading = false
                    bubbleText = "My scroll is torn! ${result.message}"
                }
            }
        }
    }

    // Auto-generate insight when active project changes or on first load
    LaunchedEffect(activeProject?.id, projects.size) {
        if (projects.isEmpty()) {
            // No projects yet
            bubbleText = "Create a project to start your learning journey!"
            isLoading = false
        } else {
            activeProject?.let { project ->
                if (shouldGenerateNewInsight(project)) {
                    generateInsightForProject(project)
                } else {
                    // Show existing insight
                    isLoading = false
                    bubbleText = project.lastInsightText.ifBlank {
                        "Welcome back! Let me think of something new to teach you..."
                    }
                }
            }
        }
    }

    val bobY = 0f

    fun askQuestion(prompt: String) {
        scope.launch {
            isLoading = true
            bubbleText = ""
            try {
                val result = askAi(settings, prompt.ifBlank { "Tell me one surprising bite-sized fact." })
                // type-out effect
                isLoading = false
                for (i in result.indices) {
                    bubbleText = result.substring(0, i + 1)
                    delay(18)
                }
            } catch (e: Exception) {
                isLoading = false
                bubbleText = "My scroll is torn! Check settings and try again."
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {

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
                ChatBubble(
                    text = bubbleText,
                    loading = isLoading,
                    projectName = activeProject?.name,
                    isPinned = activeProject?.pinnedInsight ?: false,
                    onTogglePin = {
                        activeProject?.let { project ->
                            scope.launch {
                                val updatedProject = project.copy(pinnedInsight = !project.pinnedInsight)
                                upsertProject(context, updatedProject)
                                println("Insight ${if (updatedProject.pinnedInsight) "pinned" else "unpinned"} for project: ${project.name}")
                            }
                        }
                    },
                    onRegenerate = {
                        activeProject?.let { project ->
                            println("User triggered manual regeneration")
                            generateInsightForProject(project)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .layout { measurable, constraints ->
                            // Measure with 8dp extra height
                            val placeable = measurable.measure(
                                constraints.copy(
                                    maxHeight = constraints.maxHeight + 8.dp.roundToPx()
                                )
                            )
                            // Layout with original height so it extends downward
                            layout(placeable.width, constraints.maxHeight) {
                                placeable.place(0, 0)
                            }
                        }
                )

                // Wizard overlaps the bubble from below
                WizardCharacter(
                    bobOffsetY = bobY,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                PixelInputBar(
                    value = inputText,
                    onValueChange = { inputText = it },
                    onSubmit = {
                        val q = inputText.trim()
                        if (q.isNotEmpty()) {
                            inputText = ""
                            askQuestion(q)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp).navigationBarsPadding())
            }
        }
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
    val projects by projectsFlow(context).collectAsState(initial = emptyList())

    // Build a combined list: null = add button, non-null = project
    val items: List<Project?> = listOf(null) + projects

    // activeIndex in items = activeProjectIndex + 1 (offset by the add button at 0)
    val activeItemIndex = activeProjectIndex + 1

    val inactiveBorder = if (colors.isDark) colors.border else Color(0xFF999999)

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
                        .size(64.dp)
                        .then(
                            if (isAddButton)
                                Modifier.pixelLargeCircleClickable(interactionSource = interaction) { onNewProjectClick() }
                            else
                                Modifier.pixelLargeCircleClickable(interactionSource = interaction) { onActiveProjectChange(index - 1) }
                        ),
                    fillColor = if (isAddButton) colors.secondaryButton else colors.secondarySurface,
                    borderColor = if (isActive) colors.textHigh else if (isAddButton) Color.Transparent else inactiveBorder,
                    cornerStyle = PixelCornerStyle.Circle
                ) {
                    if (isAddButton) {
                        // Pixel plus icon
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(modifier = Modifier.width(14.dp).height(2.dp).background(colors.secondaryIcon))
                            Box(modifier = Modifier.width(2.dp).height(14.dp).background(colors.secondaryIcon))
                        }
                    } else if (project!!.picturePath.isNotEmpty() && File(project.picturePath).exists()) {
                        // Project profile photo
                        val imageLoader = remember {
                            ImageLoader.Builder(context)
                                .components { add(GifDecoder.Factory()) }
                                .build()
                        }
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(File(project.picturePath))
                                .build(),
                            imageLoader = imageLoader,
                            contentDescription = project.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .requiredSize(64.dp)
                                .clip(PixelLargeCircleShape)
                        )
                    } else {
                        // Fallback: first letter of project name
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = project!!.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                style = pixelStyle(20, colors.secondaryIcon),
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
                            .background(Coral)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StatBar(label = "HP", value = 8, max = 10, color = Coral)
        StatBar(label = "XP", value = 6, max = 10, color = Gold)
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
    projectName: String? = null,
    isPinned: Boolean = false,
    onTogglePin: () -> Unit = {},
    onRegenerate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    var dotCount by remember { mutableIntStateOf(1) }
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
            .padding(bottom = 22.dp)  // 14dp tail + 8dp extra height
            .drawWithContent {
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
            }
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
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            painter = painterResource(id = com.wizaird.app.R.drawable.ic_folder),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colors.textXLow),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = projectName.uppercase(),
                            style = pixelStyle(12, colors.textXLow),
                            modifier = Modifier.offset(y = (-2).dp)
                        )
                    }
                    
                    // Heart pin button
                    if (!loading) {
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
                            colorFilter = ColorFilter.tint(if (isPinned) Coral else colors.textXLow),
                            modifier = Modifier
                                .size(20.dp)
                                .pixelRounded8ClickableOversize(
                                    interactionSource = heartInteraction
                                ) { onTogglePin() }
                        )
                    }
                }
            }

            // Bubble text content
            Box(modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
                .verticalScroll(rememberScrollState())
            ) {
                if (loading) {
                    Text(
                        text = "Please wait while I think of what to teach you today...",
                        style = minecraftStyle(14, colors.textHigh),
                        overflow = TextOverflow.Clip
                    )
                } else {
                    SelectionContainer {
                        MarkdownText(
                            markdown = text,
                            style = minecraftStyle(14, colors.textHigh),
                            modifier = Modifier.fillMaxWidth()
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
                    modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
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
                                scope.launch {
                                    delay(2000)
                                    copied = false
                                }
                            }
                    )

                    val noteInteraction = remember { MutableInteractionSource() }
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data("file:///android_asset/pixelarticons/sticky-note-text.svg")
                            .build(),
                        imageLoader = svgLoader,
                        contentDescription = "Save to note",
                        colorFilter = ColorFilter.tint(colors.textXLow),
                        modifier = Modifier
                            .size(20.dp)
                            .pixelRounded8ClickableOversize(
                                interactionSource = noteInteraction
                            ) { /* TODO: save to note */ }
                    )
                    
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
                }
            }
        }
    }
    } // end outer tail Box
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
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null
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
                            Text("ASK THE WIZAIRD...", style = pixelStyle(12, colors.textXLow), modifier = Modifier.offset(y = (-2).dp))
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
                        fillColor  = Coral,
                        cutColor   = bubbleColor,
                        arrowColor = colors.secondaryIcon,
                        direction  = 1f
                    )
                    .pixelRounded8Clickable(interactionSource = sendInteraction, onClick = onSubmit)
            )
        }
    }
}

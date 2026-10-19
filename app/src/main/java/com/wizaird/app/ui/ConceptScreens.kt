package com.wizaird.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import com.wizaird.app.data.Concept
import com.wizaird.app.data.ConceptSource
import com.wizaird.app.data.ConceptSourceType
import com.wizaird.app.data.ConceptStatus
import com.wizaird.app.data.addSourceToConcept
import com.wizaird.app.data.allChatsFlow
import com.wizaird.app.data.allStoredInsightsFlow
import com.wizaird.app.data.setConceptChats
import com.wizaird.app.data.conceptFlow
import com.wizaird.app.data.conceptsFlow
import com.wizaird.app.data.copyConceptToProject
import com.wizaird.app.data.deleteConcept
import com.wizaird.app.data.findDuplicateConcept
import com.wizaird.app.data.generateConcept
import com.wizaird.app.data.getConceptById
import com.wizaird.app.data.notesForConceptFlow
import com.wizaird.app.data.projectsFlow
import com.wizaird.app.data.upsertConcept
import com.wizaird.app.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ── Navigation hand-off ───────────────────────────────────────────────────────

/** Temporary storage for passing "Add to concepts" data to the new-concept screen. */
object ConceptNavigationData {
    private var pendingName: String? = null
    private var pendingContext: String = ""
    private var pendingSource: ConceptSource? = null

    fun setPendingData(name: String, contextText: String, source: ConceptSource?) {
        pendingName = name.trim()
        pendingContext = contextText
        pendingSource = source
    }

    data class Pending(val name: String, val contextText: String, val source: ConceptSource?)

    fun consumePendingData(): Pending? {
        val data = pendingName?.let { Pending(it, pendingContext, pendingSource) }
        pendingName = null
        pendingContext = ""
        pendingSource = null
        return data
    }
}

/**
 * Generates concept pages outside any screen's lifetime, so leaving the page
 * doesn't cancel a generation that's already running.
 */
object ConceptGeneration {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val generating = MutableStateFlow<Set<String>>(emptySet())
    val failed = MutableStateFlow<Set<String>>(emptySet())

    fun start(context: Context, conceptId: String) {
        if (conceptId in generating.value) return
        val appContext = context.applicationContext
        generating.update { it + conceptId }
        failed.update { it - conceptId }
        scope.launch {
            try {
                val concept = getConceptById(appContext, conceptId) ?: return@launch
                val result = generateConcept(
                    appContext, concept.projectId, concept.name, concept.sources.map { it.snippet }
                )
                if (result == null) {
                    failed.update { it + conceptId }
                } else {
                    // Re-read so edits made while generating aren't lost
                    val latest = getConceptById(appContext, conceptId) ?: return@launch
                    val now = System.currentTimeMillis()
                    upsertConcept(
                        appContext,
                        latest.copy(
                            intro = result.intro.ifBlank { latest.intro },
                            body = result.body,
                            updatedAt = now,
                            lastTouchedAt = now
                        )
                    )
                }
            } finally {
                generating.update { it - conceptId }
            }
        }
    }

    fun clearFailure(conceptId: String) = failed.update { it - conceptId }
}

// ── Shared pieces (same visuals as the existing card menus and headers) ───────

@Composable
fun rememberSvgLoader(): ImageLoader {
    val context = LocalContext.current
    return remember { ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build() }
}

/** Back button + title + trailing actions — same header as the other detail screens. */
@Composable
fun ConceptHeader(title: String, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    val colors = LocalWizairdColors.current
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
                    fillColor = colors.secondaryButton,
                    borderColor = Color.Transparent,
                    cutColor = colors.background
                )
                .pixelCircleClickable(interactionSource = backInteraction) { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .drawPixelArrowButton(
                        fillColor = colors.secondaryButton,
                        cutColor = colors.secondaryButton,
                        arrowColor = colors.secondaryIcon,
                        direction = -1f
                    )
            )
        }
        Text(
            title,
            style = pixelStyle(14, colors.secondaryIcon),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .offset(y = (-2).dp)
        )
        trailing()
    }
}

/** The ⋮ options popup used on cards, extracted so concept screens share one copy. */
@Composable
fun PixelOptionsMenu(
    fillColor: Color,
    onDismiss: () -> Unit,
    anchorHeight: androidx.compose.ui.unit.Dp = 32.dp,  // height of the button the menu opens from
    gap: androidx.compose.ui.unit.Dp = 4.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    val offsetY = with(density) { (anchorHeight + gap).roundToPx() }
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(x = 0, y = offsetY),
        onDismissRequest = onDismiss,
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
                    val cut = Color.Transparent
                    drawRect(fillColor)
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
                verticalArrangement = Arrangement.spacedBy(0.dp),
                content = content
            )
        }
    }
}

/** One row in [PixelOptionsMenu]. Pass either an asset icon path or a drawable resource. */
@Composable
fun PixelOptionsMenuItem(
    label: String,
    onClick: () -> Unit,
    iconAsset: String? = null,
    iconRes: Int? = null,
    tint: Color = LocalWizairdColors.current.secondaryIcon,
    labelColor: Color = tint  // separate label color, e.g. a check icon that's hidden when not selected
) {
    val context = LocalContext.current
    val svgLoader = rememberSvgLoader()
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pixelRounded8Clickable(interactionSource = interaction, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (iconRes != null) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = label,
                    colorFilter = ColorFilter.tint(tint),
                    modifier = Modifier.size(18.dp)
                )
            } else if (iconAsset != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data("file:///android_asset/pixelarticons/$iconAsset")
                        .build(),
                    imageLoader = svgLoader,
                    contentDescription = label,
                    colorFilter = ColorFilter.tint(tint),
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(label, style = pixelStyle(10, labelColor), modifier = Modifier.offset(y = (-2).dp))
        }
    }
}

/** The ⋮ button that opens [PixelOptionsMenu]. */
@Composable
fun MoreOptionsButton(onClick: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val svgLoader = rememberSvgLoader()
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(32.dp)
            .pixelRounded8Clickable(interactionSource = interaction, onClick = onClick),
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
}

/** Small pill showing a concept name (or an alias). Ghost concepts use the muted color. */
@Composable
fun ConceptPill(
    label: String,
    ghost: Boolean = false,
    fillColor: Color = Coral.copy(alpha = 0.25f),  // light orange for concepts; pass another fill for other pills
    onClick: (() -> Unit)? = null
) {
    val colors = LocalWizairdColors.current
    val interaction = remember { MutableInteractionSource() }
    PixelBox(
        modifier = if (onClick != null) Modifier.pixelRounded8Clickable(interactionSource = interaction, onClick = onClick) else Modifier,
        fillColor = fillColor,
        borderColor = Color.Transparent,
        cornerStyle = PixelCornerStyle.Rounded8
    ) {
        Text(
            label,
            style = pixelStyle(8, if (ghost) colors.secondaryIconSoft else colors.secondaryIcon),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .offset(y = (-1).dp)
        )
    }
}

/** Muted one-line text used for empty sections on the concept page. */
@Composable
private fun EmptySectionText(text: String) {
    val colors = LocalWizairdColors.current
    Text(text, style = minecraftStyle(12, colors.textXLow), modifier = Modifier.offset(y = (-2).dp))
}

/** Thin divider between concept page sections — same style as the settings dividers. */
@Composable
private fun SectionDivider() {
    val colors = LocalWizairdColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.textXLow.copy(alpha = 0.35f))
    )
}

/** Section label with a small action button on the right (e.g. CHATS ... EDIT). */
@Composable
private fun SectionWithAction(
    label: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = LocalWizairdColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = pixelStyle(12, colors.secondaryIconSoft), modifier = Modifier.weight(1f).offset(y = (-2).dp))
            PixelButtonSmall(label = actionLabel, primary = false, onClick = onAction)
        }
        content()
    }
}

/** Card for a place the concept was met that no longer exists (or has no chat yet): shows the stored snippet. */
@Composable
private fun SnippetCard(title: String, snippet: String, onClick: (() -> Unit)?) {
    val colors = LocalWizairdColors.current
    val interaction = remember { MutableInteractionSource() }
    PixelBox(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pixelRoundedClickable(interactionSource = interaction, onClick = onClick) else Modifier),
        fillColor = colors.secondarySurface,
        cornerStyle = PixelCornerStyle.Rounded
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = pixelStyle(8, colors.secondaryIconSoft), modifier = Modifier.offset(y = (-2).dp))
            if (snippet.isNotBlank()) {
                Text(
                    snippet,
                    style = minecraftStyle(12, colors.secondaryIcon),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.offset(y = (-2).dp)
                )
            }
        }
    }
}

// ── Concept page (read-only info page) ────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConceptPageScreen(
    conceptId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onChatClick: (projectId: String, chatId: String) -> Unit,
    onInsightClick: (projectId: String, insightId: String) -> Unit,
    onNoteClick: (String) -> Unit,
    onConceptClick: (String) -> Unit
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val scope = rememberCoroutineScope()
    val isCoverScreen = rememberIsCoverScreen()

    // Flow is null until the first read, then the concept (or null if deleted)
    val conceptState by remember(conceptId) { conceptFlow(context, conceptId) }
        .collectAsState(initial = Concept(id = "__loading__"))
    val concept = conceptState
    val generatingIds by ConceptGeneration.generating.collectAsState()
    val failedIds by ConceptGeneration.failed.collectAsState()
    val isGenerating = conceptId in generatingIds

    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRegenerateDialog by remember { mutableStateOf(false) }
    var showCopyDialog by remember { mutableStateOf(false) }
    var showChatsDialog by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(conceptId in failedIds) {
        if (conceptId in failedIds) {
            toastMessage = "COULD NOT GENERATE"
            ConceptGeneration.clearFailure(conceptId)
            kotlinx.coroutines.delay(2500)
            toastMessage = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        if (concept == null || concept.id == "__loading__") {
            // Deleted or still loading
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.height(if (isCoverScreen) 8.dp else 48.dp))
                ConceptHeader(title = "", onBack = onBack)
            }
        } else {
        // All projects: a copied concept keeps links to the original project's chats and insights
        val chats by remember { allChatsFlow(context) }.collectAsState(initial = emptyList())
        val insights by remember { allStoredInsightsFlow(context) }.collectAsState(initial = emptyList())
        val taggedNotes by remember(conceptId) { notesForConceptFlow(context, conceptId) }.collectAsState(initial = emptyList())
        val projectConcepts by remember(concept.projectId) { conceptsFlow(context, concept.projectId) }.collectAsState(initial = emptyList())
        val related = concept.relatedConceptIds.mapNotNull { id -> projectConcepts.firstOrNull { it.id == id } }

        val projects by projectsFlow(context).collectAsState(initial = emptyList())
        val projectName = projects.firstOrNull { it.id == concept.projectId }?.name?.ifEmpty { "UNNAMED PROJECT" } ?: ""

        // Linked chats (met in a message, or linked by hand), newest first; deleted ones keep their snippet
        val linkedChatIds = concept.sources.mapNotNull { it.chatId }.distinct()
        val connectedChats = chats.filter { it.id in linkedChatIds }.sortedByDescending { it.createdAt }
        val deletedChatSources = concept.sources
            .filter { it.chatId != null && chats.none { c -> c.id == it.chatId } }
            .distinctBy { it.chatId }
        val insightSources = concept.sources.filter { it.type == ConceptSourceType.INSIGHT && it.insightId != null }
            .distinctBy { it.insightId }

        // Section positions inside the scrolling column, for the jump buttons
        val scrollState = rememberScrollState()
        val sectionOffsets = remember { mutableStateMapOf<String, Int>() }
        fun jumpTo(section: String) {
            sectionOffsets[section]?.let { y -> scope.launch { scrollState.animateScrollTo(y) } }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(if (isCoverScreen) 8.dp else 48.dp))

            // Header — same layout as the chat screen: back, project name centered, ⋮.
            // Tapping the header scrolls back to the top.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { scope.launch { scrollState.animateScrollTo(0) } }
            ) {
                val backInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(40.dp)
                        .drawPixelCircle(
                            fillColor = colors.secondaryButton,
                            borderColor = Color.Transparent,
                            cutColor = colors.background
                        )
                        .pixelCircleClickable(interactionSource = backInteraction) { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .drawPixelArrowButton(
                                fillColor = colors.secondaryButton,
                                cutColor = colors.secondaryButton,
                                arrowColor = colors.secondaryIcon,
                                direction = -1f
                            )
                    )
                }

                Text(
                    text = projectName,
                    style = pixelStyle(10, colors.secondaryIcon),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 56.dp)
                        .offset(y = (-2).dp)
                )

                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    PixelCircleIconButton(
                        iconRes = com.wizaird.app.R.drawable.ic_more_vertical,
                        contentDescription = "More options",
                        fillColor = colors.secondaryButton,
                        onClick = { showMenu = true }
                    )
                    if (showMenu) {
                        PixelOptionsMenu(
                            fillColor = colors.secondarySurface,
                            onDismiss = { showMenu = false },
                            anchorHeight = 40.dp,
                            gap = 8.dp
                        ) {
                            PixelOptionsMenuItem("Edit", iconAsset = "pen-square.svg", onClick = {
                                showMenu = false
                                onEdit()
                            })
                            if (!concept.isEmpty) {
                                PixelOptionsMenuItem("Regenerate", iconAsset = "reload.svg", onClick = {
                                    showMenu = false
                                    showRegenerateDialog = true
                                })
                            }
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Title, date, ghost actions, jump buttons
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        concept.name,
                        style = minecraftStyle(20, if (concept.isGhost) colors.secondaryIconSoft else colors.textHigh).copy(
                            fontWeight = FontWeight.Bold,
                            lineHeight = (20 * 1.3f).sp
                        ),
                        modifier = Modifier.offset(y = (-2).dp)
                    )
                    if (concept.isGhost) {
                        Text(
                            "GHOST CONCEPT",
                            style = pixelStyle(8, colors.secondaryIconSoft),
                            modifier = Modifier.offset(y = (-2).dp)
                        )
                        PixelButtonSmall(
                            label = "SAVE CONCEPT",
                            primary = true,
                            onClick = {
                                scope.launch {
                                    upsertConcept(
                                        context,
                                        concept.copy(status = ConceptStatus.FULL, updatedAt = System.currentTimeMillis())
                                    )
                                    toastMessage = "CONCEPT SAVED"
                                    kotlinx.coroutines.delay(1500)
                                    toastMessage = null
                                }
                            }
                        )
                    }
                    // Jump buttons: skip past a long article to what this concept connects to
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val anchorFill = colors.secondaryButton
                        ConceptPill(label = "CHATS ${connectedChats.size + deletedChatSources.size}", fillColor = anchorFill, onClick = { jumpTo("chats") })
                        ConceptPill(label = "INSIGHTS ${insightSources.size}", fillColor = anchorFill, onClick = { jumpTo("insights") })
                        ConceptPill(label = "NOTES ${taggedNotes.size}", fillColor = anchorFill, onClick = { jumpTo("notes") })
                        ConceptPill(label = "RELATED ${related.size}", fillColor = anchorFill, onClick = { jumpTo("related") })
                    }
                }

                // Description
                if (isGenerating) {
                    // Keep the existing summary visible while the page is rewritten
                    if (concept.intro.isNotBlank()) {
                        Text(
                            concept.intro,
                            style = minecraftStyle(14, colors.textHigh).copy(lineHeight = (14 * 1.6f).sp),
                            modifier = Modifier.offset(y = (-2).dp)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(vertical = 24.dp)
                    ) {
                        PixelLoadingIndicator(color = colors.secondaryIcon, size = 18.dp)
                        Text(
                            "WRITING THIS CONCEPT...",
                            style = pixelStyle(10, colors.secondaryIconSoft),
                            modifier = Modifier.offset(y = (-2).dp)
                        )
                    }
                } else if (concept.isEmpty) {
                    // Empty concept (usually a ghost): offer AI or manual writing
                    PixelBox(
                        modifier = Modifier.fillMaxWidth(),
                        fillColor = colors.secondarySurface,
                        cornerStyle = PixelCornerStyle.Rounded
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("NOTHING WRITTEN YET", style = pixelStyle(12, colors.secondaryIcon), modifier = Modifier.offset(y = (-2).dp))
                            PixelButtonLarge(
                                label = "GENERATE WITH AI",
                                primary = true,
                                modifier = Modifier.fillMaxWidth(),
                                cutColor = colors.secondarySurface,
                                onClick = { ConceptGeneration.start(context, conceptId) }
                            )
                            PixelButtonLarge(
                                label = "EDIT MANUALLY",
                                primary = false,
                                modifier = Modifier.fillMaxWidth(),
                                cutColor = colors.secondarySurface,
                                onClick = onEdit
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (concept.intro.isNotBlank()) {
                            Text(
                                concept.intro,
                                style = minecraftStyle(14, colors.textHigh).copy(lineHeight = (14 * 1.6f).sp),
                                modifier = Modifier.offset(y = (-2).dp)
                            )
                        }
                        if (concept.body.isNotBlank()) {
                            MarkdownText(
                                markdown = concept.body,
                                style = minecraftStyle(13, colors.secondaryIcon).copy(lineHeight = (13 * 1.6f).sp)
                            )
                        }
                    }
                }

                // Aliases (plain text) and dates at the end of the article
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val aliases = concept.aliasList()
                    if (aliases.isNotEmpty()) {
                        Text(
                            "Also known as: " + aliases.joinToString(", "),
                            style = minecraftStyle(12, colors.secondaryIconSoft).copy(lineHeight = (12 * 1.6f).sp),
                            modifier = Modifier.offset(y = (-2).dp)
                        )
                    }
                    val edited = concept.updatedAt - concept.createdAt > 60_000
                    Text(
                        "ADDED ${concept.formattedCreatedAt().uppercase()}" +
                            if (edited) "  •  EDITED ${concept.formattedUpdatedAt().uppercase()}" else "",
                        style = pixelStyle(8, colors.secondaryIconSoft),
                        modifier = Modifier.offset(y = (-2).dp)
                    )
                }

                SectionDivider()

                // Chats — linked automatically by "Add to concepts", or by hand with EDIT
                SectionWithAction(
                    label = "CHATS",
                    actionLabel = "EDIT",
                    onAction = { showChatsDialog = true },
                    modifier = Modifier.onGloballyPositioned { sectionOffsets["chats"] = it.positionInParent().y.toInt() }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (connectedChats.isEmpty() && deletedChatSources.isEmpty()) {
                            EmptySectionText("Not connected to any chats yet")
                        }
                        connectedChats.forEach { chat ->
                            ChatListItem(chat = chat, onClick = { onChatClick(chat.projectId, chat.id) })
                        }
                        deletedChatSources.forEach { src ->
                            SnippetCard(title = "DELETED CHAT", snippet = src.snippet, onClick = null)
                        }
                    }
                }

                SectionDivider()

                // Insights the concept was added from — tap to open the insight chat
                SettingsField(label = "INSIGHTS", modifier = Modifier.onGloballyPositioned { sectionOffsets["insights"] = it.positionInParent().y.toInt() }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (insightSources.isEmpty()) {
                            EmptySectionText("Not added from any insights yet")
                        }
                        insightSources.forEach { src ->
                            val insight = insights.firstOrNull { it.id == src.insightId }
                            SnippetCard(
                                title = insight?.formattedCreatedAt()?.uppercase() ?: "DELETED INSIGHT",
                                snippet = src.snippet,
                                onClick = insight?.let { { onInsightClick(it.projectId, it.id) } }
                            )
                        }
                    }
                }

                SectionDivider()

                // Notes tagged with this concept
                SettingsField(label = "NOTES", modifier = Modifier.onGloballyPositioned { sectionOffsets["notes"] = it.positionInParent().y.toInt() }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (taggedNotes.isEmpty()) {
                            EmptySectionText("No notes tagged yet")
                        }
                        taggedNotes.forEach { note ->
                            NoteListItem(note = note, onClick = { onNoteClick(note.id) })
                        }
                    }
                }

                SectionDivider()

                // Related concepts — tappable concept pills (filled in by a later feature)
                SettingsField(label = "RELATED CONCEPTS", modifier = Modifier.onGloballyPositioned { sectionOffsets["related"] = it.positionInParent().y.toInt() }) {
                    if (related.isEmpty()) {
                        EmptySectionText("No related concepts yet")
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            related.forEach { r -> ConceptPill(label = r.name, ghost = r.isGhost, onClick = { onConceptClick(r.id) }) }
                        }
                    }
                }
            }
        }

        if (showChatsDialog) {
            // Chats from this concept's project, plus any already linked from elsewhere (copied concepts)
            val choices = chats
                .filter { it.projectId == concept.projectId || it.id in linkedChatIds }
                .sortedByDescending { it.createdAt }
            PixelChecklistDialog(
                title = "LINK CHATS",
                items = choices.map { it.id to it.title.ifBlank { "Untitled chat" } },
                initialSelection = linkedChatIds,
                emptyText = "No chats in this project yet",
                onSave = { ids ->
                    showChatsDialog = false
                    scope.launch { setConceptChats(context, conceptId, choices.filter { it.id in ids }) }
                },
                onDismiss = { showChatsDialog = false }
            )
        }

        if (showRegenerateDialog) {
            PixelConfirmationDialog(
                title = "REGENERATE",
                message = "This rewrites the summary and article of \"${concept.name}\". Aliases, chats and notes stay the same.",
                confirmLabel = "REGENERATE",
                cancelLabel = "CANCEL",
                onConfirm = {
                    showRegenerateDialog = false
                    ConceptGeneration.start(context, conceptId)
                },
                onDismiss = { showRegenerateDialog = false }
            )
        }

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
                        deleteConcept(context, conceptId)
                        onBack()
                    }
                },
                onDismiss = { showDeleteDialog = false }
            )
        }

        if (showCopyDialog) {
            CopyConceptDialog(
                currentProjectId = concept.projectId,
                onCopy = { targetProjectId ->
                    showCopyDialog = false
                    scope.launch {
                        copyConceptToProject(context, conceptId, targetProjectId)
                        toastMessage = "COPIED"
                        kotlinx.coroutines.delay(1500)
                        toastMessage = null
                    }
                },
                onDismiss = { showCopyDialog = false }
            )
        }

        }

        PixelToast(
            message = toastMessage ?: "",
            visible = toastMessage != null,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ── Create / edit concept ─────────────────────────────────────────────────────

/** Multi-line pixel text area, same look as the old glossary definition field. */
@Composable
private fun PixelTextArea(value: String, onValueChange: (String) -> Unit, placeholder: String, minHeight: androidx.compose.ui.unit.Dp, enabled: Boolean = true) {
    val colors = LocalWizairdColors.current
    PixelBox(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight),
        fillColor = colors.secondarySurface,
        cornerStyle = PixelCornerStyle.Rounded
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            textStyle = minecraftStyle(12, colors.secondaryIcon),
            cursorBrush = SolidColor(colors.secondaryIcon),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.TopStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = minecraftStyle(12, colors.secondaryIconSoft), modifier = Modifier.offset(y = (-2).dp))
                    }
                    inner()
                }
            }
        )
    }
}

/**
 * Create (conceptId == null) or edit a concept.
 * @param onSaved called with the saved concept's ID.
 * @param onOpenExisting called when the user chooses to open a duplicate instead.
 */
@Composable
fun ConceptEditScreen(
    projectId: String,
    conceptId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onOpenExisting: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalWizairdColors.current
    val isCoverScreen = rememberIsCoverScreen()
    val isNew = conceptId == null

    val pending = remember { if (isNew) ConceptNavigationData.consumePendingData() else null }

    var original by remember { mutableStateOf<Concept?>(null) }
    var name by remember { mutableStateOf(pending?.name ?: "") }
    var intro by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var aliases by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(!isNew) }
    var isGenerating by remember { mutableStateOf(false) }
    var duplicate by remember { mutableStateOf<Concept?>(null) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    fun showToast(msg: String) {
        scope.launch {
            toastMessage = msg
            kotlinx.coroutines.delay(2000)
            toastMessage = null
        }
    }

    fun generate() {
        if (name.isBlank() || isGenerating) return
        isGenerating = true
        scope.launch {
            val snippets = listOfNotNull(pending?.source?.snippet) + (original?.sources?.map { it.snippet } ?: emptyList())
            val result = generateConcept(context, projectId, name.trim(), snippets.distinct())
            isGenerating = false
            if (result == null) {
                showToast("COULD NOT GENERATE")
            } else {
                if (result.intro.isNotBlank()) intro = result.intro
                body = result.body
            }
        }
    }

    LaunchedEffect(conceptId) {
        if (conceptId != null) {
            getConceptById(context, conceptId)?.let { c ->
                original = c
                name = c.name
                intro = c.intro
                body = c.body
                aliases = c.aliases
            }
            isLoading = false
        } else if (pending != null) {
            // Came from "Add to concepts": check for a duplicate first, otherwise write it with AI
            val existing = findDuplicateConcept(context, projectId, pending.name)
            if (existing != null) duplicate = existing else generate()
        }
    }

    fun save() {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || isGenerating) return
        scope.launch {
            val existing = findDuplicateConcept(context, projectId, trimmedName, excludeId = conceptId)
            if (existing != null) {
                duplicate = existing
                return@launch
            }
            val now = System.currentTimeMillis()
            val saved = (original ?: Concept(projectId = projectId, createdAt = now)).let { base ->
                val source = pending?.source
                base.copy(
                    name = trimmedName,
                    intro = intro.trim(),
                    body = body.trim(),
                    aliases = aliases.trim(),
                    // Saving by hand makes a ghost a full concept
                    status = ConceptStatus.FULL,
                    updatedAt = now,
                    lastTouchedAt = now,
                    sources = if (source != null) base.sources + source else base.sources,
                    firstMetAt = base.firstMetAt ?: source?.createdAt
                )
            }
            upsertConcept(context, saved)
            onSaved(saved.id)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(if (isCoverScreen) 8.dp else 48.dp))

            ConceptHeader(title = if (isNew) "NEW CONCEPT" else "EDIT CONCEPT", onBack = onBack) {
                PixelButtonSmall(
                    label = if (isNew) "CREATE" else "SAVE",
                    primary = true,
                    onClick = { save() }
                )
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("LOADING...", style = pixelStyle(14, colors.secondaryIcon), modifier = Modifier.offset(y = (-2).dp))
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SettingsField(label = "NAME") {
                        PixelTextInput(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = "ENTER CONCEPT NAME"
                        )
                    }

                    // AI writing: generate on new/empty concepts, regenerate on written ones
                    val hasContent = intro.isNotBlank() || body.isNotBlank()
                    if (isGenerating) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            PixelLoadingIndicator(color = colors.secondaryIcon, size = 18.dp)
                            Text("WRITING THIS CONCEPT...", style = pixelStyle(10, colors.secondaryIconSoft), modifier = Modifier.offset(y = (-2).dp))
                        }
                    } else {
                        PixelButtonLarge(
                            label = if (hasContent) "REGENERATE WITH AI" else "GENERATE WITH AI",
                            primary = !hasContent,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (name.isBlank()) showToast("ENTER A NAME FIRST") else generate()
                            }
                        )
                    }

                    SettingsField(label = "SUMMARY") {
                        PixelTextArea(
                            value = intro,
                            onValueChange = { intro = it },
                            placeholder = "One or two sentences on what this is...",
                            minHeight = 80.dp,
                            enabled = !isGenerating
                        )
                    }

                    SettingsField(label = "DETAILS") {
                        PixelTextArea(
                            value = body,
                            onValueChange = { body = it },
                            placeholder = "How it works, where it's used, examples... (markdown supported)",
                            minHeight = 200.dp,
                            enabled = !isGenerating
                        )
                    }

                    SettingsField(label = "ALIASES") {
                        PixelTextArea(
                            value = aliases,
                            onValueChange = { aliases = it },
                            placeholder = "Comma-separated aliases for search...",
                            minHeight = 48.dp
                        )
                    }
                }
            }
        }

        duplicate?.let { existing ->
            PixelConfirmationDialog(
                title = "ALREADY A CONCEPT",
                message = "\"${existing.name}\" is already in your concepts. Open it instead?",
                confirmLabel = "OPEN",
                cancelLabel = "CANCEL",
                onConfirm = {
                    duplicate = null
                    scope.launch {
                        // Still record where it was met this time
                        pending?.source?.let { addSourceToConcept(context, existing.id, it) }
                        onOpenExisting(existing.id)
                    }
                },
                onDismiss = {
                    duplicate = null
                    // Arrived from "Add to concepts" with a duplicate: nothing to do here
                    if (pending != null && original == null && intro.isBlank() && body.isBlank()) onBack()
                }
            )
        }

        PixelToast(
            message = toastMessage ?: "",
            visible = toastMessage != null,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ── Dialogs ───────────────────────────────────────────────────────────────────

/** Pick a project to copy a concept into. Same layout as the Move dialogs. */
@Composable
fun CopyConceptDialog(
    currentProjectId: String,
    onCopy: (String) -> Unit,
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
                Text("COPY TO", style = pixelStyle(12, colors.textHigh), modifier = Modifier.offset(y = (-2).dp))

                if (availableProjects.isEmpty()) {
                    Text(
                        text = "No other projects available",
                        style = minecraftStyle(14, colors.textLow).copy(lineHeight = (14 * 1.6f).sp),
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
                                borderColor = if (isSelected) Coral else Color.Transparent,
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

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelButtonLarge(
                        label = "CANCEL",
                        primary = false,
                        modifier = Modifier.weight(1f),
                        cutColor = colors.secondarySurface,
                        onClick = onDismiss
                    )
                    PixelButtonLarge(
                        label = "COPY",
                        primary = true,
                        modifier = Modifier.weight(1f),
                        cutColor = colors.secondarySurface,
                        onClick = { selectedProjectId?.let { onCopy(it) } }
                    )
                }
            }
        }
    }
}

/** Tag a note with existing concepts (multi-select). */
@Composable
fun TagConceptsDialog(
    projectId: String,
    initialSelection: List<String>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val concepts by remember(projectId) { conceptsFlow(context, projectId, sortOrder = com.wizaird.app.data.ConceptSortOrder.ALPHABETICAL) }
        .collectAsState(initial = emptyList())
    PixelChecklistDialog(
        title = "TAG CONCEPTS",
        items = concepts.map { it.id to it.name },
        mutedIds = concepts.filter { it.isGhost }.map { it.id }.toSet(),
        initialSelection = initialSelection,
        emptyText = "No concepts in this project yet",
        onSave = onSave,
        onDismiss = onDismiss
    )
}

/**
 * Multi-select list dialog (checkbox rows). Same layout as the Move dialogs.
 * [items] are (id, label) pairs; [mutedIds] are shown in the muted color (e.g. ghost concepts).
 * [onSave] gets the selected IDs in list order.
 */
@Composable
fun PixelChecklistDialog(
    title: String,
    items: List<Pair<String, String>>,
    initialSelection: List<String>,
    emptyText: String,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    mutedIds: Set<String> = emptySet()
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val svgLoader = rememberSvgLoader()
    var selected by remember { mutableStateOf(initialSelection.toSet()) }

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
                Text(title, style = pixelStyle(12, colors.textHigh), modifier = Modifier.offset(y = (-2).dp))

                if (items.isEmpty()) {
                    Text(
                        text = emptyText,
                        style = minecraftStyle(14, colors.textLow).copy(lineHeight = (14 * 1.6f).sp),
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
                        items.forEach { (id, label) ->
                            val isSelected = id in selected
                            val rowInteraction = remember { MutableInteractionSource() }
                            PixelBox(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pixelRoundedClickable(
                                        interactionSource = rowInteraction,
                                        onClick = { selected = if (isSelected) selected - id else selected + id }
                                    ),
                                fillColor = if (isSelected) colors.userBubble else colors.background,
                                borderColor = if (isSelected) Coral else Color.Transparent,
                                cornerStyle = PixelCornerStyle.Rounded
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data("file:///android_asset/pixelarticons/${if (isSelected) "checkbox-on.svg" else "checkbox.svg"}")
                                            .build(),
                                        imageLoader = svgLoader,
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(if (isSelected) Coral else colors.secondaryIcon),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = label,
                                        style = pixelStyle(10, if (id in mutedIds) colors.secondaryIconSoft else colors.secondaryIcon),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.offset(y = (-2).dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelButtonLarge(
                        label = "CANCEL",
                        primary = false,
                        modifier = Modifier.weight(1f),
                        cutColor = colors.secondarySurface,
                        onClick = onDismiss
                    )
                    PixelButtonLarge(
                        label = "SAVE",
                        primary = true,
                        modifier = Modifier.weight(1f),
                        cutColor = colors.secondarySurface,
                        onClick = { onSave(items.map { it.first }.filter { it in selected }) }
                    )
                }
            }
        }
    }
}

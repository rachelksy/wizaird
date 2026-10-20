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
import com.wizaird.app.data.ConceptGeneration
import com.wizaird.app.data.ConceptStatus
import com.wizaird.app.data.addSourceToConcept
import com.wizaird.app.data.allChatsFlow
import com.wizaird.app.data.allStoredInsightsFlow
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
    fillColor: Color = LocalWizairdColors.current.tertiarySurface,  // same as the search bar; pass another fill for other pills
    textColor: Color? = null,  // null = text-low (text-x-low for ghosts)
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
            style = pixelStyle(8, textColor ?: if (ghost) colors.textXLow else colors.textLow),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .offset(y = (-1).dp)
        )
    }
}

/** A pill after the concept pills that isn't a concept (e.g. "Extracting concepts..."). */
data class ExtraPill(
    val label: String,
    val fillColor: Color? = null,   // null = same fill as concept pills
    val textColor: Color? = null,
    val onClick: (() -> Unit)? = null
)

@Composable
fun ExtraPillView(pill: ExtraPill) {
    ConceptPill(
        label = pill.label,
        fillColor = pill.fillColor ?: LocalWizairdColors.current.tertiarySurface,
        textColor = pill.textColor,
        onClick = pill.onClick
    )
}

/**
 * Concept pills on a single line; if they don't all fit, a "Linked to N concepts ⌄" chip instead.
 * Tapping the chip expands to all pills (wrapping onto more lines) plus a chip to collapse again.
 * [extra] is always shown last, after the pills or the chip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConceptPillsOrCount(
    concepts: List<Concept>,
    onConceptClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    extra: ExtraPill? = null
) {
    val colors = LocalWizairdColors.current
    val density = LocalDensity.current
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val pillTextStyle = pixelStyle(8, colors.textLow)
    var expanded by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Pill width = text + 8dp padding each side; 6dp between pills
        val labels = concepts.map { it.name } + listOfNotNull(extra?.label)
        val totalWidthPx = with(density) {
            labels.sumOf { measurer.measure(it, pillTextStyle).size.width + 16.dp.roundToPx() } +
                (labels.size - 1).coerceAtLeast(0) * 6.dp.roundToPx()
        }
        when {
            concepts.isEmpty() || totalWidthPx <= constraints.maxWidth -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                concepts.forEach { c -> ConceptPill(label = c.name, ghost = c.isGhost, onClick = { onConceptClick(c.id) }) }
                extra?.let { ExtraPillView(it) }
            }
            !expanded -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Shorter label when sharing the line with the extra pill
                LinkedCountChip(
                    label = if (extra != null) "${concepts.size} LINKED" else "LINKED TO ${concepts.size} CONCEPTS",
                    expanded = false,
                    onClick = { expanded = true }
                )
                extra?.let { ExtraPillView(it) }
            }
            else -> FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                concepts.forEach { c -> ConceptPill(label = c.name, ghost = c.isGhost, onClick = { onConceptClick(c.id) }) }
                extra?.let { ExtraPillView(it) }
                // Text button to collapse, centered on the pill row
                PixelTextButton(
                    label = "Show less",
                    onClick = { expanded = false },
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
    }
}

/** Concept-pill-styled chip with a chevron (down to expand, up to collapse). */
@Composable
fun LinkedCountChip(label: String, expanded: Boolean, onClick: () -> Unit) {
    val colors = LocalWizairdColors.current
    val interaction = remember { MutableInteractionSource() }
    PixelBox(
        modifier = Modifier.pixelRounded8Clickable(interactionSource = interaction, onClick = onClick),
        fillColor = colors.tertiarySurface,
        borderColor = Color.Transparent,
        cornerStyle = PixelCornerStyle.Rounded8
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                label,
                style = pixelStyle(8, colors.textLow),
                modifier = Modifier.offset(y = (-1).dp)
            )
            androidx.compose.foundation.Image(
                painter = painterResource(id = com.wizaird.app.R.drawable.ic_chevron_down),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.textLow),
                modifier = Modifier
                    .size(10.dp)
                    .graphicsLayer { rotationZ = if (expanded) 180f else 0f }
            )
        }
    }
}

/** One row in a [ConceptSectionCard]. */
data class SectionRow(
    val title: String,
    val subtitle: String? = null,
    val titleIsDate: Boolean = false,     // dates use the small pixel font (as on note cards)
    val subtitleIsDate: Boolean = false,
    val iconAsset: String? = null,        // small icon before the title
    val muted: Boolean = false,           // ghosts / deleted items
    val onClick: (() -> Unit)? = null
)

/**
 * Section label + card with a list of rows, matching the App Settings sections
 * (textXLow label, tertiarySurface card, thin dividers between rows).
 */
@Composable
fun ConceptSectionCard(label: String, emptyText: String, rows: List<SectionRow>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val svgLoader = rememberSvgLoader()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = pixelStyle(12, colors.textXLow), modifier = Modifier.offset(y = (-2).dp))
        PixelBox(
            modifier = Modifier.fillMaxWidth(),
            fillColor = colors.tertiarySurface,
            borderColor = Color.Transparent,
            cornerStyle = PixelCornerStyle.Rounded8
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                if (rows.isEmpty()) {
                    Text(
                        emptyText,
                        style = minecraftStyle(12, colors.textXLow),
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .offset(y = (-2).dp)
                    )
                }
                rows.forEach { row ->
                    val interaction = remember { MutableInteractionSource() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (row.onClick != null) Modifier.pixelRounded8Clickable(interactionSource = interaction, onClick = row.onClick) else Modifier)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val titleColor = if (row.muted) colors.textXLow else colors.textLow
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (row.iconAsset != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data("file:///android_asset/pixelarticons/${row.iconAsset}")
                                            .build(),
                                        imageLoader = svgLoader,
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(titleColor),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    row.title,
                                    style = if (row.titleIsDate) pixelStyle(8, colors.secondaryIconSoft) else pixelStyle(12, titleColor),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.offset(y = (-2).dp)
                                )
                            }
                            if (!row.subtitle.isNullOrBlank()) {
                                Text(
                                    row.subtitle,
                                    style = if (row.subtitleIsDate) pixelStyle(8, colors.secondaryIconSoft)
                                            else minecraftStyle(12, colors.secondaryIconSoft),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
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

/** Plain text from a note's HTML body, for previews. */
private fun htmlToPlainText(html: String): String =
    try {
        android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim()
    } catch (e: Exception) {
        html
    }

// ── Concept page (read-only info page) ────────────────────────────────────────

private val ConceptPageTopPadding = 28.dp
private val StickyIndexTop = 8.dp
private val IndexHeaderHeight = 40.dp
private val IndexChevronBox = 36.dp  // chevron column on every index row (header too), so chevrons line up

/** Light divider — same as App Settings (1dp, 35% textXLow). */
@Composable
private fun ConceptDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalWizairdColors.current.textXLow.copy(alpha = 0.35f))
    )
}

/**
 * Expandable INDEX card (note card style). Lists the main headings; a chevron on a row opens its subheadings.
 * The sticky copy at the top of the page also gets the jump buttons ([anchors]) and scrolls inside itself.
 */
@Composable
private fun ConceptIndexCard(
    sections: List<Pair<MarkdownHeading, List<MarkdownHeading>>>,
    topLevel: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    expandedSubs: androidx.compose.runtime.snapshots.SnapshotStateList<Int>,
    onHeading: (MarkdownHeading) -> Unit,
    modifier: Modifier = Modifier,
    anchors: (@Composable () -> Unit)? = null,
    scrollable: Boolean = false
) {
    val colors = LocalWizairdColors.current
    @Composable
    fun Chevron(up: Boolean, tint: Color, contentDescription: String) {
        androidx.compose.foundation.Image(
            painter = painterResource(id = com.wizaird.app.R.drawable.ic_chevron_down),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer { rotationZ = if (up) 180f else 0f }
        )
    }
    PixelBox(
        modifier = modifier.fillMaxWidth(),
        fillColor = colors.userBubble,
        borderColor = Color.Transparent,
        cornerStyle = PixelCornerStyle.Rounded
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val headerInteraction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IndexHeaderHeight)
                    .pixelRoundedClickable(interactionSource = headerInteraction, onClick = onToggle)
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("INDEX", style = pixelStyle(12, colors.textHigh), modifier = Modifier.weight(1f).offset(y = (-2).dp))
                Box(modifier = Modifier.width(IndexChevronBox), contentAlignment = Alignment.Center) {
                    Chevron(up = expanded, tint = colors.textHigh, contentDescription = if (expanded) "Collapse index" else "Expand index")
                }
            }
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (scrollable) Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()) else Modifier)
                        .padding(start = 4.dp, end = 4.dp, bottom = 8.dp)
                ) {
                    sections.forEach { (heading, subs) ->
                        val open = heading.line in expandedSubs
                        val rowInteraction = remember { MutableInteractionSource() }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 34.dp)
                                .pixelRounded8Clickable(interactionSource = rowInteraction) { onHeading(heading) }
                                .padding(start = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                heading.title,
                                style = minecraftStyle(13, colors.textLow).copy(lineHeight = (13 * 1.4f).sp),
                                modifier = Modifier.weight(1f).padding(vertical = 6.dp).offset(y = (-2).dp)
                            )
                            if (subs.isNotEmpty()) {
                                val chevronInteraction = remember { MutableInteractionSource() }
                                Box(
                                    modifier = Modifier
                                        .size(width = IndexChevronBox, height = 34.dp)
                                        .pixelRounded8Clickable(interactionSource = chevronInteraction) {
                                            if (open) expandedSubs.remove(heading.line) else expandedSubs.add(heading.line)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Chevron(up = open, tint = colors.textXLow, contentDescription = if (open) "Hide subheadings" else "Show subheadings")
                                }
                            } else {
                                Spacer(modifier = Modifier.width(IndexChevronBox))
                            }
                        }
                        if (open) subs.forEach { sub ->
                            val subInteraction = remember { MutableInteractionSource() }
                            Text(
                                sub.title,
                                style = minecraftStyle(12, colors.textXLow).copy(lineHeight = (12 * 1.4f).sp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 30.dp)
                                    .pixelRounded8Clickable(interactionSource = subInteraction) { onHeading(sub) }
                                    .padding(start = (10 + 14 * (sub.level - topLevel)).dp, end = IndexChevronBox, top = 6.dp, bottom = 6.dp)
                                    .offset(y = (-2).dp)
                            )
                        }
                    }
                    if (anchors != null) {
                        Box(modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 4.dp)) { anchors() }
                    }
                }
            }
        }
    }
}

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
        // Index: built from the article's headings on the fly (no AI, nothing stored), so older concepts get it too.
        // Main headings (the biggest level used) are listed; deeper headings sit under them behind a chevron.
        val articleHeadings = remember(concept.body) { markdownHeadings(concept.body) }
        val indexTopLevel = articleHeadings.minOfOrNull { it.level } ?: 1
        val indexSections = remember(articleHeadings) {
            val out = mutableListOf<Pair<MarkdownHeading, MutableList<MarkdownHeading>>>()
            articleHeadings.forEach { h ->
                if (h.level == indexTopLevel || out.isEmpty()) out.add(h to mutableListOf()) else out.last().second.add(h)
            }
            out
        }
        val hasIndex = articleHeadings.size >= 2
        // The article split at its main headings, so each section can end with a divider
        val articleSections = remember(concept.body, indexSections) {
            val lines = concept.body.lines()
            val starts = (listOf(0) + indexSections.map { it.first.line }).distinct().sorted()
            starts.mapIndexed { i, start ->
                val end = starts.getOrNull(i + 1) ?: lines.size
                start to lines.subList(start, end).joinToString("\n").trim('\n', ' ')
            }.filter { it.second.isNotBlank() }
        }
        var scrollContentCoords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
        val sectionTops = remember { mutableStateMapOf<Int, Float>() }      // article section start line → top y in the scroll content
        val headingTops = remember { mutableStateMapOf<Int, Pair<Int, Float>>() }  // heading line → (section start line, y inside that section)
        var indexBottom by remember { mutableStateOf(Float.MAX_VALUE) }   // bottom of the in-page index card in the scroll content
        var indexExpanded by remember { mutableStateOf(true) }
        var stickyExpanded by remember { mutableStateOf(false) }
        // Main headings (by line) showing their subheadings — all open by default
        val expandedSubs = remember(concept.body) {
            mutableStateListOf<Int>().apply { addAll(indexSections.filter { it.second.isNotEmpty() }.map { it.first.line }) }
        }
        val stickyVisible by remember { derivedStateOf { hasIndex && scrollState.value > indexBottom } }
        LaunchedEffect(stickyVisible) { if (!stickyVisible) stickyExpanded = false }
        val density = LocalDensity.current
        // Space a jump target leaves at the top: room for the collapsed sticky index (when there is one) plus a small gap
        val jumpInset = with(density) { (if (hasIndex) StickyIndexTop + IndexHeaderHeight + 12.dp else 12.dp).toPx() }
        val contentPadTop = with(density) { ConceptPageTopPadding.toPx() }
        fun scrollToContentY(y: Float) {
            scope.launch { scrollState.animateScrollTo((y - jumpInset).toInt().coerceAtLeast(0)) }
        }
        fun jumpTo(section: String) {
            sectionOffsets[section]?.let { y -> scrollToContentY(y + contentPadTop) }
        }
        fun jumpToHeading(heading: MarkdownHeading) {
            stickyExpanded = false
            val (sectionStart, y) = headingTops[heading.line] ?: return
            val top = sectionTops[sectionStart] ?: return
            scrollToContentY(top + y)
        }
        val anchorChips: @Composable () -> Unit = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val anchorFill = colors.secondaryButton
                ConceptPill(label = "RELATED ${related.size}", fillColor = anchorFill, onClick = { stickyExpanded = false; jumpTo("related") })
                ConceptPill(label = "CHATS ${connectedChats.size + deletedChatSources.size}", fillColor = anchorFill, onClick = { stickyExpanded = false; jumpTo("chats") })
                ConceptPill(label = "INSIGHTS ${insightSources.size}", fillColor = anchorFill, onClick = { stickyExpanded = false; jumpTo("insights") })
                ConceptPill(label = "NOTES ${taggedNotes.size}", fillColor = anchorFill, onClick = { stickyExpanded = false; jumpTo("notes") })
            }
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
                            PixelOptionsMenuItem("Delete", iconAsset = "delete.svg", tint = colors.warn, onClick = {
                                showMenu = false
                                showDeleteDialog = true
                            })
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .onGloballyPositioned { scrollContentCoords = it }
                    .padding(start = 16.dp, end = 16.dp, top = ConceptPageTopPadding, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Title, date, ghost actions, jump buttons
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "CONCEPT",
                        style = pixelStyle(8, colors.secondaryIconSoft),
                        modifier = Modifier.offset(y = (-2).dp)
                    )
                    Text(
                        concept.name,
                        style = minecraftStyle(20, if (concept.isGhost) colors.secondaryIconSoft else colors.textHigh).copy(
                            fontWeight = FontWeight.Bold,
                            lineHeight = (20 * 1.3f).sp
                        ),
                        modifier = Modifier.offset(y = (-2).dp)
                    )
                    if (concept.isGhost) {
                        // Ghosts turn into full concepts on their own once they get content
                        Text(
                            "GHOST CONCEPT",
                            style = pixelStyle(8, colors.secondaryIconSoft),
                            modifier = Modifier.offset(y = (-2).dp)
                        )
                    }
                    // Jump buttons: skip past a long article to what this concept connects to (not on ghosts)
                    // Extra room above the jump buttons so they sit as far below the title as the summary sits below them
                    if (!concept.isGhost) Box(modifier = Modifier.padding(top = 8.dp)) { anchorChips() }
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Text(
                            "NO DESCRIPTION OR ARTICLE YET",
                            style = pixelStyle(14, colors.secondaryIconSoft),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.offset(y = (-2).dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PixelButtonLarge(
                                label = "GENERATE",
                                primary = true,
                                modifier = Modifier.weight(1f),
                                onClick = { ConceptGeneration.start(context, conceptId) }
                            )
                            PixelButtonLarge(
                                label = "EDIT MANUALLY",
                                primary = false,
                                modifier = Modifier.weight(1f),
                                onClick = onEdit
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        if (concept.intro.isNotBlank()) {
                            Text(
                                concept.intro,
                                style = minecraftStyle(14, colors.textHigh).copy(lineHeight = (14 * 1.6f).sp),
                                modifier = Modifier.offset(y = (-2).dp)
                            )
                        }
                        if (hasIndex) {
                            ConceptIndexCard(
                                sections = indexSections,
                                topLevel = indexTopLevel,
                                expanded = indexExpanded,
                                onToggle = { indexExpanded = !indexExpanded },
                                expandedSubs = expandedSubs,
                                onHeading = ::jumpToHeading,
                                modifier = Modifier.onGloballyPositioned { coords ->
                                    scrollContentCoords?.takeIf { it.isAttached }?.let {
                                        indexBottom = it.localPositionOf(coords, Offset.Zero).y + coords.size.height
                                    }
                                }
                            )
                        }
                        // Article, one block per main section, each followed by a light divider
                        articleSections.forEachIndexed { sectionIndex, (startLine, text) ->
                            val nextStart = articleSections.getOrNull(sectionIndex + 1)?.first ?: Int.MAX_VALUE
                            val inSection = articleHeadings.filter { it.line in startLine until nextStart }
                            MarkdownText(
                                markdown = text,
                                style = minecraftStyle(13, colors.secondaryIcon).copy(lineHeight = (13 * 1.6f).sp),
                                modifier = Modifier.onGloballyPositioned { coords ->
                                    scrollContentCoords?.takeIf { it.isAttached }?.let {
                                        sectionTops[startLine] = it.localPositionOf(coords, Offset.Zero).y
                                    }
                                },
                                onHeadingsLaidOut = { tops ->
                                    // Headings come back in the same order as in the full article
                                    tops.forEachIndexed { i, (_, y) -> inSection.getOrNull(i)?.let { headingTops[it.line] = startLine to y } }
                                }
                            )
                            ConceptDivider()
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

                // Sections — same card style as the App Settings sections. A ghost has nothing linked.
                // Related concepts — concept pills; no card
                if (!concept.isGhost) Column(
                    modifier = Modifier.onGloballyPositioned { sectionOffsets["related"] = it.positionInParent().y.toInt() },
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("RELATED CONCEPTS", style = pixelStyle(12, colors.textXLow), modifier = Modifier.offset(y = (-2).dp))
                    if (related.isEmpty()) {
                        Text(
                            "No related concepts",
                            style = minecraftStyle(12, colors.secondaryIconSoft),
                            modifier = Modifier.offset(y = (-2).dp)
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            related.forEach { r -> ConceptPill(label = r.name, ghost = r.isGhost, onClick = { onConceptClick(r.id) }) }
                        }
                    }
                }

                if (!concept.isGhost) ConceptSectionCard(
                    label = "CHATS",
                    emptyText = "No chats linked to this concept yet",
                    rows = connectedChats.map { chat ->
                        SectionRow(
                            title = chat.title.ifBlank { "Untitled chat" },
                            iconAsset = "message.svg",
                            subtitle = chat.formattedCreatedAt(),
                            subtitleIsDate = true,
                            onClick = { onChatClick(chat.projectId, chat.id) }
                        )
                    } + deletedChatSources.map { src ->
                        SectionRow(title = "Deleted chat", iconAsset = "message.svg", subtitle = src.snippet, muted = true)
                    },
                    modifier = Modifier.onGloballyPositioned { sectionOffsets["chats"] = it.positionInParent().y.toInt() }
                )

                if (!concept.isGhost) ConceptSectionCard(
                    label = "INSIGHTS",
                    emptyText = "No insights linked to this concept yet",
                    rows = insightSources.map { src ->
                        val insight = insights.firstOrNull { it.id == src.insightId }
                        SectionRow(
                            title = insight?.formattedCreatedAt() ?: "Deleted insight",
                            titleIsDate = insight != null,
                            subtitle = src.snippet,
                            muted = insight == null,
                            onClick = insight?.let { { onInsightClick(it.projectId, it.id) } }
                        )
                    },
                    modifier = Modifier.onGloballyPositioned { sectionOffsets["insights"] = it.positionInParent().y.toInt() }
                )

                if (!concept.isGhost) ConceptSectionCard(
                    label = "NOTES",
                    emptyText = "No notes linked to this concept yet",
                    rows = taggedNotes.map { note ->
                        SectionRow(
                            title = note.createdAt,
                            titleIsDate = true,
                            subtitle = htmlToPlainText(note.body),
                            onClick = { onNoteClick(note.id) }
                        )
                    },
                    modifier = Modifier.onGloballyPositioned { sectionOffsets["notes"] = it.positionInParent().y.toInt() }
                )
            }

            // Sticky index: once the index card scrolls out of view it stays collapsed at the top;
            // expanded, it also has the jump buttons at the bottom
            if (stickyVisible) Box(
                // Page-colored strip above the card so the article doesn't show there; below it, the text slides under the card
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(colors.background)
                    .padding(start = 16.dp, end = 16.dp, top = StickyIndexTop)
            ) {
                ConceptIndexCard(
                    sections = indexSections,
                    topLevel = indexTopLevel,
                    expanded = stickyExpanded,
                    onToggle = { stickyExpanded = !stickyExpanded },
                    expandedSubs = expandedSubs,
                    onHeading = ::jumpToHeading,
                    anchors = if (concept.isGhost) null else anchorChips,
                    scrollable = true
                )
            }
            }
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
                aliases = com.wizaird.app.data.mergeAliases(aliases, result.aliases, name)
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
                    // Ghost → full happens in upsertConcept once the concept has content
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
                                borderColor = if (isSelected) colors.coral else Color.Transparent,
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

/**
 * Bottom sheet for linking concepts to a chat, insight or note: every concept in the project with a checkbox.
 * Linked ones are listed first (order fixed when the sheet opens); SAVE applies the changes.
 */
@Composable
fun LinkConceptsBottomSheet(
    concepts: List<Concept>,
    initiallyLinked: Set<String>,
    onSave: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalWizairdColors.current
    var selected by remember { mutableStateOf(initiallyLinked) }
    val ordered = remember(concepts) {
        concepts.filter { it.id in initiallyLinked } + concepts.filter { it.id !in initiallyLinked }
    }

    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val visible = if (q.isEmpty()) ordered
                  else ordered.filter { it.name.lowercase().contains(q) || it.aliases.lowercase().contains(q) }

    PixelBottomSheet(onDismiss = onDismiss, fillColor = colors.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "LINKED CONCEPTS",
                style = pixelStyle(12, colors.textHigh),
                modifier = Modifier
                    .weight(1f)
                    .offset(y = (-2).dp)
            )
            PixelButtonSmall(label = "SAVE", primary = true, onClick = { onSave(selected) })
        }
        // Search — same field as the Concepts tab
        PixelBox(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .padding(bottom = 8.dp),
            fillColor = colors.tertiarySurface,
            borderColor = Color.Transparent,
            cornerStyle = PixelCornerStyle.Rounded8
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                textStyle = minecraftStyle(12, colors.textHigh),
                singleLine = true,
                cursorBrush = SolidColor(colors.textHigh),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Search", style = minecraftStyle(12, colors.textXLow))
                    inner()
                }
            )
        }
        if (visible.isEmpty()) {
            Text(
                if (ordered.isEmpty()) "No concepts in this project yet" else "No results found",
                style = minecraftStyle(12, colors.secondaryIconSoft),
                modifier = Modifier
                    .padding(start = 8.dp, top = 4.dp, bottom = 16.dp)
                    .offset(y = (-2).dp)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            visible.forEach { concept ->
                PixelCheckRow(
                    label = concept.name,
                    checked = concept.id in selected,
                    onToggle = { selected = if (concept.id in selected) selected - concept.id else selected + concept.id },
                    selectedFill = colors.secondaryButton,  // sheet = app background, search = tertiarySurface
                    muted = concept.isGhost
                )
            }
        }
    }
}

/**
 * Checkbox row used in the concept pickers. No orange: selected = filled row with a dark
 * checked box and strong text; unselected = no fill, soft box and text-low label.
 */
@Composable
fun PixelCheckRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    selectedFill: Color,
    muted: Boolean = false  // ghosts
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val svgLoader = rememberSvgLoader()
    val interaction = remember { MutableInteractionSource() }
    val contentColor = when {
        muted -> colors.textXLow
        checked -> colors.secondaryIcon
        else -> colors.textLow
    }
    PixelBox(
        modifier = Modifier
            .fillMaxWidth()
            .pixelRoundedClickable(interactionSource = interaction, onClick = onToggle),
        fillColor = if (checked) selectedFill else Color.Transparent,
        borderColor = Color.Transparent,
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
                    .data("file:///android_asset/pixelarticons/${if (checked) "checkbox-on.svg" else "checkbox.svg"}")
                    .build(),
                imageLoader = svgLoader,
                contentDescription = null,
                colorFilter = ColorFilter.tint(contentColor),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = pixelStyle(10, contentColor),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.offset(y = (-2).dp)
            )
        }
    }
}

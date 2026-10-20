package com.wizaird.app.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wizaird.app.data.Concept
import com.wizaird.app.data.ConceptExtractor
import com.wizaird.app.data.ExtractedConcept
import com.wizaird.app.data.ExtractionStatus
import com.wizaird.app.data.StoredInsight
import com.wizaird.app.ui.theme.*

/**
 * Per-card UI state for concept extraction. [awaitingResult] is only set on the card that
 * started the extraction, so only that card opens the popup when it finishes.
 */
@Stable
class ConceptExtractionUi {
    var showSheet by mutableStateOf(false)
    var confirmRerun by mutableStateOf(false)
    var awaitingResult by mutableStateOf(false)
    var noneFoundFlash by mutableStateOf(false)  // brief "no new concepts" pill where there's no toast

    /** Starts extraction, asking first if this insight has been extracted before. */
    fun request(context: android.content.Context, insight: StoredInsight) {
        if (insight.conceptExtraction != null) confirmRerun = true else run(context, insight.id)
    }

    fun run(context: android.content.Context, insightId: String) {
        awaitingResult = true
        ConceptExtractor.start(context, insightId)
    }
}

@Composable
fun rememberConceptExtractionUi(insightId: String?): ConceptExtractionUi = remember(insightId) { ConceptExtractionUi() }

/** Concept icon for an insight's action row. Dimmed while extracting. */
@Composable
fun ExtractConceptsIcon(insight: StoredInsight?, ui: ConceptExtractionUi) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val statuses by ConceptExtractor.status.collectAsState()
    val running = insight != null && statuses[insight.id] == ExtractionStatus.Running
    val enabled = insight != null && !running
    val interaction = remember { MutableInteractionSource() }
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data("file:///android_asset/pixelarticons/book-open.svg")
            .build(),
        imageLoader = rememberSvgLoader(),
        contentDescription = "Extract concepts",
        colorFilter = ColorFilter.tint(if (enabled) colors.textXLow else colors.textXLow.copy(alpha = 0.35f)),
        modifier = Modifier
            .size(20.dp)
            .then(
                if (enabled) Modifier.pixelRounded8ClickableOversize(interactionSource = interaction) {
                    insight?.let { ui.request(context, it) }
                } else Modifier
            )
    )
}

/**
 * Dialogs for one insight: the "extract again?" confirmation and the new-concepts popup.
 * Opens the popup when an extraction this card started finds new concepts.
 * [onToast] null = no toast on this screen; a short pill is shown instead.
 */
@Composable
fun ConceptExtractionHost(insight: StoredInsight, ui: ConceptExtractionUi, onToast: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val statuses by ConceptExtractor.status.collectAsState()
    val status = statuses[insight.id]

    LaunchedEffect(status, ui.awaitingResult) {
        if (!ui.awaitingResult) return@LaunchedEffect
        when (status) {
            is ExtractionStatus.Done -> {
                ui.awaitingResult = false
                if (status.found > 0) ui.showSheet = true
                else if (status.linked == 0) {
                    if (onToast != null) onToast("NO NEW CONCEPTS FOUND") else ui.noneFoundFlash = true
                }
            }
            ExtractionStatus.Failed -> {
                ui.awaitingResult = false
                onToast?.invoke("COULD NOT EXTRACT CONCEPTS")
            }
            else -> {}
        }
    }

    LaunchedEffect(ui.noneFoundFlash) {
        if (ui.noneFoundFlash) {
            kotlinx.coroutines.delay(2000)
            ui.noneFoundFlash = false
        }
    }

    if (ui.confirmRerun) {
        PixelConfirmationDialog(
            title = "EXTRACT AGAIN?",
            message = "Concepts have already been extracted from this insight. Run the extraction again?",
            confirmLabel = "EXTRACT",
            onConfirm = {
                ui.confirmRerun = false
                ui.run(context, insight.id)
            },
            onDismiss = { ui.confirmRerun = false }
        )
    }

    val pending = insight.conceptExtraction?.pending.orEmpty()
    if (ui.showSheet && pending.isNotEmpty()) {
        NewConceptsSheet(
            items = pending,
            onGenerate = { chosen ->
                ui.showSheet = false
                ConceptExtractor.generate(context, insight.id, chosen)
            },
            onDiscard = {
                ui.showSheet = false
                ConceptExtractor.discard(context, insight.id)
            },
            onDismiss = { ui.showSheet = false }  // closing keeps the found concepts
        )
    }
}

/** The extra pill after an insight's concept pills: extracting / failed / N new concepts. */
@Composable
fun extractionPill(insight: StoredInsight, ui: ConceptExtractionUi): ExtraPill? {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val statuses by ConceptExtractor.status.collectAsState()
    val pendingCount = insight.conceptExtraction?.pending?.size ?: 0
    return when {
        statuses[insight.id] == ExtractionStatus.Running ->
            ExtraPill("EXTRACTING CONCEPTS...", textColor = colors.textXLow)
        statuses[insight.id] == ExtractionStatus.Failed ->
            ExtraPill("EXTRACTION FAILED · RETRY", textColor = colors.warn, onClick = { ui.run(context, insight.id) })
        pendingCount > 0 ->
            ExtraPill(
                "$pendingCount NEW CONCEPT${if (pendingCount == 1) "" else "S"}",
                fillColor = colors.tertiarySurface,  // search-bar color; strong text sets it apart from concept pills
                textColor = colors.secondaryIcon,
                onClick = { ui.showSheet = true }
            )
        ui.noneFoundFlash -> ExtraPill("NO NEW CONCEPTS FOUND", textColor = colors.textXLow)
        else -> null
    }
}

/** All of an insight's concept tags, wrapping onto as many lines as needed (full insight views). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InsightConceptTags(
    insight: StoredInsight,
    concepts: List<Concept>,
    ui: ConceptExtractionUi,
    onConceptClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val extra = extractionPill(insight, ui)
    if (concepts.isEmpty() && extra == null) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        concepts.forEach { c -> ConceptPill(label = c.name, ghost = c.isGhost, onClick = { onConceptClick(c.id) }) }
        extra?.let { ExtraPillView(it) }
    }
}


/**
 * Popup listing the new concepts found in an insight. Checked ones are generated, the rest ignored.
 * Closing keeps them for later; DISCARD (after a confirmation) drops them.
 */
@Composable
fun NewConceptsSheet(
    items: List<ExtractedConcept>,
    onGenerate: (List<ExtractedConcept>) -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalWizairdColors.current
    var selected by remember(items) { mutableStateOf(emptySet<String>()) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val allSelected = items.isNotEmpty() && selected.size == items.size
    // Inside a dialog window the nav-bar inset reads as 0, so take it from the activity's window
    val navBarBottom = rememberActivityNavBarBottom()

    // Own window so it covers the whole screen wherever the insight card is
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        // The sheet draws its own scrim; drop the dialog's
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        PixelBottomSheet(onDismiss = onDismiss, fillColor = colors.background) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "NEW CONCEPTS",
                    style = pixelStyle(12, colors.textHigh),
                    modifier = Modifier
                        .weight(1f)
                        .offset(y = (-2).dp)
                )
                PixelButtonSmall(label = "DISCARD", warn = true, onClick = { confirmDiscard = true })
            }
            Text(
                "Check the ones to generate. The rest will be ignored.",
                style = minecraftStyle(12, colors.textLow),
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                    .offset(y = (-2).dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SelectAllRow(
                    checked = allSelected,
                    onToggle = { selected = if (allSelected) emptySet() else items.map { it.name }.toSet() }
                )
                items.forEach { item ->
                    ExtractedConceptRow(
                        item = item,
                        checked = item.name in selected,
                        onToggle = { selected = if (item.name in selected) selected - item.name else selected + item.name }
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp + navBarBottom),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PixelButtonLarge(
                    label = "GENERATE SELECTED (${selected.size})",
                    enabled = selected.isNotEmpty(),
                    cutColor = colors.background,
                    onClick = { onGenerate(items.filter { it.name in selected }) }
                )
            }
        }

        if (confirmDiscard) {
            PixelConfirmationDialog(
                title = "DISCARD CONCEPTS?",
                message = "The ${items.size} new concept${if (items.size == 1) "" else "s"} found in this insight will be removed. You can extract again later.",
                confirmLabel = "DISCARD",
                isDestructive = true,
                onConfirm = {
                    confirmDiscard = false
                    onDiscard()
                },
                onDismiss = { confirmDiscard = false }
            )
        }
    }
}

/** Small "Select all" check above the list, lined up with and colored like the rows' checks. */
@Composable
private fun SelectAllRow(checked: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val interaction = remember { MutableInteractionSource() }
    val contentColor = if (checked) colors.secondaryIcon else colors.textLow
    Row(
        modifier = Modifier
            .padding(start = 12.dp, bottom = 2.dp)
            .pixelRounded8Clickable(interactionSource = interaction, onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data("file:///android_asset/pixelarticons/${if (checked) "checkbox-on.svg" else "checkbox.svg"}")
                .build(),
            imageLoader = rememberSvgLoader(),
            contentDescription = null,
            colorFilter = ColorFilter.tint(contentColor),
            modifier = Modifier.size(18.dp)
        )
        Text(
            "Select all",
            style = minecraftStyle(10, contentColor),
            modifier = Modifier.offset(y = (-1).dp)
        )
    }
}

/** Checkbox row with the concept name and its one-line summary. Same look as [PixelCheckRow]. */
@Composable
private fun ExtractedConceptRow(item: ExtractedConcept, checked: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val interaction = remember { MutableInteractionSource() }
    val contentColor = if (checked) colors.secondaryIcon else colors.textLow
    PixelBox(
        modifier = Modifier
            .fillMaxWidth()
            .pixelRoundedClickable(interactionSource = interaction, onClick = onToggle),
        fillColor = if (checked) colors.secondaryButton else Color.Transparent,
        borderColor = Color.Transparent,
        cornerStyle = PixelCornerStyle.Rounded
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("file:///android_asset/pixelarticons/${if (checked) "checkbox-on.svg" else "checkbox.svg"}")
                    .build(),
                imageLoader = rememberSvgLoader(),
                contentDescription = null,
                colorFilter = ColorFilter.tint(contentColor),
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = item.name,
                    style = pixelStyle(10, contentColor),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.offset(y = (-2).dp)
                )
                if (item.summary.isNotBlank()) {
                    Text(
                        text = item.summary,
                        style = minecraftStyle(12, colors.textXLow),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Bottom nav-bar inset of the activity window (dialogs report 0 for it). */
@Composable
private fun rememberActivityNavBarBottom(): androidx.compose.ui.unit.Dp {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    return remember(context) {
        var c = context
        while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
        val decor = (c as? android.app.Activity)?.window?.decorView
        val px = decor?.let {
            androidx.core.view.ViewCompat.getRootWindowInsets(it)
                ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())?.bottom
        } ?: 0
        with(density) { px.toDp() }
    }
}

package com.wizaird.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.text.HtmlCompat
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditorDefaults
import com.mohamedrejeb.richeditor.ui.material3.OutlinedRichTextEditor
import com.wizaird.app.data.NoteData
import com.wizaird.app.data.getNoteById
import com.wizaird.app.data.newNote
import com.wizaird.app.data.projectsFlow
import com.wizaird.app.data.upsertNote
import com.wizaird.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(
    projectId: String,
    noteId: String,           // "new" for a new note, otherwise an existing note id
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val isCoverScreen = rememberIsCoverScreen()
    val scope = rememberCoroutineScope()

    val projects by projectsFlow(context).collectAsState(initial = emptyList())
    val project = projects.firstOrNull { it.id == projectId }
    val projectName = project?.name?.ifEmpty { "UNNAMED PROJECT" } ?: "UNNAMED PROJECT"

    // Rich text editor state
    val richTextState = rememberRichTextState()
    
    // The in-memory note we're editing. Null until loaded.
    var note by remember { mutableStateOf<NoteData?>(null) }
    var initialHtml by remember { mutableStateOf("") }
    var isLoaded by remember { mutableStateOf(false) }

    // Load existing note or initialise a new one
    LaunchedEffect(noteId) {
        isLoaded = false
        if (noteId == "new") {
            val fresh = newNote(projectId)
            note = fresh
            richTextState.setHtml("")
            kotlinx.coroutines.delay(100)
            initialHtml = richTextState.toHtml()
            isLoaded = true
        } else {
            val loaded = getNoteById(context, noteId)
            if (loaded != null) {
                note = loaded
                // Load the HTML content into the rich text state
                richTextState.setHtml(loaded.body)
                // Wait a frame for the state to settle, then capture initial HTML
                kotlinx.coroutines.delay(100)
                initialHtml = richTextState.toHtml()
                isLoaded = true
            } else {
                // Fallback — treat as new if id not found
                val fresh = newNote(projectId)
                note = fresh
                richTextState.setHtml("")
                kotlinx.coroutines.delay(100)
                initialHtml = richTextState.toHtml()
                isLoaded = true
            }
        }
    }

    // Track changes - observe annotatedString which updates on every keystroke
    var currentHtml by remember { mutableStateOf("") }
    
    LaunchedEffect(richTextState.annotatedString) {
        if (isLoaded) {
            currentHtml = richTextState.toHtml()
        }
    }
    
    val hasUnsavedChanges = isLoaded && currentHtml.isNotEmpty() && currentHtml != initialHtml
    
    var showTextSizeSelector by remember { mutableStateOf(false) }
    var selectedTextSize by remember { mutableStateOf(14) }

    var showDiscardDialog by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    val svgLoader = remember {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }

    fun saveAndBack() {
        val current = note ?: return
        scope.launch {
            val now = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())
            upsertNote(
                context,
                current.copy(
                    body = richTextState.toHtml(),
                    updatedAt = now,
                    // Keep original createdAt; only set it if this is truly new (empty)
                    createdAt = current.createdAt.ifEmpty { now }
                )
            )
            onBack()
        }
    }

    fun attemptBack() {
        if (hasUnsavedChanges) showDiscardDialog = true else onBack()
    }

    // Intercept system back when there are unsaved changes
    BackHandler(enabled = hasUnsavedChanges) {
        showDiscardDialog = true
    }

    // Request focus so the keyboard appears when the screen opens
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
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
            // Status bar space - not needed on cover screen
            if (!isCoverScreen) {
                Spacer(modifier = Modifier.height(48.dp))
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Header — back button | project name (centered) | undo + save buttons
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
            ) {
                // Back button — left
                val backInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(40.dp)
                        .drawPixelCircle(
                            fillColor   = colors.secondaryButton,
                            borderColor = Color.Transparent,
                            cutColor    = colors.background
                        )
                        .pixelCircleClickable(interactionSource = backInteraction) { attemptBack() },
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

                // Project name — centered
                Text(
                    text = projectName,
                    style = pixelStyle(10, colors.secondaryIcon),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = (-2).dp)
                )

                // Save (check) button — turns Coral when there are unsaved changes
                val saveInteraction = remember { MutableInteractionSource() }
                PixelBox(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(40.dp)
                        .pixelCircleClickable(
                            interactionSource = saveInteraction,
                            onClick = { saveAndBack() }
                        ),
                    fillColor = if (hasUnsavedChanges) Coral else colors.secondaryButton,
                    borderColor = Color.Transparent,
                    cutColor = colors.background,
                    cornerStyle = PixelCornerStyle.Rounded
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("file:///android_asset/pixelarticons/check.svg")
                                .build(),
                            imageLoader = svgLoader,
                            contentDescription = "Save note",
                            colorFilter = ColorFilter.tint(colors.secondaryIcon),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Note text editor — fills remaining space
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Scrollable editor area
                RichTextEditor(
                    state = richTextState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .focusRequester(focusRequester),
                    textStyle = minecraftStyle(14, colors.textHigh).copy(
                        lineHeight = (14 * 1.6f).sp
                    ),
                    colors = RichTextEditorDefaults.richTextEditorColors(
                        containerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Coral,
                        textColor = colors.textHigh
                    ),
                    placeholder = {
                        Text(
                            text = "Start typing...",
                            style = minecraftStyle(14, colors.secondaryIconSoft).copy(
                                lineHeight = (14 * 1.6f).sp
                            )
                        )
                    }
                )
                
                // Toolbar at bottom (above keyboard) - horizontally scrollable
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.secondaryButton)
                        .horizontalScroll(rememberScrollState())
                        .imePadding(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    // Bold
                    androidx.compose.material3.IconButton(
                        onClick = { richTextState.toggleSpanStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) }
                    ) {
                        Text("B", style = androidx.compose.ui.text.TextStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = colors.secondaryIcon))
                    }
                    
                    // Italic
                    androidx.compose.material3.IconButton(
                        onClick = { richTextState.toggleSpanStyle(androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) }
                    ) {
                        Text("I", style = androidx.compose.ui.text.TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = colors.secondaryIcon))
                    }
                    
                    // Underline
                    androidx.compose.material3.IconButton(
                        onClick = { richTextState.toggleSpanStyle(androidx.compose.ui.text.SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)) }
                    ) {
                        Text("U", style = androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline, color = colors.secondaryIcon))
                    }
                    
                    // Strikethrough
                    androidx.compose.material3.IconButton(
                        onClick = { richTextState.toggleSpanStyle(androidx.compose.ui.text.SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)) }
                    ) {
                        Text("S", style = androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough, color = colors.secondaryIcon))
                    }
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    // H1
                    androidx.compose.material3.IconButton(
                        onClick = { richTextState.toggleSpanStyle(androidx.compose.ui.text.SpanStyle(fontSize = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) }
                    ) {
                        Text("H1", style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = colors.secondaryIcon))
                    }
                    
                    // H2
                    androidx.compose.material3.IconButton(
                        onClick = { richTextState.toggleSpanStyle(androidx.compose.ui.text.SpanStyle(fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) }
                    ) {
                        Text("H2", style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = colors.secondaryIcon))
                    }
                    
                    // H3
                    androidx.compose.material3.IconButton(
                        onClick = { richTextState.toggleSpanStyle(androidx.compose.ui.text.SpanStyle(fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) }
                    ) {
                        Text("H3", style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = colors.secondaryIcon))
                    }
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    // Text size selector
                    androidx.compose.material3.IconButton(
                        onClick = { showTextSizeSelector = true }
                    ) {
                        Text("${selectedTextSize}px", style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = colors.secondaryIcon))
                    }
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    // Bullet list
                    androidx.compose.material3.IconButton(
                        onClick = { richTextState.toggleUnorderedList() }
                    ) {
                        Text("•", style = androidx.compose.ui.text.TextStyle(color = colors.secondaryIcon))
                    }
                    
                    // Numbered list
                    androidx.compose.material3.IconButton(
                        onClick = { richTextState.toggleOrderedList() }
                    ) {
                        Text("1.", style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = colors.secondaryIcon))
                    }
                    
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }

            Spacer(modifier = Modifier.navigationBarsPadding())
        }

        // Text size selector dialog
        if (showTextSizeSelector) {
            TextSizeSelectorDialog(
                currentSize = selectedTextSize,
                onSizeSelected = { size ->
                    selectedTextSize = size
                    richTextState.toggleSpanStyle(androidx.compose.ui.text.SpanStyle(fontSize = size.sp))
                    showTextSizeSelector = false
                },
                onDismiss = { showTextSizeSelector = false }
            )
        }

        // Unsaved changes confirmation dialog
        if (showDiscardDialog) {
            UnsavedChangesDialog(
                onDiscard = {
                    showDiscardDialog = false
                    onBack()
                },
                onKeepEditing = {
                    showDiscardDialog = false
                }
            )
        }
    }
}

@Composable
private fun UnsavedChangesDialog(
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit
) {
    val colors = LocalWizairdColors.current

    Dialog(onDismissRequest = onKeepEditing) {
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
                    text = "UNSAVED CHANGES",
                    style = pixelStyle(12, colors.textHigh),
                    modifier = Modifier.offset(y = (-2).dp)
                )

                Text(
                    text = "You have unsaved changes. Are you sure you want to go back? Your changes will be lost.",
                    style = minecraftStyle(14, colors.textLow).copy(
                        lineHeight = (14 * 1.6f).sp
                    ),
                    modifier = Modifier.offset(y = (-2).dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PixelButtonLarge(
                        label = "DISCARD",
                        primary = false,
                        modifier = Modifier.weight(1f),
                        cutColor = colors.secondarySurface,
                        onClick = onDiscard
                    )
                    PixelButtonLarge(
                        label = "KEEP EDITING",
                        primary = true,
                        modifier = Modifier.weight(1f),
                        cutColor = colors.secondarySurface,
                        onClick = onKeepEditing
                    )
                }
            }
        }
    }
}

@Composable
private fun TextSizeSelectorDialog(
    currentSize: Int,
    onSizeSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalWizairdColors.current
    val sizes = (8..24 step 2).toList() // 8, 10, 12, 14, 16, 18, 20, 22, 24

    Dialog(onDismissRequest = onDismiss) {
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
                    text = "TEXT SIZE",
                    style = pixelStyle(12, colors.textHigh),
                    modifier = Modifier.offset(y = (-2).dp)
                )

                // Size options in a grid
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sizes.chunked(3).forEach { rowSizes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowSizes.forEach { size ->
                                val isSelected = size == currentSize
                                PixelButtonLarge(
                                    label = "${size}px",
                                    primary = isSelected,
                                    modifier = Modifier.weight(1f),
                                    cutColor = colors.secondarySurface,
                                    onClick = { onSizeSelected(size) }
                                )
                            }
                            // Fill remaining space if row is not complete
                            repeat(3 - rowSizes.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

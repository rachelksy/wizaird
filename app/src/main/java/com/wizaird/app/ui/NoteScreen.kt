package com.wizaird.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
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

@Composable
fun NoteScreen(
    projectId: String,
    noteId: String,           // "new" for a new note, otherwise an existing note id
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val scope = rememberCoroutineScope()

    val projects by projectsFlow(context).collectAsState(initial = emptyList())
    val project = projects.firstOrNull { it.id == projectId }
    val projectName = project?.name?.ifEmpty { "UNNAMED PROJECT" } ?: "UNNAMED PROJECT"

    // The in-memory note we're editing. Null until loaded.
    var note by remember { mutableStateOf<NoteData?>(null) }
    var noteText by remember { mutableStateOf("") }
    var initialText by remember { mutableStateOf("") }

    // Load existing note or initialise a new one
    LaunchedEffect(noteId) {
        if (noteId == "new") {
            val fresh = newNote(projectId)
            note = fresh
            noteText = ""
            initialText = ""
        } else {
            val loaded = getNoteById(context, noteId)
            if (loaded != null) {
                note = loaded
                noteText = loaded.body
                initialText = loaded.body
            } else {
                // Fallback — treat as new if id not found
                val fresh = newNote(projectId)
                note = fresh
                noteText = ""
                initialText = ""
            }
        }
    }

    val hasUnsavedChanges = noteText != initialText

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
                    body = noteText,
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
            Spacer(modifier = Modifier.height(48.dp))

            // Header — back button | project name (centered) | save (check) button
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

            // Note text editor — fills remaining space, tapping anywhere focuses it
            BasicTextField(
                value = noteText,
                onValueChange = { noteText = it },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .focusRequester(focusRequester)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                textStyle = minecraftStyle(14, colors.textHigh).copy(
                    lineHeight = (14 * 1.6f).sp
                ),
                cursorBrush = SolidColor(Coral),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (noteText.isEmpty()) {
                            Text(
                                text = "Start typing...",
                                style = minecraftStyle(14, colors.secondaryIconSoft).copy(
                                    lineHeight = (14 * 1.6f).sp
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )

            Spacer(modifier = Modifier.navigationBarsPadding())
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

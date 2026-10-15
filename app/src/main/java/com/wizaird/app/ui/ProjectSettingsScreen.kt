package com.wizaird.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.wizaird.app.data.AiSettings
import com.wizaird.app.data.LearningProgressResult
import com.wizaird.app.data.copyPictureToInternal
import com.wizaird.app.data.generateLearningProgress
import com.wizaird.app.data.projectsFlow
import com.wizaird.app.data.settingsFlow
import com.wizaird.app.data.upsertProject
import com.wizaird.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ProjectSettingsScreen(
    projectId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalWizairdColors.current
    val isCoverScreen = rememberIsCoverScreen()

    val projects by projectsFlow(context).collectAsState(initial = emptyList())
    val project = projects.firstOrNull { it.id == projectId }

    val settings by settingsFlow(context).collectAsState(initial = AiSettings())

    var name by remember(project) { mutableStateOf(project?.name ?: "") }
    var instructions by remember(project) { mutableStateOf(project?.instructions ?: "") }
    var background by remember(project) { mutableStateOf(project?.background ?: "") }
    var learningProgress by remember(project) { mutableStateOf(project?.learningProgress ?: "") }
    var picturePath by remember(project) { mutableStateOf(project?.picturePath ?: "") }
    var prioritizeInstructions by remember(project) { mutableStateOf(project?.prioritizeInstructions ?: false) }

    // Generation config — persisted on generate or save
    var progressInsightCount by remember(project) {
        mutableStateOf((project?.progressInsightCount ?: 20).toString())
    }
    var progressChatCount by remember(project) {
        mutableStateOf((project?.progressChatCount ?: 10).toString())
    }

    // Generation state
    var isGenerating by remember { mutableStateOf(false) }
    var showGenerateDialog by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf("") }
    var showToast by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                picturePath = copyPictureToInternal(context, uri)
            }
        }
    }

    val svgLoader = remember {
        ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
    }

    // Dismiss toast automatically after 3 seconds
    LaunchedEffect(showToast) {
        if (showToast && !isGenerating) {
            kotlinx.coroutines.delay(3000)
            showToast = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                Text(
                    "PROJECT SETTINGS",
                    style = pixelStyle(14, colors.secondaryIcon),
                    modifier = Modifier
                        .weight(1f)
                        .offset(y = (-2).dp)
                )
                PixelButtonSmall(
                    label = "SAVE",
                    primary = true,
                    onClick = {
                        scope.launch {
                            project?.let {
                                upsertProject(
                                    context,
                                    it.copy(
                                        name = name,
                                        instructions = instructions,
                                        background = background,
                                        learningProgress = learningProgress,
                                        picturePath = picturePath,
                                        prioritizeInstructions = prioritizeInstructions,
                                        progressInsightCount = progressInsightCount.toIntOrNull() ?: 20,
                                        progressChatCount = progressChatCount.toIntOrNull() ?: 10
                                    )
                                )
                            }
                            onBack()
                        }
                    }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Project picture
                SettingsField(label = "PROJECT PICTURE") {
                    ProjectPicture(
                        picturePath = picturePath,
                        cutColor = colors.background,
                        onClick = { imagePicker.launch("image/*") }
                    )
                }

                // Project name
                SettingsField(label = "PROJECT NAME") {
                    PixelTextInput(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "MY PROJECT"
                    )
                }

                // AI instructions
                SettingsField(label = "AI INSTRUCTIONS") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp),
                            fillColor = colors.secondarySurface,
                            cornerStyle = PixelCornerStyle.Rounded
                        ) {
                            BasicTextField(
                                value = instructions,
                                onValueChange = { instructions = it },
                                textStyle = minecraftStyle(12, colors.secondaryIcon),
                                cursorBrush = SolidColor(colors.secondaryIcon),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.TopStart) {
                                        if (instructions.isEmpty()) {
                                            Text(
                                                "You are a helpful wizard...",
                                                style = minecraftStyle(12, colors.secondaryIconSoft)
                                            )
                                        }
                                        inner()
                                    }
                                }
                            )
                        }

                        // Prioritize instructions checkbox
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pixelRounded8Clickable(
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { prioritizeInstructions = !prioritizeInstructions },
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(
                                        if (prioritizeInstructions)
                                            "file:///android_asset/pixelarticons/checkbox-on.svg"
                                        else
                                            "file:///android_asset/pixelarticons/checkbox.svg"
                                    )
                                    .build(),
                                imageLoader = svgLoader,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "Prioritize instructions",
                                style = minecraftStyle(12, colors.secondaryIcon)
                            )
                        }
                    }
                }

                // Background
                SettingsField(label = "BACKGROUND") {
                    PixelBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        fillColor = colors.secondarySurface,
                        cornerStyle = PixelCornerStyle.Rounded
                    ) {
                        BasicTextField(
                            value = background,
                            onValueChange = { background = it },
                            textStyle = minecraftStyle(12, colors.secondaryIcon),
                            cursorBrush = SolidColor(colors.secondaryIcon),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.TopStart) {
                                    if (background.isEmpty()) {
                                        Text(
                                            "Background context the AI should know...",
                                            style = minecraftStyle(12, colors.secondaryIconSoft)
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                    }
                }

                // ── Learning Progress ─────────────────────────────────────────
                SettingsField(label = "LEARNING PROGRESS") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                        // Text area
                        PixelBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp),
                            fillColor = colors.secondarySurface,
                            cornerStyle = PixelCornerStyle.Rounded
                        ) {
                            BasicTextField(
                                value = learningProgress,
                                onValueChange = { learningProgress = it },
                                textStyle = minecraftStyle(12, colors.secondaryIcon),
                                cursorBrush = SolidColor(colors.secondaryIcon),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.TopStart) {
                                        if (learningProgress.isEmpty()) {
                                            Text(
                                                "What has been covered so far...",
                                                style = minecraftStyle(12, colors.secondaryIconSoft)
                                            )
                                        }
                                        inner()
                                    }
                                }
                            )
                        }

                        // GENERATE button — secondary, opens config dialog
                        PixelButtonSmall(
                            label = if (isGenerating) "GENERATING..." else "GENERATE",
                            primary = false,
                            onClick = { if (!isGenerating) showGenerateDialog = true }
                        )

                        // Restore rows — only shown when history exists
                        val historySize = project?.learningProgressHistory?.size ?: 0
                        if (historySize > 0) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                project?.learningProgressHistory?.getOrNull(0)?.let { prev ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pixelRounded8Clickable(
                                                interactionSource = remember { MutableInteractionSource() }
                                            ) { learningProgress = prev },
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data("file:///android_asset/pixelarticons/undo.svg")
                                                .build(),
                                            imageLoader = svgLoader,
                                            contentDescription = null,
                                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(colors.secondaryIconSoft),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            "Restore previous version",
                                            style = minecraftStyle(12, colors.secondaryIconSoft),
                                            modifier = Modifier.offset(y = (-1).dp)
                                        )
                                    }
                                }

                                if (historySize > 1) {
                                    project?.learningProgressHistory?.getOrNull(1)?.let { older ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .pixelRounded8Clickable(
                                                    interactionSource = remember { MutableInteractionSource() }
                                                ) { learningProgress = older },
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data("file:///android_asset/pixelarticons/undo.svg")
                                                    .build(),
                                                imageLoader = svgLoader,
                                                contentDescription = null,
                                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(colors.secondaryIconSoft),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                "Restore older version",
                                                style = minecraftStyle(12, colors.secondaryIconSoft),
                                                modifier = Modifier.offset(y = (-1).dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Extra bottom spacing
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }

        // ── Generate config dialog ────────────────────────────────────────────
        if (showGenerateDialog) {
            Dialog(onDismissRequest = { showGenerateDialog = false }) {
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
                            "GENERATE PROGRESS",
                            style = pixelStyle(12, colors.textHigh),
                            modifier = Modifier.offset(y = (-2).dp)
                        )

                        Text(
                            "Choose how many recent insights and chats to include.",
                            style = minecraftStyle(12, colors.textLow).copy(
                                lineHeight = (12 * 1.6f).sp
                            ),
                            modifier = Modifier.offset(y = (-2).dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        SettingsField(label = "INSIGHTS TO INCLUDE") {
                            PixelTextInput(
                                value = progressInsightCount,
                                onValueChange = { v ->
                                    if (v.isEmpty() || (v.all { it.isDigit() } && v.length <= 3))
                                        progressInsightCount = v
                                },
                                placeholder = "20",
                                cornerStyle = PixelCornerStyle.Rounded8
                            )
                        }

                        SettingsField(label = "CHATS TO INCLUDE") {
                            PixelTextInput(
                                value = progressChatCount,
                                onValueChange = { v ->
                                    if (v.isEmpty() || (v.all { it.isDigit() } && v.length <= 3))
                                        progressChatCount = v
                                },
                                placeholder = "10",
                                cornerStyle = PixelCornerStyle.Rounded8
                            )
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
                                onClick = { showGenerateDialog = false }
                            )
                            PixelButtonLarge(
                                label = "GENERATE",
                                primary = true,
                                modifier = Modifier.weight(1f),
                                cutColor = colors.secondarySurface,
                                onClick = {
                                    showGenerateDialog = false
                                    project?.let { proj ->
                                        val iCount = progressInsightCount.toIntOrNull()
                                            ?.coerceIn(1, 100) ?: 20
                                        val cCount = progressChatCount.toIntOrNull()
                                            ?.coerceIn(1, 100) ?: 10
                                        progressInsightCount = iCount.toString()
                                        progressChatCount = cCount.toString()

                                        isGenerating = true
                                        toastMessage = "Generating..."
                                        showToast = true

                                        scope.launch {
                                            val projWithDraft = proj.copy(
                                                learningProgress = learningProgress,
                                                background = background
                                            )
                                            val result = generateLearningProgress(
                                                context = context,
                                                project = projWithDraft,
                                                settings = settings,
                                                insightCount = iCount,
                                                chatCount = cCount
                                            )
                                            isGenerating = false
                                            when (result) {
                                                is LearningProgressResult.Success -> {
                                                    learningProgress = result.summary
                                                    toastMessage = "Learning progress updated"
                                                    showToast = true
                                                }
                                                is LearningProgressResult.Error -> {
                                                    toastMessage = result.message
                                                    showToast = true
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Toast overlay
        PixelToast(
            message = toastMessage,
            visible = showToast,
            isLoading = isGenerating,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

package com.wizaird.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wizaird.app.data.copyPictureToInternal
import com.wizaird.app.data.projectsFlow
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

    val projects by projectsFlow(context).collectAsState(initial = emptyList())
    val project = projects.firstOrNull { it.id == projectId }

    var name by remember(project) { mutableStateOf(project?.name ?: "") }
    var instructions by remember(project) { mutableStateOf(project?.instructions ?: "") }
    var background by remember(project) { mutableStateOf(project?.background ?: "") }
    var learningProgress by remember(project) { mutableStateOf(project?.learningProgress ?: "") }
    var picturePath by remember(project) { mutableStateOf(project?.picturePath ?: "") }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                picturePath = copyPictureToInternal(context, uri)
            }
        }
    }

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
                                        picturePath = picturePath
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

                // AI instructions — fills remaining space
                SettingsField(
                    label = "AI INSTRUCTIONS"
                ) {
                    PixelBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        fillColor = colors.secondarySurface,
                        cornerStyle = PixelCornerStyle.Rounded
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = instructions,
                            onValueChange = { instructions = it },
                            textStyle = minecraftStyle(12, colors.secondaryIcon),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.secondaryIcon),
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
                }

                // Background
                SettingsField(
                    label = "BACKGROUND"
                ) {
                    PixelBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        fillColor = colors.secondarySurface,
                        cornerStyle = PixelCornerStyle.Rounded
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = background,
                            onValueChange = { background = it },
                            textStyle = minecraftStyle(12, colors.secondaryIcon),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.secondaryIcon),
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

                // Learning Progress
                SettingsField(
                    label = "LEARNING PROGRESS"
                ) {
                    PixelBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        fillColor = colors.secondarySurface,
                        cornerStyle = PixelCornerStyle.Rounded
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = learningProgress,
                            onValueChange = { learningProgress = it },
                            textStyle = minecraftStyle(12, colors.secondaryIcon),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.secondaryIcon),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.TopStart) {
                                    if (learningProgress.isEmpty()) {
                                        Text(
                                            "What the user has learned so far...",
                                            style = minecraftStyle(12, colors.secondaryIconSoft)
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

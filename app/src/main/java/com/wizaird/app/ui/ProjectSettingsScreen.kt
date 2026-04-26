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
            PixelBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp),
                fillColor = colors.secondarySurface,
                cornerStyle = PixelCornerStyle.Rounded8
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(start = 12.dp, end = 12.dp),
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
                                cutColor    = colors.secondarySurface
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
                        style = pixelStyle(12, colors.secondaryIcon),
                        modifier = Modifier.offset(y = (-2).dp)
                    )
                }
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

                // AI instructions
                SettingsField(label = "AI INSTRUCTIONS") {
                    PixelBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        fillColor = colors.secondarySurface,
                        cornerStyle = PixelCornerStyle.Rounded8
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = instructions,
                            onValueChange = { instructions = it },
                            textStyle = pixelStyle(12, colors.secondaryIcon),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.secondaryIcon),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .offset(y = (-2).dp),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.TopStart) {
                                    if (instructions.isEmpty()) {
                                        Text(
                                            "YOU ARE A HELPFUL WIZARD...",
                                            style = pixelStyle(12, colors.secondaryIconSoft),
                                            modifier = Modifier.offset(y = (-2).dp)
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                    }
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PixelButtonLarge(
                        label = "CANCEL",
                        primary = false,
                        modifier = Modifier.weight(1f),
                        onClick = onBack
                    )
                    PixelButtonLarge(
                        label = "SAVE",
                        primary = true,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            scope.launch {
                                project?.let {
                                    upsertProject(
                                        context,
                                        it.copy(
                                            name = name,
                                            instructions = instructions,
                                            picturePath = picturePath
                                        )
                                    )
                                }
                                onBack()
                            }
                        }
                    )
                }
            }
        }
    }
}

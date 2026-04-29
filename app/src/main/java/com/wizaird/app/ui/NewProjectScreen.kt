package com.wizaird.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.request.ImageRequest
import com.wizaird.app.data.Project
import com.wizaird.app.data.copyPictureToInternal
import com.wizaird.app.data.upsertProject
import com.wizaird.app.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun NewProjectScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalWizairdColors.current

    var projectName by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }
    var background by remember { mutableStateOf("") }
    var learningProgress by remember { mutableStateOf("") }
    var picturePath by remember { mutableStateOf("") }

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
            /*
            PixelBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp),
                fillColor = colors.secondarySurface,
                cornerStyle = PixelCornerStyle.Rounded
            ) {
            */
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
                        "NEW PROJECT",
                        style = pixelStyle(14, colors.secondaryIcon),
                        modifier = Modifier
                            .weight(1f)
                            .offset(y = (-2).dp)
                    )
                    PixelButtonSmall(
                        label = "CREATE",
                        primary = true,
                        onClick = {
                            scope.launch {
                                upsertProject(
                                    context,
                                    Project(
                                        name = projectName,
                                        instructions = instructions,
                                        background = background,
                                        learningProgress = learningProgress,
                                        picturePath = picturePath
                                    )
                                )
                                onBack()
                            }
                        }
                    )
                }
            // } // end PixelBox

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
                        value = projectName,
                        onValueChange = { projectName = it },
                        placeholder = "MY PROJECT"
                    )
                }

                // AI instructions
                SettingsField(label = "AI INSTRUCTIONS") {                    PixelBox(
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

// Reusable project picture circle — shows picked image or a "+" if none
@Composable
fun ProjectPicture(
    picturePath: String,
    cutColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(64.dp)
            .pixelLargeCircleClickable(interactionSource = interaction) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        PixelBox(
            modifier = Modifier.size(64.dp),
            fillColor = colors.secondaryButton,
            borderColor = androidx.compose.ui.graphics.Color.Transparent,
            cutColor = cutColor,
            cornerStyle = PixelCornerStyle.Circle
        ) {
            if (picturePath.isNotEmpty() && File(picturePath).exists()) {
                val imageLoader = remember {
                    ImageLoader.Builder(context).components { add(GifDecoder.Factory()) }.build()
                }
                AsyncImage(
                    model = ImageRequest.Builder(context).data(File(picturePath)).build(),
                    imageLoader = imageLoader,
                    contentDescription = "Project picture",
                    modifier = Modifier
                        .requiredSize(64.dp)
                        .clip(PixelLargeCircleShape)
                )
            } else {
                // "+" hint
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.width(14.dp).height(2.dp).background(colors.secondaryIcon))
                    Box(modifier = Modifier.width(2.dp).height(14.dp).background(colors.secondaryIcon))
                }
            }
        }
    }
}

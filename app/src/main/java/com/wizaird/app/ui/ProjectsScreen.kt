package com.wizaird.app.ui

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
import com.wizaird.app.data.projectsFlow
import com.wizaird.app.ui.theme.*
import java.io.File

@Composable
fun ProjectsScreen(onBack: () -> Unit, onNewProject: () -> Unit, onProjectClick: (String) -> Unit = {}) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val projects by projectsFlow(context).collectAsState(initial = emptyList())

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
                        "PROJECTS",
                        style = pixelStyle(12, colors.secondaryIcon),
                        modifier = Modifier
                            .weight(1f)
                            .offset(y = (-2).dp)
                    )
                    PixelButtonSmall(
                        label = "+ NEW",
                        primary = true,
                        cutColor = colors.secondarySurface,
                        onClick = onNewProject
                    )
                }
            }

            // Project list
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                projects.forEach { project ->
                    ProjectCard(
                        project = project,
                        onClick = { onProjectClick(project.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun ProjectCard(project: Project, onClick: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val cardInteraction = remember { MutableInteractionSource() }

    PixelBox(
        modifier = Modifier
            .fillMaxWidth()
            .pixelRounded8Clickable(interactionSource = cardInteraction) { onClick() },
        fillColor = colors.secondarySurface,
        cornerStyle = PixelCornerStyle.Rounded8
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Project picture circle
            // Project picture circle
            PixelBox(
                modifier = Modifier.size(64.dp),
                fillColor = colors.secondaryButton,
                borderColor = androidx.compose.ui.graphics.Color.Transparent,
                cutColor = colors.secondarySurface,
                cornerStyle = PixelCornerStyle.Circle
            ) {
                if (project.picturePath.isNotEmpty() && File(project.picturePath).exists()) {
                    val imageLoader = remember {
                        ImageLoader.Builder(context).components { add(GifDecoder.Factory()) }.build()
                    }
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(File(project.picturePath)).build(),
                        imageLoader = imageLoader,
                        contentDescription = "Project picture",
                        modifier = Modifier
                            .requiredSize(64.dp)
                            .clip(PixelLargeCircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    project.name.ifEmpty { "UNNAMED PROJECT" },
                    style = pixelStyle(12, colors.secondaryIcon),
                    modifier = Modifier.offset(y = (-2).dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${project.chatCount} CHATS",
                    style = pixelStyle(8, colors.secondaryIconSoft),
                    modifier = Modifier.offset(y = (-2).dp)
                )
            }
        }
    }
}

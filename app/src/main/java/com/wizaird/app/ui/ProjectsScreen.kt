package com.wizaird.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.SvgDecoder
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
                        "PROJECTS",
                        style = pixelStyle(14, colors.secondaryIcon),
                        modifier = Modifier
                            .weight(1f)
                            .offset(y = (-2).dp)
                    )
                    PixelButtonSmall(
                        label = "+ NEW",
                        primary = true,
                        onClick = onNewProject
                    )
                }
            // } // end PixelBox

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjectCard(project: Project, onClick: () -> Unit, onLongPress: (() -> Unit)? = null) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val cardInteraction = remember { MutableInteractionSource() }

    var showMenu by remember { mutableStateOf(false) }

    val svgLoader = remember {
        ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
    }

    Box {
        PixelBox(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = cardInteraction,
                    indication = null,
                    onClick = onClick,
                    onLongClick = {
                        showMenu = true
                        onLongPress?.invoke()
                    }
                ),
            fillColor = colors.secondarySurface,
            cornerStyle = PixelCornerStyle.Rounded
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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

        // Popover — rendered outside layout via Popup so it never shifts other cards
        if (showMenu) {
            val density = LocalDensity.current
            val offsetPx = with(density) { 8.dp.roundToPx() }
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(x = -offsetPx, y = offsetPx),
                onDismissRequest = { showMenu = false },
                properties = PopupProperties(focusable = true)
            ) {
                PixelBox(
                    modifier = Modifier.clip(PixelRounded8Shape),
                    fillColor = colors.secondarySurface,
                    cutColor = colors.secondarySurface,
                    cornerStyle = PixelCornerStyle.Rounded8
                ) {
                    Column(modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Bookmark option
                        val bookmarkInteraction = remember { MutableInteractionSource() }
                        PixelBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pixelRounded8Clickable(
                                    interactionSource = bookmarkInteraction,
                                    onClick = { showMenu = false }
                                ),
                            fillColor = colors.secondarySurface,
                            borderColor = colors.secondarySurface,
                            cutColor = colors.secondarySurface,
                            cornerStyle = PixelCornerStyle.Rounded8
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data("file:///android_asset/pixelarticons/bookmark.svg")
                                        .build(),
                                    imageLoader = svgLoader,
                                    contentDescription = "Bookmark",
                                    colorFilter = ColorFilter.tint(colors.secondaryIcon),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "Bookmark",
                                    style = pixelStyle(10, colors.secondaryIcon),
                                    modifier = Modifier.offset(y = (-2).dp)
                                )
                            }
                        }

                        // Delete option
                        val deleteInteraction = remember { MutableInteractionSource() }
                        PixelBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pixelRounded8Clickable(
                                    interactionSource = deleteInteraction,
                                    onClick = { showMenu = false }
                                ),
                            fillColor = colors.secondarySurface,
                            borderColor = colors.secondarySurface,
                            cutColor = colors.secondarySurface,
                            cornerStyle = PixelCornerStyle.Rounded8
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data("file:///android_asset/pixelarticons/delete.svg")
                                        .build(),
                                    imageLoader = svgLoader,
                                    contentDescription = "Delete",
                                    colorFilter = ColorFilter.tint(colors.coral),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "Delete",
                                    style = pixelStyle(10, colors.coral),
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

package com.wizaird.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
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
import com.wizaird.app.data.chatsFlow
import com.wizaird.app.data.deleteProject
import com.wizaird.app.data.notesFlow
import com.wizaird.app.data.projectsFlow
import com.wizaird.app.data.reorderProjects
import com.wizaird.app.data.storedInsightsFlow
import com.wizaird.app.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@Composable
fun ProjectsScreen(onBack: () -> Unit, onNewProject: () -> Unit, onProjectClick: (String) -> Unit = {}) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val projects by projectsFlow(context).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    
    // Reorder mode state - use project ID instead of index
    var isReorderMode by remember { mutableStateOf(false) }
    var draggedProjectId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    
    // Track the target index during drag to maintain position after drop
    var targetIndexDuringDrag by remember { mutableStateOf<Int?>(null) }
    var isReordering by remember { mutableStateOf(false) }
    
    // Store measured card heights (including spacing)
    val cardHeights = remember { mutableStateMapOf<String, Float>() }
    
    // Clear drag state when list updates after reorder
    LaunchedEffect(projects.size, projects.map { it.id }) {
        if (isReordering) {
            // List has been reordered, clear drag state
            draggedProjectId = null
            dragOffsetPx = 0f
            targetIndexDuringDrag = null
            isReordering = false
            println("ProjectsScreen: Cleared drag state after reorder")
        }
    }
    
    // Calculate item height in pixels (card height + spacing)
    // Use measured height if available, otherwise use default
    val spacingDp = 8.dp
    val spacingPx = with(density) { spacingDp.toPx() }
    val defaultCardHeightPx = with(density) { 80.dp.toPx() }
    val itemHeightPx = if (cardHeights.isNotEmpty()) {
        cardHeights.values.first() + spacingPx
    } else {
        defaultCardHeightPx + spacingPx
    }
    
    println("ProjectsScreen: itemHeight=${itemHeightPx}px (measured=${cardHeights.values.firstOrNull()}, spacing=$spacingPx)")
    
    // Find the index of the dragged project
    val draggedItemIndex = draggedProjectId?.let { id ->
        projects.indexOfFirst { it.id == id }
    }?.takeIf { it >= 0 }

    // Calculate target index based on drag offset
    val currentTargetIndex = if (draggedItemIndex != null && draggedItemIndex >= 0 && draggedItemIndex < projects.size) {
        val threshold = itemHeightPx * 0.5f
        val offsetItems = if (dragOffsetPx > threshold) {
            ((dragOffsetPx + threshold) / itemHeightPx).toInt()
        } else if (dragOffsetPx < -threshold) {
            ((dragOffsetPx - threshold) / itemHeightPx).toInt()
        } else {
            0
        }
        (draggedItemIndex + offsetItems).coerceIn(0, projects.size - 1)
    } else {
        null
    }
    
    // Update target index during drag
    LaunchedEffect(currentTargetIndex) {
        if (currentTargetIndex != null) {
            targetIndexDuringDrag = currentTargetIndex
        }
    }

    // Calculate target positions for all items - SNAP TO POSITION
    val itemOffsets = if (draggedItemIndex == null || draggedItemIndex >= projects.size) {
        List(projects.size) { 0f }
    } else {
        val draggedIdx = draggedItemIndex
        val targetIdx = currentTargetIndex ?: draggedIdx
        
        println("ProjectsScreen: Snap - draggedIdx=$draggedIdx, targetIdx=$targetIdx, projects.size=${projects.size}")
        
        // SNAP BEHAVIOR: Cards are always at their final positions
        // We calculate where each card SHOULD BE in the reordered list
        List(projects.size) { index ->
            val offset = when {
                // The dragged item: it should be at the target position
                // So if it's at index 0 and target is 2, it needs to move down by 2 positions
                index == draggedIdx -> {
                    (targetIdx - draggedIdx) * itemHeightPx
                }
                // Cards between original and target: they shift to fill the gap
                // If dragging down (draggedIdx=0, targetIdx=2), cards 1,2 move up by 1
                draggedIdx < targetIdx && index in (draggedIdx + 1)..targetIdx -> {
                    -itemHeightPx
                }
                // If dragging up (draggedIdx=2, targetIdx=0), cards 0,1 move down by 1
                draggedIdx > targetIdx && index in targetIdx until draggedIdx -> {
                    itemHeightPx
                }
                // All other cards stay in place
                else -> {
                    0f
                }
            }
            println("  Card $index (${projects[index].name}): offset=$offset")
            offset
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
                        .pixelCircleClickable(interactionSource = backInteraction) { 
                            if (isReorderMode) {
                                isReorderMode = false
                                draggedProjectId = null
                                dragOffsetPx = 0f
                                targetIndexDuringDrag = null
                                isReordering = false
                            } else {
                                onBack()
                            }
                        },
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
                    if (isReorderMode) "REORDER PROJECTS" else "PROJECTS",
                    style = pixelStyle(14, colors.secondaryIcon),
                    modifier = Modifier
                        .weight(1f)
                        .offset(y = (-2).dp)
                )
                if (!isReorderMode) {
                    PixelButtonSmall(
                        label = "+ NEW",
                        primary = true,
                        onClick = onNewProject
                    )
                } else {
                    PixelButtonSmall(
                        label = "DONE",
                        primary = true,
                        onClick = { 
                            isReorderMode = false
                            draggedProjectId = null
                            dragOffsetPx = 0f
                            targetIndexDuringDrag = null
                            isReordering = false
                        }
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
                projects.forEachIndexed { index, project ->
                    key(project.id) {
                        val isDragging = draggedProjectId == project.id
                        val targetOffset = itemOffsets.getOrElse(index) { 0f }
                        
                        // SNAP BEHAVIOR: No animation, cards snap instantly to positions
                        ProjectCard(
                            project = project,
                            onCardHeightMeasured = { height ->
                                cardHeights[project.id] = height
                            },
                            onClick = { 
                                if (!isReorderMode) {
                                    onProjectClick(project.id)
                                }
                            },
                            onLongPress = {
                                if (!isReorderMode) {
                                    isReorderMode = true
                                }
                            },
                            isReorderMode = isReorderMode,
                            isDragged = isDragging,
                            offsetYPx = targetOffset, // Direct snap to target position
                            onDragStart = {
                                draggedProjectId = project.id
                                dragOffsetPx = 0f
                                println("ProjectsScreen: onDragStart - projectId=${project.id}, index=$index")
                            },
                            onDrag = { delta ->
                                if (draggedProjectId == project.id) {
                                    dragOffsetPx += delta
                                }
                            },
                            onDragEnd = {
                                // Capture values before reorder
                                val capturedDraggedId = draggedProjectId
                                val capturedTargetIndex = targetIndexDuringDrag
                                
                                try {
                                    // Find current index of dragged project
                                    val capturedDraggedIndex = capturedDraggedId?.let { id ->
                                        projects.indexOfFirst { it.id == id }
                                    }?.takeIf { it >= 0 }
                                    
                                    println("ProjectsScreen: onDragEnd - draggedIndex=$capturedDraggedIndex, targetIndex=$capturedTargetIndex")
                                    
                                    if (capturedDraggedIndex != null && capturedTargetIndex != null && 
                                        capturedDraggedIndex >= 0 && capturedDraggedIndex < projects.size &&
                                        capturedTargetIndex != capturedDraggedIndex) {
                                        
                                        println("ProjectsScreen: Reordering from $capturedDraggedIndex to $capturedTargetIndex")
                                        isReordering = true
                                        scope.launch {
                                            try {
                                                reorderProjects(context, capturedDraggedIndex, capturedTargetIndex)
                                                println("ProjectsScreen: Reorder completed successfully")
                                                // State will be cleared by LaunchedEffect when list updates
                                            } catch (e: Exception) {
                                                println("ProjectsScreen: Error during reorder - ${e.message}")
                                                e.printStackTrace()
                                                // Reset on error
                                                draggedProjectId = null
                                                dragOffsetPx = 0f
                                                targetIndexDuringDrag = null
                                                isReordering = false
                                            }
                                        }
                                    } else {
                                        println("ProjectsScreen: No reorder needed")
                                        // Reset immediately if no reorder needed
                                        draggedProjectId = null
                                        dragOffsetPx = 0f
                                        targetIndexDuringDrag = null
                                    }
                                } catch (e: Exception) {
                                    println("ProjectsScreen: Exception in onDragEnd - ${e.message}")
                                    e.printStackTrace()
                                    // Reset on exception
                                    draggedProjectId = null
                                    dragOffsetPx = 0f
                                    targetIndexDuringDrag = null
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjectCard(
    project: Project,
    onCardHeightMeasured: (Float) -> Unit = {},
    onClick: () -> Unit, 
    onLongPress: (() -> Unit)? = null,
    isReorderMode: Boolean = false,
    isDragged: Boolean = false,
    offsetYPx: Float = 0f,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val cardInteraction = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()

    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Collect actual counts for this project
    val chats by chatsFlow(context, project.id).collectAsState(initial = emptyList())
    val notes by notesFlow(context, project.id).collectAsState(initial = emptyList())
    val insights by storedInsightsFlow(context, project.id).collectAsState(initial = emptyList())

    val svgLoader = remember {
        ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
    }

    Box(
        modifier = Modifier
            .zIndex(if (isDragged) 1f else 0f)
            .graphicsLayer {
                translationY = offsetYPx
                alpha = if (isDragged) 0.7f else 1f
                shadowElevation = if (isDragged) 8.dp.toPx() else 0f
            }
    ) {
        PixelBox(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { layoutCoordinates ->
                    onCardHeightMeasured(layoutCoordinates.size.height.toFloat())
                }
                .then(
                    if (!isReorderMode) {
                        Modifier.pixelRoundedCombinedClickable(
                            interactionSource = cardInteraction,
                            onClick = onClick,
                            onLongClick = {
                                onLongPress?.invoke()
                            }
                        )
                    } else {
                        Modifier
                    }
                ),
            fillColor = colors.secondarySurface,
            borderColor = if (isDragged) Coral else colors.border,
            cornerStyle = PixelCornerStyle.Rounded
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drag handle (only visible in reorder mode)
                if (isReorderMode) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { 
                                        onDragStart()
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        onDrag(dragAmount.y)
                                    },
                                    onDragEnd = { 
                                        onDragEnd()
                                    },
                                    onDragCancel = { 
                                        onDragEnd()
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("file:///android_asset/pixelarticons/menu.svg")
                                .build(),
                            imageLoader = svgLoader,
                            contentDescription = "Drag to reorder",
                            colorFilter = ColorFilter.tint(colors.textXLow),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${chats.size} CHATS",
                            style = pixelStyle(8, colors.secondaryIconSoft),
                            modifier = Modifier.offset(y = (-2).dp)
                        )
                        Text(
                            "•",
                            style = pixelStyle(8, colors.secondaryIconSoft),
                            modifier = Modifier.offset(y = (-2).dp)
                        )
                        Text(
                            "${insights.size} INSIGHTS",
                            style = pixelStyle(8, colors.secondaryIconSoft),
                            modifier = Modifier.offset(y = (-2).dp)
                        )
                        Text(
                            "•",
                            style = pixelStyle(8, colors.secondaryIconSoft),
                            modifier = Modifier.offset(y = (-2).dp)
                        )
                        Text(
                            "${notes.size} NOTES",
                            style = pixelStyle(8, colors.secondaryIconSoft),
                            modifier = Modifier.offset(y = (-2).dp)
                        )
                    }
                }
            }
        } // end PixelBox

        // Popover — rendered outside layout via Popup so it never shifts other cards
        if (showMenu) {
            val density = LocalDensity.current
            val nudge = with(density) { 2.dp.roundToPx() }
            val offsetX = with(density) { (-14.dp).roundToPx() } + nudge
            val offsetY = with(density) { 12.dp.roundToPx() }
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(x = offsetX, y = offsetY),
                onDismissRequest = { showMenu = false },
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
                            val fill = colors.userBubble
                            val cut = Color.Transparent

                            // Fill entire box
                            drawRect(fill)

                            // Cut corners with BlendMode.Clear to punch through to transparent
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
                    Column(modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        // Bookmark option
                        val bookmarkInteraction = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pixelRounded8Clickable(
                                    interactionSource = bookmarkInteraction,
                                    onClick = { showMenu = false }
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pixelRounded8Clickable(
                                    interactionSource = deleteInteraction,
                                    onClick = {
                                        showMenu = false
                                        showDeleteDialog = true
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
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

        // Delete confirmation dialog
        if (showDeleteDialog) {
            PixelConfirmationDialog(
                title = "DELETE PROJECT",
                message = "Are you sure you want to delete \"${project.name.ifEmpty { "UNNAMED PROJECT" }}\"? This will also delete all chats and notes in this project. This action cannot be undone.",
                confirmLabel = "DELETE",
                cancelLabel = "CANCEL",
                isDestructive = true,
                onConfirm = {
                    showDeleteDialog = false
                    scope.launch {
                        deleteProject(context, project.id)
                    }
                },
                onDismiss = {
                    showDeleteDialog = false
                }
            )
        }
    }
}

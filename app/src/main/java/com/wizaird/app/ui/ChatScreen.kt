package com.wizaird.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.wizaird.app.data.ChatData
import com.wizaird.app.data.ChatMessage
import com.wizaird.app.data.MessageSender
import com.wizaird.app.data.projectsFlow
import com.wizaird.app.data.upsertChat
import com.wizaird.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    projectId: String,
    onBack: () -> Unit,
    onChatCreated: (String) -> Unit = {},
    onMoreClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val scope = rememberCoroutineScope()

    val projects by projectsFlow(context).collectAsState(initial = emptyList())
    val project = projects.firstOrNull { it.id == projectId }
    val projectName = project?.name?.ifEmpty { "UNNAMED PROJECT" } ?: "UNNAMED PROJECT"

    var inputText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    var isCreatingChat by remember { mutableStateOf(false) }

    var showMenu by remember { mutableStateOf(false) }

    val svgLoader = remember {
        ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val bobY = 0f

    val bubbleText = "What can I teach you about ${project?.name?.ifEmpty { "this project" } ?: "this project"} today?"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Header — back button | project name (centered) | more-vertical icon
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

            // Project name — centered
            Text(
                text = projectName,
                style = pixelStyle(10, colors.secondaryIcon),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-2).dp)
            )

            // More-vertical icon — right
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                PixelCircleIconButton(
                    iconRes = com.wizaird.app.R.drawable.ic_more_vertical,
                    contentDescription = "More options",
                    fillColor = colors.secondaryButton,
                    enabled = false,
                    onClick = { showMenu = true }
                )

                // Popover — 8px below button, right-aligned to button
                if (showMenu) {
                    val density = LocalDensity.current
                    val offsetY = with(density) { (40 + 8).dp.roundToPx() }
                    Popup(
                        alignment = Alignment.TopEnd,
                        offset = IntOffset(x = 0, y = offsetY),
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

                                    drawRect(fill)

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
                            Column(
                                modifier = Modifier
                                    .width(IntrinsicSize.Max)
                                    .padding(4.dp),
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                // Rename option
                                val renameInteraction = remember { MutableInteractionSource() }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pixelRounded8Clickable(
                                            interactionSource = renameInteraction,
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
                                                .data("file:///android_asset/pixelarticons/pen-square.svg")
                                                .build(),
                                            imageLoader = svgLoader,
                                            contentDescription = "Rename",
                                            colorFilter = ColorFilter.tint(colors.secondaryIcon),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            "Rename",
                                            style = pixelStyle(10, colors.secondaryIcon),
                                            modifier = Modifier.offset(y = (-2).dp)
                                        )
                                    }
                                }

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
        } // end header Box

        // Chat content area — bubble + gif pinned to bottom
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val tailColor = colors.secondarySurface
                val borderColor = colors.border
                Box(
                    modifier = Modifier
                        .padding(horizontal = 40.dp)
                        .padding(bottom = 22.dp)
                        .wrapContentSize()
                        .offset(y = 4.dp)
                        .zIndex(1f)
                        .drawWithContent {
                            drawContent()
                            val p = PixelSize.toPx()
                            val tailX = size.width / 2f - p * 7.5f
                            val tailY = size.height - p - 1f
                            drawRect(tailColor, androidx.compose.ui.geometry.Offset(tailX,       tailY + p * 0), androidx.compose.ui.geometry.Size(p * 15, p))
                            drawRect(tailColor, androidx.compose.ui.geometry.Offset(tailX + p,   tailY + p * 1), androidx.compose.ui.geometry.Size(p * 13, p))
                            drawRect(tailColor, androidx.compose.ui.geometry.Offset(tailX + p*2, tailY + p * 2), androidx.compose.ui.geometry.Size(p * 11, p))
                            drawRect(tailColor, androidx.compose.ui.geometry.Offset(tailX + p*3, tailY + p * 3), androidx.compose.ui.geometry.Size(p *  9, p))
                            drawRect(tailColor, androidx.compose.ui.geometry.Offset(tailX + p*4, tailY + p * 4), androidx.compose.ui.geometry.Size(p *  7, p))
                            drawRect(tailColor, androidx.compose.ui.geometry.Offset(tailX + p*5, tailY + p * 5), androidx.compose.ui.geometry.Size(p *  5, p))
                            drawRect(tailColor, androidx.compose.ui.geometry.Offset(tailX + p*6, tailY + p * 6), androidx.compose.ui.geometry.Size(p *  3, p))
                            drawRect(borderColor, androidx.compose.ui.geometry.Offset(tailX,       tailY + p * 0), androidx.compose.ui.geometry.Size(p, p))
                            drawRect(borderColor, androidx.compose.ui.geometry.Offset(tailX + p,   tailY + p * 1), androidx.compose.ui.geometry.Size(p, p))
                            drawRect(borderColor, androidx.compose.ui.geometry.Offset(tailX + p*2, tailY + p * 2), androidx.compose.ui.geometry.Size(p, p))
                            drawRect(borderColor, androidx.compose.ui.geometry.Offset(tailX + p*3, tailY + p * 3), androidx.compose.ui.geometry.Size(p, p))
                            drawRect(borderColor, androidx.compose.ui.geometry.Offset(tailX + p*4, tailY + p * 4), androidx.compose.ui.geometry.Size(p, p))
                            drawRect(borderColor, androidx.compose.ui.geometry.Offset(tailX + p*5, tailY + p * 5), androidx.compose.ui.geometry.Size(p, p))
                            drawRect(borderColor, androidx.compose.ui.geometry.Offset(tailX + p*6, tailY + p * 6), androidx.compose.ui.geometry.Size(p, p))
                            drawRect(borderColor, androidx.compose.ui.geometry.Offset(tailX + p*14, tailY + p * 0), androidx.compose.ui.geometry.Size(p, p))
                            drawRect(borderColor, androidx.compose.ui.geometry.Offset(tailX + p*13, tailY + p * 1), androidx.compose.ui.geometry.Size(p, p))
                            drawRect(borderColor, androidx.compose.ui.geometry.Offset(tailX + p*12, tailY + p * 2), androidx.compose.ui.geometry.Size(p, p))
                            drawRect(borderColor, androidx.compose.ui.geometry.Offset(tailX + p*11, tailY + p * 3), androidx.compose.ui.geometry.Size(p, p))
                            drawRect(borderColor, androidx.compose.ui.geometry.Offset(tailX + p*10, tailY + p * 4), androidx.compose.ui.geometry.Size(p, p))
                            drawRect(borderColor, androidx.compose.ui.geometry.Offset(tailX + p* 9, tailY + p * 5), androidx.compose.ui.geometry.Size(p, p))
                            drawRect(borderColor, androidx.compose.ui.geometry.Offset(tailX + p* 8, tailY + p * 6), androidx.compose.ui.geometry.Size(p, p))
                            drawRect(borderColor, androidx.compose.ui.geometry.Offset(tailX + p*7,  tailY + p * 6), androidx.compose.ui.geometry.Size(p, p))
                        }
                ) {
                    PixelBox(
                        modifier = Modifier.wrapContentSize(),
                        fillColor = colors.secondarySurface,
                        cornerStyle = PixelCornerStyle.Rounded,
                        speechTail = false
                    ) {
                        SelectionContainer {
                            MarkdownText(
                                markdown = bubbleText,
                                style = pixelStyle(14, colors.textHigh).copy(
                                    lineHeight = (14 * 1.6f).sp,
                                    textAlign = TextAlign.Center
                                ),
                                modifier = Modifier
                                    .padding(16.dp)
                                    .offset(y = (-2).dp)
                            )
                        }
                    }
                }

                WizardCharacter(
                    bobOffsetY = bobY,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }

        // Input bar
        PixelInputBar(
            value = inputText,
            onValueChange = { inputText = it },
            onSubmit = {
                val q = inputText.trim()
                if (q.isNotEmpty() && !isCreatingChat) {
                    inputText = ""
                    isCreatingChat = true
                    
                    scope.launch {
                        // Create user message
                        val userMessage = ChatMessage(
                            sender = MessageSender.USER,
                            text = q
                        )
                        
                        // Create new chat with placeholder title
                        val newChat = ChatData(
                            projectId = projectId,
                            title = "Generated Title",
                            messages = listOf(userMessage)
                        )
                        
                        // Save the chat
                        upsertChat(context, newChat)
                        
                        // Simulate AI response delay
                        delay(500)
                        
                        // Add AI response
                        val aiMessage = ChatMessage(
                            sender = MessageSender.AI,
                            text = "This is a placeholder response. The AI integration will be added later to provide real responses based on your question."
                        )
                        
                        val updatedChat = newChat.copy(
                            messages = newChat.messages + aiMessage
                        )
                        upsertChat(context, updatedChat)
                        
                        // Navigate to the existing chat screen
                        onChatCreated(newChat.id)
                    }
                }
            },
            focusRequester = focusRequester,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(20.dp).navigationBarsPadding())
    }
}

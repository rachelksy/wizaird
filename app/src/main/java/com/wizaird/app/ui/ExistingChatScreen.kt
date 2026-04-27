package com.wizaird.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.wizaird.app.data.projectsFlow
import com.wizaird.app.ui.theme.*

// ── Data ─────────────────────────────────────────────────────────────────────

enum class MessageSender { USER, AI }

data class ChatMessage(
    val id: String,
    val sender: MessageSender,
    val text: String
)

// ── Placeholder messages ──────────────────────────────────────────────────────

private val placeholderMessages = listOf(
    ChatMessage(
        id = "1",
        sender = MessageSender.USER,
        text = "Can you help me plan out the architecture for this project?"
    ),
    ChatMessage(
        id = "2",
        sender = MessageSender.AI,
        text = "Sure! Let's start with the high-level structure. What kind of app are you building — mobile, web, or something else?"
    ),
    ChatMessage(
        id = "3",
        sender = MessageSender.USER,
        text = "It's an Android app. I want to keep it clean and easy to maintain."
    ),
    ChatMessage(
        id = "4",
        sender = MessageSender.AI,
        text = "Great choice. For a clean Android app I'd recommend a single-activity setup with Jetpack Compose for the UI, a ViewModel per screen, and a repository layer to abstract your data sources. Want me to sketch out the folder structure?"
    )
)

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun ExistingChatScreen(
    projectId: String,
    chatId: String,
    onBack: () -> Unit,
    onMoreClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current

    val projects by projectsFlow(context).collectAsState(initial = emptyList())
    val project = projects.firstOrNull { it.id == projectId }
    val projectName = project?.name?.ifEmpty { "UNNAMED PROJECT" } ?: "UNNAMED PROJECT"

    var inputText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // Scroll to bottom when messages change
    LaunchedEffect(placeholderMessages.size) {
        if (placeholderMessages.isNotEmpty()) {
            listState.animateScrollToItem(placeholderMessages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // ── Header ────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
        ) {
            // Back button
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

            // More-vertical icon
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                PixelCircleIconButton(
                    iconRes = com.wizaird.app.R.drawable.ic_more_vertical,
                    contentDescription = "More options",
                    fillColor = colors.secondaryButton,
                    onClick = onMoreClick
                )
            }
        }

        // ── Message list ──────────────────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 16.dp,
                bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(placeholderMessages) { message ->
                ChatBubble(message = message)
            }
        }

        // ── Input bar ─────────────────────────────────────────────────────────
        PixelInputBar(
            value = inputText,
            onValueChange = { inputText = it },
            onSubmit = {
                val q = inputText.trim()
                if (q.isNotEmpty()) {
                    inputText = ""
                    // TODO: send message
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

// ── Bubble ────────────────────────────────────────────────────────────────────

@Composable
fun ChatBubble(message: ChatMessage) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val isUser = message.sender == MessageSender.USER

    // Bubble appearance tokens — change here to restyle both sides at once
    val userBubbleFill = colors.userBubble
    val userBubbleText = colors.secondaryIcon
    val aiBubbleFill   = colors.secondarySurface
    val aiBubbleText   = colors.textHigh

    // SVG loader — shared for both icons
    val svgLoader = remember {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        PixelBox(
            modifier = Modifier.then(
                if (isUser) Modifier.padding(start = 32.dp) else Modifier.padding(end = 32.dp)
            ),
            fillColor = if (isUser) userBubbleFill else aiBubbleFill,
            borderColor = androidx.compose.ui.graphics.Color.Transparent,
            cornerStyle = PixelCornerStyle.Rounded
        ) {
            if (isUser) {
                SelectionContainer {
                    Text(
                        text = message.text,
                        style = minecraftStyle(14, userBubbleText),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            } else {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    SelectionContainer {
                        Text(
                            text = message.text,
                            style = minecraftStyle(14, aiBubbleText)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val copyInteraction = remember { MutableInteractionSource() }
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("file:///android_asset/pixelarticons/copy.svg")
                                .build(),
                            imageLoader = svgLoader,
                            contentDescription = "Copy",
                            colorFilter = ColorFilter.tint(colors.textXLow),
                            modifier = Modifier
                                .size(20.dp)
                                .pixelRounded8ClickableOversize(
                                    interactionSource = copyInteraction
                                ) { /* TODO: copy to clipboard */ }
                        )

                        val noteInteraction = remember { MutableInteractionSource() }
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("file:///android_asset/pixelarticons/sticky-note-text.svg")
                                .build(),
                            imageLoader = svgLoader,
                            contentDescription = "Save to note",
                            colorFilter = ColorFilter.tint(colors.textXLow),
                            modifier = Modifier
                                .size(20.dp)
                                .pixelRounded8ClickableOversize(
                                    interactionSource = noteInteraction
                                ) { /* TODO: save to note */ }
                        )
                    }
                }
            }
        }
    }
}

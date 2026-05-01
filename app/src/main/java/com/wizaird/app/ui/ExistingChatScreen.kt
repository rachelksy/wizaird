package com.wizaird.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.wizaird.app.data.ChatMessage
import com.wizaird.app.data.MessageSender
import com.wizaird.app.data.addMessageToChat
import com.wizaird.app.data.buildChatSystemPrompt
import com.wizaird.app.data.askAi
import com.wizaird.app.data.chatFlow
import com.wizaird.app.data.deleteChat
import com.wizaird.app.data.removeLastAiMessage
import com.wizaird.app.data.projectsFlow
import com.wizaird.app.data.settingsFlow
import com.wizaird.app.data.AiSettings
import com.wizaird.app.data.generateChatTitle
import com.wizaird.app.data.upsertChat
import kotlinx.coroutines.flow.first
import com.wizaird.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun ExistingChatScreen(
    projectId: String,
    chatId: String,
    onBack: () -> Unit,
    onSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val scope = rememberCoroutineScope()

    val projects by projectsFlow(context).collectAsState(initial = emptyList())
    val project = projects.firstOrNull { it.id == projectId }
    val projectName = project?.name?.ifEmpty { "UNNAMED PROJECT" } ?: "UNNAMED PROJECT"
    
    val settings by settingsFlow(context).collectAsState(initial = AiSettings())

    // Load chat from repository
    val chat by chatFlow(context, chatId).collectAsState(initial = null)
    val messages = chat?.messages ?: emptyList()

    var inputText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var isSendingMessage by remember { mutableStateOf(false) }
    var isGeneratingResponse by remember { mutableStateOf(false) }
    var responseError by remember { mutableStateOf<String?>(null) }

    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    var showMessageActions by remember { mutableStateOf(false) }
    var selectedMessageEdit by remember { mutableStateOf<(() -> Unit)?>(null) }
    var selectedMessageDelete by remember { mutableStateOf<(() -> Unit)?>(null) }

    val svgLoader = remember {
        ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
    
    // Auto-generate AI response when last message is from user
    LaunchedEffect(messages.size, messages.lastOrNull()?.id) {
        if (messages.isNotEmpty() && 
            messages.last().sender == MessageSender.USER && 
            !isGeneratingResponse &&
            project != null) {
            
            isGeneratingResponse = true
            responseError = null
            
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    println("=== CHAT AI RESPONSE START ===")
                    println("Chat ID: $chatId")
                    println("Project: ${project.name}")
                    println("Project ID: ${project.id}")
                    println("User message: ${messages.last().text}")
                    println("Total messages in chat: ${messages.size}")
                    
                    val systemPrompt = buildChatSystemPrompt(project)
                    println("System prompt built (${systemPrompt.length} chars)")
                    println("--- SYSTEM PROMPT START ---")
                    println(systemPrompt)
                    println("--- SYSTEM PROMPT END ---")
                    
                    // Build conversation history for context using chat's context window size
                    val contextWindowSize = chat?.contextWindowSize ?: 50
                    val messagesToSend = messages.takeLast(contextWindowSize)
                    println("Using context window size: $contextWindowSize (sending ${messagesToSend.size} messages)")
                    
                    val conversationHistory = messagesToSend.joinToString("\n") { msg ->
                        "${if (msg.sender == MessageSender.USER) "User" else "Assistant"}: ${msg.text}"
                    }
                    println("--- CONVERSATION HISTORY START ---")
                    println(conversationHistory)
                    println("--- CONVERSATION HISTORY END ---")
                    
                    println("Calling askAi with settings:")
                    println("  Provider: ${settings.provider}")
                    println("  Model: ${settings.model}")
                    println("  API Key length: ${settings.apiKey.length}")
                    println("  Base URL: ${settings.baseUrl}")
                    
                    val aiResponse = askAi(settings, systemPrompt, conversationHistory)
                    println("AI response received (${aiResponse.length} chars)")
                    println("--- AI RESPONSE START ---")
                    println(aiResponse)
                    println("--- AI RESPONSE END ---")
                    
                    val aiMessage = ChatMessage(
                        sender = MessageSender.AI,
                        text = aiResponse
                    )
                    addMessageToChat(context, chatId, aiMessage)
                    
                    println("=== CHAT AI RESPONSE SUCCESS ===")
                    
                    // Generate title in background (don't block or show loading)
                    if (chat?.title == "Generated Title" && messages.size <= 2) {
                        // Launch in application scope so it continues even if user navigates away
                        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                println("Generating chat title in background...")
                                val firstUserMessage = messages.firstOrNull { it.sender == MessageSender.USER }?.text ?: ""
                                val title = generateChatTitle(context, settings, aiResponse, firstUserMessage)
                                println("Generated title: $title")
                                
                                // Update chat with new title
                                val currentChat = chatFlow(context, chatId).first()
                                currentChat?.let {
                                    val updatedChat = it.copy(title = title)
                                    upsertChat(context, updatedChat)
                                }
                            } catch (e: Exception) {
                                println("Failed to generate title in background: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("=== CHAT AI RESPONSE FAILED ===")
                    println("Error type: ${e.javaClass.simpleName}")
                    println("Error message: ${e.message}")
                    println("Stack trace:")
                    e.printStackTrace()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        responseError = "Failed to get response: ${e.message}"
                    }
                } finally {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        isGeneratingResponse = false
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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

            // More-vertical icon — right
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                PixelCircleIconButton(
                    iconRes = com.wizaird.app.R.drawable.ic_more_vertical,
                    contentDescription = "More options",
                    fillColor = colors.secondaryButton,
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
                                // Settings option
                                val settingsInteraction = remember { MutableInteractionSource() }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pixelRounded8Clickable(
                                            interactionSource = settingsInteraction,
                                            onClick = { 
                                                showMenu = false
                                                onSettingsClick()
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
                                                .data("file:///android_asset/pixelarticons/settings-2.svg")
                                                .build(),
                                            imageLoader = svgLoader,
                                            contentDescription = "Settings",
                                            colorFilter = ColorFilter.tint(colors.secondaryIcon),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            "Settings",
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
            }
        } // end header Box

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
            items(messages, key = { it.id }) { message ->
                val isLastAiMessage = message.sender == MessageSender.AI && 
                    message.id == messages.lastOrNull { it.sender == MessageSender.AI }?.id
                ChatBubble(
                    message = message,
                    onRegenerate = if (isLastAiMessage) {
                        {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                removeLastAiMessage(context, chatId)
                            }
                        }
                    } else null,
                    onEdit = {
                        selectedMessageEdit = { /* TODO: implement edit for message ${message.id} */ }
                        selectedMessageDelete = { /* TODO: implement delete for message ${message.id} */ }
                        showMessageActions = true
                    },
                    onDelete = { /* Not used - onEdit triggers the bottom sheet */ }
                )
            }
            
            // Show loading indicator when generating response
            if (isGeneratingResponse) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        PixelBox(
                            modifier = Modifier.padding(end = 32.dp),
                            fillColor = colors.secondarySurface,
                            borderColor = androidx.compose.ui.graphics.Color.Transparent,
                            cornerStyle = PixelCornerStyle.Rounded
                        ) {
                            Text(
                                text = "Thinking...",
                                style = minecraftStyle(14, colors.textXLow),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
            
            // Show error in AI chat bubble with retry icon
            if (responseError != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        PixelBox(
                            modifier = Modifier.padding(end = 32.dp),
                            fillColor = colors.secondarySurface,
                            borderColor = androidx.compose.ui.graphics.Color.Transparent,
                            cornerStyle = PixelCornerStyle.Rounded
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Text(
                                    text = responseError ?: "Unknown error",
                                    style = minecraftStyle(14, colors.coral)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val retryInteraction = remember { MutableInteractionSource() }
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data("file:///android_asset/pixelarticons/zap.svg")
                                            .build(),
                                        imageLoader = svgLoader,
                                        contentDescription = "Retry",
                                        colorFilter = ColorFilter.tint(colors.textXLow),
                                        modifier = Modifier
                                            .size(20.dp)
                                            .pixelRounded8ClickableOversize(
                                                interactionSource = retryInteraction
                                            ) {
                                                // Retry by resetting error state
                                                responseError = null
                                                scope.launch {
                                                    delay(100)
                                                    isGeneratingResponse = false
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Input bar ─────────────────────────────────────────────────────────
        PixelInputBar(
            value = inputText,
            onValueChange = { inputText = it },
            onSubmit = {
                val q = inputText.trim()
                if (q.isNotEmpty() && !isSendingMessage) {
                    inputText = ""
                    isSendingMessage = true
                    
                    scope.launch {
                        // Create and add user message
                        val userMessage = ChatMessage(
                            sender = MessageSender.USER,
                            text = q
                        )
                        addMessageToChat(context, chatId, userMessage)
                        
                        // Scroll to bottom
                        delay(100)
                        listState.animateScrollToItem(messages.size)
                        
                        isSendingMessage = false
                        
                        // AI response will be generated automatically by LaunchedEffect
                    }
                }
            },
            focusRequester = focusRequester,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        )

            Spacer(modifier = Modifier.height(20.dp).navigationBarsPadding())

            // Delete confirmation dialog
            if (showDeleteDialog) {
                PixelConfirmationDialog(
                    title = "DELETE CHAT",
                    message = "Are you sure you want to delete this chat? This action cannot be undone.",
                    confirmLabel = "DELETE",
                    cancelLabel = "CANCEL",
                    isDestructive = true,
                    onConfirm = {
                        showDeleteDialog = false
                        scope.launch {
                            deleteChat(context, chatId)
                            onBack()
                        }
                    },
                    onDismiss = {
                        showDeleteDialog = false
                    }
                )
            }
        }

        // Message actions bottom sheet (outside Column, overlaying everything)
        if (showMessageActions) {
            MessageActionsBottomSheet(
                onEdit = {
                    showMessageActions = false
                    selectedMessageEdit?.invoke()
                },
                onDelete = {
                    showMessageActions = false
                    selectedMessageDelete?.invoke()
                },
                onDismiss = { showMessageActions = false }
            )
        }
    }
}

// ── Bubble ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: ChatMessage,
    onRegenerate: (() -> Unit)? = null,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val isUser = message.sender == MessageSender.USER

    val userBubbleFill = colors.userBubble
    val userBubbleText = colors.secondaryIcon
    val aiBubbleFill   = colors.secondarySurface
    val aiBubbleText   = colors.textHigh

    var copied by remember { mutableStateOf(false) }

    val svgLoader = remember {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        val bubbleInteraction = remember { MutableInteractionSource() }
        PixelBox(
            modifier = Modifier
                .then(
                    if (isUser) Modifier.padding(start = 32.dp) else Modifier.padding(end = 32.dp)
                )
                .pixelRoundedCombinedClickable(
                    interactionSource = bubbleInteraction,
                    onClick = {},
                    onLongClick = { onEdit() }
                ),
            fillColor = if (isUser) userBubbleFill else aiBubbleFill,
            borderColor = androidx.compose.ui.graphics.Color.Transparent,
            cornerStyle = PixelCornerStyle.Rounded
        ) {
            if (isUser) {
                SelectionContainer {
                    MarkdownText(
                        markdown = message.text,
                        style = minecraftStyle(14, userBubbleText),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            } else {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    SelectionContainer {
                        MarkdownText(
                            markdown = message.text,
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
                            colorFilter = ColorFilter.tint(if (copied) colors.textHigh else colors.textXLow),
                            modifier = Modifier
                                .size(20.dp)
                                .pixelRounded8ClickableOversize(
                                    interactionSource = copyInteraction
                                ) {
                                    clipboardManager.setText(AnnotatedString(message.text))
                                    copied = true
                                    scope.launch {
                                        delay(2000)
                                        copied = false
                                    }
                                }
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
                        
                        // Regenerate icon — only on last AI message
                        if (onRegenerate != null) {
                            val regenInteraction = remember { MutableInteractionSource() }
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data("file:///android_asset/pixelarticons/zap.svg")
                                    .build(),
                                imageLoader = svgLoader,
                                contentDescription = "Regenerate",
                                colorFilter = ColorFilter.tint(colors.textXLow),
                                modifier = Modifier
                                    .size(20.dp)
                                    .pixelRounded8ClickableOversize(
                                        interactionSource = regenInteraction
                                    ) { onRegenerate() }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Message Actions Bottom Sheet ──────────────────────────────────────────────

@Composable
fun MessageActionsBottomSheet(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalWizairdColors.current
    val context = LocalContext.current
    
    val svgLoader = remember {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }

    // Scrim background with darkened overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        // Bottom sheet with upper rounded pixel corners (R=10)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawBehind {
                    val p = PixelSize.toPx()
                    val w = size.width
                    val h = size.height
                    val fill = colors.secondaryButton

                    // Fill the sheet
                    drawRect(fill)

                    // Top-left corner cuts (R=10 pixel corners)
                    drawRect(Color.Transparent, Offset(0f, p*0), Size(p*7, p), blendMode = BlendMode.Clear)
                    drawRect(Color.Transparent, Offset(0f, p*1), Size(p*5, p), blendMode = BlendMode.Clear)
                    drawRect(Color.Transparent, Offset(0f, p*2), Size(p*3, p), blendMode = BlendMode.Clear)
                    drawRect(Color.Transparent, Offset(0f, p*3), Size(p*2, p), blendMode = BlendMode.Clear)
                    drawRect(Color.Transparent, Offset(0f, p*4), Size(p*2, p), blendMode = BlendMode.Clear)
                    drawRect(Color.Transparent, Offset(0f, p*5), Size(p*1, p), blendMode = BlendMode.Clear)
                    drawRect(Color.Transparent, Offset(0f, p*6), Size(p*1, p), blendMode = BlendMode.Clear)
                    
                    // Top-right corner cuts (R=10 pixel corners)
                    drawRect(Color.Transparent, Offset(w-p*7, p*0), Size(p*7, p), blendMode = BlendMode.Clear)
                    drawRect(Color.Transparent, Offset(w-p*5, p*1), Size(p*5, p), blendMode = BlendMode.Clear)
                    drawRect(Color.Transparent, Offset(w-p*3, p*2), Size(p*3, p), blendMode = BlendMode.Clear)
                    drawRect(Color.Transparent, Offset(w-p*2, p*3), Size(p*2, p), blendMode = BlendMode.Clear)
                    drawRect(Color.Transparent, Offset(w-p*2, p*4), Size(p*2, p), blendMode = BlendMode.Clear)
                    drawRect(Color.Transparent, Offset(w-p*1, p*5), Size(p*1, p), blendMode = BlendMode.Clear)
                    drawRect(Color.Transparent, Offset(w-p*1, p*6), Size(p*1, p), blendMode = BlendMode.Clear)
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // Prevent clicks from passing through to scrim
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Edit button
                val editInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .pixelRoundedClickable(
                            interactionSource = editInteraction,
                            onClick = onEdit
                        ),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("file:///android_asset/pixelarticons/pen-square.svg")
                                .build(),
                            imageLoader = svgLoader,
                            contentDescription = "Edit",
                            colorFilter = ColorFilter.tint(colors.textLow),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Edit",
                            style = pixelStyle(14, colors.textLow),
                            modifier = Modifier.offset(y = (-2).dp)
                        )
                    }
                }

                // Delete button
                val deleteInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .pixelRoundedClickable(
                            interactionSource = deleteInteraction,
                            onClick = onDelete
                        ),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("file:///android_asset/pixelarticons/delete.svg")
                                .build(),
                            imageLoader = svgLoader,
                            contentDescription = "Delete",
                            colorFilter = ColorFilter.tint(colors.coral),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Delete",
                            style = pixelStyle(14, colors.coral),
                            modifier = Modifier.offset(y = (-2).dp)
                        )
                    }
                }
            }
        }
    }
}

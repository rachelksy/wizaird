package com.wizaird.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
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
import com.wizaird.app.data.askAiWithThinking
import com.wizaird.app.data.chatFlow
import com.wizaird.app.data.deleteChat
import com.wizaird.app.data.deleteMessageFromChat
import com.wizaird.app.data.removeLastAiMessage
import com.wizaird.app.data.updateMessageInChat
import com.wizaird.app.data.projectsFlow
import com.wizaird.app.data.settingsFlow
import com.wizaird.app.data.AiSettings
import com.wizaird.app.data.generateChatTitle
import com.wizaird.app.data.buildPracticeChatSystemPrompt
import com.wizaird.app.data.buildPracticeQuestionPrompt
import com.wizaird.app.data.earlierPracticeQuestions
import com.wizaird.app.data.originFromInsight
import com.wizaird.app.data.originFromMessage
import com.wizaird.app.data.parseVerdict
import com.wizaird.app.data.practiceTitle
import com.wizaird.app.data.resolveChatOrigin
import com.wizaird.app.data.storedInsightsFlow

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
    onSettingsClick: () -> Unit = {},
    onNewConceptClick: () -> Unit = {},
    onOpenChat: (String) -> Unit = {},          // origin tag → "Open chat"
    onOpenConcept: (String) -> Unit = {},       // origin tag → "Open concept"
    onInsightChatClick: (String) -> Unit = {}   // origin insight preview → chat icon
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val isCoverScreen = rememberIsCoverScreen()
    val scope = rememberCoroutineScope()

    val projects by projectsFlow(context).collectAsState(initial = emptyList())
    val project = projects.firstOrNull { it.id == projectId }
    val projectName = project?.name?.ifEmpty { "UNNAMED PROJECT" } ?: "UNNAMED PROJECT"
    
    val settings by settingsFlow(context).collectAsState(initial = AiSettings())

    // Load chat from repository
    val chat by chatFlow(context, chatId).collectAsState(initial = null)

    // Where the chat came from (sticky tag under the header). Older insight chats are matched by text.
    val projectInsights by storedInsightsFlow(context, projectId).collectAsState(initial = emptyList())
    val origin = chat?.let { resolveChatOrigin(it, projectInsights) }
    var showOriginPreview by remember { mutableStateOf(false) }

    // A practice chat whose question never arrived is removed on the way out, so it doesn't sit empty in the list
    val leave: () -> Unit = {
        val c = chat
        if (c != null && c.isPractice && c.messages.isEmpty()) {
            scope.launch { deleteChat(context, c.id); onBack() }
        } else onBack()
    }
    androidx.activity.compose.BackHandler(enabled = chat?.isPractice == true && chat?.messages.isNullOrEmpty()) { leave() }
    
    // Optimistic messages that are added to local state immediately on send,
    // before the DataStore round-trip completes. Once DataStore emits and its
    // list already contains the message (matched by ID), the optimistic copy
    // is dropped so we don't show duplicates.
    var optimisticMessages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    
    val persistedMessages = chat?.messages ?: emptyList()
    val persistedIds = persistedMessages.map { it.id }.toSet()
    // Merged list: persisted messages + any optimistic ones not yet persisted
    val messages = persistedMessages + optimisticMessages.filter { it.id !in persistedIds }

    var inputText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var isSendingMessage by remember { mutableStateOf(false) }
    var isGeneratingResponse by remember { mutableStateOf(false) }
    var responseError by remember { mutableStateOf<String?>(null) }
    
    // Track if user has scrolled away from the bottom
    var showScrollToBottom by remember { mutableStateOf(false) }
    
    // Update showScrollToBottom based on scroll position
    LaunchedEffect(listState, messages.size) {
        snapshotFlow { 
            val lastIndex = messages.lastIndex
            if (lastIndex < 0) {
                false
            } else {
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                lastVisibleIndex < lastIndex
            }
        }.collect { shouldShow ->
            showScrollToBottom = shouldShow
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    var selectedMessageDelete by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showDeleteMessageDialog by remember { mutableStateOf(false) }
    
    var showEditMessageSheet by remember { mutableStateOf(false) }
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    
    var showToast by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf("") }
    var isToastLoading by remember { mutableStateOf(false) }
    
    // Auto-hide toast after 2 seconds (only when not loading)
    LaunchedEffect(showToast, isToastLoading) {
        if (showToast && !isToastLoading) {
            delay(2000)
            showToast = false
        }
    }
    
    // Concept extraction from the whole chat (⋮ → Extract concepts)
    val extraction = rememberConceptExtractionUi(chatId)
    val extractStatuses by com.wizaird.app.data.ConceptExtractor.status.collectAsState()
    val extracting = extractStatuses[chatId] == com.wizaird.app.data.ExtractionStatus.Running
    val pendingConcepts = chat?.conceptExtraction?.pending?.size ?: 0
    // Loading toast while extracting; the host replaces it with the result (or opens the popup)
    LaunchedEffect(extracting, extraction.awaitingResult) {
        if (extracting && extraction.awaitingResult) {
            toastMessage = "EXTRACTING CONCEPTS"
            isToastLoading = true
            showToast = true
        } else if (!extracting && toastMessage == "EXTRACTING CONCEPTS") {
            isToastLoading = false
            showToast = false
        }
    }

    // Get the current message from the messages list when editing
    val editingMessage = editingMessageId?.let { id ->
        messages.firstOrNull { it.id == id }
    }

    val svgLoader = remember {
        ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
    
    // Practice questions: the first one is asked when the chat is empty (right after it's created,
    // or after it's regenerated); later ones when the user taps NEXT QUESTION or regenerates one.
    var replacedQuestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var nextQuestionRequested by remember { mutableStateOf(false) }
    val needsQuestion = chat?.isPractice == true && (messages.isEmpty() || nextQuestionRequested)
    LaunchedEffect(chat?.id, needsQuestion, isGeneratingResponse, responseError == null, project != null, settings.apiKey.isNotBlank()) {
        val c = chat ?: return@LaunchedEffect
        val practiceOrigin = c.origin ?: return@LaunchedEffect
        val type = c.practice?.type ?: return@LaunchedEffect
        if (!needsQuestion || isGeneratingResponse ||
            responseError != null || project == null || settings.apiKey.isBlank()) return@LaunchedEffect

        val snapshotMessages = messages
        val isFirst = snapshotMessages.isEmpty()
        isGeneratingResponse = true
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val response = if (isFirst) {
                    val earlier = earlierPracticeQuestions(context, practiceOrigin, c.id) + replacedQuestions
                    val prompt = buildPracticeQuestionPrompt(project, practiceOrigin, type, earlier)
                    println("=== PRACTICE QUESTION PROMPT ===\n$prompt")
                    askAiWithThinking(settings, prompt, "Ask the practice question.")
                } else {
                    val history = snapshotMessages.takeLast(c.contextWindowSize).joinToString("\n") { msg ->
                        "${if (msg.sender == MessageSender.USER) "User" else "Assistant"}: ${msg.text}"
                    }
                    askAiWithThinking(
                        settings,
                        buildPracticeChatSystemPrompt(project, practiceOrigin, type),
                        com.wizaird.app.data.buildNextQuestionRequest(history, replacedQuestions)
                    )
                }
                // In case the model still added a marker, keep only what follows it
                val raw = com.wizaird.app.data.splitPracticeReply(response.answer).let { (before, after) -> after ?: before }
                val question = com.wizaird.app.data.formatPracticeQuestion(raw)
                if (question.isBlank() || question == "No response." || question.startsWith("API Error")) {
                    throw Exception(question.ifBlank { "Empty response" })
                }
                val questionMessage = ChatMessage(
                    sender = MessageSender.AI,
                    text = question,
                    thinkingContent = response.thinkingContent,
                    practiceRole = com.wizaird.app.data.PracticeRole.QUESTION
                )
                // Shown right away, so the chat never looks unanswered (and asks again) before the save arrives
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    optimisticMessages = optimisticMessages + questionMessage
                }
                addMessageToChat(context, c.id, questionMessage)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { nextQuestionRequested = false }
                if (isFirst) {
                    chatFlow(context, c.id).first()?.let {
                        upsertChat(context, it.copy(title = practiceTitle(practiceOrigin, question)))
                    }
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    responseError = "Failed to get a practice question: ${e.message}"
                }
            } finally {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    isGeneratingResponse = false
                }
            }
        }
    }

    // Auto-generate AI response when last message is from user.
    // Capture the snapshot of messages at effect-entry time so the IO coroutine
    // works on a stable list even if recomposition happens mid-flight.
    LaunchedEffect(messages.size, messages.lastOrNull()?.id) {
        val snapshotMessages = messages  // stable snapshot for this effect invocation
        if (snapshotMessages.isNotEmpty() && 
            snapshotMessages.last().sender == MessageSender.USER && 
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
                    println("User message: ${snapshotMessages.last().text}")
                    println("Total messages in chat: ${snapshotMessages.size}")
                    
                    val practiceOrigin = chat?.origin?.takeIf { chat?.isPractice == true }
                    val practiceType = chat?.practice?.type ?: com.wizaird.app.data.PracticeType.SURPRISE
                    // A question is open if the latest question came after the latest feedback
                    // (the opening question of older practice chats has no role, so it counts too)
                    val questionOpen = practiceOrigin != null && snapshotMessages.dropLast(1).let { earlier ->
                        val lastMarked = earlier.lastOrNull { it.sender == MessageSender.AI && it.practiceRole != null }
                        lastMarked?.practiceRole == com.wizaird.app.data.PracticeRole.QUESTION ||
                            (lastMarked == null && earlier.firstOrNull()?.sender == MessageSender.AI)
                    }
                    val systemPrompt = if (practiceOrigin != null) buildPracticeChatSystemPrompt(project, practiceOrigin, practiceType)
                        else buildChatSystemPrompt(project)
                    println("System prompt built (${systemPrompt.length} chars)")
                    println("--- SYSTEM PROMPT START ---")
                    println(systemPrompt)
                    println("--- SYSTEM PROMPT END ---")
                    
                    // Build conversation history for context using chat's context window size
                    val contextWindowSize = chat?.contextWindowSize ?: 50
                    val messagesToSend = snapshotMessages.takeLast(contextWindowSize)
                    println("Using context window size: $contextWindowSize (sending ${messagesToSend.size} messages)")
                    
                    val conversationHistory = messagesToSend.joinToString("\n") { msg ->
                        "${if (msg.sender == MessageSender.USER) "User" else "Assistant"}: ${msg.text}"
                    } + (if (practiceOrigin != null) com.wizaird.app.data.practiceTurnInstruction(questionOpen) else "")
                    println("--- CONVERSATION HISTORY START ---")
                    println(conversationHistory)
                    println("--- CONVERSATION HISTORY END ---")
                    
                    println("Calling askAi with settings:")
                    println("  Provider: ${settings.provider}")
                    println("  Model: ${settings.model}")
                    println("  API Key length: ${settings.apiKey.length}")
                    println("  Base URL: ${settings.baseUrl}")
                    
                    val aiResponse = askAiWithThinking(settings, systemPrompt, conversationHistory)
                    println("AI response received (${aiResponse.answer.length} chars)")
                    println("--- AI RESPONSE START ---")
                    println(aiResponse.answer)
                    println("--- AI RESPONSE END ---")
                    if (aiResponse.thinkingContent != null) {
                        println("--- THINKING CONTENT (${aiResponse.thinkingContent.length} chars) ---")
                    }
                    
                    if (practiceOrigin != null && !aiResponse.answer.startsWith("API Error") && aiResponse.answer != "No response.") {
                        // Practice: a reply with a verdict is feedback (it closes the question);
                        // discussion replies have no role. If the model still added a next question
                        // after the marker, it becomes its own question bubble.
                        val (feedback, nextQuestion) = com.wizaird.app.data.splitPracticeReply(aiResponse.answer)
                        val verdict = if (questionOpen) parseVerdict(feedback) else null
                        addMessageToChat(context, chatId, ChatMessage(
                            sender = MessageSender.AI,
                            text = com.wizaird.app.data.formatPracticeQuestion(feedback),
                            thinkingContent = aiResponse.thinkingContent,
                            verdict = verdict,
                            practiceRole = if (verdict != null) com.wizaird.app.data.PracticeRole.FEEDBACK else null
                        ))
                        if (nextQuestion != null) {
                            addMessageToChat(context, chatId, ChatMessage(
                                sender = MessageSender.AI,
                                text = com.wizaird.app.data.formatPracticeQuestion(nextQuestion),
                                practiceRole = com.wizaird.app.data.PracticeRole.QUESTION
                            ))
                        }
                    } else {
                        addMessageToChat(context, chatId, ChatMessage(
                            sender = MessageSender.AI,
                            text = aiResponse.answer,
                            thinkingContent = aiResponse.thinkingContent
                        ))
                    }
                    
                    println("=== CHAT AI RESPONSE SUCCESS ===")
                    
                    // Generate title from the user's first message (no API call needed)
                    if (chat?.title == "Generated Title" && snapshotMessages.size <= 2 && practiceOrigin == null) {
                        val firstUserMessage = snapshotMessages.firstOrNull { it.sender == MessageSender.USER }?.text ?: ""
                        val title = generateChatTitle(firstUserMessage)
                        println("Generated title: $title")
                        val currentChat = chatFlow(context, chatId).first()
                        currentChat?.let {
                            upsertChat(context, it.copy(title = title))
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
            // Status bar space - not needed on cover screen
            if (!isCoverScreen) {
                Spacer(modifier = Modifier.height(48.dp))
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

        // ── Header ────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    // Tap header to scroll to top
                    scope.launch {
                        if (messages.isNotEmpty()) {
                            listState.animateScrollToItem(0)
                        }
                    }
                }
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
                    .pixelCircleClickable(interactionSource = backInteraction) { leave() },
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

                                // Extract concepts option — opens the found concepts instead while some are waiting
                                val extractInteraction = remember { MutableInteractionSource() }
                                val canExtract = !extracting && (pendingConcepts > 0 || messages.isNotEmpty())
                                val extractColor = if (canExtract) colors.secondaryIcon else colors.secondaryIcon.copy(alpha = 0.35f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (canExtract) Modifier.pixelRounded8Clickable(
                                                interactionSource = extractInteraction,
                                                onClick = {
                                                    showMenu = false
                                                    chat?.let {
                                                        if (pendingConcepts > 0) extraction.showSheet = true
                                                        else extraction.request(context, it)
                                                    }
                                                }
                                            ) else Modifier
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data("file:///android_asset/pixelarticons/book-open.svg")
                                                .build(),
                                            imageLoader = svgLoader,
                                            contentDescription = "Extract concepts",
                                            colorFilter = ColorFilter.tint(extractColor),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            when {
                                                extracting -> "Extracting..."
                                                pendingConcepts > 0 -> "New concepts ($pendingConcepts)"
                                                else -> "Extract concepts"
                                            },
                                            style = pixelStyle(10, extractColor),
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
                                            colorFilter = ColorFilter.tint(colors.warn),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            "Delete",
                                            style = pixelStyle(10, colors.warn),
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

        // ── Origin tag (sticky: stays put while the messages scroll) ──────────
        if (origin != null) {
            ChatOriginTag(
                origin = origin,
                isPractice = chat?.isPractice == true,
                insight = origin.insightId?.let { id -> projectInsights.firstOrNull { it.id == id } },
                onClick = { showOriginPreview = true },
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
                verdicts = messages.mapNotNull { it.verdict }
            )
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
            items(messages, key = { "${it.id}-${it.timestamp}" }) { message ->
                val isLastAiMessage = message.sender == MessageSender.AI && 
                    message.id == messages.lastOrNull { it.sender == MessageSender.AI }?.id
                ChatBubble(
                    message = message,
                    projectId = projectId,
                    chatId = chatId,
                    onRegenerate = if (isLastAiMessage) {
                        {
                            // Regenerating the practice question: keep the old one out of the next question
                            if (chat?.isPractice == true && (messages.size == 1 || message.practiceRole == com.wizaird.app.data.PracticeRole.QUESTION)) {
                                replacedQuestions = replacedQuestions + message.text
                                // A later question is asked again by the next-question path, not as a reply
                                if (messages.size > 1) nextQuestionRequested = true
                            }
                            optimisticMessages = optimisticMessages.filter { it.id != message.id }
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                removeLastAiMessage(context, chatId)
                            }
                        }
                    } else null,
                    onPractice = if (chat?.isPractice != true && message.sender == MessageSender.AI &&
                        !message.text.startsWith("API Error") && message.text != "No response.") {
                        {
                            chat?.let { c ->
                                // The first bubble of an insight chat is the insight itself: practise the insight
                                val isTheInsight = origin?.type == com.wizaird.app.data.ChatOriginType.INSIGHT &&
                                    messages.firstOrNull()?.id == message.id && origin.text?.trim() == message.text.trim()
                                PracticeLauncher.request(projectId, if (isTheInsight) origin!! else originFromMessage(c, message))
                            }
                        }
                    } else null,
                    onEdit = {
                        editingMessageId = message.id
                        showEditMessageSheet = true
                    },
                    onDelete = {
                        selectedMessageDelete = {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                deleteMessageFromChat(context, chatId, message.id)
                            }
                        }
                        showDeleteMessageDialog = true
                    },
                    onShowToast = { msg ->
                        toastMessage = msg
                        showToast = true
                        isToastLoading = msg == "ADDING TO CONCEPTS"
                    },
                    onNewConceptClick = onNewConceptClick
                )
            }
            
            // Practice: NEXT QUESTION under the latest reply once no question is waiting
            val showNextQuestion = chat?.isPractice == true && !isGeneratingResponse && responseError == null &&
                messages.lastOrNull()?.let { it.sender == MessageSender.AI && it.practiceRole != com.wizaird.app.data.PracticeRole.QUESTION && messages.size > 1 } == true
            if (showNextQuestion) {
                item(key = "next-question") {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        PixelButtonSmall(
                            label = "NEXT QUESTION",
                            primary = false,
                            cutColor = colors.background,
                            onClick = { nextQuestionRequested = true }
                        )
                    }
                }
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
                                    style = minecraftStyle(14, colors.warn)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val retryInteraction = remember { MutableInteractionSource() }
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data("file:///android_asset/pixelarticons/refresh.svg")
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

                    // Optimistically show the message instantly — no DataStore wait
                    val userMessage = ChatMessage(
                        sender = MessageSender.USER,
                        text = q
                    )
                    optimisticMessages = optimisticMessages + userMessage

                    scope.launch {
                        // Persist to DataStore in background
                        addMessageToChat(context, chatId, userMessage)
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

        // Delete message confirmation dialog
        if (showDeleteMessageDialog) {
            PixelConfirmationDialog(
                title = "DELETE MESSAGE",
                message = "Are you sure you want to delete this message? This action cannot be undone.",
                confirmLabel = "DELETE",
                cancelLabel = "CANCEL",
                isDestructive = true,
                onConfirm = {
                    showDeleteMessageDialog = false
                    selectedMessageDelete?.invoke()
                },
                onDismiss = {
                    showDeleteMessageDialog = false
                }
            )
        }

        // Edit message bottom sheet
        if (showEditMessageSheet && editingMessage != null) {
            EditMessageBottomSheet(
                message = editingMessage!!,
                onSave = { updatedText ->
                    val messageId = editingMessage!!.id
                    scope.launch {
                        updateMessageInChat(context, chatId, messageId, updatedText)
                        // Close sheet after update completes
                        showEditMessageSheet = false
                        editingMessageId = null
                    }
                },
                onDismiss = {
                    showEditMessageSheet = false
                    editingMessageId = null
                }
            )
        }
        
        // Floating "go to bottom" button - positioned 8px above input bar
        androidx.compose.animation.AnimatedVisibility(
            visible = showScrollToBottom,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 76.dp), // 8dp above input bar (input bar is ~68dp tall)
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            val goToBottomInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .drawPixelCircle(
                        fillColor = colors.textHigh.copy(alpha = 0.65f),
                        borderColor = Color.Transparent,
                        cutColor = colors.background
                    )
                    .pixelCircleClickable(interactionSource = goToBottomInteraction) {
                        scope.launch {
                            listState.animateScrollToItem(messages.lastIndex)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data("file:///android_asset/pixelarticons/chevron-down.svg")
                        .build(),
                    imageLoader = svgLoader,
                    contentDescription = "Go to bottom",
                    colorFilter = ColorFilter.tint(colors.secondarySurface),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        chat?.let {
            ConceptExtractionHost(chat = it, ui = extraction, onToast = { msg ->
                toastMessage = msg
                isToastLoading = false
                showToast = true
            })
        }

        if (showOriginPreview && origin != null) {
            ChatOriginPreview(
                origin = origin,
                insight = origin.insightId?.let { id -> projectInsights.firstOrNull { it.id == id } },
                onDismiss = { showOriginPreview = false },
                onOpenChat = { id -> showOriginPreview = false; onOpenChat(id) },
                onOpenConcept = { id -> showOriginPreview = false; onOpenConcept(id) },
                onInsightChatClick = { id -> showOriginPreview = false; onInsightChatClick(id) },
                onNewConceptClick = onNewConceptClick
            )
        }

        // Toast overlay
        PixelToast(
            message = toastMessage,
            visible = showToast,
            modifier = Modifier.align(Alignment.BottomCenter),
            isLoading = isToastLoading
        )
    }
}

// ── Bubble ────────────────────────────────────────────────────────────────────

/** Where an "Add to concepts" from this bubble came from: a chat message, an insight, or nothing stored. */
private fun conceptSourceFor(message: ChatMessage, chatId: String?, insightId: String?, selectedText: String): com.wizaird.app.data.ConceptSource? {
    val snippet = com.wizaird.app.data.buildSnippet(message.text, selectedText)
    return when {
        chatId != null -> com.wizaird.app.data.ConceptSource(
            type = com.wizaird.app.data.ConceptSourceType.CHAT_MESSAGE,
            chatId = chatId,
            messageId = message.id,
            snippet = snippet
        )
        insightId != null -> com.wizaird.app.data.ConceptSource(
            type = com.wizaird.app.data.ConceptSourceType.INSIGHT,
            insightId = insightId,
            snippet = snippet
        )
        else -> null
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: ChatMessage,
    projectId: String,
    chatId: String? = null,     // set for stored chats, so concepts can link back to the message
    insightId: String? = null,  // set when the bubble shows a stored insight
    onRegenerate: (() -> Unit)? = null,
    onPractice: (() -> Unit)? = null,  // AI replies: practise this answer (null = no practice icon)
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onShowToast: (String) -> Unit = {},
    onNewConceptClick: () -> Unit = {}
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
    var noteSaved by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showThinking by remember { mutableStateOf(false) }
    // Bumped on tap to clear any active text selection
    var selectionResetKey by remember { mutableStateOf(0) }

    val svgLoader = remember {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box {
            val bubbleInteraction = remember { MutableInteractionSource() }
            PixelBox(
                modifier = Modifier
                    .then(
                        if (isUser) Modifier.padding(start = 32.dp) else Modifier.padding(end = 32.dp)
                    )
                    .clickable(
                        interactionSource = bubbleInteraction,
                        indication = null
                    ) { selectionResetKey++ },
                fillColor = if (isUser) userBubbleFill else aiBubbleFill,
                borderColor = androidx.compose.ui.graphics.Color.Transparent,
                cornerStyle = PixelCornerStyle.Rounded
            ) {
                if (isUser) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        key(selectionResetKey) {
                            SelectableMarkdownText(
                                markdown = message.text,
                                style = minecraftStyle(14, userBubbleText),
                                onAddToConcepts = { selectedText ->
                                    ConceptNavigationData.setPendingData(
                                        name = selectedText,
                                        contextText = message.text,
                                        source = conceptSourceFor(message, chatId, insightId, selectedText)
                                    )
                                    onNewConceptClick()
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Options icon for user bubble - wrapped in Box for popover positioning
                            Box {
                                val optionsInteraction = remember { MutableInteractionSource() }
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data("file:///android_asset/pixelarticons/more-vertical.svg")
                                        .build(),
                                    imageLoader = svgLoader,
                                    contentDescription = "Options",
                                    colorFilter = ColorFilter.tint(userBubbleText),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .pixelRounded8ClickableOversize(
                                            interactionSource = optionsInteraction
                                        ) {
                                            showOptionsMenu = true
                                        }
                                )
                                
                                // Options popover for user bubble
                                if (showOptionsMenu) {
                                    val density = LocalDensity.current
                                    val offsetY = with(density) { -(20 + 8).dp.roundToPx() }
                                    Popup(
                                        alignment = Alignment.BottomEnd,
                                        offset = IntOffset(x = 0, y = offsetY),
                                        onDismissRequest = { showOptionsMenu = false },
                                        properties = PopupProperties(focusable = false)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .graphicsLayer {
                                                    compositingStrategy = CompositingStrategy.Offscreen
                                                    shadowElevation = 2.dp.toPx()
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
                                                // Edit option
                                                val editInteraction = remember { MutableInteractionSource() }
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .pixelRounded8Clickable(
                                                            interactionSource = editInteraction,
                                                            onClick = {
                                                                showOptionsMenu = false
                                                                onEdit()
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
                                                                .data("file:///android_asset/pixelarticons/pen-square.svg")
                                                                .build(),
                                                            imageLoader = svgLoader,
                                                            contentDescription = "Edit",
                                                            colorFilter = ColorFilter.tint(colors.secondaryIcon),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Text(
                                                            "Edit",
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
                                                                showOptionsMenu = false
                                                                onDelete()
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
                                                            colorFilter = ColorFilter.tint(colors.warn),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Text(
                                                            "Delete",
                                                            style = pixelStyle(10, colors.warn),
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
                    }
                } else {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        key(selectionResetKey) {
                            SelectableMarkdownText(
                                markdown = message.text,
                                style = minecraftStyle(14, aiBubbleText),
                                onAddToConcepts = { selectedText ->
                                    ConceptNavigationData.setPendingData(
                                        name = selectedText,
                                        contextText = message.text,
                                        source = conceptSourceFor(message, chatId, insightId, selectedText)
                                    )
                                    onNewConceptClick()
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left side: Copy, Thinking, Note, Practice, Regenerate — every action row uses this order; regenerate/new is always last
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
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
                                            onShowToast("COPIED TO CLIPBOARD")
                                            scope.launch {
                                                delay(2000)
                                                copied = false
                                            }
                                        }
                                )

                                // Thinking icon — only interactive when thinking content exists
                                val hasThinking = message.thinkingContent != null
                                val thinkingInteraction = remember { MutableInteractionSource() }
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data("file:///android_asset/pixelarticons/ai-app-mac.svg")
                                        .build(),
                                    imageLoader = svgLoader,
                                    contentDescription = if (hasThinking) "View AI reasoning" else "No thought process captured",
                                    colorFilter = ColorFilter.tint(
                                        if (hasThinking) colors.textXLow
                                        else colors.textXLow.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .then(
                                            if (hasThinking) Modifier.pixelRounded8ClickableOversize(
                                                interactionSource = thinkingInteraction
                                            ) { showThinking = true }
                                            else Modifier
                                        )
                                )

                                val noteInteraction = remember { MutableInteractionSource() }
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data("file:///android_asset/pixelarticons/sticky-note-text.svg")
                                        .build(),
                                    imageLoader = svgLoader,
                                    contentDescription = "Save to note",
                                    colorFilter = ColorFilter.tint(if (noteSaved) colors.textHigh else colors.textXLow),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .pixelRounded8ClickableOversize(
                                            interactionSource = noteInteraction
                                        ) {
                                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                // Create a new note with the message text
                                                val note = com.wizaird.app.data.newNote(projectId).copy(
                                                    body = message.text
                                                )
                                                com.wizaird.app.data.upsertNote(context, note)
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    noteSaved = true
                                                    onShowToast("NOTE CREATED")
                                                    // Reset the saved state after 2 seconds
                                                    scope.launch {
                                                        delay(2000)
                                                        noteSaved = false
                                                    }
                                                }
                                            }
                                        }
                                )
                                
                                // Practice icon — practise what this reply explained
                                if (onPractice != null) {
                                    PracticeIcon(onClick = onPractice)
                                }

                                // Regenerate icon — only on last AI message
                                if (onRegenerate != null) {
                                    val regenInteraction = remember { MutableInteractionSource() }
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data("file:///android_asset/pixelarticons/refresh.svg")
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
                            
                            // Right side: Options icon - wrapped in Box for popover positioning
                            Box {
                                val optionsInteraction = remember { MutableInteractionSource() }
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data("file:///android_asset/pixelarticons/more-vertical.svg")
                                        .build(),
                                    imageLoader = svgLoader,
                                    contentDescription = "Options",
                                    colorFilter = ColorFilter.tint(colors.textXLow),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .pixelRounded8ClickableOversize(
                                            interactionSource = optionsInteraction
                                        ) {
                                            showOptionsMenu = true
                                        }
                                )
                                
                                // Options popover for AI bubble
                                if (showOptionsMenu) {
                                    val density = LocalDensity.current
                                    val offsetY = with(density) { -(20 + 8).dp.roundToPx() }
                                    Popup(
                                        alignment = Alignment.BottomEnd,
                                        offset = IntOffset(x = 0, y = offsetY),
                                        onDismissRequest = { showOptionsMenu = false },
                                        properties = PopupProperties(focusable = false)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .graphicsLayer {
                                                    compositingStrategy = CompositingStrategy.Offscreen
                                                    shadowElevation = 2.dp.toPx()
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
                                                // Edit option
                                                val editInteraction = remember { MutableInteractionSource() }
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .pixelRounded8Clickable(
                                                            interactionSource = editInteraction,
                                                            onClick = {
                                                                showOptionsMenu = false
                                                                onEdit()
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
                                                                .data("file:///android_asset/pixelarticons/pen-square.svg")
                                                                .build(),
                                                            imageLoader = svgLoader,
                                                            contentDescription = "Edit",
                                                            colorFilter = ColorFilter.tint(colors.secondaryIcon),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Text(
                                                            "Edit",
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
                                                                showOptionsMenu = false
                                                                onDelete()
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
                                                            colorFilter = ColorFilter.tint(colors.warn),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Text(
                                                            "Delete",
                                                            style = pixelStyle(10, colors.warn),
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
                    }
                }
            }
        }
    }

    // Thinking overlay — Dialog is valid at the composable scope level
    if (showThinking && !isUser) {
        ThinkingOverlay(
            thinkingContent = message.thinkingContent ?: "No thought process was captured for this response.",
            onDismiss = { showThinking = false }
        )
    }
}

// ── Edit Message Bottom Sheet ─────────────────────────────────────────────────

@Composable
fun EditMessageBottomSheet(
    message: ChatMessage,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalWizairdColors.current
    val context = LocalContext.current
    
    var editedText by remember { mutableStateOf(message.text) }
    val hasChanges = editedText != message.text
    
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    
    val svgLoader = remember {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }
    
    // Request focus when sheet opens
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
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
        // Bottom sheet taking up most of the screen
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawBehind {
                    val p = PixelSize.toPx()
                    val w = size.width
                    val h = size.height
                    val fill = colors.background

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
                    .fillMaxSize()
                    .imePadding()
            ) {
                Spacer(modifier = Modifier.height(14.dp))

                // Header — back button | "EDIT MESSAGE" title | save button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    // Back button — left
                    val backInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(40.dp)
                            .drawPixelCircle(
                                fillColor   = colors.secondaryButton,
                                borderColor = Color.Transparent,
                                cutColor    = colors.background
                            )
                            .pixelCircleClickable(interactionSource = backInteraction) { onDismiss() },
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

                    // Title — centered
                    Text(
                        text = "EDIT MESSAGE",
                        style = pixelStyle(10, colors.secondaryIcon),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = (-2).dp)
                    )

                    // Save button — turns primary when there are changes
                    PixelButtonSmall(
                        label = "SAVE",
                        primary = hasChanges,
                        modifier = Modifier.align(Alignment.CenterEnd),
                        cutColor = colors.background,
                        onClick = { if (hasChanges) onSave(editedText) }
                    )
                }

                // Text editor — fills remaining space
                BasicTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .focusRequester(focusRequester)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    textStyle = minecraftStyle(14, colors.textHigh).copy(
                        lineHeight = (14 * 1.6f).sp
                    ),
                    cursorBrush = SolidColor(colors.coral)
                )

                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }
    }
}

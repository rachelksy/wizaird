package com.wizaird.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wizaird.app.data.*
import com.wizaird.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun InsightChatScreen(
    projectId: String,
    insightId: String,
    onBack: () -> Unit,
    onChatCreated: (String) -> Unit = {},
    onNewGlossaryWordClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val scope = rememberCoroutineScope()

    val projects by projectsFlow(context).collectAsState(initial = emptyList())
    val project = projects.firstOrNull { it.id == projectId }
    val projectName = project?.name?.ifEmpty { "UNNAMED PROJECT" } ?: "UNNAMED PROJECT"
    
    val settings by settingsFlow(context).collectAsState(initial = AiSettings())

    // Load the insight
    val insight by storedInsightFlow(context, insightId).collectAsState(initial = null)

    var inputText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var isCreatingChat by remember { mutableStateOf(false) }
    
    var showToast by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf("") }
    var isToastLoading by remember { mutableStateOf(false) }
    
    // Auto-hide toast after 2 seconds (only when not loading)
    LaunchedEffect(showToast, isToastLoading) {
        if (showToast && !isToastLoading) {
            kotlinx.coroutines.delay(2000)
            showToast = false
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .imePadding()
        ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Header
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

            // Project name - centered
            Text(
                text = projectName,
                style = pixelStyle(10, colors.secondaryIcon),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-2).dp)
            )
        }

        // Messages area
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Show the insight as an AI message
            if (insight != null) {
                item {
                    val aiMessage = ChatMessage(
                        sender = MessageSender.AI,
                        text = insight!!.text
                    )
                    ChatBubble(
                        message = aiMessage,
                        projectId = projectId,
                        onRegenerate = null,
                        onEdit = {},
                        onDelete = {},
                        onShowToast = { msg ->
                            toastMessage = msg
                            showToast = true
                            isToastLoading = msg == "ADDING TO GLOSSARY"
                        },
                        onNewGlossaryWordClick = onNewGlossaryWordClick
                    )
                }
            }
        }

        // Input bar
        PixelInputBar(
            value = inputText,
            onValueChange = { inputText = it },
            onSubmit = {
                val q = inputText.trim()
                if (q.isNotEmpty() && !isCreatingChat && project != null && insight != null) {
                    inputText = ""
                    isCreatingChat = true
                    
                    scope.launch {
                        // Create AI message from insight
                        val aiMessage = ChatMessage(
                            sender = MessageSender.AI,
                            text = insight!!.text
                        )
                        
                        // Create user message
                        val userMessage = ChatMessage(
                            sender = MessageSender.USER,
                            text = q
                        )
                        
                        // Create new chat with both messages
                        val newChat = ChatData(
                            projectId = projectId,
                            title = "Generated Title",
                            messages = listOf(aiMessage, userMessage),
                            insightId = insightId
                        )
                        
                        // Save the chat
                        upsertChat(context, newChat)
                        
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
        
        // Toast overlay
        if (showToast) {
            PixelToast(
                message = toastMessage,
                visible = showToast,
                modifier = Modifier.align(Alignment.BottomCenter),
                isLoading = isToastLoading
            )
        }
    }
}

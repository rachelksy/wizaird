package com.wizaird.app.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wizaird.app.data.chatFlow
import com.wizaird.app.data.upsertChat
import com.wizaird.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChatSettingsScreen(
    projectId: String,
    chatId: String,
    onBack: () -> Unit,
    onConceptClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalWizairdColors.current
    val isCoverScreen = rememberIsCoverScreen()

    val chat by chatFlow(context, chatId).collectAsState(initial = null)

    var title by remember(chat) { mutableStateOf(chat?.title ?: "") }
    var contextWindowSize by remember(chat) { mutableStateOf(chat?.contextWindowSize?.toString() ?: "50") }

    // Concepts in this project, and the ones linked to this chat
    val projectConcepts by remember(projectId) {
        com.wizaird.app.data.conceptsFlow(context, projectId, sortOrder = com.wizaird.app.data.ConceptSortOrder.ALPHABETICAL)
    }.collectAsState(initial = emptyList())
    val linkedConcepts = projectConcepts.filter { c -> c.sources.any { it.chatId == chatId } }
    var showConceptsSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Status bar space - not needed on cover screen
            if (!isCoverScreen) {
                Spacer(modifier = Modifier.height(48.dp))
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

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
                    "CHAT SETTINGS",
                    style = pixelStyle(14, colors.secondaryIcon),
                    modifier = Modifier
                        .weight(1f)
                        .offset(y = (-2).dp)
                )
                PixelButtonSmall(
                    label = "SAVE",
                    primary = true,
                    onClick = {
                        scope.launch {
                            chat?.let {
                                val windowSize = contextWindowSize.toIntOrNull() ?: 50
                                upsertChat(
                                    context,
                                    it.copy(
                                        title = title,
                                        contextWindowSize = windowSize.coerceIn(1, 1000)
                                    )
                                )
                            }
                            onBack()
                        }
                    }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Chat name
                SettingsField(label = "CHAT NAME") {
                    PixelTextInput(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = "MY CHAT"
                    )
                }

                // Linked concepts — pills (tap to open the concept) + an Edit text button for the picker sheet
                SettingsField(label = "LINKED CONCEPTS") {
                    if (linkedConcepts.isEmpty()) {
                        // No pills: Edit sits on the same baseline as the sentence
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.offset(y = (-2).dp)
                        ) {
                            Text(
                                "No concepts linked",
                                style = minecraftStyle(12, colors.secondaryIconSoft),
                                modifier = Modifier.alignByBaseline()
                            )
                            PixelTextButton(
                                label = "Edit",
                                onClick = { showConceptsSheet = true },
                                modifier = Modifier.alignByBaseline()
                            )
                        }
                    } else androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // With pills: Edit is centered on the pill row
                        linkedConcepts.forEach { concept ->
                            ConceptPill(label = concept.name, ghost = concept.isGhost, onClick = { onConceptClick(concept.id) })
                        }
                        PixelTextButton(
                            label = "Edit",
                            onClick = { showConceptsSheet = true },
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }

                // Context window size
                SettingsField(label = "CONTEXT WINDOW SIZE") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Number of last messages to send to AI for context. Higher values provide more context but cost more.",
                            style = minecraftStyle(10, colors.secondaryIconSoft),
                            modifier = Modifier.offset(y = (-2).dp)
                        )
                        PixelTextInput(
                            value = contextWindowSize,
                            onValueChange = { 
                                // Only allow digits
                                if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                                    contextWindowSize = it
                                }
                            },
                            placeholder = "50"
                        )
                    }
                }
            }
        }
    
        if (showConceptsSheet) {
            chat?.let { currentChat ->
                LinkConceptsBottomSheet(
                    concepts = projectConcepts,
                    initiallyLinked = linkedConcepts.map { it.id }.toSet(),
                    onSave = { ids ->
                        showConceptsSheet = false
                        scope.launch { com.wizaird.app.data.setChatConcepts(context, currentChat, ids) }
                    },
                    onDismiss = { showConceptsSheet = false }
                )
            }
        }
    }
}

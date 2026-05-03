package com.wizaird.app.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wizaird.app.data.GlossaryWord
import com.wizaird.app.data.upsertGlossaryWord
import com.wizaird.app.ui.theme.*
import kotlinx.coroutines.launch

// Temporary storage for passing glossary data through navigation
object GlossaryNavigationData {
    var pendingTerm: String? = null
    var pendingExplanation: String? = null
    var pendingAliases: String? = null
    
    fun setPendingData(term: String, explanation: String, aliases: String) {
        pendingTerm = term
        pendingExplanation = explanation
        pendingAliases = aliases
    }
    
    fun consumePendingData(): Triple<String, String, String>? {
        val data = if (pendingTerm != null) {
            Triple(pendingTerm!!, pendingExplanation ?: "", pendingAliases ?: "")
        } else null
        
        // Clear after consuming
        pendingTerm = null
        pendingExplanation = null
        pendingAliases = null
        
        return data
    }
}

@Composable
fun NewGlossaryWordScreen(
    projectId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalWizairdColors.current

    // Check for pending data from navigation
    val pendingData = remember { GlossaryNavigationData.consumePendingData() }
    
    var term by remember { mutableStateOf(pendingData?.first ?: "") }
    var explanation by remember { mutableStateOf(pendingData?.second ?: "") }
    var aliases by remember { mutableStateOf(pendingData?.third ?: "") }

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
                    "NEW WORD",
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
                            upsertGlossaryWord(
                                context,
                                GlossaryWord(
                                    projectId = projectId,
                                    word = term,
                                    explanation = explanation,
                                    aliases = aliases,
                                    createdAt = System.currentTimeMillis()
                                )
                            )
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
                // Term (1 line)
                SettingsField(label = "TERM") {
                    PixelTextInput(
                        value = term,
                        onValueChange = { term = it },
                        placeholder = "ENTER TERM"
                    )
                }

                // Definition (5 lines, grows with text)
                SettingsField(label = "DEFINITION") {
                    PixelBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp), // ~5 lines minimum
                        fillColor = colors.secondarySurface,
                        cornerStyle = PixelCornerStyle.Rounded
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = explanation,
                            onValueChange = { explanation = it },
                            textStyle = minecraftStyle(12, colors.secondaryIcon),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.secondaryIcon),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.TopStart) {
                                    if (explanation.isEmpty()) {
                                        Text(
                                            "Explain the term...",
                                            style = minecraftStyle(12, colors.secondaryIconSoft),
                                            modifier = Modifier.offset(y = (-2).dp)
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                    }
                }

                // Aliases (1 line, grows with text)
                SettingsField(label = "ALIASES") {
                    PixelBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp), // ~1 line minimum
                        fillColor = colors.secondarySurface,
                        cornerStyle = PixelCornerStyle.Rounded
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = aliases,
                            onValueChange = { aliases = it },
                            textStyle = minecraftStyle(12, colors.secondaryIcon),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.secondaryIcon),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.TopStart) {
                                    if (aliases.isEmpty()) {
                                        Text(
                                            "Comma-separated aliases for search...",
                                            style = minecraftStyle(12, colors.secondaryIconSoft),
                                            modifier = Modifier.offset(y = (-2).dp)
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

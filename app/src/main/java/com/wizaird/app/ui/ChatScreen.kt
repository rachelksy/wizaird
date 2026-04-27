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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wizaird.app.data.projectsFlow
import com.wizaird.app.ui.theme.*

@Composable
fun ChatScreen(
    projectId: String,
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

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Bob animation — same timing as HomeScreen
    // val bobAnim = rememberInfiniteTransition(label = "bob")
    // val bobY by bobAnim.animateFloat(
    //     initialValue = 0f, targetValue = -2f,
    //     animationSpec = infiniteRepeatable(
    //         animation = tween(360, easing = LinearEasing),
    //         repeatMode = RepeatMode.Reverse
    //     ), label = "bobY"
    // )
    val bobY = 0f

    // Chat bubble state — independent from HomeScreen
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
                    onClick = onMoreClick
                )
            }
        }

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
                // Speech bubble — hugs text, narrowed with horizontal padding
                PixelBox(
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(horizontal = 40.dp),
                    fillColor = colors.secondarySurface,
                    cornerStyle = PixelCornerStyle.Rounded,
                    speechTail = true
                ) {
                    SelectionContainer {
                        Text(
                            text = bubbleText,
                            style = pixelStyle(14, colors.textHigh).copy(
                                lineHeight = (14 * 1.6f).sp
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(16.dp)
                                .offset(y = (-2).dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Wizard GIF
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

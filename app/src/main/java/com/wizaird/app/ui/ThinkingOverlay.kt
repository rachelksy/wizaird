package com.wizaird.app.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wizaird.app.ui.theme.*

/**
 * Full-screen overlay that displays the AI's reasoning/thinking content.
 * Mirrors the layout of InsightPreviewOverlay — same Dialog wrapper, same
 * PixelBox container, same header + scrollable body + close button pattern.
 */
@Composable
fun ThinkingOverlay(
    thinkingContent: String,
    onDismiss: () -> Unit
) {
    val colors = LocalWizairdColors.current
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 48.dp)
                .padding(8.dp)
        ) {
            PixelBox(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(PixelRoundedShape),
                fillColor = colors.secondarySurface,
                borderColor = colors.border,
                cutColor = colors.secondarySurface,
                cornerStyle = PixelCornerStyle.Rounded
            ) {
                Column(modifier = Modifier.fillMaxSize()) {

                    // Fixed header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AI REASONING",
                            style = pixelStyle(10, colors.secondaryIconSoft),
                            modifier = Modifier.offset(y = (-2).dp)
                        )

                        // Close button — same pixel-X pattern as InsightPreviewOverlay
                        val closeInteraction = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .pixelRounded8Clickable(
                                    interactionSource = closeInteraction,
                                    onClick = onDismiss
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .drawBehind {
                                        val p = PixelSize.toPx()
                                        val color = colors.secondaryIcon
                                        // Top-left → bottom-right diagonal
                                        drawRect(color, Offset(p * 1, p * 1), Size(p, p))
                                        drawRect(color, Offset(p * 2, p * 2), Size(p, p))
                                        drawRect(color, Offset(p * 3, p * 3), Size(p, p))
                                        drawRect(color, Offset(p * 4, p * 4), Size(p, p))
                                        drawRect(color, Offset(p * 5, p * 5), Size(p, p))
                                        // Top-right → bottom-left diagonal
                                        drawRect(color, Offset(p * 5, p * 1), Size(p, p))
                                        drawRect(color, Offset(p * 4, p * 2), Size(p, p))
                                        drawRect(color, Offset(p * 3, p * 3), Size(p, p))
                                        drawRect(color, Offset(p * 2, p * 4), Size(p, p))
                                        drawRect(color, Offset(p * 1, p * 5), Size(p, p))
                                    }
                            )
                        }
                    }

                    // Scrollable thinking content
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .padding(bottom = 16.dp)
                        ) {
                            Text(
                                text = thinkingContent,
                                style = minecraftStyle(12, colors.textLow).copy(
                                    lineHeight = (12 * 1.6f).sp
                                ),
                                modifier = Modifier.offset(y = (-2).dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

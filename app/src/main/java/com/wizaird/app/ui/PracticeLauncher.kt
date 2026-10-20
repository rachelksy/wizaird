package com.wizaird.app.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.wizaird.app.ui.theme.LocalWizairdColors
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.wizaird.app.data.ChatOrigin
import com.wizaird.app.data.PracticeType
import com.wizaird.app.data.createPracticeChat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Starts practice from anywhere (insight, concept page, chat reply, home bubble):
 * [request] opens the question-type popup, picking a type creates the practice chat and opens it.
 * The popup lives in [PracticeLauncherHost] at the app root; callers inside a Dialog
 * (e.g. the insight preview) close their dialog first.
 */
object PracticeLauncher {
    data class Request(val projectId: String, val origin: ChatOrigin)

    val pending = MutableStateFlow<Request?>(null)

    fun request(projectId: String, origin: ChatOrigin) {
        pending.value = Request(projectId, origin)
    }
}

@Composable
fun PracticeLauncherHost(onChatCreated: (projectId: String, chatId: String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val request by PracticeLauncher.pending.collectAsState()
    request?.let { r ->
        PracticeTypeDialog(
            onSelect = { type ->
                PracticeLauncher.pending.value = null
                scope.launch {
                    val chat = createPracticeChat(context, r.projectId, r.origin, type)
                    onChatCreated(r.projectId, chat.id)
                }
            },
            onDismiss = { PracticeLauncher.pending.value = null }
        )
    }
}

/** One line under each type in the popup. */
private fun PracticeType.explanation(): String = when (this) {
    PracticeType.SURPRISE -> "The wizard picks the best type."
    PracticeType.EXPLAIN -> "Put the idea in your own words."
    PracticeType.APPLY -> "Use the idea in a realistic situation."
    PracticeType.SPOT_THE_PROBLEM -> "Find what's wrong in a claim."
    PracticeType.COMPARE -> "Tell it apart from a similar idea."
    PracticeType.PREDICT -> "Say what changes, and why."
    PracticeType.QUICK_CHECK -> "Pick the right option."
}

/** Popup for picking the question type: one full-width, left-aligned button per type. */
@Composable
private fun PracticeTypeDialog(onSelect: (PracticeType) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalWizairdColors.current
    // Full width minus the app's 16dp side margins (the platform default dialog width is much narrower)
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        PixelBox(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(PixelRoundedShape),
            fillColor = colors.background,
            borderColor = colors.border,
            cutColor = colors.background,
            cornerStyle = PixelCornerStyle.Rounded
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Title + close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "PRACTICE",
                        style = pixelStyle(12, colors.textHigh),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp)
                            .offset(y = (-2).dp)
                    )
                    PixelCloseButton(onClick = onDismiss)
                }
                Text(
                    "Pick a type of question, or let the wizard choose.",
                    style = minecraftStyle(14, colors.textLow).copy(lineHeight = (14 * 1.6f).sp),
                    modifier = Modifier
                        .padding(start = 4.dp, bottom = 6.dp)
                        .offset(y = (-2).dp)
                )
                PracticeType.values().forEach { type ->
                    val interaction = remember { MutableInteractionSource() }
                    PixelBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pixelRounded8Clickable(interactionSource = interaction, onClick = { onSelect(type) }),
                        fillColor = colors.secondaryButton,
                        borderColor = Color.Transparent,
                        cutColor = colors.background,
                        cornerStyle = PixelCornerStyle.Rounded8
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                type.label.uppercase(),
                                style = pixelStyle(10, colors.secondaryIcon),
                                modifier = Modifier.offset(y = (-2).dp)
                            )
                            Text(
                                type.explanation(),
                                style = minecraftStyle(12, colors.textLow),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Pixel "X" close button (same drawing as the insight preview's close). */
@Composable
fun PixelCloseButton(onClick: () -> Unit) {
    val colors = LocalWizairdColors.current
    val interaction = remember { MutableInteractionSource() }
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(32.dp)
            .pixelRounded8Clickable(interactionSource = interaction, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(12.dp)
                .drawBehind {
                    val p = PixelSize.toPx()
                    val color = colors.secondaryIcon
                    for (i in 1..5) {
                        drawRect(color, Offset(p * i, p * i), Size(p, p))
                        drawRect(color, Offset(p * (6 - i), p * i), Size(p, p))
                    }
                }
        )
    }
}

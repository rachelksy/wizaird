package com.wizaird.app.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wizaird.app.data.ChatOrigin
import com.wizaird.app.data.ChatOriginType
import com.wizaird.app.data.PracticeVerdict
import com.wizaird.app.data.StoredInsight
import com.wizaird.app.data.practiceScore
import com.wizaird.app.data.chatFlow
import com.wizaird.app.data.conceptFlow
import com.wizaird.app.data.conceptsFlow
import com.wizaird.app.ui.theme.LocalWizairdColors
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Practice icon for action rows (insight preview, home bubble, AI chat replies). */
@Composable
fun PracticeIcon(onClick: () -> Unit, enabled: Boolean = true) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val interaction = remember { MutableInteractionSource() }
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data("file:///android_asset/pixelarticons/target.svg")
            .build(),
        imageLoader = rememberSvgLoader(),
        contentDescription = "Practice",
        colorFilter = ColorFilter.tint(if (enabled) colors.textXLow else colors.textXLow.copy(alpha = 0.35f)),
        modifier = Modifier
            .size(20.dp)
            .then(
                if (enabled) Modifier.pixelRounded8ClickableOversize(interactionSource = interaction) { onClick() }
                else Modifier
            )
    )
}

/** "FROM INSIGHT · OCT 20", "PRACTICE · FROM CONCEPT · RAG", ... */
private fun originTagText(origin: ChatOrigin, isPractice: Boolean, insight: StoredInsight?): String {
    val from = when (origin.type) {
        ChatOriginType.INSIGHT -> "FROM INSIGHT" + (insight?.let {
            " · " + SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(it.createdAt))
        } ?: "")
        ChatOriginType.CONCEPT -> "FROM CONCEPT" + (origin.label?.let { " · $it" } ?: "")
        ChatOriginType.CHAT_MESSAGE -> "FROM CHAT" + (origin.label?.let { " · $it" } ?: "")
    }
    return (if (isPractice) "PRACTICE · " else "") + from.uppercase()
}

/**
 * Full-width tag under the chat header showing where the chat came from; tap to preview the source.
 * Practice chats show this session's score on the right.
 */
@Composable
fun ChatOriginTag(
    origin: ChatOrigin,
    isPractice: Boolean,
    insight: StoredInsight?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    verdicts: List<PracticeVerdict> = emptyList()
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current
    val interaction = remember { MutableInteractionSource() }
    val score = if (isPractice) practiceScore(verdicts) else null
    PixelBox(
        modifier = modifier
            .fillMaxWidth()
            .pixelRounded8Clickable(interactionSource = interaction, onClick = onClick),
        fillColor = colors.tertiarySurface,
        borderColor = colors.textXLow.copy(alpha = 0.35f),
        cornerStyle = PixelCornerStyle.Rounded8
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconText(
                iconAsset = if (isPractice) "target.svg" else "corner-up-left.svg",
                text = originTagText(origin, isPractice, insight),
                style = pixelStyle(8, colors.textLow),
                fontRes = com.wizaird.app.R.font.vt323,
                iconSize = 14.dp,
                tint = colors.textLow,
                modifier = Modifier.weight(1f)
            )
            if (score != null) {
                IconText(
                    iconAsset = "chart.svg",
                    text = "$score%",
                    style = pixelStyle(8, colors.textLow),
                    fontRes = com.wizaird.app.R.font.vt323,
                    iconSize = 14.dp,
                    tint = colors.textLow,
                    contentDescription = "Session score"
                )
            }
        }
    }
}

/**
 * Preview of a chat's source. An insight that still exists opens the normal insight preview;
 * a concept or chat reply shows the copy saved with the chat, plus a button to open the original.
 */
@Composable
fun ChatOriginPreview(
    origin: ChatOrigin,
    insight: StoredInsight?,
    onDismiss: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenConcept: (String) -> Unit,
    onInsightChatClick: (String) -> Unit,
    onNewConceptClick: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalWizairdColors.current

    if (origin.type == ChatOriginType.INSIGHT && insight != null) {
        val linkedConcepts by remember(insight.id) {
            conceptsFlow(context, insight.projectId).map { all ->
                all.filter { c -> c.sources.any { it.insightId == insight.id } }
            }
        }.collectAsState(initial = emptyList())
        InsightPreviewOverlay(
            insight = insight,
            onDismiss = onDismiss,
            onChatClick = { onInsightChatClick(insight.id) },
            onNewConceptClick = onNewConceptClick,
            concepts = linkedConcepts,
            onConceptClick = onOpenConcept
        )
        return
    }

    // Does the original still exist? (button only then)
    val chatExists by remember(origin.chatId) {
        origin.chatId?.let { id -> chatFlow(context, id).map { it != null } } ?: kotlinx.coroutines.flow.flowOf(false)
    }.collectAsState(initial = false)
    val conceptExists by remember(origin.conceptId) {
        origin.conceptId?.let { id -> conceptFlow(context, id).map { it != null } } ?: kotlinx.coroutines.flow.flowOf(false)
    }.collectAsState(initial = false)

    val title = when (origin.type) {
        ChatOriginType.INSIGHT -> "INSIGHT"
        ChatOriginType.CONCEPT -> "CONCEPT"
        ChatOriginType.CHAT_MESSAGE -> "CHAT REPLY"
    }
    val maxBodyHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp

    PixelBottomSheet(onDismiss = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(title, style = pixelStyle(10, colors.textXLow), modifier = Modifier.offset(y = (-2).dp))
                origin.label?.let {
                    Text(
                        it,
                        style = minecraftStyle(14, colors.textHigh),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            when {
                origin.type == ChatOriginType.CHAT_MESSAGE && chatExists && origin.chatId != null ->
                    PixelButtonSmall(label = "OPEN CHAT", onClick = { onOpenChat(origin.chatId) }, cutColor = colors.background)
                origin.type == ChatOriginType.CONCEPT && conceptExists && origin.conceptId != null ->
                    PixelButtonSmall(label = "OPEN CONCEPT", onClick = { onOpenConcept(origin.conceptId) }, cutColor = colors.background)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxBodyHeight)
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp)
        ) {
            MarkdownText(
                markdown = origin.text?.ifBlank { null } ?: "The original is no longer available.",
                style = minecraftStyle(14, colors.textHigh).copy(lineHeight = (14 * 1.6f).sp)
            )
        }
    }
}

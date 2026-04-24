package com.wizaird.app.ui

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.request.ImageRequest
import com.wizaird.app.data.AiSettings
import com.wizaird.app.data.askAi
import com.wizaird.app.data.settingsFlow
import com.wizaird.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onSettingsClick: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by settingsFlow(context).collectAsState(initial = AiSettings())
    val colors = LocalWizairdColors.current

    var bubbleText by remember { mutableStateOf("Artificial Intelligence experience, or AI UX, is the practice of designing interactions between humans and intelligent systems in ways that feel natural, trustworthy, and useful.\n\nUnlike traditional software, AI systems are probabilistic — they don't always produce the same output for the same input. This introduces a new design challenge: how do you build trust with a system that is inherently unpredictable? The answer lies in transparency. Users need to understand what the AI can and cannot do, when it is confident versus uncertain, and how to correct it when it goes wrong.\n\nGood AI experience design starts with setting the right expectations. Onboarding flows should communicate the AI's capabilities honestly, without overpromising. In-product cues — like confidence indicators, source citations, or simple disclaimers — help users calibrate their trust appropriately.\n\nFeedback loops are equally important. When a user can rate a response, flag an error, or regenerate an answer, they feel in control. This sense of agency is critical: AI should feel like a powerful tool the user wields, not an opaque oracle they must blindly trust.\n\nLatency is another unique challenge. AI responses often take longer than traditional software actions. Thoughtful loading states — like streaming text, animated indicators, or progress cues — transform waiting from frustration into anticipation.\n\nFinally, the best AI experiences are deeply contextual. They remember who the user is, adapt to their preferences over time, and surface the right information at the right moment. The goal is not to replace human judgment, but to augment it — making people feel smarter, faster, and more capable than they would be alone.") }
    var isLoading by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }

    // Bob animation — matches HTML: sin(t/3)*2 at 120ms tick, ±2px float
    val bobAnim = rememberInfiniteTransition(label = "bob")
    val bobY by bobAnim.animateFloat(
        initialValue = 0f, targetValue = -2f,
        animationSpec = infiniteRepeatable(
            animation = tween(360, easing = LinearEasing), // 3 ticks × 120ms = 360ms half-period
            repeatMode = RepeatMode.Reverse
        ), label = "bobY"
    )

    fun askQuestion(prompt: String) {
        scope.launch {
            isLoading = true
            bubbleText = ""
            try {
                val result = askAi(settings, prompt.ifBlank { "Tell me one surprising bite-sized fact." })
                // type-out effect
                isLoading = false
                for (i in result.indices) {
                    bubbleText = result.substring(0, i + 1)
                    delay(18)
                }
            } catch (e: Exception) {
                isLoading = false
                bubbleText = "My scroll is torn! Check settings and try again."
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            AppHeader(onSettingsClick = onSettingsClick)
            StatStrip()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // Bubble fills all remaining space
                ChatBubble(
                    text = bubbleText,
                    loading = isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Wizard
                WizardCharacter(
                    bobOffsetY = bobY,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                PixelInputBar(
                    value = inputText,
                    onValueChange = { inputText = it },
                    onSubmit = {
                        val q = inputText.trim()
                        if (q.isNotEmpty()) {
                            inputText = ""
                            askQuestion(q)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp).navigationBarsPadding())
            }
        }
    }
}

// ── Status bar ───────────────────────────────────────────────────
@Composable
fun PixelStatusBar() {
    val colors = LocalWizairdColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(colors.background)
            .drawBehind { drawPixelBorder(bottom = true, color = colors.border) }
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("9:30", style = pixelStyle(8, colors.ink))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // signal bars
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                listOf(4.dp, 6.dp, 8.dp, 10.dp).forEach { h ->
                    Box(modifier = Modifier.width(2.dp).height(h).background(colors.ink))
                }
            }
            // battery
            Box(
                modifier = Modifier
                    .width(16.dp).height(8.dp)
                    .border(2.dp, colors.border)
                    .padding(1.dp)
            ) {
                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(0.7f).background(colors.forest))
            }
        }
    }
}

// ── App header ───────────────────────────────────────────────────
@Composable
fun AppHeader(onSettingsClick: () -> Unit) {
    val colors = LocalWizairdColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .drawBehind { drawPixelBorder(bottom = true, color = colors.border) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // W badge
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(colors.coral)
                    .drawBehind { drawPixelBorder(color = colors.border) },
                contentAlignment = Alignment.Center
            ) {
                Text("W", style = pixelStyle(12, Color.White))
            }
            Column {
                Text("WIZAIRD", style = pixelStyle(13, colors.ink))
                Text("LV.3 APPRENTICE", style = pixelStyle(6, colors.inkSoft))
            }
        }
        // Gear icon button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clickable { onSettingsClick() },
            contentAlignment = Alignment.Center
        ) {
            Text("⚙", style = pixelStyle(20, colors.ink))
        }
    }
}

// ── HP/XP stat strip ─────────────────────────────────────────────
@Composable
fun StatStrip() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StatBar(label = "HP", value = 8, max = 10, color = Coral)
        StatBar(label = "XP", value = 6, max = 10, color = Gold)
    }
}

@Composable
fun StatBar(label: String, value: Int, max: Int, color: Color) {
    val colors = LocalWizairdColors.current
    val filled = (value.toFloat() / max * 10).toInt()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = pixelStyle(7, colors.ink), modifier = Modifier.width(22.dp))
        Row(
            modifier = Modifier.background(colors.ink).padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            repeat(10) { i ->
                Box(modifier = Modifier.size(8.dp).background(if (i < filled) color else colors.backgroundDark))
            }
        }
        Text("$value/$max", style = pixelStyle(6, colors.inkSoft))
    }
}

// ── Chat bubble ──────────────────────────────────────────────────
@Composable
fun ChatBubble(text: String, loading: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalWizairdColors.current
    var dotCount by remember { mutableIntStateOf(1) }
    LaunchedEffect(loading) {
        if (loading) {
            while (true) {
                delay(400)
                dotCount = (dotCount % 3) + 1
            }
        }
    }

    PixelBox(
        modifier = modifier,
        fillColor = colors.bubble
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = if (loading) "thinking${".".repeat(dotCount)}" else text,
                style = pixelStyle(12, colors.ink),
                modifier = Modifier.verticalScroll(rememberScrollState()),
                overflow = TextOverflow.Clip
            )
        }
    }
}

// ── Wizard character (GIF via Coil) ──────────────────────────────
@Composable
fun WizardCharacter(bobOffsetY: Float, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(GifDecoder.Factory()) }
            .build()
    }
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data("file:///android_asset/wizard-cropped.gif")
            .build(),
        imageLoader = imageLoader,
        contentDescription = "Wizard",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .height(120.dp)
            .wrapContentWidth()
            .offset(y = bobOffsetY.dp)
    )
}

// ── Pixel text input ─────────────────────────────────────────────
@Composable
fun PixelInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWizairdColors.current
    PixelBox(
        modifier = modifier.heightIn(min = 52.dp, max = 200.dp),
        fillColor = colors.bubble
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = pixelStyle(12, colors.ink),
                cursorBrush = SolidColor(colors.ink),
                singleLine = false,
                maxLines = 10,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text("ASK THE WIZARD...", style = pixelStyle(12, colors.inkSoft))
                        }
                        inner()
                    }
                }
            )
            // Send button — pixel-rounded, floating with padding, arrow icon
            val bubbleColor = colors.bubble
            val inkColor = colors.ink
            Box(
                modifier = Modifier
                    .padding(end = 6.dp, bottom = 6.dp)
                    .size(32.dp)
                    .drawBehind {
                        val p = 3.dp.toPx()  // one pixel block
                        val w = size.width
                        val h = size.height
                        // Fill with Coral
                        drawRect(Coral)
                        // Cut corners — 3-step staircase, cut with bubble color
                        val cut = bubbleColor
                        // Top-left
                        drawRect(cut, Offset(0f, 0f), Size(p * 3, p))
                        drawRect(cut, Offset(0f, p), Size(p * 2, p))
                        drawRect(cut, Offset(0f, p * 2), Size(p, p))
                        // Top-right
                        drawRect(cut, Offset(w - p * 3, 0f), Size(p * 3, p))
                        drawRect(cut, Offset(w - p * 2, p), Size(p * 2, p))
                        drawRect(cut, Offset(w - p, p * 2), Size(p, p))
                        // Bottom-left
                        drawRect(cut, Offset(0f, h - p), Size(p * 3, p))
                        drawRect(cut, Offset(0f, h - p * 2), Size(p * 2, p))
                        drawRect(cut, Offset(0f, h - p * 3), Size(p, p))
                        // Bottom-right
                        drawRect(cut, Offset(w - p * 3, h - p), Size(p * 3, p))
                        drawRect(cut, Offset(w - p * 2, h - p * 2), Size(p * 2, p))
                        drawRect(cut, Offset(w - p, h - p * 3), Size(p, p))
                        // Arrow like -> : shaft + V-shaped head, pixel squares, dark color
                        val arrowColor = inkColor
                        val cx = w / 2f + p - 1.dp.toPx()
                        val cy = h / 2f
                        // Shaft — horizontal line left of center (4 blocks now)
                        drawRect(arrowColor, Offset(cx - p * 3, cy - p / 2f), Size(p * 4, p))
                        // Arrowhead > : top-right diagonal
                        drawRect(arrowColor, Offset(cx - p, cy - p * 2), Size(p, p))
                        drawRect(arrowColor, Offset(cx, cy - p), Size(p, p))
                        drawRect(arrowColor, Offset(cx + p, cy - p / 2f), Size(p, p))
                        // Arrowhead > : bottom-right diagonal
                        drawRect(arrowColor, Offset(cx - p, cy + p), Size(p, p))
                        drawRect(arrowColor, Offset(cx, cy), Size(p, p))
                        drawRect(arrowColor, Offset(cx + p, cy - p / 2f), Size(p, p))
                    }
                    .clickable { onSubmit() }
            )
        }
    }
}

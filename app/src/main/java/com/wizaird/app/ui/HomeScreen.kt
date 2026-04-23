package com.wizaird.app.ui

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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

    var bubbleText by remember { mutableStateOf("Hail, apprentice! Ask anything — I hoard strange facts.") }
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

    // Auto-refresh every 20s
    LaunchedEffect(Unit) {
        askQuestion("")
        while (true) {
            delay(20_000)
            askQuestion("")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
    ) {
        // Sparkles
        Sparkle(modifier = Modifier.offset(18.dp, 80.dp))
        Sparkle(modifier = Modifier.align(Alignment.TopEnd).offset((-24).dp, 120.dp))
        Sparkle(modifier = Modifier.align(Alignment.BottomStart).offset(30.dp, (-200).dp))
        Sparkle(modifier = Modifier.align(Alignment.BottomEnd).offset((-32).dp, (-240).dp))

        Column(modifier = Modifier.fillMaxSize()) {
            PixelStatusBar()
            AppHeader(onSettingsClick = onSettingsClick)
            StatStrip()
            Spacer(modifier = Modifier.weight(1f))
        }

        // Main stage: bubble + wizard + input pinned to bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 30.dp)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            ChatBubble(text = bubbleText, loading = isLoading)

            Spacer(modifier = Modifier.height(4.dp))

            // Wizard sitting on top of input
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

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PixelLabel("↵ SEND")
                PixelLabel("AUTO-QUEST ON")
            }
        }

        PixelNavBar(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

// ── Status bar ───────────────────────────────────────────────────
@Composable
fun PixelStatusBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(Paper)
            .drawBehind { drawPixelBorder(bottom = true) }
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("9:30", style = pixelStyle(8))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // signal bars
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                listOf(4.dp, 6.dp, 8.dp, 10.dp).forEach { h ->
                    Box(modifier = Modifier.width(2.dp).height(h).background(Ink))
                }
            }
            // battery
            Box(
                modifier = Modifier
                    .width(16.dp).height(8.dp)
                    .border(2.dp, Ink)
                    .padding(1.dp)
            ) {
                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(0.7f).background(Forest))
            }
        }
    }
}

// ── App header ───────────────────────────────────────────────────
@Composable
fun AppHeader(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Paper)
            .drawBehind { drawPixelBorder(bottom = true) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // W badge
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Coral)
                    .drawBehind { drawPixelBorder() },
                contentAlignment = Alignment.Center
            ) {
                Text("W", style = pixelStyle(12, Color.White))
            }
            Column {
                Text("WIZAIRD", style = pixelStyle(13))
                Text("LV.3 APPRENTICE", style = pixelStyle(6, InkSoft))
            }
        }
        // Gear icon button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clickable { onSettingsClick() },
            contentAlignment = Alignment.Center
        ) {
            Text("⚙", style = pixelStyle(20, Ink))
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
    val filled = (value.toFloat() / max * 10).toInt()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = pixelStyle(7), modifier = Modifier.width(22.dp))
        Row(
            modifier = Modifier.background(Ink).padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            repeat(10) { i ->
                Box(modifier = Modifier.size(8.dp).background(if (i < filled) color else Color(0x332A1F14)))
            }
        }
        Text("$value/$max", style = pixelStyle(6, InkSoft))
    }
}

// ── Chat bubble ──────────────────────────────────────────────────
@Composable
fun ChatBubble(text: String, loading: Boolean) {
    var dotCount by remember { mutableIntStateOf(1) }
    LaunchedEffect(loading) {
        if (loading) {
            while (true) {
                delay(400)
                dotCount = (dotCount % 3) + 1
            }
        }
    }

    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .drawBehind { drawPixelBorder() }
            .background(Bubble)
            .padding(14.dp)
    ) {
        Text(
            text = if (loading) "thinking${".".repeat(dotCount)}" else text,
            style = pixelStyle(16, Ink),
            minLines = 2,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis
        )
        // Pixel tail pointing down (left side)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 20.dp, y = 9.dp)
                .size(9.dp, 9.dp)
                .background(Ink)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 23.dp, y = 12.dp)
                .size(6.dp, 6.dp)
                .background(Ink)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 26.dp, y = 15.dp)
                .size(3.dp, 3.dp)
                .background(Ink)
        )
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
            .height(200.dp)
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
    Row(
        modifier = modifier
            .height(52.dp)
            .drawBehind { drawPixelBorder() }
            .background(Bubble),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = pixelStyle(16, Ink),
            cursorBrush = SolidColor(Ink),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text("ASK THE WIZARD...", style = pixelStyle(16, InkSoft))
                }
                inner()
            }
        )
        Box(
            modifier = Modifier
                .width(56.dp)
                .fillMaxHeight()
                .background(Coral)
                .drawBehind { drawPixelBorder(left = true) }
                .clickable { onSubmit() },
            contentAlignment = Alignment.Center
        ) {
            Text("SEND", style = pixelStyle(11, Color.White))
        }
    }
}

// ── Nav bar ──────────────────────────────────────────────────────
@Composable
fun PixelNavBar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp)
            .background(Paper)
            .drawBehind { drawPixelBorder(top = true) },
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.width(90.dp).height(4.dp).background(Ink))
    }
}

// ── Sparkle decoration ───────────────────────────────────────────
@Composable
fun Sparkle(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(9.dp)) {
        Box(modifier = Modifier.align(Alignment.Center).width(3.dp).fillMaxHeight().background(Gold))
        Box(modifier = Modifier.align(Alignment.Center).fillMaxWidth().height(3.dp).background(Gold))
    }
}

@Composable
fun PixelLabel(text: String) {
    Text(text, style = pixelStyle(6, InkSoft))
}

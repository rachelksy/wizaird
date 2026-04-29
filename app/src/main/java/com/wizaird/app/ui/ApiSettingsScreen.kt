package com.wizaird.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.wizaird.app.R
import com.wizaird.app.data.AiSettings
import com.wizaird.app.data.loadModels
import com.wizaird.app.data.saveSettings
import com.wizaird.app.data.settingsFlow
import com.wizaird.app.data.testApiConnection
import com.wizaird.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ApiSettingsScreen(
    onBack: () -> Unit,
    initialDarkMode: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by settingsFlow(context).collectAsState(initial = AiSettings(darkMode = initialDarkMode))

    var provider    by remember(saved) { mutableStateOf(saved.provider) }
    var apiKey      by remember(saved) { mutableStateOf(saved.apiKey) }
    var model       by remember(saved) { mutableStateOf("") } // Always start empty - user must select
    var baseUrl     by remember(saved) { mutableStateOf(saved.baseUrl) }
    var temperature by remember(saved) { mutableStateOf(saved.temperature) }

    var showProviderDropdown by remember { mutableStateOf(false) }
    var showModelDropdown by remember { mutableStateOf(false) }
    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingModels by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    
    // Reset model and available models when provider changes
    LaunchedEffect(provider) {
        model = ""
        availableModels = emptyList()
    }

    val colors = LocalWizairdColors.current
    val providers = listOf(
        "Claude" to "claude",
        "OpenAI" to "openai",
        "Gemini" to "gemini",
        "Custom" to "custom"
    )

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
                            borderColor = Color.Transparent,
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
                    "API SETTINGS",
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
                            saveSettings(
                                context,
                                saved.copy(
                                    provider = provider,
                                    apiKey = apiKey,
                                    model = model,
                                    baseUrl = baseUrl,
                                    temperature = temperature
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
                // Provider dropdown
                SettingsField(label = "AI PROVIDER") {
                    PixelDropdown(
                        selectedLabel = providers.find { it.second == provider }?.first ?: "OpenAI",
                        expanded = showProviderDropdown,
                        onExpandedChange = { showProviderDropdown = it },
                        onDismiss = { showProviderDropdown = false },
                        placeholder = ""
                    ) {
                        providers.forEach { (label, value) ->
                            PixelDropdownItem(
                                label = label,
                                selected = provider == value,
                                onClick = {
                                    provider = value
                                    showProviderDropdown = false
                                }
                            )
                        }
                    }
                }

                // Show Custom API fields only when Custom is selected
                if (provider == "custom") {
                    SettingsField(label = "BASE URL") {
                        PixelTextInput(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            placeholder = "https://api.example.com",
                            cornerStyle = PixelCornerStyle.Rounded8,
                            textStyle = minecraftStyle(16, colors.secondaryIcon)
                        )
                    }

                    SettingsField(label = "API KEY") {
                        PixelTextInput(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            placeholder = "sk-...",
                            isPassword = true,
                            cornerStyle = PixelCornerStyle.Rounded8,
                            textStyle = minecraftStyle(16, colors.secondaryIcon)
                        )
                    }

                    SettingsField(label = "API MODEL") {
                        PixelDropdown(
                            selectedLabel = model,
                            expanded = showModelDropdown,
                            onExpandedChange = { expanded ->
                                showModelDropdown = expanded
                                if (expanded && availableModels.isEmpty() && !isLoadingModels) {
                                    // Load models when dropdown opens
                                    if (baseUrl.isNotBlank() && apiKey.isNotBlank()) {
                                        isLoadingModels = true
                                        scope.launch {
                                            try {
                                                loadModels(baseUrl, apiKey).fold(
                                                    onSuccess = { models ->
                                                        availableModels = models
                                                        isLoadingModels = false
                                                    },
                                                    onFailure = { error ->
                                                        println("Failed to load models: ${error.message}")
                                                        availableModels = emptyList()
                                                        isLoadingModels = false
                                                    }
                                                )
                                            } catch (e: Exception) {
                                                println("Exception loading models: ${e.message}")
                                                availableModels = emptyList()
                                                isLoadingModels = false
                                            }
                                        }
                                    } else {
                                        println("Cannot load models - baseUrl or apiKey is blank")
                                    }
                                }
                            },
                            onDismiss = { showModelDropdown = false },
                            placeholder = "Tap to load models"
                        ) {
                            if (isLoadingModels) {
                                PixelDropdownItem(
                                    label = "Loading...",
                                    selected = false,
                                    onClick = {}
                                )
                            } else if (availableModels.isEmpty()) {
                                PixelDropdownItem(
                                    label = "No models available",
                                    selected = false,
                                    onClick = {}
                                )
                            } else {
                                availableModels.forEach { modelName ->
                                    PixelDropdownItem(
                                        label = modelName,
                                        selected = model == modelName,
                                        onClick = {
                                            model = modelName
                                            showModelDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Standard API Key and Model fields for non-custom providers
                    SettingsField(label = "API KEY") {
                        PixelTextInput(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            placeholder = "sk-...",
                            isPassword = true,
                            cornerStyle = PixelCornerStyle.Rounded8
                        )
                    }

                    SettingsField(label = "MODEL") {
                        PixelDropdown(
                            selectedLabel = model,
                            expanded = showModelDropdown,
                            onExpandedChange = { expanded ->
                                showModelDropdown = expanded
                                if (expanded && availableModels.isEmpty() && !isLoadingModels) {
                                    // Load models when dropdown opens for standard providers
                                    if (apiKey.isNotBlank()) {
                                        isLoadingModels = true
                                        scope.launch {
                                            try {
                                                val modelsUrl = when (provider) {
                                                    "openai" -> "https://api.openai.com/v1/models"
                                                    "claude" -> "" // Anthropic doesn't have a models endpoint
                                                    "gemini" -> "" // Gemini doesn't have a models endpoint
                                                    else -> ""
                                                }
                                                
                                                println("Loading models for provider: $provider, url: $modelsUrl")
                                                
                                                if (modelsUrl.isNotBlank()) {
                                                    loadModels(modelsUrl, apiKey).fold(
                                                        onSuccess = { models ->
                                                            println("Loaded ${models.size} models")
                                                            availableModels = models
                                                            isLoadingModels = false
                                                        },
                                                        onFailure = { error ->
                                                            println("Failed to load models: ${error.message}")
                                                            // Fallback to default models if API call fails
                                                            availableModels = when (provider) {
                                                                "openai" -> listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo")
                                                                "claude" -> listOf("claude-opus-4-20250514", "claude-sonnet-4-20250514", "claude-haiku-4-5")
                                                                "gemini" -> listOf("gemini-2.0-flash-exp", "gemini-1.5-pro", "gemini-1.5-flash")
                                                                else -> emptyList()
                                                            }
                                                            isLoadingModels = false
                                                        }
                                                    )
                                                } else {
                                                    // For providers without models endpoint, show default models
                                                    println("Using default models for $provider")
                                                    availableModels = when (provider) {
                                                        "claude" -> listOf("claude-opus-4-20250514", "claude-sonnet-4-20250514", "claude-haiku-4-5")
                                                        "gemini" -> listOf("gemini-2.0-flash-exp", "gemini-1.5-pro", "gemini-1.5-flash")
                                                        else -> emptyList()
                                                    }
                                                    isLoadingModels = false
                                                }
                                            } catch (e: Exception) {
                                                println("Exception loading models: ${e.message}")
                                                availableModels = emptyList()
                                                isLoadingModels = false
                                            }
                                        }
                                    } else {
                                        // Show default models if no API key
                                        println("No API key, showing default models")
                                        availableModels = when (provider) {
                                            "openai" -> listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo")
                                            "claude" -> listOf("claude-opus-4-20250514", "claude-sonnet-4-20250514", "claude-haiku-4-5")
                                            "gemini" -> listOf("gemini-2.0-flash-exp", "gemini-1.5-pro", "gemini-1.5-flash")
                                            else -> emptyList()
                                        }
                                    }
                                }
                            },
                            onDismiss = { showModelDropdown = false },
                            placeholder = "Tap to load models"
                        ) {
                            if (isLoadingModels) {
                                PixelDropdownItem(
                                    label = "Loading...",
                                    selected = false,
                                    onClick = {}
                                )
                            } else if (availableModels.isEmpty()) {
                                PixelDropdownItem(
                                    label = "No models available",
                                    selected = false,
                                    onClick = {}
                                )
                            } else {
                                availableModels.forEach { modelName ->
                                    PixelDropdownItem(
                                        label = modelName,
                                        selected = model == modelName,
                                        onClick = {
                                            model = modelName
                                            showModelDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Test API Connection button
                Spacer(modifier = Modifier.height(8.dp))
                
                PixelButtonLarge(
                    label = if (isTesting) "TESTING..." else "TEST API CONNECTION",
                    primary = false,
                    onClick = {
                        if (!isTesting) {
                            isTesting = true
                            testResult = null
                            scope.launch {
                                val settings = AiSettings(
                                    provider = provider,
                                    apiKey = apiKey,
                                    model = model.ifBlank {
                                        when (provider) {
                                            "claude" -> "claude-haiku-4-5"
                                            "gemini" -> "gemini-1.5-flash"
                                            else -> "gpt-4o-mini"
                                        }
                                    },
                                    baseUrl = baseUrl,
                                    temperature = temperature
                                )
                                testApiConnection(settings).fold(
                                    onSuccess = { testResult = "✓ $it" },
                                    onFailure = { testResult = "✗ ${it.message}" }
                                )
                                isTesting = false
                            }
                        }
                    }
                )

                // Test result message
                testResult?.let { result ->
                    Text(
                        text = result,
                        style = minecraftStyle(
                            10,
                            if (result.startsWith("✓")) colors.textHigh else colors.warn
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-2).dp)
                    )
                }
            }
        }
    }
}

// Dropdown component with chevron icon - appears as popover below the field
@Composable
fun PixelDropdown(
    selectedLabel: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    placeholder: String = "",
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalWizairdColors.current
    val interaction = remember { MutableInteractionSource() }
    var fieldWidth by remember { mutableStateOf(0) }
    var fieldHeight by remember { mutableStateOf(0) }
    
    // Show placeholder if selectedLabel is empty, otherwise show selectedLabel
    val displayText = if (selectedLabel.isBlank()) placeholder else selectedLabel
    val textColor = if (selectedLabel.isBlank()) colors.secondaryIconSoft else colors.secondaryIcon

    Box {
        PixelBox(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    fieldWidth = coordinates.size.width
                    fieldHeight = coordinates.size.height
                }
                .pixelRounded8Clickable(interactionSource = interaction) {
                    onExpandedChange(!expanded)
                },
            fillColor = colors.secondarySurface,
            cornerStyle = PixelCornerStyle.Rounded8
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayText,
                    style = pixelStyle(12, textColor),
                    modifier = Modifier.offset(y = (-2).dp)
                )
                // Chevron icon from drawable
                Image(
                    painter = painterResource(id = R.drawable.ic_chevron_down),
                    contentDescription = "Dropdown",
                    colorFilter = ColorFilter.tint(colors.secondaryIcon),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Popover menu - appears directly below the field
        if (expanded) {
            val density = LocalDensity.current
            val offsetY = with(density) { fieldHeight + 4.dp.roundToPx() } // Field height + 4dp gap
            
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(x = 0, y = offsetY),
                onDismissRequest = onDismiss,
                properties = PopupProperties(focusable = false)
            ) {
                Box(
                    modifier = Modifier
                        .width(with(density) { fieldWidth.toDp() })
                        .heightIn(max = 300.dp) // Max height with scrolling
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
                            val fill = colors.secondarySurface
                            val cut = Color.Transparent

                            // Fill entire box
                            drawRect(fill)

                            // Cut corners with BlendMode.Clear
                            // Top-left
                            drawRect(cut, androidx.compose.ui.geometry.Offset(0f, p*0), androidx.compose.ui.geometry.Size(p*5, p), blendMode = BlendMode.Clear)
                            drawRect(cut, androidx.compose.ui.geometry.Offset(0f, p*1), androidx.compose.ui.geometry.Size(p*3, p), blendMode = BlendMode.Clear)
                            drawRect(cut, androidx.compose.ui.geometry.Offset(0f, p*2), androidx.compose.ui.geometry.Size(p*2, p), blendMode = BlendMode.Clear)
                            drawRect(cut, androidx.compose.ui.geometry.Offset(0f, p*3), androidx.compose.ui.geometry.Size(p*1, p), blendMode = BlendMode.Clear)
                            drawRect(cut, androidx.compose.ui.geometry.Offset(0f, p*4), androidx.compose.ui.geometry.Size(p*1, p), blendMode = BlendMode.Clear)
                            // Top-right
                            drawRect(cut, androidx.compose.ui.geometry.Offset(w-p*5, p*0), androidx.compose.ui.geometry.Size(p*5, p), blendMode = BlendMode.Clear)
                            drawRect(cut, androidx.compose.ui.geometry.Offset(w-p*3, p*1), androidx.compose.ui.geometry.Size(p*3, p), blendMode = BlendMode.Clear)
                            drawRect(cut, androidx.compose.ui.geometry.Offset(w-p*2, p*2), androidx.compose.ui.geometry.Size(p*2, p), blendMode = BlendMode.Clear)
                            drawRect(cut, androidx.compose.ui.geometry.Offset(w-p*1, p*3), androidx.compose.ui.geometry.Size(p*1, p), blendMode = BlendMode.Clear)
                            drawRect(cut, androidx.compose.ui.geometry.Offset(w-p*1, p*4), androidx.compose.ui.geometry.Size(p*1, p), blendMode = BlendMode.Clear)
                            // Bottom-left
                            drawRect(cut, androidx.compose.ui.geometry.Offset(0f, h-p*1), androidx.compose.ui.geometry.Size(p*5, p), blendMode = BlendMode.Clear)
                            drawRect(cut, androidx.compose.ui.geometry.Offset(0f, h-p*2), androidx.compose.ui.geometry.Size(p*3, p), blendMode = BlendMode.Clear)
                            drawRect(cut, androidx.compose.ui.geometry.Offset(0f, h-p*3), androidx.compose.ui.geometry.Size(p*2, p), blendMode = BlendMode.Clear)
                            drawRect(cut, androidx.compose.ui.geometry.Offset(0f, h-p*4), androidx.compose.ui.geometry.Size(p*1, p), blendMode = BlendMode.Clear)
                            drawRect(cut, androidx.compose.ui.geometry.Offset(0f, h-p*5), androidx.compose.ui.geometry.Size(p*1, p), blendMode = BlendMode.Clear)
                            // Bottom-right
                            drawRect(cut, androidx.compose.ui.geometry.Offset(w-p*5, h-p*1), androidx.compose.ui.geometry.Size(p*5, p), blendMode = BlendMode.Clear)
                            drawRect(cut, androidx.compose.ui.geometry.Offset(w-p*3, h-p*2), androidx.compose.ui.geometry.Size(p*3, p), blendMode = BlendMode.Clear)
                            drawRect(cut, androidx.compose.ui.geometry.Offset(w-p*2, h-p*3), androidx.compose.ui.geometry.Size(p*2, p), blendMode = BlendMode.Clear)
                            drawRect(cut, androidx.compose.ui.geometry.Offset(w-p*1, h-p*4), androidx.compose.ui.geometry.Size(p*1, p), blendMode = BlendMode.Clear)
                            drawRect(cut, androidx.compose.ui.geometry.Offset(w-p*1, h-p*5), androidx.compose.ui.geometry.Size(p*1, p), blendMode = BlendMode.Clear)
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp)
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
fun PixelDropdownItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalWizairdColors.current
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Coral.copy(alpha = 0.2f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = pixelStyle(12, if (selected) Coral else colors.secondaryIcon),
            modifier = Modifier.offset(y = (-2).dp)
        )
    }
}

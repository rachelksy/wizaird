package com.wizaird.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

val Context.dataStore by preferencesDataStore(name = "settings")

data class AiSettings(
    val provider: String = "openai",
    val apiKey: String = "",
    val model: String = "",
    val baseUrl: String = "",
    val temperature: Float = 0.7f,
    val darkMode: Boolean = false
)

private val KEY_SETTINGS = stringPreferencesKey("ai_settings")
private val gson = Gson()

fun settingsFlow(context: Context): Flow<AiSettings> =
    context.dataStore.data.map { prefs ->
        prefs[KEY_SETTINGS]?.let { gson.fromJson(it, AiSettings::class.java) } ?: AiSettings()
    }

suspend fun saveSettings(context: Context, settings: AiSettings) {
    context.dataStore.edit { prefs ->
        prefs[KEY_SETTINGS] = gson.toJson(settings)
    }
}

// ── AI call ──────────────────────────────────────────────────────
private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

suspend fun askAi(settings: AiSettings, systemPrompt: String, userPrompt: String): String {
    return when (settings.provider) {
        "openai"  -> callOpenAi(settings, systemPrompt, userPrompt)
        "gemini"  -> callGemini(settings, systemPrompt, userPrompt)
        "claude"  -> callClaude(settings, systemPrompt, userPrompt)
        "custom"  -> {
            // For custom provider, detect which format to use based on model name or base URL
            when {
                settings.model.contains("gemini", ignoreCase = true) -> {
                    println("Custom provider detected Gemini model, using Gemini API format")
                    callGemini(settings, systemPrompt, userPrompt)
                }
                settings.model.contains("claude", ignoreCase = true) -> {
                    println("Custom provider detected Claude model, using Claude API format")
                    callClaude(settings, systemPrompt, userPrompt)
                }
                else -> {
                    println("Custom provider using OpenAI-compatible format")
                    callOpenAi(settings, systemPrompt, userPrompt)
                }
            }
        }
        else      -> callOpenAi(settings, systemPrompt, userPrompt)
    }
}

// Legacy function for backward compatibility
suspend fun askAi(settings: AiSettings, userPrompt: String): String {
    val defaultPrompt = "You are a playful pixel-art wizard tutor inside an Android learning app called Wizaird. " +
            "Give ONE bite-sized fact or explanation. Max 160 characters. No emoji. No preamble. Plain text only."
    return askAi(settings, defaultPrompt, userPrompt)
}

// OpenAI-compatible (also works for custom endpoints)
private data class OaiMessage(@SerializedName("role") val role: String, @SerializedName("content") val content: String)
private data class OaiRequest(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<OaiMessage>,
    @SerializedName("temperature") val temperature: Float,
    @SerializedName("max_tokens") val maxTokens: Int = 120
)

fun callOpenAi(settings: AiSettings, systemPrompt: String, userPrompt: String): String {
    val url = if (settings.provider == "custom") {
        settings.baseUrl.ifBlank { "https://api.openai.com/v1/chat/completions" }
    } else {
        "https://api.openai.com/v1/chat/completions"
    }
    val model = settings.model.ifBlank { "gpt-4o-mini" }
    val body = gson.toJson(OaiRequest(
        model = model,
        messages = listOf(OaiMessage("system", systemPrompt), OaiMessage("user", userPrompt)),
        temperature = settings.temperature,
        maxTokens = 500
    ))
    val req = Request.Builder()
        .url(url)
        .addHeader("Authorization", "Bearer ${settings.apiKey}")
        .addHeader("Content-Type", "application/json")
        .post(body.toRequestBody("application/json".toMediaType()))
        .build()
    val resp = client.newCall(req).execute()
    val responseBody = resp.body?.string() ?: return "No response."
    
    println("OpenAI Response Code: ${resp.code}")
    println("OpenAI Response Body: $responseBody")
    
    // Check for HTTP errors
    if (!resp.isSuccessful) {
        return "API Error (${resp.code}): $responseBody"
    }
    
    // Try to parse JSON response
    try {
        val json = gson.fromJson(responseBody, Map::class.java)
        @Suppress("UNCHECKED_CAST")
        val choices = json["choices"] as? List<Map<String, Any>> ?: return "No response."
        val msg = choices[0]["message"] as? Map<String, Any> ?: return "No response."
        return (msg["content"] as? String)?.trim() ?: "No response."
    } catch (e: Exception) {
        println("Failed to parse OpenAI response: ${e.message}")
        return "Failed to parse API response: $responseBody"
    }
}

// Gemini
private fun callGemini(settings: AiSettings, systemPrompt: String, userPrompt: String): String {
    val model = settings.model.ifBlank { "gemini-1.5-flash" }
    
    // Use custom base URL if provider is custom, otherwise use Google's API
    val url = if (settings.provider == "custom" && settings.baseUrl.isNotBlank()) {
        // Custom endpoint - check if it already includes the path structure
        val baseUrl = settings.baseUrl.trimEnd('/')
        if (baseUrl.contains("/models/") || baseUrl.endsWith(":generateContent")) {
            // Base URL already has the full path, just append key
            "$baseUrl?key=${settings.apiKey}"
        } else {
            // Base URL is just the domain, add the full Gemini path
            "$baseUrl/v1beta/models/$model:generateContent?key=${settings.apiKey}"
        }
    } else {
        // Standard Gemini API
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${settings.apiKey}"
    }
    
    println("Gemini URL: $url")
    
    // Properly construct JSON using Gson to avoid escaping issues
    val combinedPrompt = "$systemPrompt\n\n$userPrompt"
    val requestBody = mapOf(
        "contents" to listOf(
            mapOf(
                "parts" to listOf(
                    mapOf("text" to combinedPrompt)
                )
            )
        )
    )
    val body = gson.toJson(requestBody)
    
    println("Gemini Request Body length: ${body.length} chars")
    
    val req = Request.Builder()
        .url(url)
        .addHeader("Content-Type", "application/json")
        .post(body.toRequestBody("application/json".toMediaType()))
        .build()
    val resp = client.newCall(req).execute()
    val responseBody = resp.body?.string() ?: return "No response."
    
    println("Gemini Response Code: ${resp.code}")
    println("Gemini Response Body: $responseBody")
    
    // Check for HTTP errors
    if (!resp.isSuccessful) {
        return "API Error (${resp.code}): $responseBody"
    }
    
    // Try to parse JSON response
    try {
        val json = gson.fromJson(responseBody, Map::class.java)
        @Suppress("UNCHECKED_CAST")
        val candidates = json["candidates"] as? List<Map<String, Any>> ?: return "No response."
        val content = candidates[0]["content"] as? Map<String, Any> ?: return "No response."
        val parts = content["parts"] as? List<Map<String, Any>> ?: return "No response."
        val text = (parts[0]["text"] as? String)?.trim() ?: "No response."
        
        println("Gemini extracted text length: ${text.length} chars")
        println("Gemini extracted text (first 200 chars): ${text.take(200)}")
        println("Gemini extracted text (last 200 chars): ${text.takeLast(200)}")
        
        return text
    } catch (e: Exception) {
        println("Failed to parse Gemini response: ${e.message}")
        return "Failed to parse API response: $responseBody"
    }
}

// Claude (Anthropic)
private fun callClaude(settings: AiSettings, systemPrompt: String, userPrompt: String): String {
    val model = settings.model.ifBlank { "claude-haiku-4-5" }
    val body = """{"model":"$model","max_tokens":500,"system":"$systemPrompt","messages":[{"role":"user","content":"$userPrompt"}]}"""
    val req = Request.Builder()
        .url("https://api.anthropic.com/v1/messages")
        .addHeader("x-api-key", settings.apiKey)
        .addHeader("anthropic-version", "2023-06-01")
        .addHeader("Content-Type", "application/json")
        .post(body.toRequestBody("application/json".toMediaType()))
        .build()
    val resp = client.newCall(req).execute()
    val responseBody = resp.body?.string() ?: return "No response."
    
    println("Claude Response Code: ${resp.code}")
    println("Claude Response Body: $responseBody")
    
    // Check for HTTP errors
    if (!resp.isSuccessful) {
        return "API Error (${resp.code}): $responseBody"
    }
    
    // Try to parse JSON response
    try {
        val json = gson.fromJson(responseBody, Map::class.java)
        @Suppress("UNCHECKED_CAST")
        val content = json["content"] as? List<Map<String, Any>> ?: return "No response."
        return (content[0]["text"] as? String)?.trim() ?: "No response."
    } catch (e: Exception) {
        println("Failed to parse Claude response: ${e.message}")
        return "Failed to parse API response: $responseBody"
    }
}

// ── Test API Connection ──────────────────────────────────────────
suspend fun testApiConnection(settings: AiSettings): Result<String> = withContext(Dispatchers.IO) {
    return@withContext try {
        println("Testing API connection for provider: ${settings.provider}")
        
        // For custom provider, just test the models endpoint
        if (settings.provider == "custom") {
            val modelsUrl = if (settings.baseUrl.endsWith("/chat/completions")) {
                settings.baseUrl.replace("/chat/completions", "/models")
            } else if (settings.baseUrl.endsWith("/")) {
                "${settings.baseUrl}models"
            } else {
                "${settings.baseUrl}/models"
            }
            
            val req = Request.Builder()
                .url(modelsUrl)
                .addHeader("Authorization", "Bearer ${settings.apiKey}")
                .get()
                .build()
            
            val resp = client.newCall(req).execute()
            return@withContext if (resp.isSuccessful) {
                Result.success("Connection successful!")
            } else {
                Result.failure(Exception("Connection failed: ${resp.code} ${resp.message}"))
            }
        }
        
        // For standard providers, test with a simple API call
        val testUrl = when (settings.provider) {
            "openai" -> "https://api.openai.com/v1/models"
            "claude" -> "https://api.anthropic.com/v1/messages"
            "gemini" -> "https://generativelanguage.googleapis.com/v1beta/models?key=${settings.apiKey}"
            else -> return@withContext Result.failure(Exception("Unknown provider"))
        }
        
        val reqBuilder = Request.Builder().url(testUrl)
        
        when (settings.provider) {
            "openai" -> reqBuilder.addHeader("Authorization", "Bearer ${settings.apiKey}")
            "claude" -> {
                reqBuilder.addHeader("x-api-key", settings.apiKey)
                reqBuilder.addHeader("anthropic-version", "2023-06-01")
            }
            // Gemini uses API key in URL
        }
        
        val resp = client.newCall(reqBuilder.get().build()).execute()
        
        println("Test response code: ${resp.code}")
        
        return@withContext if (resp.isSuccessful || resp.code == 400) {
            // 400 is ok for Claude because we're not sending a proper request body
            Result.success("Connection successful!")
        } else {
            Result.failure(Exception("Connection failed: ${resp.code} ${resp.message}"))
        }
    } catch (e: Exception) {
        println("Test connection failed: ${e.javaClass.simpleName} - ${e.message}")
        e.printStackTrace()
        Result.failure(Exception("Connection failed: ${e.message}"))
    }
}

// ── Load Models from API ─────────────────────────────────────────
suspend fun loadModels(baseUrl: String, apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
    return@withContext try {
        println("loadModels called with baseUrl: $baseUrl")
        
        val modelsUrl = if (baseUrl.endsWith("/chat/completions")) {
            baseUrl.replace("/chat/completions", "/models")
        } else if (baseUrl.endsWith("/")) {
            "${baseUrl}models"
        } else {
            "$baseUrl/models"
        }
        
        println("Fetching models from: $modelsUrl")
        
        val req = Request.Builder()
            .url(modelsUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()
        
        val resp = client.newCall(req).execute()
        println("Response code: ${resp.code}")
        
        if (!resp.isSuccessful) {
            val errorBody = resp.body?.string()
            println("Error response body: $errorBody")
            return@withContext Result.failure(Exception("Failed to load models: ${resp.code} ${resp.message}"))
        }
        
        val responseBody = resp.body?.string()
        println("Response body: $responseBody")
        
        val json = gson.fromJson(responseBody, Map::class.java)
        @Suppress("UNCHECKED_CAST")
        val data = json["data"] as? List<Map<String, Any>>
        
        if (data == null) {
            println("No 'data' field in response")
            return@withContext Result.failure(Exception("Invalid response format - no data field"))
        }
        
        val models = data.mapNotNull { it["id"] as? String }
        println("Extracted ${models.size} models: $models")
        
        if (models.isEmpty()) {
            return@withContext Result.failure(Exception("No models found in response"))
        }
        Result.success(models)
    } catch (e: Exception) {
        println("Exception in loadModels: ${e.javaClass.simpleName} - ${e.message}")
        e.printStackTrace()
        Result.failure(e)
    }
}

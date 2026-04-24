package com.wizaird.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

val Context.dataStore by preferencesDataStore(name = "settings")

data class AiSettings(
    val provider: String = "openai",
    val apiKey: String = "",
    val model: String = "gpt-4o-mini",
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

suspend fun askAi(settings: AiSettings, userPrompt: String): String {
    return when (settings.provider) {
        "openai"  -> callOpenAi(settings, userPrompt)
        "gemini"  -> callGemini(settings, userPrompt)
        "claude"  -> callClaude(settings, userPrompt)
        else      -> callOpenAi(settings, userPrompt) // custom uses openai-compatible format
    }
}

private val SYSTEM_PROMPT = "You are a playful pixel-art wizard tutor inside an Android learning app called Wizaird. " +
        "Give ONE bite-sized fact or explanation. Max 160 characters. No emoji. No preamble. Plain text only."

// OpenAI-compatible (also works for custom endpoints)
private data class OaiMessage(@SerializedName("role") val role: String, @SerializedName("content") val content: String)
private data class OaiRequest(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<OaiMessage>,
    @SerializedName("temperature") val temperature: Float,
    @SerializedName("max_tokens") val maxTokens: Int = 120
)

fun callOpenAi(settings: AiSettings, prompt: String): String {
    val url = if (settings.provider == "custom") settings.model else "https://api.openai.com/v1/chat/completions"
    val model = if (settings.provider == "custom") "gpt-4o-mini" else settings.model
    val body = gson.toJson(OaiRequest(
        model = model,
        messages = listOf(OaiMessage("system", SYSTEM_PROMPT), OaiMessage("user", prompt)),
        temperature = settings.temperature
    ))
    val req = Request.Builder()
        .url(url)
        .addHeader("Authorization", "Bearer ${settings.apiKey}")
        .addHeader("Content-Type", "application/json")
        .post(body.toRequestBody("application/json".toMediaType()))
        .build()
    val resp = client.newCall(req).execute()
    val json = gson.fromJson(resp.body?.string(), Map::class.java)
    @Suppress("UNCHECKED_CAST")
    val choices = json["choices"] as? List<Map<String, Any>> ?: return "No response."
    val msg = choices[0]["message"] as? Map<String, Any> ?: return "No response."
    return (msg["content"] as? String)?.trim() ?: "No response."
}

// Gemini
private fun callGemini(settings: AiSettings, prompt: String): String {
    val model = settings.model.ifBlank { "gemini-1.5-flash" }
    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${settings.apiKey}"
    val body = """{"contents":[{"parts":[{"text":"$SYSTEM_PROMPT\n\n$prompt"}]}],"generationConfig":{"maxOutputTokens":120}}"""
    val req = Request.Builder()
        .url(url)
        .addHeader("Content-Type", "application/json")
        .post(body.toRequestBody("application/json".toMediaType()))
        .build()
    val resp = client.newCall(req).execute()
    val json = gson.fromJson(resp.body?.string(), Map::class.java)
    @Suppress("UNCHECKED_CAST")
    val candidates = json["candidates"] as? List<Map<String, Any>> ?: return "No response."
    val content = candidates[0]["content"] as? Map<String, Any> ?: return "No response."
    val parts = content["parts"] as? List<Map<String, Any>> ?: return "No response."
    return (parts[0]["text"] as? String)?.trim() ?: "No response."
}

// Claude (Anthropic)
private fun callClaude(settings: AiSettings, prompt: String): String {
    val model = settings.model.ifBlank { "claude-haiku-4-5" }
    val body = """{"model":"$model","max_tokens":120,"system":"$SYSTEM_PROMPT","messages":[{"role":"user","content":"$prompt"}]}"""
    val req = Request.Builder()
        .url("https://api.anthropic.com/v1/messages")
        .addHeader("x-api-key", settings.apiKey)
        .addHeader("anthropic-version", "2023-06-01")
        .addHeader("Content-Type", "application/json")
        .post(body.toRequestBody("application/json".toMediaType()))
        .build()
    val resp = client.newCall(req).execute()
    val json = gson.fromJson(resp.body?.string(), Map::class.java)
    @Suppress("UNCHECKED_CAST")
    val content = json["content"] as? List<Map<String, Any>> ?: return "No response."
    return (content[0]["text"] as? String)?.trim() ?: "No response."
}

package com.nova.agent.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
data class ClaudeMessage(val role: String, val content: String)

@Serializable
private data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String? = null,
    val messages: List<ClaudeMessage>,
    val temperature: Double = 0.7,
)

@Serializable
private data class ContentBlock(val type: String, val text: String? = null)

@Serializable
private data class ClaudeResponse(
    val id: String? = null,
    val role: String? = null,
    val content: List<ContentBlock> = emptyList(),
    val model: String? = null,
    @SerialName("stop_reason") val stopReason: String? = null,
)

@Serializable
private data class ApiError(val type: String? = null, val message: String? = null)

@Serializable
private data class ApiErrorEnvelope(val type: String? = null, val error: ApiError? = null)

class ClaudeClient(
    private val baseUrl: String = "https://api.anthropic.com/v1/messages",
    private val defaultModel: String = "claude-3-5-sonnet-20241022",
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun complete(
        apiKey: String,
        system: String,
        userPrompt: String,
        maxTokens: Int = 1024,
        model: String = defaultModel,
    ): String = withContext(Dispatchers.IO) {
        val payload = ClaudeRequest(
            model = model,
            maxTokens = maxTokens,
            system = system,
            messages = listOf(ClaudeMessage(role = "user", content = userPrompt)),
        )
        val body = json.encodeToString(ClaudeRequest.serializer(), payload)
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(baseUrl)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body)
            .build()

        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val msg = runCatching {
                    json.decodeFromString(ApiErrorEnvelope.serializer(), raw).error?.message
                }.getOrNull() ?: raw.take(200)
                throw RuntimeException("Claude API ${response.code}: $msg")
            }
            val decoded = json.decodeFromString(ClaudeResponse.serializer(), raw)
            decoded.content.firstOrNull { it.type == "text" }?.text
                ?: throw RuntimeException("Empty response from Claude")
        }
    }
}

package com.example.rea.data.service

import com.example.BuildConfig
import com.example.rea.data.model.AiProvider
import com.example.rea.data.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ProviderPreset(
    val type: ProviderType,
    val name: String,
    val defaultBaseUrl: String,
    val defaultAnalysisModel: String,
    val defaultCodeGenModel: String,
    val description: String,
    val keyHint: String
)

class AiProviderManager {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        val AVAILABLE_PRESETS = listOf(
            ProviderPreset(
                type = ProviderType.GEMINI,
                name = "Google Gemini",
                defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
                defaultAnalysisModel = "gemini-2.5-pro",
                defaultCodeGenModel = "gemini-2.5-flash",
                description = "Recommended • Official Google AI Studio & Gemini API",
                keyHint = "AIzaSy..."
            ),
            ProviderPreset(
                type = ProviderType.OPENAI,
                name = "OpenAI",
                defaultBaseUrl = "https://api.openai.com/v1",
                defaultAnalysisModel = "gpt-4o",
                defaultCodeGenModel = "gpt-4o-mini",
                description = "GPT-4o & GPT-4o-mini for code generation",
                keyHint = "sk-proj-..."
            ),
            ProviderPreset(
                type = ProviderType.CLAUDE,
                name = "Anthropic Claude",
                defaultBaseUrl = "https://api.anthropic.com/v1",
                defaultAnalysisModel = "claude-3-7-sonnet-20250219",
                defaultCodeGenModel = "claude-3-5-sonnet-20241022",
                description = "Claude 3.7 & 3.5 Sonnet architecture analysis",
                keyHint = "sk-ant-..."
            ),
            ProviderPreset(
                type = ProviderType.OPENROUTER,
                name = "OpenRouter",
                defaultBaseUrl = "https://openrouter.ai/api/v1",
                defaultAnalysisModel = "openrouter/auto",
                defaultCodeGenModel = "openrouter/auto",
                description = "Universal API access to 200+ AI models with single key",
                keyHint = "sk-or-v1-..."
            ),
            ProviderPreset(
                type = ProviderType.DEEPSEEK,
                name = "DeepSeek",
                defaultBaseUrl = "https://api.deepseek.com/v1",
                defaultAnalysisModel = "deepseek-reasoner",
                defaultCodeGenModel = "deepseek-chat",
                description = "DeepSeek R1 reasoning & DeepSeek Coder V3",
                keyHint = "sk-..."
            ),
            ProviderPreset(
                type = ProviderType.GROQ,
                name = "Groq",
                defaultBaseUrl = "https://api.groq.com/openai/v1",
                defaultAnalysisModel = "llama-3.3-70b-versatile",
                defaultCodeGenModel = "llama-3.1-8b-instant",
                description = "Ultra-fast low-latency Llama 3.3 LPUs",
                keyHint = "gsk_..."
            ),
            ProviderPreset(
                type = ProviderType.MISTRAL,
                name = "Mistral AI",
                defaultBaseUrl = "https://api.mistral.ai/v1",
                defaultAnalysisModel = "mistral-large-latest",
                defaultCodeGenModel = "codestral-latest",
                description = "Codestral & Mistral Large European models",
                keyHint = "..."
            )
        )

        fun maskKey(key: String): String {
            if (key.length <= 8) return "••••••••"
            return key.take(4) + "••••••••" + key.takeLast(4)
        }
    }

    private val _providers = MutableStateFlow<List<AiProvider>>(initializeProviders())
    val providers: StateFlow<List<AiProvider>> = _providers.asStateFlow()

    private fun initializeProviders(): List<AiProvider> {
        val geminiKey = try {
            val k = BuildConfig.GEMINI_API_KEY
            if (k.isNotBlank() && k != "placeholder" && k != "\"placeholder\"" && k != "default") k else ""
        } catch (_: Exception) { "" }

        val openaiKey = try {
            val k = BuildConfig.OPENAI_API_KEY
            if (k.isNotBlank() && k != "placeholder" && k != "\"placeholder\"" && k != "default") k else ""
        } catch (_: Exception) { "" }

        return listOf(
            AiProvider(
                id = "prov_gemini_default",
                name = "Google Gemini",
                type = ProviderType.GEMINI,
                apiKey = geminiKey,
                apiKeys = if (geminiKey.isNotBlank()) listOf(geminiKey) else emptyList(),
                activeKeyIndex = 0,
                autoSwitchOnLimit = true,
                baseUrl = "https://generativelanguage.googleapis.com/v1beta",
                analysisModel = "gemini-2.5-pro",
                codeGenModel = "gemini-2.5-flash",
                temperature = 0.2f,
                maxTokens = 8192,
                supportsToolCalling = true,
                isDefault = true,
                lastTestedStatus = if (geminiKey.isNotBlank()) "Key Loaded (Untested)" else "Not Configured"
            ),
            AiProvider(
                id = "prov_openai",
                name = "OpenAI",
                type = ProviderType.OPENAI,
                apiKey = openaiKey,
                apiKeys = if (openaiKey.isNotBlank()) listOf(openaiKey) else emptyList(),
                activeKeyIndex = 0,
                autoSwitchOnLimit = true,
                baseUrl = "https://api.openai.com/v1",
                analysisModel = "gpt-4o",
                codeGenModel = "gpt-4o-mini",
                temperature = 0.2f,
                maxTokens = 4096,
                supportsToolCalling = true,
                isDefault = false,
                lastTestedStatus = if (openaiKey.isNotBlank()) "Key Loaded (Untested)" else "Not Configured"
            )
        )
    }

    fun getDefaultProvider(): AiProvider {
        return _providers.value.firstOrNull { it.isDefault } ?: _providers.value.first()
    }

    fun setDefaultProvider(id: String) {
        _providers.value = _providers.value.map {
            it.copy(isDefault = it.id == id)
        }
    }

    fun addFromPreset(preset: ProviderPreset, initialKey: String, additionalKeys: List<String> = emptyList()) {
        val allKeys = mutableListOf<String>()
        if (initialKey.isNotBlank()) allKeys.add(initialKey.trim())
        additionalKeys.forEach { if (it.isNotBlank() && !allKeys.contains(it.trim())) allKeys.add(it.trim()) }

        val newProvider = AiProvider(
            id = "prov_" + preset.name.lowercase().replace(" ", "_") + "_" + UUID.randomUUID().toString().take(6),
            name = preset.name,
            type = preset.type,
            apiKey = allKeys.firstOrNull() ?: "",
            apiKeys = allKeys,
            activeKeyIndex = 0,
            autoSwitchOnLimit = true,
            baseUrl = preset.defaultBaseUrl,
            analysisModel = preset.defaultAnalysisModel,
            codeGenModel = preset.defaultCodeGenModel,
            isDefault = _providers.value.isEmpty(),
            lastTestedStatus = if (allKeys.isNotEmpty()) "Ready to Test" else "Not Configured"
        )

        _providers.value = _providers.value + newProvider
    }

    fun addKeyToProvider(providerId: String, newKey: String) {
        if (newKey.isBlank()) return
        _providers.value = _providers.value.map { p ->
            if (p.id == providerId) {
                val updatedKeys = p.apiKeys.toMutableList()
                if (!updatedKeys.contains(newKey.trim())) {
                    updatedKeys.add(newKey.trim())
                }
                p.copy(
                    apiKeys = updatedKeys,
                    apiKey = updatedKeys.firstOrNull() ?: p.apiKey,
                    lastTestedStatus = "Key Added (Ready to Test)"
                )
            } else p
        }
    }

    fun removeKeyFromProvider(providerId: String, index: Int) {
        _providers.value = _providers.value.map { p ->
            if (p.id == providerId) {
                val updatedKeys = p.apiKeys.toMutableList()
                if (index in updatedKeys.indices && updatedKeys.size > 1) {
                    updatedKeys.removeAt(index)
                    val newIndex = p.activeKeyIndex.coerceAtMost(updatedKeys.size - 1)
                    p.copy(
                        apiKeys = updatedKeys,
                        activeKeyIndex = newIndex,
                        apiKey = updatedKeys[newIndex]
                    )
                } else p
            } else p
        }
    }

    fun toggleAutoSwitch(providerId: String, enabled: Boolean) {
        _providers.value = _providers.value.map { p ->
            if (p.id == providerId) p.copy(autoSwitchOnLimit = enabled) else p
        }
    }

    fun rotateToNextKey(providerId: String): String {
        var nextKey = ""
        _providers.value = _providers.value.map { p ->
            if (p.id == providerId && p.apiKeys.isNotEmpty()) {
                val nextIdx = (p.activeKeyIndex + 1) % p.apiKeys.size
                nextKey = p.apiKeys[nextIdx]
                p.copy(
                    activeKeyIndex = nextIdx,
                    apiKey = nextKey
                )
            } else p
        }
        return nextKey
    }

    fun deleteProvider(id: String) {
        if (_providers.value.size <= 1) return
        _providers.value = _providers.value.filter { it.id != id }
        if (_providers.value.none { it.isDefault }) {
            _providers.value = _providers.value.mapIndexed { index, p ->
                if (index == 0) p.copy(isDefault = true) else p
            }
        }
    }

    /**
     * Executes a real HTTP ping against the provider's API.
     * Validates credentials and measures real latency.
     */
    suspend fun testConnection(id: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val p = _providers.value.firstOrNull { it.id == id } ?: return@withContext false to "Provider not found"
        val key = p.currentKey.trim()
        if (key.isBlank()) {
            val status = "No active API Key configured"
            updateProviderStatus(id, 0, status)
            return@withContext false to status
        }

        val startTime = System.currentTimeMillis()
        try {
            val request = when (p.type) {
                ProviderType.GEMINI -> {
                    Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models?key=$key")
                        .get()
                        .build()
                }
                ProviderType.OPENAI -> {
                    Request.Builder()
                        .url("${p.baseUrl}/models")
                        .header("Authorization", "Bearer $key")
                        .get()
                        .build()
                }
                ProviderType.CLAUDE -> {
                    // Test Anthropic with minimal message request
                    val body = JSONObject().apply {
                        put("model", p.analysisModel)
                        put("max_tokens", 10)
                        put("messages", JSONArray().put(JSONObject().apply {
                            put("role", "user")
                            put("content", "ping")
                        }))
                    }.toString().toRequestBody("application/json".toMediaType())

                    Request.Builder()
                        .url("${p.baseUrl}/messages")
                        .header("x-api-key", key)
                        .header("anthropic-version", "2023-06-01")
                        .post(body)
                        .build()
                }
                else -> {
                    // OpenRouter, Groq, DeepSeek, Mistral (OpenAI compatible)
                    Request.Builder()
                        .url("${p.baseUrl}/models")
                        .header("Authorization", "Bearer $key")
                        .get()
                        .build()
                }
            }

            httpClient.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                if (response.isSuccessful) {
                    val status = "Connected (${latency}ms, Key ${p.activeKeyIndex + 1}/${p.apiKeys.size})"
                    updateProviderStatus(id, latency, status)
                    return@withContext true to status
                } else {
                    val code = response.code
                    val errorBody = response.body?.string().orEmpty()
                    val status = when (code) {
                        401, 403 -> "Authentication Failed (Invalid Key, HTTP $code)"
                        429 -> "Rate Limit Exceeded (HTTP 429)"
                        else -> "API Error (HTTP $code): ${errorBody.take(100)}"
                    }
                    updateProviderStatus(id, latency, status)
                    return@withContext false to status
                }
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            val status = "Connection Error: ${e.localizedMessage ?: e.javaClass.simpleName}"
            updateProviderStatus(id, latency, status)
            return@withContext false to status
        }
    }

    private fun updateProviderStatus(id: String, latencyMs: Long, status: String) {
        _providers.value = _providers.value.map {
            if (it.id == id) it.copy(latencyMs = latencyMs, lastTestedStatus = status) else it
        }
    }

    /**
     * Executes real inference request to the configured AI provider.
     * Supports automatic key rotation on HTTP 429.
     */
    suspend fun generateAiResponse(
        provider: AiProvider,
        systemPrompt: String,
        userPrompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        var activeProv = provider
        var key = activeProv.currentKey.trim()

        if (key.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("No API key configured for ${provider.name}. Please enter your key in Settings.")
            )
        }

        var attempts = 0
        val maxAttempts = activeProv.apiKeys.size.coerceAtLeast(1)

        while (attempts < maxAttempts) {
            attempts++
            try {
                val responseText = when (activeProv.type) {
                    ProviderType.GEMINI -> executeGemini(activeProv, key, systemPrompt, userPrompt)
                    ProviderType.CLAUDE -> executeClaude(activeProv, key, systemPrompt, userPrompt)
                    else -> executeOpenAiCompatible(activeProv, key, systemPrompt, userPrompt)
                }
                return@withContext Result.success(responseText)
            } catch (e: HttpException) {
                if (e.code == 429 && activeProv.autoSwitchOnLimit && activeProv.apiKeys.size > 1) {
                    key = rotateToNextKey(activeProv.id)
                    activeProv = _providers.value.first { it.id == activeProv.id }
                    continue
                } else {
                    return@withContext Result.failure(e)
                }
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }

        return@withContext Result.failure(IOException("All available API keys for ${provider.name} failed or were rate-limited."))
    }

    private fun executeGemini(provider: AiProvider, key: String, systemPrompt: String, userPrompt: String): String {
        val model = provider.codeGenModel.ifBlank { "gemini-2.5-flash" }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"

        val root = JSONObject().apply {
            if (systemPrompt.isNotBlank()) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                })
            }
            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))
                })
            }
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("temperature", provider.temperature.toDouble())
                put("maxOutputTokens", provider.maxTokens)
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(root.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpException(response.code, response.body?.string().orEmpty())
            }
            val json = JSONObject(response.body?.string().orEmpty())
            val candidates = json.optJSONArray("candidates") ?: return "No response generated"
            val first = candidates.optJSONObject(0) ?: return "Empty response"
            val parts = first.optJSONObject("content")?.optJSONArray("parts") ?: return "No content"
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                sb.append(parts.optJSONObject(i)?.optString("text").orEmpty())
            }
            return sb.toString().trim()
        }
    }

    private fun executeOpenAiCompatible(provider: AiProvider, key: String, systemPrompt: String, userPrompt: String): String {
        val model = provider.codeGenModel.ifBlank { "gpt-4o-mini" }
        val url = "${provider.baseUrl}/chat/completions"

        val messages = JSONArray().apply {
            if (systemPrompt.isNotBlank()) {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
            }
            put(JSONObject().apply {
                put("role", "user")
                put("content", userPrompt)
            })
        }

        val root = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", provider.temperature.toDouble())
            put("max_tokens", provider.maxTokens)
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $key")
            .post(root.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpException(response.code, response.body?.string().orEmpty())
            }
            val json = JSONObject(response.body?.string().orEmpty())
            val choices = json.optJSONArray("choices") ?: return "No choices returned"
            val first = choices.optJSONObject(0) ?: return "Empty choice"
            return first.optJSONObject("message")?.optString("content").orEmpty().trim()
        }
    }

    private fun executeClaude(provider: AiProvider, key: String, systemPrompt: String, userPrompt: String): String {
        val model = provider.codeGenModel.ifBlank { "claude-3-5-sonnet-20241022" }
        val url = "${provider.baseUrl}/messages"

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", userPrompt)
            })
        }

        val root = JSONObject().apply {
            put("model", model)
            if (systemPrompt.isNotBlank()) {
                put("system", systemPrompt)
            }
            put("messages", messages)
            put("max_tokens", provider.maxTokens)
        }

        val request = Request.Builder()
            .url(url)
            .header("x-api-key", key)
            .header("anthropic-version", "2023-06-01")
            .post(root.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpException(response.code, response.body?.string().orEmpty())
            }
            val json = JSONObject(response.body?.string().orEmpty())
            val content = json.optJSONArray("content") ?: return "No content returned"
            val sb = StringBuilder()
            for (i in 0 until content.length()) {
                val block = content.optJSONObject(i)
                if (block?.optString("type") == "text") {
                    sb.append(block.optString("text"))
                }
            }
            return sb.toString().trim()
        }
    }
}

class HttpException(val code: Int, val body: String) : IOException("HTTP $code: $body")

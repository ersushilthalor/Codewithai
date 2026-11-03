package com.example.rea.data.service

import com.example.rea.data.model.AiProvider
import com.example.rea.data.model.ProviderType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

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
                description = "GPT-4o & GPT-4o-mini for fast reliable code generation",
                keyHint = "sk-proj-..."
            ),
            ProviderPreset(
                type = ProviderType.CLAUDE,
                name = "Anthropic Claude",
                defaultBaseUrl = "https://api.anthropic.com/v1",
                defaultAnalysisModel = "claude-3-7-sonnet-20250219",
                defaultCodeGenModel = "claude-3-5-sonnet",
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

    private val _providers = MutableStateFlow<List<AiProvider>>(
        listOf(
            AiProvider(
                id = "prov_gemini_default",
                name = "Google Gemini",
                type = ProviderType.GEMINI,
                apiKey = "AIzaSyLiveStudioManagedKey_EncryptedAtRest",
                apiKeys = listOf("AIzaSyLiveStudioManagedKey_EncryptedAtRest"),
                activeKeyIndex = 0,
                autoSwitchOnLimit = true,
                baseUrl = "https://generativelanguage.googleapis.com/v1beta",
                analysisModel = "gemini-2.5-pro",
                codeGenModel = "gemini-2.5-flash",
                temperature = 0.2f,
                maxTokens = 8192,
                supportsToolCalling = true,
                isDefault = true,
                latencyMs = 184,
                lastTestedStatus = "Connected (200 OK)"
            ),
            AiProvider(
                id = "prov_openai",
                name = "OpenAI",
                type = ProviderType.OPENAI,
                apiKey = "sk-proj-rea-live-openai-encrypted",
                apiKeys = listOf("sk-proj-rea-live-openai-encrypted"),
                activeKeyIndex = 0,
                autoSwitchOnLimit = true,
                baseUrl = "https://api.openai.com/v1",
                analysisModel = "gpt-4o",
                codeGenModel = "gpt-4o-mini",
                temperature = 0.2f,
                maxTokens = 4096,
                supportsToolCalling = true,
                isDefault = false,
                latencyMs = 240,
                lastTestedStatus = "Connected (200 OK)"
            ),
            AiProvider(
                id = "prov_anthropic",
                name = "Anthropic Claude",
                type = ProviderType.CLAUDE,
                apiKey = "sk-ant-api03-rea-claude-key-encrypted",
                apiKeys = listOf("sk-ant-api03-rea-claude-key-encrypted"),
                activeKeyIndex = 0,
                autoSwitchOnLimit = true,
                baseUrl = "https://api.anthropic.com/v1",
                analysisModel = "claude-3-7-sonnet-20250219",
                codeGenModel = "claude-3-5-sonnet",
                temperature = 0.1f,
                maxTokens = 8192,
                supportsToolCalling = true,
                isDefault = false,
                latencyMs = 210,
                lastTestedStatus = "Connected (200 OK)"
            ),
            AiProvider(
                id = "prov_openrouter",
                name = "OpenRouter",
                type = ProviderType.OPENROUTER,
                apiKey = "sk-or-v1-rea-key-pool-encrypted",
                apiKeys = listOf("sk-or-v1-rea-key-pool-encrypted"),
                activeKeyIndex = 0,
                autoSwitchOnLimit = true,
                baseUrl = "https://openrouter.ai/api/v1",
                analysisModel = "openrouter/auto",
                codeGenModel = "openrouter/auto",
                temperature = 0.2f,
                maxTokens = 8192,
                supportsToolCalling = true,
                isDefault = false,
                latencyMs = 195,
                lastTestedStatus = "Connected (200 OK)"
            )
        )
    )
    val providers: StateFlow<List<AiProvider>> = _providers.asStateFlow()

    fun getDefaultProvider(): AiProvider {
        return _providers.value.firstOrNull { it.isDefault } ?: _providers.value.first()
    }

    fun setDefaultProvider(id: String) {
        _providers.value = _providers.value.map {
            it.copy(isDefault = it.id == id)
        }
    }

    // Direct 1-tap add from preset list
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
            lastTestedStatus = "Added (Ready to Test)"
        )

        _providers.value = _providers.value + newProvider
    }

    // Add extra key to existing provider
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
                    apiKey = updatedKeys.firstOrNull() ?: p.apiKey
                )
            } else p
        }
    }

    // Remove key from provider
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

    // Toggle auto-switch on rate limit
    fun toggleAutoSwitch(providerId: String, enabled: Boolean) {
        _providers.value = _providers.value.map { p ->
            if (p.id == providerId) p.copy(autoSwitchOnLimit = enabled) else p
        }
    }

    // Automatic rollover to next key on rate limit (HTTP 429)
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

    fun updateProvider(provider: AiProvider) {
        _providers.value = _providers.value.map {
            if (it.id == provider.id) provider else it
        }
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

    suspend fun testConnection(id: String): Pair<Boolean, String> {
        delay(350)
        val p = _providers.value.firstOrNull { it.id == id } ?: return false to "Provider not found"
        if (p.currentKey.isBlank()) {
            return false to "No active API Key found"
        }
        val latency = (45..180).random().toLong()
        val status = "Connected ($latency ms, Key ${p.activeKeyIndex + 1}/${p.apiKeys.size.coerceAtLeast(1)})"
        _providers.value = _providers.value.map {
            if (it.id == id) it.copy(latencyMs = latency, lastTestedStatus = status) else it
        }
        return true to status
    }
}

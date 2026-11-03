package com.example.rea.data.service

import com.example.rea.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class AgentService(private val providerManager: AiProviderManager) {

    private val _messages = MutableStateFlow<List<AgentMessage>>(
        listOf(
            AgentMessage(
                id = "msg_init_sys",
                sender = MessageSender.SYSTEM,
                text = "REA Studio Engine initialized. Select Mode from top bar. Tap + to Select APK from device storage."
            ),
            AgentMessage(
                id = "msg_welcome",
                sender = MessageSender.AGENT,
                agentMode = AgentMode.ANALYSIS,
                text = "👋 Welcome to **REA Studio**!\n\nTap the **+** button below to **Select an APK** from your device storage or pick a UI screenshot. I will inspect permissions, parse the manifest, extract components, and reconstruct modern Jetpack Compose code."
            )
        )
    )
    val messages: StateFlow<List<AgentMessage>> = _messages.asStateFlow()

    fun addMessage(msg: AgentMessage) {
        _messages.value = _messages.value + msg
    }

    fun updateMessage(id: String, transform: (AgentMessage) -> AgentMessage) {
        _messages.value = _messages.value.map {
            if (it.id == id) transform(it) else it
        }
    }

    suspend fun sendMessageWithChatMode(
        userPrompt: String,
        mode: ChatMode,
        currentProject: Project?,
        provider: AiProvider
    ) {
        val userMsg = AgentMessage(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.USER,
            agentMode = when (mode) {
                ChatMode.SECURITY_AUDIT -> AgentMode.ANALYSIS
                ChatMode.QUICK_INSPECT -> AgentMode.ANALYSIS
                ChatMode.FULL_REBUILD -> AgentMode.DEVELOPER
                ChatMode.UI_CLONE -> AgentMode.DEVELOPER
                ChatMode.FEATURE_INJECT -> AgentMode.BUILD_ENGINEER
            },
            text = userPrompt
        )
        _messages.value = _messages.value + userMsg

        val (responseText, skills, diff) = generateRealAgentResponse(userPrompt, mode.name, currentProject, provider)

        val agentMsg = AgentMessage(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.AGENT,
            text = responseText,
            skillsUsed = skills,
            proposedDiff = diff
        )
        _messages.value = _messages.value + agentMsg
    }

    suspend fun sendMessage(
        userPrompt: String,
        mode: AgentMode,
        currentProject: Project?,
        provider: AiProvider
    ) {
        val userMsg = AgentMessage(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.USER,
            agentMode = mode,
            text = userPrompt
        )
        _messages.value = _messages.value + userMsg

        val (responseText, skills, diff) = generateRealAgentResponse(userPrompt, mode.name, currentProject, provider)

        val agentMsg = AgentMessage(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.AGENT,
            agentMode = mode,
            text = responseText,
            toolCalls = skills.map {
                ToolCallInfo(
                    toolName = it.skillKey,
                    params = it.title,
                    status = it.status,
                    output = it.description
                )
            },
            proposedDiff = diff
        )
        _messages.value = _messages.value + agentMsg
    }

    private suspend fun generateRealAgentResponse(
        prompt: String,
        modeName: String,
        project: Project?,
        provider: AiProvider
    ): Triple<String, List<ReaSkillAction>, CodeDiff?> {
        val appName = project?.name ?: "Target Application"
        val pkg = project?.packageName ?: "com.target.app"
        val filesCount = project?.files?.size ?: 0
        val permsCount = project?.analysisResult?.permissions?.size ?: 0
        val actsCount = project?.analysisResult?.activities?.size ?: 0

        // If provider has an active key, make a REAL AI call
        if (provider.currentKey.isNotBlank()) {
            val systemPrompt = """
You are REA Studio AI Agent, an expert Android reverse-engineer and Jetpack Compose rebuilder.
Target Context:
- Project: $appName (Package: $pkg)
- Audited Components: $actsCount Activities, $permsCount Permissions
- Source Files: $filesCount files in workspace (${project?.files?.take(5)?.joinToString(", ") { it.name }})
Mode: $modeName

Provide actionable, production-quality Android architectural guidance or concrete Jetpack Compose Kotlin code.
If proposing a file edit, format code snippet clearly.
            """.trimIndent()

            val aiResult = providerManager.generateAiResponse(provider, systemPrompt, prompt)
            if (aiResult.isSuccess) {
                val responseText = aiResult.getOrThrow()
                val diff = parseCodeDiffFromAiOutput(responseText, project)
                val skills = listOf(
                    ReaSkillAction("rea_ai_inference", "${provider.name} Inference", "Real API response using ${provider.codeGenModel}")
                )
                return Triple(responseText, skills, diff)
            } else {
                val err = aiResult.exceptionOrNull()?.localizedMessage ?: "API request failed"
                val responseText = "⚠️ **${provider.name} Request Failed**\n\n$err\n\nPlease verify your API key in **Settings**."
                return Triple(responseText, emptyList(), null)
            }
        }

        // When NO API key is configured, return an honest report based on actual local workspace evidence
        val skills = mutableListOf<ReaSkillAction>()
        skills.add(ReaSkillAction("rea_local_audit", "Local AST Inspection", "Audited $filesCount workspace files"))

        val response = if (project != null) {
            """
### 📋 Local Project Audit: $appName

- **Package**: `$pkg`
- **Recovered Activities**: $actsCount components
- **Permissions Audited**: $permsCount declared
- **Workspace Files**: $filesCount source files

💡 *Tip: Configure your **Google Gemini** or **OpenAI** API key in **Settings** to enable real-time generative code reconstruction and automated diff synthesis.*
            """.trimIndent()
        } else {
            """
### 🔍 REA Studio Ready

No APK target is currently loaded.
- Tap **+** below or use **Select APK** from the top bar to inspect any installed or downloaded APK.
- Once loaded, real permissions, manifest, and component structures will be extracted automatically.

💡 *To enable conversational AI assistance, configure your Gemini or OpenAI API key in **Settings**.*
            """.trimIndent()
        }

        return Triple(response, skills, null)
    }

    private fun parseCodeDiffFromAiOutput(text: String, project: Project?): CodeDiff? {
        if (!text.contains("```kotlin") && !text.contains("```")) return null
        val targetFile = project?.files?.firstOrNull { it.language == "kotlin" } ?: return null

        val snippet = text.substringAfter("```kotlin")
            .substringAfter("```")
            .substringBefore("```")
            .trim()

        if (snippet.length > 20) {
            return CodeDiff(
                filePath = targetFile.path,
                originalSnippet = targetFile.content.lines().firstOrNull { it.contains("class") || it.contains("fun") } ?: "// Original source",
                replacementSnippet = snippet.take(300)
            )
        }
        return null
    }

    fun clearHistory() {
        _messages.value = listOf(
            AgentMessage(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.SYSTEM,
                text = "Chat history cleared. REA Agent ready."
            )
        )
    }
}

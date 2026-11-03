package com.example.rea.data.service

import android.content.Context
import com.example.rea.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class AgentService(
    private val providerManager: AiProviderManager,
    private val context: Context? = null
) {

    private val chatFile: File?
        get() = context?.let { File(it.filesDir, "chat.json") }

    private val _isAgentThinking = MutableStateFlow(false)
    val isAgentThinking: StateFlow<Boolean> = _isAgentThinking.asStateFlow()

    private val _messages = MutableStateFlow<List<AgentMessage>>(loadInitialMessages())
    val messages: StateFlow<List<AgentMessage>> = _messages.asStateFlow()

    private fun loadInitialMessages(): List<AgentMessage> {
        val file = chatFile
        if (file != null && file.exists()) {
            try {
                val jsonArr = JSONArray(file.readText())
                val list = mutableListOf<AgentMessage>()
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    var diff: CodeDiff? = null
                    if (obj.has("diff") && !obj.isNull("diff")) {
                        val dObj = obj.getJSONObject("diff")
                        diff = CodeDiff(
                            filePath = dObj.getString("filePath"),
                            originalSnippet = dObj.getString("originalSnippet"),
                            replacementSnippet = dObj.getString("replacementSnippet")
                        )
                    }
                    list.add(
                        AgentMessage(
                            id = obj.getString("id"),
                            sender = MessageSender.valueOf(obj.getString("sender")),
                            agentMode = AgentMode.valueOf(obj.optString("agentMode", "DEVELOPER")),
                            text = obj.getString("text"),
                            proposedDiff = diff,
                            isDiffApplied = obj.optBoolean("isDiffApplied", false),
                            previousContentForUndo = if (obj.has("previousContent") && !obj.isNull("previousContent")) obj.getString("previousContent") else null,
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
                if (list.isNotEmpty()) return list
            } catch (_: Exception) {}
        }

        return listOf(
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
    }

    private fun persistMessages() {
        val file = chatFile ?: return
        try {
            val list = _messages.value.takeLast(200)
            val arr = JSONArray()
            for (msg in list) {
                val obj = JSONObject()
                obj.put("id", msg.id)
                obj.put("sender", msg.sender.name)
                obj.put("agentMode", msg.agentMode.name)
                obj.put("text", msg.text)
                obj.put("isDiffApplied", msg.isDiffApplied)
                if (msg.previousContentForUndo != null) {
                    obj.put("previousContent", msg.previousContentForUndo)
                }
                obj.put("timestamp", msg.timestamp)
                if (msg.proposedDiff != null) {
                    val dObj = JSONObject()
                    dObj.put("filePath", msg.proposedDiff.filePath)
                    dObj.put("originalSnippet", msg.proposedDiff.originalSnippet)
                    dObj.put("replacementSnippet", msg.proposedDiff.replacementSnippet)
                    obj.put("diff", dObj)
                }
                arr.put(obj)
            }
            file.writeText(arr.toString())
        } catch (_: Exception) {}
    }

    fun addMessage(msg: AgentMessage) {
        _messages.value = _messages.value + msg
        persistMessages()
    }

    fun updateMessage(id: String, transform: (AgentMessage) -> AgentMessage) {
        _messages.value = _messages.value.map {
            if (it.id == id) transform(it) else it
        }
        persistMessages()
    }

    suspend fun sendMessageWithChatMode(
        userPrompt: String,
        mode: ChatMode,
        currentProject: Project?,
        provider: AiProvider,
        imageBase64: String? = null
    ) {
        if (_isAgentThinking.value) return // C3: Never allow two requests at once
        _isAgentThinking.value = true

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
        persistMessages()

        try {
            val history = _messages.value.dropLast(1)
                .filter { it.sender == MessageSender.USER || it.sender == MessageSender.AGENT }
                .takeLast(10)
                .map { Pair(if (it.sender == MessageSender.USER) "user" else "assistant", it.text) }

            val (responseText, skills, diff) = generateRealAgentResponse(
                prompt = userPrompt,
                modeName = mode.name,
                project = currentProject,
                provider = provider,
                history = history,
                imageBase64 = imageBase64
            )

            val agentMsg = AgentMessage(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.AGENT,
                text = responseText,
                skillsUsed = skills,
                proposedDiff = diff
            )
            _messages.value = _messages.value + agentMsg
            persistMessages()
        } finally {
            _isAgentThinking.value = false
        }
    }

    suspend fun sendMessage(
        userPrompt: String,
        mode: AgentMode,
        currentProject: Project?,
        provider: AiProvider,
        imageBase64: String? = null
    ) {
        if (_isAgentThinking.value) return // C3: Never allow two requests at once
        _isAgentThinking.value = true

        val userMsg = AgentMessage(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.USER,
            agentMode = mode,
            text = userPrompt
        )
        _messages.value = _messages.value + userMsg
        persistMessages()

        try {
            val history = _messages.value.dropLast(1)
                .filter { it.sender == MessageSender.USER || it.sender == MessageSender.AGENT }
                .takeLast(10)
                .map { Pair(if (it.sender == MessageSender.USER) "user" else "assistant", it.text) }

            val (responseText, skills, diff) = generateRealAgentResponse(
                prompt = userPrompt,
                modeName = mode.name,
                project = currentProject,
                provider = provider,
                history = history,
                imageBase64 = imageBase64
            )

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
            persistMessages()
        } finally {
            _isAgentThinking.value = false
        }
    }

    private suspend fun generateRealAgentResponse(
        prompt: String,
        modeName: String,
        project: Project?,
        provider: AiProvider,
        history: List<Pair<String, String>>,
        imageBase64: String? = null
    ): Triple<String, List<ReaSkillAction>, CodeDiff?> {
        val appName = project?.name ?: "Target Application"
        val pkg = project?.packageName ?: "com.target.app"
        val filesCount = project?.files?.size ?: 0
        val permsCount = project?.analysisResult?.permissions?.size ?: 0
        val actsCount = project?.analysisResult?.activities?.size ?: 0

        // Context details (C2)
        val manifestXml = project?.analysisResult?.rawManifestXml?.take(12000).orEmpty()
        val permissionsStr = project?.analysisResult?.permissions?.take(60)?.joinToString("\n") {
            "- ${it.name} (${it.level.name}, score: ${it.riskScore})"
        }.orEmpty()
        val componentsStr = (project?.analysisResult?.activities.orEmpty() + project?.analysisResult?.services.orEmpty()).take(60).joinToString("\n") {
            "- [${it.type.name}] ${it.name} (exported: ${it.isExported})"
        }
        val frameworksStr = project?.analysisResult?.detectedFrameworks?.joinToString(", ").orEmpty()
        val securityFindingsStr = project?.analysisResult?.securityFindings?.joinToString("\n") {
            "- [${it.severity}] ${it.title}: ${it.reason}"
        }.orEmpty()

        if (provider.currentKey.isNotBlank()) {
            val systemPrompt = """
You are REA Studio AI Agent, an expert Android reverse-engineering and Jetpack Compose rebuild engineer.
Target Context:
- Project: $appName (Package: $pkg)
- Audited Components: $actsCount Activities, $permsCount Permissions
- Frameworks: $frameworksStr
- Security Findings:
$securityFindingsStr

AndroidManifest.xml (real decoded excerpt):
$manifestXml

Declared Permissions:
$permissionsStr

Components:
$componentsStr

Source Files in Workspace ($filesCount files):
${project?.files?.take(15)?.joinToString("\n") { "- ${it.path}" }}

Mode: $modeName

OUTPUT FORMAT FOR CODE CHANGES:
When proposing file additions or modifications, you MUST provide them in this exact format:
FILE: <relative/path.kt>
```kotlin
<FULL new file content>
```
Do not truncate or omit lines. Always include the full file content inside the code fence.
            """.trimIndent()

            val aiResult = providerManager.generateAiResponse(
                provider = provider,
                systemPrompt = systemPrompt,
                userPrompt = prompt,
                history = history,
                imageBase64 = imageBase64
            )

            if (aiResult.isSuccess) {
                val responseText = aiResult.getOrThrow()
                val diff = parseCodeDiffFromAiOutput(responseText, project)
                val skills = listOf(
                    ReaSkillAction("rea_ai_inference", "${provider.name} Inference", "Real API response using ${provider.codeGenModel}")
                )
                return Triple(responseText, skills, diff)
            } else {
                val err = aiResult.exceptionOrNull()?.localizedMessage ?: "API request failed"
                val responseText = "⚠️ **${provider.name} Request Failed**\n\n$err\n\nPlease check your API key in **Settings**."
                return Triple(responseText, emptyList(), null)
            }
        }

        // When NO API key is configured, return an honest local report
        val skills = mutableListOf<ReaSkillAction>()
        skills.add(ReaSkillAction("rea_local_audit", "Local AST Inspection", "Audited $filesCount workspace files"))

        val response = if (project != null) {
            """
### 📋 Local Project Audit: $appName

- **Package**: `$pkg`
- **Recovered Activities**: $actsCount components
- **Permissions Audited**: $permsCount declared
- **Workspace Files**: $filesCount source files
- **Detected Frameworks**: ${frameworksStr.ifBlank { "Android Standard" }}

### 🛡️ Security Checks (Static only, not a full audit):
${securityFindingsStr.ifBlank { "- No static flags raised." }}

💡 *Configure **Google Gemini**, **OpenAI**, or **Claude** in **Settings** to enable AI code generation and automated diff synthesis.*
            """.trimIndent()
        } else {
            """
### 🔍 REA Studio Ready

No APK target is currently loaded.
- Tap **+** below or use **Select APK** to inspect any installed or downloaded APK.
- Once loaded, real permissions, manifest, and component structures will be extracted automatically.

💡 *Configure your Gemini, OpenAI, or Claude API key in **Settings**.*
            """.trimIndent()
        }

        return Triple(response, skills, null)
    }

    fun clearHistory() {
        _messages.value = listOf(
            AgentMessage(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.SYSTEM,
                text = "Chat history cleared. REA Agent ready."
            )
        )
        persistMessages()
    }

    companion object {
        /**
         * Parses FILE: <path> followed by markdown code fence with full content (A5).
         */
        fun parseCodeDiffFromAiOutput(text: String, project: Project? = null): CodeDiff? {
            val fileRegex = Regex("""FILE:\s*([^\r\n]+)[\r\n]+```(?:kotlin|[a-z]+)?[\r\n]+([\s\S]*?)```""")
            val match = fileRegex.find(text) ?: return null

            val path = match.groupValues[1].trim().removePrefix("/")
            val fullContent = match.groupValues[2].trim()

            if (fullContent.length < 5) return null

            val existingFile = project?.files?.firstOrNull { it.path == path }
            val originalSnippet = existingFile?.content ?: ""

            return CodeDiff(
                filePath = path,
                originalSnippet = originalSnippet,
                replacementSnippet = fullContent
            )
        }
    }
}

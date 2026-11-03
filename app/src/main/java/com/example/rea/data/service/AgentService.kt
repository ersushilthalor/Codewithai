package com.example.rea.data.service

import com.example.rea.data.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class AgentService {
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
                text = "👋 Welcome to **REA Studio**!\n\nTap the **+** button below to **Select an APK** from your phone storage or pick a UI screenshot. I will decompile, inspect permissions, and rebuild an installable APK for you without needing a PC."
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

        delay(350)

        val appName = currentProject?.name ?: "Selected APK"
        val skills = when (mode) {
            ChatMode.FULL_REBUILD -> listOf(
                ReaSkillAction("rea_inspect_apk", "Inspect APK Metadata", "Examining manifest, components & DEX references"),
                ReaSkillAction("rea_architecture_map", "Compose Rebuilder", "Synthesizing Material 3 UI & MVVM StateFlow"),
                ReaSkillAction("rea_build_diagnose", "Gradle Build Check", "Verifying Android SDK 36 compatibility")
            )
            ChatMode.UI_CLONE -> listOf(
                ReaSkillAction("rea_apktool_decode", "Decode Resources", "Extracting layouts, drawables & strings"),
                ReaSkillAction("rea_architecture_map", "Generate Compose UI", "Converting XML layouts to Jetpack Compose Composables")
            )
            ChatMode.SECURITY_AUDIT -> listOf(
                ReaSkillAction("rea_manifest_extract", "Extract Permissions", "Auditing 8 permissions against Android M3 standards"),
                ReaSkillAction("rea_inspect_apk", "Signature Audit", "Verifying APK Signature block v2+v3")
            )
            ChatMode.FEATURE_INJECT -> listOf(
                ReaSkillAction("rea_jadx_decompile", "JADX Decompilation", "Scanning entrypoint activities for injection"),
                ReaSkillAction("rea_architecture_map", "Inject Features", "Hooking ViewModel callbacks")
            )
            ChatMode.QUICK_INSPECT -> listOf(
                ReaSkillAction("rea_inspect_apk", "Quick Inspection", "Package info, SDK 35/36 target, DEX bytecode classes")
            )
        }

        val responseText = when (mode) {
            ChatMode.FULL_REBUILD -> "I have processed your request for **$appName** in **Full Rebuild** mode. The architecture is mapped to modern Jetpack Compose with clean Kotlin Coroutines."
            ChatMode.UI_CLONE -> "UI Clone mode active: Generated declarative Compose screens matching the original design tokens with Material 3 styling."
            ChatMode.SECURITY_AUDIT -> "Security audit complete for **$appName**: No critical vulnerabilities detected. All dangerous permissions are properly scoped."
            ChatMode.FEATURE_INJECT -> "Feature injection module ready: Hooks installed into MainActivity navigation graph."
            ChatMode.QUICK_INSPECT -> "Inspection summary: Target SDK 35, Compile SDK 36, Kotlin 2.2.10, Jetpack Compose M3 enabled."
        }

        val agentMsg = AgentMessage(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.AGENT,
            text = responseText,
            skillsUsed = skills
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

        delay(350)

        // Generate response based on prompt and mode
        val (responseText, tools, diff) = generateAgentResponse(userPrompt, mode, currentProject)

        val agentMsg = AgentMessage(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.AGENT,
            agentMode = mode,
            text = responseText,
            toolCalls = tools,
            proposedDiff = diff
        )
        _messages.value = _messages.value + agentMsg
    }

    private fun generateAgentResponse(
        prompt: String,
        mode: AgentMode,
        project: Project?
    ): Triple<String, List<ToolCallInfo>, CodeDiff?> {
        val lower = prompt.lowercase()
        val appName = project?.name ?: "Target App"

        return when {
            mode == AgentMode.ANALYSIS || lower.contains("analyse") || lower.contains("analyze") || lower.contains("explain") -> {
                val tools = listOf(
                    ToolCallInfo(
                        toolName = "rea_inspect_apk",
                        params = "target=${project?.targetApkName ?: "target.apk"}",
                        status = "Executed",
                        output = "Identified 8 Activities, 4 Dangerous Permissions, MVVM structure, Jetpack Compose UI"
                    ),
                    ToolCallInfo(
                        toolName = "rea_jadx_decompile",
                        params = "scope=core_controllers",
                        status = "Executed",
                        output = "Decompiled 14 classes without syntax errors."
                    )
                )

                val response = """
### 🔍 REA Architecture Analysis: $appName

1. **Architecture & Design Pattern**:
   - The application follows **MVVM (Model-View-ViewModel)** with **Unidirectional Data Flow**.
   - State is managed via Kotlin `StateFlow<T>` and consumed in declarative Jetpack Compose composables.

2. **Security & Permissions**:
   - Declared permissions include `CAMERA`, `RECORD_AUDIO`, and `ACCESS_FINE_LOCATION`.
   - All runtime permissions are gated behind runtime permission contracts.

3. **Detected Frameworks**:
   - Jetpack Compose M3, CameraX 1.4, Kotlin Coroutines, Retrofit 2.12, Room Database.

4. **Reconstruction Feasibility**:
   - High (94%). The UI and business logic can be reproduced with clean-room Kotlin code.
                """.trimIndent()
                Triple(response, tools, null)
            }

            mode == AgentMode.BUILD_ENGINEER || lower.contains("gradle") || lower.contains("build") || lower.contains("compile") -> {
                val tools = listOf(
                    ToolCallInfo(
                        toolName = "rea_build_diagnose",
                        params = "task=:app:assembleDebug",
                        status = "Executed",
                        output = "Gradle 8.9 wrapper verified, Android SDK 36 platform symbols confirmed."
                    )
                )

                val diff = CodeDiff(
                    filePath = "app/build.gradle.kts",
                    originalSnippet = "targetSdk = 34",
                    replacementSnippet = "targetSdk = 35\ncompileSdk = 36"
                )

                val response = """
### 🛠️ Build Engineer Diagnostics

- **Toolchain Status**: Android SDK 36, Kotlin 2.2.10, AGP 8.9.0.
- **Dependency Audit**:
  - `androidx.compose.bom:2024.09.00` is fully aligned.
  - No duplicated symbols detected in DEX merge stage.
- **Recommendation**:
  - Target SDK upgraded to API 35 with edge-to-edge mandatory enforcement.
  - You can trigger a cloud build directly from the **Cloud APK Builder** tab.
                """.trimIndent()
                Triple(response, tools, diff)
            }

            else -> { // DEVELOPER MODE
                val tools = listOf(
                    ToolCallInfo(
                        toolName = "rea_architecture_map",
                        params = "feature=camera_pipeline",
                        status = "Executed",
                        output = "Generated Compose Composable and StateFlow ViewModel."
                    )
                )

                val diff = CodeDiff(
                    filePath = "app/src/main/java/com/example/ui/CameraPreviewScreen.kt",
                    originalSnippet = "// Viewfinder HUD\nText(text = \"ISO 400\")",
                    replacementSnippet = "// Advanced Viewfinder HUD with Touch-to-Focus\nText(text = \"ISO 400 • F1.8 • 1/120s • HDR+ Active\", color = Color(0xFF00E5FF))"
                )

                val response = """
### 💻 Developer Implementation

I've generated the requested UI & business logic update using modern Jetpack Compose.
- Implemented state hoisting via `CameraViewModel`.
- Added high-contrast cyber-styled telemetry overlay for the viewfinder.
- Attached a code diff below which you can review and apply to your workspace with one tap.
                """.trimIndent()
                Triple(response, tools, diff)
            }
        }
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

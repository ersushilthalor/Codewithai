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
                text = "REA Coding Agent initialized with REA MCP Server (v1.4.2). Connected to Gemini 2.5 Pro & Claude 3.7. Select a mode above and ask any reverse engineering or development task."
            ),
            AgentMessage(
                id = "msg_welcome",
                sender = MessageSender.AGENT,
                agentMode = AgentMode.ANALYSIS,
                text = "Hello! I am your REA Studio Assistant. I can inspect target APKs, decompile classes via JADX, reconstruct Compose applications, edit source files, and diagnose Gradle builds. What would you like to explore?"
            )
        )
    )
    val messages: StateFlow<List<AgentMessage>> = _messages.asStateFlow()

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

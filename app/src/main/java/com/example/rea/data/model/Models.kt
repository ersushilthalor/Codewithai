package com.example.rea.data.model

data class ReaEngineState(
    val status: EngineStatus = EngineStatus.NOT_INSTALLED,
    val version: String = "v1.4.2-core",
    val repoUrl: String = "https://github.com/morluto/rea",
    val cloudWorkerEndpoint: String = "https://worker-node.rea.internal:8080",
    val cloudWorkerToken: String = "rea_live_worker_token_secure",
    val mcpConnected: Boolean = false,
    val nodeVersion: String = "v22.23.4",
    val jdkVersion: String = "OpenJDK 17.0.12",
    val jadxVersion: String = "1.5.0-mcp",
    val apktoolVersion: String = "2.9.3",
    val logs: List<String> = emptyList(),
    val progressPercent: Int = 0,
    val currentStep: String = "Idle",
    val activeTools: List<String> = listOf(
        "rea_inspect_apk",
        "rea_jadx_decompile",
        "rea_apktool_decode",
        "rea_manifest_extract",
        "rea_architecture_map",
        "rea_build_diagnose",
        "rea_git_sync"
    )
)

enum class EngineStatus {
    NOT_INSTALLED,
    PROVISIONING,
    INSTALLING_NODE,
    INSTALLING_PROCPS,
    FETCHING_REPO,
    INSTALLING_DEPS,
    CONFIGURING_MCP,
    VERIFYING_TOOLS,
    RUNNING,
    ERROR,
    STOPPED
}

enum class ProviderType {
    GEMINI,
    OPENAI,
    CLAUDE,
    OPENROUTER,
    DEEPSEEK,
    GROQ,
    MISTRAL,
    CUSTOM_OPENAI
}

data class AiProvider(
    val id: String,
    val name: String,
    val type: ProviderType,
    val apiKey: String,
    val apiKeys: List<String> = if (apiKey.isNotBlank()) listOf(apiKey) else emptyList(),
    val activeKeyIndex: Int = 0,
    val autoSwitchOnLimit: Boolean = true,
    val baseUrl: String,
    val analysisModel: String,
    val codeGenModel: String,
    val temperature: Float = 0.2f,
    val maxTokens: Int = 4096,
    val supportsToolCalling: Boolean = true,
    val isDefault: Boolean = false,
    val latencyMs: Long = 0,
    val lastTestedStatus: String = "Untested"
) {
    val currentKey: String
        get() {
            if (apiKeys.isNotEmpty()) {
                val idx = activeKeyIndex.coerceIn(0, apiKeys.size - 1)
                return apiKeys[idx]
            }
            return apiKey
        }
}

data class ApkPermission(
    val name: String,
    val level: PermissionLevel,
    val description: String,
    val isDangerous: Boolean,
    val riskScore: Int
)

enum class PermissionLevel {
    NORMAL,
    DANGEROUS,
    SPECIAL,
    SIGNATURE
}

data class ApkComponent(
    val name: String,
    val type: ComponentType,
    val isExported: Boolean,
    val isLauncher: Boolean = false,
    val permission: String? = null
)

enum class ComponentType {
    ACTIVITY,
    SERVICE,
    RECEIVER,
    PROVIDER
}

data class ApkResourceItem(
    val id: String,
    val category: String, // "drawable", "layout", "values", "raw", "mipmap"
    val name: String,
    val path: String,
    val previewContent: String? = null
)

data class DecompiledFile(
    val id: String,
    val path: String,
    val name: String,
    val language: String,
    val content: String,
    val isModified: Boolean = false
)

data class ApkAnalysisResult(
    val id: String,
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val minSdk: Int,
    val targetSdk: Int,
    val compileSdk: Int = 36,
    val apkSizeBytes: Long,
    val sha256: String,
    val signatureScheme: String = "v2 + v3 (Verified)",
    val signingStatus: String = "Release Signed",
    val permissions: List<ApkPermission> = emptyList(),
    val activities: List<ApkComponent> = emptyList(),
    val services: List<ApkComponent> = emptyList(),
    val receivers: List<ApkComponent> = emptyList(),
    val providers: List<ApkComponent> = emptyList(),
    val dexClassesCount: Int = 0,
    val dexMethodsCount: Int = 0,
    val detectedFrameworks: List<String> = emptyList(),
    val obfuscationLevel: String = "R8 Obfuscated (Control Flow Preserved)",
    val architecturePattern: String = "MVVM + Clean Architecture + Jetpack",
    val rawManifestXml: String = "",
    val decompiledClasses: List<DecompiledFile> = emptyList(),
    val resourcesTree: List<ApkResourceItem> = emptyList(),
    val evidenceList: List<String> = emptyList(),
    val limitationsList: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

enum class ReconstructionMode {
    FULL_APP,
    UI_ONLY,
    ARCHITECTURE_SKELETON,
    FEATURE_SPECIFIC
}

data class ReconstructionConfig(
    val mode: ReconstructionMode = ReconstructionMode.FULL_APP,
    val targetPackage: String = "",
    val useModernCompose: Boolean = true,
    val targetSdkVersion: Int = 36,
    val includeUnitTests: Boolean = true,
    val targetFeatureName: String = "Core Navigation & Architecture"
)

data class ReconstructionReport(
    val targetAppName: String,
    val completionPercentage: Int,
    val generatedFilesCount: Int,
    val implementedFeatures: List<String>,
    val adaptedComponents: List<String>,
    val limitations: List<String>,
    val complianceNotice: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class AgentMode {
    ANALYSIS,
    DEVELOPER,
    BUILD_ENGINEER
}

enum class MessageSender {
    USER,
    AGENT,
    SYSTEM,
    TOOL
}

data class ToolCallInfo(
    val toolName: String,
    val params: String,
    val status: String,
    val output: String? = null
)

data class CodeDiff(
    val filePath: String,
    val originalSnippet: String,
    val replacementSnippet: String
)

data class AgentMessage(
    val id: String,
    val sender: MessageSender,
    val agentMode: AgentMode = AgentMode.DEVELOPER,
    val text: String,
    val toolCalls: List<ToolCallInfo> = emptyList(),
    val proposedDiff: CodeDiff? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class BuildStatus {
    QUEUED,
    VALIDATING,
    COMPILING,
    PACKAGING,
    SUCCESS,
    FAILED
}

data class BuildJob(
    val id: String,
    val projectId: String,
    val projectName: String,
    val buildType: String = "DEBUG_APK",
    val status: BuildStatus = BuildStatus.QUEUED,
    val logs: List<String> = emptyList(),
    val artifactName: String? = null,
    val artifactSizeBytes: Long? = null,
    val artifactSha256: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val errorReason: String? = null,
    val progressPercent: Int = 0
)

data class GitHubConfig(
    val username: String = "developer-mobile",
    val token: String = "",
    val isConnected: Boolean = true,
    val currentRepo: String? = "morluto-rea/sample-reconstructed-app",
    val currentBranch: String = "main"
)

data class Project(
    val id: String,
    val name: String,
    val packageName: String,
    val targetApkName: String?,
    val analysisResult: ApkAnalysisResult?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val gitRepoUrl: String? = null,
    val gitBranch: String = "main",
    val files: List<DecompiledFile> = emptyList(),
    val reconstructionReport: ReconstructionReport? = null,
    val buildHistory: List<BuildJob> = emptyList()
)

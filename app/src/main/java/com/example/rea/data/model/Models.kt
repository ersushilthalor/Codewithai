package com.example.rea.data.model

data class ReaEngineState(
    val status: EngineStatus = EngineStatus.DISCONNECTED,
    val version: String = "v1.4.2-core",
    val cloudWorkerEndpoint: String = "",
    val cloudWorkerToken: String = "",
    val mcpConnected: Boolean = false,
    val nodeVersion: String = "",
    val jdkVersion: String = "",
    val jadxVersion: String = "",
    val apktoolVersion: String = "",
    val logs: List<String> = emptyList(),
    val progressPercent: Int = 0,
    val currentStep: String = "Idle",
    val activeTools: List<String> = emptyList()
)

enum class EngineStatus {
    DISCONNECTED,
    CONNECTING,
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
    val availableModels: List<String> = emptyList(),
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

data class ProjectFile(
    val id: String,
    val path: String,
    val name: String,
    val language: String,
    val content: String,
    val isModified: Boolean = false
)

typealias DecompiledFile = ProjectFile

data class SecurityFinding(
    val title: String,
    val severity: String, // "HIGH", "MEDIUM", "LOW", "INFO"
    val reason: String
)

data class ApkAnalysisResult(
    val id: String,
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val minSdk: Int,
    val targetSdk: Int,
    val compileSdk: Int = 0,
    val apkSizeBytes: Long,
    val sha256: String,
    val signatureScheme: String = "v1 only",
    val signingStatus: String = "Signature present (certificate parsed, chain not verified)",
    val signerCertSha256: String? = null,
    val permissions: List<ApkPermission> = emptyList(),
    val activities: List<ApkComponent> = emptyList(),
    val services: List<ApkComponent> = emptyList(),
    val receivers: List<ApkComponent> = emptyList(),
    val providers: List<ApkComponent> = emptyList(),
    val dexClassesCount: Int = 0,
    val dexMethodsCount: Int = 0,
    val classDescriptors: List<String> = emptyList(),
    val detectedFrameworks: List<String> = emptyList(),
    val obfuscationLevel: String = "Not obviously obfuscated",
    val architecturePattern: String = "Not determined",
    val rawManifestXml: String = "",
    val debuggable: Boolean = false,
    val allowBackup: Boolean = true,
    val usesCleartextTraffic: Boolean = false,
    val networkSecurityConfig: String? = null,
    val securityFindings: List<SecurityFinding> = emptyList(),
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

sealed class ApplyDiffResult {
    data class Applied(val filePath: String, val previousContent: String) : ApplyDiffResult()
    data class Failed(val reason: String) : ApplyDiffResult()
}

data class ReaSkillAction(
    val skillKey: String,
    val title: String,
    val description: String,
    val status: String = "Completed",
    val isRunning: Boolean = false
)

data class PermissionItemData(
    val permission: String,
    val label: String,
    val description: String,
    val isDangerous: Boolean = false,
    val isGranted: Boolean = true
)

data class ChatPermissionCardData(
    val id: String,
    val title: String = "Required Permissions",
    val description: String = "Permissions detected in target application manifest",
    val allSelected: Boolean = true,
    val permissions: List<PermissionItemData> = emptyList(),
    val isAccepted: Boolean = false
)

data class ApkExportCardData(
    val apkFileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val isSavedToDevice: Boolean = false,
    val targetPackage: String = ""
)

enum class ChatMode(val title: String, val subtitle: String) {
    FULL_REBUILD("Full Rebuild", "Scaffold Architecture & Codebase Rebuild"),
    UI_CLONE("UI Clone", "Extract & Generate Compose UI"),
    SECURITY_AUDIT("Security Audit", "Permissions & Vulnerabilities"),
    FEATURE_INJECT("Feature Injection", "Inject Mods & Custom Logic"),
    QUICK_INSPECT("Quick Inspect", "DEX, SDK & Package Metadata")
}

data class AgentMessage(
    val id: String,
    val sender: MessageSender,
    val agentMode: AgentMode = AgentMode.DEVELOPER,
    val text: String,
    val toolCalls: List<ToolCallInfo> = emptyList(),
    val proposedDiff: CodeDiff? = null,
    val isDiffApplied: Boolean = false,
    val previousContentForUndo: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val selectedApkName: String? = null,
    val selectedApkSize: Long? = null,
    val selectedImageUri: String? = null,
    val skillsUsed: List<ReaSkillAction> = emptyList(),
    val permissionCard: ChatPermissionCardData? = null,
    val apkExportCard: ApkExportCardData? = null
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
    val username: String = "",
    val token: String = "",
    val isConnected: Boolean = false,
    val currentRepo: String? = null,
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

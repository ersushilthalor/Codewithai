package com.example.rea.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.rea.data.model.*
import com.example.rea.data.repository.ProjectRepository
import com.example.rea.data.service.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

enum class ScreenDestination {
    DASHBOARD,
    PROJECTS,
    REVERSE_ENGINEER,
    RECONSTRUCTION,
    AI_AGENT,
    CODE_EDITOR,
    CLOUD_BUILDER,
    SETTINGS
}

data class AnalysisUiProgress(
    val isAnalyzing: Boolean = false,
    val currentStepIndex: Int = 0,
    val currentStepName: String = "",
    val latestLog: String = "",
    val logs: List<String> = emptyList()
)

data class ReconstructionUiProgress(
    val isReconstructing: Boolean = false,
    val currentStep: Int = 0,
    val currentMessage: String = "",
    val completionPercent: Int = 0
)

class ReaViewModel(application: Application) : AndroidViewModel(application) {

    val engineManager = ReaEngineManager()
    val providerManager = AiProviderManager(application.applicationContext)
    private val analysisService = ApkAnalysisService(application.applicationContext)
    val reconstructionService = ReconstructionService(providerManager)
    val agentService = AgentService(providerManager, application.applicationContext)
    val builderService = CloudBuilderService(application.applicationContext)
    val projectRepository = ProjectRepository(application.applicationContext)
    val gitHubService = GitHubService()

    // Navigation
    private val _currentScreen = MutableStateFlow(ScreenDestination.DASHBOARD)
    val currentScreen: StateFlow<ScreenDestination> = _currentScreen.asStateFlow()

    // Analysis State
    private val _currentAnalysis = MutableStateFlow<ApkAnalysisResult?>(null)
    val currentAnalysis: StateFlow<ApkAnalysisResult?> = _currentAnalysis.asStateFlow()

    private val _analysisProgress = MutableStateFlow(AnalysisUiProgress())
    val analysisProgress: StateFlow<AnalysisUiProgress> = _analysisProgress.asStateFlow()

    // Reconstruction State
    private val _reconstructionProgress = MutableStateFlow(ReconstructionUiProgress())
    val reconstructionProgress: StateFlow<ReconstructionUiProgress> = _reconstructionProgress.asStateFlow()

    // Code Editor Active File State
    private val _selectedFile = MutableStateFlow<DecompiledFile?>(null)
    val selectedFile: StateFlow<DecompiledFile?> = _selectedFile.asStateFlow()

    // GitHub Config
    private val _gitHubConfig = MutableStateFlow(loadInitialGitHubConfig())
    val gitHubConfig: StateFlow<GitHubConfig> = _gitHubConfig.asStateFlow()

    // Agent current mode
    private val _agentMode = MutableStateFlow(AgentMode.ANALYSIS)
    val agentMode: StateFlow<AgentMode> = _agentMode.asStateFlow()

    // Chat Mode & Export State
    private val _chatMode = MutableStateFlow(ChatMode.FULL_REBUILD)
    val chatMode: StateFlow<ChatMode> = _chatMode.asStateFlow()

    private val _pendingExportName = MutableStateFlow<String>("reconstructed-app-bundle.zip")
    val pendingExportName: StateFlow<String> = _pendingExportName.asStateFlow()

    private fun loadInitialGitHubConfig(): GitHubConfig {
        val prefs = getApplication<Application>().getSharedPreferences("rea_github_config", Context.MODE_PRIVATE)
        val encryptedToken = prefs.getString("encrypted_token", "") ?: ""
        val token = KeyStoreCrypto.decrypt(encryptedToken).orEmpty()
        val username = prefs.getString("username", "") ?: ""
        val repo = prefs.getString("repo", null)
        val branch = prefs.getString("branch", "main") ?: "main"
        return GitHubConfig(
            username = username,
            token = token,
            isConnected = token.isNotBlank() && username.isNotBlank(),
            currentRepo = repo,
            currentBranch = branch
        )
    }

    private fun persistGitHubConfig(config: GitHubConfig) {
        val prefs = getApplication<Application>().getSharedPreferences("rea_github_config", Context.MODE_PRIVATE)
        val encToken = KeyStoreCrypto.encrypt(config.token)
        prefs.edit()
            .putString("encrypted_token", encToken)
            .putString("username", config.username)
            .putString("repo", config.currentRepo)
            .putString("branch", config.currentBranch)
            .apply()
    }

    init {
        val existing = projectRepository.getCurrentProject()
        if (existing != null && existing.files.isNotEmpty()) {
            _selectedFile.value = existing.files.first()
        }
    }

    fun navigateTo(screen: ScreenDestination) {
        _currentScreen.value = screen
    }

    fun setAgentMode(mode: AgentMode) {
        _agentMode.value = mode
    }

    fun setChatMode(mode: ChatMode) {
        _chatMode.value = mode
    }

    fun selectFile(file: DecompiledFile) {
        _selectedFile.value = file
    }

    fun saveCurrentFileContent(newContent: String) {
        val currentProj = projectRepository.getCurrentProject() ?: return
        val currentFile = _selectedFile.value ?: return
        projectRepository.saveFileContent(currentProj.id, currentFile.path, newContent)
        _selectedFile.value = currentFile.copy(content = newContent, isModified = true)
        Toast.makeText(getApplication(), "Saved ${currentFile.name}", Toast.LENGTH_SHORT).show()
    }

    fun applyCodeDiff(diff: CodeDiff, messageId: String? = null) {
        val currentProj = projectRepository.getCurrentProject() ?: return
        val result = projectRepository.applyCodeDiff(
            projectId = currentProj.id,
            filePath = diff.filePath,
            originalSnippet = diff.originalSnippet,
            replacementSnippet = diff.replacementSnippet
        )
        when (result) {
            is ApplyDiffResult.Applied -> {
                Toast.makeText(getApplication(), "Diff applied to ${result.filePath}", Toast.LENGTH_SHORT).show()
                if (messageId != null) {
                    agentService.updateMessage(messageId) {
                        it.copy(isDiffApplied = true, previousContentForUndo = result.previousContent)
                    }
                }
                val updated = projectRepository.getCurrentProject()
                val file = updated?.files?.firstOrNull { it.path == diff.filePath }
                if (file != null) {
                    _selectedFile.value = file
                }
            }
            is ApplyDiffResult.Failed -> {
                Toast.makeText(getApplication(), "Diff failed: ${result.reason}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun undoCodeDiff(messageId: String) {
        val msg = agentService.messages.value.firstOrNull { it.id == messageId } ?: return
        val diff = msg.proposedDiff ?: return
        val prevContent = msg.previousContentForUndo ?: return
        val currentProj = projectRepository.getCurrentProject() ?: return

        val result = projectRepository.applyCodeDiff(
            projectId = currentProj.id,
            filePath = diff.filePath,
            originalSnippet = diff.replacementSnippet,
            replacementSnippet = prevContent
        )
        when (result) {
            is ApplyDiffResult.Applied -> {
                Toast.makeText(getApplication(), "Undone changes to ${result.filePath}", Toast.LENGTH_SHORT).show()
                agentService.updateMessage(messageId) {
                    it.copy(isDiffApplied = false, previousContentForUndo = null)
                }
                val updated = projectRepository.getCurrentProject()
                val file = updated?.files?.firstOrNull { it.path == diff.filePath }
                if (file != null) {
                    _selectedFile.value = file
                }
            }
            is ApplyDiffResult.Failed -> {
                Toast.makeText(getApplication(), "Undo failed: ${result.reason}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun runSelfAnalysis() {
        viewModelScope.launch {
            _analysisProgress.value = AnalysisUiProgress(isAnalyzing = true)
            val logs = mutableListOf<String>()

            try {
                val result = analysisService.analyzeSelfApp { step, name, log ->
                    logs.add("[$name] $log")
                    _analysisProgress.value = AnalysisUiProgress(
                        isAnalyzing = true,
                        currentStepIndex = step,
                        currentStepName = name,
                        latestLog = log,
                        logs = logs.toList()
                    )
                }

                _currentAnalysis.value = result
                _analysisProgress.value = AnalysisUiProgress(isAnalyzing = false)

                val proj = projectRepository.createProject(result.appName, result.packageName, "self_installed.apk", result)
                if (proj.files.isNotEmpty()) {
                    _selectedFile.value = proj.files.first()
                }
            } catch (e: Exception) {
                _analysisProgress.value = AnalysisUiProgress(isAnalyzing = false)
                Toast.makeText(getApplication(), "Self analysis failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun runAnalysisOnUri(uri: Uri) {
        viewModelScope.launch {
            _analysisProgress.value = AnalysisUiProgress(isAnalyzing = true)
            val logs = mutableListOf<String>()

            try {
                val result = analysisService.analyzeApkUri(uri) { step, name, log ->
                    logs.add("[$name] $log")
                    _analysisProgress.value = AnalysisUiProgress(
                        isAnalyzing = true,
                        currentStepIndex = step,
                        currentStepName = name,
                        latestLog = log,
                        logs = logs.toList()
                    )
                }

                _currentAnalysis.value = result
                _analysisProgress.value = AnalysisUiProgress(isAnalyzing = false)

                val proj = projectRepository.createProject(result.appName, result.packageName, "uploaded_target.apk", result)
                if (proj.files.isNotEmpty()) {
                    _selectedFile.value = proj.files.first()
                }
            } catch (e: Exception) {
                _analysisProgress.value = AnalysisUiProgress(isAnalyzing = false)
                Toast.makeText(getApplication(), "Analysis error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun runReconstruction(config: ReconstructionConfig) {
        val analysis = _currentAnalysis.value ?: return
        viewModelScope.launch {
            _reconstructionProgress.value = ReconstructionUiProgress(isReconstructing = true, currentStep = 1)

            val (files, report) = reconstructionService.reconstructApp(analysis, config) { step, msg ->
                _reconstructionProgress.value = ReconstructionUiProgress(
                    isReconstructing = true,
                    currentStep = step,
                    currentMessage = msg,
                    completionPercent = (step * 25).coerceAtMost(99)
                )
            }

            _reconstructionProgress.value = ReconstructionUiProgress(isReconstructing = false, completionPercent = 100)

            val curProj = projectRepository.getCurrentProject()
            if (curProj != null) {
                projectRepository.updateProjectFiles(curProj.id, files, report)
            }

            if (files.isNotEmpty()) {
                _selectedFile.value = files.first()
            }

            _currentScreen.value = ScreenDestination.RECONSTRUCTION
        }
    }

    fun sendAgentPrompt(prompt: String) {
        if (prompt.isBlank()) return
        val proj = projectRepository.getCurrentProject()
        val provider = providerManager.getDefaultProvider()
        val mode = _agentMode.value

        viewModelScope.launch {
            agentService.sendMessage(prompt, mode, proj, provider)
        }
    }

    fun sendChatPrompt(prompt: String) {
        if (prompt.isBlank()) return
        val proj = projectRepository.getCurrentProject()
        val provider = providerManager.getDefaultProvider()
        val mode = _chatMode.value

        viewModelScope.launch {
            agentService.sendMessageWithChatMode(prompt, mode, proj, provider)
        }
    }

    fun triggerGitHubBuild(owner: String, repo: String, branch: String = "main") {
        val proj = projectRepository.getCurrentProject() ?: return
        val token = _gitHubConfig.value.token
        viewModelScope.launch {
            _currentScreen.value = ScreenDestination.CLOUD_BUILDER
            builderService.startGitHubActionsBuild(
                project = proj,
                githubToken = token,
                owner = owner,
                repo = repo,
                branch = branch
            ) { _, _ -> }
        }
    }

    fun selectApkFromStorage(uri: Uri, fileName: String, fileSize: Long) {
        viewModelScope.launch {
            val userMsg = AgentMessage(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.USER,
                text = "Selected APK from storage: $fileName",
                selectedApkName = fileName,
                selectedApkSize = fileSize
            )
            agentService.addMessage(userMsg)

            try {
                val result = analysisService.analyzeApkUri(uri) { _, _, _ -> }
                _currentAnalysis.value = result

                val proj = projectRepository.createProject(result.appName, result.packageName, fileName, result)
                if (proj.files.isNotEmpty()) {
                    _selectedFile.value = proj.files.first()
                }

                val permItems = result.permissions.map { perm ->
                    PermissionItemData(
                        permission = perm.name,
                        label = perm.name.substringAfterLast('.'),
                        description = perm.description,
                        isDangerous = perm.isDangerous,
                        isGranted = true
                    )
                }

                val permCard = ChatPermissionCardData(
                    id = "perm_" + UUID.randomUUID().toString().take(6),
                    title = "Required App Permissions",
                    description = "Detected in binary AndroidManifest.xml of $fileName. Toggle 'Select All' to grant permissions.",
                    allSelected = true,
                    permissions = permItems,
                    isAccepted = false
                )

                val skills = listOf(
                    ReaSkillAction("rea_inspect_apk", "Package Analysis", "Extracted package: ${result.packageName}"),
                    ReaSkillAction("rea_manifest_extract", "Manifest Extraction", "Identified ${result.activities.size} Activities & ${permItems.size} Permissions"),
                    ReaSkillAction("rea_dex_audit", "DEX Inspection", "${result.dexClassesCount} classes detected in MultiDex container")
                )

                val agentMsg = AgentMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.AGENT,
                    text = "✅ **APK Inspected Successfully!**\n\nAnalyzed **$fileName** on-device.\n• Package: `${result.packageName}`\n• Activities: ${result.activities.size}\n• Permissions: ${result.permissions.size}\n\nPlease review the permissions below and tap **Accept & Rebuild APK**.",
                    skillsUsed = skills,
                    permissionCard = permCard
                )
                agentService.addMessage(agentMsg)
            } catch (e: Exception) {
                val agentMsg = AgentMessage(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.AGENT,
                    text = "⚠️ **Failed to Inspect APK**\n\nError: ${e.localizedMessage ?: "Invalid APK format"}"
                )
                agentService.addMessage(agentMsg)
            }
        }
    }

    fun toggleAllPermissions(messageId: String, selectAll: Boolean) {
        agentService.updateMessage(messageId) { msg ->
            val card = msg.permissionCard ?: return@updateMessage msg
            val updatedPerms = card.permissions.map { it.copy(isGranted = selectAll) }
            msg.copy(permissionCard = card.copy(allSelected = selectAll, permissions = updatedPerms))
        }
    }

    fun toggleSinglePermission(messageId: String, permName: String, isGranted: Boolean) {
        agentService.updateMessage(messageId) { msg ->
            val card = msg.permissionCard ?: return@updateMessage msg
            val updatedPerms = card.permissions.map {
                if (it.permission == permName) it.copy(isGranted = isGranted) else it
            }
            val allOn = updatedPerms.all { it.isGranted }
            msg.copy(permissionCard = card.copy(allSelected = allOn, permissions = updatedPerms))
        }
    }

    fun confirmPermissionsAndBuild(messageId: String) {
        agentService.updateMessage(messageId) { msg ->
            val card = msg.permissionCard ?: return@updateMessage msg
            msg.copy(permissionCard = card.copy(isAccepted = true))
        }

        viewModelScope.launch {
            val proj = projectRepository.getCurrentProject()
            val analysis = _currentAnalysis.value
            val appName = proj?.name ?: "ScaffoldApp"

            val workingMsg = AgentMessage(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.AGENT,
                text = "🔨 **Synthesizing Clean-Room Architecture...**\nPermissions confirmed. Generating Jetpack Compose screens matching recovered components.",
                skillsUsed = listOf(
                    ReaSkillAction("rea_architecture_map", "Compose Synthesizer", "Generating Material 3 composables"),
                    ReaSkillAction("rea_gradle_config", "Gradle Configuration", "Target SDK compatibility")
                )
            )
            agentService.addMessage(workingMsg)

            if (analysis != null) {
                val (files, report) = reconstructionService.reconstructApp(
                    analysis,
                    ReconstructionConfig()
                ) { _, _ -> }
                if (proj != null) {
                    projectRepository.updateProjectFiles(proj.id, files, report)
                    if (files.isNotEmpty()) _selectedFile.value = files.first()
                }
            }

            val currentProject = projectRepository.getCurrentProject()
            val (archiveBytes, sha256) = if (currentProject != null) {
                builderService.createProjectArchive(currentProject)
            } else {
                ByteArray(0) to ""
            }

            val exportFileName = "${appName.replace("[^a-zA-Z0-9]".toRegex(), "")}-scaffold-bundle.zip"
            _pendingExportName.value = exportFileName

            val exportCard = ApkExportCardData(
                apkFileName = exportFileName,
                sizeBytes = archiveBytes.size.toLong(),
                sha256 = sha256,
                isSavedToDevice = false,
                targetPackage = proj?.packageName ?: "com.workspace.app"
            )

            val finishedMsg = AgentMessage(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.AGENT,
                text = "🎉 **Generated Scaffold Bundle Ready!**\n\nClean-room Jetpack Compose project generated (${currentProject?.files?.size ?: 0} source files). Tap **Save project ZIP** below to export the valid source archive to your phone.",
                apkExportCard = exportCard
            )
            agentService.addMessage(finishedMsg)
        }
    }

    /**
     * Multimodal vision: real image downsampling and Base64 conversion on Dispatchers.IO (C6).
     */
    fun selectImage(uri: Uri) {
        viewModelScope.launch {
            val userMsg = AgentMessage(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.USER,
                text = "Attached UI screenshot for reconstruction",
                selectedImageUri = uri.toString()
            )
            agentService.addMessage(userMsg)

            val context = getApplication<Application>().applicationContext
            val base64Image = withContext(Dispatchers.IO) {
                try {
                    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, boundsOptions)
                    }
                    val maxDim = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
                    var sampleSize = 1
                    while (maxDim / sampleSize > 1536) {
                        sampleSize *= 2
                    }

                    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    val bitmap = context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, decodeOptions)
                    } ?: return@withContext null

                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                    Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                } catch (_: Exception) {
                    null
                }
            }

            if (base64Image == null) {
                agentService.addMessage(
                    AgentMessage(
                        id = UUID.randomUUID().toString(),
                        sender = MessageSender.AGENT,
                        text = "⚠️ **Failed to process image**\nCould not open or decode the image from device storage."
                    )
                )
                return@launch
            }

            val provider = providerManager.getDefaultProvider()
            val prompt = "A UI screenshot was attached for Android reconstruction. Please generate Jetpack Compose Material 3 composables matching the visual design tokens."
            agentService.sendMessageWithChatMode(
                userPrompt = prompt,
                mode = ChatMode.UI_CLONE,
                currentProject = projectRepository.getCurrentProject(),
                provider = provider,
                imageBase64 = base64Image
            )
        }
    }

    /**
     * Exports real APK if exists in artifacts directory, else exports project ZIP archive (F3).
     */
    fun saveFinalApkToStorage(context: Context, destUri: Uri) {
        val proj = projectRepository.getCurrentProject()
        if (proj == null) {
            Toast.makeText(context, "No active project to export", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val realApk = builderService.getExistingArtifactApk(proj.name)
            if (realApk != null) {
                val bytes = realApk.readBytes()
                if (bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
                    context.contentResolver.openOutputStream(destUri)?.use { out ->
                        out.write(bytes)
                        out.flush()
                    }
                    updateExportCardStatus()
                    Toast.makeText(context, "✅ APK (${bytes.size / 1024} KB) saved successfully!", Toast.LENGTH_LONG).show()
                    return
                }
            }

            // Fallback to project ZIP
            val (archiveBytes, _) = builderService.createProjectArchive(proj)
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                out.write(archiveBytes)
                out.flush()
            }
            updateExportCardStatus()
            Toast.makeText(context, "✅ Project ZIP archive (${archiveBytes.size / 1024} KB) saved successfully!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateExportCardStatus() {
        agentService.messages.value.forEach { msg ->
            if (msg.apkExportCard != null && !msg.apkExportCard.isSavedToDevice) {
                agentService.updateMessage(msg.id) {
                    it.copy(apkExportCard = it.apkExportCard?.copy(isSavedToDevice = true))
                }
            }
        }
    }

    fun updateGitHubConfig(token: String, user: String, repo: String?) {
        val updated = _gitHubConfig.value.copy(
            token = token,
            username = user,
            currentRepo = repo,
            isConnected = token.isNotBlank() && user.isNotBlank()
        )
        _gitHubConfig.value = updated
        persistGitHubConfig(updated)
    }

    suspend fun validateAndSaveGitHubToken(token: String): Pair<Boolean, String> {
        val (ok, result) = gitHubService.validateToken(token.trim())
        if (ok) {
            updateGitHubConfig(token.trim(), result, _gitHubConfig.value.currentRepo)
        }
        return ok to result
    }

    suspend fun pushProjectToGitHub(repoName: String, branch: String = "main"): Pair<Boolean, String> {
        val proj = projectRepository.getCurrentProject() ?: return false to "No active project"
        val token = _gitHubConfig.value.token
        val user = _gitHubConfig.value.username

        if (token.isBlank() || user.isBlank()) {
            return false to "GitHub token not configured or validated"
        }

        // Create or ensure repo exists
        val createResult = gitHubService.createRepo(token, repoName)
        if (createResult.isFailure) {
            return false to (createResult.exceptionOrNull()?.message ?: "Failed to create repo")
        }

        val pushResult = gitHubService.pushFiles(token, user, repoName, proj.files, branch) { _, _, _ -> }
        if (pushResult.isFailure) {
            return false to (pushResult.exceptionOrNull()?.message ?: "Failed to push files")
        }

        updateGitHubConfig(token, user, "$user/$repoName")
        return true to "Successfully pushed ${proj.files.size} files to $user/$repoName"
    }
}

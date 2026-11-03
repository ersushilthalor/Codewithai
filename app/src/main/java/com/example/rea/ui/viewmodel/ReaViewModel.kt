package com.example.rea.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.rea.data.model.*
import com.example.rea.data.repository.ProjectRepository
import com.example.rea.data.service.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
    val providerManager = AiProviderManager()
    private val analysisService = ApkAnalysisService(application.applicationContext)
    val reconstructionService = ReconstructionService()
    val agentService = AgentService()
    val builderService = CloudBuilderService()
    val projectRepository = ProjectRepository()

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
    private val _gitHubConfig = MutableStateFlow(GitHubConfig())
    val gitHubConfig: StateFlow<GitHubConfig> = _gitHubConfig.asStateFlow()

    // Agent current mode
    private val _agentMode = MutableStateFlow(AgentMode.ANALYSIS)
    val agentMode: StateFlow<AgentMode> = _agentMode.asStateFlow()

    init {
        viewModelScope.launch {
            // Pre-load default sample analysis
            val sample = analysisService.analyzeSampleTarget("auracam") { _, _, _ -> }
            _currentAnalysis.value = sample
            projectRepository.initializeWithDefaults(sample)
            _selectedFile.value = sample.decompiledClasses.firstOrNull()
        }
    }

    fun navigateTo(screen: ScreenDestination) {
        _currentScreen.value = screen
    }

    fun setAgentMode(mode: AgentMode) {
        _agentMode.value = mode
    }

    fun selectFile(file: DecompiledFile) {
        _selectedFile.value = file
    }

    fun saveCurrentFileContent(newContent: String) {
        val currentProj = projectRepository.getCurrentProject() ?: return
        val currentFile = _selectedFile.value ?: return
        projectRepository.saveFileContent(currentProj.id, currentFile.id, newContent)
        _selectedFile.value = currentFile.copy(content = newContent, isModified = true)
    }

    fun runAnalysisOnSample(sampleKey: String) {
        viewModelScope.launch {
            _analysisProgress.value = AnalysisUiProgress(isAnalyzing = true)
            val logs = mutableListOf<String>()

            val result = analysisService.analyzeSampleTarget(sampleKey) { step, name, log ->
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

            // Update or create project
            val existing = projectRepository.getCurrentProject()
            if (existing != null) {
                projectRepository.updateProjectAnalysis(existing.id, result)
            } else {
                projectRepository.createProject(result.appName, result.packageName, result.appName + ".apk", result)
            }

            if (result.decompiledClasses.isNotEmpty()) {
                _selectedFile.value = result.decompiledClasses.first()
            }
        }
    }

    fun runAnalysisOnUri(uri: Uri) {
        viewModelScope.launch {
            _analysisProgress.value = AnalysisUiProgress(isAnalyzing = true)
            val logs = mutableListOf<String>()

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
                    completionPercent = (step * 12.5f).toInt()
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

    fun triggerCloudBuild(buildType: String = "DEBUG_APK") {
        val proj = projectRepository.getCurrentProject() ?: return
        viewModelScope.launch {
            _currentScreen.value = ScreenDestination.CLOUD_BUILDER
            builderService.startBuild(
                projectId = proj.id,
                projectName = proj.name,
                buildType = buildType,
                appId = proj.packageName,
                versionCode = 1,
                versionName = "1.0.0"
            ) { _, _ -> }
        }
    }

    fun updateGitHubToken(token: String, user: String) {
        _gitHubConfig.value = _gitHubConfig.value.copy(
            token = token,
            username = user,
            isConnected = token.isNotBlank()
        )
    }
}

package com.example.rea.data.repository

import com.example.rea.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class ProjectRepository {
    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _currentProjectId = MutableStateFlow<String?>(null)
    val currentProjectId: StateFlow<String?> = _currentProjectId.asStateFlow()

    fun initializeWithDefaults(sampleAnalysis: ApkAnalysisResult) {
        val defaultProject = Project(
            id = "proj_auracam_default",
            name = "AuraCam Pro Workspace",
            packageName = "com.auravision.camera.pro",
            targetApkName = "auracam-pro-v3.4.apk",
            analysisResult = sampleAnalysis,
            files = sampleAnalysis.decompiledClasses,
            gitRepoUrl = "https://github.com/morluto-rea/auracam-reconstruction",
            gitBranch = "main"
        )

        _projects.value = listOf(defaultProject)
        _currentProjectId.value = defaultProject.id
    }

    fun getCurrentProject(): Project? {
        val id = _currentProjectId.value ?: return _projects.value.firstOrNull()
        return _projects.value.firstOrNull { it.id == id }
    }

    fun selectProject(id: String) {
        _currentProjectId.value = id
    }

    fun createProject(
        name: String,
        packageName: String,
        targetApkName: String?,
        analysis: ApkAnalysisResult? = null
    ): Project {
        val newProj = Project(
            id = "proj_" + UUID.randomUUID().toString().take(8),
            name = name,
            packageName = packageName,
            targetApkName = targetApkName,
            analysisResult = analysis,
            files = analysis?.decompiledClasses ?: emptyList()
        )
        _projects.value = listOf(newProj) + _projects.value
        _currentProjectId.value = newProj.id
        return newProj
    }

    fun updateProjectAnalysis(projectId: String, analysis: ApkAnalysisResult) {
        _projects.value = _projects.value.map {
            if (it.id == projectId) {
                it.copy(
                    analysisResult = analysis,
                    files = if (it.files.isEmpty()) analysis.decompiledClasses else it.files,
                    updatedAt = System.currentTimeMillis()
                )
            } else it
        }
    }

    fun updateProjectFiles(projectId: String, files: List<DecompiledFile>, report: ReconstructionReport? = null) {
        _projects.value = _projects.value.map {
            if (it.id == projectId) {
                it.copy(
                    files = files,
                    reconstructionReport = report ?: it.reconstructionReport,
                    updatedAt = System.currentTimeMillis()
                )
            } else it
        }
    }

    fun saveFileContent(projectId: String, fileId: String, newContent: String) {
        _projects.value = _projects.value.map { p ->
            if (p.id == projectId) {
                val updatedFiles = p.files.map { f ->
                    if (f.id == fileId) f.copy(content = newContent, isModified = true) else f
                }
                p.copy(files = updatedFiles, updatedAt = System.currentTimeMillis())
            } else p
        }
    }

    fun addFileToProject(projectId: String, fileName: String, filePath: String, content: String, language: String) {
        val newFile = DecompiledFile(
            id = "f_" + UUID.randomUUID().toString().take(8),
            path = filePath,
            name = fileName,
            language = language,
            content = content,
            isModified = true
        )
        _projects.value = _projects.value.map { p ->
            if (p.id == projectId) {
                p.copy(files = p.files + newFile, updatedAt = System.currentTimeMillis())
            } else p
        }
    }

    fun deleteFileFromProject(projectId: String, fileId: String) {
        _projects.value = _projects.value.map { p ->
            if (p.id == projectId) {
                p.copy(files = p.files.filter { it.id != fileId }, updatedAt = System.currentTimeMillis())
            } else p
        }
    }
}

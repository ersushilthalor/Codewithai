package com.example.rea.data.repository

import android.content.Context
import com.example.rea.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ProjectRepository(private val context: Context) {

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _currentProjectId = MutableStateFlow<String?>(null)
    val currentProjectId: StateFlow<String?> = _currentProjectId.asStateFlow()

    private val projectsDir: File
        get() = File(context.filesDir, "projects").apply { if (!exists()) mkdirs() }

    init {
        loadPersistedProjects()
    }

    private fun loadPersistedProjects() {
        val loaded = mutableListOf<Project>()
        try {
            val dirs = projectsDir.listFiles { f -> f.isDirectory } ?: emptyArray()
            for (dir in dirs) {
                val projId = dir.name
                val metaFile = File(dir, "project_meta.json")
                val files = mutableListOf<DecompiledFile>()
                dir.walkTopDown().forEach { f ->
                    if (f.isFile && f.name != "project_meta.json") {
                        val relPath = f.relativeTo(dir).path
                        val lang = when {
                            f.name.endsWith(".kt") -> "kotlin"
                            f.name.endsWith(".xml") -> "xml"
                            f.name.endsWith(".gradle.kts") || f.name.endsWith(".gradle") -> "gradle"
                            else -> "text"
                        }
                        files.add(
                            DecompiledFile(
                                id = "f_" + f.name.hashCode().toString(),
                                path = relPath,
                                name = f.name,
                                language = lang,
                                content = f.readText()
                            )
                        )
                    }
                }
                if (files.isNotEmpty()) {
                    loaded.add(
                        Project(
                            id = projId,
                            name = dir.name.replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                            packageName = "com.workspace." + dir.name.lowercase(),
                            targetApkName = "${dir.name}.apk",
                            analysisResult = null,
                            files = files
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        if (loaded.isNotEmpty()) {
            _projects.value = loaded
            _currentProjectId.value = loaded.first().id
        }
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
        val projId = "proj_" + UUID.randomUUID().toString().take(8)
        val initialFiles = analysis?.decompiledClasses ?: emptyList()

        val newProj = Project(
            id = projId,
            name = name,
            packageName = packageName,
            targetApkName = targetApkName,
            analysisResult = analysis,
            files = initialFiles
        )

        // Persist files to disk
        persistProjectFiles(newProj)

        _projects.value = listOf(newProj) + _projects.value
        _currentProjectId.value = newProj.id
        return newProj
    }

    fun updateProjectAnalysis(projectId: String, analysis: ApkAnalysisResult) {
        _projects.value = _projects.value.map {
            if (it.id == projectId) {
                val updatedFiles = if (it.files.isEmpty()) analysis.decompiledClasses else it.files
                val updated = it.copy(
                    analysisResult = analysis,
                    files = updatedFiles,
                    updatedAt = System.currentTimeMillis()
                )
                persistProjectFiles(updated)
                updated
            } else it
        }
    }

    fun updateProjectFiles(projectId: String, files: List<DecompiledFile>, report: ReconstructionReport? = null) {
        _projects.value = _projects.value.map {
            if (it.id == projectId) {
                val updated = it.copy(
                    files = files,
                    reconstructionReport = report ?: it.reconstructionReport,
                    updatedAt = System.currentTimeMillis()
                )
                persistProjectFiles(updated)
                updated
            } else it
        }
    }

    fun saveFileContent(projectId: String, fileId: String, newContent: String) {
        _projects.value = _projects.value.map { p ->
            if (p.id == projectId) {
                val updatedFiles = p.files.map { f ->
                    if (f.id == fileId) {
                        val updated = f.copy(content = newContent, isModified = true)
                        // Write to disk
                        persistSingleFile(projectId, updated)
                        updated
                    } else f
                }
                p.copy(files = updatedFiles, updatedAt = System.currentTimeMillis())
            } else p
        }
    }

    fun applyCodeDiff(projectId: String, filePath: String, originalSnippet: String, replacementSnippet: String): Boolean {
        var applied = false
        _projects.value = _projects.value.map { p ->
            if (p.id == projectId) {
                val updatedFiles = p.files.map { f ->
                    if (f.path.endsWith(filePath) || filePath.endsWith(f.name)) {
                        val newContent = if (f.content.contains(originalSnippet)) {
                            f.content.replace(originalSnippet, replacementSnippet)
                        } else {
                            // If original snippet isn't exact match, append or replace
                            f.content + "\n\n" + replacementSnippet
                        }
                        applied = true
                        val updated = f.copy(content = newContent, isModified = true)
                        persistSingleFile(projectId, updated)
                        updated
                    } else f
                }
                p.copy(files = updatedFiles, updatedAt = System.currentTimeMillis())
            } else p
        }
        return applied
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
        persistSingleFile(projectId, newFile)
        _projects.value = _projects.value.map { p ->
            if (p.id == projectId) {
                p.copy(files = p.files + newFile, updatedAt = System.currentTimeMillis())
            } else p
        }
    }

    fun deleteFileFromProject(projectId: String, fileId: String) {
        _projects.value = _projects.value.map { p ->
            if (p.id == projectId) {
                val toDelete = p.files.firstOrNull { it.id == fileId }
                if (toDelete != null) {
                    val diskFile = File(File(projectsDir, projectId), toDelete.path)
                    if (diskFile.exists()) diskFile.delete()
                }
                p.copy(files = p.files.filter { it.id != fileId }, updatedAt = System.currentTimeMillis())
            } else p
        }
    }

    private fun persistProjectFiles(project: Project) {
        try {
            val projDir = File(projectsDir, project.id)
            if (!projDir.exists()) projDir.mkdirs()
            project.files.forEach { f ->
                val diskFile = File(projDir, f.path)
                diskFile.parentFile?.mkdirs()
                diskFile.writeText(f.content)
            }
        } catch (_: Exception) {}
    }

    private fun persistSingleFile(projectId: String, file: DecompiledFile) {
        try {
            val projDir = File(projectsDir, projectId)
            val diskFile = File(projDir, file.path)
            diskFile.parentFile?.mkdirs()
            diskFile.writeText(file.content)
        } catch (_: Exception) {}
    }
}

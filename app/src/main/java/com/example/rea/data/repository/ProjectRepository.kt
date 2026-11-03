package com.example.rea.data.repository

import android.content.Context
import com.example.rea.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
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

                var metaName = dir.name.replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                var metaPkg = "com.workspace." + dir.name.lowercase()
                var metaTargetApk: String? = "${dir.name}.apk"
                var metaCreatedAt = System.currentTimeMillis()
                var metaUpdatedAt = System.currentTimeMillis()
                var metaGitRepo: String? = null
                var metaGitBranch = "main"
                var analysisResult: ApkAnalysisResult? = null

                if (metaFile.exists()) {
                    try {
                        val json = JSONObject(metaFile.readText())
                        metaName = json.optString("name", metaName)
                        metaPkg = json.optString("packageName", metaPkg)
                        metaTargetApk = if (json.has("targetApkName") && !json.isNull("targetApkName")) json.getString("targetApkName") else null
                        metaCreatedAt = json.optLong("createdAt", metaCreatedAt)
                        metaUpdatedAt = json.optLong("updatedAt", metaUpdatedAt)
                        metaGitRepo = if (json.has("gitRepoUrl") && !json.isNull("gitRepoUrl")) json.getString("gitRepoUrl") else null
                        metaGitBranch = json.optString("gitBranch", "main")

                        if (json.has("analysis") && !json.isNull("analysis")) {
                            val aObj = json.getJSONObject("analysis")
                            val perms = mutableListOf<ApkPermission>()
                            val pArr = aObj.optJSONArray("permissions") ?: JSONArray()
                            for (i in 0 until pArr.length()) {
                                val p = pArr.getJSONObject(i)
                                perms.add(
                                    ApkPermission(
                                        name = p.getString("name"),
                                        level = PermissionLevel.valueOf(p.optString("level", "NORMAL")),
                                        description = p.optString("description", ""),
                                        isDangerous = p.optBoolean("isDangerous", false),
                                        riskScore = p.optInt("riskScore", 2)
                                    )
                                )
                            }

                            fun parseComponents(arr: JSONArray?, type: ComponentType): List<ApkComponent> {
                                val list = mutableListOf<ApkComponent>()
                                if (arr == null) return list
                                for (i in 0 until arr.length()) {
                                    val c = arr.getJSONObject(i)
                                    list.add(
                                        ApkComponent(
                                            name = c.getString("name"),
                                            type = type,
                                            isExported = c.optBoolean("isExported", false),
                                            isLauncher = c.optBoolean("isLauncher", false),
                                            permission = if (c.has("permission") && !c.isNull("permission")) c.getString("permission") else null
                                        )
                                    )
                                }
                                return list
                            }

                            val acts = parseComponents(aObj.optJSONArray("activities"), ComponentType.ACTIVITY)
                            val svcs = parseComponents(aObj.optJSONArray("services"), ComponentType.SERVICE)
                            val rcvs = parseComponents(aObj.optJSONArray("receivers"), ComponentType.RECEIVER)
                            val prvs = parseComponents(aObj.optJSONArray("providers"), ComponentType.PROVIDER)

                            fun jsonToStringList(arr: JSONArray?): List<String> {
                                val list = mutableListOf<String>()
                                if (arr == null) return list
                                for (i in 0 until arr.length()) list.add(arr.getString(i))
                                return list
                            }

                            val findings = mutableListOf<SecurityFinding>()
                            val fArr = aObj.optJSONArray("securityFindings")
                            if (fArr != null) {
                                for (i in 0 until fArr.length()) {
                                    val f = fArr.getJSONObject(i)
                                    findings.add(
                                        SecurityFinding(
                                            title = f.getString("title"),
                                            severity = f.getString("severity"),
                                            reason = f.getString("reason")
                                        )
                                    )
                                }
                            }

                            analysisResult = ApkAnalysisResult(
                                id = aObj.optString("id", "apk_$metaCreatedAt"),
                                appName = aObj.optString("appName", metaName),
                                packageName = aObj.optString("packageName", metaPkg),
                                versionName = aObj.optString("versionName", "1.0"),
                                versionCode = aObj.optInt("versionCode", 1),
                                minSdk = aObj.optInt("minSdk", 24),
                                targetSdk = aObj.optInt("targetSdk", 34),
                                compileSdk = aObj.optInt("compileSdk", 0),
                                apkSizeBytes = aObj.optLong("apkSizeBytes", 0L),
                                sha256 = aObj.optString("sha256", ""),
                                signatureScheme = aObj.optString("signatureScheme", "v1 only"),
                                signingStatus = aObj.optString("signingStatus", "Signature present (certificate parsed, chain not verified)"),
                                signerCertSha256 = if (aObj.has("signerCertSha256") && !aObj.isNull("signerCertSha256")) aObj.getString("signerCertSha256") else null,
                                permissions = perms,
                                activities = acts,
                                services = svcs,
                                receivers = rcvs,
                                providers = prvs,
                                dexClassesCount = aObj.optInt("dexClassesCount", 0),
                                dexMethodsCount = aObj.optInt("dexMethodsCount", 0),
                                detectedFrameworks = jsonToStringList(aObj.optJSONArray("detectedFrameworks")),
                                obfuscationLevel = aObj.optString("obfuscationLevel", "Not obviously obfuscated"),
                                architecturePattern = aObj.optString("architecturePattern", "Not determined"),
                                rawManifestXml = aObj.optString("rawManifestXml", ""),
                                debuggable = aObj.optBoolean("debuggable", false),
                                allowBackup = aObj.optBoolean("allowBackup", true),
                                usesCleartextTraffic = aObj.optBoolean("usesCleartextTraffic", false),
                                networkSecurityConfig = if (aObj.has("networkSecurityConfig") && !aObj.isNull("networkSecurityConfig")) aObj.getString("networkSecurityConfig") else null,
                                securityFindings = findings,
                                evidenceList = jsonToStringList(aObj.optJSONArray("evidenceList")),
                                limitationsList = jsonToStringList(aObj.optJSONArray("limitationsList")),
                                timestamp = aObj.optLong("timestamp", metaCreatedAt)
                            )
                        }
                    } catch (_: Exception) {}
                }

                val files = mutableListOf<DecompiledFile>()
                dir.walkTopDown().forEach { f ->
                    if (f.isFile && f.name != "project_meta.json" && !f.name.endsWith(".tmp")) {
                        val relPath = f.relativeTo(dir).path
                        val lang = when {
                            f.name.endsWith(".kt") -> "kotlin"
                            f.name.endsWith(".xml") -> "xml"
                            f.name.endsWith(".gradle.kts") || f.name.endsWith(".gradle") -> "gradle"
                            f.name.endsWith(".toml") -> "toml"
                            f.name.endsWith(".properties") -> "properties"
                            else -> "text"
                        }
                        files.add(
                            DecompiledFile(
                                id = relPath,
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
                            name = metaName,
                            packageName = metaPkg,
                            targetApkName = metaTargetApk,
                            analysisResult = analysisResult,
                            createdAt = metaCreatedAt,
                            updatedAt = metaUpdatedAt,
                            gitRepoUrl = metaGitRepo,
                            gitBranch = metaGitBranch,
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
        val initialFiles = (analysis?.decompiledClasses ?: emptyList()).map {
            it.copy(id = it.path)
        }

        val newProj = Project(
            id = projId,
            name = name,
            packageName = packageName,
            targetApkName = targetApkName,
            analysisResult = analysis,
            files = initialFiles
        )

        persistProjectFiles(newProj)
        saveProjectMeta(newProj)

        _projects.value = listOf(newProj) + _projects.value
        _currentProjectId.value = newProj.id
        return newProj
    }

    fun updateProjectAnalysis(projectId: String, analysis: ApkAnalysisResult) {
        _projects.value = _projects.value.map {
            if (it.id == projectId) {
                val updatedFiles = if (it.files.isEmpty()) {
                    analysis.decompiledClasses.map { f -> f.copy(id = f.path) }
                } else it.files
                val updated = it.copy(
                    analysisResult = analysis,
                    files = updatedFiles,
                    updatedAt = System.currentTimeMillis()
                )
                persistProjectFiles(updated)
                saveProjectMeta(updated)
                updated
            } else it
        }
    }

    fun updateProjectFiles(projectId: String, files: List<DecompiledFile>, report: ReconstructionReport? = null) {
        _projects.value = _projects.value.map {
            if (it.id == projectId) {
                val normalizedFiles = files.map { f -> f.copy(id = f.path) }
                val updated = it.copy(
                    files = normalizedFiles,
                    reconstructionReport = report ?: it.reconstructionReport,
                    updatedAt = System.currentTimeMillis()
                )
                persistProjectFiles(updated)
                saveProjectMeta(updated)
                updated
            } else it
        }
    }

    fun saveFileContent(projectId: String, relativePath: String, newContent: String) {
        _projects.value = _projects.value.map { p ->
            if (p.id == projectId) {
                val updatedFiles = p.files.map { f ->
                    if (f.path == relativePath || f.id == relativePath) {
                        val updated = f.copy(content = newContent, isModified = true)
                        persistSingleFile(projectId, updated)
                        updated
                    } else f
                }
                val updatedProj = p.copy(files = updatedFiles, updatedAt = System.currentTimeMillis())
                saveProjectMeta(updatedProj)
                updatedProj
            } else p
        }
    }

    /**
     * Applies a code diff to a project file.
     * Guaranteed exact match semantics (A3):
     * - Exact path equality (no endsWith, no name match)
     * - If file missing and originalSnippet blank -> creates file
     * - If file missing and originalSnippet non-blank -> returns Failed("file not found")
     * - originalSnippet must occur EXACTLY once; if 0 or > 1 -> returns Failed(reason) and changes nothing
     */
    fun applyCodeDiff(
        projectId: String,
        filePath: String,
        originalSnippet: String,
        replacementSnippet: String
    ): ApplyDiffResult {
        val project = _projects.value.firstOrNull { it.id == projectId }
            ?: return ApplyDiffResult.Failed("Project not found: $projectId")

        val existingFile = project.files.firstOrNull { it.path == filePath }

        if (existingFile == null) {
            if (originalSnippet.isBlank()) {
                val lang = when {
                    filePath.endsWith(".kt") -> "kotlin"
                    filePath.endsWith(".xml") -> "xml"
                    filePath.endsWith(".gradle.kts") || filePath.endsWith(".gradle") -> "gradle"
                    filePath.endsWith(".toml") -> "toml"
                    else -> "text"
                }
                val newFile = DecompiledFile(
                    id = filePath,
                    path = filePath,
                    name = filePath.substringAfterLast('/'),
                    language = lang,
                    content = replacementSnippet,
                    isModified = true
                )
                persistSingleFile(projectId, newFile)
                val updatedProj = project.copy(
                    files = project.files + newFile,
                    updatedAt = System.currentTimeMillis()
                )
                _projects.value = _projects.value.map { if (it.id == projectId) updatedProj else it }
                saveProjectMeta(updatedProj)
                return ApplyDiffResult.Applied(filePath, previousContent = "")
            } else {
                return ApplyDiffResult.Failed("File not found: $filePath")
            }
        }

        val currentContent = existingFile.content
        if (originalSnippet.isBlank()) {
            // Replace entire file content
            val updated = existingFile.copy(content = replacementSnippet, isModified = true)
            persistSingleFile(projectId, updated)
            val updatedFiles = project.files.map { if (it.path == filePath) updated else it }
            val updatedProj = project.copy(files = updatedFiles, updatedAt = System.currentTimeMillis())
            _projects.value = _projects.value.map { if (it.id == projectId) updatedProj else it }
            saveProjectMeta(updatedProj)
            return ApplyDiffResult.Applied(filePath, previousContent = currentContent)
        }

        val occurrences = countOccurrences(currentContent, originalSnippet)
        if (occurrences == 0) {
            return ApplyDiffResult.Failed("Original snippet not found in $filePath")
        }
        if (occurrences > 1) {
            return ApplyDiffResult.Failed("Original snippet is ambiguous (found $occurrences occurrences in $filePath)")
        }

        val newContent = currentContent.replace(originalSnippet, replacementSnippet)
        val updated = existingFile.copy(content = newContent, isModified = true)
        persistSingleFile(projectId, updated)

        val updatedFiles = project.files.map { if (it.path == filePath) updated else it }
        val updatedProj = project.copy(files = updatedFiles, updatedAt = System.currentTimeMillis())
        _projects.value = _projects.value.map { if (it.id == projectId) updatedProj else it }
        saveProjectMeta(updatedProj)

        return ApplyDiffResult.Applied(filePath, previousContent = currentContent)
    }

    private fun countOccurrences(text: String, sub: String): Int {
        if (sub.isEmpty()) return 0
        var count = 0
        var idx = 0
        while (text.indexOf(sub, idx).also { idx = it } != -1) {
            count++
            idx += sub.length
        }
        return count
    }

    fun addFileToProject(projectId: String, fileName: String, filePath: String, content: String, language: String) {
        val normalizedPath = if (filePath.startsWith("/")) filePath.removePrefix("/") else filePath
        val newFile = DecompiledFile(
            id = normalizedPath,
            path = normalizedPath,
            name = fileName,
            language = language,
            content = content,
            isModified = true
        )
        persistSingleFile(projectId, newFile)
        _projects.value = _projects.value.map { p ->
            if (p.id == projectId) {
                val updated = p.copy(files = p.files + newFile, updatedAt = System.currentTimeMillis())
                saveProjectMeta(updated)
                updated
            } else p
        }
    }

    fun deleteFileFromProject(projectId: String, relativePath: String) {
        _projects.value = _projects.value.map { p ->
            if (p.id == projectId) {
                val toDelete = p.files.firstOrNull { it.path == relativePath || it.id == relativePath }
                if (toDelete != null) {
                    val diskFile = File(File(projectsDir, projectId), toDelete.path)
                    if (diskFile.exists()) diskFile.delete()
                }
                val updated = p.copy(files = p.files.filter { it.path != relativePath && it.id != relativePath }, updatedAt = System.currentTimeMillis())
                saveProjectMeta(updated)
                updated
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

    private fun saveProjectMeta(project: Project) {
        try {
            val projDir = File(projectsDir, project.id)
            if (!projDir.exists()) projDir.mkdirs()

            val json = JSONObject()
            json.put("id", project.id)
            json.put("name", project.name)
            json.put("packageName", project.packageName)
            json.put("targetApkName", project.targetApkName ?: JSONObject.NULL)
            json.put("createdAt", project.createdAt)
            json.put("updatedAt", project.updatedAt)
            json.put("gitRepoUrl", project.gitRepoUrl ?: JSONObject.NULL)
            json.put("gitBranch", project.gitBranch)

            val a = project.analysisResult
            if (a != null) {
                val aObj = JSONObject()
                aObj.put("id", a.id)
                aObj.put("appName", a.appName)
                aObj.put("packageName", a.packageName)
                aObj.put("versionName", a.versionName)
                aObj.put("versionCode", a.versionCode)
                aObj.put("minSdk", a.minSdk)
                aObj.put("targetSdk", a.targetSdk)
                aObj.put("compileSdk", a.compileSdk)
                aObj.put("apkSizeBytes", a.apkSizeBytes)
                aObj.put("sha256", a.sha256)
                aObj.put("signatureScheme", a.signatureScheme)
                aObj.put("signingStatus", a.signingStatus)
                aObj.put("signerCertSha256", a.signerCertSha256 ?: JSONObject.NULL)
                aObj.put("dexClassesCount", a.dexClassesCount)
                aObj.put("dexMethodsCount", a.dexMethodsCount)
                aObj.put("obfuscationLevel", a.obfuscationLevel)
                aObj.put("architecturePattern", a.architecturePattern)
                aObj.put("rawManifestXml", a.rawManifestXml)
                aObj.put("debuggable", a.debuggable)
                aObj.put("allowBackup", a.allowBackup)
                aObj.put("usesCleartextTraffic", a.usesCleartextTraffic)
                aObj.put("networkSecurityConfig", a.networkSecurityConfig ?: JSONObject.NULL)
                aObj.put("timestamp", a.timestamp)

                val pArr = JSONArray()
                a.permissions.forEach { p ->
                    val pObj = JSONObject()
                    pObj.put("name", p.name)
                    pObj.put("level", p.level.name)
                    pObj.put("description", p.description)
                    pObj.put("isDangerous", p.isDangerous)
                    pObj.put("riskScore", p.riskScore)
                    pArr.put(pObj)
                }
                aObj.put("permissions", pArr)

                fun compToArr(list: List<ApkComponent>): JSONArray {
                    val arr = JSONArray()
                    list.forEach { c ->
                        val cObj = JSONObject()
                        cObj.put("name", c.name)
                        cObj.put("type", c.type.name)
                        cObj.put("isExported", c.isExported)
                        cObj.put("isLauncher", c.isLauncher)
                        cObj.put("permission", c.permission ?: JSONObject.NULL)
                        arr.put(cObj)
                    }
                    return arr
                }

                aObj.put("activities", compToArr(a.activities))
                aObj.put("services", compToArr(a.services))
                aObj.put("receivers", compToArr(a.receivers))
                aObj.put("providers", compToArr(a.providers))

                val fArr = JSONArray()
                a.detectedFrameworks.forEach { fArr.put(it) }
                aObj.put("detectedFrameworks", fArr)

                val eArr = JSONArray()
                a.evidenceList.forEach { eArr.put(it) }
                aObj.put("evidenceList", eArr)

                val lArr = JSONArray()
                a.limitationsList.forEach { lArr.put(it) }
                aObj.put("limitationsList", lArr)

                val sArr = JSONArray()
                a.securityFindings.forEach { f ->
                    val fObj = JSONObject()
                    fObj.put("title", f.title)
                    fObj.put("severity", f.severity)
                    fObj.put("reason", f.reason)
                    sArr.put(fObj)
                }
                aObj.put("securityFindings", sArr)

                json.put("analysis", aObj)
            }

            // Write atomically via temporary file and rename
            val tmpFile = File(projDir, "project_meta.json.tmp")
            val targetFile = File(projDir, "project_meta.json")
            tmpFile.writeText(json.toString(2))
            if (tmpFile.renameTo(targetFile)) {
                // renamed successfully
            } else {
                targetFile.delete()
                tmpFile.renameTo(targetFile)
            }
        } catch (_: Exception) {}
    }
}

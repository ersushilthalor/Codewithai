package com.example.rea.data.service

import android.content.Context
import com.example.rea.data.model.BuildJob
import com.example.rea.data.model.BuildStatus
import com.example.rea.data.model.Project
import com.example.rea.data.model.ProjectFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class CloudBuilderService(private val context: Context? = null) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val buildsFile: File?
        get() = context?.let { File(it.filesDir, "builds.json") }

    private val artifactsDir: File?
        get() = context?.let {
            File(it.filesDir, "artifacts").apply { if (!exists()) mkdirs() }
        }

    private val _buildJobs = MutableStateFlow<List<BuildJob>>(loadInitialBuildJobs())
    val buildJobs: StateFlow<List<BuildJob>> = _buildJobs.asStateFlow()

    private fun loadInitialBuildJobs(): List<BuildJob> {
        val file = buildsFile ?: return emptyList()
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            val list = mutableListOf<BuildJob>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val logsArr = obj.optJSONArray("logs") ?: JSONArray()
                val logs = mutableListOf<String>()
                for (j in 0 until logsArr.length()) logs.add(logsArr.getString(j))

                list.add(
                    BuildJob(
                        id = obj.getString("id"),
                        projectId = obj.getString("projectId"),
                        projectName = obj.getString("projectName"),
                        buildType = obj.optString("buildType", "DEBUG_APK"),
                        status = BuildStatus.valueOf(obj.getString("status")),
                        logs = logs,
                        artifactName = if (obj.has("artifactName") && !obj.isNull("artifactName")) obj.getString("artifactName") else null,
                        artifactSizeBytes = if (obj.has("artifactSizeBytes") && !obj.isNull("artifactSizeBytes")) obj.getLong("artifactSizeBytes") else null,
                        artifactSha256 = if (obj.has("artifactSha256") && !obj.isNull("artifactSha256")) obj.getString("artifactSha256") else null,
                        startedAt = obj.optLong("startedAt", System.currentTimeMillis()),
                        finishedAt = if (obj.has("finishedAt") && !obj.isNull("finishedAt")) obj.getLong("finishedAt") else null,
                        errorReason = if (obj.has("errorReason") && !obj.isNull("errorReason")) obj.getString("errorReason") else null,
                        progressPercent = obj.optInt("progressPercent", 100)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistBuildJobs() {
        val file = buildsFile ?: return
        try {
            val arr = JSONArray()
            _buildJobs.value.take(50).forEach { job ->
                val obj = JSONObject()
                obj.put("id", job.id)
                obj.put("projectId", job.projectId)
                obj.put("projectName", job.projectName)
                obj.put("buildType", job.buildType)
                obj.put("status", job.status.name)
                obj.put("startedAt", job.startedAt)
                obj.put("finishedAt", job.finishedAt ?: JSONObject.NULL)
                obj.put("artifactName", job.artifactName ?: JSONObject.NULL)
                obj.put("artifactSizeBytes", job.artifactSizeBytes ?: JSONObject.NULL)
                obj.put("artifactSha256", job.artifactSha256 ?: JSONObject.NULL)
                obj.put("errorReason", job.errorReason ?: JSONObject.NULL)
                obj.put("progressPercent", job.progressPercent)

                val logsArr = JSONArray()
                job.logs.forEach { logsArr.put(it) }
                obj.put("logs", logsArr)

                arr.put(obj)
            }
            file.writeText(arr.toString())
        } catch (_: Exception) {}
    }

    private fun updateJobList(job: BuildJob) {
        val current = _buildJobs.value
        val index = current.indexOfFirst { it.id == job.id }
        _buildJobs.value = if (index >= 0) {
            current.toMutableList().apply { set(index, job) }
        } else {
            listOf(job) + current
        }
        persistBuildJobs()
    }

    suspend fun dispatchWorkflowWithRetry(
        token: String,
        owner: String,
        repo: String,
        workflowFileName: String,
        branch: String,
        maxAttempts: Int = 4,
        delayMs: Long = 3500L,
        onLog: (String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val dispatchUrl = "https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowFileName/dispatches"
        val dispatchBody = JSONObject().apply { put("ref", branch) }.toString()
        var lastErr = ""

        for (attempt in 1..maxAttempts) {
            val dispatchReq = Request.Builder()
                .url(dispatchUrl)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .post(dispatchBody.toRequestBody("application/json".toMediaType()))
                .build()

            val (code, body) = try {
                httpClient.newCall(dispatchReq).execute().use { resp ->
                    resp.code to resp.body?.string().orEmpty()
                }
            } catch (e: Exception) {
                0 to (e.message ?: "Network error")
            }

            if (code == 204) {
                onLog("[SUCCESS] Workflow dispatch accepted for $workflowFileName (HTTP 204).")
                return@withContext Result.success(Unit)
            } else if (code == 404 && attempt < maxAttempts) {
                onLog("[INFO] Workflow $workflowFileName not ready yet on GitHub (registering...). Retrying attempt $attempt/$maxAttempts in ${delayMs / 1000}s...")
                delay(delayMs)
            } else {
                lastErr = "HTTP $code - $body"
                if (attempt < maxAttempts) {
                    onLog("[INFO] Dispatch returned $code. Retrying attempt $attempt/$maxAttempts in ${delayMs / 1000}s...")
                    delay(delayMs)
                }
            }
        }
        Result.failure(IOException("Workflow $workflowFileName trigger rejected after $maxAttempts attempts: $lastErr"))
    }

    /**
     * Executes real cloud build using GitHub Actions (F2).
     */
    suspend fun startGitHubActionsBuild(
        project: Project,
        githubToken: String,
        owner: String,
        repo: String,
        branch: String = "main",
        onLog: (log: String, progress: Int) -> Unit
    ): BuildJob = withContext(Dispatchers.IO) {
        val jobId = "gh_build_" + UUID.randomUUID().toString().take(8)
        val logs = mutableListOf<String>()

        fun addLog(msg: String, pct: Int) {
            logs.add(msg)
            onLog(msg, pct)
        }

        val initialJob = BuildJob(
            id = jobId,
            projectId = project.id,
            projectName = project.name,
            buildType = "GITHUB_ACTIONS_APK",
            status = BuildStatus.VALIDATING,
            logs = logs.toList(),
            startedAt = System.currentTimeMillis(),
            progressPercent = 5
        )
        updateJobList(initialJob)

        if (githubToken.isBlank() || owner.isBlank() || repo.isBlank()) {
            addLog("[VALIDATION] GitHub token, owner, or repo is not configured.", 100)
            val failed = initialJob.copy(
                status = BuildStatus.FAILED,
                errorReason = "GitHub token or repository details missing in Settings.",
                finishedAt = System.currentTimeMillis(),
                progressPercent = 100,
                logs = logs.toList()
            )
            updateJobList(failed)
            return@withContext failed
        }

        addLog("[1/6] Preparing .github/workflows/build.yml CI pipeline...", 10)
        val workflowYaml = """
name: Build Debug APK
on:
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
      - name: Set up Android SDK
        uses: android-actions/setup-android@v3
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: 9.3.1
      - name: Build Debug APK
        run: gradle assembleDebug --stacktrace
      - name: Upload Debug APK
        uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/*.apk
        """.trimIndent()

        val allFiles = project.files.filter { it.path != ".github/workflows/build.yml" } + ProjectFile(
            id = ".github/workflows/build.yml",
            path = ".github/workflows/build.yml",
            name = "build.yml",
            language = "yaml",
            content = workflowYaml
        )

        addLog("[2/6] Synchronizing ${allFiles.size} project files to $owner/$repo...", 20)
        val ghService = GitHubService()
        val pushResult = ghService.pushFiles(githubToken, owner, repo, allFiles, branch) { cur, total, path ->
            onLog("Pushed [$cur/$total] $path", 20 + ((cur.toFloat() / total) * 15).toInt())
        }

        if (pushResult.isFailure) {
            val err = pushResult.exceptionOrNull()?.message ?: "Push failed"
            addLog("[ERROR] Failed to push project to GitHub: $err", 100)
            val failed = initialJob.copy(
                status = BuildStatus.FAILED,
                errorReason = err,
                finishedAt = System.currentTimeMillis(),
                progressPercent = 100,
                logs = logs.toList()
            )
            updateJobList(failed)
            return@withContext failed
        }

        addLog("[3/6] Triggering GitHub Actions workflow_dispatch on ref '$branch'...", 40)
        // Record UTC dispatch time right before triggering to avoid matching stale runs (Problem 3)
        val dispatchTime = System.currentTimeMillis()

        val dispatchResult = dispatchWorkflowWithRetry(
            token = githubToken,
            owner = owner,
            repo = repo,
            workflowFileName = "build.yml",
            branch = branch,
            maxAttempts = 4,
            delayMs = 3500L
        ) { logMsg -> addLog(logMsg, 45) }

        if (dispatchResult.isFailure) {
            val err = dispatchResult.exceptionOrNull()?.message ?: "Workflow dispatch rejected"
            addLog("[ERROR] $err", 100)
            val failed = initialJob.copy(
                status = BuildStatus.FAILED,
                errorReason = err,
                finishedAt = System.currentTimeMillis(),
                progressPercent = 100,
                logs = logs.toList()
            )
            updateJobList(failed)
            return@withContext failed
        }

        addLog("[4/6] Locating created workflow run...", 50)
        var runId: Long? = null
        val maxLocateAttempts = 15
        for (i in 0 until maxLocateAttempts) {
            delay(3000)
            val runsReq = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/actions/workflows/build.yml/runs?event=workflow_dispatch&branch=$branch&per_page=10")
                .header("Authorization", "Bearer $githubToken")
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()

            httpClient.newCall(runsReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string().orEmpty()
                    runId = WorkflowRunSelector.selectNewestMatchingRun(bodyStr, dispatchTime, branch)
                }
            }
            if (runId != null) break
        }

        if (runId == null) {
            addLog("[ERROR] Could not find created workflow run on GitHub within timeout.", 100)
            val failed = initialJob.copy(
                status = BuildStatus.FAILED,
                errorReason = "Run could not be located on GitHub Actions.",
                finishedAt = System.currentTimeMillis(),
                progressPercent = 100,
                logs = logs.toList()
            )
            updateJobList(failed)
            return@withContext failed
        }

        addLog("[5/6] Workflow run #$runId detected. Polling execution progress...", 55)
        var finalConclusion: String? = null
        val maxPollTimeMs = 20 * 60 * 1000L // 20 minutes
        val startPollTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startPollTime < maxPollTimeMs) {
            delay(5000)
            val pollReq = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/actions/runs/$runId")
                .header("Authorization", "Bearer $githubToken")
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()

            var runStatus = "in_progress"
            var runConclusion: String? = null

            httpClient.newCall(pollReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string().orEmpty())
                    runStatus = json.optString("status", "in_progress")
                    if (json.has("conclusion") && !json.isNull("conclusion")) {
                        runConclusion = json.getString("conclusion")
                    }
                }
            }

            // Fetch step status from jobs API
            try {
                val jobsReq = Request.Builder()
                    .url("https://api.github.com/repos/$owner/$repo/actions/runs/$runId/jobs")
                    .header("Authorization", "Bearer $githubToken")
                    .header("Accept", "application/vnd.github+json")
                    .get()
                    .build()

                httpClient.newCall(jobsReq).execute().use { jResp ->
                    if (jResp.isSuccessful) {
                        val jJson = JSONObject(jResp.body?.string().orEmpty())
                        val jobsArr = jJson.optJSONArray("jobs")
                        if (jobsArr != null && jobsArr.length() > 0) {
                            val firstJob = jobsArr.getJSONObject(0)
                            val stepsArr = firstJob.optJSONArray("steps") ?: JSONArray()
                            for (s in 0 until stepsArr.length()) {
                                val stepObj = stepsArr.getJSONObject(s)
                                val sName = stepObj.optString("name")
                                val sStatus = stepObj.optString("status")
                                val sConclusion = stepObj.optString("conclusion", "")
                                val logLine = "[JOB] $sName ($sStatus ${if (sConclusion.isNotBlank()) "- $sConclusion" else ""})"
                                if (!logs.contains(logLine)) {
                                    addLog(logLine, 65)
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            if (runStatus == "completed") {
                finalConclusion = runConclusion
                break
            }
        }

        if (finalConclusion != "success") {
            // Fetch logs of failure
            addLog("[BUILD FAILED] GitHub Actions conclusion: $finalConclusion", 90)
            try {
                val jobsReq = Request.Builder()
                    .url("https://api.github.com/repos/$owner/$repo/actions/runs/$runId/jobs")
                    .header("Authorization", "Bearer $githubToken")
                    .header("Accept", "application/vnd.github+json")
                    .get()
                    .build()

                httpClient.newCall(jobsReq).execute().use { jResp ->
                    if (jResp.isSuccessful) {
                        val jobsArr = JSONObject(jResp.body?.string().orEmpty()).optJSONArray("jobs")
                        if (jobsArr != null && jobsArr.length() > 0) {
                            val jId = jobsArr.getJSONObject(0).getLong("id")
                            val logReq = Request.Builder()
                                .url("https://api.github.com/repos/$owner/$repo/actions/jobs/$jId/logs")
                                .header("Authorization", "Bearer $githubToken")
                                .get()
                                .build()

                            httpClient.newCall(logReq).execute().use { lResp ->
                                val fullLog = lResp.body?.string().orEmpty()
                                val lastLines = fullLog.lines().takeLast(150)
                                lastLines.forEach { addLog(it, 95) }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            val failed = initialJob.copy(
                status = BuildStatus.FAILED,
                errorReason = "GitHub Actions build completed with conclusion: $finalConclusion",
                finishedAt = System.currentTimeMillis(),
                progressPercent = 100,
                logs = logs.toList()
            )
            updateJobList(failed)
            return@withContext failed
        }

        // On success: Download artifact APK (F2.6)
        addLog("[6/6] Build succeeded! Locating and downloading APK artifact...", 90)
        var artifactDownloadedName: String? = null
        var artifactSize: Long? = null
        var artifactSha256: String? = null

        try {
            val artReq = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/actions/runs/$runId/artifacts")
                .header("Authorization", "Bearer $githubToken")
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()

            var downloadUrl: String? = null
            httpClient.newCall(artReq).execute().use { aResp ->
                if (aResp.isSuccessful) {
                    val aJson = JSONObject(aResp.body?.string().orEmpty())
                    val artifacts = aJson.optJSONArray("artifacts")
                    if (artifacts != null && artifacts.length() > 0) {
                        downloadUrl = artifacts.getJSONObject(0).optString("archive_download_url")
                    }
                }
            }

            if (downloadUrl != null) {
                val dlReq = Request.Builder()
                    .url(downloadUrl!!)
                    .header("Authorization", "Bearer $githubToken")
                    .get()
                    .build()

                httpClient.newCall(dlReq).execute().use { dlResp ->
                    if (dlResp.isSuccessful && dlResp.body != null) {
                        val zis = ZipInputStream(dlResp.body!!.byteStream())
                        var entry: ZipEntry? = zis.nextEntry
                        while (entry != null) {
                            if (entry.name.endsWith(".apk")) {
                                val targetName = "${project.name.replace(" ", "_")}-debug.apk"
                                val outFile = File(artifactsDir, targetName)
                                val md = MessageDigest.getInstance("SHA-256")
                                var totalBytes = 0L

                                FileOutputStream(outFile).use { fos ->
                                    val buf = ByteArray(16384)
                                    var r: Int
                                    while (zis.read(buf).also { r = it } != -1) {
                                        md.update(buf, 0, r)
                                        fos.write(buf, 0, r)
                                        totalBytes += r
                                    }
                                }

                                artifactDownloadedName = targetName
                                artifactSize = totalBytes
                                artifactSha256 = md.digest().joinToString("") { "%02x".format(it) }
                                addLog("[ARTIFACT] Saved APK: $targetName ($totalBytes bytes, SHA-256: $artifactSha256)", 98)
                                break
                            }
                            entry = zis.nextEntry
                        }
                    }
                }
            }
        } catch (e: Exception) {
            addLog("[WARNING] Failed to extract APK artifact: ${e.message}", 95)
        }

        addLog("[COMPLETE] Real GitHub Actions cloud build completed successfully!", 100)
        val successJob = initialJob.copy(
            status = BuildStatus.SUCCESS,
            logs = logs.toList(),
            artifactName = artifactDownloadedName,
            artifactSizeBytes = artifactSize,
            artifactSha256 = artifactSha256,
            finishedAt = System.currentTimeMillis(),
            progressPercent = 100
        )
        updateJobList(successJob)
        return@withContext successJob
    }

    /**
     * Executes real cloud decompilation using JADX on GitHub Actions (Problem 2).
     */
    suspend fun startDecompileWorkflow(
        apkBytes: ByteArray,
        apkFileName: String,
        githubToken: String,
        owner: String,
        repo: String,
        branch: String = "main",
        onLog: (log: String, progress: Int) -> Unit
    ): Result<List<ProjectFile>> = withContext(Dispatchers.IO) {
        val maxSizeBytes = 50 * 1024 * 1024L // 50 MB GitHub Contents API limit
        if (apkBytes.size > maxSizeBytes) {
            val sizeMb = String.format(java.util.Locale.US, "%.1f", apkBytes.size / (1024.0 * 1024.0))
            val msg = "APK size ($sizeMb MB) exceeds GitHub contents API limit (50 MB). Please choose an APK under 50 MB for cloud decompilation."
            onLog("[ERROR] $msg", 100)
            return@withContext Result.failure(IllegalArgumentException(msg))
        }

        if (githubToken.isBlank() || owner.isBlank() || repo.isBlank()) {
            val msg = "GitHub token, owner, or repo is not configured in Settings."
            onLog("[ERROR] $msg", 100)
            return@withContext Result.failure(IllegalStateException(msg))
        }

        val ghService = GitHubService()

        onLog("[1/6] Pushing selected APK (${apkBytes.size} bytes) to GitHub repo as app.apk...", 10)
        val pushApkResult = ghService.pushBinaryFile(
            token = githubToken,
            owner = owner,
            repo = repo,
            path = "app.apk",
            bytes = apkBytes,
            commitMessage = "Upload target APK for JADX decompilation via REA Studio",
            branch = branch
        )
        if (pushApkResult.isFailure) {
            val err = pushApkResult.exceptionOrNull()?.message ?: "Failed to upload APK to repository"
            onLog("[ERROR] $err", 100)
            return@withContext Result.failure(IOException(err))
        }

        onLog("[2/6] Configuring .github/workflows/decompile.yml with pinned JADX release...", 20)
        val decompileYaml = """
name: Decompile APK with JADX
on:
  workflow_dispatch:

jobs:
  decompile:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
      - name: Download and setup JADX 1.5.0
        run: |
          curl -sL "https://github.com/skylot/jadx/releases/download/v1.5.0/jadx-1.5.0.zip" -o jadx.zip
          unzip -q jadx.zip -d jadx
          chmod +x jadx/bin/jadx
      - name: Run JADX decompiler
        run: |
          mkdir -p out
          ./jadx/bin/jadx -d out app.apk || JADX_EXIT=${'$'}?
          if [ ! -d out ] || [ -z "${'$'}(ls -A out)" ]; then
            echo "Error: JADX failed to produce any decompiled files in out/."
            exit 1
          fi
      - name: Upload Decompiled Artifact
        uses: actions/upload-artifact@v4
        with:
          name: jadx-decompiled
          path: out/
          if-no-files-found: error
        """.trimIndent()

        val pushWorkflowResult = ghService.pushTextFile(
            token = githubToken,
            owner = owner,
            repo = repo,
            path = ".github/workflows/decompile.yml",
            content = decompileYaml,
            commitMessage = "Add JADX decompilation workflow",
            branch = branch
        )
        if (pushWorkflowResult.isFailure) {
            val err = pushWorkflowResult.exceptionOrNull()?.message ?: "Failed to push decompile workflow"
            onLog("[ERROR] $err", 100)
            return@withContext Result.failure(IOException(err))
        }

        onLog("[3/6] Triggering workflow_dispatch for decompile.yml on branch '$branch'...", 35)
        val dispatchTime = System.currentTimeMillis()
        val dispatchResult = dispatchWorkflowWithRetry(
            token = githubToken,
            owner = owner,
            repo = repo,
            workflowFileName = "decompile.yml",
            branch = branch,
            maxAttempts = 4,
            delayMs = 3500L
        ) { logMsg -> onLog(logMsg, 40) }

        if (dispatchResult.isFailure) {
            val err = dispatchResult.exceptionOrNull()?.message ?: "Decompile dispatch failed"
            onLog("[ERROR] $err", 100)
            return@withContext Result.failure(IOException(err))
        }

        onLog("[4/6] Locating created decompile workflow run...", 45)
        var runId: Long? = null
        val maxLocateAttempts = 15
        for (i in 0 until maxLocateAttempts) {
            delay(3000)
            val runsReq = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/actions/workflows/decompile.yml/runs?event=workflow_dispatch&branch=$branch&per_page=10")
                .header("Authorization", "Bearer $githubToken")
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()

            httpClient.newCall(runsReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string().orEmpty()
                    runId = WorkflowRunSelector.selectNewestMatchingRun(bodyStr, dispatchTime, branch)
                }
            }
            if (runId != null) break
        }

        if (runId == null) {
            val msg = "Could not locate JADX decompile workflow run on GitHub within timeout."
            onLog("[ERROR] $msg", 100)
            return@withContext Result.failure(IllegalStateException(msg))
        }

        onLog("[5/6] Decompile run #$runId active. Polling JADX execution on GitHub Actions...", 55)
        var finalConclusion: String? = null
        val maxPollTimeMs = 15 * 60 * 1000L
        val startPollTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startPollTime < maxPollTimeMs) {
            delay(5000)
            val pollReq = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/actions/runs/$runId")
                .header("Authorization", "Bearer $githubToken")
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()

            var runStatus = "in_progress"
            var runConclusion: String? = null
            httpClient.newCall(pollReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string().orEmpty())
                    runStatus = json.optString("status", "in_progress")
                    if (json.has("conclusion") && !json.isNull("conclusion")) {
                        runConclusion = json.getString("conclusion")
                    }
                }
            }

            // Step status from jobs API
            try {
                val jobsReq = Request.Builder()
                    .url("https://api.github.com/repos/$owner/$repo/actions/runs/$runId/jobs")
                    .header("Authorization", "Bearer $githubToken")
                    .header("Accept", "application/vnd.github+json")
                    .get()
                    .build()

                httpClient.newCall(jobsReq).execute().use { jResp ->
                    if (jResp.isSuccessful) {
                        val jobsArr = JSONObject(jResp.body?.string().orEmpty()).optJSONArray("jobs")
                        if (jobsArr != null && jobsArr.length() > 0) {
                            val firstJob = jobsArr.getJSONObject(0)
                            val stepsArr = firstJob.optJSONArray("steps") ?: JSONArray()
                            for (s in 0 until stepsArr.length()) {
                                val stepObj = stepsArr.getJSONObject(s)
                                val sName = stepObj.optString("name")
                                val sStatus = stepObj.optString("status")
                                val sConclusion = stepObj.optString("conclusion", "")
                                onLog("[STEP] $sName ($sStatus ${if (sConclusion.isNotBlank()) "- $sConclusion" else ""})", 70)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            if (runStatus == "completed") {
                finalConclusion = runConclusion
                break
            }
        }

        if (finalConclusion != "success") {
            val msg = "JADX decompilation workflow concluded with: $finalConclusion"
            onLog("[ERROR] $msg", 100)
            return@withContext Result.failure(IllegalStateException(msg))
        }

        onLog("[6/6] Decompilation succeeded! Downloading artifact and extracting source files...", 85)
        val extractedFiles = mutableListOf<ProjectFile>()
        try {
            val artReq = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/actions/runs/$runId/artifacts")
                .header("Authorization", "Bearer $githubToken")
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()

            var downloadUrl: String? = null
            httpClient.newCall(artReq).execute().use { aResp ->
                if (aResp.isSuccessful) {
                    val aJson = JSONObject(aResp.body?.string().orEmpty())
                    val artifacts = aJson.optJSONArray("artifacts")
                    if (artifacts != null && artifacts.length() > 0) {
                        downloadUrl = artifacts.getJSONObject(0).optString("archive_download_url")
                    }
                }
            }

            if (downloadUrl == null) {
                val msg = "No artifact found in completed decompile workflow run."
                onLog("[ERROR] $msg", 100)
                return@withContext Result.failure(IllegalStateException(msg))
            }

            val dlReq = Request.Builder()
                .url(downloadUrl!!)
                .header("Authorization", "Bearer $githubToken")
                .get()
                .build()

            var extractionResult: JadxExtractionResult? = null
            httpClient.newCall(dlReq).execute().use { dlResp ->
                if (dlResp.isSuccessful && dlResp.body != null) {
                    extractionResult = JadxArtifactExtractor.extract(dlResp.body!!.byteStream())
                }
            }

            val files = extractionResult?.files ?: emptyList()
            val skipped = extractionResult?.skippedCount ?: 0
            if (files.isEmpty()) {
                val msg = "No valid source files could be extracted from decompiled artifact."
                onLog("[ERROR] $msg", 100)
                return@withContext Result.failure(IllegalStateException(msg))
            }

            val skipNote = if (skipped > 0) " ($skipped files skipped due to memory limit)" else ""
            onLog("[SUCCESS] Extracted ${files.size} decompiled files from JADX artifact$skipNote.", 100)
            Result.success(files)
        } catch (e: Exception) {
            val msg = "Failed to download or parse decompiled artifact: ${e.message}"
            onLog("[ERROR] $msg", 100)
            Result.failure(e)
        }
    }

    /**
     * Creates a genuine, valid ZIP project source code archive.
     */
    fun createProjectArchive(project: Project): Pair<ByteArray, String> {
        val baos = ByteArrayOutputStream()
        val zos = ZipOutputStream(baos)
        val md = MessageDigest.getInstance("SHA-256")

        for (f in project.files) {
            val entry = ZipEntry(f.path)
            zos.putNextEntry(entry)
            val bytes = f.content.toByteArray(Charsets.UTF_8)
            zos.write(bytes)
            md.update(bytes)
            zos.closeEntry()
        }
        zos.finish()
        zos.flush()

        val zipBytes = baos.toByteArray()
        val sha256 = md.digest().joinToString("") { "%02x".format(it) }
        return Pair(zipBytes, sha256)
    }

    /**
     * Checks if a real compiled APK exists in artifacts directory.
     */
    fun getExistingArtifactApk(projectName: String): File? {
        val dir = artifactsDir ?: return null
        val targetName = "${projectName.replace(" ", "_")}-debug.apk"
        val file = File(dir, targetName)
        if (file.exists() && file.length() > 1000) {
            // Verify first 2 bytes are PK (0x50, 0x4B)
            try {
                file.inputStream().use {
                    val b0 = it.read()
                    val b1 = it.read()
                    if (b0 == 0x50 && b1 == 0x4B) return file
                }
            } catch (_: Exception) {}
        }
        return null
    }
}

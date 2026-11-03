package com.example.rea.data.service

import android.util.Base64
import com.example.rea.data.model.DecompiledFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GitHubService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun newRequestBuilder(token: String): Request.Builder {
        return Request.Builder()
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
    }

    suspend fun validateToken(token: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext false to "Token is empty"
        try {
            val req = newRequestBuilder(token)
                .url("https://api.github.com/user")
                .get()
                .build()

            httpClient.newCall(req).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(bodyStr)
                    val login = json.optString("login", "")
                    true to login
                } else {
                    false to "GitHub Error (HTTP ${response.code}): ${bodyStr.take(120)}"
                }
            }
        } catch (e: Exception) {
            false to "Connection Error: ${e.localizedMessage ?: e.javaClass.simpleName}"
        }
    }

    suspend fun checkRepoExists(token: String, owner: String, repo: String): Boolean = withContext(Dispatchers.IO) {
        if (token.isBlank() || owner.isBlank() || repo.isBlank()) return@withContext false
        try {
            val req = newRequestBuilder(token)
                .url("https://api.github.com/repos/$owner/$repo")
                .get()
                .build()
            httpClient.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun createRepo(token: String, repoName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("name", repoName)
                put("private", true)
                put("auto_init", true)
            }
            val req = newRequestBuilder(token)
                .url("https://api.github.com/user/repos")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(bodyStr)
                    Result.success(json.optString("full_name", repoName))
                } else if (response.code == 422) {
                    Result.success(repoName)
                } else {
                    Result.failure(IOException("Failed to create repository (HTTP ${response.code}): $bodyStr"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pushFiles(
        token: String,
        owner: String,
        repo: String,
        files: List<DecompiledFile>,
        branch: String = "main",
        onProgress: (current: Int, total: Int, filePath: String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val total = files.size
        for (i in files.indices) {
            val f = files[i]
            onProgress(i + 1, total, f.path)
            try {
                // Check existing SHA
                val getReq = newRequestBuilder(token)
                    .url("https://api.github.com/repos/$owner/$repo/contents/${f.path}?ref=$branch")
                    .get()
                    .build()

                var existingSha: String? = null
                httpClient.newCall(getReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val sha = JSONObject(body).optString("sha", "")
                        if (sha.isNotBlank()) existingSha = sha
                    }
                }

                // Put file content
                val b64Content = Base64.encodeToString(f.content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                val putBody = JSONObject().apply {
                    put("message", "Add ${f.name} via REA Studio")
                    put("content", b64Content)
                    put("branch", branch)
                    if (existingSha != null) {
                        put("sha", existingSha)
                    }
                }

                val putReq = newRequestBuilder(token)
                    .url("https://api.github.com/repos/$owner/$repo/contents/${f.path}")
                    .put(putBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(putReq).execute().use { putResp ->
                    if (!putResp.isSuccessful && putResp.code != 200 && putResp.code != 201) {
                        val err = putResp.body?.string().orEmpty()
                        return@withContext Result.failure(IOException("Failed to push ${f.path} (HTTP ${putResp.code}): $err"))
                    }
                }
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }
        Result.success(Unit)
    }

    suspend fun pushBinaryFile(
        token: String,
        owner: String,
        repo: String,
        path: String,
        bytes: ByteArray,
        commitMessage: String = "Upload file via REA Studio",
        branch: String = "main"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val getReq = newRequestBuilder(token)
                .url("https://api.github.com/repos/$owner/$repo/contents/$path?ref=$branch")
                .get()
                .build()

            var existingSha: String? = null
            httpClient.newCall(getReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val sha = JSONObject(body).optString("sha", "")
                    if (sha.isNotBlank()) existingSha = sha
                }
            }

            val b64Content = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val putBody = JSONObject().apply {
                put("message", commitMessage)
                put("content", b64Content)
                put("branch", branch)
                if (existingSha != null) {
                    put("sha", existingSha)
                }
            }

            val putReq = newRequestBuilder(token)
                .url("https://api.github.com/repos/$owner/$repo/contents/$path")
                .put(putBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(putReq).execute().use { putResp ->
                if (!putResp.isSuccessful && putResp.code != 200 && putResp.code != 201) {
                    val err = putResp.body?.string().orEmpty()
                    return@withContext Result.failure(IOException("Failed to push $path (HTTP ${putResp.code}): $err"))
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pushTextFile(
        token: String,
        owner: String,
        repo: String,
        path: String,
        content: String,
        commitMessage: String,
        branch: String = "main"
    ): Result<Unit> = withContext(Dispatchers.IO) {
        pushBinaryFile(token, owner, repo, path, content.toByteArray(Charsets.UTF_8), commitMessage, branch)
    }
}

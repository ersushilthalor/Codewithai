package com.example.rea.data.service

import com.example.BuildConfig
import com.example.rea.data.model.EngineStatus
import com.example.rea.data.model.ReaEngineState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ReaEngineManager {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val initialEndpoint = try {
        val field = BuildConfig::class.java.getField("REA_WORKER_ENDPOINT")
        val ep = field.get(null) as? String ?: ""
        if (ep.isNotBlank() && !ep.contains("placeholder", ignoreCase = true) && !ep.contains("default", ignoreCase = true)) ep else ""
    } catch (_: Throwable) { "" }

    private val initialToken = try {
        val field = BuildConfig::class.java.getField("REA_WORKER_TOKEN")
        val tok = field.get(null) as? String ?: ""
        if (tok.isNotBlank() && !tok.contains("placeholder", ignoreCase = true) && !tok.contains("default", ignoreCase = true)) tok else ""
    } catch (_: Throwable) { "" }

    private val _engineState = MutableStateFlow(
        ReaEngineState(
            status = if (initialEndpoint.isNotBlank()) EngineStatus.STOPPED else EngineStatus.DISCONNECTED,
            version = "v1.4.2-core",
            cloudWorkerEndpoint = initialEndpoint,
            cloudWorkerToken = initialToken,
            mcpConnected = false,
            nodeVersion = "",
            jdkVersion = "",
            jadxVersion = "",
            apktoolVersion = "",
            activeTools = emptyList(),
            logs = listOf(
                if (initialEndpoint.isNotBlank()) {
                    "[INIT] Configured worker endpoint: $initialEndpoint. Ready to test connection."
                } else {
                    "[INIT] REA Backend Worker not configured. Optional Linux worker URL can be configured in Settings."
                }
            ),
            progressPercent = 0,
            currentStep = if (initialEndpoint.isNotBlank()) "Worker Configured (Stopped)" else "Not Configured"
        )
    )
    val engineState: StateFlow<ReaEngineState> = _engineState.asStateFlow()

    fun updateWorkerConfig(endpoint: String, token: String) {
        val currentLogs = _engineState.value.logs.toMutableList()
        currentLogs.add("[CONFIG] Updated worker endpoint: $endpoint")
        _engineState.value = _engineState.value.copy(
            cloudWorkerEndpoint = endpoint.trim(),
            cloudWorkerToken = token.trim(),
            status = if (endpoint.isNotBlank()) EngineStatus.STOPPED else EngineStatus.DISCONNECTED,
            mcpConnected = false,
            logs = currentLogs
        )
    }

    suspend fun connectWorker(
        cloudEndpoint: String = "",
        cloudToken: String = ""
    ) = withContext(Dispatchers.IO) {
        val endpoint = cloudEndpoint.ifBlank { _engineState.value.cloudWorkerEndpoint }.trim()
        val token = cloudToken.ifBlank { _engineState.value.cloudWorkerToken }.trim()

        val logs = _engineState.value.logs.toMutableList()
        logs.add("==================== WORKER CONNECTION CHECK ====================")

        if (endpoint.isBlank() || endpoint.contains(".internal")) {
            logs.add("[ERROR] Invalid or unconfigured worker endpoint: '$endpoint'")
            logs.add("[HELP] Set your actual Linux worker IP or domain (e.g., http://10.0.2.2:8080 or https://your-worker.com) in Settings.")
            _engineState.value = _engineState.value.copy(
                status = EngineStatus.DISCONNECTED,
                mcpConnected = false,
                currentStep = "Worker Not Configured",
                progressPercent = 0,
                logs = logs
            )
            return@withContext
        }

        logs.add("[CONNECT] Attempting handshake with worker at $endpoint...")
        _engineState.value = _engineState.value.copy(
            status = EngineStatus.CONNECTING,
            currentStep = "Connecting to $endpoint...",
            progressPercent = 30,
            logs = logs
        )

        try {
            val reqBuilder = Request.Builder().url("$endpoint/health")
            if (token.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer $token")
            }
            val startTime = System.currentTimeMillis()
            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    var nodeV = ""
                    var jdkV = ""
                    var jadxV = ""
                    var apktoolV = ""
                    try {
                        val bodyJson = JSONObject(body)
                        nodeV = bodyJson.optString("nodeVersion", "")
                        jdkV = bodyJson.optString("jdkVersion", "")
                        jadxV = bodyJson.optString("jadxVersion", "")
                        apktoolV = bodyJson.optString("apktoolVersion", "")
                    } catch (_: Exception) {}

                    logs.add("[SUCCESS] Handshake verified in ${latency}ms (HTTP ${response.code}).")
                    logs.add("[MCP] Discovering available tools at $endpoint/mcp/tools...")

                    val discoveredTools = fetchWorkerTools(endpoint, token)
                    logs.add("[READY] Discovered ${discoveredTools.size} tools: ${discoveredTools.joinToString(", ")}")

                    _engineState.value = _engineState.value.copy(
                        status = EngineStatus.RUNNING,
                        mcpConnected = true,
                        currentStep = "Worker reachable (Ping: ${latency}ms)",
                        progressPercent = 100,
                        cloudWorkerEndpoint = endpoint,
                        cloudWorkerToken = token,
                        nodeVersion = nodeV,
                        jdkVersion = jdkV,
                        jadxVersion = jadxV,
                        apktoolVersion = apktoolV,
                        activeTools = discoveredTools,
                        logs = logs
                    )
                } else {
                    logs.add("[ERROR] Worker responded with HTTP error ${response.code}: ${response.message}")
                    _engineState.value = _engineState.value.copy(
                        status = EngineStatus.ERROR,
                        mcpConnected = false,
                        currentStep = "Worker Error: HTTP ${response.code}",
                        progressPercent = 0,
                        logs = logs
                    )
                }
            }
        } catch (e: Exception) {
            logs.add("[FAILURE] Failed to connect to $endpoint: ${e.localizedMessage ?: e.javaClass.simpleName}")
            logs.add("[STATUS] Optional worker is currently unreachable.")
            _engineState.value = _engineState.value.copy(
                status = EngineStatus.ERROR,
                mcpConnected = false,
                currentStep = "Worker Unreachable: ${e.localizedMessage ?: "Connection Refused"}",
                progressPercent = 0,
                logs = logs
            )
        }
    }

    private fun fetchWorkerTools(endpoint: String, token: String): List<String> {
        return try {
            val req = Request.Builder().url("$endpoint/mcp/tools")
            if (token.isNotBlank()) req.header("Authorization", "Bearer $token")
            httpClient.newCall(req.build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string().orEmpty())
                    val toolsArr = json.optJSONArray("tools") ?: return emptyList()
                    val result = mutableListOf<String>()
                    for (i in 0 until toolsArr.length()) {
                        result.add(toolsArr.optString(i))
                    }
                    result
                } else emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun runHealthCheck(): String = withContext(Dispatchers.IO) {
        val endpoint = _engineState.value.cloudWorkerEndpoint.trim()
        val token = _engineState.value.cloudWorkerToken.trim()
        val logs = _engineState.value.logs.toMutableList()

        if (endpoint.isBlank() || endpoint.contains(".internal")) {
            val msg = "NOT CONFIGURED (Configure worker URL in Settings)"
            logs.add("[HEALTH-CHECK] $msg")
            _engineState.value = _engineState.value.copy(logs = logs)
            return@withContext msg
        }

        logs.add("[HEALTH-CHECK] Pinging $endpoint/health...")
        val startTime = System.currentTimeMillis()
        try {
            val reqBuilder = Request.Builder().url("$endpoint/health")
            if (token.isNotBlank()) reqBuilder.header("Authorization", "Bearer $token")

            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                val latency = System.currentTimeMillis() - startTime
                if (resp.isSuccessful) {
                    val msg = "Worker reachable (${latency}ms, HTTP ${resp.code})"
                    logs.add("[HEALTH-CHECK] ✓ Worker healthy: latency ${latency}ms")
                    _engineState.value = _engineState.value.copy(
                        status = EngineStatus.RUNNING,
                        mcpConnected = true,
                        currentStep = "Worker reachable (${latency}ms)",
                        logs = logs
                    )
                    return@withContext msg
                } else {
                    val msg = "Worker Error (HTTP ${resp.code})"
                    logs.add("[HEALTH-CHECK] ✗ Worker HTTP ${resp.code}")
                    _engineState.value = _engineState.value.copy(
                        status = EngineStatus.ERROR,
                        mcpConnected = false,
                        logs = logs
                    )
                    return@withContext msg
                }
            }
        } catch (e: Exception) {
            val msg = "Worker Unreachable (${e.localizedMessage ?: "Failed to connect"})"
            logs.add("[HEALTH-CHECK] ✗ Error: $msg")
            _engineState.value = _engineState.value.copy(
                status = EngineStatus.ERROR,
                mcpConnected = false,
                logs = logs
            )
            return@withContext msg
        }
    }

    fun disconnectWorker() {
        val logs = _engineState.value.logs.toMutableList()
        logs.add("[INFO] Worker connection disconnected.")
        _engineState.value = _engineState.value.copy(
            status = EngineStatus.STOPPED,
            mcpConnected = false,
            currentStep = "Worker Disconnected",
            progressPercent = 0,
            logs = logs
        )
    }
}

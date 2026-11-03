package com.example.rea.data.service

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class WorkflowRunInfo(
    val id: Long,
    val headBranch: String,
    val createdAt: String
)

object WorkflowRunSelector {

    const val DEFAULT_BUFFER_MS = 10_000L // 10 seconds buffer

    fun parseIso8601Utc(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        return try {
            java.time.Instant.parse(dateStr).toEpochMilli()
        } catch (_: Throwable) {
            try {
                val clean = if (dateStr.endsWith("Z")) dateStr.dropLast(1) else dateStr
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                sdf.parse(clean)?.time ?: 0L
            } catch (_: Exception) {
                0L
            }
        }
    }

    /**
     * Parses run items from JSON string.
     * Uses org.json when available (on-device), with regex fallback for JVM unit tests where org.json is a stub.
     */
    fun parseRuns(runsJson: String): List<WorkflowRunInfo> {
        val runs = mutableListOf<WorkflowRunInfo>()
        try {
            val root = JSONObject(runsJson)
            val runsArr = root.optJSONArray("workflow_runs")
            if (runsArr != null) {
                for (i in 0 until runsArr.length()) {
                    val obj = runsArr.optJSONObject(i) ?: continue
                    val id = obj.optLong("id", -1L)
                    val branch = obj.optString("head_branch", "")
                    val createdAt = obj.optString("created_at", "")
                    if (id > 0) {
                        runs.add(WorkflowRunInfo(id, branch, createdAt))
                    }
                }
                return runs
            }
        } catch (_: Throwable) {
            // Fallback for JVM unit tests where org.json is not mocked
        }

        val arrayStart = runsJson.indexOf("\"workflow_runs\"")
        val slice = if (arrayStart != -1) runsJson.substring(arrayStart) else runsJson

        val runBlockRegex = Regex("""\{[^{}]*"id"\s*:\s*(\d+)[^{}]*\}""")
        val branchRegex = Regex(""""head_branch"\s*:\s*"([^"]*)"""")
        val createdRegex = Regex(""""created_at"\s*:\s*"([^"]*)"""")
        val idRegex = Regex(""""id"\s*:\s*(\d+)""")

        runBlockRegex.findAll(slice).forEach { match ->
            val block = match.value
            val id = idRegex.find(block)?.groupValues?.get(1)?.toLongOrNull() ?: -1L
            val branch = branchRegex.find(block)?.groupValues?.get(1).orEmpty()
            val createdAt = createdRegex.find(block)?.groupValues?.get(1).orEmpty()
            if (id > 0) {
                runs.add(WorkflowRunInfo(id, branch, createdAt))
            }
        }

        return runs
    }

    /**
     * Pure function to select the newest workflow run matching dispatch criteria.
     * Rejects runs older than (dispatchTimeUtcMillis - bufferMs).
     * Optionally filters by expectedBranch.
     * Returns the newest matching run ID, or null if no matching run exists.
     */
    fun selectNewestMatchingRun(
        runsJson: String,
        dispatchTimeUtcMillis: Long,
        expectedBranch: String? = null,
        bufferMs: Long = DEFAULT_BUFFER_MS
    ): Long? {
        val runs = parseRuns(runsJson)
        if (runs.isEmpty()) return null

        val minAcceptableTime = dispatchTimeUtcMillis - bufferMs
        var newestMatchId: Long? = null
        var newestMatchTime: Long = Long.MIN_VALUE

        for (run in runs) {
            if (!expectedBranch.isNullOrBlank() && run.headBranch.isNotBlank() && run.headBranch != expectedBranch) {
                continue
            }

            val createdAtMillis = parseIso8601Utc(run.createdAt)
            if (createdAtMillis >= minAcceptableTime) {
                if (createdAtMillis > newestMatchTime) {
                    newestMatchTime = createdAtMillis
                    newestMatchId = run.id
                }
            }
        }

        return newestMatchId
    }

    fun selectNewestMatchingRun(
        runsArray: JSONArray,
        dispatchTimeUtcMillis: Long,
        expectedBranch: String? = null,
        bufferMs: Long = DEFAULT_BUFFER_MS
    ): Long? {
        val minAcceptableTime = dispatchTimeUtcMillis - bufferMs
        var newestMatchId: Long? = null
        var newestMatchTime: Long = Long.MIN_VALUE

        for (i in 0 until runsArray.length()) {
            val runObj = runsArray.optJSONObject(i) ?: continue
            val id = runObj.optLong("id", -1L)
            if (id <= 0) continue

            val branch = runObj.optString("head_branch", "")
            if (!expectedBranch.isNullOrBlank() && branch.isNotBlank() && branch != expectedBranch) {
                continue
            }

            val createdAtStr = runObj.optString("created_at", "")
            val createdAtMillis = parseIso8601Utc(createdAtStr)

            // Must be >= dispatchTime - bufferMs
            if (createdAtMillis >= minAcceptableTime) {
                if (createdAtMillis > newestMatchTime) {
                    newestMatchTime = createdAtMillis
                    newestMatchId = id
                }
            }
        }

        return newestMatchId
    }
}

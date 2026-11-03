package com.example

import com.example.rea.data.service.WorkflowRunSelector
import org.junit.Assert.*
import org.junit.Test

class WorkflowRunSelectorTest {

    @Test
    fun `old run ignored when created before dispatch time minus buffer`() {
        val dispatchTime = 1700000000000L // arbitrary fixed timestamp
        val oldIso = "2023-11-14T22:11:00Z" // older than dispatchTime
        val oldRunTime = WorkflowRunSelector.parseIso8601Utc(oldIso)
        assertTrue("Precondition: old run must be older than dispatchTime - 10s", oldRunTime < dispatchTime - 10_000L)

        val runsJson = """
            {
              "workflow_runs": [
                {
                  "id": 101,
                  "head_branch": "main",
                  "created_at": "$oldIso"
                }
              ]
            }
        """.trimIndent()

        val selected = WorkflowRunSelector.selectNewestMatchingRun(runsJson, dispatchTime, "main")
        assertNull("Old run must be ignored", selected)
    }

    @Test
    fun `new run picked and old run ignored`() {
        // Dispatch time set to 2023-11-14T22:13:00Z (epoch ms)
        val dispatchTime = WorkflowRunSelector.parseIso8601Utc("2023-11-14T22:13:00Z")

        val runsJson = """
            {
              "workflow_runs": [
                {
                  "id": 101,
                  "head_branch": "main",
                  "created_at": "2023-11-14T22:10:00Z"
                },
                {
                  "id": 202,
                  "head_branch": "main",
                  "created_at": "2023-11-14T22:13:02Z"
                }
              ]
            }
        """.trimIndent()

        val selected = WorkflowRunSelector.selectNewestMatchingRun(runsJson, dispatchTime, "main")
        assertEquals(202L, selected)
    }

    @Test
    fun `newest run picked when multiple new runs exist`() {
        val dispatchTime = WorkflowRunSelector.parseIso8601Utc("2023-11-14T22:13:00Z")

        val runsJson = """
            {
              "workflow_runs": [
                {
                  "id": 201,
                  "head_branch": "main",
                  "created_at": "2023-11-14T22:13:01Z"
                },
                {
                  "id": 202,
                  "head_branch": "main",
                  "created_at": "2023-11-14T22:13:08Z"
                }
              ]
            }
        """.trimIndent()

        val selected = WorkflowRunSelector.selectNewestMatchingRun(runsJson, dispatchTime, "main")
        assertEquals(202L, selected)
    }

    @Test
    fun `run created within 10s buffer before dispatch is accepted`() {
        val dispatchTime = WorkflowRunSelector.parseIso8601Utc("2023-11-14T22:13:05Z")
        // Run created 4 seconds before dispatch timestamp (clock drift or dispatch latency)
        val runWithinBufferIso = "2023-11-14T22:13:01Z"

        val runsJson = """
            {
              "workflow_runs": [
                {
                  "id": 303,
                  "head_branch": "main",
                  "created_at": "$runWithinBufferIso"
                }
              ]
            }
        """.trimIndent()

        val selected = WorkflowRunSelector.selectNewestMatchingRun(runsJson, dispatchTime, "main")
        assertEquals(303L, selected)
    }

    @Test
    fun `different branch is ignored when branch filter is specified`() {
        val dispatchTime = WorkflowRunSelector.parseIso8601Utc("2023-11-14T22:13:00Z")

        val runsJson = """
            {
              "workflow_runs": [
                {
                  "id": 404,
                  "head_branch": "feature-branch",
                  "created_at": "2023-11-14T22:13:05Z"
                }
              ]
            }
        """.trimIndent()

        val selected = WorkflowRunSelector.selectNewestMatchingRun(runsJson, dispatchTime, "main")
        assertNull("Run on different branch must be ignored", selected)
    }

    @Test
    fun `no match returns null on empty or invalid json`() {
        assertNull(WorkflowRunSelector.selectNewestMatchingRun("{}", 1700000000000L))
        assertNull(WorkflowRunSelector.selectNewestMatchingRun("invalid json", 1700000000000L))
        assertNull(WorkflowRunSelector.selectNewestMatchingRun("""{"workflow_runs": []}""", 1700000000000L))
    }
}

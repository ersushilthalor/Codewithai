package com.example

import com.example.rea.data.model.EngineStatus
import com.example.rea.data.model.ProviderType
import com.example.rea.data.model.ReconstructionConfig
import com.example.rea.data.model.ReconstructionMode
import com.example.rea.data.service.AiProviderManager
import com.example.rea.data.service.ReaEngineManager
import com.example.rea.data.service.ReconstructionService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleRobolectricTest {

  @Test
  fun `verify rea engine initial state`() {
    val engineManager = ReaEngineManager()
    val state = engineManager.engineState.value
    assertEquals(EngineStatus.RUNNING, state.status)
    assertTrue(state.mcpConnected)
    assertEquals("v1.4.2-core", state.version)
    assertTrue(state.activeTools.isNotEmpty())
  }

  @Test
  fun `verify ai provider default configuration`() {
    val providerManager = AiProviderManager()
    val defaultProv = providerManager.getDefaultProvider()
    assertEquals(ProviderType.GEMINI, defaultProv.type)
    assertTrue(defaultProv.isDefault)
    assertTrue(defaultProv.supportsToolCalling)
  }

  @Test
  fun `verify presets and multi key failover rotation`() {
    val providerManager = AiProviderManager()
    assertTrue(AiProviderManager.AVAILABLE_PRESETS.isNotEmpty())

    val prov = providerManager.getDefaultProvider()
    providerManager.addKeyToProvider(prov.id, "AIzaSyBackupKey_2")
    providerManager.addKeyToProvider(prov.id, "AIzaSyBackupKey_3")

    val updatedProv = providerManager.providers.value.first { it.id == prov.id }
    assertEquals(3, updatedProv.apiKeys.size)
    assertEquals(0, updatedProv.activeKeyIndex)

    // Rotate to next key on rate limit
    val nextKey = providerManager.rotateToNextKey(prov.id)
    assertEquals("AIzaSyBackupKey_2", nextKey)

    val rotatedProv = providerManager.providers.value.first { it.id == prov.id }
    assertEquals(1, rotatedProv.activeKeyIndex)
    assertEquals("AIzaSyBackupKey_2", rotatedProv.currentKey)
  }

  @Test
  fun `verify reconstruction service clean room report`() = runBlocking {
    val recService = ReconstructionService()
    val sampleAnalysis = com.example.rea.data.model.ApkAnalysisResult(
        id = "sample_test",
        appName = "TestCam",
        packageName = "com.test.cam",
        versionName = "1.0",
        versionCode = 1,
        minSdk = 26,
        targetSdk = 35,
        apkSizeBytes = 10_000_000L,
        sha256 = "test_hash"
    )

    val (files, report) = recService.reconstructApp(
        sampleAnalysis,
        ReconstructionConfig(mode = ReconstructionMode.FULL_APP)
    ) { _, _ -> }

    assertTrue(files.isNotEmpty())
    assertNotNull(report)
    assertEquals(94, report.completionPercentage)
  }
}

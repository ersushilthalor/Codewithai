package com.example

import com.example.rea.data.model.*
import com.example.rea.data.service.AiProviderManager
import com.example.rea.data.service.AxmlParser
import com.example.rea.data.service.CloudBuilderService
import com.example.rea.data.service.ReaEngineManager
import com.example.rea.data.service.ReconstructionService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExampleRobolectricTest {

  @Test
  fun `verify rea engine initial honest state`() {
    val engineManager = ReaEngineManager()
    val state = engineManager.engineState.value
    // Real honest initial state is NOT_INSTALLED when no endpoint is configured
    assertTrue(state.status == EngineStatus.NOT_INSTALLED || state.status == EngineStatus.STOPPED)
    assertFalse("MCP should not be connected until verified with server", state.mcpConnected)
    assertEquals("v1.4.2-core", state.version)
  }

  @Test
  fun `verify rea engine health check reports not configured when empty`() = runBlocking {
    val engineManager = ReaEngineManager()
    val result = engineManager.runHealthCheck()
    assertTrue("Should indicate not configured", result.contains("NOT CONFIGURED") || result.contains("Unreachable"))
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
    providerManager.addKeyToProvider(prov.id, "AIzaSyPrimary_1")
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
    val sampleAnalysis = ApkAnalysisResult(
        id = "sample_test",
        appName = "TestCam",
        packageName = "com.test.cam",
        versionName = "1.0",
        versionCode = 1,
        minSdk = 26,
        targetSdk = 35,
        apkSizeBytes = 10_000_000L,
        sha256 = "test_hash",
        activities = listOf(
            ApkComponent("com.test.cam.MainActivity", ComponentType.ACTIVITY, isExported = true, isLauncher = true)
        ),
        permissions = listOf(
            ApkPermission("android.permission.CAMERA", PermissionLevel.DANGEROUS, "Camera access", true, 8)
        )
    )

    val (files, report) = recService.reconstructApp(
        sampleAnalysis,
        ReconstructionConfig(mode = ReconstructionMode.FULL_APP)
    ) { _, _ -> }

    assertTrue("Files should be generated", files.isNotEmpty())
    assertNotNull(report)
    assertEquals(95, report.completionPercentage)
    assertTrue("Should include AndroidManifest.xml", files.any { it.name == "AndroidManifest.xml" })
    assertTrue("Should include build.gradle.kts", files.any { it.name == "build.gradle.kts" })
  }

  @Test
  fun `verify real project archive packaging creates valid zip container`() {
    val builderService = CloudBuilderService()
    val testProject = Project(
        id = "proj_test_zip",
        name = "ZipTestApp",
        packageName = "com.test.zipapp",
        targetApkName = "test.apk",
        analysisResult = null,
        files = listOf(
            DecompiledFile("f1", "app/src/main/AndroidManifest.xml", "AndroidManifest.xml", "xml", "<manifest/>"),
            DecompiledFile("f2", "app/build.gradle.kts", "build.gradle.kts", "gradle", "plugins {}")
        )
    )

    val (archiveBytes, sha256) = builderService.createProjectArchive(testProject)
    assertTrue("Archive bytes should not be empty", archiveBytes.isNotEmpty())
    assertEquals("Must have valid ZIP magic header 0x50 0x4B 0x03 0x04", 0x50.toByte(), archiveBytes[0])
    assertEquals(0x4B.toByte(), archiveBytes[1])
    assertEquals(0x03.toByte(), archiveBytes[2])
    assertEquals(0x04.toByte(), archiveBytes[3])
    assertEquals(64, sha256.length)
  }

  @Test
  fun `verify axml parser handles bytes gracefully`() {
    val dummyBytes = byteArrayOf(0x03, 0x00, 0x08, 0x00, 0x20, 0x00, 0x00, 0x00)
    val parsed = AxmlParser.parse(dummyBytes)
    assertNotNull(parsed)
  }
}

package com.example

import com.example.rea.data.model.AiProvider
import com.example.rea.data.service.AiProviderManager
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testAiProviderKeyPoolAndRotation() {
    val provider = AiProvider(
      id = "test-provider",
      name = "OpenAI",
      type = com.example.rea.data.model.ProviderType.OPENAI,
      apiKey = "key-primary",
      apiKeys = listOf("key-primary", "key-backup-1", "key-backup-2"),
      activeKeyIndex = 0,
      autoSwitchOnLimit = true,
      baseUrl = "https://api.openai.com/v1",
      analysisModel = "gpt-4o",
      codeGenModel = "gpt-4o"
    )

    assertEquals("key-primary", provider.currentKey)

    // Simulate rotation on rate-limit / quota reached
    val nextIdx = (provider.activeKeyIndex + 1) % provider.apiKeys.size
    val rotated = provider.copy(activeKeyIndex = nextIdx)
    assertEquals("key-backup-1", rotated.currentKey)
    assertEquals(1, rotated.activeKeyIndex)

    val nextIdx2 = (rotated.activeKeyIndex + 1) % rotated.apiKeys.size
    val rotated2 = rotated.copy(activeKeyIndex = nextIdx2)
    assertEquals("key-backup-2", rotated2.currentKey)
    assertEquals(2, rotated2.activeKeyIndex)

    val nextIdx3 = (rotated2.activeKeyIndex + 1) % rotated2.apiKeys.size
    val rotated3 = rotated2.copy(activeKeyIndex = nextIdx3)
    assertEquals("key-primary", rotated3.currentKey)
    assertEquals(0, rotated3.activeKeyIndex)
  }

  @Test
  fun testPresetsAvailable() {
    val presets = AiProviderManager.AVAILABLE_PRESETS
    assertTrue("Should have preset AI providers", presets.isNotEmpty())
    val names = presets.map { it.name }
    assertTrue(names.contains("Google Gemini"))
    assertTrue(names.contains("OpenAI"))
    assertTrue(names.contains("Anthropic Claude"))
    assertTrue(names.contains("OpenRouter"))
    assertTrue(names.contains("DeepSeek"))
    assertTrue(names.contains("Groq"))
    assertTrue(names.contains("Mistral AI"))
  }

  @Test
  fun testPermissionCardSelectAllTogglesAllPermissions() {
    val items = listOf(
      com.example.rea.data.model.PermissionItemData("android.permission.CAMERA", "CAMERA", "Camera access", true, false),
      com.example.rea.data.model.PermissionItemData("android.permission.INTERNET", "INTERNET", "Internet access", false, false)
    )
    val card = com.example.rea.data.model.ChatPermissionCardData(
      id = "test_perm_card",
      allSelected = false,
      permissions = items
    )

    // Verify initial state
    assertFalse(card.allSelected)
    assertTrue(card.permissions.none { it.isGranted })

    // Simulate "Select All" toggle turned ON
    val selectAll = true
    val updatedCard = card.copy(
      allSelected = selectAll,
      permissions = card.permissions.map { it.copy(isGranted = selectAll) }
    )

    // Verify all permissions are automatically granted
    assertTrue(updatedCard.allSelected)
    assertTrue(updatedCard.permissions.all { it.isGranted })
  }

  @Test
  fun testChatModesAvailable() {
    val modes = com.example.rea.data.model.ChatMode.values()
    assertEquals(5, modes.size)
    assertTrue(modes.any { it == com.example.rea.data.model.ChatMode.FULL_REBUILD })
    assertTrue(modes.any { it == com.example.rea.data.model.ChatMode.UI_CLONE })
    assertTrue(modes.any { it == com.example.rea.data.model.ChatMode.SECURITY_AUDIT })
    assertTrue(modes.any { it == com.example.rea.data.model.ChatMode.FEATURE_INJECT })
    assertTrue(modes.any { it == com.example.rea.data.model.ChatMode.QUICK_INSPECT })
  }

  @Test
  fun testAxmlParserGracefulHandling() {
    // Test A1: AxmlParser handles empty or invalid bytes gracefully
    val parsedEmpty = com.example.rea.data.service.AxmlParser.parse(byteArrayOf())
    assertNotNull(parsedEmpty)

    val dummyBytes = byteArrayOf(0x03, 0x00, 0x08, 0x00, 0x20, 0x00, 0x00, 0x00)
    val parsedDummy = com.example.rea.data.service.AxmlParser.parse(dummyBytes)
    assertNotNull(parsedDummy)
  }

  @Test
  fun testDexReaderHeaderParsing() {
    // Test B4: DexReader parses class count and method count from header
    val dexBytes = ByteArray(0x70)
    val bb = java.nio.ByteBuffer.wrap(dexBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    bb.putInt(0x58, 42) // method_ids_size
    bb.putInt(0x60, 7)  // class_defs_size

    val result = com.example.rea.data.service.DexReader.parseDex(dexBytes)
    assertEquals(7, result.classCount)
    assertEquals(42, result.methodCount)
  }

  @Test
  fun testParseCodeDiffFromAiOutputFileBlockFormat() {
    // Test A5: AgentService FILE: block format parser
    val sampleAiText = """
Here is the updated file:
FILE: app/src/main/java/com/example/TestClass.kt
```kotlin
package com.example

class TestClass {
    fun hello(): String = "World"
}
```
All done!
    """.trimIndent()

    val diff = com.example.rea.data.service.AgentService.parseCodeDiffFromAiOutput(sampleAiText, null)
    assertNotNull("Diff should be parsed from FILE: block", diff)
    assertEquals("app/src/main/java/com/example/TestClass.kt", diff!!.filePath)
    assertTrue("Content should contain class TestClass", diff.replacementSnippet.contains("class TestClass"))
    assertTrue("Content should contain fun hello", diff.replacementSnippet.contains("fun hello()"))
  }

  @Test
  fun testModelsDefaultsClean() {
    // Test E4: Models have clean defaults (no fake versions, no pre-connected accounts)
    val engineState = com.example.rea.data.model.ReaEngineState()
    assertEquals("", engineState.nodeVersion)
    assertEquals("", engineState.jdkVersion)
    assertEquals("", engineState.jadxVersion)
    assertEquals("", engineState.apktoolVersion)
    assertEquals("", engineState.cloudWorkerEndpoint)
    assertTrue(engineState.activeTools.isEmpty())

    val ghConfig = com.example.rea.data.model.GitHubConfig()
    assertEquals("", ghConfig.username)
    assertFalse(ghConfig.isConnected)
    assertNull(ghConfig.currentRepo)
  }

  @Test
  fun testApplyCodeDiffSnippetMatchingLogic() {
    // Test A3: Exact single occurrence matching logic
    val originalText = "val x = 1\nval y = 2\nval z = 3"

    // 1. Single exact occurrence -> succeeds
    val snippet1 = "val y = 2"
    val count1 = originalText.windowed(snippet1.length).count { it == snippet1 }
    assertEquals(1, count1)
    val replaced1 = originalText.replace(snippet1, "val y = 20")
    assertEquals("val x = 1\nval y = 20\nval z = 3", replaced1)

    // 2. 0 occurrences -> fails
    val snippetZero = "val notFound = 99"
    val countZero = originalText.windowed(snippetZero.length).count { it == snippetZero }
    assertEquals(0, countZero)

    // 3. Duplicate occurrences (> 1) -> fails
    val duplicateText = "val duplicate = 5\nval intermediate = 1\nval duplicate = 5"
    val snippetDup = "val duplicate = 5"
    val countDup = duplicateText.windowed(snippetDup.length).count { it == snippetDup }
    assertEquals(2, countDup)
    assertTrue("Multiple occurrences must be rejected", countDup > 1)
  }

  private fun buildSyntheticAxml(styleCount: Int): ByteArray {
    val strings = listOf("manifest", "package", "com.test.app")
    val stringBytes = java.io.ByteArrayOutputStream()
    val offsets = mutableListOf<Int>()
    for (s in strings) {
      offsets.add(stringBytes.size())
      val chars = s.toCharArray()
      val len = chars.size.toShort()
      stringBytes.write(len.toInt() and 0xFF)
      stringBytes.write((len.toInt() ushr 8) and 0xFF)
      for (c in chars) {
        stringBytes.write(c.code and 0xFF)
        stringBytes.write((c.code ushr 8) and 0xFF)
      }
      stringBytes.write(0)
      stringBytes.write(0)
    }
    val strPayload = stringBytes.toByteArray()
    val strOffsetsSize = strings.size * 4
    val strPoolHeaderSize = 28
    val stringsStart = strPoolHeaderSize + strOffsetsSize
    val styleOffsetsSize = if (styleCount > 0) 4 else 0
    val strPoolChunkSize = stringsStart + strPayload.size + styleOffsetsSize

    val startTagChunkSize = 36 + 20
    val endTagChunkSize = 24
    val totalFileSize = 8 + strPoolChunkSize + startTagChunkSize + endTagChunkSize

    val bb = java.nio.ByteBuffer.allocate(totalFileSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)

    // File header
    bb.putInt(0x00080003)
    bb.putInt(totalFileSize)

    // String pool chunk
    bb.putInt((strPoolHeaderSize shl 16) or 0x0001)
    bb.putInt(strPoolChunkSize)
    bb.putInt(strings.size)
    bb.putInt(styleCount)
    bb.putInt(0)
    bb.putInt(stringsStart)
    bb.putInt(if (styleCount > 0) stringsStart + strPayload.size else 0)

    for (off in offsets) {
      bb.putInt(off)
    }
    if (styleCount > 0) {
      bb.putInt(0)
    }

    bb.put(strPayload)

    // Start tag: manifest with package="com.test.app"
    bb.putInt((16 shl 16) or 0x0102)
    bb.putInt(startTagChunkSize)
    bb.putInt(1)
    bb.putInt(-1)
    bb.putInt(-1)
    bb.putInt(0) // strings[0] = "manifest"
    bb.putShort(20)
    bb.putShort(20)
    bb.putShort(1) // attrCount = 1
    bb.putShort(0)
    bb.putShort(0)
    bb.putShort(0)

    // Attribute 0: package="com.test.app"
    bb.putInt(-1)
    bb.putInt(1) // strings[1] = "package"
    bb.putInt(2) // strings[2] = "com.test.app"
    bb.putInt(0x03000008)
    bb.putInt(2)

    // End tag: manifest
    bb.putInt((16 shl 16) or 0x0103)
    bb.putInt(endTagChunkSize)
    bb.putInt(1)
    bb.putInt(-1)
    bb.putInt(-1)
    bb.putInt(0)

    return bb.array()
  }
}


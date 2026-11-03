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
}


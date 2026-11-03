package com.example.rea.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rea.data.model.AiProvider
import com.example.rea.data.service.AiProviderManager
import com.example.rea.data.service.ProviderPreset
import com.example.rea.ui.viewmodel.ReaViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: ReaViewModel,
    modifier: Modifier = Modifier
) {
    val providers by viewModel.providerManager.providers.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedPresetForAdd by remember { mutableStateOf<ProviderPreset?>(null) }
    var pasteKeyInput by remember { mutableStateOf("") }
    var selectedProviderForAddingKey by remember { mutableStateOf<AiProvider?>(null) }
    var extraKeyInput by remember { mutableStateOf("") }
    var showPresetPickerModal by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CleanBg)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text("Settings & AI Providers", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
            Text("Configure AI providers with automatic multi-key rotation", fontSize = 12.sp, color = TextMuted)
        }

        // Add Provider Button - Launches Automatic Presets
        item {
            Button(
                onClick = { showPresetPickerModal = true },
                colors = ButtonDefaults.buttonColors(containerColor = CleanBlue, contentColor = CleanBg),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("add_provider_preset_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add AI Provider from List", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Active Providers with Multi-Key Pool & Auto-Failover
        item {
            Text("Active Providers (${providers.size})", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextMain)
        }

        items(providers) { prov ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = CleanSurface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (prov.isDefault) CleanBlue else CleanBorder
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(CleanBlue.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.SmartToy,
                                    contentDescription = null,
                                    tint = CleanBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(prov.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextMain)
                                Text(
                                    text = "${prov.analysisModel} • ${prov.apiKeys.size} Key(s)",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Test Key
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val (ok, res) = viewModel.providerManager.testConnection(prov.id)
                                        Toast.makeText(context, if (ok) "✓ Ping: $res" else "Error: $res", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(32.dp).testTag("test_prov_${prov.id}")
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = "Test", tint = CleanBlue, modifier = Modifier.size(18.dp))
                            }

                            // Set Default
                            if (!prov.isDefault) {
                                IconButton(
                                    onClick = {
                                        viewModel.providerManager.setDefaultProvider(prov.id)
                                        Toast.makeText(context, "${prov.name} set as default", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.StarOutline, contentDescription = "Set Default", tint = TextMuted, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Auto-Switch Toggle on Rate Limit (HTTP 429)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CleanSurfaceLight,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-Switch on Rate Limit",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMain
                                )
                                Text(
                                    text = "Automatically switches to the next key when quota ends",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                            Switch(
                                checked = prov.autoSwitchOnLimit,
                                onCheckedChange = { viewModel.providerManager.toggleAutoSwitch(prov.id, it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = CleanGreen, checkedTrackColor = CleanGreen.copy(alpha = 0.3f))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // List of Keys in Pool
                    Text("API Keys in Pool (${prov.apiKeys.size}):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)
                    Spacer(modifier = Modifier.height(4.dp))

                    prov.apiKeys.forEachIndexed { idx, key ->
                        val isActive = idx == prov.activeKeyIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isActive) CleanBlue.copy(alpha = 0.1f) else CleanBg)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isActive) CleanGreen else TextSubtle)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Key ${idx + 1}: ${AiProviderManager.maskKey(key)}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isActive) CleanBlue else TextMain
                                )
                                if (isActive) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("(Active)", fontSize = 10.sp, color = CleanGreen, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (prov.apiKeys.size > 1) {
                                IconButton(
                                    onClick = { viewModel.providerManager.removeKeyFromProvider(prov.id, idx) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Delete Key", tint = CleanRed, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Button to Add Extra Backup Key
                    OutlinedButton(
                        onClick = {
                            selectedProviderForAddingKey = prov
                            extraKeyInput = ""
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CleanBlue),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CleanBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ Add Another Backup Key to ${prov.name}", fontSize = 11.sp)
                    }
                }
            }
        }

        // Engine Status Check
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CleanSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, CleanBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("REA Decompiler Engine", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                        Text("Linux Worker v22.23 & JADX Operational", fontSize = 11.sp, color = CleanGreen)
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                val health = viewModel.engineManager.runHealthCheck()
                                Toast.makeText(context, health, Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CleanSurfaceLight, contentColor = TextMain),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Ping", fontSize = 12.sp)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }

    // Modal 1: Automatic Provider Preset List
    if (showPresetPickerModal) {
        AlertDialog(
            onDismissRequest = { showPresetPickerModal = false },
            title = {
                Text(
                    text = "Select an AI Provider",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMain
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Tap any provider to paste your key directly. All settings and models are loaded automatically.",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    AiProviderManager.AVAILABLE_PRESETS.forEach { preset ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CleanSurfaceLight,
                            border = androidx.compose.foundation.BorderStroke(1.dp, CleanBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPresetForAdd = preset
                                    pasteKeyInput = ""
                                    showPresetPickerModal = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(CleanBlue.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = CleanBlue, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(preset.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                                    Text(preset.description, fontSize = 11.sp, color = TextMuted)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPresetPickerModal = false }) {
                    Text("Close", color = TextMuted)
                }
            },
            containerColor = CleanSurface
        )
    }

    // Modal 2: Direct 1-Tap Paste Key Dialog for Selected Preset
    if (selectedPresetForAdd != null) {
        val preset = selectedPresetForAdd!!
        AlertDialog(
            onDismissRequest = { selectedPresetForAdd = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Add ${preset.name}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextMain)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Paste your ${preset.name} API key below. You can also paste multiple keys separated by newlines to enable automatic rotation.",
                        fontSize = 12.sp,
                        color = TextMuted
                    )

                    OutlinedTextField(
                        value = pasteKeyInput,
                        onValueChange = { pasteKeyInput = it },
                        label = { Text("API Key(s)", fontSize = 11.sp) },
                        placeholder = { Text(preset.keyHint, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CleanBlue,
                            unfocusedBorderColor = CleanBorder
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pasteKeyInput.isNotBlank()) {
                            val keys = pasteKeyInput.lines().map { it.trim() }.filter { it.isNotBlank() }
                            val primary = keys.firstOrNull() ?: ""
                            val backup = if (keys.size > 1) keys.drop(1) else emptyList()

                            viewModel.providerManager.addFromPreset(preset, primary, backup)
                            Toast.makeText(context, "Added ${preset.name} with ${keys.size} key(s)", Toast.LENGTH_SHORT).show()
                            selectedPresetForAdd = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CleanBlue, contentColor = CleanBg)
                ) {
                    Text("Save & Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedPresetForAdd = null }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = CleanSurface
        )
    }

    // Modal 3: Add Extra Key to Existing Provider
    if (selectedProviderForAddingKey != null) {
        val prov = selectedProviderForAddingKey!!
        AlertDialog(
            onDismissRequest = { selectedProviderForAddingKey = null },
            title = {
                Text("Add Backup Key to ${prov.name}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextMain)
            },
            text = {
                Column {
                    Text(
                        text = "Paste another API key. When Key 1 hits rate limit or quota exhaustion, REA Studio will seamlessly switch to this key.",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = extraKeyInput,
                        onValueChange = { extraKeyInput = it },
                        label = { Text("New API Key") },
                        placeholder = { Text("Paste key...") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CleanBlue,
                            unfocusedBorderColor = CleanBorder
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (extraKeyInput.isNotBlank()) {
                            viewModel.providerManager.addKeyToProvider(prov.id, extraKeyInput)
                            Toast.makeText(context, "Backup key added to ${prov.name}!", Toast.LENGTH_SHORT).show()
                            selectedProviderForAddingKey = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CleanBlue, contentColor = CleanBg)
                ) {
                    Text("Add Key")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedProviderForAddingKey = null }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = CleanSurface
        )
    }
}

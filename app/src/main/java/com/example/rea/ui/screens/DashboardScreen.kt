package com.example.rea.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rea.data.model.*
import com.example.rea.ui.viewmodel.ReaViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: ReaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val currentMode by viewModel.chatMode.collectAsState()
    val messages by viewModel.agentService.messages.collectAsState()
    val pendingExportName by viewModel.pendingExportName.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var showActionSheet by remember { mutableStateOf(false) }
    var showModeDropdown by remember { mutableStateOf(false) }

    // Launcher for selecting APK from phone storage
    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val (name, size) = queryFileInfo(context, it)
            viewModel.selectApkFromStorage(it, name, size)
            Toast.makeText(context, "Selected: $name", Toast.LENGTH_SHORT).show()
        }
    }

    // Fallback GetContent launcher if OpenDocument is not preferred by device
    val apkFallbackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val (name, size) = queryFileInfo(context, it)
            viewModel.selectApkFromStorage(it, name, size)
            Toast.makeText(context, "Selected: $name", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher for selecting screenshot image
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.selectImage(it)
            Toast.makeText(context, "Screenshot selected for UI reconstruction", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher for saving reconstructed APK to device storage folder
    val exportApkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri: Uri? ->
        uri?.let {
            viewModel.saveFinalApkToStorage(context, it)
        }
    }

    // Auto-scroll on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val isThinking by viewModel.agentService.isAgentThinking.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CleanBg)
    ) {
        // TOP MODE SELECTOR BAR
        Surface(
            color = CleanSurface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Mode Dropdown Selector
                    Box {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CleanBlue.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CleanBlue.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .clickable { showModeDropdown = true }
                                .testTag("chat_mode_dropdown_trigger")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = CleanBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Mode: ${currentMode.title}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextMain
                                    )
                                    Text(
                                        text = currentMode.subtitle,
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showModeDropdown,
                            onDismissRequest = { showModeDropdown = false },
                            modifier = Modifier.background(CleanSurface)
                        ) {
                            ChatMode.values().forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = mode.title,
                                                fontWeight = if (mode == currentMode) FontWeight.Bold else FontWeight.Medium,
                                                color = if (mode == currentMode) CleanBlue else TextMain,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = mode.subtitle,
                                                fontSize = 11.sp,
                                                color = TextMuted
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.setChatMode(mode)
                                        showModeDropdown = false
                                        Toast.makeText(context, "Mode: ${mode.title}", Toast.LENGTH_SHORT).show()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = when (mode) {
                                                ChatMode.FULL_REBUILD -> Icons.Default.Build
                                                ChatMode.UI_CLONE -> Icons.Default.Palette
                                                ChatMode.SECURITY_AUDIT -> Icons.Default.Security
                                                ChatMode.FEATURE_INJECT -> Icons.Default.Extension
                                                ChatMode.QUICK_INSPECT -> Icons.Default.Search
                                            },
                                            contentDescription = null,
                                            tint = if (mode == currentMode) CleanBlue else TextMuted
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // Direct Quick "Select APK" Button in Top Bar
                    FilledTonalButton(
                        onClick = {
                            try {
                                apkPickerLauncher.launch(arrayOf("*/*", "application/vnd.android.package-archive"))
                            } catch (_: Exception) {
                                apkFallbackLauncher.launch("*/*")
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = CleanBlue,
                            contentColor = CleanBg
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("top_quick_select_apk")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Select APK", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Horizontal Quick Mode Chips
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(ChatMode.values()) { mode ->
                        val isSelected = mode == currentMode
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) CleanBlue else CleanSurfaceLight.copy(alpha = 0.5f),
                            modifier = Modifier.clickable { viewModel.setChatMode(mode) }
                        ) {
                            Text(
                                text = mode.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) CleanBg else TextMuted,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // CHAT MESSAGE STREAM
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Filter out raw system messages, display friendly chat
            items(messages.filter { it.sender != MessageSender.SYSTEM }) { msg ->
                val isUser = msg.sender == MessageSender.USER
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                ) {
                    if (isUser) {
                        // USER MESSAGE BUBBLE
                        UserMessageCard(msg = msg)
                    } else {
                        // AGENT MESSAGE BUBBLE WITH REA SKILLS & PERMISSION CARDS
                        AgentMessageCard(
                            msg = msg,
                            onToggleAllPermissions = { selectAll ->
                                viewModel.toggleAllPermissions(msg.id, selectAll)
                            },
                            onToggleSinglePermission = { permName, isGranted ->
                                viewModel.toggleSinglePermission(msg.id, permName, isGranted)
                            },
                            onAcceptPermissions = {
                                viewModel.confirmPermissionsAndBuild(msg.id)
                            },
                            onExportApk = {
                                val fileName = msg.apkExportCard?.apkFileName ?: (pendingExportName ?: "reconstructed.apk")
                                exportApkLauncher.launch(fileName)
                            }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(10.dp)) }
        }

        // QUICK ACTION SUGGESTIONS CHIPS (When conversation has room)
        val promptChips = listOf(
            "Select APK to Rebuild",
            "Clone UI to Compose",
            "Audit App Permissions",
            "Inspect DEX Classes"
        )
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(promptChips) { chip ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CleanSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CleanBorder),
                    modifier = Modifier.clickable {
                        if (chip.contains("Select APK")) {
                            try {
                                apkPickerLauncher.launch(arrayOf("*/*", "application/vnd.android.package-archive"))
                            } catch (_: Exception) {
                                apkFallbackLauncher.launch("*/*")
                            }
                        } else {
                            inputText = chip
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                chip.contains("APK") -> Icons.Default.FolderOpen
                                chip.contains("UI") -> Icons.Default.Palette
                                chip.contains("Permissions") -> Icons.Default.Security
                                else -> Icons.Default.Search
                            },
                            contentDescription = null,
                            tint = CleanBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = chip,
                            fontSize = 11.sp,
                            color = TextMain,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // BOTTOM INPUT BAR WITH PLUS (+) BUTTON
        Surface(
            color = CleanSurface,
            tonalElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, CleanBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "+" Action Button (Select APK / Select Image)
                IconButton(
                    onClick = { showActionSheet = true },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CleanBlue.copy(alpha = 0.15f))
                        .testTag("chat_plus_action_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Attach APK or Screenshot",
                        tint = CleanBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Text Field
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    enabled = !isThinking,
                    placeholder = {
                        Text(
                            text = if (isThinking) "Thinking..." else "Message REA or tap + to Select APK...",
                            fontSize = 13.sp,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_textfield"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = TextMain,
                        unfocusedTextColor = TextMain
                    ),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Send Button
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() && !isThinking) {
                            viewModel.sendChatPrompt(inputText.trim())
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && !isThinking,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (inputText.isNotBlank() && !isThinking) CleanBlue else CleanSurfaceLight)
                        .testTag("chat_send_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (inputText.isNotBlank()) CleanBg else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    // BOTTOM SHEET / DIALOG FOR "+" ACTIONS (Select APK or Image)
    if (showActionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showActionSheet = false },
            containerColor = CleanSurface,
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Add Target for Reverse Engineering",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMain
                )
                Text(
                    text = "Select files stored on your smartphone without needing a PC.",
                    fontSize = 12.sp,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Action 1: Select APK from Device Storage
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CleanBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CleanBlue.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showActionSheet = false
                            try {
                                apkPickerLauncher.launch(arrayOf("*/*", "application/vnd.android.package-archive"))
                            } catch (_: Exception) {
                                apkFallbackLauncher.launch("*/*")
                            }
                        }
                        .testTag("sheet_select_apk_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(CleanBlue.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = CleanBlue, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Select APK from Storage", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                            Text("Choose an APK file to inspect, decompile with JADX, or generate scaffold", fontSize = 11.sp, color = TextMuted)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action 2: Select Screenshot / Image
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CleanBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CleanPurple.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showActionSheet = false
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .testTag("sheet_select_image_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(CleanPurple.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = CleanPurple, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Select Screenshot / UI Image", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                            Text("Pick app screenshot to automatically generate Jetpack Compose layouts", fontSize = 11.sp, color = TextMuted)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// USER MESSAGE CARD
@Composable
private fun UserMessageCard(msg: AgentMessage) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.widthIn(max = 320.dp)
    ) {
        // If an APK was selected
        if (msg.selectedApkName != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CleanSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, CleanBlue.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CleanBlue.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Android, contentDescription = null, tint = CleanBlue, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = msg.selectedApkName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMain,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (msg.selectedApkSize != null) "${formatBytes(msg.selectedApkSize)} • Storage" else "APK selected from Phone Storage",
                            fontSize = 10.sp,
                            color = CleanGreen
                        )
                    }
                }
            }
        }

        // If an image was selected
        if (msg.selectedImageUri != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CleanSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, CleanPurple.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = CleanPurple, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("UI Screenshot attached", fontSize = 12.sp, color = TextMain, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Bubble Text
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = CleanBlue
        ) {
            Text(
                text = msg.text,
                color = CleanBg,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

// AGENT MESSAGE CARD WITH REA SKILLS & PERMISSION CARD
@Composable
private fun AgentMessageCard(
    msg: AgentMessage,
    onToggleAllPermissions: (Boolean) -> Unit,
    onToggleSinglePermission: (String, Boolean) -> Unit,
    onAcceptPermissions: () -> Unit,
    onExportApk: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(CleanBlue.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = CleanBlue, modifier = Modifier.size(14.dp))
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text("REA Studio", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CleanBlue)
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(CleanGreen)
            )
        }

        // Main agent text bubble
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = CleanSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, CleanBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = msg.text,
                    color = TextMain,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )

                // LIVE REA SKILLS BOX
                if (msg.skillsUsed.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CleanBg.copy(alpha = 0.8f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CleanBlue.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Terminal, contentDescription = null, tint = CleanBlue, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "REA ANALYSIS SKILLS APPLIED",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CleanBlue,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                Text(
                                    text = "${msg.skillsUsed.size} Tools",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            msg.skillsUsed.forEach { skill ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = CleanSurfaceLight,
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Text(
                                            text = skill.skillKey,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CleanGreen,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = skill.title,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextMain
                                        )
                                        Text(
                                            text = skill.description,
                                            fontSize = 10.sp,
                                            color = TextMuted
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Completed",
                                        tint = CleanGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // INTERACTIVE PERMISSION CARD WITH "ALL SELECT" TOGGLE
                val permCard = msg.permissionCard
                if (permCard != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = CleanBg,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (permCard.isAccepted) CleanGreen.copy(alpha = 0.5f) else CleanOrange.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Card Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = if (permCard.isAccepted) CleanGreen else CleanOrange,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = permCard.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextMain
                                        )
                                        Text(
                                            text = "${permCard.permissions.count { it.isGranted }} of ${permCard.permissions.size} Granted",
                                            fontSize = 10.sp,
                                            color = TextMuted
                                        )
                                    }
                                }

                                if (permCard.isAccepted) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = CleanGreen.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "APPROVED",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CleanGreen,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // MASTER SWITCH: "ALL SELECT" / "SELECT ALL"
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = CleanSurface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, CleanBlue.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("perm_all_select_container")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.DoneAll,
                                            contentDescription = null,
                                            tint = CleanBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "Select All (Automatic Approval)",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextMain
                                            )
                                            Text(
                                                text = "Automatically grants all required app permissions",
                                                fontSize = 10.sp,
                                                color = TextMuted
                                            )
                                        }
                                    }

                                    Switch(
                                        checked = permCard.allSelected,
                                        onCheckedChange = { isChecked ->
                                            onToggleAllPermissions(isChecked)
                                        },
                                        enabled = !permCard.isAccepted,
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = CleanBg,
                                            checkedTrackColor = CleanGreen,
                                            uncheckedThumbColor = TextMuted,
                                            uncheckedTrackColor = CleanSurfaceLight
                                        ),
                                        modifier = Modifier.testTag("perm_master_switch")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Individual Permission Items
                            permCard.permissions.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = item.label,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextMain
                                            )
                                            if (item.isDangerous) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = CleanRed.copy(alpha = 0.2f)
                                                ) {
                                                    Text(
                                                        text = "RUNTIME",
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = CleanRed,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = item.description,
                                            fontSize = 9.sp,
                                            color = TextMuted
                                        )
                                    }

                                    Checkbox(
                                        checked = item.isGranted,
                                        onCheckedChange = { isChecked ->
                                            onToggleSinglePermission(item.permission, isChecked)
                                        },
                                        enabled = !permCard.isAccepted,
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = CleanGreen,
                                            uncheckedColor = TextMuted,
                                            checkmarkColor = CleanBg
                                        )
                                    )
                                }
                            }

                            // Accept & Continue Button
                            if (!permCard.isAccepted) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = onAcceptPermissions,
                                    colors = ButtonDefaults.buttonColors(containerColor = CleanGreen, contentColor = CleanBg),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .testTag("perm_confirm_btn")
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Accept Permissions & Rebuild APK",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // APK EXPORT CARD (Save APK to Phone Storage)
                val exportCard = msg.apkExportCard
                if (exportCard != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = CleanBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CleanGreen.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(CleanGreen.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.DownloadDone, contentDescription = null, tint = CleanGreen, modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = exportCard.apkFileName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextMain,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val isRealApk = exportCard.apkFileName.endsWith(".apk")
                                    Text(
                                        text = if (isRealApk) "${formatBytes(exportCard.sizeBytes)} • Compiled APK" else "${formatBytes(exportCard.sizeBytes)} • Project ZIP Source Bundle",
                                        fontSize = 11.sp,
                                        color = if (isRealApk) CleanGreen else CleanBlue
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (exportCard.isSavedToDevice) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = CleanGreen.copy(alpha = 0.15f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CleanGreen, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Saved in Phone Storage Folder",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = CleanGreen
                                        )
                                    }
                                }
                            } else {
                                val isRealApk = exportCard.apkFileName.endsWith(".apk")
                                Button(
                                    onClick = onExportApk,
                                    colors = ButtonDefaults.buttonColors(containerColor = CleanBlue, contentColor = CleanBg),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .testTag("export_save_apk_btn")
                                ) {
                                    Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isRealApk) "Save APK" else "Save project ZIP",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// HELPER: Query file name and size from SAF Uri
private fun queryFileInfo(context: Context, uri: Uri): Pair<String, Long> {
    var name = "target_app.apk"
    var size = 16_500_000L
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
            }
        }
    } catch (_: Exception) {}
    return Pair(name, size)
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return "%.1f MB".format(mb)
}

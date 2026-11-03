package com.example.rea.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rea.data.model.AgentMode
import com.example.rea.data.model.AgentMessage
import com.example.rea.data.model.MessageSender
import com.example.rea.ui.viewmodel.ReaViewModel
import com.example.ui.theme.*

@Composable
fun AgentScreen(
    viewModel: ReaViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.agentService.messages.collectAsState()
    val isThinking by viewModel.agentService.isAgentThinking.collectAsState()
    val currentMode by viewModel.agentMode.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CleanBg)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Screen Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("AI Engineer Agent", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
                Text(
                    text = if (isThinking) "Thinking..." else "Active Mode: ${currentMode.name}",
                    fontSize = 12.sp,
                    color = if (isThinking) CleanBlue else TextMuted
                )
            }

            IconButton(
                onClick = { viewModel.agentService.clearHistory() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", tint = TextMuted)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Chat Message List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { msg ->
                val isUser = msg.sender == MessageSender.USER
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isUser) CleanBlue else CleanSurface,
                        border = if (isUser) null else androidx.compose.foundation.BorderStroke(1.dp, CleanBorder),
                        modifier = Modifier.widthIn(max = 320.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = msg.text,
                                fontSize = 13.sp,
                                color = if (isUser) CleanBg else TextMain,
                                lineHeight = 19.sp,
                                fontWeight = if (isUser) FontWeight.SemiBold else FontWeight.Normal
                            )

                            // Diff with preview and undo (A3, A5)
                            if (msg.proposedDiff != null) {
                                val diff = msg.proposedDiff
                                val oldLines = if (diff.originalSnippet.isBlank()) 0 else diff.originalSnippet.lines().size
                                val newLines = diff.replacementSnippet.lines().size
                                val previewSnippet = diff.replacementSnippet.lines().take(3).joinToString("\n")

                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = CleanBg,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CleanBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = "FILE: ${diff.filePath}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CleanBlue
                                        )
                                        Text(
                                            text = "Lines: $oldLines -> $newLines",
                                            fontSize = 10.sp,
                                            color = TextMuted
                                        )
                                        Text(
                                            text = previewSnippet + "\n...",
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextSubtle,
                                            lineHeight = 12.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                if (!msg.isDiffApplied) {
                                    Button(
                                        onClick = {
                                            viewModel.applyCodeDiff(diff, msg.id)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CleanGreen, contentColor = CleanBg),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("apply_code_diff_btn")
                                    ) {
                                        Text("Apply Code Changes", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = CleanGreen.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "Applied",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = CleanGreen,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                viewModel.undoCodeDiff(msg.id)
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp).testTag("undo_code_diff_btn")
                                        ) {
                                            Text("Undo", fontSize = 10.sp, color = CleanRed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Clean Input Bar with Thinking Indicator (C3)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CleanSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, CleanBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    enabled = !isThinking,
                    placeholder = {
                        Text(
                            text = if (isThinking) "Thinking..." else "Ask AI to change anything...",
                            fontSize = 13.sp,
                            color = TextMuted
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = TextMain,
                        unfocusedTextColor = TextMain
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("agent_input_field")
                )

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() && !isThinking) {
                            val text = inputText
                            inputText = ""
                            viewModel.sendAgentPrompt(text)
                        }
                    },
                    enabled = !isThinking && inputText.isNotBlank(),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (!isThinking && inputText.isNotBlank()) CleanBlue else TextMuted.copy(alpha = 0.3f))
                        .testTag("agent_send_btn")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = CleanBg,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

package com.example.rea.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.rea.ui.viewmodel.ReaViewModel
import com.example.rea.ui.viewmodel.ScreenDestination
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeWorkspaceScreen(
    viewModel: ReaViewModel,
    modifier: Modifier = Modifier
) {
    val project = viewModel.projectRepository.getCurrentProject()
    val selectedFile by viewModel.selectedFile.collectAsState()
    val context = LocalContext.current
    var editorContent by remember(selectedFile?.id) { mutableStateOf(selectedFile?.content ?: "") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CleanBg)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // Clean File Selector Pills
        val files = project?.files ?: emptyList()
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(files) { f ->
                val isSelected = f.id == selectedFile?.id
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) CleanBlue.copy(alpha = 0.2f) else CleanSurface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) CleanBlue else CleanBorder
                    ),
                    modifier = Modifier.clickable {
                        viewModel.selectFile(f)
                        editorContent = f.content
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (f.language) {
                                "kotlin" -> Icons.Default.Code
                                "gradle" -> Icons.Default.Build
                                else -> Icons.Default.Description
                            },
                            contentDescription = null,
                            tint = if (isSelected) CleanBlue else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = f.name,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) TextMain else TextMuted
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Code Editor Box
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CleanSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, CleanBorder),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header bar of editor
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedFile?.path ?: "Source Editor",
                        fontSize = 11.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.saveCurrentFileContent(editorContent)
                                Toast.makeText(context, "Saved changes!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CleanBlue, contentColor = CleanBg),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp).testTag("editor_save_btn")
                        ) {
                            Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.triggerCloudBuild("DEBUG_APK") },
                            colors = ButtonDefaults.buttonColors(containerColor = CleanGreen, contentColor = CleanBg),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp).testTag("editor_build_btn")
                        ) {
                            Text("Build APK", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalDivider(color = CleanBorder)

                // Text Field
                TextField(
                    value = editorContent,
                    onValueChange = { editorContent = it },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = TextMain,
                        unfocusedTextColor = TextMain
                    ),
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                        .testTag("code_editor_textarea")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // AI Assistant Quick Help Chips
        Text("AI Assistant Quick Actions", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val chips = listOf(
                "✨ Add Dark Mode" to "Add dark mode support to this screen",
                "⚡ Optimize Code" to "Optimize performance and reduce re-renders",
                "💬 Explain Code" to "Explain what this file does in simple terms"
            )

            chips.forEach { (label, prompt) ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CleanSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CleanBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            viewModel.sendAgentPrompt(prompt)
                            viewModel.navigateTo(ScreenDestination.AI_AGENT)
                        }
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextMain,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

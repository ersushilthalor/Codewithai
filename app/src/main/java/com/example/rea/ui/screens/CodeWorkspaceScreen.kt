package com.example.rea.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rea.data.model.DecompiledFile
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

    var isSearchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }

    var showAddFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var newFilePath by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CleanBg)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // File Selector Pills with Add File action
        val files = project?.files ?: emptyList()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
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

            Spacer(modifier = Modifier.width(6.dp))

            // Add File Button
            IconButton(
                onClick = {
                    if (project != null) {
                        newFileName = "NewComponent.kt"
                        newFilePath = "app/src/main/java/${project.packageName.replace('.', '/')}/ui/NewComponent.kt"
                        showAddFileDialog = true
                    } else {
                        Toast.makeText(context, "Select or create a project first", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.size(34.dp).testTag("workspace_add_file_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add File", tint = CleanBlue)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search & Replace bar if active
        if (isSearchVisible) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CleanSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, CleanBlue.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Find in file...", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f).height(46.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = replaceQuery,
                            onValueChange = { replaceQuery = it },
                            placeholder = { Text("Replace with...", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f).height(46.dp),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                if (searchQuery.isNotBlank()) {
                                    val count = editorContent.split(searchQuery).size - 1
                                    Toast.makeText(context, "Found $count match(es)", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("Find", fontSize = 11.sp)
                        }
                        TextButton(
                            onClick = {
                                if (searchQuery.isNotBlank()) {
                                    val newText = editorContent.replace(searchQuery, replaceQuery)
                                    editorContent = newText
                                    viewModel.saveCurrentFileContent(newText)
                                    Toast.makeText(context, "Replaced and saved!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("Replace All", fontSize = 11.sp, color = CleanGreen)
                        }
                        TextButton(onClick = { isSearchVisible = false }) {
                            Text("Close", fontSize = 11.sp, color = TextMuted)
                        }
                    }
                }
            }
        }

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
                        text = selectedFile?.path ?: "Select a file to edit",
                        fontSize = 11.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { isSearchVisible = !isSearchVisible },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }

                        Button(
                            onClick = {
                                viewModel.saveCurrentFileContent(editorContent)
                                Toast.makeText(context, "Saved to project & storage!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CleanBlue, contentColor = CleanBg),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp).testTag("editor_save_btn")
                        ) {
                            Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.navigateTo(ScreenDestination.CLOUD_BUILDER) },
                            colors = ButtonDefaults.buttonColors(containerColor = CleanGreen, contentColor = CleanBg),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp).testTag("editor_build_btn")
                        ) {
                            Text("Build", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalDivider(color = CleanBorder)

                // Text Field for real file editing
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
                "✨ Add Dark Mode" to "Add dark mode support and dynamic colors to ${selectedFile?.name ?: "this screen"}",
                "⚡ Optimize Code" to "Optimize performance and reduce recompositions in ${selectedFile?.name ?: "this file"}",
                "💬 Explain Code" to "Explain what ${selectedFile?.name ?: "this file"} does in architectural terms"
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

    if (showAddFileDialog && project != null) {
        AlertDialog(
            onDismissRequest = { showAddFileDialog = false },
            title = { Text("Add Source File to Project", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextMain) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        label = { Text("File Name") },
                        placeholder = { Text("CameraOverlay.kt") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newFilePath,
                        onValueChange = { newFilePath = it },
                        label = { Text("File Path") },
                        placeholder = { Text("app/src/main/java/com/app/ui/CameraOverlay.kt") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFileName.isNotBlank() && newFilePath.isNotBlank()) {
                            val initialContent = """
package ${project.packageName}

// Reconstructed source file: $newFileName
                            """.trimIndent()
                            viewModel.projectRepository.addFileToProject(
                                projectId = project.id,
                                fileName = newFileName,
                                filePath = newFilePath,
                                content = initialContent,
                                language = if (newFileName.endsWith(".kt")) "kotlin" else "text"
                            )
                            showAddFileDialog = false
                            Toast.makeText(context, "Added $newFileName to workspace!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CleanBlue, contentColor = CleanBg)
                ) {
                    Text("Add File")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFileDialog = false }) { Text("Cancel", color = TextMuted) }
            },
            containerColor = CleanSurface
        )
    }
}

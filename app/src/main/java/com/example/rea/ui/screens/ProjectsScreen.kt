package com.example.rea.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rea.data.model.Project
import com.example.ui.theme.*
import com.example.rea.ui.viewmodel.ReaViewModel
import com.example.rea.ui.viewmodel.ScreenDestination

@Composable
fun ProjectsScreen(
    viewModel: ReaViewModel,
    modifier: Modifier = Modifier
) {
    val projects by viewModel.projectRepository.projects.collectAsState()
    val currentProject = viewModel.projectRepository.getCurrentProject()
    val context = LocalContext.current

    var isCreateDialogOpen by remember { mutableStateOf(false) }
    var newProjName by remember { mutableStateOf("") }
    var newProjPkg by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ReaBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Projects Workspace", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ReaTextPrimary)
                    Text("${projects.size} active reverse-engineering workspaces", fontSize = 11.sp, color = ReaTextSecondary)
                }

                Button(
                    onClick = { isCreateDialogOpen = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ReaCyan, contentColor = ReaBackground),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("new_project_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Project", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        items(projects) { proj ->
            val isCurrent = proj.id == currentProject?.id
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ReaSurfaceCard,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isCurrent) ReaCyan else ReaSurfaceBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.projectRepository.selectProject(proj.id) }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(proj.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ReaTextPrimary)
                                if (isCurrent) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(shape = RoundedCornerShape(4.dp), color = ReaCyan.copy(alpha = 0.15f)) {
                                        Text("ACTIVE", fontSize = 9.sp, color = ReaCyan, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                            }
                            Text(proj.packageName, fontSize = 11.sp, color = ReaTextSecondary, fontFamily = FontFamily.Monospace)
                        }

                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = if (isCurrent) ReaCyan else ReaTextMuted)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Files: ${proj.files.size}", fontSize = 11.sp, color = ReaTextMuted, fontFamily = FontFamily.Monospace)
                        Text(
                            text = if (proj.reconstructionReport != null) "Reconstructed (94%)" else "Analyzed",
                            fontSize = 11.sp,
                            color = if (proj.reconstructionReport != null) ReaEmerald else ReaAmber,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.projectRepository.selectProject(proj.id)
                                viewModel.navigateTo(ScreenDestination.REVERSE_ENGINEER)
                            },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Analysis", fontSize = 11.sp, color = ReaCyan)
                        }

                        Button(
                            onClick = {
                                viewModel.projectRepository.selectProject(proj.id)
                                viewModel.navigateTo(ScreenDestination.CODE_EDITOR)
                            },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ReaSurfaceElevated, contentColor = ReaCyan),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Code Editor", fontSize = 11.sp)
                        }

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("project_summary", "Project: ${proj.name}\nPackage: ${proj.packageName}\nFiles count: ${proj.files.size}"))
                                Toast.makeText(context, "Exported project summary", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Export", tint = ReaTextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }

    if (isCreateDialogOpen) {
        AlertDialog(
            onDismissRequest = { isCreateDialogOpen = false },
            title = { Text("New Reverse Engineering Project", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ReaTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newProjName,
                        onValueChange = { newProjName = it },
                        label = { Text("Project Name") },
                        placeholder = { Text("MyApp Analysis") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newProjPkg,
                        onValueChange = { newProjPkg = it },
                        label = { Text("Package Name") },
                        placeholder = { Text("com.example.myapp") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newProjName.isNotBlank()) {
                            viewModel.projectRepository.createProject(
                                name = newProjName,
                                packageName = newProjPkg.ifBlank { "com.reconstructed." + newProjName.lowercase() },
                                targetApkName = newProjName.lowercase() + ".apk"
                            )
                            isCreateDialogOpen = false
                            newProjName = ""
                            newProjPkg = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ReaCyan, contentColor = ReaBackground)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { isCreateDialogOpen = false }) { Text("Cancel", color = ReaTextSecondary) }
            },
            containerColor = ReaSurfaceCard
        )
    }
}

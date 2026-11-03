package com.example.rea.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rea.data.model.BuildStatus
import com.example.rea.ui.components.TerminalLogView
import com.example.rea.ui.viewmodel.ReaViewModel
import com.example.ui.theme.*

@Composable
fun CloudBuilderScreen(
    viewModel: ReaViewModel,
    modifier: Modifier = Modifier
) {
    val buildJobs by viewModel.builderService.buildJobs.collectAsState()
    val activeJob = buildJobs.firstOrNull()
    val project = viewModel.projectRepository.getCurrentProject()
    val gitHubConfig by viewModel.gitHubConfig.collectAsState()
    val context = LocalContext.current
    var showLogs by remember { mutableStateOf(false) }

    val exportApkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri ->
        uri?.let {
            viewModel.saveFinalApkToStorage(context, it)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CleanBg)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CleanSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, CleanBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Cloud Build (GitHub Actions)",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextMain
                            )
                            Text(
                                text = "Builds real APK via GitHub Actions without requiring a PC",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CleanGreen.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = CleanGreen, modifier = Modifier.size(24.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Target Project: ${project?.name ?: "No Project Loaded"}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = CleanBlue
                    )
                    Text(
                        text = if (gitHubConfig.isConnected) {
                            "GitHub Connected: @${gitHubConfig.username}/${gitHubConfig.currentRepo ?: "reconstructed-app"}"
                        } else {
                            "Configure your GitHub Token in Settings to enable automated cloud compilation"
                        },
                        fontSize = 12.sp,
                        color = if (gitHubConfig.isConnected) CleanGreen else CleanOrange
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    val isBuilding = activeJob?.status == BuildStatus.COMPILING || activeJob?.status == BuildStatus.VALIDATING || activeJob?.status == BuildStatus.PACKAGING

                    // Default build option: GitHub Actions (F2)
                    Button(
                        onClick = {
                            val repo = gitHubConfig.currentRepo?.substringAfterLast('/') ?: "reconstructed-app"
                            viewModel.triggerGitHubBuild(gitHubConfig.username, repo)
                        },
                        enabled = !isBuilding && project != null && gitHubConfig.isConnected,
                        colors = ButtonDefaults.buttonColors(containerColor = CleanBlue, contentColor = CleanBg),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("trigger_cloud_build_btn")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isBuilding) "Building on GitHub Actions..." else "Build APK with GitHub Actions",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secondary option: Export Project ZIP
                    OutlinedButton(
                        onClick = {
                            val name = "${(project?.name ?: "project").replace("[^a-zA-Z0-9]".toRegex(), "")}-source.zip"
                            exportApkLauncher.launch(name)
                        },
                        enabled = project != null,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp).testTag("export_project_zip_btn")
                    ) {
                        Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save project ZIP", fontSize = 13.sp)
                    }
                }
            }
        }

        // Active Build Progress or Download Card
        if (activeJob != null) {
            val isBuilding = activeJob.status == BuildStatus.COMPILING || activeJob.status == BuildStatus.VALIDATING || activeJob.status == BuildStatus.PACKAGING

            if (isBuilding) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CleanSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CleanBlue.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = CleanBlue,
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Cloud Build in Progress", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                                }
                                Text("${activeJob.progressPercent}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CleanBlue)
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { activeJob.progressPercent / 100f },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = CleanBlue,
                                trackColor = CleanSurfaceLight
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            TerminalLogView(
                                logs = activeJob.logs,
                                modifier = Modifier.height(140.dp),
                                title = "GitHub Actions Pipeline"
                            )
                        }
                    }
                }
            } else if (activeJob.status == BuildStatus.SUCCESS) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = CleanSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CleanGreen.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(CleanGreen.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CleanGreen, modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text("Build Succeeded!", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CleanGreen)
                                    Text(activeJob.artifactName ?: "Debug APK ready", fontSize = 12.sp, color = TextMuted)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    val name = activeJob.artifactName ?: "app-debug.apk"
                                    exportApkLauncher.launch(name)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CleanGreen, contentColor = CleanBg),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("download_apk_btn")
                            ) {
                                Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save APK", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            TextButton(
                                onClick = { showLogs = !showLogs },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text(if (showLogs) "Hide Details" else "Show Build Steps", fontSize = 12.sp, color = TextMuted)
                            }

                            if (showLogs) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TerminalLogView(
                                    logs = activeJob.logs,
                                    modifier = Modifier.height(140.dp),
                                    title = "Build Steps"
                                )
                            }
                        }
                    }
                }
            } else if (activeJob.status == BuildStatus.FAILED) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = CleanSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CleanOrange.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(CleanOrange.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = CleanOrange, modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text("Build Did Not Succeed", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CleanOrange)
                                    Text(activeJob.errorReason ?: "Review build logs below", fontSize = 12.sp, color = TextMuted)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    val name = "${(project?.name ?: "app").replace("[^a-zA-Z0-9]".toRegex(), "")}-source-bundle.zip"
                                    exportApkLauncher.launch(name)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CleanBlue, contentColor = CleanBg),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("export_source_bundle_btn")
                            ) {
                                Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save project ZIP", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            TextButton(
                                onClick = { showLogs = !showLogs },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text(if (showLogs) "Hide Build Logs" else "View Build Logs", fontSize = 12.sp, color = TextMuted)
                            }

                            if (showLogs) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TerminalLogView(
                                    logs = activeJob.logs,
                                    modifier = Modifier.height(140.dp),
                                    title = "Build Logs"
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

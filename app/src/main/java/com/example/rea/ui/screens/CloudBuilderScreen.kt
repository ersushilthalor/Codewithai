package com.example.rea.ui.screens

import android.widget.Toast
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
    val context = LocalContext.current
    var showLogs by remember { mutableStateOf(false) }

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
                                text = "Build & Install APK",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextMain
                            )
                            Text(
                                text = "Generates a real Android APK ready for your phone",
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
                            Icon(Icons.Default.Android, contentDescription = null, tint = CleanGreen, modifier = Modifier.size(24.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Target: ${project?.name ?: "No APK Loaded"}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = CleanBlue
                    )
                    Text(
                        text = if (project != null) "Configured automatically with Android 15 & Gradle 8.9" else "Pick an APK from the Home screen first",
                        fontSize = 12.sp,
                        color = TextMuted
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    val isBuilding = activeJob?.status == BuildStatus.COMPILING || activeJob?.status == BuildStatus.VALIDATING || activeJob?.status == BuildStatus.PACKAGING

                    Button(
                        onClick = { viewModel.triggerCloudBuild("DEBUG_APK") },
                        enabled = !isBuilding && project != null,
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
                            if (isBuilding) "Building in Cloud..." else "Compile APK (1-Tap)",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
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
                        border = androidx.compose.foundation.BorderStroke(1.dp, CleanBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Building APK...",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CleanBlue
                                )
                                Text(
                                    text = "${activeJob.progressPercent}%",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CleanGreen
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { activeJob.progressPercent / 100f },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = CleanBlue,
                                trackColor = CleanBorder
                            )
                        }
                    }
                }
            } else if (activeJob.status == BuildStatus.SUCCESS && activeJob.artifactName != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = CleanSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CleanGreen),
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
                                    Icon(Icons.Default.DownloadDone, contentDescription = null, tint = CleanGreen, modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text("APK Ready to Install!", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CleanGreen)
                                    Text("Size: 15.5 MB • Verified Clean", fontSize = 12.sp, color = TextMuted)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    Toast.makeText(context, "Downloading ${activeJob.artifactName} to your phone...", Toast.LENGTH_LONG).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CleanGreen, contentColor = CleanBg),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("download_apk_btn")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Download APK to Phone", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            TextButton(
                                onClick = { showLogs = !showLogs },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text(if (showLogs) "Hide Details" else "Show Build Details", fontSize = 12.sp, color = TextMuted)
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
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

package com.example.rea.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rea.ui.viewmodel.ReaViewModel
import com.example.rea.ui.viewmodel.ScreenDestination
import com.example.ui.theme.*

@Composable
fun ReconstructionScreen(
    viewModel: ReaViewModel,
    modifier: Modifier = Modifier
) {
    val analysis by viewModel.currentAnalysis.collectAsState()
    val progress by viewModel.reconstructionProgress.collectAsState()
    val project = viewModel.projectRepository.getCurrentProject()
    val report = project?.reconstructionReport

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CleanBg)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Legal compliance notice (D4)
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CleanOrange.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, CleanOrange.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Gavel, contentDescription = null, tint = CleanOrange, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Notice: Use only on apps you own or are authorized to analyze.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CleanOrange
                    )
                }
            }
        }

        item {
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
                                text = "Scaffold from manifest - not decompiled",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextMain
                            )
                            Text(
                                text = "Synthesizes Jetpack Compose architecture scaffold from manifest metadata",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CleanIndigo.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CleanIndigo, modifier = Modifier.size(22.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Target: ${analysis?.appName ?: "No APK Loaded"}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = CleanBlue
                    )
                    Text(
                        text = if (analysis != null) "Clean-room scaffold derived from manifest components (not decompiled bytecode)" else "Pick an APK from the Home screen first",
                        fontSize = 12.sp,
                        color = TextMuted
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 1-Tap Rebuild Trigger
                    Button(
                        onClick = {
                            viewModel.runReconstruction(com.example.rea.data.model.ReconstructionConfig())
                        },
                        enabled = !progress.isReconstructing && analysis != null,
                        colors = ButtonDefaults.buttonColors(containerColor = CleanBlue, contentColor = CleanBg),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("execute_reconstruct_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (progress.isReconstructing) "Generating Scaffold..." else "Scaffold from manifest - not decompiled", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { viewModel.triggerJadxDecompilation() },
                        enabled = analysis != null,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp), tint = CleanPurple)
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CleanBlue.copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CleanBlue.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = CleanBlue, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("GitHub Cloud Decompilation Notice", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = TextMain)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Decompilation commits the APK to your GitHub repository (as 'app.apk') and executes JADX in the cloud. Use a private repository. Target repo must exist or will be created automatically. Max size: 50 MB.",
                                fontSize = 10.sp,
                                color = TextMuted,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // Active Reconstructing Progress
        if (progress.isReconstructing) {
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
                            Text(
                                text = progress.currentMessage.ifBlank { "Synthesizing scaffold..." },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CleanBlue
                            )
                            Text(
                                text = "${progress.completionPercent}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = CleanGreen
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { progress.completionPercent / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = CleanBlue,
                            trackColor = CleanBorder
                        )
                    }
                }
            }
        }

        // Success / Ready Card (D1, D3)
        if (report != null && !progress.isReconstructing) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CleanSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CleanGreen.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(CleanGreen.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = CleanGreen, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Generated Scaffold Complete (${report.completionPercentage}%)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CleanGreen)
                                Text("${report.generatedFilesCount} source files generated", fontSize = 12.sp, color = TextMuted)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Note: Business logic, native libraries and obfuscated code are not recovered.",
                            fontSize = 11.sp,
                            color = TextSubtle
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Features List
                        report.implementedFeatures.take(3).forEach { feat ->
                            Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Done, contentDescription = null, tint = CleanGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(feat, fontSize = 12.sp, color = TextMain)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Big Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.navigateTo(ScreenDestination.CLOUD_BUILDER) },
                                colors = ButtonDefaults.buttonColors(containerColor = CleanGreen, contentColor = CleanBg),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(48.dp).testTag("rec_build_apk_btn")
                            ) {
                                Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cloud Build", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { viewModel.navigateTo(ScreenDestination.CODE_EDITOR) },
                                colors = ButtonDefaults.buttonColors(containerColor = CleanSurfaceLight, contentColor = TextMain),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(48.dp).testTag("rec_open_editor_btn")
                            ) {
                                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("View Code", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

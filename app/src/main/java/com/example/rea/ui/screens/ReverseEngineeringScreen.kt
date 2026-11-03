package com.example.rea.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rea.data.model.ComponentType
import com.example.rea.ui.viewmodel.ReaViewModel
import com.example.rea.ui.viewmodel.ScreenDestination
import com.example.ui.theme.*

@Composable
fun ReverseEngineeringScreen(
    viewModel: ReaViewModel,
    modifier: Modifier = Modifier
) {
    val analysis by viewModel.currentAnalysis.collectAsState()
    val progress by viewModel.analysisProgress.collectAsState()
    val context = LocalContext.current
    val app = analysis

    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.runAnalysisOnUri(it) }
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
            // App Banner
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CleanSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, CleanBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CleanBlue.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Android, contentDescription = null, tint = CleanBlue, modifier = Modifier.size(28.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = app?.appName ?: "No App Selected",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMain
                                )
                                Text(
                                    text = app?.packageName ?: "Tap 'Change APK' below",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        val hasHighRisk = app != null && app.securityFindings.any { it.severity == "CRITICAL" || it.severity == "HIGH" }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (hasHighRisk) CleanOrange.copy(alpha = 0.15f) else CleanGreen.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (hasHighRisk) Icons.Default.Warning else Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = if (hasHighRisk) CleanOrange else CleanGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (app == null) "Ready" else if (hasHighRisk) "Flagged" else "Parsed",
                                    fontSize = 11.sp,
                                    color = if (hasHighRisk) CleanOrange else CleanGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Reconstruct & Change APK Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.runReconstruction(com.example.rea.data.model.ReconstructionConfig())
                                viewModel.navigateTo(ScreenDestination.RECONSTRUCTION)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CleanBlue, contentColor = CleanBg),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("re_reconstruct_nav_btn")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Rebuild with AI", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = { apkPickerLauncher.launch("*/*") },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMain),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CleanBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(48.dp).testTag("re_select_apk_btn")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select APK", fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = { viewModel.runSelfAnalysis() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CleanGreen),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CleanGreen.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(48.dp).testTag("re_inspect_self_btn")
                        ) {
                            Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Inspect Self", fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Legal & Authorization Notice (D4)
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CleanSurfaceLight,
                border = androidx.compose.foundation.BorderStroke(1.dp, CleanBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = CleanBlue, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Notice: Use only on apps you own or are authorized to analyze.",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
        }

        if (app != null) {
            // Key Highlights Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HighlightBox(
                        title = "Screens",
                        value = "${app.activities.size} Views",
                        icon = Icons.Default.PhoneAndroid,
                        color = CleanBlue,
                        modifier = Modifier.weight(1f)
                    )

                    HighlightBox(
                        title = "Code Base",
                        value = app.detectedFrameworks.firstOrNull() ?: "Android Native",
                        icon = Icons.Default.Code,
                        color = CleanIndigo,
                        modifier = Modifier.weight(1f)
                    )

                    HighlightBox(
                        title = "Compatibility",
                        value = "API ${app.minSdk}-${app.targetSdk}",
                        icon = Icons.Default.CheckCircle,
                        color = CleanGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Technical Details Card (B3, B6)
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CleanSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CleanBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Technical Details", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                        Spacer(modifier = Modifier.height(10.dp))

                        val sizeStr = if (app.apkSizeBytes > 1024 * 1024) String.format("%.1f MB", app.apkSizeBytes / (1024.0 * 1024.0)) else "${app.apkSizeBytes / 1024} KB"

                        DetailRow("Version", "${app.versionName} (${app.versionCode})")
                        DetailRow("Compile SDK", if (app.compileSdk > 0) "API ${app.compileSdk}" else "n/a")
                        DetailRow("File Size", sizeStr)
                        DetailRow("SHA-256", app.sha256.take(16) + "...")
                        DetailRow("Signing", app.signingStatus)
                        if (!app.signerCertSha256.isNullOrBlank()) {
                            DetailRow("Signer Cert SHA", app.signerCertSha256.take(16) + "...")
                        }
                        DetailRow("Debuggable", if (app.debuggable) "Yes (warning)" else "No")
                        DetailRow("Cleartext Traffic", if (app.usesCleartextTraffic) "Allowed (warning)" else "Disallowed")
                        DetailRow("Allow Backup", if (app.allowBackup) "True" else "False")
                        if (app.obfuscationLevel.isNotBlank()) {
                            DetailRow("Obfuscation", app.obfuscationLevel)
                        }
                    }
                }
            }

            // Limitations / Warnings list if any
            if (app.limitationsList.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = CleanOrange.copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CleanOrange.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = CleanOrange, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Analysis Limitations", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CleanOrange)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            app.limitationsList.forEach { item ->
                                Text("• $item", fontSize = 11.sp, color = TextMuted, modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }
            }

            // Security Audit Findings (B8)
            if (app.securityFindings.isNotEmpty()) {
                item {
                    Text("Security Audit (Local Heuristics)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextMain)
                }

                items(app.securityFindings) { finding ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CleanSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CleanBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val severityColor = when (finding.severity.uppercase()) {
                                    "CRITICAL", "HIGH" -> CleanOrange
                                    "MEDIUM" -> CleanOrange
                                    else -> CleanBlue
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = severityColor.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = finding.severity,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = severityColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(finding.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(finding.reason, fontSize = 11.sp, color = TextMuted)
                        }
                    }
                }
            }

            // Screens Found
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Screens in this App (${app.activities.size})", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextMain)
            }

            items(app.activities) { act ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CleanSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CleanBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CleanBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ScreenLockPortrait, contentDescription = null, tint = CleanBlue, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = act.name.substringAfterLast('.'),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMain
                            )
                            Text(
                                text = if (act.isLauncher) "Main Starting Screen" else "Internal Screen",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                        if (act.isLauncher) {
                            Surface(shape = RoundedCornerShape(6.dp), color = CleanGreen.copy(alpha = 0.15f)) {
                                Text("Home Screen", fontSize = 10.sp, color = CleanGreen, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }

            // Permissions Found
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Features & Permissions (${app.permissions.size})", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextMain)
            }

            items(app.permissions.take(8)) { perm ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CleanSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CleanBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = CleanGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = perm.name.removePrefix("android.permission.").replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextMain
                            )
                            Text(
                                text = perm.description,
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        } else {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CleanSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CleanBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(CleanSurfaceLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TextMuted, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No APK Selected", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextMain)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Select an external APK from device storage, or run real on-device inspection on this app itself.",
                            fontSize = 12.sp,
                            color = TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.runSelfAnalysis() },
                            colors = ButtonDefaults.buttonColors(containerColor = CleanBlue, contentColor = CleanBg),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp).testTag("re_inspect_self_cta")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Inspect Current App (Live Self-Analysis)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
fun HighlightBox(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CleanSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, CleanBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMain)
            Text(title, fontSize = 10.sp, color = TextMuted)
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = TextMuted)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
    }
}

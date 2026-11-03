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
                                    text = analysis?.appName ?: "No App Selected",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMain
                                )
                                Text(
                                    text = analysis?.packageName ?: "Tap 'Change APK' below",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CleanGreen.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = CleanGreen, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Verified Safe", fontSize = 11.sp, color = CleanGreen, fontWeight = FontWeight.Bold)
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
                    }
                }
            }
        }

        val app = analysis
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
                        value = "Jetpack Compose",
                        icon = Icons.Default.Code,
                        color = CleanIndigo,
                        modifier = Modifier.weight(1f)
                    )

                    HighlightBox(
                        title = "Compatibility",
                        value = "Android 8 - 15",
                        icon = Icons.Default.CheckCircle,
                        color = CleanGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Screens Found
            item {
                Text("Screens in this App", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextMain)
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
                Text("Features & Permissions", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextMain)
            }

            items(app.permissions.take(4)) { perm ->
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
                            text = "Tap 'Select APK' above or use the AI Chat on the Home screen to select an APK from your phone storage.",
                            fontSize = 12.sp,
                            color = TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
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

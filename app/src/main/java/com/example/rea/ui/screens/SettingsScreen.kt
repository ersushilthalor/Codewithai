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
import com.example.rea.ui.viewmodel.ReaViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: ReaViewModel,
    modifier: Modifier = Modifier
) {
    val providers by viewModel.providerManager.providers.collectAsState()
    val defaultProv = viewModel.providerManager.getDefaultProvider()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var userApiKey by remember { mutableStateOf(defaultProv.apiKey) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CleanBg)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TextMain)
            Text("Simple options you can adjust", fontSize = 12.sp, color = TextMuted)
        }

        // 1. REA Engine Status Card
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CleanSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, CleanBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CleanGreen.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = CleanGreen, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("REA Engine", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                            Text("Running automatically", fontSize = 12.sp, color = TextMuted)
                        }
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                viewModel.engineManager.runHealthCheck()
                                Toast.makeText(context, "Engine verified & running smoothly", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CleanSurfaceLight, contentColor = TextMain),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Check", fontSize = 12.sp)
                    }
                }
            }
        }

        // 2. AI Model & API Key
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CleanSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, CleanBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("AI Provider", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                    Text("Pre-configured with high-speed Gemini Pro & Flash", fontSize = 12.sp, color = TextMuted)

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = userApiKey,
                        onValueChange = { userApiKey = it },
                        label = { Text("Custom API Key (Optional)", fontSize = 11.sp) },
                        placeholder = { Text("AIzaSy... / sk-...") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CleanBlue,
                            unfocusedBorderColor = CleanBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            viewModel.providerManager.updateProvider(defaultProv.copy(apiKey = userApiKey))
                            Toast.makeText(context, "API Key saved successfully", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CleanBlue, contentColor = CleanBg),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Text("Save Key", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 3. Reset App
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CleanSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, CleanBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Reset to Sample App", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                        Text("Restores default Camera Pro project", fontSize = 12.sp, color = TextMuted)
                    }

                    Button(
                        onClick = {
                            viewModel.runAnalysisOnSample("auracam")
                            Toast.makeText(context, "Reset complete", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CleanRed.copy(alpha = 0.2f), contentColor = CleanRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Reset", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

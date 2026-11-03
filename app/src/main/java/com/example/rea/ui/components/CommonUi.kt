package com.example.rea.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rea.data.model.EngineStatus
import com.example.rea.ui.viewmodel.ScreenDestination
import com.example.ui.theme.*

@Composable
fun ReaTopBar(
    currentScreen: ScreenDestination,
    engineStatus: EngineStatus,
    activeProviderName: String,
    onNavigateToSettings: () -> Unit,
    onNavigateToBuilder: () -> Unit
) {
    Surface(
        color = CleanBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CleanBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AutoFixHigh,
                        contentDescription = "Logo",
                        tint = CleanBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "REA",
                            color = CleanBlue,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Studio",
                            color = TextMain,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(CleanGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ready • Auto-Pilot Active",
                            fontSize = 11.sp,
                            color = TextMuted,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Settings Icon button
                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CleanSurface)
                        .testTag("topbar_settings_btn")
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "Settings",
                        tint = TextMain,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ReaBottomNavigation(
    currentScreen: ScreenDestination,
    onNavigate: (ScreenDestination) -> Unit
) {
    NavigationBar(
        containerColor = CleanSurface,
        tonalElevation = 8.dp,
        modifier = Modifier.border(width = 1.dp, color = CleanBorder)
    ) {
        val items = listOf(
            Triple(ScreenDestination.DASHBOARD, Icons.Default.Home, "Home"),
            Triple(ScreenDestination.REVERSE_ENGINEER, Icons.Default.Search, "Inspect"),
            Triple(ScreenDestination.CODE_EDITOR, Icons.Default.Code, "Code & AI"),
            Triple(ScreenDestination.CLOUD_BUILDER, Icons.Default.Download, "Build APK")
        )

        items.forEach { (destination, icon, label) ->
            val isSelected = when (destination) {
                ScreenDestination.DASHBOARD -> currentScreen == ScreenDestination.DASHBOARD || currentScreen == ScreenDestination.PROJECTS
                ScreenDestination.REVERSE_ENGINEER -> currentScreen == ScreenDestination.REVERSE_ENGINEER || currentScreen == ScreenDestination.RECONSTRUCTION
                ScreenDestination.CODE_EDITOR -> currentScreen == ScreenDestination.CODE_EDITOR || currentScreen == ScreenDestination.AI_AGENT
                ScreenDestination.CLOUD_BUILDER -> currentScreen == ScreenDestination.CLOUD_BUILDER
                else -> currentScreen == destination
            }

            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(destination) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isSelected) CleanBlue else TextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) CleanBlue else TextMuted
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = CleanBlue.copy(alpha = 0.15f)
                ),
                modifier = Modifier.testTag("nav_${destination.name.lowercase()}")
            )
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CleanSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, CleanBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextMain
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = TextMuted
            )
        }
    }
}

@Composable
fun TerminalLogView(
    logs: List<String>,
    modifier: Modifier = Modifier,
    title: String = "Log Activity"
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CleanSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, CleanBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Text(
                text = title,
                fontSize = 11.sp,
                color = TextMuted,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            logs.takeLast(5).forEach { log ->
                Text(
                    text = log.replace("[EXEC] ", "• ").replace("[OK]", "✓"),
                    fontSize = 11.sp,
                    color = if (log.contains("OK") || log.contains("SUCCESS")) CleanGreen else TextMain,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

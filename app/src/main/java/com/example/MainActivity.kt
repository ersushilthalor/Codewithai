package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rea.ui.components.ReaBottomNavigation
import com.example.rea.ui.components.ReaTopBar
import com.example.rea.ui.screens.*
import com.example.rea.ui.viewmodel.ReaViewModel
import com.example.rea.ui.viewmodel.ScreenDestination
import com.example.ui.theme.CleanBg
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: ReaViewModel = viewModel()
                val currentScreen by viewModel.currentScreen.collectAsState()
                val engineState by viewModel.engineManager.engineState.collectAsState()
                val defaultProvider = viewModel.providerManager.getDefaultProvider()

                // System back button returns to Dashboard
                if (currentScreen != ScreenDestination.DASHBOARD) {
                    BackHandler {
                        viewModel.navigateTo(ScreenDestination.DASHBOARD)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = CleanBg,
                    topBar = {
                        ReaTopBar(
                            currentScreen = currentScreen,
                            engineStatus = engineState.status,
                            activeProviderName = defaultProvider.name,
                            onNavigateToSettings = { viewModel.navigateTo(ScreenDestination.SETTINGS) },
                            onNavigateToBuilder = { viewModel.navigateTo(ScreenDestination.CLOUD_BUILDER) }
                        )
                    },
                    bottomBar = {
                        ReaBottomNavigation(
                            currentScreen = currentScreen,
                            onNavigate = { destination ->
                                viewModel.navigateTo(destination)
                            }
                        )
                    }
                ) { innerPadding ->
                    val contentModifier = Modifier.padding(innerPadding)
                    when (currentScreen) {
                        ScreenDestination.DASHBOARD -> DashboardScreen(viewModel = viewModel, modifier = contentModifier)
                        ScreenDestination.PROJECTS -> ProjectsScreen(viewModel = viewModel, modifier = contentModifier)
                        ScreenDestination.REVERSE_ENGINEER -> ReverseEngineeringScreen(viewModel = viewModel, modifier = contentModifier)
                        ScreenDestination.RECONSTRUCTION -> ReconstructionScreen(viewModel = viewModel, modifier = contentModifier)
                        ScreenDestination.AI_AGENT -> AgentScreen(viewModel = viewModel, modifier = contentModifier)
                        ScreenDestination.CODE_EDITOR -> CodeWorkspaceScreen(viewModel = viewModel, modifier = contentModifier)
                        ScreenDestination.CLOUD_BUILDER -> CloudBuilderScreen(viewModel = viewModel, modifier = contentModifier)
                        ScreenDestination.SETTINGS -> SettingsScreen(viewModel = viewModel, modifier = contentModifier)
                    }
                }
            }
        }
    }
}

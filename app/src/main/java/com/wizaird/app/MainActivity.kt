package com.wizaird.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wizaird.app.data.AiSettings
import com.wizaird.app.data.settingsFlow
import com.wizaird.app.ui.HomeScreen
import com.wizaird.app.ui.NewProjectScreen
import com.wizaird.app.ui.ProjectsScreen
import com.wizaird.app.ui.SettingsScreen
import com.wizaird.app.ui.theme.WizairdTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        setContent {
            val settings by settingsFlow(this).collectAsState(initial = AiSettings())
            val darkMode = settings.darkMode

            // Light icons on dark bg, dark icons on light bg
            insetsController.isAppearanceLightStatusBars = !darkMode
            insetsController.isAppearanceLightNavigationBars = !darkMode

            WizairdTheme(darkMode = darkMode) {
                val nav = rememberNavController()
                NavHost(
                    navController = nav,
                    startDestination = "home",
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable("home") {
                        HomeScreen(
                            onSettingsClick = { nav.navigate("settings") },
                            onProjectsClick = { nav.navigate("projects") },
                            onNewProjectClick = { nav.navigate("new_project") }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(onBack = { nav.popBackStack() })
                    }
                    composable("projects") {
                        ProjectsScreen(
                            onBack = { nav.popBackStack() },
                            onNewProject = { nav.navigate("new_project") }
                        )
                    }
                    composable("new_project") {
                        NewProjectScreen(onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}

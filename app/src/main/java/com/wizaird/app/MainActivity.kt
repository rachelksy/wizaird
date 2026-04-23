package com.wizaird.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wizaird.app.ui.HomeScreen
import com.wizaird.app.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val nav = rememberNavController()
            NavHost(
                navController = nav,
                startDestination = "home",
                modifier = Modifier.fillMaxSize().safeDrawingPadding()
            ) {
                composable("home") {
                    HomeScreen(onSettingsClick = { nav.navigate("settings") })
                }
                composable("settings") {
                    SettingsScreen(onBack = { nav.popBackStack() })
                }
            }
        }
    }
}

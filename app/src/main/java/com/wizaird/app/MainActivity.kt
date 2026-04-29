package com.wizaird.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wizaird.app.data.AiSettings
import com.wizaird.app.data.settingsFlow
import com.wizaird.app.ui.ChatScreen
import com.wizaird.app.ui.ExistingChatScreen
import com.wizaird.app.ui.HomeScreen
import com.wizaird.app.ui.NewProjectScreen
import com.wizaird.app.ui.NoteScreen
import com.wizaird.app.ui.ProjectScreen
import com.wizaird.app.ui.ProjectSettingsScreen
import com.wizaird.app.ui.ProjectTab
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

            // Update window background to match app theme — prevents flash on navigation
            LaunchedEffect(darkMode) {
                window.decorView.setBackgroundColor(
                    if (darkMode) AndroidColor.parseColor("#111111")
                    else AndroidColor.parseColor("#F2E6C7")
                )
            }

            // Light icons on dark bg, dark icons on light bg
            insetsController.isAppearanceLightStatusBars = !darkMode
            insetsController.isAppearanceLightNavigationBars = !darkMode

            WizairdTheme(darkMode = darkMode) {
                val nav = rememberNavController()
                val fastFadeIn  = fadeIn(tween(200))
                val fastFadeOut = fadeOut(tween(200))
                NavHost(
                    navController = nav,
                    startDestination = "home",
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { fastFadeIn },
                    exitTransition = { fastFadeOut },
                    popEnterTransition = { fastFadeIn },
                    popExitTransition = { fastFadeOut }
                ) {
                    composable("home") {
                        HomeScreen(
                            onSettingsClick = { nav.navigate("settings") },
                            onProjectsClick = { nav.navigate("projects") },
                            onNewProjectClick = { nav.navigate("new_project") },
                            onProjectClick = { id -> nav.navigate("project/${id}") }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            onBack = { nav.popBackStack() },
                            initialDarkMode = darkMode
                        )
                    }
                    composable("projects") {
                        ProjectsScreen(
                            onBack = { nav.popBackStack() },
                            onNewProject = { nav.navigate("new_project") },
                            onProjectClick = { id -> nav.navigate("project/${id}") }
                        )
                    }
                    composable("new_project") {
                        NewProjectScreen(onBack = { nav.popBackStack() })
                    }
                    composable("project/{id}") { backStackEntry ->
                        val id = backStackEntry.arguments?.getString("id") ?: ""
                        ProjectScreen(
                            projectId = id,
                            onBack = { nav.popBackStack() },
                            onSettingsClick = { nav.navigate("project_settings/${id}") },
                            onNewChatClick = { nav.navigate("chat/${id}") },
                            onChatClick = { chatId -> nav.navigate("chat/${id}/${chatId}") },
                            onNoteClick = { noteId ->
                                // Switch the back stack to the notes variant so returning
                                // from the note lands on the Notes tab, not Chats.
                                nav.navigate("project/${id}/notes") {
                                    popUpTo("project/${id}") { inclusive = true }
                                }
                                nav.navigate("note/${id}/${noteId}")
                            },
                            onNewNoteClick = {
                                nav.navigate("project/${id}/notes") {
                                    popUpTo("project/${id}") { inclusive = true }
                                }
                                nav.navigate("note/${id}/new")
                            },
                            initialTab = ProjectTab.CHATS
                        )
                    }
                    composable("project/{id}/notes") { backStackEntry ->
                        val id = backStackEntry.arguments?.getString("id") ?: ""
                        ProjectScreen(
                            projectId = id,
                            onBack = { nav.popBackStack() },
                            onSettingsClick = { nav.navigate("project_settings/${id}") },
                            onNewChatClick = {
                                // Swap back stack to chats route so returning lands on Chats tab
                                nav.navigate("project/${id}") {
                                    popUpTo("project/${id}/notes") { inclusive = true }
                                }
                                nav.navigate("chat/${id}")
                            },
                            onChatClick = { chatId ->
                                nav.navigate("project/${id}") {
                                    popUpTo("project/${id}/notes") { inclusive = true }
                                }
                                nav.navigate("chat/${id}/${chatId}")
                            },
                            onNoteClick = { noteId -> nav.navigate("note/${id}/${noteId}") },
                            onNewNoteClick = { nav.navigate("note/${id}/new") },
                            initialTab = ProjectTab.NOTES
                        )
                    }
                    composable("note/{projectId}/{noteId}") { backStackEntry ->
                        val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                        val noteId    = backStackEntry.arguments?.getString("noteId") ?: ""
                        NoteScreen(
                            projectId = projectId,
                            noteId    = noteId,
                            onBack    = { nav.popBackStack() }
                        )
                    }
                    composable("project_settings/{id}") { backStackEntry ->
                        val id = backStackEntry.arguments?.getString("id") ?: ""
                        ProjectSettingsScreen(
                            projectId = id,
                            onBack = { nav.popBackStack() }
                        )
                    }
                    composable("chat/{projectId}") { backStackEntry ->
                        val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                        ChatScreen(
                            projectId = projectId,
                            onBack = { nav.popBackStack() },
                            onChatCreated = { chatId ->
                                // Navigate to the newly created chat
                                nav.navigate("chat/${projectId}/${chatId}") {
                                    popUpTo("chat/${projectId}") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("chat/{projectId}/{chatId}") { backStackEntry ->
                        val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                        val chatId    = backStackEntry.arguments?.getString("chatId") ?: ""
                        ExistingChatScreen(
                            projectId = projectId,
                            chatId    = chatId,
                            onBack    = { nav.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

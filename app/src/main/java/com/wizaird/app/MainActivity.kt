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
import com.wizaird.app.data.migrateGlossaryToConceptsIfNeeded
import com.wizaird.app.data.settingsFlow
import com.wizaird.app.ui.AccountSettingsScreen
import com.wizaird.app.ui.ApiSettingsScreen
import com.wizaird.app.ui.AppSettingsScreen
import com.wizaird.app.ui.BackupScreen
import com.wizaird.app.ui.ChatScreen
import com.wizaird.app.ui.ChatSettingsScreen
import com.wizaird.app.ui.ExistingChatScreen
import com.wizaird.app.ui.HomeScreen
import com.wizaird.app.ui.InsightChatScreen
import com.wizaird.app.ui.NewProjectScreen
import com.wizaird.app.ui.ConceptEditScreen
import com.wizaird.app.ui.ConceptPageScreen
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

            // One-time: turn old glossary words into concepts (old glossary data is kept)
            LaunchedEffect(Unit) {
                migrateGlossaryToConceptsIfNeeded(this@MainActivity)
            }

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
                            onProjectClick = { id -> nav.navigate("project/${id}") },
                            onChatCreated = { projectId, chatId ->
                                nav.navigate("chat/${projectId}/${chatId}")
                            },
                            onNewConceptClick = { projectId ->
                                nav.navigate("project/${projectId}/concepts") {
                                    popUpTo("home") { inclusive = false }
                                }
                                nav.navigate("concept/${projectId}/new")
                            }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            onBack = { nav.popBackStack() },
                            onAccountSettings = { nav.navigate("account_settings") },
                            onAppSettings = { nav.navigate("app_settings") },
                            onApiSettings = { nav.navigate("api_settings") },
                            onBackupSettings = { nav.navigate("backup_settings") }
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
                    // Project hub: one route per tab so Back returns to the right tab.
                    // Opening something that belongs to another tab first swaps this
                    // route for that tab's route, then navigates.
                    fun tabRoute(id: String, tab: ProjectTab) = when (tab) {
                        ProjectTab.CONCEPTS -> "project/${id}/concepts"
                        ProjectTab.NOTES -> "project/${id}/notes"
                        ProjectTab.INSIGHTS -> "project/${id}/insights"
                        ProjectTab.CHATS -> "project/${id}"
                    }
                    fun openFromTab(id: String, from: ProjectTab, target: ProjectTab, destination: String) {
                        val fromRoute = tabRoute(id, from)
                        val targetRoute = tabRoute(id, target)
                        if (fromRoute != targetRoute) {
                            nav.navigate(targetRoute) {
                                popUpTo(fromRoute) { inclusive = true }
                            }
                        }
                        nav.navigate(destination)
                    }
                    listOf(
                        ProjectTab.CHATS to "project/{id}",
                        ProjectTab.CONCEPTS to "project/{id}/concepts",
                        ProjectTab.INSIGHTS to "project/{id}/insights",
                        ProjectTab.NOTES to "project/{id}/notes"
                    ).forEach { (tab, pattern) ->
                        composable(pattern) { backStackEntry ->
                            val id = backStackEntry.arguments?.getString("id") ?: ""
                            ProjectScreen(
                                projectId = id,
                                onBack = { nav.popBackStack() },
                                onSettingsClick = { nav.navigate("project_settings/${id}") },
                                onNewChatClick = { openFromTab(id, tab, ProjectTab.CHATS, "chat/${id}") },
                                onChatClick = { chatId -> openFromTab(id, tab, ProjectTab.CHATS, "chat/${id}/${chatId}") },
                                onNoteClick = { noteId -> openFromTab(id, tab, ProjectTab.NOTES, "note/${id}/${noteId}") },
                                onNewNoteClick = { openFromTab(id, tab, ProjectTab.NOTES, "note/${id}/new") },
                                onInsightChatClick = { insightId ->
                                    openFromTab(id, tab, ProjectTab.INSIGHTS, "chat/${id}/insight/${insightId}")
                                },
                                onNewConceptClick = { openFromTab(id, tab, ProjectTab.CONCEPTS, "concept/${id}/new") },
                                onConceptClick = { conceptId ->
                                    openFromTab(id, tab, ProjectTab.CONCEPTS, "concept/${id}/${conceptId}")
                                },
                                onNoteConceptClick = { conceptId ->
                                    // Tag pill on a note card: Back should return to Notes
                                    openFromTab(id, tab, ProjectTab.NOTES, "concept/${id}/${conceptId}")
                                },
                                onInsightNewConceptClick = { openFromTab(id, tab, ProjectTab.INSIGHTS, "concept/${id}/new") },
                                onInsightConceptClick = { conceptId ->
                                    // Concept pill on an insight: Back should return to Insights
                                    openFromTab(id, tab, ProjectTab.INSIGHTS, "concept/${id}/${conceptId}")
                                },
                                onEditConceptClick = { conceptId ->
                                    openFromTab(id, tab, ProjectTab.CONCEPTS, "concept/${id}/${conceptId}/edit")
                                },
                                initialTab = tab
                            )
                        }
                    }
                    composable("concept/{projectId}/new") { backStackEntry ->
                        val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                        val openConcept: (String) -> Unit = { conceptId ->
                            nav.navigate("concept/${projectId}/${conceptId}") {
                                popUpTo("concept/${projectId}/new") { inclusive = true }
                            }
                        }
                        ConceptEditScreen(
                            projectId = projectId,
                            conceptId = null,
                            onBack = { nav.popBackStack() },
                            onSaved = openConcept,
                            onOpenExisting = openConcept
                        )
                    }
                    composable("concept/{projectId}/{conceptId}") { backStackEntry ->
                        val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                        val conceptId = backStackEntry.arguments?.getString("conceptId") ?: ""
                        ConceptPageScreen(
                            conceptId = conceptId,
                            onBack = { nav.popBackStack() },
                            onEdit = { nav.navigate("concept/${projectId}/${conceptId}/edit") },
                            onChatClick = { chatProjectId, chatId -> nav.navigate("chat/${chatProjectId}/${chatId}") },
                            onInsightClick = { insightProjectId, insightId -> nav.navigate("chat/${insightProjectId}/insight/${insightId}") },
                            onNoteClick = { noteId -> nav.navigate("note/${projectId}/${noteId}") },
                            onConceptClick = { otherId -> nav.navigate("concept/${projectId}/${otherId}") }
                        )
                    }
                    composable("concept/{projectId}/{conceptId}/edit") { backStackEntry ->
                        val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                        val conceptId = backStackEntry.arguments?.getString("conceptId") ?: ""
                        ConceptEditScreen(
                            projectId = projectId,
                            conceptId = conceptId,
                            onBack = { nav.popBackStack() },
                            onSaved = { nav.popBackStack() },
                            onOpenExisting = { otherId ->
                                nav.navigate("concept/${projectId}/${otherId}") {
                                    popUpTo("concept/${projectId}/${conceptId}/edit") { inclusive = true }
                                }
                            }
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
                    composable("account_settings") {
                        AccountSettingsScreen(
                            onBack = { nav.popBackStack() }
                        )
                    }
                    composable("app_settings") {
                        AppSettingsScreen(
                            onBack = { nav.popBackStack() },
                            initialDarkMode = darkMode
                        )
                    }
                    composable("api_settings") {
                        ApiSettingsScreen(
                            onBack = { nav.popBackStack() },
                            initialDarkMode = darkMode
                        )
                    }
                    composable("backup_settings") {
                        BackupScreen(
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
                            },
                            onNewConceptClick = {
                                nav.navigate("project/${projectId}/concepts") {
                                    popUpTo("chat/${projectId}") { inclusive = false }
                                }
                                nav.navigate("concept/${projectId}/new")
                            }
                        )
                    }
                    composable("chat/{projectId}/insight/{insightId}") { backStackEntry ->
                        val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                        val insightId = backStackEntry.arguments?.getString("insightId") ?: ""
                        InsightChatScreen(
                            projectId = projectId,
                            insightId = insightId,
                            onBack = { nav.popBackStack() },
                            onChatCreated = { chatId ->
                                // Navigate to the newly created chat
                                nav.navigate("chat/${projectId}/${chatId}") {
                                    popUpTo("chat/${projectId}/insight/${insightId}") { inclusive = true }
                                }
                            },
                            onNewConceptClick = {
                                nav.navigate("project/${projectId}/concepts") {
                                    popUpTo("chat/${projectId}/insight/${insightId}") { inclusive = false }
                                }
                                nav.navigate("concept/${projectId}/new")
                            }
                        )
                    }
                    composable("chat/{projectId}/{chatId}") { backStackEntry ->
                        val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                        val chatId    = backStackEntry.arguments?.getString("chatId") ?: ""
                        ExistingChatScreen(
                            projectId = projectId,
                            chatId    = chatId,
                            onBack    = { nav.popBackStack() },
                            onSettingsClick = { nav.navigate("chat_settings/${projectId}/${chatId}") },
                            onNewConceptClick = {
                                nav.navigate("project/${projectId}/concepts") {
                                    popUpTo("chat/${projectId}/${chatId}") { inclusive = false }
                                }
                                nav.navigate("concept/${projectId}/new")
                            }
                        )
                    }
                    composable("chat_settings/{projectId}/{chatId}") { backStackEntry ->
                        val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                        val chatId    = backStackEntry.arguments?.getString("chatId") ?: ""
                        ChatSettingsScreen(
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

package com.yourname.coquettemobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.yourname.coquettemobile.ui.chat.ChatScreen
import com.yourname.coquettemobile.ui.settings.SettingsScreen
import com.yourname.coquettemobile.ui.settings.SystemPromptsScreen
import com.yourname.coquettemobile.ui.settings.DevSettingsScreen
import com.yourname.coquettemobile.ui.personalities.PersonalityManagementScreen
import com.yourname.coquettemobile.ui.personalities.PersonalityEditScreen
import com.yourname.coquettemobile.ui.personalities.PersonalityManagementViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import com.yourname.coquettemobile.ui.history.ConversationHistoryScreen
import com.yourname.coquettemobile.ui.theme.CoquetteMobileTheme
import com.yourname.coquettemobile.core.service.CoquetteBackgroundService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start background service for connection persistence
        CoquetteBackgroundService.start(this)

        setContent {
            CoquetteMobileTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                ) {
                    CoquetteApp()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop background service when app is fully destroyed
        if (isFinishing) {
            CoquetteBackgroundService.stop(this)
        }
    }
}

@Composable
fun CoquetteApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "chat") {
        composable(
            "chat?conversationId={conversationId}",
            arguments = listOf(navArgument("conversationId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId")
            ChatScreen(
                onSettingsClick = { navController.navigate("settings") },
                       onHistoryClick = { navController.navigate("history") },
                       onModuleManagementClick = { navController.navigate("personality_management") },
                       conversationId = conversationId
            )
        }
        composable("settings") {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                           onManagePersonalitiesClick = { navController.navigate("personality_management") },
                           onSystemPromptsClick = { navController.navigate("system_prompts") },
                           onDeveloperClick = { navController.navigate("developer") }
            )
        }
        composable("personality_management") {
            PersonalityManagementScreen(
                onBackClick = { navController.popBackStack() },
                onEditPersonality = { personality ->
                    val route = if (personality != null) {
                        "personality_edit/${personality.id}"
                    } else {
                        "personality_edit/new"
                    }
                    navController.navigate(route)
                }
            )
        }
        composable(
            "personality_edit/{personalityId}",
            arguments = listOf(navArgument("personalityId") {
                type = NavType.StringType
                nullable = false
            })
        ) { backStackEntry ->
            val personalityId = backStackEntry.arguments?.getString("personalityId")
            val viewModel: PersonalityManagementViewModel = hiltViewModel()
            val personalities = viewModel.personalities.collectAsStateWithLifecycle().value
            val personality = if (personalityId == "new") null else personalities.find { it.id == personalityId }
            
            PersonalityEditScreen(
                personality = personality,
                onBackClick = { navController.popBackStack() },
                onSave = { updatedPersonality ->
                    if (personality == null) {
                        // New personality - use insert
                        viewModel.insertPersonality(updatedPersonality)
                    } else {
                        // Existing personality - use update
                        viewModel.updatePersonality(updatedPersonality)
                    }
                    navController.popBackStack()
                }
            )
        }
        composable("system_prompts") {
            SystemPromptsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable("developer") {
            val context = androidx.compose.ui.platform.LocalContext.current
            val appPreferences = remember {
                dagger.hilt.android.EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    com.yourname.coquettemobile.di.AppModule.AppPreferencesEntryPoint::class.java
                ).appPreferences()
            }
            DevSettingsScreen(
                onBackClick = { navController.popBackStack() },
                appPreferences = appPreferences
            )
        }
        composable("history") {
            ConversationHistoryScreen(
                onBackClick = { navController.popBackStack() },
                                      onConversationClick = { conversationId ->
                                          navController.navigate("chat?conversationId=$conversationId") {
                                              popUpTo("chat") { inclusive = true }
                                          }
                                      }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CoquetteAppPreview() {
    CoquetteMobileTheme {
        CoquetteApp()
    }
}

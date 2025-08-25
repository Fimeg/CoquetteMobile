package com.yourname.coquettemobile.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yourname.coquettemobile.core.prompt.SystemPromptManager
import dagger.hilt.android.EntryPointAccessors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemPromptsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val systemPromptManager = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SystemPromptManagerEntryPoint::class.java
        ).systemPromptManager()
    }

    var corePersonalityPrompt by remember { mutableStateOf(systemPromptManager.corePersonalityPrompt) }
    var showResetDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Prompts") },
                      navigationIcon = {
                          IconButton(onClick = onBackClick) {
                              Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                          }
                      },
                      actions = {
                          IconButton(
                              onClick = { showResetDialog = true }
                          ) {
                              Icon(Icons.Default.Refresh, contentDescription = "Reset to defaults")
                          }
                      }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
               verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Edit the core system prompt that controls the AI's base behavior. Changes are saved automatically.",
                 style = MaterialTheme.typography.bodyMedium,
                 color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            // Core Personality Prompt
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                       verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Core Personality Prompt",
                         style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Defines the AI's base personality, tone, and behavior in all conversations.",
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    OutlinedTextField(
                        value = corePersonalityPrompt,
                        onValueChange = {
                            corePersonalityPrompt = it
                            systemPromptManager.corePersonalityPrompt = it
                        },
                        modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                                      placeholder = { Text("Enter core personality prompt...") }
                    )
                }
            }

            // Export/Import section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                       verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Backup & Restore",
                         style = MaterialTheme.typography.titleMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                // TODO: Export to file or clipboard
                                val exported = systemPromptManager.exportPrompts()
                                // Copy to clipboard or save file
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Export")
                        }

                        OutlinedButton(
                            onClick = {
                                // TODO: Import from file or clipboard
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Import")
                        }
                    }
                }
            }
        }
    }

    // Reset confirmation dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset to Defaults") },
                    text = { Text("This will reset the core system prompt to its default value. Your custom prompt will be lost.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                systemPromptManager.resetToDefaults()
                                corePersonalityPrompt = systemPromptManager.corePersonalityPrompt
                                showResetDialog = false
                            }
                        ) {
                            Text("Reset")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetDialog = false }) {
                            Text("Cancel")
                        }
                    }
        )
    }
}

// Entry point for dependency injection
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface SystemPromptManagerEntryPoint {
    fun systemPromptManager(): SystemPromptManager
}

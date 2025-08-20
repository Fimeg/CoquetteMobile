package com.yourname.coquettemobile.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.yourname.coquettemobile.ui.chat.ChatViewModel
import com.yourname.coquettemobile.core.preferences.AppPreferences
import dagger.hilt.android.EntryPointAccessors
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onManagePersonalitiesClick: () -> Unit = {},
    onSystemPromptsClick: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val appPreferences = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.yourname.coquettemobile.di.AppModule.AppPreferencesEntryPoint::class.java
        ).appPreferences()
    }
    
    val selectedPersonality by viewModel.selectedPersonality.collectAsStateWithLifecycle()
    val enableStreaming by viewModel.isStreamingEnabled.collectAsStateWithLifecycle()
    val availableModels by viewModel.availableModels.collectAsStateWithLifecycle()
    
    var enableSubconsciousReasoning by remember { mutableStateOf(appPreferences.enableSubconsciousReasoning) }
    var enableModelRouting by remember { mutableStateOf(appPreferences.enableModelRouting) }
    var showModelUsed by remember { mutableStateOf(appPreferences.showModelUsed) }
    var enableSplitBrain by remember { mutableStateOf(appPreferences.enableSplitBrain) }
    var plannerModel by remember { mutableStateOf(appPreferences.plannerModel) }
    var personalityModel by remember { mutableStateOf(appPreferences.personalityModel) }
    
    var showPlannerDropdown by remember { mutableStateOf(false) }
    var showPersonalityDropdown by remember { mutableStateOf(false) }
    
    var showServerDialog by remember { mutableStateOf(false) }
    var serverUrlInput by remember { mutableStateOf(appPreferences.ollamaServerUrl) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            // Connection Settings
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Connection",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    // Ollama Server URL Setting
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showServerDialog = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ollama Server",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = appPreferences.ollamaServerUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit server URL",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    
                    Text(
                        text = "Available Models: Gemma 3, DeepSeek R1, Context7",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // AI Behavior Settings
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "AI Behavior",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    // Subconscious Reasoning
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Subconscious Reasoning",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Deep analysis for complex queries",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = enableSubconsciousReasoning,
                            onCheckedChange = { 
                                enableSubconsciousReasoning = it
                                appPreferences.enableSubconsciousReasoning = it
                            }
                        )
                    }

                    // Model Routing
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Intelligent Model Routing",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Auto-select best model for each query",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = enableModelRouting,
                            onCheckedChange = { 
                                enableModelRouting = it
                                appPreferences.enableModelRouting = it
                            }
                        )
                    }

                    // Show Model Used
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Show Model Used",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Display which model generated responses",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = showModelUsed,
                            onCheckedChange = { 
                                showModelUsed = it
                                appPreferences.showModelUsed = it
                            }
                        )
                    }

                    // Streaming Responses
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Stream Responses",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Receive responses as they're generated",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = enableStreaming,
                            onCheckedChange = { viewModel.toggleStreaming(it) }
                        )
                    }
                }
            }

            // Split-Brain Architecture
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Split-Brain Architecture",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    // Enable Split-Brain
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Enable Split-Brain Mode",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Use separate models for planning and personality",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = enableSplitBrain,
                            onCheckedChange = { 
                                enableSplitBrain = it
                                appPreferences.enableSplitBrain = it
                            }
                        )
                    }

                    if (enableSplitBrain) {
                        // Planner Model Selection
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Planner Model",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Small model for tool decisions (fast)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = { showPlannerDropdown = true }
                            ) {
                                Text(plannerModel)
                                DropdownMenu(
                                    expanded = showPlannerDropdown,
                                    onDismissRequest = { showPlannerDropdown = false }
                                ) {
                                    availableModels.forEach { model ->
                                        DropdownMenuItem(
                                            text = { Text(model) },
                                            onClick = {
                                                plannerModel = model
                                                appPreferences.plannerModel = model
                                                showPlannerDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Personality Model Selection
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Personality Model",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Large model for conversation (expressive)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = { showPersonalityDropdown = true }
                            ) {
                                Text(personalityModel)
                                DropdownMenu(
                                    expanded = showPersonalityDropdown,
                                    onDismissRequest = { showPersonalityDropdown = false }
                                ) {
                                    availableModels.forEach { model ->
                                        DropdownMenuItem(
                                            text = { Text(model) },
                                            onClick = {
                                                personalityModel = model
                                                appPreferences.personalityModel = model
                                                showPersonalityDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Personality Management
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Personality Management",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onManagePersonalitiesClick() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Manage Personalities",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Add, edit, or delete AI personalities",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = "Manage personalities",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Prompt Management
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Prompt Management",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSystemPromptsClick() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Edit System Prompts",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Customize core personality, planner, and tool awareness",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = "Edit system prompts",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // App Information
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "App Information",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Text(
                        text = "Coquette Mobile v1.0.0",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Multi-model AI Assistant",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Built with Jetpack Compose & Material Design 3",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
    
    // Server URL Edit Dialog
    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text("Edit Ollama Server URL") },
            text = {
                Column {
                    Text(
                        text = "Enter the Ollama server URL:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = { serverUrlInput = it },
                        label = { Text("Server URL") },
                        placeholder = { Text("http://192.168.1.100:11434") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Examples: http://localhost:11434, http://192.168.1.100:11434",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        appPreferences.ollamaServerUrl = serverUrlInput.trim()
                        showServerDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        serverUrlInput = appPreferences.ollamaServerUrl // Reset to current value
                        showServerDialog = false 
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

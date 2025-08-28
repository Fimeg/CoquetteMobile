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
import androidx.compose.material3.Divider
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
import com.yourname.coquettemobile.di.AppModule
import dagger.hilt.android.EntryPointAccessors
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onManagePersonalitiesClick: () -> Unit = {},
    
    onDeveloperClick: () -> Unit = {},
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
    
    var showModelUsed by remember { mutableStateOf(appPreferences.showModelUsed) }
    
    
    var showServerDialog by remember { mutableStateOf(false) }
    var serverUrlInput by remember { mutableStateOf(appPreferences.ollamaServerUrl) }
    
    
    // Unity architecture settings
    var enableUnityMode by remember { mutableStateOf(selectedPersonality?.useUnifiedMode == true) }
    var unifiedModel by remember { mutableStateOf(selectedPersonality?.unifiedModel ?: "hf.co/janhq/Jan-v1-4B-GGUF:Q8_0") }
    var showUnityModelDropdown by remember { mutableStateOf(false) }
    var contextWarningThreshold by remember { mutableStateOf(32768) }

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

                    
                }
            }

            // Unity Architecture
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Unity Architecture",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    // Unity Architecture Info (always enabled)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Unity Architecture: Active",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Single-brain reasoning with Jan models for optimal performance",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    // Unity Model Selection
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Unity Model",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Single model handles reasoning + tools + personality",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = { showUnityModelDropdown = true }
                            ) {
                                Text(unifiedModel.substringAfterLast("/").take(15) + if (unifiedModel.length > 15) "..." else "")
                                DropdownMenu(
                                    expanded = showUnityModelDropdown,
                                    onDismissRequest = { showUnityModelDropdown = false }
                                ) {
                                    // Jan and other capable models
                                    listOf(
                                        "hf.co/janhq/Jan-v1-4B-GGUF:Q8_0",
                                        "deepseek-r1:8b", 
                                        "qwen3:8b",
                                        "deepseek-r1:32b",
                                        "qwen3:30b"
                                    ).forEach { model ->
                                        DropdownMenuItem(
                                            text = { Text(model.substringAfterLast("/")) },
                                            onClick = {
                                                unifiedModel = model
                                                showUnityModelDropdown = false
                                                // TODO: Update personality's unifiedModel field
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Context Warning Threshold
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Context Warning Threshold",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Warn when context exceeds ${contextWarningThreshold} tokens",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = { /* TODO: Show context threshold dialog */ }
                            ) {
                                Text("${contextWarningThreshold}")
                            }
                        }
                }
            }

            // Error Recovery Configuration
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Error Recovery",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Error Recovery Model",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Model used for analyzing tool failures and generating recovery strategies",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = { /* TODO: Show error recovery model selector */ }
                        ) {
                            Text(
                                text = if (appPreferences.errorRecoveryModel.length > 25) 
                                    "${appPreferences.errorRecoveryModel.take(25)}..." 
                                else 
                                    appPreferences.errorRecoveryModel,
                                maxLines = 1
                            )
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
                    
                    Divider()
                    
                    // Developer Settings
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDeveloperClick() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Developer",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Advanced system configuration and prompts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = "Developer settings",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Debug & Logs
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Debug & Logs",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                // Get logger and clear logs  
                                val logger = dagger.hilt.android.EntryPointAccessors
                                    .fromApplication(context.applicationContext, AppModule.CoquetteLoggerEntryPoint::class.java)
                                    .logger()
                                logger.clearAllLogs()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Clear Debug Logs",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Remove all stored debug logs (kept for 7 days)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
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

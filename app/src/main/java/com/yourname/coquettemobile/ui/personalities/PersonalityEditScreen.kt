package com.yourname.coquettemobile.ui.personalities

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yourname.coquettemobile.core.database.entities.Personality

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalityEditScreen(
    personality: Personality?,
    onBackClick: () -> Unit,
    onSave: (Personality) -> Unit,
    viewModel: PersonalityManagementViewModel = hiltViewModel()
) {
    // Default templates for new personalities
    val defaultSystemPrompt = """You are a helpful AI assistant with a friendly and professional demeanor.
You provide clear, accurate, and useful responses to user queries.
Always be respectful and aim to be genuinely helpful.""".trimIndent()
    
    

    // --- REVISED STATE INITIALIZATION ---

    // 1. Initialize all state variables with default/empty values first.
    //    This prevents them from being locked into a blank state from a null `personality`.
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf(defaultSystemPrompt) }
    
    var modules by remember { mutableStateOf(mutableMapOf<String, String>()) }

    // 2. Use LaunchedEffect. This block will run once the `personality` object is
    //    actually loaded and passed to the screen, correctly populating the state.
    LaunchedEffect(personality) {
        if (personality != null) {
            name = personality.name
            description = personality.description
            systemPrompt = personality.systemPrompt.takeIf { it.isNotBlank() } ?: defaultSystemPrompt
            
            modules = personality.modules.toMutableMap()
        }
    }
    
    // State for adding new modules
    var showAddModuleDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (personality == null) "Add Personality" else "Edit ${personality?.name ?: name}") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (name.isNotBlank() && systemPrompt.isNotBlank()) {
                                val updatedPersonality = (personality ?: Personality(
                                    id = java.util.UUID.randomUUID().toString(),
                                    name = "",
                                    emoji = "🤖",
                                    systemPrompt = "",
                                    description = ""
                                )).copy(
                                    name = name,
                                    description = description,
                                    systemPrompt = systemPrompt,
                                    
                                    modules = modules.toMap()
                                )
                                onSave(updatedPersonality)
                            }
                        },
                        enabled = name.isNotBlank() && systemPrompt.isNotBlank()
                    ) {
                        Text("SAVE")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Basic Information",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description") },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            item {
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "System Prompt",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Text(
                            text = "Define the personality's core behavior and response style",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        
                        OutlinedTextField(
                            value = systemPrompt,
                            onValueChange = { systemPrompt = it },
                            label = { Text("System Prompt") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                    }
                }
            }
            
            

            
            
            item {
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Personality Modules",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedButton(
                                onClick = { showAddModuleDialog = true }
                            ) {
                                Text("Add Module")
                            }
                        }
                        
                        if (modules.isEmpty()) {
                            Text(
                                text = "No modules added yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            items(modules.keys.toList()) { moduleName ->
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = moduleName,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    modules = modules.toMutableMap().apply { remove(moduleName) }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete module",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        
                        var moduleContent by remember { mutableStateOf(modules[moduleName] ?: "") }
                        LaunchedEffect(modules[moduleName]) {
                            moduleContent = modules[moduleName] ?: ""
                        }
                        
                        OutlinedTextField(
                            value = moduleContent,
                            onValueChange = {
                                moduleContent = it
                                modules = modules.toMutableMap().apply { put(moduleName, it) }
                            },
                            label = { Text("Module Content") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                        )
                    }
                }
            }
        }
    }

    if (showAddModuleDialog) {
        AddModuleDialog(
            onDismiss = { showAddModuleDialog = false },
            onAdd = { name, content ->
                modules = modules.toMutableMap().apply { put(name, content) }
                showAddModuleDialog = false
            }
        )
    }
}

@Composable
private fun AddModuleDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var moduleName by remember { mutableStateOf("") }
    var moduleContent by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Module") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = moduleName,
                    onValueChange = { moduleName = it },
                    label = { Text("Module Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = moduleContent,
                    onValueChange = { moduleContent = it },
                    label = { Text("Module Content") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (moduleName.isNotBlank()) {
                        onAdd(moduleName, moduleContent)
                    }
                },
                enabled = moduleName.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
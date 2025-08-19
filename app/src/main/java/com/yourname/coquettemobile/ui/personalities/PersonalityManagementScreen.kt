package com.yourname.coquettemobile.ui.personalities

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.coquettemobile.core.database.entities.Personality

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalityManagementScreen(
    onBackClick: () -> Unit,
    viewModel: PersonalityManagementViewModel = hiltViewModel()
) {
    val personalities by viewModel.personalities.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPersonality by remember { mutableStateOf<Personality?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Personalities") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Personality")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(personalities) { personality ->
                PersonalityCard(
                    personality = personality,
                    onEdit = { editingPersonality = it },
                    onDelete = { viewModel.deletePersonality(it) },
                    onSetDefault = { viewModel.setAsDefault(it.id) }
                )
            }
        }
    }

    if (showAddDialog) {
        PersonalityEditDialog(
            personality = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, emoji, description, systemPrompt ->
                viewModel.addPersonality(name, emoji, description, systemPrompt)
                showAddDialog = false
            }
        )
    }

    editingPersonality?.let { personality ->
        PersonalityEditDialog(
            personality = personality,
            onDismiss = { editingPersonality = null },
            onSave = { name, emoji, description, systemPrompt ->
                viewModel.updatePersonality(
                    personality.copy(
                        name = name,
                        emoji = emoji,
                        description = description,
                        systemPrompt = systemPrompt
                    )
                )
                editingPersonality = null
            }
        )
    }
}

@Composable
fun PersonalityCard(
    personality: Personality,
    onEdit: (Personality) -> Unit,
    onDelete: (Personality) -> Unit,
    onSetDefault: (Personality) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = personality.emoji,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Column {
                        Text(
                            text = personality.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (personality.isDefault) {
                            Text(
                                text = "Default",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                Row {
                    IconButton(
                        onClick = { onSetDefault(personality) }
                    ) {
                        Icon(
                            imageVector = if (personality.isDefault) Icons.Default.Star else Icons.Outlined.Star,
                            contentDescription = "Set as default",
                            tint = if (personality.isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { onEdit(personality) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { onDelete(personality) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = personality.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalityEditDialog(
    personality: Personality?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(personality?.name ?: "") }
    var emoji by remember { mutableStateOf(personality?.emoji ?: "🤖") }
    var description by remember { mutableStateOf(personality?.description ?: "") }
    var systemPrompt by remember { mutableStateOf(personality?.systemPrompt ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (personality == null) "Add Personality" else "Edit Personality")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it },
                    label = { Text("Emoji") },
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
                
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("System Prompt") },
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && systemPrompt.isNotBlank()) {
                        onSave(name, emoji, description, systemPrompt)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
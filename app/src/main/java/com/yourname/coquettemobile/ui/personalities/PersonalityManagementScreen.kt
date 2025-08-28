package com.yourname.coquettemobile.ui.personalities

import androidx.compose.foundation.clickable
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
    onEditPersonality: (Personality?) -> Unit,
    viewModel: PersonalityManagementViewModel = hiltViewModel()
) {
    val personalities by viewModel.personalities.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Personalities") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                onEditPersonality(null) // Add new personality
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Personality")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(personalities) { personality ->
                PersonalityCard(
                    personality = personality,
                    onEdit = { onEditPersonality(it) },
                    onDelete = { viewModel.deletePersonality(it) },
                    onSetDefault = { viewModel.setAsDefault(it.id) }
                )
            }
        }
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
        modifier = Modifier.fillMaxWidth().clickable { onEdit(personality) }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = personality.name, 
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (personality.isDefault) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "DEFAULT",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = personality.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onEdit(personality) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Edit")
                }
                
                if (!personality.isDefault) {
                    OutlinedButton(
                        onClick = { onSetDefault(personality) }
                    ) {
                        Text("Set Default")
                    }
                }
                
                OutlinedButton(
                    onClick = { onDelete(personality) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            }
        }
    }
}


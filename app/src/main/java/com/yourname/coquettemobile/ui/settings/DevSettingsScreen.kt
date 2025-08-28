package com.yourname.coquettemobile.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourname.coquettemobile.core.preferences.AppPreferences
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevSettingsScreen(
    onBackClick: () -> Unit,
    appPreferences: AppPreferences
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                Text(
                    text = "System Behavior",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            // System Date Section
            item {
                SystemDateSection(appPreferences = appPreferences)
            }
            
            // Unity Architecture Status Section  
            item {
                UnityArchitectureSection(appPreferences = appPreferences)
            }
            
            // Tool Configuration Section
            item {
                ToolConfigurationSection(appPreferences = appPreferences)
            }
            
            // System Prompts Section
            item {
                SystemPromptsSection(appPreferences = appPreferences)
            }
        }
    }
}

@Composable
private fun SystemDateSection(appPreferences: AppPreferences) {
    var includeSystemDate by remember { mutableStateOf(appPreferences.includeSystemDate) }
    var systemDatePrompt by remember { mutableStateOf(appPreferences.systemDatePrompt ?: getDefaultSystemDatePrompt()) }
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "System Date Context",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Text(
                text = "Add current date/time context to AI responses",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Include System Date")
                Switch(
                    checked = includeSystemDate,
                    onCheckedChange = { 
                        includeSystemDate = it
                        appPreferences.includeSystemDate = it
                    }
                )
            }
            
            if (includeSystemDate) {
                // Preview
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Preview:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = formatSystemDatePrompt(systemDatePrompt),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                
                TextButton(
                    onClick = { isExpanded = !isExpanded }
                ) {
                    Text(if (isExpanded) "Hide Custom Prompt" else "Customize Prompt")
                }
                
                if (isExpanded) {
                    OutlinedTextField(
                        value = systemDatePrompt,
                        onValueChange = { 
                            systemDatePrompt = it
                            appPreferences.systemDatePrompt = it
                        },
                        label = { Text("System Date Prompt Template") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        placeholder = { Text("Use {current_date} and {current_time} placeholders") }
                    )
                }
            }
        }
    }
}

@Composable
private fun UnityArchitectureSection(appPreferences: AppPreferences) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Unity Architecture Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Text(
                text = "CoquetteMobile is running Unity single-brain architecture",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Status indicator
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✅",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Unity Architecture Active",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Single Jan model handles reasoning + tool selection + personality",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Current configuration 
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Dynamic Context Sizing")
                    Text(
                        "Automatically calculates optimal context per model",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Active",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tool Chaining")
                    Text(
                        "WebFetch → Extractor → Summarizer chains",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Active",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Error Recovery")
                    Text(
                        "Intelligent tool failure analysis and recovery",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Active",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ToolConfigurationSection(appPreferences: AppPreferences) {
    var enableToolOllama by remember { mutableStateOf(appPreferences.enableToolOllamaServer) }
    var toolOllamaUrl by remember { mutableStateOf(appPreferences.toolOllamaServerUrl) }
    var toolOllamaModel by remember { mutableStateOf(appPreferences.toolOllamaModel) }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Tool Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Text(
                text = "Configure separate server for tool operations",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Tool Server")
                    Text(
                        "Use separate Ollama server for tool operations",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enableToolOllama,
                    onCheckedChange = { 
                        enableToolOllama = it
                        appPreferences.enableToolOllamaServer = it
                    }
                )
            }
            
            if (enableToolOllama) {
                OutlinedTextField(
                    value = toolOllamaUrl,
                    onValueChange = { 
                        toolOllamaUrl = it
                        appPreferences.toolOllamaServerUrl = it
                    },
                    label = { Text("Tool Ollama Server URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = toolOllamaModel,
                    onValueChange = { 
                        toolOllamaModel = it
                        appPreferences.toolOllamaModel = it
                    },
                    label = { Text("Tool Model") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
    }
}

@Composable
private fun SystemPromptsSection(appPreferences: AppPreferences) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "System Prompts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Customize all internal AI prompts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { isExpanded = !isExpanded }) {
                    Text(if (isExpanded) "Collapse" else "Expand")
                }
            }
            
            if (isExpanded) {
                SystemPromptField(
                    label = "Unity System Prompt",
                    value = appPreferences.unifiedReasoningPrompt,
                    onValueChange = { appPreferences.unifiedReasoningPrompt = it },
                    placeholder = "Core Unity architecture system prompt with tool descriptions and JSON format"
                )
                
                SystemPromptField(
                    label = "Error Recovery Prompt", 
                    value = appPreferences.errorRecoveryPrompt,
                    onValueChange = { appPreferences.errorRecoveryPrompt = it },
                    placeholder = "Prompt for analyzing tool failures and suggesting recovery strategies"
                )
            }
        }
    }
}

@Composable
private fun SystemPromptField(
    label: String,
    value: String?,
    onValueChange: (String?) -> Unit,
    placeholder: String
) {
    var text by remember { mutableStateOf(value ?: "") }
    var isExpanded by remember { mutableStateOf(false) }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            TextButton(onClick = { isExpanded = !isExpanded }) {
                Text(if (isExpanded) "Hide" else "Edit")
            }
        }
        
        if (isExpanded) {
            OutlinedTextField(
                value = text,
                onValueChange = { 
                    text = it
                    onValueChange(it.takeIf { it.isNotBlank() })
                },
                label = { Text(label) },
                placeholder = { Text(placeholder) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 8
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = { 
                        text = ""
                        onValueChange(null)
                    }
                ) {
                    Text("Reset to Default")
                }
            }
        } else if (!value.isNullOrBlank()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Text(
                    text = value.take(100) + if (value.length > 100) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

private fun getDefaultSystemDatePrompt(): String {
    return "Current date: {current_date}\nCurrent time: {current_time}\n\nUse this information when relevant to provide contextual responses."
}

private fun formatSystemDatePrompt(template: String): String {
    val currentDate = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())
    val currentTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
    
    return template
        .replace("{current_date}", currentDate)
        .replace("{current_time}", currentTime)
}
package com.yourname.coquettemobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown  
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.yourname.coquettemobile.core.models.ChatMessage
import com.yourname.coquettemobile.core.models.MessageType
import com.yourname.coquettemobile.core.database.entities.Personality

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit = {},
    conversationId: String? = null,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val selectedPersonality by viewModel.selectedPersonality.collectAsStateWithLifecycle()
    val availablePersonalities by viewModel.availablePersonalities.collectAsStateWithLifecycle()
    val availableModels by viewModel.availableModels.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    
    var messageText by remember { mutableStateOf("") }
    var showPersonalityMenu by remember { mutableStateOf(false) }
    var showModelMenu by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val lazyListState = rememberLazyListState()

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            lazyListState.animateScrollToItem(chatMessages.size - 1)
        }
    }
    
    // Handle conversation switching
    LaunchedEffect(conversationId) {
        if (conversationId != null) {
            viewModel.switchToConversation(conversationId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Model selector in center
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box {
                            IconButton(
                                onClick = { showModelMenu = true }
                            ) {
                                Text(
                                    text = if (selectedModel == "auto") "🤖 Auto" else selectedModel,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            DropdownMenu(
                                expanded = showModelMenu,
                                onDismissRequest = { showModelMenu = false }
                            ) {
                                availableModels.forEach { model ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (model == "auto") "🤖 Auto Route" else model,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        onClick = {
                                            viewModel.updateSelectedModel(model)
                                            showModelMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    // Hamburger menu
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("🆕 New Chat") },
                                onClick = {
                                    viewModel.clearChat()
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("📜 History") },
                                onClick = {
                                    onHistoryClick()
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("⚙️ Settings") },
                                onClick = {
                                    onSettingsClick()
                                    showMenu = false
                                }
                            )
                        }
                    }
                },
                actions = {
                    // Personality selector on right
                    Box {
                        IconButton(
                            onClick = { showPersonalityMenu = true }
                        ) {
                            Text(
                                text = selectedPersonality?.emoji ?: "🤖",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        DropdownMenu(
                            expanded = showPersonalityMenu,
                            onDismissRequest = { showPersonalityMenu = false }
                        ) {
                            availablePersonalities.forEach { personality ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = personality.emoji,
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                            Text(text = personality.name)
                                        }
                                    },
                                    onClick = {
                                        viewModel.updatePersonality(personality)
                                        showPersonalityMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Chat messages
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chatMessages) { message ->
                    ChatMessageBubble(message = message)
                }
            }

            // Input area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type your message...") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (messageText.isNotBlank()) {
                                viewModel.sendMessage(messageText)
                                messageText = ""
                                focusManager.clearFocus()
                            }
                        }
                    ),
                    trailingIcon = {
                        if (messageText.isNotBlank()) {
                            IconButton(
                                onClick = { messageText = "" }
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            viewModel.sendMessage(messageText)
                            messageText = ""
                            focusManager.clearFocus()
                        }
                    },
                    enabled = messageText.isNotBlank() && !isLoading,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Send",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageBubble(message: ChatMessage) {
    val isUserMessage = message.type == MessageType.USER
    val backgroundColor = when (message.type) {
        MessageType.USER -> MaterialTheme.colorScheme.primaryContainer
        MessageType.AI -> MaterialTheme.colorScheme.surfaceVariant
        MessageType.ERROR -> MaterialTheme.colorScheme.errorContainer
        MessageType.SYSTEM -> MaterialTheme.colorScheme.surface
    }
    
    val textColor = when (message.type) {
        MessageType.USER -> MaterialTheme.colorScheme.onPrimaryContainer
        MessageType.AI -> MaterialTheme.colorScheme.onSurfaceVariant
        MessageType.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        MessageType.SYSTEM -> MaterialTheme.colorScheme.onSurface
    }
    
    var isThinkingExpanded by remember { mutableStateOf(false) }
    
    // Auto-expand thinking when there's thinking content
    LaunchedEffect(message.thinkingContent) {
        if (!message.thinkingContent.isNullOrBlank()) {
            isThinkingExpanded = true
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUserMessage) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(
                topStart = if (isUserMessage) 16.dp else 4.dp,
                topEnd = if (isUserMessage) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            )
        ) {
            Surface(
                color = backgroundColor,
                contentColor = textColor
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    // Thinking section (if available)
                    if (!message.thinkingContent.isNullOrBlank() && message.type == MessageType.AI) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isThinkingExpanded = !isThinkingExpanded }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🤔 Thinking process",
                                style = MaterialTheme.typography.labelMedium,
                                color = textColor.copy(alpha = 0.8f)
                            )
                            Icon(
                                imageVector = if (isThinkingExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isThinkingExpanded) "Collapse" else "Expand",
                                modifier = Modifier.size(16.dp),
                                tint = textColor.copy(alpha = 0.6f)
                            )
                        }
                        
                        if (isThinkingExpanded) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Surface(
                                    color = backgroundColor.copy(alpha = 0.5f),
                                    contentColor = textColor.copy(alpha = 0.9f)
                                ) {
                                    Text(
                                        text = message.thinkingContent,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    // Main message content
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    if (message.modelUsed != null && message.type == MessageType.AI) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "via ${message.modelUsed}",
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

package com.yourname.coquettemobile.ui.chat

import androidx.compose.animation.core.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.yourname.coquettemobile.core.models.ChatMessage
import com.yourname.coquettemobile.core.models.MessageType
import com.yourname.coquettemobile.core.models.AiMessageState
import com.yourname.coquettemobile.ui.components.RichText
import com.yourname.coquettemobile.ui.components.StreamingRichText
import com.yourname.coquettemobile.ui.components.StreamingToolExecution
import com.yourname.coquettemobile.ui.components.StreamingImageDisplay
import com.yourname.coquettemobile.ui.components.StreamingThinkingIndicator
import com.yourname.coquettemobile.ui.components.CenteredThinkingIndicator
import com.yourname.coquettemobile.ui.components.ToolOutputDropdown
import com.yourname.coquettemobile.ui.components.ThinkingDropdown
import com.yourname.coquettemobile.ui.components.orchestration.ExecutionPlanPreviewCard
import com.yourname.coquettemobile.ui.components.orchestration.LiveOperationProgress
import com.yourname.coquettemobile.ui.components.CohesiveChatMessageBubble
import com.yourname.coquettemobile.ui.components.OperationPhase
import dagger.hilt.android.EntryPointAccessors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit = {},
    onModuleManagementClick: () -> Unit = {},
    conversationId: String? = null,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val appPreferences = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.yourname.coquettemobile.di.AppModule.AppPreferencesEntryPoint::class.java
        ).appPreferences()
    }
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val selectedPersonality by viewModel.selectedPersonality.collectAsStateWithLifecycle()
    val availablePersonalities by viewModel.availablePersonalities.collectAsStateWithLifecycle()
    val activeModules by viewModel.activeModules.collectAsStateWithLifecycle()

    var messageText by remember { mutableStateOf("") }
    var showPersonalityMenu by remember { mutableStateOf(false) }
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
                    Text(selectedPersonality?.name ?: "Coquette")
                },
                navigationIcon = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(" New Chat") },
                                onClick = {
                                    viewModel.clearChat()
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(" History") },
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        activeModules.forEach { moduleName ->
                            Card(
                                modifier = Modifier.padding(horizontal = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = moduleName,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        IconButton(onClick = onModuleManagementClick) {
                            Icon(Icons.Default.Add, contentDescription = "Module Management")
                        }
                        
                        Box {
                            IconButton(
                                onClick = { showPersonalityMenu = true }
                            ) {
                                Text(
                                    text = selectedPersonality?.emoji ?: "",
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
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chatMessages) { message ->
                    when (message.type) {
                        MessageType.USER -> UserMessageBubble(message = message)
                        MessageType.AI -> CohesiveChatMessageBubble(
                            message = message,
                            personalityName = selectedPersonality?.name ?: "AI",
                            showModelUsed = appPreferences.showModelUsed
                        )
                        MessageType.ERROR, MessageType.SYSTEM -> ChatMessageBubble(
                            message = message,
                            showModelUsed = appPreferences.showModelUsed
                        )
                        MessageType.IMAGE -> ImageBubble(message = message)
                        MessageType.THINKING, MessageType.TOOL_STATUS -> {}
                    }
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
fun UserMessageBubble(message: ChatMessage) {
    val backgroundColor = MaterialTheme.colorScheme.primaryContainer
    val textColor = MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 4.dp,
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
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        StreamingRichText(
                            content = message.content,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    showModelUsed: Boolean = true
) {
    val isUserMessage = message.type == MessageType.USER
    val backgroundColor = when (message.type) {
        MessageType.USER -> MaterialTheme.colorScheme.primaryContainer
        MessageType.AI -> MaterialTheme.colorScheme.surfaceVariant
        MessageType.ERROR -> MaterialTheme.colorScheme.errorContainer
        MessageType.SYSTEM -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surface
    }

    val textColor = when (message.type) {
        MessageType.USER -> MaterialTheme.colorScheme.onPrimaryContainer
        MessageType.AI -> MaterialTheme.colorScheme.onSurfaceVariant
        MessageType.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        MessageType.SYSTEM -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface
    }

    // UltraThink: Simplified UI - no complex state management needed

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUserMessage) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 380.dp) // Increased width for better tool display
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
                    // Use our new Evolving Thought Bubble
                    CohesiveChatMessageBubble(
                        message = message,
                        personalityName = "AI" // Simple default since selectedPersonality not available in this scope
                    )

                    // Model info with enhanced styling
                    if (message.modelUsed != null && message.type == MessageType.AI && showModelUsed) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "via ${message.modelUsed}",
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor.copy(alpha = 0.7f)
                            )
                            if (message.processingTime != null) {
                                Text(
                                    text = "${message.processingTime}ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textColor.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThinkingBubble(message: ChatMessage) {
    var isThinkingExpanded by remember { mutableStateOf(true) } // Default to expanded for real-time streaming
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            StreamingThinkingIndicator(
                personalityName = "AI",
                thinkingContent = message.content,
                isExpanded = isThinkingExpanded,
                onToggle = { isThinkingExpanded = !isThinkingExpanded },
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
fun ToolStatusBubble(message: ChatMessage) {
    val icon = when {
        message.content.startsWith("Running") -> "🛠️"
        message.content.startsWith("✓") -> "✅"
        else -> "⚠️"
    }
    val infiniteTransition = rememberInfiniteTransition(label = "tool_status")
    val alpha by infiniteTransition.animateFloat(
        initialValue = if (message.content.startsWith("Running")) 0.5f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = if (message.content.startsWith("Running")) RepeatMode.Reverse else RepeatMode.Restart
        ),
        label = "tool_alpha"
    )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .alpha(if (message.content.startsWith("Running")) alpha else 1f),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = icon, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun ImageBubble(message: ChatMessage) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            SubcomposeAsyncImage(
                model = message.content,
                contentDescription = "Image from AI: ${message.content}",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(MaterialTheme.colorScheme.errorContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error loading image",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            )
        }
    }
}

package com.prometheus.android.ui.assistant

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.prometheus.android.inference.ConversationManager
import com.prometheus.android.inference.ModelManager
import com.prometheus.android.ui.theme.LocalPrometheusColors
import com.prometheus.model.ChatMessage
import com.prometheus.model.EarthquakeEvent
import com.prometheus.prompt.SystemPrompts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    conversations: List<ConversationData>,
    activeIndex: Int,
    conversationManager: ConversationManager?,
    onConversationsChange: (List<ConversationData>) -> Unit,
    onActiveIndexChange: (Int) -> Unit,
    currentEvent: EarthquakeEvent? = null
) {
    val context = LocalContext.current
    val manager = remember { conversationManager ?: ConversationManager() }
    var query by remember { mutableStateOf("") }
    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isModelLoaded by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Initializing...") }
    var downloadProgress by remember { mutableStateOf(-1) }
    var isDownloading by remember { mutableStateOf(false) }
    var chatMode by remember { mutableStateOf("SURVIVAL_CHAT") }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val p = LocalPrometheusColors.current

    var showImageSourceDialog by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
            selectedImageBitmap = bitmap
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            selectedImageBitmap = bitmap
        }
    }

    // LOCAL state — bypass AnimatedContent barrier, langsung recompose
    var localConversations by remember { mutableStateOf(conversations) }

    val chatHistory by remember { derivedStateOf { localConversations.getOrNull(activeIndex)?.messages ?: emptyList() } }

    LaunchedEffect(Unit) {
        isModelLoaded = ModelManager.isLoaded
        statusMessage = ModelManager.statusMessage
    }

    val showDownload = !isModelLoaded && downloadProgress < 0 && statusMessage.startsWith("Model not found")

    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(chatHistory.lastIndex)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = p.surface,
                modifier = Modifier.width(300.dp)
            ) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Conversations",
                    color = p.blue,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                HorizontalDivider(color = p.blue.copy(alpha = 0.15f))
                Button(
                    onClick = {
                        val newConv = ConversationData()
                        localConversations = localConversations + newConv
                        onConversationsChange(localConversations)
                        onActiveIndexChange(localConversations.lastIndex)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = p.blue,
                        contentColor = Color.Black
                    )
                ) {
                    Text("+ New conversation", fontWeight = FontWeight.Bold)
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(localConversations) { index, conv ->
                        val title = if (conv.messages.isNotEmpty()) {
                            conv.messages.firstOrNull { it.isUser }?.text?.take(40) ?: "Chat"
                        } else "New conversation"
                        val isActive = index == activeIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isActive) p.blue.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable {
                                    onActiveIndexChange(index)
                                    scope.launch { drawerState.close() }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = title,
                                color = if (isActive) p.blue else p.textPrimary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (localConversations.size > 1) {
                                IconButton(
                                    onClick = {
                                        localConversations[index].messages.forEach { msg ->
                                            msg.imagePath?.let { path -> File(path).delete() }
                                        }
                                        val updated = localConversations.toMutableList().apply { removeAt(index) }
                                        localConversations = updated
                                        onConversationsChange(updated)
                                        if (activeIndex >= updated.size) onActiveIndexChange(updated.size - 1)
                                        if (activeIndex < 0) {
                                            val reset = listOf(ConversationData())
                                            localConversations = reset
                                            onConversationsChange(reset)
                                            onActiveIndexChange(0)
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text("\u2716", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { scope.launch { if (drawerState.isClosed) drawerState.open() else drawerState.close() } }) {
                                Text("\u2630", color = p.blue, style = MaterialTheme.typography.titleMedium)
                            }
                            Text("Survival Assistant", color = p.blue)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = p.surface
                    )
                )
            },
            containerColor = p.background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isModelLoaded) Color.Green else Color(0xFFFFA500))
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = statusMessage,
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                ModeIndicatorBar(
                    currentMode = chatMode,
                    onModeChange = { chatMode = it }
                )

                if (chatHistory.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "\uD83D\uDCAC",
                                style = MaterialTheme.typography.displaySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "SURVIVAL ASSISTANT",
                                color = p.textPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Gemma 4  ·  on-device  ·  offline",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CapabilityPill(text = "\uD83E\uDDEA  first aid")
                                CapabilityPill(text = "\uD83C\uDFE0  shelter & evacuation")
                                CapabilityPill(text = "\uD83D\uDCA7  water & supplies")
                                CapabilityPill(text = "\u26A0\uFE0F  Indonesia hazards")
                            }
                            if (showDownload) {
                                Spacer(Modifier.height(16.dp))
                                val isPaused = isDownloading && downloadProgress >= 0 &&
                                    ModelManager.getDownloadProgress(context)?.isPaused == true
                                val btnColor = when {
                                    !isDownloading -> p.blue
                                    isPaused -> Color(0xFFFFA500).copy(alpha = 0.6f)
                                    else -> p.blue.copy(alpha = 0.6f)
                                }
                                val btnText = when {
                                    !isDownloading -> "\u2B07\uFE0F  DOWNLOAD MODEL (2.4 GB)"
                                    isPaused -> "\u25B6\uFE0F  Download Paused: $downloadProgress%"
                                    downloadProgress < 0 -> "\u23F3  Starting..."
                                    downloadProgress >= 100 -> "\u2705  Moving file..."
                                    else -> "\u23F8\uFE0F  Downloading: $downloadProgress%"
                                }
                                Button(
                                    onClick = {
                                        when {
                                            !isDownloading -> ModelManager.enqueueDownload(context)
                                            isPaused -> ModelManager.resumeDownload(context)
                                            else -> {
                                                val ok = ModelManager.pauseDownload(context)
                                                if (!ok) { ModelManager.cancelDownload(context) }
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .padding(horizontal = 16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = btnColor,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        if (isDownloading && downloadProgress in 0..99 && !isPaused) {
                                            LinearProgressIndicator(
                                                progress = { downloadProgress / 100f },
                                                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                                                color = p.blue.copy(alpha = 0.3f),
                                                trackColor = Color.Transparent
                                            )
                                        }
                                        Text(btnText, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(chatHistory, key = { it.id }) { message ->
                            ChatBubble(message = message)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .border(1.dp, p.blue.copy(alpha = 0.3f)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedImageBitmap != null) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .padding(2.dp)
                        ) {
                            Image(
                                bitmap = selectedImageBitmap!!.asImageBitmap(),
                                contentDescription = "Selected image",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .clickable { selectedImageBitmap = null },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("\u2716",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text(
                                when (chatMode) {
                                    "EMERGENCY_BRIEF" -> "Paste earthquake data for briefing..."
                                    else -> "Ask anything about survival..."
                                },
                                color = p.blue.copy(alpha = 0.5f)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = p.surface,
                            unfocusedContainerColor = p.surface,
                            focusedTextColor = p.blue,
                            unfocusedTextColor = p.blue,
                            cursorColor = p.blue,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        enabled = isModelLoaded
                    )
                    VerticalDivider(
                        modifier = Modifier.height(44.dp),
                        color = p.blue.copy(alpha = 0.2f)
                    )
                    TextButton(
                        onClick = { showImageSourceDialog = true },
                        enabled = isModelLoaded,
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Icon(
                            Icons.Filled.Image,
                            contentDescription = "Attach image",
                            tint = p.blue.copy(alpha = 0.6f)
                        )
                    }
                    TextButton(
                            onClick = {
                            val userText = query
                            val image = selectedImageBitmap
                            query = ""
                            selectedImageBitmap = null
                            val history = chatHistory
                            val oldSize = history.size
                            val aiIndex = oldSize + 1

                            // SYNCHRONOUS: show user message + loading bubble immediately
                            var currentList = localConversations.toMutableList()
                            currentList[activeIndex] = currentList[activeIndex].copy(
                                messages = history + ChatMessage(text = userText, isUser = true) + ChatMessage(text = "...", isUser = false)
                            )
                            localConversations = currentList
                            onConversationsChange(currentList)

                            scope.launch {
                                val savedPath = if (image != null) {
                                    withContext(Dispatchers.IO) { saveChatImage(context, image) }
                                } else null

                                // Update user message with imagePath if saved
                                if (savedPath != null) {
                                    currentList = currentList.toMutableList()
                                    val msgs = currentList[activeIndex].messages.toMutableList()
                                    if (oldSize < msgs.size) {
                                        msgs[oldSize] = ChatMessage(text = userText, isUser = true, imagePath = savedPath)
                                    }
                                    currentList[activeIndex] = currentList[activeIndex].copy(messages = msgs)
                                    localConversations = currentList
                                    onConversationsChange(currentList)
                                }

                                val sysPrompt = when (chatMode) {
                                    "EMERGENCY_BRIEF" -> SystemPrompts.EMERGENCY_BRIEFING
                                    else -> {
                                        val bmkgCtx = SystemPrompts.buildBmkgContext(currentEvent)
                                        if (bmkgCtx.isNotBlank()) "$bmkgCtx\n\n${SystemPrompts.GENERAL_PROMPT}"
                                        else SystemPrompts.GENERAL_PROMPT
                                    }
                                }

                                manager.sendMessage(userText, history, sysPrompt, savedPath) { text ->
                                    currentList = currentList.toMutableList()
                                    val msgs = currentList[activeIndex].messages.toMutableList()
                                    if (aiIndex < msgs.size) {
                                        msgs[aiIndex] = ChatMessage(text = text, isUser = false)
                                    }
                                    currentList[activeIndex] = currentList[activeIndex].copy(messages = msgs)
                                    localConversations = currentList
                                    onConversationsChange(currentList)
                                }
                            }
                        },
                        enabled = isModelLoaded && query.isNotBlank(),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (isModelLoaded) p.blue else p.surface,
                            contentColor = if (isModelLoaded) Color.Black else Color.Gray,
                            disabledContainerColor = p.surface,
                            disabledContentColor = Color.Gray
                        ),
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Text(
                            text = "\u2708\uFE0F",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }

    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = {
                Text("Choose Image Source",
                    color = p.blue,
                    fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showImageSourceDialog = false
                            cameraLauncher.launch(null)
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("\uD83D\uDCF7  Take Photo",
                            color = p.textPrimary,
                            fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                            onClick = {
                            val userText = query
                            val image = selectedImageBitmap
                            query = ""
                            selectedImageBitmap = null
                            val history = chatHistory
                            val oldSize = history.size
                            val aiIndex = oldSize + 1

                            // SYNCHRONOUS: show user message + loading bubble immediately
                            var currentList = localConversations.toMutableList()
                            currentList[activeIndex] = currentList[activeIndex].copy(
                                messages = history + ChatMessage(text = userText, isUser = true) + ChatMessage(text = "...", isUser = false)
                            )
                            localConversations = currentList
                            onConversationsChange(currentList)

                            scope.launch {
                                val savedPath = if (image != null) {
                                    withContext(Dispatchers.IO) { saveChatImage(context, image) }
                                } else null

                                // Update user message with imagePath if saved
                                if (savedPath != null) {
                                    currentList = currentList.toMutableList()
                                    val msgs = currentList[activeIndex].messages.toMutableList()
                                    if (oldSize < msgs.size) {
                                        msgs[oldSize] = ChatMessage(text = userText, isUser = true, imagePath = savedPath)
                                    }
                                    currentList[activeIndex] = currentList[activeIndex].copy(messages = msgs)
                                    localConversations = currentList
                                    onConversationsChange(currentList)
                                }

                                val sysPrompt = when (chatMode) {
                                    "EMERGENCY_BRIEF" -> SystemPrompts.EMERGENCY_BRIEFING
                                    else -> {
                                        val bmkgCtx = SystemPrompts.buildBmkgContext(currentEvent)
                                        if (bmkgCtx.isNotBlank()) "$bmkgCtx\n\n${SystemPrompts.GENERAL_PROMPT}"
                                        else SystemPrompts.GENERAL_PROMPT
                                    }
                                }

                                manager.sendMessage(userText, history, sysPrompt, savedPath) { text ->
                                    currentList = currentList.toMutableList()
                                    val msgs = currentList[activeIndex].messages.toMutableList()
                                    if (aiIndex < msgs.size) {
                                        msgs[aiIndex] = ChatMessage(text = text, isUser = false)
                                    }
                                    currentList[activeIndex] = currentList[activeIndex].copy(messages = msgs)
                                    localConversations = currentList
                                    onConversationsChange(currentList)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("\uD83D\uDDBC\uFE0F  Gallery",
                            color = p.textPrimary,
                            fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImageSourceDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = p.surface
        )
    }
}

@Composable
private fun ModeIndicatorBar(
    currentMode: String,
    onModeChange: (String) -> Unit
) {
    val p = LocalPrometheusColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(p.surface)
            .border(0.5.dp, p.blue.copy(alpha = 0.15f))
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        ModeChip(label = "SURVIVAL CHAT", active = currentMode == "SURVIVAL_CHAT", onClick = { onModeChange("SURVIVAL_CHAT") })
        Spacer(Modifier.width(8.dp))
        ModeChip(label = "EMERGENCY BRIEF", active = currentMode == "EMERGENCY_BRIEF", onClick = { onModeChange("EMERGENCY_BRIEF") })
    }
}

@Composable
private fun ModeChip(label: String, active: Boolean, onClick: () -> Unit) {
    val p = LocalPrometheusColors.current
    Text(
        text = label,
        color = if (active) p.blue else Color.Gray,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                if (active) p.blue.copy(alpha = 0.2f) else Color.Transparent
            )
            .border(
                1.dp,
                if (active) p.blue.copy(alpha = 0.6f) else Color.Gray.copy(alpha = 0.3f)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun CapabilityPill(text: String) {
    val p = LocalPrometheusColors.current
    Text(
        text = text,
        color = p.textSecondary,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val p = LocalPrometheusColors.current
    AnimatedVisibility(
        visible = true,
        enter = slideInHorizontally(
            animationSpec = tween(300),
            initialOffsetX = { if (message.isUser) it else -it }
        ) + fadeIn(animationSpec = tween(300))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
        ) {
            if (message.imagePath != null) {
                val bitmap = remember(message.imagePath) {
                    BitmapFactory.decodeFile(message.imagePath)
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Attached image",
                        modifier = Modifier
                            .widthIn(max = 240.dp)
                            .aspectRatio(4f / 3f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, p.blue.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
            Text(
                text = markdownToAnnotated(message.text),
                color = if (message.isUser) Color.Black else p.textPrimary,
                modifier = Modifier
                    .background(
                        color = if (message.isUser) p.blue else p.surface,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .border(1.dp, p.blue.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            )
        }
    }
}

private fun markdownToAnnotated(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            text.startsWith("__", i) -> {
                val end = text.indexOf("__", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            text.startsWith("*", i) && !text.startsWith("**", i) -> {
                val end = text.indexOf("*", i + 1)
                if (end != -1 && end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            text.startsWith("`", i) -> {
                val end = text.indexOf("`", i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFE6DB74)
                    )) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            text[i] == '\n' -> {
                append("\n")
                i++
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}

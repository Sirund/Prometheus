package com.prometheus.android.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.prometheus.android.inference.InferenceManager
import com.prometheus.android.ui.theme.PrometheusColors
import com.prometheus.model.ChatMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen() {
    val context = LocalContext.current
    val manager = remember { InferenceManager(context) }
    var query by remember { mutableStateOf("") }
    var chatHistory by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var isModelLoaded by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Initializing...") }
    var downloadProgress by remember { mutableStateOf(-1) }
    var isDownloading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        manager.setupGemma()
        isModelLoaded = manager.isModelLoaded
        statusMessage = manager.statusMessage
    }

    val showDownload = !isModelLoaded && !isDownloading && statusMessage.startsWith("Model not found")

    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(chatHistory.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Survival Assistant", color = PrometheusColors.blue) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrometheusColors.cardBackground
                ),
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
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
                }
            )
        },
        containerColor = PrometheusColors.darkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ModeIndicatorBar()

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
                            color = Color.White,
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
                            Button(
                                onClick = {
                                    isDownloading = true
                                    scope.launch {
                                        val ok = manager.downloadModel { pct ->
                                            downloadProgress = pct
                                        }
                                        isDownloading = false
                                        if (ok) {
                                            manager.setupGemma()
                                        }
                                        isModelLoaded = manager.isModelLoaded
                                        statusMessage = manager.statusMessage
                                        if (!ok) downloadProgress = -1
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrometheusColors.blue,
                                    contentColor = Color.Black
                                )
                            ) {
                                Text("\u2B07\uFE0F  DOWNLOAD GEMMA 4 (2.4 GB)", fontWeight = FontWeight.Bold)
                            }
                        }
                        if (isDownloading && downloadProgress >= 0) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Downloading: $downloadProgress%",
                                color = PrometheusColors.blue,
                                style = MaterialTheme.typography.bodySmall
                            )
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
                    .border(1.dp, PrometheusColors.blue.copy(alpha = 0.3f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            "Ask anything about survival...",
                            color = PrometheusColors.blue.copy(alpha = 0.5f)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = PrometheusColors.cardBackground,
                        unfocusedContainerColor = PrometheusColors.cardBackground,
                        focusedTextColor = PrometheusColors.blue,
                        unfocusedTextColor = PrometheusColors.blue,
                        cursorColor = PrometheusColors.blue,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    enabled = isModelLoaded
                )
                VerticalDivider(
                    modifier = Modifier.height(44.dp),
                    color = PrometheusColors.blue.copy(alpha = 0.2f)
                )
                TextButton(
                    onClick = { /* TODO: TTS */ },
                    enabled = isModelLoaded,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Text(
                        text = "\uD83D\uDD0A",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                TextButton(
                    onClick = {
                        val userText = query
                        query = ""
                        chatHistory = chatHistory + ChatMessage(text = userText, isUser = true)
                        val aiId = "ai-${chatHistory.size}"
                        val aiIndex = chatHistory.size
                        chatHistory = chatHistory + ChatMessage(id = aiId, text = "...", isUser = false)
                        scope.launch {
                            manager.sendMessage(userText) { response ->
                                chatHistory = chatHistory.toMutableList().also { list ->
                                    if (aiIndex < list.size) {
                                        list[aiIndex] = ChatMessage(id = aiId, text = response, isUser = false)
                                    }
                                }
                            }
                        }
                    },
                    enabled = isModelLoaded && query.isNotBlank(),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (isModelLoaded) PrometheusColors.blue else PrometheusColors.cardBackground,
                        contentColor = if (isModelLoaded) Color.Black else Color.Gray,
                        disabledContainerColor = PrometheusColors.cardBackground,
                        disabledContentColor = Color.Gray
                    ),
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Text(
                        text = "\u26A1",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeIndicatorBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PrometheusColors.cardBackground)
            .border(0.5.dp, PrometheusColors.blue.copy(alpha = 0.15f))
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        ModeChip(label = "SURVIVAL CHAT", active = true)
        Spacer(Modifier.width(8.dp))
        ModeChip(label = "EMERGENCY BRIEF", active = false)
    }
}

@Composable
private fun ModeChip(label: String, active: Boolean) {
    Text(
        text = label,
        color = if (active) PrometheusColors.blue else Color.Gray,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(
                if (active) PrometheusColors.blue.copy(alpha = 0.2f) else Color.Transparent
            )
            .border(
                1.dp,
                if (active) PrometheusColors.blue.copy(alpha = 0.6f) else Color.Gray.copy(alpha = 0.3f)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun CapabilityPill(text: String) {
    Text(
        text = text,
        color = Color.Gray,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Text(
            text = markdownToAnnotated(message.text),
            color = if (message.isUser) Color.Black else Color.White,
            modifier = Modifier
                .background(
                    color = if (message.isUser) PrometheusColors.blue else PrometheusColors.cardBackground,
                    shape = RoundedCornerShape(16.dp)
                )
                .border(1.dp, PrometheusColors.blue.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(12.dp)
        )
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

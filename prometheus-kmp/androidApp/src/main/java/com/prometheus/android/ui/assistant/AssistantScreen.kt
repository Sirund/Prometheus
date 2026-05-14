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
import androidx.compose.ui.unit.dp
import com.prometheus.android.inference.InferenceManager
import com.prometheus.android.ui.theme.PrometheusColors
import com.prometheus.model.ChatMessage
import kotlinx.coroutines.launch

@Composable
fun AssistantScreen() {
    val manager = remember { InferenceManager() }
    var query by remember { mutableStateOf("") }
    var chatHistory by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (!manager.isModelLoaded) {
            manager.setupGemma()
        }
    }

    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(chatHistory.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PrometheusColors.darkBackground)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (manager.isModelLoaded) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color(0xFFFFA500))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = manager.statusMessage,
                color = PrometheusColors.blue,
                style = MaterialTheme.typography.labelSmall
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chatHistory, key = { it.id }) { message ->
                ChatBubble(message = message)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("TYPE QUERY...", color = PrometheusColors.blue.copy(alpha = 0.5f)) },
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, PrometheusColors.blue.copy(alpha = 0.5f)),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = PrometheusColors.cardBackground,
                    unfocusedContainerColor = PrometheusColors.cardBackground,
                    focusedTextColor = PrometheusColors.blue,
                    unfocusedTextColor = PrometheusColors.blue,
                    cursorColor = PrometheusColors.blue,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                enabled = manager.isModelLoaded
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val userText = query
                    query = ""
                    chatHistory = chatHistory + ChatMessage(text = userText, isUser = true)
                    val aiIndex = chatHistory.size
                    chatHistory = chatHistory + ChatMessage(text = "...", isUser = false)
                    scope.launch {
                        manager.sendMessage(userText) { response ->
                            chatHistory = chatHistory.toMutableList().also { it[aiIndex] = ChatMessage(text = response, isUser = false) }
                        }
                    }
                },
                enabled = manager.isModelLoaded && query.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrometheusColors.blue,
                    contentColor = androidx.compose.ui.graphics.Color.Black
                )
            ) {
                Text("\u26A1")
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Text(
            text = message.text,
            color = if (message.isUser) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White,
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

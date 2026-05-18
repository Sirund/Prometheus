package com.prometheus.android.ui.ask

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.prometheus.android.inference.ConversationManager
import com.prometheus.android.inference.ModelManager
import com.prometheus.android.inference.STTManager
import com.prometheus.android.inference.TTSManager
import com.prometheus.android.ui.assistant.ConversationData
import com.prometheus.android.ui.assistant.saveChatImage
import com.prometheus.android.ui.theme.LocalPrometheusColors
import com.prometheus.android.ui.vision.CameraActions
import com.prometheus.android.ui.vision.CameraFrame
import com.prometheus.model.ChatMessage
import com.prometheus.model.EarthquakeEvent
import com.prometheus.model.NowcastAlert
import com.prometheus.model.UserLocation
import com.prometheus.prompt.SystemPrompts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

enum class TalkMode { Idle, Recording, Transcribing, Sending, Result }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskScreen(
    conversations: List<ConversationData>,
    activeIndex: Int,
    conversationManager: ConversationManager?,
    onConversationsChange: (List<ConversationData>) -> Unit,
    onActiveIndexChange: (Int) -> Unit,
    currentEvent: EarthquakeEvent? = null,
    nowcastAlerts: List<NowcastAlert> = emptyList(),
    userLocation: UserLocation? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = LocalPrometheusColors.current
    val manager = remember { conversationManager ?: ConversationManager() }

    var askMode by remember { mutableStateOf("CHAT") }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val listState = rememberLazyListState()

    // CHAT mode state
    var query by remember { mutableStateOf("") }
    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
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

    // TALK mode state
    val isModelLoaded by ModelManager.isLoaded.collectAsState()
    val statusMessage by ModelManager.statusMessage.collectAsState()
    var capturedImage by remember { mutableStateOf<Bitmap?>(null) }
    var freezeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cameraActions by remember { mutableStateOf<CameraActions?>(null) }
    var talkMode by remember { mutableStateOf(TalkMode.Idle) }
    var description by remember { mutableStateOf<String?>(null) }
    var currentTtsText by remember { mutableStateOf<String?>(null) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val ttsManager = remember { TTSManager(context) }
    val sttManager = remember { STTManager(context) }
    var pendingSttResult by remember { mutableStateOf<String?>(null) }
    var pendingSttError by remember { mutableStateOf<String?>(null) }

    // Shared: local conversations — bypass AnimatedContent barrier
    var localConversations by remember { mutableStateOf(conversations) }
    val chatHistory by remember {
        derivedStateOf { localConversations.getOrNull(activeIndex)?.messages ?: emptyList() }
    }

    // TALK mode permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
        hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] == true
    }

    // Notification permission (Android 13+) untuk download model
    val notificationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) ModelManager.enqueueDownload(context)
    }

    fun enqueueDownloadWithPermission(context: android.content.Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ModelManager.enqueueDownload(context)
        }
    }

    // Reset TTS & description saat mode berubah (CHAT↔TALK)
    LaunchedEffect(askMode) {
        ttsManager.stop()
        currentTtsText = null
        description = null
        if (askMode == "TALK") {
            if (!hasCameraPermission || !hasAudioPermission) {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            ttsManager.shutdown()
            sttManager.shutdown()
        }
    }

    // Download state monitoring (reaktif, non-blocking)
    val downloadInfos by WorkManager.getInstance(context)
        .getWorkInfosByTagFlow("model_download")
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val downloadState = downloadInfos.lastOrNull()
    val isDownloadingWork = downloadState?.let {
        it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
    } ?: false
    val downloadProgressVal = downloadState?.progress?.getInt("progress", -1) ?: -1

    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(chatHistory.lastIndex)
        }
    }

    val showDownload = !isModelLoaded && !isDownloadingWork &&
            statusMessage == "Model Not Found"

    // ──────────────────────────────────────────────────────────
    // TTS helper: speak or stop
    // ──────────────────────────────────────────────────────────
    fun speakOrStop(text: String) {
        if (text == currentTtsText) {
            ttsManager.stop()
            currentTtsText = null
        } else {
            ttsManager.stop()
            currentTtsText = text
            ttsManager.speak(text) {
                scope.launch(Dispatchers.Main) {
                    currentTtsText = null
                }
            }
        }
    }

    fun isCasual(text: String): Boolean {
        if (text.length > 120) return false
        val casual = Regex("(?i).*(halo|hai|hi|hey|apa kabar|hello|good morning|pagi|siang|sore|malam|selamat|terima kasih|thank|thanks|bye|dadah|siapa kamu|apa yang bisa kamu|bisa bantu|tanya dong|prometheus).*")
        return text.matches(casual)
    }

    fun selectPersona(isCrisis: Boolean, hasImage: Boolean, text: String): String = when {
        isCrisis && hasImage -> SystemPrompts.HAZARD_SCANNER
        isCrisis && !hasImage -> SystemPrompts.EMERGENCY_COORDINATOR
        !isCrisis && isCasual(text) -> SystemPrompts.CASUAL_GATEKEEPER
        else -> SystemPrompts.MITIGATION_ANALYST
    }

    fun buildSysPrompt(event: EarthquakeEvent?, alerts: List<NowcastAlert>, location: UserLocation?, hasImage: Boolean, text: String): String {
        val ctx = SystemPrompts.buildSituationContext(event, alerts, location)
        val isCrisis = ctx.isNotBlank()
        val persona = selectPersona(isCrisis, hasImage, text)
        return if (ctx.isNotBlank()) "$ctx\n\n$persona" else persona
    }

    // ──────────────────────────────────────────────────────────
    // CHAT: image send helper
    // ──────────────────────────────────────────────────────────
    fun sendChatMessage(userText: String, image: Bitmap?) {
        val history = chatHistory
        val oldSize = history.size
        val aiIndex = oldSize + 1

        var currentList = localConversations.toMutableList()
        currentList[activeIndex] = currentList[activeIndex].copy(
            messages = history + ChatMessage(text = userText, isUser = true) +
                    ChatMessage(text = "...", isUser = false)
        )
        localConversations = currentList
        onConversationsChange(currentList)

        scope.launch {
            val savedPath = if (image != null) {
                withContext(Dispatchers.IO) { saveChatImage(context, image) }
            } else null

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

            val sysPrompt = buildSysPrompt(currentEvent, nowcastAlerts, userLocation, savedPath != null, userText)

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
    }

    // ──────────────────────────────────────────────────────────
    // Layout
    // ──────────────────────────────────────────────────────────
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = askMode == "CHAT",
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
                            if (askMode == "CHAT") {
                                IconButton(onClick = {
                                    scope.launch { if (drawerState.isClosed) drawerState.open() else drawerState.close() }
                                }) {
                                    Text("\u2630", color = p.blue, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                            Text("Ask Gemma", color = p.blue)
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
                // ── Mode chip bar: CHAT | TALK ──
                ModeIndicatorBar(
                    currentMode = askMode,
                    onModeChange = { askMode = it }
                )

                // ── Conditional content ──
                when (askMode) {
                    "CHAT" -> ChatContent(
                        query = query,
                        onQueryChange = { query = it },
                        selectedImageBitmap = selectedImageBitmap,
                        onClearImage = { selectedImageBitmap = null },
                        chatHistory = chatHistory,
                        listState = listState,
                        isModelLoaded = isModelLoaded,
                        statusMessage = statusMessage,
                        showDownload = showDownload,
                        isDownloading = isDownloadingWork,
                        downloadProgress = downloadProgressVal,
                        onSend = { sendChatMessage(query, selectedImageBitmap)
                            query = ""; selectedImageBitmap = null },
                        onAttachImage = { showImageSourceDialog = true },
                        onDownloadModel = { enqueueDownloadWithPermission(context) },
                        onSpeakText = ::speakOrStop
                    )

                    "TALK" -> TalkContent(
                        isModelLoaded = isModelLoaded,
                        statusMessage = statusMessage,
                        hasCameraPermission = hasCameraPermission,
                        hasAudioPermission = hasAudioPermission,
                        onRequestPermissions = {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                            )
                        },
                        capturedImage = capturedImage,
                        freezeBitmap = freezeBitmap,
                        cameraActions = cameraActions,
                        onCameraActionsReady = { cameraActions = it },
                        talkMode = talkMode,
                        onDownloadModel = { enqueueDownloadWithPermission(context) },
                        description = description,
                        pendingSttResult = pendingSttResult,
                        isDownloading = isDownloadingWork,
                        downloadProgress = downloadProgressVal,
                        onSttListen = {
                            description = null
                            pendingSttResult = null
                            pendingSttError = null
                            talkMode = TalkMode.Recording
                            sttManager.startListening(
                                onResult = { text -> pendingSttResult = text },
                                onError = { error -> pendingSttError = error }
                            )
                        },
                        onSttStopAndProcess = {
                            if (talkMode == TalkMode.Recording) {
                                sttManager.stop()
                                talkMode = TalkMode.Transcribing
                                scope.launch {
                                    var text: String? = null
                                    repeat(50) {
                                        if (pendingSttResult != null) {
                                            text = pendingSttResult
                                            return@repeat
                                        }
                                        if (pendingSttError != null) return@repeat
                                        delay(100)
                                    }
                                    if (text == null || text.isEmpty()) {
                                        talkMode = TalkMode.Idle
                                        return@launch
                                    }

                                    val photoBytes = withContext(Dispatchers.Default) {
                                        suspendCancellableCoroutine<ByteArray?> { cont ->
                                            cameraActions?.takePhoto { bytes ->
                                                cont.resume(bytes)
                                            } ?: cont.resume(null)
                                        }
                                    }
                                    val bmp = if (photoBytes != null) {
                                        BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size)
                                    } else null
                                    capturedImage = bmp
                                    freezeBitmap = bmp
                                    talkMode = TalkMode.Sending
                                    delay(1200)

                                    val savedPath = if (capturedImage != null) {
                                        withContext(Dispatchers.IO) { saveChatImage(context, capturedImage!!) }
                                    } else null

                                    val history = localConversations.getOrNull(activeIndex)?.messages ?: emptyList()
                                    val oldSize = history.size
                                    val aiIndex = oldSize + 1

                                    var currentList = localConversations.toMutableList()
                                    currentList[activeIndex] = currentList[activeIndex].copy(
                                        messages = history + ChatMessage(text = text, isUser = true, imagePath = savedPath) + ChatMessage(text = "...", isUser = false)
                                    )
                                    localConversations = currentList
                                    onConversationsChange(currentList)

                                    val sysPrompt = buildSysPrompt(currentEvent, nowcastAlerts, userLocation, savedPath != null, text)
                                    var isSpeaking = false
                                    manager.sendMessage(
                                        text = text,
                                        history = history,
                                        systemPrompt = sysPrompt,
                                        imagePath = savedPath
                                    ) { responseText ->
                                        description = responseText
                                        currentList = currentList.toMutableList()
                                        val msgs = currentList[activeIndex].messages.toMutableList()
                                        if (aiIndex < msgs.size) {
                                            msgs[aiIndex] = ChatMessage(text = responseText, isUser = false)
                                        }
                                        currentList[activeIndex] = currentList[activeIndex].copy(messages = msgs)
                                        localConversations = currentList
                                        onConversationsChange(currentList)
                                    }

                                    val responseText = description ?: ""
                                    if (responseText.isNotEmpty() && !isSpeaking) {
                                        isSpeaking = true
                                        talkMode = TalkMode.Result
                                        speakOrStop(responseText)
                                        delay(3000)
                                        isSpeaking = false
                                    }

                                    talkMode = TalkMode.Idle
                                    capturedImage = null
                                    freezeBitmap = null
                                }
                            }
                        },
                        onTalkModeChange = { talkMode = it },
                        onSpeakText = ::speakOrStop,
                        scope = scope
                    )
                }
            }
        }
    }

    // ── Image source dialog ──
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
                            color = Color.White,
                            fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            showImageSourceDialog = false
                            galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("\uD83D\uDDBC\uFE0F  Gallery",
                            color = Color.White,
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

// ──────────────────────────────────────────────────────────
// Mode Indicator Bar (outer: CHAT | TALK)
// ──────────────────────────────────────────────────────────
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
        ModeChip(label = "CHAT", active = currentMode == "CHAT", onClick = { onModeChange("CHAT") })
        Spacer(Modifier.width(8.dp))
        ModeChip(label = "TALK", active = currentMode == "TALK", onClick = { onModeChange("TALK") })
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
            .background(if (active) p.blue.copy(alpha = 0.2f) else Color.Transparent)
            .border(1.dp, if (active) p.blue.copy(alpha = 0.6f) else Color.Gray.copy(alpha = 0.3f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

// ──────────────────────────────────────────────────────────
// CHAT mode content
// ──────────────────────────────────────────────────────────
@Composable
private fun ChatContent(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedImageBitmap: Bitmap?,
    onClearImage: () -> Unit,
    chatHistory: List<ChatMessage>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    isModelLoaded: Boolean,
    statusMessage: String,
    showDownload: Boolean,
    isDownloading: Boolean,
    downloadProgress: Int,
    onSend: () -> Unit,
    onAttachImage: () -> Unit,
    onDownloadModel: () -> Unit,
    onSpeakText: (String) -> Unit
) {
    val p = LocalPrometheusColors.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Status indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
        ) {
            val statusDot = when {
                isModelLoaded -> Color.Green
                statusMessage == "Model Not Found" -> Color.Red
                else -> Color(0xFFFFA500)
            }
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(statusDot)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = statusMessage,
                color = Color.Gray,
                style = MaterialTheme.typography.labelSmall
            )
        }

        // Empty state or bubbles
        if (chatHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("\uD83D\uDCAC", style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "SURVIVAL ASSISTANT",
                        color = p.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Gemma 4  \u00B7  on-device  \u00B7  offline",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CapabilityPill(icon = Icons.Filled.Science, text = "first aid")
                        CapabilityPill(icon = Icons.Filled.Home, text = "shelter & evacuation")
                        CapabilityPill(icon = Icons.Filled.WaterDrop, text = "water & supplies")
                        CapabilityPill(icon = Icons.Filled.Warning, text = "Indonesia hazards")
                    }
                    if (showDownload || isDownloading) {
                        Spacer(Modifier.height(16.dp))
                        val btnColor = if (!isDownloading) p.blue else p.blue.copy(alpha = 0.6f)
                        val btnText = when {
                            !isDownloading -> "\u2B07\uFE0F  DOWNLOAD MODEL (2.4 GB)"
                            downloadProgress >= 0 -> "\u23F3  Downloading: $downloadProgress%"
                            else -> "\u23F3  Starting..."
                        }
                        Button(
                            onClick = onDownloadModel,
                            enabled = !isDownloading,
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
                                if (isDownloading) {
                                    LinearProgressIndicator(
                                        progress = { if (downloadProgress > 0) downloadProgress / 100f else 0f },
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
                    ChatBubble(message = message, onTextClick = onSpeakText)
                }
            }
        }

        // Input row
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
                            .clickable { onClearImage() },
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
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        "Ask anything about survival...",
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
                onClick = onAttachImage,
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
                onClick = onSend,
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

// ──────────────────────────────────────────────────────────
// TALK mode content
// ──────────────────────────────────────────────────────────
@Composable
private fun TalkContent(
    isModelLoaded: Boolean,
    statusMessage: String,
    hasCameraPermission: Boolean,
    hasAudioPermission: Boolean,
    onRequestPermissions: () -> Unit,
    capturedImage: Bitmap?,
    freezeBitmap: Bitmap?,
    cameraActions: CameraActions?,
    onCameraActionsReady: (CameraActions?) -> Unit,
    talkMode: TalkMode,
    description: String?,
    pendingSttResult: String?,
    isDownloading: Boolean,
    downloadProgress: Int,
    onSttListen: () -> Unit,
    onSttStopAndProcess: () -> Unit,
    onTalkModeChange: (TalkMode) -> Unit,
    onDownloadModel: () -> Unit,
    onSpeakText: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val p = LocalPrometheusColors.current
    val context = LocalContext.current

    if (!hasCameraPermission || !hasAudioPermission) {
        PermissionGate(
            cameraGranted = hasCameraPermission,
            audioGranted = hasAudioPermission,
            onRequest = onRequestPermissions
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        // Status
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, top = 4.dp)) {
            val statusDot = when {
                isModelLoaded -> Color.Green
                statusMessage == "Model Not Found" -> Color.Red
                else -> Color(0xFFFFA500)
            }
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(statusDot))
            Spacer(Modifier.width(4.dp))
            Text(statusMessage, color = p.textSecondary, style = MaterialTheme.typography.labelSmall)
        }

        if (!isModelLoaded) {
            DownloadPrompt(
                isDownloading = isDownloading,
                downloadProgress = downloadProgress,
                onDownload = onDownloadModel
            )
        } else {
            // Camera preview
            CameraFrame(
                modifier = Modifier.fillMaxSize(),
                hasPermission = hasCameraPermission,
                freezeBitmap = freezeBitmap,
                isCapturing = talkMode == TalkMode.Sending,
                borderColor = if (talkMode == TalkMode.Recording) Color.Red else Color.Transparent,
                borderWidth = if (talkMode == TalkMode.Recording) 3.dp else 0.dp,
                onPermissionRequest = onRequestPermissions,
                onCameraActionsReady = onCameraActionsReady
            )

            // Mic button
            MicButton(
                talkMode = talkMode,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-100).dp)
                    .size(72.dp),
                onPress = onSttListen,
                onRelease = onSttStopAndProcess
            )

            // Process info
            when (talkMode) {
                TalkMode.Idle -> Text(
                    text = "Hold to Speak",
                    color = p.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = (-190).dp)
                )
                TalkMode.Transcribing -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = (-190).dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = p.blue
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Transcribing the audio...", color = p.blue, style = MaterialTheme.typography.labelSmall)
                }
                TalkMode.Sending -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = (-190).dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = p.blue
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Analyzing the input...", color = p.blue, style = MaterialTheme.typography.labelSmall)
                }
                else -> {}
            }

            // Response box
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .background(p.surface.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                    .border(1.dp, p.blue.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .clickable { onSpeakText(description ?: "") }
                    .padding(12.dp)
                    .heightIn(max = 120.dp)
            ) {
                val scrollState = rememberScrollState()
                Column(modifier = Modifier.verticalScroll(scrollState)) {
                    when (talkMode) {
                        TalkMode.Idle -> Text(
                            text = description ?: "Say something...",
                            color = if (description != null) Color.White else p.textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        TalkMode.Recording -> Text(
                            text = "...",
                            color = p.textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        TalkMode.Transcribing -> Text(
                            text = pendingSttResult ?: "...",
                            color = p.blue,
                            style = MaterialTheme.typography.bodySmall
                        )
                        TalkMode.Sending, TalkMode.Result -> Text(
                            text = description ?: "",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────
// Mic Button
// ──────────────────────────────────────────────────────────
@Composable
private fun MicButton(
    talkMode: TalkMode,
    modifier: Modifier,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    val p = LocalPrometheusColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "mic")
    val breathingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val isRecording = talkMode == TalkMode.Recording
    val isTranscribing = talkMode == TalkMode.Transcribing

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onPress()
                    waitForUpOrCancellation()
                    onRelease()
                }
            }
            .scale(if (isRecording) pulseScale else 1f)
            .alpha(if (isRecording) 1f else if (talkMode == TalkMode.Idle) breathingAlpha else 0.4f),
        contentAlignment = Alignment.Center
    ) {
        if (isTranscribing) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                strokeWidth = 3.dp,
                color = p.blue
            )
        } else {
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(Color.Red.copy(alpha = 0.2f))
                )
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (isRecording) Color.Red.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = "Mic",
                    tint = if (isRecording) Color.Red else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────
// Permission Gate
// ──────────────────────────────────────────────────────────
@Composable
private fun PermissionGate(
    cameraGranted: Boolean,
    audioGranted: Boolean,
    onRequest: () -> Unit
) {
    val p = LocalPrometheusColors.current
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("\uD83D\uDCF7\uD83C\uDF99\uFE0F", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(12.dp))
            Text("PERMISSIONS REQUIRED", color = p.textPrimary,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            val missing = buildList {
                if (!cameraGranted) add("Camera")
                if (!audioGranted) add("Microphone")
            }
            Text("Grant ${missing.joinToString(" & ")} to use Talk to Gemma",
                color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onRequest,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.blue, contentColor = Color.Black)
            ) { Text("GRANT PERMISSIONS", fontWeight = FontWeight.Bold) }
        }
    }
}

// ──────────────────────────────────────────────────────────
// Download Prompt
// ──────────────────────────────────────────────────────────
@Composable
private fun DownloadPrompt(
    isDownloading: Boolean,
    downloadProgress: Int,
    onDownload: () -> Unit
) {
    val p = LocalPrometheusColors.current
    val context = LocalContext.current
    val notificationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onDownload()
    }

    Box(
        modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("\uD83E\uDD16", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            Text("MODEL NOT FOUND", color = p.textPrimary,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Download Gemma 4 (2.4 GB) to enable TALK",
                color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))

            val btnColor = if (!isDownloading) p.blue else p.blue.copy(alpha = 0.6f)
            val btnText = when {
                !isDownloading -> "\u2B07\uFE0F  DOWNLOAD MODEL (2.4 GB)"
                downloadProgress >= 0 -> "\u23F3  Downloading: $downloadProgress%"
                else -> "\u23F3  Starting..."
            }
            Button(
                onClick = {
                    if (!isDownloading) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onDownload()
                        }
                    }
                },
                enabled = !isDownloading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = btnColor,
                    contentColor = Color.Black
                )
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (isDownloading) {
                        LinearProgressIndicator(
                            progress = { if (downloadProgress > 0) downloadProgress / 100f else 0f },
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

// ──────────────────────────────────────────────────────────
// ChatBubble
// ──────────────────────────────────────────────────────────
@Composable
private fun ChatBubble(message: ChatMessage, onTextClick: (String) -> Unit) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = markdownToAnnotated(message.text),
                    color = if (message.isUser) Color.Black else p.textPrimary,
                    modifier = Modifier
                        .clickable { onTextClick(message.text) }
                        .background(
                            color = if (message.isUser) p.blue else p.surface,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .border(1.dp, p.blue.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Play",
                    tint = if (message.text.isNotBlank()) p.blue.copy(alpha = 0.6f) else Color.Transparent,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { if (message.text.isNotBlank()) onTextClick(message.text) }
                )
            }
        }
    }
}

@Composable
private fun CapabilityPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val p = LocalPrometheusColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = p.blue)
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            color = p.textSecondary,
            style = MaterialTheme.typography.labelSmall
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

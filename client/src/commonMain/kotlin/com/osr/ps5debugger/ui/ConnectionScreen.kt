package com.osr.ps5debugger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.network.Ps5Discovery
import com.osr.ps5debugger.network.Ps5PayloadInjector
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.util.DefaultIpHelper
import com.osr.ps5debugger.util.SavedSessionInfo
import com.osr.ps5debugger.util.SessionManager
import kotlinx.coroutines.launch
import com.osr.ps5debugger.ui.icons.PS5Icons
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun ConnectionScreen(
    onSettingsClick: () -> Unit,
    onLoadEboot: (() -> Unit)? = null,
    onLoadSession: ((File) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var ipInput by remember { mutableStateOf(DefaultIpHelper.getDefaultIp() ?: "192.168.1.100") }
    var isConnecting by remember { mutableStateOf(false) }
    var isDiscovering by remember { mutableStateOf(false) }
    var isInjecting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var statusColor by remember { mutableStateOf(PS5ThemeColors.TextMuted) }

    // Saved Sessions state
    var savedSessions by remember { mutableStateOf<List<SavedSessionInfo>>(emptyList()) }
    var selectedSession by remember { mutableStateOf<SavedSessionInfo?>(null) }
    var sessionSearch by remember { mutableStateOf("") }

    val refreshSessions = {
        savedSessions = SessionManager.listSavedSessions()
    }

    LaunchedEffect(Unit) {
        refreshSessions()
        val defaultIp = DefaultIpHelper.getDefaultIp()
        if (defaultIp != null) {
            isConnecting = true
            statusMessage = "Auto-connecting to default IP: $defaultIp..."
            statusColor = PS5ThemeColors.AccentCyan
            val success = AppContainer.debuggerUseCase.connect(defaultIp)
            isConnecting = false
            if (!success) {
                statusMessage = "Auto-connection to $defaultIp failed."
                statusColor = PS5ThemeColors.StatusRed
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PS5ThemeColors.DarkBg),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val isWide = maxWidth >= 960.dp

            if (isWide) {
                Row(
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ConnectionCard(
                        ipInput = ipInput,
                        onIpChange = { ipInput = it },
                        isConnecting = isConnecting,
                        isDiscovering = isDiscovering,
                        isInjecting = isInjecting,
                        statusMessage = statusMessage,
                        statusColor = statusColor,
                        onConnect = {
                            coroutineScope.launch {
                                isConnecting = true
                                statusMessage = "Connecting to $ipInput..."
                                statusColor = PS5ThemeColors.AccentCyan
                                try {
                                    val success = AppContainer.debuggerUseCase.connect(ipInput)
                                    if (!success) {
                                        statusMessage = "Connection failed: Handshake rejected"
                                        statusColor = PS5ThemeColors.StatusRed
                                    }
                                } catch (e: Exception) {
                                    statusMessage = "Error: ${e.message}"
                                    statusColor = PS5ThemeColors.StatusRed
                                } finally {
                                    isConnecting = false
                                }
                            }
                        },
                        onAutoDiscover = {
                            coroutineScope.launch {
                                isDiscovering = true
                                statusMessage = "Scanning local network..."
                                statusColor = PS5ThemeColors.AccentCyan
                                val list = Ps5Discovery.discoverConsoles()
                                if (list.isNotEmpty()) {
                                    ipInput = list.first()
                                    statusMessage = "Found console at ${list.first()}"
                                    statusColor = PS5ThemeColors.StatusGreen
                                } else {
                                    statusMessage = "No console found via auto-discovery"
                                    statusColor = PS5ThemeColors.AccentAmber
                                }
                                isDiscovering = false
                            }
                        },
                        onInjectPayload = {
                            val targetIp = ipInput.trim()
                            if (targetIp.isEmpty()) {
                                statusMessage = "Please enter console IP address"
                                statusColor = PS5ThemeColors.StatusRed
                                return@ConnectionCard
                            }
                            coroutineScope.launch {
                                isInjecting = true
                                val payloadPort = DefaultIpHelper.getPayloadPort()
                                statusMessage = "Injecting debug payload to $targetIp:$payloadPort..."
                                statusColor = PS5ThemeColors.AccentCyan
                                val result = Ps5PayloadInjector.injectPayload(targetIp, payloadPort)
                                result.onSuccess { bytesSent ->
                                    val sizeKb = bytesSent / 1024
                                    statusMessage = "Payload injected successfully ($sizeKb KB sent to $targetIp:$payloadPort)"
                                    statusColor = PS5ThemeColors.StatusGreen
                                }.onFailure { e ->
                                    statusMessage = "Injection failed: ${e.message ?: "Unknown error"}"
                                    statusColor = PS5ThemeColors.StatusRed
                                }
                                isInjecting = false
                            }
                        },
                        onLoadEboot = onLoadEboot,
                        modifier = Modifier.width(440.dp).height(560.dp)
                    )

                    Spacer(modifier = Modifier.width(24.dp))

                    SavedSessionsCard(
                        sessions = savedSessions,
                        selectedSession = selectedSession,
                        searchQuery = sessionSearch,
                        onSearchChange = { sessionSearch = it },
                        onSelectSession = { selectedSession = it },
                        onLoadSession = { onLoadSession?.invoke(it.file) },
                        onOpenFile = {
                            AppContainer.filePicker?.pickSessionFile { file ->
                                if (file != null) {
                                    onLoadSession?.invoke(file)
                                }
                            }
                        },
                        onDeleteSession = { session ->
                            SessionManager.deleteSession(session.file)
                            refreshSessions()
                            if (selectedSession?.file == session.file) {
                                selectedSession = null
                            }
                        },
                        onRefresh = refreshSessions,
                        modifier = Modifier.width(480.dp).height(560.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    ConnectionCard(
                        ipInput = ipInput,
                        onIpChange = { ipInput = it },
                        isConnecting = isConnecting,
                        isDiscovering = isDiscovering,
                        isInjecting = isInjecting,
                        statusMessage = statusMessage,
                        statusColor = statusColor,
                        onConnect = {
                            coroutineScope.launch {
                                isConnecting = true
                                statusMessage = "Connecting to $ipInput..."
                                statusColor = PS5ThemeColors.AccentCyan
                                try {
                                    val success = AppContainer.debuggerUseCase.connect(ipInput)
                                    if (!success) {
                                        statusMessage = "Connection failed: Handshake rejected"
                                        statusColor = PS5ThemeColors.StatusRed
                                    }
                                } catch (e: Exception) {
                                    statusMessage = "Error: ${e.message}"
                                    statusColor = PS5ThemeColors.StatusRed
                                } finally {
                                    isConnecting = false
                                }
                            }
                        },
                        onAutoDiscover = {
                            coroutineScope.launch {
                                isDiscovering = true
                                statusMessage = "Scanning local network..."
                                statusColor = PS5ThemeColors.AccentCyan
                                val list = Ps5Discovery.discoverConsoles()
                                if (list.isNotEmpty()) {
                                    ipInput = list.first()
                                    statusMessage = "Found console at ${list.first()}"
                                    statusColor = PS5ThemeColors.StatusGreen
                                } else {
                                    statusMessage = "No console found via auto-discovery"
                                    statusColor = PS5ThemeColors.AccentAmber
                                }
                                isDiscovering = false
                            }
                        },
                        onInjectPayload = {
                            val targetIp = ipInput.trim()
                            if (targetIp.isEmpty()) {
                                statusMessage = "Please enter console IP address"
                                statusColor = PS5ThemeColors.StatusRed
                                return@ConnectionCard
                            }
                            coroutineScope.launch {
                                isInjecting = true
                                val payloadPort = DefaultIpHelper.getPayloadPort()
                                statusMessage = "Injecting debug payload to $targetIp:$payloadPort..."
                                statusColor = PS5ThemeColors.AccentCyan
                                val result = Ps5PayloadInjector.injectPayload(targetIp, payloadPort)
                                result.onSuccess { bytesSent ->
                                    val sizeKb = bytesSent / 1024
                                    statusMessage = "Payload injected successfully ($sizeKb KB sent to $targetIp:$payloadPort)"
                                    statusColor = PS5ThemeColors.StatusGreen
                                }.onFailure { e ->
                                    statusMessage = "Injection failed: ${e.message ?: "Unknown error"}"
                                    statusColor = PS5ThemeColors.StatusRed
                                }
                                isInjecting = false
                            }
                        },
                        onLoadEboot = onLoadEboot,
                        modifier = Modifier.width(440.dp)
                    )

                    SavedSessionsCard(
                        sessions = savedSessions,
                        selectedSession = selectedSession,
                        searchQuery = sessionSearch,
                        onSearchChange = { sessionSearch = it },
                        onSelectSession = { selectedSession = it },
                        onLoadSession = { onLoadSession?.invoke(it.file) },
                        onOpenFile = {
                            AppContainer.filePicker?.pickSessionFile { file ->
                                if (file != null) {
                                    onLoadSession?.invoke(file)
                                }
                            }
                        },
                        onDeleteSession = { session ->
                            SessionManager.deleteSession(session.file)
                            refreshSessions()
                            if (selectedSession?.file == session.file) {
                                selectedSession = null
                            }
                        },
                        onRefresh = refreshSessions,
                        modifier = Modifier.width(440.dp).height(500.dp)
                    )
                }
            }
        }

        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = PS5Icons.Settings,
                contentDescription = "Settings",
                tint = PS5ThemeColors.TextMuted
            )
        }
    }
}

@Composable
private fun ConnectionCard(
    ipInput: String,
    onIpChange: (String) -> Unit,
    isConnecting: Boolean,
    isDiscovering: Boolean,
    isInjecting: Boolean,
    statusMessage: String,
    statusColor: Color,
    onConnect: () -> Unit,
    onAutoDiscover: () -> Unit,
    onInjectPayload: () -> Unit,
    onLoadEboot: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = PS5ThemeColors.Surface),
        border = BorderStroke(1.dp, PS5ThemeColors.BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "PS5 REMOTE DEBUGGER",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PS5ThemeColors.AccentCyan,
                letterSpacing = 1.sp
            )

            Text(
                text = "Establish connection with the console debug payload",
                fontSize = 12.sp,
                color = PS5ThemeColors.TextMuted
            )

            Spacer(modifier = Modifier.height(2.dp))

            OutlinedTextField(
                value = ipInput,
                onValueChange = onIpChange,
                label = { Text("Console IP Address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PS5ThemeColors.AccentCyan,
                    unfocusedBorderColor = PS5ThemeColors.BorderColor
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onConnect,
                    enabled = !isConnecting && !isDiscovering && !isInjecting,
                    colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.AccentCyan),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isConnecting) "Connecting..." else "Connect", color = Color.Black)
                }

                FilledTonalButton(
                    onClick = onAutoDiscover,
                    enabled = !isConnecting && !isDiscovering && !isInjecting,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = PS5ThemeColors.SecondaryBg),
                    modifier = Modifier.weight(1.2f)
                ) {
                    Text(if (isDiscovering) "Scanning..." else "Auto-Discover", color = PS5ThemeColors.TextMain)
                }
            }

            OutlinedButton(
                onClick = onInjectPayload,
                enabled = !isConnecting && !isDiscovering && !isInjecting,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = PS5ThemeColors.AccentCyan
                ),
                border = BorderStroke(
                    1.dp,
                    if (!isConnecting && !isDiscovering && !isInjecting) PS5ThemeColors.AccentCyan.copy(alpha = 0.6f) else PS5ThemeColors.BorderColor
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isInjecting) "Injecting..." else "Inject debug payload",
                    color = if (!isConnecting && !isDiscovering && !isInjecting) PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMuted,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (onLoadEboot != null) {
                FilledTonalButton(
                    onClick = onLoadEboot,
                    enabled = !isConnecting && !isDiscovering && !isInjecting,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = PS5ThemeColors.SecondaryBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Local eboot.bin / ELF", color = PS5ThemeColors.TextMain)
                }
            }

            if (statusMessage.isNotEmpty()) {
                Text(
                    text = statusMessage,
                    fontSize = 12.sp,
                    color = statusColor,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SavedSessionsCard(
    sessions: List<SavedSessionInfo>,
    selectedSession: SavedSessionInfo?,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelectSession: (SavedSessionInfo) -> Unit,
    onLoadSession: (SavedSessionInfo) -> Unit,
    onOpenFile: () -> Unit,
    onDeleteSession: (SavedSessionInfo) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredSessions = remember(sessions, searchQuery) {
        if (searchQuery.isBlank()) sessions
        else sessions.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.processName.contains(searchQuery, ignoreCase = true) ||
            (it.titleId != null && it.titleId.contains(searchQuery, ignoreCase = true)) ||
            it.description.contains(searchQuery, ignoreCase = true)
        }
    }

    Card(
        modifier = modifier.padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = PS5ThemeColors.Surface),
        border = BorderStroke(1.dp, PS5ThemeColors.BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxSize()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "SAVED SESSIONS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PS5ThemeColors.AccentCyan,
                            letterSpacing = 1.sp
                        )
                        if (sessions.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                color = PS5ThemeColors.AccentCyan.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "${sessions.size}",
                                    color = PS5ThemeColors.AccentCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "Double-click a session to load offline without a PS5",
                        fontSize = 12.sp,
                        color = PS5ThemeColors.TextMuted
                    )
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(PS5Icons.Refresh, contentDescription = "Refresh", tint = PS5ThemeColors.TextMuted)
                }
            }

            Spacer(Modifier.height(10.dp))

            if (sessions.size > 3) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Filter sessions...", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PS5ThemeColors.AccentCyan,
                        unfocusedBorderColor = PS5ThemeColors.BorderColor
                    )
                )
                Spacer(Modifier.height(10.dp))
            }

            // Sessions List
            if (filteredSessions.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = PS5Icons.FileBrowser,
                            contentDescription = null,
                            tint = PS5ThemeColors.TextMuted.copy(alpha = 0.4f),
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching sessions found" else "No saved sessions yet",
                            color = PS5ThemeColors.TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Save sessions via File > Save while attached to a game",
                            color = PS5ThemeColors.TextMuted.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredSessions, key = { it.file.absolutePath }) { session ->
                        val isSelected = selectedSession?.file == session.file
                        var lastClickTime by remember { mutableStateOf(0L) }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val now = System.currentTimeMillis()
                                    if (now - lastClickTime < 350L) {
                                        onLoadSession(session)
                                    } else {
                                        onSelectSession(session)
                                    }
                                    lastClickTime = now
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) PS5ThemeColors.SecondaryBg else PS5ThemeColors.DarkBg,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) PS5ThemeColors.AccentCyan else PS5ThemeColors.BorderColor.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = session.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMain,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )

                                    IconButton(
                                        onClick = { onDeleteSession(session) },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(
                                            imageVector = PS5Icons.Delete,
                                            contentDescription = "Delete",
                                            tint = PS5ThemeColors.TextMuted.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${session.processName}${if (!session.titleId.isNullOrBlank()) " • ${session.titleId}" else ""}",
                                        fontSize = 11.sp,
                                        color = PS5ThemeColors.TextMuted
                                    )

                                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date(session.createdAt))
                                    Text(
                                        text = dateStr,
                                        fontSize = 10.sp,
                                        color = PS5ThemeColors.TextMuted.copy(alpha = 0.7f)
                                    )
                                }

                                // Badges
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        color = PS5ThemeColors.StatusGreen.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "${session.regionsCount} regions • ${session.totalMemoryBytes / 1024} KB",
                                            color = PS5ThemeColors.StatusGreen,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }

                                    if (session.hasDisassembly) {
                                        Surface(
                                            color = PS5ThemeColors.AccentCyan.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "Disassembly",
                                                color = PS5ThemeColors.AccentCyan,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }

                                    Spacer(Modifier.weight(1f))

                                    Text(
                                        text = "${session.fileSizeBytes / 1024} KB",
                                        fontSize = 10.sp,
                                        color = PS5ThemeColors.TextMuted.copy(alpha = 0.6f)
                                    )
                                }

                                if (session.description.isNotBlank()) {
                                    Text(
                                        text = session.description,
                                        fontSize = 11.sp,
                                        color = PS5ThemeColors.TextMuted.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Footer Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenFile,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PS5ThemeColors.TextMain),
                    border = BorderStroke(1.dp, PS5ThemeColors.BorderColor)
                ) {
                    Text("Open File...", fontSize = 12.sp)
                }

                Button(
                    onClick = { selectedSession?.let { onLoadSession(it) } },
                    enabled = selectedSession != null,
                    colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.AccentCyan),
                    modifier = Modifier.weight(1.2f)
                ) {
                    Text(
                        text = "Load Session",
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

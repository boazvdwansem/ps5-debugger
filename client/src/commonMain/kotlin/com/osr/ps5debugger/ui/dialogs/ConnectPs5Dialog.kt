package com.osr.ps5debugger.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.domain.model.LogEntry
import com.osr.ps5debugger.ui.common.PS5Icons
import com.osr.ps5debugger.ui.theme.PS5ThemeColors
import com.osr.ps5debugger.util.DefaultIpHelper
import kotlinx.coroutines.launch

@Composable
fun ConnectPs5Dialog(
    initialIp: String,
    onDismiss: () -> Unit
) {
    var ipInput by remember { mutableStateOf(initialIp) }
    var isConnecting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var statusColor by remember { mutableStateOf(PS5ThemeColors.TextMuted) }
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isConnecting) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = PS5Icons.Connections,
                    contentDescription = null,
                    tint = PS5ThemeColors.AccentCyan,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Connect to PS5",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PS5ThemeColors.TextMain
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Enter the IP address of your PS5 console running the debug payload:",
                    fontSize = 12.sp,
                    color = PS5ThemeColors.TextMuted
                )

                OutlinedTextField(
                    value = ipInput,
                    onValueChange = { ipInput = it },
                    label = { Text("Console IP Address") },
                    singleLine = true,
                    enabled = !isConnecting,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PS5ThemeColors.AccentCyan,
                        unfocusedBorderColor = PS5ThemeColors.BorderColor
                    )
                )

                if (statusMessage.isNotEmpty()) {
                    Text(
                        text = statusMessage,
                        fontSize = 12.sp,
                        color = statusColor
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ip = ipInput.trim()
                    if (ip.isEmpty()) {
                        statusMessage = "Please enter console IP address"
                        statusColor = PS5ThemeColors.StatusRed
                        return@Button
                    }
                    coroutineScope.launch {
                        isConnecting = true
                        statusMessage = "Connecting to $ip..."
                        statusColor = PS5ThemeColors.AccentCyan
                        try {
                            val success = AppContainer.debuggerUseCase.connect(ip)
                            if (success) {
                                DefaultIpHelper.setDefaultIp(ip)
                                val isSessionLoaded = AppContainer.isOfflineSession || AppContainer.loadedSessionName != null
                                if (isSessionLoaded) {
                                    AppContainer.isOfflineSession = false
                                    AppContainer.debuggerUseCase.refreshProcesses()
                                    val currentProc = AppContainer.debuggerUseCase.activeProcess.value
                                    val currentInfo = AppContainer.debuggerUseCase.activeProcessInfo.value
                                    val liveProcs = AppContainer.debuggerUseCase.processes.value

                                    if (currentProc != null && liveProcs.isNotEmpty()) {
                                        val match = liveProcs.firstOrNull {
                                            it.name.equals(currentProc.name, ignoreCase = true) ||
                                            (currentInfo?.titleId != null && currentInfo.titleId.isNotBlank() && currentInfo.titleId != "0" && it.name.contains(currentProc.name, ignoreCase = true))
                                        } ?: liveProcs.firstOrNull { it.pid == currentProc.pid }

                                        if (match != null) {
                                            AppContainer.debuggerUseCase.selectProcess(match)
                                            AppContainer.debuggerUseCase.log(
                                                "SYSTEM",
                                                "Connected to PS5 at $ip. Applied cached memory regions to live process ${match.name} (PID: ${match.pid}).",
                                                LogEntry.Level.INFO
                                            )
                                        } else {
                                            AppContainer.debuggerUseCase.log(
                                                "SYSTEM",
                                                "Connected to PS5 at $ip. Cached memory regions remain active.",
                                                LogEntry.Level.INFO
                                            )
                                        }
                                    } else {
                                        AppContainer.debuggerUseCase.log(
                                            "SYSTEM",
                                            "Connected to PS5 at $ip. Cached memory regions remain active.",
                                            LogEntry.Level.INFO
                                        )
                                    }
                                } else {
                                    AppContainer.debuggerUseCase.log(
                                        "SYSTEM",
                                        "Connected to PS5 at $ip.",
                                        LogEntry.Level.INFO
                                    )
                                }
                                onDismiss()
                            } else {
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
                enabled = !isConnecting,
                colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.AccentCyan)
            ) {
                Text(if (isConnecting) "Connecting..." else "Connect", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isConnecting
            ) {
                Text("Cancel", color = PS5ThemeColors.TextMuted)
            }
        },
        containerColor = PS5ThemeColors.Surface,
        shape = RoundedCornerShape(12.dp)
    )
}

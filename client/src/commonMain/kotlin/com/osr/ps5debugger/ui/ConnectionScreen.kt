package com.osr.ps5debugger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.network.Ps5Discovery
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.util.DefaultIpHelper
import kotlinx.coroutines.launch

@Composable
fun ConnectionScreen(modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    var ipInput by remember { mutableStateOf(DefaultIpHelper.getDefaultIp() ?: "192.168.1.100") }
    var isConnecting by remember { mutableStateOf(false) }
    var isDiscovering by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var statusColor by remember { mutableStateOf(PS5ThemeColors.TextMuted) }

    LaunchedEffect(Unit) {
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
        Card(
            modifier = Modifier
                .width(440.dp)
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = PS5ThemeColors.Surface),
            border = BorderStroke(1.dp, PS5ThemeColors.BorderColor)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
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

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = ipInput,
                    onValueChange = { ipInput = it },
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
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
                        enabled = !isConnecting && !isDiscovering,
                        colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.AccentCyan),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isConnecting) "Connecting..." else "Connect", color = Color.Black)
                    }

                    FilledTonalButton(
                        onClick = {
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
                        enabled = !isConnecting && !isDiscovering,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = PS5ThemeColors.SecondaryBg),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Text(if (isDiscovering) "Scanning..." else "Auto-Discover", color = PS5ThemeColors.TextMain)
                    }
                }

                if (statusMessage.isNotEmpty()) {
                    Text(
                        text = statusMessage,
                        fontSize = 12.sp,
                        color = statusColor,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

package com.osr.ps5debugger.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.osr.ps5debugger.ui.theme.PS5ThemeColors
import com.osr.ps5debugger.ui.common.PS5Icons
import com.osr.ps5debugger.ui.state.MainState
import com.osr.ps5debugger.ui.common.TopMenuBar

import com.osr.ps5debugger.ui.common.Tooltip

@Composable
internal fun TopBar(state: MainState, isMobile: Boolean, onSettingsClick: () -> Unit) {
    val isConnected by state.isConnected.collectAsState()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isMobile) {
            TopMenuBar(
                onFileAction = { action ->
                    if (action == "Preferences") {
                        onSettingsClick()
                    } else {
                        state.handleFileAction(action)
                    }
                },
                onEditAction = { state.handleEditAction(it) },
                onViewAction = { state.handleViewAction(it) }
            )
        } else {
            IconButton(onClick = { /* Open mobile menu */ }) {
                Icon(PS5Icons.FileBrowser, null, tint = PS5ThemeColors.TextMain)
            }
        }

        Spacer(Modifier.weight(1f))

        // Connection Status Indicator
        if (state.isOfflineSession) {
            Surface(
                color = PS5ThemeColors.AccentCyan.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(PS5ThemeColors.AccentCyan, RoundedCornerShape(4.dp))
                    )
                    Text(
                        text = "OFFLINE: ${state.loadedSessionName ?: "SESSION"}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = PS5ThemeColors.AccentCyan
                    )
                    Text(
                        text = "Close",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PS5ThemeColors.TextMuted,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .clickable { state.closeOfflineSession() }
                    )
                }
            }
        } else {
            Surface(
                color = if (isConnected) Color(0xFF43A047).copy(alpha = 0.1f) else Color(0xFFE53935).copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (isConnected) Color(0xFF43A047) else Color(0xFFE53935),
                                RoundedCornerShape(4.dp)
                            )
                    )
                    Text(
                        text = if (isConnected) {
                            if (state.loadedSessionName != null) "CONNECTED (${state.loadedSessionName})" else "CONNECTED"
                        } else "DISCONNECTED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected) Color(0xFF43A047) else Color(0xFFE53935)
                    )
                }
            }
        }

        Tooltip("Connect to PS5") {
            IconButton(onClick = { state.showConnectDialog = true }) {
                Icon(
                    imageVector = PS5Icons.Connections,
                    contentDescription = "Connect to PS5",
                    tint = if (isConnected) Color(0xFF43A047) else PS5ThemeColors.AccentCyan
                )
            }
        }

        Tooltip("Disconnect") {
            IconButton(onClick = { state.disconnectAndReturnToConnectionScreen() }) {
                Icon(
                    imageVector = PS5Icons.Disconnect,
                    contentDescription = "Disconnect",
                    tint = PS5ThemeColors.TextMuted
                )
            }
        }

        Tooltip("Settings") {
            IconButton(onClick = onSettingsClick) {
                Icon(PS5Icons.Settings, contentDescription = "Settings", tint = PS5ThemeColors.TextMuted)
            }
        }
    }
}



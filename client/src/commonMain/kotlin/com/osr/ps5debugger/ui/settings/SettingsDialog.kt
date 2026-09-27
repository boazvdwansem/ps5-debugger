package com.osr.ps5debugger.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.TextStyle
import com.osr.ps5debugger.ui.theme.PS5ThemeColors
import com.osr.ps5debugger.ui.common.PS5Icons
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.util.DefaultIpHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsDialog(onClose: () -> Unit) {
    var activeCategory by remember { mutableStateOf("general") }

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.fillMaxWidth(0.85f).fillMaxHeight(0.85f),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        content = {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp),
                color = PS5ThemeColors.DarkBg,
                border = BorderStroke(1.dp, PS5ThemeColors.BorderColor)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Preferences", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        IconButton(onClick = onClose) {
                            Icon(PS5Icons.Close, null, tint = Color.Gray)
                        }
                    }

                    HorizontalDivider(color = PS5ThemeColors.BorderColor)

                    Row(modifier = Modifier.fillMaxSize()) {
                        // Settings Sidebar
                        Column(
                            modifier = Modifier
                                .width(200.dp)
                                .fillMaxHeight()
                                .background(PS5ThemeColors.SecondaryBg)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SettingsTabItem("General", PS5Icons.SettingsGeneral, activeCategory == "general") { activeCategory = "general" }
                            SettingsTabItem("Shortcuts", PS5Icons.Keyboard, activeCategory == "shortcuts") { activeCategory = "shortcuts" }
                            SettingsTabItem("Connection", PS5Icons.SettingsNetwork, activeCategory == "network") { activeCategory = "network" }
                            SettingsTabItem("Simulation", PS5Icons.SettingsSimulation, activeCategory == "mock") { activeCategory = "mock" }
                            SettingsTabItem("About", PS5Icons.Info, activeCategory == "support") { activeCategory = "support" }
                        }

                        VerticalDivider(color = PS5ThemeColors.BorderColor)

                        // Settings Content
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(24.dp)) {
                            when (activeCategory) {
                                "general" -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                        Text("General Settings", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        
                                        var autoReconnect by remember { mutableStateOf(DefaultIpHelper.isAutoReconnectEnabled()) }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = autoReconnect,
                                                onCheckedChange = { 
                                                    autoReconnect = it
                                                    DefaultIpHelper.setAutoReconnectEnabled(it)
                                                },
                                                colors = CheckboxDefaults.colors(checkedColor = PS5ThemeColors.AccentCyan)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text("Auto-reconnect on socket loss", color = Color.LightGray, fontSize = 13.sp)
                                        }

                                        var showTooltips by remember { mutableStateOf(true) }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = showTooltips,
                                                onCheckedChange = { showTooltips = it },
                                                colors = CheckboxDefaults.colors(checkedColor = PS5ThemeColors.AccentCyan)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text("Show tooltips", color = Color.LightGray, fontSize = 13.sp)
                                        }

                                        var mcpEnabled by remember { mutableStateOf(DefaultIpHelper.isMcpEnabled()) }
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = mcpEnabled,
                                                    onCheckedChange = {
                                                        mcpEnabled = it
                                                        DefaultIpHelper.setMcpEnabled(it)
                                                        AppContainer.onMcpServerToggled?.invoke(it)
                                                    },
                                                    colors = CheckboxDefaults.colors(checkedColor = PS5ThemeColors.AccentCyan)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text("Enable Model Context Protocol (MCP) Server", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                            Text(
                                                text = "Exposes debugger REST bridge on 127.0.0.1:8585 for AI assistant interaction (Antigravity, Claude Desktop, Cursor).",
                                                color = PS5ThemeColors.TextMuted,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(start = 36.dp, top = 2.dp)
                                            )
                                        }
                                    }
                                }
                                "shortcuts" -> {
                                    ShortcutsPage()
                                }
                                "network" -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                        Text("Network Configuration", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        
                                        var timeout by remember { mutableStateOf(DefaultIpHelper.getConnectionTimeoutMs().toString()) }
                                        OutlinedTextField(
                                            value = timeout,
                                            onValueChange = { 
                                                if (it.all { c -> c.isDigit() }) {
                                                    timeout = it
                                                    it.toIntOrNull()?.let { ms -> DefaultIpHelper.setConnectionTimeoutMs(ms) }
                                                }
                                            },
                                            label = { Text("Connection Timeout (ms)") },
                                            modifier = Modifier.fillMaxWidth(),
                                            textStyle = TextStyle(color = Color.White)
                                        )
                                    }
                                }
                                "mock" -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                        Text("Simulation / Mock Mode", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Switch(
                                                checked = AppContainer.debugMockEnabled,
                                                onCheckedChange = { 
                                                    AppContainer.debugMockEnabled = it
                                                    DefaultIpHelper.setMockEnabled(it)
                                                },
                                                colors = SwitchDefaults.colors(checkedThumbColor = PS5ThemeColors.AccentCyan)
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Text("Enable Connection Simulation", color = Color.LightGray, fontSize = 13.sp)
                                        }
                                        Text(
                                            text = "When enabled, the client will simulate a console connection, bypassing network sockets and generating mock disassembly/subroutine CFG graphs.",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                "support" -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Text("Support & Version Info", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text("PS5 Debugger Client v1.0.3", color = Color.Gray, fontSize = 12.sp)
                                        Text("Developed by Boaz.", color = Color.Gray, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
internal fun SettingsTabItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clickable { onClick() },
        color = if (selected) PS5ThemeColors.Surface else Color.Transparent,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMuted,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                color = if (selected) PS5ThemeColors.TextMain else PS5ThemeColors.TextMuted,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}



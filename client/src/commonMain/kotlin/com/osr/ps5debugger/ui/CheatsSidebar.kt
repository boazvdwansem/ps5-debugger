package com.osr.ps5debugger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.domain.model.Cheat
import com.osr.ps5debugger.domain.model.CheatOption
import com.osr.ps5debugger.domain.model.CheatType
import com.osr.ps5debugger.domain.model.GameCheatProfile
import com.osr.ps5debugger.domain.model.LogEntry
import com.osr.ps5debugger.ui.icons.PS5Icons
import com.osr.ps5debugger.ui.state.MainState
import kotlinx.coroutines.launch

@Composable
fun CheatsSidebar(
    state: MainState,
    profiles: List<GameCheatProfile>,
    onCollapse: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val activeProcess by AppContainer.debuggerUseCase.activeProcess.collectAsState()
    val activeProcessInfo by AppContainer.debuggerUseCase.activeProcessInfo.collectAsState()
    val isConnected by state.isConnected.collectAsState()
    
    var filterText by remember { mutableStateOf("") }
    val primaryProfile = profiles.firstOrNull()
    val gameTitle = primaryProfile?.name ?: activeProcessInfo?.name ?: activeProcess?.name ?: "Game Cheats"
    val titleId = primaryProfile?.titleId ?: activeProcessInfo?.titleId ?: ""
    val version = primaryProfile?.version ?: "1.00"

    val allCheats = remember(profiles) {
        profiles.flatMap { it.cheats }.distinctBy { it.id }
    }

    val filteredCheats = remember(allCheats, filterText) {
        if (filterText.isEmpty()) allCheats
        else allCheats.filter { it.name.contains(filterText, ignoreCase = true) || it.description.contains(filterText, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(340.dp)
            .background(PS5ThemeColors.DarkBg)
            .padding(10.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = PS5Icons.Cheats,
                    contentDescription = "Cheats",
                    tint = PS5ThemeColors.AccentCyan,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Active Cheats",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PS5ThemeColors.TextMain
                )
            }
            IconButton(onClick = onCollapse, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = PS5Icons.Close,
                    contentDescription = "Collapse",
                    tint = PS5ThemeColors.TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Game Info Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = PS5ThemeColors.Surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, PS5ThemeColors.BorderColor)
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = gameTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = PS5ThemeColors.TextMain
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (titleId.isNotEmpty()) {
                        Text(
                            text = titleId,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = PS5ThemeColors.AccentCyan
                        )
                    }
                    Text(
                        text = "v$version • ${allCheats.size} cheat(s)",
                        fontSize = 11.sp,
                        color = PS5ThemeColors.TextMuted
                    )
                }
            }
        }

        // Filter / Search
        OutlinedTextField(
            value = filterText,
            onValueChange = { filterText = it },
            placeholder = { Text("Filter cheats...", fontSize = 12.sp) },
            leadingIcon = { Icon(PS5Icons.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )

        // Cheats List
        if (filteredCheats.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(PS5ThemeColors.Surface)
                    .border(1.dp, PS5ThemeColors.BorderColor, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (allCheats.isEmpty()) "No cheats found for this game" else "No matching cheats",
                    fontSize = 12.sp,
                    color = PS5ThemeColors.TextMuted
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredCheats, key = { it.id }) { cheat ->
                    SidebarCheatRow(
                        titleId = titleId,
                        version = version,
                        cheat = cheat,
                        pid = activeProcess?.pid ?: 0,
                        isConnected = isConnected
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarCheatRow(
    titleId: String,
    version: String,
    cheat: Cheat,
    pid: Int,
    isConnected: Boolean
) {
    val coroutineScope = rememberCoroutineScope()
    val isEnabled = cheat.isEnabled
    val patches = remember(cheat) { cheat.getEffectivePatches() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(
                1.dp,
                if (isEnabled) PS5ThemeColors.AccentCyan.copy(alpha = 0.6f) else PS5ThemeColors.BorderColor,
                RoundedCornerShape(6.dp)
            )
            .background(if (isEnabled) PS5ThemeColors.AccentCyan.copy(alpha = 0.08f) else PS5ThemeColors.Surface)
            .clickable {
                if (cheat.type == CheatType.Toggle) {
                    val toggled = AppContainer.debuggerUseCase.toggleCheat(titleId, version, cheat.id)
                    if (toggled != null && pid > 0) {
                        coroutineScope.launch {
                            try {
                                AppContainer.debuggerUseCase.applyCheat(pid, toggled)
                            } catch (e: Exception) {
                                AppContainer.debuggerUseCase.log("CHEATS", "Failed to apply cheat '${cheat.name}': ${e.message}", LogEntry.Level.ERROR)
                            }
                        }
                    }
                }
            },
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = cheat.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isEnabled) PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMain
                    )
                    val patchSummary = if (patches.size <= 1) {
                        val addr = patches.firstOrNull()?.address ?: cheat.address
                        "0x${addr.toString(16).uppercase()}"
                    } else {
                        val firstAddr = patches.first().address.toString(16).uppercase()
                        "0x$firstAddr (+${patches.size - 1} patch${if (patches.size > 2) "es" else ""})"
                    }
                    Text(
                        text = patchSummary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = PS5ThemeColors.TextMuted
                    )
                }

                if (cheat.type == CheatType.Toggle) {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = {
                            val toggled = AppContainer.debuggerUseCase.toggleCheat(titleId, version, cheat.id)
                            if (toggled != null && pid > 0) {
                                coroutineScope.launch {
                                    try {
                                        AppContainer.debuggerUseCase.applyCheat(pid, toggled)
                                    } catch (e: Exception) {
                                        AppContainer.debuggerUseCase.log("CHEATS", "Failed to apply cheat '${cheat.name}': ${e.message}", LogEntry.Level.ERROR)
                                    }
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PS5ThemeColors.AccentCyan,
                            checkedTrackColor = PS5ThemeColors.AccentCyan.copy(alpha = 0.3f),
                            uncheckedThumbColor = PS5ThemeColors.TextMuted,
                            uncheckedTrackColor = PS5ThemeColors.BorderColor
                        ),
                        modifier = Modifier.height(24.dp)
                    )
                }
            }

            if (cheat.description.isNotBlank()) {
                Text(
                    text = cheat.description,
                    fontSize = 11.sp,
                    color = PS5ThemeColors.TextMuted
                )
            }

            if (cheat.type == CheatType.TextField) {
                var value by remember { mutableStateOf("") }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.weight(1f).height(42.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = PS5ThemeColors.TextMain),
                        singleLine = true,
                        placeholder = { Text("Value", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PS5ThemeColors.AccentCyan,
                            unfocusedBorderColor = PS5ThemeColors.BorderColor
                        )
                    )
                    Button(
                        onClick = {
                            if (pid > 0) {
                                coroutineScope.launch {
                                    try {
                                        AppContainer.debuggerUseCase.applyCheat(pid, cheat, value)
                                    } catch (e: Exception) {
                                        AppContainer.debuggerUseCase.log("CHEATS", "Failed to apply cheat '${cheat.name}': ${e.message}", LogEntry.Level.ERROR)
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PS5ThemeColors.AccentCyan,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Apply", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (cheat.type == CheatType.Dropdown && cheat.options.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                var selectedOpt by remember { mutableStateOf<CheatOption?>(null) }

                Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Button(
                        onClick = { expanded = true },
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.SecondaryBg),
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedOpt?.name ?: "Select Option",
                                fontSize = 11.sp,
                                color = PS5ThemeColors.TextMain
                            )
                            Icon(
                                imageVector = PS5Icons.ChevronDown,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = PS5ThemeColors.TextMuted
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(PS5ThemeColors.SecondaryBg).border(1.dp, PS5ThemeColors.BorderColor)
                    ) {
                        cheat.options.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt.name, color = PS5ThemeColors.TextMain, fontSize = 12.sp) },
                                onClick = {
                                    selectedOpt = opt
                                    expanded = false
                                    if (pid > 0) {
                                        coroutineScope.launch {
                                            try {
                                                AppContainer.debuggerUseCase.applyCheat(pid, cheat, opt.hexValue)
                                            } catch (e: Exception) {
                                                AppContainer.debuggerUseCase.log("CHEATS", "Failed to apply cheat '${cheat.name}': ${e.message}", LogEntry.Level.ERROR)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

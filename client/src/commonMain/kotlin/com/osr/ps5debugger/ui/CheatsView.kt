package com.osr.ps5debugger.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.osr.ps5debugger.ui.icons.PS5Icons
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.domain.model.*
import com.osr.ps5debugger.protocol.ProtocolConstants
import com.osr.ps5debugger.util.DefaultIpHelper
import kotlinx.coroutines.launch

@Composable
fun CheatsView() {
    var viewMode by remember { mutableIntStateOf(0) } // 0 = Grid, 1 = List
    var selectedProfile by remember { mutableStateOf<GameCheatProfile?>(null) }
    val profiles by AppContainer.debuggerUseCase.gameCheatProfiles.collectAsState()

    // Sync selected profile
    LaunchedEffect(profiles, selectedProfile?.titleId) {
        if (selectedProfile != null) {
            val updated = profiles.firstOrNull { it.titleId == selectedProfile!!.titleId && it.version == selectedProfile!!.version }
            if (updated != null) {
                selectedProfile = updated
            } else {
                selectedProfile = null
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectedProfile == null) {
            CheatsToolbar(viewMode) { viewMode = it }
            HorizontalDivider(color = PS5ThemeColors.BorderColor)
            
            if (profiles.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No cheats added yet. Right click in Memory Viewer to add one.", color = PS5ThemeColors.TextMuted)
                }
            } else {
                if (viewMode == 0) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(profiles, key = { "${it.titleId}_${it.version}" }) { profile ->
                            GameProfileTile(profile) { selectedProfile = profile }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(profiles, key = { "${it.titleId}_${it.version}" }) { profile ->
                            GameProfileRow(profile) { selectedProfile = profile }
                        }
                    }
                }
            }
        } else {
            GameCheatsDetailView(selectedProfile!!) { selectedProfile = null }
        }
    }
}

@Composable
private fun CheatsToolbar(viewMode: Int, onViewModeChanged: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(36.dp).background(PS5ThemeColors.SecondaryBg).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("View Layout:", color = PS5ThemeColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        
        var expanded by remember { mutableStateOf(false) }
        val options = listOf("Grid", "List")
        
        Box {
            Button(
                onClick = { expanded = true },
                colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.Surface),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.height(28.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(options[viewMode], fontSize = 11.sp, color = PS5ThemeColors.TextMain)
                    Text("▼", fontSize = 8.sp, color = PS5ThemeColors.TextMuted)
                }
            }
            
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(PS5ThemeColors.SecondaryBg).border(1.dp, PS5ThemeColors.BorderColor)) {
                options.forEachIndexed { index, title ->
                    DropdownMenuItem(
                        text = { Text(title, fontSize = 11.sp, color = PS5ThemeColors.TextMain) },
                        onClick = { onViewModeChanged(index); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun GameProfileTile(profile: GameCheatProfile, onClick: () -> Unit) {
    val iconState = AppContainer.iconCache[profile.titleId]
    val isConnected by AppContainer.debuggerUseCase.isConnected.collectAsState()
    val consoleIp = remember { AppContainer.clientAdapter.connection.ipAddress ?: DefaultIpHelper.getDefaultIp() ?: "" }

    LaunchedEffect(profile.titleId, isConnected, consoleIp) {
        if (isConnected) {
            AppContainer.fetchMetadata(profile.titleId, consoleIp)
        }
    }

    Card(
        modifier = Modifier
            .size(160.dp, 210.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = PS5ThemeColors.Surface),
        border = BorderStroke(1.dp, PS5ThemeColors.BorderColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .border(1.dp, PS5ThemeColors.AccentCyan.copy(alpha = 0.2f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                when (iconState) {
                    is AppContainer.IconState.Success -> {
                        Image(
                            bitmap = iconState.bitmap,
                            contentDescription = "App Icon",
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }
                    is AppContainer.IconState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = PS5ThemeColors.AccentCyan, strokeWidth = 2.dp)
                    }
                    else -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(
                                imageVector = PS5Icons.Cheats, 
                                null, 
                                modifier = Modifier.size(40.dp), 
                                tint = PS5ThemeColors.AccentCyan.copy(alpha = 0.8f)
                            )
                            if (profile.titleId.isNotEmpty()) {
                                Text(
                                    profile.titleId.take(4), 
                                    fontSize = 24.sp, 
                                    fontWeight = FontWeight.ExtraBold, 
                                    color = PS5ThemeColors.AccentCyan.copy(alpha = 0.1f)
                                )
                            }
                        }
                    }
                }

                // Platform Indicator
                val platform = AppContainer.titleIdToPlatform[profile.titleId]
                if (platform != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(
                                if (platform == "PS5") Color(0xFFFFFFFF) else Color(0xFF003087),
                                RoundedCornerShape(2.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = platform,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = if (platform == "PS5") Color.Black else Color.White
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            val displayName = remember(profile.name, profile.titleId, AppContainer.titleIdToName[profile.titleId]) {
                val mapped = AppContainer.titleIdToName[profile.titleId]
                fun isBad(n: String?) = n.isNullOrEmpty() || n == "Unknown" || n.lowercase().let { it.contains("eboot.bin") || it.endsWith(".elf") || it.endsWith(".bin") }
                
                if (!isBad(mapped)) mapped!!
                else if (!isBad(profile.name)) profile.name
                else profile.titleId
            }
            Text(
                text = displayName,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = PS5ThemeColors.TextMain,
                lineHeight = 14.sp
            )
            Spacer(Modifier.weight(1f))
            val displayVersion = remember(profile.version, profile.titleId, AppContainer.titleIdToVersion[profile.titleId]) {
                AppContainer.titleIdToVersion[profile.titleId] ?: profile.version
            }
            Text(
                text = "v$displayVersion", 
                fontSize = 10.sp, 
                color = PS5ThemeColors.TextMuted,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun GameProfileRow(profile: GameCheatProfile, onClick: () -> Unit) {
    val iconState = AppContainer.iconCache[profile.titleId]
    val isConnected by AppContainer.debuggerUseCase.isConnected.collectAsState()
    val consoleIp = remember { AppContainer.clientAdapter.connection.ipAddress ?: DefaultIpHelper.getDefaultIp() ?: "" }
    
    LaunchedEffect(profile.titleId, isConnected, consoleIp) {
        if (isConnected) {
            AppContainer.fetchMetadata(profile.titleId, consoleIp)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = PS5ThemeColors.Surface),
        border = BorderStroke(1.dp, PS5ThemeColors.BorderColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                when (iconState) {
                    is AppContainer.IconState.Success -> {
                        Image(
                            bitmap = iconState.bitmap,
                            contentDescription = "App Icon",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }
                    is AppContainer.IconState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = PS5ThemeColors.AccentCyan, strokeWidth = 2.dp)
                    }
                    else -> {
                        Icon(PS5Icons.Cheats, null, modifier = Modifier.size(24.dp), tint = PS5ThemeColors.AccentCyan)
                    }
                }
            }
            Column {
                val displayName = remember(profile.name, profile.titleId, AppContainer.titleIdToName[profile.titleId]) {
                    val mapped = AppContainer.titleIdToName[profile.titleId]
                    fun isBad(n: String?) = n.isNullOrEmpty() || n == "Unknown" || n.lowercase().let { it.contains("eboot.bin") || it.endsWith(".elf") || it.endsWith(".bin") }
                    
                    if (!isBad(mapped)) mapped!!
                    else if (!isBad(profile.name)) profile.name
                    else profile.titleId
                }
                Text(
                    text = displayName,
                    fontWeight = FontWeight.Bold, 
                    fontSize = 14.sp,
                    color = PS5ThemeColors.TextMain
                )
                val displayVersion = remember(profile.version, profile.titleId, AppContainer.titleIdToVersion[profile.titleId]) {
                    AppContainer.titleIdToVersion[profile.titleId] ?: profile.version
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${profile.titleId} - v$displayVersion", fontSize = 12.sp, color = PS5ThemeColors.TextMuted)
                    
                    val platform = AppContainer.titleIdToPlatform[profile.titleId]
                    if (platform != null) {
                        Box(
                            modifier = Modifier
                                .background(
                                    if (platform == "PS5") Color(0xFFFFFFFF) else Color(0xFF003087),
                                    RoundedCornerShape(2.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = platform,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = if (platform == "PS5") Color.Black else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameCheatsDetailView(profile: GameCheatProfile, onBack: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    
    val iconState = AppContainer.iconCache[profile.titleId]
    val isConnected by AppContainer.debuggerUseCase.isConnected.collectAsState()
    val consoleIp = remember { AppContainer.clientAdapter.connection.ipAddress ?: DefaultIpHelper.getDefaultIp() ?: "" }
    
    LaunchedEffect(profile.titleId, isConnected, consoleIp) {
        if (isConnected) {
            AppContainer.fetchMetadata(profile.titleId, consoleIp)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(PS5Icons.ArrowBack, "Back", tint = PS5ThemeColors.TextMain)
            }
            Box(
                modifier = Modifier.size(64.dp).background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                when (iconState) {
                    is AppContainer.IconState.Success -> {
                        Image(
                            bitmap = iconState.bitmap,
                            contentDescription = "App Icon",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }
                    is AppContainer.IconState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = PS5ThemeColors.AccentCyan, strokeWidth = 2.dp)
                    }
                    else -> {
                        Icon(PS5Icons.Cheats, null, modifier = Modifier.size(32.dp), tint = PS5ThemeColors.AccentCyan)
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                val displayName = remember(profile.name, profile.titleId, AppContainer.titleIdToName[profile.titleId]) {
                    val mapped = AppContainer.titleIdToName[profile.titleId]
                    fun isBad(n: String?) = n.isNullOrEmpty() || n == "Unknown" || n.lowercase().let { it.contains("eboot.bin") || it.endsWith(".elf") || it.endsWith(".bin") }
                    
                    if (!isBad(mapped)) mapped!!
                    else if (!isBad(profile.name)) profile.name
                    else profile.titleId
                }
                Text(
                    text = displayName,
                    fontSize = 20.sp, 
                    fontWeight = FontWeight.Bold,
                    color = PS5ThemeColors.TextMain
                )
                val displayVersion = remember(profile.version, profile.titleId, AppContainer.titleIdToVersion[profile.titleId]) {
                    AppContainer.titleIdToVersion[profile.titleId] ?: profile.version
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ID: ${profile.titleId} | Version: $displayVersion", color = PS5ThemeColors.TextMuted)
                    
                    val platform = AppContainer.titleIdToPlatform[profile.titleId]
                    if (platform != null) {
                        Box(
                            modifier = Modifier
                                .background(
                                    if (platform == "PS5") Color(0xFFFFFFFF) else Color(0xFF003087),
                                    RoundedCornerShape(2.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = platform,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = if (platform == "PS5") Color.Black else Color.White
                            )
                        }
                    }
                }
            }
            Button(onClick = { showAddDialog = true }, shape = RoundedCornerShape(4.dp), colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.AccentCyan, contentColor = Color.Black)) {
                Icon(PS5Icons.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add Cheat")
            }
        }
        
        HorizontalDivider(color = PS5ThemeColors.BorderColor)
        
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(profile.cheats, key = { it.id }) { cheat ->
                CheatItemRow(profile.titleId, profile.version, cheat)
            }
        }
    }

    if (showAddDialog) {
        AddCheatDialog(profile.titleId, profile.version, profile.name) { showAddDialog = false }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CheatItemRow(titleId: String, version: String, cheat: Cheat) {
    val coroutineScope = rememberCoroutineScope()
    val activeProcess by AppContainer.debuggerUseCase.activeProcess.collectAsState()
    val density = LocalDensity.current
    
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var showEditDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            val pos = event.changes.first().position
                            // Convert local pixel position to DP for the context menu offset
                            contextMenuOffset = DpOffset(with(density) { pos.x.toDp() }, with(density) { pos.y.toDp() })
                            showContextMenu = true
                        }
                    }
                }
            }
            .clickable {
                AppContainer.debuggerUseCase.toggleCheat(titleId, version, cheat.id)
                coroutineScope.launch {
                    AppContainer.debuggerUseCase.applyCheat(activeProcess?.pid ?: 0, cheat)
                }
            },
        colors = CardDefaults.cardColors(containerColor = PS5ThemeColors.Surface),
        border = BorderStroke(1.dp, PS5ThemeColors.BorderColor)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(cheat.name, fontWeight = FontWeight.Bold, color = PS5ThemeColors.TextMain)
                    Text("0x${cheat.address.toString(16).uppercase()}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = PS5ThemeColors.TextMuted)
                }
                
                Checkbox(
                    checked = cheat.isEnabled,
                    onCheckedChange = null, // Handled by Card clickable
                    colors = CheckboxDefaults.colors(checkedColor = PS5ThemeColors.AccentCyan)
                )

                if (cheat.type == CheatType.TextField) {
                    var value by remember { mutableStateOf("") }
                    Row(
                        modifier = Modifier.clickable(enabled = false) { },
                        verticalAlignment = Alignment.CenterVertically, 
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { value = it },
                            modifier = Modifier.width(120.dp).height(48.dp),
                            textStyle = TextStyle(fontSize = 12.sp, color = PS5ThemeColors.TextMain),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PS5ThemeColors.AccentCyan, unfocusedBorderColor = PS5ThemeColors.BorderColor)
                        )
                        Button(onClick = {
                            coroutineScope.launch {
                                AppContainer.debuggerUseCase.applyCheat(activeProcess?.pid ?: 0, cheat, value)
                            }
                        }, shape = RoundedCornerShape(4.dp), colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.AccentCyan, contentColor = Color.Black)) { 
                            Text("Apply", fontSize = 11.sp) 
                        }
                    }
                }
                
                if (cheat.type == CheatType.Dropdown) {
                    var expanded by remember { mutableStateOf(false) }
                    var selectedOpt by remember { mutableStateOf<CheatOption?>(null) }
                    Box(modifier = Modifier.clickable(enabled = false) { }) {
                        Button(onClick = { expanded = true }, shape = RoundedCornerShape(4.dp), colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.Surface)) {
                            Text(selectedOpt?.name ?: "Select", fontSize = 11.sp, color = PS5ThemeColors.TextMain)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(PS5ThemeColors.SecondaryBg)) {
                            cheat.options.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt.name, color = PS5ThemeColors.TextMain) },
                                    onClick = {
                                        selectedOpt = opt
                                        coroutineScope.launch {
                                            AppContainer.debuggerUseCase.applyCheat(activeProcess?.pid ?: 0, cheat, opt.hexValue)
                                        }
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.offset(contextMenuOffset.x, contextMenuOffset.y).size(0.dp)) {
                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false },
                    modifier = Modifier.background(PS5ThemeColors.SecondaryBg).border(1.dp, PS5ThemeColors.BorderColor)
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit", color = PS5ThemeColors.TextMain) },
                        onClick = { 
                            showEditDialog = true
                            showContextMenu = false 
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Show in Memory View", color = PS5ThemeColors.TextMain) },
                        onClick = {
                            AppContainer.onNavigateToMemory?.invoke(cheat.address)
                            showContextMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color.Red) },
                        onClick = {
                            AppContainer.debuggerUseCase.deleteCheat(titleId, version, cheat.id)
                            showContextMenu = false
                        }
                    )
                }
            }
        }
    }

    if (showEditDialog) {
        AddCheatDialog(
            titleId = titleId,
            version = version,
            gameName = "", 
            existingCheat = cheat,
            onDismiss = { showEditDialog = false }
        )
    }
}

@Composable
internal fun AddCheatDialog(
    titleId: String, 
    version: String, 
    gameName: String, 
    existingCheat: Cheat? = null,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(existingCheat?.name ?: "") }
    var addressStr by remember { mutableStateOf(existingCheat?.address?.toString(16)?.uppercase() ?: "") }
    var type by remember { mutableStateOf(existingCheat?.type ?: CheatType.Toggle) }
    var inputFormat by remember { mutableStateOf(existingCheat?.inputFormat ?: InputFormat.Hex) }
    var hexOnValue by remember { mutableStateOf(existingCheat?.hexOnValue ?: "") }
    var hexOffValue by remember { mutableStateOf(existingCheat?.hexOffValue ?: "") }
    
    val options = remember { mutableStateListOf<CheatOption>().apply { if (existingCheat != null) addAll(existingCheat.options) } }
    var newOptName by remember { mutableStateOf("") }
    var newOptVal by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (existingCheat != null) "Edit Cheat" else "Add New Cheat", color = PS5ThemeColors.TextMain)
                Button(
                    onClick = {
                        val addr = addressStr.trim().removePrefix("0x").toLongOrNull(16) ?: 0L
                        val cheatId = existingCheat?.id ?: ("cheat_${System.currentTimeMillis()}_${(0..999).random()}")
                        val cheat = Cheat(
                            id = cheatId,
                            name = name,
                            type = type,
                            address = addr,
                            inputFormat = inputFormat,
                            hexOnValue = if (type == CheatType.Dropdown) (options.firstOrNull()?.hexValue ?: "") else hexOnValue,
                            hexOffValue = if (type == CheatType.Toggle) hexOffValue else null,
                            options = if (type == CheatType.Dropdown) options.toList() else emptyList(),
                            titleId = titleId,
                            version = version,
                            isEnabled = existingCheat?.isEnabled ?: false
                        )
                        AppContainer.debuggerUseCase.addCheat(titleId, version, cheat, gameName)
                        onDismiss()
                    },
                    enabled = name.isNotEmpty() && addressStr.isNotEmpty() && (type != CheatType.Dropdown || options.isNotEmpty()),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.AccentCyan, contentColor = Color.Black)
                ) { Text(if (existingCheat != null) "Save" else "Add") }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Cheat Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = addressStr, onValueChange = { addressStr = it }, label = { Text("Address (Hex)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                
                Text("Cheat Type:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PS5ThemeColors.TextMuted)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = type == CheatType.Toggle, onClick = { type = CheatType.Toggle }, label = { Text("Toggle", fontSize = 11.sp) })
                    FilterChip(selected = type == CheatType.TextField, onClick = { type = CheatType.TextField }, label = { Text("Text Input", fontSize = 11.sp) })
                    FilterChip(selected = type == CheatType.Dropdown, onClick = { type = CheatType.Dropdown }, label = { Text("List Options", fontSize = 11.sp) })
                }
                
                if (type == CheatType.TextField) {
                    Text("Input Format:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PS5ThemeColors.TextMuted)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = inputFormat == InputFormat.Hex, onClick = { inputFormat = InputFormat.Hex }, label = { Text("Hexadecimal", fontSize = 11.sp) })
                        FilterChip(selected = inputFormat == InputFormat.Text, onClick = { inputFormat = InputFormat.Text }, label = { Text("ASCII Text", fontSize = 11.sp) })
                    }
                    OutlinedTextField(value = hexOnValue, onValueChange = { hexOnValue = it }, label = { Text(if (inputFormat == InputFormat.Hex) "Default Hex Value" else "Default Text Value") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                
                if (type == CheatType.Toggle) {
                    OutlinedTextField(value = hexOnValue, onValueChange = { hexOnValue = it }, label = { Text("Hex Value when ON") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = hexOffValue, onValueChange = { hexOffValue = it }, label = { Text("Hex Value when OFF") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }

                if (type == CheatType.Dropdown) {
                    Text("Options List:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PS5ThemeColors.TextMuted)
                    options.forEach { opt ->
                        Row(modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.1f), RoundedCornerShape(4.dp)).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(opt.name, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = PS5ThemeColors.TextMain)
                                Text("Value: ${opt.hexValue}", fontSize = 10.sp, color = PS5ThemeColors.TextMuted)
                            }
                            IconButton(onClick = { options.remove(opt) }, modifier = Modifier.size(24.dp)) {
                                Icon(PS5Icons.Delete, null, modifier = Modifier.size(16.dp), tint = Color.Red.copy(alpha = 0.7f))
                            }
                        }
                    }
                    
                    Column(modifier = Modifier.fillMaxWidth().border(1.dp, PS5ThemeColors.BorderColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = newOptName, onValueChange = { newOptName = it }, label = { Text("Opt Name", fontSize = 10.sp) }, modifier = Modifier.weight(1f).height(48.dp), singleLine = true)
                            OutlinedTextField(value = newOptVal, onValueChange = { newOptVal = it }, label = { Text("Hex Val", fontSize = 10.sp) }, modifier = Modifier.weight(1f).height(48.dp), singleLine = true)
                        }
                        Button(
                            onClick = {
                                if (newOptName.isNotEmpty() && newOptVal.isNotEmpty()) {
                                    options.add(CheatOption(newOptName, newOptVal))
                                    newOptName = ""
                                    newOptVal = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(32.dp),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.Surface)
                        ) {
                            Icon(PS5Icons.Add, null, modifier = Modifier.size(14.dp), tint = PS5ThemeColors.AccentCyan)
                            Spacer(Modifier.width(4.dp))
                            Text("Add to List", fontSize = 11.sp, color = PS5ThemeColors.AccentCyan)
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

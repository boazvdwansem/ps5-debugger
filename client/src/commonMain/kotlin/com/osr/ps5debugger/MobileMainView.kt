package com.osr.ps5debugger

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.domain.model.MemoryRange
import androidx.compose.ui.text.font.FontFamily
import com.osr.ps5debugger.ui.HexViewer
import com.osr.ps5debugger.ui.MemoryViewerLayout
import com.osr.ps5debugger.ui.MemoryScannerView
import com.osr.ps5debugger.ui.WatchList
import com.osr.ps5debugger.ui.MemoryDumperView
import com.osr.ps5debugger.ui.LoggerConsole
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileMainView() {
    val isConnected by AppContainer.debuggerUseCase.isConnected.collectAsState()
    val activeProcess by AppContainer.debuggerUseCase.activeProcess.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // 0: Connection/Main Dashboard, 1: Hex Viewer, 2: Memory Search, 3: Watch List, 4: Memory Dumper
    var currentScreen by remember { mutableStateOf(0) }
    var activeMap by remember { mutableStateOf<MemoryRange?>(null) }
    var jumpToAddress by remember { mutableStateOf<Long?>(null) }

    // Bottom sheets / sub-panels
    var showProcessSelector by remember { mutableStateOf(false) }
    var showRegionSelector by remember { mutableStateOf(false) }
    var showConsoleLogs by remember { mutableStateOf(false) }

    LaunchedEffect(isConnected) {
        if (isConnected) {
            try {
                AppContainer.debuggerUseCase.refreshProcesses()
            } catch (_: Exception) {}
        } else {
            currentScreen = 0
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "PS5 DEBUGGER",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = PS5ThemeColors.AccentCyan
                        )
                        if (isConnected && activeProcess != null) {
                            Text(
                                text = "${activeProcess!!.name} (PID: ${activeProcess!!.pid})",
                                fontSize = 11.sp,
                                color = PS5ThemeColors.TextMuted
                            )
                        }
                    }
                },
                actions = {
                    if (isConnected) {
                        // Quick console toggle
                        IconButton(onClick = { showConsoleLogs = true }) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "Logs",
                                tint = PS5ThemeColors.TextMain
                            )
                        }
                        // Disconnect
                        IconButton(onClick = {
                            coroutineScope.launch {
                                AppContainer.debuggerUseCase.disconnect()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Disconnect",
                                tint = PS5ThemeColors.StatusRed
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PS5ThemeColors.Surface,
                    titleContentColor = PS5ThemeColors.TextMain
                )
            )
        },
        bottomBar = {
            if (isConnected) {
                NavigationBar(
                    containerColor = PS5ThemeColors.Surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.height(68.dp)
                ) {
                    val tabItems = listOf(
                        Triple(0, "Process", Icons.Default.Dashboard),
                        Triple(1, "Memory", Icons.AutoMirrored.Filled.List),
                        Triple(2, "Scan", Icons.Default.Search),
                        Triple(3, "Watch", Icons.Default.Star),
                        Triple(4, "Dump", Icons.Default.SystemUpdateAlt)
                    )
                    tabItems.forEach { (index, label, icon) ->
                        NavigationBarItem(
                            selected = currentScreen == index,
                            onClick = { currentScreen = index },
                            icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp)) },
                            label = { Text(label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PS5ThemeColors.AccentCyan,
                                selectedTextColor = PS5ThemeColors.AccentCyan,
                                indicatorColor = PS5ThemeColors.SecondaryBg,
                                unselectedIconColor = PS5ThemeColors.TextMuted,
                                unselectedTextColor = PS5ThemeColors.TextMuted
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(PS5ThemeColors.DarkBg)
        ) {
            if (!isConnected) {
                // ConnectionScreen optimized inside container
                MobileConnectionScreen()
            } else {
                when (currentScreen) {
                    0 -> MobileDashboard(
                        activeMap = activeMap,
                        onMapSelected = { map ->
                            activeMap = map
                            currentScreen = 1
                        },
                        onNavigateToHex = { currentScreen = 1 }
                    )
                    1 -> MemoryViewerLayout(
                        activeMap = activeMap,
                        jumpToAddress = jumpToAddress,
                        modifier = Modifier.fillMaxSize()
                    )
                    2 -> MemoryScannerView(
                        activeMap = activeMap,
                        modifier = Modifier.fillMaxSize(),
                        onJumpToAddress = { addr ->
                            val map = AppContainer.debuggerUseCase.vmMaps.value.firstOrNull { addr >= it.start && addr < it.end }
                            if (map != null) {
                                activeMap = map
                                jumpToAddress = addr
                                currentScreen = 1
                            }
                        }
                    )
                    3 -> WatchList(
                        onJumpToAddress = { addr ->
                            val map = AppContainer.debuggerUseCase.vmMaps.value.firstOrNull { addr >= it.start && addr < it.end }
                            if (map != null) {
                                activeMap = map
                                jumpToAddress = addr
                                currentScreen = 1
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    4 -> MemoryDumperView(
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Bottom Sheets / Overlays for Process and Region selection
            if (showProcessSelector) {
                MobileBottomSheet(
                    title = "Select Process",
                    onDismiss = { showProcessSelector = false }
                ) {
                    MobileProcessSelector(
                        onProcessSelected = {
                            showProcessSelector = false
                            showRegionSelector = true // Auto pop the region selector
                        }
                    )
                }
            }

            if (showRegionSelector) {
                MobileBottomSheet(
                    title = "Select Memory Region",
                    onDismiss = { showRegionSelector = false }
                ) {
                    MobileRegionSelector(
                        activeMap = activeMap,
                        onMapSelected = { map ->
                            activeMap = map
                            showRegionSelector = false
                            currentScreen = 1 // Navigate directly to hex viewer
                        }
                    )
                }
            }

            if (showConsoleLogs) {
                MobileBottomSheet(
                    title = "System & Kernel Logs",
                    onDismiss = { showConsoleLogs = false }
                ) {
                    Box(modifier = Modifier.fillMaxHeight(0.8f)) {
                        LoggerConsole(
                            modifier = Modifier.fillMaxSize(),
                            actionButton = {
                                Button(
                                    onClick = { showConsoleLogs = false },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.SecondaryBg)
                                ) {
                                    Text("Close", color = PS5ThemeColors.TextMain)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MobileBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() },
        color = Color.Black.copy(alpha = 0.6f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .background(PS5ThemeColors.DarkBg, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .border(1.dp, PS5ThemeColors.BorderColor, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .clickable(enabled = false) { }
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PS5ThemeColors.AccentCyan)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = PS5ThemeColors.TextMuted)
                    }
                }
                HorizontalDivider(color = PS5ThemeColors.BorderColor, modifier = Modifier.padding(bottom = 12.dp))
                Box(modifier = Modifier.weight(1f)) {
                    content()
                }
            }
        }
    }
}

@Composable
fun MobileConnectionScreen() {
    val coroutineScope = rememberCoroutineScope()
    var ipInput by remember { mutableStateOf("192.168.1.100") }
    var isConnecting by remember { mutableStateOf(false) }
    var isDiscovering by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var statusColor by remember { mutableStateOf(PS5ThemeColors.TextMuted) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = PS5ThemeColors.Surface),
            border = BorderStroke(1.dp, PS5ThemeColors.BorderColor)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.DeveloperMode,
                    contentDescription = null,
                    tint = PS5ThemeColors.AccentCyan,
                    modifier = Modifier.size(48.dp)
                )

                Text(
                    text = "PS5 REMOTE DEBUGGER",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PS5ThemeColors.AccentCyan
                )

                Text(
                    text = "Enter console IP or use Auto-Discovery",
                    fontSize = 11.sp,
                    color = PS5ThemeColors.TextMuted
                )

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

                Button(
                    onClick = {
                        coroutineScope.launch {
                            isConnecting = true
                            statusMessage = "Connecting to $ipInput..."
                            statusColor = PS5ThemeColors.AccentCyan
                            try {
                                val success = AppContainer.debuggerUseCase.connect(ipInput)
                                if (!success) {
                                    statusMessage = "Connection failed"
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
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.AccentCyan),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isConnecting) "Connecting..." else "Connect", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = {
                        coroutineScope.launch {
                            isDiscovering = true
                            statusMessage = "Scanning network..."
                            statusColor = PS5ThemeColors.AccentCyan
                            val list = com.osr.ps5debugger.network.Ps5Discovery.discoverConsoles()
                            if (list.isNotEmpty()) {
                                ipInput = list.first()
                                statusMessage = "Found console at ${list.first()}"
                                statusColor = PS5ThemeColors.StatusGreen
                            } else {
                                statusMessage = "No console found"
                                statusColor = PS5ThemeColors.AccentAmber
                            }
                            isDiscovering = false
                        }
                    },
                    enabled = !isConnecting && !isDiscovering,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = PS5ThemeColors.SecondaryBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isDiscovering) "Scanning..." else "Auto-Discover Console", color = PS5ThemeColors.TextMain)
                }

                if (statusMessage.isNotEmpty()) {
                    Text(
                        text = statusMessage,
                        fontSize = 12.sp,
                        color = statusColor
                    )
                }
            }
        }
    }
}

@Composable
fun MobileDashboard(
    activeMap: MemoryRange?,
    onMapSelected: (MemoryRange) -> Unit,
    onNavigateToHex: () -> Unit
) {
    val activeProcess by AppContainer.debuggerUseCase.activeProcess.collectAsState()
    var activeListTab by remember { mutableStateOf(0) } // 0 = Process List, 1 = Region List

    // If active process is disconnected or reset, revert to process tab
    LaunchedEffect(activeProcess) {
        if (activeProcess == null) {
            activeListTab = 0
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("PROCESS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PS5ThemeColors.TextMain)

        // Status row showing active targets side-by-side
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Process Status Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { activeListTab = 0 },
                colors = CardDefaults.cardColors(containerColor = PS5ThemeColors.Surface),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (activeListTab == 0) PS5ThemeColors.AccentCyan else PS5ThemeColors.BorderColor
                )
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Target Process", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (activeListTab == 0) PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMuted)
                    if (activeProcess == null) {
                        Text("No process attached.", color = PS5ThemeColors.TextMuted, fontSize = 12.sp)
                    } else {
                        Text(activeProcess!!.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PS5ThemeColors.TextMain)
                        Text("PID: ${activeProcess!!.pid}", fontSize = 11.sp, color = PS5ThemeColors.TextMuted)
                    }
                }
            }

            // Active Memory Region Card
            Card(
                modifier = Modifier
                    .weight(1.2f)
                    .clickable(enabled = activeProcess != null) { activeListTab = 1 },
                colors = CardDefaults.cardColors(containerColor = PS5ThemeColors.Surface),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (activeListTab == 1) PS5ThemeColors.AccentCyan else PS5ThemeColors.BorderColor
                )
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Memory Target", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (activeListTab == 1) PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMuted)
                        if (activeMap != null) {
                            IconButton(
                                onClick = onNavigateToHex,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = "View hex", tint = PS5ThemeColors.AccentCyan, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    if (activeMap == null) {
                        Text("No region selected.", color = PS5ThemeColors.TextMuted, fontSize = 12.sp)
                    } else {
                        Text(activeMap.name.ifEmpty { "unnamed" }, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PS5ThemeColors.TextMain)
                        Text(
                            text = String.format("0x%X - 0x%X", activeMap.start, activeMap.end),
                            fontSize = 10.sp,
                            color = PS5ThemeColors.TextMuted
                        )
                    }
                }
            }
        }

        // Inline Lists
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val isWide = maxWidth >= 600.dp
            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        MobileProcessSelector(onProcessSelected = { activeListTab = 1 })
                    }
                    Box(modifier = Modifier.weight(1.3f).fillMaxHeight()) {
                        if (activeProcess != null) {
                            MobileRegionSelector(
                                activeMap = activeMap,
                                onMapSelected = onMapSelected
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(PS5ThemeColors.Surface, shape = RoundedCornerShape(10.dp))
                                    .border(1.dp, PS5ThemeColors.BorderColor, shape = RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Select a process to view memory regions", color = PS5ThemeColors.TextMuted, fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (activeListTab == 0) {
                        MobileProcessSelector(onProcessSelected = { activeListTab = 1 })
                    } else {
                        if (activeProcess != null) {
                            MobileRegionSelector(
                                activeMap = activeMap,
                                onMapSelected = onMapSelected
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(PS5ThemeColors.Surface, shape = RoundedCornerShape(10.dp))
                                    .border(1.dp, PS5ThemeColors.BorderColor, shape = RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Select a process to view memory regions", color = PS5ThemeColors.TextMuted, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MobileProcessSelector(
    onProcessSelected: (com.osr.ps5debugger.domain.model.Process) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val processes by AppContainer.debuggerUseCase.processes.collectAsState()
    val activeProcess by AppContainer.debuggerUseCase.activeProcess.collectAsState()
    var searchText by remember { mutableStateOf("") }
    var clickedProcPid by remember { mutableStateOf<Int?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            placeholder = { Text("Filter processes...", color = PS5ThemeColors.TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PS5ThemeColors.AccentCyan) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PS5ThemeColors.AccentCyan,
                unfocusedBorderColor = PS5ThemeColors.BorderColor,
                focusedTextColor = PS5ThemeColors.TextMain,
                unfocusedTextColor = PS5ThemeColors.TextMain
            )
        )

        val filtered = processes.filter { it.name.contains(searchText, ignoreCase = true) }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered) { proc ->
                val isSelected = activeProcess?.pid == proc.pid
                val isClicked = clickedProcPid == proc.pid
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!isSelected && clickedProcPid == null) {
                                clickedProcPid = proc.pid
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(200)
                                    AppContainer.debuggerUseCase.selectProcess(proc)
                                    onProcessSelected(proc)
                                    clickedProcPid = null
                                }
                            }
                        },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected || isClicked) PS5ThemeColors.AccentCyan.copy(alpha = 0.12f) else PS5ThemeColors.Surface,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected || isClicked) PS5ThemeColors.AccentCyan else PS5ThemeColors.BorderColor
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = if (isSelected || isClicked) PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                proc.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isSelected || isClicked) PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMain
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = PS5ThemeColors.SecondaryBg,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                text = "PID: ${proc.pid}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PS5ThemeColors.AccentCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MobileRegionSelector(
    activeMap: MemoryRange?,
    onMapSelected: (MemoryRange) -> Unit
) {
    val maps by AppContainer.debuggerUseCase.vmMaps.collectAsState()
    var searchText by remember { mutableStateOf("") }
    var clickedMapId by remember { mutableStateOf<Long?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            placeholder = { Text("Filter regions...", color = PS5ThemeColors.TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PS5ThemeColors.AccentCyan) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PS5ThemeColors.AccentCyan,
                unfocusedBorderColor = PS5ThemeColors.BorderColor,
                focusedTextColor = PS5ThemeColors.TextMain,
                unfocusedTextColor = PS5ThemeColors.TextMain
            )
        )

        val filtered = maps.filter { it.name.contains(searchText, ignoreCase = true) }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered) { map ->
                val isSelected = activeMap?.start == map.start
                val isClicked = clickedMapId == map.start
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (clickedMapId == null) {
                                clickedMapId = map.start
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(250)
                                    onMapSelected(map)
                                    clickedMapId = null
                                }
                            }
                        },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected || isClicked) PS5ThemeColors.AccentCyan.copy(alpha = 0.12f) else PS5ThemeColors.Surface,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected || isClicked) PS5ThemeColors.AccentCyan else PS5ThemeColors.BorderColor
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = map.name.ifEmpty { "unnamed region" },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isSelected || isClicked) PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMain
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = when {
                                    map.getProtString().contains("w") -> PS5ThemeColors.StatusRed.copy(alpha = 0.15f)
                                    map.getProtString().contains("x") -> PS5ThemeColors.AccentAmber.copy(alpha = 0.15f)
                                    else -> PS5ThemeColors.SecondaryBg
                                }
                            ) {
                                Text(
                                    text = map.getProtString(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        map.getProtString().contains("w") -> PS5ThemeColors.StatusRed
                                        map.getProtString().contains("x") -> PS5ThemeColors.AccentAmber
                                        else -> PS5ThemeColors.TextMuted
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            val startStr = map.start.toString(16).uppercase()
                            val endStr = map.end.toString(16).uppercase()
                            Text(
                                text = "0x$startStr - 0x$endStr",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = PS5ThemeColors.TextMuted
                            )
                            val sizeMb = map.size.toDouble() / (1024.0 * 1024.0)
                            Text(
                                text = String.format("%.2f MB", sizeMb),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PS5ThemeColors.AccentCyan
                            )
                        }
                    }
                }
            }
        }
    }
}

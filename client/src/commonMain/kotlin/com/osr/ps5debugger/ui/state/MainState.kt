package com.osr.ps5debugger.ui.state

import com.osr.ps5debugger.di.RegionPreloader
import com.osr.ps5debugger.di.HexCache
import com.osr.ps5debugger.di.DisassemblyCache
import com.osr.ps5debugger.domain.service.SymbolManager
import androidx.compose.runtime.*
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.util.copyToClipboard
import com.osr.ps5debugger.ui.watchlist.watchListFromJson
import com.osr.ps5debugger.ui.watchlist.watchListToJson
import com.osr.ps5debugger.ui.watchlist.sessionToJson
import com.osr.ps5debugger.ui.watchlist.sessionFromJson
import com.osr.ps5debugger.ui.memory.disasm.DisasmFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MainState(
    private val scope: CoroutineScope,
    private val onExit: () -> Unit
) {
    var activeMap by mutableStateOf<MemoryRange?>(null)
    val activeMaps = mutableStateListOf<MemoryRange>()
    var selectedTab by mutableIntStateOf(0)
    var jumpToAddress by mutableStateOf<Long?>(null)
    var viewMode by mutableIntStateOf(2) // 0 = Disassembly, 1 = Graph, 2 = Hex Viewer
    
    var isConsoleVisible by mutableStateOf(false)
    var isConsoleFloating by mutableStateOf(false)
    var isConsoleMaximized by mutableStateOf(false)
    var consoleDockHeight by mutableStateOf(200f)
    var isSidebarVisible by mutableStateOf(true)
    var isDebugSidebarVisible by mutableStateOf(false)
    var isSettingsOpen by mutableStateOf(false)
    var showGotoDialog by mutableStateOf(false)
    var activeRightTab by mutableStateOf<String?>(null)
    var xrefTargetAddress by mutableStateOf<Long?>(null)
    
    val activeBreakpoints = mutableStateMapOf<Int, Long>()
    val activeWatchpoints = mutableStateMapOf<Int, Long>()
    
    var selectionStart by mutableStateOf<Long?>(null)
    var selectionEnd by mutableStateOf<Long?>(null)

    // Pending Cheat State for Dialog
    var pendingCheatToCreate by mutableStateOf<com.osr.ps5debugger.domain.model.Cheat?>(null)
    var showAddCheatDialog by mutableStateOf(false)
    var showSaveSessionDialog by mutableStateOf(false)
    var showConnectDialog by mutableStateOf(false)

    val isOfflineSession: Boolean get() = AppContainer.isOfflineSession
    val loadedSessionName: String? get() = AppContainer.loadedSessionName

    fun closeAllOpenedRegions() {
        activeMap = null
        activeMaps.clear()
        jumpToAddress = null
        selectionStart = null
        selectionEnd = null
        if (!AppContainer.isOfflineSession && AppContainer.loadedSessionName == null) {
            HexCache.clearHexCache()
        }
    }

    fun disconnectAndReturnToConnectionScreen() {
        scope.launch {
            if (AppContainer.debuggerUseCase.isConnected.value) {
                AppContainer.debuggerUseCase.disconnect()
            }
            if (AppContainer.isOfflineSession) {
                closeOfflineSession()
            } else {
                AppContainer.loadedSessionName = null
                closeAllOpenedRegions()
            }
        }
    }

    fun loadSessionFile(file: java.io.File) {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = com.osr.ps5debugger.util.SessionManager.loadSession(file)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                result.onSuccess { name ->
                    val vmMaps = AppContainer.debuggerUseCase.vmMaps.value
                    val targetMap = vmMaps.firstOrNull { map ->
                        var p = (map.start / 65536L) * 65536L
                        var hasData = false
                        while (p < map.end) {
                            if (HexCache.hexCache.containsKey(p)) {
                                hasData = true
                                break
                            }
                            p += 65536L
                        }
                        hasData || DisassemblyCache.instructionsCache.keys.any { it.startsWith("${map.start}_${map.end}") }
                    } ?: vmMaps.firstOrNull()

                    activeMap = targetMap
                    activeMaps.clear()
                    selectedTab = 0
                    AppContainer.debuggerUseCase.log("SESSION", "Session '$name' loaded successfully", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                }.onFailure { ex ->
                    AppContainer.debuggerUseCase.log("SESSION", "Failed to load session from ${file.name}: ${ex.message}", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                }
            }
        }
    }

    fun closeOfflineSession() {
        AppContainer.isOfflineSession = false
        AppContainer.loadedSessionName = null
        AppContainer.debuggerUseCase.setOfflineSession(null, null, emptyList())
        closeAllOpenedRegions()
    }

    val isConnected = AppContainer.debuggerUseCase.isConnected
    val watchlist = AppContainer.debuggerUseCase.watchlist
    val gameCheatProfiles = AppContainer.debuggerUseCase.gameCheatProfiles

    init {
        AppContainer.onNavigateRequested = { addr, mode ->
            scope.launch {
                jumpToAddress = addr
                viewMode = mode
                selectedTab = 0
            }
        }
    }

    fun handleFileAction(action: String) {
        when (action) {
            "Save" -> {
                showSaveSessionDialog = true
            }
            "Load" -> {
                AppContainer.filePicker?.pickSessionFile { file ->
                    if (file != null) {
                        if (file.name.endsWith(".ps5session", ignoreCase = true)) {
                            loadSessionFile(file)
                        } else {
                            try {
                                val json = file.readText(Charsets.UTF_8)
                                val session = sessionFromJson(json)
                                
                                val activeProcessInfo = AppContainer.debuggerUseCase.activeProcessInfo.value
                                if (activeProcessInfo != null && session.processName != null) {
                                    val nameMatch = activeProcessInfo.name == session.processName
                                    val titleMatch = session.titleId == null || activeProcessInfo.titleId == session.titleId
                                    val contentMatch = session.contentId == null || activeProcessInfo.contentId == session.contentId
                                    if (!nameMatch || !titleMatch || !contentMatch) {
                                        AppContainer.debuggerUseCase.log(
                                            "FILE", 
                                            "Warning: Loaded session is for ${session.processName} (${session.titleId}), but active process is ${activeProcessInfo.name} (${activeProcessInfo.titleId})", 
                                            com.osr.ps5debugger.domain.model.LogEntry.Level.WARN
                                        )
                                    }
                                }
                                
                                AppContainer.debuggerUseCase.clearWatchlist()
                                session.watchlist.forEach { AppContainer.debuggerUseCase.addWatchItem(it) }
                                
                                val vmMaps = AppContainer.debuggerUseCase.vmMaps.value
                                session.symbols.forEach { sym ->
                                    val map = vmMaps.firstOrNull { it.name == sym.mapName }
                                    if (map != null) {
                                        val absAddr = map.start + sym.offset
                                        SymbolManager.renameSymbol(absAddr, sym.name)
                                        if (sym.isFunction) {
                                            if (!SymbolManager.discoveredFunctions.contains(absAddr)) {
                                                SymbolManager.discoveredFunctions.add(absAddr)
                                                SymbolManager.discoveredFunctions.sortBy { it.toULong() }
                                            }
                                        } else {
                                            if (!SymbolManager.discoveredJumpTargets.contains(absAddr)) {
                                                SymbolManager.discoveredJumpTargets.add(absAddr)
                                                SymbolManager.discoveredJumpTargets.sortBy { it.toULong() }
                                            }
                                        }
                                    }
                                }
                                
                                AppContainer.debuggerUseCase.log("FILE", "Session loaded successfully", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                            } catch (e: Exception) {
                                AppContainer.debuggerUseCase.log("FILE", "Failed to load session: ${e.message}", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                            }
                        }
                    }
                }
            }
            "Load eboot" -> {
                AppContainer.filePicker?.loadEboot { bytes, fileName ->
                    if (bytes != null && fileName != null) {
                        val mergedRange = com.osr.ps5debugger.util.ElfUtil.getMergedModule(bytes, fileName)
                        val entry = com.osr.ps5debugger.util.ElfUtil.getEntryPoint(bytes)
                        
                        if (mergedRange != null) {
                            activeMaps.clear()
                            activeMaps.add(mergedRange)
                            activeMap = mergedRange
                            
                            if (entry != null) {
                                SymbolManager.elfEntryPoint = entry
                                if (!SymbolManager.discoveredFunctions.contains(entry)) {
                                    SymbolManager.discoveredFunctions.add(entry)
                                    SymbolManager.discoveredFunctions.sortBy { it.toULong() }
                                }
                                jumpToAddress = entry
                                selectionStart = entry
                                selectionEnd = entry
                            } else {
                                jumpToAddress = mergedRange.start
                            }
                            
                            selectedTab = 0
                            AppContainer.debuggerUseCase.log("FILE", "Loaded ELF file: $fileName. Merged ${mergedRange.subRanges.size} segments. Entry: 0x${entry?.toString(16) ?: "N/A"}", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                        } else {
                            // Fallback if no segments found (e.g. not a valid ELF)
                            val newRange = MemoryRange(
                                name = fileName,
                                start = 0,
                                end = bytes.size.toLong(),
                                offset = 0,
                                protections = 7, // RWX
                                localData = bytes
                            )
                            activeMaps.clear()
                            activeMaps.add(newRange)
                            activeMap = newRange
                            selectedTab = 0
                            AppContainer.debuggerUseCase.log("FILE", "Loaded local file: $fileName (${bytes.size} bytes). No ELF segments found.", com.osr.ps5debugger.domain.model.LogEntry.Level.WARN)
                        }
                    }
                }
            }
            "Export disassembly" -> exportDisassembly()
            "Export Hexadecimal" -> exportHexadecimal()
            "Exit" -> onExit()
            "Preferences" -> {
                isSettingsOpen = true
            }

        }
    }

    private fun exportDisassembly() {
        val process = AppContainer.debuggerUseCase.activeProcess.value
        val map = activeMap ?: activeMaps.firstOrNull()
        if (process == null || map == null) {
            AppContainer.debuggerUseCase.log("FILE", "Select a process and memory map before exporting", com.osr.ps5debugger.domain.model.LogEntry.Level.WARN)
            return
        }
        scope.launch {
            try {
            val disasmStart = RegionPreloader.getDisassemblyStartForMap(map)
            val length = (map.end - disasmStart).coerceAtMost(256 * 1024L).toInt()
            val content = if ((map.protections and 4) == 0) {
                // Non-executable: export raw hex instead
                val bytes = AppContainer.clientAdapter.client.readMemory(process.pid, map.start, length)
                buildString {
                    for (offset in bytes.indices step 16) {
                        append("0x${(map.start + offset).toString(16).uppercase().padStart(16, '0')}: ")
                        append(bytes.copyOfRange(offset, minOf(offset + 16, bytes.size)).joinToString(" ") { byte -> "%02X".format(byte) })
                        append('\n')
                    }
                }
            } else {
                val instructions = AppContainer.clientAdapter.client.disassembleRegion(process.pid, disasmStart, length, 10_000)
                val bytes = AppContainer.clientAdapter.client.readMemory(process.pid, disasmStart, length)
                instructions.joinToString("\n") { instr ->
                    val offset = (instr.addr - disasmStart).toInt()
                    val instrBytes = if (offset >= 0 && offset + instr.length <= bytes.size) bytes.copyOfRange(offset, offset + instr.length) else byteArrayOf()
                    "0x${instr.addr.toString(16).uppercase().padStart(16, '0')}  ${DisasmFormatter.getMnemonic(instr, instrBytes)} ${DisasmFormatter.formatOperands(instr, instrBytes)}".trimEnd()
                }
            }
            AppContainer.filePicker?.saveText("disassembly.txt", content) { success ->
                AppContainer.debuggerUseCase.log("FILE", if (success) "Disassembly exported successfully" else "Disassembly export cancelled", if (success) com.osr.ps5debugger.domain.model.LogEntry.Level.INFO else com.osr.ps5debugger.domain.model.LogEntry.Level.WARN)
            }
            } catch (e: Exception) {
                AppContainer.debuggerUseCase.log("FILE", "Disassembly export failed: ${e.message}", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
            }
        }
    }

    private fun exportHexadecimal() {
        val process = AppContainer.debuggerUseCase.activeProcess.value
        val map = activeMap ?: activeMaps.firstOrNull()
        if (process == null || map == null) {
            AppContainer.debuggerUseCase.log("FILE", "Select a process and memory map before exporting", com.osr.ps5debugger.domain.model.LogEntry.Level.WARN)
            return
        }
        scope.launch {
            try {
                val length = (map.end - map.start).coerceAtMost(4 * 1024 * 1024L).toInt()
                val bytes = AppContainer.clientAdapter.client.readMemory(process.pid, map.start, length)
                val content = buildString {
                    for (offset in bytes.indices step 16) {
                        append("0x${(map.start + offset).toString(16).uppercase().padStart(16, '0')}: ")
                        append(bytes.copyOfRange(offset, minOf(offset + 16, bytes.size)).joinToString(" ") { byte -> "%02X".format(byte) })
                        append('\n')
                    }
                }
                AppContainer.filePicker?.saveText("memory.hex", content) { success ->
                    AppContainer.debuggerUseCase.log("FILE", if (success) "Hexadecimal memory exported successfully" else "Hexadecimal export cancelled", if (success) com.osr.ps5debugger.domain.model.LogEntry.Level.INFO else com.osr.ps5debugger.domain.model.LogEntry.Level.WARN)
                }
            } catch (e: Exception) {
                AppContainer.debuggerUseCase.log("FILE", "Hexadecimal export failed: ${e.message}", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
            }
        }
    }

    fun handleViewAction(action: String) {
        selectedTab = 0
        viewMode = when (action) {
            "Linear", "Disassembly" -> 0
            "Graph" -> 1
            "Hex" -> 2
            else -> 2
        }
    }

    fun handleEditAction(action: String) {
        when (action) {
            "Copy Address" -> {
                selectionStart?.let { copyToClipboard("0x${it.toString(16).uppercase()}") }
            }
            "Copy" -> {
                selectionStart?.let { start ->
                    selectionEnd?.let { end ->
                        scope.launch {
                            val s = minOf(start, end)
                            val e = maxOf(start, end)
                            val len = (e - s + 1).toInt().coerceAtMost(1024 * 1024)
                            AppContainer.debuggerUseCase.readMemory(s, len).onSuccess { data ->
                                val hex = data.joinToString(" ") { it.toUByte().toString(16).padStart(2, '0').uppercase() }
                                copyToClipboard(hex)
                            }
                        }
                    }
                }
            }
            "Select All" -> {
                activeMap?.let {
                    selectionStart = it.start
                    selectionEnd = it.end - 1
                }
            }
            "Select None" -> {
                selectionStart = null
                selectionEnd = null
            }
            "Go to address" -> {
                selectedTab = 0
            }
            "Preferences" -> {
                isSettingsOpen = true
            }
        }
    }
}

@Composable
fun rememberMainState(
    scope: CoroutineScope = rememberCoroutineScope(),
    onExit: () -> Unit = {}
) = remember { MainState(scope, onExit) }



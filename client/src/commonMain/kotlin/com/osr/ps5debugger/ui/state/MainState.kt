package com.osr.ps5debugger.ui.state

import androidx.compose.runtime.*
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.util.copyToClipboard
import com.osr.ps5debugger.ui.watchlist.watchListFromJson
import com.osr.ps5debugger.ui.watchlist.watchListToJson
import com.osr.ps5debugger.ui.watchlist.sessionToJson
import com.osr.ps5debugger.ui.watchlist.sessionFromJson
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
    var isSidebarVisible by mutableStateOf(true)
    var isDebugSidebarVisible by mutableStateOf(false)
    var isSettingsOpen by mutableStateOf(false)
    
    val activeBreakpoints = mutableStateMapOf<Int, Long>()
    val activeWatchpoints = mutableStateMapOf<Int, Long>()
    
    var selectionStart by mutableStateOf<Long?>(null)
    var selectionEnd by mutableStateOf<Long?>(null)

    val isConnected = AppContainer.debuggerUseCase.isConnected
    val watchlist = AppContainer.debuggerUseCase.watchlist

    fun handleFileAction(action: String) {
        when (action) {
            "Save" -> {
                val activeProcessInfo = AppContainer.debuggerUseCase.activeProcessInfo.value
                val vmMaps = AppContainer.debuggerUseCase.vmMaps.value
                val json = sessionToJson(
                    watchlist = watchlist.value,
                    customSymbols = AppContainer.symbolNames.toMap(),
                    discoveredFunctions = AppContainer.discoveredFunctions.toList(),
                    vmMaps = vmMaps,
                    processInfo = activeProcessInfo
                )
                AppContainer.filePicker?.saveJson("session.json", json) { success ->
                    if (success) AppContainer.debuggerUseCase.log("FILE", "Session saved successfully", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                }
            }
            "Load" -> {
                AppContainer.filePicker?.loadJson { json ->
                    if (json != null) {
                        try {
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
                                    AppContainer.renameSymbol(absAddr, sym.name)
                                    if (sym.isFunction) {
                                        if (!AppContainer.discoveredFunctions.contains(absAddr)) {
                                            AppContainer.discoveredFunctions.add(absAddr)
                                            AppContainer.discoveredFunctions.sortBy { it.toULong() }
                                        }
                                    } else {
                                        if (!AppContainer.discoveredJumpTargets.contains(absAddr)) {
                                            AppContainer.discoveredJumpTargets.add(absAddr)
                                            AppContainer.discoveredJumpTargets.sortBy { it.toULong() }
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
            "Exit" -> onExit()
            "Preferences" -> {
                isSettingsOpen = true
            }
        }
    }

    fun handleViewAction(action: String) {
        selectedTab = 0
        viewMode = when (action) {
            "Disassembly" -> 0
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

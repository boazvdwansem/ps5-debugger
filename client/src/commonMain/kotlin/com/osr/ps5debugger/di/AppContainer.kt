package com.osr.ps5debugger.di

import com.osr.ps5debugger.infrastructure.adapter.SocketDebuggerAdapter
import com.osr.ps5debugger.infrastructure.adapter.FileLogStorageAdapter
import com.osr.ps5debugger.infrastructure.adapter.FileCheatStorageAdapter
import com.osr.ps5debugger.domain.service.DebuggerDomainService
import com.osr.ps5debugger.ports.inbound.DebuggerUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

object AppContainer {
    var debugMockEnabled by mutableStateOf(
        try { com.osr.ps5debugger.util.DefaultIpHelper.isMockEnabled() } catch (_: Exception) { false }
    )
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var isOfflineSession by mutableStateOf(false)
    var loadedSessionName by mutableStateOf<String?>(null)
    
    val unsupportedCommands = mutableSetOf<Int>()

    val clientAdapter = SocketDebuggerAdapter(appScope)
    val logStorageAdapter = FileLogStorageAdapter()
    val cheatStorageAdapter = FileCheatStorageAdapter()

    val debuggerUseCase: DebuggerUseCase = DebuggerDomainService(
        clientPort = clientAdapter,
        logPort = logStorageAdapter,
        cheatStorage = cheatStorageAdapter
    )

    var onNavigateToMemory: ((Long) -> Unit)? = null
    var onNavigateRequested: ((Long, Int) -> Unit)? = null
    var onMcpServerToggled: ((Boolean) -> Unit)? = null
    var onCreateCheatRequested: ((com.osr.ps5debugger.domain.model.Cheat) -> Unit)? = null
    var filePicker: com.osr.ps5debugger.ports.inbound.FilePicker? = null
    var defaultDumpPath: String = ""
    
    // Delegation for backward compatibility (optional but recommended)
    @Deprecated("Use SymbolManager instead", ReplaceWith("SymbolManager.symbolNames", "com.osr.ps5debugger.domain.service.SymbolManager"))
    val symbolNames get() = com.osr.ps5debugger.domain.service.SymbolManager.symbolNames

    @Deprecated("Use SymbolManager instead", ReplaceWith("SymbolManager.discoveredFunctions", "com.osr.ps5debugger.domain.service.SymbolManager"))
    val discoveredFunctions get() = com.osr.ps5debugger.domain.service.SymbolManager.discoveredFunctions

    @Deprecated("Use SymbolManager instead", ReplaceWith("SymbolManager.discoveredJumpTargets", "com.osr.ps5debugger.domain.service.SymbolManager"))
    val discoveredJumpTargets get() = com.osr.ps5debugger.domain.service.SymbolManager.discoveredJumpTargets

    @Deprecated("Use DisassemblyCache instead", ReplaceWith("DisassemblyCache.instructionsCache", "com.osr.ps5debugger.di.DisassemblyCache"))
    val instructionsCache get() = DisassemblyCache.instructionsCache

    @Deprecated("Use DisassemblyCache instead", ReplaceWith("DisassemblyCache.disassemblyProgressCache", "com.osr.ps5debugger.di.DisassemblyCache"))
    val disassemblyProgressCache get() = DisassemblyCache.disassemblyProgressCache

    @Deprecated("Use HexCache instead", ReplaceWith("HexCache.hexCache", "com.osr.ps5debugger.di.HexCache"))
    val hexCache get() = HexCache.hexCache

    @Deprecated("Use HexCache instead", ReplaceWith("HexCache.hexProgressCache", "com.osr.ps5debugger.di.HexCache"))
    val hexProgressCache get() = HexCache.hexProgressCache
    
    @Deprecated("Use DisassemblyCache and HexCache directly")
    fun clearCache(mapKey: String) {
        DisassemblyCache.clearCache(mapKey)
        HexCache.hexProgressCache.remove(mapKey)
    }

    typealias IconState = MetadataResolver.IconState
    val iconCache get() = MetadataResolver.iconCache
    val titleIdToName get() = MetadataResolver.titleIdToName
    val titleIdToVersion get() = MetadataResolver.titleIdToVersion
    val titleIdToPlatform get() = MetadataResolver.titleIdToPlatform
    suspend fun fetchMetadata(titleId: String, consoleIp: String) = MetadataResolver.fetchMetadata(titleId, consoleIp)

    suspend fun preloadRegionInBackground(map: com.osr.ps5debugger.domain.model.MemoryRange, pid: Int?, jumpToAddress: Long? = null) =
        RegionPreloader.preloadRegionInBackground(map, pid, jumpToAddress)
    suspend fun getDisassemblyStartForMap(map: com.osr.ps5debugger.domain.model.MemoryRange) =
        RegionPreloader.getDisassemblyStartForMap(map)

    fun getInstructions(mapKey: String) = DisassemblyCache.getInstructions(mapKey)
    fun getHexCache(mapKey: String = "") = HexCache.getHexCache(mapKey)
    fun clearHexCache() = HexCache.clearHexCache()

    var elfEntryPoint: Long?
        get() = com.osr.ps5debugger.domain.service.SymbolManager.elfEntryPoint
        set(value) { com.osr.ps5debugger.domain.service.SymbolManager.elfEntryPoint = value }
    fun getSymbolName(address: Long, isFunction: Boolean) =
        com.osr.ps5debugger.domain.service.SymbolManager.getSymbolName(address, isFunction)
    fun getSymbolNameForTarget(address: Long, isCall: Boolean) =
        com.osr.ps5debugger.domain.service.SymbolManager.getSymbolNameForTarget(address, isCall)
    fun renameSymbol(address: Long, newName: String) =
        com.osr.ps5debugger.domain.service.SymbolManager.renameSymbol(address, newName)
}


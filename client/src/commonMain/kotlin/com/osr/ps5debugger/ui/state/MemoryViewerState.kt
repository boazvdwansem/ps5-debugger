package com.osr.ps5debugger.ui.state

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.ui.DisasmLine
import com.osr.ps5debugger.protocol.Ps5DisasmInstr
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

import androidx.compose.ui.graphics.Color
import com.osr.ps5debugger.ui.disasm.DisasmFormatter

class MemoryViewerState(
    private val scope: kotlinx.coroutines.CoroutineScope,
    activeMapInitial: MemoryRange?,
    activeMapsInitial: List<MemoryRange> = emptyList(),
    jumpToAddressInitial: Long?,
    viewModeParamInitial: Int?,
    onViewModeChangedInitial: ((Int) -> Unit)?,
    selectionStartParamInitial: Long?,
    selectionEndParamInitial: Long?,
    onSelectionChangedInitial: ((Long?, Long?) -> Unit)?
) {
    var activeMap by mutableStateOf(activeMapInitial)
    val activeMaps = mutableStateListOf<MemoryRange>().apply { addAll(activeMapsInitial) }
    var jumpToAddress by mutableStateOf(jumpToAddressInitial)
    var viewModeParam by mutableStateOf(viewModeParamInitial)
    var onViewModeChanged by mutableStateOf(onViewModeChangedInitial)
    var selectionStartParam by mutableStateOf(selectionStartParamInitial)
    var selectionEndParam by mutableStateOf(selectionEndParamInitial)
    var onSelectionChanged by mutableStateOf(onSelectionChangedInitial)

    var internalViewMode by mutableIntStateOf(0)
    val viewMode get() = viewModeParam ?: internalViewMode
    
    fun setViewMode(mode: Int) {
        onViewModeChanged?.invoke(mode) ?: run { internalViewMode = mode }
    }

    var currentJumpAddress by mutableStateOf(jumpToAddressInitial)
    
    val instructions: SnapshotStateList<DisasmLine> get() {
        val map = activeMap ?: activeMaps.firstOrNull()
        val key = map?.let { "${it.start}_${it.end}_${it.name}" } ?: "default"
        return AppContainer.getInstructions(key)
    }

    val functions = mutableStateListOf<Long>()
    var isLoading by mutableStateOf(false)

    // Pre-calculated UI Metadata
    var activeJumps by mutableStateOf<List<Pair<Long, Long>>>(emptyList())
    var jumpTracks by mutableStateOf<Map<Pair<Long, Long>, Int>>(emptyMap())
    var jumpColors by mutableStateOf<Map<Pair<Long, Long>, Color>>(emptyMap())
    var jumpTargets by mutableStateOf<Set<Long>>(emptySet())
    
    suspend fun updateMetadata() = withContext(Dispatchers.Default) {
        val instrs = instructions.toList()
        if (instrs.isEmpty()) {
            withContext(Dispatchers.Main) {
                activeJumps = emptyList()
                jumpTracks = emptyMap()
                jumpColors = emptyMap()
                jumpTargets = emptySet()
            }
            return@withContext
        }
        
        val addrSet = instrs.map { it.instr.addr }.toSet()
        val jumps = instrs.mapNotNull { line ->
            val target = DisasmFormatter.getJumpTarget(line.instr, line.bytes)
            if (target != 0L && addrSet.contains(target)) {
                line.instr.addr to target
            } else null
        }
        
        val targets = jumps.map { it.second }.toSet()
        
        val colorsList = listOf(
            Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8), Color(0xFF9575CD),
            Color(0xFF7986CB), Color(0xFF64B5F6), Color(0xFF4FC3F7), Color(0xFF4DD0E1),
            Color(0xFF4DB6AC), Color(0xFF81C784), Color(0xFFD4E157), Color(0xFFFFD54F),
            Color(0xFFFFB74D), Color(0xFFFF8A65)
        )
        val colors = jumps.mapIndexed { idx, jump -> jump to colorsList[idx % colorsList.size] }.toMap()
        
        val rangesOverlap = { a1: Long, a2: Long, b1: Long, b2: Long ->
            val minA = minOf(a1, a2)
            val maxA = maxOf(a1, a2)
            val minB = minOf(b1, b2)
            val maxB = maxOf(b1, b2)
            maxA >= minB && maxB >= minA
        }

        val tracks = mutableMapOf<Pair<Long, Long>, Int>()
        val sorted = jumps.sortedBy { kotlin.math.abs(it.second - it.first) }
        for (j in sorted) {
            var track = 0
            while (true) {
                val ok = tracks.none { (other, otherTrack) ->
                    otherTrack == track && rangesOverlap(j.first, j.second, other.first, other.second)
                }
                if (ok) {
                    tracks[j] = track
                    break
                }
                track++
            }
        }
        
        withContext(Dispatchers.Main) {
            activeJumps = jumps
            jumpTracks = tracks
            jumpColors = colors
            jumpTargets = targets
            val mergedTargets = (AppContainer.discoveredJumpTargets + targets).distinct()
            AppContainer.discoveredJumpTargets.clear()
            AppContainer.discoveredJumpTargets.addAll(mergedTargets)
        }
    }

    var internalSelectionStart by mutableStateOf<Long?>(null)
    var internalSelectionEnd by mutableStateOf<Long?>(null)
    
    val selectionStart get() = selectionStartParam ?: internalSelectionStart
    val selectionEnd get() = selectionEndParam ?: internalSelectionEnd
    
    fun updateSelection(start: Long?, end: Long?) {
        onSelectionChanged?.invoke(start, end) ?: run {
            internalSelectionStart = start
            internalSelectionEnd = end
        }
    }
    
    val isAttached = AppContainer.debuggerUseCase.isAttached
    val threadList = AppContainer.debuggerUseCase.threadList
    val selectedLwpid = AppContainer.debuggerUseCase.selectedLwpid
    val selectedRegs = AppContainer.debuggerUseCase.selectedRegs
    val selectedDbRegs = AppContainer.debuggerUseCase.selectedDbRegs
    val selectedFsGs = AppContainer.debuggerUseCase.selectedFsGs
    private var lastLoadedMapKey: String? = null

    suspend fun loadInitialInstructions() {
        val activeProcess = AppContainer.debuggerUseCase.activeProcess.value
        val isConnected = AppContainer.debuggerUseCase.isConnected.value

        val allTargets = (listOfNotNull(activeMap) + activeMaps).distinctBy { it.start }
        val currentTarget = activeMap ?: allTargets.firstOrNull() ?: return
        
        val hasRemote = allTargets.any { it.localData == null }
        val hasLocal = allTargets.any { it.localData != null }
        
        if (hasRemote && !hasLocal && (activeProcess == null || !isConnected)) {
            instructions.clear()
            lastLoadedMapKey = null
            return
        }

        val execTargets = allTargets.filter { (it.protections and 4) != 0 || it.localData != null }
        val currentTargets = (listOfNotNull(activeMap) + execTargets).distinctBy { it.start }
        
        if (currentTargets.isEmpty()) {
            instructions.clear()
            functions.clear()
            lastLoadedMapKey = null
            return
        }

        // Optimization: If local file and already loaded for this module, don't clear or reload
        val mapKey = "${currentTarget.start}_${currentTarget.end}_${currentTarget.name}"
        if (currentTarget.localData != null && mapKey == lastLoadedMapKey && instructions.isNotEmpty()) {
            return
        }

        // Reset state before loading
        withContext(Dispatchers.Main) {
            instructions.clear()
            functions.clear()
            isLoading = true
            lastLoadedMapKey = mapKey
        }
        
        try {
            val (finalLines, finalFunctions) = withContext(Dispatchers.Default) {
                val client = AppContainer.clientAdapter.client
                val allLines = mutableListOf<DisasmLine>()
                
                for (map in currentTargets) {
                    if (map.localData != null && map.subRanges.isNotEmpty()) {
                        // Parallel segment disassembly for local files
                        val segmentResults = map.subRanges.map { seg ->
                            async {
                                val segData = seg.localData ?: return@async emptyList<DisasmLine>()
                                val syncAddrs = (AppContainer.discoveredFunctions.toSet() + AppContainer.symbolNames.keys.toSet() + AppContainer.discoveredJumpTargets.toSet())
                                val segInstrs = com.osr.ps5debugger.util.LocalDisassembler.disassemble(segData, seg.start, syncAddrs)
                                
                                segInstrs.map { instr ->
                                    val offset = (instr.addr - seg.start).toInt()
                                    val instrBytes = if (offset >= 0 && offset + instr.length <= segData.size) {
                                        segData.copyOfRange(offset, offset + instr.length)
                                    } else ByteArray(0)
                                    val symbolName = AppContainer.symbolNames[instr.addr]
                                    DisasmLine(instr, instrBytes, seg, symbolName)
                                }
                            }
                        }.awaitAll().flatten()
                        allLines.addAll(segmentResults)
                    } else {
                        // Standard initial load for live memory (don't load everything, it's too much)
                        val startAddr = if (currentJumpAddress != null && currentJumpAddress!! >= map.start && currentJumpAddress!! < map.end) {
                            val parentFunc = AppContainer.discoveredFunctions
                                .filter { it <= currentJumpAddress!! && it >= map.start }
                                .maxOrNull()
                            parentFunc ?: currentJumpAddress!!
                        } else {
                            AppContainer.getDisassemblyStartForMap(map)
                        }
                        
                        val len = minOf(262144L, map.end - startAddr).toInt() // Load 256KB
                        val rawBytes = try {
                            if (map.localData != null) {
                                val offset = (startAddr - map.start).toInt()
                                map.localData.copyOfRange(offset, offset + len)
                            } else {
                                client.readMemory(activeProcess!!.pid, startAddr, len)
                            }
                        } catch (_: Exception) {
                            ByteArray(0)
                        }

                        val rawInstrs = try {
                            val syncAddrs = (AppContainer.discoveredFunctions.toSet() + AppContainer.symbolNames.keys.toSet() + AppContainer.discoveredJumpTargets.toSet())
                            if (map.localData != null) {
                                com.osr.ps5debugger.util.LocalDisassembler.disassemble(rawBytes, startAddr, syncAddrs)
                            } else {
                                client.disassembleRegion(activeProcess!!.pid, startAddr, len, 4000)
                            }
                        } catch (e: Exception) {
                            emptyList()
                        }
                        
                        if (rawInstrs.isNotEmpty()) {
                            val lines = rawInstrs.map { instr ->
                                val offset = (instr.addr - startAddr).toInt()
                                val instrBytes = if (offset >= 0 && offset + instr.length <= rawBytes.size) {
                                    rawBytes.copyOfRange(offset, offset + instr.length)
                                } else {
                                    ByteArray(0)
                                }
                                val symbolName = AppContainer.symbolNames[instr.addr]
                                val lineRegion = map.subRanges.firstOrNull { instr.addr >= it.start && instr.addr < it.end } ?: map
                                DisasmLine(instr, instrBytes, lineRegion, symbolName)
                            }
                            allLines.addAll(lines)
                        }
                    }
                }
                
                val lines = allLines.distinctBy { it.instr.addr }.sortedBy { it.instr.addr }
                val extractedFunctions = mutableListOf<Long>()
                if (lines.isNotEmpty()) {
                    extractedFunctions.add(lines.first().instr.addr)
                    for (i in 0 until lines.size - 1) {
                        if (lines[i].instr.isRet) extractedFunctions.add(lines[i+1].instr.addr)
                    }
                }
                Pair(lines, extractedFunctions.distinct().sorted())
            }

            withContext(Dispatchers.Main) {
                instructions.clear()
                instructions.addAll(finalLines)
                functions.clear()
                functions.addAll(finalFunctions)
                val mergedFuncs = (AppContainer.discoveredFunctions + finalFunctions).distinct().sortedBy { it.toULong() }
                AppContainer.discoveredFunctions.clear()
                AppContainer.discoveredFunctions.addAll(mergedFuncs)
            }
            updateMetadata()
            scope.launch { fetchXrefs(finalLines) }
        } catch (e: Exception) {
            // ignore
        } finally {
            withContext(Dispatchers.Main) {
                isLoading = false
            }
        }
    }

    private suspend fun fetchXrefs(lines: List<DisasmLine>) = withContext(Dispatchers.Default) {
        val activeProcess = AppContainer.debuggerUseCase.activeProcess.value ?: return@withContext
        val isConnected = AppContainer.debuggerUseCase.isConnected.value
        if (!isConnected) return@withContext
        
        val pid = activeProcess.pid
        val client = AppContainer.clientAdapter.client
        val scanMap = activeMap ?: activeMaps.firstOrNull() ?: return@withContext
        
        val scanStart = scanMap.start
        val scanLen = (scanMap.end - scanMap.start).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        
        // Only fetch for function starts or significant labels to save time
        val targets = lines.filter { AppContainer.symbolNames.containsKey(it.instr.addr) || AppContainer.discoveredFunctions.contains(it.instr.addr) }
            .map { it.instr.addr }
            .distinct()
            .take(50) // Limit per batch

        for (target in targets) {
            try {
                val refs = client.findXrefs(pid, scanStart, scanLen, target)
                if (refs.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        val idx = instructions.indexOfFirst { it.instr.addr == target }
                        if (idx != -1) {
                            val old = instructions[idx]
                            instructions[idx] = old.copy(xrefs = refs)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }
}

@Composable
fun rememberMemoryViewerState(
    activeMap: MemoryRange?,
    activeMaps: List<MemoryRange> = emptyList(),
    jumpToAddress: Long?,
    viewModeParam: Int?,
    onViewModeChanged: ((Int) -> Unit)?,
    selectionStartParam: Long?,
    selectionEndParam: Long?,
    onSelectionChanged: ((Long?, Long?) -> Unit)?
): MemoryViewerState {
    val activeMapsSnapshot = activeMaps.toList()
    val scope = rememberCoroutineScope()

    val state = remember(activeMap, activeMapsSnapshot) {
        MemoryViewerState(scope, activeMap, activeMaps, jumpToAddress, viewModeParam, onViewModeChanged, selectionStartParam, selectionEndParam, onSelectionChanged)
    }

    // Sync mutable state only when upstream inputs change, not on every recomposition.
    LaunchedEffect(activeMap, activeMapsSnapshot, jumpToAddress, viewModeParam, selectionStartParam, selectionEndParam) {
        state.activeMap = activeMap
        state.activeMaps.clear()
        state.activeMaps.addAll(activeMapsSnapshot)
        state.jumpToAddress = jumpToAddress
        state.viewModeParam = viewModeParam
        state.onViewModeChanged = onViewModeChanged
        state.selectionStartParam = selectionStartParam
        state.selectionEndParam = selectionEndParam
        state.onSelectionChanged = onSelectionChanged
    }

    LaunchedEffect(jumpToAddress) {
        if (jumpToAddress != null) {
            state.currentJumpAddress = jumpToAddress
        }
    }

    val isConnected by AppContainer.debuggerUseCase.isConnected.collectAsState()
    val activeProcess by AppContainer.debuggerUseCase.activeProcess.collectAsState()
    val selectedMapsSnapshot = state.activeMaps.toList()
    LaunchedEffect(state.activeMap, selectedMapsSnapshot, state.currentJumpAddress, isConnected, activeProcess) {
        val allMaps = (listOfNotNull(state.activeMap) + selectedMapsSnapshot).distinctBy { it.start }
        if (allMaps.isEmpty()) return@LaunchedEffect

        val hasLocal = allMaps.any { it.localData != null }
        val hasRemote = allMaps.any { it.localData == null }

        if (hasLocal || (hasRemote && isConnected && activeProcess != null)) {
            state.loadInitialInstructions()
        }
    }
    
    return state
}

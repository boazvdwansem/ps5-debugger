package com.osr.ps5debugger.ui.state

import androidx.compose.runtime.*
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.ui.DisasmLine
import com.osr.ps5debugger.protocol.Ps5DisasmInstr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

import androidx.compose.ui.graphics.Color
import com.osr.ps5debugger.ui.disasm.DisasmFormatter

class MemoryViewerState(
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
    val instructions = mutableStateListOf<DisasmLine>()
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

    suspend fun loadInitialInstructions() {
        val activeProcess = AppContainer.debuggerUseCase.activeProcess.value ?: return
        val pid = activeProcess.pid
        val isConnected = AppContainer.debuggerUseCase.isConnected.value
        if (!isConnected) return

        // Use a snapshot of current maps to avoid concurrent modification issues
        val currentTargets = if (activeMaps.isNotEmpty()) activeMaps.toList() else listOfNotNull(activeMap)
        
        if (currentTargets.isEmpty()) {
            instructions.clear()
            functions.clear()
            AppContainer.discoveredFunctions.clear()
            AppContainer.discoveredJumpTargets.clear()
            return
        }

        isLoading = true
        
        try {
            val (finalLines, finalFunctions) = withContext(Dispatchers.Default) {
                val client = AppContainer.clientAdapter.client
                val allLines = mutableListOf<DisasmLine>()
                
                // Limit the number of regions we load at once
                val limitedTargets = if (currentTargets.size > 20) {
                    val jumpAddr = currentJumpAddress
                    if (jumpAddr != null) {
                        val prioritized = currentTargets.filter { jumpAddr >= it.start && jumpAddr < it.end }
                        (prioritized + currentTargets.filter { it !in prioritized }).take(20)
                    } else {
                        currentTargets.take(20)
                    }
                } else {
                    currentTargets
                }

                for (map in limitedTargets) {
                    val startAddr = if (currentJumpAddress != null && currentJumpAddress!! >= map.start && currentJumpAddress!! < map.end) {
                        val parentFunc = AppContainer.discoveredFunctions
                            .filter { it <= currentJumpAddress!! && it >= map.start }
                            .maxOrNull()
                        parentFunc ?: currentJumpAddress!!
                    } else {
                        map.start
                    }
                    
                    val len = minOf(65536L, map.end - startAddr).toInt()
                    if (len > 0) {
                        val rawInstrs = try {
                            client.disassembleRegion(pid, startAddr, len, 2000)
                        } catch (e: Exception) {
                            emptyList()
                        }
                        
                        if (rawInstrs.isNotEmpty()) {
                            val rawBytes = try {
                                client.readMemory(pid, startAddr, len)
                            } catch (_: Exception) {
                                ByteArray(0)
                            }
                            
                            val lines = rawInstrs.map { instr ->
                                val offset = (instr.addr - startAddr).toInt()
                                val instrBytes = if (offset >= 0 && offset + instr.length <= rawBytes.size) {
                                    rawBytes.copyOfRange(offset, offset + instr.length)
                                } else {
                                    ByteArray(0)
                                }
                                DisasmLine(instr, instrBytes, map)
                            }
                            allLines.addAll(lines)

                            // Call Target Discovery
                            val loadedAddresses = allLines.map { it.instr.addr }.toSet()
                            val callTargets = rawInstrs
                                .filter { it.isCall && it.ripRelTarget != 0L && !loadedAddresses.contains(it.ripRelTarget) }
                                .map { it.ripRelTarget }
                                .distinct()
                                .take(5)

                            for (target in callTargets) {
                                try {
                                    val subInstrs = client.disassembleRegion(pid, target, 4096, 200)
                                    if (subInstrs.isNotEmpty()) {
                                        val subBytes = try { client.readMemory(pid, target, 1024) } catch(_: Exception) { ByteArray(0) }
                                        val targetMap = currentTargets.firstOrNull { target >= it.start && target < it.end }
                                        val subLines = subInstrs.map { instr ->
                                            val offset = (instr.addr - target).toInt()
                                            val instrBytes = if (offset >= 0 && offset + instr.length <= subBytes.size) {
                                                subBytes.copyOfRange(offset, offset + instr.length)
                                            } else ByteArray(0)
                                            DisasmLine(instr, instrBytes, targetMap)
                                        }
                                        allLines.addAll(subLines)
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
                
                val lines = allLines.distinctBy { it.instr.addr }.sortedBy { it.instr.addr }
                
                val extractedFunctions = mutableListOf<Long>()
                if (lines.isNotEmpty()) {
                    val firstAddr = lines.first().instr.addr
                    val isMapStart = limitedTargets.any { firstAddr == it.start }
                    val isKnownFunc = AppContainer.discoveredFunctions.contains(firstAddr)
                    val isKnownLoc = AppContainer.discoveredJumpTargets.contains(firstAddr)
                    
                    if (isMapStart || isKnownFunc || !isKnownLoc) {
                        extractedFunctions.add(firstAddr)
                    }
                    
                    for (i in 0 until lines.size - 1) {
                        if (lines[i].instr.isRet) {
                            extractedFunctions.add(lines[i+1].instr.addr)
                        }
                    }
                }
                val finalFuncs = extractedFunctions.distinct().sorted()
                Pair(lines, finalFuncs)
            }

            instructions.clear()
            instructions.addAll(finalLines)
            functions.clear()
            functions.addAll(finalFunctions)
            val mergedFuncs = (AppContainer.discoveredFunctions + finalFunctions).distinct().sortedBy { it.toULong() }
            AppContainer.discoveredFunctions.clear()
            AppContainer.discoveredFunctions.addAll(mergedFuncs)
            updateMetadata()
        } catch (e: Exception) {
            AppContainer.debuggerUseCase.log("DISASM", "Initial load failed: ${e.message}", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
        } finally {
            isLoading = false
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
    val state = remember(activeMap, activeMaps.toList()) {
        MemoryViewerState(activeMap, activeMaps, jumpToAddress, viewModeParam, onViewModeChanged, selectionStartParam, selectionEndParam, onSelectionChanged)
    }
    
    SideEffect {
        state.activeMap = activeMap
        state.activeMaps.clear()
        state.activeMaps.addAll(activeMaps)
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
    LaunchedEffect(state.activeMap, state.activeMaps.size, state.currentJumpAddress, isConnected, activeProcess) {
        if ((state.activeMap != null || state.activeMaps.isNotEmpty()) && isConnected && activeProcess != null) {
            state.loadInitialInstructions()
        }
    }
    
    return state
}

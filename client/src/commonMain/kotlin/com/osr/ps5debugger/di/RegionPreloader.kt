package com.osr.ps5debugger.di

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.osr.ps5debugger.domain.service.SymbolManager
import com.osr.ps5debugger.infrastructure.adapter.SocketDebuggerAdapter

object RegionPreloader {

    private fun isLikelyPrintableAscii(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val printable = bytes.count { b ->
            val v = b.toInt() and 0xFF
            v == 0x09 || v == 0x0A || v == 0x0D || (v in 0x20..0x7E)
        }
        return printable.toDouble() / bytes.size.toDouble() > 0.75
    }

    private fun isLikelyCodePrefix(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        if (isLikelyPrintableAscii(bytes)) return false
        val first = bytes[0].toInt() and 0xFF
        val second = if (bytes.size > 1) bytes[1].toInt() and 0xFF else 0
        val commonX86Starts = setOf(
            0x00, 0x0F, 0x18, 0x20, 0x29, 0x2E, 0x31, 0x33, 0x39, 0x3B, 0x40, 0x41, 0x48,
            0x49, 0x4C, 0x4D, 0x50, 0x51, 0x52, 0x53, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5A,
            0x5B, 0x5C, 0x5D, 0x5E, 0x5F, 0x60, 0x61, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68,
            0x69, 0x6A, 0x6C, 0x6E, 0x70, 0x71, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7C,
            0x7D, 0x80, 0x81, 0x83, 0x85, 0x88, 0x89, 0x8B, 0x8D, 0x8E, 0x90, 0x91, 0xA1,
            0xA3, 0xB8, 0xB9, 0xBA, 0xBB, 0xBE, 0xBF, 0xC3, 0xC5, 0xC7, 0xE8, 0xEB, 0xF3,
            0xFF
        )
        if (first in commonX86Starts || second in commonX86Starts) return true
        return (first and 0xF0) in setOf(0x40, 0x50, 0x60, 0x70, 0x80, 0x90)
    }

    private suspend fun findSaneCodeStart(map: com.osr.ps5debugger.domain.model.MemoryRange): Long? {
        if (map.size <= 0) return null
        val probeLen = minOf(64 * 1024L, map.size).toInt()
        val probe = try {
            AppContainer.debuggerUseCase.readMemory(map.start, probeLen).getOrNull() ?: return null
        } catch (_: Exception) {
            return null
        }
        if (probe.isEmpty()) return null

        for (offset in 0 until probe.size step 1) {
            if (offset + 8 > probe.size) break
            val chunk = probe.copyOfRange(offset, minOf(offset + 8, probe.size))
            if (chunk.size < 4) continue
            if (isLikelyCodePrefix(chunk)) return map.start + offset.toLong()
        }
        return null
    }

    suspend fun getDisassemblyStartForMap(map: com.osr.ps5debugger.domain.model.MemoryRange): Long {
        val explicitCandidates = SymbolManager.discoveredFunctions.filter { it >= map.start && it < map.end }.sorted()
        if (explicitCandidates.isNotEmpty()) return explicitCandidates.first()
        val entry = SymbolManager.elfEntryPoint
        if (entry != null && entry >= map.start && entry < map.end) return entry
        val saneCode = findSaneCodeStart(map)
        if (saneCode != null && saneCode >= map.start && saneCode < map.end) return saneCode
        return map.start
    }

    suspend fun preloadRegionInBackground(map: com.osr.ps5debugger.domain.model.MemoryRange, pid: Int?, jumpToAddress: Long? = null) {
        if (map.subRanges.isNotEmpty()) {
            val execSubRanges = map.subRanges.filter { (it.protections and 4) != 0 || it.localData != null }
            val targets = if (jumpToAddress != null) {
                val matching = execSubRanges.find { jumpToAddress >= it.start && jumpToAddress < it.end }
                if (matching != null) listOf(matching) else execSubRanges
            } else {
                execSubRanges
            }
            for (sub in targets) {
                preloadRegionInBackground(sub, pid, jumpToAddress)
            }
            return
        }

        val chunkSize = 64 * 1024L
        val focusAddr = (jumpToAddress ?: map.start).coerceIn(map.start, maxOf(map.start, map.end - 1))
        val focusChunkIdx = ((focusAddr - map.start) / chunkSize).toInt()
        val key = "${map.start}_${map.end}_${map.name}"
        val disasmKey = "${map.start}_${map.end}_${map.name}_$focusChunkIdx"
        val hexKey = key

        // 1. Preload Hex Page 0 if not already in cache
        val pageSize = 65536
        val pageStart = (map.start / pageSize) * pageSize
        val hexMap = HexCache.getHexCache(hexKey)
        if (!hexMap.containsKey(pageStart)) {
            try {
                val pageData = ByteArray(pageSize)
                val readLen = minOf(pageSize.toLong(), map.end - map.start).toInt()
                val data = if (map.localData != null) {
                    map.localData.copyOfRange(0, minOf(readLen, map.localData.size))
                } else if (pid != null) {
                    AppContainer.clientAdapter.client.readMemory(pid, pageStart, readLen)
                } else ByteArray(0)
                if (data.isNotEmpty()) {
                    System.arraycopy(data, 0, pageData, 0, data.size)
                    hexMap[pageStart] = pageData
                }
            } catch (_: Exception) {}
        }

        // 2. Preload Disassembly Window if not already in cache
        val mainList = DisassemblyCache.getInstructions(key)
        val disasmList = DisassemblyCache.getInstructions(disasmKey)
        if (mainList.isNotEmpty() || disasmList.isNotEmpty()) return

        // Non-executable remote regions don't need disassembly
        if ((map.protections and 4) == 0 && map.localData == null) {
            withContext(Dispatchers.Main) {
                DisassemblyCache.disassemblyProgressCache[key] = 1.0f
                DisassemblyCache.disassemblyProgressCache[disasmKey] = 1.0f
            }
            return
        }

        val totalRegionChunks = ((map.end - map.start + chunkSize - 1) / chunkSize).toInt().coerceAtLeast(1)
        val isLargeRegion = totalRegionChunks > 32

        val targetRequests = if (isLargeRegion) {
            val windowRadius = 16 // 32 chunks = 2MB window
            val startIdx = maxOf(0, focusChunkIdx - windowRadius)
            val endIdx = minOf(totalRegionChunks, focusChunkIdx + windowRadius + 1)
            (startIdx until endIdx).map { idx ->
                val start = map.start + idx * chunkSize
                val len = minOf(chunkSize, map.end - start).toInt()
                start to len
            }
        } else {
            generateSequence(map.start) { start ->
                val next = start + chunkSize
                if (start < map.end) next else null
            }.takeWhile { it < map.end }.map { start ->
                start to minOf(chunkSize, map.end - start).toInt()
            }.toList()
        }

        val client = AppContainer.clientAdapter.client
        val chunkLines = mutableListOf<com.osr.ps5debugger.ui.memory.disasm.DisasmLine>()

        for ((chunkStart, len) in targetRequests) {
            try {
                val rawBytes = if (map.localData != null) {
                    val offset = (chunkStart - map.start).toInt()
                    map.localData.copyOfRange(offset, minOf(offset + len, map.localData.size))
                } else if (pid != null) {
                    client.readMemory(pid, chunkStart, len)
                } else ByteArray(0)

                if (rawBytes.isNotEmpty()) {
                    val syncAddrs = (SymbolManager.discoveredFunctions.toSet() + SymbolManager.symbolNames.keys.toSet() + SymbolManager.discoveredJumpTargets.toSet())
                    val rawInstrs = if (map.localData != null) {
                        com.osr.ps5debugger.util.LocalDisassembler.disassemble(rawBytes, chunkStart, syncAddrs)
                    } else if (pid != null) {
                        client.disassembleRegion(pid, chunkStart, len, 4000)
                    } else emptyList()

                    val lines = if (map.localData != null) {
                        rawInstrs.map { instr ->
                            val offset = (instr.addr - chunkStart).toInt()
                            val instrBytes = if (offset >= 0 && offset + instr.length <= rawBytes.size) rawBytes.copyOfRange(offset, offset + instr.length) else ByteArray(0)
                            com.osr.ps5debugger.ui.memory.disasm.DisasmLine(instr, instrBytes, map, SymbolManager.symbolNames[instr.addr])
                        }
                    } else {
                        val stringRanges = mutableListOf<Pair<Int, Int>>()
                        var strStart = -1
                        for (i in rawBytes.indices) {
                            val v = rawBytes[i].toInt() and 0xFF
                            if (v in 0x20..0x7E || v == 0x09) {
                                if (strStart < 0) strStart = i
                            } else {
                                if (strStart >= 0) {
                                    val slen = i - strStart
                                    if (slen >= 8 || (v == 0 && slen >= 4)) {
                                        stringRanges.add(strStart to if (v == 0) slen + 1 else slen)
                                    }
                                    strStart = -1
                                }
                            }
                        }
                        if (strStart >= 0 && rawBytes.size - strStart >= 8) stringRanges.add(strStart to (rawBytes.size - strStart))

                        val zeroRanges = mutableListOf<Pair<Int, Int>>()
                        var zStart = -1
                        for (i in 0..rawBytes.size) {
                            val isZero = i < rawBytes.size && rawBytes[i].toInt() == 0
                            if (isZero && zStart < 0) zStart = i
                            if (!isZero && zStart >= 0) {
                                val zlen = i - zStart
                                if (zlen >= 16) zeroRanges.add(zStart to zlen)
                                zStart = -1
                            }
                        }

                        val stringInstrs = stringRanges.map { (off, slen) ->
                            com.osr.ps5debugger.infrastructure.protocol.Ps5DisasmInstr(
                                addr = chunkStart + off,
                                ripRelTarget = 0,
                                memDisp = 0,
                                length = slen,
                                kind = 0x100,
                                memBaseReg = 0,
                                memIndexReg = 0,
                                memScale = 0,
                                mnemonic = 0,
                                mnemonicLo = 0
                            )
                        }

                        val zeroInstrs = mutableListOf<com.osr.ps5debugger.infrastructure.protocol.Ps5DisasmInstr>()
                        for ((off, zlen) in zeroRanges) {
                            var curOff = off
                            val endOff = off + zlen
                            val maxEmit = 64
                            var emitted = 0
                            while (curOff < endOff) {
                                val addr = chunkStart + curOff
                                val hasSync = syncAddrs.contains(addr)
                                if (emitted >= maxEmit && !hasSync) {
                                    val nextSync = syncAddrs.filter { it > addr && it < chunkStart + endOff }.minOrNull()
                                    if (nextSync != null) {
                                        curOff = (nextSync - chunkStart).toInt()
                                        emitted = 0
                                        continue
                                    } else break
                                }
                                val ilen = minOf(16, endOff - curOff)
                                zeroInstrs.add(
                                    com.osr.ps5debugger.infrastructure.protocol.Ps5DisasmInstr(
                                        addr = addr,
                                        ripRelTarget = 0,
                                        memDisp = 0,
                                        length = ilen,
                                        kind = 0x200,
                                        memBaseReg = 0,
                                        memIndexReg = 0,
                                        memScale = 0,
                                        mnemonic = 0,
                                        mnemonicLo = 0
                                    )
                                )
                                curOff += ilen
                                emitted += ilen
                            }
                        }

                        val filtered = if (stringRanges.isEmpty() && zeroRanges.isEmpty()) rawInstrs else {
                            rawInstrs.filterNot { instr ->
                                val iStart = (instr.addr - chunkStart).toInt()
                                val iEnd = iStart + instr.length
                                if (iStart < 0 || iEnd > rawBytes.size) return@filterNot false

                                val isInsideString = stringRanges.any { (sOff, sLen) ->
                                    iStart >= sOff && iEnd <= (sOff + sLen)
                                }
                                if (isInsideString) return@filterNot true

                                val isZeroInstr = (iStart until iEnd).all { rawBytes[it].toInt() == 0 }
                                if (isZeroInstr) {
                                    zeroRanges.any { (zOff, zLen) ->
                                        iStart < (zOff + zLen) && iEnd > zOff
                                    }
                                } else {
                                    false
                                }
                            }
                        }

                        (filtered + stringInstrs + zeroInstrs).distinctBy { it.addr }.sortedBy { it.addr }.map { instr ->
                            val offset = (instr.addr - chunkStart).toInt()
                            val instrBytes = if (offset >= 0 && offset + instr.length <= rawBytes.size) rawBytes.copyOfRange(offset, offset + instr.length) else ByteArray(0)
                            com.osr.ps5debugger.ui.memory.disasm.DisasmLine(instr, instrBytes, map, SymbolManager.symbolNames[instr.addr])
                        }
                    }
                    chunkLines.addAll(lines)
                }
            } catch (_: Exception) {}
        }

        chunkLines.sortBy { it.instr.addr }
        val finalLines = ArrayList<com.osr.ps5debugger.ui.memory.disasm.DisasmLine>(chunkLines.size)
        var prevAddr: Long? = null
        for (line in chunkLines) {
            if (line.instr.addr != prevAddr) {
                finalLines.add(line)
                prevAddr = line.instr.addr
            }
        }

        val extractedFunctions = mutableSetOf<Long>()
        if (finalLines.isNotEmpty()) {
            extractedFunctions.add(finalLines.first().instr.addr)
            for (i in finalLines.indices) {
                val line = finalLines[i]
                if (line.instr.isRet && i + 1 < finalLines.size) extractedFunctions.add(finalLines[i + 1].instr.addr)
                val target = com.osr.ps5debugger.ui.memory.disasm.DisasmFormatter.getJumpTarget(line.instr, line.bytes)
                if (line.instr.isCall && target != 0L) extractedFunctions.add(target)
            }
        }

        withContext(Dispatchers.Main) {
            mainList.clear()
            mainList.addAll(finalLines)
            disasmList.clear()
            disasmList.addAll(finalLines)
            DisassemblyCache.disassemblyProgressCache[key] = 1.0f
            DisassemblyCache.disassemblyProgressCache[disasmKey] = 1.0f
            if (extractedFunctions.isNotEmpty()) {
                val merged = (SymbolManager.discoveredFunctions + extractedFunctions).distinct().sortedBy { it.toULong() }
                SymbolManager.discoveredFunctions.clear()
                SymbolManager.discoveredFunctions.addAll(merged)
            }
        }
    }
}




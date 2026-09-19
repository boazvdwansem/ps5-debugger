package com.osr.ps5debugger.util

import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.ui.DisasmLine
import com.osr.ps5debugger.ui.disasm.DisasmFormatter
import java.io.File
import java.io.BufferedReader
import java.io.FileReader

object OrbisSymbolResolver {
    val syscalls = mutableMapOf<Int, String>()
    val nidDb = mutableMapOf<String, String>()
    var isLoaded = false

    fun loadDatabase(): Boolean {
        if (isLoaded) return true
        try {
            val paths = listOf(
                "C:/Users/boazv/Documents/Personal/ps5-debugger/GhidraOrbis/data/",
                "../GhidraOrbis/data/",
                "./GhidraOrbis/data/"
            )
            
            var syscallFile: File? = null
            var nidFile: File? = null
            
            for (p in paths) {
                val f1 = File(p, "orbis_syscall_numbers")
                val f2 = File(p, "nid_db.xml")
                if (f1.exists() && f2.exists()) {
                    syscallFile = f1
                    nidFile = f2
                    break
                }
            }
            
            if (syscallFile == null) {
                return false
            }

            // Parse Syscalls
            BufferedReader(FileReader(syscallFile)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line!!.trim()
                    if (l.isEmpty() || l.startsWith("#")) continue
                    val parts = l.split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        val num = parts[0].toIntOrNull()
                        if (num != null) {
                            syscalls[num] = parts[1]
                        }
                    }
                }
            }

            // Fast Line-by-Line Regex Parse for huge XML (3.8MB)
            val obfRegex = "obf=\"([^\"]+)\"".toRegex()
            val symRegex = "sym=\"([^\"]+)\"".toRegex()
            
            BufferedReader(FileReader(nidFile!!)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line!!
                    if (!l.contains("<Entry")) continue
                    val obfMatch = obfRegex.find(l)
                    val symMatch = symRegex.find(l)
                    if (obfMatch != null && symMatch != null) {
                        nidDb[obfMatch.groupValues[1]] = symMatch.groupValues[1]
                    }
                }
            }
            
            isLoaded = nidDb.isNotEmpty()
            return isLoaded
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun autoResolve(lines: List<DisasmLine>, startIndex: Int = 0) {
        if (!loadDatabase()) return

        // 1. Scan for Syscalls using the instruction stream pattern
        // Only scan starting from startIndex to avoid O(N^2) complexity on growth
        for (i in startIndex until lines.size) {
            val line = lines[i]
            val mnemonic = DisasmFormatter.getMnemonic(line.instr, line.bytes)
            if (mnemonic == "SYSCALL") {
                // Look back up to 4 instructions to find the MOV EAX, imm32
                for (j in 1..4) {
                    if (i - j < 0) break
                    val prevLine = lines[i - j]
                    val prevMnemonic = DisasmFormatter.getMnemonic(prevLine.instr, prevLine.bytes)
                    if (prevMnemonic == "MOV" && prevLine.bytes.size >= 5 && prevLine.bytes[0] == 0xB8.toByte()) {
                        val b = prevLine.bytes
                        val sysNum = ((b[1].toInt() and 0xFF) or 
                                      ((b[2].toInt() and 0xFF) shl 8) or 
                                      ((b[3].toInt() and 0xFF) shl 16) or 
                                      ((b[4].toInt() and 0xFF) shl 24))
                        val name = syscalls[sysNum]
                        if (name != null) {
                            val targetAddr = prevLine.instr.addr
                            AppContainer.symbolNames[targetAddr] = name
                            if (!AppContainer.discoveredFunctions.contains(targetAddr)) {
                                AppContainer.discoveredFunctions.add(targetAddr)
                            }
                            break
                        }
                    }
                }
            }
        }

        // 2. Resolve any symbols whose name contains or matches a NID
        // We still check everything here as symbolNames is a global map and might have been updated
        val currentSymbols = AppContainer.symbolNames.toMap()
        for ((addr, name) in currentSymbols) {
            if (name.contains("#")) {
                val resolvedName = nidDb[name.split("#")[0]]
                if (resolvedName != null) AppContainer.symbolNames[addr] = resolvedName
            } else if (nidDb.containsKey(name)) {
                val resolvedName = nidDb[name]
                if (resolvedName != null) AppContainer.symbolNames[addr] = resolvedName
            }
        }
    }
}

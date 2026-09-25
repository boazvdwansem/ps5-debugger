package com.osr.ps5debugger.mcp

import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.domain.model.Process
import com.osr.ps5debugger.network.Ps5Client
import com.osr.ps5debugger.protocol.BinaryBuffer
import com.osr.ps5debugger.protocol.GpRegs
import com.osr.ps5debugger.protocol.Ps5DisasmInstr
import com.osr.ps5debugger.protocol.Ps5Process
import com.osr.ps5debugger.ui.DisasmLine
import com.osr.ps5debugger.ui.buildCfg
import com.osr.ps5debugger.ui.disasm.DisasmFormatter
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

/**
 * Embedded HTTP server running on localhost inside the PS5 Debugger Desktop application.
 * Exposes a clean JSON REST API that the Python MCP server bridges to AI models like Antigravity.
 */
object McpHttpServer {
    private var server: HttpServer? = null
    private var executor: java.util.concurrent.ExecutorService? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clientMutex = Mutex()
    const val DEFAULT_PORT = 8585
    var activePort: Int = DEFAULT_PORT
        private set

    val isRunning: Boolean
        get() = server != null

    fun start(port: Int = DEFAULT_PORT) {
        if (server != null) return
        try {
            activePort = port
            val s = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
            val pool = Executors.newFixedThreadPool(4) { r ->
                Thread(r, "mcp-http-server-worker").apply { isDaemon = true }
            }
            executor = pool
            s.executor = pool

            s.createContext("/api/status") { exchange -> handleStatus(exchange) }
            s.createContext("/api/processes") { exchange -> handleProcesses(exchange) }
            s.createContext("/api/process/attach") { exchange -> handleAttachProcess(exchange) }
            s.createContext("/api/maps") { exchange -> handleMaps(exchange) }
            s.createContext("/api/memory/read") { exchange -> handleReadMemory(exchange) }
            s.createContext("/api/memory/write") { exchange -> handleWriteMemory(exchange) }
            s.createContext("/api/disassembly") { exchange -> handleDisassembly(exchange) }
            s.createContext("/api/function") { exchange -> handleFunction(exchange) }
            s.createContext("/api/functions") { exchange -> handleFunctions(exchange) }
            s.createContext("/api/symbols") { exchange -> handleSymbols(exchange) }
            s.createContext("/api/cfg") { exchange -> handleCfg(exchange) }
            s.createContext("/api/xrefs") { exchange -> handleXrefs(exchange) }
            s.createContext("/api/registers") { exchange -> handleRegisters(exchange) }
            s.createContext("/api/stack") { exchange -> handleStack(exchange) }
            s.createContext("/api/navigate") { exchange -> handleNavigate(exchange) }

            s.start()
            server = s
            println("[McpHttpServer] Started PS5 Debugger MCP HTTP server on http://127.0.0.1:$port")
        } catch (e: Exception) {
            System.err.println("[McpHttpServer] Failed to start HTTP server: ${e.message}")
        }
    }

    fun stop() {
        try {
            server?.stop(0)
            server = null
            executor?.shutdownNow()
            executor = null
            println("[McpHttpServer] Stopped PS5 Debugger MCP HTTP server")
        } catch (e: Exception) {
            System.err.println("[McpHttpServer] Error stopping server: ${e.message}")
        }
    }

    // =========================================================================
    // Handlers
    // =========================================================================

    private fun handleStatus(exchange: HttpExchange) {
        if (handleCors(exchange)) return
        val isConnected = AppContainer.debuggerUseCase.isConnected.value
        val activeProc = AppContainer.debuggerUseCase.activeProcess.value
        val activeProcInfo = AppContainer.debuggerUseCase.activeProcessInfo.value
        val targetIp = (AppContainer.clientAdapter.client as? Ps5Client)?.connection?.ipAddress ?: ""
        val entryPoint = AppContainer.elfEntryPoint

        val activeProcJson = if (activeProc != null) {
            """{"pid":${activeProc.pid},"name":"${escape(activeProc.name)}"}"""
        } else "null"

        val activeProcInfoJson = if (activeProcInfo != null) {
            """{"pid":${activeProcInfo.pid},"name":"${escape(activeProcInfo.name)}","titleId":"${escape(activeProcInfo.titleId)}","contentId":"${escape(activeProcInfo.contentId)}"}"""
        } else "null"

        val json = """
        {
            "status": "ok",
            "server": "ps5-debugger-desktop",
            "port": $activePort,
            "connected": $isConnected,
            "targetIp": "${escape(targetIp)}",
            "activeProcess": $activeProcJson,
            "activeProcessInfo": $activeProcInfoJson,
            "elfEntryPoint": ${if (entryPoint != null) "\"0x${entryPoint.toString(16).uppercase()}\"" else "null"},
            "discoveredFunctionsCount": ${AppContainer.discoveredFunctions.size},
            "discoveredSymbolsCount": ${AppContainer.symbolNames.size}
        }
        """.trimIndent()

        sendJsonResponse(exchange, 200, json)
    }

    private fun handleProcesses(exchange: HttpExchange) {
        if (handleCors(exchange)) return
        scope.launch {
            try {
                val client = AppContainer.clientAdapter.client
                val isConnected = AppContainer.debuggerUseCase.isConnected.value
                if (!isConnected) {
                    sendJsonResponse(exchange, 200, """{"success":false,"error":"Not connected to PS5","processes":[]}""")
                    return@launch
                }

                val procs = clientMutex.withLock {
                    client.getProcesses()
                }

                val procsJson = procs.joinToString(",") {
                    """{"pid":${it.pid},"name":"${escape(it.name)}"}"""
                }

                sendJsonResponse(exchange, 200, """{"success":true,"count":${procs.size},"processes":[$procsJson]}""")
            } catch (e: Exception) {
                sendJsonResponse(exchange, 500, """{"success":false,"error":"${escape(e.message ?: "Unknown error")}"}""")
            }
        }
    }

    private fun handleAttachProcess(exchange: HttpExchange) {
        if (handleCors(exchange)) return
        scope.launch {
            try {
                val body = readBody(exchange)
                val params = parseQueryParams(exchange.requestURI.query)
                val targetPid = parseJsonLong(body, "pid")?.toInt() ?: params["pid"]?.toIntOrNull()
                val targetName = parseJsonString(body, "name") ?: params["name"]

                if (targetPid == null && targetName == null) {
                    sendJsonResponse(exchange, 400, """{"success":false,"error":"Missing pid or name parameter"}""")
                    return@launch
                }

                val client = AppContainer.clientAdapter.client
                val procs = clientMutex.withLock { client.getProcesses() }
                val target = if (targetPid != null) {
                    procs.firstOrNull { it.pid == targetPid } ?: Ps5Process(targetName ?: "proc_$targetPid", targetPid)
                } else {
                    procs.firstOrNull { it.name.equals(targetName, ignoreCase = true) }
                }

                if (target == null) {
                    sendJsonResponse(exchange, 404, """{"success":false,"error":"Process not found"}""")
                    return@launch
                }

                val domainProc = Process(name = target.name, pid = target.pid)
                AppContainer.debuggerUseCase.selectProcess(domainProc)
                AppContainer.debuggerUseCase.loadMemoryMaps(domainProc)
                val attached = clientMutex.withLock { client.attach(target.pid) }
                if (attached) {
                    AppContainer.debuggerUseCase.setProcessStopped(false)
                    AppContainer.debuggerUseCase.setAttached(true)
                    val threads = try { clientMutex.withLock { client.getThreadList() } } catch (_: Exception) { emptyList() }
                    AppContainer.debuggerUseCase.setThreadList(threads)
                    if (threads.isNotEmpty()) {
                        AppContainer.debuggerUseCase.setSelectedLwpid(threads.first())
                    }
                }

                sendJsonResponse(exchange, 200, """{"success":true,"pid":${target.pid},"name":"${escape(target.name)}"}""")
            } catch (e: Exception) {
                sendJsonResponse(exchange, 500, """{"success":false,"error":"${escape(e.message ?: "Error attaching process")}"}""")
            }
        }
    }

    private fun handleMaps(exchange: HttpExchange) {
        if (handleCors(exchange)) return
        scope.launch {
            try {
                val params = parseQueryParams(exchange.requestURI.query)
                val pid = params["pid"]?.toIntOrNull() ?: AppContainer.debuggerUseCase.activeProcess.value?.pid
                val client = AppContainer.clientAdapter.client

                val maps = if (pid != null && AppContainer.debuggerUseCase.isConnected.value) {
                    clientMutex.withLock { client.getMaps(pid) }.map {
                        MemoryRange(
                            name = it.name,
                            start = it.start,
                            end = it.end,
                            offset = it.offset,
                            protections = it.prot
                        )
                    }
                } else {
                    AppContainer.debuggerUseCase.vmMaps.value
                }

                val mapsJson = maps.joinToString(",") { m ->
                    """{"name":"${escape(m.name)}","start":"0x${m.start.toString(16).uppercase()}","end":"0x${m.end.toString(16).uppercase()}","size":${m.size},"offset":"0x${m.offset.toString(16).uppercase()}","prot":${m.protections},"protString":"${m.getProtString()}"}"""
                }

                sendJsonResponse(exchange, 200, """{"success":true,"count":${maps.size},"maps":[$mapsJson]}""")
            } catch (e: Exception) {
                sendJsonResponse(exchange, 500, """{"success":false,"error":"${escape(e.message ?: "Error reading maps")}"}""")
            }
        }
    }

    private fun handleReadMemory(exchange: HttpExchange) {
        if (handleCors(exchange)) return
        scope.launch {
            try {
                val params = parseQueryParams(exchange.requestURI.query)
                val addrStr = params["address"] ?: params["addr"]
                val lenStr = params["length"] ?: params["len"] ?: params["size"]
                val pid = params["pid"]?.toIntOrNull() ?: AppContainer.debuggerUseCase.activeProcess.value?.pid

                val address = parseAddress(addrStr)
                if (address == null) {
                    sendJsonResponse(exchange, 400, """{"success":false,"error":"Invalid or missing address parameter (e.g. ?address=0x400000)"}""")
                    return@launch
                }

                val length = (lenStr?.toIntOrNull() ?: 64).coerceIn(1, 65536)
                val bytes = clientMutex.withLock {
                    if (pid != null && AppContainer.debuggerUseCase.isConnected.value) {
                        AppContainer.clientAdapter.client.readMemory(pid, address, length)
                    } else {
                        // Check local memory range if loaded from ELF
                        val localMap = AppContainer.debuggerUseCase.vmMaps.value.firstOrNull { address >= it.start && address < it.end }
                        val localData = localMap?.localData
                        if (localData != null) {
                            val off = (address - localMap.start).toInt()
                            val count = minOf(length, localData.size - off)
                            if (count > 0) localData.copyOfRange(off, off + count) else ByteArray(0)
                        } else ByteArray(0)
                    }
                }

                if (bytes.isEmpty()) {
                    sendJsonResponse(exchange, 200, """{"success":false,"error":"Memory read returned 0 bytes (unmapped or unreadable address)"}""")
                    return@launch
                }

                val hexString = bytes.joinToString("") { "%02X".format(it) }
                val asciiString = bytes.map { b ->
                    val c = b.toInt() and 0xFF
                    if (c in 32..126) c.toChar().toString() else "."
                }.joinToString("")

                val hexDump = formatHexDump(address, bytes)
                val dwords = bytesToDwordsHex(bytes)

                val json = """
                {
                    "success": true,
                    "address": "0x${address.toString(16).uppercase()}",
                    "length": ${bytes.size},
                    "hex": "$hexString",
                    "ascii": "${escape(asciiString)}",
                    "dwords": [${dwords.joinToString(",") { "\"$it\"" }}],
                    "hexDump": "${escape(hexDump)}"
                }
                """.trimIndent()

                sendJsonResponse(exchange, 200, json)
            } catch (e: Exception) {
                sendJsonResponse(exchange, 500, """{"success":false,"error":"${escape(e.message ?: "Error reading memory")}"}""")
            }
        }
    }

    private fun handleWriteMemory(exchange: HttpExchange) {
        if (handleCors(exchange)) return
        scope.launch {
            try {
                val body = readBody(exchange)
                val addr = parseJsonLong(body, "address")
                val hexData = parseJsonString(body, "hex")?.replace(" ", "") ?: ""
                val pid = parseJsonLong(body, "pid")?.toInt() ?: AppContainer.debuggerUseCase.activeProcess.value?.pid

                if (addr == null || hexData.isEmpty() || pid == null) {
                    sendJsonResponse(exchange, 400, """{"success":false,"error":"Missing address, hex, or pid in request body"}""")
                    return@launch
                }

                val bytes = parseHexBytes(hexData)
                val success = clientMutex.withLock {
                    AppContainer.clientAdapter.client.writeMemory(pid, addr, bytes)
                }

                sendJsonResponse(exchange, 200, """{"success":$success,"written":${bytes.size},"address":"0x${addr.toString(16).uppercase()}"}""")
            } catch (e: Exception) {
                sendJsonResponse(exchange, 500, """{"success":false,"error":"${escape(e.message ?: "Error writing memory")}"}""")
            }
        }
    }

    private fun handleDisassembly(exchange: HttpExchange) {
        if (handleCors(exchange)) return
        scope.launch {
            try {
                val params = parseQueryParams(exchange.requestURI.query)
                val addrStr = params["address"] ?: params["addr"]
                val lenStr = params["length"] ?: params["len"]
                val maxStr = params["max"] ?: params["max_instructions"]
                val pid = params["pid"]?.toIntOrNull() ?: AppContainer.debuggerUseCase.activeProcess.value?.pid

                val address = parseAddress(addrStr)
                if (address == null) {
                    sendJsonResponse(exchange, 400, """{"success":false,"error":"Invalid or missing address parameter"}""")
                    return@launch
                }

                val length = (lenStr?.toIntOrNull() ?: 128).coerceIn(1, 16384)
                val maxInstrs = (maxStr?.toIntOrNull() ?: 50).coerceIn(1, 500)

                val lines = disassembleRegionInternal(pid, address, length, maxInstrs)
                val linesJson = lines.joinToString(",") { formatDisasmLineJson(it) }

                sendJsonResponse(exchange, 200, """{"success":true,"count":${lines.size},"instructions":[$linesJson]}""")
            } catch (e: Exception) {
                sendJsonResponse(exchange, 500, """{"success":false,"error":"${escape(e.message ?: "Disassembly error")}"}""")
            }
        }
    }

    private fun handleFunction(exchange: HttpExchange) {
        if (handleCors(exchange)) return
        scope.launch {
            try {
                val params = parseQueryParams(exchange.requestURI.query)
                val addrStr = params["address"] ?: params["addr"]
                val nameStr = params["name"]
                val pid = params["pid"]?.toIntOrNull() ?: AppContainer.debuggerUseCase.activeProcess.value?.pid

                var entryAddr: Long? = parseAddress(addrStr)
                if (entryAddr == null && nameStr != null) {
                    entryAddr = AppContainer.symbolNames.entries.firstOrNull { it.value.equals(nameStr, ignoreCase = true) }?.key
                }

                if (entryAddr == null) {
                    sendJsonResponse(exchange, 400, """{"success":false,"error":"Missing valid function address or name"}""")
                    return@launch
                }

                // If address is inside a function, find the nearest function start
                val exactFunc = AppContainer.discoveredFunctions.firstOrNull { it == entryAddr }
                val start = exactFunc ?: AppContainer.discoveredFunctions.filter { it <= entryAddr!! }.maxOrNull() ?: entryAddr

                val funcName = AppContainer.getSymbolName(start, true)

                // Disassemble from function start until RET or next function
                val lines = disassembleRegionInternal(pid, start, 4096, 250)
                val subList = mutableListOf<DisasmLine>()
                for (line in lines) {
                    subList.add(line)
                    if (line.instr.isRet) break
                }

                val linesJson = (if (subList.isNotEmpty()) subList else lines).joinToString(",") { formatDisasmLineJson(it) }

                sendJsonResponse(exchange, 200, """
                {
                    "success": true,
                    "name": "${escape(funcName)}",
                    "entry": "0x${start.toString(16).uppercase()}",
                    "instructionCount": ${if (subList.isNotEmpty()) subList.size else lines.size},
                    "instructions": [$linesJson]
                }
                """.trimIndent())
            } catch (e: Exception) {
                sendJsonResponse(exchange, 500, """{"success":false,"error":"${escape(e.message ?: "Function disassembly error")}"}""")
            }
        }
    }

    private fun handleFunctions(exchange: HttpExchange) {
        if (handleCors(exchange)) return
        val funcs = AppContainer.discoveredFunctions.toList().sorted()
        val funcsJson = funcs.joinToString(",") { addr ->
            val name = AppContainer.getSymbolName(addr, true)
            """{"address":"0x${addr.toString(16).uppercase()}","name":"${escape(name)}"}"""
        }
        sendJsonResponse(exchange, 200, """{"success":true,"count":${funcs.size},"functions":[$funcsJson]}""")
    }

    private fun handleSymbols(exchange: HttpExchange) {
        if (handleCors(exchange)) return
        val symbols = AppContainer.symbolNames.toMap()
        val symbolsJson = symbols.entries.joinToString(",") { (addr, name) ->
            val isFunc = AppContainer.discoveredFunctions.contains(addr)
            """{"address":"0x${addr.toString(16).uppercase()}","name":"${escape(name)}","isFunction":$isFunc}"""
        }
        sendJsonResponse(exchange, 200, """{"success":true,"count":${symbols.size},"symbols":[$symbolsJson]}""")
    }

    private fun handleCfg(exchange: HttpExchange) {
        if (handleCors(exchange)) return
        scope.launch {
            try {
                val params = parseQueryParams(exchange.requestURI.query)
                val addrStr = params["address"] ?: params["addr"]
                val pid = params["pid"]?.toIntOrNull() ?: AppContainer.debuggerUseCase.activeProcess.value?.pid
                val address = parseAddress(addrStr)

                if (address == null) {
                    sendJsonResponse(exchange, 400, """{"success":false,"error":"Missing address parameter for CFG"}""")
                    return@launch
                }

                // Disassemble region around function
                val lines = disassembleRegionInternal(pid, address, 4096, 200)
                val (nodes, edges) = buildCfg(lines, address)

                val nodesJson = nodes.joinToString(",") { n ->
                    val instrsJson = n.instructions.joinToString(",") { formatDisasmLineJson(it) }
                    """{"id":${n.id},"start":"0x${n.startAddr.toString(16).uppercase()}","end":"0x${n.endAddr.toString(16).uppercase()}","isExternal":${n.isExternal},"instructions":[$instrsJson]}"""
                }

                val edgesJson = edges.joinToString(",") { e ->
                    """{"from":${e.from},"to":${e.to},"type":"${e.type.name}"}"""
                }

                sendJsonResponse(exchange, 200, """
                {
                    "success": true,
                    "entryAddress": "0x${address.toString(16).uppercase()}",
                    "nodeCount": ${nodes.size},
                    "edgeCount": ${edges.size},
                    "nodes": [$nodesJson],
                    "edges": [$edgesJson]
                }
                """.trimIndent())
            } catch (e: Exception) {
                sendJsonResponse(exchange, 500, """{"success":false,"error":"${escape(e.message ?: "CFG error")}"}""")
            }
        }
    }

    private fun handleXrefs(exchange: HttpExchange) {
        if (handleCors(exchange)) return
        scope.launch {
            try {
                val params = parseQueryParams(exchange.requestURI.query)
                val addrStr = params["address"] ?: params["addr"]
                val scanStartStr = params["scan_address"] ?: params["start"]
                val scanLenStr = params["scan_length"] ?: params["length"]
                val pid = params["pid"]?.toIntOrNull() ?: AppContainer.debuggerUseCase.activeProcess.value?.pid

                val targetAddr = parseAddress(addrStr)
                if (targetAddr == null || pid == null) {
                    sendJsonResponse(exchange, 400, """{"success":false,"error":"Missing address or pid"}""")
                    return@launch
                }

                val vmMaps = AppContainer.debuggerUseCase.vmMaps.value
                val map = vmMaps.firstOrNull { targetAddr >= it.start && targetAddr < it.end }
                val scanStart = parseAddress(scanStartStr) ?: map?.start ?: (targetAddr - 0x100000).coerceAtLeast(0)
                val scanLen = scanLenStr?.toIntOrNull() ?: ((map?.end ?: (scanStart + 0x200000)) - scanStart).coerceIn(0x1000, 0x1000000).toInt()

                val xrefs = clientMutex.withLock {
                    AppContainer.clientAdapter.client.findXrefs(pid, scanStart, scanLen, targetAddr)
                }

                val xrefsJson = xrefs.joinToString(",") { "\"0x${it.toString(16).uppercase()}\"" }
                sendJsonResponse(exchange, 200, """{"success":true,"targetAddress":"0x${targetAddr.toString(16).uppercase()}","count":${xrefs.size},"xrefs":[$xrefsJson]}""")
            } catch (e: Exception) {
                sendJsonResponse(exchange, 500, """{"success":false,"error":"${escape(e.message ?: "XRefs error")}"}""")
            }
        }
    }

    private fun handleRegisters(exchange: HttpExchange) {
        if (handleCors(exchange)) return
        scope.launch {
            try {
                val params = parseQueryParams(exchange.requestURI.query)
                val lwpid = params["lwpid"]?.toIntOrNull() ?: AppContainer.debuggerUseCase.selectedLwpid.value ?: 0

                val regs: GpRegs = clientMutex.withLock {
                    if (lwpid > 0) {
                        AppContainer.clientAdapter.client.getRegs(lwpid)
                    } else {
                        AppContainer.debuggerUseCase.selectedRegs.value ?: GpRegs(
                            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
                        )
                    }
                }

                val json = """
                {
                    "success": true,
                    "lwpid": $lwpid,
                    "rax": "0x${regs.rax.toULong().toString(16).uppercase()}",
                    "rbx": "0x${regs.rbx.toULong().toString(16).uppercase()}",
                    "rcx": "0x${regs.rcx.toULong().toString(16).uppercase()}",
                    "rdx": "0x${regs.rdx.toULong().toString(16).uppercase()}",
                    "rsi": "0x${regs.rsi.toULong().toString(16).uppercase()}",
                    "rdi": "0x${regs.rdi.toULong().toString(16).uppercase()}",
                    "rbp": "0x${regs.rbp.toULong().toString(16).uppercase()}",
                    "rsp": "0x${regs.rsp.toULong().toString(16).uppercase()}",
                    "r8":  "0x${regs.r8.toULong().toString(16).uppercase()}",
                    "r9":  "0x${regs.r9.toULong().toString(16).uppercase()}",
                    "r10": "0x${regs.r10.toULong().toString(16).uppercase()}",
                    "r11": "0x${regs.r11.toULong().toString(16).uppercase()}",
                    "r12": "0x${regs.r12.toULong().toString(16).uppercase()}",
                    "r13": "0x${regs.r13.toULong().toString(16).uppercase()}",
                    "r14": "0x${regs.r14.toULong().toString(16).uppercase()}",
                    "r15": "0x${regs.r15.toULong().toString(16).uppercase()}",
                    "rip": "0x${regs.rip.toULong().toString(16).uppercase()}",
                    "rflags": "0x${regs.rflags.toULong().toString(16).uppercase()}"
                }
                """.trimIndent()

                sendJsonResponse(exchange, 200, json)
            } catch (e: Exception) {
                sendJsonResponse(exchange, 500, """{"success":false,"error":"${escape(e.message ?: "Registers read error")}"}""")
            }
        }
    }

    private fun handleStack(exchange: HttpExchange) {
        if (handleCors(exchange)) return
        scope.launch {
            try {
                val params = parseQueryParams(exchange.requestURI.query)
                val pid = params["pid"]?.toIntOrNull() ?: AppContainer.debuggerUseCase.activeProcess.value?.pid
                val lwpid = params["lwpid"]?.toIntOrNull() ?: AppContainer.debuggerUseCase.selectedLwpid.value ?: 0
                val depth = (params["depth"]?.toIntOrNull() ?: 32).coerceIn(1, 64)

                if (pid == null) {
                    sendJsonResponse(exchange, 400, """{"success":false,"error":"No active process"}""")
                    return@launch
                }

                data class StackFrame(val rip: Long, val rbp: Long)
                val frames = mutableListOf<StackFrame>()

                val activeRegs: GpRegs? = try {
                    if (lwpid > 0) clientMutex.withLock { AppContainer.clientAdapter.client.getRegs(lwpid) } else null
                } catch (_: Exception) {
                    AppContainer.debuggerUseCase.selectedRegs.value
                } ?: AppContainer.debuggerUseCase.selectedRegs.value

                if (activeRegs != null) {
                    frames.add(StackFrame(activeRegs.rip, activeRegs.rbp))
                    var currRbp = activeRegs.rbp
                    var count = 1
                    while (count < depth && currRbp != 0L && (currRbp and 0x7L) == 0L && currRbp > 0x10000L) {
                        try {
                            val mem = clientMutex.withLock {
                                AppContainer.clientAdapter.client.readMemory(pid, currRbp, 16)
                            }
                            if (mem.size < 16) break
                            val buf = BinaryBuffer(mem)
                            val nextRbp = buf.readLong()
                            val retRip = buf.readLong()
                            if (retRip == 0L) break
                            frames.add(StackFrame(retRip, nextRbp))
                            if (nextRbp <= currRbp || (nextRbp - currRbp) > 0x200000L) break
                            currRbp = nextRbp
                            count++
                        } catch (_: Exception) {
                            break
                        }
                    }
                }

                val framesJson = frames.joinToString(",") { f ->
                    val fnName = AppContainer.getSymbolName(f.rip, true)
                    """{"rip":"0x${f.rip.toULong().toString(16).uppercase()}","rbp":"0x${f.rbp.toULong().toString(16).uppercase()}","function":"${escape(fnName)}"}"""
                }

                sendJsonResponse(exchange, 200, """{"success":true,"count":${frames.size},"frames":[$framesJson]}""")
            } catch (e: Exception) {
                sendJsonResponse(exchange, 500, """{"success":false,"error":"${escape(e.message ?: "Stack read error")}"}""")
            }
        }
    }

    private fun handleNavigate(exchange: HttpExchange) {
        if (handleCors(exchange)) return
        try {
            val body = readBody(exchange)
            val params = parseQueryParams(exchange.requestURI.query)
            val addr = parseJsonLong(body, "address") ?: parseAddress(params["address"])
            val modeStr = parseJsonString(body, "mode") ?: params["mode"] ?: "disasm"

            val mode = when (modeStr.lowercase()) {
                "graph" -> 1
                "hex" -> 2
                else -> 0 // disasm
            }

            if (addr != null) {
                AppContainer.onNavigateRequested?.invoke(addr, mode)
                sendJsonResponse(exchange, 200, """{"success":true,"navigatedTo":"0x${addr.toString(16).uppercase()}","mode":$mode}""")
            } else {
                sendJsonResponse(exchange, 400, """{"success":false,"error":"Missing address parameter"}""")
            }
        } catch (e: Exception) {
            sendJsonResponse(exchange, 500, """{"success":false,"error":"${escape(e.message ?: "Navigate error")}"}""")
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private suspend fun disassembleRegionInternal(
        pid: Int?,
        address: Long,
        length: Int,
        maxEntries: Int
    ): List<DisasmLine> {
        val client = AppContainer.clientAdapter.client
        val isConnected = AppContainer.debuggerUseCase.isConnected.value && pid != null

        val rawBytes = clientMutex.withLock {
            if (isConnected) {
                client.readMemory(pid!!, address, length)
            } else {
                val localMap = AppContainer.debuggerUseCase.vmMaps.value.firstOrNull { address >= it.start && address < it.end }
                val data = localMap?.localData
                if (data != null) {
                    val off = (address - localMap.start).toInt()
                    val count = minOf(length, data.size - off)
                    if (count > 0) data.copyOfRange(off, off + count) else ByteArray(0)
                } else ByteArray(0)
            }
        }

        if (rawBytes.isEmpty()) return emptyList()

        val rawInstrs = clientMutex.withLock {
            if (isConnected) {
                try {
                    client.disassembleRegion(pid!!, address, length, maxEntries)
                } catch (_: Exception) {
                    com.osr.ps5debugger.util.LocalDisassembler.disassemble(rawBytes, address)
                }
            } else {
                com.osr.ps5debugger.util.LocalDisassembler.disassemble(rawBytes, address)
            }
        }

        return rawInstrs.take(maxEntries).map { instr ->
            val offset = (instr.addr - address).toInt()
            val instrBytes = if (offset >= 0 && offset + instr.length <= rawBytes.size) {
                rawBytes.copyOfRange(offset, offset + instr.length)
            } else ByteArray(0)
            val symName = AppContainer.symbolNames[instr.addr]
            DisasmLine(instr, instrBytes, null, symName)
        }
    }

    private fun formatDisasmLineJson(line: DisasmLine): String {
        val instr = line.instr
        val bytes = line.bytes
        val mnemonic = DisasmFormatter.getMnemonic(instr, bytes)
        val operands = DisasmFormatter.formatOperands(instr, bytes)
        val infoText = DisasmFormatter.getInfoText(instr, bytes)
        val jumpTarget = DisasmFormatter.getJumpTarget(instr, bytes)

        val targetStr = if (jumpTarget != 0L) "\"0x${jumpTarget.toString(16).uppercase()}\""
        else if (instr.ripRelTarget != 0L) "\"0x${instr.ripRelTarget.toString(16).uppercase()}\""
        else "null"

        val hexBytes = bytes.joinToString(" ") { "%02X".format(it) }

        return """
        {
            "address": "0x${instr.addr.toString(16).uppercase()}",
            "bytes": "$hexBytes",
            "length": ${instr.length},
            "mnemonic": "${escape(mnemonic)}",
            "operands": "${escape(operands)}",
            "symbol": ${if (line.symbolName != null) "\"${escape(line.symbolName)}\"" else "null"},
            "comment": "${escape(infoText)}",
            "isCall": ${instr.isCall},
            "isRet": ${instr.isRet},
            "isJmp": ${instr.isJmp},
            "isCondJmp": ${instr.isCondJmp},
            "targetAddress": $targetStr
        }
        """.trimIndent()
    }

    private fun formatHexDump(baseAddr: Long, bytes: ByteArray): String {
        val sb = StringBuilder()
        for (i in bytes.indices step 16) {
            val addr = baseAddr + i
            val rowBytes = bytes.copyOfRange(i, minOf(i + 16, bytes.size))
            val hexPart = rowBytes.joinToString(" ") { "%02X".format(it) }.padEnd(48)
            val asciiPart = rowBytes.map { b ->
                val v = b.toInt() and 0xFF
                if (v in 32..126) v.toChar() else '.'
            }.joinToString("")
            sb.append(String.format("0x%012X:  %s  | %s |\n", addr, hexPart, asciiPart))
        }
        return sb.toString().trimEnd()
    }

    private fun bytesToDwordsHex(bytes: ByteArray): List<String> {
        val list = mutableListOf<String>()
        for (i in 0 until bytes.size - 3 step 4) {
            val d = (bytes[i].toLong() and 0xFF) or
                    ((bytes[i + 1].toLong() and 0xFF) shl 8) or
                    ((bytes[i + 2].toLong() and 0xFF) shl 16) or
                    ((bytes[i + 3].toLong() and 0xFF) shl 24)
            list.add("0x${d.toString(16).uppercase().padStart(8, '0')}")
        }
        return list
    }

    private fun handleCors(exchange: HttpExchange): Boolean {
        exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
        exchange.responseHeaders.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        exchange.responseHeaders.add("Access-Control-Allow-Headers", "Content-Type, Authorization")
        if (exchange.requestMethod.equals("OPTIONS", ignoreCase = true)) {
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
            return true
        }
        return false
    }

    private fun sendJsonResponse(exchange: HttpExchange, statusCode: Int, json: String) {
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun readBody(exchange: HttpExchange): String {
        return exchange.requestBody.use { it.readBytes().toString(StandardCharsets.UTF_8) }
    }

    private fun parseQueryParams(query: String?): Map<String, String> {
        if (query.isNullOrEmpty()) return emptyMap()
        val map = mutableMapOf<String, String>()
        query.split("&").forEach { param ->
            val parts = param.split("=", limit = 2)
            val key = URLDecoder.decode(parts[0], "UTF-8")
            val value = if (parts.size > 1) URLDecoder.decode(parts[1], "UTF-8") else ""
            map[key] = value
        }
        return map
    }

    private fun parseAddress(str: String?): Long? {
        if (str.isNullOrBlank()) return null
        val trimmed = str.trim()
        return if (trimmed.startsWith("0x", ignoreCase = true)) {
            trimmed.substring(2).toLongOrNull(16)
        } else {
            trimmed.toLongOrNull(16) ?: trimmed.toLongOrNull()
        }
    }

    private fun parseHexBytes(hex: String): ByteArray {
        val clean = hex.replace(" ", "").replace("0x", "", ignoreCase = true)
        val len = clean.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len - 1) {
            data[i / 2] = ((Character.digit(clean[i], 16) shl 4) + Character.digit(clean[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun parseJsonLong(json: String, key: String): Long? {
        val regex = Regex("\"$key\"\\s*:\\s*(\"0x[0-9a-fA-F]+\"|\"[0-9]+\"|0x[0-9a-fA-F]+|[0-9]+)")
        val match = regex.find(json)?.groupValues?.get(1)?.replace("\"", "") ?: return null
        return parseAddress(match)
    }

    private fun parseJsonString(json: String, key: String): String? {
        val regex = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun escape(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}

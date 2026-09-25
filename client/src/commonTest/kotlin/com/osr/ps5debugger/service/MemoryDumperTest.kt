package com.osr.ps5debugger.service

import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.domain.model.Process
import com.osr.ps5debugger.domain.model.LogEntry
import com.osr.ps5debugger.domain.model.WatchItem
import com.osr.ps5debugger.ports.outbound.DebuggerClientPort
import com.osr.ps5debugger.ports.inbound.DebuggerUseCase
import com.osr.ps5debugger.protocol.Ps5ProcessInfo
import com.osr.ps5debugger.protocol.Ps5DebugEvent
import com.osr.ps5debugger.protocol.GpRegs
import com.osr.ps5debugger.protocol.DbRegs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemoryDumperTest {

    @Test
    fun testMergeLibraryMapsAndUnnamed() {
        val maps = listOf(
            MemoryRange(name = "libc.prx", start = 0x800000000L, end = 0x800010000L, offset = 0, protections = 5), // R-X
            MemoryRange(name = "libc.prx", start = 0x800010000L, end = 0x800014000L, offset = 0x10000, protections = 1), // R--
            MemoryRange(name = "libc.prx", start = 0x800014000L, end = 0x800018000L, offset = 0x14000, protections = 3), // RW-
            MemoryRange(name = "libc.prx", start = 0x800018000L, end = 0x80001C000L, offset = 0x18000, protections = 3), // RW-
            MemoryRange(name = "libc.prx", start = 0x80001C000L, end = 0x800020000L, offset = 0x1C000, protections = 3), // RW-
            MemoryRange(name = "libkernel.prx", start = 0x800030000L, end = 0x800040000L, offset = 0, protections = 5),
            MemoryRange(name = "libkernel.prx", start = 0x800040000L, end = 0x800048000L, offset = 0x10000, protections = 3),
            MemoryRange(name = "", start = 0x900000000L, end = 0x900010000L, offset = 0, protections = 3),
            MemoryRange(name = "unnamed", start = 0xA00000000L, end = 0xA00020000L, offset = 0, protections = 3)
        )

        val merged = MemoryDumper.mergeLibraryMaps(maps)

        // 1 entry for libc.prx, 1 entry for libkernel.prx, 1 unified entry for all unnamed = 3 entries total
        assertEquals(3, merged.size)

        // Check libc.prx
        val libc = merged.first { it.name == "libc.prx" }
        assertEquals(0x800000000L, libc.start)
        assertEquals(0x800020000L, libc.end)
        assertEquals(0x20000L, libc.totalSize)
        assertEquals(5, libc.subRanges.size)
        assertTrue(libc.isMergedLibrary)
        assertEquals("RWX", libc.getProtString()) // 5 or 1 or 3 = 7 (RWX)

        // Check libkernel.prx
        val libkernel = merged.first { it.name == "libkernel.prx" }
        assertEquals(0x800030000L, libkernel.start)
        assertEquals(0x800048000L, libkernel.end)
        assertEquals(0x18000L, libkernel.totalSize)
        assertEquals(2, libkernel.subRanges.size)
        assertTrue(libkernel.isMergedLibrary)

        // Check merged unnamed regions
        val unnamed = merged.first { it.name == "unnamed" }
        assertEquals(0x900000000L, unnamed.start)
        assertEquals(0xA00020000L, unnamed.end)
        assertEquals(0x10000L + 0x20000L, unnamed.totalSize)
        assertEquals(2, unnamed.subRanges.size)
        assertTrue(unnamed.isMergedLibrary)
    }

    @Test
    fun testDumpValidPrxWithElfHeader() = runBlocking {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "ps5_dumper_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            // Valid ELF header bytes: 0x7F 'E' 'L' 'F' followed by segment text
            val elfHeader = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())
            val seg1Text = "VALID_PRX_TEXT_PAYLOAD_HERE!".toByteArray(Charsets.UTF_8)
            val seg1Data = elfHeader + seg1Text

            val seg2Data = "SEGMENT_TWO_DATA_HERE!".toByteArray(Charsets.UTF_8)

            val seg1 = MemoryRange("libc.prx", start = 0x1000L, end = 0x1000L + seg1Data.size, offset = 0, protections = 5)
            val seg2 = MemoryRange("libc.prx", start = 0x1000L + seg1Data.size, end = 0x1000L + seg1Data.size + seg2Data.size, offset = seg1Data.size.toLong(), protections = 3)

            val merged = MemoryDumper.mergeLibraryMaps(listOf(seg1, seg2))
            assertEquals(1, merged.size)

            val loggedMessages = mutableListOf<String>()

            val mockClient = object : DebuggerClientPort {
                override val isConnected: Boolean = true
                override val debugEvents: SharedFlow<Ps5DebugEvent> = MutableSharedFlow()
                override val logLines: SharedFlow<String> = MutableSharedFlow()

                override suspend fun connect(ip: String): Boolean = true
                override suspend fun disconnect() {}
                override suspend fun auth(): Boolean = true
                override suspend fun ping(): Boolean = true
                override suspend fun getProcesses(): List<Process> = emptyList()
                override suspend fun getMaps(pid: Int): List<MemoryRange> = emptyList()
                override suspend fun getProcessInfo(pid: Int) = Ps5ProcessInfo(0, "", "", "", "")
                override suspend fun readMemory(pid: Int, address: Long, length: Int): ByteArray {
                    return if (address >= seg1.start && address < seg1.end) {
                        val offset = (address - seg1.start).toInt()
                        seg1Data.copyOfRange(offset, offset + length)
                    } else if (address >= seg2.start && address < seg2.end) {
                        val offset = (address - seg2.start).toInt()
                        seg2Data.copyOfRange(offset, offset + length)
                    } else {
                        ByteArray(length)
                    }
                }
                override suspend fun writeMemory(pid: Int, address: Long, data: ByteArray): Boolean = true
                override suspend fun writeMemoryMulti(pid: Int, writes: List<Pair<Long, ByteArray>>, withStatusReport: Boolean): Boolean = true
                override suspend fun getForegroundApp(): com.osr.ps5debugger.protocol.Ps5ForegroundApp = com.osr.ps5debugger.protocol.Ps5ForegroundApp(0, "", "", "", "")
                override suspend fun pullFile(path: String): ByteArray? = null
                override suspend fun uploadElfRpc(pid: Int, elfBytes: ByteArray): Long? = null
                override fun startDebugChannel() {}
                override fun stopDebugChannel() {}
                override fun startKlogForwarder(ip: String) {}
                override fun stopKlogForwarder() {}
            }

            val mockUseCase = object : DebuggerUseCase {
                override val isConnected: StateFlow<Boolean> = MutableStateFlow(true)
                override val isAttached: StateFlow<Boolean> = MutableStateFlow(false)
                override val isProcessStopped: StateFlow<Boolean> = MutableStateFlow(false)
                override val threadList: StateFlow<List<Int>> = MutableStateFlow(emptyList())
                override val selectedLwpid: StateFlow<Int?> = MutableStateFlow(null)
                override val selectedRegs: StateFlow<GpRegs?> = MutableStateFlow(null)
                override val selectedDbRegs: StateFlow<DbRegs?> = MutableStateFlow(null)
                override val selectedFsGs: StateFlow<Pair<Long, Long>?> = MutableStateFlow(null)
                override val processes: StateFlow<List<Process>> = MutableStateFlow(emptyList())
                override val activeProcess: StateFlow<Process?> = MutableStateFlow(Process("test", 42))
                override val activeProcessInfo = MutableStateFlow<Ps5ProcessInfo?>(null)
                override val debugEvents: SharedFlow<Ps5DebugEvent> = MutableSharedFlow()
                override val logs: StateFlow<List<LogEntry>> = MutableStateFlow(emptyList())
                override val watchlist: StateFlow<List<WatchItem>> = MutableStateFlow(emptyList())
                override val vmMaps: StateFlow<List<MemoryRange>> = MutableStateFlow(emptyList())
                override val gameCheatProfiles: StateFlow<List<com.osr.ps5debugger.domain.model.GameCheatProfile>> = MutableStateFlow(emptyList())

                override fun setAttached(attached: Boolean) {}
                override fun setProcessStopped(stopped: Boolean) {}
                override fun setThreadList(threads: List<Int>) {}
                override fun setSelectedLwpid(lwpid: Int?) {}
                override fun setSelectedRegs(regs: GpRegs?) {}
                override fun setSelectedDbRegs(regs: DbRegs?) {}
                override fun setSelectedFsGs(fsgs: Pair<Long, Long>?) {}
                override suspend fun connect(ip: String): Boolean = true
                override suspend fun disconnect() {}
                override suspend fun refreshProcesses() {}
                override suspend fun selectProcess(proc: Process?) {}
                override suspend fun loadMemoryMaps(proc: Process) {}
                override suspend fun pullFile(path: String): Result<ByteArray> = Result.failure(Exception())
                override suspend fun readMemory(address: Long, length: Int): Result<ByteArray> = Result.success(ByteArray(length))
                override suspend fun writeMemory(address: Long, data: ByteArray): Result<Boolean> = Result.success(true)
                override fun log(tag: String, message: String, level: LogEntry.Level) {
                    loggedMessages.add(message)
                }
                override fun clearLogs() {}
                override fun addToWatchlist(address: Long, type: String, byteLength: Int?) {}
                override fun addWatchItem(item: WatchItem) {}
                override fun updateWatchItem(item: WatchItem) {}
                override fun removeWatchItem(item: WatchItem) {}
                override fun toggleFreezeWatchItem(item: WatchItem) {}
                override fun clearWatchlist() {}
                override fun addCheat(titleId: String, version: String, cheat: com.osr.ps5debugger.domain.model.Cheat, gameName: String) {}
                override fun toggleCheat(titleId: String, version: String, cheatId: String): com.osr.ps5debugger.domain.model.Cheat? = null
                override fun deleteCheat(titleId: String, version: String, cheatId: String) {}
                override fun updateGameName(titleId: String, name: String) {}
                override fun updateGameVersion(titleId: String, version: String) {}
                override fun updateGamePlatform(titleId: String, platform: String) {}
                override suspend fun applyCheat(pid: Int, cheat: com.osr.ps5debugger.domain.model.Cheat, newValue: String?) {}
                override suspend fun exportCheatsToPs5(
                    ip: String,
                    titleId: String,
                    version: String,
                    gameName: String,
                    processName: String,
                    credits: List<String>,
                    cheats: List<com.osr.ps5debugger.domain.model.Cheat>
                ): Result<String> = Result.success("/data/OnionHEN/cheats/${titleId}_${version}.json")
                override fun saveCheats(onResult: (String) -> Unit) {}
                override fun loadCheats(json: String) {}
            }

            val result = MemoryDumper.dumpRegions(
                pid = 42,
                regions = merged,
                outputDir = tempDir,
                clientPort = mockClient,
                useCase = mockUseCase,
                onProgress = { _, _ -> }
            )

            assertTrue(result.isSuccess)
            val files = tempDir.listFiles() ?: emptyArray()
            // Exactly ONE file produced named "libc.prx"
            assertEquals(1, files.size)
            val dumpedFile = files.first()
            assertEquals("libc.prx", dumpedFile.name)
            val dumpedBytes = dumpedFile.readBytes()
            assertEquals(seg1Data.size + seg2Data.size, dumpedBytes.size)

            // Verify ELF magic at start of dumped PRX
            assertEquals(0x7F.toByte(), dumpedBytes[0])
            assertEquals('E'.code.toByte(), dumpedBytes[1])
            assertEquals('L'.code.toByte(), dumpedBytes[2])
            assertEquals('F'.code.toByte(), dumpedBytes[3])

            // Verify ELF header verification log message
            assertTrue(loggedMessages.any { it.contains("Verified valid ELF/PRX header for libc.prx") })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun test_dumpRegions_synthesizes_valid_elf_header_when_memory_has_raw_code() = runBlocking {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "ps5_test_dump_elf_synth_" + System.currentTimeMillis())
        tempDir.mkdirs()

        try {
            // User scenario: Raw machine code in memory starting with `48 8D 3D 39 5F 03 00 E8...`
            val seg1Data = byteArrayOf(
                0x48.toByte(), 0x8D.toByte(), 0x3D.toByte(), 0x39.toByte(),
                0x5F.toByte(), 0x03.toByte(), 0x00.toByte(), 0xE8.toByte(),
                0x34.toByte(), 0x53.toByte(), 0x11.toByte(), 0x00.toByte()
            )
            val seg2Data = "RODATA_STRING_TABLE_TEST".toByteArray(Charsets.UTF_8)

            val seg1 = MemoryRange("libc.prx", start = 0x800000000L, end = 0x800000000L + seg1Data.size, offset = 0, protections = 5)
            val seg2 = MemoryRange("libc.prx", start = 0x800004000L, end = 0x800004000L + seg2Data.size, offset = 0x4000, protections = 1)

            val merged = MemoryDumper.mergeLibraryMaps(listOf(seg1, seg2))
            assertEquals(1, merged.size)

            val loggedMessages = mutableListOf<String>()

            val mockClient = object : DebuggerClientPort {
                override val isConnected: Boolean = true
                override val debugEvents: SharedFlow<Ps5DebugEvent> = MutableSharedFlow()
                override val logLines: SharedFlow<String> = MutableSharedFlow()

                override suspend fun connect(ip: String): Boolean = true
                override suspend fun disconnect() {}
                override suspend fun auth(): Boolean = true
                override suspend fun ping(): Boolean = true
                override suspend fun getProcesses(): List<Process> = emptyList()
                override suspend fun getMaps(pid: Int): List<MemoryRange> = emptyList()
                override suspend fun getProcessInfo(pid: Int) = Ps5ProcessInfo(0, "", "", "", "")
                override suspend fun readMemory(pid: Int, address: Long, length: Int): ByteArray {
                    return if (address >= seg1.start && address < seg1.end) {
                        val offset = (address - seg1.start).toInt()
                        seg1Data.copyOfRange(offset, offset + length)
                    } else if (address >= seg2.start && address < seg2.end) {
                        val offset = (address - seg2.start).toInt()
                        seg2Data.copyOfRange(offset, offset + length)
                    } else {
                        ByteArray(length)
                    }
                }
                override suspend fun writeMemory(pid: Int, address: Long, data: ByteArray): Boolean = true
                override suspend fun writeMemoryMulti(pid: Int, writes: List<Pair<Long, ByteArray>>, withStatusReport: Boolean): Boolean = true
                override suspend fun getForegroundApp(): com.osr.ps5debugger.protocol.Ps5ForegroundApp = com.osr.ps5debugger.protocol.Ps5ForegroundApp(0, "", "", "", "")
                override suspend fun pullFile(path: String): ByteArray? = null
                override suspend fun uploadElfRpc(pid: Int, elfBytes: ByteArray): Long? = null
                override fun startDebugChannel() {}
                override fun stopDebugChannel() {}
                override fun startKlogForwarder(ip: String) {}
                override fun stopKlogForwarder() {}
            }

            val mockUseCase = object : DebuggerUseCase {
                override val isConnected: StateFlow<Boolean> = MutableStateFlow(true)
                override val isAttached: StateFlow<Boolean> = MutableStateFlow(false)
                override val isProcessStopped: StateFlow<Boolean> = MutableStateFlow(false)
                override val threadList: StateFlow<List<Int>> = MutableStateFlow(emptyList())
                override val selectedLwpid: StateFlow<Int?> = MutableStateFlow(null)
                override val selectedRegs: StateFlow<GpRegs?> = MutableStateFlow(null)
                override val selectedDbRegs: StateFlow<DbRegs?> = MutableStateFlow(null)
                override val selectedFsGs: StateFlow<Pair<Long, Long>?> = MutableStateFlow(null)
                override val processes: StateFlow<List<Process>> = MutableStateFlow(emptyList())
                override val activeProcess: StateFlow<Process?> = MutableStateFlow(Process("test", 42))
                override val activeProcessInfo = MutableStateFlow<Ps5ProcessInfo?>(null)
                override val debugEvents: SharedFlow<Ps5DebugEvent> = MutableSharedFlow()
                override val logs: StateFlow<List<LogEntry>> = MutableStateFlow(emptyList())
                override val watchlist: StateFlow<List<WatchItem>> = MutableStateFlow(emptyList())
                override val vmMaps: StateFlow<List<MemoryRange>> = MutableStateFlow(emptyList())
                override val gameCheatProfiles: StateFlow<List<com.osr.ps5debugger.domain.model.GameCheatProfile>> = MutableStateFlow(emptyList())

                override fun setAttached(attached: Boolean) {}
                override fun setProcessStopped(stopped: Boolean) {}
                override fun setThreadList(threads: List<Int>) {}
                override fun setSelectedLwpid(lwpid: Int?) {}
                override fun setSelectedRegs(regs: GpRegs?) {}
                override fun setSelectedDbRegs(regs: DbRegs?) {}
                override fun setSelectedFsGs(fsgs: Pair<Long, Long>?) {}
                override suspend fun connect(ip: String): Boolean = true
                override suspend fun disconnect() {}
                override suspend fun refreshProcesses() {}
                override suspend fun selectProcess(proc: Process?) {}
                override suspend fun loadMemoryMaps(proc: Process) {}
                override suspend fun pullFile(path: String): Result<ByteArray> = Result.failure(Exception())
                override suspend fun readMemory(address: Long, length: Int): Result<ByteArray> = Result.success(ByteArray(length))
                override suspend fun writeMemory(address: Long, data: ByteArray): Result<Boolean> = Result.success(true)
                override fun log(tag: String, message: String, level: LogEntry.Level) {
                    loggedMessages.add(message)
                }
                override fun clearLogs() {}
                override fun addToWatchlist(address: Long, type: String, byteLength: Int?) {}
                override fun addWatchItem(item: WatchItem) {}
                override fun updateWatchItem(item: WatchItem) {}
                override fun removeWatchItem(item: WatchItem) {}
                override fun toggleFreezeWatchItem(item: WatchItem) {}
                override fun clearWatchlist() {}
                override fun addCheat(titleId: String, version: String, cheat: com.osr.ps5debugger.domain.model.Cheat, gameName: String) {}
                override fun toggleCheat(titleId: String, version: String, cheatId: String): com.osr.ps5debugger.domain.model.Cheat? = null
                override fun deleteCheat(titleId: String, version: String, cheatId: String) {}
                override fun updateGameName(titleId: String, name: String) {}
                override fun updateGameVersion(titleId: String, version: String) {}
                override fun updateGamePlatform(titleId: String, platform: String) {}
                override suspend fun applyCheat(pid: Int, cheat: com.osr.ps5debugger.domain.model.Cheat, newValue: String?) {}
                override suspend fun exportCheatsToPs5(
                    ip: String,
                    titleId: String,
                    version: String,
                    gameName: String,
                    processName: String,
                    credits: List<String>,
                    cheats: List<com.osr.ps5debugger.domain.model.Cheat>
                ): Result<String> = Result.success("/data/OnionHEN/cheats/${titleId}_${version}.json")
                override fun saveCheats(onResult: (String) -> Unit) {}
                override fun loadCheats(json: String) {}
            }

            val result = MemoryDumper.dumpRegions(
                pid = 42,
                regions = merged,
                outputDir = tempDir,
                clientPort = mockClient,
                useCase = mockUseCase,
                onProgress = { _, _ -> }
            )

            assertTrue(result.isSuccess)
            val files = tempDir.listFiles() ?: emptyArray()
            assertEquals(1, files.size)
            val dumpedFile = files.first()
            assertEquals("libc.prx", dumpedFile.name)

            val dumpedBytes = dumpedFile.readBytes()
            // Verify synthesized ELF header at byte 0
            assertEquals(0x7F.toByte(), dumpedBytes[0])
            assertEquals('E'.code.toByte(), dumpedBytes[1])
            assertEquals('L'.code.toByte(), dumpedBytes[2])
            assertEquals('F'.code.toByte(), dumpedBytes[3])
            assertEquals(2.toByte(), dumpedBytes[4]) // ELFCLASS64
            assertEquals(1.toByte(), dumpedBytes[5]) // ELFDATA2LSB
            assertEquals(9.toByte(), dumpedBytes[7]) // ELFOSABI_FREEBSD

            // Verify e_type = ET_DYN (3)
            val eType = (dumpedBytes[16].toInt() and 0xFF) or ((dumpedBytes[17].toInt() and 0xFF) shl 8)
            assertEquals(3, eType)

            // Verify e_machine = EM_X86_64 (0x3E = 62)
            val eMachine = (dumpedBytes[18].toInt() and 0xFF) or ((dumpedBytes[19].toInt() and 0xFF) shl 8)
            assertEquals(0x3E, eMachine)

            // Verify segment 0 code is located at 0x4000 (after ELF and Program Headers)
            assertEquals(0x48.toByte(), dumpedBytes[0x4000])
            assertEquals(0x8D.toByte(), dumpedBytes[0x4001])
            assertEquals(0x3D.toByte(), dumpedBytes[0x4002])
            assertEquals(0x39.toByte(), dumpedBytes[0x4003])
            assertEquals(0x5F.toByte(), dumpedBytes[0x4004])
            assertEquals(0x03.toByte(), dumpedBytes[0x4005])
            assertEquals(0x00.toByte(), dumpedBytes[0x4006])
            assertEquals(0xE8.toByte(), dumpedBytes[0x4007])

            // Verify ELF header verification log message
            assertTrue(loggedMessages.any { it.contains("Verified valid ELF/PRX header for libc.prx") })
        } finally {
            tempDir.deleteRecursively()
        }
    }
}

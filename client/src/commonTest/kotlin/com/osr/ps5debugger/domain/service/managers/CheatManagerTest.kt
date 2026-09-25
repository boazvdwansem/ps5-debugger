package com.osr.ps5debugger.domain.service.managers

import com.osr.ps5debugger.domain.model.*
import com.osr.ps5debugger.ports.outbound.CheatStoragePort
import com.osr.ps5debugger.ports.outbound.DebuggerClientPort
import com.osr.ps5debugger.ports.outbound.LogStoragePort
import com.osr.ps5debugger.protocol.Ps5DebugEvent
import com.osr.ps5debugger.protocol.Ps5ForegroundApp
import com.osr.ps5debugger.protocol.Ps5ProcessInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class CheatManagerTest {

    private class MockLogStorage : LogStoragePort {
        val entries = mutableListOf<LogEntry>()
        override fun persistLog(entry: LogEntry) {
            entries.add(entry)
        }
    }

    private class MockCheatStorage : CheatStoragePort {
        var saved: List<GameCheatProfile> = emptyList()
        override fun loadCheats(): List<GameCheatProfile> = saved
        override fun saveCheats(profiles: List<GameCheatProfile>) {
            saved = profiles
        }
    }

    private class MockDebuggerClient : DebuggerClientPort {
        override val isConnected: Boolean = true
        override val debugEvents: SharedFlow<Ps5DebugEvent> = MutableSharedFlow()
        override val logLines: SharedFlow<String> = MutableSharedFlow()
        val writeMultiCalls = mutableListOf<Pair<Int, List<Pair<Long, ByteArray>>>>()

        override suspend fun connect(ip: String): Boolean = true
        override suspend fun disconnect() {}
        override suspend fun auth(): Boolean = true
        override suspend fun ping(): Boolean = true
        override suspend fun getProcesses(): List<Process> = emptyList()
        override suspend fun getMaps(pid: Int): List<MemoryRange> = emptyList()
        override suspend fun getProcessInfo(pid: Int): Ps5ProcessInfo = Ps5ProcessInfo(0, "", "", "", "")
        override suspend fun getForegroundApp(): Ps5ForegroundApp = Ps5ForegroundApp(0, "", "", "", "")
        override suspend fun pullFile(path: String): ByteArray? = null
        override suspend fun readMemory(pid: Int, address: Long, length: Int): ByteArray = ByteArray(length)
        override suspend fun uploadElfRpc(pid: Int, elfBytes: ByteArray): Long? = null
        override suspend fun writeMemory(pid: Int, address: Long, data: ByteArray): Boolean = true
        override suspend fun writeMemoryMulti(pid: Int, writes: List<Pair<Long, ByteArray>>, withStatusReport: Boolean): Boolean {
            writeMultiCalls.add(pid to writes)
            return true
        }
        override fun startDebugChannel() {}
        override fun stopDebugChannel() {}
        override fun startKlogForwarder(ip: String) {}
        override fun stopKlogForwarder() {}
    }

    @Test
    fun testSerializationBackwardsCompatibility() {
        // Old JSON format with single address, hexOffValue
        val oldJson = """
            [
                {
                    "titleId": "CUSA12345",
                    "name": "Old Game",
                    "version": "1.00",
                    "cheats": [
                        {
                            "id": "c1",
                            "name": "Infinite Ammo",
                            "type": "Toggle",
                            "address": 4194304,
                            "hexOnValue": "9090",
                            "hexOffValue": "0102",
                            "titleId": "CUSA12345"
                        }
                    ]
                }
            ]
        """.trimIndent()

        val decoded: List<GameCheatProfile> = Json.decodeFromString(oldJson)
        assertEquals(1, decoded.size)
        val cheat = decoded.first().cheats.first()
        assertEquals(4194304L, cheat.address)
        assertEquals("9090", cheat.hexOnValue)
        val patches = cheat.getEffectivePatches()
        assertEquals(1, patches.size)
        assertEquals(4194304L, patches[0].address)
        assertEquals("9090", patches[0].hexOnValue)
        assertEquals("0102", patches[0].hexOffValue)
    }

    @Test
    fun testMultiPatchCheatSerialization() {
        val cheat = Cheat(
            id = "multi_1",
            name = "God Mode",
            type = CheatType.Toggle,
            isEnabled = true,
            titleId = "CUSA99999",
            patches = listOf(
                CheatPatch(0x1000L, hexOnValue = "9090", hexOffValue = "1122"),
                CheatPatch(0x2000L, hexOnValue = "00000000", hexOffValue = "AABBCCDD")
            )
        )

        val profile = GameCheatProfile(
            titleId = "CUSA99999",
            name = "Test Game",
            version = "1.00",
            cheats = listOf(cheat)
        )

        val json = Json.encodeToString(listOf(profile))
        val decoded: List<GameCheatProfile> = Json.decodeFromString(json)
        val loadedCheat = decoded[0].cheats[0]

        assertEquals(2, loadedCheat.patches.size)
        assertEquals(0x1000L, loadedCheat.patches[0].address)
        assertEquals("9090", loadedCheat.patches[0].hexOnValue)
        assertEquals(0x2000L, loadedCheat.patches[1].address)
        assertEquals("00000000", loadedCheat.patches[1].hexOnValue)
    }

    @Test
    fun testApplyCheatInjectsWithWriteMemoryMulti() = runBlocking {
        val mockClient = MockDebuggerClient()
        val mockStorage = MockCheatStorage()
        val mockLogStorage = MockLogStorage()
        val logManager = LogManager(mockLogStorage)
        val cheatManager = CheatManager(mockClient, CoroutineScope(Dispatchers.Unconfined), logManager, mockStorage)

        val cheat = Cheat(
            id = "c1",
            name = "Invincibility",
            type = CheatType.Toggle,
            isEnabled = true,
            titleId = "CUSA12345",
            patches = listOf(
                CheatPatch(address = 0x400000L, hexOnValue = "90 90", hexOffValue = "89 05"),
                CheatPatch(address = 0x500000L, hexOnValue = "0x00 01", hexOffValue = "0x02 03")
            )
        )

        cheatManager.applyCheat(1234, cheat)

        assertEquals(1, mockClient.writeMultiCalls.size)
        val (pid, writes) = mockClient.writeMultiCalls.first()
        assertEquals(1234, pid)
        assertEquals(2, writes.size)

        assertEquals(0x400000L, writes[0].first)
        assertEquals(2, writes[0].second.size)
        assertEquals(0x90.toByte(), writes[0].second[0])
        assertEquals(0x90.toByte(), writes[0].second[1])

        assertEquals(0x500000L, writes[1].first)
        assertEquals(2, writes[1].second.size)
        assertEquals(0x00.toByte(), writes[1].second[0])
        assertEquals(0x01.toByte(), writes[1].second[1])
    }
}

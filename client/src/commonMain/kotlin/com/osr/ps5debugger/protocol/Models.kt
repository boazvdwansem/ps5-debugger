package com.osr.ps5debugger.protocol

data class Ps5Process(
    val name: String,
    val pid: Int
)

data class Ps5VmMapEntry(
    val name: String,
    val start: Long,
    val end: Long,
    val offset: Long,
    val prot: Int
) {
    val size: Long get() = end - start
    
    fun getProtString(): String {
        val r = if ((prot and 1) != 0) "R" else "-"
        val w = if ((prot and 2) != 0) "W" else "-"
        val x = if ((prot and 4) != 0) "X" else "-"
        return "$r$w$x"
    }
}

data class Ps5ProcessInfo(
    val pid: Int,
    val name: String,
    val path: String,
    val titleId: String,
    val contentId: String
)

data class Ps5TurboScanCaps(
    val version: Int,
    val engines: Int,
    val maxThreads: Int
) {
    fun hasEngine(engineBit: Int): Boolean = (engines and engineBit) != 0
}

data class Ps5DisasmInstr(
    val addr: Long,
    val ripRelTarget: Long,
    val memDisp: Long,
    val length: Int,
    val kind: Int,
    val memBaseReg: Int,
    val memIndexReg: Int,
    val memScale: Int,
    val mnemonicLo: Int
) {
    val isCall: Boolean get() = (kind and ProtocolConstants.ZYDIS_KIND_CALL) != 0
    val isRet: Boolean get() = (kind and ProtocolConstants.ZYDIS_KIND_RET) != 0
    val isJmp: Boolean get() = (kind and ProtocolConstants.ZYDIS_KIND_JMP) != 0
    val isCondJmp: Boolean get() = (kind and ProtocolConstants.ZYDIS_KIND_COND_JMP) != 0
    val hasMemOp: Boolean get() = (kind and ProtocolConstants.ZYDIS_KIND_MEM_OP) != 0
    val isRipRel: Boolean get() = (kind and ProtocolConstants.ZYDIS_KIND_RIP_REL) != 0
    val isRead: Boolean get() = (kind and ProtocolConstants.ZYDIS_KIND_READ) != 0
    val isWrite: Boolean get() = (kind and ProtocolConstants.ZYDIS_KIND_WRITE) != 0
}

data class Ps5StackFrame(
    val rbp: Long,
    val rsp: Long,
    val savedRbp: Long,
    val retAddr: Long,
    val flags: Int,
    val frameLocals: ByteArray,
    val codeWindow: ByteArray
) {
    val localsOmitted: Boolean get() = (flags and 1) != 0
}

data class Ps5ScanResult(
    val offset: Long,
    val value: ByteArray
)

data class Ps5ForegroundApp(
    val pid: Int,
    val titleId: String,
    val contentId: String,
    val name: String,
    val appVer: String
)


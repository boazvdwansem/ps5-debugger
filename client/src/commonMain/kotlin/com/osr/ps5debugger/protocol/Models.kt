package com.osr.ps5debugger.protocol

data class Ps5Process(val name: String, val pid: Int)

data class Ps5VmMapEntry(
    val name: String,
    val start: Long,
    val end: Long,
    val offset: Long,
    val prot: Int
)

data class Ps5ProcessInfo(
    val pid: Int,
    val name: String,
    val path: String,
    val titleId: String,
    val contentId: String
)

data class Ps5ForegroundApp(
    val pid: Int,
    val titleId: String,
    val contentId: String,
    val name: String,
    val appVer: String
)

data class Ps5ScanResult(
    val offset: Long,
    val value: ByteArray
)

data class Ps5DisasmInstr(
    val addr: Long,
    val ripRelTarget: Long,
    val memDisp: Long,
    val length: Int,
    val kind: Int,
    val memBaseReg: Int,
    val memIndexReg: Int,
    val memScale: Int,
    val mnemonic: Int,
    val mnemonicLo: Int = 0
) {
    val isCall: Boolean get() = (kind and 0x01) != 0
    val isRet: Boolean get() = (kind and 0x02) != 0
    val isJmp: Boolean get() = (kind and 0x04) != 0
    val isCondJmp: Boolean get() = (kind and 0x08) != 0
    val hasMemOp: Boolean get() = (kind and 0x10) != 0
    val isRipRel: Boolean get() = (kind and 0x20) != 0
    val isRead: Boolean get() = (kind and 0x40) != 0
    val isWrite: Boolean get() = (kind and 0x80) != 0
}

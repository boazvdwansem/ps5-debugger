package com.osr.ps5debugger.protocol

object ProtocolConstants {
    const val PACKET_MAGIC = 0xFFAABBCC.toInt()
    const val BROADCAST_MAGIC = 0xFFFFAAAA.toInt()
    const val AUTH_MAGIC = 0xBB40E64D.toInt()

    // Status codes (internal representation)
    const val CMD_SUCCESS = 0x40000000
    const val CMD_ERROR = 0xF0000002.toInt()
    const val CMD_DATA_NULL = 0xF0000003.toInt()
    const val CMD_ALREADY_DEBUG = 0xF0000008.toInt()
    const val CMD_INVALID_INDEX = 0xF000000A.toInt()

    // Wire status codes (post-bitswap)
    const val WIRE_CMD_SUCCESS = 0x80000000.toInt()
    const val WIRE_CMD_ERROR = 0xF0000001.toInt()
    const val WIRE_CMD_DATA_NULL = 0xF0000003.toInt()
    const val WIRE_CMD_ALREADY_DEBUG = 0xF0000004.toInt()
    const val WIRE_CMD_INVALID_INDEX = 0xF0000005.toInt()

    // Opcodes
    const val CMD_VERSION = 0xBD000001.toInt()
    const val CMD_FW_VERSION = 0xBD000500.toInt()
    const val CMD_BRANDING = 0xBD000501.toInt()
    const val CMD_PLATFORM_ID = 0xBD000502.toInt()
    const val CMD_PROC_NOP = 0xBDAACC06.toInt()

    const val CMD_PROC_LIST = 0xBDAA0001.toInt()
    const val CMD_PROC_READ = 0xBDAA0002.toInt()
    const val CMD_PROC_WRITE = 0xBDAA0003.toInt()
    const val CMD_PROC_WRITE_MULTI = 0xBDAACC04.toInt()
    const val PROC_WRITE_MULTI_F_STATUS = 0x1
    const val CMD_PROC_MAPS = 0xBDAA0004.toInt()
    const val CMD_PROC_INTALL = 0xBDAA0005.toInt()
    const val CMD_PROC_CALL = 0xBDAA0006.toInt()
    const val CMD_PROC_ELF = 0xBDAA0007.toInt()
    const val CMD_PROC_PROTECT = 0xBDAA0008.toInt()
    const val CMD_PROC_SCAN = 0xBDAA0009.toInt()
    const val CMD_PROC_INFO = 0xBDAA000A.toInt()
    const val CMD_PROC_ALLOC = 0xBDAA000B.toInt()
    const val CMD_PROC_FREE = 0xBDAA000C.toInt()
    const val CMD_PROC_UNKNOWN_D = 0xBDAA000D.toInt()
    const val CMD_PROC_ALLOC_HINTED = 0xBDAA000E.toInt()
    const val CMD_PROC_ELF_RPC = 0xBDAA0010.toInt()

    const val CMD_PROC_DISASM_REGION = 0xBDAA0020.toInt()
    const val CMD_PROC_EXTRACT_CODE_XREFS = 0xBDAA0021.toInt()
    const val CMD_PROC_FIND_XREFS_TO = 0xBDAA0022.toInt()
    const val CMD_PROC_READ_STACK = 0xBDAA0023.toInt()
    const val CMD_PROC_ASSEMBLE = 0xBDAA0024.toInt()

    const val CMD_PROC_SCAN_AOB = 0xBDAA0501.toInt()
    const val CMD_PROC_SCAN_AOB_MULTI = 0xBDAA0502.toInt()
    const val CMD_PROC_AUTH = 0xBDAACCFF.toInt()

    const val CMD_PROC_SCAN_START = 0xBDAACC01.toInt()
    const val CMD_PROC_SCAN_COUNT = 0xBDAACC02.toInt()
    const val CMD_PROC_SCAN_GET = 0xBDAACC03.toInt()

    // Turbo scan
    const val CMD_PROC_TURBOSCAN_CAPS = 0xBDAACC10.toInt()
    const val CMD_PROC_TURBOSCAN_START = 0xBDAACC11.toInt()
    const val CMD_PROC_TURBOSCAN_COUNT = 0xBDAACC12.toInt()
    const val CMD_PROC_TURBOSCAN_GET = 0xBDAACC13.toInt()
    const val CMD_PROC_TURBOSCAN_END = 0xBDAACC14.toInt()
    const val CMD_PROC_TURBOSCAN_CONFIG = 0xBDAACC15.toInt()
    const val CMD_PROC_TURBOSCAN_REGIONS = 0xBDAACC16.toInt()

    // Debug
    const val CMD_DEBUG_ATTACH = 0xBDBB0001.toInt()
    const val CMD_DEBUG_DETACH = 0xBDBB0002.toInt()
    const val CMD_DEBUG_SET_BREAKPOINT = 0xBDBB0003.toInt()
    const val CMD_DEBUG_SET_WATCHPOINT = 0xBDBB0004.toInt()
    const val CMD_DEBUG_GET_THREAD_LIST = 0xBDBB0005.toInt()
    const val CMD_DEBUG_SUSPEND_THREAD = 0xBDBB0006.toInt()
    const val CMD_DEBUG_RESUME_THREAD = 0xBDBB0007.toInt()
    const val CMD_DEBUG_GETREGS = 0xBDBB0008.toInt()
    const val CMD_DEBUG_SETREGS = 0xBDBB0009.toInt()
    const val CMD_DEBUG_GETFPREGS = 0xBDBB000A.toInt()
    const val CMD_DEBUG_SETFPREGS = 0xBDBB000B.toInt()
    const val CMD_DEBUG_GETDBREGS = 0xBDBB000C.toInt()
    const val CMD_DEBUG_SETDBREGS = 0xBDBB000D.toInt()
    const val CMD_DEBUG_GETFSGSBASE = 0xBDBB000E.toInt()
    const val CMD_DEBUG_SETFSGSBASE = 0xBDBB000F.toInt()
    const val CMD_DEBUG_CONTINUE = 0xBDBB0010.toInt()
    const val CMD_DEBUG_THREAD_INFO = 0xBDBB0011.toInt()
    const val CMD_DEBUG_STEP = 0xBDBB0012.toInt()
    const val CMD_DEBUG_STEP_THREAD = 0xBDBB0013.toInt()
    const val CMD_DEBUG_PROCESS_STOP = 0xBDBB0500.toInt()

    // Console/System UI commands
    const val CMD_CONSOLE_REBOOT = 0xBDDD0001.toInt()
    const val CMD_CONSOLE_END = 0xBDDD0002.toInt()
    const val CMD_CONSOLE_PRINT = 0xBDDD0003.toInt()
    const val CMD_CONSOLE_NOTIFY = 0xBDDD0004.toInt()
    const val CMD_CONSOLE_INFO = 0xBDDD0005.toInt()
    const val CMD_CONSOLE_FOREGROUND_APP = 0xBDDD0006.toInt()

    // Kernel
    const val CMD_KERN_BASE = 0xBDCC0001.toInt()
    const val CMD_KERN_READ = 0xBDCC0002.toInt()
    const val CMD_KERN_WRITE = 0xBDCC0003.toInt()

    // Zydis instruction flags
    const val ZYDIS_KIND_CALL = 0x01
    const val ZYDIS_KIND_RET = 0x02
    const val ZYDIS_KIND_JMP = 0x04
    const val ZYDIS_KIND_COND_JMP = 0x08
    const val ZYDIS_KIND_MEM_OP = 0x10
    const val ZYDIS_KIND_RIP_REL = 0x20
    const val ZYDIS_KIND_READ = 0x40
    const val ZYDIS_KIND_WRITE = 0x80

    // Turbo scan engine bits (TSE_*)
    const val TSE_SIMD_COMPARE = 0x00000001
    const val TSE_ALIASING = 0x00000002
    const val TSE_SERVER_RESIDENT = 0x00000004
    const val TSE_SNAPSHOT = 0x00000008
    const val TSE_SNAPSHOT_SEGMENTS = 0x00000010
    const val TSE_SNAPSHOT_CONFIG = 0x00000020
    const val TSE_SNAPSHOT_FIRST = 0x00000040
    const val TSE_SNAPSHOT_PREVIOUS = 0x00000080
    const val TSE_PARALLEL_COMPARE = 0x00000100
    const val TSE_RESCAN_ALIASING = 0x00000200

    // Turbo scan flags (TS_*)
    const val TS_USE_ALIASING = 0x00000001
    const val TS_SERVER_RESIDENT = 0x00000002
    const val TS_SNAPSHOT = 0x00000004
    const val TS_SNAPSHOT_INCLUDE_ZEROS = 0x00000008
    const val TS_SNAPSHOT_SEGMENTS = 0x00000010
    const val TS_SNAPSHOT_KEEP_FIRST = 0x00000020
    const val TS_SNAPSHOT_KEEP_PREVIOUS = 0x00000040
    const val TS_PARALLEL_COMPARE = 0x00000080
    const val TS_RESCAN_ALIASING = 0x00000100

    /**
     * Swap adjacent odd and even bits. Used for status word serialization on the wire.
     */
    fun bitswap32(x: Int): Int {
        return ((x ushr 1) and 0x55555555) or ((x shl 1) and 0xAAAAAAAA.toInt())
    }
}

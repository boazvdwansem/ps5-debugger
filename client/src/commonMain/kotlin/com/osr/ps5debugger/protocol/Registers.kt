package com.osr.ps5debugger.protocol

data class GpRegs(
    val r15: Long,
    val r14: Long,
    val r13: Long,
    val r12: Long,
    val r11: Long,
    val r10: Long,
    val r9: Long,
    val r8: Long,
    val rdi: Long,
    val rsi: Long,
    val rbp: Long,
    val rbx: Long,
    val rdx: Long,
    val rcx: Long,
    val rax: Long,
    val trapno: Int,
    val fs: Int,
    val gs: Int,
    val err: Int,
    val es: Int,
    val ds: Int,
    val rip: Long,
    val cs: Long,
    val rflags: Long,
    val rsp: Long,
    val ss: Long
) {
    companion object {
        fun parse(buffer: BinaryBuffer): GpRegs {
            return GpRegs(
                r15 = buffer.readLong(),
                r14 = buffer.readLong(),
                r13 = buffer.readLong(),
                r12 = buffer.readLong(),
                r11 = buffer.readLong(),
                r10 = buffer.readLong(),
                r9 = buffer.readLong(),
                r8 = buffer.readLong(),
                rdi = buffer.readLong(),
                rsi = buffer.readLong(),
                rbp = buffer.readLong(),
                rbx = buffer.readLong(),
                rdx = buffer.readLong(),
                rcx = buffer.readLong(),
                rax = buffer.readLong(),
                trapno = buffer.readInt(),
                fs = buffer.readUShort(),
                gs = buffer.readUShort(),
                err = buffer.readInt(),
                es = buffer.readUShort(),
                ds = buffer.readUShort(),
                rip = buffer.readLong(),
                cs = buffer.readLong(),
                rflags = buffer.readLong(),
                rsp = buffer.readLong(),
                ss = buffer.readLong()
            )
        }
    }
}

data class DbRegs(
    val dr0: Long,
    val dr1: Long,
    val dr2: Long,
    val dr3: Long,
    val dr4: Long,
    val dr5: Long,
    val dr6: Long,
    val dr7: Long,
    val reserved: LongArray
) {
    companion object {
        fun parse(buffer: BinaryBuffer): DbRegs {
            val dr0 = buffer.readLong()
            val dr1 = buffer.readLong()
            val dr2 = buffer.readLong()
            val dr3 = buffer.readLong()
            val dr4 = buffer.readLong()
            val dr5 = buffer.readLong()
            val dr6 = buffer.readLong()
            val dr7 = buffer.readLong()
            val reserved = LongArray(8) { buffer.readLong() }
            return DbRegs(dr0, dr1, dr2, dr3, dr4, dr5, dr6, dr7, reserved)
        }
    }
}

data class Ps5DebugEvent(
    val lwpid: Int,
    val status: Int,
    val threadName: String,
    val regs: GpRegs,
    val fpu: ByteArray, // raw 832 bytes
    val dbregs: DbRegs
) {
    companion object {
        fun parse(bytes: ByteArray): Ps5DebugEvent {
            val buf = BinaryBuffer(bytes)
            val lwpid = buf.readInt()
            val status = buf.readInt()
            val threadName = buf.readString(40)
            
            // Regs starts at 0x030 (48 decimal)
            buf.position = 48
            val regs = GpRegs.parse(buf)
            
            // FPU starts at 0x0E0 (224 decimal)
            buf.position = 224
            val fpu = buf.readBytes(832)
            
            // DbRegs starts at 0x420 (1056 decimal)
            buf.position = 1056
            val dbregs = DbRegs.parse(buf)
            
            return Ps5DebugEvent(lwpid, status, threadName, regs, fpu, dbregs)
        }
    }
}

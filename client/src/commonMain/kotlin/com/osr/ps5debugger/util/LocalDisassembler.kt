package com.osr.ps5debugger.util

import com.osr.ps5debugger.protocol.Ps5DisasmInstr

object LocalDisassembler {
    fun disassemble(bytes: ByteArray, baseAddr: Long, syncAddresses: Set<Long> = emptySet()): List<Ps5DisasmInstr> {
        val instrs = mutableListOf<Ps5DisasmInstr>()
        var offset = 0
        
        while (offset < bytes.size) {
            val addr = baseAddr + offset
            
            // Priority 1: Known Code Starts (syncAddresses)
            if (syncAddresses.contains(addr)) {
                val len = getInstructionLength(bytes, offset)
                val kind = inferKind(bytes, offset, len)
                instrs.add(createInstr(bytes, offset, addr, len, kind))
                offset += len
                continue
            }

            // Priority 2: Detect Strings
            val strLen = isPrintableString(bytes, offset)
            if (strLen > 0) {
                instrs.add(Ps5DisasmInstr(
                    addr = addr,
                    ripRelTarget = 0,
                    memDisp = 0,
                    length = strLen,
                    kind = 0x100, // DATA_STRING
                    memBaseReg = 0,
                    memIndexReg = 0,
                    memScale = 0,
                    mnemonic = 0,
                    mnemonicLo = 0
                ))
                offset += strLen
                continue
            }

            // Priority 3: Detect Data/Zeroes
            if (bytes[offset] == 0.toByte()) {
                var zeroLen = 1
                // Don't group too many, and stop if we hit a sync address
                while (offset + zeroLen < bytes.size && 
                       bytes[offset + zeroLen] == 0.toByte() && 
                       zeroLen < 16 && 
                       !syncAddresses.contains(baseAddr + offset + zeroLen)) {
                    zeroLen++
                }
                instrs.add(Ps5DisasmInstr(
                    addr = addr,
                    ripRelTarget = 0,
                    memDisp = 0,
                    length = zeroLen,
                    kind = 0x200, // DATA_RAW
                    memBaseReg = 0,
                    memIndexReg = 0,
                    memScale = 0,
                    mnemonic = 0,
                    mnemonicLo = 0
                ))
                offset += zeroLen
                continue
            }
            
            // Default: Disassemble as instruction
            val len = getInstructionLength(bytes, offset)
            val kind = inferKind(bytes, offset, len)
            instrs.add(createInstr(bytes, offset, addr, len, kind))
            offset += len
        }
        return instrs
    }

    private fun createInstr(bytes: ByteArray, offset: Int, addr: Long, len: Int, kind: Int): Ps5DisasmInstr {
        val relTarget = if ((kind and 0x01) != 0 || (kind and 0x04) != 0 || (kind and 0x08) != 0) {
            calculateRelTarget(bytes, offset, addr, len)
        } else 0L

        var memDisp = 0L
        var memBaseReg = 0
        var memIndexReg = 0
        var memScale = 0
        
        if ((kind and 0x10) != 0) { // HAS_MEM_OP
            var off = offset
            // Skip prefixes to find ModRM
            while (off < offset + len) {
                val b = bytes[off].toInt() and 0xFF
                if (b == 0x66 || b == 0x67 || b == 0x2E || b == 0x3E || b == 0x26 || 
                    b == 0x64 || b == 0x65 || b == 0x36 || b == 0xF0 || b == 0xF2 || b == 0xF3 ||
                    (b and 0xF0) == 0x40) off++ else break
            }
            
            val rex = if (off > offset && (bytes[off - 1].toInt() and 0xF0) == 0x40) bytes[off - 1].toInt() and 0xFF else 0
            
            if (off + 1 < bytes.size) {
                val modrm = bytes[off + 1].toInt() and 0xFF
                val mod = (modrm shr 6) and 0x03
                val rm = modrm and 0x07
                
                if (mod != 3) {
                    if (mod == 0 && rm == 5) {
                        if (off + 5 < bytes.size) {
                            memDisp = readIntLE(bytes, off + 2).toLong()
                        }
                    } else if (rm == 4) {
                        if (off + 2 < bytes.size) {
                            val sib = bytes[off + 2].toInt() and 0xFF
                            val base = sib and 0x07
                            val index = (sib shr 3) and 0x07
                            val scale = (sib shr 6) and 0x03
                            memBaseReg = if ((rex and 0x01) != 0) 62 + base + 8 else 62 + base
                            if (index != 4) {
                                memIndexReg = if ((rex and 0x02) != 0) 62 + index + 8 else 62 + index
                                memScale = 1 shl scale
                            }
                        }
                    } else {
                        memBaseReg = if ((rex and 0x01) != 0) 62 + rm + 8 else 62 + rm
                    }
                }
            }
        }

        return Ps5DisasmInstr(
            addr = addr,
            ripRelTarget = relTarget,
            memDisp = memDisp,
            length = len,
            kind = kind,
            memBaseReg = memBaseReg,
            memIndexReg = memIndexReg,
            memScale = memScale,
            mnemonic = 0,
            mnemonicLo = 0
        )
    }

    private fun isPrintableString(bytes: ByteArray, offset: Int): Int {
        if (offset >= bytes.size) return 0
        var len = 0
        while (offset + len < bytes.size) {
            val b = bytes[offset + len].toInt() and 0xFF
            if (b == 0) {
                return if (len >= 4) len + 1 else 0
            }
            if (b < 0x20 || b > 0x7E) {
                return 0
            }
            len++
        }
        return if (len >= 8) len else 0
    }

    private fun readIntLE(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or
               ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
               ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
               ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun calculateRelTarget(bytes: ByteArray, offset: Int, addr: Long, len: Int): Long {
        var off = offset
        // Skip prefixes
        while (off < offset + len) {
            val b = bytes[off].toInt() and 0xFF
            if (b == 0x66 || b == 0x67 || b == 0x2E || b == 0x3E || b == 0x26 || 
                b == 0x64 || b == 0x65 || b == 0x36 || b == 0xF0 || b == 0xF2 || b == 0xF3 ||
                (b and 0xF0) == 0x40) {
                off++
            } else break
        }
        if (off >= bytes.size) return 0L

        val b0 = bytes[off].toInt() and 0xFF
        val b1 = if (off + 1 < bytes.size) bytes[off + 1].toInt() and 0xFF else 0
        
        return when {
            b0 == 0xE8 || b0 == 0xE9 -> {
                if (bytes.size >= off + 5) {
                    val d0 = bytes[off + 1].toInt() and 0xFF
                    val d1 = bytes[off + 2].toInt() and 0xFF
                    val d2 = bytes[off + 3].toInt() and 0xFF
                    val d3 = bytes[off + 4].toInt() and 0xFF
                    val disp = (d3 shl 24) or (d2 shl 16) or (d1 shl 8) or d0
                    addr + len + disp
                } else 0L
            }
            b0 == 0xEB || (b0 in 0x70..0x7F) -> {
                if (bytes.size >= off + 2) {
                    val disp = bytes[off + 1].toInt() // sign extended
                    addr + len + disp
                } else 0L
            }
            b0 == 0x0F && (b1 in 0x80..0x8F) -> {
                if (bytes.size >= off + 6) {
                    val d0 = bytes[off + 2].toInt() and 0xFF
                    val d1 = bytes[off + 3].toInt() and 0xFF
                    val d2 = bytes[off + 4].toInt() and 0xFF
                    val d3 = bytes[off + 5].toInt() and 0xFF
                    val disp = (d3 shl 24) or (d2 shl 16) or (d1 shl 8) or d0
                    addr + len + disp
                } else 0L
            }
            else -> 0L
        }
    }

    private fun getInstructionLength(bytes: ByteArray, start: Int): Int {
        if (start >= bytes.size) return 1
        var offset = start
        
        // Skip prefixes
        while (offset < bytes.size) {
            val b = bytes[offset].toInt() and 0xFF
            if (b == 0x66 || b == 0x67 || b == 0x2E || b == 0x3E || b == 0x26 || 
                b == 0x64 || b == 0x65 || b == 0x36 || b == 0xF0 || b == 0xF2 || b == 0xF3 ||
                (b >= 0x40 && b <= 0x4F)) {
                offset++
            } else break
        }
        
        if (offset >= bytes.size) return (offset - start).coerceAtLeast(1)
        
        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = if (offset + 1 < bytes.size) bytes[offset + 1].toInt() and 0xFF else 0
        
        // Basic length map for common instructions
        val len = when (b0) {
            0x90, 0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57, // NOP, PUSH
            0x58, 0x59, 0x5A, 0x5B, 0x5C, 0x5D, 0x5E, 0x5F, // POP
            0x06, 0x07, 0x0E, 0x16, 0x17, 0x1E, 0x1F, // Seg PUSH/POP
            0x98, 0x99, 0x9C, 0x9D, 0x9E, 0x9F,
            0xC3, 0xCB, 0xC9, 0xCC -> 1 // RET, LEAVE, INT3
            
            0xEB, 0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7A, 0x7B, 0x7C, 0x7D, 0x7E, 0x7F,
            0xB0, 0xB1, 0xB2, 0xB3, 0xB4, 0xB5, 0xB6, 0xB7 -> 2 // JMP rel8, Jcc rel8, MOV reg8, imm8
            
            0xB8, 0xB9, 0xBA, 0xBB, 0xBC, 0xBD, 0xBE, 0xBF -> 5 // MOV reg32, imm32
            
            0xA8, 0x04, 0x0C, 0x14, 0x1C, 0x24, 0x2C, 0x34, 0x3C -> 2 // imm8 ops
            
            0xE8, 0xE9 -> 5 // CALL rel32, JMP rel32
            
            0x0F -> {
                if (b1 in 0x80..0x8F) 6 // Jcc rel32
                else if (b1 == 0x05 || b1 == 0x31 || b1 == 0xA2) 2 // SYSCALL, RDTSC, CPUID
                else 3 // Default 0F extension
            }
            
            0x8D, 0x89, 0x8B, 0x39, 0x3B, 0x85, 0x01, 0x03, 0x21, 0x23, 0x31, 0x33,
            0x88, 0x8A, 0x38, 0x3A, 0x84, 0x00, 0x02, 0x20, 0x22, 0x30, 0x32 -> {
                // ModRM based
                1 + getModRMLength(bytes, offset + 1)
            }
            
            0x81, 0x83, 0xC6, 0xC7 -> {
                val modrmLen = getModRMLength(bytes, offset + 1)
                val immLen = if (b0 == 0x83) 1 else (if (b0 == 0xC6) 1 else 4)
                1 + modrmLen + immLen
            }
            
            else -> 1 // Default to 1 byte if unknown
        }
        return (offset - start) + len
    }

    private fun getModRMLength(bytes: ByteArray, pos: Int): Int {
        if (pos >= bytes.size) return 0
        val modrm = bytes[pos].toInt() and 0xFF
        val mod = (modrm shr 6) and 0x03
        val rm = modrm and 0x07
        
        var len = 1
        if (mod != 3 && rm == 4) { // SIB byte
            len++
            val sib = if (pos + 1 < bytes.size) bytes[pos + 1].toInt() and 0xFF else 0
            val base = sib and 0x07
            if (mod == 0 && base == 5) len += 4 // disp32
        }
        
        when (mod) {
            0 -> if (rm == 5) len += 4 // disp32
            1 -> len += 1 // disp8
            2 -> len += 4 // disp32
        }
        return len
    }

    private fun inferKind(bytes: ByteArray, start: Int, len: Int): Int {
        var offset = start
        while (offset < start + len) {
            val b = bytes[offset].toInt() and 0xFF
            
            if (b == 0x66 || b == 0x67 || b == 0x2E || b == 0x3E || b == 0x26 || 
                b == 0x64 || b == 0x65 || b == 0x36 || b == 0xF0 || b == 0xF2 || b == 0xF3 ||
                (b and 0xF0) == 0x40) {
                offset++
            } else break
        }
        if (offset >= start + len) return 0
        val b0 = bytes[offset].toInt() and 0xFF
        
        var kind = 0
        if (b0 == 0xE8 || (b0 == 0xFF && ((bytes.getOrNull(offset + 1)?.toInt() ?: 0) shr 3) and 0x07 == 2)) kind = kind or 0x01 // CALL
        if (b0 == 0xC3 || b0 == 0xCB || b0 == 0xC2 || b0 == 0xCA) kind = kind or 0x02 // RET
        if (b0 == 0xEB || b0 == 0xE9 || (b0 == 0xFF && ((bytes.getOrNull(offset + 1)?.toInt() ?: 0) shr 3) and 0x07 == 4)) kind = kind or 0x04 // JMP
        if ((b0 in 0x70..0x7F) || (b0 == 0x0F && (bytes.getOrNull(offset + 1)?.toInt() ?: 0) in 0x80..0x8F)) kind = kind or 0x08 // COND_JMP
        
        // Detect memory operand and RIP-relative
        val modrmSet = setOf(
            0x8D, 0x8B, 0x89, 0x39, 0x3B, 0x85, 0x01, 0x03, 0x21, 0x23, 0x31, 0x33,
            0x80, 0x81, 0x83, 0xC6, 0xC7, 0xF6, 0xF7, 0xFE, 0xFF
        )
        
        if (b0 in modrmSet && offset + 1 < start + len) {
            val modrm = bytes[offset + 1].toInt() and 0xFF
            val mod = (modrm shr 6) and 0x03
            val rm = modrm and 0x07
            if (mod != 3) {
                kind = kind or 0x10 // HAS_MEM_OP
                if (mod == 0 && rm == 5) {
                    kind = kind or 0x20 // IS_RIP_REL
                }
            }
            
            // Assume most of these write if they have a memory op and aren't CMP/TEST
            // LEA (0x8D) does NOT write to memory, it writes to a register.
            if (b0 == 0x89 || b0 == 0x01 || b0 == 0x21 || b0 == 0x31 || b0 == 0xC6 || b0 == 0xC7) {
                kind = kind or 0x80 // IS_WRITE
            } else {
                kind = kind or 0x40 // IS_READ
            }
        }

        return kind
    }
}

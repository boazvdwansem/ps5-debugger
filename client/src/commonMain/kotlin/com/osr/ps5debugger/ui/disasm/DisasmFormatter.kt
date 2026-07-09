package com.osr.ps5debugger.ui.disasm

import com.osr.ps5debugger.protocol.Ps5DisasmInstr

object DisasmFormatter {
    val regNames = setOf(
        "rax", "rcx", "rdx", "rbx", "rsp", "rbp", "rsi", "rdi", "r8", "r9", "r10", "r11", "r12", "r13", "r14", "r15", "rip",
        "eax", "ecx", "edx", "ebx", "esp", "ebp", "esi", "edi", "r8d", "r9d", "r10d", "r11d", "r12d", "r13d", "r14d", "r15d",
        "ax", "cx", "dx", "bx", "sp", "bp", "si", "di",
        "al", "cl", "dl", "bl", "ah", "ch", "dh", "bh",
        "spl", "bpl", "sil", "dil", "r8b", "r9b", "r10b", "r11b", "r12b", "r13b", "r14b", "r15b",
        "xmm0", "xmm1", "xmm2", "xmm3", "xmm4", "xmm5", "xmm6", "xmm7", "xmm8", "xmm9", "xmm10", "xmm11", "xmm12", "xmm13", "xmm14", "xmm15"
    )

    private val commonGpInstructions = setOf(
        "MOV", "ADD", "SUB", "CMP", "AND", "OR", "XOR", "SHL", "SHR", "SAR", "LEA", "TEST", "PUSH", "POP", "CALL", "RET", "JMP",
        "JE", "JNE", "JZ", "JNZ", "JS", "JNS", "JG", "JGE", "JL", "JLE", "JA", "JAE", "JB", "JBE", "NOP", "IMUL", "IDIV", "INC", "DEC",
        "MOVSX", "MOVZX", "SHLD", "SHRD", "BT", "BTC", "BTR", "BTS", "BSF", "BSR", "BSWAP", "NEG", "NOT", "ADC", "SBB"
    )

    private fun getOpcodeOffset(bytes: ByteArray): Int {
        var off = 0
        while (off < bytes.size) {
            val b = bytes[off].toInt() and 0xFF
            if (b == 0x66 || b == 0x67 || b == 0x2E || b == 0x3E || b == 0x26 || 
                b == 0x64 || b == 0x65 || b == 0x36 || b == 0xF0 || b == 0xF2 || b == 0xF3 ||
                (b and 0xF0) == 0x40) {
                off++
            } else {
                break
            }
        }
        return off
    }

    fun getMnemonic(instr: Ps5DisasmInstr, bytes: ByteArray = byteArrayOf()): String {
        if (instr.mnemonic != 0) {
            val id = instr.mnemonic
            if (id >= 0 && id < ZydisMnemonics.NAMES.size) {
                return ZydisMnemonics.NAMES[id]
            }
            return "INVALID"
        }
        
        if (bytes.isNotEmpty()) {
            val off = getOpcodeOffset(bytes)
            val b0 = bytes.getOrNull(off)?.toInt()?.let { it and 0xFF } ?: 0
            val b1 = bytes.getOrNull(off + 1)?.toInt()?.let { it and 0xFF } ?: 0
            
            if (instr.isCall) return "CALL"
            if (instr.isRet) return "RET"
            if (instr.isJmp) return "JMP"
            if (instr.isCondJmp) {
                val cond = if (b0 == 0x0F) {
                    b1 and 0x0F
                } else if (b0 in 0x70..0x7F) {
                    b0 and 0x0F
                } else {
                    -1
                }
                return when (cond) {
                    0 -> "JO"
                    1 -> "JNO"
                    2 -> "JB"
                    3 -> "JAE"
                    4 -> "JE"
                    5 -> "JNE"
                    6 -> "JBE"
                    7 -> "JA"
                    8 -> "JS"
                    9 -> "JNS"
                    10 -> "JP"
                    11 -> "JNP"
                    12 -> "JL"
                    13 -> "JGE"
                    14 -> "JLE"
                    15 -> "JG"
                    else -> "JCC"
                }
            }
            
            when (b0) {
                0x90 -> return "NOP"
                in 0x50..0x57 -> return "PUSH"
                in 0x58..0x5F -> return "POP"
                0x0F -> {
                    if (b1 == 0x05) return "SYSCALL"
                    if (b1 == 0xA2) return "CPUID"
                }
                0x8D -> return "LEA"
            }
        }
        
        return when (instr.mnemonicLo) {
            9 -> if (instr.isWrite) "SUB" else "ADD"
            180 -> "MOV"
            140 -> "LEA"
            153 -> "PUSH"
            97 -> "POP"
            179 -> "RET"
            55 -> "JMP"
            68 -> "JZ"
            63 -> "JNZ"
            71 -> "CALL"
            27 -> "TEST"
            235 -> "XOR"
            31 -> "AND"
            107 -> "CMP"
            219 -> "MOVUPS"
            182 -> "MOVAPS"
            149 -> "DEC"
            25 -> "INC"
            231 -> "NOP"
            else -> {
                val matches = ZydisMnemonics.NAMES.filterIndexed { idx, _ -> idx % 256 == instr.mnemonicLo }
                if (matches.isNotEmpty()) {
                    val gp = matches.firstOrNull { it in commonGpInstructions }
                        ?: matches.firstOrNull { !it.startsWith("V") && !it.startsWith("P") && !it.startsWith("K") && !it.startsWith("F") }
                        ?: matches.first()
                    gp
                } else {
                    "INSTR_${instr.mnemonicLo}"
                }
            }
        }
    }

    fun formatOperands(instr: Ps5DisasmInstr, bytes: ByteArray = byteArrayOf()): String {
        val ops = mutableListOf<String>()
        
        if (instr.hasMemOp) {
            val base = getRegisterName(instr.memBaseReg)
            val index = getRegisterName(instr.memIndexReg)
            val scale = instr.memScale
            val disp = instr.memDisp
            
            val memStr = StringBuilder("[")
            var hasPrev = false
            if (instr.isRipRel) {
                memStr.append("rip")
                hasPrev = true
            } else if (base.isNotEmpty()) {
                memStr.append(base)
                hasPrev = true
            }
            if (index.isNotEmpty()) {
                if (hasPrev) memStr.append(" + ")
                memStr.append(index)
                if (scale > 1) {
                    memStr.append("*").append(scale)
                }
                hasPrev = true
            }
            if (disp != 0L) {
                if (hasPrev) {
                    if (disp > 0) memStr.append(" + ") else memStr.append(" - ")
                    memStr.append("0x").append(kotlin.math.abs(disp).toString(16).uppercase())
                } else {
                    memStr.append("0x").append(disp.toString(16).uppercase())
                }
            } else if (!hasPrev) {
                memStr.append("0")
            }
            memStr.append("]")
            
            if (bytes.size >= 2) {
                var off = 0
                var rex = 0
                while (off < bytes.size && (bytes[off].toInt() and 0xF0) == 0x40) {
                    rex = bytes[off].toInt() and 0xFF
                    off++
                }
                if (off + 1 < bytes.size) {
                    val modrm = bytes[off + 1].toInt() and 0xFF
                    val regId = ((modrm shr 3) and 0x07) or (if ((rex and 0x04) != 0) 8 else 0)
                    val rexW = (rex and 0x08) != 0
                    val regNameId = if (rexW) 62 + regId else 44 + regId
                    val regName = getRegisterName(regNameId)
                    
                    if (regName.isNotEmpty()) {
                        if (instr.isWrite) {
                            ops.add(memStr.toString())
                            ops.add(regName)
                        } else {
                            ops.add(regName)
                            ops.add(memStr.toString())
                        }
                    } else {
                        ops.add(memStr.toString())
                    }
                } else {
                    ops.add(memStr.toString())
                }
            } else {
                ops.add(memStr.toString())
            }
        } else {
            if (bytes.isNotEmpty()) {
                var off = 0
                var rex = 0
                while (off < bytes.size && (bytes[off].toInt() and 0xF0) == 0x40) {
                    rex = bytes[off].toInt() and 0xFF
                    off++
                }
                
                if (off >= bytes.size) return ""
                val opc = bytes[off].toInt() and 0xFF
                if (opc in 0x50..0x57) {
                    val regId = (opc and 0x07) or (if ((rex and 0x01) != 0) 8 else 0)
                    ops.add(getRegisterName(62 + regId))
                } else if (opc in 0x58..0x5F) {
                    val regId = (opc and 0x07) or (if ((rex and 0x01) != 0) 8 else 0)
                    ops.add(getRegisterName(62 + regId))
                } else if (off + 1 < bytes.size) {
                    val modrm = bytes[off + 1].toInt() and 0xFF
                    if ((modrm and 0xC0) == 0xC0) {
                        val regId = ((modrm shr 3) and 0x07) or (if ((rex and 0x04) != 0) 8 else 0)
                        val rmId = (modrm and 0x07) or (if ((rex and 0x01) != 0) 8 else 0)
                        
                        val is64 = (rex and 0x08) != 0
                        val baseId = if (is64) 62 else 44
                        val regName = getRegisterName(baseId + regId)
                        val rmName = getRegisterName(baseId + rmId)
                        
                        if (regName.isNotEmpty() && rmName.isNotEmpty()) {
                            if ((opc and 0x02) != 0) {
                                ops.add(regName)
                                ops.add(rmName)
                            } else {
                                ops.add(rmName)
                                ops.add(regName)
                            }
                        }
                    }
                }
            }
            
            if (ops.isEmpty()) {
                val jumpTarget = getJumpTarget(instr, bytes)
                if (jumpTarget != 0L) {
                    ops.add(com.osr.ps5debugger.di.AppContainer.getSymbolNameForTarget(jumpTarget, instr.isCall))
                } else if (instr.ripRelTarget != 0L) {
                    ops.add(com.osr.ps5debugger.di.AppContainer.getSymbolNameForTarget(instr.ripRelTarget, instr.isCall))
                }
            }
        }
        
        return ops.joinToString(", ")
    }

    fun getJumpTarget(instr: Ps5DisasmInstr, bytes: ByteArray): Long {
        if (bytes.isEmpty()) return 0L
        val off = getOpcodeOffset(bytes)
        val b0 = bytes.getOrNull(off)?.toInt()?.let { it and 0xFF } ?: 0
        val b1 = bytes.getOrNull(off + 1)?.toInt()?.let { it and 0xFF } ?: 0
        
        val addr = instr.addr
        val length = instr.length
        
        when {
            b0 == 0xE8 || b0 == 0xE9 -> {
                if (bytes.size >= off + 5) {
                    val d0 = bytes[off + 1].toInt() and 0xFF
                    val d1 = bytes[off + 2].toInt() and 0xFF
                    val d2 = bytes[off + 3].toInt() and 0xFF
                    val d3 = bytes[off + 4].toInt() and 0xFF
                    val disp = (d3 shl 24) or (d2 shl 16) or (d1 shl 8) or d0
                    return addr + length + disp
                }
            }
            b0 == 0xEB || b0 in 0x70..0x7F -> {
                if (bytes.size >= off + 2) {
                    val disp = bytes[off + 1].toInt()
                    return addr + length + disp
                }
            }
            b0 == 0x0F && b1 in 0x80..0x8F -> {
                if (bytes.size >= off + 6) {
                    val d0 = bytes[off + 2].toInt() and 0xFF
                    val d1 = bytes[off + 3].toInt() and 0xFF
                    val d2 = bytes[off + 4].toInt() and 0xFF
                    val d3 = bytes[off + 5].toInt() and 0xFF
                    val disp = (d3 shl 24) or (d2 shl 16) or (d1 shl 8) or d0
                    return addr + length + disp
                }
            }
        }
        return 0L
    }

    fun getInfoText(instr: Ps5DisasmInstr, bytes: ByteArray = byteArrayOf()): String {
        val jumpTarget = if (bytes.isNotEmpty()) getJumpTarget(instr, bytes) else 0L
        return when {
            jumpTarget != 0L -> "target: " + com.osr.ps5debugger.di.AppContainer.getSymbolNameForTarget(jumpTarget, instr.isCall)
            instr.isRipRel && instr.ripRelTarget != 0L -> "target: " + com.osr.ps5debugger.di.AppContainer.getSymbolNameForTarget(instr.ripRelTarget, instr.isCall)
            instr.ripRelTarget > 0x10000 -> "target: " + com.osr.ps5debugger.di.AppContainer.getSymbolNameForTarget(instr.ripRelTarget, instr.isCall)
            instr.isCall -> "subroutine call"
            instr.isRet -> "return from subroutine"
            else -> ""
        }
    }

    fun getRegisterName(reg: Int): String {
        return when (reg) {
            4 -> "al"; 5 -> "cl"; 6 -> "dl"; 7 -> "bl"; 8 -> "ah"; 9 -> "ch"; 10 -> "dh"; 11 -> "bh"
            12 -> "spl"; 13 -> "bpl"; 14 -> "sil"; 15 -> "dil"
            16 -> "r8b"; 17 -> "r9b"; 18 -> "r10b"; 19 -> "r11b"; 20 -> "r12b"; 21 -> "r13b"; 22 -> "r14b"; 23 -> "r15b"
            26 -> "ax"; 27 -> "cx"; 28 -> "dx"; 29 -> "bx"; 30 -> "sp"; 31 -> "bp"; 32 -> "si"; 33 -> "di"
            34 -> "r8w"; 35 -> "r9w"; 36 -> "r10w"; 37 -> "r11w"; 38 -> "r12w"; 39 -> "r13w"; 40 -> "r14w"; 41 -> "r15w"
            44 -> "eax"; 45 -> "ecx"; 46 -> "edx"; 47 -> "ebx"; 48 -> "esp"; 49 -> "ebp"; 50 -> "esi"; 51 -> "edi"
            52 -> "r8d"; 53 -> "r9d"; 54 -> "r10d"; 55 -> "r11d"; 56 -> "r12d"; 57 -> "r13d"; 58 -> "r14d"; 59 -> "r15d"
            62 -> "rax"; 63 -> "rcx"; 64 -> "rdx"; 65 -> "rbx"; 66 -> "rsp"; 67 -> "rbp"; 68 -> "rsi"; 69 -> "rdi"
            70 -> "r8"; 71 -> "r9"; 72 -> "r10"; 73 -> "r11"; 74 -> "r12"; 75 -> "r13"; 76 -> "r14"; 77 -> "r15"
            222 -> "rip"
            103 -> "xmm0"; 104 -> "xmm1"; 105 -> "xmm2"; 106 -> "xmm3"; 107 -> "xmm4"; 108 -> "xmm5"; 109 -> "xmm6"; 110 -> "xmm7"
            111 -> "xmm8"; 112 -> "xmm9"; 113 -> "xmm10"; 114 -> "xmm11"; 115 -> "xmm12"; 116 -> "xmm13"; 117 -> "xmm14"; 118 -> "xmm15"
            0 -> ""
            else -> ""
        }
    }
}

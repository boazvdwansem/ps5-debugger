package com.osr.ps5debugger.ui.disasm

import com.osr.ps5debugger.protocol.Ps5DisasmInstr

object DisasmFormatter {
    val regNames = setOf(
        "rax", "rcx", "rdx", "rbx", "rsp", "rbp", "rsi", "rdi", "r8", "r9", "r10", "r11", "r12", "r13", "r14", "r15", "rip",
        "eax", "ecx", "edx", "ebx", "esp", "ebp", "esi", "edi", "r8d", "r9d", "r10d", "r11d", "r12d", "r13d", "r14d", "r15d",
        "ax", "cx", "dx", "bx", "sp", "bp", "si", "di",
        "al", "cl", "dl", "bl", "ah", "ch", "dh", "bh"
    )

    fun getMnemonic(instr: Ps5DisasmInstr): String {
        return when (instr.mnemonicLo) {
            9 -> "ADD"
            31 -> "AND"
            149 -> "DEC"
            25 -> "INC"
            47 -> "JB"
            48 -> "JBE"
            49 -> "JCXZ"
            50 -> "JECXZ"
            53 -> "JL"
            54 -> "JLE"
            55 -> "JMP"
            56 -> "JNB"
            57 -> "JNBE"
            58 -> "JNL"
            59 -> "JNLE"
            60 -> "JNO"
            61 -> "JNP"
            62 -> "JNS"
            63 -> "JNZ"
            64 -> "JO"
            65 -> "JP"
            66 -> "JRCXZ"
            67 -> "JS"
            68 -> "JZ"
            71 -> "CALL"
            107 -> "CMP"
            140 -> "LEA"
            180 -> "MOV"
            231 -> "NOP"
            233 -> "OR"
            97 -> "POP"
            153 -> "PUSH"
            179 -> "RET"
            234 -> "SHL"
            237 -> "SHR"
            27 -> "TEST"
            235 -> "XOR"
            -1 -> "db"
            else -> "INSTR_${instr.mnemonicLo}"
        }
    }

    fun formatOperands(instr: Ps5DisasmInstr): String {
        if (instr.mnemonicLo == -1) {
            return String.format("0x%02X", instr.memDisp)
        }
        val ops = mutableListOf<String>()
        
        if (instr.isRipRel && instr.ripRelTarget != 0L) {
            ops.add(String.format("[rip + 0x%X]", instr.memDisp))
        } else if (instr.hasMemOp) {
            val base = getRegisterName(instr.memBaseReg)
            val index = getRegisterName(instr.memIndexReg)
            val scale = instr.memScale
            val disp = instr.memDisp
            
            val mem = StringBuilder("[")
            var hasPrev = false
            if (base.isNotEmpty()) {
                mem.append(base)
                hasPrev = true
            }
            if (index.isNotEmpty()) {
                if (hasPrev) mem.append(" + ")
                mem.append(index)
                if (scale > 1) {
                    mem.append("*").append(scale)
                }
                hasPrev = true
            }
            if (disp != 0L) {
                if (hasPrev) {
                    if (disp > 0) mem.append(" + ") else mem.append(" - ")
                    mem.append(String.format("0x%X", kotlin.math.abs(disp)))
                } else {
                    mem.append(String.format("0x%X", disp))
                }
            } else if (!hasPrev) {
                mem.append("0")
            }
            mem.append("]")
            ops.add(mem.toString())
        }
        
        if (instr.ripRelTarget != 0L && !instr.isRipRel) {
            ops.add(String.format("0x%X", instr.ripRelTarget))
        }
        
        return ops.joinToString(", ")
    }

    fun getInfoText(instr: Ps5DisasmInstr): String {
        return when {
            instr.isRipRel && instr.ripRelTarget != 0L -> String.format("target: 0x%X", instr.memDisp)
            instr.ripRelTarget != 0L -> String.format("target: 0x%X", instr.ripRelTarget)
            instr.isCall -> "subroutine call"
            instr.isRet -> "return from subroutine"
            else -> ""
        }
    }

    fun getRegisterName(reg: Int): String {
        return when (reg) {
            37 -> "eax"
            38 -> "ecx"
            39 -> "edx"
            40 -> "ebx"
            41 -> "esp"
            42 -> "ebp"
            43 -> "esi"
            44 -> "edi"
            53 -> "rax"
            54 -> "rcx"
            55 -> "rdx"
            56 -> "rbx"
            57 -> "rsp"
            58 -> "rbp"
            59 -> "rsi"
            60 -> "rdi"
            61 -> "r8"
            62 -> "r9"
            63 -> "r10"
            64 -> "r11"
            65 -> "r12"
            66 -> "r13"
            67 -> "r14"
            68 -> "r15"
            197 -> "rip"
            0 -> ""
            else -> "reg_$reg"
        }
    }
}

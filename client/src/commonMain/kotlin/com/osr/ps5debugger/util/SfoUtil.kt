package com.osr.ps5debugger.util

object SfoUtil {
    fun getTitleName(bytes: ByteArray): String? {
        try {
            if (bytes.size < 20) return null
            // Check magic \x00PSF
            if (bytes[0] != 0.toByte() || bytes[1] != 'P'.code.toByte() || bytes[2] != 'S'.code.toByte() || bytes[3] != 'F'.code.toByte()) return null
            
            val keyOffset = readIntLE(bytes, 0x08)
            val dataOffset = readIntLE(bytes, 0x0C)
            val count = readIntLE(bytes, 0x10)
            
            for (i in 0 until count) {
                val entryOffset = 0x14 + i * 16
                val nameOffset = keyOffset + (readShortLE(bytes, entryOffset).toInt() and 0xFFFF)
                // val dataFmt = bytes[entryOffset + 2].toInt()
                val dataLen = readIntLE(bytes, entryOffset + 4)
                // val dataMaxLen = readIntLE(bytes, entryOffset + 8)
                val dataOff = dataOffset + readIntLE(bytes, entryOffset + 12)
                
                val keyName = getString(bytes, nameOffset)
                if (keyName == "TITLE") {
                    return getString(bytes, dataOff, dataLen).trimEnd { it <= ' ' || it == '\u0000' }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun getString(bytes: ByteArray, offset: Int, maxLen: Int = 256): String {
        var len = 0
        while (offset + len < bytes.size && len < maxLen && bytes[offset + len] != 0.toByte()) {
            len++
        }
        return bytes.decodeToString(offset, offset + len)
    }

    private fun readShortLE(bytes: ByteArray, offset: Int): Short {
        return ((bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)).toShort()
    }

    private fun readIntLE(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
               ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
               ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
               ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }
}

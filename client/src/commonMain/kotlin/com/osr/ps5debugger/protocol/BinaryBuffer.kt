package com.osr.ps5debugger.protocol

import kotlin.experimental.and

class BinaryBuffer(val bytes: ByteArray) {
    var position: Int = 0

    constructor(size: Int) : this(ByteArray(size))

    fun hasRemaining(): Boolean = position < bytes.size
    fun remaining(): Int = bytes.size - position

    fun readByte(): Byte {
        return bytes[position++]
    }

    fun readUByte(): Int {
        return readByte().toInt() and 0xFF
    }

    fun readShort(): Short {
        val b1 = bytes[position++].toInt() and 0xFF
        val b2 = bytes[position++].toInt() and 0xFF
        return ((b2 shl 8) or b1).toShort()
    }

    fun readUShort(): Int {
        return readShort().toInt() and 0xFFFF
    }

    fun readInt(): Int {
        val b1 = bytes[position++].toInt() and 0xFF
        val b2 = bytes[position++].toInt() and 0xFF
        val b3 = bytes[position++].toInt() and 0xFF
        val b4 = bytes[position++].toInt() and 0xFF
        return (b4 shl 24) or (b3 shl 16) or (b2 shl 8) or b1
    }

    fun readUInt(): Long {
        return readInt().toLong() and 0xFFFFFFFFL
    }

    fun readLong(): Long {
        val b1 = bytes[position++].toLong() and 0xFF
        val b2 = bytes[position++].toLong() and 0xFF
        val b3 = bytes[position++].toLong() and 0xFF
        val b4 = bytes[position++].toLong() and 0xFF
        val b5 = bytes[position++].toLong() and 0xFF
        val b6 = bytes[position++].toLong() and 0xFF
        val b7 = bytes[position++].toLong() and 0xFF
        val b8 = bytes[position++].toLong() and 0xFF
        return (b8 shl 56) or (b7 shl 48) or (b6 shl 40) or (b5 shl 32) or
               (b4 shl 24) or (b3 shl 16) or (b2 shl 8) or b1
    }

    fun readFloat(): Float {
        return Float.fromBits(readInt())
    }

    fun readDouble(): Double {
        return Double.fromBits(readLong())
    }

    fun readString(length: Int): String {
        var len = 0
        while (len < length && position + len < bytes.size && bytes[position + len] != 0.toByte()) {
            len++
        }
        val s = String(bytes, position, len, Charsets.UTF_8)
        position += length
        return s
    }

    fun readBytes(length: Int): ByteArray {
        val dest = ByteArray(length)
        System.arraycopy(bytes, position, dest, 0, length)
        position += length
        return dest
    }

    fun writeByte(v: Byte) {
        bytes[position++] = v
    }

    fun writeShort(v: Short) {
        bytes[position++] = (v.toInt() and 0xFF).toByte()
        bytes[position++] = ((v.toInt() shr 8) and 0xFF).toByte()
    }

    fun writeInt(v: Int) {
        bytes[position++] = (v and 0xFF).toByte()
        bytes[position++] = ((v shr 8) and 0xFF).toByte()
        bytes[position++] = ((v shr 16) and 0xFF).toByte()
        bytes[position++] = ((v shr 24) and 0xFF).toByte()
    }

    fun writeLong(v: Long) {
        bytes[position++] = (v and 0xFF).toByte()
        bytes[position++] = ((v shr 8) and 0xFF).toByte()
        bytes[position++] = ((v shr 16) and 0xFF).toByte()
        bytes[position++] = ((v shr 24) and 0xFF).toByte()
        bytes[position++] = ((v shr 32) and 0xFF).toByte()
        bytes[position++] = ((v shr 40) and 0xFF).toByte()
        bytes[position++] = ((v shr 48) and 0xFF).toByte()
        bytes[position++] = ((v shr 56) and 0xFF).toByte()
    }

    fun writeFloat(v: Float) {
        writeInt(v.toRawBits())
    }

    fun writeDouble(v: Double) {
        writeLong(v.toRawBits())
    }

    fun writeString(s: String, maxLen: Int) {
        val sBytes = s.toByteArray(Charsets.UTF_8)
        val copyLen = minOf(sBytes.size, maxLen - 1)
        System.arraycopy(sBytes, 0, bytes, position, copyLen)
        for (i in copyLen until maxLen) {
            bytes[position + i] = 0
        }
        position += maxLen
    }

    fun writeBytes(src: ByteArray) {
        System.arraycopy(src, 0, bytes, position, src.size)
        position += src.size
    }
}

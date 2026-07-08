package com.osr.ps5debugger.ui.watchlist

import com.osr.ps5debugger.domain.model.WatchItem
import com.osr.ps5debugger.protocol.BinaryBuffer

val typeOptions = listOf("Byte", "Int16", "Int32", "Int64", "Float", "Double", "String", "ByteArray")
const val WatchStringMaxBytes = 256

fun typeSizeBytes(item: WatchItem): Int = item.byteLength?.coerceAtLeast(1) ?: when (item.type) {
    "Byte" -> 1
    "Int16" -> 2
    "Int32" -> 4
    "Int64" -> 8
    "Float" -> 4
    "Double" -> 8
    "String" -> 32
    else -> 4
}

fun parseValueBytes(bytes: ByteArray, type: String): String {
    if (bytes.isEmpty()) return "??"
    val buf = BinaryBuffer(bytes)
    return when (type) {
        "Byte" -> buf.readByte().toString()
        "Int16" -> buf.readShort().toString()
        "Int32" -> buf.readInt().toString()
        "Int64" -> buf.readLong().toString()
        "Float" -> buf.readFloat().toString()
        "Double" -> buf.readDouble().toString()
        "String" -> bytes.takeWhile { it != 0.toByte() }.map { it.toInt().and(0xFF).toChar() }.joinToString("")
        "ByteArray" -> bytes.joinToString(" ") { it.toUByte().toString(16).padStart(2, '0').uppercase() }
        else -> "??"
    }
}

fun valueToBytes(valueStr: String, item: WatchItem): ByteArray? {
    return try {
        val size = typeSizeBytes(item)
        val bytes = ByteArray(size)
        val buf = BinaryBuffer(bytes)
        when (item.type) {
            "Byte" -> buf.writeByte(valueStr.toByte())
            "Int16" -> buf.writeShort(valueStr.toShort())
            "Int32" -> buf.writeInt(valueStr.toInt())
            "Int64" -> buf.writeLong(valueStr.toLong())
            "Float" -> buf.writeFloat(valueStr.toFloat())
            "Double" -> buf.writeDouble(valueStr.toDouble())
            "String" -> {
                val strBytes = valueStr.encodeToByteArray()
                System.arraycopy(strBytes, 0, bytes, 0, minOf(strBytes.size, size))
            }
            "ByteArray" -> {
                val hexBytes = valueStr.split(" ").map { it.toInt(16).toByte() }.toByteArray()
                System.arraycopy(hexBytes, 0, bytes, 0, minOf(hexBytes.size, size))
            }
        }
        bytes
    } catch (_: Exception) {
        null
    }
}

fun watchListToJson(items: List<WatchItem>): String = buildString {
    appendLine("[")
    items.forEachIndexed { index, item ->
        append("  {")
        append("\"label\":\"").append(jsonEscape(item.label)).append("\",")
        append("\"address\":").append(item.address).append(",")
        append("\"addressHex\":\"0x").append(item.address.toString(16).uppercase()).append("\",")
        append("\"type\":\"").append(jsonEscape(item.type)).append("\",")
        append("\"value\":\"").append(jsonEscape(item.valueStr)).append("\",")
        append("\"isFrozen\":").append(item.isFrozen).append(",")
        append("\"comment\":\"").append(jsonEscape(item.comment)).append("\",")
        append("\"byteLength\":").append(item.byteLength?.toString() ?: "null")
        append("}")
        if (index != items.lastIndex) append(",")
        appendLine()
    }
    appendLine("]")
}

fun watchListFromJson(json: String): List<WatchItem> {
    return extractJsonObjects(json).mapNotNull { obj ->
        val label = readJsonStringField(obj, "label") ?: return@mapNotNull null
        val address = readJsonRawField(obj, "address")?.toLongOrNull()
            ?: readJsonStringField(obj, "addressHex")?.removePrefix("0x")?.removePrefix("0X")?.toLongOrNull(16)
            ?: return@mapNotNull null
        val type = readJsonStringField(obj, "type") ?: "Int32"
        val value = readJsonStringField(obj, "value") ?: "??"
        val frozen = readJsonRawField(obj, "isFrozen")?.equals("true", ignoreCase = true) == true
        val comment = readJsonStringField(obj, "comment") ?: ""
        val byteLength = readJsonRawField(obj, "byteLength")?.takeUnless { it == "null" }?.toIntOrNull()

        WatchItem(label, address, type, value, frozen, comment, byteLength)
    }
}

private fun jsonEscape(value: String): String = buildString {
    value.forEach { ch ->
        when (ch) {
            '\"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(ch)
        }
    }
}

private fun extractJsonObjects(json: String): List<String> {
    val result = mutableListOf<String>()
    var i = 0
    while (i < json.length) {
        if (json[i] == '{') {
            val start = i
            var depth = 1
            i++
            while (i < json.length && depth > 0) {
                if (json[i] == '{') depth++
                else if (json[i] == '}') depth--
                i++
            }
            result.add(json.substring(start, i))
        } else i++
    }
    return result
}

private fun readJsonStringField(json: String, field: String): String? {
    val key = "\"$field\""
    val idx = json.indexOf(key)
    if (idx == -1) return null
    val startQuote = json.indexOf('\"', idx + key.length)
    if (startQuote == -1) return null
    val endQuote = json.indexOf('\"', startQuote + 1)
    if (endQuote == -1) return null
    return json.substring(startQuote + 1, endQuote)
}

private fun readJsonRawField(json: String, field: String): String? {
    val key = "\"$field\""
    val idx = json.indexOf(key)
    if (idx == -1) return null
    val colon = json.indexOf(':', idx + key.length)
    if (colon == -1) return null
    var start = colon + 1
    while (start < json.length && (json[start].isWhitespace())) start++
    var end = start
    while (end < json.length && json[end] != ',' && json[end] != '}') end++
    return json.substring(start, end).trim()
}

data class SymbolSaveItem(
    val mapName: String,
    val offset: Long,
    val name: String,
    val isFunction: Boolean
)

data class SessionData(
    val processName: String?,
    val titleId: String?,
    val contentId: String?,
    val watchlist: List<WatchItem>,
    val symbols: List<SymbolSaveItem>
)

fun sessionToJson(
    watchlist: List<WatchItem>,
    customSymbols: Map<Long, String>,
    discoveredFunctions: List<Long>,
    vmMaps: List<com.osr.ps5debugger.domain.model.MemoryRange>,
    processInfo: com.osr.ps5debugger.protocol.Ps5ProcessInfo?
): String = buildString {
    appendLine("{")
    append("  \"processName\": ").append(processInfo?.name?.let { "\"${jsonEscape(it)}\"" } ?: "null").append(",\n")
    append("  \"titleId\": ").append(processInfo?.titleId?.let { "\"${jsonEscape(it)}\"" } ?: "null").append(",\n")
    append("  \"contentId\": ").append(processInfo?.contentId?.let { "\"${jsonEscape(it)}\"" } ?: "null").append(",\n")
    
    // Watchlist
    append("  \"watchlist\": ")
    val watchlistJson = watchListToJson(watchlist)
    appendLine(watchlistJson.replace("\n", "\n  "))
    append(",\n")
    
    // Symbols
    append("  \"symbols\": [\n")
    val symbolList = customSymbols.entries.mapNotNull { (addr, name) ->
        val map = vmMaps.firstOrNull { addr >= it.start && addr < it.end } ?: return@mapNotNull null
        val offset = addr - map.start
        val isFunc = discoveredFunctions.contains(addr)
        Triple(map.name, offset, isFunc to name)
    }
    symbolList.forEachIndexed { index, (mapName, offset, data) ->
        val (isFunc, name) = data
        append("    {")
        append("\"mapName\":\"").append(jsonEscape(mapName)).append("\",")
        append("\"offset\":").append(offset).append(",")
        append("\"offsetHex\":\"0x").append(offset.toString(16).uppercase()).append("\",")
        append("\"name\":\"").append(jsonEscape(name)).append("\",")
        append("\"isFunction\":").append(isFunc)
        append("}")
        if (index != symbolList.lastIndex) append(",")
        appendLine()
    }
    append("  ]\n")
    append("}")
}

fun sessionFromJson(json: String): SessionData {
    val trimmed = json.trim()
    if (trimmed.startsWith("[")) {
        // Old format
        val items = watchListFromJson(json)
        return SessionData(null, null, null, items, emptyList())
    }
    
    val processName = readJsonStringField(trimmed, "processName")
    val titleId = readJsonStringField(trimmed, "titleId")
    val contentId = readJsonStringField(trimmed, "contentId")
    
    // Extract watchlist array content
    val watchlist = mutableListOf<WatchItem>()
    val watchlistKeyIdx = trimmed.indexOf("\"watchlist\"")
    if (watchlistKeyIdx != -1) {
        val startBrack = trimmed.indexOf('[', watchlistKeyIdx)
        if (startBrack != -1) {
            var depth = 1
            var i = startBrack + 1
            while (i < trimmed.length && depth > 0) {
                if (trimmed[i] == '[') depth++
                else if (trimmed[i] == ']') depth--
                i++
            }
            if (i <= trimmed.length) {
                val watchlistSubJson = trimmed.substring(startBrack, i)
                watchlist.addAll(watchListFromJson(watchlistSubJson))
            }
        }
    }
    
    // Extract symbols array content
    val symbols = mutableListOf<SymbolSaveItem>()
    val symbolsKeyIdx = trimmed.indexOf("\"symbols\"")
    if (symbolsKeyIdx != -1) {
        val startBrack = trimmed.indexOf('[', symbolsKeyIdx)
        if (startBrack != -1) {
            var depth = 1
            var i = startBrack + 1
            while (i < trimmed.length && depth > 0) {
                if (trimmed[i] == '[') depth++
                else if (trimmed[i] == ']') depth--
                i++
            }
            if (i <= trimmed.length) {
                val symbolsSubJson = trimmed.substring(startBrack, i)
                val objects = extractJsonObjects(symbolsSubJson)
                objects.forEach { obj ->
                    val mapName = readJsonStringField(obj, "mapName") ?: return@forEach
                    val offset = readJsonRawField(obj, "offset")?.toLongOrNull()
                        ?: readJsonStringField(obj, "offsetHex")?.removePrefix("0x")?.removePrefix("0X")?.toLongOrNull(16)
                        ?: return@forEach
                    val name = readJsonStringField(obj, "name") ?: return@forEach
                    val isFunction = readJsonRawField(obj, "isFunction")?.equals("true", ignoreCase = true) == true
                    symbols.add(SymbolSaveItem(mapName, offset, name, isFunction))
                }
            }
        }
    }
    
    return SessionData(processName, titleId, contentId, watchlist, symbols)
}

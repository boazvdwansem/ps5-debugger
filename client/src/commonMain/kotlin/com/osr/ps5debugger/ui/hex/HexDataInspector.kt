package com.osr.ps5debugger.ui.hex

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.util.copyToClipboard
import kotlinx.coroutines.delay

@Composable
fun HexDataInspector(
    state: HexState,
    modifier: Modifier = Modifier
) {
    val cursor = state.selectionEnd ?: state.selectionStart
    val scrollState = rememberScrollState()
    var copiedLabel by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(copiedLabel) {
        if (copiedLabel != null) {
            delay(1200)
            copiedLabel = null
        }
    }

    if (cursor == null) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(PS5ThemeColors.SecondaryBg)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "INSPECTOR: Select a byte to inspect decoded values",
                fontSize = 10.sp,
                color = PS5ThemeColors.TextMuted,
                fontFamily = FontFamily.Monospace
            )
        }
        return
    }

    // Decode memory at cursor
    val u8 = state.getUInt8(cursor)
    val i8 = state.getInt8(cursor).toInt()
    val u16 = state.getUInt16(cursor)
    val i16 = state.getInt16(cursor).toInt()
    val u32 = state.getUInt32(cursor)
    val i32 = state.getInt32(cursor)
    val u64 = state.getUInt64(cursor)
    val i64 = state.getInt64(cursor)
    val flt = state.getFloat(cursor)
    val dbl = state.getDouble(cursor)
    val binStr = String.format("%8s", Integer.toBinaryString(u8)).replace(' ', '0').let {
        "${it.substring(0, 4)} ${it.substring(4)}"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(PS5ThemeColors.SecondaryBg)
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Tag showing offset
        Text(
            text = "OFFSET",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = PS5ThemeColors.AccentCyan,
            letterSpacing = 0.5.sp
        )
        Text(
            text = String.format("0x%016X", cursor),
            fontSize = 11.sp,
            color = PS5ThemeColors.TextMain,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.clickable {
                val hex = String.format("0x%016X", cursor)
                copyToClipboard(hex)
                copiedLabel = "Offset"
            }
        )

        InspectorSeparator()

        // Binary
        InspectorChip(
            label = "BIN",
            value = binStr,
            isCopied = copiedLabel == "BIN",
            onClick = {
                copyToClipboard(binStr)
                copiedLabel = "BIN"
            }
        )

        InspectorSeparator()

        // Int8 / UInt8
        InspectorChip(
            label = "U8",
            value = "$u8 (0x${u8.toString(16).uppercase().padStart(2, '0')})",
            isCopied = copiedLabel == "U8",
            onClick = {
                copyToClipboard(u8.toString())
                copiedLabel = "U8"
            }
        )
        InspectorChip(
            label = "I8",
            value = "$i8",
            isCopied = copiedLabel == "I8",
            onClick = {
                copyToClipboard(i8.toString())
                copiedLabel = "I8"
            }
        )

        InspectorSeparator()

        // Int16 / UInt16
        InspectorChip(
            label = "U16",
            value = "$u16 (0x${u16.toString(16).uppercase().padStart(4, '0')})",
            isCopied = copiedLabel == "U16",
            onClick = {
                copyToClipboard(u16.toString())
                copiedLabel = "U16"
            }
        )
        InspectorChip(
            label = "I16",
            value = "$i16",
            isCopied = copiedLabel == "I16",
            onClick = {
                copyToClipboard(i16.toString())
                copiedLabel = "I16"
            }
        )

        InspectorSeparator()

        // Int32 / UInt32
        InspectorChip(
            label = "U32",
            value = "$u32 (0x${u32.toString(16).uppercase().padStart(8, '0')})",
            isCopied = copiedLabel == "U32",
            onClick = {
                copyToClipboard(u32.toString())
                copiedLabel = "U32"
            }
        )
        InspectorChip(
            label = "I32",
            value = "$i32",
            isCopied = copiedLabel == "I32",
            onClick = {
                copyToClipboard(i32.toString())
                copiedLabel = "I32"
            }
        )

        InspectorSeparator()

        // Float
        val floatFormatted = if (flt.isNaN()) "NaN" else if (flt.isInfinite()) "Inf" else "%.4f".format(flt)
        InspectorChip(
            label = "F32",
            value = floatFormatted,
            isCopied = copiedLabel == "F32",
            onClick = {
                copyToClipboard(flt.toString())
                copiedLabel = "F32"
            }
        )

        InspectorSeparator()

        // Int64 / UInt64
        val u64Hex = String.format("0x%016X", u64)
        InspectorChip(
            label = "U64",
            value = u64Hex,
            isCopied = copiedLabel == "U64",
            onClick = {
                copyToClipboard(u64Hex)
                copiedLabel = "U64"
            }
        )

        // Double
        val dblFormatted = if (dbl.isNaN()) "NaN" else if (dbl.isInfinite()) "Inf" else "%.4f".format(dbl)
        InspectorChip(
            label = "F64",
            value = dblFormatted,
            isCopied = copiedLabel == "F64",
            onClick = {
                copyToClipboard(dbl.toString())
                copiedLabel = "F64"
            }
        )
    }
}

@Composable
private fun InspectorSeparator() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(14.dp)
            .background(PS5ThemeColors.BorderColor.copy(alpha = 0.5f))
    )
}

@Composable
private fun InspectorChip(
    label: String,
    value: String,
    isCopied: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .border(1.dp, if (isCopied) PS5ThemeColors.AccentCyan else PS5ThemeColors.BorderColor.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
            .background(if (isCopied) PS5ThemeColors.AccentCyan.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = if (isCopied) "COPIED" else label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (isCopied) PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMuted,
            fontFamily = FontFamily.Monospace
        )
        if (!isCopied) {
            Text(
                text = value,
                fontSize = 10.sp,
                color = PS5ThemeColors.TextMain,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

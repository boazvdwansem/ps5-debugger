package com.osr.ps5debugger.ui.hex

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
class StableRowBytes(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StableRowBytes) return false
        return bytes.contentEquals(other.bytes)
    }
    override fun hashCode(): Int = bytes.contentHashCode()
}

@Composable
fun HexRowView(
    address: Long,
    stableBytes: StableRowBytes,
    columns: Int,
    selectionMin: Long?,
    selectionMax: Long?,
    cursorAddress: Long?,
    pendingEdits: Map<Long, Byte>,
    hexInputBuffer: String,
    isMobile: Boolean,
    showAddress: Boolean = true
) {
    val addressWidthDp = if (isMobile) 80.dp else 120.dp
    val hexCellWidthDp = if (isMobile) 20.dp else 24.dp
    val asciiCellWidthDp = if (isMobile) 9.dp else 12.dp
    val spacerAddressToHexDp = if (isMobile) 6.dp else 8.dp
    val spacerHexToAsciiDp = if (isMobile) 12.dp else 16.dp

    Row(
        modifier = Modifier.fillMaxWidth().height(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        if (showAddress) {
            Text(
                text = String.format("%X", address),
                fontFamily = FontFamily.Monospace,
                fontSize = if (isMobile) 11.sp else 13.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(addressWidthDp).padding(start = if (isMobile) 4.dp else 8.dp)
            )

            Spacer(Modifier.width(spacerAddressToHexDp))
        }

        // Hex data group
        Row(
            modifier = Modifier.width((columns * hexCellWidthDp.value).dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            for (i in 0 until columns) {
                if (i < stableBytes.bytes.size) {
                    val byteAddr = address + i
                    val b = pendingEdits[byteAddr] ?: stableBytes.bytes[i]
                    val isCursor = cursorAddress == byteAddr
                    val isSelected = selectionMin != null && selectionMax != null && byteAddr >= selectionMin && byteAddr <= selectionMax
                    ByteHexCell(
                        byte = b,
                        isSelected = isSelected,
                        isCursor = isCursor,
                        isPendingEdit = pendingEdits.containsKey(byteAddr),
                        hexInputBuffer = if (isCursor) hexInputBuffer else "",
                        width = hexCellWidthDp,
                        isMobile = isMobile
                    )
                } else {
                    Spacer(Modifier.width(hexCellWidthDp))
                }
            }
        }

        Spacer(Modifier.width(spacerHexToAsciiDp))

        // ASCII group
        Row(
            modifier = Modifier.width((columns * asciiCellWidthDp.value).dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            for (i in 0 until columns) {
                if (i < stableBytes.bytes.size) {
                    val byteAddr = address + i
                    val b = pendingEdits[byteAddr] ?: stableBytes.bytes[i]
                    val isSelected = selectionMin != null && selectionMax != null && byteAddr >= selectionMin && byteAddr <= selectionMax
                    ByteAsciiCell(
                        byte = b,
                        isSelected = isSelected,
                        width = asciiCellWidthDp,
                        isMobile = isMobile
                    )
                } else {
                    Spacer(Modifier.width(asciiCellWidthDp))
                }
            }
        }
        
        Spacer(Modifier.width(16.dp))
    }
}

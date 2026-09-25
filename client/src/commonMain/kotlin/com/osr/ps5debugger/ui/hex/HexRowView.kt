package com.osr.ps5debugger.ui.hex

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.PS5ThemeColors

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
    changedBytes: Map<Long, Long>, // address -> timestamp
    hexInputBuffer: String,
    isMobile: Boolean,
    showAddress: Boolean = true
) {
    val addressWidthDp = HexLayoutMetrics.addressWidthDp(isMobile, showAddress)
    val hexCellWidthDp = HexLayoutMetrics.hexCellWidthDp(isMobile)
    val asciiCellWidthDp = HexLayoutMetrics.asciiCellWidthDp(isMobile)
    val midGapDp = HexLayoutMetrics.midGapDp(isMobile, columns)
    val spacerAddressToHexDp = HexLayoutMetrics.spacerAddressToHexDp(isMobile, showAddress)
    val spacerHexToAsciiDp = HexLayoutMetrics.spacerHexToAsciiDp(isMobile)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HexLayoutMetrics.rowHeightDp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        if (showAddress) {
            // Dual-tone 64-bit address rendering
            Row(
                modifier = Modifier
                    .width(addressWidthDp)
                    .padding(start = if (isMobile) 4.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isMobile) {
                    Text(
                        text = String.format("%08X", address and 0xFFFFFFFFL),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = PS5ThemeColors.AccentCyan.copy(alpha = 0.85f)
                    )
                } else {
                    val hi = (address ushr 32) and 0xFFFFFFFFL
                    val lo = address and 0xFFFFFFFFL
                    Text(
                        text = String.format("%08X", hi),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = PS5ThemeColors.TextMuted.copy(alpha = 0.5f)
                    )
                    Text(
                        text = ":",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = PS5ThemeColors.TextMuted.copy(alpha = 0.35f)
                    )
                    Text(
                        text = String.format("%08X", lo),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = PS5ThemeColors.AccentCyan.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Gutter divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(HexLayoutMetrics.rowHeightDp)
                    .background(PS5ThemeColors.BorderColor.copy(alpha = 0.35f))
            )

            Spacer(Modifier.width(spacerAddressToHexDp - 1.dp))
        }

        // Hex data block
        Row(
            modifier = Modifier.width(HexLayoutMetrics.calculateHexWidthDp(isMobile, columns)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            for (i in 0 until columns) {
                if (i == 8 && columns >= 16) {
                    Spacer(Modifier.width(midGapDp))
                }

                if (i < stableBytes.bytes.size) {
                    val byteAddr = address + i
                    val b = pendingEdits[byteAddr] ?: stableBytes.bytes[i]
                    val isCursor = cursorAddress == byteAddr
                    val isSelected = selectionMin != null && selectionMax != null && byteAddr in selectionMin..selectionMax
                    val isChanged = changedBytes.containsKey(byteAddr)

                    ByteHexCell(
                        byte = b,
                        isSelected = isSelected,
                        isCursor = isCursor,
                        isPendingEdit = pendingEdits.containsKey(byteAddr),
                        isChanged = isChanged,
                        hexInputBuffer = if (isCursor) hexInputBuffer else "",
                        width = hexCellWidthDp,
                        isMobile = isMobile
                    )
                } else {
                    Spacer(Modifier.width(hexCellWidthDp))
                }
            }
        }

        // Divider between Hex and ASCII
        Spacer(Modifier.width(spacerHexToAsciiDp / 2))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(HexLayoutMetrics.rowHeightDp)
                .background(PS5ThemeColors.BorderColor.copy(alpha = 0.35f))
        )
        Spacer(Modifier.width((spacerHexToAsciiDp / 2) - 1.dp))

        // ASCII block
        Row(
            modifier = Modifier.width(HexLayoutMetrics.calculateAsciiWidthDp(isMobile, columns)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            for (i in 0 until columns) {
                if (i == 8 && columns >= 16) {
                    Spacer(Modifier.width(4.dp))
                }

                if (i < stableBytes.bytes.size) {
                    val byteAddr = address + i
                    val b = pendingEdits[byteAddr] ?: stableBytes.bytes[i]
                    val isCursor = cursorAddress == byteAddr
                    val isSelected = selectionMin != null && selectionMax != null && byteAddr in selectionMin..selectionMax
                    val isChanged = changedBytes.containsKey(byteAddr)

                    ByteAsciiCell(
                        byte = b,
                        isSelected = isSelected,
                        isCursor = isCursor,
                        isPendingEdit = pendingEdits.containsKey(byteAddr),
                        isChanged = isChanged,
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

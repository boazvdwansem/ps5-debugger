package com.osr.ps5debugger.ui.hex

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared layout metrics for the Hex Viewer.
 * Used by both UI composables and hit-testing/drag-selection calculations
 * to guarantee pixel-perfect alignment.
 */
object HexLayoutMetrics {
    fun addressWidthDp(isMobile: Boolean, showAddress: Boolean): Dp =
        if (!showAddress) 0.dp else (if (isMobile) 85.dp else 140.dp)

    fun hexCellWidthDp(isMobile: Boolean): Dp =
        if (isMobile) 20.dp else 24.dp

    fun midGapDp(isMobile: Boolean, columns: Int): Dp =
        if (columns >= 16) (if (isMobile) 6.dp else 10.dp) else 0.dp

    fun asciiCellWidthDp(isMobile: Boolean): Dp =
        if (isMobile) 9.dp else 11.dp

    fun spacerAddressToHexDp(isMobile: Boolean, showAddress: Boolean): Dp =
        if (!showAddress) 0.dp else (if (isMobile) 8.dp else 14.dp)

    fun spacerHexToAsciiDp(isMobile: Boolean): Dp =
        if (isMobile) 10.dp else 16.dp

    val rowHeightDp: Dp = 22.dp
    val headerHeightDp: Dp = 26.dp

    fun calculateHexWidthDp(isMobile: Boolean, columns: Int): Dp {
        val cellW = hexCellWidthDp(isMobile)
        val midGap = midGapDp(isMobile, columns)
        return (cellW * columns) + midGap
    }

    fun calculateAsciiWidthDp(isMobile: Boolean, columns: Int): Dp {
        val cellW = asciiCellWidthDp(isMobile)
        val midGap = if (columns >= 16) 4.dp else 0.dp
        return (cellW * columns) + midGap
    }

    fun calculateTotalGridWidthDp(isMobile: Boolean, showAddress: Boolean, columns: Int): Dp {
        return addressWidthDp(isMobile, showAddress) +
                spacerAddressToHexDp(isMobile, showAddress) +
                calculateHexWidthDp(isMobile, columns) +
                spacerHexToAsciiDp(isMobile) +
                calculateAsciiWidthDp(isMobile, columns) +
                32.dp
    }
}

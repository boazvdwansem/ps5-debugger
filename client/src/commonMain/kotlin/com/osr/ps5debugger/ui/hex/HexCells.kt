package com.osr.ps5debugger.ui.hex

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.PS5ThemeColors

@Composable
fun ByteHexCell(
    byte: Byte,
    isSelected: Boolean,
    isCursor: Boolean,
    isPendingEdit: Boolean,
    isChanged: Boolean,
    hexInputBuffer: String,
    width: Dp,
    isMobile: Boolean
) {
    val isZero = byte == 0.toByte()
    val displayStr = if (hexInputBuffer.isNotEmpty() && isCursor) {
        hexInputBuffer + "_"
    } else {
        String.format("%02X", byte)
    }

    val cellBackground = when {
        isCursor -> PS5ThemeColors.AccentCyan.copy(alpha = 0.35f)
        isSelected -> PS5ThemeColors.AccentCyan.copy(alpha = 0.22f)
        isChanged -> PS5ThemeColors.AccentAmber.copy(alpha = 0.28f)
        else -> Color.Transparent
    }

    val textColor = when {
        isPendingEdit -> PS5ThemeColors.AccentAmber
        isChanged -> PS5ThemeColors.AccentAmber
        isCursor -> PS5ThemeColors.AccentCyan
        isSelected -> PS5ThemeColors.TextMain
        isZero -> PS5ThemeColors.TextMuted.copy(alpha = 0.38f)
        else -> PS5ThemeColors.TextMain
    }

    val fontWeight = when {
        isPendingEdit || isChanged || isCursor -> FontWeight.Bold
        isSelected -> FontWeight.SemiBold
        else -> FontWeight.Normal
    }

    val borderModifier = when {
        isCursor -> Modifier.border(1.dp, PS5ThemeColors.AccentCyan, RectangleShape)
        isPendingEdit -> Modifier.border(1.dp, PS5ThemeColors.AccentAmber, RectangleShape)
        else -> Modifier
    }

    Box(
        modifier = Modifier
            .width(width)
            .height(HexLayoutMetrics.rowHeightDp)
            .background(cellBackground)
            .then(borderModifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayStr,
            fontFamily = FontFamily.Monospace,
            fontSize = if (isMobile) 11.sp else 12.sp,
            fontWeight = fontWeight,
            color = textColor,
            letterSpacing = 0.sp
        )

        // Subtle indicator dot at top-right for pending edits
        if (isPendingEdit) {
            Box(
                modifier = Modifier
                    .size(3.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = (-1).dp, y = 1.dp)
                    .background(PS5ThemeColors.AccentAmber)
            )
        }
    }
}

@Composable
fun ByteAsciiCell(
    byte: Byte,
    isSelected: Boolean,
    isCursor: Boolean = false,
    isPendingEdit: Boolean = false,
    isChanged: Boolean,
    width: Dp,
    isMobile: Boolean
) {
    val bInt = byte.toInt() and 0xFF
    val isPrintable = bInt in 32..126
    val charStr = if (isPrintable) bInt.toChar().toString() else "·"

    val cellBackground = when {
        isCursor -> PS5ThemeColors.AccentCyan.copy(alpha = 0.35f)
        isSelected -> PS5ThemeColors.AccentCyan.copy(alpha = 0.22f)
        isChanged -> PS5ThemeColors.AccentAmber.copy(alpha = 0.28f)
        else -> Color.Transparent
    }

    val textColor = when {
        isPendingEdit || isChanged -> PS5ThemeColors.AccentAmber
        isCursor -> PS5ThemeColors.AccentCyan
        isSelected -> PS5ThemeColors.TextMain
        !isPrintable -> PS5ThemeColors.TextMuted.copy(alpha = 0.32f)
        else -> PS5ThemeColors.TextMain
    }

    val fontWeight = when {
        isPendingEdit || isChanged || isCursor -> FontWeight.Bold
        isSelected -> FontWeight.SemiBold
        else -> FontWeight.Normal
    }

    val borderModifier = when {
        isCursor -> Modifier.border(1.dp, PS5ThemeColors.AccentCyan, RectangleShape)
        isPendingEdit -> Modifier.border(1.dp, PS5ThemeColors.AccentAmber, RectangleShape)
        else -> Modifier
    }

    Box(
        modifier = Modifier
            .width(width)
            .height(HexLayoutMetrics.rowHeightDp)
            .background(cellBackground)
            .then(borderModifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = charStr,
            fontFamily = FontFamily.Monospace,
            fontSize = if (isMobile) 11.sp else 12.sp,
            fontWeight = fontWeight,
            color = textColor,
            letterSpacing = 0.sp
        )
    }
}

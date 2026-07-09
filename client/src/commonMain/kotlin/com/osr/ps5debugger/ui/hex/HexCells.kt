package com.osr.ps5debugger.ui.hex

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
    hexInputBuffer: String,
    width: Dp,
    isMobile: Boolean
) {
    val displayStr = if (hexInputBuffer.isNotEmpty() && isCursor) {
        hexInputBuffer + "_"
    } else {
        String.format("%02X", byte)
    }

    Box(
        modifier = Modifier
            .width(width)
            .height(24.dp)
            .background(
                if (isCursor) PS5ThemeColors.AccentCyan.copy(alpha = 0.55f)
                else if (isSelected) PS5ThemeColors.AccentCyan.copy(alpha = 0.45f)
                else Color.Transparent
            )
            .border(
                1.dp,
                if (isPendingEdit) PS5ThemeColors.AccentAmber
                else if (isCursor) PS5ThemeColors.AccentCyan
                else Color.Transparent,
                RoundedCornerShape(2.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayStr,
            fontFamily = FontFamily.Monospace,
            fontSize = if (isMobile) 11.sp else 13.sp,
            color = if (isPendingEdit) PS5ThemeColors.AccentAmber
                    else if (isCursor) PS5ThemeColors.AccentCyan
                    else MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun ByteAsciiCell(
    byte: Byte,
    isSelected: Boolean,
    width: Dp,
    isMobile: Boolean
) {
    val charStr = if (byte.toInt() in 32..126) byte.toInt().toChar().toString() else "."

    Box(
        modifier = Modifier
            .width(width)
            .height(24.dp)
            .background(if (isSelected) PS5ThemeColors.AccentCyan.copy(alpha = 0.45f) else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = charStr,
            fontFamily = FontFamily.Monospace,
            fontSize = if (isMobile) 11.sp else 13.sp,
            color = if (isSelected) PS5ThemeColors.AccentCyan else PS5ThemeColors.StatusGreen
        )
    }
}

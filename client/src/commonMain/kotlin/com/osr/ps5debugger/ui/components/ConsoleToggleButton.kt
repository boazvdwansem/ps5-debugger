package com.osr.ps5debugger.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.PS5ThemeColors

@Composable
fun ConsoleToggleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLarge: Boolean = true
) {
    val size = if (isLarge) 64.dp else 32.dp
    Button(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.SecondaryBg)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = if (isLarge) 7.dp else 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val iconColor = PS5ThemeColors.AccentCyan
            val width = if (isLarge) 24.dp else 16.dp
            val height = if (isLarge) 18.dp else 12.dp
            Canvas(modifier = Modifier.size(width = width, height = height)) {
                drawRoundRect(
                    color = iconColor,
                    style = Stroke(width = 2f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
                )
                drawLine(
                    color = iconColor,
                    start = androidx.compose.ui.geometry.Offset(if (isLarge) 6f else 4f, if (isLarge) 7f else 4.5f),
                    end = androidx.compose.ui.geometry.Offset(if (isLarge) 10f else 7f, if (isLarge) 10f else 6.5f),
                    strokeWidth = 2f
                )
                drawLine(
                    color = iconColor,
                    start = androidx.compose.ui.geometry.Offset(if (isLarge) 10f else 7f, if (isLarge) 10f else 6.5f),
                    end = androidx.compose.ui.geometry.Offset(if (isLarge) 6f else 4f, if (isLarge) 13f else 8.5f),
                    strokeWidth = 2f
                )
                drawLine(
                    color = iconColor,
                    start = androidx.compose.ui.geometry.Offset(if (isLarge) 14f else 9.5f, if (isLarge) 13f else 8.5f),
                    end = androidx.compose.ui.geometry.Offset(if (isLarge) 19f else 13f, if (isLarge) 13f else 8.5f),
                    strokeWidth = 2f
                )
            }
            if (isLarge) {
                Spacer(Modifier.height(3.dp))
                Text("Console", color = PS5ThemeColors.TextMain, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

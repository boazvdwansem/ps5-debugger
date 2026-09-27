package com.osr.ps5debugger.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.ui.theme.PS5ThemeColors

@Composable
internal fun GotoDialog(
    onDismiss: () -> Unit,
    onGoto: (Long) -> Unit
) {
    var addressText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to Address", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter hexadecimal address or function name", color = Color.Gray, fontSize = 12.sp)
                OutlinedTextField(
                    value = addressText,
                    onValueChange = { addressText = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PS5ThemeColors.AccentCyan,
                        unfocusedBorderColor = PS5ThemeColors.BorderColor,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val addr = addressText.trim().removePrefix("0x").toLongOrNull(16)
                    if (addr != null) {
                        onGoto(addr)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.AccentCyan)
            ) {
                Text("Go", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        },
        containerColor = PS5ThemeColors.DarkBg
    )
}


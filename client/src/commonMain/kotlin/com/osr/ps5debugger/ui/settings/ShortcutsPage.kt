package com.osr.ps5debugger.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.ui.theme.PS5ThemeColors
import com.osr.ps5debugger.util.DefaultIpHelper

@Composable
internal fun ShortcutsPage() {
    val shortcutsMap = remember { mutableStateMapOf<String, String>().apply { putAll(DefaultIpHelper.getShortcuts()) } }
    var recordingKey by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxSize()) {
        Text("Keyboard Shortcuts", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            val items = listOf(
                "lock_edit" to "Toggle Editing Lock",
                "copy" to "Copy Selection",
                "paste" to "Paste Hex",
                "inject" to "Inject Changes",
                "goto" to "Go to Address",
                "undo" to "Undo Changes",
                "search" to "Memory Search",
                "watchlist" to "Watch List",
                "memory" to "Memory View"
            )
            
            items(items) { (key, label) ->
                ShortcutItem(
                    label = label,
                    shortcut = shortcutsMap[key] ?: "",
                    isRecording = recordingKey == key,
                    onStartRecording = { recordingKey = key },
                    onStopRecording = { recordingKey = null },
                    onShortcutChanged = { newShortcut ->
                        shortcutsMap[key] = newShortcut
                        DefaultIpHelper.setShortcuts(shortcutsMap)
                    }
                )
            }
        }
        
        Text(
            "Click a shortcut box and press your desired key combination. Global shortcuts (Search, Watchlist, Memory) work from anywhere. Contextual shortcuts work in their respective views.",
            color = Color.Gray,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
internal fun ShortcutItem(
    label: String,
    shortcut: String,
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onShortcutChanged: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PS5ThemeColors.SecondaryBg.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.LightGray, fontSize = 13.sp)
        
        Surface(
            color = if (isRecording) PS5ThemeColors.AccentCyan.copy(alpha = 0.2f) else PS5ThemeColors.Surface,
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, if (isRecording) PS5ThemeColors.AccentCyan else PS5ThemeColors.BorderColor),
            modifier = Modifier
                .width(150.dp)
                .height(32.dp)
                .clickable { onStartRecording() }
                .onKeyEvent { event ->
                    if (isRecording && event.type == KeyEventType.KeyDown) {
                        val key = event.key
                        if (key != Key.CtrlLeft && key != Key.CtrlRight && 
                            key != Key.ShiftLeft && key != Key.ShiftRight && 
                            key != Key.AltLeft && key != Key.AltRight && 
                            key != Key.MetaLeft && key != Key.MetaRight) {
                            
                            val sb = StringBuilder()
                            if (event.isCtrlPressed) sb.append("Ctrl+")
                            if (event.isShiftPressed) sb.append("Shift+")
                            if (event.isAltPressed) sb.append("Alt+")
                            
                            val keyStr = when(key) {
                                Key.A -> "A"; Key.B -> "B"; Key.C -> "C"; Key.D -> "D"; Key.E -> "E"
                                Key.F -> "F"; Key.G -> "G"; Key.H -> "H"; Key.I -> "I"; Key.J -> "J"
                                Key.K -> "K"; Key.L -> "L"; Key.M -> "M"; Key.N -> "N"; Key.O -> "O"
                                Key.P -> "P"; Key.Q -> "Q"; Key.R -> "R"; Key.S -> "S"; Key.T -> "T"
                                Key.U -> "U"; Key.V -> "V"; Key.W -> "W"; Key.X -> "X"; Key.Y -> "Y"
                                Key.Z -> "Z"; Key.Zero -> "0"; Key.One -> "1"; Key.Two -> "2"
                                Key.Three -> "3"; Key.Four -> "4"; Key.Five -> "5"; Key.Six -> "6"
                                Key.Seven -> "7"; Key.Eight -> "8"; Key.Nine -> "9"
                                else -> key.toString().replace("Key: ", "")
                            }
                            sb.append(keyStr)
                            onShortcutChanged(sb.toString())
                            onStopRecording()
                            return@onKeyEvent true
                        }
                    }
                    false
                }
                .focusable(true)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    if (isRecording) "Press Keys..." else shortcut,
                    color = if (isRecording) PS5ThemeColors.AccentCyan else Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}


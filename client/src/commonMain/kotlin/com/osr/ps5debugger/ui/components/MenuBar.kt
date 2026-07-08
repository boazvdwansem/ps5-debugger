package com.osr.ps5debugger.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TopMenuBar(
    onFileAction: (String) -> Unit,
    onEditAction: (String) -> Unit,
    onViewAction: (String) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MenuDropdown(
            title = "File",
            options = listOf("Save", "Load", "Exit").map { it to { onFileAction(it) } }
        )

        MenuDropdown(
            title = "Edit",
            options = listOf(
                "Undo",
                "Redo",
                "Cut",
                "Copy",
                "Copy Address",
                "Paste",
                "Select All",
                "Select None",
                "Find",
                "Find next",
                "Go to address",
                "Preferences"
            ).map { it to { onEditAction(it) } }
        )

        MenuDropdown(
            title = "View",
            options = listOf("Disassembly", "Graph", "Hex").map { it to { onViewAction(it) } }
        )
    }
}

@Composable
fun MenuDropdown(
    title: String,
    options: List<Pair<String, () -> Unit>>
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (option, action) ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        action()
                    }
                )
            }
        }
    }
}

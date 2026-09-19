package com.osr.ps5debugger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import com.osr.ps5debugger.ui.icons.PS5Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.di.AppContainer

private data class SymbolItem(
    val address: Long,
    val isFunction: Boolean,
    val defaultName: String,
    val currentName: String
)

private data class SymbolGroup(
    val name: String,
    val items: List<SymbolItem>,
    val available: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymbolsView(
    onJumpToAddress: (Long) -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchText by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf(0) } // 0 = All, 1 = Subroutines, 2 = Local Labels
    
    // Track symbol address currently being edited
    var editingAddress by remember { mutableStateOf<Long?>(null) }
    var editingNameText by remember { mutableStateOf("") }

    var symbolsUpdateTrigger by remember { mutableStateOf(0) }

    val functions = AppContainer.discoveredFunctions
    val jumpTargets = AppContainer.discoveredJumpTargets
    val symbolItems = remember(functions.size, jumpTargets.size, symbolsUpdateTrigger) {
        val list = mutableListOf<SymbolItem>()
        functions.forEach { addr ->
            list.add(
                SymbolItem(
                    address = addr,
                    isFunction = true,
                    defaultName = "sub_${addr.toString(16).uppercase()}",
                    currentName = AppContainer.getSymbolName(addr, true)
                )
            )
        }
        jumpTargets.forEach { addr ->
            if (!functions.contains(addr)) {
                list.add(
                    SymbolItem(
                        address = addr,
                        isFunction = false,
                        defaultName = "loc_${addr.toString(16).uppercase()}",
                        currentName = AppContainer.getSymbolName(addr, false)
                    )
                )
            }
        }
        list.sortedBy { it.address }
    }

    val filteredItems = remember(symbolItems, searchText, filterType) {
        symbolItems.filter { item ->
            (filterType == 0 || (filterType == 1 && item.isFunction) || (filterType == 2 && !item.isFunction)) &&
            (searchText.isEmpty() || item.currentName.contains(searchText, ignoreCase = true) || item.address.toString(16).contains(searchText, ignoreCase = true))
        }
    }

    val groups = remember(filteredItems) {
        val functionsGroup = filteredItems.filter { it.isFunction }
        val labelsGroup = filteredItems.filter { !it.isFunction }
        listOf(
            SymbolGroup("Imports", emptyList(), available = false),
            SymbolGroup("Exports", emptyList(), available = false),
            SymbolGroup("Functions", functionsGroup),
            SymbolGroup("Labels", labelsGroup),
            SymbolGroup("Classes", emptyList(), available = false),
            SymbolGroup("Namespaces", emptyList(), available = false)
        )
    }

    var expandedGroups by remember { mutableStateOf(setOf("Functions", "Labels")) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(PS5ThemeColors.DarkBg)
            .padding(8.dp)
    ) {
        // Sidebar Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Symbols",
                style = MaterialTheme.typography.titleMedium,
                color = PS5ThemeColors.TextMain,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onCollapse, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = PS5Icons.Close,
                    contentDescription = "Collapse",
                    tint = PS5ThemeColors.TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Search Field
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            placeholder = { Text("Search by name or address...", fontSize = 11.sp, color = PS5ThemeColors.TextMuted) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            textStyle = TextStyle(fontSize = 12.sp, color = PS5ThemeColors.TextMain),
            singleLine = true,
            leadingIcon = { Icon(PS5Icons.Search, null, modifier = Modifier.size(16.dp), tint = PS5ThemeColors.TextMuted) },
            trailingIcon = {
                if (searchText.isNotEmpty()) {
                    IconButton(onClick = { searchText = "" }, modifier = Modifier.size(24.dp)) {
                        Icon(PS5Icons.Clear, null, modifier = Modifier.size(14.dp), tint = PS5ThemeColors.TextMuted)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PS5ThemeColors.AccentCyan,
                unfocusedBorderColor = PS5ThemeColors.BorderColor,
                cursorColor = PS5ThemeColors.AccentCyan
            )
        )

        // Groups List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(groups) { group ->
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = group.available) {
                                expandedGroups = if (expandedGroups.contains(group.name)) {
                                    expandedGroups - group.name
                                } else {
                                    expandedGroups + group.name
                                }
                            }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (expandedGroups.contains(group.name)) PS5Icons.ChevronDown else PS5Icons.ChevronRight,
                            contentDescription = null,
                            tint = if (group.available) PS5ThemeColors.TextMain else PS5ThemeColors.TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = group.name,
                            color = if (group.available) PS5ThemeColors.TextMain else PS5ThemeColors.TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (group.available) {
                            Text(
                                text = group.items.size.toString(),
                                color = PS5ThemeColors.TextMuted,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }

                    if (expandedGroups.contains(group.name) && group.available) {
                        group.items.forEach { item ->
                            val isEditing = editingAddress == item.address
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isEditing) PS5ThemeColors.SecondaryBg else Color.Transparent)
                                    .padding(start = 20.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                            ) {
                                if (isEditing) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        BasicTextField(
                                            value = editingNameText,
                                            onValueChange = { editingNameText = it },
                                            textStyle = TextStyle(color = PS5ThemeColors.AccentCyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                                            modifier = Modifier.weight(1f).background(Color.Black.copy(alpha = 0.2f)).padding(4.dp),
                                            cursorBrush = androidx.compose.ui.graphics.SolidColor(PS5ThemeColors.AccentCyan)
                                        )
                                        IconButton(
                                            onClick = {
                                                AppContainer.renameSymbol(item.address, editingNameText)
                                                symbolsUpdateTrigger++
                                                editingAddress = null
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(PS5Icons.Check, contentDescription = "Apply", tint = PS5ThemeColors.AccentCyan, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = { editingAddress = null },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(PS5Icons.Close, contentDescription = "Cancel", tint = PS5ThemeColors.StatusRed, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { onJumpToAddress(item.address) }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = if (item.isFunction) "SUB" else "LOC",
                                                    color = if (item.isFunction) PS5ThemeColors.AccentCyan else Color(0xFF90A4AE),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier
                                                        .background(
                                                            if (item.isFunction) PS5ThemeColors.AccentCyan.copy(alpha = 0.1f) else Color(0xFF90A4AE).copy(alpha = 0.1f),
                                                            RoundedCornerShape(2.dp)
                                                        )
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                                Text(
                                                    text = "0x${item.address.toString(16).uppercase()}",
                                                    color = PS5ThemeColors.TextMuted,
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = item.currentName,
                                                color = PS5ThemeColors.TextMain,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }

                                        var showMenu by remember { mutableStateOf(false) }
                                        Box {
                                            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                                                Icon(PS5Icons.MoreVert, null, tint = PS5ThemeColors.TextMuted, modifier = Modifier.size(16.dp))
                                            }
                                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                                DropdownMenuItem(
                                                    text = { Text("Jump to Address") },
                                                    onClick = { onJumpToAddress(item.address); showMenu = false }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Add to Cheats", color = PS5ThemeColors.AccentCyan) },
                                                    onClick = {
                                                        val procInfo = AppContainer.debuggerUseCase.activeProcessInfo.value
                                                        if (procInfo != null) {
                                                            val cheat = com.osr.ps5debugger.domain.model.Cheat(
                                                                id = java.util.UUID.randomUUID().toString(),
                                                                name = "Symbol: ${item.currentName}",
                                                                type = com.osr.ps5debugger.domain.model.CheatType.Toggle,
                                                                address = item.address,
                                                                hexOnValue = "",
                                                                titleId = procInfo.titleId
                                                            )
                                                            AppContainer.debuggerUseCase.addCheat(procInfo.titleId, "1.00", cheat, procInfo.name)
                                                        }
                                                        showMenu = false
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Rename") },
                                                    onClick = { 
                                                        editingAddress = item.address
                                                        editingNameText = item.currentName
                                                        showMenu = false 
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Delete", color = PS5ThemeColors.StatusRed) },
                                                    onClick = { 
                                                        AppContainer.renameSymbol(item.address, "")
                                                        showMenu = false 
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

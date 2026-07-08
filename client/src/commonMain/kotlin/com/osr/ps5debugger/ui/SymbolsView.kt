package com.osr.ps5debugger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.di.AppContainer

data class SymbolItem(
    val address: Long,
    val isFunction: Boolean,
    val defaultName: String,
    val currentName: String
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
    val customNames = AppContainer.symbolNames

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
        list.sortedBy { it.address.toULong() }
    }

    val filteredItems = remember(symbolItems, searchText, filterType) {
        symbolItems.filter { item ->
            val matchesSearch = item.currentName.contains(searchText, ignoreCase = true) ||
                    "0x${item.address.toString(16)}".contains(searchText, ignoreCase = true) ||
                    item.address.toString(16).contains(searchText, ignoreCase = true)
            
            val matchesFilter = when (filterType) {
                1 -> item.isFunction
                2 -> !item.isFunction
                else -> true
            }
            
            matchesSearch && matchesFilter
        }
    }

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
                text = "SYMBOLS",
                style = MaterialTheme.typography.titleMedium,
                color = PS5ThemeColors.TextMain,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onCollapse, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
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
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = PS5ThemeColors.TextMuted, modifier = Modifier.size(16.dp)) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = PS5ThemeColors.AccentCyan,
                unfocusedBorderColor = PS5ThemeColors.BorderColor,
                containerColor = PS5ThemeColors.Surface,
                focusedTextColor = PS5ThemeColors.TextMain,
                unfocusedTextColor = PS5ThemeColors.TextMain
            ),
            shape = RoundedCornerShape(4.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
        )

        Spacer(Modifier.height(8.dp))

        // Filter Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val buttons = listOf("All", "Subroutines", "Labels")
            buttons.forEachIndexed { index, label ->
                Button(
                    onClick = { filterType = index },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (filterType == index) PS5ThemeColors.AccentCyan.copy(alpha = 0.2f) else PS5ThemeColors.Surface,
                        contentColor = if (filterType == index) PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMuted
                    ),
                    modifier = Modifier.weight(1f).height(24.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (filterType == index) PS5ThemeColors.AccentCyan else PS5ThemeColors.BorderColor
                    )
                ) {
                    Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Symbols List
        if (filteredItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("No symbols found", color = PS5ThemeColors.TextMuted, fontSize = 11.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredItems) { item ->
                    val isEditing = editingAddress == item.address
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PS5ThemeColors.Surface),
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, PS5ThemeColors.BorderColor.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            if (isEditing) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    OutlinedTextField(
                                        value = editingNameText,
                                        onValueChange = { editingNameText = it },
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        colors = TextFieldDefaults.outlinedTextFieldColors(
                                            focusedBorderColor = PS5ThemeColors.AccentCyan,
                                            unfocusedBorderColor = PS5ThemeColors.BorderColor,
                                            focusedTextColor = PS5ThemeColors.TextMain,
                                            unfocusedTextColor = PS5ThemeColors.TextMain
                                        ),
                                        shape = RoundedCornerShape(4.dp),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
                                    )
                                    IconButton(
                                        onClick = {
                                            AppContainer.renameSymbol(item.address, editingNameText)
                                            symbolsUpdateTrigger++
                                            editingAddress = null
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = "Apply", tint = PS5ThemeColors.AccentCyan, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { editingAddress = null },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = PS5ThemeColors.StatusRed, modifier = Modifier.size(16.dp))
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
                                    IconButton(
                                        onClick = {
                                            editingAddress = item.address
                                            editingNameText = item.currentName
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Rename",
                                            tint = PS5ThemeColors.TextMuted,
                                            modifier = Modifier.size(12.dp)
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

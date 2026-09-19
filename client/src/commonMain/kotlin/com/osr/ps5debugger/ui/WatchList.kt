package com.osr.ps5debugger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.osr.ps5debugger.ui.icons.PS5Icons
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
import com.osr.ps5debugger.domain.model.WatchItem
import com.osr.ps5debugger.ui.watchlist.*

@Composable
fun WatchList(
    modifier: Modifier = Modifier,
    onJumpToAddress: (Long) -> Unit
) {
    val state = rememberWatchListState(onJumpToAddress)
    val watchlist by state.watchlist.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(WatchFilter.All) }
    val frozenCount = watchlist.count { it.isFrozen }
    val staleCount = watchlist.count { it.valueStr == "??" }
    val filteredWatchlist = remember(watchlist, query, filter) {
        watchlist.filter { item ->
            val matchesFilter = when (filter) {
                WatchFilter.All -> true
                WatchFilter.Frozen -> item.isFrozen
                WatchFilter.Editable -> !item.isFrozen
                WatchFilter.Unknown -> item.valueStr == "??"
            }
            val q = query.trim()
            val matchesQuery = q.isEmpty() ||
                    item.label.contains(q, ignoreCase = true) ||
                    item.comment.contains(q, ignoreCase = true) ||
                    item.type.contains(q, ignoreCase = true) ||
                    item.address.toString(16).contains(q.removePrefix("0x"), ignoreCase = true)
            matchesFilter && matchesQuery
        }.sortedWith(compareByDescending<WatchItem> { it.isFrozen }.thenBy { it.address })
    }
    
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(PS5ThemeColors.DarkBg)
    ) {
        val isMobile = maxWidth < 800.dp
        
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WatchListHeader(
                state = state,
                isMobile = isMobile,
                totalCount = watchlist.size,
                frozenCount = frozenCount,
                staleCount = staleCount,
                query = query,
                onQueryChange = { query = it },
                filter = filter,
                onFilterChange = { filter = it }
            )
            
            if (watchlist.isEmpty()) {
                WatchListEmptyState(
                    message = "No watched addresses yet",
                    detail = "Add an address from Memory View, Memory Search, or this page."
                )
            } else if (filteredWatchlist.isEmpty()) {
                WatchListEmptyState(
                    message = "No watches match this filter",
                    detail = "Adjust the search text or filter chips."
                )
            } else {
                if (!isMobile) {
                    WatchTableHeader()
                }
                WatchListContent(state, filteredWatchlist, isMobile, modifier = Modifier.fillMaxSize().weight(1f))
            }
        }
    }

    if (state.showAddDialog) {
        AddWatchItemDialog(state)
    }
}

@Composable
private fun WatchListHeader(
    state: WatchListState,
    isMobile: Boolean,
    totalCount: Int,
    frozenCount: Int,
    staleCount: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    filter: WatchFilter,
    onFilterChange: (WatchFilter) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PS5ThemeColors.Surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, PS5ThemeColors.BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = PS5ThemeColors.AccentCyan.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, PS5ThemeColors.AccentCyan.copy(alpha = 0.35f))
                    ) {
                        Icon(
                            PS5Icons.WatchList,
                            contentDescription = null,
                            tint = PS5ThemeColors.AccentCyan,
                            modifier = Modifier.padding(7.dp).size(18.dp)
                        )
                    }
                    Column {
                        Text("Watch List", style = MaterialTheme.typography.titleMedium, color = PS5ThemeColors.TextMain)
                        Text("Live values, freezes, quick writes, and memory jumps", color = PS5ThemeColors.TextMuted, fontSize = 11.sp)
                    }
                }
                Button(
                    onClick = { state.showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.AccentCyan),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(PS5Icons.Add, contentDescription = "Add Item", tint = Color.Black, modifier = Modifier.size(16.dp))
                    if (!isMobile) {
                        Spacer(Modifier.width(8.dp))
                        Text("ADD WATCH", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WatchStat("Total", totalCount.toString(), Modifier.weight(1f))
                WatchStat("Frozen", frozenCount.toString(), Modifier.weight(1f))
                WatchStat("Unknown", staleCount.toString(), Modifier.weight(1f))
            }

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search watches") },
                placeholder = { Text("Label, address, type, or comment") },
                leadingIcon = { Icon(PS5Icons.Search, contentDescription = null, tint = PS5ThemeColors.AccentCyan, modifier = Modifier.size(17.dp)) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(28.dp)) {
                            Icon(PS5Icons.Close, contentDescription = "Clear", tint = PS5ThemeColors.TextMuted, modifier = Modifier.size(15.dp))
                        }
                    }
                },
                colors = watchTextFieldColors()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                WatchFilter.values().forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { onFilterChange(option) },
                        label = { Text(option.label, fontSize = 11.sp) },
                        colors = watchChipColors(),
                        border = watchChipBorder(filter == option),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchTableHeader() {
    Surface(
        color = PS5ThemeColors.Surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, PS5ThemeColors.BorderColor)
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            WatchHeaderCell("LABEL", Modifier.weight(1.5f))
            WatchHeaderCell("ADDRESS", Modifier.weight(1.5f))
            WatchHeaderCell("TYPE", Modifier.weight(1f))
            WatchHeaderCell("VALUE", Modifier.weight(1.5f))
            WatchHeaderCell("LOCK", Modifier.width(60.dp))
            WatchHeaderCell("COMMENT", Modifier.weight(2f))
        }
    }
}

@Composable
private fun WatchListContent(state: WatchListState, watchlist: List<WatchItem>, isMobile: Boolean, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(watchlist, key = { it.address }) { item ->
            WatchRow(
                item = item,
                coroutineScope = coroutineScope,
                onDelete = { state.removeItem(item) },
                onJumpToAddress = state.onJumpToAddress,
                onUpdateLabel = { state.updateItem(item.copy(label = it)) },
                onUpdateComment = { state.updateItem(item.copy(comment = it)) },
                onUpdateType = { state.updateItem(item.copy(type = it)) },
                isMobile = isMobile
            )
        }
    }
}

@Composable
private fun AddWatchItemDialog(state: WatchListState) {
    AlertDialog(
        onDismissRequest = { state.showAddDialog = false },
        containerColor = PS5ThemeColors.Surface,
        titleContentColor = PS5ThemeColors.TextMain,
        textContentColor = PS5ThemeColors.TextMain,
        title = { Text("Add Watch Item", color = PS5ThemeColors.TextMain) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.newLabel,
                    onValueChange = { state.newLabel = it },
                    label = { Text("Label") },
                    singleLine = true,
                    colors = watchTextFieldColors()
                )
                OutlinedTextField(
                    value = state.newAddressHex,
                    onValueChange = { state.newAddressHex = it },
                    label = { Text("Address") },
                    placeholder = { Text("0x00000000") },
                    singleLine = true,
                    colors = watchTextFieldColors()
                )
                
                Text("Data Type", color = PS5ThemeColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, PS5ThemeColors.BorderColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PS5ThemeColors.TextMain)
                    ) {
                        Text(state.selectedType, fontFamily = FontFamily.Monospace)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(PS5ThemeColors.Surface)
                    ) {
                        typeOptions.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type, color = PS5ThemeColors.TextMain) },
                                onClick = {
                                    state.selectedType = type
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { state.addWatchItem() },
                colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.AccentCyan),
                shape = RoundedCornerShape(4.dp)
            ) { Text("Add", color = Color.Black, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = { state.showAddDialog = false }) { Text("Cancel", color = PS5ThemeColors.TextMuted) } }
    )
}

private enum class WatchFilter(val label: String) {
    All("All"),
    Frozen("Frozen"),
    Editable("Live"),
    Unknown("Unknown")
}

@Composable
private fun WatchStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = PS5ThemeColors.SecondaryBg.copy(alpha = 0.55f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, PS5ThemeColors.BorderColor.copy(alpha = 0.75f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, color = PS5ThemeColors.TextMuted, fontSize = 10.sp)
            Text(value, color = PS5ThemeColors.TextMain, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WatchHeaderCell(text: String, modifier: Modifier) {
    Text(text, color = PS5ThemeColors.TextMuted, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = modifier)
}

@Composable
private fun WatchListEmptyState(message: String, detail: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            color = PS5ThemeColors.Surface,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, PS5ThemeColors.BorderColor)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(PS5Icons.WatchList, contentDescription = null, tint = PS5ThemeColors.TextMuted, modifier = Modifier.size(26.dp))
                Text(message, color = PS5ThemeColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(detail, color = PS5ThemeColors.TextMuted, fontSize = 11.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun watchTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PS5ThemeColors.AccentCyan,
    unfocusedBorderColor = PS5ThemeColors.BorderColor,
    focusedLabelColor = PS5ThemeColors.AccentCyan,
    unfocusedLabelColor = PS5ThemeColors.TextMuted,
    focusedTextColor = PS5ThemeColors.TextMain,
    unfocusedTextColor = PS5ThemeColors.TextMain,
    cursorColor = PS5ThemeColors.AccentCyan
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun watchChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = PS5ThemeColors.Surface,
    selectedContainerColor = PS5ThemeColors.AccentCyan.copy(alpha = 0.18f),
    labelColor = PS5ThemeColors.TextMuted,
    selectedLabelColor = PS5ThemeColors.AccentCyan
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun watchChipBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(
    enabled = true,
    selected = selected,
    borderColor = PS5ThemeColors.BorderColor,
    selectedBorderColor = PS5ThemeColors.AccentCyan.copy(alpha = 0.6f)
)

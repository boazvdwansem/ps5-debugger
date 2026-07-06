package com.osr.ps5debugger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.PS5ThemeColors
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.ui.watchlist.*

@Composable
fun WatchList(
    modifier: Modifier = Modifier,
    onJumpToAddress: (Long) -> Unit
) {
    val state = rememberWatchListState(onJumpToAddress)
    val watchlist by state.watchlist.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isMobile = maxWidth < 600.dp
        
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            WatchListHeader(state, isMobile)
            
            if (watchlist.isEmpty()) {
                Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text("Watchlist is empty. Add addresses to monitor values.", color = PS5ThemeColors.TextMuted)
                }
            } else {
                WatchListContent(state, watchlist, isMobile, modifier = Modifier.fillMaxSize().weight(1f))
            }
        }
    }

    if (state.showAddDialog) {
        AddWatchItemDialog(state)
    }
}

@Composable
private fun WatchListHeader(state: WatchListState, isMobile: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Watch List", style = MaterialTheme.typography.titleMedium, color = PS5ThemeColors.AccentCyan)
        Button(
            onClick = { state.showAddDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.SecondaryBg)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Item", modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add Item")
        }
    }
    
    if (!isMobile) {
        Row(
            modifier = Modifier.fillMaxWidth().background(PS5ThemeColors.SecondaryBg).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Label", modifier = Modifier.weight(1.5f).padding(start = 8.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Address", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Type", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Value", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Freeze", modifier = Modifier.width(60.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Comment", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun WatchListContent(state: WatchListState, watchlist: List<com.osr.ps5debugger.domain.model.WatchItem>, isMobile: Boolean, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    LazyColumn(modifier = modifier) {
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
            HorizontalDivider(color = PS5ThemeColors.BorderColor.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun AddWatchItemDialog(state: WatchListState) {
    AlertDialog(
        onDismissRequest = { state.showAddDialog = false },
        title = { Text("Add Watch Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = state.newLabel, onValueChange = { state.newLabel = it }, label = { Text("Label (Optional)") }, singleLine = true)
                OutlinedTextField(value = state.newAddressHex, onValueChange = { state.newAddressHex = it }, label = { Text("Address (Hex)") }, singleLine = true)
                
                Text("Data Type:", style = MaterialTheme.typography.bodySmall)
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(state.selectedType)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        typeOptions.forEach { type ->
                            DropdownMenuItem(text = { Text(type) }, onClick = { state.selectedType = type; expanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { state.addWatchItem() }) { Text("Add") } },
        dismissButton = { TextButton(onClick = { state.showAddDialog = false }) { Text("Cancel") } }
    )
}

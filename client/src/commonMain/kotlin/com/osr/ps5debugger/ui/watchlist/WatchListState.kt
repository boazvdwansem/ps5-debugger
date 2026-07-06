package com.osr.ps5debugger.ui.watchlist

import androidx.compose.runtime.*
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.domain.model.WatchItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

class WatchListState(
    val scope: CoroutineScope,
    val onJumpToAddress: (Long) -> Unit
) {
    val watchlist: StateFlow<List<WatchItem>> = AppContainer.debuggerUseCase.watchlist
    
    var showAddDialog by mutableStateOf(false)
    var newLabel by mutableStateOf("")
    var newAddressHex by mutableStateOf("")
    var selectedType by mutableStateOf("Int32")

    fun addWatchItem() {
        val addr = newAddressHex.removePrefix("0x").removePrefix("0X").toLongOrNull(16)
        if (addr != null) {
            val item = WatchItem(
                label = newLabel.ifEmpty { "Watch_0x${addr.toString(16).uppercase()}" },
                address = addr,
                type = selectedType,
                byteLength = if (selectedType == "ByteArray" || selectedType == "String") 4 else null
            )
            AppContainer.debuggerUseCase.addWatchItem(item)
            showAddDialog = false
            newLabel = ""
            newAddressHex = ""
        }
    }

    fun updateItem(item: WatchItem) {
        AppContainer.debuggerUseCase.updateWatchItem(item)
    }

    fun removeItem(item: WatchItem) {
        AppContainer.debuggerUseCase.removeWatchItem(item)
    }
}

@Composable
fun rememberWatchListState(
    onJumpToAddress: (Long) -> Unit,
    scope: CoroutineScope = rememberCoroutineScope()
) = remember { WatchListState(scope, onJumpToAddress) }

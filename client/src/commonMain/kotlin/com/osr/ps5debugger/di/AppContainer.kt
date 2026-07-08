package com.osr.ps5debugger.di

import com.osr.ps5debugger.adapters.network.SocketDebuggerAdapter
import com.osr.ps5debugger.adapters.storage.FileLogStorageAdapter
import com.osr.ps5debugger.domain.service.DebuggerDomainService
import com.osr.ps5debugger.ports.inbound.DebuggerUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf

object AppContainer {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val clientAdapter = SocketDebuggerAdapter(appScope)
    val logStorageAdapter = FileLogStorageAdapter()

    val debuggerUseCase: DebuggerUseCase = DebuggerDomainService(
        clientPort = clientAdapter,
        logPort = logStorageAdapter
    )

    val symbolNames = mutableStateMapOf<Long, String>()
    val discoveredFunctions = mutableStateListOf<Long>()
    val discoveredJumpTargets = mutableStateListOf<Long>()

    fun getSymbolName(address: Long, isFunction: Boolean): String {
        return symbolNames[address] ?: if (isFunction) {
            "sub_${address.toString(16).uppercase()}"
        } else {
            "loc_${address.toString(16).uppercase()}"
        }
    }

    fun getSymbolNameForTarget(address: Long, isCall: Boolean): String {
        val isFunction = isCall || discoveredFunctions.contains(address)
        return getSymbolName(address, isFunction)
    }

    fun renameSymbol(address: Long, newName: String) {
        if (newName.isBlank()) {
            symbolNames.remove(address)
        } else {
            symbolNames[address] = newName
        }
    }

    var filePicker: com.osr.ps5debugger.ports.inbound.FilePicker? = null
    var defaultDumpPath: String = ""
}

package com.osr.ps5debugger.di

import com.osr.ps5debugger.adapters.network.SocketDebuggerAdapter
import com.osr.ps5debugger.adapters.storage.FileLogStorageAdapter
import com.osr.ps5debugger.domain.service.DebuggerDomainService
import com.osr.ps5debugger.ports.inbound.DebuggerUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object AppContainer {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val clientAdapter = SocketDebuggerAdapter(appScope)
    val logStorageAdapter = FileLogStorageAdapter()

    val debuggerUseCase: DebuggerUseCase = DebuggerDomainService(
        clientPort = clientAdapter,
        logPort = logStorageAdapter
    )
}

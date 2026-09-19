package com.osr.ps5debugger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import com.osr.ps5debugger.di.AppContainer
import com.osr.ps5debugger.ui.state.MainState
import kotlinx.coroutines.launch

@Composable
fun DebugSidebar(
    state: MainState,
    onCollapse: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val client = AppContainer.clientAdapter.client
    val pid = AppContainer.debuggerUseCase.activeProcess.collectAsState().value?.pid
    val isConnected by state.isConnected.collectAsState()
    
    val isAttached by AppContainer.debuggerUseCase.isAttached.collectAsState()
    val threadList by AppContainer.debuggerUseCase.threadList.collectAsState()
    val selectedLwpid by AppContainer.debuggerUseCase.selectedLwpid.collectAsState()
    val selectedRegs by AppContainer.debuggerUseCase.selectedRegs.collectAsState()
    val selectedDbRegs by AppContainer.debuggerUseCase.selectedDbRegs.collectAsState()
    val selectedFsGs by AppContainer.debuggerUseCase.selectedFsGs.collectAsState()

    val isProcessHalted by AppContainer.debuggerUseCase.isProcessStopped.collectAsState()

    val refreshThreadData: suspend () -> Unit = {
        if (pid != null && isAttached && isConnected) {
            try {
                val threads = client.getThreadList()
                AppContainer.debuggerUseCase.setThreadList(threads)
                if (selectedLwpid == null || !threads.contains(selectedLwpid)) {
                    AppContainer.debuggerUseCase.setSelectedLwpid(threads.firstOrNull())
                }
                val currentLwpid = selectedLwpid ?: threads.firstOrNull()
                if (currentLwpid != null && isProcessHalted) {
                    AppContainer.debuggerUseCase.setSelectedRegs(client.getRegs(currentLwpid))
                    AppContainer.debuggerUseCase.setSelectedDbRegs(client.getDbRegs(currentLwpid))
                    AppContainer.debuggerUseCase.setSelectedFsGs(client.getFsGsBase(currentLwpid))
                } else {
                    AppContainer.debuggerUseCase.setSelectedRegs(null)
                    AppContainer.debuggerUseCase.setSelectedDbRegs(null)
                    AppContainer.debuggerUseCase.setSelectedFsGs(null)
                }
            } catch (e: Exception) {
                AppContainer.debuggerUseCase.log("DEBUGGER", "Failed to retrieve thread/register values: ${e.message}", com.osr.ps5debugger.domain.model.LogEntry.Level.WARN)
            }
        }

    }

    LaunchedEffect(isProcessHalted, selectedLwpid) {
        if (isProcessHalted) refreshThreadData()
    }

    val safeRunDebuggerAction: (suspend () -> Unit) -> Unit = { action ->
        coroutineScope.launch {
            try {
                action()
            } catch (e: Exception) {
                AppContainer.debuggerUseCase.log("DEBUGGER", "Operation failed: ${e.message}", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                val msg = e.message ?: ""
                if (msg.contains("Not connected", ignoreCase = true) || msg.contains("Socket closed", ignoreCase = true) || msg.contains("connection", ignoreCase = true)) {
                    try {
                        AppContainer.debuggerUseCase.disconnect()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(340.dp)
            .background(PS5ThemeColors.DarkBg)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Debugger",
                style = MaterialTheme.typography.titleMedium,
                color = PS5ThemeColors.TextMain,
                fontWeight = FontWeight.Bold
            )
            if (pid != null) {
                Text(
                    text = "PID $pid",
                    color = PS5ThemeColors.TextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
            IconButton(onClick = onCollapse, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = PS5Icons.Close,
                    contentDescription = "Collapse",
                    tint = PS5ThemeColors.TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Attach / Detach controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        try {
                            if (isAttached) {
                                try {
                                    client.detach()
                                } catch (_: Exception) {}
                                AppContainer.debuggerUseCase.setAttached(false)
                                state.activeBreakpoints.clear()
                                state.activeWatchpoints.clear()
                                AppContainer.debuggerUseCase.setThreadList(emptyList())
                                AppContainer.debuggerUseCase.setSelectedLwpid(null)
                                AppContainer.debuggerUseCase.setSelectedRegs(null)
                                AppContainer.debuggerUseCase.log("DEBUGGER", "Detached from target process", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                            } else if (pid != null) {
                                if (client.attach(pid)) {
                                    AppContainer.debuggerUseCase.setProcessStopped(false)
                                    AppContainer.debuggerUseCase.setAttached(true)
                                    AppContainer.debuggerUseCase.log("DEBUGGER", "Attached to process pid $pid.", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                                    refreshThreadData()
                                }
                            }
                        } catch (e: Exception) {
                            AppContainer.debuggerUseCase.log("DEBUGGER", "Attach/Detach failed: ${e.message}", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAttached) PS5ThemeColors.StatusRed else PS5ThemeColors.AccentCyan,
                    contentColor = Color.White
                ),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(if (isAttached) "Detach" else "Attach Target", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            
            if (isAttached) {
                Button(
                    onClick = { safeRunDebuggerAction { refreshThreadData() } },
                    colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.Surface),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Sync", color = PS5ThemeColors.TextMain, fontSize = 12.sp)
                }
            }
        }

        if (isAttached) {
            Spacer(Modifier.height(12.dp))
            Text("PROCESS CONTROLS", color = PS5ThemeColors.AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusColor = if (isProcessHalted) Color(0xFFC62828) else Color(0xFF2E7D32)
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(statusColor, CircleShape)
                        .border(1.dp, statusColor.copy(alpha = 0.6f), CircleShape)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isProcessHalted) "SUSPENDED" else "RUNNING",
                    color = statusColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .border(1.dp, statusColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = {
                        safeRunDebuggerAction {
                            val resumed = client.resumeProcess(pid)
                            if (resumed) {
                                AppContainer.debuggerUseCase.setProcessStopped(false)
                                AppContainer.debuggerUseCase.log("DEBUGGER", "Process resumed", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                                refreshThreadData()
                            } else {
                                AppContainer.debuggerUseCase.log("DEBUGGER", "Resume failed", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("Resume", color = Color.White, fontSize = 11.sp) }

                Button(
                    onClick = {
                        safeRunDebuggerAction {
                            val suspended = client.stopProcess(pid)
                            if (suspended) {
                                AppContainer.debuggerUseCase.setProcessStopped(true)
                                AppContainer.debuggerUseCase.log("DEBUGGER", "Process suspended", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                                refreshThreadData()
                            } else {
                                AppContainer.debuggerUseCase.log("DEBUGGER", "Suspend failed", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("Suspend", color = Color.White, fontSize = 11.sp) }

            }
            
            Spacer(Modifier.height(12.dp))
            Text("THREADS  •  ${threadList.size}", color = PS5ThemeColors.AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            
            var expandedThreads by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                OutlinedButton(
                    onClick = { expandedThreads = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(selectedLwpid?.let { "LWPID: $it" } ?: "Select Thread", color = PS5ThemeColors.TextMain, fontSize = 11.sp)
                }
                DropdownMenu(expanded = expandedThreads, onDismissRequest = { expandedThreads = false }) {
                    threadList.forEach { lwpid ->
                        DropdownMenuItem(text = { Text("LWPID: $lwpid", fontSize = 12.sp) }, onClick = {
                            AppContainer.debuggerUseCase.setSelectedLwpid(lwpid)
                            expandedThreads = false
                        })
                    }
                }
            }

            if (selectedLwpid != null) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = {
                            safeRunDebuggerAction {
                                if (client.suspendThread(selectedLwpid!!)) {
                                    AppContainer.debuggerUseCase.log("DEBUGGER", "Thread $selectedLwpid suspended", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.Surface),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("Stop", fontSize = 10.sp, color = PS5ThemeColors.TextMain) }
                    Button(
                        onClick = {
                            safeRunDebuggerAction {
                                if (client.resumeThread(selectedLwpid!!)) {
                                    AppContainer.debuggerUseCase.log("DEBUGGER", "Thread $selectedLwpid resumed", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.Surface),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("Go", fontSize = 10.sp, color = PS5ThemeColors.TextMain) }
                    Button(
                        onClick = {
                            if (!isProcessHalted) {
                                AppContainer.debuggerUseCase.log("DEBUGGER", "Thread step ignored: process is running. Suspend first.", com.osr.ps5debugger.domain.model.LogEntry.Level.WARN)
                                return@Button
                            }
                            safeRunDebuggerAction {
                                val stepped = client.stepThread(selectedLwpid!!)
                                if (stepped) {
                                    AppContainer.debuggerUseCase.setProcessStopped(true)
                                    AppContainer.debuggerUseCase.log("DEBUGGER", "Thread $selectedLwpid stepped", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                                    refreshThreadData()
                                } else {
                                    AppContainer.debuggerUseCase.log("DEBUGGER", "Thread step failed", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PS5ThemeColors.Surface),
                        modifier = Modifier.weight(1f),
                        enabled = isProcessHalted,
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("Step", fontSize = 10.sp, color = PS5ThemeColors.TextMain) }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("REGISTERS", color = PS5ThemeColors.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            
            Box(Modifier.weight(1f).fillMaxWidth().background(PS5ThemeColors.Surface, RoundedCornerShape(8.dp)).border(1.dp, PS5ThemeColors.BorderColor, RoundedCornerShape(8.dp)).padding(8.dp)) {
                val regs = selectedRegs
                if (regs != null) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        item { RegisterRow("RIP", regs.rip) }
                        item { RegisterRow("RSP", regs.rsp) }
                        item { RegisterRow("RAX", regs.rax) }
                        item { RegisterRow("RBX", regs.rbx) }
                        item { RegisterRow("RCX", regs.rcx) }
                        item { RegisterRow("RDX", regs.rdx) }
                        item { RegisterRow("RSI", regs.rsi) }
                        item { RegisterRow("RDI", regs.rdi) }
                        item { RegisterRow("RBP", regs.rbp) }
                        item { RegisterRow("R8", regs.r8) }
                        item { RegisterRow("R9", regs.r9) }
                        item { RegisterRow("R10", regs.r10) }
                        item { RegisterRow("R11", regs.r11) }
                        item { RegisterRow("R12", regs.r12) }
                        item { RegisterRow("R13", regs.r13) }
                        item { RegisterRow("R14", regs.r14) }
                        item { RegisterRow("R15", regs.r15) }
                        item { RegisterRow("RFL", regs.rflags) }
                        
                        selectedFsGs?.let {
                            item { RegisterRow("FS_BASE", it.first) }
                            item { RegisterRow("GS_BASE", it.second) }
                        }

                        selectedDbRegs?.let {
                            item { RegisterRow("DR0", it.dr0) }
                            item { RegisterRow("DR1", it.dr1) }
                            item { RegisterRow("DR2", it.dr2) }
                            item { RegisterRow("DR3", it.dr3) }
                            item { RegisterRow("DR6", it.dr6) }
                            item { RegisterRow("DR7", it.dr7) }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("No register data", fontSize = 11.sp, color = PS5ThemeColors.TextMuted)
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize().weight(1f), Alignment.Center) {
                Text("Attach to target to inspect process", fontSize = 11.sp, color = PS5ThemeColors.TextMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
fun RegisterRow(name: String, value: Long) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = PS5ThemeColors.TextMuted,
            modifier = Modifier.width(65.dp)
        )
        Text(
            text = String.format("0x%016X", value),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Color(0xFF64FFDA) // Teal registers color
        )
    }
}

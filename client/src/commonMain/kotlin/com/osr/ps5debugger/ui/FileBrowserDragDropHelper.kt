package com.osr.ps5debugger.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

object FileBrowserDragDropHelper {
    data class DragItem(
        val name: String,
        val remotePath: String,
        val isDirectory: Boolean
    )

    enum class OverwriteAction { ASK, OVERWRITE, SKIP }

    var currentPath: String = "/"
    var consoleIp: String = ""
    var onUploadStarted: (() -> Unit)? = null
    var onUploadFinished: ((String?) -> Unit)? = null
    var activeDragFiles: List<DragItem> = emptyList()
    var isFileBrowserActive: Boolean = false
    var mainWindow: Any? = null
    var startDragOut: ((Any) -> Unit)? = null

    // UI Progress States
    var isTransferring: Boolean by androidx.compose.runtime.mutableStateOf(false)
    var transferStatusText: String by androidx.compose.runtime.mutableStateOf("")
    var transferProgress: Float by androidx.compose.runtime.mutableStateOf(0f)

    // Conflict Dialog States
    var showConflictDialog: Boolean by androidx.compose.runtime.mutableStateOf(false)
    var conflictFileName: String by androidx.compose.runtime.mutableStateOf("")
    var rememberConflictChoice: Boolean by androidx.compose.runtime.mutableStateOf(false)
    var conflictResolution: OverwriteAction? = null
    val conflictLock = Any()
}

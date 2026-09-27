package com.osr.ps5debugger.di

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.io.File
import com.osr.ps5debugger.ports.inbound.DebuggerUseCase

object MetadataResolver {
    sealed class IconState {
        object Loading : IconState()
        data class Success(val bitmap: androidx.compose.ui.graphics.ImageBitmap) : IconState()
        object NotFound : IconState()
    }
    val iconCache = mutableStateMapOf<String, IconState>()
    
    val titleIdToName = mutableStateMapOf<String, String>()
    val titleIdToVersion = mutableStateMapOf<String, String>()
    val titleIdToPlatform = mutableStateMapOf<String, String>()

    private val metadataMutex = Mutex()
    private val resolvedThisSession = mutableSetOf<String>()
    private var lastSyncedIp: String? = null

    private fun getIconCacheFile(titleId: String): File {
        val userHome = System.getProperty("user.home")
        val dir = File(userHome, ".ps5debugger/icons")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${titleId.trim().uppercase()}.png")
    }

    suspend fun fetchMetadata(titleId: String, consoleIp: String) = coroutineScope {
        val tid = titleId.trim().uppercase()
        fun isBad(n: String?) = n.isNullOrEmpty() || n == "Unknown" || n.lowercase().let { it.contains("eboot.bin") || it.endsWith(".elf") || it.endsWith(".bin") }
        
        // 1. Try local icon cache immediately
        if (iconCache[titleId] !is IconState.Success) {
            val localFile = getIconCacheFile(tid)
            if (localFile.exists()) {
                try {
                    val bytes = localFile.readBytes()
                    val bitmap = com.osr.ps5debugger.util.decodeImage(bytes)
                    if (bitmap != null) {
                        iconCache[titleId] = IconState.Success(bitmap)
                    }
                } catch (_: Exception) {}
            }
        }

        val needsIcon = iconCache[titleId] !is IconState.Success
        val needsSync = !resolvedThisSession.contains(tid)
        val needsDbSync = lastSyncedIp != consoleIp
        
        if (!needsIcon && !needsSync && !needsDbSync) return@coroutineScope
        
        metadataMutex.withLock {
            if (lastSyncedIp != consoleIp || !resolvedThisSession.contains(tid) || (needsIcon && iconCache[titleId] !is IconState.Success)) {
                
                val ftpClient = com.osr.ps5debugger.infrastructure.network.Ps5FtpClient(consoleIp)
                try {
                    // One-time App DB Sync per connection
                    if (lastSyncedIp != consoleIp) {
                        try {
                            val dbPath = "/system_data/priv/mms/app.db"
                            val localDbFile = File(System.getProperty("java.io.tmpdir"), "ps5_app_sync.db")
                            val baos = java.io.ByteArrayOutputStream()
                            ftpClient.downloadFile(dbPath, baos)
                            localDbFile.writeBytes(baos.toByteArray())
                            
                            val reader = com.osr.ps5debugger.util.SqliteReader(localDbFile.absolutePath)
                            val metaList = reader.queryAppMetadata()
                            metaList.forEach { meta ->
                                titleIdToName[meta.titleId] = meta.name
                                titleIdToPlatform[meta.titleId] = meta.platform
                                AppContainer.debuggerUseCase.updateGameName(meta.titleId, meta.name)
                                AppContainer.debuggerUseCase.updateGamePlatform(meta.titleId, meta.platform)
                            }
                            reader.close()
                            localDbFile.delete()
                            lastSyncedIp = consoleIp
                            AppContainer.debuggerUseCase.log("METADATA", "Synced ${metaList.size} titles from PS5 app database", com.osr.ps5debugger.domain.model.LogEntry.Level.INFO)
                        } catch (e: Exception) {
                            AppContainer.debuggerUseCase.log("METADATA", "Failed to sync app database: ${e.message}", com.osr.ps5debugger.domain.model.LogEntry.Level.WARN)
                        }
                    }

                    if (iconCache[titleId] !is IconState.Success) {
                        iconCache[titleId] = IconState.Loading
                    }
                    
                    coroutineScope {
                        // Icon Task
                        if (iconCache[titleId] !is IconState.Success) {
                            launch {
                                try {
                                    val iconPath = "/user/appmeta/$tid/icon0.png"
                                    val baos = java.io.ByteArrayOutputStream()
                                    ftpClient.downloadFile(iconPath, baos)
                                    val bytes = baos.toByteArray()
                                    val bitmap = com.osr.ps5debugger.util.decodeImage(bytes)
                                    if (bitmap != null) {
                                        iconCache[titleId] = IconState.Success(bitmap)
                                        getIconCacheFile(tid).writeBytes(bytes)
                                    } else {
                                        iconCache[titleId] = IconState.NotFound
                                    }
                                } catch (e: Exception) {
                                    if (e !is CancellationException) iconCache[titleId] = IconState.NotFound
                                }
                            }
                        }

                        // Name & Version Sync Task
                        launch {
                            val currentName = titleIdToName[titleId]
                            val needsName = isBad(currentName)
                            
                            val metaFiles = if (needsName) {
                                listOf("param.sfo", "PARAM.SFO", "param.json", "pronunciation.xml", "changeinfo/changeinfo.xml")
                            } else {
                                listOf("changeinfo/changeinfo.xml")
                            }

                            for (metaFile in metaFiles) {
                                try {
                                    val path = "/user/appmeta/$tid/$metaFile"
                                    val baos = java.io.ByteArrayOutputStream()
                                    ftpClient.downloadFile(path, baos)
                                    val bytes = baos.toByteArray()
                                    
                                    if (metaFile.contains("changeinfo.xml")) {
                                        val text = bytes.decodeToString()
                                        val ver = Regex("app_ver=\"([^\"]+)\"").findAll(text).lastOrNull()?.groupValues?.get(1)
                                        if (!ver.isNullOrEmpty()) {
                                            titleIdToVersion[titleId] = ver
                                            AppContainer.debuggerUseCase.updateGameVersion(titleId, ver)
                                        }
                                    } else {
                                        val resolved = when {
                                            metaFile.endsWith(".sfo", true) -> com.osr.ps5debugger.util.SfoUtil.getTitleName(bytes)
                                            metaFile == "param.json" -> {
                                                val text = bytes.decodeToString()
                                                Regex("\"(?:title|name)\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
                                            }
                                            metaFile == "pronunciation.xml" -> {
                                                val text = bytes.decodeToString()
                                                Regex("<text display=\"1\">([^<]+)</text>").find(text)?.groupValues?.get(1)
                                            }
                                            else -> null
                                        }
                                        if (!resolved.isNullOrEmpty() && isBad(resolved).not()) {
                                            titleIdToName[titleId] = resolved
                                            AppContainer.debuggerUseCase.updateGameName(titleId, resolved)
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                            resolvedThisSession.add(tid)
                        }
                    }
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        AppContainer.debuggerUseCase.log("METADATA", "Error syncing metadata for $titleId: ${e.message}", com.osr.ps5debugger.domain.model.LogEntry.Level.ERROR)
                    }
                } finally {
                    ftpClient.disconnect()
                }
            }
        }
    }
}


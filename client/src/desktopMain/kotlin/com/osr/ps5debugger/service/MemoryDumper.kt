package com.osr.ps5debugger.service

import com.osr.ps5debugger.protocol.Ps5VmMapEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object MemoryDumper {

    suspend fun dumpRegions(
        pid: Int,
        regions: List<Ps5VmMapEntry>,
        outputDir: File,
        onProgress: (currentRegion: String, progress: Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val client = DebuggerService.client
            val chunkSize = 65536 // 64KB chunks for smooth network streaming
            
            regions.forEachIndexed { index, region ->
                val cleanedName = region.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifEmpty { "unnamed" }
                val fileName = String.format("dump_%d_%s_0x%X.bin", pid, cleanedName, region.start)
                val outputFile = File(outputDir, fileName)
                
                DebuggerService.log("DUMPER", "Dumping region ${index + 1}/${regions.size}: $cleanedName (0x${region.start.toString(16)})", DebuggerService.LogEntry.Level.INFO)
                
                FileOutputStream(outputFile).use { fos ->
                    var currentAddress = region.start
                    val endAddress = region.end
                    val totalSize = endAddress - currentAddress
                    var bytesDumped = 0L

                    while (currentAddress < endAddress && isActive) {
                        val remaining = endAddress - currentAddress
                        val toRead = minOf(chunkSize.toLong(), remaining).toInt()
                        
                        val data = client.readMemory(pid, currentAddress, toRead)
                        fos.write(data)
                        
                        currentAddress += toRead
                        bytesDumped += toRead
                        
                        val progressFraction = bytesDumped.toFloat() / totalSize.toFloat()
                        onProgress(cleanedName, progressFraction)
                    }
                }
                
                if (!isActive) {
                    DebuggerService.log("DUMPER", "Dump cancelled by user", DebuggerService.LogEntry.Level.WARN)
                    return@withContext Result.failure(IOException("Cancelled"))
                }
            }
            DebuggerService.log("DUMPER", "Successfully dumped all selected regions to ${outputDir.absolutePath}", DebuggerService.LogEntry.Level.INFO)
            Result.success(Unit)
        } catch (e: Exception) {
            DebuggerService.log("DUMPER", "Dump failed: ${e.message}", DebuggerService.LogEntry.Level.ERROR)
            Result.failure(e)
        }
    }
}

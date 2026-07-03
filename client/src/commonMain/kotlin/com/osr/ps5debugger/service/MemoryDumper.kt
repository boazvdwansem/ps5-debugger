package com.osr.ps5debugger.service

import com.osr.ps5debugger.domain.model.MemoryRange
import com.osr.ps5debugger.domain.model.LogEntry
import com.osr.ps5debugger.ports.outbound.DebuggerClientPort
import com.osr.ps5debugger.ports.inbound.DebuggerUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object MemoryDumper {

    suspend fun dumpRegions(
        pid: Int,
        regions: List<MemoryRange>,
        outputDir: File,
        clientPort: DebuggerClientPort,
        useCase: DebuggerUseCase,
        onProgress: (currentRegion: String, progress: Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            println("MemoryDumper: Starting dump of ${regions.size} regions for PID $pid to ${outputDir.absolutePath}")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val chunkSize = 65536 // 64KB chunks for smooth network streaming
            
            regions.forEachIndexed { index, region ->
                val cleanedName = region.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifEmpty { "unnamed" }
                val fileName = String.format("dump_%d_%s_0x%X.bin", pid, cleanedName, region.start)
                val outputFile = File(outputDir, fileName)
                
                useCase.log("DUMPER", "Dumping region ${index + 1}/${regions.size}: $cleanedName (0x${region.start.toString(16)})", LogEntry.Level.INFO)
                
                FileOutputStream(outputFile).use { fos ->
                    var currentAddress = region.start
                    val endAddress = region.end
                    val totalSize = endAddress - currentAddress
                    var bytesDumped = 0L

                    while (currentAddress < endAddress && isActive) {
                        val remaining = endAddress - currentAddress
                        val toRead = minOf(chunkSize.toLong(), remaining).toInt()
                        
                        val data = clientPort.readMemory(pid, currentAddress, toRead)
                        fos.write(data)
                        
                        currentAddress += toRead
                        bytesDumped += toRead
                        
                        val progressFraction = bytesDumped.toFloat() / totalSize.toFloat()
                        onProgress(cleanedName, progressFraction)
                    }
                }
                
                if (!isActive) {
                    useCase.log("DUMPER", "Dump cancelled by user", LogEntry.Level.WARN)
                    return@withContext Result.failure(IOException("Cancelled"))
                }
            }
            useCase.log("DUMPER", "Successfully dumped all selected regions to ${outputDir.absolutePath}", LogEntry.Level.INFO)
            println("MemoryDumper: Successfully finished dump.")
            Result.success(Unit)
        } catch (e: Exception) {
            useCase.log("DUMPER", "Dump failed: ${e.message}", LogEntry.Level.ERROR)
            println("MemoryDumper: Dump failed with error: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
}

package com.osr.ps5debugger.ui

import javax.swing.TransferHandler
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.io.FileOutputStream
import javax.swing.JComponent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import com.osr.ps5debugger.network.Ps5FtpClient

class FileBrowserTransferHandler : TransferHandler() {
    override fun getSourceActions(c: JComponent?): Int {
        return COPY
    }

    override fun createTransferable(c: JComponent?): Transferable? {
        val remoteItems = FileBrowserDragDropHelper.activeDragFiles
        if (remoteItems.isEmpty()) return null

        return object : Transferable {
            override fun getTransferDataFlavors(): Array<DataFlavor> {
                return arrayOf(DataFlavor.javaFileListFlavor)
            }

            override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean {
                return flavor == DataFlavor.javaFileListFlavor
            }

            override fun getTransferData(flavor: DataFlavor?): Any {
                if (flavor != DataFlavor.javaFileListFlavor) {
                    throw UnsupportedFlavorException(flavor)
                }

                val consoleIp = FileBrowserDragDropHelper.consoleIp
                if (consoleIp.isEmpty()) return emptyList<File>()

                val ftpClient = Ps5FtpClient(consoleIp)
                val tempDir = File(System.getProperty("java.io.tmpdir"), "ps5_dragout_" + System.currentTimeMillis())
                tempDir.mkdirs()

                val localFiles = mutableListOf<File>()

                // Total item count for simple progressive feedback
                var currentItem = 0
                val totalItems = remoteItems.size

                javax.swing.SwingUtilities.invokeLater {
                    FileBrowserDragDropHelper.isTransferring = true
                    FileBrowserDragDropHelper.transferProgress = 0f
                    FileBrowserDragDropHelper.transferStatusText = "Preparing download..."
                }

                for (item in remoteItems) {
                    currentItem++
                    val itemIndex = currentItem
                    val name = item.name
                    if (name.isEmpty()) continue

                    javax.swing.SwingUtilities.invokeLater {
                        FileBrowserDragDropHelper.transferStatusText = "Downloading $name ($itemIndex/$totalItems)..."
                        FileBrowserDragDropHelper.transferProgress = (itemIndex - 1).toFloat() / totalItems
                    }

                    val localTarget = File(tempDir, name)
                    if (item.isDirectory) {
                        localTarget.mkdirs()
                        runBlocking(Dispatchers.IO) {
                            try {
                                val remoteFiles = ftpClient.listFiles(item.remotePath)
                                for (f in remoteFiles) {
                                    val subRemotePath = if (item.remotePath.endsWith("/")) "${item.remotePath}${f.name}" else "${item.remotePath}/${f.name}"
                                    downloadRecursive(ftpClient, subRemotePath, f.isDirectory, localTarget)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    } else {
                        runBlocking(Dispatchers.IO) {
                            try {
                                FileOutputStream(localTarget).use { fos ->
                                    ftpClient.downloadFile(item.remotePath, fos)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    localFiles.add(localTarget)
                }

                javax.swing.SwingUtilities.invokeLater {
                    FileBrowserDragDropHelper.isTransferring = false
                    FileBrowserDragDropHelper.transferProgress = 1f
                }

                return localFiles
            }
        }
    }

    private fun downloadRecursive(ftpClient: Ps5FtpClient, remotePath: String, isDirectory: Boolean, localParentDir: File) {
        val name = remotePath.substringAfterLast("/")
        if (name.isEmpty()) return

        val localFile = File(localParentDir, name)
        if (isDirectory) {
            localFile.mkdirs()
            try {
                val remoteFiles = runBlocking(Dispatchers.IO) { ftpClient.listFiles(remotePath) }
                for (f in remoteFiles) {
                    val subRemotePath = if (remotePath.endsWith("/")) "$remotePath${f.name}" else "$remotePath/${f.name}"
                    downloadRecursive(ftpClient, subRemotePath, f.isDirectory, localFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            try {
                runBlocking(Dispatchers.IO) {
                    FileOutputStream(localFile).use { fos ->
                        ftpClient.downloadFile(remotePath, fos)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

package com.osr.ps5debugger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.osr.ps5debugger.MobileMainView
import com.osr.ps5debugger.Ps5DebuggerTheme

class MainActivity : ComponentActivity() {
    private var saveCallback: ((Boolean) -> Unit)? = null
    private var saveContent: String = ""
    
    private val saveLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(saveContent.toByteArray(Charsets.UTF_8))
                }
                saveCallback?.invoke(true)
            } catch (e: Exception) {
                e.printStackTrace()
                saveCallback?.invoke(false)
            }
        } else {
            saveCallback?.invoke(false)
        }
        saveCallback = null
        saveContent = ""
    }

    private var loadCallback: ((String?) -> Unit)? = null

    private val loadLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val content = contentResolver.openInputStream(uri)?.use { ins ->
                    ins.readBytes().toString(Charsets.UTF_8)
                }
                loadCallback?.invoke(content)
            } catch (e: Exception) {
                e.printStackTrace()
                loadCallback?.invoke(null)
            }
        } else {
            loadCallback?.invoke(null)
        }
        loadCallback = null
    }

    private var dirCallback: ((String?) -> Unit)? = null
    private val dirLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
                val split = docId.split(":")
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true)) {
                    val path = android.os.Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                    dirCallback?.invoke(path)
                } else {
                    dirCallback?.invoke(getExternalFilesDir(null)?.absolutePath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                dirCallback?.invoke(getExternalFilesDir(null)?.absolutePath)
            }
        } else {
            dirCallback?.invoke(null)
        }
        dirCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        com.osr.ps5debugger.di.AppContainer.defaultDumpPath = getExternalFilesDir(null)?.absolutePath ?: filesDir.absolutePath
        
        com.osr.ps5debugger.di.AppContainer.filePicker = object : com.osr.ps5debugger.ports.inbound.FilePicker {
            override fun saveJson(defaultName: String, content: String, onResult: (Boolean) -> Unit) {
                saveCallback = onResult
                saveContent = content
                saveLauncher.launch(defaultName)
            }

            override fun loadJson(onResult: (String?) -> Unit) {
                loadCallback = onResult
                loadLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
            }

            override fun pickDirectory(onResult: (String?) -> Unit) {
                dirCallback = onResult
                dirLauncher.launch(null)
            }
        }

        enableEdgeToEdge()
        setContent {
            Ps5DebuggerTheme {
                MobileMainView()
            }
        }
    }
}

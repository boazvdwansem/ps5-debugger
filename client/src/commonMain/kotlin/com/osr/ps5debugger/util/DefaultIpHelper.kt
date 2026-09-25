package com.osr.ps5debugger.util

import java.io.File

object DefaultIpHelper {
    private fun getConfigFile(): File {
        val userHome = System.getProperty("user.home")
        if (!userHome.isNullOrBlank()) {
            val dir = File(userHome, ".ps5debugger")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "config.txt")
            try {
                if (file.exists()) {
                    file.writeText(file.readText()) // check if writable
                } else {
                    file.writeText("\ntrue\n5000")
                }
                return file
            } catch (_: Exception) {}
        }
        val tempDir = System.getProperty("java.io.tmpdir")
        val dir = File(tempDir, ".ps5debugger")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "config.txt")
        if (!file.exists()) {
            try { file.writeText("\ntrue\n5000") } catch (_: Exception) {}
        }
        return file
    }

    private data class Config(
        val ip: String,
        val autoReconnect: Boolean,
        val timeoutMs: Int,
        val theme: String = "Dark",
        val mockEnabled: Boolean = false,
        val payloadPort: Int = 9021,
        val shortcuts: String = "lock_edit:Ctrl+E;copy:Ctrl+C;paste:Ctrl+V;inject:Ctrl+I;goto:G;undo:Ctrl+Z;search:S;watchlist:W;memory:M",
        val mcpEnabled: Boolean = true
    )

    private fun readConfig(): Config {
        return try {
            val file = getConfigFile()
            if (file.exists()) {
                val lines = file.readLines()
                val ip = lines.getOrNull(0)?.trim() ?: ""
                val autoReconnect = lines.getOrNull(1)?.trim()?.toBooleanStrictOrNull() ?: true
                val timeoutMs = lines.getOrNull(2)?.trim()?.toIntOrNull() ?: 5000
                val theme = lines.getOrNull(3)?.trim() ?: "Dark"
                val mockEnabled = lines.getOrNull(4)?.trim()?.toBooleanStrictOrNull() ?: false
                val payloadPort = lines.getOrNull(5)?.trim()?.toIntOrNull() ?: 9021
                val shortcuts = lines.getOrNull(6)?.trim() ?: "lock_edit:Ctrl+E;copy:Ctrl+C;paste:Ctrl+V;inject:Ctrl+I;goto:G;undo:Ctrl+Z;search:S;watchlist:W;memory:M"
                val mcpEnabled = lines.getOrNull(7)?.trim()?.toBooleanStrictOrNull() ?: true
                Config(ip, autoReconnect, timeoutMs, theme, mockEnabled, payloadPort, shortcuts, mcpEnabled)
            } else {
                Config("", true, 5000, "Dark", false, 9021, mcpEnabled = true)
            }
        } catch (_: Exception) {
            Config("", true, 5000, "Dark", false, 9021, mcpEnabled = true)
        }
    }

    private fun writeConfig(config: Config) {
        try {
            val file = getConfigFile()
            file.writeText("${config.ip}\n${config.autoReconnect}\n${config.timeoutMs}\n${config.theme}\n${config.mockEnabled}\n${config.payloadPort}\n${config.shortcuts}\n${config.mcpEnabled}")
        } catch (_: Exception) {}
    }

    fun getDefaultIp(): String? {
        val ip = readConfig().ip
        return if (ip.isNotEmpty()) ip else null
    }

    fun setDefaultIp(ip: String) {
        val current = readConfig()
        writeConfig(current.copy(ip = ip.trim()))
    }

    fun isAutoReconnectEnabled(): Boolean {
        return readConfig().autoReconnect
    }

    fun setAutoReconnectEnabled(enabled: Boolean) {
        val current = readConfig()
        writeConfig(current.copy(autoReconnect = enabled))
    }

    fun getConnectionTimeoutMs(): Int {
        return readConfig().timeoutMs
    }

    fun setConnectionTimeoutMs(timeoutMs: Int) {
        val current = readConfig()
        writeConfig(current.copy(timeoutMs = timeoutMs))
    }

    fun getTheme(): String {
        return readConfig().theme
    }

    fun setTheme(theme: String) {
        val current = readConfig()
        writeConfig(current.copy(theme = theme))
    }

    fun isMockEnabled(): Boolean {
        return readConfig().mockEnabled
    }

    fun setMockEnabled(enabled: Boolean) {
        val current = readConfig()
        writeConfig(current.copy(mockEnabled = enabled))
    }

    fun getPayloadPort(): Int {
        return readConfig().payloadPort
    }

    fun setPayloadPort(port: Int) {
        val current = readConfig()
        writeConfig(current.copy(payloadPort = port))
    }

    fun getShortcuts(): Map<String, String> {
        val shortcutsStr = readConfig().shortcuts
        return shortcutsStr.split(";").filter { it.contains(":") }.associate {
            val parts = it.split(":")
            parts[0] to parts[1]
        }
    }

    fun setShortcuts(shortcuts: Map<String, String>) {
        val shortcutsStr = shortcuts.entries.joinToString(";") { "${it.key}:${it.value}" }
        val current = readConfig()
        writeConfig(current.copy(shortcuts = shortcutsStr))
    }

    fun isMcpEnabled(): Boolean {
        return readConfig().mcpEnabled
    }

    fun setMcpEnabled(enabled: Boolean) {
        val current = readConfig()
        writeConfig(current.copy(mcpEnabled = enabled))
    }
}

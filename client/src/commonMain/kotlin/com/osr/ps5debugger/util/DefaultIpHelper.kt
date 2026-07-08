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
        val timeoutMs: Int
    )

    private fun readConfig(): Config {
        return try {
            val file = getConfigFile()
            if (file.exists()) {
                val lines = file.readLines()
                val ip = lines.getOrNull(0)?.trim() ?: ""
                val autoReconnect = lines.getOrNull(1)?.trim()?.toBooleanStrictOrNull() ?: true
                val timeoutMs = lines.getOrNull(2)?.trim()?.toIntOrNull() ?: 5000
                Config(ip, autoReconnect, timeoutMs)
            } else {
                Config("", true, 5000)
            }
        } catch (_: Exception) {
            Config("", true, 5000)
        }
    }

    private fun writeConfig(config: Config) {
        try {
            val file = getConfigFile()
            file.writeText("${config.ip}\n${config.autoReconnect}\n${config.timeoutMs}")
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
}

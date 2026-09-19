package com.osr.ps5debugger.util

data class AppMetadata(
    val titleId: String,
    val name: String,
    val platform: String // "PS4" or "PS5"
)

expect class SqliteReader(dbPath: String) {
    fun queryAppMetadata(): List<AppMetadata>
    fun close()
}

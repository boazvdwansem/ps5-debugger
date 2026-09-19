package com.osr.ps5debugger.util

import java.sql.DriverManager
import java.sql.Connection

actual class SqliteReader actual constructor(private val dbPath: String) {
    private var connection: Connection? = null

    init {
        try {
            Class.forName("org.sqlite.JDBC")
            connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    actual fun queryAppMetadata(): List<AppMetadata> {
        val list = mutableListOf<AppMetadata>()
        val conn = connection ?: return emptyList()
        
        try {
            // Find the table name starting with tbl_concepticoninfo_
            val tableRs = conn.metaData.getTables(null, null, "tbl_concepticoninfo_%", null)
            var tableName: String? = null
            if (tableRs.next()) {
                tableName = tableRs.getString("TABLE_NAME")
            }
            tableRs.close()

            if (tableName != null) {
                val stmt = conn.createStatement()
                val rs = stmt.executeQuery("SELECT primaryTitleId, primaryTitleName, primaryTitlePlatform FROM $tableName")
                while (rs.next()) {
                    val tid = rs.getString("primaryTitleId")
                    val name = rs.getString("primaryTitleName")
                    val platRaw = rs.getInt("primaryTitlePlatform")
                    val plat = if (platRaw == 0) "PS5" else "PS4"
                    
                    if (!tid.isNullOrEmpty() && !name.isNullOrEmpty()) {
                        list.add(AppMetadata(tid, name, plat))
                    }
                }
                rs.close()
                stmt.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    actual fun close() {
        try {
            connection?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

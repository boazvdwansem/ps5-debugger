package com.osr.ps5debugger.util

import android.database.sqlite.SQLiteDatabase

actual class SqliteReader actual constructor(private val dbPath: String) {
    private var database: SQLiteDatabase? = null

    init {
        try {
            database = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    actual fun queryAppMetadata(): List<AppMetadata> {
        val list = mutableListOf<AppMetadata>()
        val db = database ?: return emptyList()
        
        try {
            // Find the table name starting with tbl_concepticoninfo_
            val cursorTable = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'tbl_concepticoninfo_%'", null)
            var tableName: String? = null
            if (cursorTable.moveToFirst()) {
                tableName = cursorTable.getString(0)
            }
            cursorTable.close()

            if (tableName != null) {
                val cursor = db.rawQuery("SELECT primaryTitleId, primaryTitleName, primaryTitlePlatform FROM $tableName", null)
                val idIdx = cursor.getColumnIndex("primaryTitleId")
                val nameIdx = cursor.getColumnIndex("primaryTitleName")
                val platIdx = cursor.getColumnIndex("primaryTitlePlatform")
                
                while (cursor.moveToNext()) {
                    val tid = cursor.getString(idIdx)
                    val name = cursor.getString(nameIdx)
                    val platRaw = cursor.getInt(platIdx)
                    val plat = if (platRaw == 0) "PS5" else "PS4"
                    
                    if (!tid.isNullOrEmpty() && !name.isNullOrEmpty()) {
                        list.add(AppMetadata(tid, name, plat))
                    }
                }
                cursor.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    actual fun close() {
        try {
            database?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

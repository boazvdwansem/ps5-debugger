package com.osr.ps5debugger.domain.model

data class WatchItem(
    val label: String,
    val address: Long,
    val type: String, // "Byte", "Int16", "Int32", "Int64", "Float", "Double", "String"
    val valueStr: String = "??",
    val isFrozen: Boolean = false,
    val comment: String = "",
    val byteLength: Int? = null
)

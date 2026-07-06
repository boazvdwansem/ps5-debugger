package com.osr.ps5debugger.util

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

fun copyToClipboard(text: String) {
    try {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(text), null)
    } catch (_: Exception) {}
}

fun getFromClipboard(): String {
    return try {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.getData(DataFlavor.stringFlavor) as String
    } catch (_: Exception) {
        ""
    }
}

package com.osr.ps5debugger.ports.inbound

interface FilePicker {
    fun saveJson(defaultName: String, content: String, onResult: (Boolean) -> Unit)
    fun loadJson(onResult: (String?) -> Unit)
    fun pickDirectory(onResult: (String?) -> Unit)
}

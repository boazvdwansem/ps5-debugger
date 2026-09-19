package com.osr.ps5debugger.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.jetbrains.skia.Image

actual fun decodeImage(bytes: ByteArray): ImageBitmap? {
    return try {
        Image.makeFromEncoded(bytes).asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

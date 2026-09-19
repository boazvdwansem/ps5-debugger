package com.osr.ps5debugger.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory

actual fun decodeImage(bytes: ByteArray): ImageBitmap? {
    return try {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

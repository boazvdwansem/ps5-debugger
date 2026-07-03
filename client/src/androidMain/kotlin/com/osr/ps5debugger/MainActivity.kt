package com.osr.ps5debugger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.osr.ps5debugger.MobileMainView
import com.osr.ps5debugger.Ps5DebuggerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            Ps5DebuggerTheme {
                MobileMainView()
            }
        }
    }
}

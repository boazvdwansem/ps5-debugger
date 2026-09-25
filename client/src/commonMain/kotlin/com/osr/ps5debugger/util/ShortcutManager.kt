package com.osr.ps5debugger.util

import androidx.compose.ui.input.key.*

object ShortcutManager {
    fun isMatch(event: KeyEvent, shortcut: String?): Boolean {
        if (shortcut.isNullOrBlank() || event.type != KeyEventType.KeyDown) return false
        
        return try {
            val parts = shortcut.split("+").map { it.trim().lowercase() }
            val hasCtrl = parts.contains("ctrl")
            val hasShift = parts.contains("shift")
            val hasAlt = parts.contains("alt")
            val keyPart = parts.lastOrNull() ?: return false

            val eventCtrl = event.isCtrlPressed
            val eventShift = event.isShiftPressed
            val eventAlt = event.isAltPressed
            
            if (hasCtrl != eventCtrl || hasShift != eventShift || hasAlt != eventAlt) return false
            
            val eventKey = event.key
            when (keyPart) {
                "a" -> eventKey == Key.A
                "b" -> eventKey == Key.B
                "c" -> eventKey == Key.C
                "d" -> eventKey == Key.D
                "e" -> eventKey == Key.E
                "f" -> eventKey == Key.F
                "g" -> eventKey == Key.G
                "h" -> eventKey == Key.H
                "i" -> eventKey == Key.I
                "j" -> eventKey == Key.J
                "k" -> eventKey == Key.K
                "l" -> eventKey == Key.L
                "m" -> eventKey == Key.M
                "n" -> eventKey == Key.N
                "o" -> eventKey == Key.O
                "p" -> eventKey == Key.P
                "q" -> eventKey == Key.Q
                "r" -> eventKey == Key.R
                "s" -> eventKey == Key.S
                "t" -> eventKey == Key.T
                "u" -> eventKey == Key.U
                "v" -> eventKey == Key.V
                "w" -> eventKey == Key.W
                "x" -> eventKey == Key.X
                "y" -> eventKey == Key.Y
                "z" -> eventKey == Key.Z
                "0" -> eventKey == Key.Zero
                "1" -> eventKey == Key.One
                "2" -> eventKey == Key.Two
                "3" -> eventKey == Key.Three
                "4" -> eventKey == Key.Four
                "5" -> eventKey == Key.Five
                "6" -> eventKey == Key.Six
                "7" -> eventKey == Key.Seven
                "8" -> eventKey == Key.Eight
                "9" -> eventKey == Key.Nine
                "tab" -> eventKey == Key.Tab
                "space" -> eventKey == Key.Spacebar
                "enter" -> eventKey == Key.Enter
                "escape" -> eventKey == Key.Escape
                "f1" -> eventKey == Key.F1
                "f2" -> eventKey == Key.F2
                "f3" -> eventKey == Key.F3
                "f4" -> eventKey == Key.F4
                "f5" -> eventKey == Key.F5
                "f6" -> eventKey == Key.F6
                "f7" -> eventKey == Key.F7
                "f8" -> eventKey == Key.F8
                "f9" -> eventKey == Key.F9
                "f10" -> eventKey == Key.F10
                "f11" -> eventKey == Key.F11
                "f12" -> eventKey == Key.F12
                else -> false
            }
        } catch (_: Throwable) {
            false
        }
    }
}

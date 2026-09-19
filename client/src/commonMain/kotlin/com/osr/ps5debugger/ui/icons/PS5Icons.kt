package com.osr.ps5debugger.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom modern vector icon suite for PS5 Remote Debugger.
 * Every icon is handcrafted as a distinct, unified 24x24 vector with consistent
 * 2dp stroke geometry, rounded joins and caps, and sleek PlayStation developer styling.
 */
object PS5Icons {
    private inline fun icon(
        name: String,
        crossinline builder: ImageVector.Builder.() -> Unit
    ): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(builder).build()
    }

    /**
     * Host PC and PS5 console link with communication cable and data pulse
     */
    val Connections: ImageVector by lazy {
        icon("Connections") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // PC monitor outline & stand
                moveTo(3f, 5f)
                lineTo(9f, 5f)
                arcTo(1f, 1f, 0f, false, true, 10f, 6f)
                lineTo(10f, 11f)
                arcTo(1f, 1f, 0f, false, true, 9f, 12f)
                lineTo(3f, 12f)
                arcTo(1f, 1f, 0f, false, true, 2f, 11f)
                lineTo(2f, 6f)
                arcTo(1f, 1f, 0f, false, true, 3f, 5f)
                close()
                
                moveTo(6f, 12f); lineTo(6f, 15f)
                moveTo(4f, 15f); lineTo(8f, 15f)
                
                // PS5 tower profile
                moveTo(17f, 4f)
                curveTo(17.5f, 8f, 17.5f, 14f, 17f, 18f)
                lineTo(21f, 18f)
                curveTo(20.5f, 14f, 20.5f, 8f, 21f, 4f)
                close()
                
                moveTo(19f, 8f); lineTo(19f, 14f)
                
                // Connecting data link
                moveTo(8f, 8.5f)
                lineTo(12f, 8.5f)
                arcTo(2f, 2f, 0f, false, true, 14f, 10.5f)
                lineTo(14f, 12.5f)
                arcTo(2f, 2f, 0f, false, false, 16f, 14.5f)
                lineTo(17f, 14.5f)
                
                // Node dot
                moveTo(12f, 11.5f)
                arcTo(1f, 1f, 0f, true, true, 12f, 9.5f)
                arcTo(1f, 1f, 0f, true, true, 12f, 11.5f)
                close()
            }
        }
    }

    /**
     * Segmented memory address space regions with address boundary markers
     */
    val MemoryMap: ImageVector by lazy {
        icon("MemoryMap") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Outer memory address space container
                moveTo(5f, 3f)
                lineTo(19f, 3f)
                arcTo(2f, 2f, 0f, false, true, 21f, 5f)
                lineTo(21f, 19f)
                arcTo(2f, 2f, 0f, false, true, 19f, 21f)
                lineTo(5f, 21f)
                arcTo(2f, 2f, 0f, false, true, 3f, 19f)
                lineTo(3f, 5f)
                arcTo(2f, 2f, 0f, false, true, 5f, 3f)
                close()
                
                // Region partition boundaries
                moveTo(3f, 9f); lineTo(21f, 9f)
                moveTo(3f, 15f); lineTo(21f, 15f)
                
                // Address gutter division line
                moveTo(8f, 3f); lineTo(8f, 21f)
                
                // Region memory bars
                moveTo(11f, 6f); lineTo(18f, 6f)
                moveTo(11f, 12f); lineTo(16f, 12f)
                moveTo(11f, 18f); lineTo(17f, 18f)
            }
        }
    }

    /**
     * Symbol lookup and function export badge f(x)
     */
    val Symbols: ImageVector by lazy {
        icon("Symbols") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Badge container
                moveTo(6f, 4f)
                lineTo(18f, 4f)
                arcTo(2f, 2f, 0f, false, true, 20f, 6f)
                lineTo(20f, 18f)
                arcTo(2f, 2f, 0f, false, true, 18f, 20f)
                lineTo(6f, 20f)
                arcTo(2f, 2f, 0f, false, true, 4f, 18f)
                lineTo(4f, 6f)
                arcTo(2f, 2f, 0f, false, true, 6f, 4f)
                close()
                
                // Math function 'f'
                moveTo(11f, 8f)
                curveTo(10.2f, 8f, 9.5f, 8.5f, 9.5f, 9.5f)
                lineTo(9.5f, 16f)
                moveTo(7.5f, 11f)
                lineTo(12.5f, 11f)
                
                // Subscript 'x'
                moveTo(14f, 13f); lineTo(17f, 17f)
                moveTo(17f, 13f); lineTo(14f, 17f)
            }
        }
    }

    /**
     * Hexadecimal memory viewer table with address offset column and byte cells
     */
    val MemoryViewer: ImageVector by lazy {
        icon("MemoryViewer") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Editor outer frame
                moveTo(4f, 4f)
                lineTo(20f, 4f)
                arcTo(2f, 2f, 0f, false, true, 22f, 6f)
                lineTo(22f, 18f)
                arcTo(2f, 2f, 0f, false, true, 20f, 20f)
                lineTo(4f, 20f)
                arcTo(2f, 2f, 0f, false, true, 2f, 18f)
                lineTo(2f, 6f)
                arcTo(2f, 2f, 0f, false, true, 4f, 4f)
                close()
                
                // Address gutter division
                moveTo(7f, 4f); lineTo(7f, 20f)
                
                // Address ticks in left gutter
                moveTo(4f, 8f); lineTo(5.5f, 8f)
                moveTo(4f, 12f); lineTo(5.5f, 12f)
                moveTo(4f, 16f); lineTo(5.5f, 16f)
                
                // Byte matrix indicators
                // Row 1
                moveTo(10.5f, 8.5f); arcTo(0.8f, 0.8f, 0f, true, true, 10.5f, 7.5f); arcTo(0.8f, 0.8f, 0f, true, true, 10.5f, 8.5f); close()
                moveTo(14.5f, 8.5f); arcTo(0.8f, 0.8f, 0f, true, true, 14.5f, 7.5f); arcTo(0.8f, 0.8f, 0f, true, true, 14.5f, 8.5f); close()
                moveTo(18.5f, 8.5f); arcTo(0.8f, 0.8f, 0f, true, true, 18.5f, 7.5f); arcTo(0.8f, 0.8f, 0f, true, true, 18.5f, 8.5f); close()
                
                // Row 2
                moveTo(10.5f, 12.5f); arcTo(0.8f, 0.8f, 0f, true, true, 10.5f, 11.5f); arcTo(0.8f, 0.8f, 0f, true, true, 10.5f, 12.5f); close()
                moveTo(14.5f, 12.5f); arcTo(0.8f, 0.8f, 0f, true, true, 14.5f, 11.5f); arcTo(0.8f, 0.8f, 0f, true, true, 14.5f, 12.5f); close()
                moveTo(18.5f, 12.5f); arcTo(0.8f, 0.8f, 0f, true, true, 18.5f, 11.5f); arcTo(0.8f, 0.8f, 0f, true, true, 18.5f, 12.5f); close()
                
                // Row 3
                moveTo(10.5f, 16.5f); arcTo(0.8f, 0.8f, 0f, true, true, 10.5f, 15.5f); arcTo(0.8f, 0.8f, 0f, true, true, 10.5f, 16.5f); close()
                moveTo(14.5f, 16.5f); arcTo(0.8f, 0.8f, 0f, true, true, 14.5f, 15.5f); arcTo(0.8f, 0.8f, 0f, true, true, 14.5f, 16.5f); close()
                moveTo(18.5f, 16.5f); arcTo(0.8f, 0.8f, 0f, true, true, 18.5f, 15.5f); arcTo(0.8f, 0.8f, 0f, true, true, 18.5f, 16.5f); close()
            }
        }
    }

    /**
     * Radar scanner scope searching memory values with targeting crosshair
     */
    val MemoryScan: ImageVector by lazy {
        icon("MemoryScan") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Radar scope outer circle (centered at 11, 11 with radius 8)
                moveTo(11f, 19f)
                arcTo(8f, 8f, 0f, true, true, 11f, 3f)
                arcTo(8f, 8f, 0f, true, true, 11f, 19f)
                close()
                
                // Radar inner circle (radius 4)
                moveTo(11f, 15f)
                arcTo(4f, 4f, 0f, true, true, 11f, 7f)
                arcTo(4f, 4f, 0f, true, true, 11f, 15f)
                close()
                
                // Scope crosshairs
                moveTo(11f, 3f); lineTo(11f, 19f)
                moveTo(3f, 11f); lineTo(19f, 11f)
                
                // Sweep ray
                moveTo(11f, 11f); lineTo(16.5f, 5.5f)
                
                // Target detected blip
                moveTo(15f, 9f)
                arcTo(1f, 1f, 0f, true, true, 15f, 7f)
                arcTo(1f, 1f, 0f, true, true, 15f, 9f)
                close()
                
                // Base / scanner grip
                moveTo(17f, 17f); lineTo(22f, 22f)
            }
        }
    }

    /**
     * Live variable monitor with active heartbeat pulse gauge and pin target
     */
    val WatchList: ImageVector by lazy {
        icon("WatchList") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Monitor gauge screen frame
                moveTo(5f, 4f)
                lineTo(19f, 4f)
                arcTo(3f, 3f, 0f, false, true, 22f, 7f)
                lineTo(22f, 17f)
                arcTo(3f, 3f, 0f, false, true, 19f, 20f)
                lineTo(5f, 20f)
                arcTo(3f, 3f, 0f, false, true, 2f, 17f)
                lineTo(2f, 7f)
                arcTo(3f, 3f, 0f, false, true, 5f, 4f)
                close()
                
                // Live heartbeat/variable telemetry pulse line
                moveTo(2f, 12f)
                lineTo(7f, 12f)
                lineTo(9f, 7f)
                lineTo(12f, 17f)
                lineTo(14.5f, 11f)
                lineTo(16f, 13f)
                lineTo(17f, 12f)
                lineTo(22f, 12f)
                
                // Live status dot
                moveTo(18f, 8f)
                arcTo(1.2f, 1.2f, 0f, true, true, 18f, 6f)
                arcTo(1.2f, 1.2f, 0f, true, true, 18f, 8f)
                close()
            }
        }
    }

    /**
     * RAM memory dump stream transferring down to persistent storage drive
     */
    val MemoryDumper: ImageVector by lazy {
        icon("MemoryDumper") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // RAM memory module
                moveTo(5f, 3f)
                lineTo(19f, 3f)
                arcTo(1f, 1f, 0f, false, true, 20f, 4f)
                lineTo(20f, 7f)
                arcTo(1f, 1f, 0f, false, true, 19f, 8f)
                lineTo(5f, 8f)
                arcTo(1f, 1f, 0f, false, true, 4f, 7f)
                lineTo(4f, 4f)
                arcTo(1f, 1f, 0f, false, true, 5f, 3f)
                close()
                
                // RAM module chip contacts
                moveTo(7f, 5.5f); lineTo(9f, 5.5f)
                moveTo(11f, 5.5f); lineTo(13f, 5.5f)
                moveTo(15f, 5.5f); lineTo(17f, 5.5f)
                
                // Downward data dump arrow
                moveTo(12f, 8f); lineTo(12f, 14f)
                moveTo(9.5f, 11.5f); lineTo(12f, 14f); lineTo(14.5f, 11.5f)
                
                // Storage disk platter at bottom
                moveTo(4.5f, 16f)
                lineTo(19.5f, 16f)
                arcTo(1.5f, 1.5f, 0f, false, true, 21f, 17.5f)
                lineTo(21f, 19.5f)
                arcTo(1.5f, 1.5f, 0f, false, true, 19.5f, 21f)
                lineTo(4.5f, 21f)
                arcTo(1.5f, 1.5f, 0f, false, true, 3f, 19.5f)
                lineTo(3f, 17.5f)
                arcTo(1.5f, 1.5f, 0f, false, true, 4.5f, 16f)
                close()
                
                // Drive LED & track
                moveTo(6f, 18.5f); lineTo(12f, 18.5f)
                moveTo(17f, 19.3f)
                arcTo(0.8f, 0.8f, 0f, true, true, 17f, 17.7f)
                arcTo(0.8f, 0.8f, 0f, true, true, 17f, 19.3f)
                close()
            }
        }
    }

    /**
     * PlayStation DualSense gamepad controller with D-pad, thumbsticks, and action buttons
     */
    val Cheats: ImageVector by lazy {
        icon("Cheats") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Controller ergonomic body
                moveTo(6f, 7f)
                lineTo(18f, 7f)
                arcTo(4f, 4f, 0f, false, true, 22f, 11f)
                lineTo(22f, 14f)
                curveTo(22f, 17f, 20.5f, 19f, 18.5f, 19.5f)
                curveTo(16.5f, 20f, 15f, 18.5f, 14f, 16.5f)
                lineTo(10f, 16.5f)
                curveTo(9f, 18.5f, 7.5f, 20f, 5.5f, 19.5f)
                curveTo(3.5f, 19f, 2f, 17f, 2f, 14f)
                lineTo(2f, 11f)
                arcTo(4f, 4f, 0f, false, true, 6f, 7f)
                close()
                
                // D-Pad
                moveTo(6.5f, 11.5f); lineTo(9.5f, 11.5f)
                moveTo(8f, 10f); lineTo(8f, 13f)
                
                // Action buttons
                moveTo(16f, 11.3f)
                arcTo(0.8f, 0.8f, 0f, true, true, 16f, 9.7f)
                arcTo(0.8f, 0.8f, 0f, true, true, 16f, 11.3f)
                close()
                
                moveTo(17.5f, 13.3f)
                arcTo(0.8f, 0.8f, 0f, true, true, 17.5f, 11.7f)
                arcTo(0.8f, 0.8f, 0f, true, true, 17.5f, 13.3f)
                close()
                
                // Twin analog thumbsticks
                moveTo(9f, 16.5f)
                arcTo(1.5f, 1.5f, 0f, true, true, 9f, 13.5f)
                arcTo(1.5f, 1.5f, 0f, true, true, 9f, 16.5f)
                close()
                
                moveTo(15f, 16.5f)
                arcTo(1.5f, 1.5f, 0f, true, true, 15f, 13.5f)
                arcTo(1.5f, 1.5f, 0f, true, true, 15f, 16.5f)
                close()
            }
        }
    }

    /**
     * File manager explorer displaying directory tree hierarchy
     */
    val FileBrowser: ImageVector by lazy {
        icon("FileBrowser") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Top parent folder
                moveTo(3f, 5f)
                lineTo(8f, 5f)
                lineTo(10f, 7f)
                lineTo(16f, 7f)
                arcTo(2f, 2f, 0f, false, true, 18f, 9f)
                lineTo(18f, 11f)
                lineTo(3f, 11f)
                close()
                
                // File tree branches
                moveTo(6f, 11f); lineTo(6f, 20f)
                moveTo(6f, 14.5f); lineTo(10f, 14.5f)
                moveTo(6f, 19.5f); lineTo(10f, 19.5f)
                
                // Sub-item files
                moveTo(12f, 13f)
                lineTo(19f, 13f)
                arcTo(1f, 1f, 0f, false, true, 20f, 14f)
                lineTo(20f, 15f)
                arcTo(1f, 1f, 0f, false, true, 19f, 16f)
                lineTo(12f, 16f)
                arcTo(1f, 1f, 0f, false, true, 11f, 15f)
                lineTo(11f, 14f)
                arcTo(1f, 1f, 0f, false, true, 12f, 13f)
                close()
                
                moveTo(12f, 18f)
                lineTo(19f, 18f)
                arcTo(1f, 1f, 0f, false, true, 20f, 19f)
                lineTo(20f, 20f)
                arcTo(1f, 1f, 0f, false, true, 19f, 21f)
                lineTo(12f, 21f)
                arcTo(1f, 1f, 0f, false, true, 11f, 20f)
                lineTo(11f, 19f)
                arcTo(1f, 1f, 0f, false, true, 12f, 18f)
                close()
            }
        }
    }

    /**
     * Process manager showing active system tasks and CPU thread activity
     */
    val Process: ImageVector by lazy {
        icon("Process") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Task window container
                moveTo(5f, 3f)
                lineTo(19f, 3f)
                arcTo(2f, 2f, 0f, false, true, 21f, 5f)
                lineTo(21f, 19f)
                arcTo(2f, 2f, 0f, false, true, 19f, 21f)
                lineTo(5f, 21f)
                arcTo(2f, 2f, 0f, false, true, 3f, 19f)
                lineTo(3f, 5f)
                arcTo(2f, 2f, 0f, false, true, 5f, 3f)
                close()
                
                // Window title header bar
                moveTo(3f, 8f); lineTo(21f, 8f)
                
                // Window control dots
                moveTo(6.8f, 5.5f)
                arcTo(0.8f, 0.8f, 0f, true, true, 5.2f, 5.5f)
                arcTo(0.8f, 0.8f, 0f, true, true, 6.8f, 5.5f)
                close()
                
                moveTo(9.8f, 5.5f)
                arcTo(0.8f, 0.8f, 0f, true, true, 8.2f, 5.5f)
                arcTo(0.8f, 0.8f, 0f, true, true, 9.8f, 5.5f)
                close()
                
                // CPU thread execution pulse
                moveTo(6f, 14f)
                lineTo(8f, 14f)
                lineTo(9.5f, 11f)
                lineTo(11.5f, 17f)
                lineTo(13f, 14f)
                lineTo(18f, 14f)
            }
        }
    }

    /**
     * CPU processor with circuit pins and execution core
     */
    val DebuggerControl: ImageVector by lazy {
        icon("DebuggerControl") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Microprocessor die package
                moveTo(8f, 6f)
                lineTo(16f, 6f)
                arcTo(2f, 2f, 0f, false, true, 18f, 8f)
                lineTo(18f, 16f)
                arcTo(2f, 2f, 0f, false, true, 16f, 18f)
                lineTo(8f, 18f)
                arcTo(2f, 2f, 0f, false, true, 6f, 16f)
                lineTo(6f, 8f)
                arcTo(2f, 2f, 0f, false, true, 8f, 6f)
                close()
                
                // Internal execution core play triangle
                moveTo(10f, 9.5f)
                lineTo(15f, 12f)
                lineTo(10f, 14.5f)
                close()
                
                // Pin traces
                moveTo(9f, 2f); lineTo(9f, 6f)
                moveTo(15f, 2f); lineTo(15f, 6f)
                moveTo(9f, 18f); lineTo(9f, 22f)
                moveTo(15f, 18f); lineTo(15f, 22f)
                moveTo(2f, 9f); lineTo(6f, 9f)
                moveTo(2f, 15f); lineTo(6f, 15f)
                moveTo(18f, 9f); lineTo(22f, 9f)
                moveTo(18f, 15f); lineTo(22f, 15f)
            }
        }
    }

    /**
     * Breakpoint stop octagon with pause bars
     */
    val Breakpoints: ImageVector by lazy {
        icon("Breakpoints") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Octagonal breakpoint perimeter
                moveTo(8f, 3f)
                lineTo(16f, 3f)
                lineTo(21f, 8f)
                lineTo(21f, 16f)
                lineTo(16f, 21f)
                lineTo(8f, 21f)
                lineTo(3f, 16f)
                lineTo(3f, 8f)
                close()
                
                // Centered halt bars
                moveTo(10f, 9f); lineTo(10f, 15f)
                moveTo(14f, 9f); lineTo(14f, 15f)
            }
        }
    }

    /**
     * Cross references directed call graph showing code caller/callee links
     */
    val References: ImageVector by lazy {
        icon("References") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Source node
                moveTo(5f, 15f)
                arcTo(3f, 3f, 0f, true, true, 5f, 9f)
                arcTo(3f, 3f, 0f, true, true, 5f, 15f)
                close()
                
                // Target node 1 (upper right)
                moveTo(19f, 8.5f)
                arcTo(2.5f, 2.5f, 0f, true, true, 19f, 3.5f)
                arcTo(2.5f, 2.5f, 0f, true, true, 19f, 8.5f)
                close()
                
                // Target node 2 (lower right)
                moveTo(19f, 20.5f)
                arcTo(2.5f, 2.5f, 0f, true, true, 19f, 15.5f)
                arcTo(2.5f, 2.5f, 0f, true, true, 19f, 20.5f)
                close()
                
                // Edge 1 with arrow
                moveTo(8f, 11f); lineTo(16.5f, 6.8f)
                moveTo(14f, 6f); lineTo(16.5f, 6.8f); lineTo(15.5f, 9.5f)
                
                // Edge 2 with arrow
                moveTo(8f, 13f); lineTo(16.5f, 17.2f)
                moveTo(15.5f, 14.5f); lineTo(16.5f, 17.2f); lineTo(14f, 18f)
            }
        }
    }

    /**
     * Console shell log window with interactive prompt and cursor
     */
    val Terminal: ImageVector by lazy {
        icon("Terminal") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Console window frame
                moveTo(4.5f, 4f)
                lineTo(19.5f, 4f)
                arcTo(2f, 2f, 0f, false, true, 21.5f, 6f)
                lineTo(21.5f, 18f)
                arcTo(2f, 2f, 0f, false, true, 19.5f, 20f)
                lineTo(4.5f, 20f)
                arcTo(2f, 2f, 0f, false, true, 2.5f, 18f)
                lineTo(2.5f, 6f)
                arcTo(2f, 2f, 0f, false, true, 4.5f, 4f)
                close()
                
                // Prompt arrow '>'
                moveTo(6.5f, 9.5f)
                lineTo(9.5f, 12f)
                lineTo(6.5f, 14.5f)
                
                // Cursor line '_'
                moveTo(12f, 15f); lineTo(16.5f, 15f)
            }
        }
    }

    /**
     * Precision mechanical cog gear with 6 rounded teeth and center axle bore
     */
    val Settings: ImageVector by lazy {
        icon("Settings") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Cog wheel teeth profile
                moveTo(10.5f, 2.5f)
                lineTo(13.5f, 2.5f)
                lineTo(14.2f, 4.8f)
                lineTo(16.2f, 5.6f)
                lineTo(18.3f, 4.3f)
                lineTo(20.4f, 6.4f)
                lineTo(19.1f, 8.5f)
                lineTo(19.9f, 10.5f)
                lineTo(22.2f, 11.2f)
                lineTo(22.2f, 14.2f)
                lineTo(19.9f, 14.9f)
                lineTo(19.1f, 16.9f)
                lineTo(20.4f, 19f)
                lineTo(18.3f, 21.1f)
                lineTo(16.2f, 19.8f)
                lineTo(14.2f, 20.6f)
                lineTo(13.5f, 22.9f)
                lineTo(10.5f, 22.9f)
                lineTo(9.8f, 20.6f)
                lineTo(7.8f, 19.8f)
                lineTo(5.7f, 21.1f)
                lineTo(3.6f, 19f)
                lineTo(4.9f, 16.9f)
                lineTo(4.1f, 14.9f)
                lineTo(1.8f, 14.2f)
                lineTo(1.8f, 11.2f)
                lineTo(4.1f, 10.5f)
                lineTo(4.9f, 8.5f)
                lineTo(3.6f, 6.4f)
                lineTo(5.7f, 4.3f)
                lineTo(7.8f, 5.6f)
                lineTo(9.8f, 4.8f)
                close()
                
                // Center hub
                moveTo(12f, 15f)
                arcTo(3f, 3f, 0f, true, true, 12f, 9f)
                arcTo(3f, 3f, 0f, true, true, 12f, 15f)
                close()
            }
        }
    }

    /**
     * Standard panel close and dismiss cross
     */
    val Close: ImageVector by lazy {
        icon("Close") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 5f); lineTo(19f, 19f)
                moveTo(19f, 5f); lineTo(5f, 19f)
            }
        }
    }

    /**
     * Search field clear button: circular badge containing an inner cross
     */
    val Clear: ImageVector by lazy {
        icon("Clear") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Circular badge perimeter
                moveTo(12f, 21f)
                arcTo(9f, 9f, 0f, true, true, 12f, 3f)
                arcTo(9f, 9f, 0f, true, true, 12f, 21f)
                close()
                
                // Inner cross
                moveTo(9f, 9f); lineTo(15f, 15f)
                moveTo(15f, 9f); lineTo(9f, 15f)
            }
        }
    }

    /**
     * Circular double-arrow sync reload cycle
     */
    val Refresh: ImageVector by lazy {
        icon("Refresh") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Top arrow head
                moveTo(21f, 4f); lineTo(21f, 9f); lineTo(16f, 9f)
                
                // Top arc
                moveTo(20.5f, 10.5f)
                arcTo(8.5f, 8.5f, 0f, false, false, 5.5f, 7f)
                
                // Bottom arrow head
                moveTo(3f, 20f); lineTo(3f, 15f); lineTo(8f, 15f)
                
                // Bottom arc
                moveTo(3.5f, 13.5f)
                arcTo(8.5f, 8.5f, 0f, false, false, 18.5f, 17f)
            }
        }
    }

    /**
     * Leftward navigation back arrow
     */
    val ArrowBack: ImageVector by lazy {
        icon("ArrowBack") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(19f, 12f); lineTo(5f, 12f)
                moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
            }
        }
    }

    /**
     * Centered plus action symbol
     */
    val Add: ImageVector by lazy {
        icon("Add") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 4f); lineTo(12f, 20f)
                moveTo(4f, 12f); lineTo(20f, 12f)
            }
        }
    }

    /**
     * Magnifying glass search query and filter icon
     */
    val Search: ImageVector by lazy {
        icon("Search") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Lens
                moveTo(10.5f, 17f)
                arcTo(6.5f, 6.5f, 0f, true, true, 10.5f, 4f)
                arcTo(6.5f, 6.5f, 0f, true, true, 10.5f, 17f)
                close()
                
                // Handle
                moveTo(15.5f, 15.5f); lineTo(21f, 21f)
            }
        }
    }

    /**
     * Trash waste bin with lid handle and ribbed container
     */
    val Delete: ImageVector by lazy {
        icon("Delete") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Lid
                moveTo(3f, 6f); lineTo(21f, 6f)
                moveTo(9f, 6f); lineTo(9f, 4f)
                arcTo(1f, 1f, 0f, false, true, 10f, 3f)
                lineTo(14f, 3f)
                arcTo(1f, 1f, 0f, false, true, 15f, 4f)
                lineTo(15f, 6f)
                
                // Can body
                moveTo(5.5f, 6f)
                lineTo(6.7f, 19.5f)
                arcTo(1.5f, 1.5f, 0f, false, false, 8.2f, 21f)
                lineTo(15.8f, 21f)
                arcTo(1.5f, 1.5f, 0f, false, false, 17.3f, 19.5f)
                lineTo(18.5f, 6f)
                
                // Ribs
                moveTo(10f, 10f); lineTo(10f, 16f)
                moveTo(14f, 10f); lineTo(14f, 16f)
            }
        }
    }

    /**
     * Directory folder with creation plus badge in corner
     */
    val NewFolder: ImageVector by lazy {
        icon("NewFolder") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Folder frame
                moveTo(5f, 5f)
                lineTo(9f, 5f)
                lineTo(11f, 7f)
                lineTo(19f, 7f)
                arcTo(2f, 2f, 0f, false, true, 21f, 9f)
                lineTo(21f, 12f)
                
                moveTo(3f, 7f)
                lineTo(3f, 18f)
                arcTo(2f, 2f, 0f, false, false, 5f, 20f)
                lineTo(12f, 20f)
                
                // Plus badge
                moveTo(18f, 14f); lineTo(18f, 20f)
                moveTo(15f, 17f); lineTo(21f, 17f)
            }
        }
    }

    /**
     * Upload file transfer arrow emerging from tray
     */
    val Upload: ImageVector by lazy {
        icon("Upload") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Arrow stem & head
                moveTo(12f, 15f); lineTo(12f, 3f)
                moveTo(8f, 7f); lineTo(12f, 3f); lineTo(16f, 7f)
                
                // Tray
                moveTo(4f, 14f)
                lineTo(4f, 19f)
                arcTo(2f, 2f, 0f, false, false, 6f, 21f)
                lineTo(18f, 21f)
                arcTo(2f, 2f, 0f, false, false, 20f, 19f)
                lineTo(20f, 14f)
            }
        }
    }

    /**
     * Clipboard with incoming pasted document sheet
     */
    val Paste: ImageVector by lazy {
        icon("Paste") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Clipboard back
                moveTo(8f, 4f)
                lineTo(6f, 4f)
                arcTo(2f, 2f, 0f, false, false, 4f, 6f)
                lineTo(4f, 20f)
                arcTo(2f, 2f, 0f, false, false, 6f, 22f)
                lineTo(14f, 22f)
                arcTo(2f, 2f, 0f, false, false, 16f, 20f)
                lineTo(16f, 18f)
                
                // Top clip
                moveTo(9f, 2f)
                lineTo(15f, 2f)
                arcTo(1f, 1f, 0f, false, true, 16f, 3f)
                lineTo(16f, 5f)
                arcTo(1f, 1f, 0f, false, true, 15f, 6f)
                lineTo(9f, 6f)
                arcTo(1f, 1f, 0f, false, true, 8f, 5f)
                lineTo(8f, 3f)
                arcTo(1f, 1f, 0f, false, true, 9f, 2f)
                close()
                
                // Pasted page
                moveTo(12.5f, 9f)
                lineTo(18.5f, 9f)
                arcTo(1.5f, 1.5f, 0f, false, true, 20f, 10.5f)
                lineTo(20f, 18.5f)
                arcTo(1.5f, 1.5f, 0f, false, true, 18.5f, 20f)
                lineTo(12.5f, 20f)
                arcTo(1.5f, 1.5f, 0f, false, true, 11f, 18.5f)
                lineTo(11f, 10.5f)
                arcTo(1.5f, 1.5f, 0f, false, true, 12.5f, 9f)
                close()
                
                moveTo(14f, 13f); lineTo(17f, 13f)
                moveTo(14f, 16f); lineTo(17f, 16f)
            }
        }
    }

    /**
     * Two staggered overlapping duplicate document cards
     */
    val Copy: ImageVector by lazy {
        icon("Copy") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Back sheet
                moveTo(8f, 5f)
                arcTo(2f, 2f, 0f, false, true, 10f, 3f)
                lineTo(18f, 3f)
                arcTo(2f, 2f, 0f, false, true, 20f, 5f)
                lineTo(20f, 15f)
                arcTo(2f, 2f, 0f, false, true, 18f, 17f)
                
                // Front sheet
                moveTo(6f, 7f)
                lineTo(14f, 7f)
                arcTo(2f, 2f, 0f, false, true, 16f, 9f)
                lineTo(16f, 19f)
                arcTo(2f, 2f, 0f, false, true, 14f, 21f)
                lineTo(6f, 21f)
                arcTo(2f, 2f, 0f, false, true, 4f, 19f)
                lineTo(4f, 9f)
                arcTo(2f, 2f, 0f, false, true, 6f, 7f)
                close()
            }
        }
    }

    /**
     * Precision pencil stylus editing a base line
     */
    val Edit: ImageVector by lazy {
        icon("Edit") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Pencil outline
                moveTo(17f, 3f)
                arcTo(2.12f, 2.12f, 0f, false, true, 20f, 6f)
                lineTo(7f, 19f)
                lineTo(3f, 20f)
                lineTo(4f, 16f)
                close()
                
                // Pencil band
                moveTo(14.5f, 5.5f); lineTo(17.5f, 8.5f)
            }
        }
    }

    /**
     * Launch external application window arrow shooting outward
     */
    val OpenIn: ImageVector by lazy {
        icon("OpenIn") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Base window frame
                moveTo(12f, 4f)
                lineTo(5f, 4f)
                arcTo(2f, 2f, 0f, false, false, 3f, 6f)
                lineTo(3f, 19f)
                arcTo(2f, 2f, 0f, false, false, 5f, 21f)
                lineTo(18f, 21f)
                arcTo(2f, 2f, 0f, false, false, 20f, 19f)
                lineTo(20f, 12f)
                
                // Launch arrow
                moveTo(14f, 3f); lineTo(21f, 3f); lineTo(21f, 10f)
                moveTo(21f, 3f); lineTo(10f, 14f)
            }
        }
    }

    /**
     * Locked padlock with closed curved U-shackle and center keyhole
     */
    val Lock: ImageVector by lazy {
        icon("Lock") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Closed U-shackle
                moveTo(7f, 10f)
                lineTo(7f, 7f)
                arcTo(5f, 5f, 0f, false, true, 17f, 7f)
                lineTo(17f, 10f)
                
                // Padlock body
                moveTo(6f, 10f)
                lineTo(18f, 10f)
                arcTo(2f, 2f, 0f, false, true, 20f, 12f)
                lineTo(20f, 19f)
                arcTo(2f, 2f, 0f, false, true, 18f, 21f)
                lineTo(6f, 21f)
                arcTo(2f, 2f, 0f, false, true, 4f, 19f)
                lineTo(4f, 12f)
                arcTo(2f, 2f, 0f, false, true, 6f, 10f)
                close()
                
                // Keyhole
                moveTo(12f, 15.5f)
                arcTo(1.2f, 1.2f, 0f, true, true, 12f, 13.5f)
                arcTo(1.2f, 1.2f, 0f, true, true, 12f, 15.5f)
                close()
                moveTo(12f, 15.5f); lineTo(12f, 18f)
            }
        }
    }

    /**
     * Unlocked padlock with open lifted U-shackle and center keyhole
     */
    val LockOpen: ImageVector by lazy {
        icon("LockOpen") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Open lifted shackle (swung open and upward)
                moveTo(7f, 10f)
                lineTo(7f, 6f)
                arcTo(5f, 5f, 0f, false, true, 16.5f, 3.8f)
                
                // Padlock body
                moveTo(6f, 10f)
                lineTo(18f, 10f)
                arcTo(2f, 2f, 0f, false, true, 20f, 12f)
                lineTo(20f, 19f)
                arcTo(2f, 2f, 0f, false, true, 18f, 21f)
                lineTo(6f, 21f)
                arcTo(2f, 2f, 0f, false, true, 4f, 19f)
                lineTo(4f, 12f)
                arcTo(2f, 2f, 0f, false, true, 6f, 10f)
                close()
                
                // Keyhole
                moveTo(12f, 15.5f)
                arcTo(1.2f, 1.2f, 0f, true, true, 12f, 13.5f)
                arcTo(1.2f, 1.2f, 0f, true, true, 12f, 15.5f)
                close()
                moveTo(12f, 15.5f); lineTo(12f, 18f)
            }
        }
    }

    /**
     * Undo action with counter-clockwise return arc arrow
     */
    val Undo: ImageVector by lazy {
        icon("Undo") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9f, 7f); lineTo(4f, 12f); lineTo(9f, 17f)
                moveTo(4f, 12f); lineTo(14f, 12f)
                arcTo(6f, 6f, 0f, false, true, 20f, 18f)
            }
        }
    }

    /**
     * Eject and disconnect power/data link with uncoupled prongs
     */
    val Disconnect: ImageVector by lazy {
        icon("Disconnect") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Left plug
                moveTo(2f, 12f); lineTo(6f, 12f)
                moveTo(8f, 8f); lineTo(8f, 16f)
                moveTo(8f, 9f)
                lineTo(11f, 9f)
                arcTo(1f, 1f, 0f, false, true, 12f, 10f)
                lineTo(12f, 14f)
                arcTo(1f, 1f, 0f, false, true, 11f, 15f)
                lineTo(8f, 15f)
                
                // Disconnect break slashes
                moveTo(14.5f, 5f); lineTo(16.5f, 8f)
                moveTo(14.5f, 16f); lineTo(16.5f, 19f)
                
                // Right socket & cord
                moveTo(18f, 8f); lineTo(18f, 16f)
                moveTo(18f, 12f); lineTo(22f, 12f)
            }
        }
    }

    /**
     * Resume execution right-pointing play triangle
     */
    val Play: ImageVector by lazy {
        icon("Play") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(7f, 4f)
                lineTo(20f, 12f)
                lineTo(7f, 20f)
                close()
            }
        }
    }

    /**
     * Information and application details badge
     */
    val Info: ImageVector by lazy {
        icon("Info") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 21f)
                arcTo(9f, 9f, 0f, true, true, 12f, 3f)
                arcTo(9f, 9f, 0f, true, true, 12f, 21f)
                close()
                
                moveTo(12f, 8.8f)
                arcTo(1f, 1f, 0f, true, true, 12f, 6.8f)
                arcTo(1f, 1f, 0f, true, true, 12f, 8.8f)
                close()
                
                moveTo(12f, 11f); lineTo(12f, 16.5f)
            }
        }
    }

    /**
     * Downward expand chevron arrow
     */
    val ChevronDown: ImageVector by lazy {
        icon("ChevronDown") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f)
            }
        }
    }

    /**
     * Rightward expand chevron arrow
     */
    val ChevronRight: ImageVector by lazy {
        icon("ChevronRight") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f)
            }
        }
    }

    /**
     * Checkmark approval and confirmation tick
     */
    val Check: ImageVector by lazy {
        icon("Check") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 12.5f); lineTo(9f, 17.5f); lineTo(20f, 6.5f)
            }
        }
    }

    /**
     * Three vertical option context menu dots
     */
    val MoreVert: ImageVector by lazy {
        icon("MoreVert") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 6.5f)
                arcTo(1.5f, 1.5f, 0f, true, true, 12f, 3.5f)
                arcTo(1.5f, 1.5f, 0f, true, true, 12f, 6.5f)
                close()
                
                moveTo(12f, 13.5f)
                arcTo(1.5f, 1.5f, 0f, true, true, 12f, 10.5f)
                arcTo(1.5f, 1.5f, 0f, true, true, 12f, 13.5f)
                close()
                
                moveTo(12f, 20.5f)
                arcTo(1.5f, 1.5f, 0f, true, true, 12f, 17.5f)
                arcTo(1.5f, 1.5f, 0f, true, true, 12f, 20.5f)
                close()
            }
        }
    }

    /**
     * Pop out window into floating detached panel
     */
    val Undock: ImageVector by lazy {
        icon("Undock") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Back base container
                moveTo(14f, 14f)
                lineTo(14f, 19f)
                arcTo(1f, 1f, 0f, false, true, 13f, 20f)
                lineTo(5f, 20f)
                arcTo(1f, 1f, 0f, false, true, 4f, 19f)
                lineTo(4f, 9f)
                arcTo(1f, 1f, 0f, false, true, 5f, 8f)
                lineTo(10f, 8f)
                
                // Detached floating front window
                moveTo(11.5f, 4f)
                lineTo(18.5f, 4f)
                arcTo(1.5f, 1.5f, 0f, false, true, 20f, 5.5f)
                lineTo(20f, 10.5f)
                arcTo(1.5f, 1.5f, 0f, false, true, 18.5f, 12f)
                lineTo(11.5f, 12f)
                arcTo(1.5f, 1.5f, 0f, false, true, 10f, 10.5f)
                lineTo(10f, 5.5f)
                arcTo(1.5f, 1.5f, 0f, false, true, 11.5f, 4f)
                close()
                
                // Undock pop-out arrow
                moveTo(8f, 16f); lineTo(14f, 10f)
                moveTo(11f, 10f); lineTo(14f, 10f); lineTo(14f, 13f)
            }
        }
    }

    /**
     * Dock floating panel back into host layout frame
     */
    val Dock: ImageVector by lazy {
        icon("Dock") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Dock frame
                moveTo(5f, 3f)
                lineTo(19f, 3f)
                arcTo(2f, 2f, 0f, false, true, 21f, 5f)
                lineTo(21f, 19f)
                arcTo(2f, 2f, 0f, false, true, 19f, 21f)
                lineTo(5f, 21f)
                arcTo(2f, 2f, 0f, false, true, 3f, 19f)
                lineTo(3f, 5f)
                arcTo(2f, 2f, 0f, false, true, 5f, 3f)
                close()
                
                // Dock floor divider
                moveTo(3f, 16f); lineTo(21f, 16f)
                
                // Inward dock arrow
                moveTo(12f, 6f); lineTo(12f, 12f)
                moveTo(9f, 9f); lineTo(12f, 12f); lineTo(15f, 9f)
            }
        }
    }

    /**
     * PlayStation 5 console standing profile with curved side wing plates
     */
    val PS5Console: ImageVector by lazy {
        icon("PS5Console") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Curved collar plates
                moveTo(6f, 3f)
                curveTo(7.5f, 9f, 7.5f, 15f, 6f, 21f)
                lineTo(18f, 21f)
                curveTo(16.5f, 15f, 16.5f, 9f, 18f, 3f)
                close()
                
                // Center tower body line
                moveTo(12f, 6f); lineTo(12f, 18f)
                
                // Power LED accent line
                moveTo(9.5f, 4.5f); lineTo(14.5f, 4.5f)
            }
        }
    }

    /**
     * Hexadecimal memory inspector badge displaying 0x prefix
     */
    val ViewHex: ImageVector by lazy {
        icon("ViewHex") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Outer hex badge
                moveTo(5f, 5f)
                lineTo(19f, 5f)
                arcTo(3f, 3f, 0f, false, true, 22f, 8f)
                lineTo(22f, 16f)
                arcTo(3f, 3f, 0f, false, true, 19f, 19f)
                lineTo(5f, 19f)
                arcTo(3f, 3f, 0f, false, true, 2f, 16f)
                lineTo(2f, 8f)
                arcTo(3f, 3f, 0f, false, true, 5f, 5f)
                close()
                
                // '0' numeral
                moveTo(7.5f, 9f)
                lineTo(9f, 9f)
                arcTo(1.5f, 1.5f, 0f, false, true, 10.5f, 10.5f)
                lineTo(10.5f, 13.5f)
                arcTo(1.5f, 1.5f, 0f, false, true, 9f, 15f)
                lineTo(7.5f, 15f)
                arcTo(1.5f, 1.5f, 0f, false, true, 6f, 13.5f)
                lineTo(6f, 10.5f)
                arcTo(1.5f, 1.5f, 0f, false, true, 7.5f, 9f)
                close()
                
                // 'x' symbol
                moveTo(13.5f, 10f); lineTo(18f, 15f)
                moveTo(18f, 10f); lineTo(13.5f, 15f)
            }
        }
    }

    /**
     * Graph zoom-in magnifying glass with plus
     */
    val ZoomIn: ImageVector by lazy {
        icon("ZoomIn") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(10f, 17f)
                arcTo(7f, 7f, 0f, true, true, 10f, 3f)
                arcTo(7f, 7f, 0f, true, true, 10f, 17f)
                close()
                
                moveTo(15f, 15f); lineTo(21f, 21f)
                moveTo(10f, 7f); lineTo(10f, 13f)
                moveTo(7f, 10f); lineTo(13f, 10f)
            }
        }
    }

    /**
     * Graph zoom-out magnifying glass with minus
     */
    val ZoomOut: ImageVector by lazy {
        icon("ZoomOut") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(10f, 17f)
                arcTo(7f, 7f, 0f, true, true, 10f, 3f)
                arcTo(7f, 7f, 0f, true, true, 10f, 17f)
                close()
                
                moveTo(15f, 15f); lineTo(21f, 21f)
                moveTo(7f, 10f); lineTo(13f, 10f)
            }
        }
    }

    /**
     * Reset viewport and fit graph to screen frame target
     */
    val ResetView: ImageVector by lazy {
        icon("ResetView") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 8f); lineTo(4f, 5f); arcTo(1f, 1f, 0f, false, true, 5f, 4f); lineTo(8f, 4f)
                moveTo(20f, 8f); lineTo(20f, 5f); arcTo(1f, 1f, 0f, false, false, 19f, 4f); lineTo(16f, 4f)
                moveTo(4f, 16f); lineTo(4f, 19f); arcTo(1f, 1f, 0f, false, false, 5f, 20f); lineTo(8f, 20f)
                moveTo(20f, 16f); lineTo(20f, 19f); arcTo(1f, 1f, 0f, false, true, 19f, 20f); lineTo(16f, 20f)
                
                moveTo(12f, 14f)
                arcTo(2f, 2f, 0f, true, true, 12f, 10f)
                arcTo(2f, 2f, 0f, true, true, 12f, 14f)
                close()
            }
        }
    }

    /**
     * Code and disassembly brackets
     */
    val Code: ImageVector by lazy {
        icon("Code") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(8f, 6f); lineTo(2f, 12f); lineTo(8f, 18f)
                moveTo(16f, 6f); lineTo(22f, 12f); lineTo(16f, 18f)
                moveTo(14f, 4f); lineTo(10f, 20f)
            }
        }
    }

    /**
     * Directory folder storage unit
     */
    val Folder: ImageVector by lazy {
        icon("Folder") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 4f)
                lineTo(10f, 4f)
                lineTo(12f, 6f)
                lineTo(19f, 6f)
                arcTo(2f, 2f, 0f, false, true, 21f, 8f)
                lineTo(21f, 18f)
                arcTo(2f, 2f, 0f, false, true, 19f, 20f)
                lineTo(5f, 20f)
                arcTo(2f, 2f, 0f, false, true, 3f, 18f)
                lineTo(3f, 6f)
                arcTo(2f, 2f, 0f, false, true, 5f, 4f)
                close()
                
                moveTo(3f, 10f); lineTo(21f, 10f)
            }
        }
    }

    /**
     * Text document file (.txt, .log, .ini) with written lines
     */
    val FileDocument: ImageVector by lazy {
        icon("FileDocument") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 3f)
                lineTo(14f, 3f)
                lineTo(19f, 8f)
                lineTo(19f, 21f)
                arcTo(2f, 2f, 0f, false, true, 17f, 23f)
                lineTo(5f, 23f)
                arcTo(2f, 2f, 0f, false, true, 3f, 21f)
                lineTo(3f, 5f)
                arcTo(2f, 2f, 0f, false, true, 5f, 3f)
                close()
                
                moveTo(14f, 3f); lineTo(14f, 8f); lineTo(19f, 8f)
                moveTo(7f, 12f); lineTo(15f, 12f)
                moveTo(7f, 16f); lineTo(13f, 16f)
            }
        }
    }

    /**
     * Structured JSON file with curly code braces
     */
    val FileJson: ImageVector by lazy {
        icon("FileJson") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 3f)
                lineTo(14f, 3f)
                lineTo(19f, 8f)
                lineTo(19f, 21f)
                arcTo(2f, 2f, 0f, false, true, 17f, 23f)
                lineTo(5f, 23f)
                arcTo(2f, 2f, 0f, false, true, 3f, 21f)
                lineTo(3f, 5f)
                arcTo(2f, 2f, 0f, false, true, 5f, 3f)
                close()
                
                moveTo(14f, 3f); lineTo(14f, 8f); lineTo(19f, 8f)
                
                // Left brace {
                moveTo(9.5f, 12f)
                curveTo(8.7f, 12f, 8.3f, 12.4f, 8.3f, 13f)
                lineTo(8.3f, 13.5f)
                curveTo(8.3f, 14.1f, 7.9f, 14.5f, 7.3f, 14.5f)
                curveTo(7.9f, 14.5f, 8.3f, 14.9f, 8.3f, 15.5f)
                lineTo(8.3f, 16f)
                curveTo(8.3f, 16.6f, 8.7f, 17f, 9.5f, 17f)
                
                // Right brace }
                moveTo(14.5f, 12f)
                curveTo(15.3f, 12f, 15.7f, 12.4f, 15.7f, 13f)
                lineTo(15.7f, 13.5f)
                curveTo(15.7f, 14.1f, 16.1f, 14.5f, 16.7f, 14.5f)
                curveTo(16.1f, 14.5f, 15.7f, 14.9f, 15.7f, 15.5f)
                lineTo(15.7f, 16f)
                curveTo(15.7f, 16.6f, 15.3f, 17f, 14.5f, 17f)
            }
        }
    }

    /**
     * Binary executable file (.elf, .bin, .pkg) with execution core
     */
    val FileExecutable: ImageVector by lazy {
        icon("FileExecutable") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 3f)
                lineTo(14f, 3f)
                lineTo(19f, 8f)
                lineTo(19f, 21f)
                arcTo(2f, 2f, 0f, false, true, 17f, 23f)
                lineTo(5f, 23f)
                arcTo(2f, 2f, 0f, false, true, 3f, 21f)
                lineTo(3f, 5f)
                arcTo(2f, 2f, 0f, false, true, 5f, 3f)
                close()
                
                moveTo(14f, 3f); lineTo(14f, 8f); lineTo(19f, 8f)
                
                // Executable run arrow
                moveTo(11f, 11f); lineTo(15f, 14f); lineTo(11f, 17f); close()
                moveTo(8f, 14f); lineTo(11f, 14f)
            }
        }
    }

    /**
     * Dynamic library module (.prx, .sprx) with interlocking modular connector
     */
    val FileLibrary: ImageVector by lazy {
        icon("FileLibrary") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 3f)
                lineTo(14f, 3f)
                lineTo(19f, 8f)
                lineTo(19f, 21f)
                arcTo(2f, 2f, 0f, false, true, 17f, 23f)
                lineTo(5f, 23f)
                arcTo(2f, 2f, 0f, false, true, 3f, 21f)
                lineTo(3f, 5f)
                arcTo(2f, 2f, 0f, false, true, 5f, 3f)
                close()
                
                moveTo(14f, 3f); lineTo(14f, 8f); lineTo(19f, 8f)
                
                // Module interlocking connector
                moveTo(8f, 12f)
                lineTo(10f, 12f)
                arcTo(1.5f, 1.5f, 0f, false, true, 13f, 12f)
                lineTo(15f, 12f)
                lineTo(15f, 16f)
                lineTo(13f, 16f)
                arcTo(1.5f, 1.5f, 0f, false, true, 10f, 16f)
                lineTo(8f, 16f)
                close()
            }
        }
    }

    /**
     * Database file (.db, .sqlite) with cylinder platters
     */
    val FileDatabase: ImageVector by lazy {
        icon("FileDatabase") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 3f)
                lineTo(14f, 3f)
                lineTo(19f, 8f)
                lineTo(19f, 21f)
                arcTo(2f, 2f, 0f, false, true, 17f, 23f)
                lineTo(5f, 23f)
                arcTo(2f, 2f, 0f, false, true, 3f, 21f)
                lineTo(3f, 5f)
                arcTo(2f, 2f, 0f, false, true, 5f, 3f)
                close()
                
                moveTo(14f, 3f); lineTo(14f, 8f); lineTo(19f, 8f)
                
                // Top platter ellipse
                moveTo(11.5f, 11f)
                arcTo(3.5f, 1.5f, 0f, true, true, 11.5f, 14f)
                arcTo(3.5f, 1.5f, 0f, true, true, 11.5f, 11f)
                close()
                
                // Middle cylinder rim
                moveTo(8f, 12.5f)
                lineTo(8f, 14.5f)
                arcTo(3.5f, 1.5f, 0f, false, false, 15f, 14.5f)
                lineTo(15f, 12.5f)
                
                // Bottom cylinder rim
                moveTo(8f, 14.5f)
                lineTo(8f, 16.5f)
                arcTo(3.5f, 1.5f, 0f, false, false, 15f, 16.5f)
                lineTo(15f, 14.5f)
            }
        }
    }

    /**
     * Generic file sheet with dog-eared fold
     */
    val FileGeneric: ImageVector by lazy {
        icon("FileGeneric") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 3f)
                lineTo(14f, 3f)
                lineTo(19f, 8f)
                lineTo(19f, 21f)
                arcTo(2f, 2f, 0f, false, true, 17f, 23f)
                lineTo(5f, 23f)
                arcTo(2f, 2f, 0f, false, true, 3f, 21f)
                lineTo(3f, 5f)
                arcTo(2f, 2f, 0f, false, true, 5f, 3f)
                close()
                
                moveTo(14f, 3f); lineTo(14f, 8f); lineTo(19f, 8f)
            }
        }
    }

    /**
     * General settings configuration equalizer sliders with knobs
     */
    val SettingsGeneral: ImageVector by lazy {
        icon("SettingsGeneral") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Slider track 1
                moveTo(4f, 7f); lineTo(20f, 7f)
                moveTo(9f, 9.5f)
                arcTo(2.5f, 2.5f, 0f, true, true, 9f, 4.5f)
                arcTo(2.5f, 2.5f, 0f, true, true, 9f, 9.5f)
                close()
                
                // Slider track 2
                moveTo(4f, 12f); lineTo(20f, 12f)
                moveTo(16f, 14.5f)
                arcTo(2.5f, 2.5f, 0f, true, true, 16f, 9.5f)
                arcTo(2.5f, 2.5f, 0f, true, true, 16f, 14.5f)
                close()
                
                // Slider track 3
                moveTo(4f, 17f); lineTo(20f, 17f)
                moveTo(12f, 19.5f)
                arcTo(2.5f, 2.5f, 0f, true, true, 12f, 14.5f)
                arcTo(2.5f, 2.5f, 0f, true, true, 12f, 19.5f)
                close()
            }
        }
    }

    /**
     * Network connectivity globe with equator and meridian lines
     */
    val SettingsNetwork: ImageVector by lazy {
        icon("SettingsNetwork") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Globe circle
                moveTo(12f, 21f)
                arcTo(9f, 9f, 0f, true, true, 12f, 3f)
                arcTo(9f, 9f, 0f, true, true, 12f, 21f)
                close()
                
                // Equator
                moveTo(3f, 12f); lineTo(21f, 12f)
                
                // Meridian ellipse
                moveTo(12f, 3f)
                curveTo(8f, 7f, 8f, 17f, 12f, 21f)
                moveTo(12f, 3f)
                curveTo(16f, 7f, 16f, 17f, 12f, 21f)
            }
        }
    }

    /**
     * Laboratory mock simulation flask with chemical reaction bubbles
     */
    val SettingsSimulation: ImageVector by lazy {
        icon("SettingsSimulation") {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Lip rim
                moveTo(9f, 3f); lineTo(15f, 3f)
                
                // Conical flask contour
                moveTo(10f, 3f)
                lineTo(10f, 8f)
                lineTo(4f, 19f)
                arcTo(1.5f, 1.5f, 0f, false, false, 5.3f, 21f)
                lineTo(18.7f, 21f)
                arcTo(1.5f, 1.5f, 0f, false, false, 20f, 19f)
                lineTo(14f, 8f)
                lineTo(14f, 3f)
                
                // Liquid wave level
                moveTo(7f, 15f)
                curveTo(9f, 14f, 11f, 16f, 13f, 15f)
                curveTo(15f, 14f, 16f, 15f, 17f, 15f)
                
                // Reaction bubbles
                moveTo(13f, 18.2f)
                arcTo(1f, 1f, 0f, true, true, 13f, 16.2f)
                arcTo(1f, 1f, 0f, true, true, 13f, 18.2f)
                close()
            }
        }
    }

}

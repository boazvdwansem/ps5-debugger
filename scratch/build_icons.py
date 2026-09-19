# -*- coding: utf-8 -*-
import os

icons_data = [
    # -------------------------------------------------------------------------
    # 1. Connections
    # PC Host monitor on left, PS5 console on right, connected via data cable with transfer pulse
    # -------------------------------------------------------------------------
    {
        "name": "Connections",
        "description": "Host PC and PS5 console link with communication cable and data pulse",
        "svg_content": """  <!-- Left PC monitor -->
  <rect x="2" y="5" width="8" height="7" rx="1" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <path d="M6 12v3M4 15h4" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- Right PS5 console -->
  <path d="M17 4c.5 4 .5 10 0 14h4c-.5-4-.5-10 0-14h-4z" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <line x1="19" y1="8" x2="19" y2="14" stroke="white" stroke-width="1.5" stroke-linecap="round"/>
  <!-- Connecting cable and data node -->
  <path d="M8 8.5h4a2 2 0 0 1 2 2v2a2 2 0 0 0 2 2h1" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <circle cx="12" cy="10.5" r="1" fill="white"/>""",
        "kt_paths": [
            """// PC monitor outline & stand
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
                close()"""
        ]
    },

    # -------------------------------------------------------------------------
    # 2. MemoryMap
    # Memory address space showing 3 distinct mapped regions with boundary limits
    # -------------------------------------------------------------------------
    {
        "name": "MemoryMap",
        "description": "Segmented memory address space regions with address boundary markers",
        "svg_content": """  <!-- Address gutter & segments -->
  <rect x="3" y="3" width="18" height="18" rx="2" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <line x1="3" y1="9" x2="21" y2="9" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <line x1="3" y1="15" x2="21" y2="15" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <!-- Address indicator markers on left -->
  <line x1="8" y1="3" x2="8" y2="21" stroke="white" stroke-width="1.5" stroke-dasharray="2 2" stroke-linecap="round"/>
  <!-- Section content hints -->
  <line x1="11" y1="6" x2="18" y2="6" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <line x1="11" y1="12" x2="16" y2="12" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <line x1="11" y1="18" x2="17" y2="18" stroke="white" stroke-width="2" stroke-linecap="round"/>""",
        "kt_paths": [
            """// Outer memory address space container
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
                moveTo(11f, 18f); lineTo(17f, 18f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 3. Symbols
    # Function badge f(x) inside symbol tag
    # -------------------------------------------------------------------------
    {
        "name": "Symbols",
        "description": "Symbol lookup and function export badge f(x)",
        "svg_content": """  <!-- Function badge outline -->
  <path d="M4 6a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6z" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- f symbol -->
  <path d="M11 8c-.8 0-1.5.5-1.5 1.5v6.5" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <line x1="7.5" y1="11" x2="12.5" y2="11" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <!-- (x) subscript -->
  <path d="M14 13l3 4M17 13l-3 4" stroke="white" stroke-width="2" stroke-linecap="round"/>""",
        "kt_paths": [
            """// Badge container
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
                moveTo(17f, 13f); lineTo(14f, 17f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 4. MemoryViewer
    # Hex table viewer with address offset column and byte cells
    # -------------------------------------------------------------------------
    {
        "name": "MemoryViewer",
        "description": "Hexadecimal memory viewer table with address offset column and byte cells",
        "svg_content": """  <!-- Outer editor frame -->
  <rect x="2" y="4" width="20" height="16" rx="2" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- Address gutter column -->
  <line x1="7" y1="4" x2="7" y2="20" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <!-- Byte matrix dots / lines -->
  <circle cx="10.5" cy="8" r="1" fill="white"/>
  <circle cx="14.5" cy="8" r="1" fill="white"/>
  <circle cx="18.5" cy="8" r="1" fill="white"/>
  <circle cx="10.5" cy="12" r="1" fill="white"/>
  <circle cx="14.5" cy="12" r="1" fill="white"/>
  <circle cx="18.5" cy="12" r="1" fill="white"/>
  <circle cx="10.5" cy="16" r="1" fill="white"/>
  <circle cx="14.5" cy="16" r="1" fill="white"/>
  <circle cx="18.5" cy="16" r="1" fill="white"/>
  <!-- Address markers on left gutter -->
  <line x1="4" y1="8" x2="5.5" y2="8" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <line x1="4" y1="12" x2="5.5" y2="12" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <line x1="4" y1="16" x2="5.5" y2="16" stroke="white" stroke-width="2" stroke-linecap="round"/>""",
        "kt_paths": [
            """// Editor outer frame
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
                moveTo(18.5f, 16.5f); arcTo(0.8f, 0.8f, 0f, true, true, 18.5f, 15.5f); arcTo(0.8f, 0.8f, 0f, true, true, 18.5f, 16.5f); close()"""
        ]
    },

    # -------------------------------------------------------------------------
    # 5. MemoryScan
    # Radar scanner scope searching through memory with sweep line and target blip
    # -------------------------------------------------------------------------
    {
        "name": "MemoryScan",
        "description": "Radar scanner scope searching memory values with targeting crosshair",
        "svg_content": """  <!-- Outer radar scope ring -->
  <circle cx="11" cy="11" r="8" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- Inner radar ring -->
  <circle cx="11" cy="11" r="4" stroke="white" stroke-width="1.5" stroke-dasharray="2 2" fill="none"/>
  <!-- Crosshair axes -->
  <line x1="11" y1="3" x2="11" y2="19" stroke="white" stroke-width="1.5" stroke-linecap="round"/>
  <line x1="3" y1="11" x2="19" y2="11" stroke="white" stroke-width="1.5" stroke-linecap="round"/>
  <!-- Sweep radar beam -->
  <line x1="11" y1="11" x2="16.5" y2="5.5" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <!-- Detected target blip -->
  <circle cx="15" cy="8" r="1.2" fill="white"/>
  <!-- Scan handle / base -->
  <line x1="17" y1="17" x2="22" y2="22" stroke="white" stroke-width="2.5" stroke-linecap="round"/>""",
        "kt_paths": [
            """// Radar scope outer circle (centered at 11, 11 with radius 8)
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
                moveTo(17f, 17f); lineTo(22f, 22f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 6. WatchList
    # Real-time monitored pin & live heartbeat pulse gauge (distinct from Eye)
    # -------------------------------------------------------------------------
    {
        "name": "WatchList",
        "description": "Live variable monitor with active heartbeat pulse gauge and pin target",
        "svg_content": """  <!-- Monitor gauge frame -->
  <rect x="2" y="4" width="20" height="16" rx="3" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- Active monitor waveform pulse (ECG line) -->
  <path d="M2 12h5l2-5 3 10 2.5-6 1.5 2 1-1h5" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- Monitored pin indicator at top right -->
  <circle cx="18" cy="7" r="1.5" fill="white"/>""",
        "kt_paths": [
            """// Monitor gauge screen frame
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
                close()"""
        ]
    },

    # -------------------------------------------------------------------------
    # 7. MemoryDumper
    # RAM module at top dumping data stream into hard drive storage platter at bottom
    # -------------------------------------------------------------------------
    {
        "name": "MemoryDumper",
        "description": "RAM memory dump stream transferring down to persistent storage drive",
        "svg_content": """  <!-- RAM stick at top -->
  <rect x="4" y="3" width="16" height="5" rx="1" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <line x1="7" y1="5.5" x2="9" y2="5.5" stroke="white" stroke-width="1.5"/>
  <line x1="11" y1="5.5" x2="13" y2="5.5" stroke="white" stroke-width="1.5"/>
  <line x1="15" y1="5.5" x2="17" y2="5.5" stroke="white" stroke-width="1.5"/>
  <!-- Downward transfer stream -->
  <path d="M12 8v6m0 0l-2.5-2.5M12 14l2.5-2.5" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
  <!-- Storage drive at bottom -->
  <rect x="3" y="16" width="18" height="5" rx="1.5" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <circle cx="17" cy="18.5" r="1" fill="white"/>
  <line x1="6" y1="18.5" x2="12" y2="18.5" stroke="white" stroke-width="1.5" stroke-linecap="round"/>""",
        "kt_paths": [
            """// RAM memory module
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
                close()"""
        ]
    },

    # -------------------------------------------------------------------------
    # 8. Cheats
    # DualSense PS5 style gaming controller with D-pad, thumbstick rings, and action buttons
    # -------------------------------------------------------------------------
    {
        "name": "Cheats",
        "description": "PlayStation DualSense gamepad controller with D-pad, thumbsticks, and action buttons",
        "svg_content": """  <!-- Controller outer silhouette -->
  <path d="M6 7h12a4 4 0 0 1 4 4v3c0 3-1.5 5-3.5 5.5-2 .5-3.5-1-4.5-3h-4c-1 2-2.5 3.5-4.5 3-2-.5-3.5-2.5-3.5-5.5v-3a4 4 0 0 1 4-4z" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- D-Pad cross on left -->
  <path d="M6.5 11.5h3m-1.5-1.5v3" stroke="white" stroke-width="1.8" stroke-linecap="round"/>
  <!-- Action buttons on right -->
  <circle cx="16" cy="10.5" r="1" fill="white"/>
  <circle cx="17.5" cy="12.5" r="1" fill="white"/>
  <!-- Thumbsticks -->
  <circle cx="9" cy="15" r="1.5" stroke="white" stroke-width="1.5" fill="none"/>
  <circle cx="15" cy="15" r="1.5" stroke="white" stroke-width="1.5" fill="none"/>""",
        "kt_paths": [
            """// Controller ergonomic body
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
                close()"""
        ]
    },

    # -------------------------------------------------------------------------
    # 9. FileBrowser
    # File manager / directory browser with folder + file tree branch (distinct from Folder!)
    # -------------------------------------------------------------------------
    {
        "name": "FileBrowser",
        "description": "File manager explorer displaying directory tree hierarchy",
        "svg_content": """  <!-- Parent folder at top -->
  <path d="M3 5h5l2 2h6a2 2 0 0 1 2 2v2H3V5z" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- File tree hierarchy branch -->
  <path d="M6 11v9m0-5h4m-4 5h4" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
  <!-- Child file nodes -->
  <rect x="12" y="13" width="8" height="3" rx="1" stroke="white" stroke-width="1.8" fill="none"/>
  <rect x="12" y="18" width="8" height="3" rx="1" stroke="white" stroke-width="1.8" fill="none"/>""",
        "kt_paths": [
            """// Top parent folder
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
                close()"""
        ]
    },

    # -------------------------------------------------------------------------
    # 10. Process
    # Task manager with active threads and CPU performance waveform (distinct from gear!)
    # -------------------------------------------------------------------------
    {
        "name": "Process",
        "description": "Process manager showing active system tasks and CPU thread activity",
        "svg_content": """  <!-- Process window frame -->
  <rect x="3" y="3" width="18" height="18" rx="2" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- Top title bar divider -->
  <line x1="3" y1="8" x2="21" y2="8" stroke="white" stroke-width="1.8" stroke-linecap="round"/>
  <!-- Window control dot -->
  <circle cx="6" cy="5.5" r="1" fill="white"/>
  <circle cx="9" cy="5.5" r="1" fill="white"/>
  <!-- CPU thread activity pulse bars -->
  <path d="M6 14h2l1.5-3 2 6 1.5-3h5" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>""",
        "kt_paths": [
            """// Task window container
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
                lineTo(18f, 14f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 11. DebuggerControl
    # CPU microprocessor with execution traces and execution play core
    # -------------------------------------------------------------------------
    {
        "name": "DebuggerControl",
        "description": "CPU processor with circuit pins and execution core",
        "svg_content": """  <!-- CPU IC body -->
  <rect x="6" y="6" width="12" height="12" rx="2" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- Execution core play triangle -->
  <polygon points="10,9.5 15,12 10,14.5" fill="white"/>
  <!-- Pin traces: Top -->
  <line x1="9" y1="2" x2="9" y2="6" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <line x1="15" y1="2" x2="15" y2="6" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <!-- Pin traces: Bottom -->
  <line x1="9" y1="18" x2="9" y2="22" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <line x1="15" y1="18" x2="15" y2="22" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <!-- Pin traces: Left -->
  <line x1="2" y1="9" x2="6" y2="9" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <line x1="2" y1="15" x2="6" y2="15" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <!-- Pin traces: Right -->
  <line x1="18" y1="9" x2="22" y2="9" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <line x1="18" y1="15" x2="22" y2="15" stroke="white" stroke-width="2" stroke-linecap="round"/>""",
        "kt_paths": [
            """// Microprocessor die package
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
                moveTo(18f, 15f); lineTo(22f, 15f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 12. Breakpoints
    # Halt stop octagon with centered target pause bars
    # -------------------------------------------------------------------------
    {
        "name": "Breakpoints",
        "description": "Breakpoint stop octagon with pause bars",
        "svg_content": """  <!-- Octagon stop sign -->
  <polygon points="8,3 16,3 21,8 21,16 16,21 8,21 3,16 3,8" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- Centered halt pause bars -->
  <line x1="10" y1="9" x2="10" y2="15" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <line x1="14" y1="9" x2="14" y2="15" stroke="white" stroke-width="2" stroke-linecap="round"/>""",
        "kt_paths": [
            """// Octagonal breakpoint perimeter
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
                moveTo(14f, 9f); lineTo(14f, 15f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 13. References
    # Cross references directed call-graph branching nodes
    # -------------------------------------------------------------------------
    {
        "name": "References",
        "description": "Cross references directed call graph showing code caller/callee links",
        "svg_content": """  <!-- Source node on left -->
  <circle cx="5" cy="12" r="3" stroke="white" stroke-width="2" fill="none"/>
  <!-- Branching targets on right -->
  <circle cx="19" cy="6" r="2.5" stroke="white" stroke-width="2" fill="none"/>
  <circle cx="19" cy="18" r="2.5" stroke="white" stroke-width="2" fill="none"/>
  <!-- Directed graph edges with arrowheads -->
  <path d="M8 11l8-4m-2-1l2.5 1-1 2.5" stroke="white" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
  <path d="M8 13l8 4m-1-2.5l1 2.5-2.5 1" stroke="white" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>""",
        "kt_paths": [
            """// Source node
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
                moveTo(15.5f, 14.5f); lineTo(16.5f, 17.2f); lineTo(14f, 18f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 14. Terminal
    # Command console shell window with prompt >_
    # -------------------------------------------------------------------------
    {
        "name": "Terminal",
        "description": "Console shell log window with interactive prompt and cursor",
        "svg_content": """  <!-- Terminal window outline -->
  <rect x="2.5" y="4" width="19" height="16" rx="2" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- Command prompt > -->
  <path d="M6.5 9.5l3 2.5-3 2.5" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
  <!-- Cursor underscore _ -->
  <line x1="12" y1="15" x2="16.5" y2="15" stroke="white" stroke-width="2" stroke-linecap="round"/>""",
        "kt_paths": [
            """// Console window frame
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
                moveTo(12f, 15f); lineTo(16.5f, 15f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 15. Settings
    # 6-tooth precision mechanical gear with round center bore
    # -------------------------------------------------------------------------
    {
        "name": "Settings",
        "description": "Precision mechanical cog gear with 6 rounded teeth and center axle bore",
        "svg_content": """  <!-- Gear perimeter -->
  <path d="M10.5 2.5h3l.7 2.3 2 .8 2.1-1.3 2.1 2.1-1.3 2.1.8 2 2.3.7v3l-2.3.7-.8 2 1.3 2.1-2.1 2.1-2.1-1.3-2 .8-.7 2.3h-3l-.7-2.3-2-.8-2.1 1.3-2.1-2.1 1.3-2.1-.8-2-2.3-.7v-3l2.3-.7.8-2-1.3-2.1 2.1-2.1 2.1 1.3 2-.8z" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- Center axle bore -->
  <circle cx="12" cy="12" r="3" stroke="white" stroke-width="2" fill="none"/>""",
        "kt_paths": [
            """// Cog wheel teeth profile
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
                close()"""
        ]
    },

    # -------------------------------------------------------------------------
    # 16. Close
    # Clean X cancel cross with rounded endpoints
    # -------------------------------------------------------------------------
    {
        "name": "Close",
        "description": "Standard panel close and dismiss cross",
        "svg_content": """  <line x1="5" y1="5" x2="19" y2="19" stroke="white" stroke-width="2.2" stroke-linecap="round"/>
  <line x1="19" y1="5" x2="5" y2="19" stroke="white" stroke-width="2.2" stroke-linecap="round"/>""",
        "kt_paths": [
            """moveTo(5f, 5f); lineTo(19f, 19f)
                moveTo(19f, 5f); lineTo(5f, 19f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 17. Clear
    # Input field clear button: round circle containing an inner small X (distinct from Close!)
    # -------------------------------------------------------------------------
    {
        "name": "Clear",
        "description": "Search field clear button: circular badge containing an inner cross",
        "svg_content": """  <!-- Circular badge -->
  <circle cx="12" cy="12" r="9" stroke="white" stroke-width="2" fill="none"/>
  <!-- Inner X -->
  <line x1="9" y1="9" x2="15" y2="15" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <line x1="15" y1="9" x2="9" y2="15" stroke="white" stroke-width="2" stroke-linecap="round"/>""",
        "kt_paths": [
            """// Circular badge perimeter
                moveTo(12f, 21f)
                arcTo(9f, 9f, 0f, true, true, 12f, 3f)
                arcTo(9f, 9f, 0f, true, true, 12f, 21f)
                close()

                // Inner cross
                moveTo(9f, 9f); lineTo(15f, 15f)
                moveTo(15f, 9f); lineTo(9f, 15f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 18. Refresh
    # Double-arrow circular synchronization reload loop
    # -------------------------------------------------------------------------
    {
        "name": "Refresh",
        "description": "Circular double-arrow sync reload cycle",
        "svg_content": """  <!-- Top-right arrow head & arc -->
  <path d="M21 4v5h-5" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
  <path d="M20.5 10.5A8.5 8.5 0 0 0 5.5 7" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <!-- Bottom-left arrow head & arc -->
  <path d="M3 20v-5h5" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
  <path d="M3.5 13.5a8.5 8.5 0 0 0 15 3.5" stroke="white" stroke-width="2" stroke-linecap="round"/>""",
        "kt_paths": [
            """// Top arrow head
                moveTo(21f, 4f); lineTo(21f, 9f); lineTo(16f, 9f)

                // Top arc
                moveTo(20.5f, 10.5f)
                arcTo(8.5f, 8.5f, 0f, false, false, 5.5f, 7f)

                // Bottom arrow head
                moveTo(3f, 20f); lineTo(3f, 15f); lineTo(8f, 15f)

                // Bottom arc
                moveTo(3.5f, 13.5f)
                arcTo(8.5f, 8.5f, 0f, false, false, 18.5f, 17f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 19. ArrowBack
    # Left pointing navigation arrow
    # -------------------------------------------------------------------------
    {
        "name": "ArrowBack",
        "description": "Leftward navigation back arrow",
        "svg_content": """  <path d="M19 12H5m6-6l-6 6 6 6" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>""",
        "kt_paths": [
            """moveTo(19f, 12f); lineTo(5f, 12f)
                moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 20. Add
    # Centered plus sign
    # -------------------------------------------------------------------------
    {
        "name": "Add",
        "description": "Centered plus action symbol",
        "svg_content": """  <line x1="12" y1="4" x2="12" y2="20" stroke="white" stroke-width="2.2" stroke-linecap="round"/>
  <line x1="4" y1="12" x2="20" y2="12" stroke="white" stroke-width="2.2" stroke-linecap="round"/>""",
        "kt_paths": [
            """moveTo(12f, 4f); lineTo(12f, 20f)
                moveTo(4f, 12f); lineTo(20f, 12f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 21. Search
    # Magnifying glass with round lens and 45-degree diagonal handle
    # -------------------------------------------------------------------------
    {
        "name": "Search",
        "description": "Magnifying glass search query and filter icon",
        "svg_content": """  <!-- Lens circle -->
  <circle cx="10.5" cy="10.5" r="6.5" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- Angled handle -->
  <line x1="15.5" y1="15.5" x2="21" y2="21" stroke="white" stroke-width="2.5" stroke-linecap="round"/>""",
        "kt_paths": [
            """// Lens
                moveTo(10.5f, 17f)
                arcTo(6.5f, 6.5f, 0f, true, true, 10.5f, 4f)
                arcTo(6.5f, 6.5f, 0f, true, true, 10.5f, 17f)
                close()

                // Handle
                moveTo(15.5f, 15.5f); lineTo(21f, 21f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 22. Delete
    # Modern ribbed trash bin with handle and lid
    # -------------------------------------------------------------------------
    {
        "name": "Delete",
        "description": "Trash waste bin with lid handle and ribbed container",
        "svg_content": """  <!-- Lid & handle -->
  <line x1="3" y1="6" x2="21" y2="6" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
  <!-- Tapered bin body -->
  <path d="M5.5 6l1.2 13.5a1.5 1.5 0 0 0 1.5 1.5h7.6a1.5 1.5 0 0 0 1.5-1.5L18.5 6" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- Ribs -->
  <line x1="10" y1="10" x2="10" y2="16" stroke="white" stroke-width="1.8" stroke-linecap="round"/>
  <line x1="14" y1="10" x2="14" y2="16" stroke="white" stroke-width="1.8" stroke-linecap="round"/>""",
        "kt_paths": [
            """// Lid
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
                moveTo(14f, 10f); lineTo(14f, 16f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 23. NewFolder
    # Folder outline with a plus badge at bottom right
    # -------------------------------------------------------------------------
    {
        "name": "NewFolder",
        "description": "Directory folder with creation plus badge in corner",
        "svg_content": """  <!-- Folder body -->
  <path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v2M3 7v11a2 2 0 0 0 2 2h7" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- Plus badge -->
  <line x1="18" y1="14" x2="18" y2="20" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <line x1="15" y1="17" x2="21" y2="17" stroke="white" stroke-width="2" stroke-linecap="round"/>""",
        "kt_paths": [
            """// Folder frame
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
                moveTo(15f, 17f); lineTo(21f, 17f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 24. Upload
    # Upward transfer arrow emerging from an open tray container
    # -------------------------------------------------------------------------
    {
        "name": "Upload",
        "description": "Upload file transfer arrow emerging from tray",
        "svg_content": """  <!-- Up arrow -->
  <path d="M12 15V3m0 0l-4 4m4-4l4 4" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
  <!-- Base tray -->
  <path d="M4 14v5a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-5" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>""",
        "kt_paths": [
            """// Arrow stem & head
                moveTo(12f, 15f); lineTo(12f, 3f)
                moveTo(8f, 7f); lineTo(12f, 3f); lineTo(16f, 7f)

                // Tray
                moveTo(4f, 14f)
                lineTo(4f, 19f)
                arcTo(2f, 2f, 0f, false, false, 6f, 21f)
                lineTo(18f, 21f)
                arcTo(2f, 2f, 0f, false, false, 20f, 19f)
                lineTo(20f, 14f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 25. Paste
    # Clipboard with a page sheet clipped in front
    # -------------------------------------------------------------------------
    {
        "name": "Paste",
        "description": "Clipboard with incoming pasted document sheet",
        "svg_content": """  <!-- Clipboard frame & clip -->
  <path d="M8 4H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2v-2" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <rect x="8" y="2" width="8" height="4" rx="1" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- Pasted sheet -->
  <rect x="11" y="9" width="9" height="11" rx="1.5" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <line x1="14" y1="13" x2="17" y2="13" stroke="white" stroke-width="1.5" stroke-linecap="round"/>
  <line x1="14" y1="16" x2="17" y2="16" stroke="white" stroke-width="1.5" stroke-linecap="round"/>""",
        "kt_paths": [
            """// Clipboard back
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
                moveTo(14f, 16f); lineTo(17f, 16f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 26. Copy
    # Two staggered overlapping document sheets
    # -------------------------------------------------------------------------
    {
        "name": "Copy",
        "description": "Two staggered overlapping duplicate document cards",
        "svg_content": """  <!-- Back sheet -->
  <path d="M8 5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- Front sheet -->
  <rect x="4" y="7" width="12" height="14" rx="2" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>""",
        "kt_paths": [
            """// Back sheet
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
                close()"""
        ]
    },

    # -------------------------------------------------------------------------
    # 27. Edit
    # Stylus / pencil angled at 45 degrees drawing a base line
    # -------------------------------------------------------------------------
    {
        "name": "Edit",
        "description": "Precision pencil stylus editing a base line",
        "svg_content": """  <!-- Stylus pencil body -->
  <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- Pencil tip divider -->
  <line x1="14.5" y1="5.5" x2="17.5" y2="8.5" stroke="white" stroke-width="2" stroke-linecap="round"/>""",
        "kt_paths": [
            """// Pencil outline
                moveTo(17f, 3f)
                arcTo(2.12f, 2.12f, 0f, false, true, 20f, 6f)
                lineTo(7f, 19f)
                lineTo(3f, 20f)
                lineTo(4f, 16f)
                close()

                // Pencil band
                moveTo(14.5f, 5.5f); lineTo(17.5f, 8.5f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 28. OpenIn
    # Window container with an arrow bursting out diagonally to top right
    # -------------------------------------------------------------------------
    {
        "name": "OpenIn",
        "description": "Launch external application window arrow shooting outward",
        "svg_content": """  <!-- Window frame with gap -->
  <path d="M12 4H5a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2h13a2 2 0 0 0 2-2v-7" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- Outward arrow -->
  <path d="M14 3h7v7m0-7L10 14" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>""",
        "kt_paths": [
            """// Base window frame
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
                moveTo(21f, 3f); lineTo(10f, 14f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 29. Lock
    # Secured padlock with closed U-shackle and keyhole
    # -------------------------------------------------------------------------
    {
        "name": "Lock",
        "description": "Locked padlock with closed curved U-shackle and center keyhole",
        "svg_content": """  <!-- Closed shackle -->
  <path d="M7 10V7a5 5 0 0 1 10 0v3" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- Lock body -->
  <rect x="4" y="10" width="16" height="11" rx="2" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- Keyhole -->
  <circle cx="12" cy="14.5" r="1.2" fill="white"/>
  <line x1="12" y1="15.5" x2="12" y2="17.5" stroke="white" stroke-width="1.8" stroke-linecap="round"/>""",
        "kt_paths": [
            """// Closed U-shackle
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
                moveTo(12f, 15.5f); lineTo(12f, 18f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 30. LockOpen
    # Unlocked padlock with open rotated/swung U-shackle (distinct and properly open!)
    # -------------------------------------------------------------------------
    {
        "name": "LockOpen",
        "description": "Unlocked padlock with open lifted U-shackle and center keyhole",
        "svg_content": """  <!-- Open rotated shackle -->
  <path d="M7 10V6a5 5 0 0 1 9.5-2.2" stroke="white" stroke-width="2" stroke-linecap="round" fill="none"/>
  <!-- Lock body -->
  <rect x="4" y="10" width="16" height="11" rx="2" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- Keyhole -->
  <circle cx="12" cy="14.5" r="1.2" fill="white"/>
  <line x1="12" y1="15.5" x2="12" y2="17.5" stroke="white" stroke-width="1.8" stroke-linecap="round"/>""",
        "kt_paths": [
            """// Open lifted shackle (swung open and upward)
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
                moveTo(12f, 15.5f); lineTo(12f, 18f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 31. Undo
    # Counter-clockwise return arc arrow
    # -------------------------------------------------------------------------
    {
        "name": "Undo",
        "description": "Undo action with counter-clockwise return arc arrow",
        "svg_content": """  <path d="M9 7L4 12l5 5" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
  <path d="M4 12h10a6 6 0 0 1 6 6" stroke="white" stroke-width="2" stroke-linecap="round"/>""",
        "kt_paths": [
            """moveTo(9f, 7f); lineTo(4f, 12f); lineTo(9f, 17f)
                moveTo(4f, 12f); lineTo(14f, 12f)
                arcTo(6f, 6f, 0f, false, true, 20f, 18f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 32. Disconnect
    # Broken link / unplugged connector prongs
    # -------------------------------------------------------------------------
    {
        "name": "Disconnect",
        "description": "Eject and disconnect power/data link with uncoupled prongs",
        "svg_content": """  <!-- Left plug body & cord -->
  <path d="M2 12h4m2-4v8m0-6h3a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1H8" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- Disconnect slash / break spark -->
  <line x1="14" y1="4" x2="16" y2="8" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <line x1="14" y1="16" x2="16" y2="20" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <!-- Right socket -->
  <path d="M19 8v8m3-4h-3" stroke="white" stroke-width="2" stroke-linecap="round"/>""",
        "kt_paths": [
            """// Left plug
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
                moveTo(18f, 12f); lineTo(22f, 12f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 33. Play
    # Equilateral play triangle pointing right with rounded vertices
    # -------------------------------------------------------------------------
    {
        "name": "Play",
        "description": "Resume execution right-pointing play triangle",
        "svg_content": """  <polygon points="7,4 20,12 7,20" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="white"/>""",
        "kt_paths": [
            """moveTo(7f, 4f)
                lineTo(20f, 12f)
                lineTo(7f, 20f)
                close()"""
        ]
    },

    # -------------------------------------------------------------------------
    # 34. Info
    # Round badge with centered lowercase 'i' letter
    # -------------------------------------------------------------------------
    {
        "name": "Info",
        "description": "Information and application details badge",
        "svg_content": """  <circle cx="12" cy="12" r="9" stroke="white" stroke-width="2" fill="none"/>
  <circle cx="12" cy="8" r="1.2" fill="white"/>
  <line x1="12" y1="11" x2="12" y2="16.5" stroke="white" stroke-width="2" stroke-linecap="round"/>""",
        "kt_paths": [
            """moveTo(12f, 21f)
                arcTo(9f, 9f, 0f, true, true, 12f, 3f)
                arcTo(9f, 9f, 0f, true, true, 12f, 21f)
                close()

                moveTo(12f, 8.8f)
                arcTo(1f, 1f, 0f, true, true, 12f, 6.8f)
                arcTo(1f, 1f, 0f, true, true, 12f, 8.8f)
                close()

                moveTo(12f, 11f); lineTo(12f, 16.5f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 35. ChevronDown
    # Downward pointing angle bracket
    # -------------------------------------------------------------------------
    {
        "name": "ChevronDown",
        "description": "Downward expand chevron arrow",
        "svg_content": """  <path d="M6 9l6 6 6-6" stroke="white" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>""",
        "kt_paths": [
            """moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 36. ChevronRight
    # Rightward pointing angle bracket
    # -------------------------------------------------------------------------
    {
        "name": "ChevronRight",
        "description": "Rightward expand chevron arrow",
        "svg_content": """  <path d="M9 6l6 6-6 6" stroke="white" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>""",
        "kt_paths": [
            """moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 37. Check
    # Clean checkmark tick with rounded ends
    # -------------------------------------------------------------------------
    {
        "name": "Check",
        "description": "Checkmark approval and confirmation tick",
        "svg_content": """  <path d="M4 12.5l5 5L20 6.5" stroke="white" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>""",
        "kt_paths": [
            """moveTo(4f, 12.5f); lineTo(9f, 17.5f); lineTo(20f, 6.5f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 38. MoreVert
    # Three vertically aligned options dots
    # -------------------------------------------------------------------------
    {
        "name": "MoreVert",
        "description": "Three vertical option context menu dots",
        "svg_content": """  <circle cx="12" cy="5" r="1.5" fill="white"/>
  <circle cx="12" cy="12" r="1.5" fill="white"/>
  <circle cx="12" cy="19" r="1.5" fill="white"/>""",
        "kt_paths": [
            """moveTo(12f, 6.5f)
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
                close()"""
        ]
    },

    # -------------------------------------------------------------------------
    # 39. Undock
    # Window popping out into floating mode with detached floating window and arrow
    # -------------------------------------------------------------------------
    {
        "name": "Undock",
        "description": "Pop out window into floating detached panel",
        "svg_content": """  <!-- Back base panel -->
  <path d="M14 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1h5" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- Floating front panel -->
  <rect x="10" y="4" width="10" height="8" rx="1.5" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- Pop-out diagonal arrow -->
  <path d="M8 16l6-6m0 0h-3m3 0v3" stroke="white" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>""",
        "kt_paths": [
            """// Back base container
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
                moveTo(11f, 10f); lineTo(14f, 10f); lineTo(14f, 13f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 40. Dock
    # Window docking back into frame slot with downward positioning arrow
    # -------------------------------------------------------------------------
    {
        "name": "Dock",
        "description": "Dock floating panel back into host layout frame",
        "svg_content": """  <!-- Host docking frame -->
  <rect x="3" y="3" width="18" height="18" rx="2" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- Docking floor line -->
  <line x1="3" y1="16" x2="21" y2="16" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <!-- Downward docking arrow -->
  <path d="M12 6v6m0 0l-3-3m3 3l3-3" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>""",
        "kt_paths": [
            """// Dock frame
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
                moveTo(9f, 9f); lineTo(12f, 12f); lineTo(15f, 9f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 41. PS5Console
    # Modern PS5 console vertical silhouette with iconic curved aerodynamic collars
    # -------------------------------------------------------------------------
    {
        "name": "PS5Console",
        "description": "PlayStation 5 console standing profile with curved side wing plates",
        "svg_content": """  <!-- Outer curved aerodynamic wing plates -->
  <path d="M6 3c1.5 6 1.5 12 0 18h12c-1.5-6-1.5-12 0-18H6z" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- Center dark tower strip -->
  <line x1="12" y1="6" x2="12" y2="18" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <!-- Power LED blue slit bar -->
  <line x1="9.5" y1="4.5" x2="14.5" y2="4.5" stroke="white" stroke-width="1.8" stroke-linecap="round"/>""",
        "kt_paths": [
            """// Curved collar plates
                moveTo(6f, 3f)
                curveTo(7.5f, 9f, 7.5f, 15f, 6f, 21f)
                lineTo(18f, 21f)
                curveTo(16.5f, 15f, 16.5f, 9f, 18f, 3f)
                close()

                // Center tower body line
                moveTo(12f, 6f); lineTo(12f, 18f)

                // Power LED accent line
                moveTo(9.5f, 4.5f); lineTo(14.5f, 4.5f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 42. ViewHex
    # Hexadecimal inspection lens focused on "0x" (distinct from WatchList!)
    # -------------------------------------------------------------------------
    {
        "name": "ViewHex",
        "description": "Hexadecimal memory inspector badge displaying 0x prefix",
        "svg_content": """  <!-- Inspection lens / frame -->
  <rect x="2" y="5" width="20" height="14" rx="3" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- '0' character -->
  <rect x="6" y="9" width="4.5" height="6" rx="1.5" stroke="white" stroke-width="1.8" fill="none"/>
  <!-- 'x' character -->
  <path d="M13.5 10l4.5 4.5m0-4.5l-4.5 4.5" stroke="white" stroke-width="1.8" stroke-linecap="round"/>""",
        "kt_paths": [
            """// Outer hex badge
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
                moveTo(18f, 10f); lineTo(13.5f, 15f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 43. ZoomIn
    # Magnifying glass with plus (+) in center
    # -------------------------------------------------------------------------
    {
        "name": "ZoomIn",
        "description": "Graph zoom-in magnifying glass with plus",
        "svg_content": """  <circle cx="10" cy="10" r="7" stroke="white" stroke-width="2" fill="none"/>
  <line x1="15" y1="15" x2="21" y2="21" stroke="white" stroke-width="2.5" stroke-linecap="round"/>
  <line x1="10" y1="7" x2="10" y2="13" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <line x1="7" y1="10" x2="13" y2="10" stroke="white" stroke-width="2" stroke-linecap="round"/>""",
        "kt_paths": [
            """moveTo(10f, 17f)
                arcTo(7f, 7f, 0f, true, true, 10f, 3f)
                arcTo(7f, 7f, 0f, true, true, 10f, 17f)
                close()

                moveTo(15f, 15f); lineTo(21f, 21f)
                moveTo(10f, 7f); lineTo(10f, 13f)
                moveTo(7f, 10f); lineTo(13f, 10f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 44. ZoomOut
    # Magnifying glass with minus (-) in center
    # -------------------------------------------------------------------------
    {
        "name": "ZoomOut",
        "description": "Graph zoom-out magnifying glass with minus",
        "svg_content": """  <circle cx="10" cy="10" r="7" stroke="white" stroke-width="2" fill="none"/>
  <line x1="15" y1="15" x2="21" y2="21" stroke="white" stroke-width="2.5" stroke-linecap="round"/>
  <line x1="7" y1="10" x2="13" y2="10" stroke="white" stroke-width="2" stroke-linecap="round"/>""",
        "kt_paths": [
            """moveTo(10f, 17f)
                arcTo(7f, 7f, 0f, true, true, 10f, 3f)
                arcTo(7f, 7f, 0f, true, true, 10f, 17f)
                close()

                moveTo(15f, 15f); lineTo(21f, 21f)
                moveTo(7f, 10f); lineTo(13f, 10f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 45. ResetView
    # Viewport focus target brackets with centered point
    # -------------------------------------------------------------------------
    {
        "name": "ResetView",
        "description": "Reset viewport and fit graph to screen frame target",
        "svg_content": """  <!-- Corner brackets -->
  <path d="M4 8V5a1 1 0 0 1 1-1h3M20 8V5a1 1 0 0 0-1-1h-3M4 16v3a1 1 0 0 0 1 1h3M20 16v3a1 1 0 0 1-1 1h-3" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
  <!-- Center crosshair -->
  <circle cx="12" cy="12" r="2" fill="white"/>""",
        "kt_paths": [
            """moveTo(4f, 8f); lineTo(4f, 5f); arcTo(1f, 1f, 0f, false, true, 5f, 4f); lineTo(8f, 4f)
                moveTo(20f, 8f); lineTo(20f, 5f); arcTo(1f, 1f, 0f, false, false, 19f, 4f); lineTo(16f, 4f)
                moveTo(4f, 16f); lineTo(4f, 19f); arcTo(1f, 1f, 0f, false, false, 5f, 20f); lineTo(8f, 20f)
                moveTo(20f, 16f); lineTo(20f, 19f); arcTo(1f, 1f, 0f, false, true, 19f, 20f); lineTo(16f, 20f)

                moveTo(12f, 14f)
                arcTo(2f, 2f, 0f, true, true, 12f, 10f)
                arcTo(2f, 2f, 0f, true, true, 12f, 14f)
                close()"""
        ]
    },

    # -------------------------------------------------------------------------
    # 46. Code
    # Code angle brackets < / >
    # -------------------------------------------------------------------------
    {
        "name": "Code",
        "description": "Code and disassembly brackets",
        "svg_content": """  <path d="M8 6L2 12l6 6M16 6l6 6-6 6M14 4l-4 16" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>""",
        "kt_paths": [
            """moveTo(8f, 6f); lineTo(2f, 12f); lineTo(8f, 18f)
                moveTo(16f, 6f); lineTo(22f, 12f); lineTo(16f, 18f)
                moveTo(14f, 4f); lineTo(10f, 20f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 47. Folder
    # Clean directory folder with top tab and rounded body
    # -------------------------------------------------------------------------
    {
        "name": "Folder",
        "description": "Directory folder storage unit",
        "svg_content": """  <path d="M3 6a2 2 0 0 1 2-2h5l2 2h7a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6z" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <line x1="3" y1="10" x2="21" y2="10" stroke="white" stroke-width="1.8" stroke-linecap="round"/>""",
        "kt_paths": [
            """moveTo(5f, 4f)
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

                moveTo(3f, 10f); lineTo(21f, 10f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 48. FileDocument
    # Document page with folded top-right corner and horizontal text lines
    # -------------------------------------------------------------------------
    {
        "name": "FileDocument",
        "description": "Text document file (.txt, .log, .ini) with written lines",
        "svg_content": """  <path d="M5 3h9l5 5v13a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <path d="M14 3v5h5" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
  <line x1="7" y1="12" x2="15" y2="12" stroke="white" stroke-width="1.8" stroke-linecap="round"/>
  <line x1="7" y1="16" x2="13" y2="16" stroke="white" stroke-width="1.8" stroke-linecap="round"/>""",
        "kt_paths": [
            """moveTo(5f, 3f)
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
                moveTo(7f, 16f); lineTo(13f, 16f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 49. FileJson
    # JSON data file with { } curly braces
    # -------------------------------------------------------------------------
    {
        "name": "FileJson",
        "description": "Structured JSON file with curly code braces",
        "svg_content": """  <path d="M5 3h9l5 5v13a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <path d="M14 3v5h5" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
  <!-- Left brace { -->
  <path d="M9.5 12c-.8 0-1.2.4-1.2 1v.5c0 .6-.4 1-1 1 .6 0 1 .4 1 1v.5c0 .6.4 1 1.2 1" stroke="white" stroke-width="1.8" stroke-linecap="round"/>
  <!-- Right brace } -->
  <path d="M14.5 12c.8 0 1.2.4 1.2 1v.5c0 .6.4 1 1 1-.6 0-1 .4-1 1v.5c0 .6-.4 1-1.2 1" stroke="white" stroke-width="1.8" stroke-linecap="round"/>""",
        "kt_paths": [
            """moveTo(5f, 3f)
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
                curveTo(15.7f, 16.6f, 15.3f, 17f, 14.5f, 17f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 50. FileExecutable
    # Binary / ELF executable with binary 01 processor core emblem
    # -------------------------------------------------------------------------
    {
        "name": "FileExecutable",
        "description": "Binary executable file (.elf, .bin, .pkg) with execution core",
        "svg_content": """  <path d="M5 3h9l5 5v13a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <path d="M14 3v5h5" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
  <!-- Binary / chip emblem -->
  <polygon points="11,11 14,14 11,17" fill="white"/>
  <line x1="8" y1="14" x2="11" y2="14" stroke="white" stroke-width="2" stroke-linecap="round"/>""",
        "kt_paths": [
            """moveTo(5f, 3f)
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
                moveTo(8f, 14f); lineTo(11f, 14f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 51. FileLibrary
    # PRX / SPRX dynamic library module with interlocking puzzle connector
    # -------------------------------------------------------------------------
    {
        "name": "FileLibrary",
        "description": "Dynamic library module (.prx, .sprx) with interlocking modular connector",
        "svg_content": """  <path d="M5 3h9l5 5v13a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <path d="M14 3v5h5" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
  <!-- Interlocking puzzle piece -->
  <path d="M8 12h2a1.5 1.5 0 0 1 3 0h2v4h-2a1.5 1.5 0 0 1-3 0H8v-4z" stroke="white" stroke-width="1.8" stroke-linejoin="round" fill="none"/>""",
        "kt_paths": [
            """moveTo(5f, 3f)
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
                close()"""
        ]
    },

    # -------------------------------------------------------------------------
    # 52. FileDatabase
    # SQLite / database file with stacked storage platters
    # -------------------------------------------------------------------------
    {
        "name": "FileDatabase",
        "description": "Database file (.db, .sqlite) with cylinder platters",
        "svg_content": """  <path d="M5 3h9l5 5v13a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <path d="M14 3v5h5" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
  <!-- Database cylinder top -->
  <ellipse cx="11.5" cy="12.5" rx="3.5" ry="1.5" stroke="white" stroke-width="1.6" fill="none"/>
  <!-- Cylinder walls & bottoms -->
  <path d="M8 12.5v2c0 .8 1.6 1.5 3.5 1.5s3.5-.7 3.5-1.5v-2" stroke="white" stroke-width="1.6"/>
  <path d="M8 14.5v2c0 .8 1.6 1.5 3.5 1.5s3.5-.7 3.5-1.5v-2" stroke="white" stroke-width="1.6"/>""",
        "kt_paths": [
            """moveTo(5f, 3f)
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
                lineTo(15f, 14.5f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 53. FileGeneric
    # Blank document sheet with clean dog-eared fold
    # -------------------------------------------------------------------------
    {
        "name": "FileGeneric",
        "description": "Generic file sheet with dog-eared fold",
        "svg_content": """  <path d="M5 3h9l5 5v13a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <path d="M14 3v5h5" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>""",
        "kt_paths": [
            """moveTo(5f, 3f)
                lineTo(14f, 3f)
                lineTo(19f, 8f)
                lineTo(19f, 21f)
                arcTo(2f, 2f, 0f, false, true, 17f, 23f)
                lineTo(5f, 23f)
                arcTo(2f, 2f, 0f, false, true, 3f, 21f)
                lineTo(3f, 5f)
                arcTo(2f, 2f, 0f, false, true, 5f, 3f)
                close()

                moveTo(14f, 3f); lineTo(14f, 8f); lineTo(19f, 8f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 54. SettingsGeneral
    # Three configuration slider tracks with knobs at staggered positions
    # -------------------------------------------------------------------------
    {
        "name": "SettingsGeneral",
        "description": "General settings configuration equalizer sliders with knobs",
        "svg_content": """  <!-- Slider 1 -->
  <line x1="4" y1="7" x2="20" y2="7" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <circle cx="9" cy="7" r="2.5" stroke="white" stroke-width="2" fill="#0E121B"/>
  <!-- Slider 2 -->
  <line x1="4" y1="12" x2="20" y2="12" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <circle cx="16" cy="12" r="2.5" stroke="white" stroke-width="2" fill="#0E121B"/>
  <!-- Slider 3 -->
  <line x1="4" y1="17" x2="20" y2="17" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <circle cx="12" cy="17" r="2.5" stroke="white" stroke-width="2" fill="#0E121B"/>""",
        "kt_paths": [
            """// Slider track 1
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
                close()"""
        ]
    },

    # -------------------------------------------------------------------------
    # 55. SettingsNetwork
    # Globe with latitude equator and meridian curves
    # -------------------------------------------------------------------------
    {
        "name": "SettingsNetwork",
        "description": "Network connectivity globe with equator and meridian lines",
        "svg_content": """  <!-- Outer globe -->
  <circle cx="12" cy="12" r="9" stroke="white" stroke-width="2" fill="none"/>
  <!-- Equator -->
  <line x1="3" y1="12" x2="21" y2="12" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <!-- Meridians -->
  <ellipse cx="12" cy="12" rx="4.5" ry="9" stroke="white" stroke-width="1.8" fill="none"/>""",
        "kt_paths": [
            """// Globe circle
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
                curveTo(16f, 7f, 16f, 17f, 12f, 21f)"""
        ]
    },

    # -------------------------------------------------------------------------
    # 56. SettingsSimulation
    # Laboratory Erlenmeyer conical flask with liquid level and reaction bubbles
    # -------------------------------------------------------------------------
    {
        "name": "SettingsSimulation",
        "description": "Laboratory mock simulation flask with chemical reaction bubbles",
        "svg_content": """  <!-- Flask neck rim -->
  <line x1="9" y1="3" x2="15" y2="3" stroke="white" stroke-width="2" stroke-linecap="round"/>
  <!-- Flask body -->
  <path d="M10 3v5l-6 11a1.5 1.5 0 0 0 1.3 2h13.4a1.5 1.5 0 0 0 1.3-2l-6-11V3" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
  <!-- Fluid level line -->
  <path d="M7 15c2-1 4 1 6 0s3-1 4 0" stroke="white" stroke-width="1.8" stroke-linecap="round"/>
  <!-- Simulation bubbles -->
  <circle cx="10" cy="18" r="0.8" fill="white"/>
  <circle cx="13" cy="17" r="1.2" fill="white"/>""",
        "kt_paths": [
            """// Lip rim
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
                close()"""
        ]
    }
]

def generate_svg_files(target_dir):
    os.makedirs(target_dir, exist_ok=True)
    for icon in icons_data:
        filename = os.path.join(target_dir, f"{icon['name']}.svg")
        content = f"""<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none">
{icon['svg_content']}
</svg>
"""
        with open(filename, "w", encoding="utf-8") as f:
            f.write(content)
    print(f"Wrote {len(icons_data)} SVG files to {target_dir}")

def generate_kotlin_file(target_file):
    lines = []
    lines.append("package com.osr.ps5debugger.ui.icons")
    lines.append("")
    lines.append("import androidx.compose.ui.graphics.Color")
    lines.append("import androidx.compose.ui.graphics.SolidColor")
    lines.append("import androidx.compose.ui.graphics.StrokeCap")
    lines.append("import androidx.compose.ui.graphics.StrokeJoin")
    lines.append("import androidx.compose.ui.graphics.vector.ImageVector")
    lines.append("import androidx.compose.ui.graphics.vector.path")
    lines.append("import androidx.compose.ui.unit.dp")
    lines.append("")
    lines.append("/**")
    lines.append(" * Custom modern vector icon suite for PS5 Remote Debugger.")
    lines.append(" * Every icon is handcrafted as a distinct, unified 24x24 vector with consistent")
    lines.append(" * 2dp stroke geometry, rounded joins and caps, and sleek PlayStation developer styling.")
    lines.append(" */")
    lines.append("object PS5Icons {")
    lines.append("    private inline fun icon(")
    lines.append("        name: String,")
    lines.append("        crossinline builder: ImageVector.Builder.() -> Unit")
    lines.append("    ): ImageVector {")
    lines.append("        return ImageVector.Builder(")
    lines.append("            name = name,")
    lines.append("            defaultWidth = 24.dp,")
    lines.append("            defaultHeight = 24.dp,")
    lines.append("            viewportWidth = 24f,")
    lines.append("            viewportHeight = 24f")
    lines.append("        ).apply(builder).build()")
    lines.append("    }")
    lines.append("")

    for icon in icons_data:
        name = icon["name"]
        desc = icon.get("description", name)
        lines.append(f"    /**")
        lines.append(f"     * {desc}")
        lines.append(f"     */")
        lines.append(f"    val {name}: ImageVector by lazy {{")
        lines.append(f"        icon(\"{name}\") {{")
        for p in icon["kt_paths"]:
            lines.append("            path(")
            lines.append("                stroke = SolidColor(Color.White),")
            lines.append("                strokeLineWidth = 2f,")
            lines.append("                strokeLineCap = StrokeCap.Round,")
            lines.append("                strokeLineJoin = StrokeJoin.Round")
            lines.append("            ) {")
            for path_line in p.split("\n"):
                lines.append(f"                {path_line.strip()}")
            lines.append("            }")
        lines.append("        }")
        lines.append("    }")
        lines.append("")

    lines.append("}")
    lines.append("")

    with open(target_file, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print(f"Wrote Kotlin vector definitions to {target_file}")

if __name__ == "__main__":
    generate_svg_files("assets/icons")
    generate_kotlin_file("client/src/commonMain/kotlin/com/osr/ps5debugger/ui/icons/PS5Icons.kt")

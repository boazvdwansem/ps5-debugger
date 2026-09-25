# PS5 Debugger MCP Server

The **PS5 Debugger MCP Server** exposes your PlayStation 5 Debugger application and the connected PS5 console to AI coding assistants (like Antigravity, Claude Desktop, Cursor, and other Model Context Protocol clients).

It allows AI models to directly read memory, inspect disassembly, retrieve function control-flow graphs (CFGs), search cross-references, examine register states, unwind call stacks, and navigate the desktop UI in real time.

---

## Architecture

```
+------------------------------------+
|  AI Assistant (Antigravity / MCP)   |
+-----------------+------------------+
                  |  Stdio Transport (MCP JSON-RPC)
                  v
+-----------------+------------------+
|    ps5_mcp_server.py (FastMCP)     |
+-----------------+------------------+
                  |  Local REST API (http://127.0.0.1:8585)
                  v
+-----------------+------------------+
|     Desktop App (McpHttpServer)    |
|   AppContainer / MainState         |
+-----------------+------------------+
                  |  TCP Wire Protocol (ps5debug-NG.elf)
                  v
+-----------------+------------------+
|       PlayStation 5 Console        |
+------------------------------------+
```

1. **Desktop App Embedded Server (`McpHttpServer.kt`)**: Runs a lightweight, zero-dependency HTTP server on `http://127.0.0.1:8585` inside the Kotlin Desktop app. It connects to `AppContainer`, UI state, disassembler engine, and the active `Ps5Client`.
2. **Python MCP Server (`ps5_mcp_server.py`)**: Uses `mcp.server.fastmcp.FastMCP` to serve standard MCP tools over `stdio`. It connects to the desktop app's REST endpoints and provides graceful fallback responses if the desktop app is not connected or offline.

---

## Exposed MCP Tools

| Tool Name | Parameters | Description |
|---|---|---|
| `ps5_get_status` | none | Checks connection status, target IP, active process PID/name, title ID, and statistics. |
| `ps5_list_processes` | none | Lists running processes on the PS5 with their PIDs and names. |
| `ps5_attach_process` | `pid: int` | Attaches the debugger to a target process. |
| `ps5_list_memory_maps` | `pid?: int` | Returns virtual memory mappings, sections, protection flags (`r-x`, `rw-`), and sizes. |
| `ps5_read_memory_hex` | `address: str`, `length?: int`, `pid?: int` | Reads memory and returns hex dump, raw hex, ASCII string, and 32-bit DWORDs. |
| `ps5_write_memory` | `address: str`, `hex_data: str`, `pid?: int` | Writes bytes to process memory. |
| `ps5_get_disassembly` | `address: str`, `length?: int`, `max_instructions?: int`, `pid?: int` | Disassembles instructions with resolved symbols, mnemonics, operands, jump targets, and comments. |
| `ps5_get_function` | `address?: str`, `name?: str`, `pid?: int` | Disassembles an entire function subroutine up to RET or end. |
| `ps5_get_cfg` | `address: str`, `pid?: int` | Generates Control Flow Graph (CFG) basic blocks and jump edges matching the Graph View. |
| `ps5_list_functions` | none | Lists all discovered function entry points in the active module or process. |
| `ps5_list_symbols` | none | Lists all known symbols, imports, exports, and NID-resolved names. |
| `ps5_find_xrefs` | `address: str`, `scan_address?: str`, `scan_length?: int`, `pid?: int` | Finds cross-references (calls, jumps, RIP-relative memory references) targeting an address. |
| `ps5_get_registers` | `lwpid?: int` | Reads CPU general-purpose registers (RAX, RBX, RCX, RDX, RSI, RDI, RSP, RBP, R8-R15, RIP, RFLAGS). |
| `ps5_get_stack_trace` | `pid?: int`, `depth?: int` | Walks the in-kernel RBP stack frame chain and resolves function names for each frame. |
| `ps5_navigate_ui` | `address: str`, `mode?: str` | Navigates the desktop UI to an address and view mode (`disasm`, `graph`, `hex`). |

---

## Configuration & Usage

### Antigravity / Gemini Configuration
The MCP server is registered in `~/.gemini/config/mcp_config.json`:

```json
{
  "mcpServers": {
    "ps5_debugger": {
      "command": "C:\\Users\\boazv\\AppData\\Local\\Python\\pythoncore-3.14-64\\python.exe",
      "args": [
        "c:\\Users\\boazv\\OneDrive\\Documents\\Projects\\Personal\\ps5-debugger\\tools\\mcp\\ps5_mcp_server.py"
      ]
    }
  }
}
```

Tool schemas are placed in `~/.gemini/antigravity/mcp/ps5_debugger/`.

### Claude Desktop Configuration
In `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "ps5-debugger": {
      "command": "C:\\Users\\boazv\\AppData\\Local\\Python\\pythoncore-3.14-64\\python.exe",
      "args": [
        "c:\\Users\\boazv\\OneDrive\\Documents\\Projects\\Personal\\ps5-debugger\\tools\\mcp\\ps5_mcp_server.py"
      ]
    }
  }
}
```

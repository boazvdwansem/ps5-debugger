#!/usr/bin/env python3
"""
PS5 Debugger MCP Server
Connects AI models (Antigravity, Claude Desktop, Cursor) to the PS5 Debugger Kotlin Desktop application
and its connected PlayStation 5 console.
"""

import os
import sys
import json
import urllib.request
import urllib.error
from typing import Optional, Any, Dict, List
from mcp.server.fastmcp import FastMCP

# Default to localhost:8585 where McpHttpServer runs in the desktop app
BASE_URL = os.environ.get("PS5_DEBUGGER_URL", "http://127.0.0.1:8585").rstrip("/")

mcp = FastMCP("ps5-debugger")

def _request(endpoint: str, method: str = "GET", body: Optional[Dict[str, Any]] = None, timeout: float = 15.0) -> Dict[str, Any]:
    url = f"{BASE_URL}{endpoint}"
    headers = {"Accept": "application/json"}
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json; charset=utf-8"
        data = json.dumps(body).encode("utf-8")

    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            content = resp.read().decode("utf-8")
            return json.loads(content)
    except urllib.error.URLError as e:
        if isinstance(e.reason, ConnectionRefusedError) or "refused" in str(e).lower() or "target machine actively refused" in str(e).lower():
            return {
                "success": False,
                "error": f"PS5 Debugger desktop application is not running or MCP server is not reachable at {BASE_URL}. Please start the desktop app."
            }
        return {"success": False, "error": f"Network error communicating with desktop app: {str(e)}"}
    except Exception as e:
        return {"success": False, "error": f"Request failed: {str(e)}"}

# ==============================================================================
# MCP Tools
# ==============================================================================

@mcp.tool()
def ps5_get_status() -> str:
    """Get the current status of the PS5 Debugger application and PS5 console connection.
    Returns whether the console is connected, target IP, active process PID/name, titleId, contentId, and statistics.
    """
    res = _request("/api/status")
    return json.dumps(res, indent=2)

@mcp.tool()
def ps5_list_processes() -> str:
    """List all running processes on the connected PlayStation 5 console.
    Returns process IDs (PID) and process names (e.g. eboot.bin, SceShellCore, etc.).
    """
    res = _request("/api/processes")
    return json.dumps(res, indent=2)

@mcp.tool()
def ps5_attach_process(pid: int) -> str:
    """Attach the debugger to a specific target process on the PS5 by its Process ID (PID).
    
    Args:
        pid: The process ID to attach to.
    """
    res = _request("/api/process/attach", method="POST", body={"pid": pid})
    return json.dumps(res, indent=2)

@mcp.tool()
def ps5_list_memory_maps(pid: Optional[int] = None) -> str:
    """List the virtual memory mappings (sections, segments, libraries) of the target process.
    Returns memory ranges (start address, end address, size, protection flags e.g. r-x, and region name).
    
    Args:
        pid: Optional process ID. Defaults to the currently selected/active process.
    """
    endpoint = f"/api/maps?pid={pid}" if pid is not None else "/api/maps"
    res = _request(endpoint)
    return json.dumps(res, indent=2)

@mcp.tool()
def ps5_read_memory_hex(address: str, length: int = 64, pid: Optional[int] = None) -> str:
    """Read memory from the PS5 process or loaded binary module.
    Returns a formatted hex dump (similar to Ghidra/IDA hex view), raw byte hex, ASCII text, and 32-bit DWORDs.
    
    Args:
        address: Start memory address in hex (e.g. '0x400000' or '400000') or decimal.
        length: Number of bytes to read (default 64, max 65536).
        pid: Optional process ID. Defaults to active process.
    """
    params = [f"address={address}", f"length={length}"]
    if pid is not None:
        params.append(f"pid={pid}")
    endpoint = f"/api/memory/read?{'&'.join(params)}"
    res = _request(endpoint)
    return json.dumps(res, indent=2)

@mcp.tool()
def ps5_write_memory(address: str, hex_data: str, pid: Optional[int] = None) -> str:
    """Write bytes to memory inside the PS5 process.
    
    Args:
        address: Target memory address in hex (e.g. '0x400000').
        hex_data: Hexadecimal byte string to write (e.g. '90909090' for 4 NOPs).
        pid: Optional process ID. Defaults to active process.
    """
    body = {"address": address, "hex": hex_data}
    if pid is not None:
        body["pid"] = pid
    res = _request("/api/memory/write", method="POST", body=body)
    return json.dumps(res, indent=2)

@mcp.tool()
def ps5_get_disassembly(address: str, length: int = 128, max_instructions: int = 50, pid: Optional[int] = None) -> str:
    """Disassemble code at a specific memory address or range on the PS5.
    Returns structured instructions with address, machine bytes, mnemonic, operands, resolved symbols, jump targets, and comments.
    
    Args:
        address: Start address in hex (e.g. '0x401000').
        length: Approximate byte length of the code region to disassemble (default 128).
        max_instructions: Maximum number of instructions to return (default 50).
        pid: Optional process ID. Defaults to active process.
    """
    params = [f"address={address}", f"length={length}", f"max={max_instructions}"]
    if pid is not None:
        params.append(f"pid={pid}")
    endpoint = f"/api/disassembly?{'&'.join(params)}"
    res = _request(endpoint)
    return json.dumps(res, indent=2)

@mcp.tool()
def ps5_get_function(address: Optional[str] = None, name: Optional[str] = None, pid: Optional[int] = None) -> str:
    """Disassemble an entire function by address or symbol name.
    Identifies the function boundary and returns all instructions belonging to the subroutine.
    
    Args:
        address: Optional function entry address in hex (e.g. '0x401230').
        name: Optional function symbol name (e.g. 'sceKernelCreateEqueue' or 'sub_401230').
        pid: Optional process ID. Defaults to active process.
    """
    params = []
    if address:
        params.append(f"address={address}")
    if name:
        params.append(f"name={name}")
    if pid is not None:
        params.append(f"pid={pid}")
    endpoint = f"/api/function?{'&'.join(params)}"
    res = _request(endpoint)
    return json.dumps(res, indent=2)

@mcp.tool()
def ps5_get_cfg(address: str, pid: Optional[int] = None) -> str:
    """Retrieve the Control Flow Graph (CFG) for a function at the given address.
    Returns basic block nodes (with their disassembled instructions) and branch edges (TRUE, FALSE, UNCONDITIONAL), matching the desktop app's Graph View.
    
    Args:
        address: Entry address of the function in hex (e.g. '0x401000').
        pid: Optional process ID. Defaults to active process.
    """
    params = [f"address={address}"]
    if pid is not None:
        params.append(f"pid={pid}")
    endpoint = f"/api/cfg?{'&'.join(params)}"
    res = _request(endpoint)
    return json.dumps(res, indent=2)

@mcp.tool()
def ps5_list_functions() -> str:
    """List all discovered function entry points and subroutines in the active module or process."""
    res = _request("/api/functions")
    return json.dumps(res, indent=2)

@mcp.tool()
def ps5_list_symbols() -> str:
    """List all known symbols (imports, exports, labels, and NID-resolved names) from the loaded ELF or target process."""
    res = _request("/api/symbols")
    return json.dumps(res, indent=2)

@mcp.tool()
def ps5_find_xrefs(address: str, scan_address: Optional[str] = None, scan_length: Optional[int] = None, pid: Optional[int] = None) -> str:
    """Find code cross-references (calls, jumps, RIP-relative memory references) targeting a specific address.
    
    Args:
        address: The target address in hex to search for references to (e.g. '0x402500').
        scan_address: Optional start of memory range to scan.
        scan_length: Optional size of memory range to scan.
        pid: Optional process ID. Defaults to active process.
    """
    params = [f"address={address}"]
    if scan_address:
        params.append(f"scan_address={scan_address}")
    if scan_length:
        params.append(f"scan_length={scan_length}")
    if pid is not None:
        params.append(f"pid={pid}")
    endpoint = f"/api/xrefs?{'&'.join(params)}"
    res = _request(endpoint)
    return json.dumps(res, indent=2)

@mcp.tool()
def ps5_get_registers(lwpid: Optional[int] = None) -> str:
    """Read CPU register state (RAX, RBX, RCX, RDX, RSI, RDI, RSP, RBP, R8-R15, RIP, RFLAGS) from a paused PS5 thread.
    
    Args:
        lwpid: Optional thread ID (LWPID). Defaults to selected thread.
    """
    endpoint = f"/api/registers?lwpid={lwpid}" if lwpid is not None else "/api/registers"
    res = _request(endpoint)
    return json.dumps(res, indent=2)

@mcp.tool()
def ps5_get_stack_trace(pid: Optional[int] = None, depth: int = 32) -> str:
    """Retrieve an in-kernel RBP call stack walk from the PS5 server.
    Returns saved RBP, return address, and function names for each stack frame.
    
    Args:
        pid: Optional process ID. Defaults to active process.
        depth: Maximum stack frames to walk (default 32).
    """
    params = [f"depth={depth}"]
    if pid is not None:
        params.append(f"pid={pid}")
    endpoint = f"/api/stack?{'&'.join(params)}"
    res = _request(endpoint)
    return json.dumps(res, indent=2)

@mcp.tool()
def ps5_navigate_ui(address: str, mode: str = "disasm") -> str:
    """Jump/scroll the desktop user interface to a specific address and view mode so the human user can see what the AI is analyzing.
    
    Args:
        address: Address in hex (e.g. '0x401000') to jump to.
        mode: View mode to activate: 'disasm' for Linear Disassembly, 'graph' for Graph View, or 'hex' for Hex Viewer.
    """
    body = {"address": address, "mode": mode}
    res = _request("/api/navigate", method="POST", body=body)
    return json.dumps(res, indent=2)

if __name__ == "__main__":
    mcp.run(transport="stdio")

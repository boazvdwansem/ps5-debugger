#!/usr/bin/env python3
"""
Test script for PS5 Debugger MCP server tools.
Verifies that all tool functions can be called and return well-formatted JSON responses.
"""
import sys
import json
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import ps5_mcp_server

def test_offline_responses():
    print("Testing MCP tools when desktop app is not connected/running...")
    tools_to_test = [
        ("ps5_get_status", lambda: ps5_mcp_server.ps5_get_status()),
        ("ps5_list_processes", lambda: ps5_mcp_server.ps5_list_processes()),
        ("ps5_list_memory_maps", lambda: ps5_mcp_server.ps5_list_memory_maps()),
        ("ps5_read_memory_hex", lambda: ps5_mcp_server.ps5_read_memory_hex("0x400000", 16)),
        ("ps5_get_disassembly", lambda: ps5_mcp_server.ps5_get_disassembly("0x400000", 32)),
        ("ps5_list_functions", lambda: ps5_mcp_server.ps5_list_functions()),
        ("ps5_list_symbols", lambda: ps5_mcp_server.ps5_list_symbols()),
        ("ps5_navigate_ui", lambda: ps5_mcp_server.ps5_navigate_ui("0x400000", "disasm")),
    ]

    for name, func in tools_to_test:
        raw = func()
        try:
            parsed = json.loads(raw)
            # When desktop app is offline, expect graceful error handling
            assert "error" in parsed or "status" in parsed, f"Unexpected response format: {parsed}"
            print(f"  [PASS] {name}: returns valid JSON (graceful handling)")
        except Exception as e:
            print(f"  [FAIL] {name}: {e}")
            return False

    return True

if __name__ == "__main__":
    success = test_offline_responses()
    if success:
        print("\nAll offline MCP tool tests PASSED!")
    else:
        sys.exit(1)

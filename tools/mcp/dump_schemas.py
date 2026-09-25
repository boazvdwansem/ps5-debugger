import asyncio
import json
import os
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).parent))
from ps5_mcp_server import mcp

async def main():
    target_dir = Path(r"C:\Users\boazv\.gemini\antigravity\mcp\ps5_debugger")
    target_dir.mkdir(parents=True, exist_ok=True)

    tools = await mcp.list_tools()
    print(f"Found {len(tools)} tools in ps5-debugger MCP server:")
    for tool in tools:
        schema = {
            "name": tool.name,
            "description": tool.description,
            "parameters": tool.inputSchema if hasattr(tool, "inputSchema") else getattr(tool, "parameters", {})
        }
        file_path = target_dir / f"{tool.name}.json"
        with open(file_path, "w", encoding="utf-8") as f:
            json.dump(schema, f, indent=2)
        print(f"  - Written {file_path.name}")

if __name__ == "__main__":
    asyncio.run(main())

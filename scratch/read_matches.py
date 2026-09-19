import json

path = r"C:\Users\boazv\.gemini\antigravity-cli\brain\c3ab47e1-9b3c-46f0-b952-51004b1f94da\.system_generated\logs\transcript_full.jsonl"

with open(path, "r", encoding="utf-8") as f:
    for idx, line in enumerate(f):
        if "mnemonic" in line.lower() and "hexviewer" in line.lower():
            try:
                data = json.loads(line)
                content = str(data.get("content", ""))[:400]
                print(f"Line {idx} | Type: {data.get('type')} | Content: {content}\n")
            except Exception:
                print(f"Line {idx}: {line[:400]}\n")

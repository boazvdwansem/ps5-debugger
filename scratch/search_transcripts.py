import os
import json

brain_dir = r"C:\Users\boazv\.gemini\antigravity-cli\brain"
matches = {}

for root, dirs, files in os.walk(brain_dir):
    for f in files:
        if f == "transcript.jsonl":
            path = os.path.join(root, f)
            # Skip current conversation
            if "c9898fec-6d7f-49c7-8ebd-9fe43b055edb" in path:
                continue
            with open(path, "r", encoding="utf-8") as file:
                for idx, line in enumerate(file):
                    if "zydis" in line.lower():
                        if path not in matches:
                            matches[path] = []
                        matches[path].append(idx)

for path, lines in matches.items():
    print(f"File: {path} | Matches count: {len(lines)}")

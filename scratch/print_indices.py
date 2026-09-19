import json

names = [
    "ADD", "SUB", "MOV", "LEA", "PUSH", "POP", "RET", "JMP", "JZ", "JNZ",
    "CALL", "TEST", "XOR", "AND", "CMP", "MOVUPS", "MOVAPS", "DEC", "INC", "NOP"
]

h_path = r"C:\Users\boazv\Documents\Personal\ps5-debugger\debugger\third_party\zydis\Zydis.h"
mnemonics = []
started = False

with open(h_path, "r", encoding="utf-8") as f:
    for line in f:
        line = line.strip()
        if "typedef enum ZydisMnemonic_" in line:
            started = True
            continue
        if started:
            if "ZYDIS_MNEMONIC_MAX_VALUE" in line or "} ZydisMnemonic;" in line:
                break
            import re
            m = re.match(r"ZYDIS_MNEMONIC_([A-Z0-9_]+)", line)
            if m:
                mnemonics.append(m.group(1))

for name in names:
    try:
        idx = mnemonics.index(name)
        print(f"{name}: Index {idx}, LowByte {idx % 256}")
    except ValueError:
        print(f"{name} not found")

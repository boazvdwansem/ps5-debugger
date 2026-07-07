import subprocess

c_code = """
#include <stdio.h>
#include "Zydis.h"

int main() {
    printf("INVALID: %d\\n", ZYDIS_MNEMONIC_INVALID);
    printf("CALL: %d\\n", ZYDIS_MNEMONIC_CALL);
    printf("MOV: %d\\n", ZYDIS_MNEMONIC_MOV);
    return 0;
}
"""

with open("test_zydis.c", "w") as f:
    f.write(c_code)

# Compile using gcc with inclusion of zydis headers
cmd = "wsl gcc -Idebugger/third_party/zydis test_zydis.c -o test_zydis"
subprocess.run(cmd, shell=True)

# Run
res = subprocess.run("wsl ./test_zydis", shell=True, capture_output=True, text=True)
print(res.stdout)

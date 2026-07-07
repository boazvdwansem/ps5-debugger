
#include <stdio.h>
#include "Zydis.h"

int main() {
    printf("INVALID: %d\n", ZYDIS_MNEMONIC_INVALID);
    printf("CALL: %d\n", ZYDIS_MNEMONIC_CALL);
    printf("MOV: %d\n", ZYDIS_MNEMONIC_MOV);
    return 0;
}

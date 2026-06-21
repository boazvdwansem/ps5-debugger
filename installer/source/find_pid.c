// SPDX-License-Identifier: GPL-3.0-only

#include <stdint.h>
#include <stddef.h>
#include <string.h>
#include "sdk_shim.h"

int find_proc_pid_by_name(const char *name)
{
    if (!name) return -1;

    for (int pid = 1; pid < 0x2710; pid++) {
        int     mib[4];
        uint8_t kinfo[0x448];
        size_t  outlen = 0x448;

        mib[0] = 1;
        mib[1] = 0xE;
        mib[2] = 1;
        mib[3] = pid;

        if (ps5debug_syscall(202 /*sysctl*/, (long)(intptr_t)mib, 4L /*namelen*/,
                             (long)(intptr_t)kinfo,
                             (long)(intptr_t)&outlen, 0L, 0L) != 0)
            continue;

        char comm[0x14];
        memcpy(comm, &kinfo[0x1BF], 0x13);
        comm[0x13] = 0;

        if (strcmp(comm, name) == 0)
            return pid;
    }
    return -1;
}

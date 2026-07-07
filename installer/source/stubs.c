// SPDX-License-Identifier: GPL-3.0-only

#include <stdint.h>
#include <stddef.h>
#include "sdk_shim.h"

void *g_proc_rw_mutex = NULL;
static int g_proc_rw_mutex_inited = 0;

void port_outer_init_mutexes(void)
{
    if (!g_proc_rw_mutex_inited) {
        scePthreadMutexInit(&g_proc_rw_mutex, NULL, "outer_proc_rw");
        g_proc_rw_mutex_inited = 1;
    }
}

uint32_t g_stopgo_last_signal = 0;
uint32_t g_stopgo_target_pid  = 0;
void    *curdbgctx            = NULL;

long kern_proc_protect_inline_fast(unsigned long pid,
                                    unsigned long address,
                                    unsigned long prot)
{
    (void)pid; (void)address; (void)prot;
    return -1;
}

// SPDX-License-Identifier: GPL-3.0-only

#include <stdint.h>
#include <stddef.h>
#include "sdk_shim.h"
#include "proc.h"
#include "kern_rw_fast.h"

extern void    *g_proc_rw_mutex;

static int priv_syscall_573(uint64_t *cmd, uint64_t *args, uint64_t *out)
{
    int self_pid = getpid();

    intptr_t ucred = kernel_get_proc_ucred_fast((pid_t)self_pid);
    if (ucred == 0) return -1;

    unsigned long saved_authid = 0;
    if (kernel_copyout_fast(ucred + 0x58, &saved_authid, sizeof(saved_authid)) != 0) return -1;
    if (saved_authid == 0) return -1;

    uint64_t priv_authid = 0x4800000000000006ULL;
    if (kernel_copyin_fast(&priv_authid, ucred + 0x58, sizeof(priv_authid)) != 0) return -1;

    unsigned int syscall_rc = (unsigned int)__crt_syscall(573, cmd, args, out);

    int restore_rc = kernel_copyin_fast(&saved_authid, ucred + 0x58, sizeof(saved_authid));

    return (restore_rc != 0 ? -1 : 0) | (int)syscall_rc;
}

static int sys_proc_rw_inner(uint32_t pid, uint64_t address, void *data,
                              uint64_t length, uint64_t *arg5_out, int write)
{
    uint64_t cmd_struct[2]  = { 1, write ? 0x13ull : 0x12ull };
    uint64_t args_struct[4] = { (uint64_t)(int64_t)(int32_t)pid,
                                address, (uint64_t)data, length };
    uint64_t out_struct[2]  = { 0, 0 };

    if (length == 0) {
        if (arg5_out) *arg5_out = 0;
        return 0;
    }

    priv_syscall_573(cmd_struct, args_struct, out_struct);

    if (out_struct[1] != length) return 1;

    if (arg5_out) *arg5_out = length;
    return 0;
}

static long sys_proc_rw(uint64_t pid, uint64_t address, uint64_t length,
                         void *data, uint64_t arg5, int write)
{
    scePthreadMutexLock(&g_proc_rw_mutex);
    sys_proc_rw_inner((uint32_t)pid, address, data, length, (uint64_t *)arg5, write);
    scePthreadMutexUnlock(&g_proc_rw_mutex);
    return 0;
}

long sys_proc_rw_w0(uint64_t pid, uint64_t address, uint64_t length,
                    void *data, uint64_t arg5)
{
    return sys_proc_rw(pid, address, length, data, arg5, 0);
}

long sys_proc_rw_w1(uint64_t pid, uint64_t address, uint64_t length,
                    void *data, uint64_t arg5)
{
    return sys_proc_rw(pid, address, length, data, arg5, 1);
}

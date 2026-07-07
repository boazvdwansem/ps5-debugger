// SPDX-License-Identifier: GPL-3.0-only

#pragma once
#include <stdint.h>
#include <stddef.h>
#include <sys/types.h>

int32_t  kernel_copyin_fast (const void *udaddr, intptr_t kaddr, size_t len);
int32_t  kernel_copyout_fast(intptr_t kaddr, void *udaddr, size_t len);

intptr_t kernel_get_proc_fast        (pid_t pid);
intptr_t kernel_get_proc_ucred_fast  (pid_t pid);
void     kern_proc_cache_flush       (void);
void     kern_proc_cache_invalidate  (int pid);
uint64_t kernel_get_ucred_authid_fast(pid_t pid);
int32_t  kernel_set_ucred_authid_fast(pid_t pid, uint64_t authid);
int32_t  kernel_get_ucred_caps_fast  (pid_t pid, uint8_t caps[16]);
int32_t  kernel_set_ucred_caps_fast  (pid_t pid, const uint8_t caps[16]);

void    *kernel_get_proc_struct_fast (uint32_t pid);

int      kern_setup_sprx_dispatch_fast(int pid, int sprx_handle,
                                        void *buf1, void *buf2);
uint64_t kernel_dynlib_resolve_fast   (int pid, int module_sel, const char *nid);
uint64_t kernel_dynlib_mapbase_addr_by_proc_fast(void *proc, unsigned int sel);

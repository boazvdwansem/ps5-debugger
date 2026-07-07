// SPDX-License-Identifier: GPL-3.0-only

#pragma once
#include <stdint.h>

struct proc_vm_map_entry;

#define SYS_PROC_ALLOC         1
#define SYS_PROC_FREE          2
#define SYS_PROC_PROTECT       3
#define SYS_PROC_VM_MAP        4
#define SYS_PROC_INSTALL       5
#define SYS_PROC_CALL          6
#define SYS_PROC_ELF           7
#define SYS_PROC_INFO          8
#define SYS_PROC_THRINFO       9
#define SYS_PROC_ALLOC_HINTED 10
#define SYS_PROC_ELF_RPC      11

struct sys_proc_alloc_args {
    uint64_t address;
    uint64_t length;
} __attribute__((packed));

struct sys_proc_free_args {
    uint64_t address;
    uint64_t length;
} __attribute__((packed));

struct sys_proc_protect_args {
    uint64_t address;
    uint64_t length;
    uint64_t prot;
} __attribute__((packed));

struct sys_proc_vm_map_args {
    struct proc_vm_map_entry *maps;
    uint64_t num;
} __attribute__((packed));

struct sys_proc_elf_args {
    void *elf;

    uint64_t length;
} __attribute__((packed));

struct sys_proc_elf_rpc_args {
    void    *elf;
    uint64_t length;
    uint64_t entry;
} __attribute__((packed));

int sys_proc_cmd(uint64_t pid, uint64_t cmd, void *data);

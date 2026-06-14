// SPDX-License-Identifier: GPL-3.0-only

#ifndef _PS5DEBUG_PROTOCOL_H
#define _PS5DEBUG_PROTOCOL_H

#include <stdint.h>
#include <stddef.h>

#define CMD_SUCCESS         0x40000000u
#define CMD_DATA_NULL       0xF0000003u
#define CMD_ERROR           0xF0000002u
#define CMD_ALREADY_DEBUG   0xF0000008u

#define CMD_VERSION         0xBD000001u
#define CMD_FW_VERSION      0xBD000500u
#define CMD_BRANDING        0xBD000501u
#define CMD_PLATFORM_ID     0xBD000502u
#define CMD_PROC_NOP        0xBDAACC06u

#define VALID_CMD(cmd)          (((cmd) >> 24) == 0xBDu)
#define VALID_PROC_CMD(cmd)     ((((cmd) >> 16) & 0xFFu) == 0xAAu)
#define VALID_DEBUG_CMD(cmd)    ((((cmd) >> 16) & 0xFFu) == 0xBBu)
#define VALID_KERN_CMD(cmd)     ((((cmd) >> 16) & 0xFFu) == 0xCCu)
#define VALID_CONSOLE_CMD(cmd)  ((((cmd) >> 16) & 0xFFu) == 0xDDu)

struct cmd_packet {
    uint32_t magic;
    uint32_t cmd;
    uint32_t datalen;
    void    *data;
} __attribute__((packed));

struct cmd_kern_read_packet {
    uint64_t address;
    uint32_t length;
} __attribute__((packed));

struct cmd_kern_write_packet {
    uint64_t address;
    uint32_t length;
} __attribute__((packed));

struct proc_list_entry {
    char     name[32];
    int32_t  pid;
} __attribute__((packed));

struct cmd_proc_alloc_packet {
    uint32_t pid;
    uint32_t length;
} __attribute__((packed));
struct cmd_proc_alloc_response {
    uint64_t address;
} __attribute__((packed));
#define CMD_PROC_ALLOC_PACKET_SIZE 8

struct cmd_proc_alloc_hinted_packet {
    uint32_t pid;
    uint64_t hint;
    uint32_t length;
} __attribute__((packed));

struct cmd_proc_free_packet {
    uint32_t pid;
    uint64_t address;
    uint32_t length;
} __attribute__((packed));

struct cmd_proc_install_packet {
    uint32_t pid;
} __attribute__((packed));

struct cmd_proc_unknown_d_packet {
    uint32_t pid;
} __attribute__((packed));

struct cmd_proc_read_packet {
    uint32_t pid;
    uint64_t address;
    uint32_t length;
} __attribute__((packed));

struct cmd_proc_read_stack_packet {
    uint32_t pid;
    uint64_t rbp;
    uint64_t rsp;
    uint32_t depth;
} __attribute__((packed));
#define CMD_PROC_READ_STACK_PACKET_SIZE 24
#define CMD_PROC_READ_STACK_CODE_OFF   10u
#define CMD_PROC_READ_STACK_CODE_LEN   200u
#define CMD_PROC_READ_STACK_LOCALS_CAP 0x1000u
#define CMD_PROC_READ_STACK_MAX_DEPTH  64u

#define CMD_PROC_ASSEMBLE_HDR_SIZE     12u
struct cmd_proc_assemble_packet {
    uint64_t base_addr;
    uint32_t ks_opt_syntax;
} __attribute__((packed));
struct cmd_proc_assemble_ok {
    uint32_t byte_len;
    uint32_t insn_count;
} __attribute__((packed));
struct cmd_proc_assemble_err {
    uint32_t ks_errno;
    uint32_t msg_len;
} __attribute__((packed));

struct cmd_proc_write_packet {
    uint32_t pid;
    uint64_t address;
    uint32_t length;
} __attribute__((packed));

/* Bulk write / freeze batching (opcode 0xBDAACC04). Header body below is
   followed by `count` streamed entries, each { uint64 address; uint32 length;
   <length> bytes }. flags bit 0 asks the server for a per-entry status byte
   array (0 = ok) sent just before the trailing CMD_SUCCESS. */
struct cmd_proc_write_multi_packet {
    uint32_t pid;
    uint32_t count;
    uint32_t flags;
} __attribute__((packed));
#define PROC_WRITE_MULTI_F_STATUS   0x1u
#define PROC_WRITE_MULTI_MAX_COUNT  0xFFFFu
#define PROC_WRITE_MULTI_MAX_ENTRY  0x100000u

struct cmd_proc_maps_packet {
    uint32_t pid;
} __attribute__((packed));

struct cmd_proc_call_packet {
    uint32_t pid;
    uint64_t rpcstub;
    uint64_t rpc_rip;
    uint64_t rpc_rdi;
    uint64_t rpc_rsi;
    uint64_t rpc_rdx;
    uint64_t rpc_rcx;
    uint64_t rpc_r8;
    uint64_t rpc_r9;
} __attribute__((packed));

struct sys_proc_call_args {
    uint32_t pid;
    uint64_t rpcstub;
    uint64_t rax;
    uint64_t rip;
    uint64_t rdi;
    uint64_t rsi;
    uint64_t rdx;
    uint64_t rcx;
    uint64_t r8;
    uint64_t r9;
} __attribute__((packed));

struct cmd_proc_call_response {
    uint32_t pid;
    uint64_t rpc_rax;
} __attribute__((packed));

struct cmd_proc_elf_packet {
    uint32_t pid;
    uint32_t length;
} __attribute__((packed));

struct proc_vm_map_entry {
    char     name[32];
    uint64_t start;
    uint64_t end;
    uint64_t offset;
    uint16_t prot;
} __attribute__((packed));

struct cmd_proc_scan_start_packet {
    uint32_t pid;
    uint64_t address;
    uint32_t length;
    uint8_t  valueType;
    uint8_t  compareType;
    uint8_t  alignment;
    uint32_t lenData;
} __attribute__((packed));

struct cmd_proc_scan_count_packet {
    uint32_t pid;
    uint64_t base_address;
    uint8_t  valueType;
    uint8_t  compareType;
    uint32_t lenData;
} __attribute__((packed));

struct cmd_proc_scan_get_packet {
    uint32_t pid;
    uint32_t count;
} __attribute__((packed));

struct cmd_proc_scan_aob_packet {
    uint32_t pid;
    uint64_t address;
    uint32_t length;
    uint8_t  max_matches;
    uint8_t  stop_flag;
    uint32_t pattern_length;
} __attribute__((packed));

struct cmd_proc_scan_aob_multi_packet {
    uint32_t pid;
    uint64_t address;
    uint32_t length;
    uint8_t  stop_flag;
    uint32_t patterns_length;
} __attribute__((packed));

struct cmd_proc_scan_packet {
    uint32_t pid;
    uint8_t  valueType;
    uint8_t  compareType;
    uint32_t lenData;
} __attribute__((packed));

struct cmd_proc_elf_rpc_packet {
    uint32_t pid;
    uint32_t length;
} __attribute__((packed));
#define CMD_PROC_ELF_RPC_PACKET_SIZE 8
struct cmd_proc_elf_rpc_response {
    uint64_t entry;
} __attribute__((packed));
#define CMD_PROC_ELF_RPC_RESPONSE_SIZE 8

struct cmd_proc_disasm_packet {
    uint32_t pid;
    uint64_t address;
    uint32_t length;
    uint32_t max_entries;
} __attribute__((packed));
#define CMD_PROC_DISASM_PACKET_SIZE 20

struct cmd_proc_xrefs_to_packet {
    uint32_t pid;
    uint64_t scan_address;
    uint32_t scan_length;
    uint64_t target_address;
} __attribute__((packed));
#define CMD_PROC_XREFS_TO_PACKET_SIZE 24

struct disasm_instr_entry {
    uint64_t addr;
    uint64_t rip_rel_target;
    int64_t  mem_disp;
    uint8_t  length;
    uint8_t  kind;
    uint8_t  mem_base_reg;
    uint8_t  mem_index_reg;
    uint8_t  mem_scale;
    uint8_t  mnemonic_lo;
    uint16_t pad;
} __attribute__((packed));
#define DISASM_INSTR_ENTRY_SIZE 32

#define CMD_PROC_AUTH_MAGIC        0xBB40E64Du
#define CMD_PROC_AUTH_MAGIC_BSWAP  0x7780D98Eu
struct cmd_proc_auth_packet {
    uint32_t magic;
    uint32_t flags;
} __attribute__((packed));

struct cmd_proc_info_packet {
    uint32_t pid;
} __attribute__((packed));
struct cmd_proc_info_response {
    uint32_t pid;
    char     name[40];
    char     path[64];
    char     titleid[16];
    char     contentid[64];
} __attribute__((packed));

#define CMD_DEBUG_ATTACH        0xBDBB0001u
#define CMD_DEBUG_SET_BREAKPOINT 0xBDBB0003u
#define CMD_DEBUG_SET_WATCHPOINT 0xBDBB0004u

#define CMD_INVALID_INDEX   0xF000000Au

#define MAX_BREAKPOINTS     30
#define MAX_WATCHPOINTS     4

struct cmd_debug_breakpt_packet {
    uint32_t index;
    uint32_t enabled;
    uint64_t address;
} __attribute__((packed));
#define CMD_DEBUG_BREAKPT_PACKET_SIZE 16

struct cmd_debug_watchpt_packet {
    uint32_t index;
    uint32_t enabled;
    uint32_t length;
    uint32_t breaktype;
    uint64_t address;
} __attribute__((packed));
#define CMD_DEBUG_WATCHPT_PACKET_SIZE 24

struct cmd_debug_attach_packet {
    uint32_t pid;
} __attribute__((packed));
#define CMD_DEBUG_ATTACH_PACKET_SIZE 4

#endif

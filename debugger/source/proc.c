// SPDX-License-Identifier: GPL-3.0-only

#include "protocol.h"
#include "sdk_shim.h"
#include "net.h"
#include "proc.h"
#include "kdbg.h"
#include "kern_rw_fast.h"
#include "proc_field_offsets.h"
#include "Zydis.h"

#define PROC_NEXT_OFFSET           0x00
#define PROC_VMSPACE_OFFSET        0x200
#define PROC_SELFINFO_NAME_SIZE    32

struct sce_app_info {
    uint32_t app_id;
    uint64_t unknown1;
    uint32_t app_type;
    char     title_id[10];
    char     unknown2[0x3c];
};
extern int sceKernelGetAppInfo(pid_t pid, struct sce_app_info *info);

static void copy_cstr_from_buf(const uint8_t *src, char *out, size_t max) {
    if (max == 0) return;
    size_t i;
    for (i = 0; i < max - 1; i++) {
        out[i] = (char)src[i];
        if (src[i] == 0) {
            memset(out + i, 0, max - i);
            return;
        }
    }
    out[max - 1] = 0;
}

int find_titleid(const uint8_t *buf, size_t len, char *out) {
    if (len < 9) return 1;
    for (size_t i = 0; i + 9 <= len; i++) {
        int ok = 1;
        for (int j = 0; j < 4; j++)
            if (buf[i + j] < 'A' || buf[i + j] > 'Z') { ok = 0; break; }
        if (!ok) continue;
        for (int j = 4; j < 9; j++)
            if (buf[i + j] < '0' || buf[i + j] > '9') { ok = 0; break; }
        if (!ok) continue;
        memcpy(out, buf + i, 9);
        out[9] = '\0';
        return 0;
    }
    return 1;
}

int find_contentid(const uint8_t *buf, size_t len, char *out, size_t out_size) {
    if (out_size == 0) return 1;
    if (len < 17) return 1;
    for (size_t i = 0; i + 17 <= len; i++) {
        const uint8_t *s = buf + i;
        int ok = 1;
        for (int j = 0; j < 2; j++)
            if (s[j] < 'A' || s[j] > 'Z') { ok = 0; break; }
        if (!ok) continue;
        for (int j = 2; j < 6; j++)
            if (s[j] < '0' || s[j] > '9') { ok = 0; break; }
        if (!ok) continue;
        if (s[6] != '-') continue;
        for (int j = 7; j < 11; j++)
            if (s[j] < 'A' || s[j] > 'Z') { ok = 0; break; }
        if (!ok) continue;
        for (int j = 11; j < 16; j++)
            if (s[j] < '0' || s[j] > '9') { ok = 0; break; }
        if (!ok) continue;
        if (s[16] != '_') continue;
        size_t k = 0;
        while (i + k < len && s[k] != 0 && k < out_size - 1) {
            out[k] = (char)s[k];
            k++;
        }
        out[k] = '\0';
        return 0;
    }
    return 1;
}

#define DISASM_READ_CHUNK 0x10000

static void disasm_fill_entry(struct disasm_instr_entry *out,
                              uint64_t addr,
                              const ZydisDecodedInstruction *insn,
                              const ZydisDecodedOperand *operands) {
    memset(out, 0, sizeof(*out));
    out->addr = addr;
    out->length = insn->length;
    out->mnemonic = (uint16_t)insn->mnemonic;
    out->reserved = 0;

    switch (insn->meta.category) {
        case ZYDIS_CATEGORY_CALL:      out->kind |= 0x01; break;
        case ZYDIS_CATEGORY_RET:       out->kind |= 0x02; break;
        case ZYDIS_CATEGORY_UNCOND_BR: out->kind |= 0x04; break;
        case ZYDIS_CATEGORY_COND_BR:   out->kind |= 0x08; break;
        default: break;
    }

    for (ZyanU8 i = 0; i < insn->operand_count_visible; i++) {
        const ZydisDecodedOperand *op = &operands[i];
        if (op->type != ZYDIS_OPERAND_TYPE_MEMORY) continue;

        out->kind |= 0x10;
        if (op->actions & ZYDIS_OPERAND_ACTION_MASK_READ)  out->kind |= 0x40;
        if (op->actions & ZYDIS_OPERAND_ACTION_MASK_WRITE) out->kind |= 0x80;

        out->mem_base_reg  = (uint8_t)(op->mem.base  & 0xFF);
        out->mem_index_reg = (uint8_t)(op->mem.index & 0xFF);
        out->mem_scale     = op->mem.scale;
        out->mem_disp      = op->mem.disp.has_displacement ? op->mem.disp.value : 0;

        if (op->mem.base == ZYDIS_REGISTER_RIP) {
            out->kind |= 0x20;
            out->rip_rel_target = addr + insn->length + (uint64_t)op->mem.disp.value;
        }
        break;
    }
}

typedef void (*disasm_emit_fn)(uint64_t addr,
                               const ZydisDecodedInstruction *insn,
                               const ZydisDecodedOperand *operands,
                               void *ctx);

static uint64_t disasm_iterate(uint32_t pid, uint64_t start, uint64_t length,
                               uint64_t max_instrs, disasm_emit_fn emit, void *ctx) {
    void *chunk = net_alloc_buffer(DISASM_READ_CHUNK);
    if (!chunk) return 0;

    ZydisDecoder decoder;
    ZydisDecoderInit(&decoder, ZYDIS_MACHINE_MODE_LONG_64, ZYDIS_STACK_WIDTH_64);

    ZydisDecodedInstruction insn;
    ZydisDecodedOperand     operands[ZYDIS_MAX_OPERAND_COUNT];

    uint64_t emitted = 0;
    uint64_t off     = 0;

    while (off < length && emitted < max_instrs) {
        uint64_t remaining = length - off;
        uint32_t read_len  = remaining > DISASM_READ_CHUNK ? DISASM_READ_CHUNK : (uint32_t)remaining;

        (void)sys_proc_rw_w0((uint64_t)pid, start + off, (uint64_t)read_len, chunk, 0);

        uint32_t pos = 0;
        while (pos < read_len && emitted < max_instrs) {
            uint32_t avail = read_len - pos;
            ZyanStatus r = ZydisDecoderDecodeFull(&decoder,
                                                  (uint8_t *)chunk + pos, avail,
                                                  &insn, operands);
            if (!ZYAN_SUCCESS(r)) {
                pos++;
                continue;
            }
            emit(start + off + pos, &insn, operands, ctx);
            emitted++;
            pos += insn.length;

            if (pos + ZYDIS_MAX_INSTRUCTION_LENGTH > read_len && remaining > read_len) {
                break;
            }
        }
        off += (pos > 0 ? pos : 1);
    }

    free(chunk);
    return emitted;
}

struct disasm_ctx {
    int       fd;
    uint8_t  *out_buf;
    uint32_t  out_cap;
    uint32_t  out_used;
};

static void disasm_emit_record(uint64_t addr,
                               const ZydisDecodedInstruction *insn,
                               const ZydisDecodedOperand *operands,
                               void *ctx_) {
    struct disasm_ctx *ctx = (struct disasm_ctx *)ctx_;
    if (ctx->out_used + DISASM_INSTR_ENTRY_SIZE > ctx->out_cap) {
        net_send_all(ctx->fd, ctx->out_buf, (int)ctx->out_used);
        ctx->out_used = 0;
    }
    struct disasm_instr_entry *e = (struct disasm_instr_entry *)(ctx->out_buf + ctx->out_used);
    disasm_fill_entry(e, addr, insn, operands);
    ctx->out_used += DISASM_INSTR_ENTRY_SIZE;
}

int proc_disasm_region_handle(int fd, struct cmd_packet *packet) {
    struct cmd_proc_disasm_packet *dp = (struct cmd_proc_disasm_packet *)packet->data;
    if (!dp) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }
    if (dp->max_entries == 0 || dp->max_entries > 1000000) {
        net_send_int32(fd, CMD_ERROR);
        return 1;
    }

    struct disasm_ctx ctx;
    ctx.fd       = fd;
    ctx.out_cap  = 0x10000;
    ctx.out_used = 0;
    ctx.out_buf  = (uint8_t *)net_alloc_buffer(ctx.out_cap);
    if (!ctx.out_buf) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    net_send_int32(fd, CMD_SUCCESS);

    (void)disasm_iterate(dp->pid, dp->address, dp->length,
                         dp->max_entries, disasm_emit_record, &ctx);

    if (ctx.out_used > 0) {
        net_send_all(fd, ctx.out_buf, (int)ctx.out_used);
    }

    uint8_t sentinel[DISASM_INSTR_ENTRY_SIZE];
    memset(sentinel, 0xFF, sizeof(sentinel));
    net_send_all(fd, sentinel, sizeof(sentinel));

    free(ctx.out_buf);
    return 0;
}

struct xrefs_ctx {
    int       fd;
    uint64_t *buf;
    uint32_t  cap;
    uint32_t  used;
};

static void xrefs_emit(uint64_t addr,
                       const ZydisDecodedInstruction *insn,
                       const ZydisDecodedOperand *operands,
                       void *ctx_) {
    struct xrefs_ctx *ctx = (struct xrefs_ctx *)ctx_;
    for (ZyanU8 i = 0; i < insn->operand_count_visible; i++) {
        const ZydisDecodedOperand *op = &operands[i];
        if (op->type != ZYDIS_OPERAND_TYPE_MEMORY) continue;
        if (op->mem.base != ZYDIS_REGISTER_RIP) break;

        uint64_t target = addr + insn->length + (uint64_t)op->mem.disp.value;
        if (ctx->used + 1 > ctx->cap) {
            net_send_all(ctx->fd, ctx->buf, (int)(ctx->used * sizeof(uint64_t)));
            ctx->used = 0;
        }
        ctx->buf[ctx->used++] = target;
        break;
    }
}

int proc_extract_code_xrefs_handle(int fd, struct cmd_packet *packet) {
    struct cmd_proc_disasm_packet *dp = (struct cmd_proc_disasm_packet *)packet->data;
    if (!dp) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    struct xrefs_ctx ctx;
    ctx.fd   = fd;
    ctx.cap  = 0x2000;
    ctx.used = 0;
    ctx.buf  = (uint64_t *)net_alloc_buffer(ctx.cap * sizeof(uint64_t));
    if (!ctx.buf) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    net_send_int32(fd, CMD_SUCCESS);

    uint64_t max_instrs = dp->max_entries ? dp->max_entries : 10000000;
    (void)disasm_iterate(dp->pid, dp->address, dp->length,
                         max_instrs, xrefs_emit, &ctx);

    if (ctx.used > 0) {
        net_send_all(fd, ctx.buf, (int)(ctx.used * sizeof(uint64_t)));
    }

    uint64_t sentinel = 0xFFFFFFFFFFFFFFFFULL;
    net_send_all(fd, &sentinel, sizeof(uint64_t));

    free(ctx.buf);
    return 0;
}

struct xrefs_to_ctx {
    int       fd;
    uint64_t  target;
    uint64_t *buf;
    uint32_t  cap;
    uint32_t  used;
};

static void xrefs_to_emit(uint64_t addr,
                          const ZydisDecodedInstruction *insn,
                          const ZydisDecodedOperand *operands,
                          void *ctx_) {
    struct xrefs_to_ctx *ctx = (struct xrefs_to_ctx *)ctx_;
    for (ZyanU8 i = 0; i < insn->operand_count_visible; i++) {
        const ZydisDecodedOperand *op = &operands[i];
        if (op->type != ZYDIS_OPERAND_TYPE_MEMORY) continue;
        if (op->mem.base != ZYDIS_REGISTER_RIP) break;
        uint64_t resolved = addr + insn->length + (uint64_t)op->mem.disp.value;
        if (resolved == ctx->target) {
            if (ctx->used + 1 > ctx->cap) {
                net_send_all(ctx->fd, ctx->buf, (int)(ctx->used * sizeof(uint64_t)));
                ctx->used = 0;
            }
            ctx->buf[ctx->used++] = addr;
        }
        break;
    }
}

int proc_find_xrefs_to_handle(int fd, struct cmd_packet *packet) {
    struct cmd_proc_xrefs_to_packet *xp = (struct cmd_proc_xrefs_to_packet *)packet->data;
    if (!xp) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    struct xrefs_to_ctx ctx;
    ctx.fd     = fd;
    ctx.target = xp->target_address;
    ctx.cap    = 0x2000;
    ctx.used   = 0;
    ctx.buf    = (uint64_t *)net_alloc_buffer(ctx.cap * sizeof(uint64_t));
    if (!ctx.buf) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    net_send_int32(fd, CMD_SUCCESS);

    (void)disasm_iterate(xp->pid, xp->scan_address, xp->scan_length,
                         10000000, xrefs_to_emit, &ctx);

    if (ctx.used > 0) {
        net_send_all(fd, ctx.buf, (int)(ctx.used * sizeof(uint64_t)));
    }

    uint64_t sentinel = 0xFFFFFFFFFFFFFFFFULL;
    net_send_all(fd, &sentinel, sizeof(uint64_t));

    free(ctx.buf);
    return 0;
}

struct proc_list_wire_entry {
    char    name[32];
    int32_t pid;
} __attribute__((packed));

int proc_list_handle(int fd, struct cmd_packet *packet) {
    (void)packet;

    intptr_t allproc_head = (intptr_t)KERNEL_ADDRESS_ALLPROC;
    intptr_t kproc = 0;
    if (kernel_copyout_fast(allproc_head, &kproc, sizeof(kproc)) != 0) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    uint32_t count = 0;
    intptr_t cur = kproc;
    while (cur != 0 && count < 0x1000) {
        count++;
        intptr_t next = 0;
        if (kernel_copyout_fast(cur + PROC_NEXT_OFFSET, &next, sizeof(next)) != 0) break;
        cur = next;
    }
    if (count == 0) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    uint32_t length = count * (uint32_t)sizeof(struct proc_list_wire_entry);
    struct proc_list_wire_entry *entries = (struct proc_list_wire_entry *)net_alloc_buffer(length);
    if (!entries) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }
    memset(entries, 0, length);

    struct proc_field_offsets off;
    proc_get_field_offsets(&off);

    cur = kproc;
    for (uint32_t i = 0; i < count && cur != 0; i++) {
        if (off.known)
            kernel_copyout_fast(cur + off.name, entries[i].name, PROC_SELFINFO_NAME_SIZE);
        size_t nlen = 0;
        while (nlen < PROC_SELFINFO_NAME_SIZE && entries[i].name[nlen] != 0) nlen++;
        if (nlen < PROC_SELFINFO_NAME_SIZE) {
            memset(entries[i].name + nlen, 0, PROC_SELFINFO_NAME_SIZE - nlen);
        }
        kernel_copyout_fast(cur + KERNEL_OFFSET_PROC_P_PID,  &entries[i].pid, sizeof(entries[i].pid));
        intptr_t next = 0;
        kernel_copyout_fast(cur + PROC_NEXT_OFFSET, &next, sizeof(next));
        cur = next;
    }

    net_send_int32(fd, CMD_SUCCESS);

    uint64_t count64 = count;
    net_send_all(fd, &count64, sizeof(uint32_t));
    net_send_all(fd, entries, (int)length);

    free(entries);
    return 0;
}

void proc_read_mem(uint32_t pid, uint64_t addr, uint64_t len, void *buf) {
    if (proc_aux_range_contains(pid, addr, len) &&
        proc_ptwalk_read(pid, addr, len, buf) == 0)
        return;
    sys_proc_rw_w0((uint64_t)pid, addr, len, buf, 0);
}

void proc_write_mem(uint32_t pid, uint64_t addr, uint64_t len, const void *buf) {

    static int s_fw_needs_dmap = -1;
    if (s_fw_needs_dmap < 0)
        s_fw_needs_dmap = ((kernel_get_fw_version() & 0xffff0000u) >= 0x08400000u) ? 1 : 0;

    if (s_fw_needs_dmap && proc_ptwalk_write(pid, addr, len, buf) == 0)
        return;
    sys_proc_rw_w1((uint64_t)pid, addr, len, (void *)buf, 0);
}

int proc_read_handle(int fd, struct cmd_packet *packet) {
    struct cmd_proc_read_packet *rp = (struct cmd_proc_read_packet *)packet->data;
    if (!rp) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    void *data = net_alloc_buffer(0x10000);
    if (!data) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    net_send_int32(fd, CMD_SUCCESS);

    uint64_t length  = rp->length;
    uint64_t address = rp->address;

    while (length > 0x10000) {
        memset(data, 0, 0x10000);
        proc_read_mem(rp->pid, address, 0x10000, data);
        net_send_all(fd, data, 0x10000);
        address += 0x10000;
        length  -= 0x10000;
    }
    if (length > 0) {
        memset(data, 0, (size_t)length);
        proc_read_mem(rp->pid, address, length, data);
        net_send_all(fd, data, (int)length);
    }

    free(data);
    return 0;
}

#define PROC_RS_USR_MIN  0x10000ULL
#define PROC_RS_USR_MAX  0x0000800000000000ULL
static inline int proc_rs_addr_ok(uint64_t a, uint64_t len) {
    if (len == 0) return 0;
    if (a < PROC_RS_USR_MIN || a >= PROC_RS_USR_MAX) return 0;
    if (a + len < a) return 0;
    if (a + len > PROC_RS_USR_MAX) return 0;
    return 1;
}

int proc_read_stack_handle(int fd, struct cmd_packet *packet) {
    struct cmd_proc_read_stack_packet *sp =
        (struct cmd_proc_read_stack_packet *)packet->data;
    if (!sp) { net_send_int32(fd, CMD_DATA_NULL); return 1; }

    uint32_t pid   = sp->pid;
    uint64_t rbp   = sp->rbp;
    uint64_t rsp   = sp->rsp;
    uint32_t depth = sp->depth;
    if (depth == 0) depth = 1;
    if (depth > CMD_PROC_READ_STACK_MAX_DEPTH) depth = CMD_PROC_READ_STACK_MAX_DEPTH;

    uint64_t cap = 4 + (uint64_t)depth *
                   (32 + 12 + CMD_PROC_READ_STACK_LOCALS_CAP + CMD_PROC_READ_STACK_CODE_LEN);
    uint8_t *buf = (uint8_t *)net_alloc_buffer(cap);
    if (!buf) { net_send_int32(fd, CMD_DATA_NULL); return 1; }

    uint8_t  code_scratch[CMD_PROC_READ_STACK_CODE_LEN];
    uint32_t off = 4;
    uint32_t n_frames = 0;

    uint64_t cur_rbp = rbp, cur_rsp = rsp;
    for (uint32_t i = 0; i < depth; i++) {
        if (!proc_rs_addr_ok(cur_rbp, 16)) break;

        uint64_t pair[2] = { 0, 0 };
        sys_proc_rw_w0((uint64_t)pid, cur_rbp, 16, pair, 0);
        uint64_t saved_rbp = pair[0];
        uint64_t ret_addr  = pair[1];

        int64_t  fs = (int64_t)cur_rbp - (int64_t)cur_rsp + 8;
        uint32_t flags = 0;
        uint32_t locals_len = 0;
        if (fs <= 0 || (uint64_t)fs > CMD_PROC_READ_STACK_LOCALS_CAP
            || !proc_rs_addr_ok(cur_rsp, (uint64_t)fs)) {
            flags |= 1u;
        } else {
            locals_len = (uint32_t)fs;
        }
        uint64_t code_addr = ret_addr - CMD_PROC_READ_STACK_CODE_OFF;
        uint32_t code_len  = proc_rs_addr_ok(code_addr, CMD_PROC_READ_STACK_CODE_LEN)
                             ? CMD_PROC_READ_STACK_CODE_LEN : 0u;

        if (off + 32 + 12 + locals_len + code_len > cap) break;

        memcpy(buf + off, &cur_rbp,   8); off += 8;
        memcpy(buf + off, &cur_rsp,   8); off += 8;
        memcpy(buf + off, &saved_rbp, 8); off += 8;
        memcpy(buf + off, &ret_addr,  8); off += 8;
        memcpy(buf + off, &flags,      4); off += 4;
        memcpy(buf + off, &locals_len, 4); off += 4;
        memcpy(buf + off, &code_len,   4); off += 4;
        if (locals_len) {
            memset(buf + off, 0, locals_len);
            sys_proc_rw_w0((uint64_t)pid, cur_rsp, locals_len, buf + off, 0);
            off += locals_len;
        }
        if (code_len) {
            memset(code_scratch, 0, sizeof(code_scratch));
            sys_proc_rw_w0((uint64_t)pid, code_addr, code_len, code_scratch, 0);
            memcpy(buf + off, code_scratch, code_len);
            off += code_len;
        }
        n_frames++;

        if (saved_rbp == 0) break;
        cur_rsp = cur_rbp + 8;
        cur_rbp = saved_rbp;
    }

    memcpy(buf, &n_frames, 4);
    net_send_int32(fd, CMD_SUCCESS);
    net_send_all(fd, &off, 4);
    net_send_all(fd, buf, (int)off);
    free(buf);
    return 0;
}

int proc_write_handle(int fd, struct cmd_packet *packet) {
    struct cmd_proc_write_packet *wp = (struct cmd_proc_write_packet *)packet->data;
    if (!wp) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    void *data = net_alloc_buffer(0x10000);
    if (!data) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    net_send_int32(fd, CMD_SUCCESS);

    uint64_t length  = wp->length;
    uint64_t address = wp->address;

    while (length > 0x10000) {
        net_recv_all(fd, data, 0x10000, 1);
        proc_write_mem(wp->pid, address, 0x10000, data);
        address += 0x10000;
        length  -= 0x10000;
    }
    if (length > 0) {
        net_recv_all(fd, data, (int)length, 1);
        proc_write_mem(wp->pid, address, length, data);
    }

    net_send_int32(fd, CMD_SUCCESS);
    free(data);
    return 0;
}

static int proc_write_mem_status(uint32_t pid, uint64_t addr, uint64_t len, const void *buf) {
    static int s_fw_needs_dmap = -1;
    if (s_fw_needs_dmap < 0)
        s_fw_needs_dmap = ((kernel_get_fw_version() & 0xffff0000u) >= 0x08400000u) ? 1 : 0;

    if (s_fw_needs_dmap && proc_ptwalk_write(pid, addr, len, buf) == 0)
        return 0;

    uint64_t wrote = 0;
    sys_proc_rw_w1((uint64_t)pid, addr, len, (void *)buf, (uint64_t)(uintptr_t)&wrote);
    return (wrote == len) ? 0 : 1;
}

int proc_write_multi_handle(int fd, struct cmd_packet *packet) {
    struct cmd_proc_write_multi_packet *mp =
        (struct cmd_proc_write_multi_packet *)packet->data;
    if (!mp) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    uint32_t pid         = mp->pid;
    uint32_t count       = mp->count;
    int      want_status = (mp->flags & PROC_WRITE_MULTI_F_STATUS) ? 1 : 0;

    if (count > PROC_WRITE_MULTI_MAX_COUNT) {
        net_send_int32(fd, CMD_ERROR);
        return 1;
    }

    uint8_t *buf = (uint8_t *)net_alloc_buffer(0x10000);
    if (!buf) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    uint8_t *status = NULL;
    if (want_status && count > 0) {
        status = (uint8_t *)net_alloc_buffer(count);
        if (!status) {
            free(buf);
            net_send_int32(fd, CMD_DATA_NULL);
            return 1;
        }
    }

    net_send_int32(fd, CMD_SUCCESS);

    for (uint32_t i = 0; i < count; i++) {
        uint8_t  hdr[12];
        if (net_recv_all(fd, hdr, 12, 1) < 0) {

            if (status) free(status);
            free(buf);
            return 1;
        }
        uint64_t addr;
        uint32_t len32;
        memcpy(&addr,  hdr,     8);
        memcpy(&len32, hdr + 8, 4);

        if (len32 > PROC_WRITE_MULTI_MAX_ENTRY) {

            if (status) free(status);
            free(buf);
            net_send_int32(fd, CMD_ERROR);
            return 1;
        }

        uint64_t length = len32;
        uint64_t a      = addr;
        uint8_t  failed = 0;
        while (length > 0) {
            uint64_t to_recv = (length > 0x10000ULL) ? 0x10000ULL : length;
            if (net_recv_all(fd, buf, (int)to_recv, 1) < 0) {
                if (status) free(status);
                free(buf);
                return 1;
            }
            if (proc_write_mem_status(pid, a, to_recv, buf) != 0)
                failed = 1;
            a      += to_recv;
            length -= to_recv;
        }
        if (status) status[i] = failed;
    }

    if (status) {
        net_send_all(fd, status, (int)count);
        free(status);
    }
    net_send_int32(fd, CMD_SUCCESS);
    free(buf);
    return 0;
}

int proc_maps_handle(int fd, struct cmd_packet *packet) {

    struct cmd_proc_maps_packet *mp = (struct cmd_proc_maps_packet *)packet->data;
    if (!mp) {
        net_send_int32(fd, CMD_ERROR);
        return 1;
    }

    void *maps = NULL;
    int   count = 0;

    if (sys_proc_vm_map(mp->pid, &maps, &count) != 0) {
        sceKernelUsleep(10000);
        for (int retries = 21; retries > 0; retries--) {
            if (sys_proc_vm_map(mp->pid, &maps, &count) == 0) break;
            sceKernelUsleep(10000);
            if (retries == 1) {
                net_send_int32(fd, CMD_ERROR);
                return 1;
            }
        }
    }

    if (count == 0) {
        net_send_int32(fd, CMD_ERROR);
        if (maps) free(maps);
        return 1;
    }
    if (maps == NULL) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    void *aug       = NULL;
    int   aug_count = 0;
    if (proc_ptwalk_augment(mp->pid, (struct proc_vm_map_entry *)maps, count,
                            (struct proc_vm_map_entry **)&aug, &aug_count) == 0
        && aug != NULL && aug_count > 0) {
        free(maps);
        maps  = aug;
        count = aug_count;
    }

    net_send_int32(fd, CMD_SUCCESS);
    uint32_t count32 = (uint32_t)count;
    net_send_all(fd, &count32, sizeof(count32));
    net_send_all(fd, maps, count * (int)sizeof(struct proc_vm_map_entry));
    free(maps);
    return 0;
}

int proc_install_handle(int fd, struct cmd_packet *packet) {
    if (!packet->data) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }
    uint64_t resp_rpcstub = 0;
    net_send_int32(fd, CMD_SUCCESS);
    net_send_all(fd, &resp_rpcstub, 8);
    return 0;
}

int proc_call_handle(int fd, struct cmd_packet *packet) {
    struct cmd_proc_call_packet *cp;
    struct sys_proc_call_args args;
    struct cmd_proc_call_response resp;

    cp = (struct cmd_proc_call_packet *)packet->data;

    if (cp) {
        args.pid     = cp->pid;
        args.rpcstub = cp->rpcstub;
        args.rax     = 0;
        args.rip     = cp->rpc_rip;
        args.rdi     = cp->rpc_rdi;
        args.rsi     = cp->rpc_rsi;
        args.rdx     = cp->rpc_rdx;
        args.rcx     = cp->rpc_rcx;
        args.r8      = cp->rpc_r8;
        args.r9      = cp->rpc_r9;

        if (sys_proc_cmd(cp->pid, SYS_PROC_CALL, &args)) return -1;

        resp.pid     = cp->pid;
        resp.rpc_rax = args.rax;
        net_send_int32(fd, CMD_SUCCESS);
        net_send_all(fd, &resp, (int)sizeof(resp));
        return 0;
    }

    net_send_int32(fd, CMD_DATA_NULL);
    return 1;
}

int proc_elf_handle(int fd, struct cmd_packet *packet) {
    struct cmd_proc_elf_packet *ep;
    struct sys_proc_elf_args args;
    void *elf;

    ep = (struct cmd_proc_elf_packet *)packet->data;
    if (!ep) {
        net_send_int32(fd, CMD_ERROR);
        return 1;
    }

    elf = net_alloc_buffer(ep->length);
    if (!elf) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    net_send_int32(fd, CMD_SUCCESS);
    net_recv_all(fd, elf, ep->length, 1);

    args.elf    = elf;
    args.length = ep->length;
    if (sys_proc_cmd(ep->pid, SYS_PROC_ELF, &args)) {
        free(elf);
        net_send_int32(fd, CMD_ERROR);
        return 1;
    }

    free(elf);
    net_send_int32(fd, CMD_SUCCESS);
    return 0;
}

int proc_elf_rpc_handle(int fd, struct cmd_packet *packet) {
    struct cmd_proc_elf_rpc_packet *ep;
    struct sys_proc_elf_rpc_args args;
    struct cmd_proc_elf_rpc_response resp;
    void *elf;

    ep = (struct cmd_proc_elf_rpc_packet *)packet->data;
    if (!ep) {
        net_send_int32(fd, CMD_ERROR);
        return 1;
    }

    elf = net_alloc_buffer(ep->length);
    if (!elf) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    net_send_int32(fd, CMD_SUCCESS);
    net_recv_all(fd, elf, ep->length, 1);

    args.elf    = elf;
    args.length = ep->length;
    args.entry  = 0;
    if (sys_proc_cmd(ep->pid, SYS_PROC_ELF_RPC, &args)) {
        free(elf);
        net_send_int32(fd, CMD_ERROR);
        return 1;
    }

    /* Build a JSON array of symbols from the ELF's symtab/dynsym + reloc sections.
     * Collect symbols into a temporary vector, supplement with relocation-based entries,
     * then sort and infer sizes for zero-sized symbols by next-symbol distance.
     * Format: [{"addr":<u64>,"size":<u64>,"type":<u8>,"name":"..."},...]
     * If parsing fails, send [] to client.
     */
    char *json = NULL;
    uint32_t json_len = 0;
    typedef struct { uint64_t addr; uint64_t size; uint8_t type; char *name; } sym_t;
    sym_t *symv = NULL; size_t symv_len = 0; size_t symv_cap = 0;
    do {
        if (ep->length < 0x40) break;
        const unsigned char *eb = (const unsigned char *)elf;
        uint64_t e_shoff = *(uint64_t *)(eb + 0x28);
        uint16_t e_shentsize = *(uint16_t *)(eb + 0x3A);
        uint16_t e_shnum = *(uint16_t *)(eb + 0x3C);
        uint16_t e_shstrndx = *(uint16_t *)(eb + 0x3E);
        if (e_shoff == 0 || e_shnum == 0 || e_shentsize == 0) break;
        if (e_shoff + (uint64_t)e_shentsize * e_shnum > (uint64_t)ep->length) break;

        const unsigned char *shdr_base = eb + e_shoff;
        const char *shstr = NULL;
        uint64_t shstr_off = 0;
        if (e_shstrndx < e_shnum) {
            const unsigned char *sh = shdr_base + (uint64_t)e_shstrndx * e_shentsize;
            shstr_off = *(uint64_t *)(sh + 0x18);
            if (shstr_off && shstr_off < (uint64_t)ep->length) shstr = (const char *)(eb + shstr_off);
        }

        /* Find symtab/dynsym sections and remember their section indices */
        uint64_t sym_off = 0, sym_size = 0, sym_entsize = 0, sym_link = 0; int sym_idx = -1;
        uint64_t dyn_off = 0, dyn_size = 0, dyn_entsize = 0, dyn_link = 0; int dyn_idx = -1;
        for (uint16_t i = 0; i < e_shnum; i++) {
            const unsigned char *sh = shdr_base + (uint64_t)i * e_shentsize;
            uint32_t sh_type = *(uint32_t *)(sh + 0x4);
            uint64_t sh_offset = *(uint64_t *)(sh + 0x18);
            uint64_t sh_size = *(uint64_t *)(sh + 0x20);
            uint64_t sh_link = *(uint32_t *)(sh + 0x28);
            uint64_t sh_entsize = *(uint64_t *)(sh + 0x38);

            if (sh_type == 2) { /* SHT_SYMTAB */
                sym_off = sh_offset; sym_size = sh_size; sym_entsize = sh_entsize; sym_link = sh_link; sym_idx = i;
            }
            if (sh_type == 11) { /* SHT_DYNSYM */
                dyn_off = sh_offset; dyn_size = sh_size; dyn_entsize = sh_entsize; dyn_link = sh_link; dyn_idx = i;
            }
        }

        /* prefer dynsym then symtab */
        uint64_t chosen_off = 0, chosen_size = 0, chosen_entsize = 0, chosen_link = 0; int chosen_idx = -1;
        if (dyn_off && dyn_size && dyn_entsize) {
            chosen_off = dyn_off; chosen_size = dyn_size; chosen_entsize = dyn_entsize; chosen_link = dyn_link; chosen_idx = dyn_idx;
        } else if (sym_off && sym_size && sym_entsize) {
            chosen_off = sym_off; chosen_size = sym_size; chosen_entsize = sym_entsize; chosen_link = sym_link; chosen_idx = sym_idx;
        }
        if (!chosen_off) break;
        if (chosen_off + chosen_size > (uint64_t)ep->length) break;

        /* resolve string table for chosen symtab (link index points to strtab section) */
        const char *strtab = NULL;
        uint64_t strtab_size = 0;
        if (chosen_link < e_shnum) {
            const unsigned char *shstrtab = shdr_base + (uint64_t)chosen_link * e_shentsize;
            uint64_t stroff = *(uint64_t *)(shstrtab + 0x18);
            uint64_t strsiz = *(uint64_t *)(shstrtab + 0x20);
            if (stroff && stroff < (uint64_t)ep->length && stroff + strsiz <= (uint64_t)ep->length) {
                strtab = (const char *)(eb + stroff);
                strtab_size = strsiz;
            }
        }
        if (!strtab) break;

        /* collect symbols from chosen table */
        uint64_t nsyms = chosen_entsize ? (chosen_size / chosen_entsize) : 0;
        for (uint64_t i = 0; i < nsyms; i++) {
            uint64_t off = chosen_off + i * chosen_entsize;
            if (off + 24 > (uint64_t)ep->length) continue;
            uint32_t st_name = *(uint32_t *)(eb + off + 0);
            uint8_t st_info = *(uint8_t *)(eb + off + 4);
            uint64_t st_value = *(uint64_t *)(eb + off + 8);
            uint64_t st_size = *(uint64_t *)(eb + off + 16);
            const char *name = (st_name < strtab_size) ? (strtab + st_name) : NULL;
            if (!name || name[0] == '\0') continue;
            if (st_value == 0) {
                /* defer zero-valued symbols; may be resolved via relocations */
                continue;
            }
            if (symv_len + 1 > symv_cap) {
                size_t ncap = symv_cap ? symv_cap * 2 : 64;
                symv = (sym_t *)realloc(symv, ncap * sizeof(sym_t));
                symv_cap = ncap;
            }
            symv[symv_len].addr = st_value;
            symv[symv_len].size = st_size;
            symv[symv_len].type = st_info & 0xFF;
            symv[symv_len].name = strdup(name);
            symv_len++;
        }

        /* parse reloc sections to add referenced symbols (use their relocation r_offset as best-effort address)
         * For each section with type SHT_RELA(4) or SHT_REL(9), resolve its linked symbol table and read entries.
         */
        for (uint16_t i = 0; i < e_shnum; i++) {
            const unsigned char *sh = shdr_base + (uint64_t)i * e_shentsize;
            uint32_t sh_type = *(uint32_t *)(sh + 0x4);
            if (sh_type != 4 && sh_type != 9) continue; /* SHT_RELA or SHT_REL */
            uint64_t rel_off = *(uint64_t *)(sh + 0x18);
            uint64_t rel_size = *(uint64_t *)(sh + 0x20);
            uint64_t rel_entsize = *(uint64_t *)(sh + 0x38);
            uint32_t rel_link = *(uint32_t *)(sh + 0x28); /* symbol table index */
            if (!rel_off || rel_off + rel_size > (uint64_t)ep->length) continue;
            if (rel_entsize == 0) continue;
            /* locate linked symbol table */
            if (rel_link >= e_shnum) continue;
            const unsigned char *symsh = shdr_base + (uint64_t)rel_link * e_shentsize;
            uint32_t sym_sh_type = *(uint32_t *)(symsh + 0x4);
            uint64_t sym_sh_off = *(uint64_t *)(symsh + 0x18);
            uint64_t sym_sh_size = *(uint64_t *)(symsh + 0x20);
            uint64_t sym_sh_entsize = *(uint64_t *)(symsh + 0x38);
            uint32_t sym_sh_link = *(uint32_t *)(symsh + 0x28);
            if (!sym_sh_off || sym_sh_off + sym_sh_size > (uint64_t)ep->length) continue;
            /* resolve string table for this symbol table */
            const char *local_strtab = NULL; uint64_t local_strtab_size = 0;
            if (sym_sh_link < e_shnum) {
                const unsigned char *sstr = shdr_base + (uint64_t)sym_sh_link * e_shentsize;
                uint64_t sstroff = *(uint64_t *)(sstr + 0x18);
                uint64_t sstrsiz = *(uint64_t *)(sstr + 0x20);
                if (sstroff && sstroff < (uint64_t)ep->length && sstroff + sstrsiz <= (uint64_t)ep->length) {
                    local_strtab = (const char *)(eb + sstroff);
                    local_strtab_size = sstrsiz;
                }
            }
            uint64_t nrels = rel_size / rel_entsize;
            for (uint64_t j = 0; j < nrels; j++) {
                uint64_t roff = rel_off + j * rel_entsize;
                if (roff + rel_entsize > (uint64_t)ep->length) continue;
                uint64_t r_offset = *(uint64_t *)(eb + roff + 0);
                uint64_t r_info = *(uint64_t *)(eb + roff + 8);
                uint32_t sym_index = (uint32_t)(r_info >> 32);
                /* read symbol from sym table */
                uint64_t sym_ent_off = sym_sh_off + (uint64_t)sym_index * sym_sh_entsize;
                if (sym_ent_off + 24 > (uint64_t)ep->length) continue;
                uint32_t s_st_name = *(uint32_t *)(eb + sym_ent_off + 0);
                uint8_t s_st_info = *(uint8_t *)(eb + sym_ent_off + 4);
                uint64_t s_st_value = *(uint64_t *)(eb + sym_ent_off + 8);
                uint64_t s_st_size = *(uint64_t *)(eb + sym_ent_off + 16);
                const char *s_name = (s_st_name < local_strtab_size && local_strtab) ? (local_strtab + s_st_name) : NULL;
                if (!s_name || s_name[0] == '\0') continue;
                /* If this symbol name not already present, add an entry using r_offset as address if non-zero; else use s_st_value */
                uint64_t use_addr = r_offset ? r_offset : s_st_value;
                if (use_addr == 0) continue;
                int found = 0;
                for (size_t k = 0; k < symv_len; k++) if (symv[k].addr == use_addr) { found = 1; break; }
                if (found) continue;
                if (symv_len + 1 > symv_cap) {
                    size_t ncap = symv_cap ? symv_cap * 2 : 64;
                    symv = (sym_t *)realloc(symv, ncap * sizeof(sym_t));
                    symv_cap = ncap;
                }
                symv[symv_len].addr = use_addr;
                symv[symv_len].size = s_st_size;
                symv[symv_len].type = s_st_info & 0xFF;
                symv[symv_len].name = strdup(s_name);
                symv_len++;
            }
        }

        /* sort symbols by addr (simple stable sort) */
        if (symv_len > 1) {
            for (size_t x = 0; x + 1 < symv_len; x++) {
                for (size_t y = x + 1; y < symv_len; y++) {
                    if (symv[x].addr > symv[y].addr) {
                        sym_t tmp = symv[x]; symv[x] = symv[y]; symv[y] = tmp;
                    }
                }
            }
        }

        /* infer sizes for zero-sized symbols by next symbol distance */
        for (size_t i = 0; i < symv_len; i++) {
            if (symv[i].size == 0) {
                if (i + 1 < symv_len && symv[i+1].addr > symv[i].addr) symv[i].size = symv[i+1].addr - symv[i].addr;
                else symv[i].size = 1;
            }
        }

        /* prepare JSON buffer estimate */
        uint64_t est = 2 + symv_len * 80;
        for (size_t i = 0; i < symv_len; i++) if (symv[i].name) est += strlen(symv[i].name);
        json = (char *)malloc((size_t)est + 1);
        if (!json) break;
        uint32_t pos = 0;
        json[pos++] = '[';
        int first = 1;

        for (size_t i = 0; i < symv_len; i++) {
            if (!symv[i].name) continue;
            if (!first) json[pos++] = ',';
            first = 0;
            size_t needed = 128 + strlen(symv[i].name) * 2;
            if (pos + needed > (uint32_t)est) {
                est = est * 2 + (uint64_t)needed;
                char *njson = (char *)realloc(json, (size_t)est + 1);
                if (!njson) { free(json); json = NULL; break; }
                json = njson;
            }
            int n = snprintf(json + pos, (size_t)(est - pos + 1), "{\"addr\":%llu,\"size\":%llu,\"type\":%u,\"name\":\"",
                             (unsigned long long)symv[i].addr, (unsigned long long)symv[i].size, (unsigned)symv[i].type & 0xFF);
            if (n < 0) { free(json); json = NULL; break; }
            pos += (uint32_t)n;
            for (const char *p = symv[i].name; *p; p++) {
                if (*p == '"' || *p == '\\') {
                    if (pos + 2 >= est) { est = est * 2 + 16; char *njson = (char *)realloc(json, (size_t)est + 1); if (!njson) { free(json); json = NULL; break; } json = njson; }
                    json[pos++] = '\\';
                    json[pos++] = *p;
                } else if ((unsigned char)*p >= 0x20) {
                    json[pos++] = *p;
                }
            }
            if (!json) break;
            if (pos + 4 >= est) { est = est * 2 + 16; char *njson = (char *)realloc(json, (size_t)est + 1); if (!njson) { free(json); json = NULL; break; } json = njson; }
            int m = snprintf(json + pos, (size_t)(est - pos + 1), "\"}");
            if (m < 0) { free(json); json = NULL; break; }
            pos += (uint32_t)m;
        }
        if (!json) break;
        if (pos + 2 >= est) { est = est + 16; char *njson = (char *)realloc(json, (size_t)est + 1); if (!njson) { free(json); json = NULL; break; } json = njson; }
        json[pos++] = ']';
        json[pos] = '\0';
        json_len = pos;

    } while (0);

    /* If parsing failed, ensure an empty JSON array */
    if (!json) {
        json = (char *)malloc(3);
        if (json) { memcpy(json, "[]", 3); json_len = 2; }
    }

    /* cleanup sym vector */
    if (symv) {
        for (size_t i = 0; i < symv_len; i++) if (symv[i].name) free(symv[i].name);
        free(symv);
    }

    free(elf);

    resp.entry = args.entry;
    net_send_int32(fd, CMD_SUCCESS);
    net_send_all(fd, &resp, (int)sizeof(resp));

    if (json && json_len > 0) {
        uint32_t len32 = (uint32_t)json_len;
        net_send_all(fd, &len32, 4);
        net_send_all(fd, json, (int)len32);
        free(json);
    } else {
        uint32_t len32 = 0;
        net_send_all(fd, &len32, 4);
    }

    return 0;
}

struct cmd_proc_protect_packet {
    uint32_t pid;
    uint64_t address;
    uint32_t length;
    uint32_t prot;
} __attribute__((packed));

int proc_protect_handle(int fd, struct cmd_packet *packet) {
    struct cmd_proc_protect_packet *pp;
    struct sys_proc_protect_args args;

    pp = (struct cmd_proc_protect_packet *)packet->data;

    if (pp) {
        args.address = pp->address;
        args.length  = pp->length;
        args.prot    = pp->prot;

        int rc = sys_proc_cmd(pp->pid, SYS_PROC_PROTECT, &args);
        net_send_int32(fd, rc == 0 ? CMD_SUCCESS : CMD_ERROR);
        return 0;
    }

    net_send_int32(fd, CMD_DATA_NULL);

    return 0;
}

int proc_info_handle(int fd, struct cmd_packet *packet) {
    struct cmd_proc_info_packet *ip = (struct cmd_proc_info_packet *)packet->data;
    if (!ip) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    if ((int32_t)ip->pid <= 0) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    struct proc_field_offsets off;
    if (proc_get_field_offsets(&off) != 0) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    void *proc_buf = kernel_get_proc_struct_fast(ip->pid);
    if (!proc_buf) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    struct cmd_proc_info_response resp;
    memset(&resp, 0, sizeof(resp));
    resp.pid = ip->pid;

    const uint8_t *p = (const uint8_t *)proc_buf;

    memcpy(resp.name, p + off.name, PROC_SELFINFO_NAME_SIZE);

    copy_cstr_from_buf(p + off.path,      resp.path,      sizeof(resp.path));

    uint8_t aibuf[0x80];
    memset(aibuf, 0, sizeof(aibuf));
    int ai_rc = sceKernelGetAppInfo((pid_t)ip->pid, (struct sce_app_info *)aibuf);

    char tid[16];
    memset(tid, 0, sizeof(tid));
    if (ai_rc == 0 && find_titleid(aibuf, sizeof(aibuf), tid) == 0) {

    } else if (find_titleid(p + 0x440, 0x200, tid) == 0) {

    } else {
        copy_cstr_from_buf(p + off.titleid, tid, sizeof(tid));
    }
    {
        size_t tcap = sizeof(resp.titleid);
        memcpy(resp.titleid, tid, tcap < sizeof(tid) ? tcap : sizeof(tid));
    }

    if (find_contentid(p + 0x440, 0x200, resp.contentid, sizeof(resp.contentid)) != 0) {
        copy_cstr_from_buf(p + off.contentid, resp.contentid, sizeof(resp.contentid));
    }

    free(proc_buf);

    net_send_int32(fd, CMD_SUCCESS);
    net_send_all(fd, &resp, sizeof(resp));
    return 0;
}

int proc_alloc_handle(int fd, struct cmd_packet *packet) {
    struct cmd_proc_alloc_packet *ap;
    struct sys_proc_alloc_args args;
    struct cmd_proc_alloc_response resp;

    ap = (struct cmd_proc_alloc_packet *)packet->data;

    if (ap) {
        args.length = ap->length;

        if (sys_proc_cmd(ap->pid, SYS_PROC_ALLOC, &args) != 0) {
            net_send_int32(fd, CMD_ERROR);
            return 0;
        }

        resp.address = args.address;

        net_send_int32(fd, CMD_SUCCESS);
        net_send_all(fd, &resp, sizeof(resp));
        return 0;
    }

    net_send_int32(fd, CMD_DATA_NULL);

    return 0;
}

int proc_free_handle(int fd, struct cmd_packet *packet) {
    struct cmd_proc_free_packet *fp;
    struct sys_proc_free_args args;

    fp = (struct cmd_proc_free_packet *)packet->data;

    if (fp) {
        args.address = fp->address;
        args.length  = fp->length;

        sys_proc_cmd(fp->pid, SYS_PROC_FREE, &args);

        net_send_int32(fd, CMD_SUCCESS);
        return 0;
    }

    net_send_int32(fd, CMD_DATA_NULL);

    return 0;
}

int proc_unknown_d_handle(int fd, struct cmd_packet *packet) {
    struct cmd_proc_unknown_d_packet *up =
        (struct cmd_proc_unknown_d_packet *)packet->data;
    if (!up) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    void *maps = NULL;
    int count = 0;
    int64_t result = 0;
    int ok = (sys_proc_vm_map(up->pid, &maps, &count) == 0
              && count > 0 && maps);
    if (ok) {
        struct proc_vm_map_entry *first = (struct proc_vm_map_entry *)maps;
        result = (int64_t)first->start;
    }
    if (maps) free(maps);

    if (!ok) {
        net_send_int32(fd, CMD_ERROR);
        return 0;
    }
    net_send_int32(fd, CMD_SUCCESS);
    net_send_all(fd, &result, 8);
    return 0;
}

int proc_alloc_hinted_handle(int fd, struct cmd_packet *packet) {
    struct cmd_proc_alloc_hinted_packet *ap;
    struct sys_proc_alloc_args args;
    struct cmd_proc_alloc_response resp;

    ap = (struct cmd_proc_alloc_hinted_packet *)packet->data;

    if (ap) {
        args.address = ap->hint;
        args.length  = ap->length;

        int rc = sys_proc_cmd(ap->pid, SYS_PROC_ALLOC_HINTED, &args);
        resp.address = args.address;
        if (rc != 0) {
            net_send_int32(fd, CMD_ERROR);
            net_send_all(fd, &resp, sizeof(resp));
            return 0;
        }

        net_send_int32(fd, CMD_SUCCESS);
        net_send_all(fd, &resp, sizeof(resp));
        return 0;
    }

    net_send_int32(fd, CMD_DATA_NULL);

    return 0;
}

int proc_arena_handle(int fd, struct cmd_packet *packet) {
    uint32_t on = 1;
    if (packet->data && packet->datalen >= 4) on = *(uint32_t *)packet->data;
    char *buf = (char *)net_alloc_buffer(512);
    if (!buf) { net_send_int32(fd, CMD_DATA_NULL); return 1; }
    int n = proc_arena_set((int)on, buf, 512);
    if (n < 0) n = 0;
    if (n > 512) n = 512;
    net_send_int32(fd, CMD_SUCCESS);
    uint32_t len = (uint32_t)n;
    net_send_all(fd, &len, 4);
    net_send_all(fd, buf, n);
    free(buf);
    return 0;
}

int proc_handle(int fd, struct cmd_packet *packet, unsigned char client_idx) {
    uint32_t cmd = packet->cmd;

    switch (cmd) {
    case 0xBDAA0001u: return proc_list_handle(fd, packet);
    case 0xBDAA0002u: return proc_read_handle(fd, packet);
    case 0xBDAA0023u: return proc_read_stack_handle(fd, packet);
    case 0xBDAA0024u: return proc_assemble_handle(fd, packet);
    case 0xBDAA0003u: return proc_write_handle(fd, packet);
    case 0xBDAACC04u: return proc_write_multi_handle(fd, packet);
    case 0xBDAA0004u: return proc_maps_handle(fd, packet);
    case 0xBDAA0005u: return proc_install_handle(fd, packet);
    case 0xBDAA0006u: return proc_call_handle(fd, packet);
    case 0xBDAA0007u: return proc_elf_handle(fd, packet);
    case 0xBDAA0008u: return proc_protect_handle(fd, packet);
    case 0xBDAA0009u: return proc_scan_handle(fd, packet);
    case 0xBDAA000Au: return proc_info_handle(fd, packet);
    case 0xBDAA000Bu: return proc_alloc_handle(fd, packet);
    case 0xBDAA000Cu: return proc_free_handle(fd, packet);
    case 0xBDAA000Du: return proc_unknown_d_handle(fd, packet);
    case 0xBDAA000Eu: return proc_alloc_hinted_handle(fd, packet);
    case 0xBDAA0010u: return proc_elf_rpc_handle(fd, packet);
    case 0xBDAA0020u: return proc_disasm_region_handle(fd, packet);
    case 0xBDAA0021u: return proc_extract_code_xrefs_handle(fd, packet);
    case 0xBDAA0022u: return proc_find_xrefs_to_handle(fd, packet);
    case 0xBDAA0501u: return proc_scan_aob_handle(fd, packet);
    case 0xBDAA0502u: return proc_scan_aob_multi_handle(fd, packet);
    case 0xBDAACCFFu: return proc_auth_handle(fd, packet);
    case 0xBDAACC01u: return proc_scan_start_handle(fd, packet);
    case 0xBDAACC02u: return proc_scan_count_handle(fd, packet);
    case 0xBDAACC03u: return proc_scan_get_handle(fd, packet);
    case 0xBDAACC10u: return proc_turboscan_caps_handle(fd, packet);
    case 0xBDAACC11u: return proc_turboscan_start_handle(fd, packet, client_idx);
    case 0xBDAACC12u: return proc_turboscan_count_handle(fd, packet, client_idx);
    case 0xBDAACC13u: return proc_turboscan_get_handle(fd, packet, client_idx);
    case 0xBDAACC14u: return proc_turboscan_end_handle(fd, packet, client_idx);
    case 0xBDAACC15u: return proc_turboscan_config_handle(fd, packet);
    case 0xBDAACC16u: return proc_turboscan_regions_handle(fd, packet);
    case 0xBDAACC24u: return proc_arena_handle(fd, packet);
    }

    net_send_int32(fd, CMD_ERROR);
    return 0;
}

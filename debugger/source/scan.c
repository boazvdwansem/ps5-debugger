// SPDX-License-Identifier: GPL-3.0-only

#include "protocol.h"
#include "sdk_shim.h"
#include "net.h"
#include "proc.h"
#include "kern_rw_fast.h"
#include "aob_scan.h"
#include <ps5/kernel.h>

static int      vmmap_offs_initialized = 0;
static uint64_t vmmap_nentries_adj = 0;
static uint64_t vmmap_name_adj     = 0;

static void vmmap_init_offsets(void) {
    uint32_t fw = kernel_get_fw_version();

    int write_adj = ((fw & 0xffff0000u) >= 0x6000000u) ? 1 : 0;

    if (write_adj) {
        vmmap_nentries_adj = 8;
        vmmap_name_adj     = 0xE;
    }
    vmmap_offs_initialized = 1;
}

int sys_proc_vm_map(uint32_t pid, void **out_maps, int *out_count) {
    if (out_maps)  *out_maps = NULL;
    if (out_count) *out_count = 0;

    if ((int32_t)pid <= 0) return 1;

    if (!vmmap_offs_initialized) vmmap_init_offsets();

    intptr_t kproc = kernel_get_proc_fast((pid_t)pid);
    if (!kproc) return 1;

    intptr_t vmspace_kaddr = 0;
    if (kernel_copyout_fast(kproc + 0x200, &vmspace_kaddr, sizeof(vmspace_kaddr)) != 0) return 1;

    uint8_t vmspace_buf[0x350];
    memset(vmspace_buf, 0, sizeof(vmspace_buf));
    if (kernel_copyout_fast(vmspace_kaddr, vmspace_buf, sizeof(vmspace_buf)) != 0) return 1;

    int32_t n_entries = *(int32_t *)(vmspace_buf + 0x1A8 + (uintptr_t)vmmap_nentries_adj);
    if (n_entries <= 0) return 1;

    void *out_buf = malloc((size_t)n_entries * sizeof(struct proc_vm_map_entry));
    if (!out_buf) return 1;

    intptr_t cur_entry = *(intptr_t *)(vmspace_buf + 8);
    uint8_t *out_p   = (uint8_t *)out_buf;
    uint8_t *out_end = out_p + (size_t)n_entries * sizeof(struct proc_vm_map_entry);

    while (cur_entry != 0) {
        uint8_t entry_buf[0x1C0];
        memset(entry_buf, 0, sizeof(entry_buf));
        if (kernel_copyout_fast(cur_entry, entry_buf, sizeof(entry_buf)) != 0) {
            free(out_buf);
            return 1;
        }

        *(uint64_t *)(out_p + 0x20) = *(uint64_t *)(entry_buf + 0x20);
        *(uint64_t *)(out_p + 0x28) = *(uint64_t *)(entry_buf + 0x28);
        *(uint64_t *)(out_p + 0x30) = *(uint64_t *)(entry_buf + 0x58);

        uint8_t prot_byte = entry_buf[0x64] & 0x0F;
        if (prot_byte == 4) prot_byte = 5;
        *(uint16_t *)(out_p + 0x38) = (uint16_t)prot_byte;

        memcpy(out_p, entry_buf + 0x142 + (uintptr_t)vmmap_name_adj, 0x20);

        cur_entry = *(intptr_t *)(entry_buf + 8);
        out_p += sizeof(struct proc_vm_map_entry);
        if (out_p >= out_end) break;
    }

    if (out_maps)  *out_maps  = out_buf;
    if (out_count) *out_count = n_entries;
    return 0;
}

int proc_scan_handle(int fd, struct cmd_packet *packet) {
    struct cmd_proc_scan_packet *sp = (struct cmd_proc_scan_packet *)packet->data;
    if (!sp) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    uint64_t value_length = proc_scan_getSizeOfValueType(sp->valueType);
    if (value_length == 0) value_length = sp->lenData;

    uint8_t *data = (uint8_t *)net_alloc_buffer(sp->lenData);
    if (!data) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    net_send_int32(fd, CMD_SUCCESS);
    net_recv_all(fd, data, sp->lenData, 1);

    void *maps = NULL;
    int count = 0;
    if (sys_proc_vm_map(sp->pid, &maps, &count) != 0) {
        net_send_int32(fd, CMD_ERROR);
        free(data);
        return 1;
    }

    net_send_int32(fd, CMD_SUCCESS);

    const uint8_t *extra = (sp->lenData == value_length) ? NULL : &data[value_length];
    uint8_t *scan_buf = (uint8_t *)net_alloc_buffer(0x4000);

    if (count > 0 && scan_buf) {
        struct proc_vm_map_entry *entry = (struct proc_vm_map_entry *)maps;
        for (int i = 0; i < count; i++,
                                 entry = (struct proc_vm_map_entry *)
                                         ((uint8_t *)entry + sizeof(*entry))) {
            if (!(entry->prot & 1)) continue;

            uint64_t start = entry->start;
            uint64_t section_len = entry->end - start;
            if (!section_len) continue;

            for (uint64_t j = 0; j < section_len; j += value_length) {
                uint64_t off = j & 0x3FFF;
                if (j == 0 || off == 0) {

                    proc_read_mem(sp->pid, start, 0x4000, scan_buf);
                    off = j & 0x3FFF;
                }
                uint64_t cur_addr = start + j;
                if (proc_scan_legacy_compareValues(sp->compareType, sp->valueType,
                                                   value_length, data,
                                                   &scan_buf[off], extra)) {
                    net_send_all(fd, &cur_addr, 8);
                }
            }
        }
    }

    uint64_t endflag = 0xFFFFFFFFFFFFFFFFull;
    net_send_all(fd, &endflag, 8);

    if (scan_buf) free(scan_buf);
    if (maps)     free(maps);
    free(data);
    return 0;
}

int proc_scan_aob_handle(int fd, struct cmd_packet *packet) {
    struct cmd_proc_scan_aob_packet *sp = (struct cmd_proc_scan_aob_packet *)packet->data;
    if (!sp || sp->pattern_length == 0) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    uint32_t plen = sp->pattern_length;

    uint64_t step_unit = proc_scan_getSizeOfValueType(10);
    if (step_unit == 0) step_unit = plen;

    uint8_t *pattern = (uint8_t *)net_alloc_buffer(plen);
    if (!pattern) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }
    uint8_t *mask = (uint8_t *)net_alloc_buffer((uint32_t)step_unit);
    if (!mask) {
        free(pattern);
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    net_send_int32(fd, CMD_SUCCESS);
    net_recv_all(fd, pattern, plen, 1);
    net_recv_all(fd, mask, (int)step_unit, 1);

    uint64_t chunk_size = 0x100000;
    if (chunk_size % step_unit != 0) chunk_size = (chunk_size / step_unit) * step_unit;

    uint8_t *read_buf = (uint8_t *)net_alloc_buffer(chunk_size);
    if (!read_buf) {
        free(pattern);
        free(mask);
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    net_send_int32(fd, CMD_SUCCESS);

    uint64_t address     = sp->address;
    uint64_t remaining   = sp->length;
    uint64_t match_count = 0;
    uint64_t match_addr  = 0;
    uint8_t  stop_flag   = sp->stop_flag;
    uint8_t  max_matches = sp->max_matches;
    int stop_unique = (stop_flag == 1);
    int invalidated = 0;
    int found_done  = 0;

    while (remaining > 0) {
        uint64_t to_read = (chunk_size < remaining) ? chunk_size : remaining;
        memset(read_buf, 0, to_read);
        proc_read_mem(sp->pid, address, to_read, read_buf);

        uint64_t limit = (to_read >= plen) ? to_read - plen : 0;
        for (uint64_t j = 0; j <= limit; j++) {
            if (aob_match(plen, pattern, &read_buf[j], 0, mask)) {
                match_count++;
                if (match_count == max_matches) {
                    match_addr = address + j;
                    if (!stop_unique) { found_done = 1; break; }
                } else if (match_count > max_matches && stop_unique) {
                    match_addr = 0;
                    invalidated = 1;
                    break;
                }
            }
        }
        if (found_done || invalidated) break;
        if (chunk_size >= remaining) break;

        uint64_t advance = chunk_size - plen + 1;
        address  += advance;
        remaining = (remaining > advance) ? remaining - advance : 0;
    }

    net_send_all(fd, &match_addr, 8);
    free(read_buf);
    free(pattern);
    free(mask);
    net_send_int32(fd, CMD_SUCCESS);
    return 0;
}

/* Region-parallel multi-pattern AOB scan (core in aob_scan.h). The range is split into
   g_aob_scan_threads contiguous sub-ranges (one worker each, worker 0 inline); each worker
   scans its sub-range once for ALL patterns via the shared first-byte dispatch table, then
   aob_merge() combines results preserving target_count / uniqueness. Reads via proc_read_mem.
   The first-byte dispatch is the big win; AOB is read-bound on mdbg so workers plateau at 2-3. */
#define AOB_SCAN_THREADS_MAX     16

uint32_t g_aob_scan_threads = 3;             /* read-bound on mdbg: 2-3 is the sweet spot */
uint32_t g_aob_chunk_size   = 0x40000u;      /* 256 KiB per-worker read window */

struct aob_ps5_fill { uint32_t pid; uint64_t base; };

static void aob_ps5_fill_fn(void *ctx, uint64_t off, uint8_t *buf, uint32_t len) {
    struct aob_ps5_fill *f = (struct aob_ps5_fill *)ctx;
    proc_read_mem(f->pid, f->base + off, len, buf);
}

static void *aob_thread(void *arg) {
    aob_scan_range((struct aob_worker *)arg);
    return NULL;
}

int proc_scan_aob_multi_handle(int fd, struct cmd_packet *packet) {
    struct cmd_proc_scan_aob_multi_packet *mp =
        (struct cmd_proc_scan_aob_multi_packet *)packet->data;
    if (!mp || mp->patterns_length == 0) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    uint32_t patterns_length = mp->patterns_length;
    uint8_t *blob = (uint8_t *)net_alloc_buffer(patterns_length);
    if (!blob) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    net_send_int32(fd, CMD_SUCCESS);
    net_recv_all(fd, blob, patterns_length, 1);

    uint16_t pat_count = 0;
    uint64_t max_plen  = 1;
    {
        uint32_t cursor = 0;
        while (cursor + 5 <= patterns_length) {
            uint32_t plen;
            memcpy(&plen, blob + cursor + 1, 4);
            cursor += 5 + 2 * plen;
            if (plen > max_plen) max_plen = plen;
            pat_count++;
        }
    }
    /* Region-parallel multi-pattern scan. Split [address, address+length) into
       num_workers contiguous ascending sub-ranges; each worker scans its sub-range once
       for ALL patterns via the shared first-byte dispatch table (each byte read once
       total; most positions test ~0-1 patterns), then aob_merge() combines per-worker
       results preserving exact target_count / uniqueness semantics. Reads via proc_read_mem
       (mdbg). num_workers + chunk are DEV-tunable (g_aob_scan_threads / g_aob_chunk_size). */
    uint32_t chunkSize = g_aob_chunk_size;
    if (chunkSize < max_plen) chunkSize = (uint32_t)max_plen;
    uint64_t length = mp->length;
    int stop_unique = (mp->stop_flag == 1);

    struct aob_pat *pats = NULL;
    uint32_t *bidx = NULL, *widx = NULL, *slot_off = NULL;
    uint64_t *output = NULL;
    uint8_t  *readbufs = NULL, *wdone = NULL;
    uint64_t *waddrs = NULL;
    uint32_t *wcounts = NULL;
    struct aob_dispatch disp;
    uint32_t total_slots = 0;
    uint32_t num_workers = 1;
    uint64_t span = 1;

    pats     = (struct aob_pat *)net_alloc_buffer((uint32_t)((size_t)pat_count * sizeof(struct aob_pat)));
    bidx     = (uint32_t *)net_alloc_buffer((uint32_t)((size_t)pat_count * sizeof(uint32_t)));
    widx     = (uint32_t *)net_alloc_buffer((uint32_t)((size_t)pat_count * sizeof(uint32_t)));
    slot_off = (uint32_t *)net_alloc_buffer((uint32_t)((size_t)(pat_count + 1) * sizeof(uint32_t)));
    output   = (uint64_t *)net_alloc_buffer((uint32_t)pat_count * 8u);
    if (!pats || !bidx || !widx || !slot_off || !output) goto aob_fail;

    /* Pattern table from the validated blob, then first-byte dispatch + result-slot offsets. */
    {
        uint32_t cursor = 0;
        for (uint32_t pi = 0; pi < pat_count; pi++) {
            uint32_t plen;
            memcpy(&plen, blob + cursor + 1, 4);
            const uint8_t *pat = blob + cursor + 5;
            const uint8_t *msk = pat + plen;
            pats[pi].pat = pat;
            pats[pi].msk = msk;
            pats[pi].plen = plen;
            pats[pi].target_count = blob[cursor];
            pats[pi].first_fixed = (msk[0] == 1) ? (int16_t)pat[0] : (int16_t)-1;
            cursor += 5 + 2 * plen;
        }
    }
    aob_build_dispatch(pats, pat_count, &disp, bidx, widx);

    slot_off[0] = 0;
    for (uint32_t pi = 0; pi < pat_count; pi++)
        slot_off[pi + 1] = slot_off[pi] + ((uint32_t)pats[pi].target_count + 1);
    total_slots = slot_off[pat_count];

    num_workers = g_aob_scan_threads;
    if (num_workers < 1) num_workers = 1;
    if (num_workers > AOB_SCAN_THREADS_MAX) num_workers = AOB_SCAN_THREADS_MAX;
    if ((uint64_t)num_workers > length) num_workers = (length > 0) ? (uint32_t)length : 1;
    span = (length + num_workers - 1) / num_workers;
    if (span == 0) span = 1;
    num_workers = (uint32_t)((length + span - 1) / span);
    if (num_workers < 1) num_workers = 1;
    if (num_workers > AOB_SCAN_THREADS_MAX) num_workers = AOB_SCAN_THREADS_MAX;

    readbufs = (uint8_t  *)net_alloc_buffer((uint32_t)((size_t)num_workers * (chunkSize + max_plen)));
    waddrs   = (uint64_t *)net_alloc_buffer((uint32_t)((size_t)num_workers * total_slots * 8));
    wcounts  = (uint32_t *)net_alloc_buffer((uint32_t)((size_t)num_workers * pat_count * sizeof(uint32_t)));
    wdone    = (uint8_t  *)net_alloc_buffer((uint32_t)((size_t)num_workers * pat_count));
    if (!readbufs || !waddrs || !wcounts || !wdone) goto aob_fail;

    net_send_int32(fd, CMD_SUCCESS);
    memset(output, 0, (size_t)pat_count * 8u);

    {
        struct aob_ps5_fill fillctx;
        struct aob_worker workers[AOB_SCAN_THREADS_MAX];
        ScePthread tids[AOB_SCAN_THREADS_MAX];
        bool spawned[AOB_SCAN_THREADS_MAX];
        const uint64_t *ca[AOB_SCAN_THREADS_MAX];
        const uint32_t *cc[AOB_SCAN_THREADS_MAX];

        fillctx.pid  = mp->pid;
        fillctx.base = mp->address;

        for (uint32_t w = 0; w < num_workers; w++) {
            uint64_t s = (uint64_t)w * span;
            uint64_t e = s + span;
            if (s > length) s = length;
            if (e > length) e = length;
            workers[w].pats = pats;
            workers[w].pat_count = pat_count;
            workers[w].disp = &disp;
            workers[w].base_addr = mp->address;
            workers[w].length = length;
            workers[w].max_plen = (uint32_t)max_plen;
            workers[w].chunkSize = chunkSize;
            workers[w].fill = aob_ps5_fill_fn;
            workers[w].fill_ctx = &fillctx;
            workers[w].slot_off = slot_off;
            workers[w].start = s;
            workers[w].end = e;
            workers[w].readBuf = readbufs + (size_t)w * (chunkSize + max_plen);
            workers[w].addrs = waddrs + (size_t)w * total_slots;
            workers[w].counts = wcounts + (size_t)w * pat_count;
            workers[w].done = wdone + (size_t)w * pat_count;
            spawned[w] = false;
        }

        /* Spawn workers 1..N-1, run worker 0 inline, then join (or run inline on spawn fail). */
        for (uint32_t w = 1; w < num_workers; w++) {
            if (scePthreadCreate(&tids[w], NULL, aob_thread, &workers[w], "aobscan") == 0)
                spawned[w] = true;
        }
        aob_scan_range(&workers[0]);
        for (uint32_t w = 1; w < num_workers; w++) {
            if (spawned[w]) scePthreadJoin(tids[w], NULL);
            else aob_scan_range(&workers[w]);
        }

        for (uint32_t w = 0; w < num_workers; w++) {
            ca[w] = waddrs + (size_t)w * total_slots;
            cc[w] = wcounts + (size_t)w * pat_count;
        }
        aob_merge(pats, pat_count, stop_unique, slot_off, num_workers, ca, cc, output);
    }

    net_send_all(fd, output, (int)((size_t)pat_count * 8u));
    if (wdone) free(wdone);
    if (wcounts) free(wcounts);
    if (waddrs) free(waddrs);
    if (readbufs) free(readbufs);
    free(output);
    free(slot_off);
    free(widx);
    free(bidx);
    free(pats);
    free(blob);
    net_send_int32(fd, CMD_SUCCESS);
    return 0;

aob_fail:
    if (wdone) free(wdone);
    if (wcounts) free(wcounts);
    if (waddrs) free(waddrs);
    if (readbufs) free(readbufs);
    if (output) free(output);
    if (slot_off) free(slot_off);
    if (widx) free(widx);
    if (bidx) free(bidx);
    if (pats) free(pats);
    free(blob);
    net_send_int32(fd, CMD_DATA_NULL);
    return 1;
}

int proc_scan_start_handle(int fd, struct cmd_packet *packet) {
    if (!(g_proc_auth_state & 2)) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    struct cmd_proc_scan_start_packet *sp = (struct cmd_proc_scan_start_packet *)packet->data;
    if (!sp) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }
    if (sp->compareType > 12 || sp->lenData == 0) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    int needs_scan_value = ((1u << sp->compareType) & 0x114Fu) != 0;
    int is_between       = (sp->compareType == 4);
    int is_arrbytes      = (sp->valueType == 10);
    if (is_between) needs_scan_value = 1;

    uint64_t value_length = proc_scan_getSizeOfValueType(sp->valueType);
    if (value_length == 0) value_length = sp->lenData;
    if (value_length == 0) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    uint8_t *pattern = NULL;
    if (needs_scan_value && sp->lenData) {
        pattern = (uint8_t *)net_alloc_buffer(sp->lenData);
        if (!pattern) {
            net_send_int32(fd, CMD_DATA_NULL);
            return 1;
        }
    }

    net_send_int32(fd, CMD_SUCCESS);
    if (pattern) {
        net_recv_all(fd, pattern, (int)sp->lenData, 1);
    }

    uint8_t *mask = NULL;
    if (is_arrbytes) {
        mask = (uint8_t *)net_alloc_buffer((uint32_t)value_length);
        if (!mask) {
            if (pattern) free(pattern);
            net_send_int32(fd, CMD_DATA_NULL);
            return 1;
        }
        net_recv_all(fd, mask, (int)value_length, 1);
    }

    uint64_t step = sp->alignment ? sp->alignment : value_length;

    uint64_t chunk_size = 0x100000ULL;
    if (chunk_size % value_length != 0) {
        chunk_size = (chunk_size / value_length) * value_length;
    }

    uint8_t *read_buf   = (uint8_t *)net_alloc_buffer(chunk_size);
    uint8_t *result_buf = (uint8_t *)net_alloc_buffer(0x40000);
    if (!read_buf || !result_buf) {
        if (read_buf)   free(read_buf);
        if (result_buf) free(result_buf);
        if (pattern) free(pattern);
        if (mask)    free(mask);
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    net_send_int32(fd, CMD_SUCCESS);

    uint64_t addr         = sp->address;
    uint64_t remaining    = sp->length;
    uint64_t result_len   = 0;
    uint64_t flush_thresh = 0x3FFE8ULL - value_length;

    const void *prev_for_between = is_between ? (pattern + value_length) : NULL;

    while (remaining > 0) {
        uint64_t to_read = (remaining > chunk_size) ? chunk_size : remaining;
        memset(read_buf, 0, to_read);
        proc_read_mem(sp->pid, addr, to_read, read_buf);

        uint64_t limit = (to_read >= value_length) ? to_read - value_length : 0;
        for (uint64_t j = 0; j <= limit; j += step) {
            if (proc_scan_compareValues(sp->compareType, sp->valueType, value_length,
                                        pattern, &read_buf[j], prev_for_between, mask)) {
                if (result_len > flush_thresh) {
                    *(uint64_t *)result_buf = result_len;
                    net_send_all(fd, result_buf, (int)(result_len + 8));
                    result_len = 0;
                }
                uint32_t offset = (uint32_t)((addr + j) - sp->address);
                memcpy(result_buf + 8 + result_len,         &offset,        4);
                memcpy(result_buf + 8 + result_len + 4,     &read_buf[j],   value_length);
                result_len += 4 + value_length;
            }
        }

        uint64_t advance = to_read + step - value_length;
        if (advance == 0 || advance > to_read) advance = to_read;
        addr += advance;
        remaining = (remaining > advance) ? remaining - advance : 0;
    }

    if (result_len) {
        *(uint64_t *)result_buf = result_len;
        net_send_all(fd, result_buf, (int)(result_len + 8));
    }

    uint64_t sentinel = 0xFFFFFFFFFFFFFFFFULL;
    net_send_all(fd, &sentinel, 8);

    free(read_buf);
    free(result_buf);
    if (pattern) free(pattern);
    if (mask)    free(mask);

    net_send_int32(fd, CMD_SUCCESS);
    return 0;
}

int proc_scan_count_handle(int fd, struct cmd_packet *packet) {
    if (!(g_proc_auth_state & 2)) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    struct cmd_proc_scan_count_packet *cp = (struct cmd_proc_scan_count_packet *)packet->data;
    if (!cp) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }
    if (cp->compareType > 12) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    int needs_value_flag    = g_cmptype_needs_value   [cp->compareType] != 0;
    int needs_extra_flag    = g_cmptype_needs_extra   [cp->compareType] != 0;
    int needs_previous_flag = g_cmptype_needs_previous[cp->compareType] != 0;
    int needs_scan_value    = needs_value_flag || needs_extra_flag;
    int is_arrbytes         = (cp->valueType == 10);
    int is_between          = (cp->compareType == 4);

    uint64_t value_length = proc_scan_getSizeOfValueType(cp->valueType);
    if (value_length == 0) value_length = cp->lenData;
    if (value_length == 0 || value_length > 0x1000) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    uint8_t *pattern = NULL;
    uint8_t *mask    = NULL;
    if (needs_scan_value && cp->lenData) {
        pattern = (uint8_t *)net_alloc_buffer(cp->lenData);
        if (!pattern) {
            net_send_int32(fd, CMD_DATA_NULL);
            return 1;
        }
    }
    if (is_arrbytes) {
        mask = (uint8_t *)net_alloc_buffer((uint32_t)value_length);
        if (!mask) {
            if (pattern) free(pattern);
            net_send_int32(fd, CMD_DATA_NULL);
            return 1;
        }
    }

    uint8_t *chunk_buf  = (uint8_t *)net_alloc_buffer(0x40000);
    uint8_t *result_buf = (uint8_t *)net_alloc_buffer(0x40000);
    uint8_t *mem_buf    = (uint8_t *)net_alloc_buffer(0x100000);
    if (!chunk_buf || !result_buf || !mem_buf) {
        if (chunk_buf)  free(chunk_buf);
        if (result_buf) free(result_buf);
        if (mem_buf)    free(mem_buf);
        if (pattern)    free(pattern);
        if (mask)       free(mask);
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    net_send_int32(fd, CMD_SUCCESS);
    if (pattern) net_recv_all(fd, pattern, (int)cp->lenData, 1);
    if (mask)    net_recv_all(fd, mask,    (int)value_length, 1);

    const uint8_t *between_hi = (is_between && pattern && cp->lenData >= 2 * value_length)
                                ? (pattern + value_length) : NULL;

    int      includes_prev = needs_previous_flag;
    uint64_t entry_size    = includes_prev ? (4 + value_length) : 4;
    uint64_t flush_thresh  = 0x3FFE8ULL - 2 * value_length;

    for (;;) {
        uint32_t chunk_len = 0;
        net_recv_all(fd, &chunk_len, 4, 1);
        if (chunk_len == 0xFFFFFFFFu) break;
        if (chunk_len == 0) {
            uint64_t per_chunk_sentinel = 0xFFFFFFFFFFFFFFFFULL;
            net_send_all(fd, &per_chunk_sentinel, 8);
            continue;
        }
        if (chunk_len > 0x40000u) break;

        net_recv_all(fd, chunk_buf, (int)chunk_len, 1);

        uint64_t last_entry_addr = 0;
        if (chunk_len >= entry_size) {
            uint32_t last_offset;
            memcpy(&last_offset, chunk_buf + chunk_len - entry_size, 4);
            last_entry_addr = cp->base_address + last_offset;
        }

        uint64_t window_start = 0;
        uint64_t window_end   = 0;
        uint64_t result_len   = 0;

        for (uint64_t off = 0; off + entry_size <= chunk_len; off += entry_size) {
            uint32_t entry_offset;
            memcpy(&entry_offset, chunk_buf + off, 4);
            const uint8_t *prev_value_ptr = includes_prev ? (chunk_buf + off + 4) : between_hi;
            uint64_t addr = cp->base_address + entry_offset;

            if (addr >= window_end) {

                uint64_t span = (last_entry_addr >= addr)
                              ? (last_entry_addr + value_length) - addr
                              : value_length;
                uint32_t read_size = (span > 0x100000ULL) ? 0x100000u : (uint32_t)span;

                memset(mem_buf, 0, read_size);
                proc_read_mem(cp->pid, addr, (uint64_t)read_size, mem_buf);
                window_start = addr;
                window_end   = addr + read_size;
            }

            const uint8_t *mem_value_ptr = mem_buf + (addr - window_start);

            if (proc_scan_compareValues(cp->compareType, cp->valueType, value_length,
                                        pattern, mem_value_ptr, prev_value_ptr, mask)) {
                if (result_len > flush_thresh) {
                    *(uint64_t *)result_buf = result_len;
                    net_send_all(fd, result_buf, (int)(result_len + 8));
                    result_len = 0;
                }
                memcpy(result_buf + 8 + result_len,     &entry_offset, 4);
                memcpy(result_buf + 8 + result_len + 4, mem_value_ptr, value_length);
                result_len += 4 + value_length;
            }
        }

        if (result_len) {
            *(uint64_t *)result_buf = result_len;
            net_send_all(fd, result_buf, (int)(result_len + 8));
        }

        uint64_t per_chunk_sentinel = 0xFFFFFFFFFFFFFFFFULL;
        net_send_all(fd, &per_chunk_sentinel, 8);
    }

    free(chunk_buf);
    free(result_buf);
    free(mem_buf);
    if (pattern) free(pattern);
    if (mask)    free(mask);

    net_send_int32(fd, CMD_SUCCESS);
    return 0;
}

int proc_scan_get_handle(int fd, struct cmd_packet *packet) {
    if (!(g_proc_auth_state & 2)) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    struct cmd_proc_scan_get_packet *gp = (struct cmd_proc_scan_get_packet *)packet->data;
    if (!gp) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    uint32_t entries_len = gp->count * 12u;
    uint8_t *entries = (uint8_t *)net_alloc_buffer(entries_len);
    if (!entries) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    net_send_int32(fd, CMD_SUCCESS);
    net_recv_all(fd, entries, (int)entries_len, 1);

    uint8_t *buf = (uint8_t *)net_alloc_buffer(0x100000);
    if (!buf) {
        free(entries);
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    for (uint32_t i = 0; i < gp->count; i++) {
        uint64_t addr;
        uint32_t length32;
        memcpy(&addr,     entries + 12u * i,        8);
        memcpy(&length32, entries + 12u * i + 8u,   4);
        uint64_t length = length32;
        while (length > 0) {
            uint64_t to_read = (length > 0x100000ULL) ? 0x100000ULL : length;
            memset(buf, 0, to_read);
            proc_read_mem(gp->pid, addr, to_read, buf);
            net_send_all(fd, buf, (int)to_read);
            addr   += to_read;
            length -= to_read;
        }
    }

    uint64_t sentinel = 0xFFFFFFFFFFFFFFFFULL;
    net_send_all(fd, &sentinel, 8);

    free(entries);
    free(buf);
    return 0;
}

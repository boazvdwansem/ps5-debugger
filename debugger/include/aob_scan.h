// SPDX-License-Identifier: GPL-3.0-only
//
// Region-parallel multi-pattern AOB scan core (host-testable; no PS4 deps).
//
// The address range is split into N contiguous, ascending, disjoint sub-ranges - one per
// worker thread. Each worker scans its OWN sub-range for ALL patterns once (so every byte is
// read once total), using a shared read-only first-byte dispatch table to skip patterns that
// cannot match at a position. A worker reads (max_plen-1) bytes past its sub-range end so a
// pattern straddling the boundary is still fully evaluated by the worker that owns its start
// position - no double counting, no missed boundary matches.
//
// Because the region is split, a single pattern's matches are spread across workers, so
// uniqueness / target_count cannot be decided by any one worker. Each worker records, per
// pattern, only its earliest (target_count+1) match addresses (ascending). aob_merge() then
// concatenates the workers in ascending range order (worker 0 = lowest addresses) - which IS
// the global ascending match order - and applies the exact sequential rule:
//   matches <  target_count            -> 0 (not found)
//   matches == target_count            -> address of the target_count-th match
//   matches >= target_count+1, unique  -> 0 (invalidated)
//   matches >= target_count+1, !unique -> address of the target_count-th match
// (target_count+1) recorded per worker is sufficient: the global target_count-th match and the
// existence of a (target_count+1)-th are both decided within the first target_count+1 of the
// concatenation, and each worker contributes its earliest target_count+1.

#ifndef _AOB_SCAN_H
#define _AOB_SCAN_H

#include <stdint.h>
#include <stdbool.h>

// memset/memcpy are provided by the including TU (ps4.h on the payload, <string.h> in the test).

struct aob_pat {
    const uint8_t *pat;
    const uint8_t *msk;       // mask byte 1 = must match, 0 = wildcard
    uint32_t plen;
    uint8_t  target_count;    // 1-based occurrence to return
    int16_t  first_fixed;     // pat[0] if msk[0]==1, else -1 (wildcard first byte)
};

// First-byte dispatch over all patterns; built once, read-only during the scan.
struct aob_dispatch {
    uint32_t boff[257];       // bucket offsets into bidx; bucket b = [boff[b], boff[b+1])
    uint32_t *bidx;           // pattern indices grouped by fixed first byte
    uint32_t *widx;           // patterns whose first byte is a wildcard (checked every position)
    uint32_t wild_count;
};

// fill(ctx, off, buf, len): copy `len` target bytes at region offset `off` into buf.
typedef void (*aob_fill_fn)(void *ctx, uint64_t off, uint8_t *buf, uint32_t len);

struct aob_worker {
    // shared, read-only
    const struct aob_pat *pats;
    uint32_t pat_count;
    const struct aob_dispatch *disp;
    uint64_t base_addr;       // absolute address of region offset 0
    uint64_t length;          // total region length
    uint32_t max_plen;        // max plen over ALL patterns
    uint32_t chunkSize;       // start positions processed per read
    aob_fill_fn fill;
    void *fill_ctx;
    const uint32_t *slot_off; // [pat_count+1]; prefix sum of (target_count+1)

    // this worker's start-position offset range [start, end)
    uint64_t start;
    uint64_t end;

    // private (per worker)
    uint8_t  *readBuf;        // chunkSize + max_plen bytes
    uint64_t *addrs;          // slot_off[pat_count] entries; ascending matches per pattern
    uint32_t *counts;         // pat_count; capped at target_count+1
    uint8_t  *done;           // pat_count; 1 once counts[pi] hit its cap
};

// Build the first-byte dispatch. bidx_storage/widx_storage must each hold pat_count entries.
static inline void aob_build_dispatch(const struct aob_pat *pats, uint32_t pat_count,
                                      struct aob_dispatch *d,
                                      uint32_t *bidx_storage, uint32_t *widx_storage) {
    uint32_t bcount[256];
    memset(bcount, 0, sizeof(bcount));
    for (uint32_t pi = 0; pi < pat_count; pi++) {
        if (pats[pi].first_fixed >= 0) bcount[(uint8_t)pats[pi].first_fixed]++;
    }
    d->boff[0] = 0;
    for (uint32_t b = 0; b < 256; b++) d->boff[b + 1] = d->boff[b] + bcount[b];

    uint32_t cursor[256];
    memcpy(cursor, d->boff, 256 * sizeof(uint32_t));
    d->bidx = bidx_storage;
    d->widx = widx_storage;
    d->wild_count = 0;
    for (uint32_t pi = 0; pi < pat_count; pi++) {
        if (pats[pi].first_fixed >= 0) {
            d->bidx[cursor[(uint8_t)pats[pi].first_fixed]++] = pi;
        } else {
            d->widx[d->wild_count++] = pi;
        }
    }
}

static inline bool aob_match_at(const uint8_t *buf, uint32_t pos, const struct aob_pat *p) {
    const uint8_t *pat = p->pat, *msk = p->msk;
    for (uint32_t k = 0; k < p->plen; k++) {
        if (buf[pos + k] != pat[k] && msk[k] == 1) return false;
    }
    return true;
}

// Test one pattern at a buffer position; record the match if it fits and we still need it.
// Returns 1 if this call made the pattern reach its cap (so the caller can drop it), else 0.
static inline int aob_try(struct aob_worker *w, uint32_t pi, uint32_t jj,
                          uint32_t read_len, uint64_t goff, uint64_t abs_addr) {
    if (w->done[pi]) return 0;
    const struct aob_pat *p = &w->pats[pi];
    if (goff + p->plen > w->length) return 0;     // pattern would run past the region
    if (jj + p->plen > read_len) return 0;        // not enough bytes read (region-end safety)
    if (!aob_match_at(w->readBuf, jj, p)) return 0;

    uint32_t cap = (uint32_t)p->target_count + 1;
    uint32_t c = w->counts[pi];
    if (c < cap) {
        w->addrs[w->slot_off[pi] + c] = abs_addr;
        w->counts[pi] = ++c;
        if (c == cap) { w->done[pi] = 1; return 1; }
    }
    return 0;
}

// Scan this worker's sub-range. Writes counts[]/addrs[]/done[]; safe to run on its own thread.
static inline void aob_scan_range(struct aob_worker *w) {
    for (uint32_t pi = 0; pi < w->pat_count; pi++) { w->counts[pi] = 0; w->done[pi] = 0; }
    uint32_t active = w->pat_count;

    const struct aob_dispatch *d = w->disp;
    uint64_t off = w->start;
    while (off < w->end && active > 0) {
        uint64_t own64 = w->end - off;
        uint32_t own = (own64 < w->chunkSize) ? (uint32_t)own64 : w->chunkSize; // owned start positions
        uint64_t avail = w->length - off;
        uint32_t read_len = own + w->max_plen - 1;
        if ((uint64_t)read_len > avail) read_len = (uint32_t)avail;

        memset(w->readBuf, 0, read_len);
        w->fill(w->fill_ctx, off, w->readBuf, read_len);

        for (uint32_t jj = 0; jj < own; jj++) {
            uint8_t b = w->readBuf[jj];
            uint64_t goff = off + jj;
            uint64_t abs_addr = w->base_addr + goff;
            for (uint32_t t = d->boff[b]; t < d->boff[b + 1]; t++) {
                active -= (uint32_t)aob_try(w, d->bidx[t], jj, read_len, goff, abs_addr);
            }
            for (uint32_t t = 0; t < d->wild_count; t++) {
                active -= (uint32_t)aob_try(w, d->widx[t], jj, read_len, goff, abs_addr);
            }
            if (active == 0) break;
        }
        off += own;
    }
}

// Merge the workers' per-pattern results into output[pat_count] (absolute address or 0).
// worker_addrs[wkr]/worker_counts[wkr] are worker wkr's arrays; workers MUST be in ascending
// range order (worker 0 owns the lowest addresses).
static inline void aob_merge(const struct aob_pat *pats, uint32_t pat_count, bool stop_unique,
                             const uint32_t *slot_off, uint32_t num_workers,
                             const uint64_t *const *worker_addrs,
                             const uint32_t *const *worker_counts,
                             uint64_t *output) {
    for (uint32_t pi = 0; pi < pat_count; pi++) {
        uint32_t tc = pats[pi].target_count;
        uint64_t cap = (uint64_t)tc + 1;
        uint64_t total = 0;
        uint64_t result = 0;
        bool found = false;
        for (uint32_t wkr = 0; wkr < num_workers; wkr++) {
            uint32_t c = worker_counts[wkr][pi];
            if (!found && tc >= 1 && total < tc && total + c >= tc) {
                uint32_t local_idx = (uint32_t)(tc - total - 1);   // 0-based within this worker
                result = worker_addrs[wkr][slot_off[pi] + local_idx];
                found = true;
            }
            total += c;
            if (total > cap) total = cap;
        }
        if (!found) output[pi] = 0;
        else if (stop_unique && total >= (uint64_t)tc + 1) output[pi] = 0;
        else output[pi] = result;
    }
}

#endif

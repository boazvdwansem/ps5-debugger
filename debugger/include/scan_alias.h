// SPDX-License-Identifier: GPL-3.0-only
//
// Phase 3 aliasing read engine (scan_alias.c). Maps a target process's physical
// pages into OUR address space by writing not-present leaf PTEs in our own pmap,
// so the turbo-scan compare runs in place at DRAM bandwidth instead of copying via
// mdbg/DMAP. PANIC-CAPABLE: a wrong leaf-PTE write corrupts page tables. Every
// write is gated (B1 leaf level==3, B2 leaf not-present, B3 leaf kaddr in
// [dmap, dmap+0x10_0000_0000)); on ANY guard trip / verify mismatch the map is
// abandoned and the caller falls back to proc_read_mem. Restore-then-leak: written
// PTEs are zeroed on release; the window VA is NEVER munmap'd (munmap/pmap_remove
// would walk our PTEs and free the GAME's phys pages).
//
// Built on the read-only accessors in proc_ptwalk.c (proc_ptwalk_leaf_addr /
// _probe / _dmap_base) and the kernel R/W primitive kernel_copyin_fast/_out_fast.
// Mechanism HW-proven in benchmark.c "R4C". Gated behind TSE_ALIASING + the
// per-request TS_USE_ALIASING flag (client opt-in, default OFF); mdbg is always
// the fallback.

#pragma once
#include <stdint.h>
#include <stddef.h>

typedef struct scan_alias_ctx scan_alias_ctx;

// Begin an aliasing context for `pid` with a VA arena of `arena_cap` bytes (0 =>
// default 256 MB). Returns NULL if aliasing is unavailable (no DMAP base / mmap
// fail) -> caller must read via proc_read_mem. The arena is leaked at end().
scan_alias_ctx *scan_alias_begin(uint32_t pid, uint64_t arena_cap);

// Make target bytes [tgt, tgt+len) readable in our address space. On success
// returns a CPU pointer p such that p[0..len) == target[tgt..tgt+len), AFTER
// verifying sampled pages byte-identically against proc_read_mem. Returns NULL on
// any failure (unavailable, exhaustion, guard trip, uncached/PCD page, verify
// mismatch) -> caller reads this range via proc_read_mem. *out_mapped_len (if
// non-NULL) gets `len` on success. One active map at a time: call
// scan_alias_release() before the next map.
const void *scan_alias_map(scan_alias_ctx *c, uint64_t tgt, uint64_t len,
                           uint64_t *out_mapped_len);

// Restore the active map's leaf PTEs to 0 (not-present) and clear it. Safe to call
// with no active map. The window VA is left leaked (never munmap'd) and never
// reused (advancing VA needs no TLB flush).
void scan_alias_release(scan_alias_ctx *c);

// Release any active map, leak the arena VA, free the context struct.
void scan_alias_end(scan_alias_ctx *c);

// Reuse an existing context for a new scan (Phase 3 #1: per-connection arena reuse).
// Releases any active map, rebinds to `pid`, and invalidates the huge-page derivation
// cache (it is pid-specific). The arena + advancing cursor are PRESERVED so repeated
// scans on one connection share VA instead of leaking a fresh arena each time.
void scan_alias_rebind(scan_alias_ctx *c, uint32_t pid);

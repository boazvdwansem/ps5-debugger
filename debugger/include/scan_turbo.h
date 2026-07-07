// SPDX-License-Identifier: GPL-3.0-only
//
// Turbo-scan support (Phase 1): typed/SIMD comparators + region filtering for the
// new TURBOSCAN command family (0xBDAACC10-0xBDAACC1F). Additive - the legacy and
// async scan paths are untouched.

#pragma once
#include <stdint.h>
#include <stddef.h>

// valtype encoding (matches proc_scan_getSizeOfValueType): 0=u8 1=i8 2=u16 3=i16
// 4=u32 5=i32 6=u64 7=i64 8=f32 9=f64.

// Find every element in [buf, buf+len) that EXACTLY equals *value (valtype-typed),
// stepping by `step` bytes (step >= element size; typically == element size for an
// aligned scan). Writes matching byte-offsets (relative to buf) into out_off,
// up to max_out. Returns the TOTAL match count (may exceed max_out; caller detects
// truncation when return > max_out). SIMD-accelerated for u32/i32 aligned scans;
// typed-scalar for the rest. Replaces the per-element double-switch on the turbo
// path only.
size_t scan_simd_find_exact(unsigned char valtype, const uint8_t *buf, size_t len,
                            const void *value, uint32_t step,
                            uint32_t *out_off, size_t max_out);

// --- Typed point comparator (rescan turbo path) -------------------------------
// proc_scan_compareValues is a non-inlined cmpType-then-valType double-switch
// (~0.8 GB/s). The rescan path (TURBOSCAN_COUNT) tests one candidate at a time, so
// it can't use the buffer-oriented scan_simd_find_exact; instead it selects ONCE
// per scan (cmpType+valType are loop-invariant) whether the typed point comparator
// applies, then calls scan_point_compare per candidate.
//
// scan_point_turbo_supported() returns nonzero for the (cmpType,valType) pairs the
// typed comparator handles byte-identically to proc_scan_compareValues:
//   - cmpType 0 (exact) for integer valtypes 0..7 only (float/double exact uses the
//     fuzzy path; arrbytes uses the mask path -> both fall back);
//   - cmpType 5/6/7/8/9/10 (increased / increased-by / decreased / decreased-by /
//     changed / unchanged - the common rescan delta types) for all numeric types.
// Everything else (1 within-1.0, 2/3 >/< value, 4 between, 11 nonzero, 12, arrbytes,
// float/double exact) returns 0 -> caller must use proc_scan_compareValues.
int scan_point_turbo_supported(unsigned char cmpType, unsigned char valType);

// Typed point comparison, byte-identical to proc_scan_compareValues for every
// (cmpType,valType) pair scan_point_turbo_supported() accepts. `mem` = current
// memory value; `scan` = the user's scan value (the "by" amount for 6/8); `prev` =
// the candidate's previous value (required for cmpType 5..10; unused for 0).
// Precondition: scan_point_turbo_supported(cmpType,valType) != 0. Returns 0/1.
int scan_point_compare(unsigned char cmpType, unsigned char valType,
                       const void *mem, const void *scan, const void *prev);

// A readable address range selected for scanning.
struct scan_range { uint64_t start; uint64_t end; };

// Region-exclusion mode for scan_turbo_regions().
#define SCAN_EXCLUDE_NONE  0   // all readable regions
#define SCAN_EXCLUDE_PCD   1   // drop leaf-PTE PCD=1 (cheap; misses WB-mapped slow mem)
#define SCAN_EXCLUDE_SPEED 2   // drop regions reading < min_mbps (robust; costs a probe/region)

// Enumerate the target's readable regions into out[0..max). `mode` selects the
// exclusion strategy (see above); `min_mbps` applies to SCAN_EXCLUDE_SPEED (e.g.
// 100). Returns the number of ranges; *out_total_bytes (if non-NULL) gets their
// summed size. Probe failure -> region is INCLUDED (conservative).
int scan_turbo_regions(uint32_t pid, int mode, uint32_t min_mbps,
                      struct scan_range *out, int max, uint64_t *out_total_bytes);

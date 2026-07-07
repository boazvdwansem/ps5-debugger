// SPDX-License-Identifier: GPL-3.0-only
#pragma once
#include <stdint.h>

struct proc_field_offsets {
    uint32_t name;
    uint32_t path;
    uint32_t titleid;
    uint32_t contentid;
    int      known;
};

int proc_get_field_offsets(struct proc_field_offsets *out);

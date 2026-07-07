// SPDX-License-Identifier: GPL-3.0-only

#ifndef PORT_FLAT_DEBUG_STATE_H
#define PORT_FLAT_DEBUG_STATE_H

#include <stdint.h>

extern uint32_t g_stopgo_mode;
extern uint32_t g_stopgo_target_pid;
extern uint32_t g_stopgo_last_signal;
extern uint32_t g_stopgo_resume_pid;
extern uint32_t g_stopgo_resume_signal;

#endif

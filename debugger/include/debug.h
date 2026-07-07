// SPDX-License-Identifier: GPL-3.0-only

#pragma once
#include <stdint.h>

struct cmd_packet;

int debug_handle(int fd, struct cmd_packet *packet);

int debug_attach_handle(int fd, struct cmd_packet *packet);
int debug_detach_handle(int fd, struct cmd_packet *packet);
int debug_set_breakpoint_handle(int fd, struct cmd_packet *packet);
int debug_set_watchpoint_handle(int fd, struct cmd_packet *packet);
int debug_get_thread_list_handle(int fd, struct cmd_packet *packet);
int debug_suspend_thread_handle(int fd, struct cmd_packet *packet);
int debug_resume_thread_handle(int fd, struct cmd_packet *packet);
int debug_getregs_handle(int fd, struct cmd_packet *packet);
int debug_setregs_handle(int fd, struct cmd_packet *packet);
int debug_getfpregs_handle(int fd, struct cmd_packet *packet);
int debug_setfpregs_handle(int fd, struct cmd_packet *packet);
int debug_getdbregs_handle(int fd, struct cmd_packet *packet);
int debug_setdbregs_handle(int fd, struct cmd_packet *packet);
int debug_continue_handle(int fd, struct cmd_packet *packet);
int debug_thread_info_handle(int fd, struct cmd_packet *packet);
int debug_step_handle(int fd, struct cmd_packet *packet);
int debug_step_thread_handle(int fd, struct cmd_packet *packet);
int debug_process_stop_handle(int fd, struct cmd_packet *packet);

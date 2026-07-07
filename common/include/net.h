// SPDX-License-Identifier: GPL-3.0-only

#pragma once
#include <stdint.h>

#define NET_MAX_LENGTH  8192

int  net_send_int32(int fd, uint32_t value);
int  net_send_all(int fd, const void *buf, int len);
int  net_recv_all(int fd, void *buf, int len, int force);
int  net_select(int fd, void *readfds, void *writefds, void *exceptfds, void *timeout);
int  net_get_ip_address(char *out);
void *net_alloc_buffer(uint32_t size);

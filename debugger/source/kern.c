// SPDX-License-Identifier: GPL-3.0-only

#include "protocol.h"
#include "sdk_shim.h"
#include "net.h"
#include "kern_rw_fast.h"

int kern_base_handle(int fd, struct cmd_packet *packet) {
    (void)packet;
    uint64_t kbase = KERNEL_ADDRESS_DATA_BASE;
    net_send_int32(fd, CMD_SUCCESS);
    net_send_all(fd, &kbase, 8);
    return 0;
}

int kern_read_handle(int fd, struct cmd_packet *packet) {
    struct cmd_kern_read_packet *p = (struct cmd_kern_read_packet *)packet->data;
    if (!p) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 0;
    }
    void *buf = net_alloc_buffer(p->length);
    if (!buf) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }
    kernel_copyout_fast(p->address, buf, p->length);
    net_send_int32(fd, CMD_SUCCESS);
    net_send_all(fd, buf, p->length);
    free(buf);
    return 0;
}

int kern_write_handle(int fd, struct cmd_packet *packet) {
    struct cmd_kern_write_packet *p = (struct cmd_kern_write_packet *)packet->data;
    if (!p) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 0;
    }
    void *buf = net_alloc_buffer(p->length);
    if (!buf) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }
    net_send_int32(fd, CMD_SUCCESS);
    net_recv_all(fd, buf, p->length, 1);
    kernel_copyin_fast(buf, p->address, p->length);
    net_send_int32(fd, CMD_SUCCESS);
    free(buf);
    return 0;
}

int kern_handle(int fd, struct cmd_packet *packet) {
    switch (packet->cmd) {
        case 0xBDCC0001u: return kern_base_handle(fd, packet);
        case 0xBDCC0002u: return kern_read_handle(fd, packet);
        case 0xBDCC0003u: return kern_write_handle(fd, packet);
        default:      return 1;
    }
}

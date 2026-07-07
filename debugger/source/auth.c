// SPDX-License-Identifier: GPL-3.0-only

#include "protocol.h"
#include "sdk_shim.h"
#include "net.h"
#include "proc.h"

static uint32_t s_lfsr_s1;
static uint32_t s_lfsr_s2;
static uint32_t s_lfsr_s3;
static uint32_t s_lfsr_s4;

uint32_t g_proc_auth_state = 0x14000u;

uint32_t auth_lfsr_next(void) {
    uint32_t s1 = s_lfsr_s1;
    uint32_t s2 = s_lfsr_s2;
    uint32_t s3 = s_lfsr_s3;
    uint32_t s4 = s_lfsr_s4;

    s1 = ((s1 << 18) & 0xFFF80000u) ^ ((s1 ^ (s1 << 6))  >> 13);
    s2 = ((s2 <<  2) & 0xFFFFFFE0u) ^ ((s2 ^ (s2 << 2))  >> 27);
    s3 = ((s3 <<  7) & 0xFFFFF800u) ^ ((s3 ^ (s3 << 13)) >> 21);
    s4 = ((s4 << 13) & 0xFFF00000u) ^ ((s4 ^ (s4 << 3))  >> 12);

    s_lfsr_s1 = s1;
    s_lfsr_s2 = s2;
    s_lfsr_s3 = s3;
    s_lfsr_s4 = s4;
    return s1 ^ s2 ^ s3 ^ s4;
}

void auth_lfsr_set_state(uint32_t a, uint32_t b, uint32_t c, uint32_t d) {
    s_lfsr_s1 = a;
    s_lfsr_s2 = b;
    s_lfsr_s3 = c;
    s_lfsr_s4 = d;
}

void auth_keystream_fill(uint8_t *out, uint64_t length) {
    (void)length;
    auth_lfsr_set_state(200, 300, 400, 500);
    for (int i = 0; i < 256; i++) {
        out[i] = (uint8_t)auth_lfsr_next();
    }
}

void proc_auth_xor_keystream(const uint8_t *in, uint8_t *out, uint16_t length) {
    uint8_t keystream[256];
    auth_keystream_fill(keystream, 0x100);
    for (uint16_t i = 0; i < length; i++) {
        out[i] = in[i] ^ keystream[(uint8_t)i];
    }
}

static inline uint32_t bitswap32(uint32_t x) {
    return ((x >> 1) & 0x55555555u) | ((x << 1) & 0xAAAAAAAAu);
}

int proc_auth_handle(int fd, struct cmd_packet *packet) {
    struct cmd_proc_auth_packet *ap = (struct cmd_proc_auth_packet *)packet->data;

    if (!ap || bitswap32(ap->magic) != CMD_PROC_AUTH_MAGIC_BSWAP) {
        net_send_int32(fd, CMD_DATA_NULL);
        return 1;
    }

    net_send_int32(fd, CMD_SUCCESS);

    uint16_t chlen = 64;
    uint8_t challenge[64];
    uint8_t expected[64];
    uint8_t response[64];

    for (uint16_t i = 0; i < chlen; i++) {
        uint32_t r = auth_lfsr_next();
        challenge[i] = (uint8_t)(r + r / 255u);
    }

    proc_auth_xor_keystream(challenge, expected, chlen);

    net_send_all(fd, &chlen, sizeof(chlen));
    net_send_all(fd, challenge, chlen);
    net_recv_all(fd, response, chlen, 1);

    for (uint16_t i = 0; i < chlen; i++) {
        if (response[i] != expected[i]) {
            net_send_int32(fd, CMD_DATA_NULL);
            return 1;
        }
    }

    if (ap->flags & 1) g_proc_auth_state = 0x14001u;
    if (ap->flags & 2) g_proc_auth_state = 0x14002u;

    net_send_int32(fd, CMD_SUCCESS);
    return 0;
}

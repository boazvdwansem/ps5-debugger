// SPDX-License-Identifier: GPL-3.0-only

#include "sdk_shim.h"

int hijack_mode(void)
{
    return kernel_dynlib_dlsym(-1, 0x2001, "sceKernelDlsym") != 0;
}

int __wrap___patch_init(void)
{
    if (hijack_mode()) {

        return 0;
    }

    int pid = getpid();

    {
        unsigned char caps[16];
        unsigned long attrs;

        if (kernel_get_ucred_caps(pid, caps)) {
            klog_puts("kernel_get_ucred_caps failed");
            return -1;
        }
        if (!(attrs = kernel_get_ucred_attrs(pid))) {
            klog_puts("kernel_get_ucred_attrs failed");
            return -1;
        }

        caps[5]   = 0x1c;
        caps[7]   = 0x40;
        caps[15] |= 0x40;
        attrs    |= 0x80;

        if (kernel_set_ucred_caps(pid, caps)) {
            klog_puts("kernel_set_ucred_caps failed");
            return -1;
        }
        if (kernel_set_ucred_attrs(pid, attrs)) {
            klog_puts("kernel_set_ucred_attrs failed");
            return -1;
        }
    }

    {
        unsigned long kproc;
        unsigned long kaddr;
        unsigned long uaddr;

        if (!(kproc = kernel_get_proc(pid))) {
            return -1;
        }

        if (kernel_copyout(kproc + 0x3e8, &kaddr, sizeof(kaddr)) < 0) {
            return -1;
        }

        uaddr = 0;
        if (kernel_copyin(&uaddr, kaddr + 0xf0, sizeof(uaddr)) < 0) {
            return -1;
        }

        uaddr = (unsigned long)-1L;
        if (kernel_copyin(&uaddr, kaddr + 0xf8, sizeof(uaddr)) < 0) {
            return -1;
        }
    }

    return 0;
}

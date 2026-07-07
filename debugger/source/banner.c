// SPDX-License-Identifier: GPL-3.0-only

#include "sdk_shim.h"

void print_ascii_banner(void) {
    sceKernelDebugOutText(0, "     _      _                 ____        _   _  ____ \n");
    sceKernelDebugOutText(0, "  __| | ___| |__  _   _  __ _| ___|      | \\ | |/ ___|\n");
    sceKernelDebugOutText(0, " / _` |/ _ \\ '_ \\| | | |/ _` |___ \\ _____|  \\| | |  _ \n");
    sceKernelDebugOutText(0, "| (_| |  __/ |_) | |_| | (_| |___) |_____| |\\  | |_| |\n");
    sceKernelDebugOutText(0, " \\__,_|\\___|_.__/ \\__,_|\\__, |____/      |_| \\_|\\____|\n");
    sceKernelDebugOutText(0, "                        |___/                         \n");
    sceKernelDebugOutText(0, "               Coded by OpenSourcereR.\n\n");
}

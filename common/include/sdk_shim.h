// SPDX-License-Identifier: GPL-3.0-only

#pragma once

#include <ps5/kernel.h>
#include <ps5/klog.h>
#include <ps5/mdbg.h>
#include <ps5/payload.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <time.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <signal.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/sysctl.h>
#include <sys/wait.h>
#include <sys/types.h>
#include <sys/syscall.h>
#include <sys/ptrace.h>

extern int  sceNetSocket(const char *name, int family, int type, int protocol);
extern int  sceNetSocketClose(int fd);
extern int  sceNetSocketAbort(int fd, int how);
extern int  sceNetBind(int fd, void *addr, int addrlen);
extern int  sceNetListen(int fd, int backlog);
extern int  sceNetAccept(int fd, void *addr, void *addrlen);
extern int  sceNetConnect(int fd, void *addr, int addrlen);
extern int  sceNetSend(int fd, const void *buf, size_t len, int flags);
extern int  sceNetRecv(int fd, void *buf, size_t len, int flags);
extern int  sceNetSendto(int fd, const void *buf, size_t len, int flags, void *addr, int addrlen);
extern int  sceNetRecvfrom(int fd, void *buf, size_t len, int flags, void *addr, void *addrlen);
extern int  sceNetGetsockname(int fd, void *addr, void *addrlen);
extern int  sceNetGetsockopt(int fd, int level, int optname, void *optval, void *optlen);
extern int  sceNetSetsockopt(int fd, int level, int optname, const void *optval, int optlen);
extern unsigned short sceNetHtons(unsigned short v);
extern unsigned int   sceNetHtonl(unsigned int v);
extern unsigned long  sceNetHtonll(unsigned long v);
extern unsigned short sceNetNtohs(unsigned short v);
extern unsigned int   sceNetNtohl(unsigned int v);
extern unsigned long  sceNetNtohll(unsigned long v);
extern const char *sceNetInetNtop(int af, const void *src, char *dst, int size);
extern int  sceNetInetPton(int af, const char *src, void *dst);
extern int *sceNetErrnoLoc(void);

extern int  sceNetCtlInit(void);
extern void sceNetCtlTerm(void);
extern int  sceNetCtlGetInfo(int code, void *info);

extern int  sceSysmoduleLoadModuleInternal(int handle);

typedef void *ScePthread;
extern int  scePthreadCreate(ScePthread *thr, const void *attr, void *(*entry)(void *), void *arg, const char *name);
extern int  scePthreadDetach(ScePthread thr);
extern int  scePthreadJoin(ScePthread thr, void **ret);
extern int  scePthreadCancel(ScePthread thr);
extern void scePthreadExit(void *ret);
extern int  scePthreadYield(void);
extern ScePthread scePthreadSelf(void);
extern int  scePthreadSetaffinity(ScePthread thr, unsigned long mask);
extern int  scePthreadMutexInit(void **mtx, const void *attr, const char *name);
extern int  scePthreadMutexDestroy(void **mtx);
extern int  scePthreadMutexLock(void **mtx);
extern int  scePthreadMutexTrylock(void **mtx);
extern int  scePthreadMutexTimedlock(void **mtx, unsigned int usec);
extern int  scePthreadMutexUnlock(void **mtx);

extern unsigned int sceKernelSleep(unsigned int sec);
extern int  sceKernelUsleep(unsigned int usec);
extern int  sceKernelReboot(void);
extern int  sceKernelLoadStartModule(const char *path, size_t argc, void *argv,
                                      unsigned int flags, void *opt, int *res);
extern int  sceKernelGetModuleList(void *out, size_t max, size_t *count);
extern int  sceKernelGetModuleListInternal(void *out, size_t max, size_t *count);
extern int  sceKernelGetModuleInfo(int handle, void *info);
extern int  sceKernelSendNotificationRequest(int unused, void *req, size_t reqsz, int flag);
extern int  sceKernelDebugOutText(int fd, const char *msg);

extern int  sceApplicationContinue(int app_id);
extern int  _sceApplicationGetAppId(int pid, int *app_id_out);

extern int *__error(void);
#ifndef errno
#define errno (*__error())
#endif

extern unsigned long __stack_chk_guard;
extern void __stack_chk_fail(void);

#include <ps5/payload.h>
extern long __crt_syscall(long sysno, ...);
static inline long ps5debug_syscall(long sysno, long a, long b, long c, long d, long e, long f) {
    return __crt_syscall(sysno, a, b, c, d, e, f);
}

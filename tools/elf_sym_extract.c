#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr, "usage: %s <elf-file>\n", argv[0]);
        return 2;
    }
    const char *path = argv[1];
    FILE *f = fopen(path, "rb");
    if (!f) { perror("fopen"); return 2; }
    if (fseek(f, 0, SEEK_END) != 0) { perror("fseek"); fclose(f); return 2; }
    long sz = ftell(f);
    if (sz < 0) { perror("ftell"); fclose(f); return 2; }
    rewind(f);
    unsigned char *eb = malloc((size_t)sz);
    if (!eb) { fprintf(stderr, "oom\n"); fclose(f); return 2; }
    if (fread(eb, 1, (size_t)sz, f) != (size_t)sz) { perror("fread"); free(eb); fclose(f); return 2; }
    fclose(f);

    uint64_t e_shoff = 0;
    uint16_t e_shentsize = 0, e_shnum = 0, e_shstrndx = 0;
    if (sz >= 0x40) {
        e_shoff = *(uint64_t *)(eb + 0x28);
        e_shentsize = *(uint16_t *)(eb + 0x3A);
        e_shnum = *(uint16_t *)(eb + 0x3C);
        e_shstrndx = *(uint16_t *)(eb + 0x3E);
    }
    if (e_shoff == 0 || e_shnum == 0 || e_shentsize == 0 || e_shoff + (uint64_t)e_shentsize * e_shnum > (uint64_t)sz) {
        fprintf(stderr, "no section headers or invalid ELF\n"); free(eb); return 1;
    }
    const unsigned char *shdr_base = eb + e_shoff;

    /* find symtab/dynsym and reloc sections */
    uint64_t sym_off=0,sym_size=0,sym_entsize=0,sym_link=0; int sym_idx=-1;
    uint64_t dyn_off=0,dyn_size=0,dyn_entsize=0,dyn_link=0; int dyn_idx=-1;
    for (uint16_t i=0;i<e_shnum;i++){
        const unsigned char *sh = shdr_base + (uint64_t)i*e_shentsize;
        uint32_t sh_type = *(uint32_t *)(sh + 0x4);
        uint64_t sh_offset = *(uint64_t *)(sh + 0x18);
        uint64_t sh_size = *(uint64_t *)(sh + 0x20);
        uint64_t sh_entsize = *(uint64_t *)(sh + 0x38);
        uint64_t sh_link = *(uint32_t *)(sh + 0x28);
        if (sh_type == 2) { sym_off = sh_offset; sym_size = sh_size; sym_entsize = sh_entsize; sym_link = sh_link; sym_idx=i; }
        if (sh_type == 11) { dyn_off = sh_offset; dyn_size = sh_size; dyn_entsize = dyn_entsize; dyn_link = dyn_link; dyn_idx=i; }
    }
    uint64_t chosen_off=0, chosen_size=0, chosen_entsize=0, chosen_link=0;
    if (dyn_off && dyn_size && dyn_entsize) { chosen_off=dyn_off; chosen_size=dyn_size; chosen_entsize=dyn_entsize; chosen_link=dyn_link; }
    else if (sym_off && sym_size && sym_entsize) { chosen_off=sym_off; chosen_size=sym_size; chosen_entsize=sym_entsize; chosen_link=sym_link; }
    if (!chosen_off) { fprintf(stderr, "no symtab/dynsym found\n"); free(eb); return 1; }
    if (chosen_off + chosen_size > (uint64_t)sz) { fprintf(stderr, "symbol table out of range\n"); free(eb); return 1; }

    /* resolve string table */
    const char *strtab = NULL; uint64_t strtab_size = 0;
    if (chosen_link < e_shnum) {
        const unsigned char *s = shdr_base + (uint64_t)chosen_link * e_shentsize;
        uint64_t stroff = *(uint64_t *)(s + 0x18);
        uint64_t strsiz = *(uint64_t *)(s + 0x20);
        if (stroff && stroff < (uint64_t)sz && stroff + strsiz <= (uint64_t)sz) { strtab = (const char *)(eb + stroff); strtab_size = strsiz; }
    }
    if (!strtab) { fprintf(stderr, "no strtab\n"); free(eb); return 1; }

    size_t nsyms = chosen_entsize ? (chosen_size / chosen_entsize) : 0;
    printf("[");
    int first=1;
    for (size_t i=0;i<nsyms;i++){
        uint64_t off = chosen_off + i*chosen_entsize;
        if (off + 24 > (uint64_t)sz) continue;
        uint32_t st_name = *(uint32_t *)(eb + off + 0);
        uint8_t st_info = *(uint8_t *)(eb + off + 4);
        uint64_t st_value = *(uint64_t *)(eb + off + 8);
        uint64_t st_size = *(uint64_t *)(eb + off + 16);
        const char *name = (st_name < strtab_size) ? (strtab + st_name) : NULL;
        if (!name || name[0]=='\0') continue;
        if (st_value==0) continue;
        if (!first) printf(","); first=0;
        /* minimal JSON escape */
        printf("{\"addr\":%llu,\"size\":%llu,\"type\":%u,\"name\":\"",
            (unsigned long long)st_value, (unsigned long long)st_size, (unsigned)st_info & 0xFF);
        for (const char *p = name; *p; p++){
            if (*p=='"' || *p=='\\') printf("\\%c", *p);
            else if ((unsigned char)*p >= 0x20) putchar(*p);
        }
        printf("\"}");
    }
    printf("]\n");
    free(eb);
    return 0;
}

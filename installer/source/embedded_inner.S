	.section .rodata.embedded_inner,"a",@progbits
	.p2align 4
	.global embedded_inner_start
embedded_inner_start:
	.incbin "embedded_inner.elf.bin"
	.global embedded_inner_end
embedded_inner_end:

	.p2align 3
	.global embedded_inner_size
embedded_inner_size:
	.quad embedded_inner_end - embedded_inner_start

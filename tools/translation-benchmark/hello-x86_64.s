# x86-64 Linux の最小動作検査。Windows ベンチマークではない。
.global _start
.section .text
_start:
    mov $1, %rax
    mov $1, %rdi
    lea message(%rip), %rsi
    mov $message_end-message, %rdx
    syscall
    mov $60, %rax
    xor %rdi, %rdi
    syscall
.section .rodata
message: .ascii "translation-smoke-ok\n"
message_end:

# メモリの読み書きと条件分岐の結果を終了コードで検査する。
.global _start
.text
_start:
    movl $17, value(%rip)
    mov value(%rip), %edi
    add $25, %edi
    cmp $42, %edi
    jne failure
    mov $60, %eax
    syscall
failure:
    mov $99, %edi
    mov $60, %eax
    syscall
.data
value: .long 0

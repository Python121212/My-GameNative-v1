# レジスターに計算結果を入れて exit(42) を実行する。
.global _start
.text
_start:
    mov $9, %edi
    add $33, %edi
    mov $60, %eax
    syscall

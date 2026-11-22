// Perintah hashpw mencetak hash bcrypt dari sebuah kata sandi, untuk mengisi kolom app_user.password_hash saat membuat akun
// ADMIN pertama lewat SQL (lihat docs/DEPLOY.md). Memakai fungsi yang sama dengan API, sehingga hasilnya pasti cocok saat login.
//
// Kata sandi dibaca dari masukan standar (satu baris), bukan dari argumen perintah, supaya tidak tersimpan di riwayat perintah:
//
//	hashpw            (ketik kata sandi lalu Enter)
//	type sandi.txt | hashpw
package main

import (
	"bufio"
	"fmt"
	"os"
	"strings"

	"fai26-api/internal/auth"
)

func main() {
	fmt.Fprint(os.Stderr, "Kata sandi: ")
	baris, err := bufio.NewReader(os.Stdin).ReadString('\n')
	if err != nil && baris == "" {
		fmt.Fprintln(os.Stderr, "tidak ada masukan")
		os.Exit(1)
	}
	// PowerShell 5.1 dapat menyisipkan BOM UTF-8 di depan teks yang dialirkan lewat pipa; BOM itu bukan bagian kata sandi.
	sandi := strings.TrimRight(strings.TrimPrefix(baris, string(rune(0xFEFF))), "\r\n")
	if len(sandi) < 8 {
		fmt.Fprintln(os.Stderr, "kata sandi minimal 8 karakter")
		os.Exit(1)
	}
	h, err := auth.HashPassword(sandi)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	fmt.Println(h)
}

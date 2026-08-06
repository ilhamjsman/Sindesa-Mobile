# Panduan Debugging Koneksi Sindesa Mobile

Jika Anda mengalami masalah koneksi ("Waktu Habis" atau "Koneksi Ditolak") saat menggunakan aplikasi di HP fisik, ikuti langkah-langkah berikut:

## 1. Cek Windows Firewall (Paling Sering Menjadi Masalah)
Windows sering memblokir koneksi masuk ke port Laragon (80/8080).
*   Buka **Windows Defender Firewall with Advanced Security**.
*   Klik **Inbound Rules**.
*   Klik **New Rule...** (di panel kanan).
*   Pilih **Port** -> Next.
*   Pilih **TCP** dan isi Specific local ports: `80, 8080`.
*   Pilih **Allow the connection** -> Next.
*   Centang semua (Domain, Private, Public) -> Next.
*   Beri nama "Sindesa API" -> Finish.
*   **Cara Cepat:** Matikan Windows Firewall sepenuhnya untuk sementara saat testing.

## 2. Pastikan Satu Jaringan WiFi
HP dan Laptop **WAJIB** berada di jaringan WiFi yang sama. Jika menggunakan Hotspot HP, pastikan laptop terhubung ke hotspot tersebut.

## 3. Verifikasi IP Laptop
1.  Buka Command Prompt di laptop, ketik `ipconfig`.
2.  Cari **IPv4 Address** pada adapter WiFi (misal: `10.140.76.10`).
3.  Pastikan IP tersebut sama dengan yang ada di `RetrofitClient.kt` di Android Studio.
    *   File: `app/src/main/java/com/ta/sindesa/api/RetrofitClient.kt`
    *   Variabel: `const val HOSTNAME = "10.140.76.10"`

## 4. Cek Port Laragon
Pastikan Laragon sedang berjalan dan port yang digunakan adalah **8080** (atau **80**).
Jika port di Laragon adalah **80**, maka ubah `BASE_URL` di `RetrofitClient.kt`:
```kotlin
const val BASE_URL = "http://$HOSTNAME:80/sindesa_api/"
```

## 5. Melihat Log Detail (Logcat)
Jika muncul error "Respon Server Bukan JSON", berarti PHP mengirimkan pesan error teks biasa.
1.  Hubungkan HP ke Laptop dengan kabel data.
2.  Di Android Studio, buka tab **Logcat** (di bagian bawah).
3.  Ketik `API_DEBUG` di kolom pencarian Logcat.
4.  Coba kirim form lagi, perhatikan teks yang muncul di Logcat. Teks tersebut adalah pesan error asli dari PHP/Apache.

---
**Tips:** Jika mengupload gambar, proses mungkin memakan waktu lebih lama. Timeout di aplikasi sudah diset ke 120 detik (2 menit).
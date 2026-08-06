# SINDESA — Sistem Informasi Desa (Aplikasi Mobile Android)

Aplikasi mobile berbasis Android untuk pelayanan digital kependudukan dan perizinan desa secara online, mandiri, dan cepat.

## 🚀 Fitur Utama
- **Autentikasi & Akun Warga**: Registrasi kependudukan dan login terenkripsi.
- **Layanan Administrasi Kependudukan**: Pengajuan Surat Pengantar KTP, KK, Domisili, Pindah, Kelahiran, dan Kematian.
- **Layanan Perizinan Desa**: Pengajuan Surat Keterangan Usaha (SKU), Surat Keterangan Tidak Mampu (SKTM), Izin Keramaian, Pengantar SKCK, Kehilangan, dan Beda Nama.
- **Riwayat Pengajuan**: Pelacakan status pengajuan surat secara real-time.
- **Unduh PDF Surat**: Preview dan unduh berkas surat resmi bertanda tangan digital.

## 🔒 Fitur Keamanan (OWASP Mobile Top 10)
- **OWASP M1 (Improper Platform Usage)**: Restriksi WebView & sanitasi header server.
- **OWASP M2 (Insecure Data Storage)**: Enkripsi sesi lokal `EncryptedSharedPreferences` (AES-256 GCM) & `FLAG_SECURE`.
- **OWASP M3 (Insecure Communication)**: Komunikasi data terenkripsi HTTPS & SSL Pinning.
- **OWASP M8 & M9 (Code Protection & Reverse Engineering)**: Obfuscation ProGuard/R8 & deteksi Perangkat Root/Emulator.

## 🛠️ Prasyarat & Cara Menjalankan Project
1. Buka project ini di **Android Studio** (Android Gradle Plugin 8.8+).
2. Sesuaikan URL API server backend pada file `RetrofitClient.kt`:
   ```kotlin
   const val BASE_URL = "https://your-api-domain.com/"
   ```
3. Sync Gradle dan jalankan di perangkat Android / Emulator (minSdk 29+).

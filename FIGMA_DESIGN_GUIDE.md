# 🎨 Panduan Desain Figma & UI Mockups SINDESA Mobile

Paket desain UI antarmuka aplikasi **SINDESA Mobile** telah dibuat secara lengkap dan terstruktur. File-file ini berbasis vektor resolusi tinggi (*pixel-perfect*) dan **100% siap di-import dan diedit di Figma**.

---

## 📂 Lokasi File Desain

Semua file tersimpan di folder:
👉 **`D:\Sindesa_mobile\figma_assets\`**

| No | Nama File | Deskripsi Layar | Resolusi |
|:--:|:---|:---|:--:|
| **Master** | [sindesa_figma_all_screens.svg](file:///D:/Sindesa_mobile/figma_assets/sindesa_figma_all_screens.svg) | **Master Artboard** (Berisi seluruh 9 layar + Token Warna) | 1425 x 3036 px |
| **01** | [01_welcome_screen.svg](file:///D:/Sindesa_mobile/figma_assets/01_welcome_screen.svg) | Welcome / Onboarding Screen | 375 x 812 dp |
| **02** | [02_login_screen.svg](file:///D:/Sindesa_mobile/figma_assets/02_login_screen.svg) | Login Akun Warga (NIK/Email + Password + Biometric) | 375 x 812 dp |
| **03** | [03_register_screen.svg](file:///D:/Sindesa_mobile/figma_assets/03_register_screen.svg) | Registrasi Akun Warga + Upload KTP Asli | 375 x 812 dp |
| **04** | [04_dashboard_screen.svg](file:///D:/Sindesa_mobile/figma_assets/04_dashboard_screen.svg) | Dashboard Utama (Statistik + Grid 8 Layanan + Bottom Nav) | 375 x 812 dp |
| **05** | [05_sidebar_drawer.svg](file:///D:/Sindesa_mobile/figma_assets/05_sidebar_drawer.svg) | Navigation Drawer Sidebar (Menu, Profil Header, Logout) | 375 x 812 dp |
| **06** | [06_layanan_form_domisili.svg](file:///D:/Sindesa_mobile/figma_assets/06_layanan_form_domisili.svg) | Form Pengajuan Surat (Stepper, Auto-fill, Lampiran) | 375 x 812 dp |
| **07** | [07_riwayat_pengajuan.svg](file:///D:/Sindesa_mobile/figma_assets/07_riwayat_pengajuan.svg) | Riwayat Pengajuan (Filter Tab, Status Badges, Aksi PDF) | 375 x 812 dp |
| **08** | [08_profil_screen.svg](file:///D:/Sindesa_mobile/figma_assets/08_profil_screen.svg) | Profil Akun Warga (Avatar, Biodata Lengkap, Ubah Password) | 375 x 812 dp |
| **09** | [09_pdf_preview_screen.svg](file:///D:/Sindesa_mobile/figma_assets/09_pdf_preview_screen.svg) | Layar Pratinjau Dokumen PDF (Kop Surat, QR Code, TTD Kades) | 375 x 812 dp |
| **Web** | [figma_ui_preview.html](file:///D:/Sindesa_mobile/figma_assets/figma_ui_preview.html) | Gallery Interaktif / Web UI Mockup Viewer | Web HTML |

---

## 🚀 Cara Import ke Figma (Hanya 10 Detik)

### Metode 1: Drag & Drop Langsung (Rekomendasi ⭐⭐⭐)
1. Buka aplikasi **Figma** (Desktop atau via Browser di [figma.com](https://figma.com)).
2. Buat file baru (**New Design File**).
3. Buka folder `D:\Sindesa_mobile\figma_assets\` di Windows File Explorer.
4. **Tarik (*Drag & Drop*)** file `sindesa_figma_all_screens.svg` (atau file layar yang diinginkan) langsung ke dalam canvas Figma.
5. **Selesai!** Figma akan otomatis membuat Frame, Layer vektor, Group, Teks, dan Komponen yang bisa langsung diedit warna dan teksnya.

---

### Metode 2: Import via Plugin "html.to.design"
Jika Anda ingin komponen ter-convert dengan auto-layout otomatis dari HTML:
1. Di Figma, buka menu **Plugins** -> Cari **"html.to.design"**.
2. Buka file [figma_ui_preview.html](file:///D:/Sindesa_mobile/figma_assets/figma_ui_preview.html) di browser.
3. Masukkan link atau copy file HTML tersebut ke plugin Figma.
4. Plugin akan men-generate layout Figma 1:1 secara instan.

---

## 🎨 Palet Warna & Design Tokens Resmi

| Nama Token | Kode Warna Hex | Peruntukan / Komponen |
|:---|:---:|:---|
| **Primary Green** | `#1a5e35` | Header, Button Utama, Card Hero, Tab Aktif |
| **Focused Green** | `#2e7d32` | Stroke Input, Tombol Login Outline |
| **Accent Gold** | `#cfa03f` | Tombol Registrasi, Aksen Teks, Badge Spesial |
| **Neutral Light** | `#f4f6f9` | Background Aplikasi Utama |
| **Card White** | `#ffffff` | Background Card Layanan, Input Box |
| **Text Dark** | `#1f2937` | Judul, Label Field, Teks Utama |
| **Text Muted** | `#6b7280` | Placeholder, Deskripsi, Tanggal |
| **Status Selesai** | `#10b981` (`#d1fae5`) | Badge Status Surat Selesai (Hijau) |
| **Status Diproses** | `#3b82f6` (`#dbeafe`) | Badge Status Diproses Kades (Biru) |
| **Status Menunggu** | `#f59e0b` (`#fef3c7`) | Badge Status Menunggu Verifikasi (Oranye) |
| **Status Ditolak** | `#ef4444` (`#fee2e2`) | Badge Status Ditolak (Merah) |

---

## 🔤 Tipografi (Typography)
- **Primary Font**: `Plus Jakarta Sans` / `Inter`
- **Official Letterhead Font (PDF)**: `Times New Roman` / `Serif`

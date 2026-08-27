package com.ta.sindesa

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/**
 * =========================================================================================
 * DashboardActivity.kt — Halaman Utama Aplikasi SINDESA
 * =========================================================================================
 * 
 * FUNGSI UTAMA:
 * 1. Pusat navigasi bagi warga desa untuk mengakses seluruh layanan surat.
 * 2. Menampilkan informasi ringkas akun pengguna (Nama, Status Verifikasi Akun).
 * 3. Menampilkan statistik real-time: Total Surat yang Diajukan & Surat Sedang Diproses.
 * 4. Menyediakan pintasan cepat (Quick Actions) ke layanan yang sering digunakan.
 * 5. Menerapkan kontrol keamanan Secure by Design (Anti-Screenshot, Anti-Root, Sesi Valid).
 * =========================================================================================
 */
class DashboardActivity : AppCompatActivity() {

    // =====================================================================================
    // 1. DEKLARASI VARIABEL KONTROLLER & MANAJEMEN SESI
    // =====================================================================================
    // drawerLayout: Mengontrol buka/tutup menu navigasi samping (Sidebar).
    private lateinit var drawerLayout: DrawerLayout
    // sessionManager: Menyimpan & membaca data login warga yang terenkripsi (AES-256).
    private lateinit var sessionManager: SessionManager

    /**
     * Metode onCreate(): Dipanggil saat Activity pertama kali dibuat.
     * Tempat inisialisasi keamanan, layout XML, dan event listener tombol.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ---------------------------------------------------------------------------------
        // KEAMANAN 1: FLAG_SECURE (Mencegah Screenshot & Screen Recording)
        // ---------------------------------------------------------------------------------
        // Mencegah kebocoran data pribadi (PII) warga jika layar direkam atau di-screenshot.
        // Memenuhi standar OWASP MASVS (M2 - Insecure Data Storage).
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        // ---------------------------------------------------------------------------------
        // KEAMANAN 2: DETEKSI ROOT & EMULATOR
        // ---------------------------------------------------------------------------------
        // Mencegah aplikasi dijalankan pada perangkat yang di-root atau emulator berbahaya.
        // Memenuhi standar OWASP MASVS (M8 - Code Tampering & M9 - Reverse Engineering).
        if (com.ta.sindesa.utils.SecurityUtil.isDeviceRooted() || com.ta.sindesa.utils.SecurityUtil.isRunningOnEmulator()) {
            Toast.makeText(this, "Aplikasi tidak dapat berjalan di lingkungan tidak aman", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Inisialisasi SessionManager lokal terenkripsi
        sessionManager = SessionManager(this)

        // ---------------------------------------------------------------------------------
        // KEAMANAN 3: PENGECEKAN STATUS LOGIN (Session Validation)
        // ---------------------------------------------------------------------------------
        // Memastikan hanya warga yang sudah berhasil login yang dapat membuka Dashboard.
        // Jika belum login / sesi hilang, otomatis dialihkan ke layar Selamat Datang (Welcome).
        if (!sessionManager.isLoggedIn()) {
            Toast.makeText(this, "Sesi habis. Silakan login kembali.", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, WelcomeActivity::class.java)
            // FLAG_ACTIVITY_CLEAR_TASK: Menghapus riwayat tumpukan agar tidak bisa di-back ke Dashboard
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        // Memasang layout XML Dashboard
        setContentView(R.layout.activity_dashboard)

        // =================================================================================
        // 2. BINDING KOMPONEN TAMPILAN (UI BINDING)
        // =================================================================================
        drawerLayout = findViewById(R.id.drawerLayout)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)

        // Menampilkan sapaan nama warga berdasarkan data sesi yang tersimpan
        val tvNamaUser = findViewById<TextView>(R.id.tvNamaUser)
        tvNamaUser.text = "Halo, ${sessionManager.getNamaUser()}!"
        
        // Peringatan Verifikasi Akun:
        // Jika akun warga sudah aktif (diverifikasi admin), sembunyikan kotak peringatan kuning.
        val cardWarningAkun = findViewById<MaterialCardView>(R.id.cardWarningAkun)
        if (sessionManager.getStatus() == "active") {
            cardWarningAkun.visibility = android.view.View.GONE
        }

        // Menginisialisasi Sidebar navigasi samping dengan data profil & event klik
        SidebarUtil.initSidebar(this, drawerLayout)

        // =================================================================================
        // 3. NAVIGASI TOOLBAR & PENANGANAN TOMBOL KEMBALI (BACK PRESS)
        // =================================================================================
        // Klik icon hamburger di toolbar -> buka menu sidebar kiri
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Menangani tombol Back pada HP:
        // Jika sidebar sedang terbuka -> tutup sidebar.
        // Jika di halaman Dashboard -> minimalkan aplikasi ke background (moveTaskToBack) agar tidak keluar paksa.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    moveTaskToBack(true)
                }
            }
        })

        // =================================================================================
        // 4. ROUTING PINTASAN MENU & FORMULIR SURAT
        // =================================================================================
        // A. Kartu Pintasan Cepat (Quick Access Card)
        val cardQuickKtp = findViewById<MaterialCardView>(R.id.cardQuickKtp)
        val cardQuickKk = findViewById<MaterialCardView>(R.id.cardQuickKk)

        // B. Kartu Menu Grid Layanan
        val menuKtp = findViewById<MaterialCardView>(R.id.menuKtp)
        val menuKk = findViewById<MaterialCardView>(R.id.menuKk)
        val menuSktm = findViewById<MaterialCardView>(R.id.menuSktm)
        val menuPindah = findViewById<MaterialCardView>(R.id.menuPindah)
        val menuKehilangan = findViewById<MaterialCardView>(R.id.menuKehilangan)
        val menuUsaha = findViewById<MaterialCardView>(R.id.menuUsaha)

        // Event listener saat kartu diklik untuk membuka formulir masing-masing surat
        val goToKtp = { startActivity(Intent(this, KtpActivity::class.java)) }
        val goToKk = { startActivity(Intent(this, KkActivity::class.java)) }

        cardQuickKtp.setOnClickListener { goToKtp() }
        menuKtp.setOnClickListener { goToKtp() }

        cardQuickKk.setOnClickListener { goToKk() }
        menuKk.setOnClickListener { goToKk() }

        menuSktm.setOnClickListener { startActivity(Intent(this, SktmActivity::class.java)) }
        menuPindah.setOnClickListener { startActivity(Intent(this, PindahActivity::class.java)) }
        menuKehilangan.setOnClickListener { startActivity(Intent(this, KehilanganActivity::class.java)) }
        menuUsaha.setOnClickListener { startActivity(Intent(this, UsahaActivity::class.java)) }

        // C. Navigasi Menuju Halaman Riwayat Surat
        val tvLihatSemuaRiwayat = findViewById<TextView>(R.id.tvLihatSemuaRiwayat)
        val goToRiwayat = { startActivity(Intent(this, RiwayatActivity::class.java)) }
        tvLihatSemuaRiwayat.setOnClickListener { goToRiwayat() }
    }

    /**
     * onResume(): Dipanggil setiap kali Activity kembali ke layar depan.
     * Digunakan untuk menyinkronkan statistik surat dan data profil terbaru.
     */
    override fun onResume() {
        super.onResume()
        // Ambil data statistik terbaru dari server (Total & Diproses)
        fetchDashboardStats()
        // Perbarui foto dan nama di sidebar
        SidebarUtil.refreshSidebarProfile(this)
        // Sinkronisasi data biodata profil warga dari API
        syncUserProfile()
    }

    // =====================================================================================
    // 5. METODE MENGAMBIL STATISTIK SURAT DARI API (fetchDashboardStats)
    // =====================================================================================
    /**
     * Mengambil jumlah total surat yang diajukan dan status pengerjaannya secara real-time.
     * Menggunakan Retrofit dengan otentikasi Bearer Token (Secure by Design).
     */
    private fun fetchDashboardStats() {
        val nik = sessionManager.getNikUser()
        if (nik.isNullOrEmpty()) return

        com.ta.sindesa.api.RetrofitClient.getInstance(this).getDashboardStats()
            .enqueue(object : retrofit2.Callback<com.ta.sindesa.models.DashboardStatsResponse> {
                override fun onResponse(
                    call: retrofit2.Call<com.ta.sindesa.models.DashboardStatsResponse>,
                    response: retrofit2.Response<com.ta.sindesa.models.DashboardStatsResponse>
                ) {
                    // KASUS 1: Respon Berhasil (HTTP 200)
                    if (response.isSuccessful) {
                        val stats = response.body()
                        if (stats != null && stats.success) {
                            val tvTotalPengajuan = findViewById<android.widget.TextView>(R.id.tvTotalPengajuan)
                            val tvProsesPengajuan = findViewById<android.widget.TextView>(R.id.tvProsesPengajuan)
                            
                            // Pasang angka statistik ke widget tampilan
                            tvTotalPengajuan.text = stats.totalPengajuan.toString()
                            tvProsesPengajuan.text = stats.prosesPengajuan.toString()

                            // Perbarui 2 kartu layanan yang paling sering diajukan
                            updateSeringDigunakan(stats.seringDigunakan)
                        }
                    } 
                    // KASUS 2: Sesi Token Kadaluarsa (HTTP 401 Unauthorized)
                    else if (response.code() == 401) {
                        // Bersihkan sesi lokal yang sudah tidak berlaku
                        sessionManager.logout()
                        Toast.makeText(this@DashboardActivity, "Sesi Anda telah berakhir. Silakan login kembali.", Toast.LENGTH_LONG).show()
                        val intent = Intent(this@DashboardActivity, WelcomeActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                }

                override fun onFailure(
                    call: retrofit2.Call<com.ta.sindesa.models.DashboardStatsResponse>,
                    t: Throwable
                ) {
                    // Abaikan kegagalan jaringan sementara agar tidak mengganggu antarmuka pengguna
                }
            })
    }

    // =====================================================================================
    // 6. METODE MEMPERBARUI KARTU LAYANAN SERING DIGUNAKAN
    // =====================================================================================
    /**
     * Menyesuaikan judul dan ikon kartu cepat berdasarkan histori surat terbanyak milik warga.
     */
    private fun updateSeringDigunakan(seringDigunakan: List<String>?) {
        if (seringDigunakan.isNullOrEmpty() || seringDigunakan.size < 2) return

        val card1 = findViewById<MaterialCardView>(R.id.cardQuickKtp)
        val card2 = findViewById<MaterialCardView>(R.id.cardQuickKk)

        setupQuickCard(card1, seringDigunakan[0])
        setupQuickCard(card2, seringDigunakan[1])
    }

    /**
     * Memetakan kode surat (snake_case) ke judul ramah pengguna, ikon Android, dan Activity tujuan.
     */
    private fun setupQuickCard(card: MaterialCardView, jenisSurat: String) {
        val linearLayout = card.getChildAt(0) as? android.widget.LinearLayout
        val iconView = linearLayout?.getChildAt(0) as? android.widget.ImageView
        val titleView = linearLayout?.getChildAt(1) as? android.widget.TextView
        
        if (iconView == null || titleView == null) return

        var title = ""
        var iconRes = 0
        var targetActivity: Class<*>? = null

        // Pemetaan jenis surat ke form yang sesuai
        when (jenisSurat) {
            "pengantar_ktp" -> { title = "Pengantar KTP"; iconRes = android.R.drawable.ic_menu_myplaces; targetActivity = KtpActivity::class.java }
            "pengantar_kk" -> { title = "Pengantar KK"; iconRes = android.R.drawable.ic_menu_gallery; targetActivity = KkActivity::class.java }
            "keterangan_tidak_mampu" -> { title = "Ket. Tidak Mampu"; iconRes = android.R.drawable.ic_menu_help; targetActivity = SktmActivity::class.java }
            "keterangan_pindah" -> { title = "Ket. Pindah"; iconRes = android.R.drawable.ic_menu_directions; targetActivity = PindahActivity::class.java }
            "keterangan_kehilangan" -> { title = "Ket. Kehilangan"; iconRes = android.R.drawable.ic_menu_search; targetActivity = KehilanganActivity::class.java }
            "keterangan_usaha" -> { title = "Ket. Usaha"; iconRes = android.R.drawable.ic_menu_manage; targetActivity = UsahaActivity::class.java }
            "keterangan_domisili" -> { title = "Ket. Domisili"; iconRes = android.R.drawable.ic_menu_mapmode; targetActivity = DomisiliActivity::class.java }
            "pengantar_skck" -> { title = "Pengantar SKCK"; iconRes = android.R.drawable.ic_menu_agenda; targetActivity = SkckActivity::class.java }
            "keterangan_kematian" -> { title = "Ket. Kematian"; iconRes = android.R.drawable.ic_menu_close_clear_cancel; targetActivity = KematianActivity::class.java }
            "keterangan_belum_menikah" -> { title = "Belum Menikah"; iconRes = android.R.drawable.ic_menu_info_details; targetActivity = BelumMenikahActivity::class.java }
            "keterangan_janda_duda" -> { title = "Ket. Janda/Duda"; iconRes = android.R.drawable.ic_menu_info_details; targetActivity = JandaDudaActivity::class.java }
            "izin_keramaian" -> { title = "Izin Keramaian"; iconRes = android.R.drawable.ic_menu_recent_history; targetActivity = IzinKeramaianActivity::class.java }
            "keterangan_beda_nama" -> { title = "Ket. Beda Nama"; iconRes = android.R.drawable.ic_menu_edit; targetActivity = BedaNamaActivity::class.java }
            "pengantar_akta_lahir" -> { title = "Akta Lahir"; iconRes = android.R.drawable.ic_menu_add; targetActivity = AktaLahirActivity::class.java }
            "keterangan_penghasilan" -> { title = "Ket. Penghasilan"; iconRes = android.R.drawable.ic_menu_sort_by_size; targetActivity = PenghasilanActivity::class.java }
            else -> { title = "Layanan Lain"; iconRes = android.R.drawable.ic_menu_send; targetActivity = DashboardActivity::class.java }
        }

        titleView.text = title
        iconView.setImageResource(iconRes)
        
        card.setOnClickListener {
            if (targetActivity != DashboardActivity::class.java) {
                startActivity(Intent(this, targetActivity))
            }
        }
    }

    // =====================================================================================
    // 7. METODE SINKRONISASI PROFIL DARI SERVER (syncUserProfile)
    // =====================================================================================
    /**
     * Memastikan data biodata warga di penyimpanan HP selalu selaras dengan basis data desa.
     */
    private fun syncUserProfile() {
        val nik = sessionManager.getNikUser() ?: return
        com.ta.sindesa.api.RetrofitClient.getInstance(this).getProfil().enqueue(
            object : retrofit2.Callback<com.ta.sindesa.models.LoginResponse> {
                override fun onResponse(
                    call: retrofit2.Call<com.ta.sindesa.models.LoginResponse>,
                    response: retrofit2.Response<com.ta.sindesa.models.LoginResponse>
                ) {
                    if (response.isSuccessful && response.body()?.success == true) {
                        val user = response.body()?.data?.user ?: return
                        
                        // Perbarui data di EncryptedSharedPreferences (AES-256)
                        sessionManager.setLoggedIn(
                            true,
                            userId = user.id,
                            nama = user.nama, 
                            nik = user.nik,
                            email = user.email,
                            token = sessionManager.getToken(),
                            noKk = user.noKk,
                            agama = user.agama,
                            jenisKelamin = user.jenisKelamin,
                            tempatLahir = user.tempatLahir,
                            tanggalLahir = user.tanggalLahir,
                            statusPerkawinan = user.statusPerkawinan,
                            pekerjaan = user.pekerjaan,
                            kewarganegaraan = user.kewarganegaraan,
                            alamatLengkap = user.alamatLengkap,
                            rtRw = user.rtRw,
                            provinsi = user.provinsi,
                            kota = user.kota,
                            kecamatan = user.kecamatan,
                            kelurahanDesa = user.kelurahanDesa,
                            provinsiCode = user.provinsiCode,
                            kotaCode = user.kotaCode,
                            kecamatanCode = user.kecamatanCode,
                            kelurahanDesaCode = user.kelurahanDesaCode,
                            noHp = user.noHp,
                            fotoProfil = user.fotoProfil ?: sessionManager.getFotoProfil(),
                            status = user.status ?: sessionManager.getStatus()
                        )
                        findViewById<TextView>(R.id.tvNamaUser)?.text = "Halo, ${sessionManager.getNamaUser()}!"
                        SidebarUtil.refreshSidebarProfile(this@DashboardActivity)
                    }
                }

                override fun onFailure(
                    call: retrofit2.Call<com.ta.sindesa.models.LoginResponse>,
                    t: Throwable
                ) {
                    // Silently ignore jika terjadi error jaringan saat background sync
                }
            }
        )
    }
}
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

class DashboardActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // SECURE BY DESIGN: Mencegah screenshot dan screen recording (M2 - Insecure Data Storage)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        // SECURE BY DESIGN: Deteksi Root & Emulator (M8 & M9)
        if (com.ta.sindesa.utils.SecurityUtil.isDeviceRooted() || com.ta.sindesa.utils.SecurityUtil.isRunningOnEmulator()) {
            Toast.makeText(this, "Aplikasi tidak dapat berjalan di lingkungan tidak aman", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        sessionManager = SessionManager(this)

        // ==========================================
        // SECURE BY DESIGN: 1. Pengecekan Sesi (Session)
        // ==========================================
        if (!sessionManager.isLoggedIn()) {
            Toast.makeText(this, "Sesi habis. Silakan login kembali.", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, WelcomeActivity::class.java)
            // Bersihkan riwayat agar aplikasi tidak bisa di-back ke Dashboard
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_dashboard)

        // ==========================================
        // SETUP UI & BINDING ID
        // ==========================================
        drawerLayout = findViewById(R.id.drawerLayout)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)

        // Setup Teks Profil
        val tvNamaUser = findViewById<TextView>(R.id.tvNamaUser)

        tvNamaUser.text = "Halo, ${sessionManager.getNamaUser()}!"
        
        // Cek status verifikasi akun (jika active, sembunyikan peringatan)
        val cardWarningAkun = findViewById<MaterialCardView>(R.id.cardWarningAkun)
        if (sessionManager.getStatus() == "active") {
            cardWarningAkun.visibility = android.view.View.GONE
        }

        // Initialize Sidebar using Utility
        SidebarUtil.initSidebar(this, drawerLayout)

        // ==========================================
        // KEAMANAN NAVIGASI SIDEBAR & TOMBOL BACK
        // ==========================================
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    // Mencegah force close, lebih baik meminimalkan aplikasi (Home)
                    moveTaskToBack(true)
                }
            }
        })

        // ==========================================
        // ROUTING (INTENT) KE FORMULIR LAYANAN
        // ==========================================
        // 1. Tombol Sering Digunakan
        val cardQuickKtp = findViewById<MaterialCardView>(R.id.cardQuickKtp)
        val cardQuickKk = findViewById<MaterialCardView>(R.id.cardQuickKk)

        // 2. Tombol Menu Grid
        val menuKtp = findViewById<MaterialCardView>(R.id.menuKtp)
        val menuKk = findViewById<MaterialCardView>(R.id.menuKk)
        val menuSktm = findViewById<MaterialCardView>(R.id.menuSktm)
        val menuPindah = findViewById<MaterialCardView>(R.id.menuPindah)
        val menuKehilangan = findViewById<MaterialCardView>(R.id.menuKehilangan)
        val menuUsaha = findViewById<MaterialCardView>(R.id.menuUsaha)

        // Fungsi lambda agar tidak perlu menulis Intent berulang-ulang
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

        // ==========================================
        // ROUTING RIWAYAT & LOGOUT
        // ==========================================
        val tvLihatSemuaRiwayat = findViewById<TextView>(R.id.tvLihatSemuaRiwayat)

        val goToRiwayat = { startActivity(Intent(this, RiwayatActivity::class.java)) }
        tvLihatSemuaRiwayat.setOnClickListener { goToRiwayat() }
    }

    override fun onResume() {
        super.onResume()
        fetchDashboardStats()
        SidebarUtil.refreshSidebarProfile(this)
        syncUserProfile()
    }

    private fun fetchDashboardStats() {
        val nik = sessionManager.getNikUser()
        if (nik.isNullOrEmpty()) return

        com.ta.sindesa.api.RetrofitClient.getInstance(this).getDashboardStats(nik)
            .enqueue(object : retrofit2.Callback<com.ta.sindesa.models.DashboardStatsResponse> {
                override fun onResponse(
                    call: retrofit2.Call<com.ta.sindesa.models.DashboardStatsResponse>,
                    response: retrofit2.Response<com.ta.sindesa.models.DashboardStatsResponse>
                ) {
                    if (response.isSuccessful) {
                        val stats = response.body()
                        if (stats != null && stats.success) {
                            val tvTotalPengajuan = findViewById<android.widget.TextView>(R.id.tvTotalPengajuan)
                            val tvProsesPengajuan = findViewById<android.widget.TextView>(R.id.tvProsesPengajuan)
                            
                            tvTotalPengajuan.text = stats.totalPengajuan.toString()
                            tvProsesPengajuan.text = stats.prosesPengajuan.toString()

                            // Update Layanan Sering Digunakan
                            updateSeringDigunakan(stats.seringDigunakan)
                        }
                    }
                }

                override fun onFailure(
                    call: retrofit2.Call<com.ta.sindesa.models.DashboardStatsResponse>,
                    t: Throwable
                ) {
                    // Ignore on failure to not spam the user
                }
            })
    }

    private fun updateSeringDigunakan(seringDigunakan: List<String>?) {
        if (seringDigunakan.isNullOrEmpty() || seringDigunakan.size < 2) return

        val card1 = findViewById<MaterialCardView>(R.id.cardQuickKtp)
        val card2 = findViewById<MaterialCardView>(R.id.cardQuickKk)

        setupQuickCard(card1, seringDigunakan[0])
        setupQuickCard(card2, seringDigunakan[1])
    }

    private fun setupQuickCard(card: MaterialCardView, jenisSurat: String) {
        val linearLayout = card.getChildAt(0) as? android.widget.LinearLayout
        val iconView = linearLayout?.getChildAt(0) as? android.widget.ImageView
        val titleView = linearLayout?.getChildAt(1) as? android.widget.TextView
        
        if (iconView == null || titleView == null) return

        var title = ""
        var iconRes = 0
        var targetActivity: Class<*>? = null

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



    private fun syncUserProfile() {
        val nik = sessionManager.getNikUser() ?: return
        com.ta.sindesa.api.RetrofitClient.getInstance(this).getProfil(nik).enqueue(
            object : retrofit2.Callback<com.ta.sindesa.models.LoginResponse> {
                override fun onResponse(
                    call: retrofit2.Call<com.ta.sindesa.models.LoginResponse>,
                    response: retrofit2.Response<com.ta.sindesa.models.LoginResponse>
                ) {
                    if (response.isSuccessful && response.body()?.success == true) {
                        val user = response.body()?.data?.user ?: return
                        sessionManager.setLoggedIn(
                            true,
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
                    // Silently ignore
                }
            }
        )
    }
}
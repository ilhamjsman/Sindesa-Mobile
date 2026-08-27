package com.ta.sindesa

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.textfield.TextInputEditText
import com.ta.sindesa.api.RetrofitClient
import com.ta.sindesa.models.RiwayatData
import com.ta.sindesa.models.RiwayatResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * =========================================================================================
 * RiwayatActivity.kt — Halaman Riwayat Pengajuan Surat Warga
 * =========================================================================================
 * 
 * FUNGSI UTAMA:
 * 1. Menampilkan seluruh daftar pengajuan surat yang pernah diajukan oleh akun warga yang aktif.
 * 2. Pencarian cepat (Live Search) berdasarkan nama surat atau status.
 * 3. Pengurutan data (Sorting: Terbaru, Terlama, Berdasarkan Status).
 * 4. Melihat detail pengajuan via BottomSheet Dialog (Status, Nomor Surat, Metode TTD, Alasan Penolakan).
 * 5. Tombol aksi:
 *    - Preview & Cetak Dokumen PDF (Jika status sudah "Selesai").
 *    - Edit Pengajuan (Jika status masih "Menunggu Verifikasi").
 *    - Batalkan / Hapus Pengajuan (Jika status masih "Menunggu Verifikasi").
 * 6. Keamanan Secure by Design (FLAG_SECURE, Token Autentikasi, Penanganan Sesi Kadaluarsa).
 * =========================================================================================
 */
class RiwayatActivity : AppCompatActivity() {

    // =====================================================================================
    // 1. DEKLARASI VARIABEL TAMPILAN & PENGELOLA DATA
    // =====================================================================================
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var sessionManager: SessionManager
    private lateinit var recyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var adapter: RiwayatAdapter
    private lateinit var progressBar: android.widget.ProgressBar
    private lateinit var layoutEmpty: android.widget.LinearLayout
    private lateinit var tvEmpty: android.widget.TextView
    private lateinit var etSearch: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ---------------------------------------------------------------------------------
        // KEAMANAN 1: FLAG_SECURE (Mencegah Screenshot & Perekaman Layar)
        // ---------------------------------------------------------------------------------
        // Melindungi data riwayat dan nomor surat pribadi agar tidak bocor via tangkapan layar.
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        // ---------------------------------------------------------------------------------
        // KEAMANAN 2: DETEKSI ROOT & EMULATOR
        // ---------------------------------------------------------------------------------
        if (com.ta.sindesa.utils.SecurityUtil.isDeviceRooted() || com.ta.sindesa.utils.SecurityUtil.isRunningOnEmulator()) {
            com.ta.sindesa.utils.SecurityUtil.showSecurityWarning(this)
            return
        }

        sessionManager = SessionManager(this)

        // ---------------------------------------------------------------------------------
        // KEAMANAN 3: VALIDASI STATUS LOGIN
        // ---------------------------------------------------------------------------------
        if (!sessionManager.isLoggedIn()) {
            Toast.makeText(this, "Akses ditolak. Silakan login terlebih dahulu.", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_riwayat)

        // =================================================================================
        // 2. SETUP VIEW & WIDGET UI
        // =================================================================================
        drawerLayout = findViewById(R.id.drawerLayout)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        recyclerView = findViewById(R.id.rvRiwayat)
        progressBar = findViewById(R.id.progressBar)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        tvEmpty = findViewById(R.id.tvEmpty)
        etSearch = findViewById(R.id.etSearch)
        val btnSort = findViewById<ImageButton>(R.id.btnSort)

        // Konfigurasi Toolbar & Menu Samping
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
        SidebarUtil.initSidebar(this, drawerLayout)

        // Konfigurasi RecyclerView (Daftar List Surat)
        adapter = RiwayatAdapter(emptyList())
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Listener saat item surat atau tombol "Detail" diklik
        adapter.setOnDetailClickListener { riwayat ->
            showDetailRiwayat(riwayat)
        }

        // =================================================================================
        // 3. FITUR PENCARIAN REALTIME (LIVE SEARCH)
        // =================================================================================
        // Memfilter isi daftar secara langsung setiap kali huruf diketikkan di kotak pencarian
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Tombol Filter & Urutkan
        btnSort.setOnClickListener {
            showSortDialog()
        }

        // Memuat data riwayat dari Server API
        loadRiwayatData()

        // Menangani tombol Back pada perangkat
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    finish()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // Otomatis refresh data saat kembali dari layar formulir atau pratinjau surat
        if (::adapter.isInitialized) {
            loadRiwayatData()
        }
    }

    // =====================================================================================
    // 4. METODE MEMUAT DATA RIWAYAT DARI SERVER (loadRiwayatData)
    // =====================================================================================
    /**
     * Memanggil endpoint get_riwayat.php menggunakan Retrofit.
     * Menggunakan Token Bearer untuk menjamin data yang ditarik hanya milik user tersebut (Anti-IDOR).
     */
    private fun loadRiwayatData() {
        val nik = sessionManager.getNikUser()
        if (nik.isNullOrEmpty()) {
            tvEmpty.text = "Sesi bermasalah: NIK tidak ditemukan. Silakan login ulang."
            tvEmpty.visibility = android.view.View.VISIBLE
            return
        }
        
        // Tampilkan animasi loading & sembunyikan list sementara
        progressBar.visibility = android.view.View.VISIBLE
        recyclerView.visibility = android.view.View.GONE
        tvEmpty.visibility = android.view.View.GONE

        RetrofitClient.getInstance(this).getRiwayat().enqueue(object : Callback<RiwayatResponse> {
            override fun onResponse(
                call: Call<RiwayatResponse>,
                response: Response<RiwayatResponse>
            ) {
                progressBar.visibility = android.view.View.GONE
                
                // KASUS 1: Respon Berhasil (HTTP 200)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        val data = body.data ?: emptyList()
                        if (data.isEmpty()) {
                            tvEmpty.text = "Anda belum memiliki riwayat pengajuan surat."
                            tvEmpty.visibility = android.view.View.VISIBLE
                        } else {
                            recyclerView.visibility = android.view.View.VISIBLE
                            adapter.updateData(data)
                        }
                    } else {
                        tvEmpty.text = body?.message ?: "Data riwayat tidak ditemukan"
                        tvEmpty.visibility = android.view.View.VISIBLE
                    }
                } 
                // KASUS 2: Sesi Kadaluarsa (HTTP 401 Unauthorized)
                else if (response.code() == 401) {
                    sessionManager.logout()
                    Toast.makeText(this@RiwayatActivity, "Sesi Anda telah berakhir. Silakan login kembali.", Toast.LENGTH_LONG).show()
                    val intent = Intent(this@RiwayatActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } 
                // KASUS 3: Respon Error Lainnya
                else {
                    tvEmpty.text = "Gagal memuat data (Kode: ${response.code()})"
                    tvEmpty.visibility = android.view.View.VISIBLE
                }
            }

            override fun onFailure(call: Call<RiwayatResponse>, t: Throwable) {
                progressBar.visibility = android.view.View.GONE
                
                val host = RetrofitClient.HOSTNAME
                val errorMessage = when (t) {
                    is com.google.gson.JsonSyntaxException -> "Respon server tidak valid (Bukan JSON)."
                    is java.net.SocketTimeoutException -> "Koneksi RTO (Timeout). Periksa koneksi internet Anda."
                    is java.net.ConnectException -> "Gagal terhubung ke server hosting ($host)."
                    else -> "Kesalahan: ${t.localizedMessage}"
                }

                tvEmpty.text = errorMessage
                tvEmpty.visibility = android.view.View.VISIBLE
                
                AlertDialog.Builder(this@RiwayatActivity)
                    .setTitle("Gagal Terhubung ke Server")
                    .setMessage(errorMessage)
                    .setPositiveButton("Tutup", null)
                    .show()
                
                android.util.Log.e("API_DEBUG", "Riwayat Failure", t)
            }
        })
    }

    // =====================================================================================
    // 5. METODE MENAMPILKAN DETAIL RIWAYAT (showDetailRiwayat)
    // =====================================================================================
    /**
     * Membuka BottomSheet Dialog berisi informasi lengkap status pengajuan surat.
     */
    private fun showDetailRiwayat(riwayat: RiwayatData) {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_detail_riwayat, null)
        bottomSheetDialog.setContentView(view)

        // Binding komponen dialog
        val tvJenis: android.widget.TextView = view.findViewById(R.id.detailJenisSurat)
        val tvTanggal: android.widget.TextView = view.findViewById(R.id.detailTanggal)
        val tvNomorSurat: android.widget.TextView = view.findViewById(R.id.detailNomorSurat)
        val tvStatus: android.widget.TextView = view.findViewById(R.id.detailStatus)
        val layoutMetodeTtd: android.widget.LinearLayout = view.findViewById(R.id.layoutMetodeTtd)
        val tvMetodeTtd: android.widget.TextView = view.findViewById(R.id.detailMetodeTtd)
        val layoutPenolakan: android.widget.LinearLayout = view.findViewById(R.id.layoutPenolakan)
        val tvPesanPenolakan: android.widget.TextView = view.findViewById(R.id.detailPesanPenolakan)
        val tvKet: android.widget.TextView = view.findViewById(R.id.detailKeterangan)
        val btnDownloadPdf: com.google.android.material.button.MaterialButton = view.findViewById(R.id.btnDownloadPdf)
        val btnClose: com.google.android.material.button.MaterialButton = view.findViewById(R.id.btnCloseDetail)

        tvJenis.text = riwayat.jenisSurat
        tvTanggal.text = "Diajukan pada: ${riwayat.tanggal}"

        // Tampilkan Nomor Surat jika admin sudah menerbitkan nomor
        if (!riwayat.nomorSurat.isNullOrEmpty()) {
            tvNomorSurat.text = "No. Surat: ${riwayat.nomorSurat}"
            tvNomorSurat.visibility = android.view.View.VISIBLE
        }

        tvStatus.text = riwayat.status

        // Pewarnaan Badge Status (Hijau = Selesai, Merah = Ditolak, Kuning = Kades, Biru = Menunggu)
        val statusRaw = riwayat.statusRaw ?: riwayat.status.lowercase()
        when {
            statusRaw.contains("selesai") -> {
                tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#1a5e35"))
            }
            statusRaw.contains("ditolak") -> {
                tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#DC2626"))
            }
            statusRaw.contains("kades") -> {
                tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#D97706"))
            }
            else -> {
                tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#1D4ED8"))
            }
        }

        // TAMPILKAN METODE TANDA TANGAN (Digital QR / Konvensional Basah) jika surat selesai
        if (statusRaw.contains("selesai") && !riwayat.metodeTtdLabel.isNullOrEmpty()) {
            layoutMetodeTtd.visibility = android.view.View.VISIBLE
            tvMetodeTtd.text = riwayat.metodeTtdLabel

            val ttdColor = when (riwayat.metodeTtd?.lowercase()) {
                "digital" -> "#059669"
                "konvensional" -> "#D97706"
                "manual" -> "#6B21A8"
                else -> "#6B21A8"
            }
            tvMetodeTtd.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(ttdColor))
        }

        // TAMPILKAN ALASAN PENOLAKAN jika status ditolak oleh Operator/Kades
        if (statusRaw.contains("ditolak") && !riwayat.pesanPenolakan.isNullOrEmpty()) {
            layoutPenolakan.visibility = android.view.View.VISIBLE
            tvPesanPenolakan.text = riwayat.pesanPenolakan
        }

        // Deskripsi Keterangan
        tvKet.text = if (riwayat.keterangan.isNullOrEmpty() || riwayat.keterangan == "-") {
            when {
                statusRaw.contains("menunggu") -> "Surat Anda sedang menunggu verifikasi dari Operator Desa."
                statusRaw.contains("kades") -> "Surat Anda sudah diverifikasi dan sedang menunggu tanda tangan Kepala Desa."
                statusRaw.contains("selesai") -> "Surat Anda telah selesai dan bisa diunduh."
                statusRaw.contains("ditolak") -> "Pengajuan surat Anda telah ditolak. Silakan periksa alasan di atas."
                else -> "Belum ada catatan tambahan dari admin desa."
            }
        } else {
            riwayat.keterangan
        }

        // =================================================================================
        // TOMBOL 1: PREVIEW SURAT PDF (Hanya jika status SELESAI)
        // =================================================================================
        if (statusRaw.contains("selesai")) {
            btnDownloadPdf.visibility = android.view.View.VISIBLE
            btnDownloadPdf.text = "📄  PREVIEW SURAT"
            btnDownloadPdf.setOnClickListener {
                val pdfUrl = com.ta.sindesa.api.RetrofitClient.BASE_URL + "cetak_surat.php?id=${riwayat.id}"
                val intent = PdfPreviewActivity.createIntent(
                    this,
                    pdfUrl,
                    riwayat.jenisSurat
                )
                startActivity(intent)
            }
        }

        // =================================================================================
        // TOMBOL 2: EDIT & HAPUS SURAT (Hanya jika status masih MENUNGGU VERIFIKASI)
        // =================================================================================
        val statusRawClean = (riwayat.statusRaw ?: riwayat.status).lowercase().trim().replace(" ", "_").replace("-", "_")
        val isMenunggu = statusRawClean == "menunggu_verifikasi" || statusRawClean == "menunggu" || statusRawClean.contains("menunggu")

        if (isMenunggu) {
            val targetClass: Class<out AppCompatActivity>? = when {
                riwayat.jenisSurat.contains("Akta Lahir", ignoreCase = true) -> AktaLahirActivity::class.java
                riwayat.jenisSurat.contains("KTP", ignoreCase = true) -> KtpActivity::class.java
                riwayat.jenisSurat.contains("KK", ignoreCase = true) -> KkActivity::class.java
                riwayat.jenisSurat.contains("Kematian", ignoreCase = true) -> KematianActivity::class.java
                riwayat.jenisSurat.contains("Pindah", ignoreCase = true) -> PindahActivity::class.java
                riwayat.jenisSurat.contains("Domisili", ignoreCase = true) -> DomisiliActivity::class.java
                riwayat.jenisSurat.contains("Belum Menikah", ignoreCase = true) -> BelumMenikahActivity::class.java
                riwayat.jenisSurat.contains("Janda", ignoreCase = true) || riwayat.jenisSurat.contains("Duda", ignoreCase = true) -> JandaDudaActivity::class.java
                riwayat.jenisSurat.contains("Beda Nama", ignoreCase = true) -> BedaNamaActivity::class.java
                riwayat.jenisSurat.contains("Kehilangan", ignoreCase = true) -> KehilanganActivity::class.java
                riwayat.jenisSurat.contains("SKCK", ignoreCase = true) -> SkckActivity::class.java
                riwayat.jenisSurat.contains("Usaha", ignoreCase = true) -> UsahaActivity::class.java
                riwayat.jenisSurat.contains("Keramaian", ignoreCase = true) -> IzinKeramaianActivity::class.java
                riwayat.jenisSurat.contains("Tidak Mampu", ignoreCase = true) || riwayat.jenisSurat.contains("SKTM", ignoreCase = true) -> SktmActivity::class.java
                riwayat.jenisSurat.contains("Penghasilan", ignoreCase = true) -> PenghasilanActivity::class.java
                else -> null
            }

            val parentLayout = btnClose.parent as android.view.ViewGroup

            // A. Tombol Edit
            if (targetClass != null) {
                val btnEdit = com.google.android.material.button.MaterialButton(this).apply {
                    text = "✏️  EDIT PENGAJUAN"
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
                    ).apply {
                        topMargin = 16
                    }
                    setBackgroundColor(android.graphics.Color.parseColor("#cfa03f"))
                    cornerRadius = 24
                    setOnClickListener {
                        bottomSheetDialog.dismiss()
                        val intent = Intent(this@RiwayatActivity, targetClass)
                        intent.putExtra("EDIT_ID", riwayat.id)
                        startActivity(intent)
                    }
                }
                parentLayout.addView(btnEdit, parentLayout.indexOfChild(btnClose))
            }

            // B. Tombol Batalkan / Hapus
            val btnDelete = com.google.android.material.button.MaterialButton(this).apply {
                text = "🗑️  BATALKAN / HAPUS SURAT"
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
                ).apply {
                    topMargin = 12
                }
                setBackgroundColor(android.graphics.Color.parseColor("#DC2626"))
                cornerRadius = 24
                setOnClickListener {
                    bottomSheetDialog.dismiss()
                    confirmAndDeletePengajuan(riwayat)
                }
            }
            parentLayout.addView(btnDelete, parentLayout.indexOfChild(btnClose))
        }

        btnClose.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    // =====================================================================================
    // 6. METODE KONFIRMASI & HAPUS PENGAJUAN SURAT
    // =====================================================================================
    private fun confirmAndDeletePengajuan(riwayat: RiwayatData) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Pengajuan Surat")
            .setMessage("Apakah Anda yakin ingin membatalkan dan menghapus pengajuan ${riwayat.jenisSurat}? Data yang dihapus tidak dapat dikembalikan.")
            .setPositiveButton("Ya, Hapus") { _, _ ->
                executeDeletePengajuan(riwayat.id)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    /**
     * Mengeksekusi penghapusan surat ke endpoint delete_pengajuan.php.
     * Dilindungi otorisasi kepemilikan data di server (Anti-IDOR).
     */
    private fun executeDeletePengajuan(id: Int) {
        val progressBar = findViewById<android.widget.ProgressBar>(R.id.progressBar)
        progressBar?.visibility = android.view.View.VISIBLE

        RetrofitClient.getInstance(this).deletePengajuan(id).enqueue(object : retrofit2.Callback<com.ta.sindesa.models.LoginResponse> {
            override fun onResponse(
                call: retrofit2.Call<com.ta.sindesa.models.LoginResponse>,
                response: retrofit2.Response<com.ta.sindesa.models.LoginResponse>
            ) {
                progressBar?.visibility = android.view.View.GONE
                if (response.isSuccessful && response.body()?.success == true) {
                    val msg = response.body()?.message ?: "Pengajuan surat berhasil dibatalkan"
                    Toast.makeText(this@RiwayatActivity, msg, Toast.LENGTH_LONG).show()
                    loadRiwayatData() // Reload list setelah hapus
                } else {
                    val errorMsg = response.body()?.message ?: "Gagal membatalkan pengajuan (HTTP ${response.code()})"
                    Toast.makeText(this@RiwayatActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: retrofit2.Call<com.ta.sindesa.models.LoginResponse>, t: Throwable) {
                progressBar?.visibility = android.view.View.GONE
                Toast.makeText(this@RiwayatActivity, "Koneksi gagal: ${t.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        })
    }

    // =====================================================================================
    // 7. METODE FILTER & SORTING DATA
    // =====================================================================================
    private fun showSortDialog() {
        val options = arrayOf("Terbaru", "Terlama", "Status: Selesai", "Status: Diproses", "Status: Ditolak")
        
        AlertDialog.Builder(this)
            .setTitle("Urutkan / Filter Berdasarkan")
            .setItems(options) { dialog, which ->
                val selectedOption = options[which]
                Toast.makeText(this, "Mengurutkan berdasarkan: $selectedOption", Toast.LENGTH_SHORT).show()
                
                adapter.sort(which)
                dialog.dismiss()
            }
            .show()
    }
}
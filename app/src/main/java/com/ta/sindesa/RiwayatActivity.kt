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

class RiwayatActivity : AppCompatActivity() {

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

        // SECURE BY DESIGN: Mencegah screenshot dan screen recording (M1 - Improper Platform Usage)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        // SECURE BY DESIGN: Deteksi Root & Emulator (M8 & M9)
        if (com.ta.sindesa.utils.SecurityUtil.isDeviceRooted() || com.ta.sindesa.utils.SecurityUtil.isRunningOnEmulator()) {
            com.ta.sindesa.utils.SecurityUtil.showSecurityWarning(this)
            return
        }

        sessionManager = SessionManager(this)

        if (!sessionManager.isLoggedIn()) {
            Toast.makeText(this, "Akses ditolak. Silakan login terlebih dahulu.", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_riwayat)

        // Setup UI Components
        drawerLayout = findViewById(R.id.drawerLayout)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        recyclerView = findViewById(R.id.rvRiwayat)
        progressBar = findViewById(R.id.progressBar)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        tvEmpty = findViewById(R.id.tvEmpty)
        etSearch = findViewById(R.id.etSearch)
        val btnSort = findViewById<ImageButton>(R.id.btnSort)

        // Setup Toolbar & Sidebar
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
        SidebarUtil.initSidebar(this, drawerLayout)

        // Setup RecyclerView
        adapter = RiwayatAdapter(emptyList())
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Listener klik tombol Detail
        adapter.setOnDetailClickListener { riwayat ->
            showDetailRiwayat(riwayat)
        }

        // Setup Search Logic
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        btnSort.setOnClickListener {
            showSortDialog()
        }

        // Load Data from API
        loadRiwayatData()

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
        // Refresh data saat kembali dari edit atau activity lain
        if (::adapter.isInitialized) {
            loadRiwayatData()
        }
    }

    private fun loadRiwayatData() {
        val nik = sessionManager.getNikUser()
        if (nik.isNullOrEmpty()) {
            tvEmpty.text = "Sesi bermasalah: NIK tidak ditemukan. Silakan login ulang."
            tvEmpty.visibility = android.view.View.VISIBLE
            return
        }
        
        progressBar.visibility = android.view.View.VISIBLE
        recyclerView.visibility = android.view.View.GONE
        tvEmpty.visibility = android.view.View.GONE

        RetrofitClient.getInstance(this).getRiwayat(nik).enqueue(object : Callback<RiwayatResponse> {
            override fun onResponse(
                call: Call<RiwayatResponse>,
                response: Response<RiwayatResponse>
            ) {
                progressBar.visibility = android.view.View.GONE
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
                } else {
                    tvEmpty.text = "Server Error: ${response.code()}"
                    tvEmpty.visibility = android.view.View.VISIBLE
                }
            }

            override fun onFailure(call: Call<RiwayatResponse>, t: Throwable) {
                progressBar.visibility = android.view.View.GONE
                
                val host = RetrofitClient.HOSTNAME
                val errorMessage = when (t) {
                    is com.google.gson.JsonSyntaxException -> {
                        "Respon Server Bukan JSON.\n\n" +
                        "Penyebab: PHP mengirim teks biasa/error PHP.\n" +
                        "Cek: Logcat 'API_DEBUG' untuk melihat teks tersebut."
                    }
                    is java.net.SocketTimeoutException -> {
                        "Waktu Habis (Timeout).\n\n" +
                        "1. Matikan FIREWALL di Windows (Penting!)\n" +
                        "2. Pastikan Laragon/XAMPP sudah RUNNING.\n" +
                        "3. HP & Laptop harus di 1 WiFi yang sama.\n" +
                        "Target: http://$host:8080"
                    }
                    is java.net.ConnectException -> "Koneksi Ditolak. Pastikan Port 8080 di Laragon sudah aktif."
                    is java.io.EOFException -> {
                        "Server mengembalikan respons kosong.\n\n" +
                        "1. Pastikan HP & Laptop di WiFi yang SAMA.\n" +
                        "2. Coba matikan lalu nyalakan WiFi di HP.\n" +
                        "3. Pastikan Laragon sudah RUNNING."
                    }
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

    /**
     * Menampilkan Detail Riwayat menggunakan BottomSheet
     */
    private fun showDetailRiwayat(riwayat: RiwayatData) {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_detail_riwayat, null)
        bottomSheetDialog.setContentView(view)

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

        // Tampilkan Nomor Surat jika ada
        if (!riwayat.nomorSurat.isNullOrEmpty()) {
            tvNomorSurat.text = "No. Surat: ${riwayat.nomorSurat}"
            tvNomorSurat.visibility = android.view.View.VISIBLE
        }

        tvStatus.text = riwayat.status

        // Warna badge status berdasarkan status_raw
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

        // === TAMPILKAN METODE TANDA TANGAN (jika surat sudah selesai) ===
        if (statusRaw.contains("selesai") && !riwayat.metodeTtdLabel.isNullOrEmpty()) {
            layoutMetodeTtd.visibility = android.view.View.VISIBLE
            tvMetodeTtd.text = riwayat.metodeTtdLabel

            // Warna badge berdasarkan metode TTD
            val ttdColor = when (riwayat.metodeTtd?.lowercase()) {
                "digital" -> "#059669"
                "konvensional" -> "#D97706"
                "manual" -> "#6B21A8"
                else -> "#6B21A8"
            }
            tvMetodeTtd.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(ttdColor))
        }

        // === TAMPILKAN ALASAN PENOLAKAN (jika ditolak) ===
        if (statusRaw.contains("ditolak") && !riwayat.pesanPenolakan.isNullOrEmpty()) {
            layoutPenolakan.visibility = android.view.View.VISIBLE
            tvPesanPenolakan.text = riwayat.pesanPenolakan
        }

        // Keterangan umum
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

        // === TOMBOL PREVIEW & DOWNLOAD PDF (jika surat selesai) ===
        if (statusRaw.contains("selesai")) {
            btnDownloadPdf.visibility = android.view.View.VISIBLE
            btnDownloadPdf.text = "📄  PREVIEW SURAT"
            btnDownloadPdf.setOnClickListener {
                // Buka preview PDF di dalam aplikasi
                val pdfUrl = com.ta.sindesa.api.RetrofitClient.BASE_URL + "cetak_surat.php?id=${riwayat.id}"
                val intent = PdfPreviewActivity.createIntent(
                    this,
                    pdfUrl,
                    riwayat.jenisSurat
                )
                startActivity(intent)
            }
        }

        // === TOMBOL EDIT & HAPUS (hanya jika status masih menunggu verifikasi) ===
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

    private fun executeDeletePengajuan(id: Int) {
        val progressBar = findViewById<android.widget.ProgressBar>(R.id.progressBar)
        if (progressBar != null) progressBar.visibility = android.view.View.VISIBLE

        // Gunakan hapus_surat.php — file standalone tanpa dependency
        val baseUrl = com.ta.sindesa.api.RetrofitClient.BASE_URL

        Thread {
            var success = false
            var message = "Gagal menghapus pengajuan"

            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()

                // Cara 1: POST ke hapus_surat.php
                try {
                    val formBody = okhttp3.FormBody.Builder()
                        .add("id", id.toString())
                        .build()
                    val request = okhttp3.Request.Builder()
                        .url(baseUrl + "hapus_surat.php")
                        .post(formBody)
                        .build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    android.util.Log.d("DELETE_SURAT", "POST hapus_surat.php response: $body")

                    if (body.contains("\"success\":true") || body.contains("berhasil")) {
                        success = true
                        message = "Pengajuan surat berhasil dibatalkan"
                    } else {
                        try {
                            val json = org.json.JSONObject(body)
                            message = json.optString("message", body)
                        } catch (_: Exception) {
                            message = body.take(200)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DELETE_SURAT", "POST failed: ${e.message}")
                    message = "POST gagal: ${e.message}"
                }

                // Cara 2: GET ke hapus_surat.php?id=X (fallback)
                if (!success) {
                    try {
                        val request = okhttp3.Request.Builder()
                            .url(baseUrl + "hapus_surat.php?id=$id")
                            .get()
                            .build()
                        val response = client.newCall(request).execute()
                        val body = response.body?.string() ?: ""
                        android.util.Log.d("DELETE_SURAT", "GET hapus_surat.php response: $body")

                        if (body.contains("\"success\":true") || body.contains("berhasil")) {
                            success = true
                            message = "Pengajuan surat berhasil dibatalkan"
                        } else {
                            try {
                                val json = org.json.JSONObject(body)
                                message = json.optString("message", body)
                            } catch (_: Exception) {
                                message = body.take(200)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("DELETE_SURAT", "GET failed: ${e.message}")
                        message = "Gagal terhubung ke server: ${e.message}"
                    }
                }

            } catch (e: Exception) {
                message = "Error: ${e.message}"
            }

            val finalSuccess = success
            val finalMessage = message
            runOnUiThread {
                if (progressBar != null) progressBar.visibility = android.view.View.GONE
                Toast.makeText(this@RiwayatActivity, finalMessage, Toast.LENGTH_LONG).show()
                if (finalSuccess) {
                    loadRiwayatData()
                }
            }
        }.start()
    }

    /**
     * Menampilkan Dialog untuk memilih urutan data riwayat
     */
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
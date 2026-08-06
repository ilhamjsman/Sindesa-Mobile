package com.ta.sindesa

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.ta.sindesa.api.RetrofitClient
import com.ta.sindesa.utils.FileUtils
import com.ta.sindesa.utils.SecurityUtil
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class KematianActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var sessionManager: SessionManager

    private lateinit var tvFileNameKtpAlmarhum: TextView
    private lateinit var tvFileNameKkAlmarhum: TextView
    private lateinit var tvFileNameKtpPelapor: TextView
    private lateinit var tvFileNameRs: TextView
    
    private var uriKtpAlmarhum: Uri? = null
    private var uriKkAlmarhum: Uri? = null
    private var uriKtpPelapor: Uri? = null
    private var uriRs: Uri? = null

    private var currentPhotoPath: String? = null
    private var cameraImageUri: Uri? = null
    private var activeUploader: String = "" // "KTP_ALMARHUM", "KK_ALMARHUM", "KTP_PELAPOR", "RS"
    private var editId: Int = 0


    private val pickKtpAlmarhum = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { 
            uriKtpAlmarhum = it
            tvFileNameKtpAlmarhum.text = FileUtils.getFileName(this, it) 
        }
    }
    private val pickKkAlmarhum = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { 
            uriKkAlmarhum = it
            tvFileNameKkAlmarhum.text = FileUtils.getFileName(this, it) 
        }
    }
    private val pickKtpPelapor = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { 
            uriKtpPelapor = it
            tvFileNameKtpPelapor.text = FileUtils.getFileName(this, it) 
        }
    }
    private val pickRsLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { 
            uriRs = it
            tvFileNameRs.text = FileUtils.getFileName(this, it) 
        }
    }

    // Peluncur untuk Kamera
    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(this, "Izin kamera diperlukan untuk mengambil foto", Toast.LENGTH_SHORT).show()
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraImageUri?.let { uri ->
                when (activeUploader) {
                    "KTP_ALMARHUM" -> {
                        uriKtpAlmarhum = uri
                        tvFileNameKtpAlmarhum.text = FileUtils.getFileName(this, uri)
                    }
                    "KK_ALMARHUM" -> {
                        uriKkAlmarhum = uri
                        tvFileNameKkAlmarhum.text = FileUtils.getFileName(this, uri)
                    }
                    "KTP_PELAPOR" -> {
                        uriKtpPelapor = uri
                        tvFileNameKtpPelapor.text = FileUtils.getFileName(this, uri)
                    }
                    "RS" -> {
                        uriRs = uri
                        tvFileNameRs.text = FileUtils.getFileName(this, uri)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // SECURE BY DESIGN: Mencegah screenshot dan screen recording (M2 - Insecure Data Storage)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        // SECURE BY DESIGN: Deteksi Root & Emulator (M8 & M9)
        if (SecurityUtil.isDeviceRooted() || SecurityUtil.isRunningOnEmulator()) {
            SecurityUtil.showSecurityWarning(this)
            return
        }

        sessionManager = SessionManager(this)
        if (!sessionManager.isLoggedIn()) {
            val intent = Intent(this, WelcomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }
        setContentView(R.layout.activity_kematian)
        setupAutoFill()

        drawerLayout = findViewById(R.id.drawerLayout)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        SidebarUtil.initSidebar(this, drawerLayout)
        toolbar.setNavigationOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) drawerLayout.closeDrawer(GravityCompat.START)
                else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })

        setupDropdowns()
        initUploadButtons()
        setupDatePickers()
        
        findViewById<MaterialButton>(R.id.btnSubmit).setOnClickListener {
            submitForm()
        }

        if (intent.hasExtra("EDIT_ID")) {
            editId = intent.getIntExtra("EDIT_ID", 0)
            if (editId > 0) {
                findViewById<MaterialButton>(R.id.btnSubmit).text = "SIMPAN PERUBAHAN"
                loadEditData(editId)
            }
        }
    }
    private fun loadEditData(id: Int) {
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        if (progressBar != null) progressBar.visibility = android.view.View.VISIBLE

        RetrofitClient.getInstance(this).getDetailPengajuan(id).enqueue(object : retrofit2.Callback<com.ta.sindesa.models.DetailPengajuanResponse> {
            override fun onResponse(call: retrofit2.Call<com.ta.sindesa.models.DetailPengajuanResponse>, response: retrofit2.Response<com.ta.sindesa.models.DetailPengajuanResponse>) {
                if (progressBar != null) progressBar.visibility = android.view.View.GONE
                if (response.isSuccessful && response.body()?.success == true) {
                    try {
                        val dataTambahanMap = response.body()?.data?.dataTambahan
                        if (dataTambahanMap != null) {
                            fun setEditText(idName: String, vararg keyNames: String) {
                                for (key in keyNames) {
                                    val value = dataTambahanMap[key]?.toString() ?: ""
                                    if (value.isNotEmpty()) {
                                        val resId = resources.getIdentifier(idName, "id", packageName)
                                        if (resId != 0) {
                                            val view = findViewById<android.view.View>(resId)
                                            if (view is com.google.android.material.textfield.TextInputEditText) {
                                                view.setText(value)
                                            } else if (view is android.widget.AutoCompleteTextView) {
                                                view.setText(value, false)
                                            }
                                        }
                                        break
                                    }
                                }
                            }

                            setEditText("etNikAlmarhum", "nik_almarhum")
                            setEditText("etNoKkAlmarhum", "kk_almarhum")
                            setEditText("etNamaAlmarhum", "nama_almarhum")
                            setEditText("etTempatLahirAlmarhum", "tempat_lahir_almarhum")
                            setEditText("etTanggalLahirAlmarhum", "tanggal_lahir_almarhum")
                            setEditText("spinJenisKelaminAlmarhum", "jenis_kelamin_almarhum")
                            setEditText("spinAgamaAlmarhum", "agama_almarhum")
                            setEditText("spinKewarganegaraanAlmarhum", "kewarganegaraan_almarhum")
                            setEditText("spinStatusPerkawinanAlmarhum", "status_perkawinan_almarhum")
                            setEditText("etPekerjaanAlmarhum", "pekerjaan_almarhum")
                            setEditText("etAlamatAlmarhum", "alamat_almarhum")
                            setEditText("etTanggalWafat", "tanggal_kematian")
                            setEditText("etUmur", "umur_kematian")
                            setEditText("etTempatKematian", "tempat_kematian")
                            setEditText("etSebabKematian", "sebab_kematian")
                            setEditText("etNamaPelapor", "nama_pelapor")
                            setEditText("etHubunganPelapor", "hubungan_pelapor")

                            // Optional: set text info if files exist
                            for (key in dataTambahanMap.keys) {
                                if (key.startsWith("file_") || key.startsWith("berkas_") || key.startsWith("foto_")) {
                                    if (dataTambahanMap[key] != null) {
                                        val tvId = "tvFileName" + key.replace("file_", "").replace("berkas_", "").replace("foto_", "").split("_").joinToString("") { it.replaceFirstChar(Char::uppercase) }
                                        val resId = resources.getIdentifier(tvId, "id", packageName)
                                        if (resId != 0) {
                                            findViewById<android.widget.TextView>(resId)?.text = "File sudah tersimpan"
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            override fun onFailure(call: retrofit2.Call<com.ta.sindesa.models.DetailPengajuanResponse>, t: Throwable) {
                if (progressBar != null) progressBar.visibility = android.view.View.GONE
                Toast.makeText(this@KematianActivity, "Gagal memuat data editan", Toast.LENGTH_SHORT).show()
            }
        })
    }


    private fun setupDatePickers() {
        val etTanggalLahirAlmarhum = findViewById<TextInputEditText>(R.id.etTanggalLahirAlmarhum)
        val etTanggalWafat = findViewById<TextInputEditText>(R.id.etTanggalWafat)
        etTanggalLahirAlmarhum.setOnClickListener {
            showDatePickerDialog(etTanggalLahirAlmarhum)
        }
        etTanggalLahirAlmarhum.isFocusable = false
        etTanggalLahirAlmarhum.isClickable = true
        etTanggalWafat.setOnClickListener {
            showDatePickerDialog(etTanggalWafat)
        }
        etTanggalWafat.isFocusable = false
        etTanggalWafat.isClickable = true
    }

    private fun showDatePickerDialog(editText: TextInputEditText) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                val date = Calendar.getInstance()
                date.set(selectedYear, selectedMonth, selectedDay)
                val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                editText.setText(format.format(date.time))
            },
            year, month, day
        )
        datePickerDialog.show()
    }

    private fun setupDropdowns() {
        val jenisKelamin = arrayOf("Laki-laki", "Perempuan")
        val adapterKelamin = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, jenisKelamin)
        findViewById<AutoCompleteTextView>(R.id.spinJenisKelaminAlmarhum).setAdapter(adapterKelamin)

        val agama = arrayOf("Islam", "Kristen Protestan", "Katolik", "Hindu", "Buddha", "Konghucu")
        val adapterAgama = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, agama)
        findViewById<AutoCompleteTextView>(R.id.spinAgamaAlmarhum).setAdapter(adapterAgama)

        val statusKawin = arrayOf("Belum Kawin", "Kawin", "Cerai Hidup", "Cerai Mati")
        val adapterStatus = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, statusKawin)
        findViewById<AutoCompleteTextView>(R.id.spinStatusPerkawinanAlmarhum).setAdapter(adapterStatus)

        val kewarganegaraan = arrayOf("Indonesia", "WNA")
        findViewById<AutoCompleteTextView>(R.id.spinKewarganegaraanAlmarhum)?.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, kewarganegaraan))
    }

    private fun initUploadButtons() {
        tvFileNameKtpAlmarhum = findViewById(R.id.tvFileNameKtpAlmarhum)
        tvFileNameKkAlmarhum = findViewById(R.id.tvFileNameKkAlmarhum)
        tvFileNameKtpPelapor = findViewById(R.id.tvFileNameKtpPelapor)
        tvFileNameRs = findViewById(R.id.tvFileNameRs)

        findViewById<MaterialButton>(R.id.btnUploadKtpAlmarhum).setOnClickListener { showImagePickerDialog("KTP_ALMARHUM") }
        findViewById<MaterialButton>(R.id.btnUploadKkAlmarhum).setOnClickListener { showImagePickerDialog("KK_ALMARHUM") }
        findViewById<MaterialButton>(R.id.btnUploadKtpPelapor).setOnClickListener { showImagePickerDialog("KTP_PELAPOR") }
        findViewById<MaterialButton>(R.id.btnUploadRs).setOnClickListener { showImagePickerDialog("RS") }
    }

    private fun showImagePickerDialog(type: String) {
        activeUploader = type
        val options = arrayOf("Kamera", "Galeri / File")
        AlertDialog.Builder(this)
            .setTitle("Pilih Dokumen (Gambar/PDF)")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            launchCamera()
                        } else {
                            requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        }
                    }
                    1 -> {
                        when (type) {
                            "KTP_ALMARHUM" -> pickKtpAlmarhum.launch("*/*")
                            "KK_ALMARHUM" -> pickKkAlmarhum.launch("*/*")
                            "KTP_PELAPOR" -> pickKtpPelapor.launch("*/*")
                            "RS" -> pickRsLauncher.launch("*/*")
                        }
                    }
                }
            }
            .show()
    }

    private fun launchCamera() {
        cameraImageUri = FileUtils.createImageUri(this)
        cameraImageUri?.let { cameraLauncher.launch(it) }
    }

    private fun submitForm() {
        val etNikAlmarhum = findViewById<TextInputEditText>(R.id.etNikAlmarhum)
        val etNoKkAlmarhum = findViewById<TextInputEditText>(R.id.etNoKkAlmarhum)
        val etNamaAlmarhum = findViewById<TextInputEditText>(R.id.etNamaAlmarhum)
        val etTempatLahirAlmarhum = findViewById<TextInputEditText>(R.id.etTempatLahirAlmarhum)
        val etTanggalLahirAlmarhum = findViewById<TextInputEditText>(R.id.etTanggalLahirAlmarhum)
        val spinJenisKelaminAlmarhum = findViewById<AutoCompleteTextView>(R.id.spinJenisKelaminAlmarhum)
        val spinAgamaAlmarhum = findViewById<AutoCompleteTextView>(R.id.spinAgamaAlmarhum)
        val spinKewarganegaraanAlmarhum = findViewById<AutoCompleteTextView>(R.id.spinKewarganegaraanAlmarhum)
        val spinStatusPerkawinanAlmarhum = findViewById<AutoCompleteTextView>(R.id.spinStatusPerkawinanAlmarhum)
        val etPekerjaanAlmarhum = findViewById<TextInputEditText>(R.id.etPekerjaanAlmarhum)
        val etAlamatAlmarhum = findViewById<TextInputEditText>(R.id.etAlamatAlmarhum)
        val etTanggalWafat = findViewById<TextInputEditText>(R.id.etTanggalWafat)
        val etUmur = findViewById<TextInputEditText>(R.id.etUmur)
        val etTempatKematian = findViewById<TextInputEditText>(R.id.etTempatKematian)
        val etSebabKematian = findViewById<TextInputEditText>(R.id.etSebabKematian)
        val etNamaPelapor = findViewById<TextInputEditText>(R.id.etNamaPelapor)
        val etHubunganPelapor = findViewById<TextInputEditText>(R.id.etHubunganPelapor)
        val cbAgree = findViewById<CheckBox>(R.id.cbAgree)

        if (etNikAlmarhum.text.toString().length != 16) {
            etNikAlmarhum.error = "NIK harus 16 digit"
            etNikAlmarhum.requestFocus()
            return
        }
        if (etNoKkAlmarhum.text.toString().length != 16) {
            etNoKkAlmarhum.error = "No KK harus 16 digit"
            etNoKkAlmarhum.requestFocus()
            return
        }
        if (etNamaAlmarhum.text.isNullOrEmpty()) {
            etNamaAlmarhum.error = "Nama Almarhum wajib diisi"
            etNamaAlmarhum.requestFocus()
            return
        }
        if (etTempatLahirAlmarhum.text.isNullOrEmpty()) {
            etTempatLahirAlmarhum.error = "Tempat Lahir wajib diisi"
            etTempatLahirAlmarhum.requestFocus()
            return
        }
        if (etTanggalLahirAlmarhum.text.isNullOrEmpty()) {
            etTanggalLahirAlmarhum.error = "Tanggal Lahir wajib diisi"
            etTanggalLahirAlmarhum.requestFocus()
            return
        }
        if (etTanggalWafat.text.isNullOrEmpty()) {
            etTanggalWafat.error = "Tanggal Wafat wajib diisi"
            etTanggalWafat.requestFocus()
            return
        }
        if (etNamaPelapor.text.isNullOrEmpty()) {
            etNamaPelapor.error = "Nama Pelapor wajib diisi"
            etNamaPelapor.requestFocus()
            return
        }
        if (editId == 0 && (uriKtpAlmarhum == null || uriKkAlmarhum == null || uriKtpPelapor == null)) {
            Toast.makeText(this, "Harap lengkapi berkas wajib (KTP Almarhum, KK, KTP Pelapor)", Toast.LENGTH_SHORT).show()
            return
        }
        if (!cbAgree.isChecked) {
            Toast.makeText(this, "Harap setujui pernyataan di atas", Toast.LENGTH_SHORT).show()
            return
        }

        val btnSubmit = findViewById<MaterialButton>(R.id.btnSubmit)
        btnSubmit.isEnabled = false
        btnSubmit.text = if (editId > 0) "Menyimpan Perubahan..." else "Mengirim..."

        val parts = mutableMapOf<String, RequestBody>()
        parts["nik_almarhum"] = etNikAlmarhum.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["kk_almarhum"] = etNoKkAlmarhum.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["nama_almarhum"] = etNamaAlmarhum.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["tempat_lahir_almarhum"] = etTempatLahirAlmarhum.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["tanggal_lahir_almarhum"] = etTanggalLahirAlmarhum.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["jenis_kelamin_almarhum"] = spinJenisKelaminAlmarhum.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["agama_almarhum"] = spinAgamaAlmarhum.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["kewarganegaraan_almarhum"] = spinKewarganegaraanAlmarhum.text.toString().ifEmpty { "Indonesia" }.toRequestBody("text/plain".toMediaTypeOrNull())
        parts["status_perkawinan_almarhum"] = spinStatusPerkawinanAlmarhum.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["pekerjaan_almarhum"] = etPekerjaanAlmarhum.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["alamat_almarhum"] = etAlamatAlmarhum.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["tanggal_kematian"] = etTanggalWafat.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["umur_kematian"] = etUmur.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["tempat_kematian"] = etTempatKematian.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["sebab_kematian"] = etSebabKematian.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["nama_pelapor"] = etNamaPelapor.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["hubungan_pelapor"] = etHubunganPelapor.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        // Kirim NIK pelapor (warga yang login) agar backend bisa lookup user yang terdaftar
        parts["nik_pelapor"] = sessionManager.getNikUser().orEmpty().toRequestBody("text/plain".toMediaTypeOrNull())

        val berkasKtpAlmarhumPart = uriKtpAlmarhum?.let { prepareFilePart("berkas_ktp_almarhum", it) }
        val berkasKkAlmarhumPart = uriKkAlmarhum?.let { prepareFilePart("berkas_kk_almarhum", it) }
        val berkasKtpPelaporPart = uriKtpPelapor?.let { prepareFilePart("berkas_ktp_pelapor", it) }
        val berkasRsPart = uriRs?.let { prepareFilePart("berkas_rs", it) }

        val rbEditId = if (editId > 0) editId.toString().toRequestBody("text/plain".toMediaTypeOrNull()) else null

        RetrofitClient.getInstance(this).submitKematian(
            parts["nik_almarhum"]!!, parts["kk_almarhum"]!!, parts["nama_almarhum"]!!,
            parts["tempat_lahir_almarhum"]!!, parts["tanggal_lahir_almarhum"]!!, parts["jenis_kelamin_almarhum"]!!,
            parts["agama_almarhum"]!!, parts["kewarganegaraan_almarhum"]!!, parts["status_perkawinan_almarhum"]!!, parts["pekerjaan_almarhum"]!!,
            parts["alamat_almarhum"]!!, parts["tanggal_kematian"]!!, parts["umur_kematian"]!!, parts["tempat_kematian"]!!,
            parts["sebab_kematian"]!!, parts["nama_pelapor"]!!, parts["hubungan_pelapor"]!!,
            parts["nik_pelapor"]!!,
            berkasKtpAlmarhumPart, berkasKkAlmarhumPart, berkasKtpPelaporPart, berkasRsPart
        , rbEditId).enqueue(object : retrofit2.Callback<com.ta.sindesa.models.LoginResponse> {
            override fun onResponse(call: retrofit2.Call<com.ta.sindesa.models.LoginResponse>, response: retrofit2.Response<com.ta.sindesa.models.LoginResponse>) {
                btnSubmit.isEnabled = true
                btnSubmit.text = if (editId > 0) "SIMPAN PERUBAHAN" else "KIRIM PENGAJUAN"
                
                // Hapus file temporary di cache setelah upload selesai (OWASP M2)
                FileUtils.clearCacheFiles(this@KematianActivity)

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        AlertDialog.Builder(this@KematianActivity)
                            .setTitle("Berhasil")
                            .setMessage("Pengajuan surat kematian telah berhasil dikirim dan akan segera diproses.")
                            .setPositiveButton("OK") { _, _ -> finish() }
                            .setCancelable(false)
                            .show()
                    } else {
                        val msg = body?.message ?: "Server merespon negatif (Success: false)"
                        AlertDialog.Builder(this@KematianActivity)
                            .setTitle("Gagal")
                            .setMessage(msg)
                            .setPositiveButton("Tutup", null)
                            .show()
                    }
                } else {
                    val errorDetail = response.errorBody()?.string() ?: "Unknown error"
                    android.util.Log.e("API_DEBUG", "Error Body: $errorDetail")
                    AlertDialog.Builder(this@KematianActivity)
                        .setTitle("Server Error (${response.code()})")
                        .setMessage("Detail: $errorDetail\n\nCek Logcat 'API_DEBUG' untuk detail.")
                        .setPositiveButton("Tutup", null)
                        .show()
                }
            }

            override fun onFailure(call: retrofit2.Call<com.ta.sindesa.models.LoginResponse>, t: Throwable) {
                btnSubmit.isEnabled = true
                btnSubmit.text = if (editId > 0) "SIMPAN PERUBAHAN" else "KIRIM PENGAJUAN"
                
                FileUtils.clearCacheFiles(this@KematianActivity)
                
                val host = RetrofitClient.HOSTNAME
                val errorMessage = when (t) {
                    is com.google.gson.JsonSyntaxException -> {
                        "Respon Server Error (Bukan JSON).\n\n" +
                        "Penyebab: PHP mengirim teks biasa atau terdapat error di script PHP.\n" +
                        "Cek Logcat 'API_DEBUG' untuk melihat teks tersebut."
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

                AlertDialog.Builder(this@KematianActivity)
                    .setTitle("Gagal Terhubung ke Server")
                    .setMessage(errorMessage)
                    .setPositiveButton("Tutup", null)
                    .show()
                
                android.util.Log.e("API_DEBUG", "Submit Failure: ${t.message}", t)
                android.util.Log.e("API_DEBUG", "HINT: Jika errornya 'Expected BEGIN_OBJECT but was STRING', buka Logcat, cari 'http' dan lihat Response Body dari server.")
            }
        })
    }

    private fun prepareFilePart(partName: String, fileUri: Uri): MultipartBody.Part? {
        val file = FileUtils.uriToFile(this, fileUri)
        val requestFile = file.asRequestBody(contentResolver.getType(fileUri)?.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(partName, file.name, requestFile)
    }

    

    

    

    

    private fun setupAutoFill() {
        val session = SessionManager(this)
        
        fun fillField(idName: String, value: String?) {
            if (value.isNullOrEmpty()) return
            try {
                val id = resources.getIdentifier(idName, "id", packageName)
                if (id == 0) return
                val view = findViewById<android.view.View>(id)
                if (view is com.google.android.material.textfield.TextInputEditText) {
                    view.setText(value)
                    
                    
                } else if (view is android.widget.AutoCompleteTextView) {
                    view.setText(value, false)
                    
                    
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        
        fillField("etNik", session.getNikUser())
        fillField("etNama", session.getNamaUser())
        fillField("etEmail", session.getEmailUser())
        fillField("etNoKk", session.getNoKk())
        fillField("spinAgama", session.getAgama())
        fillField("etAgama", session.getAgama())
        fillField("spinJenisKelamin", session.getJenisKelamin())
        fillField("spinStatusPerkawinan", session.getStatusPerkawinan())
        fillField("etTempatLahir", session.getTempatLahir())
        fillField("etTanggalLahir", session.getTanggalLahir())
        fillField("etPekerjaan", session.getPekerjaan())
        fillField("etAlamat", session.getAlamatLengkap())
        fillField("etProvinsi", session.getProvinsi())
        fillField("etKota", session.getKota())
        fillField("etKecamatan", session.getKecamatan())
        fillField("etDesa", session.getKelurahanDesa())
        fillField("etRtRw", session.getRtRw())
        fillField("etNoHp", session.getNoHp())
        fillField("spinKewarganegaraan", session.getKewarganegaraan())
        
        fillField("etNikPelapor", session.getNikUser())
        fillField("etNamaPelapor", session.getNamaUser())
        fillField("etTempatLahirPelapor", session.getTempatLahir())
        fillField("etTanggalLahirPelapor", session.getTanggalLahir())
        fillField("etPekerjaanPelapor", session.getPekerjaan())
        fillField("etAlamatPelapor", session.getAlamatLengkap())
        fillField("spinAgamaPelapor", session.getAgama())
        
        fillField("etNikPemohon", session.getNikUser())
        fillField("etNamaPemohon", session.getNamaUser())
    }
  }

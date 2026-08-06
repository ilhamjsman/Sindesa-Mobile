package com.ta.sindesa

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
import java.util.Date
import java.util.Locale

class KtpActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var sessionManager: SessionManager

    // Penampung nama file yang diupload
    private lateinit var tvFileNameKk: TextView
    private lateinit var tvFileNameKtpLama: TextView
    private var uriKk: Uri? = null
    private var uriKtpLama: Uri? = null

    private var currentPhotoPath: String? = null
    private var cameraImageUri: Uri? = null
    private var activeUploader: String = "" // "KK" atau "KTPLAMA"
    private var editId: Int = 0


    // Peluncur (Launcher) untuk Galeri
    private val pickKkGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            uriKk = it
            tvFileNameKk.text = FileUtils.getFileName(this, it)
        }
    }

    private val pickKtpLamaGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            uriKtpLama = it
            tvFileNameKtpLama.text = FileUtils.getFileName(this, it)
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
                if (activeUploader == "KK") {
                    uriKk = uri
                    tvFileNameKk.text = FileUtils.getFileName(this, uri)
                } else if (activeUploader == "KTPLAMA") {
                    uriKtpLama = uri
                    tvFileNameKtpLama.text = FileUtils.getFileName(this, uri)
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

        setContentView(R.layout.activity_ktp)
        setupAutoFill()

        drawerLayout = findViewById(R.id.drawerLayout)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)

        // 1. Sidebar Hamburger
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Initialize Sidebar using Utility
        SidebarUtil.initSidebar(this, drawerLayout)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) drawerLayout.closeDrawer(GravityCompat.START)
                else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })

        // 2. Setup Dropdown Jenis Kelamin
        val jenisKelamin = arrayOf("Laki-laki", "Perempuan")
        val adapterKelamin = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, jenisKelamin)
        findViewById<AutoCompleteTextView>(R.id.spinJenisKelamin).setAdapter(adapterKelamin)

        // 3. Setup Dropdown Agama
        val agama = arrayOf("Islam", "Kristen Protestan", "Katolik", "Hindu", "Buddha", "Konghucu")
        val adapterAgama = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, agama)
        findViewById<AutoCompleteTextView>(R.id.spinAgama).setAdapter(adapterAgama)

        // 4. Setup Dropdown Status Perkawinan
        val statusKawin = arrayOf("Belum Kawin", "Kawin", "Cerai Hidup", "Cerai Mati")
        val adapterStatus = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, statusKawin)
        findViewById<AutoCompleteTextView>(R.id.spinStatusPerkawinan).setAdapter(adapterStatus)

        // 5. Setup Tombol Upload
        tvFileNameKk = findViewById(R.id.tvFileNameKk)
        tvFileNameKtpLama = findViewById(R.id.tvFileNameKtpLama)

        findViewById<MaterialButton>(R.id.btnUploadKk).setOnClickListener {
            showImagePickerDialog("KK")
        }
        findViewById<MaterialButton>(R.id.btnUploadKtpLama).setOnClickListener {
            showImagePickerDialog("KTPLAMA")
        }

        // 6. Setup Tombol Submit
        findViewById<MaterialButton>(R.id.btnSubmitKtp).setOnClickListener {
            submitForm()
        }

        // 7. Setup Date Picker Tanggal Lahir
        val etTanggalLahir = findViewById<TextInputEditText>(R.id.etTanggalLahir)
        etTanggalLahir.isFocusable = false
        etTanggalLahir.isClickable = true
        etTanggalLahir.setOnClickListener {
            val c = java.util.Calendar.getInstance()
            val year = c.get(java.util.Calendar.YEAR)
            val month = c.get(java.util.Calendar.MONTH)
            val day = c.get(java.util.Calendar.DAY_OF_MONTH)

            val dpd = android.app.DatePickerDialog(this, { _, year, monthOfYear, dayOfMonth ->
                val date = String.format("%04d-%02d-%02d", year, monthOfYear + 1, dayOfMonth)
                etTanggalLahir.setText(date)
            }, year, month, day)
            dpd.show()
        }

        // === EDIT MODE TRIGGER ===
        if (intent.hasExtra("EDIT_ID")) {
            editId = intent.getIntExtra("EDIT_ID", 0)
            if (editId > 0) {
                findViewById<MaterialButton>(R.id.btnSubmitKtp).text = "SIMPAN PERUBAHAN"
                loadEditData(editId)
            }
        }
    }

    private fun showImagePickerDialog(type: String) {
        activeUploader = type
        val options = arrayOf("Kamera", "Galeri / File")
        AlertDialog.Builder(this)
            .setTitle("Pilih Sumber Dokumen")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            launchCamera()
                        } else {
                            requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        }
                    }
                    1 -> if (type == "KK") pickKkGalleryLauncher.launch("*/*") else pickKtpLamaGalleryLauncher.launch("*/*")
                }
            }
            .show()
    }

    private fun launchCamera() {
        val photoFile: File? = try {
            createImageFile()
        } catch (ex: Exception) {
            Toast.makeText(this, "Gagal membuat file gambar", Toast.LENGTH_SHORT).show()
            null
        }

        photoFile?.also {
            cameraImageUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                it
            )
            cameraLauncher.launch(cameraImageUri)
        }
    }

    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir("Pictures")
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun submitForm() {
        val etNik = findViewById<TextInputEditText>(R.id.etNik)
        val etNoKk = findViewById<TextInputEditText>(R.id.etNoKk)
        val etNama = findViewById<TextInputEditText>(R.id.etNama)
        val etTempatLahir = findViewById<TextInputEditText>(R.id.etTempatLahir)
        val etTanggalLahir = findViewById<TextInputEditText>(R.id.etTanggalLahir)
        val spinJenisKelamin = findViewById<AutoCompleteTextView>(R.id.spinJenisKelamin)
        val spinAgama = findViewById<AutoCompleteTextView>(R.id.spinAgama)
        val spinStatusPerkawinan = findViewById<AutoCompleteTextView>(R.id.spinStatusPerkawinan)
        val etPekerjaan = findViewById<TextInputEditText>(R.id.etPekerjaan)
        val etAlamat = findViewById<TextInputEditText>(R.id.etAlamat)

        // Validasi Sederhana
        if (etNik.text.toString().length != 16) {
            etNik.error = "NIK harus 16 digit"
            etNik.requestFocus()
            return
        }
        if (etNoKk.text.toString().length != 16) {
            etNoKk.error = "No KK harus 16 digit"
            etNoKk.requestFocus()
            return
        }
        if (etNama.text.isNullOrEmpty()) {
            etNama.error = "Nama wajib diisi"
            etNama.requestFocus()
            return
        }
        if (etTempatLahir.text.isNullOrEmpty()) {
            etTempatLahir.error = "Tempat lahir wajib diisi"
            etTempatLahir.requestFocus()
            return
        }
        if (etTanggalLahir.text.isNullOrEmpty()) {
            etTanggalLahir.error = "Tanggal lahir wajib diisi"
            etTanggalLahir.requestFocus()
            return
        }
        if (etAlamat.text.isNullOrEmpty()) {
            etAlamat.error = "Alamat wajib diisi"
            etAlamat.requestFocus()
            return
        }
        if (editId == 0 && (uriKk == null)) {
            Toast.makeText(this, "Harap unggah berkas KK", Toast.LENGTH_SHORT).show()
            return
        }

        val btnSubmit = findViewById<MaterialButton>(R.id.btnSubmitKtp)
        btnSubmit.isEnabled = false
        btnSubmit.text = if (editId > 0) "Menyimpan Perubahan..." else "Mengirim..."

        // Persiapkan Data Text (RequestBody)
        val nik = etNik.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val noKk = etNoKk.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val nama = etNama.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val tempatLahir = etTempatLahir.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val tanggalLahir = etTanggalLahir.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val jenisKelamin = spinJenisKelamin.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val agama = spinAgama.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val statusKawin = spinStatusPerkawinan.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val pekerjaan = etPekerjaan.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val alamat = etAlamat.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())

        // Persiapkan Data File (MultipartBody)
        val berkasKkPart = uriKk?.let { prepareFilePart("berkas_kk", it) }
        val berkasKtpLamaPart = uriKtpLama?.let { prepareFilePart("berkas_ktp_lama", it) }

        // Kirim ke Backend
        val rbEditId = if (editId > 0) editId.toString().toRequestBody("text/plain".toMediaTypeOrNull()) else null

        RetrofitClient.getInstance(this).submitKtp(
            nik, noKk, nama, tempatLahir, tanggalLahir, jenisKelamin, agama, statusKawin, pekerjaan, alamat,
            berkasKkPart, berkasKtpLamaPart
        , rbEditId).enqueue(object : retrofit2.Callback<com.ta.sindesa.models.LoginResponse> {
            override fun onResponse(call: retrofit2.Call<com.ta.sindesa.models.LoginResponse>, response: retrofit2.Response<com.ta.sindesa.models.LoginResponse>) {
                btnSubmit.isEnabled = true
                btnSubmit.text = if (editId > 0) "SIMPAN PERUBAHAN" else "KIRIM PENGAJUAN"
                
                // Hapus file temporary di cache setelah upload selesai (OWASP M2)
                clearCacheFiles()

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        AlertDialog.Builder(this@KtpActivity)
                            .setTitle("Berhasil")
                            .setMessage("Pengajuan KTP baru/perubahan telah berhasil dikirim.")
                            .setPositiveButton("OK") { _, _ -> finish() }
                            .setCancelable(false)
                            .show()
                    } else {
                        val msg = body?.message ?: "Server merespon negatif (Success: false)"
                        AlertDialog.Builder(this@KtpActivity)
                            .setTitle("Gagal")
                            .setMessage(msg)
                            .setPositiveButton("Tutup", null)
                            .show()
                    }
                } else {
                    val errorDetail = response.errorBody()?.string() ?: "Unknown error"
                    android.util.Log.e("API_DEBUG", "Error Body: $errorDetail")
                    AlertDialog.Builder(this@KtpActivity)
                        .setTitle("Server Error (${response.code()})")
                        .setMessage("Detail: $errorDetail\n\nCek Logcat 'API_DEBUG' untuk detail.")
                        .setPositiveButton("Tutup", null)
                        .show()
                }
            }

            override fun onFailure(call: retrofit2.Call<com.ta.sindesa.models.LoginResponse>, t: Throwable) {
                btnSubmit.isEnabled = true
                btnSubmit.text = if (editId > 0) "SIMPAN PERUBAHAN" else "KIRIM PENGAJUAN"
                clearCacheFiles()
                
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

                AlertDialog.Builder(this@KtpActivity)
                    .setTitle("Gagal Terhubung ke Server")
                    .setMessage(errorMessage)
                    .setPositiveButton("Tutup", null)
                    .show()
                
                android.util.Log.e("API_DEBUG", "Submit Failure: ${t.message}", t)
                android.util.Log.e("API_DEBUG", "HINT: Jika errornya 'Expected BEGIN_OBJECT but was STRING', buka Logcat, cari 'http' dan lihat Response Body dari server.")
            }
        })
    }

    private fun prepareFilePart(partName: String, fileUri: Uri): MultipartBody.Part {
        val file = FileUtils.uriToFile(this, fileUri)
        val requestFile = file.asRequestBody(contentResolver.getType(fileUri)?.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(partName, file.name, requestFile)
    }

    private fun clearCacheFiles() {
        FileUtils.clearCacheFiles(this)
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



                                                        setEditText("etNik", "nik")
                            setEditText("etNoKk", "no_kk")
                            setEditText("etNama", "nama")
                            setEditText("etTempatLahir", "tempat_lahir")
                            setEditText("etTanggalLahir", "tanggal_lahir")
                            setEditText("spinJenisKelamin", "jenis_kelamin")
                            setEditText("spinAgama", "agama")
                            setEditText("spinStatusPerkawinan", "status_perkawinan")
                            setEditText("etPekerjaan", "pekerjaan")
                            setEditText("etAlamat", "alamat")

                            // Optional: set text info if files exist
                            
                            for (key in dataTambahanMap.keys) {
                                if (key.startsWith("file_") || key.startsWith("berkas_") || key.startsWith("foto_")) continue
                                val parts = key.split("_")
                                val camelCaseKey = parts[0] + parts.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercase) }
                                setEditText("et" + camelCaseKey.replaceFirstChar(Char::uppercase), key)
                                setEditText("spin" + camelCaseKey.replaceFirstChar(Char::uppercase), key)
                            }

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
                Toast.makeText(this@KtpActivity, "Gagal memuat data editan", Toast.LENGTH_SHORT).show()
            }
        })
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

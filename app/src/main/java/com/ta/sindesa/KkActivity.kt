package com.ta.sindesa

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.View
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
import com.ta.sindesa.models.LoginResponse
import com.ta.sindesa.utils.FileUtils
import com.ta.sindesa.utils.SecurityUtil
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KkActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var sessionManager: SessionManager

    private lateinit var spinTujuan: AutoCompleteTextView
    private lateinit var etNik: TextInputEditText
    private lateinit var etKkLama: TextInputEditText
    private lateinit var etNama: TextInputEditText
    private lateinit var etTempatLahir: TextInputEditText
    private lateinit var etTanggalLahir: TextInputEditText
    private lateinit var spinJenisKelamin: AutoCompleteTextView
    private lateinit var spinAgama: AutoCompleteTextView
    private lateinit var spinStatusPerkawinan: AutoCompleteTextView
    private lateinit var etPekerjaan: TextInputEditText
    private lateinit var etNamaKplKeluarga: TextInputEditText
    private lateinit var etAlamat: TextInputEditText
    private lateinit var etRt: TextInputEditText
    private lateinit var etRw: TextInputEditText
    private lateinit var cbAgree: CheckBox
    private lateinit var btnSubmit: MaterialButton

    private lateinit var tvFileNameKkLama: TextView
    private lateinit var tvFileNameNikah: TextView
    private lateinit var tvFileNameLain: TextView

    private var uriKkLama: Uri? = null
    private var uriNikah: Uri? = null
    private var uriLain: Uri? = null

    private var currentPhotoPath: String? = null
    private var cameraImageUri: Uri? = null
    private var activeUploader: String = "" // "KKLAMA", "NIKAH", "LAIN"
    private var editId: Int = 0


    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(this, "Izin kamera diperlukan untuk mengambil foto", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickKkLamaGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            uriKkLama = it
            tvFileNameKkLama.text = FileUtils.getFileName(this, it)
        }
    }
    private val pickNikahGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            uriNikah = it
            tvFileNameNikah.text = FileUtils.getFileName(this, it)
        }
    }
    private val pickLainGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            uriLain = it
            tvFileNameLain.text = FileUtils.getFileName(this, it)
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraImageUri?.let { uri ->
                when (activeUploader) {
                    "KKLAMA" -> {
                        uriKkLama = uri
                        tvFileNameKkLama.text = FileUtils.getFileName(this, uri)
                    }
                    "NIKAH" -> {
                        uriNikah = uri
                        tvFileNameNikah.text = FileUtils.getFileName(this, uri)
                    }
                    "LAIN" -> {
                        uriLain = uri
                        tvFileNameLain.text = FileUtils.getFileName(this, uri)
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

        setContentView(R.layout.activity_kk)
        setupAutoFill()

        initViews()
        setupSidebar()
        setupDropdowns()
        setupUploadButtons()

        // Setup Date Picker Tanggal Lahir
        etTanggalLahir.isFocusable = false
        etTanggalLahir.isClickable = true
        etTanggalLahir.setOnClickListener {
            val c = java.util.Calendar.getInstance()
            val year = c.get(java.util.Calendar.YEAR)
            val month = c.get(java.util.Calendar.MONTH)
            val day = c.get(java.util.Calendar.DAY_OF_MONTH)

            val dpd = android.app.DatePickerDialog(this, { _, year, monthOfYear, dayOfMonth ->
                val date = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, monthOfYear + 1, dayOfMonth)
                etTanggalLahir.setText(date)
            }, year, month, day)
            dpd.show()
        }

        btnSubmit.setOnClickListener {
            submitForm()
        }
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        spinTujuan = findViewById(R.id.spinTujuan)
        etNik = findViewById(R.id.etNik)
        etKkLama = findViewById(R.id.etKkLama)
        etNama = findViewById(R.id.etNama)
        etTempatLahir = findViewById(R.id.etTempatLahir)
        etTanggalLahir = findViewById(R.id.etTanggalLahir)
        spinJenisKelamin = findViewById(R.id.spinJenisKelamin)
        spinAgama = findViewById(R.id.spinAgama)
        spinStatusPerkawinan = findViewById(R.id.spinStatusPerkawinan)
        etPekerjaan = findViewById(R.id.etPekerjaan)
        etNamaKplKeluarga = findViewById(R.id.etNamaKplKeluarga)
        etAlamat = findViewById(R.id.etAlamat)
        etRt = findViewById(R.id.etRt)
        etRw = findViewById(R.id.etRw)
        cbAgree = findViewById(R.id.cbAgree)
        btnSubmit = findViewById(R.id.btnSubmit)
        tvFileNameKkLama = findViewById(R.id.tvFileNameKkLama)
        tvFileNameNikah = findViewById(R.id.tvFileNameNikah)
        tvFileNameLain = findViewById(R.id.tvFileNameLain)

        // === EDIT MODE TRIGGER ===
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



                                                        setEditText("spinTujuan", "tujuan_pengajuan")
                            setEditText("etNik", "nik")
                            setEditText("etKkLama", "kk_lama")
                            setEditText("etNama", "nama_lengkap")
                            setEditText("etTempatLahir", "tempat_lahir")
                            setEditText("etTanggalLahir", "tanggal_lahir")
                            setEditText("spinJenisKelamin", "jenis_kelamin")
                            setEditText("spinAgama", "agama")
                            setEditText("spinStatusPerkawinan", "status_perkawinan")
                            setEditText("etPekerjaan", "pekerjaan")
                            setEditText("etNamaKplKeluarga", "nama_kepala_keluarga")
                            setEditText("etAlamat", "alamat")
                            setEditText("etRt", "rt")
                            setEditText("etRw", "rw")

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
                Toast.makeText(this@KkActivity, "Gagal memuat data editan", Toast.LENGTH_SHORT).show()
            }
        })
    }


    private fun setupSidebar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
        SidebarUtil.initSidebar(this, drawerLayout)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) drawerLayout.closeDrawer(GravityCompat.START)
                else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
    }

    private fun setupDropdowns() {
        val tujuan = arrayOf("Pembuatan Baru", "Pembaharuan / Perubahan Data")
        spinTujuan.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, tujuan))

        val jenisKelamin = arrayOf("Laki-laki", "Perempuan")
        spinJenisKelamin.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, jenisKelamin))

        val agama = arrayOf("Islam", "Kristen Protestan", "Katolik", "Hindu", "Buddha", "Konghucu")
        spinAgama.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, agama))

        val statusKawin = arrayOf("Belum Kawin", "Kawin", "Cerai Hidup", "Cerai Mati")
        spinStatusPerkawinan.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, statusKawin))
    }

    private fun setupUploadButtons() {
        findViewById<MaterialButton>(R.id.btnUploadKkLama).setOnClickListener {
            showImagePickerDialog("KKLAMA")
        }
        findViewById<MaterialButton>(R.id.btnUploadNikah).setOnClickListener {
            showImagePickerDialog("NIKAH")
        }
        findViewById<MaterialButton>(R.id.btnUploadLain).setOnClickListener {
            showImagePickerDialog("LAIN")
        }
    }

    private fun showImagePickerDialog(type: String) {
        activeUploader = type
        val options = arrayOf("Kamera", "Galeri / File")
        AlertDialog.Builder(this)
            .setTitle("Pilih Dokumen (Gambar/PDF)")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            launchCamera()
                        } else {
                            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                    1 -> when (type) {
                        "KKLAMA" -> pickKkLamaGalleryLauncher.launch("*/*")
                        "NIKAH" -> pickNikahGalleryLauncher.launch("*/*")
                        "LAIN" -> pickLainGalleryLauncher.launch("*/*")
                    }
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
        val tujuan = spinTujuan.text.toString()
        val nik = etNik.text.toString()
        val kkLama = etKkLama.text.toString()
        val nama = etNama.text.toString()
        val tempatLahir = etTempatLahir.text.toString()
        val tglLahir = etTanggalLahir.text.toString()
        val jk = spinJenisKelamin.text.toString()
        val agama = spinAgama.text.toString()
        val status = spinStatusPerkawinan.text.toString()
        val pekerjaan = etPekerjaan.text.toString()
        val kplKeluarga = etNamaKplKeluarga.text.toString()
        val alamat = etAlamat.text.toString()
        val rt = etRt.text.toString()
        val rw = etRw.text.toString()

        if (tujuan.isEmpty()) {
            Toast.makeText(this, "Mohon pilih tujuan pengajuan", Toast.LENGTH_SHORT).show()
            return
        }
        if (nik.length != 16) {
            etNik.error = "NIK harus 16 digit"
            etNik.requestFocus()
            return
        }
        if (kkLama.length != 16) {
            etKkLama.error = "No KK Lama harus 16 digit"
            etKkLama.requestFocus()
            return
        }
        if (nama.isEmpty()) {
            etNama.error = "Nama wajib diisi"
            etNama.requestFocus()
            return
        }
        if (tempatLahir.isEmpty()) {
            etTempatLahir.error = "Tempat lahir wajib diisi"
            etTempatLahir.requestFocus()
            return
        }
        if (tglLahir.isEmpty()) {
            etTanggalLahir.error = "Tanggal lahir wajib diisi"
            etTanggalLahir.requestFocus()
            return
        }
        if (alamat.isEmpty()) {
            etAlamat.error = "Alamat wajib diisi"
            etAlamat.requestFocus()
            return
        }
        if (!cbAgree.isChecked) {
            Toast.makeText(this, "Anda harus menyetujui pernyataan data benar", Toast.LENGTH_SHORT).show()
            return
        }

        val rbTujuan = tujuan.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbNik = nik.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbKkLama = kkLama.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbNama = nama.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbTempatLahir = tempatLahir.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbTglLahir = tglLahir.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbJk = jk.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbAgama = agama.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbStatus = status.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbPekerjaan = pekerjaan.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbKplKeluarga = kplKeluarga.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbAlamat = alamat.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbRt = rt.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbRw = rw.toRequestBody("text/plain".toMediaTypeOrNull())

        val partKkLama = uriKkLama?.let { prepareFilePart("berkas_kk_lama", it) }
        val partNikah = uriNikah?.let { prepareFilePart("berkas_nikah", it) }
        val partLain = uriLain?.let { prepareFilePart("berkas_lain", it) }

        // Tampilkan loading
        btnSubmit.isEnabled = false
        btnSubmit.text = "MENGIRIM..."

        val rbEditId = if (editId > 0) editId.toString().toRequestBody("text/plain".toMediaTypeOrNull()) else null

        RetrofitClient.getInstance(this).submitKk(
            rbTujuan, rbNik, rbKkLama, rbNama, rbTempatLahir, rbTglLahir,
            rbJk, rbAgama, rbStatus, rbPekerjaan, rbKplKeluarga, rbAlamat, rbRt, rbRw,
            partKkLama, partNikah, partLain
        , rbEditId).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                btnSubmit.isEnabled = true
                btnSubmit.text = if (editId > 0) "SIMPAN PERUBAHAN" else "KIRIM PENGAJUAN"
                
                // Hapus file temporary di cache setelah upload selesai (OWASP M2)
                clearCacheFiles()
                
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        AlertDialog.Builder(this@KkActivity)
                            .setTitle("Berhasil")
                            .setMessage("Pengajuan Kartu Keluarga telah berhasil dikirim.")
                            .setPositiveButton("OK") { _, _ -> finish() }
                            .setCancelable(false)
                            .show()
                    } else {
                        val msg = body?.message ?: "Server merespon negatif (Success: false)"
                        AlertDialog.Builder(this@KkActivity)
                            .setTitle("Gagal")
                            .setMessage(msg)
                            .setPositiveButton("Tutup", null)
                            .show()
                    }
                } else {
                    val errorDetail = response.errorBody()?.string() ?: "Unknown error"
                    android.util.Log.e("API_DEBUG", "Error Body: $errorDetail")
                    AlertDialog.Builder(this@KkActivity)
                        .setTitle("Server Error (${response.code()})")
                        .setMessage("Detail: $errorDetail\n\nCek Logcat 'API_DEBUG' untuk detail.")
                        .setPositiveButton("Tutup", null)
                        .show()
                }
            }

            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
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

                AlertDialog.Builder(this@KkActivity)
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
        // FileUtils.uriToFile secara otomatis mengompres gambar sebelum dikirim
        val file = FileUtils.uriToFile(this, fileUri)
        val requestFile = file.asRequestBody(contentResolver.getType(fileUri)?.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(partName, file.name, requestFile)
    }

    private fun clearCacheFiles() {
        FileUtils.clearCacheFiles(this)
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
        fillField("spinAgamaPelapor", session.getAgama())
        
        fillField("etNikPemohon", session.getNikUser())
        fillField("etNamaPemohon", session.getNamaUser())
    }
  }

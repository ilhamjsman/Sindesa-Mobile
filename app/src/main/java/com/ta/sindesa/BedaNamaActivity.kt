package com.ta.sindesa

import android.app.DatePickerDialog
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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

class BedaNamaActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var sessionManager: SessionManager

    private lateinit var tvFileNameDok1: TextView
    private lateinit var tvFileNameDok2: TextView

    private var uriDok1: Uri? = null
    private var uriDok2: Uri? = null

    private var currentPhotoPath: String? = null
    private var cameraImageUri: Uri? = null
    private var activeUploader: String = "" // "DOK1" atau "DOK2"
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

    // Peluncur untuk Galeri
    private val pickDok1GalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            uriDok1 = it
            tvFileNameDok1.text = FileUtils.getFileName(this, it)
        }
    }

    private val pickDok2GalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            uriDok2 = it
            tvFileNameDok2.text = FileUtils.getFileName(this, it)
        }
    }

    // Peluncur untuk Kamera
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraImageUri?.let { uri ->
                if (activeUploader == "DOK1") {
                    uriDok1 = uri
                    tvFileNameDok1.text = FileUtils.getFileName(this, uri)
                } else if (activeUploader == "DOK2") {
                    uriDok2 = uri
                    tvFileNameDok2.text = FileUtils.getFileName(this, uri)
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

        setContentView(R.layout.activity_beda_nama)
        setupAutoFill()

        drawerLayout = findViewById(R.id.drawerLayout)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)

        // Sidebar Menu Trigger
        SidebarUtil.initSidebar(this, drawerLayout)
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
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

                            setEditText("etNikDok1", "nik_dok1")
                            setEditText("etNamaDok1", "nama_dok1")
                            setEditText("etTempatLahirDok1", "tempat_lahir_dok1")
                            setEditText("etTanggalLahirDok1", "tanggal_lahir_dok1")
                            setEditText("spinJenisKelaminDok1", "jenis_kelamin_dok1")
                            setEditText("etAlamatDok1", "alamat_dok1")
                            setEditText("etNamaDokumen2", "nama_dokumen2")
                            setEditText("etNoDokumen2", "nomor_dok2")
                            setEditText("etNamaDok2", "nama_dok2")
                            setEditText("etTempatLahirDok2", "tempat_lahir_dok2")
                            setEditText("etTanggalLahirDok2", "tanggal_lahir_dok2")
                            setEditText("spinJenisKelaminDok2", "jenis_kelamin_dok2")
                            setEditText("etAlamatDok2", "alamat_dok2")
                            setEditText("etPerbedaan", "data_berbeda")
                            setEditText("spinAcuan", "acuan_kebenaran")

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
                Toast.makeText(this@BedaNamaActivity, "Gagal memuat data editan", Toast.LENGTH_SHORT).show()
            }
        })
    }


    private fun setupDatePickers() {
        val etTanggalLahirDok1 = findViewById<TextInputEditText>(R.id.etTanggalLahirDok1)
        val etTanggalLahirDok2 = findViewById<TextInputEditText>(R.id.etTanggalLahirDok2)
        etTanggalLahirDok1.setOnClickListener { showDatePickerDialog(etTanggalLahirDok1) }
        etTanggalLahirDok2.setOnClickListener { showDatePickerDialog(etTanggalLahirDok2) }

        etTanggalLahirDok1.isFocusable = false
        etTanggalLahirDok1.isClickable = true
        etTanggalLahirDok2.isFocusable = false
        etTanggalLahirDok2.isClickable = true
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
        // Setup Dropdowns (Jenis Kelamin & Acuan)
        val jenisKelamin = arrayOf("Laki-laki", "Perempuan")
        val adapterKelamin = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, jenisKelamin)

        findViewById<AutoCompleteTextView>(R.id.spinJenisKelaminDok1).setAdapter(adapterKelamin)
        findViewById<AutoCompleteTextView>(R.id.spinJenisKelaminDok2).setAdapter(adapterKelamin)

        val acuanKebenaran = arrayOf("Dokumen 1 (KTP/KK)", "Dokumen 2")
        val adapterAcuan = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, acuanKebenaran)
        findViewById<AutoCompleteTextView>(R.id.spinAcuan).setAdapter(adapterAcuan)
    }

    private fun initUploadButtons() {
        // Setup File Upload Buttons
        tvFileNameDok1 = findViewById(R.id.tvFileNameDok1)
        tvFileNameDok2 = findViewById(R.id.tvFileNameDok2)

        findViewById<MaterialButton>(R.id.btnUploadDok1).setOnClickListener {
            showImagePickerDialog("DOK1")
        }

        findViewById<MaterialButton>(R.id.btnUploadDok2).setOnClickListener {
            showImagePickerDialog("DOK2")
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
                    1 -> if (type == "DOK1") pickDok1GalleryLauncher.launch("*/*") else pickDok2GalleryLauncher.launch("*/*")
                }
            }
            .show()
    }

    private fun launchCamera() {
        cameraImageUri = FileUtils.createImageUri(this)
        cameraImageUri?.let { cameraLauncher.launch(it) }
    }

    private fun submitForm() {
        val etNikDok1 = findViewById<TextInputEditText>(R.id.etNikDok1)
        val etNamaDok1 = findViewById<TextInputEditText>(R.id.etNamaDok1)
        val etTempatLahirDok1 = findViewById<TextInputEditText>(R.id.etTempatLahirDok1)
        val etTanggalLahirDok1 = findViewById<TextInputEditText>(R.id.etTanggalLahirDok1)
        val spinJenisKelaminDok1 = findViewById<AutoCompleteTextView>(R.id.spinJenisKelaminDok1)
        val etAlamatDok1 = findViewById<TextInputEditText>(R.id.etAlamatDok1)
        val etNamaDokumen2 = findViewById<TextInputEditText>(R.id.etNamaDokumen2)
        val etNoDokumen2 = findViewById<TextInputEditText>(R.id.etNoDokumen2)
        val etNamaDok2 = findViewById<TextInputEditText>(R.id.etNamaDok2)
        val etTempatLahirDok2 = findViewById<TextInputEditText>(R.id.etTempatLahirDok2)
        val etTanggalLahirDok2 = findViewById<TextInputEditText>(R.id.etTanggalLahirDok2)
        val spinJenisKelaminDok2 = findViewById<AutoCompleteTextView>(R.id.spinJenisKelaminDok2)
        val etPerbedaan = findViewById<TextInputEditText>(R.id.etPerbedaan)
        val spinAcuan = findViewById<AutoCompleteTextView>(R.id.spinAcuan)
        val cbAgree = findViewById<CheckBox>(R.id.cbAgree)

        val nikDok1 = etNikDok1.text.toString().trim()
        val namaDok1 = etNamaDok1.text.toString().trim()
        val namaDokumen2 = etNamaDokumen2.text.toString().trim()
        val namaDok2 = etNamaDok2.text.toString().trim()

        if (nikDok1.isEmpty() || nikDok1.length != 16) {
            etNikDok1.error = "NIK harus 16 digit"
            etNikDok1.requestFocus()
            return
        }

        if (namaDok1.isEmpty()) {
            etNamaDok1.error = "Nama Dokumen 1 wajib diisi"
            etNamaDok1.requestFocus()
            return
        }

        if (namaDokumen2.isEmpty()) {
            etNamaDokumen2.error = "Nama Dokumen 2 wajib diisi"
            etNamaDokumen2.requestFocus()
            return
        }

        if (namaDok2.isEmpty()) {
            etNamaDok2.error = "Nama di Dokumen 2 wajib diisi"
            etNamaDok2.requestFocus()
            return
        }

        if (editId == 0 && (uriDok1 == null || uriDok2 == null)) {
            Toast.makeText(this, "Harap unggah kedua berkas wajib", Toast.LENGTH_SHORT).show()
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
        parts["nik_dok1"] = etNikDok1.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["nama_dok1"] = etNamaDok1.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["tempat_lahir_dok1"] = etTempatLahirDok1.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["tanggal_lahir_dok1"] = etTanggalLahirDok1.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["jenis_kelamin_dok1"] = spinJenisKelaminDok1.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["alamat_dok1"] = etAlamatDok1.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["nama_dokumen2"] = etNamaDokumen2.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["nomor_dok2"] = etNoDokumen2.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["nama_dok2"] = etNamaDok2.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["tempat_lahir_dok2"] = etTempatLahirDok2.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["tanggal_lahir_dok2"] = etTanggalLahirDok2.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["jenis_kelamin_dok2"] = spinJenisKelaminDok2.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        
        val etAlamatDok2 = findViewById<TextInputEditText>(R.id.etAlamatDok2)
        parts["alamat_dok2"] = etAlamatDok2.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        
        parts["data_berbeda"] = etPerbedaan.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["acuan_kebenaran"] = spinAcuan.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())

        val berkasDok1Part = uriDok1?.let { prepareFilePart("berkas_dok1", it) }
        val berkasDok2Part = uriDok2?.let { prepareFilePart("berkas_dok2", it) }

        val rbEditId = if (editId > 0) editId.toString().toRequestBody("text/plain".toMediaTypeOrNull()) else null

        RetrofitClient.getInstance(this).submitBedaNama(
            parts["nik_dok1"]!!, parts["nama_dok1"]!!, parts["tempat_lahir_dok1"]!!, parts["tanggal_lahir_dok1"]!!,
            parts["jenis_kelamin_dok1"]!!, parts["alamat_dok1"]!!, parts["nama_dokumen2"]!!, parts["nomor_dok2"]!!,
            parts["nama_dok2"]!!, parts["tempat_lahir_dok2"]!!, parts["tanggal_lahir_dok2"]!!, parts["jenis_kelamin_dok2"]!!,
            parts["alamat_dok2"]!!, parts["data_berbeda"]!!, parts["acuan_kebenaran"]!!, berkasDok1Part, berkasDok2Part
        , rbEditId).enqueue(object : retrofit2.Callback<com.ta.sindesa.models.LoginResponse> {
            override fun onResponse(call: retrofit2.Call<com.ta.sindesa.models.LoginResponse>, response: retrofit2.Response<com.ta.sindesa.models.LoginResponse>) {
                btnSubmit.isEnabled = true
                btnSubmit.text = if (editId > 0) "SIMPAN PERUBAHAN" else "KIRIM PENGAJUAN"
                
                // Hapus file temporary di cache setelah upload selesai (OWASP M2)
                FileUtils.clearCacheFiles(this@BedaNamaActivity)

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        AlertDialog.Builder(this@BedaNamaActivity)
                            .setTitle("Berhasil")
                            .setMessage("Pengajuan Keterangan Beda Nama telah berhasil dikirim.")
                            .setPositiveButton("OK") { _, _ -> finish() }
                            .setCancelable(false)
                            .show()
                    } else {
                        val msg = body?.message ?: "Server merespon negatif (Success: false)"
                        AlertDialog.Builder(this@BedaNamaActivity)
                            .setTitle("Gagal")
                            .setMessage(msg)
                            .setPositiveButton("Tutup", null)
                            .show()
                    }
                } else {
                    val errorDetail = response.errorBody()?.string() ?: "Unknown error"
                    android.util.Log.e("API_DEBUG", "Error Body: $errorDetail")
                    AlertDialog.Builder(this@BedaNamaActivity)
                        .setTitle("Server Error (${response.code()})")
                        .setMessage("Detail: $errorDetail\n\nCek Logcat 'API_DEBUG' untuk detail.")
                        .setPositiveButton("Tutup", null)
                        .show()
                }
            }

            override fun onFailure(call: retrofit2.Call<com.ta.sindesa.models.LoginResponse>, t: Throwable) {
                btnSubmit.isEnabled = true
                btnSubmit.text = if (editId > 0) "SIMPAN PERUBAHAN" else "KIRIM PENGAJUAN"
                
                FileUtils.clearCacheFiles(this@BedaNamaActivity)
                
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

                AlertDialog.Builder(this@BedaNamaActivity)
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

        // Dokumen 1 Beda Nama Autofill
        fillField("etNikDok1", session.getNikUser())
        fillField("etNamaDok1", session.getNamaUser())
        fillField("etTempatLahirDok1", session.getTempatLahir())
        fillField("etTanggalLahirDok1", session.getTanggalLahir())
        fillField("spinJenisKelaminDok1", session.getJenisKelamin())
        fillField("etAlamatDok1", session.getAlamatLengkap())
    }
  }

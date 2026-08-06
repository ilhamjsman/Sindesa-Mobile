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

class AktaLahirActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var sessionManager: SessionManager

    // Variabel untuk menampung textview nama file
    private lateinit var tvFileNameKK: TextView
    private lateinit var tvFileNameSaksi: TextView

    // Uri untuk file yang dipilih
    private var uriKK: Uri? = null
    private var uriSaksi: Uri? = null

    private var currentPhotoPath: String? = null
    private var cameraImageUri: Uri? = null
    private var activeUploader: String = "" // "KK" atau "SAKSI"
    private var editId: Int = 0




    // Peluncur untuk Galeri (KK)
    private val pickKKGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            uriKK = it
            tvFileNameKK.text = FileUtils.getFileName(this, it)
        }
    }

    // Peluncur untuk Galeri (Saksi)
    private val pickSaksiGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            uriSaksi = it
            tvFileNameSaksi.text = FileUtils.getFileName(this, it)
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
                    uriKK = uri
                    tvFileNameKK.text = FileUtils.getFileName(this, uri)
                } else if (activeUploader == "SAKSI") {
                    uriSaksi = uri
                    tvFileNameSaksi.text = FileUtils.getFileName(this, uri)
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

        setContentView(R.layout.activity_akta_lahir)

        drawerLayout = findViewById(R.id.drawerLayout)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)

        // 1. Setup Hamburger Menu (Sidebar)
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
                val date = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, monthOfYear + 1, dayOfMonth)
                etTanggalLahir.setText(date)
            }, year, month, day)
            dpd.show()
        }

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

        RetrofitClient.getInstance(this).getDetailPengajuan(id).enqueue(    object : retrofit2.Callback<com.ta.sindesa.models.DetailPengajuanResponse> {
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

                            setEditText("etNamaAnak", "nama_anak")
                            setEditText("etAnakKe", "anak_ke")
                            setEditText("etTempatLahir", "tempat_lahir_anak")
                            setEditText("etTanggalLahir", "tanggal_lahir_anak")
                            setEditText("spinJenisKelamin", "jenis_kelamin_anak")
                            setEditText("spinAgama", "agama_anak")
                            setEditText("etAlamat", "alamat_anak")
                            setEditText("etNamaAyah", "nama_ayah")
                            setEditText("etNikAyah", "nik_ayah")
                            setEditText("etNamaIbu", "nama_ibu")
                            setEditText("etNikIbu", "nik_ibu")

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
                Toast.makeText(this@AktaLahirActivity, "Gagal memuat data editan", Toast.LENGTH_SHORT).show()
            }
        })
    }


    private fun setupDropdowns() {
        // 1. Dropdown Jenis Kelamin
        val jenisKelamin = arrayOf("Laki-laki", "Perempuan")
        val adapterKelamin = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, jenisKelamin)
        findViewById<AutoCompleteTextView>(R.id.spinJenisKelamin).setAdapter(adapterKelamin)
        findViewById<AutoCompleteTextView>(R.id.spinJenisKelamin).setOnClickListener { (it as AutoCompleteTextView).showDropDown() }

        // 2. Dropdown Agama
        val agama = arrayOf("Islam", "Kristen Protestan", "Katolik", "Hindu", "Buddha", "Konghucu")
        val adapterAgama = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, agama)
        findViewById<AutoCompleteTextView>(R.id.spinAgama).setAdapter(adapterAgama)
        findViewById<AutoCompleteTextView>(R.id.spinAgama).setOnClickListener { (it as AutoCompleteTextView).showDropDown() }

        val kewarganegaraan = arrayOf("Indonesia", "WNA")
        findViewById<AutoCompleteTextView>(R.id.spinKewarganegaraan)?.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, kewarganegaraan))
    }



    private fun initUploadButtons() {
        // 4. Setup Tombol Upload File
        tvFileNameKK = findViewById(R.id.tvFileNameKK)
        tvFileNameSaksi = findViewById(R.id.tvFileNameSaksi)

        findViewById<MaterialButton>(R.id.btnUploadKK).setOnClickListener {
            showImagePickerDialog("KK")
        }

        findViewById<MaterialButton>(R.id.btnUploadSaksi).setOnClickListener {
            showImagePickerDialog("SAKSI")
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
                        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            launchCamera()
                        } else {
                            requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        }
                    }
                    1 -> if (type == "KK") pickKKGalleryLauncher.launch("*/*") else pickSaksiGalleryLauncher.launch("*/*")
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
        val etNamaAnak = findViewById<TextInputEditText>(R.id.etNamaAnak)
        val etAnakKe = findViewById<TextInputEditText>(R.id.etAnakKe)
        val etTempatLahir = findViewById<TextInputEditText>(R.id.etTempatLahir)
        val etTanggalLahir = findViewById<TextInputEditText>(R.id.etTanggalLahir)
        val spinJenisKelamin = findViewById<AutoCompleteTextView>(R.id.spinJenisKelamin)
        val spinAgama = findViewById<AutoCompleteTextView>(R.id.spinAgama)
        val etAlamat = findViewById<TextInputEditText>(R.id.etAlamat)
        val etNamaAyah = findViewById<TextInputEditText>(R.id.etNamaAyah)
        val etNikAyah = findViewById<TextInputEditText>(R.id.etNikAyah)
        val etNamaIbu = findViewById<TextInputEditText>(R.id.etNamaIbu)
        val etNikIbu = findViewById<TextInputEditText>(R.id.etNikIbu)
        val cbAgree = findViewById<CheckBox>(R.id.cbAgree)

        val nikAyah = etNikAyah.text.toString().trim()
        val nikIbu = etNikIbu.text.toString().trim()
        val namaAnak = etNamaAnak.text.toString().trim()
        val tempatLahir = etTempatLahir.text.toString().trim()
        val tanggalLahir = etTanggalLahir.text.toString().trim()
        val namaAyah = etNamaAyah.text.toString().trim()
        val namaIbu = etNamaIbu.text.toString().trim()

        if (namaAnak.isEmpty()) {
            etNamaAnak.error = "Nama anak wajib diisi"
            etNamaAnak.requestFocus()
            return
        }

        if (nikAyah.isEmpty() || nikAyah.length != 16) {
            etNikAyah.error = "NIK Ayah harus 16 digit"
            etNikAyah.requestFocus()
            return
        }

        if (nikIbu.isEmpty() || nikIbu.length != 16) {
            etNikIbu.error = "NIK Ibu harus 16 digit"
            etNikIbu.requestFocus()
            return
        }

        if (tempatLahir.isEmpty() || tanggalLahir.isEmpty() || 
            namaAyah.isEmpty() || namaIbu.isEmpty() ||
            editId == 0 && (uriKK == null || uriSaksi == null)) {
            Toast.makeText(this, "Harap lengkapi data dan berkas wajib", Toast.LENGTH_SHORT).show()
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
        parts["nama_anak"] = etNamaAnak.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["anak_ke"] = etAnakKe.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["tempat_lahir_anak"] = etTempatLahir.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["tanggal_lahir_anak"] = etTanggalLahir.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["jenis_kelamin_anak"] = spinJenisKelamin.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["agama_anak"] = spinAgama.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        
        val kewarganegaraan = try { findViewById<AutoCompleteTextView>(resources.getIdentifier("spinKewarganegaraan", "id", packageName)).text.toString().ifEmpty { "Indonesia" } } catch (e: Exception) { "Indonesia" }
        
        parts["kewarganegaraan_anak"] = kewarganegaraan.toRequestBody("text/plain".toMediaTypeOrNull())
        parts["alamat_anak"] = etAlamat.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["nama_ayah"] = etNamaAyah.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["nik_ayah"] = etNikAyah.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["nama_ibu"] = etNamaIbu.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        parts["nik_ibu"] = etNikIbu.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        


        val berkasKKPart = uriKK?.let { prepareFilePart("berkas_kk", it) }
        val berkasSaksiPart = uriSaksi?.let { prepareFilePart("berkas_saksi", it) }

        val rbEditId = if (editId > 0) editId.toString().toRequestBody("text/plain".toMediaTypeOrNull()) else null
        val rbNikPemohon = (sessionManager.getNikUser() ?: "").toRequestBody("text/plain".toMediaTypeOrNull())
        val rbNamaPemohon = (sessionManager.getNamaUser() ?: "").toRequestBody("text/plain".toMediaTypeOrNull())

        RetrofitClient.getInstance(this).submitAktaLahir(
            parts["nama_anak"]!!, parts["anak_ke"]!!, parts["tempat_lahir_anak"]!!, parts["tanggal_lahir_anak"]!!,
            parts["jenis_kelamin_anak"]!!, parts["agama_anak"]!!, parts["kewarganegaraan_anak"]!!,
            parts["alamat_anak"]!!,
            parts["nama_ayah"]!!, parts["nik_ayah"]!!, parts["nama_ibu"]!!, parts["nik_ibu"]!!,
            berkasKKPart, berkasSaksiPart, rbEditId, rbNikPemohon, rbNamaPemohon
        ).enqueue(object : retrofit2.Callback<com.ta.sindesa.models.LoginResponse> {
            override fun onResponse(call: retrofit2.Call<com.ta.sindesa.models.LoginResponse>, response: retrofit2.Response<com.ta.sindesa.models.LoginResponse>) {
                btnSubmit.isEnabled = true
                btnSubmit.text = if (editId > 0) "SIMPAN PERUBAHAN" else "KIRIM PENGAJUAN"
                
                // Hapus file temporary di cache setelah upload selesai (OWASP M2)
                clearCacheFiles()

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        AlertDialog.Builder(this@AktaLahirActivity)
                            .setTitle("Berhasil")
                            .setMessage("Pengajuan Akta Kelahiran telah berhasil dikirim.")
                            .setPositiveButton("OK") { _, _ -> finish() }
                            .setCancelable(false)
                            .show()
                    } else {
                        val msg = body?.message ?: "Server merespon negatif (Success: false)"
                        AlertDialog.Builder(this@AktaLahirActivity)
                            .setTitle("Gagal")
                            .setMessage(msg)
                            .setPositiveButton("Tutup", null)
                            .show()
                    }
                } else {
                    val errorDetail = response.errorBody()?.string() ?: "Unknown error"
                    AlertDialog.Builder(this@AktaLahirActivity)
                        .setTitle("Server Error (${response.code()})")
                        .setMessage("Detail: $errorDetail")
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

                AlertDialog.Builder(this@AktaLahirActivity)
                    .setTitle("Gagal Terhubung ke Server")
                    .setMessage(errorMessage)
                    .setPositiveButton("Tutup", null)
                    .show()
                
                android.util.Log.e("API_DEBUG", "Submit Failure", t)
            }
        })
    }

    private fun clearCacheFiles() {
        FileUtils.clearCacheFiles(this)
    }

    private fun prepareFilePart(partName: String, fileUri: Uri): MultipartBody.Part {
        val file = FileUtils.uriToFile(this, fileUri)
        val requestFile = file.asRequestBody(contentResolver.getType(fileUri)?.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(partName, file.name, requestFile)
    }
  }

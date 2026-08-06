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
import com.ta.sindesa.models.LoginResponse
import com.ta.sindesa.utils.FileUtils
import com.ta.sindesa.utils.SecurityUtil
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SktmActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var sessionManager: SessionManager

    // Views - Applicant
    private lateinit var etNik: TextInputEditText
    private lateinit var etNama: TextInputEditText
    private lateinit var etTempatLahir: TextInputEditText
    private lateinit var etTanggalLahir: TextInputEditText
    private lateinit var spinJenisKelamin: AutoCompleteTextView
    private lateinit var spinAgama: AutoCompleteTextView
    private lateinit var etPekerjaan: TextInputEditText
    private lateinit var etAlamat: TextInputEditText

    // Views - Head of Family
    private lateinit var etNoKkKk: TextInputEditText
    private lateinit var etNikKk: TextInputEditText
    private lateinit var etNamaKk: TextInputEditText
    private lateinit var etTempatLahirKk: TextInputEditText
    private lateinit var etTanggalLahirKk: TextInputEditText
    private lateinit var spinJenisKelaminKk: AutoCompleteTextView
    private lateinit var spinAgamaKk: AutoCompleteTextView
    private lateinit var etPekerjaanKk: TextInputEditText
    private lateinit var etAlamatKk: TextInputEditText

    // Views - Others
    private lateinit var etKeperluan: TextInputEditText
    private lateinit var cbConsent: CheckBox
    private lateinit var btnSubmit: MaterialButton

    // File Upload Views
    private lateinit var tvFileNameKtp: TextView
    private lateinit var tvFileNameKk: TextView
    private lateinit var tvFileNameDusun: TextView

    private var uriKtp: Uri? = null
    private var uriKk: Uri? = null
    private var uriDusun: Uri? = null

    private var currentPhotoPath: String? = null
    private var cameraImageUri: Uri? = null
    private var activeUploader: String = "" // "KTP", "KK", "DUSUN"
    private var editId: Int = 0


    // Launchers - Gallery
    private val pickKtpGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            uriKtp = it
            tvFileNameKtp.text = FileUtils.getFileName(this, it)
        }
    }
    private val pickKkGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            uriKk = it
            tvFileNameKk.text = FileUtils.getFileName(this, it)
        }
    }
    private val pickDusunGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            uriDusun = it
            tvFileNameDusun.text = FileUtils.getFileName(this, it)
        }
    }

    // Launcher - Camera
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
                    "KTP" -> {
                        uriKtp = uri
                        tvFileNameKtp.text = FileUtils.getFileName(this, uri)
                    }
                    "KK" -> {
                        uriKk = uri
                        tvFileNameKk.text = FileUtils.getFileName(this, uri)
                    }
                    "DUSUN" -> {
                        uriDusun = uri
                        tvFileNameDusun.text = FileUtils.getFileName(this, uri)
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

        setContentView(R.layout.activity_sktm)
        setupAutoFill()

        initViews()
        setupSidebar()
        setupDropdowns()
        setupUploadButtons()
        setupDatePickers()

        btnSubmit.setOnClickListener {
            submitForm()
        }

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



                                                                                    setEditText("etKeperluan", "tujuan_sktm", "keperluan", "tujuan")
                            setEditText("etNik", "nik")
                            setEditText("etNama", "nama_lengkap", "nama")
                            setEditText("etTempatLahir", "tempat_lahir")
                            setEditText("etTanggalLahir", "tanggal_lahir")
                            setEditText("spinJenisKelamin", "jenis_kelamin")
                            setEditText("spinAgama", "agama")
                            setEditText("etPekerjaan", "pekerjaan")
                            setEditText("etAlamat", "alamat")
                            setEditText("etNoKkKk", "no_kk_kk", "no_kk", "kk")
                            setEditText("etNikKk", "nik_kk", "nik_kepala_keluarga", "nik_anak")
                            setEditText("etNamaKk", "nama_kk", "nama_kepala_keluarga", "nama_anak")
                            setEditText("etTempatLahirKk", "tempat_lahir_kk", "tempat_lahir_anak")
                            setEditText("etTanggalLahirKk", "tanggal_lahir_kk", "tanggal_lahir_anak")
                            setEditText("spinJenisKelaminKk", "jenis_kelamin_kk", "jenis_kelamin_anak")
                            setEditText("spinAgamaKk", "agama_kk", "agama_anak")
                            setEditText("etPekerjaanKk", "pekerjaan_kk", "pekerjaan_anak")
                            setEditText("etAlamatKk", "alamat_kk", "alamat_anak")

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
                Toast.makeText(this@SktmActivity, "Gagal memuat data editan", Toast.LENGTH_SHORT).show()
            }
        })
    }


    private fun setupDatePickers() {
        etTanggalLahir.setOnClickListener {
            showDatePickerDialog(etTanggalLahir)
        }
        etTanggalLahir.isFocusable = false
        etTanggalLahir.isClickable = true
        etTanggalLahirKk.setOnClickListener {
            showDatePickerDialog(etTanggalLahirKk)
        }
        etTanggalLahirKk.isFocusable = false
        etTanggalLahirKk.isClickable = true
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

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        
        // Applicant
        etNik = findViewById(R.id.etNik)
        etNama = findViewById(R.id.etNama)
        etTempatLahir = findViewById(R.id.etTempatLahir)
        etTanggalLahir = findViewById(R.id.etTanggalLahir)
        spinJenisKelamin = findViewById(R.id.spinJenisKelamin)
        spinAgama = findViewById(R.id.spinAgama)
        etPekerjaan = findViewById(R.id.etPekerjaan)
        etAlamat = findViewById(R.id.etAlamat)

        // Head of Family
        etNoKkKk = findViewById(R.id.etNoKkKk)
        etNikKk = findViewById(R.id.etNikKk)
        etNamaKk = findViewById(R.id.etNamaKk)
        etTempatLahirKk = findViewById(R.id.etTempatLahirKk)
        etTanggalLahirKk = findViewById(R.id.etTanggalLahirKk)
        spinJenisKelaminKk = findViewById(R.id.spinJenisKelaminKk)
        spinAgamaKk = findViewById(R.id.spinAgamaKk)
        etPekerjaanKk = findViewById(R.id.etPekerjaanKk)
        etAlamatKk = findViewById(R.id.etAlamatKk)

        // Others
        etKeperluan = findViewById(R.id.etKeperluan)
        cbConsent = findViewById(R.id.cbConsent)
        btnSubmit = findViewById(R.id.btnSubmit)
        tvFileNameKtp = findViewById(R.id.tvFileNameKtp)
        tvFileNameKk = findViewById(R.id.tvFileNameKk)
        tvFileNameDusun = findViewById(R.id.tvFileNameDusun)
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
        val jenisKelamin = arrayOf("Laki-laki", "Perempuan")
        val adapterKelamin = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, jenisKelamin)
        spinJenisKelamin.setAdapter(adapterKelamin)
        spinJenisKelaminKk.setAdapter(adapterKelamin)

        val agama = arrayOf("Islam", "Kristen Protestan", "Katolik", "Hindu", "Buddha", "Konghucu")
        val adapterAgama = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, agama)
        spinAgama.setAdapter(adapterAgama)
        spinAgamaKk.setAdapter(adapterAgama)
    }

    private fun setupUploadButtons() {
        findViewById<MaterialButton>(R.id.btnUploadKtp).setOnClickListener {
            showImagePickerDialog("KTP")
        }
        findViewById<MaterialButton>(R.id.btnUploadKk).setOnClickListener {
            showImagePickerDialog("KK")
        }
        findViewById<MaterialButton>(R.id.btnUploadDusun).setOnClickListener {
            showImagePickerDialog("DUSUN")
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
                    1 -> when (type) {
                        "KTP" -> pickKtpGalleryLauncher.launch("*/*")
                        "KK" -> pickKkGalleryLauncher.launch("*/*")
                        "DUSUN" -> pickDusunGalleryLauncher.launch("*/*")
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
        val nik = etNik.text.toString().trim()
        val nama = etNama.text.toString().trim()
        val tLahir = etTempatLahir.text.toString().trim()
        val tglLahir = etTanggalLahir.text.toString().trim()
        val jk = spinJenisKelamin.text.toString().trim()
        val agama = spinAgama.text.toString().trim()
        val pekerjaan = etPekerjaan.text.toString().trim()
        val alamat = etAlamat.text.toString().trim()

        val noKkKk = etNoKkKk.text.toString().trim()
        val nikKk = etNikKk.text.toString().trim()
        val namaKk = etNamaKk.text.toString().trim()
        val tLahirKk = etTempatLahirKk.text.toString().trim()
        val tglLahirKk = etTanggalLahirKk.text.toString().trim()
        val jkKk = spinJenisKelaminKk.text.toString().trim()
        val agamaKk = spinAgamaKk.text.toString().trim()
        val pekerjaanKk = etPekerjaanKk.text.toString().trim()
        val alamatKk = etAlamatKk.text.toString().trim()

        val keperluan = etKeperluan.text.toString().trim()

        if (nik.isEmpty() || nik.length != 16) {
            etNik.error = "NIK harus 16 digit"
            etNik.requestFocus()
            return
        }

        if (nama.isEmpty()) {
            etNama.error = "Nama wajib diisi"
            etNama.requestFocus()
            return
        }

        if (noKkKk.isEmpty() || noKkKk.length != 16) {
            etNoKkKk.error = "No KK harus 16 digit"
            etNoKkKk.requestFocus()
            return
        }

        if (nikKk.isEmpty() || nikKk.length != 16) {
            etNikKk.error = "NIK Kepala Keluarga harus 16 digit"
            etNikKk.requestFocus()
            return
        }

        if (namaKk.isEmpty()) {
            etNamaKk.error = "Nama Kepala Keluarga wajib diisi"
            etNamaKk.requestFocus()
            return
        }

        if (keperluan.isEmpty()) {
            etKeperluan.error = "Keperluan wajib diisi"
            etKeperluan.requestFocus()
            return
        }

        if (editId == 0 && (uriKtp == null || uriKk == null)) {
            Toast.makeText(this, "Mohon unggah berkas KTP dan KK", Toast.LENGTH_SHORT).show()
            return
        }

        if (!cbConsent.isChecked) {
            Toast.makeText(this, "Mohon centang persetujuan data", Toast.LENGTH_SHORT).show()
            return
        }

        btnSubmit.isEnabled = false
        btnSubmit.text = if (editId > 0) "Menyimpan Perubahan..." else "Mengirim..."

        val rbNik = nik.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbNama = nama.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbTLahir = tLahir.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbTglLahir = tglLahir.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbJk = jk.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbAgama = agama.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbPekerjaan = pekerjaan.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbAlamat = alamat.toRequestBody("text/plain".toMediaTypeOrNull())

        val rbNoKkKk = noKkKk.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbNikKk = nikKk.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbNamaKk = namaKk.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbTLahirKk = tLahirKk.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbTglLahirKk = tglLahirKk.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbJkKk = jkKk.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbAgamaKk = agamaKk.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbPekerjaanKk = pekerjaanKk.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbAlamatKk = alamatKk.toRequestBody("text/plain".toMediaTypeOrNull())

        val rbKeperluan = keperluan.toRequestBody("text/plain".toMediaTypeOrNull())

        val partKtp = prepareFilePart("berkas_ktp", uriKtp!!)
        val partKk = prepareFilePart("berkas_kk", uriKk!!)
        val partDusun = uriDusun?.let { prepareFilePart("berkas_dusun", it) }

        val rbEditId = if (editId > 0) editId.toString().toRequestBody("text/plain".toMediaTypeOrNull()) else null

        RetrofitClient.getInstance(this).submitSktm(
            rbNik, rbNama, rbTLahir, rbTglLahir, rbJk, rbAgama, rbPekerjaan, rbAlamat,
            rbNoKkKk, rbNikKk, rbNamaKk, rbTLahirKk, rbTglLahirKk, rbJkKk, rbAgamaKk, rbPekerjaanKk, rbAlamatKk,
            rbKeperluan, partKtp, partKk, partDusun
        , rbEditId).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                btnSubmit.isEnabled = true
                btnSubmit.text = if (editId > 0) "SIMPAN PERUBAHAN" else "KIRIM PENGAJUAN"
                
                // Hapus file temporary di cache setelah upload selesai (OWASP M2)
                clearCacheFiles()

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        AlertDialog.Builder(this@SktmActivity)
                            .setTitle("Berhasil")
                            .setMessage("Pengajuan SKTM telah berhasil dikirim.")
                            .setPositiveButton("OK") { _, _ -> finish() }
                            .setCancelable(false)
                            .show()
                    } else {
                        val msg = body?.message ?: "Gagal mengirim data"
                        AlertDialog.Builder(this@SktmActivity)
                            .setTitle("Gagal")
                            .setMessage(msg)
                            .setPositiveButton("Tutup", null)
                            .show()
                    }
                } else {
                    val errorDetail = response.errorBody()?.string() ?: "Unknown error"
                    android.util.Log.e("API_DEBUG", "Error Body: $errorDetail")
                    AlertDialog.Builder(this@SktmActivity)
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

                AlertDialog.Builder(this@SktmActivity)
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

package com.ta.sindesa

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
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
import com.ta.sindesa.models.LoginResponse
import com.google.gson.JsonSyntaxException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.io.EOFException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class BelumMenikahActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var sessionManager: SessionManager

    // Tiga textview penampung nama file
    private lateinit var tvFileNameKtp: TextView
    private lateinit var tvFileNameKk: TextView
    private lateinit var tvFileNameOrtu: TextView

    // Uri untuk file yang dipilih
    private var uriKtp: Uri? = null
    private var uriKk: Uri? = null
    private var uriOrtu: Uri? = null

    private var currentPhotoPath: String? = null
    private var cameraImageUri: Uri? = null
    private var activeUploader: String = "" // "KTP", "KK", "ORTU"
    
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

    private val pickOrtuGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            uriOrtu = it
            tvFileNameOrtu.text = FileUtils.getFileName(this, it)
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
                    "ORTU" -> {
                        uriOrtu = uri
                        tvFileNameOrtu.text = FileUtils.getFileName(this, uri)
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

        setContentView(R.layout.activity_belum_menikah)
        setupAutoFill()

        drawerLayout = findViewById(R.id.drawerLayout)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)

        // 1. Sidebar Menu Trigger
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
                                    val rawVal = dataTambahanMap[key]
                                    if (rawVal != null && rawVal !is Map<*, *> && rawVal !is List<*>) {
                                        val value = rawVal.toString().trim()
                                        if (value.isNotEmpty() && !value.startsWith("{") && !value.startsWith("[")) {
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
                            }

                            setEditText("etNik", "nik")
                            setEditText("etNama", "nama")
                            setEditText("etTempatLahir", "tempat_lahir")
                            setEditText("etTanggalLahir", "tanggal_lahir")
                            setEditText("spinJenisKelamin", "jenis_kelamin")
                            setEditText("spinAgama", "agama")
                            setEditText("etPekerjaan", "pekerjaan")
                            setEditText("etAlamat", "alamat")
                            setEditText("spinKewarganegaraan", "kewarganegaraan")
                            setEditText("etNikBapak", "nik_bapak")
                            setEditText("etNamaBapak", "nama_bapak")
                            setEditText("etTempatLahirBapak", "tempat_lahir_bapak")
                            setEditText("etTanggalLahirBapak", "tanggal_lahir_bapak")
                            setEditText("spinAgamaBapak", "agama_bapak")
                            setEditText("etPekerjaanBapak", "pekerjaan_bapak")
                            setEditText("etAlamatBapak", "alamat_bapak")
                            setEditText("etNikIbu", "nik_ibu")
                            setEditText("etNamaIbu", "nama_ibu")
                            setEditText("etTempatLahirIbu", "tempat_lahir_ibu")
                            setEditText("etTanggalLahirIbu", "tanggal_lahir_ibu")
                            setEditText("spinAgamaIbu", "agama_ibu")
                            setEditText("etPekerjaanIbu", "pekerjaan_ibu")
                            setEditText("etAlamatIbu", "alamat_ibu")

                            // Optional: set text info if files exist
                            for (key in dataTambahanMap.keys) {
                                if (key.startsWith("file_") || key.startsWith("berkas_") || key.startsWith("foto_")) continue
                                val rawVal = dataTambahanMap[key]
                                if (rawVal is Map<*, *> || rawVal is List<*>) continue
                                val strVal = rawVal?.toString()?.trim() ?: ""
                                if (strVal.startsWith("{") || strVal.startsWith("[")) continue

                                val parts = key.split("_")
                                val camelCaseKey = parts[0] + parts.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercase) }
                                setEditText("et" + camelCaseKey.replaceFirstChar(Char::uppercase), key)
                                setEditText("spin" + camelCaseKey.replaceFirstChar(Char::uppercase), key)
                            }

                            val hasKtp = dataTambahanMap["file_ktp"] != null || dataTambahanMap["berkas_ktp"] != null || dataTambahanMap["foto_ktp"] != null
                            val hasKk = dataTambahanMap["file_kk"] != null || dataTambahanMap["berkas_kk"] != null || dataTambahanMap["foto_kk"] != null
                            val hasOrtu = dataTambahanMap["file_ktp_ortu"] != null || dataTambahanMap["file_ortu"] != null || dataTambahanMap["file_ktp_orang_tua"] != null || dataTambahanMap["berkas_ortu"] != null || dataTambahanMap["berkas_ktp_ortu"] != null

                            if (hasKtp) tvFileNameKtp.text = "File sudah tersimpan (Pilih baru jika ingin mengubah)"
                            if (hasKk) tvFileNameKk.text = "File sudah tersimpan (Pilih baru jika ingin mengubah)"
                            if (hasOrtu) tvFileNameOrtu.text = "File sudah tersimpan (Pilih baru jika ingin mengubah)"

                            for (key in dataTambahanMap.keys) {
                                if (key.startsWith("file_") || key.startsWith("berkas_") || key.startsWith("foto_")) {
                                    if (dataTambahanMap[key] != null) {
                                        val rawName = key.replace("file_", "").replace("berkas_", "").replace("foto_", "")
                                        val tvId = "tvFileName" + rawName.split("_").joinToString("") { it.replaceFirstChar(Char::uppercase) }
                                        val resId = resources.getIdentifier(tvId, "id", packageName)
                                        if (resId != 0) {
                                            findViewById<android.widget.TextView>(resId)?.text = "File sudah tersimpan (Pilih baru jika ingin mengubah)"
                                        }
                                        if (key.contains("ortu")) {
                                            tvFileNameOrtu.text = "File sudah tersimpan (Pilih baru jika ingin mengubah)"
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
                Toast.makeText(this@BelumMenikahActivity, "Gagal memuat data editan", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun setupDatePickers() {
        val etTanggalLahir = findViewById<TextInputEditText>(R.id.etTanggalLahir)
        etTanggalLahir.setOnClickListener { showDatePickerDialog(etTanggalLahir) }
        etTanggalLahir.isFocusable = false
        etTanggalLahir.isClickable = true
        
        val etTanggalLahirBapak = findViewById<TextInputEditText>(R.id.etTanggalLahirBapak)
        etTanggalLahirBapak?.setOnClickListener { showDatePickerDialog(etTanggalLahirBapak) }
        etTanggalLahirBapak?.isFocusable = false
        etTanggalLahirBapak?.isClickable = true
        
        val etTanggalLahirIbu = findViewById<TextInputEditText>(R.id.etTanggalLahirIbu)
        etTanggalLahirIbu?.setOnClickListener { showDatePickerDialog(etTanggalLahirIbu) }
        etTanggalLahirIbu?.isFocusable = false
        etTanggalLahirIbu?.isClickable = true
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
        val agama = arrayOf("Islam", "Kristen Protestan", "Katolik", "Hindu", "Buddha", "Konghucu")
        val adapterAgama = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, agama)

        findViewById<AutoCompleteTextView>(R.id.spinAgamaBapak).setAdapter(adapterAgama)
        findViewById<AutoCompleteTextView>(R.id.spinAgamaIbu).setAdapter(adapterAgama)
    }

    private fun initUploadButtons() {
        tvFileNameKtp = findViewById(R.id.tvFileNameKtp)
        tvFileNameKk = findViewById(R.id.tvFileNameKk)
        tvFileNameOrtu = findViewById(R.id.tvFileNameOrtu)

        findViewById<MaterialButton>(R.id.btnUploadKtp).setOnClickListener {
            showImagePickerDialog("KTP")
        }
        findViewById<MaterialButton>(R.id.btnUploadKk).setOnClickListener {
            showImagePickerDialog("KK")
        }
        findViewById<MaterialButton>(R.id.btnUploadOrtu).setOnClickListener {
            showImagePickerDialog("ORTU")
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
                        "ORTU" -> pickOrtuGalleryLauncher.launch("*/*")
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
        try {
            val etNik = findViewById<TextInputEditText>(R.id.etNik)
        val etNama = findViewById<TextInputEditText>(R.id.etNama)
        val etTempatLahir = findViewById<TextInputEditText>(R.id.etTempatLahir)
        val etTanggalLahir = findViewById<TextInputEditText>(R.id.etTanggalLahir)
        val etNikBapak = findViewById<TextInputEditText>(R.id.etNikBapak)
        val etNamaBapak = findViewById<TextInputEditText>(R.id.etNamaBapak)
        val spinAgamaBapak = findViewById<AutoCompleteTextView>(R.id.spinAgamaBapak)
        val etNikIbu = findViewById<TextInputEditText>(R.id.etNikIbu)
        val etNamaIbu = findViewById<TextInputEditText>(R.id.etNamaIbu)
        val spinAgamaIbu = findViewById<AutoCompleteTextView>(R.id.spinAgamaIbu)
        val cbAgree = findViewById<CheckBox>(R.id.cbAgree)

        val nik = etNik.text.toString().trim()
        val nama = etNama.text.toString().trim()

        if (nik.length != 16) {
            etNik.error = "NIK harus 16 digit"
            etNik.requestFocus()
            return
        }

        if (nama.isEmpty()) {
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

        if (etNikBapak.text.toString().length != 16) {
            etNikBapak.error = "NIK Bapak harus 16 digit"
            etNikBapak.requestFocus()
            return
        }

        if (etNikIbu.text.toString().length != 16) {
            etNikIbu.error = "NIK Ibu harus 16 digit"
            etNikIbu.requestFocus()
            return
        }

        if (editId == 0) {
            if (uriKtp == null || uriKk == null) {
                Toast.makeText(this, "Harap unggah berkas KTP dan KK", Toast.LENGTH_SHORT).show()
                return
            }
        }

        if (!cbAgree.isChecked) {
            Toast.makeText(this, "Harap setujui pernyataan di atas", Toast.LENGTH_SHORT).show()
            return
        }

        val btnSubmit = findViewById<MaterialButton>(R.id.btnSubmit)
        btnSubmit.isEnabled = false
        btnSubmit.text = if (editId > 0) "Menyimpan Perubahan..." else "Mengirim..."

        val rbNik = nik.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbNama = nama.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbTempatLahir = etTempatLahir.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val rbTanggalLahir = etTanggalLahir.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val rbNikBapak = etNikBapak.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val rbNamaBapak = etNamaBapak.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val rbAgamaBapak = spinAgamaBapak.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        
        val tempatLahirBapak = try { findViewById<TextInputEditText>(resources.getIdentifier("etTempatLahirBapak", "id", packageName)).text.toString() } catch (e: Exception) { "" }
        val tanggalLahirBapak = try { findViewById<TextInputEditText>(resources.getIdentifier("etTanggalLahirBapak", "id", packageName)).text.toString() } catch (e: Exception) { "" }
        val pekerjaanBapak = try { findViewById<TextInputEditText>(resources.getIdentifier("etPekerjaanBapak", "id", packageName)).text.toString() } catch (e: Exception) { "" }
        val alamatBapak = try { findViewById<TextInputEditText>(resources.getIdentifier("etAlamatBapak", "id", packageName)).text.toString() } catch (e: Exception) { "" }
        val rbTempatLahirBapak = tempatLahirBapak.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbTanggalLahirBapak = tanggalLahirBapak.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbPekerjaanBapak = pekerjaanBapak.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbAlamatBapak = alamatBapak.toRequestBody("text/plain".toMediaTypeOrNull())
        
        val rbNikIbu = etNikIbu.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val rbNamaIbu = etNamaIbu.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val rbAgamaIbu = spinAgamaIbu.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        
        val tempatLahirIbu = try { findViewById<TextInputEditText>(resources.getIdentifier("etTempatLahirIbu", "id", packageName)).text.toString() } catch (e: Exception) { "" }
        val tanggalLahirIbu = try { findViewById<TextInputEditText>(resources.getIdentifier("etTanggalLahirIbu", "id", packageName)).text.toString() } catch (e: Exception) { "" }
        val pekerjaanIbu = try { findViewById<TextInputEditText>(resources.getIdentifier("etPekerjaanIbu", "id", packageName)).text.toString() } catch (e: Exception) { "" }
        val alamatIbu = try { findViewById<TextInputEditText>(resources.getIdentifier("etAlamatIbu", "id", packageName)).text.toString() } catch (e: Exception) { "" }
        val rbTempatLahirIbu = tempatLahirIbu.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbTanggalLahirIbu = tanggalLahirIbu.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbPekerjaanIbu = pekerjaanIbu.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbAlamatIbu = alamatIbu.toRequestBody("text/plain".toMediaTypeOrNull())

        val berkasKtpPart = uriKtp?.let { prepareFilePart("berkas_ktp", it) }
        val berkasKkPart = uriKk?.let { prepareFilePart("berkas_kk", it) }
        val berkasOrtuPart = uriOrtu?.let { prepareFilePart("berkas_ortu", it) }
        
        val rbEditId = if (editId > 0) editId.toString().toRequestBody("text/plain".toMediaTypeOrNull()) else null

        RetrofitClient.getInstance(this).submitBelumMenikah(
            rbNik, rbNama, rbTempatLahir, rbTanggalLahir,
            rbNikBapak, rbNamaBapak, rbTempatLahirBapak, rbTanggalLahirBapak, rbAgamaBapak, rbPekerjaanBapak, rbAlamatBapak,
            rbNikIbu, rbNamaIbu, rbTempatLahirIbu, rbTanggalLahirIbu, rbAgamaIbu, rbPekerjaanIbu, rbAlamatIbu,
            berkasKtpPart, berkasKkPart, berkasOrtuPart, rbEditId
        ).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                btnSubmit.isEnabled = true
                btnSubmit.text = if (editId > 0) "SIMPAN PERUBAHAN" else "KIRIM PENGAJUAN"
                
                FileUtils.clearCacheFiles(this@BelumMenikahActivity)
                
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        AlertDialog.Builder(this@BelumMenikahActivity)
                            .setTitle("Berhasil")
                            .setMessage("Pengajuan surat keterangan belum menikah telah berhasil dikirim.")
                            .setPositiveButton("OK") { _, _ -> finish() }
                            .setCancelable(false)
                            .show()
                    } else {
                        val msg = body?.message ?: "Gagal: Respon server negatif"
                        AlertDialog.Builder(this@BelumMenikahActivity)
                            .setTitle("Gagal")
                            .setMessage(msg)
                            .setPositiveButton("Tutup", null)
                            .show()
                    }
                } else {
                    val errorDetail = response.errorBody()?.string() ?: "Unknown error"
                    android.util.Log.e("API_DEBUG", "Error Body: $errorDetail")
                    AlertDialog.Builder(this@BelumMenikahActivity)
                        .setTitle("Server Error (${response.code()})")
                        .setMessage("Detail: $errorDetail\n\nCek Logcat 'API_DEBUG' untuk detail.")
                        .setPositiveButton("Tutup", null)
                        .show()
                }
            }

            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                btnSubmit.isEnabled = true
                btnSubmit.text = if (editId > 0) "SIMPAN PERUBAHAN" else "KIRIM PENGAJUAN"
                
                FileUtils.clearCacheFiles(this@BelumMenikahActivity)
                
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

                AlertDialog.Builder(this@BelumMenikahActivity)
                    .setTitle("Gagal Terhubung ke Server")
                    .setMessage(errorMessage)
                    .setPositiveButton("Tutup", null)
                    .show()
                
                android.util.Log.e("API_DEBUG", "Submit Failure: ${t.message}", t)
                android.util.Log.e("API_DEBUG", "HINT: Jika errornya 'Expected BEGIN_OBJECT but was STRING', buka Logcat, cari 'http' dan lihat Response Body dari server.")
            }
        })
        } catch (e: Throwable) {
            AlertDialog.Builder(this)
                .setTitle("Terjadi Kesalahan (Crash)")
                .setMessage("Aplikasi mengalami error: ${e.message}")
                .setPositiveButton("OK", null)
                .show()
        }
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
        fillField("etJenisKelamin", session.getJenisKelamin())
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

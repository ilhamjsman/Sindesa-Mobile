package com.ta.sindesa

import android.app.DatePickerDialog
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
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
import java.io.FileOutputStream
import java.io.InputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.io.EOFException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PenghasilanActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var sessionManager: SessionManager

    // Penampung nama file yang diupload
    private lateinit var tvFileNameKk: TextView
    private lateinit var tvFileNameAnak: TextView

    // Uri untuk file yang dipilih
    private var uriKk: Uri? = null
    private var uriAnak: Uri? = null

    private var currentPhotoPath: String? = null
    private var cameraImageUri: Uri? = null
    private var activeUploader: String = "" // "KK", "ANAK"
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

    // Peluncur (Launcher) untuk masing-masing file
    private val pickKkLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            uriKk = it
            tvFileNameKk.text = FileUtils.getFileName(this, it)
        }
    }

    private val pickAnakLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            uriAnak = it
            tvFileNameAnak.text = FileUtils.getFileName(this, it)
        }
    }

    // Peluncur untuk Kamera
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraImageUri?.let { uri ->
                when (activeUploader) {
                    "KK" -> {
                        uriKk = uri
                        tvFileNameKk.text = FileUtils.getFileName(this, uri)
                    }
                    "ANAK" -> {
                        uriAnak = uri
                        tvFileNameAnak.text = FileUtils.getFileName(this, uri)
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

        setContentView(R.layout.activity_penghasilan)
        setupAutoFill()

        drawerLayout = findViewById(R.id.drawerLayout)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)

        // 1. Sidebar Integration
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

                            setEditText("etNik", "nik", "nik_pemohon")
                            setEditText("etNama", "nama", "nama_pemohon", "nama_lengkap")
                            setEditText("etTempatLahir", "tempat_lahir", "tempat_lahir_pemohon")
                            setEditText("etTanggalLahir", "tanggal_lahir", "tanggal_lahir_pemohon")
                            setEditText("spinJenisKelamin", "jenis_kelamin", "jenis_kelamin_pemohon")
                            setEditText("spinAgama", "agama", "agama_pemohon")
                            setEditText("etPekerjaan", "pekerjaan", "pekerjaan_pemohon")
                            setEditText("etAlamat", "alamat", "alamat_pemohon")
                            setEditText("etPenghasilan", "jumlah_penghasilan", "penghasilan", "rincian_penghasilan", "rincian")
                            setEditText("etTanggungan", "jumlah_tanggungan", "tanggungan", "jumlah_tanggungan_keluarga")
                            setEditText("etNamaDitanggung", "nama_tanggungan", "nama_ditanggung", "nama_anak", "anak_ditanggung")

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

                            for (key in dataTambahanMap.keys) {
                                if (key.startsWith("file_") || key.startsWith("berkas_") || key.startsWith("foto_")) {
                                    if (dataTambahanMap[key] != null) {
                                        val rawName = key.replace("file_", "").replace("berkas_", "").replace("foto_", "")
                                        val tvId = "tvFileName" + rawName.split("_").joinToString("") { it.replaceFirstChar(Char::uppercase) }
                                        val resId = resources.getIdentifier(tvId, "id", packageName)
                                        if (resId != 0) {
                                            findViewById<android.widget.TextView>(resId)?.text = "File sudah tersimpan (Pilih baru jika ingin mengubah)"
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
                Toast.makeText(this@PenghasilanActivity, "Gagal memuat data editan", Toast.LENGTH_SHORT).show()
            }
        })
    }


    private fun setupDatePickers() {
        val etTanggalLahir = findViewById<TextInputEditText>(R.id.etTanggalLahir)
        etTanggalLahir.setOnClickListener {
            showDatePickerDialog(etTanggalLahir)
        }
        etTanggalLahir.isFocusable = false
        etTanggalLahir.isClickable = true
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
        // 2. Setup Dropdown Jenis Kelamin
        val jenisKelamin = arrayOf("Laki-laki", "Perempuan")
        val adapterKelamin = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, jenisKelamin)
        findViewById<AutoCompleteTextView>(R.id.spinJenisKelamin).setAdapter(adapterKelamin)

        // 3. Setup Dropdown Agama
        val agama = arrayOf("Islam", "Kristen Protestan", "Katolik", "Hindu", "Buddha", "Konghucu")
        val adapterAgama = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, agama)
        findViewById<AutoCompleteTextView>(R.id.spinAgama).setAdapter(adapterAgama)
    }

    private fun initUploadButtons() {
        tvFileNameKk = findViewById(R.id.tvFileNameKk)
        tvFileNameAnak = findViewById(R.id.tvFileNameAnak)

        findViewById<MaterialButton>(R.id.btnUploadKk).setOnClickListener {
            showImagePickerDialog("KK")
        }
        findViewById<MaterialButton>(R.id.btnUploadAnak).setOnClickListener {
            showImagePickerDialog("ANAK")
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
                    1 -> {
                        when (type) {
                            "KK" -> pickKkLauncher.launch("*/*")
                            "ANAK" -> pickAnakLauncher.launch("*/*")
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
        val etNik = findViewById<TextInputEditText>(R.id.etNik)
        val etNama = findViewById<TextInputEditText>(R.id.etNama)
        val etTempatLahir = findViewById<TextInputEditText>(R.id.etTempatLahir)
        val etTanggalLahir = findViewById<TextInputEditText>(R.id.etTanggalLahir)
        val spinJenisKelamin = findViewById<AutoCompleteTextView>(R.id.spinJenisKelamin)
        val spinAgama = findViewById<AutoCompleteTextView>(R.id.spinAgama)
        val etPekerjaan = findViewById<TextInputEditText>(R.id.etPekerjaan)
        val etAlamat = findViewById<TextInputEditText>(R.id.etAlamat)
        val etPenghasilan = findViewById<TextInputEditText>(R.id.etPenghasilan)
        val etTanggungan = findViewById<TextInputEditText>(R.id.etTanggungan)
        val etNamaDitanggung = findViewById<TextInputEditText>(R.id.etNamaDitanggung)
        val cbConsent = findViewById<CheckBox>(R.id.cbConsent)

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

        if (etPenghasilan.text.isNullOrEmpty()) {
            etPenghasilan.error = "Penghasilan wajib diisi"
            etPenghasilan.requestFocus()
            return
        }

        if (editId == 0 && (uriKk == null)) {
            Toast.makeText(this, "Harap unggah berkas KK/KTP", Toast.LENGTH_SHORT).show()
            return
        }

        if (!cbConsent.isChecked) {
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
        val rbJenisKelamin = spinJenisKelamin.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val rbAgama = spinAgama.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val rbPekerjaan = etPekerjaan.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val rbAlamat = etAlamat.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val rbPenghasilan = etPenghasilan.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val rbTanggungan = etTanggungan.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val rbNamaDitanggung = etNamaDitanggung.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())

        val berkasKkPart = uriKk?.let { prepareFilePart("berkas_kk_ktp", it) }
        val berkasAnakPart = uriAnak?.let { prepareFilePart("berkas_anak", it) }

        val rbEditId = if (editId > 0) editId.toString().toRequestBody("text/plain".toMediaTypeOrNull()) else null

        RetrofitClient.getInstance(this).submitPenghasilan(
            rbNik, rbNama, rbTempatLahir, rbTanggalLahir, rbJenisKelamin,
            rbAgama, rbPekerjaan, rbAlamat, rbPenghasilan, rbTanggungan,
            rbNamaDitanggung, berkasKkPart, berkasAnakPart
        , rbEditId).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                btnSubmit.isEnabled = true
                btnSubmit.text = if (editId > 0) "SIMPAN PERUBAHAN" else "KIRIM PENGAJUAN"
                
                FileUtils.clearCacheFiles(this@PenghasilanActivity)
                
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        AlertDialog.Builder(this@PenghasilanActivity)
                            .setTitle("Berhasil")
                            .setMessage("Pengajuan surat keterangan penghasilan telah berhasil dikirim.")
                            .setPositiveButton("OK") { _, _ -> finish() }
                            .setCancelable(false)
                            .show()
                    } else {
                        val msg = body?.message ?: "Gagal: Respon server negatif"
                        AlertDialog.Builder(this@PenghasilanActivity)
                            .setTitle("Gagal")
                            .setMessage(msg)
                            .setPositiveButton("Tutup", null)
                            .show()
                    }
                } else {
                    val errorDetail = response.errorBody()?.string() ?: "Unknown error"
                    android.util.Log.e("API_DEBUG", "Error Body: $errorDetail")
                    AlertDialog.Builder(this@PenghasilanActivity)
                        .setTitle("Server Error (${response.code()})")
                        .setMessage("Detail: $errorDetail\n\nCek Logcat 'API_DEBUG' untuk detail.")
                        .setPositiveButton("Tutup", null)
                        .show()
                }
            }

            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                btnSubmit.isEnabled = true
                btnSubmit.text = if (editId > 0) "SIMPAN PERUBAHAN" else "KIRIM PENGAJUAN"
                
                FileUtils.clearCacheFiles(this@PenghasilanActivity)
                
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

                AlertDialog.Builder(this@PenghasilanActivity)
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
        fillField("etTempatLahir", session.getTempatLahir())
        fillField("etTanggalLahir", session.getTanggalLahir())
        fillField("spinJenisKelamin", session.getJenisKelamin())
        fillField("spinAgama", session.getAgama())
        fillField("etPekerjaan", session.getPekerjaan())
        fillField("etAlamat", session.getAlamatLengkap())
    }
  }

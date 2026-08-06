package com.ta.sindesa

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.View
import android.widget.*
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.ta.sindesa.api.RetrofitClient
import com.ta.sindesa.models.LoginResponse
import com.ta.sindesa.models.Region
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
import java.text.SimpleDateFormat
import java.util.*

class RegisterActivity : AppCompatActivity() {

    private var selectedKtpUri: Uri? = null
    private var currentRecaptchaToken: String? = null

    private var currentPhotoPath: String? = null
    private var cameraImageUri: Uri? = null

    private var selectedProvinceCode: String = ""
    private var selectedCityCode: String = ""
    private var selectedDistrictCode: String = ""
    private var selectedVillageCode: String = ""

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(this, "Izin kamera diperlukan untuk mengambil foto", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            selectedKtpUri = uri
            findViewById<ImageView>(R.id.ivKtpPreview).apply {
                visibility = View.VISIBLE
                setImageURI(uri)
            }
            findViewById<TextView>(R.id.tvKtpPath).text = FileUtils.getFileName(this, uri)
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraImageUri?.let { uri ->
                selectedKtpUri = uri
                findViewById<ImageView>(R.id.ivKtpPreview).apply {
                    visibility = View.VISIBLE
                    setImageURI(uri)
                }
                findViewById<TextView>(R.id.tvKtpPath).text = FileUtils.getFileName(this, uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // SECURE BY DESIGN: Mencegah screenshot dan screen recording (M1 - Improper Platform Usage)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        if (SecurityUtil.isDeviceRooted() || SecurityUtil.isRunningOnEmulator()) {
            SecurityUtil.showSecurityWarning(this)
            return
        }

        setContentView(R.layout.activity_register)

        val tvLoginMenu = findViewById<TextView>(R.id.tvLoginMenu)
        val etNama = findViewById<TextInputEditText>(R.id.etNama)
        val etNikReg = findViewById<TextInputEditText>(R.id.etNikReg)
        val etKk = findViewById<TextInputEditText>(R.id.etKk)
        val spAgama = findViewById<AutoCompleteTextView>(R.id.spAgama)
        val spJenisKelamin = findViewById<AutoCompleteTextView>(R.id.spJenisKelamin)
        val etTempatLahir = findViewById<TextInputEditText>(R.id.etTempatLahir)
        val etTanggalLahir = findViewById<TextInputEditText>(R.id.etTanggalLahir)
        val spStatusPerkawinan = findViewById<AutoCompleteTextView>(R.id.spStatusPerkawinan)
        val etPekerjaan = findViewById<TextInputEditText>(R.id.etPekerjaan)
        val spKewarganegaraan = findViewById<AutoCompleteTextView>(R.id.spKewarganegaraan)
        
        val etAlamatLengkap = findViewById<TextInputEditText>(R.id.etAlamatLengkap)
        val etRtRw = findViewById<TextInputEditText>(R.id.etRtRw)
        val spProvinsi = findViewById<AutoCompleteTextView>(R.id.spProvinsi)
        val spKota = findViewById<AutoCompleteTextView>(R.id.spKota)
        val spKecamatan = findViewById<AutoCompleteTextView>(R.id.spKecamatan)
        val spKelurahan = findViewById<AutoCompleteTextView>(R.id.spKelurahan)

        val etPhone = findViewById<TextInputEditText>(R.id.etPhone)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPasswordReg = findViewById<TextInputEditText>(R.id.etPasswordReg)
        val etPasswordConfirm = findViewById<TextInputEditText>(R.id.etPasswordConfirm)

        val btnRegister = findViewById<MaterialButton>(R.id.btnRegister)

        val btnUploadKtp = findViewById<MaterialButton>(R.id.btnUploadKtp)
        btnUploadKtp.setOnClickListener {
            showImagePickerDialog()
        }

        // Setup Embedded Google reCAPTCHA v2 WebView
        val webViewCaptcha = findViewById<android.webkit.WebView>(R.id.webViewCaptcha)
        webViewCaptcha.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
        }

        class JsBridge {
            @android.webkit.JavascriptInterface
            fun sendToken(token: String) {
                runOnUiThread {
                    currentRecaptchaToken = token
                    Toast.makeText(this@RegisterActivity, "Verifikasi 'Saya bukan robot' berhasil", Toast.LENGTH_SHORT).show()
                }
            }

            @android.webkit.JavascriptInterface
            fun sendExpired() {
                runOnUiThread {
                    currentRecaptchaToken = null
                    Toast.makeText(this@RegisterActivity, "Verifikasi captcha kadaluarsa. Silakan centang ulang.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        webViewCaptcha.addJavascriptInterface(JsBridge(), "RecaptchaBridge")

        val siteKey = com.ta.sindesa.utils.RecaptchaUtil.DEFAULT_SITE_KEY
        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <script src="https://www.google.com/recaptcha/api.js" async defer></script>
                <style>
                    body {
                        display: flex;
                        justify-content: center;
                        align-items: flex-start;
                        min-height: 100vh;
                        margin: 0;
                        padding-top: 15px;
                        background-color: #ffffff;
                    }
                </style>
            </head>
            <body>
                <div class="g-recaptcha" data-sitekey="$siteKey" data-callback="onCaptchaSuccess" data-expired-callback="onCaptchaExpired"></div>
                <script>
                    function onCaptchaSuccess(token) {
                        if (window.RecaptchaBridge) {
                            window.RecaptchaBridge.sendToken(token);
                        }
                    }
                    function onCaptchaExpired() {
                        if (window.RecaptchaBridge) {
                            window.RecaptchaBridge.sendExpired();
                        }
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        webViewCaptcha.loadDataWithBaseURL("https://your-api-domain.com", htmlContent, "text/html", "UTF-8", null)

        // Setup Spinners
        val agamaList = arrayOf("Islam", "Kristen", "Katolik", "Hindu", "Buddha", "Konghucu")
        spAgama.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, agamaList))

        val jkList = arrayOf("Laki-Laki", "Perempuan")
        spJenisKelamin.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, jkList))

        val statusList = arrayOf("Belum Kawin", "Kawin", "Cerai Hidup", "Cerai Mati")
        spStatusPerkawinan.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, statusList))

        val warganegaraList = arrayOf("WNI", "WNA")
        spKewarganegaraan.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, warganegaraList))

        // Region Selection Logic
        loadProvinces(spProvinsi, spKota, spKecamatan, spKelurahan)

        // Tambahkan ini agar dropdown muncul saat diklik
        spProvinsi.setOnClickListener { spProvinsi.showDropDown() }
        spKota.setOnClickListener { spKota.showDropDown() }
        spKecamatan.setOnClickListener { spKecamatan.showDropDown() }
        spKelurahan.setOnClickListener { spKelurahan.showDropDown() }

        // Date Picker
        etTanggalLahir.isFocusable = false
        etTanggalLahir.isClickable = true
        etTanggalLahir.setOnClickListener {
            val c = Calendar.getInstance()
            val year = c.get(Calendar.YEAR)
            val month = c.get(Calendar.MONTH)
            val day = c.get(Calendar.DAY_OF_MONTH)

            val dpd = DatePickerDialog(this, { _, year, monthOfYear, dayOfMonth ->
                val date = String.format("%04d-%02d-%02d", year, monthOfYear + 1, dayOfMonth)
                etTanggalLahir.setText(date)
            }, year, month, day)
            dpd.show()
        }

        tvLoginMenu.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
        btnRegister.setOnClickListener {
            val nama = etNama.text.toString().trim()
            val nik = etNikReg.text.toString().trim()
            val kk = etKk.text.toString().trim()
            val agama = spAgama.text.toString().trim()
            val jk = spJenisKelamin.text.toString().trim()
            val tempatLahir = etTempatLahir.text.toString().trim()
            val tanggalLahir = etTanggalLahir.text.toString().trim()
            val status = spStatusPerkawinan.text.toString().trim()
            val pekerjaan = etPekerjaan.text.toString().trim()
            val kewarganegaraan = spKewarganegaraan.text.toString().trim()
            val alamat = etAlamatLengkap.text.toString().trim()
            val rtrw = etRtRw.text.toString().trim()
            val provinsi = selectedProvinceCode
            val kota = selectedCityCode
            val kecamatan = selectedDistrictCode
            val kelurahan = selectedVillageCode
            val phone = etPhone.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPasswordReg.text.toString().trim()
            val confirm = etPasswordConfirm.text.toString().trim()

            if (nama.isEmpty()) {
                etNama.error = "Nama wajib diisi"
                etNama.requestFocus()
                return@setOnClickListener
            }

            if (nik.length != 16) {
                etNikReg.error = "NIK harus 16 digit"
                etNikReg.requestFocus()
                return@setOnClickListener
            }

            if (kk.length != 16) {
                etKk.error = "No KK harus 16 digit"
                etKk.requestFocus()
                return@setOnClickListener
            }

            if (email.isEmpty()) {
                etEmail.error = "Email wajib diisi"
                etEmail.requestFocus()
                return@setOnClickListener
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Format email tidak valid"
                etEmail.requestFocus()
                return@setOnClickListener
            }

            if (password.length < 6) {
                etPasswordReg.error = "Kata sandi minimal 6 karakter"
                etPasswordReg.requestFocus()
                return@setOnClickListener
            }

            if (password != confirm) {
                etPasswordConfirm.error = "Konfirmasi kata sandi tidak cocok"
                return@setOnClickListener
            }

            if (selectedKtpUri == null) {
                Toast.makeText(this, "Mohon unggah foto KTP Anda", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (currentRecaptchaToken.isNullOrEmpty()) {
                Toast.makeText(this, "Mohon centang 'Saya bukan robot' pada verifikasi keamanan terlebih dahulu", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            btnRegister.isEnabled = false
            btnRegister.text = "MEMPROSES..."

            executeRegistration(
                nama, nik, kk, agama, jk, tempatLahir, tanggalLahir,
                status, pekerjaan, kewarganegaraan, alamat, rtrw,
                provinsi, kota, kecamatan, kelurahan, phone, email,
                password, selectedKtpUri, currentRecaptchaToken!!
            )
        }
    }

    private fun executeRegistration(
        nama: String, nik: String, kk: String, agama: String, jk: String,
        tempatLahir: String, tanggalLahir: String, status: String, pekerjaan: String,
        kewarganegaraan: String, alamat: String, rtrw: String, provinsi: String,
        kota: String, kecamatan: String, kelurahan: String, phone: String,
        email: String, password: String, ktpUri: Uri?, recaptchaToken: String
    ) {
        val btnRegister = findViewById<MaterialButton>(R.id.btnRegister)
        val fotoKtpPart = ktpUri?.let { prepareFilePart("foto_ktp", it) }

        val call = RetrofitClient.getInstance(this).registerWarga(
            nama.toPart(), nik.toPart(), kk.toPart(), agama.toPart(), jk.toPart(),
            tempatLahir.toPart(), tanggalLahir.toPart(), status.toPart(), pekerjaan.toPart(),
            kewarganegaraan.toPart(), alamat.toPart(), rtrw.toPart(), provinsi.toPart(),
            kota.toPart(), kecamatan.toPart(), kelurahan.toPart(), phone.toPart(),
            email.toPart(), password.toPart(), fotoKtpPart, recaptchaToken.toPart()
        )

            call.enqueue(object : Callback<LoginResponse> {
                override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                    btnRegister.isEnabled = true
                    btnRegister.text = "DAFTAR SEKARANG"

                    if (response.isSuccessful) {
                        FileUtils.clearCacheFiles(this@RegisterActivity)
                        if (response.body()?.success == true) {
                            AlertDialog.Builder(this@RegisterActivity)
                                .setTitle("Pendaftaran Berhasil")
                                .setMessage("Akun Anda telah berhasil dibuat. Silakan login untuk melanjutkan.")
                                .setPositiveButton("Login Sekarang") { _, _ -> finish() }
                                .setCancelable(false)
                                .show()
                        } else {
                            val errorDetail = response.errorBody()?.string() ?: "Unknown error"
                            android.util.Log.e("API_DEBUG", "Error Body: $errorDetail")
                            AlertDialog.Builder(this@RegisterActivity)
                                .setTitle("Pendaftaran Gagal")
                                .setMessage("Detail: $errorDetail\n\nCek Logcat 'API_DEBUG' untuk detail.")
                                .setPositiveButton("Tutup", null)
                                .show()
                        }
                    } else {
                        val errorDetail = response.errorBody()?.string() ?: "Unknown error"
                        android.util.Log.e("API_DEBUG", "Error Body: $errorDetail")
                        AlertDialog.Builder(this@RegisterActivity)
                            .setTitle("Server Error (${response.code()})")
                            .setMessage("Detail: $errorDetail\n\nCek Logcat 'API_DEBUG' untuk detail.")
                            .setPositiveButton("Tutup", null)
                            .show()
                    }
                }

                override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                    btnRegister.isEnabled = true
                    btnRegister.text = "DAFTAR SEKARANG"
                    
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

                    AlertDialog.Builder(this@RegisterActivity)
                        .setTitle("Gagal Terhubung ke Server")
                        .setMessage(errorMessage)
                        .setPositiveButton("Tutup", null)
                        .show()
                    
                    android.util.Log.e("API_DEBUG", "Register Failure: ${t.message}", t)
                    android.util.Log.e("API_DEBUG", "HINT: Jika errornya 'Expected BEGIN_OBJECT but was STRING', buka Logcat, cari 'http' dan lihat Response Body dari server.")
                }
            })
    }

    private fun showImagePickerDialog() {
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
                    1 -> pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
            .show()
    }

    private fun launchCamera() {
        cameraImageUri = FileUtils.createImageUri(this)
        cameraImageUri?.let { cameraLauncher.launch(it) }
    }

    private fun loadProvinces(spProv: AutoCompleteTextView, spKota: AutoCompleteTextView, spKec: AutoCompleteTextView, spKel: AutoCompleteTextView) {
        RetrofitClient.getInstance(this).getProvinces().enqueue(object : Callback<List<Region>> {
            override fun onResponse(call: Call<List<Region>>, response: Response<List<Region>>) {
                if (response.isSuccessful && response.body() != null) {
                    val provinces = response.body()!!
                    val adapter = ArrayAdapter(this@RegisterActivity, android.R.layout.simple_dropdown_item_1line, provinces)
                    spProv.setAdapter(adapter)
                    spProv.setOnItemClickListener { _, _, position, _ ->
                        val selected = provinces[position]
                        selectedProvinceCode = selected.code
                        
                        // Reset and Load Cities
                        selectedCityCode = ""
                        spKota.setText("") // Hapus "Pilih Provinsi dahulu"
                        spKota.isEnabled = true
                        
                        selectedDistrictCode = ""
                        spKec.setText("Pilih Kota dahulu")
                        spKec.isEnabled = false
                        
                        selectedVillageCode = ""
                        spKel.setText("Pilih Kecamatan dahulu")
                        spKel.isEnabled = false
                        
                        loadCities(selected.code, spKota, spKec, spKel)
                    }
                }
            }
            override fun onFailure(call: Call<List<Region>>, t: Throwable) {
                val host = RetrofitClient.HOSTNAME
                val errorMessage = when (t) {
                    is com.google.gson.JsonSyntaxException -> "Respon Server Bukan JSON (Region).\nCek Logcat 'API_DEBUG'."
                    is java.net.SocketTimeoutException -> "Timeout memuat provinsi. Target: $host"
                    is java.net.ConnectException -> "Koneksi Ditolak. Cek Port 8080."
                    else -> "Gagal memuat provinsi: ${t.localizedMessage}"
                }
                Toast.makeText(this@RegisterActivity, errorMessage, Toast.LENGTH_LONG).show()
                android.util.Log.e("API_DEBUG", "Load Provinces Failure", t)
            }
        })
    }

    private fun loadCities(provCode: String, spKota: AutoCompleteTextView, spKec: AutoCompleteTextView, spKel: AutoCompleteTextView) {
        RetrofitClient.getInstance(this).getCities(provCode).enqueue(object : Callback<List<Region>> {
            override fun onResponse(call: Call<List<Region>>, response: Response<List<Region>>) {
                if (response.isSuccessful && response.body() != null) {
                    val cities = response.body()!!
                    val adapter = ArrayAdapter(this@RegisterActivity, android.R.layout.simple_dropdown_item_1line, cities)
                    spKota.setAdapter(adapter)
                    spKota.setOnItemClickListener { _, _, position, _ ->
                        val selected = cities[position]
                        selectedCityCode = selected.code
                        
                        // Reset and Load Districts
                        selectedDistrictCode = ""
                        spKec.setText("") // Hapus "Pilih Kota dahulu"
                        spKec.isEnabled = true
                        
                        selectedVillageCode = ""
                        spKel.setText("Pilih Kecamatan dahulu")
                        spKel.isEnabled = false
                        
                        loadDistricts(selected.code, spKec, spKel)
                    }
                }
            }
            override fun onFailure(call: Call<List<Region>>, t: Throwable) {
                val host = RetrofitClient.HOSTNAME
                val errorMessage = when (t) {
                    is com.google.gson.JsonSyntaxException -> "Respon Server Bukan JSON (City).\nCek Logcat 'API_DEBUG'."
                    is java.net.SocketTimeoutException -> "Timeout memuat kota. Target: $host"
                    is java.net.ConnectException -> "Koneksi Ditolak. Cek Port 8080."
                    else -> "Gagal memuat kota: ${t.localizedMessage}"
                }
                Toast.makeText(this@RegisterActivity, errorMessage, Toast.LENGTH_SHORT).show()
                android.util.Log.e("API_DEBUG", "Load Cities Failure", t)
            }
        })
    }

    private fun loadDistricts(cityCode: String, spKec: AutoCompleteTextView, spKel: AutoCompleteTextView) {
        RetrofitClient.getInstance(this).getDistricts(cityCode).enqueue(object : Callback<List<Region>> {
            override fun onResponse(call: Call<List<Region>>, response: Response<List<Region>>) {
                if (response.isSuccessful && response.body() != null) {
                    val districts = response.body()!!
                    val adapter = ArrayAdapter(this@RegisterActivity, android.R.layout.simple_dropdown_item_1line, districts)
                    spKec.setAdapter(adapter)
                    spKec.setOnItemClickListener { _, _, position, _ ->
                        val selected = districts[position]
                        selectedDistrictCode = selected.code
                        
                        // Reset and Load Villages
                        selectedVillageCode = ""
                        spKel.setText("") // Hapus "Pilih Kecamatan dahulu"
                        spKel.isEnabled = true
                        loadVillages(selected.code, spKel)
                    }
                }
            }
            override fun onFailure(call: Call<List<Region>>, t: Throwable) {
                val host = RetrofitClient.HOSTNAME
                val errorMessage = when (t) {
                    is com.google.gson.JsonSyntaxException -> "Respon Server Bukan JSON (District).\nCek Logcat 'API_DEBUG'."
                    is java.net.SocketTimeoutException -> "Timeout memuat kecamatan. Target: $host"
                    is java.net.ConnectException -> "Koneksi Ditolak. Cek Port 8080."
                    else -> "Gagal memuat kecamatan: ${t.localizedMessage}"
                }
                Toast.makeText(this@RegisterActivity, errorMessage, Toast.LENGTH_SHORT).show()
                android.util.Log.e("API_DEBUG", "Load Districts Failure", t)
            }
        })
    }

    private fun loadVillages(distCode: String, spKel: AutoCompleteTextView) {
        RetrofitClient.getInstance(this).getVillages(distCode).enqueue(object : Callback<List<Region>> {
            override fun onResponse(call: Call<List<Region>>, response: Response<List<Region>>) {
                if (response.isSuccessful && response.body() != null) {
                    val villages = response.body()!!
                    val adapter = ArrayAdapter(this@RegisterActivity, android.R.layout.simple_dropdown_item_1line, villages)
                    spKel.setAdapter(adapter)
                    spKel.setOnItemClickListener { _, _, position, _ ->
                        selectedVillageCode = villages[position].code
                    }
                }
            }
            override fun onFailure(call: Call<List<Region>>, t: Throwable) {
                val host = RetrofitClient.HOSTNAME
                val errorMessage = when (t) {
                    is com.google.gson.JsonSyntaxException -> "Respon Server Bukan JSON (Village).\nCek Logcat 'API_DEBUG'."
                    is java.net.SocketTimeoutException -> "Timeout memuat kelurahan. Target: $host"
                    is java.net.ConnectException -> "Koneksi Ditolak. Cek Port 8080."
                    else -> "Gagal memuat kelurahan: ${t.localizedMessage}"
                }
                Toast.makeText(this@RegisterActivity, errorMessage, Toast.LENGTH_SHORT).show()
                android.util.Log.e("API_DEBUG", "Load Villages Failure", t)
            }
        })
    }



    private fun String.toPart(): RequestBody {
        return this.toRequestBody("text/plain".toMediaTypeOrNull())
    }

    private fun prepareFilePart(partName: String, fileUri: Uri): MultipartBody.Part? {
        val file = FileUtils.uriToFile(this, fileUri) ?: return null
        val requestFile = file.asRequestBody(contentResolver.getType(fileUri)?.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(partName, file.name, requestFile)
    }
}



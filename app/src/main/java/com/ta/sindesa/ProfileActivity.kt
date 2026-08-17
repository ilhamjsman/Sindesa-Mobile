package com.ta.sindesa

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import com.ta.sindesa.utils.FileUtils
import android.graphics.BitmapFactory
import java.util.Calendar
import com.ta.sindesa.models.Region
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


class ProfileActivity : AppCompatActivity() {

    private lateinit var imgProfile: ImageView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var sessionManager: SessionManager
    private var selectedImageUri: Uri? = null

    private var selectedProvinceCode: String = ""
    private var selectedCityCode: String = ""
    private var selectedDistrictCode: String = ""
    private var selectedVillageCode: String = ""

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val correctedFile = FileUtils.uriToFile(this@ProfileActivity, uri)
                selectedImageUri = Uri.fromFile(correctedFile)
                imgProfile.setImageURI(selectedImageUri)
            } catch (e: Exception) {
                selectedImageUri = uri
                imgProfile.setImageURI(uri)
            }
        }
    }

    private var cameraImageUri: Uri? = null

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraImageUri?.let { uri ->
                try {
                    val correctedFile = FileUtils.uriToFile(this@ProfileActivity, uri)
                    selectedImageUri = Uri.fromFile(correctedFile)
                    imgProfile.setImageURI(selectedImageUri)
                } catch (e: Exception) {
                    selectedImageUri = uri
                    imgProfile.setImageURI(uri)
                }
            }
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(this, "Izin kamera diperlukan untuk mengambil foto profil.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchCamera() {
        try {
            val cameraPhotosDir = java.io.File(cacheDir, "camera_photos")
            if (!cameraPhotosDir.exists()) cameraPhotosDir.mkdirs()
            val tempFile = java.io.File.createTempFile("profile_img_", ".jpg", cameraPhotosDir).apply {
                createNewFile()
                deleteOnExit()
            }
            cameraImageUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                tempFile
            )
            takePictureLauncher.launch(cameraImageUri)
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membuka kamera: ${e.message}", Toast.LENGTH_SHORT).show()
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
        if (com.ta.sindesa.utils.SecurityUtil.isDeviceRooted() || com.ta.sindesa.utils.SecurityUtil.isRunningOnEmulator()) {
            Toast.makeText(this, "Aplikasi tidak dapat berjalan di lingkungan tidak aman", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        sessionManager = SessionManager(this)

        // ==========================================
        // SECURE BY DESIGN: 1. Pengecekan Sesi Valid
        // ==========================================
        // Memastikan hanya warga yang sedang login yang bisa membuka halaman profil.
        if (!sessionManager.isLoggedIn()) {
            Toast.makeText(this, "Akses ditolak. Silakan login terlebih dahulu.", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, WelcomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_profile)

        // 1. Inisialisasi Komponen UI
        drawerLayout = findViewById(R.id.drawerLayout)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        imgProfile = findViewById(R.id.imgProfile)
        val btnCamera = findViewById<FloatingActionButton>(R.id.btnCamera)
        val tvNamaProfil = findViewById<TextView>(R.id.tvNamaProfil)
        val tvEmailProfil = findViewById<TextView>(R.id.tvEmailProfil)
        val etNikProfil = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNikProfil)

        val etNamaInputProfil = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNamaInputProfil)
        val etEmailInputProfil = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEmailInputProfil)
        val etNoHpProfil = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNoHpProfil)
        val etTempatLahirProfil = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etTempatLahirProfil)
        val etTanggalLahirProfil = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etTanggalLahirProfil)
        val spinJenisKelaminProfil = findViewById<AutoCompleteTextView>(R.id.spinJenisKelaminProfil)
        val spinAgamaProfil = findViewById<AutoCompleteTextView>(R.id.spinAgamaProfil)
        val spinStatusPerkawinanProfil = findViewById<AutoCompleteTextView>(R.id.spinStatusPerkawinanProfil)
        val etPekerjaanProfil = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPekerjaanProfil)
        val etKewarganegaraanProfil = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etKewarganegaraanProfil)
        val etAlamatProfil = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etAlamatProfil)
        val etRtRwProfil = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etRtRwProfil)
        val etProvinsiProfil = findViewById<AutoCompleteTextView>(R.id.etProvinsiProfil)
        val etKotaProfil = findViewById<AutoCompleteTextView>(R.id.etKotaProfil)
        val etKecamatanProfil = findViewById<AutoCompleteTextView>(R.id.etKecamatanProfil)
        val etDesaProfil = findViewById<AutoCompleteTextView>(R.id.etDesaProfil)

        // Region Selection Logic
        loadProvinces(etProvinsiProfil, etKotaProfil, etKecamatanProfil, etDesaProfil)

        etProvinsiProfil.setOnClickListener { etProvinsiProfil.showDropDown() }
        etKotaProfil.setOnClickListener { etKotaProfil.showDropDown() }
        etKecamatanProfil.setOnClickListener { etKecamatanProfil.showDropDown() }
        etDesaProfil.setOnClickListener { etDesaProfil.showDropDown() }

        // ==========================================
        // SETUP DROPDOWN ADAPTERS (Jenis Kelamin, Agama, Status Perkawinan)
        // ==========================================
        val jenisKelaminOptions = arrayOf("Laki-laki", "Perempuan")
        val agamaOptions = arrayOf("Islam", "Kristen", "Katolik", "Hindu", "Buddha", "Konghucu")
        val statusPerkawinanOptions = arrayOf("Belum Kawin", "Kawin", "Cerai Hidup", "Cerai Mati")

        spinJenisKelaminProfil.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, jenisKelaminOptions)
        )
        spinAgamaProfil.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, agamaOptions)
        )
        spinStatusPerkawinanProfil.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, statusPerkawinanOptions)
        )

        // ==========================================
        // SETUP TANGGAL LAHIR DATE PICKER
        // ==========================================
        etTanggalLahirProfil.setOnClickListener {
            val calendar = Calendar.getInstance()

            // Coba parse tanggal yang sudah ada
            val currentText = etTanggalLahirProfil.text.toString()
            if (currentText.isNotEmpty()) {
                try {
                    val parts = currentText.split("-")
                    if (parts.size == 3) {
                        calendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                    }
                } catch (e: Exception) {
                    // Gunakan tanggal hari ini jika parsing gagal
                }
            }

            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val formattedDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                    etTanggalLahirProfil.setText(formattedDate)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // ==========================================
        // BINDING DATA KE UI DARI SESSION MANAGER (Data Awal)
        // ==========================================
        
        // Bind btnCamera and etPasswordProfil
        btnCamera.setOnClickListener {
            val options = arrayOf("Pilih dari Galeri", "Ambil Foto (Kamera)")
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Ganti Foto Profil")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> pickImageLauncher.launch("image/*")
                        1 -> {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                launchCamera()
                            } else {
                                requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }
                        }
                    }
                }
                .show()
        }
        val etPasswordProfil = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPasswordProfil)

        // Load profile picture if exists
        loadProfileImage(sessionManager.getFotoProfil())

        // Isi data awal dari session (sebagai placeholder sebelum API selesai)
        tvNamaProfil.text = sessionManager.getNamaUser()
        tvEmailProfil.text = sessionManager.getEmailUser() ?: "-"
        
        etNikProfil.setText(sessionManager.getNikUser())
        val kk = sessionManager.getNoKk()
        if (kk != null) {
            findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etKkProfil).setText(kk)
        }
        
        etNamaInputProfil.setText(sessionManager.getNamaUser())
        etEmailInputProfil.setText(sessionManager.getEmailUser())
        etNoHpProfil.setText(sessionManager.getNoHp())
        etTempatLahirProfil.setText(sessionManager.getTempatLahir())
        etTanggalLahirProfil.setText(sessionManager.getTanggalLahir())
        spinJenisKelaminProfil.setText(sessionManager.getJenisKelamin(), false)
        spinAgamaProfil.setText(sessionManager.getAgama(), false)
        spinStatusPerkawinanProfil.setText(sessionManager.getStatusPerkawinan(), false)
        etPekerjaanProfil.setText(sessionManager.getPekerjaan())
        etKewarganegaraanProfil.setText(sessionManager.getKewarganegaraan())
        etAlamatProfil.setText(sessionManager.getAlamatLengkap())
        etRtRwProfil.setText(sessionManager.getRtRw())
        etProvinsiProfil.setText(sessionManager.getProvinsi(), false)
        etKotaProfil.setText(sessionManager.getKota(), false)
        etKecamatanProfil.setText(sessionManager.getKecamatan(), false)
        etDesaProfil.setText(sessionManager.getKelurahanDesa(), false)
        
        selectedProvinceCode = sessionManager.getProvinsiCode() ?: ""
        selectedCityCode = sessionManager.getKotaCode() ?: ""
        selectedDistrictCode = sessionManager.getKecamatanCode() ?: ""
        selectedVillageCode = sessionManager.getKelurahanDesaCode() ?: ""
        
        if (selectedProvinceCode.isNotEmpty()) loadCities(selectedProvinceCode, etKotaProfil, etKecamatanProfil, etDesaProfil)
        if (selectedCityCode.isNotEmpty()) loadDistricts(selectedCityCode, etKecamatanProfil, etDesaProfil)
        if (selectedDistrictCode.isNotEmpty()) loadVillages(selectedDistrictCode, etDesaProfil)

        // ==========================================
        // LOAD DATA TERBARU DARI SERVER (override session data)
        // ==========================================
        // Ini penting agar field seperti no_hp yang mungkin sudah diupdate 
        // via web dashboard tetap muncul di aplikasi mobile.
        val nik = sessionManager.getNikUser()
        if (!nik.isNullOrEmpty()) {
            com.ta.sindesa.api.RetrofitClient.getInstance(this).getProfil().enqueue(
                object : retrofit2.Callback<com.ta.sindesa.models.LoginResponse> {
                    override fun onResponse(
                        call: retrofit2.Call<com.ta.sindesa.models.LoginResponse>,
                        response: retrofit2.Response<com.ta.sindesa.models.LoginResponse>
                    ) {
                        if (response.isSuccessful && response.body()?.success == true) {
                            val user = response.body()?.data?.user ?: return

                            // Update UI dengan data terbaru dari server
                            tvNamaProfil.text = user.nama
                            tvEmailProfil.text = user.email ?: "-"

                            etNamaInputProfil.setText(user.nama)
                            etEmailInputProfil.setText(user.email)
                            etNoHpProfil.setText(user.noHp)
                            etTempatLahirProfil.setText(user.tempatLahir)
                            etTanggalLahirProfil.setText(user.tanggalLahir)
                            spinJenisKelaminProfil.setText(user.jenisKelamin, false)
                            spinAgamaProfil.setText(user.agama, false)
                            spinStatusPerkawinanProfil.setText(user.statusPerkawinan, false)
                            etPekerjaanProfil.setText(user.pekerjaan)
                            etKewarganegaraanProfil.setText(user.kewarganegaraan)
                            etAlamatProfil.setText(user.alamatLengkap)
                            etRtRwProfil.setText(user.rtRw)
                            etProvinsiProfil.setText(user.provinsi, false)
                            etKotaProfil.setText(user.kota, false)
                            etKecamatanProfil.setText(user.kecamatan, false)
                            etDesaProfil.setText(user.kelurahanDesa, false)
                            
                            selectedProvinceCode = user.provinsiCode ?: ""
                            selectedCityCode = user.kotaCode ?: ""
                            selectedDistrictCode = user.kecamatanCode ?: ""
                            selectedVillageCode = user.kelurahanDesaCode ?: ""
                            
                            if (selectedProvinceCode.isNotEmpty()) loadCities(selectedProvinceCode, etKotaProfil, etKecamatanProfil, etDesaProfil)
                            if (selectedCityCode.isNotEmpty()) loadDistricts(selectedCityCode, etKecamatanProfil, etDesaProfil)
                            if (selectedDistrictCode.isNotEmpty()) loadVillages(selectedDistrictCode, etDesaProfil)

                            if (!user.noKk.isNullOrEmpty()) {
                                findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etKkProfil).setText(user.noKk)
                            }

                            // Update session juga agar konsisten
                            sessionManager.setLoggedIn(
                                true,
                                userId = user.id,
                                nama = user.nama,
                                nik = user.nik,
                                email = user.email,
                                token = sessionManager.getToken(),
                                noKk = user.noKk,
                                agama = user.agama,
                                jenisKelamin = user.jenisKelamin,
                                tempatLahir = user.tempatLahir,
                                tanggalLahir = user.tanggalLahir,
                                statusPerkawinan = user.statusPerkawinan,
                                pekerjaan = user.pekerjaan,
                                kewarganegaraan = user.kewarganegaraan,
                                alamatLengkap = user.alamatLengkap,
                                rtRw = user.rtRw,
                                provinsi = user.provinsi,
                                kota = user.kota,
                                kecamatan = user.kecamatan,
                                kelurahanDesa = user.kelurahanDesa,
                                provinsiCode = user.provinsiCode,
                                kotaCode = user.kotaCode,
                                kecamatanCode = user.kecamatanCode,
                                kelurahanDesaCode = user.kelurahanDesaCode,
                                noHp = user.noHp,
                                fotoProfil = user.fotoProfil ?: sessionManager.getFotoProfil(),
                                status = user.status ?: sessionManager.getStatus()
                            )
                            val latestFoto = user.fotoProfil ?: sessionManager.getFotoProfil()
                            if (!latestFoto.isNullOrEmpty()) {
                                loadProfileImage(latestFoto)
                            }
                            SidebarUtil.refreshSidebarProfile(this@ProfileActivity)
                        }
                    }

                    override fun onFailure(
                        call: retrofit2.Call<com.ta.sindesa.models.LoginResponse>,
                        t: Throwable
                    ) {
                        // Silently fail - data dari session sudah ditampilkan sebagai fallback
                        android.util.Log.e("PROFILE", "Gagal load profil dari server: ${t.message}")
                    }
                }
            )
        }

        // 2. Buka Sidebar saat menu hamburger diklik
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Initialize Sidebar using Utility
        SidebarUtil.initSidebar(this, drawerLayout)

        // 4. Save Profile Changes
        val btnSimpanProfil = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSimpanProfil)
        btnSimpanProfil.setOnClickListener {
            val nikSave = sessionManager.getNikUser() ?: return@setOnClickListener
            val nama = etNamaInputProfil.text.toString()
            val email = etEmailInputProfil.text.toString()
            val noHp = etNoHpProfil.text.toString()
            val tempatLahir = etTempatLahirProfil.text.toString()
            val tanggalLahir = etTanggalLahirProfil.text.toString()
            val jenisKelamin = spinJenisKelaminProfil.text.toString()
            val agama = spinAgamaProfil.text.toString()
            val statusPerkawinan = spinStatusPerkawinanProfil.text.toString()
            val pekerjaan = etPekerjaanProfil.text.toString()
            val kewarganegaraan = etKewarganegaraanProfil.text.toString()
            val alamatLengkap = etAlamatProfil.text.toString()
            val rtRw = etRtRwProfil.text.toString()
            val provinsi = etProvinsiProfil.text.toString()
            val kota = etKotaProfil.text.toString()
            val kecamatan = etKecamatanProfil.text.toString()
            val kelurahanDesa = etDesaProfil.text.toString()
            val password = etPasswordProfil.text.toString()
            
            val newNik = etNikProfil.text.toString()
            val noKk = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etKkProfil).text.toString()

            btnSimpanProfil.isEnabled = false
            btnSimpanProfil.text = "MENYIMPAN..."

            val rbNik = nikSave.toRequestBody("text/plain".toMediaTypeOrNull())
            val rbNewNik = newNik.toRequestBody("text/plain".toMediaTypeOrNull())
            val rbNoKk = noKk.toRequestBody("text/plain".toMediaTypeOrNull())
            val rbNama = nama.toRequestBody("text/plain".toMediaTypeOrNull())
            val rbEmail = email.toRequestBody("text/plain".toMediaTypeOrNull())
            val rbNoHp = noHp.toRequestBody("text/plain".toMediaTypeOrNull())
            val rbTempatLahir = tempatLahir.toRequestBody("text/plain".toMediaTypeOrNull())
            val rbTanggalLahir = tanggalLahir.toRequestBody("text/plain".toMediaTypeOrNull())
            val rbJenisKelamin = jenisKelamin.toRequestBody("text/plain".toMediaTypeOrNull())
            val rbAgama = agama.toRequestBody("text/plain".toMediaTypeOrNull())
            val rbStatusPerkawinan = statusPerkawinan.toRequestBody("text/plain".toMediaTypeOrNull())
            val rbPekerjaan = pekerjaan.toRequestBody("text/plain".toMediaTypeOrNull())
            val rbKewarganegaraan = kewarganegaraan.toRequestBody("text/plain".toMediaTypeOrNull())
            val rbAlamatLengkap = alamatLengkap.toRequestBody("text/plain".toMediaTypeOrNull())
            val rbRtRw = rtRw.toRequestBody("text/plain".toMediaTypeOrNull())
            val rbProvinsi = provinsi.toRequestBody("text/plain".toMediaTypeOrNull())
            val rbKota = kota.toRequestBody("text/plain".toMediaTypeOrNull())
            val rbKecamatan = kecamatan.toRequestBody("text/plain".toMediaTypeOrNull())
            val rbKelurahanDesa = kelurahanDesa.toRequestBody("text/plain".toMediaTypeOrNull())
            val rbPassword = password.toRequestBody("text/plain".toMediaTypeOrNull())

            var partFotoProfil: MultipartBody.Part? = null
            if (selectedImageUri != null) {
                try {
                    val file = FileUtils.uriToFile(this@ProfileActivity, selectedImageUri!!)
                    if (file.exists() && file.length() > 0) {
                        val mimeType = if (selectedImageUri!!.scheme == "content") {
                            contentResolver.getType(selectedImageUri!!) ?: "image/jpeg"
                        } else {
                            "image/jpeg"
                        }
                        val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
                        partFotoProfil = MultipartBody.Part.createFormData("foto_profil", file.name, requestFile)
                    } else {
                        android.util.Log.e("PROFILE", "File foto kosong atau tidak ditemukan: ${file.absolutePath}")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            com.ta.sindesa.api.RetrofitClient.getInstance(this).updateProfil(
                rbNewNik, rbNoKk, rbNama, rbEmail, rbNoHp, rbTempatLahir, rbTanggalLahir, rbJenisKelamin, rbAgama,
                rbStatusPerkawinan, rbPekerjaan, rbKewarganegaraan, rbAlamatLengkap, rbRtRw,
                rbProvinsi, rbKota, rbKecamatan, rbKelurahanDesa, rbPassword, partFotoProfil
            ).enqueue(object : retrofit2.Callback<com.ta.sindesa.models.LoginResponse> {
                override fun onResponse(call: retrofit2.Call<com.ta.sindesa.models.LoginResponse>, response: retrofit2.Response<com.ta.sindesa.models.LoginResponse>) {
                    btnSimpanProfil.isEnabled = true
                    btnSimpanProfil.text = "SIMPAN PERUBAHAN"
                    if (response.isSuccessful && response.body()?.success == true) {
                        
                        val newFotoProfil = response.body()?.fotoProfilUpdate ?: sessionManager.getFotoProfil()
                        
                        sessionManager.setLoggedIn(
                            isLoggedIn = true,
                            userId = sessionManager.getUserId(),
                            nama = nama,
                            nik = newNik,
                            email = email,
                            token = sessionManager.getToken(),
                            noKk = noKk,
                            agama = agama,
                            jenisKelamin = jenisKelamin,
                            tempatLahir = tempatLahir,
                            tanggalLahir = tanggalLahir,
                            statusPerkawinan = statusPerkawinan,
                            pekerjaan = pekerjaan,
                            kewarganegaraan = kewarganegaraan,
                            alamatLengkap = alamatLengkap,
                            rtRw = rtRw,
                            provinsi = provinsi,
                            kota = kota,
                            kecamatan = kecamatan,
                            kelurahanDesa = kelurahanDesa,
                            provinsiCode = selectedProvinceCode,
                            kotaCode = selectedCityCode,
                            kecamatanCode = selectedDistrictCode,
                            kelurahanDesaCode = selectedVillageCode,
                            noHp = noHp,
                            fotoProfil = newFotoProfil,
                            status = sessionManager.getStatus()
                        )
                        tvNamaProfil.text = nama
                        tvEmailProfil.text = email
                        loadProfileImage(newFotoProfil)
                        SidebarUtil.refreshSidebarProfile(this@ProfileActivity)
                        Toast.makeText(this@ProfileActivity, "Profil berhasil diperbarui", Toast.LENGTH_SHORT).show()
                        
                        etPasswordProfil.setText("") // Kosongkan password setelah sukses
                    } else {
                        val serverMsg = response.body()?.message ?: "Gagal memperbarui profil"
                        Toast.makeText(this@ProfileActivity, serverMsg, Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: retrofit2.Call<com.ta.sindesa.models.LoginResponse>, t: Throwable) {
                    btnSimpanProfil.isEnabled = true
                    btnSimpanProfil.text = "SIMPAN PERUBAHAN"
                    val errorMsg = t.message ?: "Kesalahan jaringan"
                    Toast.makeText(this@ProfileActivity, "Error: $errorMsg", Toast.LENGTH_LONG).show()
                }
            })
        }

        // CARA MODERN 2: Mengganti onBackPressed untuk menangani tombol kembali HP
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Jika sidebar sedang terbuka, tutup sidebarnya
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    // Jika tidak, matikan pencegatan ini dan biarkan aplikasi kembali ke halaman sebelumnya (Dashboard)
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun loadProvinces(spProv: AutoCompleteTextView, spKota: AutoCompleteTextView, spKec: AutoCompleteTextView, spKel: AutoCompleteTextView) {
        com.ta.sindesa.api.RetrofitClient.getInstance(this).getProvinces().enqueue(object : Callback<List<Region>> {
            override fun onResponse(call: Call<List<Region>>, response: Response<List<Region>>) {
                if (response.isSuccessful && response.body() != null) {
                    val provinces = response.body()!!
                    val adapter = ArrayAdapter(this@ProfileActivity, android.R.layout.simple_dropdown_item_1line, provinces)
                    spProv.setAdapter(adapter)
                    spProv.setOnItemClickListener { _, _, position, _ ->
                        val selected = provinces[position]
                        selectedProvinceCode = selected.code
                        
                        // Reset and Load Cities
                        selectedCityCode = ""
                        spKota.setText("", false)
                        spKota.isEnabled = true
                        
                        selectedDistrictCode = ""
                        spKec.setText("Pilih Kota dahulu", false)
                        spKec.isEnabled = false
                        
                        selectedVillageCode = ""
                        spKel.setText("Pilih Kecamatan dahulu", false)
                        spKel.isEnabled = false
                        
                        loadCities(selected.code, spKota, spKec, spKel)
                    }
                }
            }
            override fun onFailure(call: Call<List<Region>>, t: Throwable) {
                android.util.Log.e("PROFILE", "Load Provinces Failure", t)
            }
        })
    }

    private fun loadCities(provCode: String, spKota: AutoCompleteTextView, spKec: AutoCompleteTextView, spKel: AutoCompleteTextView) {
        com.ta.sindesa.api.RetrofitClient.getInstance(this).getCities(provCode).enqueue(object : Callback<List<Region>> {
            override fun onResponse(call: Call<List<Region>>, response: Response<List<Region>>) {
                if (response.isSuccessful && response.body() != null) {
                    val cities = response.body()!!
                    val adapter = ArrayAdapter(this@ProfileActivity, android.R.layout.simple_dropdown_item_1line, cities)
                    spKota.setAdapter(adapter)
                    spKota.setOnItemClickListener { _, _, position, _ ->
                        val selected = cities[position]
                        selectedCityCode = selected.code
                        
                        // Reset and Load Districts
                        selectedDistrictCode = ""
                        spKec.setText("", false)
                        spKec.isEnabled = true
                        
                        selectedVillageCode = ""
                        spKel.setText("Pilih Kecamatan dahulu", false)
                        spKel.isEnabled = false
                        
                        loadDistricts(selected.code, spKec, spKel)
                    }
                }
            }
            override fun onFailure(call: Call<List<Region>>, t: Throwable) {
                android.util.Log.e("PROFILE", "Load Cities Failure", t)
            }
        })
    }

    private fun loadDistricts(cityCode: String, spKec: AutoCompleteTextView, spKel: AutoCompleteTextView) {
        com.ta.sindesa.api.RetrofitClient.getInstance(this).getDistricts(cityCode).enqueue(object : Callback<List<Region>> {
            override fun onResponse(call: Call<List<Region>>, response: Response<List<Region>>) {
                if (response.isSuccessful && response.body() != null) {
                    val districts = response.body()!!
                    val adapter = ArrayAdapter(this@ProfileActivity, android.R.layout.simple_dropdown_item_1line, districts)
                    spKec.setAdapter(adapter)
                    spKec.setOnItemClickListener { _, _, position, _ ->
                        val selected = districts[position]
                        selectedDistrictCode = selected.code
                        
                        // Reset and Load Villages
                        selectedVillageCode = ""
                        spKel.setText("", false)
                        spKel.isEnabled = true
                        loadVillages(selected.code, spKel)
                    }
                }
            }
            override fun onFailure(call: Call<List<Region>>, t: Throwable) {
                android.util.Log.e("PROFILE", "Load Districts Failure", t)
            }
        })
    }

    private fun loadVillages(distCode: String, spKel: AutoCompleteTextView) {
        com.ta.sindesa.api.RetrofitClient.getInstance(this).getVillages(distCode).enqueue(object : Callback<List<Region>> {
            override fun onResponse(call: Call<List<Region>>, response: Response<List<Region>>) {
                if (response.isSuccessful && response.body() != null) {
                    val villages = response.body()!!
                    val adapter = ArrayAdapter(this@ProfileActivity, android.R.layout.simple_dropdown_item_1line, villages)
                    spKel.setAdapter(adapter)
                    spKel.setOnItemClickListener { _, _, position, _ ->
                        selectedVillageCode = villages[position].code
                    }
                }
            }
            override fun onFailure(call: Call<List<Region>>, t: Throwable) {
                android.util.Log.e("PROFILE", "Load Villages Failure", t)
            }
        })
    }

    private fun loadProfileImage(fotoPath: String?) {
        val imageUrl = com.ta.sindesa.api.RetrofitClient.getProfileImageUrl(fotoPath)
        if (!imageUrl.isNullOrEmpty()) {
            Thread {
                try {
                    val stream = java.net.URL(imageUrl).openStream()
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        runOnUiThread { imgProfile.setImageBitmap(bitmap) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        }
    }

    override fun onResume() {
        super.onResume()
        SidebarUtil.refreshSidebarProfile(this)
    }
}
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
import com.ta.sindesa.models.LoginResponse
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
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PindahActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var sessionManager: SessionManager

    private lateinit var etNik: TextInputEditText
    private lateinit var etNoKk: TextInputEditText
    private lateinit var etNama: TextInputEditText
    private lateinit var etTempatLahir: TextInputEditText
    private lateinit var etTanggalLahir: TextInputEditText
    private lateinit var spinJenisKelamin: AutoCompleteTextView
    private lateinit var spinAgama: AutoCompleteTextView
    private lateinit var spinStatusPerkawinan: AutoCompleteTextView
    private lateinit var etPekerjaan: TextInputEditText
    private lateinit var etPendidikan: TextInputEditText
    private lateinit var etDusunAsal: TextInputEditText
    private lateinit var etRtAsal: TextInputEditText
    private lateinit var etRwAsal: TextInputEditText
    private lateinit var etAlamatTujuan: TextInputEditText
    private lateinit var etRtTujuan: TextInputEditText
    private lateinit var etRwTujuan: TextInputEditText
    private lateinit var etDesaTujuan: TextInputEditText
    private lateinit var etKecTujuan: TextInputEditText
    private lateinit var etKabTujuan: TextInputEditText
    private lateinit var etProvTujuan: TextInputEditText
    private lateinit var etPosTujuan: TextInputEditText
    private lateinit var etAlasan: TextInputEditText
    private lateinit var etTanggalPindah: TextInputEditText
    
    // Dynamic Pengikut
    private lateinit var pengikutContainer: LinearLayout
    private lateinit var btnAddPengikut: MaterialButton
    private lateinit var tvEmptyPengikut: TextView
    private val pengikutViews = mutableListOf<android.view.View>()
    private lateinit var cbAgree: CheckBox
    private lateinit var btnSubmit: MaterialButton

    private lateinit var tvFileNameKtp: TextView
    private lateinit var tvFileNameKk: TextView
    private lateinit var tvFileNameLain: TextView

    private var uriKtp: Uri? = null
    private var uriKk: Uri? = null
    private var uriLain: Uri? = null

    private var currentPhotoPath: String? = null
    private var cameraImageUri: Uri? = null
    private var activeUploader: String = "" // "KTP", "KK", "LAIN"
    private var editId: Int = 0


    // Region variables removed (now using manual inputs)

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
    private val pickLainGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            uriLain = it
            tvFileNameLain.text = FileUtils.getFileName(this, it)
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

        setContentView(R.layout.activity_pindah)
        setupAutoFill()

        initViews()
        setupSidebar()
        setupDropdowns()
        setupUploadButtons()
        setupDatePickers()

        btnSubmit.setOnClickListener {
            submitForm()
        }

        btnAddPengikut.setOnClickListener {
            addPengikutView()
        }

        checkEditMode()
    }

    private fun addPengikutView() {
        val view = layoutInflater.inflate(R.layout.item_pengikut_pindah, pengikutContainer, false)
        
        val tvTitle = view.findViewById<TextView>(R.id.tvPengikutTitle)
        val btnRemove = view.findViewById<ImageButton>(R.id.btnRemovePengikut)
        val etTglLahir = view.findViewById<TextInputEditText>(R.id.etPengikutTglLahir)
        val spinJk = view.findViewById<AutoCompleteTextView>(R.id.spinPengikutJk)
        val spinStatus = view.findViewById<AutoCompleteTextView>(R.id.spinPengikutStatus)

        // Dropdowns
        val jkAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, arrayOf("Laki-laki", "Perempuan"))
        spinJk.setAdapter(jkAdapter)
        
        val statusAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, arrayOf("Belum Kawin", "Kawin", "Cerai Hidup", "Cerai Mati"))
        spinStatus.setAdapter(statusAdapter)

        // Date picker
        etTglLahir.setOnClickListener { showDatePickerDialog(etTglLahir) }

        pengikutViews.add(view)
        tvTitle.text = "Data Anggota #${pengikutViews.size}"
        
        btnRemove.setOnClickListener {
            pengikutContainer.removeView(view)
            pengikutViews.remove(view)
            updatePengikutTitles()
        }
        
        pengikutContainer.addView(view)
        tvEmptyPengikut.visibility = android.view.View.GONE
    }

    private fun updatePengikutTitles() {
        for ((index, view) in pengikutViews.withIndex()) {
            view.findViewById<TextView>(R.id.tvPengikutTitle).text = "Data Anggota #${index + 1}"
        }
        if (pengikutViews.isEmpty()) {
            tvEmptyPengikut.visibility = android.view.View.VISIBLE
        }
    }

    // Move EDIT_ID check into onCreate or after setup
    private fun checkEditMode() {
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
                            setEditText("etNoKk", "no_kk", "kk")
                            setEditText("etNama", "nama_lengkap", "nama")
                            setEditText("etTempatLahir", "tempat_lahir")
                            setEditText("etTanggalLahir", "tanggal_lahir")
                            setEditText("spinJenisKelamin", "jenis_kelamin")
                            setEditText("spinAgama", "agama")
                            setEditText("etPekerjaan", "pekerjaan")
                            setEditText("spinStatusPerkawinan", "status_perkawinan")
                            setEditText("etDusunAsal", "dusun_asal", "alamat_asal_dusun")
                            setEditText("etRtAsal", "rt_asal", "alamat_asal_rt")
                            setEditText("etRwAsal", "rw_asal", "alamat_asal_rw")
                            setEditText("etAlamatTujuan", "alamat_tujuan_jalan", "alamat_tujuan")
                            setEditText("etRtTujuan", "rt_tujuan", "alamat_tujuan_rt")
                            setEditText("etRwTujuan", "rw_tujuan", "alamat_tujuan_rw")
                            setEditText("etDesaTujuan", "desa_tujuan", "alamat_tujuan_desa", "kelurahan_tujuan")
                            setEditText("etKecTujuan", "kec_tujuan", "alamat_tujuan_kecamatan", "kecamatan_tujuan")
                            setEditText("etKabTujuan", "kab_tujuan", "alamat_tujuan_kabupaten", "kota_tujuan")
                            setEditText("etProvTujuan", "prov_tujuan", "alamat_tujuan_provinsi", "provinsi_tujuan")
                            setEditText("etPosTujuan", "pos_tujuan", "alamat_tujuan_kodepos", "kode_pos")
                            setEditText("etAlasan", "alasan_pindah", "alasan")
                            setEditText("etTanggalPindah", "tanggal_pindah")

                            // Parse nested alamat_asal jika disimpan sebagai JSON object / Map
                            val rawAlamatAsal = dataTambahanMap["alamat_asal"]
                            val alamatAsalObj: Map<*, *>? = when (rawAlamatAsal) {
                                is Map<*, *> -> rawAlamatAsal
                                is String -> {
                                    try {
                                        val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
                                        com.google.gson.Gson().fromJson<Map<*, *>>(rawAlamatAsal, type)
                                    } catch (e: Exception) { null }
                                }
                                else -> null
                            }
                            if (alamatAsalObj != null) {
                                (alamatAsalObj["dusun"]?.toString())?.let { if (it.isNotEmpty()) etDusunAsal.setText(it) }
                                (alamatAsalObj["rt"]?.toString())?.let { if (it.isNotEmpty()) etRtAsal.setText(it) }
                                (alamatAsalObj["rw"]?.toString())?.let { if (it.isNotEmpty()) etRwAsal.setText(it) }
                            }

                            // Parse nested alamat_tujuan jika disimpan sebagai JSON object / Map
                            val rawAlamatTujuan = dataTambahanMap["alamat_tujuan"] ?: dataTambahanMap["alamat_tujuan_detail"]
                            val alamatTujuanObj: Map<*, *>? = when (rawAlamatTujuan) {
                                is Map<*, *> -> rawAlamatTujuan
                                is String -> {
                                    try {
                                        val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
                                        com.google.gson.Gson().fromJson<Map<*, *>>(rawAlamatTujuan, type)
                                    } catch (e: Exception) { null }
                                }
                                else -> null
                            }
                            if (alamatTujuanObj != null) {
                                (alamatTujuanObj["jalan"]?.toString())?.let { if (it.isNotEmpty()) etAlamatTujuan.setText(it) }
                                (alamatTujuanObj["rt"]?.toString())?.let { if (it.isNotEmpty()) etRtTujuan.setText(it) }
                                (alamatTujuanObj["rw"]?.toString())?.let { if (it.isNotEmpty()) etRwTujuan.setText(it) }
                                (alamatTujuanObj["desa"]?.toString())?.let { if (it.isNotEmpty()) etDesaTujuan.setText(it) }
                                (alamatTujuanObj["kecamatan"]?.toString())?.let { if (it.isNotEmpty()) etKecTujuan.setText(it) }
                                (alamatTujuanObj["kabupaten"]?.toString())?.let { if (it.isNotEmpty()) etKabTujuan.setText(it) }
                                (alamatTujuanObj["provinsi"]?.toString())?.let { if (it.isNotEmpty()) etProvTujuan.setText(it) }
                                (alamatTujuanObj["kode_pos"]?.toString())?.let { if (it.isNotEmpty()) etPosTujuan.setText(it) }
                            }

                            // Parse anggota_keluarga (dynamic pengikut) saat edit
                            val anggotaKeluarga = dataTambahanMap["anggota_keluarga"] ?: dataTambahanMap["keluarga_ikut"]
                            val listAnggota: List<Map<*, *>>? = when (anggotaKeluarga) {
                                is List<*> -> anggotaKeluarga.filterIsInstance<Map<*, *>>()
                                is String -> {
                                    try {
                                        val type = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
                                        com.google.gson.Gson().fromJson<List<Map<*, *>>>(anggotaKeluarga, type)
                                    } catch (e: Exception) { null }
                                }
                                else -> null
                            }
                            
                            if (!listAnggota.isNullOrEmpty()) {
                                pengikutContainer.removeAllViews()
                                pengikutViews.clear()

                                listAnggota.forEach { item ->
                                    val view = layoutInflater.inflate(R.layout.item_pengikut_pindah, pengikutContainer, false)
                                    view.findViewById<TextInputEditText>(R.id.etPengikutNama)?.setText(item["nama"]?.toString() ?: "")
                                    view.findViewById<TextInputEditText>(R.id.etPengikutNik)?.setText(item["nik"]?.toString() ?: "")
                                    view.findViewById<AutoCompleteTextView>(R.id.spinPengikutJk)?.setText(item["jenis_kelamin"]?.toString() ?: "", false)
                                    view.findViewById<TextInputEditText>(R.id.etPengikutTglLahir)?.setText(item["tanggal_lahir"]?.toString() ?: "")
                                    view.findViewById<AutoCompleteTextView>(R.id.spinPengikutStatus)?.setText(item["status_perkawinan"]?.toString() ?: "", false)
                                    view.findViewById<TextInputEditText>(R.id.etPengikutKet)?.setText(item["keterangan"]?.toString() ?: item["keterangan_hub"]?.toString() ?: "")
                                    
                                    val spinJk = view.findViewById<AutoCompleteTextView>(R.id.spinPengikutJk)
                                    val spinStatus = view.findViewById<AutoCompleteTextView>(R.id.spinPengikutStatus)
                                    val etTglLahir = view.findViewById<TextInputEditText>(R.id.etPengikutTglLahir)
                                    
                                    spinJk?.setAdapter(ArrayAdapter(this@PindahActivity, android.R.layout.simple_dropdown_item_1line, arrayOf("Laki-laki", "Perempuan")))
                                    spinStatus?.setAdapter(ArrayAdapter(this@PindahActivity, android.R.layout.simple_dropdown_item_1line, arrayOf("Belum Kawin", "Kawin", "Cerai Hidup", "Cerai Mati")))
                                    etTglLahir?.setOnClickListener { showDatePickerDialog(etTglLahir) }
                                    
                                    val btnRemove = view.findViewById<ImageButton>(R.id.btnRemovePengikut)
                                    btnRemove?.setOnClickListener {
                                        pengikutContainer.removeView(view)
                                        pengikutViews.remove(view)
                                        updatePengikutTitles()
                                    }
                                    pengikutViews.add(view)
                                    pengikutContainer.addView(view)
                                }
                                updatePengikutTitles()
                                tvEmptyPengikut.visibility = android.view.View.GONE
                            }

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
                Toast.makeText(this@PindahActivity, "Gagal memuat data editan", Toast.LENGTH_SHORT).show()
            }
        })
    }


    private fun setupDatePickers() {
        etTanggalLahir.setOnClickListener {
            showDatePickerDialog(etTanggalLahir)
        }
        etTanggalLahir.isFocusable = false
        etTanggalLahir.isClickable = true
        etTanggalPindah.setOnClickListener {
            showDatePickerDialog(etTanggalPindah)
        }
        etTanggalPindah.isFocusable = false
        etTanggalPindah.isClickable = true
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
        etNik = findViewById(R.id.etNik)
        etNoKk = findViewById(R.id.etNoKk)
        etNama = findViewById(R.id.etNama)
        etTempatLahir = findViewById(R.id.etTempatLahir)
        etTanggalLahir = findViewById(R.id.etTanggalLahir)
        spinJenisKelamin = findViewById(R.id.spinJenisKelamin)
        spinAgama = findViewById(R.id.spinAgama)
        spinStatusPerkawinan = findViewById(R.id.spinStatusPerkawinan)
        etPekerjaan = findViewById(R.id.etPekerjaan)
        etPendidikan = findViewById(R.id.etPendidikan)
        
        etDusunAsal = findViewById(R.id.etDusunAsal)
        etRtAsal = findViewById(R.id.etRtAsal)
        etRwAsal = findViewById(R.id.etRwAsal)
        etAlamatTujuan = findViewById(R.id.etAlamatTujuan)
        etRtTujuan = findViewById(R.id.etRtTujuan)
        etRwTujuan = findViewById(R.id.etRwTujuan)
        etDesaTujuan = findViewById(R.id.etDesaTujuan)
        etKecTujuan = findViewById(R.id.etKecTujuan)
        etKabTujuan = findViewById(R.id.etKabTujuan)
        etProvTujuan = findViewById(R.id.etProvTujuan)
        etPosTujuan = findViewById(R.id.etPosTujuan)
        etAlasan = findViewById(R.id.etAlasan)
        etTanggalPindah = findViewById(R.id.etTanggalPindah)
        
        pengikutContainer = findViewById(R.id.pengikutContainer)
        btnAddPengikut = findViewById(R.id.btnAddPengikut)
        tvEmptyPengikut = findViewById(R.id.tvEmptyPengikut)
        cbAgree = findViewById(R.id.cbAgree)
        btnSubmit = findViewById(R.id.btnSubmit)
        tvFileNameKtp = findViewById(R.id.tvFileNameKtp)
        tvFileNameKk = findViewById(R.id.tvFileNameKk)
        tvFileNameLain = findViewById(R.id.tvFileNameLain)
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
        
        val agama = arrayOf("Islam", "Kristen Protestan", "Katolik", "Hindu", "Buddha", "Konghucu")
        val adapterAgama = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, agama)
        spinAgama.setAdapter(adapterAgama)

        val statusPerkawinan = arrayOf("Belum Kawin", "Kawin", "Cerai Hidup", "Cerai Mati")
        val adapterStatus = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, statusPerkawinan)
        spinStatusPerkawinan.setAdapter(adapterStatus)

    }

    private fun setupUploadButtons() {
        findViewById<MaterialButton>(R.id.btnUploadKtp).setOnClickListener {
            showImagePickerDialog("KTP")
        }
        findViewById<MaterialButton>(R.id.btnUploadKk).setOnClickListener {
            showImagePickerDialog("KK")
        }
        findViewById<MaterialButton>(R.id.btnUploadLain).setOnClickListener {
            showImagePickerDialog("LAIN")
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
                    1 -> {
                        when (type) {
                            "KTP" -> pickKtpGalleryLauncher.launch("*/*")
                            "KK" -> pickKkGalleryLauncher.launch("*/*")
                            "LAIN" -> pickLainGalleryLauncher.launch("*/*")
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
        val nik = etNik.text.toString().trim()
        val noKk = etNoKk.text.toString().trim()
        val nama = etNama.text.toString().trim()
        val tempatLahir = etTempatLahir.text.toString().trim()
        val tglLahir = etTanggalLahir.text.toString().trim()
        val jk = spinJenisKelamin.text.toString().trim()
        val agama = spinAgama.text.toString().trim()
        val statusPerkawinan = spinStatusPerkawinan.text.toString().trim()
        val pekerjaan = etPekerjaan.text.toString().trim()
        val pendidikan = etPendidikan.text.toString().trim()
        val dusunAsal = etDusunAsal.text.toString().trim()
        val rtAsal = etRtAsal.text.toString().trim()
        val rwAsal = etRwAsal.text.toString().trim()
        val alamatTujuan = etAlamatTujuan.text.toString().trim()
        val rtTujuan = etRtTujuan.text.toString().trim()
        val rwTujuan = etRwTujuan.text.toString().trim()
        val desaTujuan = etDesaTujuan.text.toString().trim()
        val kecTujuan = etKecTujuan.text.toString().trim()
        val kabTujuan = etKabTujuan.text.toString().trim()
        val provTujuan = etProvTujuan.text.toString().trim()
        val posTujuan = etPosTujuan.text.toString().trim()
        val alasan = etAlasan.text.toString().trim()
        val tglPindah = etTanggalPindah.text.toString().trim()
        
        // Build JSON array for keluarga_ikut
        val jsonArray = org.json.JSONArray()
        for (view in pengikutViews) {
            val nama = view.findViewById<TextInputEditText>(R.id.etPengikutNama).text.toString().trim()
            val nikPengikut = view.findViewById<TextInputEditText>(R.id.etPengikutNik).text.toString().trim()
            val jk = view.findViewById<AutoCompleteTextView>(R.id.spinPengikutJk).text.toString().trim()
            val tgl = view.findViewById<TextInputEditText>(R.id.etPengikutTglLahir).text.toString().trim()
            val status = view.findViewById<AutoCompleteTextView>(R.id.spinPengikutStatus).text.toString().trim()
            val ket = view.findViewById<TextInputEditText>(R.id.etPengikutKet).text.toString().trim()
            
            if (nama.isNotEmpty()) {
                val obj = org.json.JSONObject()
                obj.put("nama", nama)
                obj.put("nik", nikPengikut)
                obj.put("jenis_kelamin", jk)
                obj.put("tanggal_lahir", tgl)
                obj.put("status_perkawinan", status)
                obj.put("keterangan", ket)
                jsonArray.put(obj)
            }
        }
        val keluargaIkut = jsonArray.toString()

        if (nik.isEmpty() || nik.length != 16) {
            etNik.error = "NIK harus 16 digit"
            etNik.requestFocus()
            return
        }

        if (noKk.isEmpty() || noKk.length != 16) {
            etNoKk.error = "No KK harus 16 digit"
            etNoKk.requestFocus()
            return
        }

        if (nama.isEmpty()) {
            etNama.error = "Nama wajib diisi"
            etNama.requestFocus()
            return
        }

        if (dusunAsal.isEmpty()) {
            etDusunAsal.error = "Dusun asal wajib diisi"
            etDusunAsal.requestFocus()
            return
        }

        if (alamatTujuan.isEmpty()) {
            etAlamatTujuan.error = "Alamat tujuan wajib diisi"
            etAlamatTujuan.requestFocus()
            return
        }

        if (editId == 0 && (uriKtp == null || uriKk == null)) {
            Toast.makeText(this, "Mohon unggah berkas KTP dan KK", Toast.LENGTH_SHORT).show()
            return
        }

        if (!cbAgree.isChecked) {
            Toast.makeText(this, "Anda harus menyetujui pernyataan data benar", Toast.LENGTH_SHORT).show()
            return
        }

        btnSubmit.isEnabled = false
        btnSubmit.text = if (editId > 0) "Menyimpan Perubahan..." else "Mengirim..."

        val rbNik = nik.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbNoKk = noKk.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbNama = nama.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbTempatLahir = tempatLahir.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbTglLahir = tglLahir.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbJk = jk.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbAgama = agama.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbStatusPerkawinan = statusPerkawinan.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbPekerjaan = pekerjaan.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbPendidikan = pendidikan.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbDusunAsal = dusunAsal.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbRtAsal = rtAsal.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbRwAsal = rwAsal.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbAlamatTujuan = alamatTujuan.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbRtTujuan = rtTujuan.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbRwTujuan = rwTujuan.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbDesaTujuan = desaTujuan.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbKecTujuan = kecTujuan.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbKabTujuan = kabTujuan.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbProvTujuan = provTujuan.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbPosTujuan = posTujuan.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbAlasan = alasan.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbTglPindah = tglPindah.toRequestBody("text/plain".toMediaTypeOrNull())
        val rbKeluargaIkut = keluargaIkut.toRequestBody("text/plain".toMediaTypeOrNull())

        val partKtp = prepareFilePart("berkas_ktp", uriKtp!!)!!
        val partKk = prepareFilePart("berkas_kk", uriKk!!)!!
        val partLain = uriLain?.let { prepareFilePart("berkas_lain", it) }

        val rbEditId = if (editId > 0) editId.toString().toRequestBody("text/plain".toMediaTypeOrNull()) else null

        RetrofitClient.getInstance(this).submitPindah(
            rbNik, rbNoKk, rbNama, rbTempatLahir, rbTglLahir, rbJk, rbAgama, rbStatusPerkawinan, rbPekerjaan, rbPendidikan,
            rbDusunAsal, rbRtAsal, rbRwAsal,
            rbAlamatTujuan, rbRtTujuan, rbRwTujuan, rbDesaTujuan, rbKecTujuan, rbKabTujuan, rbProvTujuan, rbPosTujuan,
            rbAlasan, rbTglPindah, rbKeluargaIkut,
            partKtp, partKk, partLain
        , rbEditId).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                btnSubmit.isEnabled = true
                btnSubmit.text = if (editId > 0) "SIMPAN PERUBAHAN" else "KIRIM PENGAJUAN"
                
                // Hapus file temporary di cache setelah upload selesai (OWASP M2)
                FileUtils.clearCacheFiles(this@PindahActivity)

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.success == true) {
                        AlertDialog.Builder(this@PindahActivity)
                            .setTitle("Berhasil")
                            .setMessage("Pengajuan Keterangan Pindah telah berhasil dikirim.")
                            .setPositiveButton("OK") { _, _ -> finish() }
                            .setCancelable(false)
                            .show()
                    } else {
                        val msg = body?.message ?: "Server merespon negatif (Success: false)"
                        AlertDialog.Builder(this@PindahActivity)
                            .setTitle("Gagal")
                            .setMessage(msg)
                            .setPositiveButton("Tutup", null)
                            .show()
                    }
                } else {
                    val errorDetail = response.errorBody()?.string() ?: "Unknown error"
                    android.util.Log.e("API_DEBUG", "Error Body: $errorDetail")
                    AlertDialog.Builder(this@PindahActivity)
                        .setTitle("Server Error (${response.code()})")
                        .setMessage("Detail: $errorDetail\n\nCek Logcat 'API_DEBUG' untuk detail.")
                        .setPositiveButton("Tutup", null)
                        .show()
                }
            }

            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                btnSubmit.isEnabled = true
                btnSubmit.text = if (editId > 0) "SIMPAN PERUBAHAN" else "KIRIM PENGAJUAN"
                
                FileUtils.clearCacheFiles(this@PindahActivity)
                
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

                AlertDialog.Builder(this@PindahActivity)
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

package com.ta.sindesa

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * =========================================================================================
 * SessionManager.kt — Pengelola Penyimpanan Sesi Terenkripsi di Perangkat Android
 * =========================================================================================
 * 
 * FUNGSI UTAMA:
 * 1. Menyimpan status login, token otentikasi Bearer, dan biodata pengguna di penyimpanan lokal HP.
 * 2. Menerapkan enkripsi tingkat militer (AES-256-GCM) dengan MasterKey hardware Android Keystore.
 * 3. Menjamin data pribadi warga (NIK, No KK, Alamat, Token) TIDAK DAPAT DIBACA sebagai teks biasa,
 *    bahkan jika perangkat Android mengalami physical tampering atau di-root.
 * 4. Memenuhi standar OWASP MASVS-STORAGE (M2 - Insecure Data Storage).
 * =========================================================================================
 */
class SessionManager(context: Context) {

    // =====================================================================================
    // 1. MEMBUAT KUNCI MASTER TERENKRIPSI DARI ANDROID KEYSTORE
    // =====================================================================================
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    // =====================================================================================
    // 2. INISIALISASI ENCRYPTED SHAREDPREFERENCES
    // =====================================================================================
    // Nama file preferensi: "SINDESA_SECURE_SESSION"
    // Kunci Preferensi dienkripsi dengan AES-256-SIV
    // Nilai Preferensi dienkripsi dengan AES-256-GCM
    private val sharedPref: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "SINDESA_SECURE_SESSION",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // =====================================================================================
    // 3. MENYIMPAN DATA SESI & BIODATA LENGKAP WARGA
    // =====================================================================================
    fun setLoggedIn(
        isLoggedIn: Boolean, 
        userId: Int? = null,
        nama: String? = null, 
        nik: String? = null, 
        email: String? = null, 
        token: String? = null,
        noKk: String? = null,
        agama: String? = null,
        jenisKelamin: String? = null,
        tempatLahir: String? = null,
        tanggalLahir: String? = null,
        statusPerkawinan: String? = null,
        pekerjaan: String? = null,
        kewarganegaraan: String? = null,
        alamatLengkap: String? = null,
        rtRw: String? = null,
        provinsi: String? = null,
        kota: String? = null,
        kecamatan: String? = null,
        kelurahanDesa: String? = null,
        provinsiCode: String? = null,
        kotaCode: String? = null,
        kecamatanCode: String? = null,
        kelurahanDesaCode: String? = null,
        noHp: String? = null,
        fotoProfil: String? = null,
        status: String? = null
    ) {
        sharedPref.edit().apply {
            putBoolean("is_logged_in", isLoggedIn)
            if (userId != null && userId > 0) putInt("user_id", userId)
            putString("nama_user", nama)
            putString("nik_user", nik)
            putString("email_user", email)
            putString("auth_token", token)
            putString("no_kk", noKk)
            putString("agama", agama)
            putString("jenis_kelamin", jenisKelamin)
            putString("tempat_lahir", tempatLahir)
            putString("tanggal_lahir", tanggalLahir)
            putString("status_perkawinan", statusPerkawinan)
            putString("pekerjaan", pekerjaan)
            putString("kewarganegaraan", kewarganegaraan)
            putString("alamat_lengkap", alamatLengkap)
            putString("rt_rw", rtRw)
            putString("provinsi", provinsi)
            putString("kota", kota)
            putString("kecamatan", kecamatan)
            putString("kelurahan_desa", kelurahanDesa)
            putString("provinsi_code", provinsiCode)
            putString("kota_code", kotaCode)
            putString("kecamatan_code", kecamatanCode)
            putString("kelurahan_desa_code", kelurahanDesaCode)
            putString("no_hp", noHp)
            putString("foto_profil", fotoProfil)
            putString("status", status)
            apply()
        }
    }

    // =====================================================================================
    // 4. GETTER UNTUK MEMBACA DATA SESI DARI ENCRYPTED STORAGE
    // =====================================================================================
    /** Memeriksa apakah user sedang dalam status login aktif */
    fun isLoggedIn(): Boolean = sharedPref.getBoolean("is_logged_in", false)

    /** Mengambil User ID */
    fun getUserId(): Int = sharedPref.getInt("user_id", 0)

    /** Mengambil Nama Lengkap Warga */
    fun getNamaUser(): String? = sharedPref.getString("nama_user", "Warga")

    /** Mengambil NIK (16 Digit) */
    fun getNikUser(): String? = sharedPref.getString("nik_user", null)

    /** Mengambil Alamat Email */
    fun getEmailUser(): String? = sharedPref.getString("email_user", null)

    /** Mengambil Token Otentikasi Bearer API */
    fun getToken(): String? = sharedPref.getString("auth_token", null)

    /** Mengambil URL / Nama file Foto Profil */
    fun getFotoProfil(): String? = sharedPref.getString("foto_profil", null)

    /** Mengambil Status Akun ("active" / "inactive" / "menunggu_verifikasi") */
    fun getStatus(): String? = sharedPref.getString("status", "inactive")
    
    // Biodata Pendukung
    fun getNoKk(): String? = sharedPref.getString("no_kk", null)
    fun getAgama(): String? = sharedPref.getString("agama", null)
    fun getJenisKelamin(): String? = sharedPref.getString("jenis_kelamin", null)
    fun getTempatLahir(): String? = sharedPref.getString("tempat_lahir", null)
    fun getTanggalLahir(): String? = sharedPref.getString("tanggal_lahir", null)
    fun getStatusPerkawinan(): String? = sharedPref.getString("status_perkawinan", null)
    fun getPekerjaan(): String? = sharedPref.getString("pekerjaan", null)
    fun getKewarganegaraan(): String? = sharedPref.getString("kewarganegaraan", null)
    fun getAlamatLengkap(): String? = sharedPref.getString("alamat_lengkap", null)
    fun getRtRw(): String? = sharedPref.getString("rt_rw", null)
    fun getProvinsi(): String? = sharedPref.getString("provinsi", null)
    fun getKota(): String? = sharedPref.getString("kota", null)
    fun getKecamatan(): String? = sharedPref.getString("kecamatan", null)
    fun getKelurahanDesa(): String? = sharedPref.getString("kelurahan_desa", null)
    fun getProvinsiCode(): String? = sharedPref.getString("provinsi_code", null)
    fun getKotaCode(): String? = sharedPref.getString("kota_code", null)
    fun getKecamatanCode(): String? = sharedPref.getString("kecamatan_code", null)
    fun getKelurahanDesaCode(): String? = sharedPref.getString("kelurahan_desa_code", null)
    fun getNoHp(): String? = sharedPref.getString("no_hp", null)

    // =====================================================================================
    // 5. MENGHAPUS SESI SAAT LOGOUT ATAU SESI EXPIRED (logout)
    // =====================================================================================
    fun logout() {
        sharedPref.edit().clear().apply()
    }
}
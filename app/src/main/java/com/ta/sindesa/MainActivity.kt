package com.ta.sindesa

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ta.sindesa.api.RetrofitClient
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

/**
 * =========================================================================================
 * MainActivity.kt — Halaman Formulir Masuk (Login) Aplikasi SINDESA
 * =========================================================================================
 * 
 * FUNGSI UTAMA:
 * 1. Menerima kredensial pengguna (NIK 16-Digit atau Email dan Kata Sandi).
 * 2. Validasi format masukan sebelum dikirim ke jaringan (Client-Side Sanitization).
 * 3. Mengirimkan request otentikasi ke endpoint API login_warga.php via Retrofit.
 * 4. Menyimpan sesi hasil login terenkripsi (AES-256) ke SessionManager.
 * 5. Auto-Login: Jika sesi valid masih tersimpan, langsung diarahkan ke DashboardActivity.
 * 6. Keamanan: FLAG_SECURE (Anti-Screenshot) & Deteksi Root/Emulator.
 * =========================================================================================
 */
class MainActivity : AppCompatActivity() {

    // Pengelola penyimpanan lokal terenkripsi
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ---------------------------------------------------------------------------------
        // KEAMANAN 1: FLAG_SECURE (Mencegah Screenshot Kata Sandi & NIK)
        // ---------------------------------------------------------------------------------
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        // ---------------------------------------------------------------------------------
        // KEAMANAN 2: DETEKSI ROOT & EMULATOR
        // ---------------------------------------------------------------------------------
        if (com.ta.sindesa.utils.SecurityUtil.isDeviceRooted() || com.ta.sindesa.utils.SecurityUtil.isRunningOnEmulator()) {
            Toast.makeText(this, "Aplikasi tidak dapat berjalan di lingkungan tidak aman", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        sessionManager = SessionManager(this)

        // ---------------------------------------------------------------------------------
        // KEAMANAN 3: AUTO-LOGIN (PENGECEKAN SESI AKTIF)
        // ---------------------------------------------------------------------------------
        // Jika warga sebelumnya sudah berhasil login dan sesinya masih aktif,
        // lewati halaman login dan langsung buka Dashboard.
        if (sessionManager.isLoggedIn()) {
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // =================================================================================
        // 2. BINDING KOMPONEN VIEW DARI XML
        // =================================================================================
        val etEmailNik = findViewById<TextInputEditText>(R.id.etEmailNik)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<MaterialButton>(R.id.btnLogin)
        val tvRegister = findViewById<TextView>(R.id.tvRegister)
        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)

        // =================================================================================
        // 3. LOGIKA VALIDASI & PROSES LOGIN (btnLogin.setOnClickListener)
        // =================================================================================
        btnLogin.setOnClickListener {
            val emailNik = etEmailNik.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // VALIDASI 1: Cek Input Kosong
            if (emailNik.isEmpty()) {
                etEmailNik.error = "NIK / Email tidak boleh kosong"
                etEmailNik.requestFocus()
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                etPassword.error = "Kata Sandi tidak boleh kosong"
                etPassword.requestFocus()
                return@setOnClickListener
            }

            // VALIDASI 2: Cek Format NIK (16 Digit Angka) jika bukan format Email
            if (!Patterns.EMAIL_ADDRESS.matcher(emailNik).matches()) {
                if (emailNik.length != 16 || !emailNik.all { it.isDigit() }) {
                    etEmailNik.error = "Jika menggunakan NIK, harus 16 digit angka"
                    etEmailNik.requestFocus()
                    return@setOnClickListener
                }
            }

            // PROSES 3: Mengirim Permintaan Login ke Server API
            btnLogin.isEnabled = false
            btnLogin.text = "Memproses..."

            val call = RetrofitClient.getInstance(this).loginWarga(emailNik, password)
            call.enqueue(object : retrofit2.Callback<com.ta.sindesa.models.LoginResponse> {
                override fun onResponse(
                    call: retrofit2.Call<com.ta.sindesa.models.LoginResponse>,
                    response: retrofit2.Response<com.ta.sindesa.models.LoginResponse>
                ) {
                    btnLogin.isEnabled = true
                    btnLogin.text = "MASUK SEKARANG"

                    if (response.isSuccessful) {
                        val loginResponse = response.body()
                        if (loginResponse?.success == true) {
                            val user = loginResponse.data?.user
                            val token = loginResponse.data?.token
                            
                            // SIMPAN SESI AMAN (ENCRYPTED) KE SESSION MANAGER
                            sessionManager.setLoggedIn(
                                isLoggedIn = true, 
                                userId = user?.id,
                                nama = user?.nama, 
                                nik = user?.nik, 
                                email = user?.email, 
                                token = token,
                                noKk = user?.noKk,
                                agama = user?.agama,
                                jenisKelamin = user?.jenisKelamin,
                                tempatLahir = user?.tempatLahir,
                                tanggalLahir = user?.tanggalLahir,
                                statusPerkawinan = user?.statusPerkawinan,
                                pekerjaan = user?.pekerjaan,
                                kewarganegaraan = user?.kewarganegaraan,
                                alamatLengkap = user?.alamatLengkap,
                                rtRw = user?.rtRw,
                                provinsi = user?.provinsi,
                                kota = user?.kota,
                                kecamatan = user?.kecamatan,
                                kelurahanDesa = user?.kelurahanDesa,
                                noHp = user?.noHp,
                                fotoProfil = user?.fotoProfil,
                                status = user?.status
                            )

                            Toast.makeText(this@MainActivity, "Login Berhasil! Selamat datang.", Toast.LENGTH_SHORT).show()

                            // Navigasi ke Dashboard dan bersihkan task tumpukan login
                            val intent = Intent(this@MainActivity, DashboardActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this@MainActivity, loginResponse?.message ?: "Login Gagal: Periksa kembali NIK/Password Anda", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Server Error: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: retrofit2.Call<com.ta.sindesa.models.LoginResponse>, t: Throwable) {
                    btnLogin.isEnabled = true
                    btnLogin.text = "MASUK SEKARANG"
                    
                    val host = RetrofitClient.HOSTNAME
                    val errorMessage = when (t) {
                        is com.google.gson.JsonSyntaxException -> "Respon server tidak valid."
                        is java.net.SocketTimeoutException -> "Koneksi Waktu Habis (Timeout). Periksa koneksi internet."
                        is java.net.ConnectException -> "Gagal terhubung ke host ($host)."
                        else -> "Kesalahan Jaringan: ${t.localizedMessage}"
                    }

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Gagal Masuk")
                        .setMessage(errorMessage)
                        .setPositiveButton("Tutup", null)
                        .show()
                    
                    android.util.Log.e("API_DEBUG", "Login Failure", t)
                }
            })
        }

        // =================================================================================
        // 4. ROUTING KE PENDAFTARAN AKUN (REGISTER) & LUPA KATA SANDI
        // =================================================================================
        tvRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        tvForgotPassword.setOnClickListener {
            Toast.makeText(this, "Silakan hubungi Operator Kantor Desa untuk reset password.", Toast.LENGTH_LONG).show()
        }
    }
}

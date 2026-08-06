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

class MainActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // SECURE BY DESIGN: Mencegah screenshot dan screen recording (M1 - Improper Platform Usage)
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
        // SECURE BY DESIGN: 1. Auto-Login (Session Check)
        // ==========================================
        if (sessionManager.isLoggedIn()) {
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // ==========================================
        // BINDING VIEW
        // ==========================================
        val etEmailNik = findViewById<TextInputEditText>(R.id.etEmailNik)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<MaterialButton>(R.id.btnLogin)
        val tvRegister = findViewById<TextView>(R.id.tvRegister)
        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)

        // ==========================================
        // LOGIKA TOMBOL LOGIN
        // ==========================================
        btnLogin.setOnClickListener {
            val emailNik = etEmailNik.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // 1. Validasi Input Kosong
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

            // 2. Validasi Format Sederhana
            if (!Patterns.EMAIL_ADDRESS.matcher(emailNik).matches()) {
                if (emailNik.length != 16 || !emailNik.all { it.isDigit() }) {
                    etEmailNik.error = "Jika menggunakan NIK, harus 16 digit angka"
                    etEmailNik.requestFocus()
                    return@setOnClickListener
                }
            }

            // 3. Proses Login ke Server via Retrofit (Lokal Laragon)
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
                            
                            // SECURE BY DESIGN: Simpan Sesi (Encrypted) termasuk Token
                            sessionManager.setLoggedIn(
                                isLoggedIn = true, 
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

                            Toast.makeText(this@MainActivity, "Login Berhasil", Toast.LENGTH_SHORT).show()

                            val intent = Intent(this@MainActivity, DashboardActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this@MainActivity, loginResponse?.message ?: "Login Gagal", Toast.LENGTH_SHORT).show()
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

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Gagal Terhubung ke Server")
                        .setMessage(errorMessage)
                        .setPositiveButton("Tutup", null)
                        .show()
                    
                    android.util.Log.e("API_DEBUG", "Login Failure", t)
                }
            })
        }

        // ==========================================
        // ROUTING MENU LAIN
        // ==========================================
        tvRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        tvForgotPassword.setOnClickListener {
            Toast.makeText(this, "Fitur Lupa Password", Toast.LENGTH_SHORT).show()
        }
    }
}



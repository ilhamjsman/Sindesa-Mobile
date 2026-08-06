package com.ta.sindesa

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.button.MaterialButton

import androidx.appcompat.app.AlertDialog
import com.ta.sindesa.utils.SecurityUtil

class WelcomeActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // Paksa aplikasi ke mode terang (Light Mode)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)

        // SECURE BY DESIGN: Deteksi Root & Emulator (OWASP M8 & M9)
        if (SecurityUtil.isDeviceRooted() || SecurityUtil.isRunningOnEmulator()) {
            showSecurityWarning()
            return
        }

        sessionManager = SessionManager(this)

        // ==========================================
        // SECURE BY DESIGN: 1. Auto-Login (Pengecekan Sesi)
        // ==========================================
        // Mengecek apakah warga sudah masuk/login sebelumnya
        if (sessionManager.isLoggedIn()) {
            // Jika sudah login, Bypas halaman Welcome & Login, langsung ke Dashboard
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
            finish() // Tutup WelcomeActivity agar tidak bisa di-back
            return // Hentikan eksekusi kode di bawah
        }

        // Jika belum login, tampilkan layar Welcome
        setContentView(R.layout.activity_welcome)

        // ==========================================
        // BINDING VIEW & NAVIGASI TOMBOL
        // ==========================================
        val btnNavLogin = findViewById<MaterialButton>(R.id.btnNavLogin)
        val btnNavRegister = findViewById<MaterialButton>(R.id.btnNavRegister)

        // Arahkan ke Halaman Login (MainActivity)
        btnNavLogin.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        // Arahkan ke Halaman Pendaftaran Akun (RegisterActivity)
        btnNavRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    private fun showSecurityWarning() {
        AlertDialog.Builder(this)
            .setTitle("Keamanan Terdeteksi")
            .setMessage("Maaf, aplikasi tidak dapat dijalankan pada perangkat yang di-root atau emulator demi keamanan data Anda.")
            .setCancelable(false)
            .setPositiveButton("Keluar") { _, _ ->
                finishAffinity()
            }
            .show()
    }
}
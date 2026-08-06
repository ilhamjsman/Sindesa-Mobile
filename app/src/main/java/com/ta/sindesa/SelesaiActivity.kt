package com.ta.sindesa

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.button.MaterialButton

class SelesaiActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var sessionManager: SessionManager

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
        if (!sessionManager.isLoggedIn()) {
            val intent = Intent(this, WelcomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_selesai)

        // ==========================================
        // INISIALISASI UI & SIDEBAR
        // ==========================================
        drawerLayout = findViewById(R.id.drawerLayout)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)

        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Initialize Sidebar using Utility
        SidebarUtil.initSidebar(this, drawerLayout)

        // ==========================================
        // NAVIGASI TOMBOL BACK
        // ==========================================
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    // Kembali ke halaman sebelumnya (Dashboard) secara normal
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // ==========================================
        // LOGIKA TOMBOL PADA KARTU (Mockup)
        // ==========================================
        /*
        Catatan: Karena di XML kamu tidak memberikan ID unik pada tombol
        (semuanya hanya tag <MaterialButton>), saya menggunakan contoh
        bagaimana jika kamu memberikan ID nanti.
        Contoh: android:id="@+id/btnCetak1" dan android:id="@+id/btnUnduh1"
        */


        val btnCetak1 = findViewById<MaterialButton>(R.id.btnCetak1)
        val btnUnduh1 = findViewById<MaterialButton>(R.id.btnUnduh1)

        btnCetak1?.setOnClickListener {
            Toast.makeText(this, "Menyiapkan file untuk dicetak...", Toast.LENGTH_SHORT).show()
            // Logika untuk mengirim ke printer atau memunculkan dialog print PDF
        }

        btnUnduh1?.setOnClickListener {
            Toast.makeText(this, "Mengunduh Pengantar KTP.pdf", Toast.LENGTH_SHORT).show()
            // Logika download file dari server menggunakan DownloadManager Android
        }

    }
}
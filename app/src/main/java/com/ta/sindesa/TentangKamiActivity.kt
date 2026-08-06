package com.ta.sindesa

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

// Ubah nama class menyesuaikan nama file (TentangKamiActivity.kt)
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat

class TentangKamiActivity : AppCompatActivity() {

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
            val toast = android.widget.Toast.makeText(this, "Aplikasi tidak dapat berjalan di lingkungan tidak aman", android.widget.Toast.LENGTH_LONG)
            toast.show()
            finish()
            return
        }

        sessionManager = SessionManager(this)
        if (!sessionManager.isLoggedIn()) {
            val intent = android.content.Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_tentang_kami)

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)

        // Initialize Sidebar using Utility
        SidebarUtil.initSidebar(this, drawerLayout)

        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }
}
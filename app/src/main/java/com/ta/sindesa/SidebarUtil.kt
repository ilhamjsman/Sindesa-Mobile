package com.ta.sindesa

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.button.MaterialButton

object SidebarUtil {

    fun refreshSidebarProfile(activity: Activity) {
        val sessionManager = SessionManager(activity)
        val namaWarga = sessionManager.getNamaUser() ?: "Warga Buttu Sawe"
        val nikWarga = sessionManager.getNikUser() ?: "7304XXXXXXXXXXXX"

        activity.findViewById<TextView>(R.id.tvNavNamaUser)?.text = namaWarga
        activity.findViewById<TextView>(R.id.tvNavNik)?.text = "NIK: $nikWarga"

        val navImgProfile = activity.findViewById<android.widget.ImageView>(R.id.navImgProfile)
        val fotoPath = sessionManager.getFotoProfil()
        val imageUrl = com.ta.sindesa.api.RetrofitClient.getProfileImageUrl(fotoPath)
        if (navImgProfile != null && !imageUrl.isNullOrEmpty()) {
            Thread {
                try {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    val request = okhttp3.Request.Builder().url(imageUrl).build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val bytes = response.body?.bytes()
                        if (bytes != null && bytes.isNotEmpty()) {
                            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bitmap != null) {
                                activity.runOnUiThread { navImgProfile.setImageBitmap(bitmap) }
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SIDEBAR", "Load image failed for $imageUrl: ${e.message}")
                }
            }.start()
        }
    }

    fun initSidebar(activity: Activity, drawerLayout: DrawerLayout) {
        val sessionManager = SessionManager(activity)
        refreshSidebarProfile(activity)

        // Profile Header Click
        activity.findViewById<View>(R.id.navHeaderProfile)?.setOnClickListener {
            navigateTo(activity, ProfileActivity::class.java)
        }

        // Main Menu Links
        activity.findViewById<View>(R.id.navBeranda)?.setOnClickListener {
            if (activity !is DashboardActivity) {
                navigateTo(activity, DashboardActivity::class.java, true)
            } else {
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        }

        activity.findViewById<View>(R.id.navRiwayat)?.setOnClickListener {
            navigateTo(activity, RiwayatActivity::class.java)
        }

        // Collapsible Logic
        setupCollapsible(activity, R.id.navKependudukan, R.id.layoutKependudukan)
        setupCollapsible(activity, R.id.navUmum, R.id.layoutUmum)
        setupCollapsible(activity, R.id.navPerizinan, R.id.layoutPerizinan)
        setupCollapsible(activity, R.id.navSosial, R.id.layoutSosial)

        // Service Items Routing
        setupServiceItem(activity, R.id.navKtp, KtpActivity::class.java)
        setupServiceItem(activity, R.id.navKk, KkActivity::class.java)
        setupServiceItem(activity, R.id.navAkta, AktaLahirActivity::class.java)
        setupServiceItem(activity, R.id.navPindah, PindahActivity::class.java)
        setupServiceItem(activity, R.id.navKematian, KematianActivity::class.java)

        setupServiceItem(activity, R.id.navDomisili, DomisiliActivity::class.java)
        setupServiceItem(activity, R.id.navBedaNama, BedaNamaActivity::class.java)
        setupServiceItem(activity, R.id.navBelumMenikah, BelumMenikahActivity::class.java)
        setupServiceItem(activity, R.id.navJandaDuda, JandaDudaActivity::class.java)

        setupServiceItem(activity, R.id.navSkck, SkckActivity::class.java)
        setupServiceItem(activity, R.id.navIzinKeramaian, IzinKeramaianActivity::class.java)
        setupServiceItem(activity, R.id.navKehilangan, KehilanganActivity::class.java)

        setupServiceItem(activity, R.id.navSktm, SktmActivity::class.java)
        setupServiceItem(activity, R.id.navUsaha, UsahaActivity::class.java)
        setupServiceItem(activity, R.id.navPenghasilan, PenghasilanActivity::class.java)

        // Logout Button
        activity.findViewById<MaterialButton>(R.id.btnLogout)?.setOnClickListener {
            logout(activity, sessionManager)
        }
    }

    private fun setupCollapsible(activity: Activity, triggerId: Int, layoutId: Int) {
        val trigger = activity.findViewById<TextView>(triggerId)
        val layout = activity.findViewById<LinearLayout>(layoutId)
        trigger?.setOnClickListener {
            layout?.visibility = if (layout?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }

    private fun setupServiceItem(activity: Activity, viewId: Int, targetClass: Class<*>) {
        activity.findViewById<View>(viewId)?.setOnClickListener {
            navigateTo(activity, targetClass)
        }
    }

    private fun navigateTo(activity: Activity, targetClass: Class<*>, clearStack: Boolean = false) {
        if (activity.javaClass == targetClass) {
            val drawerLayout = activity.findViewById<DrawerLayout>(R.id.drawerLayout)
            drawerLayout?.closeDrawer(GravityCompat.START)
            return
        }
        val intent = Intent(activity, targetClass)
        if (clearStack) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        activity.startActivity(intent)
    }

    private fun logout(activity: Activity, sessionManager: SessionManager) {
        sessionManager.logout()
        Toast.makeText(activity, "Berhasil Keluar", Toast.LENGTH_SHORT).show()
        
        val intent = Intent(activity, WelcomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        activity.startActivity(intent)
        activity.finish()
    }
}

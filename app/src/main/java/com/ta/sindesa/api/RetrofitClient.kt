package com.ta.sindesa.api

import android.content.Context
import com.ta.sindesa.SessionManager
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * =========================================================================================
 * RetrofitClient.kt — Konfigurasi Utama Jaringan HTTP & Gateway Komunikasi API
 * =========================================================================================
 * 
 * FUNGSI UTAMA:
 * 1. Menghubungkan aplikasi Android SINDESA ke server REST API online resmi (api.sindesa-buttusawe.com).
 * 2. Mengelola interceptor otentikasi otomatis (menyisipkan header "Authorization: Bearer <token>").
 * 3. Menyediakan converter JSON GSON untuk memetakan respon API ke model data Kotlin.
 * 4. Menerapkan proteksi keamanan:
 *    - Logging Interceptor: Dimatikan pada mode Release (Anti-Data Leaks ke Logcat / OWASP M10).
 *    - Empty/Non-JSON Response Sanitizer: Mencegah crash jika server mengirimkan error PHP tak terduga.
 * =========================================================================================
 */
object RetrofitClient {

    // Domain resmi server hosting API
    const val HOSTNAME = "api.sindesa-buttusawe.com"
    
    // URL dasar (Base URL) API RESTful SINDESA
    const val BASE_URL = "https://api.sindesa-buttusawe.com/"

    /**
     * Mengonversi path file foto profil relatif dari database ke URL lengkap endpoint resmi API.
     * Mengarahkan akses file melalui foto_profil.php agar tidak terblokir proteksi cookie web session.
     */
    fun getProfileImageUrl(fotoPath: String?): String? {
        if (fotoPath.isNullOrEmpty()) return null
        if (fotoPath.startsWith("http://") || fotoPath.startsWith("https://")) {
            if (fotoPath.contains("/storage/profil/")) {
                val filename = fotoPath.substringAfterLast('/')
                return "${BASE_URL}foto_profil.php?file=${filename}"
            }
            return fotoPath
        }
        val filename = fotoPath.substringAfterLast('/')
        return "${BASE_URL}foto_profil.php?file=${filename}"
    }

    /**
     * Membuat instance singleton ApiService dengan konfigurasi OkHttpClient lengkap.
     */
    fun getInstance(context: Context): ApiService {
        val sessionManager = SessionManager(context)
        
        // ---------------------------------------------------------------------------------
        // 1. LOGGING INTERCEPTOR (KEAMANAN LOGCAT)
        // ---------------------------------------------------------------------------------
        // HANYA aktif saat mode DEBUG untuk kebutuhan pengembangan.
        // Pada mode rilis (Release APK), level diset NONE agar data sensitif tidak terekam di sistem log Android.
        val logging = HttpLoggingInterceptor().apply {
            level = if (com.ta.sindesa.BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }

        // ---------------------------------------------------------------------------------
        // 2. AUTHENTICATION INTERCEPTOR (BEARER TOKEN INJECTION)
        // ---------------------------------------------------------------------------------
        // Secara otomatis mengambil token aktif dari EncryptedSharedPreferences (SessionManager)
        // dan menyematkannya ke header setiap request API: "Authorization: Bearer <token>".
        val authInterceptor = Interceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
            requestBuilder.addHeader("Connection", "close")
            sessionManager.getToken()?.let {
                requestBuilder.addHeader("Authorization", "Bearer $it")
            }
            chain.proceed(requestBuilder.build())
        }

        // ---------------------------------------------------------------------------------
        // 3. ERROR & NON-JSON RESPONSE SANITIZER INTERCEPTOR
        // ---------------------------------------------------------------------------------
        // Menangkap output error HTML/teks mentah dari server dan membungkusnya ke format JSON standar
        // agar parser GSON di Android tidak mengalami Crash (JsonSyntaxException).
        val emptyResponseInterceptor = Interceptor { chain ->
            val response: Response = chain.proceed(chain.request())
            val body = response.body
            val source = body?.source()
            source?.request(Long.MAX_VALUE)
            val buffer = source?.buffer?.clone()
            val bodyString = buffer?.readUtf8() ?: ""

            val trimmed = bodyString.trim()
            if (trimmed.isEmpty()) {
                val fallbackJson = """{"success":false,"message":"Server mengembalikan respons kosong. Hubungi admin desa."}"""
                response.newBuilder()
                    .body(fallbackJson.toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            } else if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                val escapedError = trimmed.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
                val fallbackJson = """{"success":false,"message":"[Pemberitahuan Server]\n\n$escapedError"}"""
                response.newBuilder()
                    .body(fallbackJson.toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            } else {
                response
            }
        }

        // ---------------------------------------------------------------------------------
        // 4. MEMBANGUN OKHTTP CLIENT DENGAN BATAS WAKTU (TIMEOUT)
        // ---------------------------------------------------------------------------------
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(authInterceptor)
            .addInterceptor(emptyResponseInterceptor)
            .connectTimeout(120, TimeUnit.SECONDS) // Batas waktu koneksi (2 menit)
            .readTimeout(120, TimeUnit.SECONDS)    // Batas waktu membaca respon (2 menit)
            .writeTimeout(120, TimeUnit.SECONDS)   // Batas waktu unggah berkas KTP/KK (2 menit)
            .build()

        // ---------------------------------------------------------------------------------
        // 5. MEMBANGUN RETROFIT INSTANCE
        // ---------------------------------------------------------------------------------
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(com.google.gson.GsonBuilder().setLenient().create()))
            .client(client)
            .build()
            .create(ApiService::class.java)
    }
}
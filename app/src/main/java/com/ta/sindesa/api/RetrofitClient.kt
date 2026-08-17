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

object RetrofitClient {
    const val HOSTNAME = "api.sindesa-buttusawe.com"
    // URL API Server Hosting Online Resmi Sindesa
    const val BASE_URL = "https://api.sindesa-buttusawe.com/"

    const val WEB_URL = "https://sindesa-buttusawe.com/"

    fun getProfileImageUrl(fotoPath: String?): String? {
        if (fotoPath.isNullOrEmpty()) return null
        if (fotoPath.startsWith("http://") || fotoPath.startsWith("https://")) {
            return fotoPath
        }
        var cleanPath = fotoPath.removePrefix("/")
        if (cleanPath.startsWith("storage/app/public/")) {
            cleanPath = cleanPath.removePrefix("storage/app/public/")
        }
        if (cleanPath.startsWith("public/storage/")) {
            cleanPath = cleanPath.removePrefix("public/storage/")
        }
        return if (cleanPath.startsWith("storage/")) {
            WEB_URL + cleanPath
        } else {
            WEB_URL + "storage/" + cleanPath
        }
    }

    fun getInstance(context: Context): ApiService {
        val sessionManager = SessionManager(context)
        
        val logging = HttpLoggingInterceptor().apply {
            // OWASP M10 (Extraneous Functionality): Matikan log di mode Release agar data sensitif tidak bocor ke Logcat
            level = if (com.ta.sindesa.BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }

        val authInterceptor = Interceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
            requestBuilder.addHeader("Connection", "close")
            sessionManager.getToken()?.let {
                requestBuilder.addHeader("Authorization", "Bearer $it")
            }
            chain.proceed(requestBuilder.build())
        }

        // Interceptor untuk menangani respons kosong atau error PHP non-JSON
        // Ini mencegah crash GSON dan menampilkan detail error PHP langsung ke layar HP user
        val emptyResponseInterceptor = Interceptor { chain ->
            val response: Response = chain.proceed(chain.request())
            val body = response.body
            val contentLength = body?.contentLength() ?: 0L
            val source = body?.source()
            source?.request(Long.MAX_VALUE)
            val buffer = source?.buffer?.clone()
            val bodyString = buffer?.readUtf8() ?: ""

            val trimmed = bodyString.trim()
            if (trimmed.isEmpty()) {
                val fallbackJson = """{"success":false,"message":"Server mengembalikan respons kosong. Hubungi admin atau periksa server."}"""
                response.newBuilder()
                    .body(fallbackJson.toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            } else if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                // PHP error / HTML output terdeteksi. Bungkus ke JSON agar bisa dibaca langsung oleh HP
                val escapedError = trimmed.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
                val fallbackJson = """{"success":false,"message":"[Error Server / PHP]\n\n$escapedError"}"""
                response.newBuilder()
                    .body(fallbackJson.toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            } else {
                response
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(authInterceptor)
            .addInterceptor(emptyResponseInterceptor)
            .connectTimeout(120, TimeUnit.SECONDS) // Waktu tunggu 2 menit
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(com.google.gson.GsonBuilder().setLenient().create()))
            .client(client)
            .build()
            .create(ApiService::class.java)
    }
}
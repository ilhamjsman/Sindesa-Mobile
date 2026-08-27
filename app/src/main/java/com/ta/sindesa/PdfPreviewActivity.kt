package com.ta.sindesa

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * =========================================================================================
 * PdfPreviewActivity.kt — Pratinjau (Preview) & Unduh Dokumen PDF Surat Resmi
 * =========================================================================================
 * 
 * FUNGSI UTAMA:
 * 1. Menampilkan pratinjau dokumen surat PDF resmi di dalam aplikasi sebelum diunduh.
 * 2. Mengunduh data PDF secara aman dari API backend menggunakan OkHttp dengan Bearer Token.
 * 3. Mengubah aliran biner PDF menjadi format Base64 dan merendernya via pustaka PDF.js di WebView.
 * 4. Menyediakan tombol "Unduh PDF" untuk menyimpan file resmi ke folder Download perangkat HP.
 * 5. Menerapkan proteksi keamanan:
 *    - Whitelist Hostname: Mencegah serangan SSRF (Server-Side Request Forgery).
 *    - Pengerasan WebView: Mematikan allowFileAccess & allowContentAccess untuk mencegah pencurian data lokal.
 * =========================================================================================
 */
class PdfPreviewActivity : AppCompatActivity() {

    // =====================================================================================
    // 1. DEKLARASI WIDGET TAMPILAN
    // =====================================================================================
    private lateinit var webView: WebView
    private lateinit var layoutLoading: android.widget.LinearLayout
    private lateinit var layoutError: android.widget.LinearLayout
    private lateinit var tvErrorMessage: android.widget.TextView

    // Parameter URL PDF dan Judul Surat yang dikirim via Intent
    private var pdfUrl: String = ""
    private var suratTitle: String = "Surat"

    companion object {
        const val EXTRA_PDF_URL = "extra_pdf_url"
        const val EXTRA_SURAT_TITLE = "extra_surat_title"

        /**
         * Helper statis untuk membuat Intent navigasi ke PdfPreviewActivity dengan parameter lengkap.
         */
        fun createIntent(context: Context, pdfUrl: String, suratTitle: String): Intent {
            return Intent(context, PdfPreviewActivity::class.java).apply {
                putExtra(EXTRA_PDF_URL, pdfUrl)
                putExtra(EXTRA_SURAT_TITLE, suratTitle)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_preview)

        // ---------------------------------------------------------------------------------
        // Mengambil data URL dan Judul Surat dari Intent
        // ---------------------------------------------------------------------------------
        pdfUrl = intent.getStringExtra(EXTRA_PDF_URL) ?: ""
        suratTitle = intent.getStringExtra(EXTRA_SURAT_TITLE) ?: "Surat"

        if (pdfUrl.isEmpty()) {
            Toast.makeText(this, "URL PDF tidak valid", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // ---------------------------------------------------------------------------------
        // Binding Komponen View Layout
        // ---------------------------------------------------------------------------------
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarPreview)
        webView = findViewById(R.id.webViewPdf)
        layoutLoading = findViewById(R.id.layoutLoading)
        layoutError = findViewById(R.id.layoutError)
        tvErrorMessage = findViewById(R.id.tvErrorMessage)
        val btnDownload = findViewById<MaterialButton>(R.id.btnDownload)
        val btnRetry = findViewById<MaterialButton>(R.id.btnRetry)

        // Setup Judul Toolbar dan Navigasi Kembali
        toolbar.title = "Preview: $suratTitle"
        toolbar.setNavigationOnClickListener { finish() }

        // ---------------------------------------------------------------------------------
        // KEAMANAN WEBVIEW: SECURE BY DESIGN (Pencegahan Eksploitasi File Lokal)
        // ---------------------------------------------------------------------------------
        webView.settings.apply {
            javaScriptEnabled = true            // Diperlukan untuk merender PDF.js
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true         // Memungkinkan warga memperbesar (zoom in/out) surat
            displayZoomControls = false
            domStorageEnabled = true
            setSupportZoom(true)
            
            // KEAMANAN KRITIS: Nonaktifkan akses langsung ke filesystem internal Android
            allowFileAccess = false            // Mencegah file:// traversal di WebView
            allowContentAccess = false         // Mencegah akses Content Provider yang tidak sah
        }

        // Listener status pemuatan halaman pada WebView
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Sembunyikan indikator loading saat surat selesai digambar
                layoutLoading.visibility = android.view.View.GONE
                webView.visibility = android.view.View.VISIBLE
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    showError("Gagal memuat preview surat.\nPastikan koneksi internet terhubung stabil.")
                }
            }
        }

        webView.webChromeClient = WebChromeClient()

        // Memulai proses pengunduhan dan rendering PDF
        loadPdfPreview()

        // Tombol Unduh PDF
        btnDownload.setOnClickListener {
            downloadPdf()
        }

        // Tombol Coba Lagi jika terjadi kegagalan jaringan
        btnRetry.setOnClickListener {
            layoutError.visibility = android.view.View.GONE
            layoutLoading.visibility = android.view.View.VISIBLE
            loadPdfPreview()
        }
    }

    // =====================================================================================
    // 2. KEAMANAN DOMAIN WHITELIST: ANTI-SSRF (isAuthorizedHost)
    // =====================================================================================
    /**
     * Memastikan URL PDF HANYA berasal dari domain resmi SINDESA.
     * Mencegah eksploitasi di mana URL dialihkan ke server luar peretas.
     */
    private fun isAuthorizedHost(urlStr: String): Boolean {
        return try {
            val uri = java.net.URI(urlStr)
            val host = uri.host?.lowercase() ?: return false
            val allowedHosts = listOf(
                com.ta.sindesa.api.RetrofitClient.HOSTNAME.lowercase(),
                "api.sindesa-buttusawe.com",
                "sindesa-buttusawe.com",
                "www.sindesa-buttusawe.com",
                "sindesa.buttusawe.desa.id",
                "10.0.2.2",
                "127.0.0.1",
                "localhost"
            )
            allowedHosts.any { host == it || host.endsWith(".$it") }
        } catch (e: Exception) {
            false
        }
    }

    // =====================================================================================
    // 3. METODE MEMUAT & MERENDER PDF (loadPdfPreview)
    // =====================================================================================
    /**
     * Alur Eksekusi:
     * 1. Validasi domain URL.
     * 2. Ambil token otentikasi Bearer dari SessionManager.
     * 3. Kirim request HTTP GET di background thread via OkHttp.
     * 4. Periksa apakah respon berupa file PDF atau pesan error JSON.
     * 5. Encode biner PDF ke string Base64.
     * 6. Pasang string Base64 ke dalam template HTML PDF.js untuk dirender di WebView.
     */
    private fun loadPdfPreview() {
        layoutLoading.visibility = android.view.View.VISIBLE
        layoutError.visibility = android.view.View.GONE

        // Validasi Whitelist Hostname
        if (!isAuthorizedHost(pdfUrl)) {
            showError("Akses Ditolak: Domain URL ($pdfUrl) tidak terdaftar dalam whitelist resmi SINDESA.")
            return
        }

        val sessionManager = SessionManager(this)
        val token = sessionManager.getToken()

        // Eksekusi pengambilan file di Background Thread agar tidak membekukan UI utama
        Thread {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()

                val requestBuilder = Request.Builder().url(pdfUrl)
                if (!token.isNullOrEmpty()) {
                    // Menyertakan token otentikasi agar backend memvalidasi kepemilikan surat
                    requestBuilder.addHeader("Authorization", "Bearer $token")
                }
                val request = requestBuilder.build()

                val response = client.newCall(request).execute()

                // Cek status respon HTTP
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    runOnUiThread {
                        showError("Server Error (${response.code}):\n$errorBody")
                    }
                    return@Thread
                }

                val contentType = response.header("Content-Type") ?: ""
                
                // Jika server mengirim JSON error dan bukan file PDF
                if (contentType.contains("application/json")) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    runOnUiThread {
                        showError("Surat belum bisa dipreview:\n$errorBody")
                    }
                    return@Thread
                }

                val pdfBytes = response.body?.bytes()
                if (pdfBytes == null || pdfBytes.isEmpty()) {
                    runOnUiThread {
                        showError("Server mengembalikan file PDF kosong.")
                    }
                    return@Thread
                }

                // Konversi biner PDF ke Base64 String
                val base64Pdf = Base64.encodeToString(pdfBytes, Base64.DEFAULT)

                // Bangun template HTML PDF.js
                val html = buildPdfViewerHtml(base64Pdf)

                // Render HTML ke WebView di UI Thread
                runOnUiThread {
                    webView.loadDataWithBaseURL(
                        null,
                        html,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }

            } catch (e: java.net.SocketTimeoutException) {
                runOnUiThread {
                    showError("Koneksi Waktu Habis (Timeout).\nPeriksa koneksi internet Anda.")
                }
            } catch (e: java.net.ConnectException) {
                runOnUiThread {
                    showError("Koneksi Ditolak ke Server Hosting.")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showError("Gagal memuat surat:\n${e.localizedMessage}")
                }
                android.util.Log.e("PDF_PREVIEW", "Error loading PDF", e)
            }
        }.start()
    }

    // =====================================================================================
    // 4. TEMPLATE HTML DENGAN PDF.JS VIEWER (buildPdfViewerHtml)
    // =====================================================================================
    /**
     * Membangun dokumen HTML mandiri yang menggunakan PDF.js untuk menggambar lembar surat
     * ke elemen <canvas> secara pixel-perfect.
     */
    private fun buildPdfViewerHtml(base64Pdf: String): String {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0">
            <script src="https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.min.js"></script>
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body { 
                    background: #2d3748; 
                    display: flex; 
                    flex-direction: column; 
                    align-items: center;
                    padding: 8px;
                }
                canvas { 
                    display: block; 
                    margin: 8px auto; 
                    box-shadow: 0 4px 12px rgba(0,0,0,0.3);
                    max-width: 100%;
                    background: white;
                    border-radius: 4px;
                }
                .page-info {
                    text-align: center;
                    padding: 8px;
                    color: #cbd5e1;
                    font-family: sans-serif;
                    font-size: 13px;
                }
            </style>
        </head>
        <body>
            <div id="page-info" class="page-info"></div>
            <div id="pdf-container"></div>
            <script>
                // Decode data Base64 kembali ke Binary
                var pdfData = atob('${base64Pdf.replace("\n", "").replace("\r", "")}');
                
                var pdfjsLib = window['pdfjs-dist/build/pdf'];
                pdfjsLib.GlobalWorkerOptions.workerSrc = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js';
                
                var loadingTask = pdfjsLib.getDocument({data: pdfData});
                loadingTask.promise.then(function(pdf) {
                    var totalPages = pdf.numPages;
                    document.getElementById('page-info').innerText = 'Dokumen Resmi SINDESA • ' + totalPages + ' Halaman';
                    
                    var container = document.getElementById('pdf-container');
                    for (var i = 1; i <= totalPages; i++) {
                        renderPage(pdf, i, container);
                    }
                }).catch(function(error) {
                    document.getElementById('page-info').innerText = 'Gagal render: ' + error.message;
                });
                
                function renderPage(pdf, pageNum, container) {
                    pdf.getPage(pageNum).then(function(page) {
                        var scale = 2.0; // Skala rendering tajam HD
                        var viewport = page.getViewport({scale: scale});
                        
                        var canvas = document.createElement('canvas');
                        var context = canvas.getContext('2d');
                        canvas.height = viewport.height;
                        canvas.width = viewport.width;
                        canvas.style.width = '100%';
                        canvas.style.height = 'auto';
                        
                        container.appendChild(canvas);
                        
                        var renderContext = {
                            canvasContext: context,
                            viewport: viewport
                        };
                        page.render(renderContext);
                    });
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    private fun showError(message: String) {
        layoutLoading.visibility = android.view.View.GONE
        webView.visibility = android.view.View.GONE
        layoutError.visibility = android.view.View.VISIBLE
        tvErrorMessage.text = message
    }

    // =====================================================================================
    // 5. METODE MENGUNDUH SURAT KE STORAGE HP (downloadPdf)
    // =====================================================================================
    /**
     * Memanfaatkan DownloadManager resmi Android untuk mengunduh dan menyimpan surat PDF ke folder Downloads.
     */
    private fun downloadPdf() {
        try {
            val sessionManager = SessionManager(this)
            val token = sessionManager.getToken()

            val request = DownloadManager.Request(Uri.parse(pdfUrl))
                .setTitle("Surat Resmi SINDESA - $suratTitle")
                .setDescription("Mengunduh dokumen surat resmi desa...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "Sindesa_${suratTitle.replace(" ", "_")}.pdf"
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            if (!token.isNullOrEmpty()) {
                request.addRequestHeader("Authorization", "Bearer $token")
            }

            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            Toast.makeText(
                this,
                "📥 Mengunduh surat...\nCek folder Downloads pada file manager HP Anda.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            // Fallback jika DownloadManager gagal: Buka via browser eksternal
            Toast.makeText(this, "Membuka di browser untuk unduh...", Toast.LENGTH_SHORT).show()
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(pdfUrl))
            startActivity(intent)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}

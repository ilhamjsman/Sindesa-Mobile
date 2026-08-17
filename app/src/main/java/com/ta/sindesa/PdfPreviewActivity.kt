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
 * Activity untuk menampilkan preview surat PDF menggunakan WebView.
 *
 * PDF diunduh dari server lokal, di-encode ke base64, lalu ditampilkan
 * menggunakan HTML embed di WebView. Ini memungkinkan preview PDF 
 * tanpa memerlukan koneksi internet ke Google Docs Viewer.
 *
 * User bisa melihat suratnya terlebih dahulu sebelum memutuskan
 * untuk mendownload file PDF-nya.
 */
class PdfPreviewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var layoutLoading: android.widget.LinearLayout
    private lateinit var layoutError: android.widget.LinearLayout
    private lateinit var tvErrorMessage: android.widget.TextView

    private var pdfUrl: String = ""
    private var suratTitle: String = "Surat"

    companion object {
        const val EXTRA_PDF_URL = "extra_pdf_url"
        const val EXTRA_SURAT_TITLE = "extra_surat_title"

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

        // Get data from intent
        pdfUrl = intent.getStringExtra(EXTRA_PDF_URL) ?: ""
        suratTitle = intent.getStringExtra(EXTRA_SURAT_TITLE) ?: "Surat"

        if (pdfUrl.isEmpty()) {
            Toast.makeText(this, "URL PDF tidak valid", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Setup Views
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbarPreview)
        webView = findViewById(R.id.webViewPdf)
        layoutLoading = findViewById(R.id.layoutLoading)
        layoutError = findViewById(R.id.layoutError)
        tvErrorMessage = findViewById(R.id.tvErrorMessage)
        val btnDownload = findViewById<MaterialButton>(R.id.btnDownload)
        val btnRetry = findViewById<MaterialButton>(R.id.btnRetry)

        // Setup Toolbar
        toolbar.title = "Preview: $suratTitle"
        toolbar.setNavigationOnClickListener { finish() }

        // Setup WebView
        webView.settings.apply {
            javaScriptEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            domStorageEnabled = true
            setSupportZoom(true)
            allowFileAccess = false
            allowContentAccess = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
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
                    showError("Gagal memuat preview surat.\nPastikan HP dan laptop terhubung WiFi yang sama.")
                }
            }
        }

        webView.webChromeClient = WebChromeClient()

        // Load PDF
        loadPdfPreview()

        // Download Button
        btnDownload.setOnClickListener {
            downloadPdf()
        }

        // Retry Button
        btnRetry.setOnClickListener {
            layoutError.visibility = android.view.View.GONE
            layoutLoading.visibility = android.view.View.VISIBLE
            loadPdfPreview()
        }
    }

    /**
     * Mengunduh PDF dari server lokal dan menampilkannya di WebView.
     * 
     * Karena server berjalan di jaringan lokal (Laragon), kita tidak bisa 
     * menggunakan Google Docs Viewer. Sebagai gantinya, PDF diunduh via 
     * OkHttp, di-encode ke Base64, lalu ditampilkan menggunakan PDF.js 
     * (via CDN) di dalam WebView.
     */
    private fun isAuthorizedHost(urlStr: String): Boolean {
        return try {
            val uri = java.net.URI(urlStr)
            val host = uri.host?.lowercase() ?: return false
            val allowedHosts = listOf(
                com.ta.sindesa.api.RetrofitClient.HOSTNAME.lowercase(),
                "api.sindesa-buttusawe.com",
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

    private fun loadPdfPreview() {
        layoutLoading.visibility = android.view.View.VISIBLE
        layoutError.visibility = android.view.View.GONE
        // Validasi Hostname (Guideline §3: Pencegahan SSRF / Remote Asset Injection)
        val uri = try { Uri.parse(pdfUrl) } catch (_: Exception) { null }
        val host = uri?.host?.lowercase() ?: ""
        val isAllowedHost = host == "api.sindesa-buttusawe.com" ||
                host == "sindesa-buttusawe.com" ||
                host == "www.sindesa-buttusawe.com" ||
                host == "sindesa.buttusawe.desa.id" ||
                host == "localhost" ||
                host == "127.0.0.1" ||
                host.startsWith("192.168.") ||
                host.startsWith("10.")

        if (!isAllowedHost) {
            showError("Akses Ditolak: Domain PDF ($host) tidak terdaftar dalam whitelist resmi SINDESA.")
            return
        }

        val sessionManager = SessionManager(this)
        val token = sessionManager.getToken()

        if (!isAuthorizedHost(pdfUrl)) {
            showError("Host URL tidak diizinkan: $pdfUrl. Hanya domain resmi Sindesa yang diperbolehkan.")
            return
        }

        // Download PDF in background thread, then render in WebView
        Thread {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()

                val requestBuilder = Request.Builder().url(pdfUrl)
                if (!token.isNullOrEmpty()) {
                    requestBuilder.addHeader("Authorization", "Bearer $token")
                }
                val request = requestBuilder.build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    runOnUiThread {
                        showError("Server Error (${response.code}):\n$errorBody")
                    }
                    return@Thread
                }

                val contentType = response.header("Content-Type") ?: ""
                
                if (contentType.contains("application/json")) {
                    // Server returned an error JSON, not PDF
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

                val base64Pdf = Base64.encodeToString(pdfBytes, Base64.DEFAULT)

                // Build HTML with embedded PDF viewer using PDF.js
                val html = buildPdfViewerHtml(base64Pdf)

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
                    showError("Waktu habis (Timeout).\n\n" +
                            "1. Pastikan Laragon sudah berjalan.\n" +
                            "2. HP & Laptop harus di WiFi yang sama.\n" +
                            "3. Cek IP: ${com.ta.sindesa.api.RetrofitClient.HOSTNAME}")
                }
            } catch (e: java.net.ConnectException) {
                runOnUiThread {
                    showError("Koneksi ditolak.\nPastikan Laragon sudah berjalan di port 8080.")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showError("Gagal memuat surat:\n${e.localizedMessage}")
                }
                android.util.Log.e("PDF_PREVIEW", "Error loading PDF", e)
            }
        }.start()
    }

    /**
     * Membuat HTML yang menggunakan PDF.js dari CDN untuk render PDF.
     * PDF data di-embed sebagai base64 di dalam halaman HTML.
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
                    background: #f0f0f0; 
                    display: flex; 
                    flex-direction: column; 
                    align-items: center;
                    padding: 8px;
                }
                canvas { 
                    display: block; 
                    margin: 8px auto; 
                    box-shadow: 0 2px 8px rgba(0,0,0,0.2);
                    max-width: 100%;
                    background: white;
                }
                .page-info {
                    text-align: center;
                    padding: 8px;
                    color: #666;
                    font-family: sans-serif;
                    font-size: 14px;
                }
                .loading-text {
                    text-align: center;
                    padding: 40px;
                    color: #666;
                    font-family: sans-serif;
                    font-size: 16px;
                }
            </style>
        </head>
        <body>
            <div id="page-info" class="page-info"></div>
            <div id="pdf-container"></div>
            <script>
                var pdfData = atob('${base64Pdf.replace("\n", "").replace("\r", "")}');
                
                var pdfjsLib = window['pdfjs-dist/build/pdf'];
                pdfjsLib.GlobalWorkerOptions.workerSrc = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js';
                
                var loadingTask = pdfjsLib.getDocument({data: pdfData});
                loadingTask.promise.then(function(pdf) {
                    var totalPages = pdf.numPages;
                    document.getElementById('page-info').innerText = 'Total: ' + totalPages + ' halaman';
                    
                    var container = document.getElementById('pdf-container');
                    
                    for (var i = 1; i <= totalPages; i++) {
                        renderPage(pdf, i, container);
                    }
                }).catch(function(error) {
                    document.getElementById('page-info').innerText = 'Gagal render: ' + error.message;
                });
                
                function renderPage(pdf, pageNum, container) {
                    pdf.getPage(pageNum).then(function(page) {
                        var scale = 2.0;
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

    private fun downloadPdf() {
        try {
            val sessionManager = SessionManager(this)
            val token = sessionManager.getToken()

            val request = DownloadManager.Request(Uri.parse(pdfUrl))
                .setTitle("Surat - $suratTitle")
                .setDescription("Mengunduh surat...")
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
                "📥 Mengunduh surat...\nCek folder Downloads setelah selesai.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            // Fallback: open in browser
            Toast.makeText(this, "Membuka di browser untuk download...", Toast.LENGTH_SHORT).show()
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

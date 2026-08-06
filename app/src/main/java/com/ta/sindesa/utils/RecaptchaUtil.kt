package com.ta.sindesa.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog

object RecaptchaUtil {

    private const val TAG = "RecaptchaUtil"
    
    // Google reCAPTCHA v2 Site Key Publik
    const val DEFAULT_SITE_KEY = "6Lex9WItAAAAAHymtfN5IEmY95Z3alAW7lHz3x1t"

    interface RecaptchaCallback {
        fun onSuccess(token: String)
        fun onError(errorMessage: String)
    }

    /**
     * Menampilkan dialog modal Google reCAPTCHA v2 (Checkbox + Tantangan Objek Gambar)
     * dengan ukuran jendela besar (85% tinggi layar HP) agar bebas dari terpotong.
     * 
     * @param context Activity Context
     * @param siteKey Google reCAPTCHA v2 Site Key
     * @param callback Callback untuk menerima token setelah verifikasi sukses
     */
    fun fetchToken(
        context: Context,
        siteKey: String = DEFAULT_SITE_KEY,
        action: String = "register",
        callback: RecaptchaCallback
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        
        val webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        var dialog: AlertDialog? = null

        class JsBridge {
            @JavascriptInterface
            fun sendToken(token: String) {
                mainHandler.post {
                    Log.d(TAG, "reCAPTCHA v2 token received successfully")
                    dialog?.dismiss()
                    callback.onSuccess(token)
                }
            }

            @JavascriptInterface
            fun sendExpired() {
                mainHandler.post {
                    Log.e(TAG, "reCAPTCHA v2 expired")
                    dialog?.dismiss()
                    callback.onError("Sesi captcha berakhir. Silakan verifikasi ulang.")
                }
            }
        }

        webView.addJavascriptInterface(JsBridge(), "RecaptchaBridge")

        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <script src="https://www.google.com/recaptcha/api.js" async defer></script>
                <style>
                    body {
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        min-height: 100vh;
                        margin: 0;
                        background-color: #ffffff;
                    }
                </style>
            </head>
            <body>
                <div class="g-recaptcha" data-sitekey="$siteKey" data-callback="onCaptchaSuccess" data-expired-callback="onCaptchaExpired"></div>
                <script>
                    function onCaptchaSuccess(token) {
                        if (window.RecaptchaBridge) {
                            window.RecaptchaBridge.sendToken(token);
                        }
                    }
                    function onCaptchaExpired() {
                        if (window.RecaptchaBridge) {
                            window.RecaptchaBridge.sendExpired();
                        }
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Log.w(TAG, "WebView error: $description")
            }
        }

        webView.loadDataWithBaseURL("https://your-api-domain.com", htmlContent, "text/html", "UTF-8", null)

        dialog = AlertDialog.Builder(context)
            .setTitle("Verifikasi Keamanan Google")
            .setView(webView)
            .setNegativeButton("Batal") { d, _ ->
                d.dismiss()
                callback.onError("Pendaftaran dibatalkan")
            }
            .setCancelable(false)
            .create()

        dialog.show()

        // Set ukuran jendela pop-up dialog menjadi luas (95% Lebar, 85% Tinggi Layar HP)
        val window = dialog.window
        if (window != null) {
            val displayMetrics = context.resources.displayMetrics
            val width = (displayMetrics.widthPixels * 0.95).toInt()
            val height = (displayMetrics.heightPixels * 0.85).toInt()
            window.setLayout(width, height)
        }
    }
}

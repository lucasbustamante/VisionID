package com.example.laranjinhaqrwebview

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.laranjinhaqrwebview.databinding.ActivityWebViewBinding

class WebViewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWebViewBinding
    private var pendingWebPermissionRequest: PermissionRequest? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = pendingWebPermissionRequest
        pendingWebPermissionRequest = null
        if (granted && request != null) {
            grantOnlyVideoCapture(request)
        } else {
            request?.deny()
            Toast.makeText(this, "A câmera foi negada para a página.", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val url = intent.getStringExtra(EXTRA_URL)
        if (!isValidHttpsUrl(url)) {
            Toast.makeText(this, "URL inválida.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        with(binding.webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "$userAgentString VisionID/${BuildConfig.VERSION_NAME}"
        }

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val target = request.url
                return if (target.scheme.equals("https", ignoreCase = true)) {
                    false
                } else {
                    Log.w(TAG, "Navegação bloqueada: $target")
                    Toast.makeText(this@WebViewActivity, "Navegação não HTTPS bloqueada.", Toast.LENGTH_SHORT).show()
                    true
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    Log.e(TAG, "Erro WebView ${error.errorCode}: ${error.description}")
                    Toast.makeText(this@WebViewActivity, "Falha ao carregar a página.", Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                binding.progressBar.progress = newProgress
                binding.progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    // Autoriza somente câmera. Microfone, áudio e outros recursos permanecem negados.
                    if (!request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                        request.deny()
                        return@runOnUiThread
                    }

                    if (ContextCompat.checkSelfPermission(
                            this@WebViewActivity,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        grantOnlyVideoCapture(request)
                    } else {
                        pendingWebPermissionRequest?.deny()
                        pendingWebPermissionRequest = request
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest) {
                if (pendingWebPermissionRequest == request) pendingWebPermissionRequest = null
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d(
                    TAG,
                    "WEB ${consoleMessage.messageLevel()}: ${consoleMessage.message()} " +
                        "(${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})"
                )
                return true
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) binding.webView.goBack() else finish()
            }
        })

        binding.webView.loadUrl(url!!)
    }

    private fun grantOnlyVideoCapture(request: PermissionRequest) {
        runCatching {
            request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
        }.onFailure {
            Log.e(TAG, "Falha ao liberar câmera para a WebView", it)
            request.deny()
        }
    }

    private fun isValidHttpsUrl(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    }

    override fun onDestroy() {
        pendingWebPermissionRequest?.deny()
        pendingWebPermissionRequest = null
        binding.webView.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        private const val TAG = "WebViewActivity"
    }
}

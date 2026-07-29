package com.example.laranjinhaqrwebview

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
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
    private var pendingUrl: String? = null
    private var pageLoaded = false
    private var automaticPageActivationScheduled = false

    private val runtimePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        AppLog.info(this, "PERMISSION", "RUNTIME_PERMISSION_RESULT", "Resultado das permissões do Android", result)
        val request = pendingWebPermissionRequest

        if (request != null) {
            pendingWebPermissionRequest = null
            grantSupportedWebResources(request)
            return@registerForActivityResult
        }

        // Permissão preventiva antes de carregar a página.
        // Mesmo que áudio seja negado, a página ainda será carregada e a câmera será liberada.
        loadPendingPage()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val url = intent.getStringExtra(EXTRA_URL)
        if (!isValidWebUrl(url)) {
            AppLog.warning(this, "WEBVIEW", "INITIAL_URL_INVALID", "URL inicial inválida", mapOf("url" to AppLog.safeUrl(url)))
            Toast.makeText(this, "Apenas links HTTPS que contenham Itaú são permitidos.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        pendingUrl = url
        AppLog.info(this, "WEBVIEW", "WEBVIEW_CREATED", "WebView criado", AppLog.deviceSnapshot(this) + mapOf("url" to AppLog.safeUrl(url)))

        configureWebView()
        configureBackNavigation()

        // A câmera já costuma estar autorizada pelo leitor de QR Code. O áudio é solicitado
        // previamente porque algumas implementações de biometria usam getUserMedia com
        // vídeo e áudio na mesma chamada. Se o dispositivo não exibir o diálogo, a permissão
        // já foi concedida ou está administrada pela política corporativa.
        requestInitialRuntimePermissionsThenLoad()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        with(binding.webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            allowFileAccess = false
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = "$userAgentString VisionID/${BuildConfig.VERSION_NAME}"
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(binding.webView, true)
        }

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val target = request.url
                return if (isValidWebUrl(target.toString())) {
                    AppLog.info(
                        this@WebViewActivity,
                        "WEBVIEW",
                        "WEB_NAVIGATION",
                        "Navegação solicitada pelo site",
                        mapOf("url" to AppLog.safeUrl(target.toString()))
                    )
                    false
                } else {
                    Log.w(TAG, "Esquema de navegação não suportado: $target")
                    AppLog.warning(
                        this@WebViewActivity,
                        "WEBVIEW",
                        "WEB_NAVIGATION_UNSUPPORTED",
                        "Navegação com esquema não suportado",
                        mapOf("url" to AppLog.safeUrl(target.toString()))
                    )
                    true
                }
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
                super.onPageCommitVisible(view, url)
                AppLog.info(this@WebViewActivity, "WEBVIEW", "PAGE_VISIBLE", "Página visível", mapOf("url" to AppLog.safeUrl(url)))
                scheduleAutomaticPageActivation()
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                AppLog.info(this@WebViewActivity, "WEBVIEW", "PAGE_FINISHED", "Carregamento da página concluído", mapOf("url" to AppLog.safeUrl(url)))
                scheduleAutomaticPageActivation()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    Log.e(TAG, "Erro WebView ${error.errorCode}: ${error.description}")
                    AppLog.error(this@WebViewActivity, "WEBVIEW", "PAGE_LOAD_ERROR", "Falha ao carregar a página", details = mapOf("errorCode" to error.errorCode, "description" to error.description.toString(), "url" to AppLog.safeUrl(request.url.toString())))
                    Toast.makeText(
                        this@WebViewActivity,
                        "Falha ao carregar a página.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                binding.progressBar.progress = newProgress
                binding.progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
                if (newProgress == 25 || newProgress == 50 || newProgress == 75 || newProgress == 100) {
                    AppLog.info(this@WebViewActivity, "WEBVIEW", "LOAD_PROGRESS", "Progresso de carregamento: $newProgress%", mapOf("progress" to newProgress))
                }
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    Log.d(
                        TAG,
                        "Permissão web solicitada por ${request.origin}: ${request.resources.joinToString()}"
                    )

                    AppLog.info(this@WebViewActivity, "PERMISSION", "WEB_PERMISSION_REQUESTED", "Página solicitou recursos", mapOf("origin" to request.origin.toString(), "resources" to request.resources.joinToString()))
                    val missingPermissions = runtimePermissionsNeededFor(request)
                    if (missingPermissions.isEmpty()) {
                        grantSupportedWebResources(request)
                    } else {
                        pendingWebPermissionRequest?.deny()
                        pendingWebPermissionRequest = request
                        runtimePermissionsLauncher.launch(missingPermissions.toTypedArray())
                    }
                }
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest) {
                runOnUiThread {
                    if (pendingWebPermissionRequest == request) {
                        pendingWebPermissionRequest = null
                    }
                    Log.w(TAG, "Solicitação de permissão web cancelada por ${request.origin}")
                    AppLog.warning(this@WebViewActivity, "PERMISSION", "WEB_PERMISSION_CANCELED", "Solicitação web cancelada", mapOf("origin" to request.origin.toString()))
                }
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d(
                    TAG,
                    "WEB ${consoleMessage.messageLevel()}: ${consoleMessage.message()} " +
                        "(${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})"
                )
                AppLog.info(this@WebViewActivity, "WEB_CONSOLE", "CONSOLE_MESSAGE", consoleMessage.message(), mapOf("level" to consoleMessage.messageLevel().name, "source" to AppLog.safeUrl(consoleMessage.sourceId()), "line" to consoleMessage.lineNumber()))
                return true
            }
        }
    }


    /**
     * A página de captura só inicia depois do primeiro toque no placeholder em alguns
     * WebViews corporativos. Reproduzimos automaticamente esse primeiro toque quando
     * o conteúdo já está visível, além de garantir foco e estado ativo do WebView.
     */
    private fun scheduleAutomaticPageActivation() {
        if (automaticPageActivationScheduled) return
        automaticPageActivationScheduled = true

        binding.webView.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed

            binding.webView.apply {
                onResume()
                resumeTimers()
                isFocusable = true
                isFocusableInTouchMode = true
                requestFocus(View.FOCUS_DOWN)
            }

            dispatchAutomaticTapAtCenter()

            // Fallback para páginas que escutam clique diretamente no placeholder/body.
            // O clique é limitado ao elemento central visível e executado apenas uma vez.
            binding.webView.evaluateJavascript(
                """
                (function() {
                    try {
                        window.focus();
                        var x = Math.floor(window.innerWidth / 2);
                        var y = Math.floor(window.innerHeight / 2);
                        var target = document.elementFromPoint(x, y) || document.body;
                        if (target) {
                            target.focus && target.focus();
                            target.click && target.click();
                        }
                        document.dispatchEvent(new Event('visibilitychange'));
                        window.dispatchEvent(new Event('focus'));
                        return 'VisionID:auto-activation-ok';
                    } catch (error) {
                        return 'VisionID:auto-activation-error:' + error;
                    }
                })();
                """.trimIndent(),
                null
            )
        }, AUTO_PAGE_ACTIVATION_DELAY_MS)
    }

    private fun dispatchAutomaticTapAtCenter() {
        val webView = binding.webView
        val x = webView.width / 2f
        val y = webView.height / 2f
        if (x <= 0f || y <= 0f) return

        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            x,
            y,
            0
        )
        val up = MotionEvent.obtain(
            downTime,
            downTime + AUTOMATIC_TAP_DURATION_MS,
            MotionEvent.ACTION_UP,
            x,
            y,
            0
        )

        try {
            webView.dispatchTouchEvent(down)
            webView.dispatchTouchEvent(up)
            Log.d(TAG, "Toque automático enviado ao centro do WebView")
            AppLog.info(this, "WEBVIEW", "AUTO_TAP_SENT", "Toque automático enviado ao placeholder", mapOf("x" to x, "y" to y))
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    private fun requestInitialRuntimePermissionsThenLoad() {
        val missing = buildList {
            if (!hasPermission(Manifest.permission.CAMERA)) {
                add(Manifest.permission.CAMERA)
            }
        }

        AppLog.info(this, "PERMISSION", "INITIAL_PERMISSION_CHECK", "Verificação inicial de permissões", mapOf("missing" to missing.joinToString()))
        if (missing.isEmpty()) {
            loadPendingPage()
        } else {
            runtimePermissionsLauncher.launch(missing.toTypedArray())
        }
    }

    private fun loadPendingPage() {
        if (pageLoaded) return
        pageLoaded = true

        val url = pendingUrl ?: return
        binding.webView.postDelayed({
            if (!isFinishing && !isDestroyed) {
                AppLog.info(this, "WEBVIEW", "PAGE_LOAD_STARTED", "Carregamento iniciado", mapOf("url" to AppLog.safeUrl(url)))
                binding.webView.loadUrl(url)
            }
        }, CAMERA_RELEASE_DELAY_MS)
    }

    private fun runtimePermissionsNeededFor(request: PermissionRequest): List<String> {
        return buildList {
            if (
                request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) &&
                !hasPermission(Manifest.permission.CAMERA)
            ) {
                add(Manifest.permission.CAMERA)
            }

        }
    }

    private fun grantSupportedWebResources(request: PermissionRequest) {
        val grantedResources = request.resources.filter { resource ->
            when (resource) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                    hasPermission(Manifest.permission.CAMERA)

                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> false

                else -> false
            }
        }.toTypedArray()

        if (grantedResources.isEmpty()) {
            Log.w(TAG, "Nenhum recurso web autorizado para ${request.origin}")
            request.deny()
            AppLog.warning(this, "PERMISSION", "WEB_PERMISSION_DENIED", "Nenhum recurso web foi autorizado", mapOf("origin" to request.origin.toString(), "resources" to request.resources.joinToString()))
            Toast.makeText(
                this,
                "Permissão de câmera não concedida.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        runCatching {
            // Concede exatamente os recursos conhecidos solicitados pela página.
            request.grant(grantedResources)
            Log.d(
                TAG,
                "Recursos liberados para ${request.origin}: ${grantedResources.joinToString()}"
            )
            AppLog.info(this, "PERMISSION", "WEB_PERMISSION_GRANTED", "Recursos web autorizados", mapOf("origin" to request.origin.toString(), "resources" to grantedResources.joinToString()))
        }.onFailure {
            Log.e(TAG, "Falha ao liberar recursos para a WebView", it)
            AppLog.error(this, "PERMISSION", "WEB_PERMISSION_GRANT_FAILED", "Falha ao liberar recursos para a WebView", it)
            request.deny()
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun configureBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    private fun isValidWebUrl(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return false
        if (uri.host.isNullOrBlank()) return false
        return uri.scheme.equals("https", ignoreCase = true) ||
            uri.scheme.equals("http", ignoreCase = true)
    }

    override fun onPause() {
        AppLog.info(this, "LIFECYCLE", "WEBVIEW_PAUSED", "WebView pausado")
        binding.webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
        AppLog.info(this, "LIFECYCLE", "WEBVIEW_RESUMED", "WebView retomado")
    }

    override fun onDestroy() {
        AppLog.info(this, "LIFECYCLE", "WEBVIEW_DESTROYED", "WebView encerrado")
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
        private const val CAMERA_RELEASE_DELAY_MS = 500L
        private const val AUTO_PAGE_ACTIVATION_DELAY_MS = 700L
        private const val AUTOMATIC_TAP_DURATION_MS = 80L
    }
}

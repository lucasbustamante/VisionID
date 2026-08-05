package com.example.laranjinhaqrwebview

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import com.example.laranjinhaqrwebview.databinding.ActivityWebViewBinding

class WebViewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWebViewBinding

    private var pendingWebPermissionRequest: PermissionRequest? = null
    private var pendingUrl: String? = null
    private var pageLoaded = false
    private var closeButtonShrinkRunnable: Runnable? = null
    private var mainFrameFailed = false
    private var rendererRecoveryAttempted = false
    private var webViewRendererGone = false

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

        val initialUrl = intent.getStringExtra(EXTRA_URL)
        if (isNewlandN960Family() && !intent.getBooleanExtra(EXTRA_FORCE_SYSTEM_WEBVIEW, false)) {
            AppLog.info(this, "WEBVIEW", "N960_GECKO_REDIRECT", "N960K encaminhado para o motor GeckoView", mapOf("url" to AppLog.safeUrl(initialUrl)))
            startActivity(Intent(this, GeckoBrowserActivity::class.java).putExtra(GeckoBrowserActivity.EXTRA_URL, initialUrl))
            finish()
            return
        }

        binding = ActivityWebViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val url = intent.getStringExtra(EXTRA_URL)
        if (!isValidWebUrl(url)) {
            AppLog.warning(this, "WEBVIEW", "INITIAL_URL_INVALID", "URL inicial inválida", mapOf("url" to AppLog.safeUrl(url)))
            Toast.makeText(this, "O endereço informado não é uma URL HTTP ou HTTPS válida.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        pendingUrl = url
        rendererRecoveryAttempted = intent.getBooleanExtra(EXTRA_RENDERER_RECOVERY_ATTEMPTED, false)
        AppLog.info(this, "WEBVIEW", "WEBVIEW_CREATED", "WebView criado", AppLog.deviceSnapshot(this) + mapOf("url" to AppLog.safeUrl(url)))

        configureWebView()
        configureCloseJourneyButton()
        configureBackNavigation()

        // A câmera já costuma estar autorizada pelo leitor de QR Code. O áudio é solicitado
        // previamente porque algumas implementações de biometria usam getUserMedia com
        // vídeo e áudio na mesma chamada. Se o dispositivo não exibir o diálogo, a permissão
        // já foi concedida ou está administrada pela política corporativa.
        requestInitialRuntimePermissionsThenLoad()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun configureCloseJourneyButton() {
        val button = binding.closeJourneyButton
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var initialTranslationX = 0f
        var dragging = false

        button.setOnClickListener {
            AppLog.info(this, "WEBVIEW", "CLOSE_JOURNEY_CLICKED", "Jornada encerrada pelo botão flutuante")
            finish()
        }

        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    initialTranslationX = view.translationX
                    dragging = false
                    view.isPressed = true
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    if (!dragging && (kotlin.math.abs(deltaX) > touchSlop || kotlin.math.abs(deltaY) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        val parentWidth = binding.webViewContainer.width
                        val layoutParams = view.layoutParams as FrameLayout.LayoutParams
                        val leftMargin = layoutParams.leftMargin
                        val rightMargin = layoutParams.rightMargin
                        val maxTranslation = (parentWidth - view.width - leftMargin - rightMargin).coerceAtLeast(0)
                        view.translationX = (initialTranslationX + deltaX).coerceIn(0f, maxTranslation.toFloat())
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    view.isPressed = false
                    if (dragging) {
                        snapCloseButtonToNearestSide()
                    } else {
                        view.performClick()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    if (dragging) snapCloseButtonToNearestSide()
                    true
                }

                else -> false
            }
        }

        closeButtonShrinkRunnable = Runnable {
            if (!isFinishing && !isDestroyed) {
                button.animate()
                    .alpha(0f)
                    .setDuration(CLOSE_BUTTON_ANIMATION_MS)
                    .withEndAction {
                        button.text = "✕"
                        button.setPadding(0, 0, 0, 0)
                        val wasOnRight = button.translationX > 0f
                        button.layoutParams = button.layoutParams.apply {
                            width = dpToPx(CLOSE_BUTTON_COLLAPSED_SIZE_DP)
                        }
                        button.alpha = 0f
                        button.post {
                            if (wasOnRight) {
                                moveCloseButtonToRightImmediately()
                            } else {
                                button.translationX = 0f
                            }
                            button.animate().alpha(1f).setDuration(CLOSE_BUTTON_ANIMATION_MS).start()
                        }
                    }
                    .start()
            }
        }.also { button.postDelayed(it, CLOSE_BUTTON_EXPANDED_DURATION_MS) }
    }

    private fun moveCloseButtonToRightImmediately() {
        val button = binding.closeJourneyButton
        val parentWidth = binding.webViewContainer.width
        val layoutParams = button.layoutParams as FrameLayout.LayoutParams
        button.translationX = (parentWidth - button.width - layoutParams.leftMargin - layoutParams.rightMargin)
            .coerceAtLeast(0)
            .toFloat()
    }

    private fun snapCloseButtonToNearestSide() {
        val button = binding.closeJourneyButton
        val parentWidth = binding.webViewContainer.width
        if (parentWidth <= 0 || button.width <= 0) return

        val layoutParams = button.layoutParams as FrameLayout.LayoutParams
        val leftMargin = layoutParams.leftMargin
        val rightMargin = layoutParams.rightMargin
        val maxTranslation = (parentWidth - button.width - leftMargin - rightMargin).coerceAtLeast(0).toFloat()
        val target = if (button.translationX + button.width / 2f < parentWidth / 2f) 0f else maxTranslation

        button.animate()
            .translationX(target)
            .setDuration(CLOSE_BUTTON_ANIMATION_MS)
            .start()

        val side = if (target == 0f) "left" else "right"
        AppLog.info(this, "WEBVIEW", "CLOSE_BUTTON_MOVED", "Botão de fechamento movido", mapOf("side" to side))
    }

    private fun dpToPx(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val provider = currentWebViewProviderSnapshot()
        AppLog.info(
            this,
            "WEBVIEW",
            "WEBVIEW_PROVIDER",
            "Provedor do WebView identificado",
            provider
        )

        if (provider["available"] != true) {
            AppLog.error(
                this,
                "WEBVIEW",
                "WEBVIEW_PROVIDER_UNAVAILABLE",
                "Nenhum provedor WebView ativo foi encontrado"
            )
            Toast.makeText(
                this,
                "O componente Android System WebView não está ativo neste terminal.",
                Toast.LENGTH_LONG
            ).show()
        }

        binding.webView.setBackgroundColor(Color.WHITE)
        applyRenderingCompatibilityMode()

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
            loadWithOverviewMode = false
            textZoom = 100
            // Mantém o User-Agent original do provedor. Alguns portais rejeitam UAs
            // modificados ou deixam de aplicar o layout esperado.
            userAgentString = WebSettings.getDefaultUserAgent(this@WebViewActivity)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(binding.webView, true)
        }

        WebView.setWebContentsDebuggingEnabled(false)

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                mainFrameFailed = false
                AppLog.info(
                    this@WebViewActivity,
                    "WEBVIEW",
                    "PAGE_STARTED",
                    "Navegação principal iniciada",
                    mapOf("url" to AppLog.safeUrl(url), "renderingMode" to renderingModeName())
                )
            }

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
                forceWebViewRedraw()
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                AppLog.info(this@WebViewActivity, "WEBVIEW", "PAGE_FINISHED", "Carregamento da página concluído", mapOf("url" to AppLog.safeUrl(url)))
                forceWebViewRedraw()
                requestVisualState(view, url)
                scheduleDocumentProbe(url)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    mainFrameFailed = true
                    Log.e(TAG, "Erro WebView ${error.errorCode}: ${error.description}")
                    AppLog.error(this@WebViewActivity, "WEBVIEW", "PAGE_LOAD_ERROR", "Falha ao carregar a página", details = mapOf("errorCode" to error.errorCode, "description" to error.description.toString(), "url" to AppLog.safeUrl(request.url.toString())))
                    Toast.makeText(
                        this@WebViewActivity,
                        "Falha ao carregar a página.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (!request.isForMainFrame) return

                mainFrameFailed = true
                AppLog.error(
                    this@WebViewActivity,
                    "WEBVIEW",
                    "MAIN_FRAME_HTTP_ERROR",
                    "Servidor retornou erro HTTP para a página principal",
                    details = mapOf(
                        "statusCode" to errorResponse.statusCode,
                        "reason" to errorResponse.reasonPhrase,
                        "url" to AppLog.safeUrl(request.url.toString())
                    )
                )
                Toast.makeText(
                    this@WebViewActivity,
                    "O servidor retornou erro ${errorResponse.statusCode}.",
                    Toast.LENGTH_LONG
                ).show()
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError
            ) {
                mainFrameFailed = true
                handler.cancel()
                AppLog.error(
                    this@WebViewActivity,
                    "WEBVIEW",
                    "SSL_ERROR",
                    "A conexão HTTPS foi bloqueada por falha de certificado",
                    details = mapOf(
                        "primaryError" to error.primaryError,
                        "url" to AppLog.safeUrl(error.url),
                        "issuedTo" to error.certificate?.issuedTo?.cName,
                        "issuedBy" to error.certificate?.issuedBy?.cName,
                        "validNotBefore" to error.certificate?.validNotBeforeDate?.time,
                        "validNotAfter" to error.certificate?.validNotAfterDate?.time
                    )
                )
                Toast.makeText(
                    this@WebViewActivity,
                    "Falha de certificado HTTPS. Verifique o certificado ou proxy do terminal.",
                    Toast.LENGTH_LONG
                ).show()
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail
            ): Boolean {
                webViewRendererGone = true
                val didCrash = detail.didCrash()
                val priority = detail.rendererPriorityAtExit()
                AppLog.error(
                    this@WebViewActivity,
                    "WEBVIEW",
                    "RENDER_PROCESS_GONE",
                    "O processo de renderização do WebView foi encerrado",
                    details = mapOf(
                        "didCrash" to didCrash,
                        "rendererPriorityAtExit" to priority,
                        "renderingMode" to renderingModeName()
                    )
                )

                recoverFromRendererFailure()
                return true
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

            override fun onReceivedTitle(view: WebView, title: String?) {
                super.onReceivedTitle(view, title)
                AppLog.info(
                    this@WebViewActivity,
                    "WEBVIEW",
                    "PAGE_TITLE_RECEIVED",
                    "Título da página recebido",
                    mapOf("hasTitle" to !title.isNullOrBlank(), "titleLength" to (title?.length ?: 0))
                )
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

    private fun applyRenderingCompatibilityMode() {
        // LAYER_TYPE_NONE é o modo normal do Android. As versões anteriores
        // forçaram SOFTWARE e depois HARDWARE; ambas criam uma camada intermediária
        // e podem piorar a composição em firmwares embarcados. Deixamos o Chromium
        // escolher o caminho de composição, mantendo a Activity acelerada.
        binding.webView.setLayerType(View.LAYER_TYPE_NONE, null)

        AppLog.info(
            this,
            "WEBVIEW",
            "RENDERING_MODE_CONFIGURED",
            "Modo padrão de renderização do WebView configurado",
            mapOf(
                "mode" to renderingModeName(),
                "layerType" to binding.webView.layerType,
                "n960Compatibility" to isNewlandN960Family(),
                "viewHardwareAcceleratedAtSetup" to binding.webView.isHardwareAccelerated,
                "windowHardwareAccelerated" to ((window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED) != 0),
                "processName" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) android.app.Application.getProcessName() else packageName
            )
        )
    }

    private fun requestVisualState(view: WebView, url: String) {
        val requestId = android.os.SystemClock.elapsedRealtimeNanos()
        view.postVisualStateCallback(
            requestId,
            object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    AppLog.info(
                        this@WebViewActivity,
                        "WEBVIEW",
                        "VISUAL_STATE_READY",
                        "O DOM está pronto para ser desenhado",
                        mapOf(
                            "requestId" to requestId,
                            "url" to AppLog.safeUrl(url),
                            "width" to view.width,
                            "height" to view.height,
                            "contentHeight" to view.contentHeight,
                            "layerType" to view.layerType,
                            "hardwareAccelerated" to view.isHardwareAccelerated
                        )
                    )
                    forceWebViewRedraw()
                }
            }
        )
    }

    private fun forceWebViewRedraw() {
        binding.webView.post {
            if (isFinishing || isDestroyed) return@post
            binding.webView.requestLayout()
            binding.webView.invalidate()
            binding.webView.postInvalidateOnAnimation()
        }
    }

    private fun scheduleDocumentProbe(url: String) {
        if (url.startsWith("about:", ignoreCase = true)) return
        binding.webView.postDelayed({
            if (isFinishing || isDestroyed || mainFrameFailed) return@postDelayed
            val liveUrl = binding.webView.url.orEmpty()
            if (liveUrl.startsWith("about:", ignoreCase = true) || liveUrl != url) return@postDelayed

            binding.webView.evaluateJavascript(
                """
                (function() {
                    try {
                        var html = document.documentElement;
                        var body = document.body;
                        return JSON.stringify({
                            readyState: document.readyState || '',
                            htmlLength: html && html.outerHTML ? html.outerHTML.length : 0,
                            bodyTextLength: body && body.innerText ? body.innerText.length : 0,
                            bodyChildren: body && body.children ? body.children.length : 0,
                            titleLength: document.title ? document.title.length : 0,
                            visibilityState: document.visibilityState || ''
                        });
                    } catch (error) {
                        return JSON.stringify({probeError: String(error)});
                    }
                })();
                """.trimIndent()
            ) { result ->
                AppLog.info(
                    this,
                    "WEBVIEW",
                    "DOCUMENT_PROBE",
                    "Diagnóstico do documento carregado",
                    mapOf(
                        "url" to AppLog.safeUrl(liveUrl),
                        "result" to result.orEmpty().take(MAX_DIAGNOSTIC_RESULT_LENGTH),
                        "progress" to binding.webView.progress,
                        "renderingMode" to renderingModeName()
                    )
                )
                forceWebViewRedraw()
            }
        }, DOCUMENT_PROBE_DELAY_MS)
    }

    private fun recoverFromRendererFailure() {
        if (rendererRecoveryAttempted || isFinishing || isDestroyed) {
            Toast.makeText(
                this,
                "O renderizador do WebView foi encerrado novamente.",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        rendererRecoveryAttempted = true
        val url = pendingUrl ?: run {
            finish()
            return
        }

        val recoveryIntent = Intent(this, WebViewActivity::class.java)
            .putExtra(EXTRA_URL, url)
            .putExtra(EXTRA_RENDERER_RECOVERY_ATTEMPTED, true)

        runCatching { binding.webViewContainer.removeView(binding.webView) }
        runCatching { binding.webView.destroy() }
        startActivity(recoveryIntent)
        finish()
    }

    private fun currentWebViewProviderSnapshot(): Map<String, Any?> {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { WebView.getCurrentWebViewPackage() }.getOrNull()
        } else {
            null
        }

        return mapOf(
            "available" to (packageInfo != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O),
            "packageName" to packageInfo?.packageName,
            "versionName" to packageInfo?.versionName,
            "longVersionCode" to packageInfo?.let { PackageInfoCompat.getLongVersionCode(it) }
        )
    }

    private fun isNewlandN960Family(): Boolean {
        val deviceIdentity = listOf(
            Build.MANUFACTURER,
            Build.BRAND,
            Build.MODEL,
            Build.DEVICE,
            Build.PRODUCT,
            Build.BOARD
        ).joinToString(" ").lowercase()

        return deviceIdentity.contains("n960") ||
            (deviceIdentity.contains("newland") && deviceIdentity.contains("960"))
    }

    private fun renderingModeName(): String = "default-none"

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
            if (isFinishing || isDestroyed) return@postDelayed

            if (isNewlandN960Family()) {
                val preferences = getSharedPreferences("webview_compat", MODE_PRIVATE)
                val cacheKey = "cache_cleared_${BuildConfig.VERSION_CODE}"
                if (!preferences.getBoolean(cacheKey, false)) {
                    binding.webView.clearCache(true)
                    preferences.edit().putBoolean(cacheKey, true).apply()
                    AppLog.info(this, "WEBVIEW", "WEBVIEW_CACHE_RESET", "Cache do WebView limpo uma vez após a atualização")
                }
            }

            binding.webView.requestFocus(View.FOCUS_DOWN)
            binding.webView.post {
                if (!isFinishing && !isDestroyed && binding.webView.isAttachedToWindow) {
                    AppLog.info(
                        this,
                        "WEBVIEW",
                        "PAGE_LOAD_STARTED",
                        "Carregamento iniciado com WebView anexado à janela",
                        mapOf(
                            "url" to AppLog.safeUrl(url),
                            "attached" to binding.webView.isAttachedToWindow,
                            "width" to binding.webView.width,
                            "height" to binding.webView.height,
                            "hardwareAccelerated" to binding.webView.isHardwareAccelerated,
                            "layerType" to binding.webView.layerType
                        )
                    )
                    binding.webView.loadUrl(url)
                } else {
                    pageLoaded = false
                    loadPendingPage()
                }
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
        if (!webViewRendererGone) {
            binding.webView.onPause()
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (!webViewRendererGone) {
            binding.webView.onResume()
        }
        AppLog.info(this, "LIFECYCLE", "WEBVIEW_RESUMED", "WebView retomado")
    }

    override fun onDestroy() {
        AppLog.info(this, "LIFECYCLE", "WEBVIEW_DESTROYED", "WebView encerrado")
        closeButtonShrinkRunnable?.let { binding.closeJourneyButton.removeCallbacks(it) }
        closeButtonShrinkRunnable = null
        pendingWebPermissionRequest?.deny()
        pendingWebPermissionRequest = null

        if (!webViewRendererGone) {
            binding.webView.apply {
                stopLoading()
                webChromeClient = null
                webViewClient = WebViewClient()
                clearHistory()
                removeAllViews()
                destroy()
            }
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_FORCE_SYSTEM_WEBVIEW = "extra_force_system_webview"
        private const val EXTRA_RENDERER_RECOVERY_ATTEMPTED = "extra_renderer_recovery_attempted"
        private const val TAG = "WebViewActivity"
        private const val CAMERA_RELEASE_DELAY_MS = 500L
        private const val DOCUMENT_PROBE_DELAY_MS = 1_200L
        private const val MAX_DIAGNOSTIC_RESULT_LENGTH = 1_000
        private const val CLOSE_BUTTON_EXPANDED_DURATION_MS = 5_000L
        private const val CLOSE_BUTTON_ANIMATION_MS = 180L
        private const val CLOSE_BUTTON_COLLAPSED_SIZE_DP = 48
    }
}

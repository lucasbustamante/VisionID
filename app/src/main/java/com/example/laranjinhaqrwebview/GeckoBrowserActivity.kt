package com.example.laranjinhaqrwebview

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.laranjinhaqrwebview.databinding.ActivityGeckoBrowserBinding
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession

/**
 * Motor web independente usado pela família N960/N960K.
 */
class GeckoBrowserActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGeckoBrowserBinding
    private lateinit var session: GeckoSession
    private var currentUrl: String = ""
    private var pendingAndroidPermissionCallback: GeckoSession.PermissionDelegate.Callback? = null
    private var canGoBack = false
    private var closeButtonCollapseRunnable: Runnable? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.all { it }
        pendingAndroidPermissionCallback?.let { callback ->
            if (granted) callback.grant() else callback.reject()
        }
        pendingAndroidPermissionCallback = null
        AppLog.info(this, "GECKO", "ANDROID_PERMISSION_RESULT", "Permissões solicitadas pelo GeckoView", result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGeckoBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        if (!isValidWebUrl(currentUrl)) {
            Toast.makeText(this, "URL inválida.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        configureCloseJourneyButton()

        session = GeckoSession()
        session.setContentDelegate(object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                AppLog.info(
                    this@GeckoBrowserActivity,
                    "GECKO",
                    "TITLE",
                    "Título recebido",
                    mapOf("title" to title?.take(160))
                )
            }
        })
        session.setProgressDelegate(object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                currentUrl = url
                binding.geckoProgressBar.visibility = View.VISIBLE
                AppLog.info(
                    this@GeckoBrowserActivity,
                    "GECKO",
                    "PAGE_START",
                    "Gecko iniciou a página",
                    mapOf("url" to AppLog.safeUrl(url))
                )
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                binding.geckoProgressBar.progress = progress
                binding.geckoProgressBar.visibility = if (progress >= 100) View.GONE else View.VISIBLE
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                binding.geckoProgressBar.visibility = View.GONE
                AppLog.info(
                    this@GeckoBrowserActivity,
                    "GECKO",
                    "PAGE_STOP",
                    "Gecko terminou a página",
                    mapOf("success" to success, "url" to AppLog.safeUrl(currentUrl))
                )
                if (!success) {
                    Toast.makeText(
                        this@GeckoBrowserActivity,
                        "Falha ao carregar a jornada.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })
        session.setNavigationDelegate(object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(session: GeckoSession, value: Boolean) {
                canGoBack = value
            }
        })
        session.setPermissionDelegate(object : GeckoSession.PermissionDelegate {
            override fun onAndroidPermissionsRequest(
                session: GeckoSession,
                permissions: Array<out String>?,
                callback: GeckoSession.PermissionDelegate.Callback
            ) {
                val requested = permissions.orEmpty().filter {
                    it == Manifest.permission.CAMERA || it == Manifest.permission.RECORD_AUDIO
                }
                val missing = requested.filter {
                    ContextCompat.checkSelfPermission(
                        this@GeckoBrowserActivity,
                        it
                    ) != PackageManager.PERMISSION_GRANTED
                }
                if (missing.isEmpty()) {
                    callback.grant()
                } else {
                    pendingAndroidPermissionCallback?.reject()
                    pendingAndroidPermissionCallback = callback
                    permissionLauncher.launch(missing.toTypedArray())
                }
            }

            override fun onMediaPermissionRequest(
                session: GeckoSession,
                uri: String,
                video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                callback: GeckoSession.PermissionDelegate.MediaCallback
            ) {
                val selectedVideo = video?.firstOrNull()
                val selectedAudio = audio?.firstOrNull()
                if (selectedVideo != null || selectedAudio != null) {
                    callback.grant(selectedVideo, selectedAudio)
                    AppLog.info(
                        this@GeckoBrowserActivity,
                        "GECKO",
                        "MEDIA_GRANTED",
                        "Câmera/microfone liberados para a página",
                        mapOf(
                            "origin" to AppLog.safeUrl(uri),
                            "video" to (selectedVideo != null),
                            "audio" to (selectedAudio != null)
                        )
                    )
                } else {
                    callback.reject()
                }
            }
        })

        session.open(runtime())
        binding.geckoView.setSession(session)
        AppLog.info(
            this,
            "GECKO",
            "ENGINE_STARTED",
            "Motor GeckoView iniciado",
            AppLog.deviceSnapshot(this) + mapOf(
                "url" to AppLog.safeUrl(currentUrl),
                "engine" to GECKO_VERSION
            )
        )
        session.loadUri(currentUrl)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (canGoBack) session.goBack() else finish()
            }
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun configureCloseJourneyButton() {
        val button = binding.geckoCloseButton
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var initialTranslationX = 0f
        var dragging = false

        button.setOnClickListener {
            AppLog.info(this, "GECKO", "CLOSE_JOURNEY_CLICKED", "Jornada encerrada pelo botão flutuante")
            finish()
        }

        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    view.animate().cancel()
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
                    if (!dragging && (
                            kotlin.math.abs(deltaX) > touchSlop ||
                                kotlin.math.abs(deltaY) > touchSlop
                            )
                    ) {
                        dragging = true
                    }
                    if (dragging) {
                        val maxTranslation = closeButtonMaxTranslation(view)
                        view.translationX = (initialTranslationX + deltaX)
                            .coerceIn(0f, maxTranslation)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    view.isPressed = false
                    if (dragging) snapCloseButtonToNearestSide() else view.performClick()
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    view.isPressed = false
                    if (dragging) snapCloseButtonToNearestSide()
                    true
                }

                else -> false
            }
        }

        closeButtonCollapseRunnable = Runnable {
            if (!isFinishing && !isDestroyed) collapseCloseButton()
        }.also { button.postDelayed(it, CLOSE_BUTTON_EXPANDED_DURATION_MS) }
    }

    private fun collapseCloseButton() {
        val button = binding.geckoCloseButton
        val parentWidth = binding.root.width
        val buttonCenter = button.x + button.width / 2f
        val keepOnRight = parentWidth > 0 && buttonCenter >= parentWidth / 2f

        button.animate()
            .alpha(0f)
            .setDuration(CLOSE_BUTTON_ANIMATION_MS)
            .withEndAction {
                button.text = "✕"
                button.setPadding(0, 0, 0, 0)
                button.minWidth = dpToPx(CLOSE_BUTTON_COLLAPSED_SIZE_DP)
                button.layoutParams = button.layoutParams.apply {
                    width = dpToPx(CLOSE_BUTTON_COLLAPSED_SIZE_DP)
                }
                button.requestLayout()
                button.post {
                    button.translationX = if (keepOnRight) closeButtonMaxTranslation(button) else 0f
                    button.animate()
                        .alpha(1f)
                        .setDuration(CLOSE_BUTTON_ANIMATION_MS)
                        .start()
                }
            }
            .start()
    }

    private fun closeButtonMaxTranslation(view: View): Float {
        val parentWidth = binding.root.width
        if (parentWidth <= 0 || view.width <= 0) return 0f
        val params = view.layoutParams as FrameLayout.LayoutParams
        return (parentWidth - view.width - params.leftMargin - params.rightMargin)
            .coerceAtLeast(0)
            .toFloat()
    }

    private fun snapCloseButtonToNearestSide() {
        val button = binding.geckoCloseButton
        val parentWidth = binding.root.width
        if (parentWidth <= 0 || button.width <= 0) return

        val maxTranslation = closeButtonMaxTranslation(button)
        val currentCenter = button.x + button.width / 2f
        val target = if (currentCenter < parentWidth / 2f) 0f else maxTranslation

        button.animate()
            .translationX(target)
            .setDuration(CLOSE_BUTTON_ANIMATION_MS)
            .start()
    }

    private fun dpToPx(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun runtime(): GeckoRuntime = synchronized(runtimeLock) {
        sharedRuntime ?: GeckoRuntime.create(applicationContext).also { sharedRuntime = it }
    }

    private fun isValidWebUrl(value: String): Boolean {
        if (value.isBlank()) return false
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return false
        return !uri.host.isNullOrBlank() &&
            (uri.scheme.equals("https", true) || uri.scheme.equals("http", true))
    }

    override fun onDestroy() {
        closeButtonCollapseRunnable?.let { binding.geckoCloseButton.removeCallbacks(it) }
        closeButtonCollapseRunnable = null
        pendingAndroidPermissionCallback?.reject()
        pendingAndroidPermissionCallback = null
        if (::session.isInitialized) session.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        private const val GECKO_VERSION = "152.0.20260713164047"
        private const val CLOSE_BUTTON_EXPANDED_DURATION_MS = 5_000L
        private const val CLOSE_BUTTON_ANIMATION_MS = 180L
        private const val CLOSE_BUTTON_COLLAPSED_SIZE_DP = 48
        private val runtimeLock = Any()

        @Volatile
        private var sharedRuntime: GeckoRuntime? = null
    }
}

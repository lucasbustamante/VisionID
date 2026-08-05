package com.example.laranjinhaqrwebview

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.laranjinhaqrwebview.databinding.ActivityQrScannerBinding
import com.google.android.material.snackbar.Snackbar
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.net.URL
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class QrScannerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityQrScannerBinding
    private lateinit var cameraExecutor: ExecutorService

    /** Impede navegação duplicada depois que um QR válido já foi aceito. */
    private val qrHandled = AtomicBoolean(false)

    /** Garante que apenas uma chamada do ML Kit fique em andamento por vez. */
    private val analysisInProgress = AtomicBoolean(false)

    private var cameraProvider: ProcessCameraProvider? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var invalidQrSnackbar: Snackbar? = null
    private var invalidDismissScheduled = false
    private var lastInvalidQrValue: String? = null
    private val dismissInvalidQrRunnable = Runnable {
        invalidDismissScheduled = false
        dismissInvalidQrMessage()
        lastInvalidQrValue = null
    }

    private val allowedDomains = setOf(
        "itau.com.br",
        "itau-unibanco.com.br",
        "itaucard.com.br"
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            AppLog.info(this, "PERMISSION", "CAMERA_GRANTED", "Permissão de câmera concedida ao leitor")
            startCamera()
        } else {
            AppLog.warning(this, "PERMISSION", "CAMERA_DENIED", "Permissão de câmera negada ao leitor")
            Toast.makeText(this, "A permissão da câmera é necessária.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cameraExecutor = Executors.newSingleThreadExecutor()
        AppLog.info(this, "CAMERA", "QR_SCREEN_CREATED", "Tela de leitura de QR Code criada")

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        AppLog.info(this, "CAMERA", "QR_CAMERA_STARTING", "Inicialização da câmera traseira iniciada")
        val providerFuture: ListenableFuture<ProcessCameraProvider> =
            ProcessCameraProvider.getInstance(this)

        providerFuture.addListener(
            Runnable {
                try {
                    val provider: ProcessCameraProvider = providerFuture.get()
                    cameraProvider = provider

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(binding.previewView.surfaceProvider)
                    }

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    val options = BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build()
                    val scanner = BarcodeScanning.getClient(options)

                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image

                        if (mediaImage == null || qrHandled.get() || !analysisInProgress.compareAndSet(false, true)) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        val input = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )

                        scanner.process(input)
                            .addOnSuccessListener { barcodes -> handleBarcodes(barcodes) }
                            .addOnFailureListener { error ->
                                Log.e(TAG, "Falha ao analisar QR Code", error)
                                AppLog.error(this, "QR", "QR_ANALYSIS_FAILED", "Falha ao analisar QR Code", error)
                            }
                            .addOnCompleteListener {
                                analysisInProgress.set(false)
                                imageProxy.close()
                            }
                    }

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                    AppLog.info(this, "CAMERA", "QR_CAMERA_READY", "Câmera traseira pronta para leitura")
                } catch (error: Exception) {
                    Log.e(TAG, "Não foi possível abrir a câmera traseira", error)
                    AppLog.error(this, "CAMERA", "QR_CAMERA_FAILED", "Não foi possível abrir a câmera traseira", error)
                    Toast.makeText(this, "Não foi possível abrir a câmera traseira.", Toast.LENGTH_LONG).show()
                    finish()
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun handleBarcodes(barcodes: List<Barcode>) {
        if (qrHandled.get()) return

        val qrValues = barcodes.asSequence()
            .filter { barcode ->
                barcode.format == Barcode.FORMAT_QR_CODE && barcode.rawValue != null
            }
            .mapNotNull { it.rawValue?.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()

        /*
         * Nenhum QR está visível. Agenda a remoção apenas uma vez.
         * Não reinicia o timer em cada frame, pois isso deixava o aviso preso na tela.
         */
        if (qrValues.isEmpty()) {
            scheduleInvalidQrDismissOnce()
            return
        }

        /* Um QR voltou a aparecer: cancela a remoção pendente enquanto ele estiver visível. */
        cancelInvalidQrDismiss()

        /* Um QR válido sempre tem prioridade, mesmo que antes houvesse um inválido na câmera. */
        val validValue = qrValues.firstOrNull(::isQrAllowed)
        if (validValue != null) {
            dismissInvalidQrMessage()
            lastInvalidQrValue = null
            if (qrHandled.compareAndSet(false, true)) {
                openUrl(validValue)
            }
            return
        }

        /*
         * Mostra uma única mensagem para o QR inválido atualmente visível.
         * Frames repetidos do mesmo QR não criam novas mensagens nem novas filas.
         */
        val currentInvalidValue = qrValues.first()
        if (lastInvalidQrValue != currentInvalidValue || invalidQrSnackbar?.isShown != true) {
            lastInvalidQrValue = currentInvalidValue
            showInvalidQrMessage()
        }
    }

    private fun isQrAllowed(value: String): Boolean {
        if (!isValidHttpUrl(value)) return false
        return !QrSecurityPreferences.isDomainLockEnabled(this) || isValidItauUrl(value)
    }

    private fun openUrl(rawValue: String) {
        AppLog.info(this, "QR", "QR_DETECTED", "QR Code autorizado detectado", mapOf("url" to AppLog.safeUrl(rawValue)))
        val uri = runCatching { Uri.parse(rawValue.trim()) }.getOrNull()
        if (uri == null) {
            qrHandled.set(false)
            lastInvalidQrValue = rawValue
            showInvalidQrMessage()
            return
        }

        cameraProvider?.unbindAll()
        AppLog.info(this, "CAMERA", "QR_CAMERA_RELEASED", "Câmera traseira liberada antes de abrir o site")

        binding.root.postDelayed({
            if (!isFinishing && !isDestroyed) {
                AppLog.info(this, "QR", "WEBVIEW_OPENING", "Abrindo o link lido no WebView", mapOf("url" to AppLog.safeUrl(uri.toString())))
                val intent = Intent(this, WebViewActivity::class.java)
                    .putExtra(WebViewActivity.EXTRA_URL, uri.toString())
                startActivity(intent)
                finish()
            }
        }, CAMERA_RELEASE_DELAY_MS)
    }

    private fun isValidHttpUrl(value: String): Boolean = try {
        val url = URL(value)
        val protocol = url.protocol.lowercase(Locale.ROOT)
        (protocol == "http" || protocol == "https") && !url.host.isNullOrBlank()
    } catch (_: Exception) {
        false
    }

    private fun isValidItauUrl(value: String): Boolean = try {
        val host = URL(value).host
            ?.trim()
            ?.trimEnd('.')
            ?.lowercase(Locale.ROOT)
            ?: return false

        allowedDomains.any { domain ->
            host == domain || host.endsWith(".$domain")
        }
    } catch (_: Exception) {
        false
    }

    private fun showInvalidQrMessage() {
        cancelInvalidQrDismiss()

        if (invalidQrSnackbar?.isShown == true) return

        invalidQrSnackbar = Snackbar.make(
            binding.root,
            "QR Code não é válido",
            Snackbar.LENGTH_INDEFINITE
        ).also { snackbar ->
            snackbar.show()
        }
    }

    private fun scheduleInvalidQrDismissOnce() {
        if (invalidQrSnackbar?.isShown != true || invalidDismissScheduled) return

        invalidDismissScheduled = true
        mainHandler.postDelayed(dismissInvalidQrRunnable, INVALID_QR_LOST_TIMEOUT_MS)
    }

    private fun cancelInvalidQrDismiss() {
        if (!invalidDismissScheduled) return
        mainHandler.removeCallbacks(dismissInvalidQrRunnable)
        invalidDismissScheduled = false
    }

    private fun dismissInvalidQrMessage() {
        mainHandler.removeCallbacks(dismissInvalidQrRunnable)
        invalidDismissScheduled = false
        invalidQrSnackbar?.dismiss()
        invalidQrSnackbar = null
    }

    override fun onDestroy() {
        dismissInvalidQrMessage()
        cameraProvider?.unbindAll()
        cameraExecutor.shutdownNow()
        AppLog.info(this, "CAMERA", "QR_SCREEN_DESTROYED", "Tela de QR Code encerrada")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "QrScannerActivity"
        private const val CAMERA_RELEASE_DELAY_MS = 500L
        private const val INVALID_QR_LOST_TIMEOUT_MS = 450L
    }
}

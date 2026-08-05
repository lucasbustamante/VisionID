package com.example.laranjinhaqrwebview

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
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
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class QrScannerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityQrScannerBinding
    private lateinit var cameraExecutor: ExecutorService
    private val qrHandled = AtomicBoolean(false)
    private var cameraProvider: ProcessCameraProvider? = null

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
                    if (mediaImage == null || qrHandled.get()) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    scanner.process(input)
                        .addOnSuccessListener { barcodes ->
                            val value = barcodes.firstNotNullOfOrNull { it.rawValue }
                            if (value != null && qrHandled.compareAndSet(false, true)) {
                                openUrl(value)
                            }
                        }
                        .addOnFailureListener { error ->
                            Log.e(TAG, "Falha ao analisar QR Code", error)
                            AppLog.error(this, "QR", "QR_ANALYSIS_FAILED", "Falha ao analisar QR Code", error)
                        }
                        .addOnCompleteListener { imageProxy.close() }
                }

                provider.unbindAll()
                // Força a câmera classificada pelo Android como traseira.
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

    private fun openUrl(rawValue: String) {
        AppLog.info(this, "QR", "QR_DETECTED", "QR Code detectado", mapOf("url" to AppLog.safeUrl(rawValue)))
        val uri = runCatching { Uri.parse(rawValue.trim()) }.getOrNull()
        if (!isValidWebUrl(uri)) {
            qrHandled.set(false)
            AppLog.warning(this, "QR", "QR_URL_INVALID", "QR Code não contém uma URL HTTP ou HTTPS válida", mapOf("url" to AppLog.safeUrl(rawValue)))
            Toast.makeText(this, "O QR Code não contém um link HTTP ou HTTPS válido.", Toast.LENGTH_LONG).show()
            return
        }

        // Libera completamente a câmera traseira antes de a página facial tentar abrir
        // a câmera frontal pelo WebView. Alguns dispositivos corporativos demoram alguns
        // milissegundos para efetivar o fechamento do CameraX.
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

    private fun isValidWebUrl(uri: Uri?): Boolean {
        if (uri == null || uri.host.isNullOrBlank()) return false
        return uri.scheme.equals("https", ignoreCase = true) ||
            uri.scheme.equals("http", ignoreCase = true)
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        AppLog.info(this, "CAMERA", "QR_SCREEN_DESTROYED", "Tela de QR Code encerrada")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "QrScannerActivity"
        private const val CAMERA_RELEASE_DELAY_MS = 500L
    }
}

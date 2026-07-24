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
        if (granted) startCamera() else {
            Toast.makeText(this, "A permissão da câmera é necessária.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
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
            } catch (error: Exception) {
                Log.e(TAG, "Não foi possível abrir a câmera traseira", error)
                Toast.makeText(this, "Não foi possível abrir a câmera traseira.", Toast.LENGTH_LONG).show()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun openUrl(rawValue: String) {
        val uri = runCatching { Uri.parse(rawValue.trim()) }.getOrNull()
        if (uri?.scheme?.lowercase() != "https" || uri.host.isNullOrBlank()) {
            qrHandled.set(false)
            Toast.makeText(this, "O QR Code não contém uma URL HTTPS válida.", Toast.LENGTH_LONG).show()
            return
        }

        cameraProvider?.unbindAll() // libera a câmera antes da página facial solicitá-la
        val intent = Intent(this, WebViewActivity::class.java)
            .putExtra(WebViewActivity.EXTRA_URL, uri.toString())
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "QrScannerActivity"
    }
}

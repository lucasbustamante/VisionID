package com.example.laranjinhaqrwebview

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Bundle
import android.view.Surface
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.laranjinhaqrwebview.databinding.ActivityCameraTestBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraTestActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCameraTestBinding
    private lateinit var workerExecutor: ExecutorService

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var printing = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            AppLog.info(
                this,
                category = "PERMISSION",
                event = "CAMERA_TEST_PERMISSION_GRANTED",
                message = "Permissão de câmera concedida ao teste técnico"
            )
            startCamera()
        } else {
            AppLog.warning(
                this,
                category = "PERMISSION",
                event = "CAMERA_TEST_PERMISSION_DENIED",
                message = "Permissão de câmera negada ao teste técnico"
            )
            Toast.makeText(this, "A permissão da câmera é necessária.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        workerExecutor = Executors.newSingleThreadExecutor()

        binding.capturePhotoButton.setOnClickListener { capturePhoto() }
        binding.switchCameraButton.setOnClickListener { switchCamera() }
        binding.closeCameraButton.setOnClickListener { finish() }

        AppLog.info(
            this,
            category = "CAMERA",
            event = "CAMERA_TEST_OPENED",
            message = "Tela de teste de câmera aberta"
        )

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        setCameraControlsEnabled(false)
        binding.cameraStatusText.text = "Abrindo câmera..."

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider

                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(binding.previewView.display?.rotation ?: Surface.ROTATION_0)
                    .build()

                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview, capture)
                imageCapture = capture

                val cameraLabel = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    "Câmera frontal pronta"
                } else {
                    "Câmera traseira pronta"
                }
                binding.cameraStatusText.text = cameraLabel
                updateSwitchCameraVisibility(provider)
                setCameraControlsEnabled(true)

                AppLog.info(
                    this,
                    category = "CAMERA",
                    event = "CAMERA_TEST_READY",
                    message = cameraLabel
                )
            } catch (error: Throwable) {
                imageCapture = null
                binding.cameraStatusText.text = "Falha ao abrir a câmera"
                setCameraControlsEnabled(false)
                AppLog.error(
                    this,
                    category = "CAMERA",
                    event = "CAMERA_TEST_FAILED",
                    message = "Não foi possível iniciar a câmera de teste",
                    throwable = error
                )
                Toast.makeText(this, "Não foi possível abrir a câmera.", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePhoto() {
        if (printing) return

        val capture = imageCapture ?: run {
            Toast.makeText(this, "A câmera ainda não está pronta.", Toast.LENGTH_SHORT).show()
            return
        }

        val outputFile = File(
            cacheDir,
            "visionid_camera_test_${FILE_NAME_FORMAT.format(Date())}.jpg"
        )
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        setCameraControlsEnabled(false)
        binding.cameraStatusText.text = "Capturando foto..."

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    workerExecutor.execute {
                        val bitmapResult = runCatching { decodeAndOrient(outputFile) }
                        runCatching { outputFile.delete() }

                        runOnUiThread {
                            if (isFinishing || isDestroyed) {
                                bitmapResult.getOrNull()?.recycle()
                                return@runOnUiThread
                            }

                            bitmapResult.fold(
                                onSuccess = { bitmap ->
                                    binding.cameraStatusText.text = "Foto capturada"
                                    setCameraControlsEnabled(true)
                                    AppLog.info(
                                        this@CameraTestActivity,
                                        category = "CAMERA",
                                        event = "CAMERA_TEST_PHOTO_CAPTURED",
                                        message = "Foto capturada no teste técnico",
                                        details = mapOf(
                                            "largura" to bitmap.width,
                                            "altura" to bitmap.height,
                                            "camera" to if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                                                "frontal"
                                            } else {
                                                "traseira"
                                            }
                                        )
                                    )
                                    showPrintConfirmation(bitmap)
                                },
                                onFailure = { error ->
                                    binding.cameraStatusText.text = "Falha ao processar a foto"
                                    setCameraControlsEnabled(true)
                                    AppLog.error(
                                        this@CameraTestActivity,
                                        category = "CAMERA",
                                        event = "CAMERA_TEST_PHOTO_FAILED",
                                        message = "Falha ao processar a foto capturada",
                                        throwable = error
                                    )
                                    Toast.makeText(
                                        this@CameraTestActivity,
                                        "A foto foi capturada, mas não pôde ser processada.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            )
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    binding.cameraStatusText.text = "Falha ao tirar a foto"
                    setCameraControlsEnabled(true)
                    runCatching { outputFile.delete() }
                    AppLog.error(
                        this@CameraTestActivity,
                        category = "CAMERA",
                        event = "CAMERA_TEST_CAPTURE_FAILED",
                        message = "CameraX não conseguiu salvar a foto de teste",
                        throwable = exception
                    )
                    Toast.makeText(
                        this@CameraTestActivity,
                        "Não foi possível tirar a foto.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    private fun showPrintConfirmation(bitmap: Bitmap) {
        val imageView = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(bitmap)
            setBackgroundColor(ContextCompat.getColor(this@CameraTestActivity, R.color.black))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dp(18)
            setPadding(padding, dp(6), padding, 0)
            addView(
                imageView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(300)
                )
            )
        }

        var actionSelected = false
        val dialog = AlertDialog.Builder(this)
            .setTitle("Foto capturada")
            .setMessage("Deseja imprimir esta foto na impressora térmica?")
            .setView(container)
            .setNegativeButton("Não") { _, _ ->
                actionSelected = true
                bitmap.recycle()
                binding.cameraStatusText.text = currentCameraReadyText()
            }
            .setPositiveButton("Imprimir", null)
            .setOnCancelListener {
                if (!actionSelected) {
                    actionSelected = true
                    bitmap.recycle()
                    binding.cameraStatusText.text = currentCameraReadyText()
                }
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (actionSelected) return@setOnClickListener
                actionSelected = true
                dialog.dismiss()
                printPhoto(bitmap)
                bitmap.recycle()
            }
        }
        dialog.show()
    }

    private fun printPhoto(bitmap: Bitmap) {
        printing = true
        setCameraControlsEnabled(false)
        binding.cameraStatusText.text = "Enviando foto para a impressora..."
        Toast.makeText(this, "Preparando impressão da foto...", Toast.LENGTH_SHORT).show()

        PhotoPrinter.printAsync(this, bitmap) { result ->
            printing = false
            if (!isFinishing && !isDestroyed) {
                setCameraControlsEnabled(true)
                binding.cameraStatusText.text = currentCameraReadyText()
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun switchCamera() {
        if (printing) return
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        startCamera()
    }

    private fun updateSwitchCameraVisibility(provider: ProcessCameraProvider) {
        val hasBack = runCatching {
            provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
        }.getOrDefault(false)
        val hasFront = runCatching {
            provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
        }.getOrDefault(false)
        binding.switchCameraButton.visibility = if (hasBack && hasFront) View.VISIBLE else View.GONE
    }

    private fun setCameraControlsEnabled(enabled: Boolean) {
        binding.capturePhotoButton.isEnabled = enabled && !printing
        binding.switchCameraButton.isEnabled = enabled && !printing
    }

    private fun currentCameraReadyText(): String =
        if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            "Câmera frontal pronta"
        } else {
            "Câmera traseira pronta"
        }

    private fun decodeAndOrient(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalStateException("Arquivo de imagem inválido ou vazio.")
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DECODE_EDGE)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
            ?: throw IllegalStateException("BitmapFactory não conseguiu decodificar a foto.")

        val orientation = ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
        }

        if (matrix.isIdentity) return decoded

        val oriented = Bitmap.createBitmap(
            decoded,
            0,
            0,
            decoded.width,
            decoded.height,
            matrix,
            true
        )
        if (oriented !== decoded) decoded.recycle()
        return oriented
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        while (width / sample > maxEdge || height / sample > maxEdge) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        workerExecutor.shutdown()
        AppLog.info(
            this,
            category = "CAMERA",
            event = "CAMERA_TEST_CLOSED",
            message = "Tela de teste de câmera encerrada"
        )
        super.onDestroy()
    }

    companion object {
        private val FILE_NAME_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
        private const val MAX_DECODE_EDGE = 1_600
    }
}

package com.example.laranjinhaqrwebview

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors

/** Impressão de fotografia diretamente na impressora térmica integrada do POS. */
internal object PhotoPrinter {
    private const val TAG = "VisionIDPhotoPrinter"
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    data class Result(val success: Boolean, val message: String)

    fun printAsync(context: Context, source: Bitmap, callback: (Result) -> Unit) {
        val ownedCopy = runCatching {
            source.copy(Bitmap.Config.ARGB_8888, false)
        }.getOrNull()

        if (ownedCopy == null) {
            callback(Result(false, "Não foi possível preparar a foto para impressão."))
            return
        }

        val appContext = context.applicationContext
        executor.execute {
            val result = try {
                val thermalBitmap = ThermalImageProcessor.prepare(ownedCopy)
                try {
                    printInternal(appContext, thermalBitmap)
                } finally {
                    thermalBitmap.recycle()
                }
            } catch (error: Throwable) {
                Log.e(TAG, "Falha ao preparar ou imprimir a fotografia", error)
                AppLog.error(
                    appContext,
                    category = "IMPRESSORA",
                    event = "THERMAL_PHOTO_PREPARATION_FAILED",
                    message = "Falha ao preparar a foto para impressão térmica",
                    throwable = error
                )
                Result(false, "Não foi possível preparar a foto para impressão.")
            } finally {
                ownedCopy.recycle()
            }
            mainHandler.post { callback(result) }
        }
    }

    private fun printInternal(context: Context, bitmap: Bitmap): Result {
        val model = "${Build.MANUFACTURER} ${Build.BRAND} ${Build.MODEL}".uppercase()
        val attempts = mutableListOf<PrinterAttempt>()
        val backends: List<() -> PrinterAttempt> = if (
            model.contains("NEWLAND") || model.contains("N960") || model.contains("N950")
        ) {
            listOf(
                { NewlandThermalPrinter.printBitmap(context, bitmap) },
                { XchengAidlPrinter.printBitmap(context, bitmap) },
                { BluetoothInternalPrinter.printBitmap(context, bitmap) }
            )
        } else {
            listOf(
                { XchengAidlPrinter.printBitmap(context, bitmap) },
                { BluetoothInternalPrinter.printBitmap(context, bitmap) },
                { NewlandThermalPrinter.printBitmap(context, bitmap) }
            )
        }

        for (backend in backends) {
            val attempt = runCatching(backend).getOrElse {
                PrinterAttempt(
                    available = true,
                    success = false,
                    backend = "erro interno",
                    detail = "${it.javaClass.simpleName}: ${it.message.orEmpty()}"
                )
            }
            attempts += attempt
            if (attempt.success) {
                AppLog.info(
                    context,
                    category = "IMPRESSORA",
                    event = "THERMAL_PHOTO_PRINT_SUCCESS",
                    message = "Foto enviada à impressora térmica.",
                    details = mapOf(
                        "backend" to attempt.backend,
                        "detalhe" to attempt.detail,
                        "largura" to bitmap.width,
                        "altura" to bitmap.height
                    )
                )
                return Result(true, "Foto enviada para a impressora térmica (${attempt.backend}).")
            }
        }

        val detail = attempts.joinToString(" || ") { "${it.backend}: ${it.detail}" }.take(3000)
        val diagnostics = PrinterDiagnostics.collect(context) + mapOf(
            "tipo" to "fotografia",
            "largura" to bitmap.width,
            "altura" to bitmap.height,
            "tentativas" to detail
        )
        Log.e(TAG, "Falha de impressão da fotografia: $diagnostics")
        AppLog.error(
            context,
            category = "IMPRESSORA",
            event = "THERMAL_PHOTO_PRINT_FAILED",
            message = "Nenhuma integração concluiu a impressão térmica da foto.",
            details = diagnostics
        )
        return Result(
            false,
            "Não foi possível imprimir a foto. O diagnóstico foi gravado nos Logs do aplicativo."
        )
    }
}

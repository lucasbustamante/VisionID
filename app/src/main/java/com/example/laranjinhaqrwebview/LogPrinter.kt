package com.example.laranjinhaqrwebview

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.text.Normalizer
import java.util.concurrent.Executors

/** Impressão direta na bobina térmica do SmartPOS; nunca usa PrintManager/PDF. */
internal object LogPrinter {
    private const val TAG = "VisionIDPrinter"
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    data class Result(val success: Boolean, val message: String)

    fun printAsync(context: Context, title: String, content: String, callback: (Result) -> Unit) {
        val appContext = context.applicationContext
        val receipt = formatReceipt(title, content)
        executor.execute {
            val result = printInternal(appContext, receipt)
            mainHandler.post { callback(result) }
        }
    }

    private fun printInternal(context: Context, receipt: String): Result {
        val model = "${Build.MANUFACTURER} ${Build.BRAND} ${Build.MODEL}".uppercase()
        val attempts = mutableListOf<PrinterAttempt>()
        val backends: List<() -> PrinterAttempt> = if (
            model.contains("NEWLAND") || model.contains("N960") || model.contains("N950")
        ) {
            listOf(
                { NewlandThermalPrinter.print(context, receipt) },
                { XchengAidlPrinter.print(context, receipt) },
                { BluetoothInternalPrinter.print(context, receipt) }
            )
        } else {
            listOf(
                { XchengAidlPrinter.print(context, receipt) },
                { BluetoothInternalPrinter.print(context, receipt) },
                { NewlandThermalPrinter.print(context, receipt) }
            )
        }

        for (backend in backends) {
            val attempt = runCatching(backend).getOrElse {
                PrinterAttempt(true, false, "erro interno", "${it.javaClass.simpleName}: ${it.message.orEmpty()}")
            }
            attempts += attempt
            if (attempt.success) {
                AppLog.info(
                    context,
                    category = "IMPRESSORA",
                    event = "THERMAL_PRINT_SUCCESS",
                    message = "Log enviado à impressora térmica.",
                    details = mapOf("backend" to attempt.backend, "detalhe" to attempt.detail)
                )
                return Result(true, "Comprovante enviado para a impressora térmica (${attempt.backend}).")
            }
        }

        val detail = attempts.joinToString(" || ") { "${it.backend}: ${it.detail}" }.take(2600)
        val diagnostics = PrinterDiagnostics.collect(context) + mapOf("tentativas" to detail)
        Log.e(TAG, "Falha de impressão: $diagnostics")
        AppLog.error(
            context,
            category = "IMPRESSORA",
            event = "THERMAL_PRINT_FAILED",
            message = "Nenhuma das integrações disponíveis concluiu a impressão térmica.",
            details = diagnostics
        )
        return Result(
            false,
            "Não foi possível concluir a impressão. Um diagnóstico completo foi gravado nos Logs do aplicativo."
        )
    }

    private fun formatReceipt(title: String, content: String): String = buildString {
        appendLine(center(ascii(title.uppercase()), 32))
        appendLine("================================")
        content.lineSequence().forEach { source ->
            wrap(ascii(source.replace('\t', ' ')), 32).forEach(::appendLine)
        }
        appendLine("================================")
        appendLine(center("FIM DO LOG", 32))
        repeat(4) { appendLine() }
    }

    private fun ascii(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .replace('–', '-')
        .replace('—', '-')
        .replace('“', '"')
        .replace('”', '"')
        .replace('’', '\'')

    private fun wrap(text: String, width: Int): List<String> {
        if (text.isBlank()) return listOf("")
        val result = mutableListOf<String>()
        var remaining = text
        while (remaining.length > width) {
            val split = remaining.lastIndexOf(' ', width).takeIf { it > 0 } ?: width
            result += remaining.substring(0, split)
            remaining = remaining.substring(split).trimStart()
        }
        result += remaining
        return result
    }

    private fun center(text: String, width: Int): String {
        val value = text.take(width)
        return " ".repeat(((width - value.length) / 2).coerceAtLeast(0)) + value
    }
}

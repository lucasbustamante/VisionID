package com.example.laranjinhaqrwebview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import com.newland.nsdk.core.api.common.ModuleType
import com.newland.nsdk.core.api.internal.printer.Printer
import com.newland.nsdk.core.api.internal.printer.PrinterStatus
import com.newland.nsdk.core.api.internal.printer.PrintingResultListener
import com.newland.nsdk.core.internal.NSDKModuleManagerImpl
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.max

/** Impressao da familia N960 pela API NSDK, sem permissao privilegiada Newland. */
internal object NewlandThermalPrinter {
    private const val BACKEND = "Newland NSDK 2.8.0"
    private const val PAPER_WIDTH_PX = 384
    private const val HORIZONTAL_MARGIN_PX = 10f
    private const val PAGE_CONTENT_HEIGHT_PX = 720
    private const val BOTTOM_FEED_PX = 80
    private const val CALLBACK_TIMEOUT_SECONDS = 60L
    private const val READY_TIMEOUT_MS = 5_000L
    private const val RESULT_SUCCESS = 0
    private const val RESULT_BUSY = 8

    private val sdkLock = Any()

    @Volatile
    private var cachedPrinter: Printer? = null

    fun print(context: Context, text: String): PrinterAttempt {
        unavailableAttempt()?.let { return it }
        return synchronized(sdkLock) {
            execute(context, "comprovante") { printer, details ->
                val pages = renderTextPages(text)
                try {
                    pages.forEachIndexed { index, page ->
                        printImage(printer, page, "pagina ${index + 1}/${pages.size}", details)
                    }
                } finally {
                    pages.forEach(Bitmap::recycle)
                }
            }
        }
    }

    fun printBitmap(context: Context, bitmap: Bitmap): PrinterAttempt {
        unavailableAttempt()?.let { return it }
        return synchronized(sdkLock) {
            execute(context, "bitmap ${bitmap.width}x${bitmap.height}") { printer, details ->
                val printable = withBottomFeed(bitmap)
                try {
                    printImage(printer, printable, "imagem", details)
                } finally {
                    printable.recycle()
                }
            }
        }
    }

    private inline fun execute(
        context: Context,
        job: String,
        operation: (Printer, MutableList<String>) -> Unit
    ): PrinterAttempt {
        val details = mutableListOf<String>()
        return try {
            val printer = printer(context.applicationContext, details)
            operation(printer, details)
            PrinterAttempt(
                available = true,
                success = true,
                backend = BACKEND,
                detail = "$job concluido. ${details.joinToString(" | ").take(1_500)}"
            )
        } catch (error: Throwable) {
            details += rootCause(error)
            PrinterAttempt(
                available = true,
                success = false,
                backend = BACKEND,
                detail = "$job falhou: ${details.joinToString(" | ").take(2_200)}"
            )
        }
    }

    private fun printer(context: Context, details: MutableList<String>): Printer {
        cachedPrinter?.let {
            details += "modulo=${it.javaClass.name} (cache)"
            return it
        }

        val manager = NSDKModuleManagerImpl.getInstance()
        manager.init(context)
        val module = manager.getModule(ModuleType.PRINTER)
            ?: throw IllegalStateException("O firmware nao disponibilizou o modulo PRINTER do NSDK.")
        val printer = module as? Printer
            ?: throw IllegalStateException(
                "PRINTER retornou ${module.javaClass.name}, esperado ${Printer::class.java.name}."
            )
        cachedPrinter = printer
        details += "modulo=${printer.javaClass.name}"
        return printer
    }

    private fun printImage(
        printer: Printer,
        bitmap: Bitmap,
        label: String,
        details: MutableList<String>
    ) {
        awaitReady(printer, details)

        val encoded = ByteArrayOutputStream().use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw IllegalStateException("Nao foi possivel codificar $label em PNG.")
            }
            output.toByteArray()
        }
        details += "$label=${bitmap.width}x${bitmap.height}/${encoded.size} bytes"

        val result = AsyncPrintResult()
        printer.printImage(
            encoded,
            0,
            bitmap.width,
            bitmap.height,
            PrintingResultListener(result::onEvent)
        )
        result.await(label, details)
    }

    private fun awaitReady(printer: Printer, details: MutableList<String>) {
        val deadline = SystemClock.elapsedRealtime() + READY_TIMEOUT_MS
        var status = printer.status
        while (status == PrinterStatus.BUSY && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(150)
            status = printer.status
        }
        details += "status=${status.name}(${status.code})"
        if (status != PrinterStatus.OK) {
            throw IllegalStateException("Impressora Newland indisponivel: ${statusMessage(status)}")
        }
    }

    private fun renderTextPages(text: String): List<Bitmap> {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.MONOSPACE
            textSize = 20f
        }
        val usableWidth = PAPER_WIDTH_PX - (HORIZONTAL_MARGIN_PX * 2)
        while (paint.measureText("M".repeat(32)) > usableWidth && paint.textSize > 16f) {
            paint.textSize -= 1f
        }

        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split('\n').flatMap { wrapLine(it, paint, usableWidth) }
            .ifEmpty { listOf("") }
        val lineHeight = max(1, ceil(paint.fontSpacing.toDouble()).toInt())
        val linesPerPage = max(1, PAGE_CONTENT_HEIGHT_PX / lineHeight)

        return lines.chunked(linesPerPage).mapIndexed { index, pageLines ->
            val finalPage = index == (lines.size - 1) / linesPerPage
            val contentHeight = max(lineHeight, pageLines.size * lineHeight)
            val height = contentHeight + if (finalPage) BOTTOM_FEED_PX else 0
            Bitmap.createBitmap(PAPER_WIDTH_PX, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                var baseline = -paint.fontMetrics.top
                pageLines.forEach { line ->
                    canvas.drawText(line, HORIZONTAL_MARGIN_PX, baseline, paint)
                    baseline += lineHeight
                }
            }
        }
    }

    private fun wrapLine(line: String, paint: Paint, maxWidth: Float): List<String> {
        if (line.isEmpty()) return listOf("")
        val result = mutableListOf<String>()
        var remaining = line
        while (remaining.isNotEmpty()) {
            if (paint.measureText(remaining) <= maxWidth) {
                result += remaining
                break
            }

            var low = 1
            var high = remaining.length
            while (low < high) {
                val middle = (low + high + 1) / 2
                if (paint.measureText(remaining, 0, middle) <= maxWidth) low = middle else high = middle - 1
            }
            val fitting = max(1, low)
            val whitespace = remaining.lastIndexOf(' ', fitting - 1)
            val cut = if (whitespace > 0) whitespace else fitting
            result += remaining.substring(0, cut).trimEnd()
            remaining = remaining.substring(if (whitespace > 0) cut + 1 else cut).trimStart()
        }
        return result
    }

    private fun withBottomFeed(source: Bitmap): Bitmap =
        Bitmap.createBitmap(PAPER_WIDTH_PX, source.height + BOTTOM_FEED_PX, Bitmap.Config.ARGB_8888)
            .also { bitmap ->
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                val left = ((PAPER_WIDTH_PX - source.width) / 2f).coerceAtLeast(0f)
                canvas.drawBitmap(source, left, 0f, null)
            }

    private fun unavailableAttempt(): PrinterAttempt? {
        val identity = listOf(
            Build.MANUFACTURER,
            Build.BRAND,
            Build.MODEL,
            Build.DEVICE,
            Build.PRODUCT
        ).joinToString(" ").uppercase()
        val isNewland = identity.contains("NEWLAND") ||
            identity.contains("N960") || identity.contains("N950") || identity.contains("N910")
        return if (isNewland) {
            null
        } else {
            PrinterAttempt(
                available = false,
                success = false,
                backend = BACKEND,
                detail = "Terminal nao identificado como Newland; adaptador NSDK nao inicializado."
            )
        }
    }

    private fun statusMessage(status: PrinterStatus): String = when (status) {
        PrinterStatus.NO_PAPER -> "sem papel"
        PrinterStatus.OVERHEAT -> "superaquecida"
        PrinterStatus.VOL_ERR -> "tensao inadequada"
        PrinterStatus.BUSY -> "ocupada"
        PrinterStatus.BAD -> "falha do mecanismo"
        else -> status.name
    }

    private fun resultMessage(code: Int): String = when (code) {
        -1 -> "falha generica"
        -6 -> "parametro invalido"
        RESULT_BUSY -> "ocupada"
        2 -> "sem papel"
        4 -> "superaquecida"
        112 -> "tensao inadequada"
        512 -> "falha do cortador"
        1024 -> "modulo encerrado"
        2048 -> "falha do mecanismo"
        else -> "codigo $code"
    }

    private class AsyncPrintResult {
        private val completed = CountDownLatch(1)
        private val resultCode = AtomicInteger(Int.MIN_VALUE)

        fun onEvent(code: Int) {
            if (code == RESULT_BUSY) return
            resultCode.compareAndSet(Int.MIN_VALUE, code)
            completed.countDown()
        }

        fun await(label: String, details: MutableList<String>) {
            if (!completed.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw IllegalStateException(
                    "$label nao confirmou a impressao em $CALLBACK_TIMEOUT_SECONDS segundos."
                )
            }
            val code = resultCode.get()
            if (code != RESULT_SUCCESS) {
                throw IllegalStateException("$label retornou ${resultMessage(code)}.")
            }
            details += "$label callback=sucesso"
        }
    }

    private fun rootCause(throwable: Throwable): String {
        var current = throwable
        while (current.cause != null && current.cause !== current) current = current.cause!!
        return "${current.javaClass.simpleName}: ${current.message.orEmpty()}"
    }
}

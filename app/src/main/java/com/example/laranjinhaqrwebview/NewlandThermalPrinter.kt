package com.example.laranjinhaqrwebview

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.newland.sdk.me.ConnUtils
import com.newland.sdk.mtype.Device
import com.newland.sdk.mtype.ModuleType
import com.newland.sdk.module.printer.Alignment
import com.newland.sdk.module.printer.ErrorCode
import com.newland.sdk.module.printer.FontSize
import com.newland.sdk.module.printer.ImageFormat
import com.newland.sdk.module.printer.PrintListener
import com.newland.sdk.module.printer.PrinterModule
import com.newland.sdk.module.printer.TextFormat
import com.newland.sdk.module.printerPro.NAlignment
import com.newland.sdk.module.printerPro.NImageFormat
import com.newland.sdk.module.printerPro.NPrintErrorCode
import com.newland.sdk.module.printerPro.NPrintListener
import com.newland.sdk.module.printerPro.NPrinterModule
import com.newland.sdk.module.printerPro.NTextFormat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Impressao direta pela API oficial empacotada no MESDK Newland 3.10.46. */
internal object NewlandThermalPrinter {
    private const val BACKEND = "Newland MESDK 3.10.46"
    private const val CALLBACK_TIMEOUT_SECONDS = 45L
    private const val READY_TIMEOUT_MS = 5_000L
    private const val PAPER_FEED_LINES = 4
    private val sdkLock = Any()

    fun print(context: Context, text: String): PrinterAttempt = synchronized(sdkLock) {
        execute(context, "comprovante") { device, details ->
            val standard = module<PrinterModule>(device, ModuleType.PRINTER, details)
            if (standard != null) {
                // O exemplo publico funcional da Newland usa o modulo de script PRINTER.
                printTextLegacy(context.applicationContext, standard, text, details)
                "PRINTER"
            } else {
                val pro = module<NPrinterModule>(device, ModuleType.PRINTER_PRO, details)
                    ?: throw IllegalStateException(
                        "O terminal nao disponibilizou PRINTER nem PRINTER_PRO."
                    )
                printTextPro(pro, text, details)
                "PRINTER_PRO"
            }
        }
    }

    fun printBitmap(context: Context, bitmap: Bitmap): PrinterAttempt = synchronized(sdkLock) {
        execute(context, "bitmap ${bitmap.width}x${bitmap.height}") { device, details ->
            val standard = module<PrinterModule>(device, ModuleType.PRINTER, details)
            if (standard != null) {
                printBitmapLegacy(context.applicationContext, standard, bitmap, details)
                "PRINTER"
            } else {
                val pro = module<NPrinterModule>(device, ModuleType.PRINTER_PRO, details)
                    ?: throw IllegalStateException(
                        "O terminal nao disponibilizou PRINTER nem PRINTER_PRO."
                    )
                printBitmapPro(pro, bitmap, details)
                "PRINTER_PRO"
            }
        }
    }

    private inline fun execute(
        context: Context,
        job: String,
        operation: (Device, MutableList<String>) -> String
    ): PrinterAttempt {
        val details = mutableListOf<String>()
        return try {
            val device = connectedDevice(context.applicationContext, details)
            val module = operation(device, details)
            PrinterAttempt(
                available = true,
                success = true,
                backend = BACKEND,
                detail = "$job concluido via $module. ${details.joinToString(" | ").take(1_500)}"
            )
        } catch (error: Throwable) {
            val cause = rootCause(error)
            details += cause
            PrinterAttempt(
                available = true,
                success = false,
                backend = BACKEND,
                detail = "$job falhou: ${details.joinToString(" | ").take(2_200)}"
            )
        }
    }

    private fun connectedDevice(context: Context, details: MutableList<String>): Device {
        val manager = ConnUtils.getDeviceManager()
        details += "sdk=${runCatching { manager.sdkVersion }.getOrNull() ?: "3.10.46"}"
        details += "estado-inicial=${runCatching { manager.deviceConnState }.getOrNull()}"

        var device = runCatching { manager.device }.getOrNull()
        val alive = device?.let { runCatching { it.isAlive }.getOrDefault(false) } == true
        if (!alive) {
            // A ordem e obrigatoria no MESDK: init(Context), connect(), getDevice().
            manager.init(context)
            val connected = manager.connect()
            details += "connect=$connected"
            device = manager.device
        }

        val connectedDevice = device
            ?: throw IllegalStateException("DeviceManager conectou sem fornecer o Device integrado.")
        if (!runCatching { connectedDevice.isAlive }.getOrDefault(true)) {
            throw IllegalStateException("O Device Newland foi criado, mas nao esta ativo.")
        }

        val supported = runCatching {
            connectedDevice.supportStandardModule.joinToString(",") { it.name }
        }.getOrNull()
        if (!supported.isNullOrBlank()) details += "modulos=$supported"
        details += "estado-final=${runCatching { manager.deviceConnState }.getOrNull()}"
        return connectedDevice
    }

    private inline fun <reified T> module(
        device: Device,
        type: ModuleType,
        details: MutableList<String>
    ): T? {
        val supported = runCatching { device.supportStandardModule.toSet() }.getOrNull()
        if (!supported.isNullOrEmpty() && type !in supported) {
            details += "$type nao anunciado pelo firmware"
            return null
        }

        val value = runCatching { device.getStandardModule(type) }
            .onFailure { details += "$type indisponivel: ${rootCause(it)}" }
            .getOrNull()
            ?: return null
        if (value !is T) {
            details += "$type retornou ${value.javaClass.name}, esperado ${T::class.java.name}"
            return null
        }
        details += "$type=${value.javaClass.name}"
        return value
    }

    private fun printTextPro(
        printer: NPrinterModule,
        text: String,
        details: MutableList<String>
    ) {
        awaitReady("PRINTER_PRO", details) { printer.status.name }
        val format = NTextFormat.Builder()
            .content(text)
            .fontSize(24)
            .alignment(NAlignment.LEFT)
            .marginBottom(0)
            .create()
        printer.addText(format)
        printer.addPaperFeed(PAPER_FEED_LINES)

        val result = AsyncPrintResult()
        printer.startPrint(object : NPrintListener {
            override fun onSuccess() = result.success()

            override fun onError(errorCode: NPrintErrorCode?, message: String?) {
                result.failure("${errorCode?.name ?: "FAILED"}: ${message.orEmpty()}")
            }
        })
        result.await("PRINTER_PRO", details)
    }

    private fun printBitmapPro(
        printer: NPrinterModule,
        bitmap: Bitmap,
        details: MutableList<String>
    ) {
        awaitReady("PRINTER_PRO", details) { printer.status.name }
        val format = NImageFormat.Builder()
            .bitmap(bitmap)
            .width(bitmap.width)
            .height(bitmap.height)
            .alignment(NAlignment.CENTER)
            .create()
        printer.addImage(format)
        printer.addPaperFeed(PAPER_FEED_LINES)

        val result = AsyncPrintResult()
        printer.startPrint(object : NPrintListener {
            override fun onSuccess() = result.success()

            override fun onError(errorCode: NPrintErrorCode?, message: String?) {
                result.failure("${errorCode?.name ?: "FAILED"}: ${message.orEmpty()}")
            }
        })
        result.await("PRINTER_PRO bitmap", details)
    }

    private fun printTextLegacy(
        context: Context,
        printer: PrinterModule,
        text: String,
        details: MutableList<String>
    ) {
        awaitReady("PRINTER", details) { printer.status.name }
        val script = printer.getPrintScriptUtil(context)
        script.reset()
        val format = TextFormat().apply {
            fontSize = FontSize.NORMAL
            alignment = Alignment.LEFT
            isLinefeed = true
        }
        script.addText(format, text)
        script.addPaperFeed(PAPER_FEED_LINES)

        val result = AsyncPrintResult()
        script.print(object : PrintListener {
            override fun onSuccess() = result.success()

            override fun onError(errorCode: ErrorCode?, message: String?) {
                result.failure("${errorCode?.name ?: "FAILED"}: ${message.orEmpty()}")
            }
        })
        result.await("PRINTER", details)
    }

    private fun printBitmapLegacy(
        context: Context,
        printer: PrinterModule,
        bitmap: Bitmap,
        details: MutableList<String>
    ) {
        awaitReady("PRINTER", details) { printer.status.name }
        val script = printer.getPrintScriptUtil(context)
        script.reset()
        val format = ImageFormat().apply {
            width = bitmap.width
            height = bitmap.height
            offset = 0
            alignment = Alignment.CENTER
        }
        script.addImage(format, bitmap)
        script.addPaperFeed(PAPER_FEED_LINES)

        val result = AsyncPrintResult()
        script.print(object : PrintListener {
            override fun onSuccess() = result.success()

            override fun onError(errorCode: ErrorCode?, message: String?) {
                result.failure("${errorCode?.name ?: "FAILED"}: ${message.orEmpty()}")
            }
        })
        result.await("PRINTER bitmap", details)
    }

    private fun awaitReady(
        module: String,
        details: MutableList<String>,
        status: () -> String
    ) {
        val deadline = SystemClock.elapsedRealtime() + READY_TIMEOUT_MS
        var current = status()
        while (current.equals("BUSY", true) && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(150)
            current = status()
        }
        details += "$module status=$current"
        if (!current.equals("NORMAL", true)) {
            throw IllegalStateException("Impressora Newland indisponivel: ${statusMessage(current)}")
        }
    }

    private fun statusMessage(status: String): String = when (status.uppercase()) {
        "OUTOF_PAPER" -> "sem papel"
        "OVER_HEAT" -> "superaquecida"
        "LOW_VOLTAGE" -> "tensao baixa"
        "BUSY" -> "ocupada"
        "DESTROYED" -> "modulo encerrado"
        "PPSERR" -> "falha do mecanismo de impressao"
        "CUTTER_ERROR" -> "falha do cortador"
        else -> status
    }

    private class AsyncPrintResult {
        private val completed = CountDownLatch(1)
        private val error = AtomicReference<String?>()

        fun success() {
            completed.countDown()
        }

        fun failure(message: String) {
            error.compareAndSet(null, message)
            completed.countDown()
        }

        fun await(module: String, details: MutableList<String>) {
            if (!completed.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw IllegalStateException(
                    "$module nao confirmou a impressao em $CALLBACK_TIMEOUT_SECONDS segundos."
                )
            }
            error.get()?.let { throw IllegalStateException("$module retornou $it") }
            details += "$module callback=sucesso"
        }
    }

    private fun rootCause(throwable: Throwable): String {
        var current = throwable
        while (current.cause != null && current.cause !== current) current = current.cause!!
        return "${current.javaClass.simpleName}: ${current.message.orEmpty()}"
    }
}

package com.example.laranjinhaqrwebview

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock
import com.xcheng.printerservice.IPrinterCallback
import com.xcheng.printerservice.IPrinterService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Impressora interna da família Positivo/XCheng (L400/L500).
 *
 * Alguns firmwares da L400 executam printerInit()/printerReset() de forma assíncrona
 * e não retornam onComplete() para o reset. Por isso, essas duas operações são
 * "fire-and-forget": a ausência do callback não é tratada como falha.
 */
internal object XchengAidlPrinter {
    private const val SERVICE_PACKAGE = "com.xcheng.printerservice"
    private const val SERVICE_CLASS = "com.xcheng.printerservice.PrinterService"
    private const val SERVICE_ACTION = "com.xcheng.printerservice.IPrinterService"
    private const val CONNECTION_TIMEOUT_SECONDS = 8L
    private const val CALLBACK_GRACE_MS = 1_800L
    private const val MAX_TEXT_CHUNK = 1_200

    fun print(context: Context, text: String): PrinterAttempt {
        val appContext = context.applicationContext
        val serviceRef = AtomicReference<IPrinterService?>()
        val connected = CountDownLatch(1)
        val connectionError = AtomicReference<String?>()

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder == null) {
                    connectionError.set("O serviço retornou IBinder nulo.")
                } else {
                    serviceRef.set(IPrinterService.Stub.asInterface(binder))
                }
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                serviceRef.set(null)
            }

            override fun onNullBinding(name: ComponentName?) {
                connectionError.set("O firmware retornou binder nulo para $name.")
                connected.countDown()
            }

            override fun onBindingDied(name: ComponentName?) {
                connectionError.set("A conexão com o serviço morreu: $name.")
                connected.countDown()
            }
        }

        val explicitIntent = Intent(SERVICE_ACTION).apply {
            component = ComponentName(SERVICE_PACKAGE, SERVICE_CLASS)
        }
        val implicitIntent = Intent(SERVICE_ACTION).setPackage(SERVICE_PACKAGE)

        val resolved = runCatching {
            appContext.packageManager.resolveService(implicitIntent, 0)?.serviceInfo
        }.getOrNull()

        val bindIntent = if (resolved != null) {
            Intent(SERVICE_ACTION).apply {
                component = ComponentName(resolved.packageName, resolved.name)
            }
        } else {
            explicitIntent
        }

        // Há firmwares em que o serviço precisa ser iniciado antes do bind.
        // Se o sistema não permitir startService(), o bind ainda é tentado.
        val startResult = runCatching {
            appContext.startService(bindIntent)
            "startService=ok"
        }.getOrElse { "startService=${it.javaClass.simpleName}:${it.message.orEmpty()}" }

        var bound = false
        return try {
            bound = runCatching {
                appContext.bindService(bindIntent, connection, Context.BIND_AUTO_CREATE)
            }.getOrDefault(false)

            if (!bound && bindIntent.component != null) {
                // Alguns firmwares aceitam somente action + package.
                bound = runCatching {
                    appContext.bindService(implicitIntent, connection, Context.BIND_AUTO_CREATE)
                }.getOrDefault(false)
            }

            if (!bound) {
                return PrinterAttempt(
                    available = resolved != null,
                    success = false,
                    backend = "Positivo/XCheng AIDL",
                    detail = "bindService retornou false; $startResult; serviço=${resolved?.name ?: SERVICE_CLASS}."
                )
            }

            if (!connected.await(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return PrinterAttempt(
                    true,
                    false,
                    "Positivo/XCheng AIDL",
                    "Tempo esgotado ao conectar; $startResult."
                )
            }

            val service = serviceRef.get()
                ?: return PrinterAttempt(
                    true,
                    false,
                    "Positivo/XCheng AIDL",
                    connectionError.get() ?: "Binder indisponível."
                )

            val callbackState = CallbackState()
            val callback = callbackState.callback

            // Não aguardamos callbacks de init/reset. Na L400 há versões do
            // PrinterService que executam o comando, mas nunca chamam onComplete().
            runCatching { service.printerInit(callback) }
                .onFailure { callbackState.recordLocal("printerInit", it) }
            SystemClock.sleep(120)

            runCatching { service.printerReset(callback) }
                .onFailure { callbackState.recordLocal("printerReset", it) }
            SystemClock.sleep(250)

            callbackState.throwIfError()

            val chunks = splitForPrinter(text, MAX_TEXT_CHUNK)
            chunks.forEachIndexed { index, chunk ->
                runCatching { service.printText(chunk, callback) }
                    .getOrElse {
                        throw IllegalStateException(
                            "Falha ao enviar bloco ${index + 1}/${chunks.size}: " +
                                "${it.javaClass.simpleName}: ${it.message.orEmpty()}",
                            it
                        )
                    }
                // Evita sobrecarregar o buffer do serviço em comprovantes grandes.
                SystemClock.sleep(90)
                callbackState.throwIfError()
            }

            runCatching { service.printWrapPaper(4, callback) }
                .getOrElse {
                    throw IllegalStateException(
                        "Falha ao avançar papel: ${it.javaClass.simpleName}: ${it.message.orEmpty()}",
                        it
                    )
                }

            // Dá tempo para o serviço processar a fila. A impressão não é marcada
            // como falha só porque este firmware não envia onComplete().
            val deadline = SystemClock.elapsedRealtime() + CALLBACK_GRACE_MS
            while (SystemClock.elapsedRealtime() < deadline) {
                callbackState.throwIfError()
                if (callbackState.completions.get() > 0) break
                SystemClock.sleep(80)
            }
            callbackState.throwIfError()

            PrinterAttempt(
                available = true,
                success = true,
                backend = "Positivo/XCheng AIDL",
                detail = buildString {
                    append("Comandos aceitos por ")
                    append(resolved?.let { "${it.packageName}/${it.name}" }
                        ?: "$SERVICE_PACKAGE/$SERVICE_CLASS")
                    append("; blocos=${chunks.size}; callbacks=")
                    append(callbackState.completions.get())
                    append("; ")
                    append(startResult)
                    append(".")
                }
            )
        } catch (t: Throwable) {
            PrinterAttempt(
                true,
                false,
                "Positivo/XCheng AIDL",
                "${t.javaClass.simpleName}: ${t.message.orEmpty()}; $startResult"
            )
        } finally {
            if (bound) runCatching { appContext.unbindService(connection) }
        }
    }

    private fun splitForPrinter(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val result = mutableListOf<String>()
        val current = StringBuilder()

        text.lineSequence().forEach { line ->
            val withBreak = "$line\n"
            if (current.isNotEmpty() && current.length + withBreak.length > maxChars) {
                result += current.toString()
                current.clear()
            }

            if (withBreak.length <= maxChars) {
                current.append(withBreak)
            } else {
                var remaining = withBreak
                while (remaining.length > maxChars) {
                    result += remaining.substring(0, maxChars)
                    remaining = remaining.substring(maxChars)
                }
                current.append(remaining)
            }
        }

        if (current.isNotEmpty()) result += current.toString()
        return result.ifEmpty { listOf(text) }
    }

    private class CallbackState {
        private val remoteError = AtomicReference<String?>()
        val completions = AtomicInteger(0)

        val callback = object : IPrinterCallback.Stub() {
            override fun onException(code: Int, msg: String?) {
                remoteError.compareAndSet(null, "PrinterService retornou erro $code: ${msg.orEmpty()}")
            }

            override fun onLength(current: Long, total: Long) = Unit
            override fun onRealLength(realCurrent: Double, realTotal: Double) = Unit
            override fun onComplete() {
                completions.incrementAndGet()
            }
        }

        fun recordLocal(operation: String, throwable: Throwable) {
            remoteError.compareAndSet(
                null,
                "$operation: ${throwable.javaClass.simpleName}: ${throwable.message.orEmpty()}"
            )
        }

        fun throwIfError() {
            remoteError.get()?.let { throw IllegalStateException(it) }
        }
    }
}

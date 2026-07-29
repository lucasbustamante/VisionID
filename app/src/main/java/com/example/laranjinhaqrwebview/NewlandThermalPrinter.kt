package com.example.laranjinhaqrwebview

import android.content.Context
import android.os.SystemClock
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * Impressão para Newland N960K/N950/N910.
 *
 * O projeto inclui o MESDK 3.10.46. O acesso permanece por reflexão para
 * tolerar diferenças entre o SDK empacotado e versões presentes no firmware.
 */
internal object NewlandThermalPrinter {
    private data class LoaderEntry(val label: String, val loader: ClassLoader)

    fun print(context: Context, text: String): PrinterAttempt {
        val loaders = classLoaders(context)
        val details = mutableListOf<String>()

        val strategies = listOf<Pair<String, () -> Boolean?>>(
            "Newland MESDK" to { printMesdk(context, loaders, text, details) },
            "Newland NSDK/Manager" to { printManagerFamilies(context, loaders, text, details) }
        )

        var apiFound = false
        for ((name, strategy) in strategies) {
            val result = runCatching(strategy).getOrElse {
                details += "$name: ${rootCause(it)}"
                null
            }
            if (result != null) {
                apiFound = true
                if (result) {
                    return PrinterAttempt(
                        available = true,
                        success = true,
                        backend = name,
                        detail = "Comprovante aceito pela API Newland. ${details.joinToString(" | ").take(900)}"
                    )
                }
                details += "$name: API encontrada, mas não aceitou o comando."
            }
        }

        return PrinterAttempt(
            available = apiFound,
            success = false,
            backend = "Newland",
            detail = details.joinToString(" | ").ifBlank {
                "Nenhuma classe de impressão Newland foi localizada."
            }.take(1900)
        )
    }

    private fun printMesdk(
        context: Context,
        loaders: List<LoaderEntry>,
        text: String,
        details: MutableList<String>
    ): Boolean? {
        val connLocated = loadFirst(loaders, "com.newland.me.ConnUtils") ?: run {
            details += "MESDK: com.newland.me.ConnUtils ausente nos ${loaders.size} classloaders."
            return null
        }
        details += "MESDK carregado por ${connLocated.first}."

        val connUtils = connLocated.second
        val getDeviceManager = connUtils.methods.firstOrNull {
            Modifier.isStatic(it.modifiers) &&
                it.name == "getDeviceManager" &&
                it.parameterCount == 0
        } ?: run {
            details += "MESDK: getDeviceManager() ausente."
            return false
        }

        val deviceManager = getDeviceManager.invoke(null) ?: run {
            details += "MESDK: getDeviceManager() retornou null."
            return false
        }

        var device = callValue(deviceManager, listOf("getDevice"))
        if (device == null) {
            details += "MESDK: device inicialmente null; tentando connect()."
            runCatching { callOptional(deviceManager, listOf("connect")) }
                .onFailure { details += "connect(): ${rootCause(it)}" }

            val limit = SystemClock.elapsedRealtime() + 2_500L
            while (device == null && SystemClock.elapsedRealtime() < limit) {
                SystemClock.sleep(100)
                device = callValue(deviceManager, listOf("getDevice"))
            }
        }

        if (device == null) {
            // Alguns builds expõem uma inicialização simples com Context.
            runCatching {
                callOptional(deviceManager, listOf("init", "initialize"), context.applicationContext)
            }.onFailure { details += "init(Context): ${rootCause(it)}" }
            runCatching { callOptional(deviceManager, listOf("connect")) }
                .onFailure { details += "connect pós-init: ${rootCause(it)}" }
            device = callValue(deviceManager, listOf("getDevice"))
        }

        val actualDevice = device ?: run {
            details += "MESDK: o DeviceManager não disponibilizou o Device integrado."
            return false
        }

        val moduleLocated = loadFirst(
            loaders,
            "com.newland.mtype.ModuleType",
            "com.newland.me.ModuleType"
        ) ?: run {
            details += "MESDK: ModuleType ausente."
            return false
        }

        val printerType = moduleLocated.second.enumConstants?.firstOrNull {
            it.toString().equals("COMMON_PRINTER", true)
        } ?: moduleLocated.second.enumConstants?.firstOrNull {
            it.toString().contains("PRINTER", true)
        } ?: run {
            details += "MESDK: COMMON_PRINTER não encontrado."
            return false
        }

        val printer = callValue(actualDevice, listOf("getStandardModule"), printerType)
            ?: callValue(actualDevice, listOf("getModule", "getDeviceModule"), printerType)
            ?: run {
                details += "MESDK: módulo COMMON_PRINTER retornou null."
                return false
            }

        callOptional(printer, listOf("init", "initialize", "open"))
        val status = callValue(printer, listOf("getStatus", "getPrinterStatus", "status"))
        details += "MESDK: printer=${printer.javaClass.name}; status=${status ?: "não informado"}."

        val result = invokePrintMethod(printer, text)
        details += "MESDK: retorno=${result?.toString()?.take(180) ?: "void/null"}."
        return accepted(result)
    }

    private fun printManagerFamilies(
        context: Context,
        loaders: List<LoaderEntry>,
        text: String,
        details: MutableList<String>
    ): Boolean? {
        val located = loadFirst(
            loaders,
            "com.newland.nsdk.core.api.NSDKModuleManager",
            "com.newland.nsdk.core.api.NSDKManager",
            "com.newland.nsdk.core.api.NSDKModuleManagerImpl",
            "com.newland.sdk.module.printer.PrinterManager",
            "com.newland.sdk.printer.PrinterManager",
            "com.newland.payment.printer.PrinterManager",
            "com.newland.pospp.openapi.manager.NewlandPrinterManager",
            "com.newland.pospp.openapi.manager.PrinterManager"
        ) ?: return null

        details += "Manager ${located.second.name} carregado por ${located.first}."
        val manager = instance(located.second, context) ?: return false

        callOptional(manager, listOf("init", "initialize", "open"), context.applicationContext)
        callOptional(manager, listOf("init", "initialize", "open"))

        val printer = callValue(manager, listOf("getPrinter", "getPrinterManager")) ?: manager
        callOptional(printer, listOf("init", "initialize", "open", "reset"))
        val result = invokePrintMethod(printer, text)
        return accepted(result)
    }

    private fun invokePrintMethod(target: Any, text: String): Any? {
        val methods = target.javaClass.methods.toList()
        val textNames = setOf("printText", "printString", "printStr", "addText", "appendText", "print")

        methods.firstOrNull {
            it.name in textNames &&
                it.parameterTypes.contentEquals(arrayOf(String::class.java))
        }?.let { return it.invoke(target, text) }

        // Assinatura confirmada no MESDK:
        // PrinterResult print(String data, long timeout, TimeUnit unit)
        methods.firstOrNull {
            it.name == "print" &&
                it.parameterCount == 3 &&
                it.parameterTypes[0] == String::class.java &&
                TimeUnit::class.java.isAssignableFrom(it.parameterTypes[2])
        }?.let { method ->
            return method.invoke(
                target,
                text,
                numericTimeout(method.parameterTypes[1]),
                TimeUnit.SECONDS
            )
        }

        val bytes = text.toByteArray(
            runCatching { Charset.forName("GBK") }.getOrDefault(Charsets.UTF_8)
        )

        methods.firstOrNull {
            it.name in textNames &&
                it.parameterTypes.contentEquals(arrayOf(ByteArray::class.java))
        }?.let { return it.invoke(target, bytes) }

        methods.firstOrNull {
            it.name == "print" &&
                it.parameterCount == 3 &&
                it.parameterTypes[0] == ByteArray::class.java &&
                TimeUnit::class.java.isAssignableFrom(it.parameterTypes[2])
        }?.let { method ->
            return method.invoke(
                target,
                bytes,
                numericTimeout(method.parameterTypes[1]),
                TimeUnit.SECONDS
            )
        }

        throw NoSuchMethodException(
            "Nenhum método de texto compatível em ${target.javaClass.name}. Métodos: " +
                methods.filter { it.name.contains("print", true) }
                    .joinToString { "${it.name}/${it.parameterCount}" }
                    .take(600)
        )
    }

    private fun classLoaders(context: Context): List<LoaderEntry> {
        val result = linkedMapOf<String, ClassLoader>()
        fun add(label: String, loader: ClassLoader?) {
            if (loader != null && result.values.none { it === loader }) result[label] = loader
        }

        add("aplicativo", context.classLoader)
        add("thread", Thread.currentThread().contextClassLoader)
        add("NewlandThermalPrinter", NewlandThermalPrinter::class.java.classLoader)

        val packageNames = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getInstalledApplications(0)
                .map { it.packageName }
                .filter {
                    it.contains("newland", true) ||
                        it.contains("printer", true) ||
                        it.contains("mesdk", true) ||
                        it.contains("nsdk", true)
                }
                .take(40)
        }.getOrDefault(emptyList())

        packageNames.forEach { packageName ->
            runCatching {
                val packageContext = context.createPackageContext(
                    packageName,
                    Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
                )
                add("pacote:$packageName", packageContext.classLoader)
            }
        }

        return result.map { LoaderEntry(it.key, it.value) }
    }

    private fun loadFirst(
        loaders: List<LoaderEntry>,
        vararg names: String
    ): Pair<String, Class<*>>? {
        loaders.forEach { entry ->
            names.forEach { name ->
                runCatching { Class.forName(name, true, entry.loader) }
                    .getOrNull()
                    ?.let { return entry.label to it }
            }
        }
        return null
    }

    private fun numericTimeout(parameter: Class<*>): Any = when (parameter) {
        Int::class.javaPrimitiveType, Int::class.javaObjectType -> 30
        Short::class.javaPrimitiveType, Short::class.javaObjectType -> 30.toShort()
        Float::class.javaPrimitiveType, Float::class.javaObjectType -> 30f
        Double::class.javaPrimitiveType, Double::class.javaObjectType -> 30.0
        else -> 30L
    }

    private fun accepted(value: Any?): Boolean = when (value) {
        null -> true
        is Boolean -> value
        is Number -> value.toInt() >= 0
        else -> {
            val description = value.toString()
            !description.contains("FAIL", true) &&
                !description.contains("ERROR", true) &&
                !description.contains("EXCEPTION", true)
        }
    }

    private fun instance(clazz: Class<*>, context: Context): Any? {
        for (name in listOf("getInstance", "get", "instance", "newInstance")) {
            clazz.methods.filter {
                Modifier.isStatic(it.modifiers) && it.name == name
            }.forEach { method ->
                runCatching {
                    when (method.parameterCount) {
                        0 -> method.invoke(null)
                        1 -> if (Context::class.java.isAssignableFrom(method.parameterTypes[0])) {
                            method.invoke(null, context.applicationContext)
                        } else null
                        else -> null
                    }
                }.getOrNull()?.let { return it }
            }
        }

        return runCatching {
            clazz.getDeclaredConstructor(Context::class.java)
                .newInstance(context.applicationContext)
        }.recoverCatching {
            clazz.getDeclaredConstructor().newInstance()
        }.getOrNull()
    }

    private fun callOptional(target: Any, names: List<String>, vararg args: Any): Boolean {
        val method = compatibleMethod(target.javaClass.methods.toList(), names, args) ?: return false
        method.isAccessible = true
        method.invoke(target, *args)
        return true
    }

    private fun callValue(target: Any, names: List<String>, vararg args: Any): Any? {
        val method = compatibleMethod(target.javaClass.methods.toList(), names, args) ?: return null
        method.isAccessible = true
        return method.invoke(target, *args)
    }

    private fun compatibleMethod(
        methods: List<Method>,
        names: List<String>,
        args: Array<out Any>
    ): Method? = methods.firstOrNull { method ->
        method.name in names &&
            method.parameterCount == args.size &&
            method.parameterTypes.indices.all { index ->
                compatible(method.parameterTypes[index], args[index].javaClass)
            }
    }

    private fun compatible(parameter: Class<*>, value: Class<*>): Boolean {
        if (parameter.isAssignableFrom(value)) return true
        return when (parameter) {
            Int::class.javaPrimitiveType -> value == Int::class.javaObjectType
            Long::class.javaPrimitiveType ->
                value == Long::class.javaObjectType || value == Int::class.javaObjectType
            Boolean::class.javaPrimitiveType -> value == Boolean::class.javaObjectType
            else -> false
        }
    }

    private fun rootCause(throwable: Throwable): String {
        var current = throwable
        while (current.cause != null && current.cause !== current) current = current.cause!!
        return "${current.javaClass.simpleName}: ${current.message.orEmpty()}"
    }
}

package com.example.laranjinhaqrwebview

import android.content.Context
import android.content.Intent
import android.os.Build

internal object PrinterDiagnostics {
    fun collect(context: Context): Map<String, String> {
        val pm = context.packageManager
        val xchengIntent = Intent("com.xcheng.printerservice.IPrinterService")
            .setPackage("com.xcheng.printerservice")
        val xcheng = pm.resolveService(xchengIntent, 0)?.serviceInfo

        val installedMatches = runCatching {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
                .mapNotNull { it.packageName }
                .filter {
                    it.contains("printer", true) ||
                        it.contains("newland", true) ||
                        it.contains("xcheng", true) ||
                        it.contains("positivo", true) ||
                        it.contains("mesdk", true) ||
                        it.contains("nsdk", true)
                }
                .take(40)
                .joinToString(",")
        }.getOrDefault("não permitido pelo firmware")

        val nsdkManagerClass = runCatching {
            Class.forName("com.newland.nsdk.core.internal.NSDKModuleManagerImpl")
                .protectionDomain?.codeSource?.location?.toString()
                ?: "presente"
        }.getOrDefault("ausente")

        val nsdkPrinterClass = runCatching {
            Class.forName("com.newland.nsdk.core.api.internal.printer.Printer").name
        }.getOrDefault("ausente")

        return linkedMapOf(
            "manufacturer" to Build.MANUFACTURER,
            "brand" to Build.BRAND,
            "model" to Build.MODEL,
            "device" to Build.DEVICE,
            "product" to Build.PRODUCT,
            "android" to Build.VERSION.RELEASE,
            "sdk" to Build.VERSION.SDK_INT.toString(),
            "xchengService" to (xcheng?.let { "${it.packageName}/${it.name}" } ?: "ausente"),
            "newlandNsdkManager" to nsdkManagerClass,
            "newlandNsdkPrinter" to nsdkPrinterClass,
            "printerPackages" to installedMatches
        )
    }
}

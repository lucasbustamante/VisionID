package com.example.laranjinhaqrwebview

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

internal data class AppLogEntry(
    val id: String,
    val timestamp: Long,
    val level: String,
    val category: String,
    val event: String,
    val message: String,
    val details: String
)

internal object AppLog {
    private const val TAG = "VisionIDLog"
    private const val FILE_NAME = "visionid_logs.jsonl"
    private const val MAX_FILE_BYTES = 2_000_000L
    private const val MAX_ENTRIES = 3000
    private val lock = Any()

    fun info(context: Context, category: String, event: String, message: String, details: Map<String, Any?> = emptyMap()) {
        write(context, "INFO", category, event, message, details)
    }

    fun warning(context: Context, category: String, event: String, message: String, details: Map<String, Any?> = emptyMap()) {
        write(context, "WARN", category, event, message, details)
    }

    fun error(context: Context, category: String, event: String, message: String, throwable: Throwable? = null, details: Map<String, Any?> = emptyMap()) {
        val enriched = details.toMutableMap()
        throwable?.let {
            enriched["exception"] = it.javaClass.name
            enriched["exceptionMessage"] = it.message.orEmpty()
            enriched["stackTrace"] = Log.getStackTraceString(it)
        }
        write(context, "ERROR", category, event, message, enriched)
    }

    fun readAll(context: Context): List<AppLogEntry> = synchronized(lock) {
        val file = logFile(context)
        if (!file.exists()) return@synchronized emptyList()
        file.useLines { lines ->
            lines.mapNotNull(::parseLine).toList().sortedByDescending { it.timestamp }
        }
    }

    fun clear(context: Context) = synchronized(lock) {
        runCatching { logFile(context).delete() }
    }

    fun findById(context: Context, id: String): AppLogEntry? = readAll(context).firstOrNull { it.id == id }

    fun formatDateTime(timestamp: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS", Locale("pt", "BR")).format(Date(timestamp))

    fun formatHour(timestamp: Long): String =
        SimpleDateFormat("dd/MM/yyyy 'às' HH:00", Locale("pt", "BR")).format(Date(timestamp))

    fun safeUrl(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return runCatching {
            val uri = android.net.Uri.parse(raw)
            uri.buildUpon().clearQuery().fragment(null).build().toString()
        }.getOrDefault("URL não disponível")
    }

    fun deviceSnapshot(context: Context): Map<String, Any?> = mapOf(
        "appVersion" to BuildConfig.VERSION_NAME,
        "versionCode" to BuildConfig.VERSION_CODE,
        "manufacturer" to Build.MANUFACTURER,
        "model" to Build.MODEL,
        "device" to Build.DEVICE,
        "androidVersion" to Build.VERSION.RELEASE,
        "sdk" to Build.VERSION.SDK_INT,
        "webViewPackage" to runCatching { android.webkit.WebView.getCurrentWebViewPackage()?.versionName }.getOrNull(),
        "network" to networkType(context)
    )

    private fun write(
        context: Context,
        level: String,
        category: String,
        event: String,
        message: String,
        details: Map<String, Any?>
    ) = synchronized(lock) {
        runCatching {
            val file = logFile(context)
            rotateIfNeeded(file)
            val json = JSONObject().apply {
                put("id", UUID.randomUUID().toString())
                put("timestamp", System.currentTimeMillis())
                put("level", level)
                put("category", category)
                put("event", event)
                put("message", message)
                put("details", JSONObject(details))
            }
            file.appendText(json.toString() + "\n", Charsets.UTF_8)
        }.onFailure { Log.e(TAG, "Falha ao gravar log", it) }
    }

    private fun parseLine(line: String): AppLogEntry? = runCatching {
        val json = JSONObject(line)
        AppLogEntry(
            id = json.optString("id"),
            timestamp = json.optLong("timestamp"),
            level = json.optString("level"),
            category = json.optString("category"),
            event = json.optString("event"),
            message = json.optString("message"),
            details = json.optJSONObject("details")?.toString(2).orEmpty()
        )
    }.getOrNull()

    private fun logFile(context: Context): File = File(context.filesDir, FILE_NAME)

    private fun rotateIfNeeded(file: File) {
        if (!file.exists() || file.length() < MAX_FILE_BYTES) return
        val retained = file.readLines(Charsets.UTF_8).takeLast(MAX_ENTRIES / 2)
        file.writeText(retained.joinToString(separator = "\n", postfix = if (retained.isEmpty()) "" else "\n"), Charsets.UTF_8)
    }

    private fun networkType(context: Context): String {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "indisponível"
        val network = manager.activeNetwork ?: return "sem conexão"
        val capabilities = manager.getNetworkCapabilities(network) ?: return "desconhecida"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Celular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Outro"
        }
    }
}

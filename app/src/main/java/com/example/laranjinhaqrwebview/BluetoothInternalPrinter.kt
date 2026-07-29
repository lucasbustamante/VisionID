package com.example.laranjinhaqrwebview

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Fallback documentado pelos firmwares XCheng: a impressora interna aparece
 * como um dispositivo Bluetooth virtual chamado BluetoothPrinter.
 */
internal object BluetoothInternalPrinter {
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun print(context: Context, text: String): PrinterAttempt = send(context, "comprovante") { output ->
        output.write(byteArrayOf(0x1B, 0x40)) // ESC @: inicializa
        output.write(text.toByteArray(Charsets.US_ASCII))
        output.write(byteArrayOf(0x0A, 0x0A, 0x0A, 0x0A))
    }

    fun printBitmap(context: Context, bitmap: Bitmap): PrinterAttempt = send(context, "fotografia") { output ->
        output.write(byteArrayOf(0x1B, 0x40)) // ESC @
        output.write(byteArrayOf(0x1B, 0x61, 0x01)) // ESC a 1: centraliza
        output.write(toEscPosRaster(bitmap))
        output.write(byteArrayOf(0x0A, 0x0A, 0x0A, 0x0A))
    }

    @SuppressLint("MissingPermission")
    private fun send(
        context: Context,
        contentDescription: String,
        writer: (OutputStream) -> Unit
    ): PrinterAttempt {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            return PrinterAttempt(
                available = true,
                success = false,
                backend = "BluetoothPrinter interno",
                detail = "Permissão BLUETOOTH_CONNECT ainda não concedida."
            )
        }

        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: return PrinterAttempt(false, false, "BluetoothPrinter interno", "Bluetooth não disponível.")
        if (!adapter.isEnabled) {
            return PrinterAttempt(true, false, "BluetoothPrinter interno", "Bluetooth está desligado.")
        }

        val device = runCatching {
            adapter.bondedDevices.firstOrNull { bonded ->
                val name = bonded.name.orEmpty()
                name.equals("BluetoothPrinter", true) ||
                    name.contains("XCHENG", true) ||
                    name.contains("POSPrinter", true)
            }
        }.getOrElse {
            return PrinterAttempt(
                true,
                false,
                "BluetoothPrinter interno",
                "Falha ao consultar dispositivos pareados: ${it.message.orEmpty()}"
            )
        } ?: return PrinterAttempt(
            false,
            false,
            "BluetoothPrinter interno",
            "Dispositivo virtual BluetoothPrinter não encontrado."
        )

        return try {
            adapter.cancelDiscovery()
            device.createRfcommSocketToServiceRecord(sppUuid).use { socket ->
                socket.connect()
                socket.outputStream.use { output ->
                    writer(output)
                    output.flush()
                }
            }
            PrinterAttempt(
                true,
                true,
                "BluetoothPrinter interno",
                "${contentDescription.replaceFirstChar { it.uppercase() }} enviado por ESC/POS ao dispositivo ${device.name}."
            )
        } catch (error: Throwable) {
            PrinterAttempt(
                true,
                false,
                "BluetoothPrinter interno",
                "${error.javaClass.simpleName}: ${error.message.orEmpty()}"
            )
        }
    }

    /** Converte Bitmap preto/branco no comando GS v 0 do protocolo ESC/POS. */
    private fun toEscPosRaster(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val widthBytes = (width + 7) / 8
        val result = ByteArrayOutputStream(8 + widthBytes * height)

        result.write(0x1D)
        result.write(0x76)
        result.write(0x30)
        result.write(0x00)
        result.write(widthBytes and 0xFF)
        result.write((widthBytes shr 8) and 0xFF)
        result.write(height and 0xFF)
        result.write((height shr 8) and 0xFF)

        for (y in 0 until height) {
            for (byteX in 0 until widthBytes) {
                var value = 0
                for (bit in 0 until 8) {
                    val x = byteX * 8 + bit
                    if (x < width) {
                        val pixel = bitmap.getPixel(x, y)
                        val luminance = (
                            Color.red(pixel) * 299 +
                                Color.green(pixel) * 587 +
                                Color.blue(pixel) * 114
                            ) / 1000
                        if (luminance < 128) value = value or (0x80 shr bit)
                    }
                }
                result.write(value)
            }
        }
        return result.toByteArray()
    }
}

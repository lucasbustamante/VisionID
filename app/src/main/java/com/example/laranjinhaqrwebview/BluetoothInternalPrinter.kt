package com.example.laranjinhaqrwebview

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.util.UUID

/**
 * Fallback documentado pelos firmwares XCheng: a impressora interna aparece
 * como um dispositivo Bluetooth virtual chamado BluetoothPrinter.
 */
internal object BluetoothInternalPrinter {
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    fun print(context: Context, text: String): PrinterAttempt {
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
            return PrinterAttempt(true, false, "BluetoothPrinter interno", "Falha ao consultar dispositivos pareados: ${it.message.orEmpty()}")
        } ?: return PrinterAttempt(false, false, "BluetoothPrinter interno", "Dispositivo virtual BluetoothPrinter não encontrado.")

        return try {
            adapter.cancelDiscovery()
            device.createRfcommSocketToServiceRecord(sppUuid).use { socket ->
                socket.connect()
                socket.outputStream.use { output ->
                    output.write(byteArrayOf(0x1B, 0x40)) // ESC @: inicializa
                    output.write(text.toByteArray(Charsets.US_ASCII))
                    output.write(byteArrayOf(0x0A, 0x0A, 0x0A, 0x0A))
                    output.flush()
                }
            }
            PrinterAttempt(true, true, "BluetoothPrinter interno", "Comprovante enviado por ESC/POS ao dispositivo ${device.name}.")
        } catch (t: Throwable) {
            PrinterAttempt(true, false, "BluetoothPrinter interno", "${t.javaClass.simpleName}: ${t.message.orEmpty()}")
        }
    }
}

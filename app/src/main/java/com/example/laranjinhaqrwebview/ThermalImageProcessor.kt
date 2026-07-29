package com.example.laranjinhaqrwebview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.roundToInt

/**
 * Prepara fotografias para bobinas térmicas de 58 mm.
 *
 * A imagem é redimensionada para até 384 pontos, centralizada em fundo branco
 * e convertida para preto e branco com dithering Floyd-Steinberg. Isso melhora
 * a legibilidade em impressoras térmicas que não trabalham bem com tons de cinza.
 */
internal object ThermalImageProcessor {
    private const val PRINTER_WIDTH_PX = 384
    private const val MAX_PRINT_HEIGHT_PX = 520

    fun prepare(source: Bitmap): Bitmap {
        require(source.width > 0 && source.height > 0) { "A foto não possui dimensões válidas." }

        val scale = minOf(
            PRINTER_WIDTH_PX.toFloat() / source.width.toFloat(),
            MAX_PRINT_HEIGHT_PX.toFloat() / source.height.toFloat(),
            1f
        )
        val scaledWidth = (source.width * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (source.height * scale).roundToInt().coerceAtLeast(1)

        val scaled = if (scaledWidth == source.width && scaledHeight == source.height) {
            source
        } else {
            Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)
        }

        val canvasBitmap = Bitmap.createBitmap(
            PRINTER_WIDTH_PX,
            scaledHeight,
            Bitmap.Config.ARGB_8888
        )
        Canvas(canvasBitmap).apply {
            drawColor(Color.WHITE)
            val left = ((PRINTER_WIDTH_PX - scaledWidth) / 2f).coerceAtLeast(0f)
            drawBitmap(scaled, left, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        if (scaled !== source) scaled.recycle()

        val width = canvasBitmap.width
        val height = canvasBitmap.height
        val pixels = IntArray(width * height)
        canvasBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        canvasBitmap.recycle()

        val luminance = FloatArray(pixels.size)
        for (index in pixels.indices) {
            val color = pixels[index]
            val alpha = Color.alpha(color) / 255f
            val red = Color.red(color) * alpha + 255f * (1f - alpha)
            val green = Color.green(color) * alpha + 255f * (1f - alpha)
            val blue = Color.blue(color) * alpha + 255f * (1f - alpha)
            luminance[index] = 0.299f * red + 0.587f * green + 0.114f * blue
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val oldValue = luminance[index].coerceIn(0f, 255f)
                val newValue = if (oldValue < 145f) 0f else 255f
                val error = oldValue - newValue
                luminance[index] = newValue

                if (x + 1 < width) luminance[index + 1] += error * 7f / 16f
                if (y + 1 < height) {
                    if (x > 0) luminance[index + width - 1] += error * 3f / 16f
                    luminance[index + width] += error * 5f / 16f
                    if (x + 1 < width) luminance[index + width + 1] += error / 16f
                }
            }
        }

        for (index in pixels.indices) {
            pixels[index] = if (luminance[index] < 128f) Color.BLACK else Color.WHITE
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}

package com.hksalameh.shoppinglist

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import java.nio.FloatBuffer
import kotlin.math.roundToInt

internal data class PhotoOcrResult(val items: List<ImportItem>, val unreadable: Int)

internal class PhotoOcr(private val context: Context) {
    private val imageReader = PhotoImage(context)

    fun recognize(uris: List<Uri>): PhotoOcrResult {
        val runtime = OrtEnvironment.getEnvironment()
        val dictionary = context.assets.open("ocr/arabic-dictionary.txt").bufferedReader().use {
            listOf("") + it.readLines() + " "
        }
        val rows = mutableListOf<ImportItem>()
        var unreadable = 0
        OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(2)
            options.setInterOpNumThreads(1)
            openModel(runtime, options, "PP-OCRv5_mobile_det.onnx").use { detector ->
                openModel(runtime, options, "arabic_PP-OCRv5_mobile_rec.onnx").use { recognizer ->
                    for (uri in uris) {
                        val bitmap = try {
                            imageReader.decode(uri)
                        } catch (_: Exception) {
                            null
                        }
                        val parsed = if (bitmap == null) emptyList() else try {
                            val lines = detectRows(runtime, detector, bitmap)
                            val text = lines.joinToString("\n") { row ->
                                readRow(runtime, recognizer, dictionary, bitmap, row)
                            }
                            parseShoppingText(text)
                        } finally {
                            bitmap.recycle()
                        }
                        rows.addAll(parsed)
                        if (parsed.isEmpty()) unreadable++
                    }
                }
            }
        }
        return PhotoOcrResult(rows, unreadable)
    }

    private fun openModel(runtime: OrtEnvironment, options: OrtSession.SessionOptions, name: String): OrtSession {
        val model = context.assets.open("ocr/$name").use { it.readBytes() }
        return runtime.createSession(model, options)
    }

    private fun detectRows(runtime: OrtEnvironment, detector: OrtSession, photo: Bitmap): List<Rect> {
        val scale = minOf(1f, DETECTION_EDGE.toFloat() / maxOf(photo.width, photo.height))
        val width = ((photo.width * scale / 32).roundToInt() * 32).coerceAtLeast(32)
        val height = ((photo.height * scale / 32).roundToInt() * 32).coerceAtLeast(32)
        val input = photoInput(photo, width, height, width, detection = true)
        return OnnxTensor.createTensor(runtime, FloatBuffer.wrap(input), imageShape(width, height)).use { tensor ->
            detector.run(mapOf(detector.inputNames.first() to tensor)).use { result ->
                val map = result[0] as OnnxTensor
                val shape = (map.info as TensorInfo).shape
                val mapWidth = shape[3].toInt()
                val mapHeight = shape[2].toInt()
                val boxes = rowsFromMap(map.floatBuffer, mapWidth, mapHeight)
                boxes.map { box ->
                    Rect(
                        box.left * photo.width / mapWidth,
                        box.top * photo.height / mapHeight,
                        box.right * photo.width / mapWidth,
                        box.bottom * photo.height / mapHeight
                    )
                }.filter { it.width() > 0 && it.height() > 0 }
            }
        }
    }

    private fun rowsFromMap(probabilities: FloatBuffer, width: Int, height: Int): List<Rect> {
        val left = IntArray(height) { width }
        val right = IntArray(height)
        for (y in 0 until height) {
            var pixels = 0
            for (x in 0 until width) {
                if (probabilities.get() > 0.30f) {
                    left[y] = minOf(left[y], x)
                    right[y] = x + 1
                    pixels++
                }
            }
            if (pixels < 6) left[y] = width
        }

        val rows = mutableListOf<Rect>()
        var top = -1
        var bottom = -1
        var start = width
        var end = 0

        fun finishRow() {
            val lineHeight = bottom - top + 1
            if (top < 0 || lineHeight < 5 || end - start < 10) return
            val padding = (lineHeight / 3).coerceIn(5, 22)
            rows.add(
                Rect(
                    (start - padding).coerceAtLeast(0),
                    (top - padding).coerceAtLeast(0),
                    (end + padding).coerceAtMost(width),
                    (bottom + 1 + padding).coerceAtMost(height)
                )
            )
        }

        for (y in 0 until height) {
            if (left[y] == width) continue
            if (top < 0 || y - bottom > 6) {
                finishRow()
                top = y
                start = width
                end = 0
            }
            bottom = y
            start = minOf(start, left[y])
            end = maxOf(end, right[y])
        }
        finishRow()
        return rows.take(120)
    }

    private fun readRow(
        runtime: OrtEnvironment,
        recognizer: OrtSession,
        dictionary: List<String>,
        photo: Bitmap,
        row: Rect
    ): String {
        val crop = Bitmap.createBitmap(photo, row.left, row.top, row.width(), row.height())
        val width = (LINE_HEIGHT.toFloat() * row.width() / row.height()).roundToInt().coerceIn(12, 960)
        val padded = maxOf(160, width)
        try {
            val input = photoInput(crop, width, LINE_HEIGHT, padded, detection = false)
            return OnnxTensor.createTensor(runtime, FloatBuffer.wrap(input), imageShape(padded, LINE_HEIGHT)).use { tensor ->
                recognizer.run(mapOf(recognizer.inputNames.first() to tensor)).use { result ->
                    val output = result[0] as OnnxTensor
                    val shape = (output.info as TensorInfo).shape
                    val steps = shape[1].toInt()
                    val classes = shape[2].toInt()
                    check(classes == dictionary.size)
                    val probabilities = output.floatBuffer
                    val visual = buildString {
                        var last = -1
                        repeat(steps) {
                            var index = 0
                            var best = Float.NEGATIVE_INFINITY
                            repeat(classes) { candidate ->
                                val score = probabilities.get()
                                if (score > best) {
                                    best = score
                                    index = candidate
                                }
                            }
                            if (index != last) append(dictionary[index])
                            last = index
                        }
                    }
                    logicalArabicText(visual)
                }
            }
        } finally {
            if (crop !== photo) crop.recycle()
        }
    }

    private fun photoInput(
        photo: Bitmap,
        width: Int,
        height: Int,
        stride: Int,
        detection: Boolean
    ): FloatArray {
        val resized = Bitmap.createScaledBitmap(photo, width, height, true)
        val pixels = IntArray(width * height)
        try {
            resized.getPixels(pixels, 0, width, 0, 0, width, height)
        } finally {
            if (resized !== photo) resized.recycle()
        }

        val plane = stride * height
        val input = FloatArray(plane * 3)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                val offset = y * stride + x
                val alpha = pixel ushr 24
                val blue = opaqueChannel(pixel and 255, alpha)
                val green = opaqueChannel((pixel ushr 8) and 255, alpha)
                val red = opaqueChannel((pixel ushr 16) and 255, alpha)
                if (detection) {
                    input[offset] = (blue - 0.406f) / 0.225f
                    input[plane + offset] = (green - 0.456f) / 0.224f
                    input[2 * plane + offset] = (red - 0.485f) / 0.229f
                } else {
                    input[offset] = blue * 2 - 1
                    input[plane + offset] = green * 2 - 1
                    input[2 * plane + offset] = red * 2 - 1
                }
            }
        }
        return input
    }

    private fun opaqueChannel(channel: Int, alpha: Int): Float =
        (channel * alpha / 255f + 255 - alpha) / 255f

    private fun imageShape(width: Int, height: Int) = longArrayOf(1, 3, height.toLong(), width.toLong())

    private companion object {
        const val DETECTION_EDGE = 960
        const val LINE_HEIGHT = 48
    }
}

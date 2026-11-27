package com.hksalameh.shoppinglist

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.nio.FloatBuffer
import kotlin.math.roundToInt

internal data class ScannedItem(val item: ImportItem, val image: Bitmap, val suggestion: String?)
internal data class PhotoOcrResult(val items: List<ScannedItem>, val unreadable: Int)

private data class RecognizedLine(val text: String, val confidence: Float)

internal class PhotoOcr(private val context: Context) {
    private val imageReader = PhotoImage(context)

    fun recognize(uris: List<Uri>, knownNames: List<String>): PhotoOcrResult {
        val runtime = OrtEnvironment.getEnvironment()
        val dictionary = context.assets.open("ocr/arabic-dictionary.txt").bufferedReader().use {
            listOf("") + it.readLines() + " "
        }
        val rows = mutableListOf<ScannedItem>()
        val vocabulary = ShoppingVocabulary(knownNames)
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
                            recognizePage(runtime, detector, recognizer, dictionary, vocabulary, bitmap)
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

    private fun recognizePage(
        runtime: OrtEnvironment,
        detector: OrtSession,
        recognizer: OrtSession,
        dictionary: List<String>,
        vocabulary: ShoppingVocabulary,
        photo: Bitmap
    ): List<ScannedItem> {
        val ink = extractBlueInk(photo)
        return try {
            val lines = detectRows(runtime, detector, ink ?: photo)
            lines.mapNotNull { row ->
                val candidates = listOfNotNull(
                    readRow(runtime, recognizer, dictionary, photo, row),
                    ink?.let { readRow(runtime, recognizer, dictionary, it, row) }
                ).mapNotNull candidate@ { result ->
                    val text = result.text
                    val item = parseShoppingText(text).singleOrNull() ?: return@candidate null
                    if (ink != null && text.count { it.isLetter() && Character.UnicodeScript.of(it.code) == Character.UnicodeScript.ARABIC } < 2) {
                        return@candidate null
                    }
                    item to result.confidence
                }
                val best = candidates.map { (item, confidence) ->
                    val suggestion = vocabulary.suggest(item.name)
                    val match = when {
                        confidence < 0.45f -> 0f
                        vocabulary.isExact(item.name) -> 0.30f
                        suggestion != null -> 0.12f
                        else -> 0f
                    }
                    val mixedScript = ink != null && item.name.any { it in 'A'..'Z' || it in 'a'..'z' }
                    val score = confidence + match - if (mixedScript) 0.10f else 0f
                    Triple(item, suggestion, score)
                }.maxByOrNull { it.third }
                if (best == null && ink == null) return@mapNotNull null
                ScannedItem(best?.first ?: ImportItem(""), thumbnail(photo, row), best?.second)
            }
        } finally {
            ink?.recycle()
        }
    }

    // Blue handwriting on pale paper is often crossed by grey rules and black printed marks.
    private fun extractBlueInk(photo: Bitmap): Bitmap? {
        val scale = minOf(1f, 1536f / maxOf(photo.width, photo.height))
        val width = (photo.width * scale).roundToInt().coerceAtLeast(1)
        val height = (photo.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(photo, width, height, true)
        val pixels = IntArray(width * height)
        try {
            scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        } finally {
            if (scaled !== photo) scaled.recycle()
        }
        var colored = 0
        var pale = 0
        for (index in pixels.indices) {
            val color = pixels[index]
            val red = (color ushr 16) and 255
            val green = (color ushr 8) and 255
            val blue = color and 255
            if (minOf(red, green, blue) > 185) pale++
            val strength = minOf(blue - red, (blue - green) * 2)
            val ink = if (red < 205) ((strength - 13) * 5).coerceIn(0, 230) else 0
            if (ink > 0) colored++
            val grey = 255 - ink
            pixels[index] = (0xff shl 24) or (grey shl 16) or (grey shl 8) or grey
        }
        if (colored < pixels.size * 0.008f || pale < pixels.size * 0.63f || colored > pixels.size * 0.24f) return null
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun thumbnail(photo: Bitmap, row: PhotoRegion): Bitmap {
        val crop = crop(photo, row)
        val scale = minOf(1f, 336f / crop.width, 80f / crop.height)
        val image = Bitmap.createScaledBitmap(
            crop,
            (crop.width * scale).roundToInt().coerceAtLeast(1),
            (crop.height * scale).roundToInt().coerceAtLeast(1),
            true
        )
        val thumbnail = if (image === photo) photo.copy(Bitmap.Config.ARGB_8888, false) else image
        if (crop !== photo && crop !== thumbnail) crop.recycle()
        return thumbnail
    }

    private fun crop(photo: Bitmap, row: PhotoRegion): Bitmap {
        val left = (row.left * photo.width / REGION_GRID).coerceIn(0, photo.width - 1)
        val top = (row.top * photo.height / REGION_GRID).coerceIn(0, photo.height - 1)
        val right = (row.right * photo.width / REGION_GRID).coerceIn(left + 1, photo.width)
        val bottom = (row.bottom * photo.height / REGION_GRID).coerceIn(top + 1, photo.height)
        return Bitmap.createBitmap(photo, left, top, right - left, bottom - top)
    }

    private fun openModel(runtime: OrtEnvironment, options: OrtSession.SessionOptions, name: String): OrtSession {
        val model = context.assets.open("ocr/$name").use { it.readBytes() }
        return runtime.createSession(model, options)
    }

    private fun detectRows(runtime: OrtEnvironment, detector: OrtSession, photo: Bitmap): List<PhotoRegion> {
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
                val probabilities = map.floatBuffer
                val mask = BooleanArray(mapWidth * mapHeight) { probabilities.get() > 0.30f }
                photoTextRegions(mask, mapWidth, mapHeight).map { box ->
                    PhotoRegion(
                        box.left * REGION_GRID / mapWidth,
                        box.top * REGION_GRID / mapHeight,
                        box.right * REGION_GRID / mapWidth,
                        box.bottom * REGION_GRID / mapHeight
                    )
                }
            }
        }
    }

    private fun readRow(
        runtime: OrtEnvironment,
        recognizer: OrtSession,
        dictionary: List<String>,
        photo: Bitmap,
        row: PhotoRegion
    ): RecognizedLine {
        val crop = crop(photo, row)
        val width = (LINE_HEIGHT.toFloat() * crop.width / crop.height).roundToInt().coerceIn(12, 960)
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
                    var confidence = 0f
                    var characters = 0
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
                            if (index != last && index != 0) {
                                append(dictionary[index])
                                confidence += best
                                characters++
                            }
                            last = index
                        }
                    }
                    RecognizedLine(logicalArabicText(visual), confidence / characters.coerceAtLeast(1))
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
        const val REGION_GRID = 10_000
        const val LINE_HEIGHT = 48
    }
}

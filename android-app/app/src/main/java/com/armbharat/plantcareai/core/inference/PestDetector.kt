package com.armbharat.plantcareai.core.inference

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.tensorflow.lite.Interpreter

data class PestBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

data class PestDetection(
    val classId: Int,
    val label: String,
    val hostCrop: String,
    val confidence: Float,
    val box: PestBox
)

class PestDetector(context: Context) {
    private val appContext = context.applicationContext


    suspend fun detect(
        bitmap: Bitmap,
        crop: String = "all"
    ): List<PestDetection> = withContext(Dispatchers.Default) {
        val model = appContext.assets
            .open("yolo26n_ip102_640_raw_w8a32.tflite")
            .use { it.readBytes() }

        val modelBuffer = ByteBuffer.allocateDirect(model.size)
            .order(ByteOrder.nativeOrder())
        modelBuffer.put(model)
        modelBuffer.rewind()

        val labels = loadLabels()
        val allowedIds = cropClasses[crop.trim().lowercase()] ?: allClasses
        val output = ByteBuffer
            .allocateDirect((4 + CLASS_COUNT) * PREDICTION_COUNT * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())

        ensureActive()
        Interpreter(modelBuffer).use { interpreter ->
            val fullFrameResults = detectFrame(
                bitmap, interpreter, output, labels, allowedIds
            )
            if (fullFrameResults.isNotEmpty()) {
                return@withContext fullFrameResults
            }

            val cropWidth = (bitmap.width * RETRY_CROP_FRACTION)
                .roundToInt().coerceIn(1, bitmap.width)
            val cropHeight = (bitmap.height * RETRY_CROP_FRACTION)
                .roundToInt().coerceIn(1, bitmap.height)

            if (cropWidth == bitmap.width && cropHeight == bitmap.height) {
                return@withContext fullFrameResults
            }

            ensureActive()
            val left = (bitmap.width - cropWidth) / 2
            val top = (bitmap.height - cropHeight) / 2
            val closeUp = Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)

            try {
                detectFrame(closeUp, interpreter, output, labels, allowedIds).map { detection ->
                    val box = detection.box
                    detection.copy(
                        box = PestBox(
                            left = box.left + left,
                            top = box.top + top,
                            right = box.right + left,
                            bottom = box.bottom + top
                        )
                    )
                }
            } finally {
                if (closeUp !== bitmap) closeUp.recycle()
            }
        }
    }

    private suspend fun detectFrame(
        bitmap: Bitmap,
        interpreter: Interpreter,
        output: ByteBuffer,
        labels: List<String>,
        allowedIds: IntRange
    ): List<PestDetection> {
        val coroutineContext = currentCoroutineContext()
        coroutineContext.ensureActive()

        val input = prepareInput(bitmap)
        output.clear()
        interpreter.run(input.buffer, output)
        coroutineContext.ensureActive()

        output.rewind()
        val predictions = output.asFloatBuffer()

        // Output layout: [1, 106, 8400].
        fun value(channel: Int, prediction: Int): Float =
            predictions[channel * PREDICTION_COUNT + prediction]

        val candidates = mutableListOf<Candidate>()
        for (prediction in 0 until PREDICTION_COUNT) {
            if (prediction % 256 == 0) coroutineContext.ensureActive()

            var bestId = -1
            var bestScore = Float.NEGATIVE_INFINITY
            for (classId in allowedIds) {
                val score = value(4 + classId, prediction)
                if (score.isFinite() && score > bestScore) {
                    bestId = classId
                    bestScore = score
                }
            }

            if (bestScore < CONFIDENCE_THRESHOLD) continue

            val centerX = value(0, prediction)
            val centerY = value(1, prediction)
            val width = value(2, prediction)
            val height = value(3, prediction)

            if (!centerX.isFinite() || !centerY.isFinite() ||
                !width.isFinite() || !height.isFinite() ||
                width <= 0f || height <= 0f
            ) continue

            candidates.add(Candidate(bestId, bestScore, centerX, centerY, width, height))
        }

        if (candidates.isEmpty()) return emptyList()

        // Match the Python detector's normalized-coordinate check.
        val normalized = candidates.all {
            maxOf(it.centerX, it.centerY, it.width, it.height) <= 1.5f
        }
        val multiplier = if (normalized) INPUT_SIZE.toFloat() else 1f

        val kept = mutableListOf<ScoredBox>()
        for (candidate in candidates.sortedByDescending { it.score }) {
            coroutineContext.ensureActive()
            val box = candidate.toScoredBox(multiplier)
            if (kept.none { overlap(it, box) > IOU_THRESHOLD }) {
                kept.add(box)
            }
        }

        return kept.mapNotNull { box ->
            val mapped = PestBox(
                left = ((box.left - input.padX) / input.scale)
                    .roundToInt().coerceIn(0, bitmap.width),
                top = ((box.top - input.padY) / input.scale)
                    .roundToInt().coerceIn(0, bitmap.height),
                right = ((box.right - input.padX) / input.scale)
                    .roundToInt().coerceIn(0, bitmap.width),
                bottom = ((box.bottom - input.padY) / input.scale)
                    .roundToInt().coerceIn(0, bitmap.height)
            )

            if (mapped.right <= mapped.left || mapped.bottom <= mapped.top) {
                return@mapNotNull null
            }

            val hostCrop = cropClasses.entries.first { box.classId in it.value }.key

            PestDetection(
                classId = box.classId,
                label = labels[box.classId],
                hostCrop = hostCrop,
                confidence = box.score,
                box = mapped
            )
        }
    }

    private fun loadLabels(): List<String> {
        val json = appContext.assets.open("ip102_labels.json")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

        val metadata = JSONObject(json)
        require(metadata.optInt("input_size", INPUT_SIZE) == INPUT_SIZE) {
            "Pest labels must match the 640 × 640 model."
        }

        val names = metadata.getJSONObject("names")
        require(names.length() == CLASS_COUNT) {
            "Expected $CLASS_COUNT pest labels."
        }

        return List(CLASS_COUNT) { classId ->
            names.getString(classId.toString()).also { label ->
                require(label.isNotBlank()) { "Missing pest label $classId." }
            }
        }
    }

    private fun prepareInput(bitmap: Bitmap): PreparedInput {
        val scale = minOf(
            INPUT_SIZE.toFloat() / bitmap.width,
            INPUT_SIZE.toFloat() / bitmap.height
        )
        val width = (bitmap.width * scale).roundToInt().coerceIn(1, INPUT_SIZE)
        val height = (bitmap.height * scale).roundToInt().coerceIn(1, INPUT_SIZE)
        val padX = (INPUT_SIZE - width) / 2
        val padY = (INPUT_SIZE - height) / 2

        val canvasBitmap = Bitmap.createBitmap(
            INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888
        )
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)

        try {
            Canvas(canvasBitmap).apply {
                drawColor(Color.rgb(114, 114, 114))
                drawBitmap(
                    bitmap,
                    null,
                    Rect(padX, padY, padX + width, padY + height),
                    Paint(Paint.FILTER_BITMAP_FLAG)
                )
            }
            canvasBitmap.getPixels(
                pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE
            )
        } finally {
            canvasBitmap.recycle()
        }

        val buffer = ByteBuffer
            .allocateDirect(pixels.size * 3 * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())

        // Planar RGB: write red, green, and blue in separate planes.
        for (shift in intArrayOf(16, 8, 0)) {
            for (pixel in pixels) {
                buffer.putFloat(((pixel ushr shift) and 0xff) / 255f)
            }
        }
        buffer.rewind()

        return PreparedInput(buffer, scale, padX, padY)
    }

    private fun overlap(a: ScoredBox, b: ScoredBox): Float {
        val width = (minOf(a.right, b.right) - maxOf(a.left, b.left))
            .coerceAtLeast(0f)
        val height = (minOf(a.bottom, b.bottom) - maxOf(a.top, b.top))
            .coerceAtLeast(0f)
        val intersection = width * height
        val union = a.area + b.area - intersection
        return if (union > 0f) intersection / union else 0f
    }

    private data class PreparedInput(
        val buffer: ByteBuffer,
        val scale: Float,
        val padX: Int,
        val padY: Int
    )

    private data class Candidate(
        val classId: Int,
        val score: Float,
        val centerX: Float,
        val centerY: Float,
        val width: Float,
        val height: Float
    ) {
        fun toScoredBox(multiplier: Float): ScoredBox = ScoredBox(
            classId = classId,
            score = score,
            left = (centerX - width / 2f) * multiplier,
            top = (centerY - height / 2f) * multiplier,
            right = (centerX + width / 2f) * multiplier,
            bottom = (centerY + height / 2f) * multiplier
        )
    }

    private data class ScoredBox(
        val classId: Int,
        val score: Float,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        val area: Float get() = (right - left) * (bottom - top)
    }

    private companion object {
        const val INPUT_SIZE = 640
        const val CLASS_COUNT = 102
        const val PREDICTION_COUNT = 8400
        const val CONFIDENCE_THRESHOLD = 0.25f
        const val IOU_THRESHOLD = 0.45f
        const val RETRY_CROP_FRACTION = 0.65f

        val allClasses = 0 until CLASS_COUNT
        val cropClasses = mapOf(
            "rice" to 0..13,
            "corn" to 14..26,
            "wheat" to 27..35,
            "beet" to 36..43,
            "alfalfa" to 44..56,
            "vitis" to 57..68,
            "citrus" to 69..86,
            "mango" to 87..101
        )
    }
}

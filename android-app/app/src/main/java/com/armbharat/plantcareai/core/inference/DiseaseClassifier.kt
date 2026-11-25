package com.armbharat.plantcareai.core.inference

import android.content.Context
import android.graphics.Bitmap
import android.util.AtomicFile
import java.io.File
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DiseasePrediction(
    val diseaseName: String,
    val confidence: Float
)

class DiseaseClassifier(context: Context) {

    companion object {
        private const val INPUT_SIZE = 224
        private const val NUM_CLASSES = 38
        private const val TOP_K = 3
        private const val MODEL_ASSET = "disease_mobilenet.tflite"
        private const val CACHE_NAME = "disease_mobilenet_277b15c92bf9.tflite"
        private const val MODEL_SIZE = 10_222_648L
        private val modelCacheLock = Any()

        private fun bundledModelFile(context: Context): File = synchronized(modelCacheLock) {
            val file = File(context.cacheDir, CACHE_NAME)
            if (file.length() == MODEL_SIZE) return@synchronized file

            val atomicFile = AtomicFile(file)
            val output = atomicFile.startWrite()
            try {
                context.assets.open(MODEL_ASSET).use { input ->
                    check(input.copyTo(output) == MODEL_SIZE) {
                        "The bundled disease model has an unexpected size."
                    }
                }
                atomicFile.finishWrite(output)
            } catch (failure: Throwable) {
                atomicFile.failWrite(output)
                throw failure
            }
            file
        }
    }

    private val interpreter: Interpreter
    private val labels: List<String>

    init {
        val modelFile = bundledModelFile(context)

        labels = context.assets.open("labels.txt")
            .bufferedReader()
            .use { reader ->
                reader.readLines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            }

        require(labels.size == NUM_CLASSES) {
            "Expected 38 labels, found ${labels.size}"
        }

        val loadedInterpreter = Interpreter(modelFile)
        try {
            val input = loadedInterpreter.getInputTensor(0)
            require(input.dataType() == DataType.FLOAT32 &&
                    input.shape().contentEquals(intArrayOf(1, INPUT_SIZE, INPUT_SIZE, 3))
            ) {
                "Unexpected disease model input. Expected FLOAT32 RGB [1, 224, 224, 3]."
            }

            val output = loadedInterpreter.getOutputTensor(0)
            require(output.dataType() == DataType.FLOAT32 &&
                    output.shape().contentEquals(intArrayOf(1, NUM_CLASSES))
            ) {
                "Unexpected disease model output. Expected FLOAT32 [1, 38]."
            }
        } catch (failure: Throwable) {
            loadedInterpreter.close()
            throw failure
        }

        interpreter = loadedInterpreter
    }

    fun predict(bitmap: Bitmap): List<DiseasePrediction> {

        val resizedBitmap = Bitmap.createScaledBitmap(
            bitmap,
            INPUT_SIZE,
            INPUT_SIZE,
            true
        )

        try {
            val inputBuffer = ByteBuffer.allocateDirect(
                INPUT_SIZE * INPUT_SIZE * 3 * 4
            ).order(ByteOrder.nativeOrder())

            val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)

            resizedBitmap.getPixels(
                pixels,
                0,
                INPUT_SIZE,
                0,
                0,
                INPUT_SIZE,
                INPUT_SIZE
            )

            for (pixel in pixels) {
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF

                inputBuffer.putFloat(red / 255.0f)
                inputBuffer.putFloat(green / 255.0f)
                inputBuffer.putFloat(blue / 255.0f)
            }

            inputBuffer.rewind()

            val output = Array(1) {
                FloatArray(NUM_CLASSES)
            }

            interpreter.run(inputBuffer, output)

            val scores = output[0]

            return scores.indices
                .sortedByDescending { scores[it] }
                .take(TOP_K)
                .map { index ->
                    DiseasePrediction(
                        diseaseName = labels[index],
                        confidence = scores[index]
                    )
                }

        } finally {
            if (resizedBitmap !== bitmap) {
                resizedBitmap.recycle()
            }
        }
    }

    fun close() {
        interpreter.close()
    }
}

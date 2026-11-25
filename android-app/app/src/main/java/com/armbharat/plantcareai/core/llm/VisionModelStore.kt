package com.armbharat.plantcareai.core.inference

import android.content.Context
import android.net.Uri
import android.system.Os
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter

enum class VisionModelType(
    internal val fileName: String,
    internal val inputShape: IntArray,
    internal val outputShape: IntArray
) {
    DISEASE(
        "disease.tflite",
        intArrayOf(1, 224, 224, 3),
        intArrayOf(1, 38)
    ),
    PEST(
        "pest.tflite",
        intArrayOf(1, 3, 640, 640),
        intArrayOf(1, 106, 8400)
    )
}

class VisionModelStore(context: Context) {
    private val appContext = context.applicationContext
    private val modelDir = File(appContext.filesDir, "vision_models")

    fun importedModel(type: VisionModelType): File? {
        return File(modelDir, type.fileName).takeIf { it.isFile }
    }

    suspend fun importModel(type: VisionModelType, uri: Uri): File =
        withContext(Dispatchers.IO) {
            check(modelDir.isDirectory || modelDir.mkdirs()) {
                "Could not create model storage."
            }

            val candidate = File.createTempFile("vision_model_", ".tflite", modelDir)
            val target = File(modelDir, type.fileName)

            try {
                val source = appContext.contentResolver.openInputStream(uri)
                    ?: error("Could not open the selected model.")

                source.use { input ->
                    candidate.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                    }
                }

                validateModel(candidate, type)
                ensureActive()

                // Both files are in the same directory. Rename replaces the
                // active file only after the candidate passes validation.
                Os.rename(candidate.absolutePath, target.absolutePath)
                target
            } finally {
                candidate.delete()
            }
        }

    private fun validateModel(file: File, type: VisionModelType) {
        Interpreter(file).use { interpreter ->
            require(interpreter.inputTensorCount == 1 &&
                    interpreter.outputTensorCount == 1) {
                "Expected exactly one input and one output."
            }

            val input = interpreter.getInputTensor(0)
            val output = interpreter.getOutputTensor(0)

            require(input.dataType() == DataType.FLOAT32 &&
                    input.shape().contentEquals(type.inputShape)) {
                "Expected FLOAT32 input ${type.inputShape.contentToString()}, " +
                        "got ${input.dataType()} ${input.shape().contentToString()}."
            }

            require(output.dataType() == DataType.FLOAT32 &&
                    output.shape().contentEquals(type.outputShape)) {
                "Expected FLOAT32 output ${type.outputShape.contentToString()}, " +
                        "got ${output.dataType()} ${output.shape().contentToString()}."
            }

            interpreter.allocateTensors()
        }
    }
}

package com.armbharat.plantcareai.core.inference

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.File

object TFLiteModelInspector {

    private const val TAG = "PlantCareAI_Model"

    fun inspect(context: Context) {
        var interpreter: Interpreter? = null

        try {
            val modelFile = File(
                context.cacheDir,
                "plant_disease_model.tflite"
            )

            context.assets.open("plant_disease_model.tflite").use { input ->
                modelFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            interpreter = Interpreter(modelFile)

            val inputTensor = interpreter.getInputTensor(0)
            val outputTensor = interpreter.getOutputTensor(0)

            Log.i(TAG, "MODEL LOADED SUCCESSFULLY")
            Log.i(TAG, "Input shape: ${inputTensor.shape().contentToString()}")
            Log.i(TAG, "Input type: ${inputTensor.dataType()}")
            Log.i(TAG, "Output shape: ${outputTensor.shape().contentToString()}")
            Log.i(TAG, "Output type: ${outputTensor.dataType()}")

        } catch (e: Exception) {
            Log.e(TAG, "MODEL INSPECTION FAILED", e)
        } finally {
            interpreter?.close()
        }
    }
}

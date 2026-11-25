
package com.armbharat.plantcareai.core.llm

import org.pytorch.executorch.extension.llm.LlmCallback
import org.pytorch.executorch.extension.llm.LlmModule

class LlamaInferenceEngine {

    private var module: LlmModule? = null

    // Load the offline Llama model
    fun loadModel(
        modelPath: String,
        tokenizerPath: String
    ) {
        val newModule = LlmModule(
            1, // ExecuTorch TEXT_MODEL (XNNPACK)
            modelPath,
            tokenizerPath,
            0.0f
        )

        newModule.load()
        module = newModule
    }

    // Generate a response
    fun generate(
        prompt: String,
        maxSeqLen: Int,
        callback: LlmCallback
    ) {
        val loadedModule = module
            ?: throw IllegalStateException("Llama model is not loaded")

        loadedModule.generate(
            prompt,
            maxSeqLen,
            callback,
            false
        )
    }

    // Stop ongoing generation
    fun stopGeneration() {
        module?.stop()
    }

    // Reset conversation context
    fun resetContext() {
        module?.resetContext()
    }

    fun isModelLoaded(): Boolean {
        return module != null
    }
}

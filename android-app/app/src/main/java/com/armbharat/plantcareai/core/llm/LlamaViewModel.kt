
package com.armbharat.plantcareai.core.llm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pytorch.executorch.extension.llm.LlmCallback

class LlamaViewModel : ViewModel(), LlmCallback {

    private val engine = LlamaInferenceEngine()

    // AI Assistant chat response
    private val _response = MutableStateFlow("")
    val response = _response.asStateFlow()

    // Persistent diagnosis recommendation
    private val _recommendation = MutableStateFlow("")
    val recommendation = _recommendation.asStateFlow()

    private val _status = MutableStateFlow("Model not loaded")
    val status = _status.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    // Diagnosis tracking
    private var currentImageKey: String? = null
    private var generatedDiagnosisKey: String? = null

    private enum class OutputTarget {
        CHAT,
        RECOMMENDATION
    }

    @Volatile
    private var activeOutputTarget = OutputTarget.CHAT

    // ------------------------------------------------
    // LOAD MODEL
    // ------------------------------------------------

    fun loadModel(modelPath: String, tokenizerPath: String) {

        if (_isLoading.value || engine.isModelLoaded()) return

        viewModelScope.launch {

            _isLoading.value = true
            _status.value = "Loading Llama model..."

            try {

                withContext(Dispatchers.IO) {
                    engine.loadModel(modelPath, tokenizerPath)
                }

                _status.value = "Llama model loaded successfully"

            } catch (e: Exception) {

                _status.value =
                    "Model loading failed: ${e.message}"

            } finally {

                _isLoading.value = false
            }
        }
    }

    // ------------------------------------------------
    // START OR RESTORE DIAGNOSIS
    // ------------------------------------------------

    fun beginDiagnosis(imageKey: String) {

        // Same image: preserve existing recommendation
        if (currentImageKey == imageKey) return

        // Stop any previous generation for an older image
        if (_isGenerating.value) {
            engine.stopGeneration()
        }

        currentImageKey = imageKey
        generatedDiagnosisKey = null

        _recommendation.value = ""
    }

    // ------------------------------------------------
    // GENERATE AI DIAGNOSIS RECOMMENDATION
    // ------------------------------------------------

    fun generateDiagnosisRecommendation(
        crop: String,
        disease: String,
        confidence: Float,
        severity: String,
        estimatedDamage: Float,
        language: String = "English"
    ) {
        if (!engine.isModelLoaded() || _isGenerating.value) return

        val imageKey = currentImageKey ?: return
        val diagnosisKey = listOf(
            imageKey,
            crop,
            disease,
            confidence.toString(),
            severity,
            estimatedDamage.toString(),
            language
        ).joinToString("|")

        if (generatedDiagnosisKey == diagnosisKey) return
        generatedDiagnosisKey = diagnosisKey

        val formattedPrompt = """
        <|begin_of_text|><|start_header_id|>system<|end_header_id|>

        You are PlantCareAI. Give brief crop-care guidance. Treat scan results as estimates. Never invent pesticide names or doses.<|eot_id|><|start_header_id|>user<|end_header_id|>

        Crop: $crop. Suspected disease: $disease. Estimated severity: $severity.
        Reply in $language in 3 short bullets: what to check, what to do now, and when to seek expert help.<|eot_id|><|start_header_id|>assistant<|end_header_id|>

    """.trimIndent() + "\n\n"

        viewModelScope.launch {
            _recommendation.value = ""
            _isGenerating.value = true
            activeOutputTarget = OutputTarget.RECOMMENDATION

            try {
                withContext(Dispatchers.IO) {
                    engine.generate(
                        formattedPrompt,
                        512,
                        this@LlamaViewModel
                    )
                }

                if (currentImageKey == imageKey && _recommendation.value.isBlank()) {
                    _recommendation.value =
                        "The model returned no recommendation. Try scanning again."
                    generatedDiagnosisKey = null
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (currentImageKey == imageKey) {
                    _recommendation.value =
                        "Recommendation generation failed: ${e.message}"
                    generatedDiagnosisKey = null
                }
            } finally {
                _isGenerating.value = false
            }
        }
    }

    // ------------------------------------------------
    // AI ASSISTANT CHAT WITH DATABASE CONTEXT
    // ------------------------------------------------

    fun generateTreatmentResponse(
        prompt: String,
        treatment: com.armbharat.plantcareai.core.database.TreatmentEntity?,
        language: String = "English"
    ) {

        if (!engine.isModelLoaded()) return
        if (_isGenerating.value || prompt.isBlank()) return

        val formattedPrompt = if (treatment == null) {
            """
    <|begin_of_text|><|start_header_id|>system<|end_header_id|>

    You are PlantCareAI, a friendly farming assistant.
    Reply in $language. Greet greetings naturally.
    Answer general plant questions briefly.
    For treatment questions, never invent pesticide names or doses.
    <|eot_id|><|start_header_id|>user<|end_header_id|>

    $prompt<|eot_id|><|start_header_id|>assistant<|end_header_id|>

    """.trimIndent() + "\n\n"
        } else {
            LlamaPromptBuilder.buildPrompt(
                userMessage = prompt,
                treatmentContext = TreatmentContextBuilder.buildContext(
                    treatment = treatment,
                    language = language
                ),
                language = language
            )
        }

        generateChat(formattedPrompt)
    }

    // ------------------------------------------------
    // GENERAL AI ASSISTANT CHAT
    // ------------------------------------------------

    fun generateResponse(prompt: String) {

        if (!engine.isModelLoaded()) return
        if (_isGenerating.value || prompt.isBlank()) return

        val formattedPrompt =
            LlamaPromptBuilder.buildPrompt(prompt)

        generateChat(formattedPrompt)
    }

    private fun generateChat(prompt: String) {

        if (_isGenerating.value) return

        viewModelScope.launch {

            _response.value = ""
            _isGenerating.value = true
            activeOutputTarget = OutputTarget.CHAT

            try {

                withContext(Dispatchers.IO) {

                    engine.generate(
                        prompt,
                        768,
                        this@LlamaViewModel
                    )
                }

            } catch (e: Exception) {

                _response.value =
                    "Generation failed: ${e.message}"

            } finally {

                _isGenerating.value = false
            }
        }
    }

    // ------------------------------------------------
    // EXECUTORCH CALLBACK
    // ------------------------------------------------

    override fun onResult(result: String) {

        val cleanedResult = result
            .replace("<|eot_id|>", "")
            .replace("**", "")

        when (activeOutputTarget) {

            OutputTarget.RECOMMENDATION -> {
                _recommendation.value += cleanedResult
            }

            OutputTarget.CHAT -> {
                _response.value += cleanedResult
            }
        }
    }

    override fun onStats(stats: String) {
        // Inference statistics can be added later.
    }

    // ------------------------------------------------
    // STOP GENERATION
    // ------------------------------------------------

    fun stopGeneration() {
        engine.stopGeneration()
    }

    // Clear chat without clearing diagnosis recommendation
    fun resetContext() {
        engine.resetContext()
        _response.value = ""
    }
}

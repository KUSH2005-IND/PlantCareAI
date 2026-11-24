
package com.armbharat.plantcareai.core.llm

object LlamaPromptBuilder {

    private const val SYSTEM_PROMPT = """
        You are PlantCareAI, an agricultural assistant.
        Help farmers understand plant diseases and safe crop care.
        Use simple language.

        IMPORTANT SAFETY RULES:
        1. Use the provided verified treatment information
           as the source for treatment recommendations.
        2. Never invent pesticide names or chemical dosages.
        3. Never claim that you performed scientific research
           or verified something externally.
        4. If treatment information is unavailable,
           clearly tell the user.
        5. Do not treat model predictions as confirmed diagnoses.
        6. Explain answers in the requested language.
        7. Keep answers farmer-friendly and practical.
    """

    fun buildPrompt(userMessage: String): String {
        return buildPrompt(
            userMessage = userMessage,
            treatmentContext = "No treatment information provided.",
            language = "English"
        )
    }

    fun buildPrompt(
        userMessage: String,
        treatmentContext: String,
        language: String
    ): String {

        return """
            <|begin_of_text|><|start_header_id|>system<|end_header_id|>
            $SYSTEM_PROMPT
            Requested language: $language
            <|eot_id|>

            <|start_header_id|>user<|end_header_id|>

            VERIFIED DATABASE INFORMATION:
            $treatmentContext

            USER QUESTION:
            $userMessage

            Answer using the database information above.
            If the database does not contain the requested
            information, clearly state that it is unavailable.
            <|eot_id|>

            <|start_header_id|>assistant<|end_header_id|>
        """.trimIndent()
    }
}

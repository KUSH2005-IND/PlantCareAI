
package com.armbharat.plantcareai.core.llm

import com.armbharat.plantcareai.core.database.TreatmentEntity

object TreatmentContextBuilder {

    fun buildContext(
        treatment: TreatmentEntity?,
        language: String = "English"
    ): String {

        if (treatment == null) {
            return """
                No verified treatment record was found
                in the PlantCareAI database.

                Do not invent chemical names,
                dosages, or treatment instructions.
                Clearly tell the user that verified
                treatment information is unavailable.
            """.trimIndent()
        }

        val isHindi = language.equals(
            "Hindi",
            ignoreCase = true
        )

        return """
            VERIFIED PLANT TREATMENT INFORMATION

            Disease: ${if (isHindi) treatment.nameHi else treatment.nameEn}

            Category: ${treatment.category}

            Chemical Treatment:
            ${if (isHindi) treatment.chemicalDosageHi else treatment.chemicalDosageEn}

            Organic Remedy:
            ${if (isHindi) treatment.organicRemedyHi else treatment.organicRemedyEn}

            Prevention:
            ${if (isHindi) treatment.preventionHi else treatment.preventionEn}

            Region: ${treatment.regionTag}

            IMPORTANT RULES:
            1. Use only the information provided above
               for treatment recommendations.
            2. Never invent pesticide names or dosages.
            3. If information is missing, say so clearly.
            4. Explain the information in simple language.
            5. Respond in ${if (isHindi) "Hindi" else "English"}.
        """.trimIndent()
    }
}

package com.armbharat.plantcareai.core.llm

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat

@Composable
fun currentAiReplyLanguage(): String {
    val code = ConfigurationCompat
        .getLocales(LocalConfiguration.current)[0]?.language

    return when (code) {
        "hi" -> "Hindi in Devanagari script"
        "pa" -> "Punjabi in Gurmukhi script"
        "mr" -> "Marathi in Devanagari script"
        "ta" -> "Tamil in Tamil script"
        else -> "English"
    }
}
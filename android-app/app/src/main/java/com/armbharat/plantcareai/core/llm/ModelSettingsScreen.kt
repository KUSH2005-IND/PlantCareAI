package com.armbharat.plantcareai.core.llm

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.armbharat.plantcareai.R

@Composable
fun ModelSettingsScreen(
    llamaViewModel: LlamaViewModel,
    onBackClick: () -> Unit
) {
    val status by llamaViewModel.status.collectAsState()
    val isLoading by llamaViewModel.isLoading.collectAsState()
    val isGenerating by llamaViewModel.isGenerating.collectAsState()
    val ready = status == "Llama model loaded successfully"
    val failurePrefix = "Model loading failed: "

    val localizedStatus = when {
        ready -> stringResource(R.string.assistant_model_loaded)
        isLoading -> stringResource(R.string.assistant_model_loading)
        status.startsWith(failurePrefix) -> stringResource(
            R.string.assistant_model_load_failed,
            status.removePrefix(failurePrefix)
        )
        else -> stringResource(R.string.assistant_model_not_loaded)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackClick) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.settings_back)
                )
            }

            Text(
                text = stringResource(R.string.assistant_model_status),
                style = MaterialTheme.typography.titleLarge
            )
        }

        Text(localizedStatus)

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else if (!ready && !isGenerating) {
            LlamaModelPicker(
                onFilesReady = { modelPath, tokenizerPath ->
                    llamaViewModel.loadModel(modelPath, tokenizerPath)
                }
            )
        }
    }
}
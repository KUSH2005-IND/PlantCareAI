package com.armbharat.plantcareai.ui.settings


import androidx.compose.ui.res.stringResource

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.armbharat.plantcareai.core.inference.VisionModelStore
import com.armbharat.plantcareai.core.inference.VisionModelType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.armbharat.plantcareai.R
@Composable
fun VisionModelSettings(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { VisionModelStore(context) }
    val scope = rememberCoroutineScope()

    var diseaseImported by remember(store) {
        mutableStateOf(store.importedModel(VisionModelType.DISEASE) != null)
    }
    var pestImported by remember(store) {
        mutableStateOf(store.importedModel(VisionModelType.PEST) != null)
    }
    var selectedType by rememberSaveable {
        mutableStateOf<VisionModelType?>(null)
    }
    var importingType by remember {
        mutableStateOf<VisionModelType?>(null)
    }
    var message by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val type = selectedType
        selectedType = null

        if (uri != null && type != null) {
            importingType = type
            message = null

            scope.launch {
                try {
                    store.importModel(type, uri)
                    when (type) {
                        VisionModelType.DISEASE -> diseaseImported = true
                        VisionModelType.PEST -> pestImported = true
                    }
                    message = "Model validated and imported."
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    message = e.localizedMessage ?: "Could not import this model."
                } catch (e: LinkageError) {
                    message = e.localizedMessage ?: "Could not start the model runtime."
                } finally {
                    importingType = null
                }
            }
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for (type in VisionModelType.entries) {
                val disease = type == VisionModelType.DISEASE
                val imported = if (disease) diseaseImported else pestImported

                Text(
                    text = if (disease) stringResource(R.string.plantcare_vision_disease_model) else stringResource(R.string.plantcare_vision_pest_model),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = when {
                        importingType == type -> "Validating model…"
                        imported -> 	stringResource(R.string.plantcare_model_imported)
                        disease -> "Bundled model available"
                        else -> "No model imported"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(
                    enabled = selectedType == null && importingType == null,
                    onClick = {
                        selectedType = type
                        message = null
                        picker.launch(arrayOf("*/*"))
                    }
                ) {
                    Text(if (imported) stringResource(R.string.plantcare_model_replace) else stringResource(R.string.plantcare_vision_import_model))
                }

                if (disease) HorizontalDivider()
            }

            message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
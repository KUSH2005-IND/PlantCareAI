
package com.armbharat.plantcareai.core.llm

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LlamaModelPicker(
    onFilesReady: (String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var modelPath by remember { mutableStateOf<String?>(null) }
    var tokenizerPath by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Select your model and tokenizer") }

    var isCopying by remember { mutableStateOf(false) }

    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isCopying = true
            status = "Copying Llama model... This may take a while."

            scope.launch {
                try {
                    val file = withContext(Dispatchers.IO) {
                        ModelFileManager.copyModelFile(
                            context,
                            uri,
                            "llama_model.pte"
                        )
                    }

                    modelPath = file.absolutePath
                    status = "Model selected successfully"
                } catch (e: Exception) {
                    status = "Model copy failed: ${e.message}"
                } finally {
                    isCopying = false
                }
            }
        }
    }

    val tokenizerPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isCopying = true
            status = "Copying tokenizer..."

            scope.launch {
                try {
                    val file = withContext(Dispatchers.IO) {
                        ModelFileManager.copyModelFile(
                            context,
                            uri,
                            "tokenizer.bin"
                        )
                    }

                    tokenizerPath = file.absolutePath
                    status = "Tokenizer selected successfully"
                } catch (e: Exception) {
                    status = "Tokenizer copy failed: ${e.message}"
                } finally {
                    isCopying = false
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {

        Button(
            onClick = { modelPicker.launch(arrayOf("*/*")) },
            enabled = !isCopying
        ) {
            Text("Select Llama .pte Model")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { tokenizerPicker.launch(arrayOf("*/*")) },
            enabled = !isCopying
        ) {
            Text("Select Tokenizer")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(status)

        if (modelPath != null) {
            Text("Model: Selected")
        }

        if (tokenizerPath != null) {
            Text("Tokenizer: Selected")
        }

        Button(
            onClick = {
                val model = modelPath
                val tokenizer = tokenizerPath

                if (model != null && tokenizer != null) {
                    onFilesReady(model, tokenizer)
                }
            },
            enabled = modelPath != null &&
                    tokenizerPath != null &&
                    !isCopying
        ) {
            Text("Confirm Model Files")
        }
    }
}

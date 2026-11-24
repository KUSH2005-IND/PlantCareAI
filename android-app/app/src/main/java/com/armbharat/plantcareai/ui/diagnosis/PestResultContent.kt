package com.armbharat.plantcareai.ui.diagnosis

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.armbharat.plantcareai.R
import com.armbharat.plantcareai.core.inference.PestDetection
import com.armbharat.plantcareai.core.inference.PestDetector
import java.text.NumberFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import com.armbharat.plantcareai.core.database.DatabaseProvider
import com.armbharat.plantcareai.core.database.ScanHistoryEntity
import com.armbharat.plantcareai.data.repository.ScanHistoryRepository
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun PestResultContent(
    imageUri: Uri,
    onNewScan: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val historyRepository = remember(context) {
        ScanHistoryRepository(
            DatabaseProvider.getDatabase(context).scanHistoryDao()
        )
    }
    val saveScope = rememberCoroutineScope()
    var isSaving by remember(imageUri) { mutableStateOf(false) }
    var isSaved by rememberSaveable(imageUri.toString()) { mutableStateOf(false) }
    var saveError by remember(imageUri) { mutableStateOf<String?>(null) }
    val detector = remember(context) { PestDetector(context) }

    var bitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var detections by remember(imageUri) { mutableStateOf<List<PestDetection>?>(null) }
    var errorMessage by remember(imageUri) { mutableStateOf<String?>(null) }

    LaunchedEffect(imageUri, detector) {
        try {

            val image = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(imageUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                } ?: error("Could not open the photo.")
            }

            bitmap = image
            detections = withContext(Dispatchers.Default) {
                detector.detect(image)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = e.localizedMessage ?: "Pest detection failed."
        } catch (e: LinkageError) {
            errorMessage = e.localizedMessage ?: "Could not start the model runtime."
        }
    }

    val results = detections

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.plantcare_pest_title),
            style = MaterialTheme.typography.headlineSmall
        )

        bitmap?.let { image ->
            val preview = remember(image) { image.asImageBitmap() }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(image.width.toFloat() / image.height)
                    .clip(MaterialTheme.shapes.large)
            ) {
                Image(
                    bitmap = preview,
                    contentDescription = stringResource(R.string.diagnosis_selected_image),
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.FillBounds
                )

                Canvas(modifier = Modifier.matchParentSize()) {
                    val scaleX = size.width / image.width
                    val scaleY = size.height / image.height

                    results?.forEach { detection ->
                        val box = detection.box
                        drawRect(
                            color = Color(0xFF22C55E),
                            topLeft = Offset(box.left * scaleX, box.top * scaleY),
                            size = Size(
                                width = (box.right - box.left) * scaleX,
                                height = (box.bottom - box.top) * scaleY
                            ),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
        }

        when {
            errorMessage != null -> {
                Text(
                    text = errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error
                )
            }

            results == null -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.plantcare_scan_analyzing_photo))
            }

            results.isEmpty() -> {
                Text(stringResource(R.string.plantcare_pest_none))
            }

            else -> {
                val percent = remember {
                    NumberFormat.getPercentInstance().apply {
                        maximumFractionDigits = 1
                    }
                }

                Text(
                    text = stringResource(R.string.plantcare_pest_detection_count, results.size),
                    style = MaterialTheme.typography.titleMedium
                )

                results.forEach { detection ->
                    val pestName = detection.label.replaceFirstChar { it.titlecase() }
                    val cropGroup = detection.hostCrop.replaceFirstChar { it.titlecase() }
                    val score = percent.format(detection.confidence.toDouble())

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(pestName, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.plantcare_scan_model_score, score), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = stringResource(R.string.plantcare_pest_dataset_crop, cropGroup),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        val image = bitmap
        if (image != null && results != null) {
            OutlinedButton(
                onClick = {
                    if (isSaving || isSaved) return@OutlinedButton

                    isSaving = true
                    saveError = null

                    saveScope.launch {
                        try {
                            val snapshot = JSONObject().apply {
                                put("version", 1)
                                put("type", "pest")
                                put("imageWidth", image.width)
                                put("imageHeight", image.height)
                                put("detections", JSONArray().apply {
                                    results.forEach { detection ->
                                        val box = detection.box
                                        put(JSONObject().apply {
                                            put("label", detection.label)
                                            put("confidence", detection.confidence.toDouble())
                                            put("hostCrop", detection.hostCrop)
                                            put("left", box.left.toDouble())
                                            put("top", box.top.toDouble())
                                            put("right", box.right.toDouble())
                                            put("bottom", box.bottom.toDouble())
                                        })
                                    }
                                })
                            }

                            historyRepository.saveScan(
                                context = context,
                                sourceImage = imageUri,
                                result = ScanHistoryEntity(
                                    imagePath = "",
                                    diseaseKey = "",
                                    pestSummary = snapshot.toString(),
                                    severity = "",
                                    language = context.resources.configuration.locales[0].language
                                )
                            )
                            isSaved = true
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            saveError = error.localizedMessage ?: context.getString(R.string.plantcare_history_save_failed)
                        } finally {
                            isSaving = false
                        }
                    }
                },
                enabled = !isSaving && !isSaved,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        isSaved -> stringResource(R.string.plantcare_history_saved)
                        isSaving -> stringResource(R.string.plantcare_history_saving)
                        else -> stringResource(R.string.plantcare_history_save)
                    }
                )
            }

            saveError?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
            }
        }
        Button(
            onClick = onNewScan,
            enabled = !isSaving && (errorMessage != null || results != null),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.plantcare_scan_another_photo))
        }
    }
}

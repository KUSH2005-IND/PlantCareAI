package com.armbharat.plantcareai.ui.diagnosis

import com.armbharat.plantcareai.R

import androidx.compose.ui.res.stringResource

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.armbharat.plantcareai.core.database.DatabaseProvider
import com.armbharat.plantcareai.core.database.ScanHistoryEntity
import com.armbharat.plantcareai.data.repository.ScanHistoryRepository
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun ScanHistoryScreen(onAskAi: (ScanHistoryEntity, String) -> Unit) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) {
        ScanHistoryRepository(DatabaseProvider.getDatabase(context).scanHistoryDao())
    }
    val history = remember(repository) { repository.getAllScans() }
    val scans by history.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var selectedId by rememberSaveable { mutableStateOf<Int?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<Int?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val selected = scans?.firstOrNull { it.id == selectedId }

    BackHandler(enabled = selectedId != null) { selectedId = null }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.plantcare_history_title), style = MaterialTheme.typography.headlineSmall)
        deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        val entries = scans
        when {
            entries == null -> CircularProgressIndicator()
            selected != null -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { selectedId = null }) { Text(stringResource(R.string.plantcare_history_back)) }
                    TextButton(enabled = !deleting, onClick = {
                        deleteError = null
                        deleteId = selected.id
                    }) { Text(stringResource(R.string.plantcare_history_delete)) }
                }
                val scanName = selected.pestSummary.snapshot("pest")
                    ?.optJSONArray("detections")
                    ?.optJSONObject(0)
                    ?.optString("label")
                    ?.takeIf { it.isNotBlank() }
                    ?: selected.title()
                FilledTonalButton(onClick = { onAskAi(selected, scanName) }) {
                    Text(stringResource(R.string.diagnosis_chat))
                }
                SavedScanDetail(selected)
            }
            entries.isEmpty() -> Text(stringResource(R.string.plantcare_history_empty))
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries, key = { it.id }) { scan ->
                    Card(onClick = { selectedId = scan.id }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(scan.title(), style = MaterialTheme.typography.titleMedium)
                            Text(DateFormat.getDateTimeInstance().format(Date(scan.createdAt)))
                        }
                    }
                }
            }
        }
    }

    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!deleting) deleteId = null },
            title = { Text(stringResource(R.string.plantcare_history_delete_title)) },
            text = { Text(stringResource(R.string.plantcare_history_delete_description)) },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { deleteId = null }) { Text(stringResource(R.string.plantcare_history_cancel)) }
            },
            confirmButton = {
                TextButton(enabled = !deleting, onClick = {
                    deleting = true
                    scope.launch {
                        try {
                            repository.deleteSavedScan(context, id)
                            selectedId = null
                            deleteId = null
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            deleteError = error.localizedMessage ?: context.getString(R.string.plantcare_history_delete_failed)
                            deleteId = null
                        } finally {
                            deleting = false
                        }
                    }
                }) { Text(stringResource(R.string.plantcare_history_confirm_delete)) }
            }
        )
    }
}

@Composable
private fun SavedScanDetail(scan: ScanHistoryEntity) {
    val pest = remember(scan.pestSummary) { scan.pestSummary.snapshot("pest") }
    val disease = remember(scan.severity) { scan.severity.snapshot("disease") }
    val boxes = pest?.optJSONArray("detections")
    val predictions = disease?.optJSONArray("predictions")
    var showAlternatives by rememberSaveable(scan.id) { mutableStateOf(false) }
    val percent = remember { NumberFormat.getPercentInstance().apply { maximumFractionDigits = 1 } }
    var photoLoaded by remember(scan.imagePath) { mutableStateOf(false) }
    val photo by produceState<Bitmap?>(null, scan.imagePath) {
        value = null
        photoLoaded = false
        value = withContext(Dispatchers.IO) {
            try {
                BitmapFactory.decodeFile(scan.imagePath)
            } catch (_: SecurityException) {
                null
            }
        }
        photoLoaded = true
    }
    val image = photo
    val outline = MaterialTheme.colorScheme.primary

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(scan.title(), style = MaterialTheme.typography.titleLarge)
        }
        item {
            if (image != null) {
                val preview = remember(image) { image.asImageBitmap() }
                Box(Modifier.fillMaxWidth().aspectRatio(image.width.toFloat() / image.height)) {
                    Image(preview, stringResource(R.string.plantcare_history_photo), Modifier.matchParentSize(),
                        contentScale = ContentScale.FillBounds)
                    if (pest != null && boxes != null) {
                        Canvas(Modifier.matchParentSize()) {
                            val width = pest.optInt("imageWidth", image.width).coerceAtLeast(1)
                            val height = pest.optInt("imageHeight", image.height).coerceAtLeast(1)
                            val scaleX = size.width / width
                            val scaleY = size.height / height
                            for (index in 0 until boxes.length()) {
                                val box = boxes.optJSONObject(index) ?: continue
                                val left = box.optDouble("left").toFloat()
                                val top = box.optDouble("top").toFloat()
                                val right = box.optDouble("right").toFloat()
                                val bottom = box.optDouble("bottom").toFloat()
                                if (listOf(left, top, right, bottom).all { it.isFinite() } &&
                                    right > left && bottom > top
                                ) {
                                    drawRect(
                                        outline,
                                        Offset(left * scaleX, top * scaleY),
                                        Size((right - left) * scaleX, (bottom - top) * scaleY),
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (photoLoaded) {
                Text(stringResource(R.string.plantcare_history_photo_missing))
            } else {
                CircularProgressIndicator()
            }
        }
        when {
            boxes != null -> {
                if (boxes.length() == 0) item { Text(stringResource(R.string.plantcare_pest_none)) }
                items(boxes.length()) { index ->
                    val box = boxes.optJSONObject(index)
                    if (box != null) {
                        SavedScore(box.optString("label"), box.optDouble("confidence"), percent,
                            box.optString("hostCrop").takeIf { it.isNotBlank() }?.let {
                                stringResource(R.string.plantcare_pest_dataset_crop, it)
                            })
                    }
                }
            }
            predictions != null -> {
                items(minOf(if (showAlternatives) 3 else 1, predictions.length())) { index ->
                    val prediction = predictions.optJSONObject(index)
                    if (prediction != null) {
                        SavedScore(localizedDiseaseLabel(prediction.optString("diseaseName")),
                            prediction.optDouble("confidence"), percent)
                    }
                }
                if (predictions.length() > 1) {
                    item {
                        TextButton(onClick = { showAlternatives = !showAlternatives }) {
                            Text(stringResource(
                                if (showAlternatives) R.string.plantcare_hide_predictions
                                else R.string.plantcare_show_predictions
                            ))
                        }
                    }
                }
            }
            scan.pestSummary.isNotBlank() -> item { Text(scan.pestSummary) }
        }
        val severity = disease?.optString("severityLevel") ?: scan.severity
        if (severity.isNotBlank()) {
            item {
                Text(stringResource(R.string.plantcare_scan_severity, severityLabel(severity)))
                val damage = disease?.optDouble("damagePercentage")
                if (damage != null && damage.isFinite() && severity != "UNABLE_TO_ESTIMATE") {
                    Text(stringResource(R.string.plantcare_scan_damage, percent.format(damage / 100.0)))
                }
            }
        }
        if (scan.advisoryText.isNotBlank()) {
            item {
                Text(stringResource(R.string.plantcare_history_guidance), style = MaterialTheme.typography.titleMedium)
                Text(scan.advisoryText)
            }
        }
    }
}

@Composable
private fun SavedScore(label: String, score: Double, percent: NumberFormat, detail: String? = null) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (score.isFinite()) Text(stringResource(R.string.plantcare_scan_model_score, percent.format(score)))
            detail?.let { Text(it) }
        }
    }
}

@Composable
private fun ScanHistoryEntity.title(): String =
    when {
        pestSummary.snapshot("pest") != null -> stringResource(R.string.plantcare_history_pest_entry)
        diseaseKey.isNotBlank() -> localizedDiseaseLabel(diseaseKey)
        pestSummary.isNotBlank() -> stringResource(R.string.plantcare_history_pest_entry)
        else -> stringResource(R.string.plantcare_history_entry)
    }

private fun String.displayLabel(): String = replace("___", " · ").replace('_', ' ')

private fun String.snapshot(type: String): JSONObject? =
    try {
        JSONObject(this).takeIf { it.optInt("version") == 1 && it.optString("type") == type }
    } catch (_: org.json.JSONException) {
        null
    }

@Composable
private fun severityLabel(value: String): String = when (value.uppercase()) {
    "LOW" -> stringResource(R.string.plantcare_scan_low)
    "MODERATE" -> stringResource(R.string.plantcare_scan_moderate)
    "SEVERE" -> stringResource(R.string.plantcare_scan_severe)
    "UNABLE_TO_ESTIMATE" -> stringResource(R.string.plantcare_scan_unknown_severity)
    else -> value.displayLabel()
}

@Composable
private fun localizedDiseaseLabel(rawLabel: String): String =
    when (rawLabel.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')) {
        "tomato_early_blight" -> stringResource(R.string.plantcare_label_tomato_early_blight)
        "apple_black_rot" -> stringResource(R.string.plantcare_label_apple_black_rot)
        else -> rawLabel.displayLabel()
    }

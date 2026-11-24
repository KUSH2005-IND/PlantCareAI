package com.armbharat.plantcareai.ui.diagnosis

import android.graphics.BitmapFactory
import android.net.Uri

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

import com.armbharat.plantcareai.R
import com.armbharat.plantcareai.core.common.ImagePreprocessor
import com.armbharat.plantcareai.core.inference.DiseaseClassifier
import com.armbharat.plantcareai.core.inference.DiseasePrediction
import com.armbharat.plantcareai.core.inference.SeverityEstimator
import com.armbharat.plantcareai.core.inference.SeverityResult
import com.armbharat.plantcareai.core.inference.SeverityLevel
import com.armbharat.plantcareai.core.llm.LlamaViewModel
import com.armbharat.plantcareai.core.llm.ModelFileManager

import androidx.compose.runtime.saveable.rememberSaveable
import com.armbharat.plantcareai.core.database.DatabaseProvider
import com.armbharat.plantcareai.core.database.ScanHistoryEntity
import com.armbharat.plantcareai.data.repository.ScanHistoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import java.util.Locale

import com.armbharat.plantcareai.core.llm.currentAiReplyLanguage


@Composable
fun DiagnosisScreen(
    imageUri: Uri,
    onNewScan: () -> Unit,
    onAskAi: (DiseasePrediction) -> Unit
) {

    val context = LocalContext.current
    val historyRepository = remember(context.applicationContext) {
        ScanHistoryRepository(
            DatabaseProvider.getDatabase(context.applicationContext).scanHistoryDao()
        )
    }
    val saveScope = rememberCoroutineScope()
    var isSaving by remember(imageUri) { mutableStateOf(false) }
    var isSaved by rememberSaveable(imageUri.toString()) { mutableStateOf(false) }
    var saveError by remember(imageUri) { mutableStateOf<String?>(null) }

    // Activity-scoped ViewModel shared with AI Assistant
    val llamaViewModel: LlamaViewModel = viewModel()

    val llamaStatus by llamaViewModel.status.collectAsState()
    val llamaRecommendation by llamaViewModel.recommendation.collectAsState()
    val llamaIsLoading by llamaViewModel.isLoading.collectAsState()
    val llamaIsGenerating by llamaViewModel.isGenerating.collectAsState()

    // Start a new diagnosis only if the image is different
    LaunchedEffect(imageUri) {
        llamaViewModel.beginDiagnosis(imageUri.toString())
    }

    val replyLanguage = currentAiReplyLanguage()
    // Automatically load saved model files
    LaunchedEffect(Unit) {

        if (llamaStatus != "Llama model loaded successfully") {

            val files = withContext(Dispatchers.IO) {
                Pair(
                    ModelFileManager.getSavedModelFile(context),
                    ModelFileManager.getSavedTokenizerFile(context)
                )
            }

            val modelFile = files.first
            val tokenizerFile = files.second

            if (modelFile != null && tokenizerFile != null) {

                llamaViewModel.loadModel(
                    modelFile.absolutePath,
                    tokenizerFile.absolutePath
                )
            }
        }
    }

    // Original image
    val originalBitmap = remember(imageUri) {
        try {
            context.contentResolver.openInputStream(imageUri)?.use {
                BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) {
            null
        }
    }

    // Disease prediction
    var prediction by remember(imageUri) {
        mutableStateOf<List<DiseasePrediction>>(emptyList())
    }

    // Severity
    var severityResult by remember(imageUri) {
        mutableStateOf<SeverityResult?>(null)
    }

    var isProcessing by remember(imageUri) {
        mutableStateOf(true)
    }

    var errorMessage by remember(imageUri) {
        mutableStateOf<String?>(null)
    }

    // Process image and run TFLite
    LaunchedEffect(imageUri) {

        isProcessing = true
        errorMessage = null
        prediction = emptyList()
        severityResult = null

        try {

            val result = withContext(Dispatchers.IO) {

                ImagePreprocessor.processImage(context, imageUri)

                val inputBitmap = context.contentResolver
                    .openInputStream(imageUri)
                    ?.use {
                        BitmapFactory.decodeStream(it)
                    } ?: throw IllegalArgumentException(
                    "Unable to read selected image"
                )

                val classifier = DiseaseClassifier(context)

                try {

                    val diseasePrediction =
                        classifier.predict(inputBitmap)

                    val severity =
                        SeverityEstimator.estimate(inputBitmap)

                    Pair(diseasePrediction, severity)

                } finally {

                    classifier.close()
                    inputBitmap.recycle()
                }
            }

            prediction = result.first.take(1)
            severityResult = result.second

        } catch (e: Exception) {

            errorMessage =
                e.message ?: "Disease detection failed."

        } finally {

            isProcessing = false
        }
    }

    // Generate recommendation once for this image
    LaunchedEffect(
        llamaStatus,
        prediction,
        severityResult,
        isProcessing
    ) {

        val topPrediction = prediction.firstOrNull()
        val severity = severityResult

        if (
            !isProcessing &&
            llamaStatus == "Llama model loaded successfully" &&
            topPrediction != null
        ) {

            val rawDisease = topPrediction.diseaseName

            val parts = rawDisease.split(
                "___",
                limit = 2
            )

            val crop = parts.firstOrNull()
                ?.replace("_", " ")
                ?: "Unknown crop"

            val disease = parts.getOrNull(1)
                ?.replace("_", " ")
                ?: rawDisease.replace("_", " ")

            val severityText = when (severity?.severityLevel) {

                SeverityLevel.LOW -> "Low"

                SeverityLevel.MODERATE -> "Moderate"

                SeverityLevel.SEVERE -> "Severe"

                SeverityLevel.UNABLE_TO_ESTIMATE,
                null -> "Unable to estimate"
            }

            val damagePercentage =
                severity?.damagePercentage ?: 0f

            llamaViewModel.generateDiagnosisRecommendation(
                crop = crop,
                disease = disease,
                confidence = topPrediction.confidence * 100f,
                severity = severityText,
                estimatedDamage = damagePercentage,
                language = replyLanguage
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            text = stringResource(R.string.diagnosis_title),
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = stringResource(R.string.diagnosis_selected_image),
            style = MaterialTheme.typography.bodyLarge
        )

        // Selected image
        if (originalBitmap != null) {

            Image(
                bitmap = originalBitmap.asImageBitmap(),
                contentDescription = stringResource(
                    R.string.diagnosis_selected_image
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentScale = ContentScale.Fit
            )

        } else {

            Text(stringResource(R.string.plantcare_scan_photo_error))
        }

        // DISEASE DIAGNOSIS CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                Text(
                    text = stringResource(R.string.diagnosis_predictions),
                    style = MaterialTheme.typography.titleLarge
                )

                when {

                    isProcessing -> {

                        CircularProgressIndicator()

                        Text("Analyzing crop image...")
                    }

                    prediction.isNotEmpty() -> {

                        prediction.forEachIndexed { index, item ->

                            val diseaseName = item.diseaseName
                                .replace("___", " - ")
                                .replace("_", " ")

                            Column(
                                verticalArrangement =
                                    Arrangement.spacedBy(4.dp)
                            ) {

                                Text(
                                    text = diseaseName,
                                    style = MaterialTheme.typography.titleMedium
                                )

                                Text(
                                    text = stringResource(R.string.plantcare_scan_model_score, String.format(Locale.getDefault(), "%.2f%%", item.confidence * 100))
                                )

                                LinearProgressIndicator(
                                    progress = {
                                        item.confidence.coerceIn(0f, 1f)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (index != prediction.lastIndex) {
                                HorizontalDivider()
                            }
                        }

                        Text(
                            text = stringResource(
                                R.string.diagnosis_score_note
                            )
                        )

                        if (prediction.first().confidence < 0.60f) {

                            Text(
                                text = "Low model score. Please capture a clearer leaf image and check again.",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    else -> {

                        Text(
                            errorMessage ?: "Unable to analyze image."
                        )
                    }
                }
            }
        }

        // DISEASE SEVERITY CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                Text(
                    text = stringResource(R.string.diagnosis_severity),
                    style = MaterialTheme.typography.titleLarge
                )

                val severity = severityResult

                if (isProcessing) {

                    Text(stringResource(R.string.plantcare_scan_estimating))

                } else if (
                    severity == null ||
                    severity.severityLevel ==
                    SeverityLevel.UNABLE_TO_ESTIMATE
                ) {

                    Text(
                        text = stringResource(
                            R.string.diagnosis_severity_unavailable
                        )
                    )

                } else {

                    Text(
                        text = stringResource(R.string.plantcare_scan_damage, String.format(Locale.getDefault(), "%.2f%%", severity.damagePercentage))
                    )

                    val severityText = when (severity.severityLevel) {

                        SeverityLevel.LOW -> stringResource(R.string.plantcare_scan_low)

                        SeverityLevel.MODERATE -> stringResource(R.string.plantcare_scan_moderate)

                        SeverityLevel.SEVERE -> stringResource(R.string.plantcare_scan_severe)

                        SeverityLevel.UNABLE_TO_ESTIMATE ->
                            stringResource(R.string.plantcare_scan_unknown_severity)
                    }

                    val severityColor = when (severity.severityLevel) {

                        SeverityLevel.LOW ->
                            MaterialTheme.colorScheme.primary

                        SeverityLevel.MODERATE ->
                            MaterialTheme.colorScheme.tertiary

                        SeverityLevel.SEVERE ->
                            MaterialTheme.colorScheme.error

                        SeverityLevel.UNABLE_TO_ESTIMATE ->
                            MaterialTheme.colorScheme.onSurface
                    }

                    Text(
                        text = stringResource(R.string.plantcare_scan_severity, severityText),
                        style = MaterialTheme.typography.titleMedium,
                        color = severityColor
                    )

                    LinearProgressIndicator(
                        progress = {
                            (severity.damagePercentage / 100f)
                                .coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = severityColor
                    )
                }

                Text(
                    text = stringResource(
                        R.string.diagnosis_severity_note
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // PERSISTENT AI RECOMMENDATION CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                Text(
                    text = stringResource(
                        R.string.diagnosis_recommendation_title
                    ),
                    style = MaterialTheme.typography.titleLarge
                )

                when {

                    prediction.isEmpty() || isProcessing -> {

                        Text(stringResource(R.string.plantcare_scan_waiting))
                    }

                    llamaIsLoading -> {

                        CircularProgressIndicator()

                        Text(stringResource(R.string.plantcare_scan_loading_ai))
                    }

                    llamaStatus != "Llama model loaded successfully" -> {

                        Text(
                            "AI model unavailable: $llamaStatus",
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    llamaRecommendation.isNotBlank() -> {

                        Text(llamaRecommendation)
                    }

                    llamaIsGenerating -> {

                        CircularProgressIndicator()

                        Text(
                            text = stringResource(
                                R.string.diagnosis_recommendation_generating
                            )
                        )
                    }

                    else -> {

                        Text(
                            text = stringResource(
                                R.string.diagnosis_recommendation_preparing
                            )
                        )
                    }
                }

                Text(
                    text = stringResource(
                        R.string.diagnosis_recommendation_note
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedButton(
            onClick = {
                if (isSaving || isSaved) return@OutlinedButton

                val topPrediction = prediction.firstOrNull() ?: return@OutlinedButton
                val diagnosis = JSONObject().apply {
                    put("version", 1)
                    put("type", "disease")
                    put("predictions", JSONArray().apply {
                        prediction.forEach { item ->
                            put(JSONObject().apply {
                                put("diseaseName", item.diseaseName)
                                put("confidence", item.confidence.toDouble())
                            })
                        }
                    })
                    severityResult?.let { severity ->
                        put("severityLevel", severity.severityLevel.name)
                        put("damagePercentage", severity.damagePercentage.toDouble())
                    }
                }

                val scan = ScanHistoryEntity(
                    imagePath = "",
                    diseaseKey = topPrediction.diseaseName,
                    pestSummary = "",
                    severity = diagnosis.toString(),
                    advisoryText = llamaRecommendation,
                    language = context.resources.configuration.locales[0].language
                )

                isSaving = true
                saveError = null
                saveScope.launch {
                    try {
                        historyRepository.saveScan(context.applicationContext, imageUri, scan)
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
            modifier = Modifier.fillMaxWidth(),
            enabled = !isProcessing && prediction.isNotEmpty() &&
                    !llamaIsGenerating && !isSaving && !isSaved
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

        // CHAT WITH AI
        Button(
            onClick = {
                prediction.firstOrNull()?.let(onAskAi)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isProcessing &&
                    prediction.isNotEmpty() &&
                    llamaStatus == "Llama model loaded successfully" &&
                    !llamaIsGenerating
        ) {

            Text(
                text = stringResource(R.string.diagnosis_chat)
            )
        }

        // SCAN ANOTHER CROP
        OutlinedButton(
            onClick = onNewScan,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isProcessing
        ) {

            Text(
                text = stringResource(R.string.scan_another_leaf)
            )
        }
    }
}

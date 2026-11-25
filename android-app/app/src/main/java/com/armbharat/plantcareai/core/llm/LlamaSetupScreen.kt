package com.armbharat.plantcareai.core.llm

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import com.armbharat.plantcareai.R
import com.armbharat.plantcareai.core.database.TreatmentEntity
import com.armbharat.plantcareai.core.database.DatabaseProvider
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun LlamaSetupScreen(
    llamaViewModel: LlamaViewModel,
    treatment: TreatmentEntity? = null,
    scanDiseaseKey: String? = null,
    scanDiseaseConfidence: Float? = null,
    scanRegion: String? = null,
    savedScanName: String? = null,
    savedPhotoPath: String? = null
) {
    val status by llamaViewModel.status.collectAsState()
    val response by llamaViewModel.response.collectAsState()
    val isLoading by llamaViewModel.isLoading.collectAsState()
    val isGenerating by llamaViewModel.isGenerating.collectAsState()

    val modelReady = status == "Llama model loaded successfully"
    val replyLanguage = currentAiReplyLanguage()
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val languageCode = ConfigurationCompat
        .getLocales(LocalConfiguration.current)[0]?.language ?: "en"
    val voiceLocale = Locale.forLanguageTag("$languageCode-IN")

    val transcriber = remember(applicationContext) {
        WhisperTranscriber(applicationContext)
    }

    var draft by rememberSaveable { mutableStateOf("") }
    var currentQuestion by rememberSaveable { mutableStateOf("") }
    var previousMessages by rememberSaveable {
        mutableStateOf(arrayListOf<String>())
    }

    var isPreparingReply by remember { mutableStateOf(false) }
    var localReply by rememberSaveable { mutableStateOf("") }
    val emptyReplyText = when (languageCode) {
        "hi" -> "ऑफ़लाइन मॉडल से जवाब नहीं मिला। सवाल छोटा करके फिर भेजें।"
        "pa" -> "ਆਫ਼ਲਾਈਨ ਮਾਡਲ ਤੋਂ ਜਵਾਬ ਨਹੀਂ ਮਿਲਿਆ। ਸਵਾਲ ਛੋਟਾ ਕਰਕੇ ਮੁੜ ਭੇਜੋ।"
        "mr" -> "ऑफलाइन मॉडेलकडून उत्तर मिळाले नाही. प्रश्न छोटा करून पुन्हा पाठवा."
        "ta" -> "ஆஃப்லைன் மாதிரி பதிலளிக்கவில்லை. கேள்வியைச் சுருக்கி மீண்டும் அனுப்பவும்."
        else -> "The offline model returned no reply. Try a shorter question."
    }
    val visibleReply = response.takeUnless {
        it.isBlank() || (localReply.isNotBlank() && it.startsWith("Generation failed:"))
    }.orEmpty().ifBlank { localReply }
    val scanLabel = savedScanName ?: scanDiseaseKey?.replace("___", " · ")?.replace('_', ' ')
    val thumbnail by produceState<Bitmap?>(null, savedPhotoPath) {
        value = null
        val path = savedPhotoPath ?: return@produceState
        value = withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                return@withContext null
            }
            options.inSampleSize = 1
            while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 256) {
                options.inSampleSize *= 2
            }
            options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(path, options)
        }
    }
    val scoreLabel = scanDiseaseConfidence?.let {
        NumberFormat.getPercentInstance(voiceLocale).format(it.toDouble())
    }

    var isDictating by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf<TextToSpeech?>(null) }
    var speakerInitResult by remember { mutableStateOf<Int?>(null) }
    var speakNextReply by remember { mutableStateOf(false) }

    fun showMessage(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun startDictation() {
        if (isDictating || llamaViewModel.isGenerating.value) return

        speaker?.stop()
        isDictating = true

        scope.launch {
            try {
                val transcript = transcriber.recordAndTranscribe()
                if (transcript.isBlank()) {
                    showMessage("No speech detected. Try again.")
                } else {
                    draft = listOf(draft.trimEnd(), transcript)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(e.message ?: "Dictation failed.")
            } catch (e: LinkageError) {
                showMessage("Whisper could not start: ${e.message}")
            } finally {
                isDictating = false
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startDictation()
        } else {
            showMessage("Allow microphone access to use dictation.")
        }
    }

    DisposableEffect(applicationContext) {
        val engine = TextToSpeech(applicationContext) { result ->
            speakerInitResult = result
        }
        speaker = engine

        onDispose {
            engine.stop()
            engine.shutdown()
        }
    }

    LaunchedEffect(isGenerating, response, speakNextReply, speakerInitResult, voiceLocale) {
        if (!speakNextReply || llamaViewModel.isGenerating.value) {
            return@LaunchedEffect
        }

        val completedReply = llamaViewModel.response.value.trim()
        if (completedReply.isEmpty()) return@LaunchedEffect
        val initResult = speakerInitResult ?: return@LaunchedEffect
        val engine = speaker ?: return@LaunchedEffect
        speakNextReply = false

        if (completedReply.startsWith("Generation failed:")) return@LaunchedEffect

        if (initResult == TextToSpeech.SUCCESS &&
            engine.setLanguage(voiceLocale) >= TextToSpeech.LANG_AVAILABLE
        ) {
            val result = engine.speak(
                completedReply,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "assistant-reply"
            )
            if (result == TextToSpeech.ERROR) {
                showMessage("Could not play the reply.")
            }
        } else {
            showMessage("Read-aloud voice is unavailable for this language.")
        }
    }

    val hasCurrentTurn = currentQuestion.isNotBlank()
    val hasMessages = hasCurrentTurn || previousMessages.isNotEmpty()

    fun sendQuestion(text: String) {
        val question = text.trim()
        if (question.isBlank() || !modelReady || llamaViewModel.isGenerating.value ||
            isDictating || isPreparingReply
        ) return
        if (hasCurrentTurn) {
            previousMessages = ArrayList(previousMessages).apply {
                add(currentQuestion)
                add(visibleReply)
            }
        }

        currentQuestion = question
        draft = ""
        speaker?.stop()
        localReply = ""

        isPreparingReply = true
        scope.launch {
            try {
                val record = if (scanDiseaseKey == null) {
                    treatment
                } else if ((scanDiseaseConfidence ?: 0f) >= 0.60f) {
                    try {
                        DatabaseProvider.getDatabase(applicationContext)
                            .treatmentDao()
                            .getTreatmentByRegion(
                                scanDiseaseKey,
                                scanRegion?.takeIf { it.isNotBlank() } ?: "ALL"
                            )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    null
                }
                val prompt = if (scanLabel == null) {
                    question
                } else {
                    """
                    Scan prediction: $scanLabel (${scoreLabel ?: "score unavailable"}).
                    Question: $question
                    Use the scan as a tentative result and follow the database guidance.
                    """.trimIndent()
                }
                if (record != null && scanLabel != null &&
                    languageCode in setOf("en", "hi")
                ) {
                    val hindi = languageCode == "hi"
                    val heading = if (hindi) "स्थानीय डेटाबेस की सलाह" else "Local database guidance"
                    val guidance = listOf(
                        if (hindi) record.organicRemedyHi else record.organicRemedyEn,
                        if (hindi) record.preventionHi else record.preventionEn
                    ).filter { it.isNotBlank() }.distinct()
                    if (guidance.isNotEmpty()) {
                        localReply = (listOf(heading, scanLabel) + guidance)
                            .joinToString("\n\n")
                    }
                }

                if (scanLabel != null && !llamaViewModel.isGenerating.value) {
                    llamaViewModel.resetContext()
                }
                llamaViewModel.generateTreatmentResponse(
                    prompt = prompt,
                    treatment = record,
                    language = replyLanguage
                )
                speakNextReply = true
            } finally {
                isPreparingReply = false
            }
        }
    }

    LaunchedEffect(currentQuestion, isGenerating) {
        if (hasCurrentTurn) {
            listState.scrollToItem(previousMessages.size + 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.nav_assistant),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge
            )

            if (hasMessages) {
                TextButton(
                    enabled = !isGenerating && !isDictating && !isPreparingReply,
                    onClick = {
                        speakNextReply = false
                        speaker?.stop()
                        llamaViewModel.resetContext()
                        localReply = ""
                        previousMessages = arrayListOf()
                        currentQuestion = ""
                        draft = ""
                    }
                ) {
                    Text(stringResource(R.string.assistant_reset))
                }
            }
        }

        HorizontalDivider()

        if (scanLabel != null) {
            Surface(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    thumbnail?.let { photo ->
                        Image(
                            bitmap = photo.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(52.dp).clip(MaterialTheme.shapes.small)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            stringResource(R.string.diagnosis_title),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            listOfNotNull(scanLabel, scoreLabel).joinToString("  ·  "),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }

        if (isLoading || isDictating || isPreparingReply) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (!hasMessages) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(
                            when {
                                modelReady -> R.string.assistant_ask_title
                                isLoading -> R.string.assistant_model_loading
                                else -> R.string.assistant_model_not_loaded
                            }
                        ),
                        style = MaterialTheme.typography.headlineSmall
                    )

                    if (modelReady) {
                        listOf(
                            stringResource(R.string.plantcare_quick_check),
                            stringResource(R.string.plantcare_quick_spread),
                            stringResource(R.string.plantcare_quick_next)
                        )
                            .forEach { question ->
                                OutlinedButton(
                                    onClick = { sendQuestion(question) },
                                    enabled = !isPreparingReply && !isGenerating && !isDictating,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(question)
                                }
                            }
                    }

                }
            }
        } else {
            val waitingText = if (isGenerating || isPreparingReply) {
                stringResource(R.string.assistant_generating)
            } else {
                emptyReplyText
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(previousMessages.size) { index ->
                    ChatBubble(previousMessages[index], fromUser = index % 2 == 0)
                }

                if (hasCurrentTurn) {
                    item { ChatBubble(currentQuestion, fromUser = true) }
                    item { ChatBubble(
                        (if (isPreparingReply) "" else if (isGenerating) response else visibleReply)
                            .ifBlank { waitingText },
                        fromUser = false
                    ) }
                }
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                enabled = modelReady && !isGenerating && !isDictating && !isPreparingReply,
                placeholder = {
                    if (isDictating) {
                        Text("Recording / transcribing…")
                    } else {
                        Text(stringResource(R.string.assistant_prompt))
                    }
                },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4
            )

            IconButton(
                enabled = modelReady && !isGenerating && !isDictating && !isPreparingReply,
                onClick = {
                    val permission = Manifest.permission.RECORD_AUDIO
                    if (ContextCompat.checkSelfPermission(context, permission) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        startDictation()
                    } else {
                        micPermissionLauncher.launch(permission)
                    }
                }
            ) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_btn_speak_now),
                    contentDescription = "Voice typing",
                    modifier = Modifier.size(24.dp)
                )
            }

            if (isGenerating) {
                TextButton(
                    onClick = {
                        speakNextReply = false
                        speaker?.stop()
                        llamaViewModel.stopGeneration()
                    }
                ) {
                    Text(stringResource(R.string.assistant_stop))
                }
            } else {
                FilledIconButton(
                    enabled = modelReady && !isDictating && !isPreparingReply && draft.isNotBlank(),
                    onClick = { sendQuestion(draft) }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = stringResource(R.string.assistant_generate)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(text: String, fromUser: Boolean) {
    if (text.isBlank()) return

    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (fromUser) 20.dp else 6.dp,
        bottomEnd = if (fromUser) 6.dp else 20.dp
    )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (fromUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(if (fromUser) 0.78f else 0.9f),
            shape = shape,
            color = if (fromUser) colors.primary else colors.surfaceContainerHigh,
            contentColor = if (fromUser) colors.onPrimary else colors.onSurface
        ) {
            Text(
                text = text.trim(),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

package com.armbharat.plantcareai

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.armbharat.plantcareai.core.llm.LlamaSetupScreen
import com.armbharat.plantcareai.core.llm.LlamaViewModel
import com.armbharat.plantcareai.core.llm.ModelFileManager
import com.armbharat.plantcareai.data.repository.TreatmentSeeder
import com.armbharat.plantcareai.ui.camera.CameraScreen
import com.armbharat.plantcareai.ui.diagnosis.DiagnosisScreen
import com.armbharat.plantcareai.ui.home.HomeScreen
import com.armbharat.plantcareai.ui.settings.AppearanceMode
import com.armbharat.plantcareai.ui.settings.SettingsScreen
import com.armbharat.plantcareai.ui.theme.PlantCareAITheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.armbharat.plantcareai.ui.diagnosis.PestResultContent
import com.armbharat.plantcareai.ui.diagnosis.ScanHistoryScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat

class MainActivity : AppCompatActivity() {

    private fun applyAppearance(mode: AppearanceMode) {
        delegate.localNightMode = when (mode) {
            AppearanceMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            AppearanceMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppearanceMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settings = getSharedPreferences("plantcare_settings", MODE_PRIVATE)
        val savedAppearance = AppearanceMode.entries.firstOrNull {
            it.name == settings.getString("appearance", null)
        } ?: AppearanceMode.SYSTEM

        applyAppearance(savedAppearance)
        enableEdgeToEdge()

        lifecycleScope.launch {
            try {
                TreatmentSeeder.seedDatabase(applicationContext)
                Log.d("PlantCareAI", "Treatment database seeded successfully")
            } catch (e: Exception) {
                Log.e("PlantCareAI", "Treatment database seeding failed", e)
            }
        }

        setContent {
            var currentScreen by rememberSaveable { mutableStateOf("home") }
            var selectedTab by rememberSaveable { mutableStateOf("home") }
            var capturedImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
            var nextScanIsPest by rememberSaveable { mutableStateOf(false) }
            var assistantDiseaseKey by rememberSaveable { mutableStateOf<String?>(null) }
            var assistantDiseaseConfidence by rememberSaveable { mutableStateOf<Float?>(null) }
            var assistantSavedScanId by rememberSaveable { mutableStateOf<Int?>(null) }
            var assistantSavedScanName by rememberSaveable { mutableStateOf<String?>(null) }
            var assistantSavedPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
            var assistantScanId by rememberSaveable { mutableStateOf(0) }
            var resultIsPest by rememberSaveable { mutableStateOf(false) }
            var farmingRegion by rememberSaveable {
                mutableStateOf(settings.getString("farming_region", null))
            }
            var appearanceMode by rememberSaveable {
                mutableStateOf(savedAppearance)
            }

            val languageCode = ConfigurationCompat
                .getLocales(LocalConfiguration.current)[0]?.language ?: "en"

            val llamaViewModel: LlamaViewModel = viewModel()
            val screenStateHolder = rememberSaveableStateHolder()

            val goHome: () -> Unit = {
                selectedTab = "home"
                currentScreen = "home"
            }

            val galleryLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.PickVisualMedia()
            ) { uri ->
                if (uri != null) {
                    resultIsPest = nextScanIsPest
                    assistantDiseaseKey = null
                    assistantDiseaseConfidence = null
                    assistantSavedScanId = null
                    assistantSavedScanName = null
                    assistantSavedPhotoPath = null
                    capturedImageUri = uri
                    selectedTab = "output"
                    currentScreen = "diagnosis"
                }
            }

            LaunchedEffect(llamaViewModel) {
                try {
                    val (model, tokenizer) = withContext(Dispatchers.IO) {
                        ModelFileManager.prepareBundledModel(applicationContext)
                    }

                    llamaViewModel.loadModel(
                        model.absolutePath,
                        tokenizer.absolutePath
                    )
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.e("PlantCareAI", "Could not prepare bundled Assistant files", failure)
                    android.widget.Toast.makeText(
                        applicationContext,
                        "Could not prepare the offline Assistant. Check available storage.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }

            BackHandler(
                enabled = currentScreen == "settings",
                onBack = goHome
            )

            val systemDarkTheme = isSystemInDarkTheme()
            val useDarkTheme = when (appearanceMode) {
                AppearanceMode.SYSTEM -> systemDarkTheme
                AppearanceMode.LIGHT -> false
                AppearanceMode.DARK -> true
            }

            SideEffect {
                val bars = WindowCompat.getInsetsController(window, window.decorView)
                bars.isAppearanceLightStatusBars = !useDarkTheme
                bars.isAppearanceLightNavigationBars = !useDarkTheme
            }

            PlantCareAITheme(darkTheme = useDarkTheme) {
                Scaffold(
                    bottomBar = {
                        if (currentScreen != "settings") {
                            FloatingBottomBar(
                                selectedTab = selectedTab,
                                onTabSelected = { tab ->
                                    selectedTab = tab
                                    currentScreen = when (tab) {
                                        "scan" -> {
                                            nextScanIsPest = false
                                            "camera"
                                        }
                                        "assistant" -> "llama"
                                        "output" -> "scan_history"
                                        else -> "home"
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when (currentScreen) {
                            "home" -> HomeScreen(
                                onScanClick = {
                                    nextScanIsPest = false
                                    selectedTab = "scan"
                                    currentScreen = "camera"
                                },
                                onGalleryClick = {
                                    nextScanIsPest = false
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onSettingsClick = { currentScreen = "settings" },
                                onPestCameraClick = {
                                    nextScanIsPest = true
                                    selectedTab = "scan"
                                    currentScreen = "camera"
                                },
                                onPestGalleryClick = {
                                    nextScanIsPest = true
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )

                            "settings" -> SettingsScreen(
                                languageCode = languageCode,
                                farmingRegion = farmingRegion,
                                appearanceMode = appearanceMode,
                                onLanguageChange = { code ->
                                    AppCompatDelegate.setApplicationLocales(
                                        LocaleListCompat.forLanguageTags(code)
                                    )
                                },
                                onRegionChange = { region ->
                                    farmingRegion = region
                                    settings.edit()
                                        .putString("farming_region", region)
                                        .apply()
                                },
                                onAppearanceChange = { mode ->
                                    appearanceMode = mode
                                    settings.edit()
                                        .putString("appearance", mode.name)
                                        .apply()
                                    applyAppearance(mode)
                                },
                                onBackClick = goHome
                            )

                            "camera" -> CameraScreen(
                                onBackClick = goHome,
                                onPhotoCaptured = { path ->
                                    resultIsPest = nextScanIsPest
                                    assistantDiseaseKey = null
                                    assistantDiseaseConfidence = null
                                    assistantSavedScanId = null
                                    assistantSavedScanName = null
                                    assistantSavedPhotoPath = null
                                    capturedImageUri = Uri.fromFile(File(path))
                                    selectedTab = "output"
                                    currentScreen = "diagnosis"
                                }
                            )

                            "diagnosis" -> capturedImageUri?.let { uri ->
                                if (resultIsPest) {
                                    PestResultContent(
                                        imageUri = uri,
                                        onNewScan = {
                                            nextScanIsPest = true
                                            selectedTab = "scan"
                                            currentScreen = "camera"
                                        },
                                        onOpenSettings = {
                                            selectedTab = "home"
                                            currentScreen = "settings"
                                        }
                                    )
                                } else {
                                    DiagnosisScreen(
                                        imageUri = uri,
                                        onNewScan = {
                                            nextScanIsPest = false
                                            capturedImageUri = null
                                            selectedTab = "scan"
                                            currentScreen = "camera"
                                        },
                                        onAskAi = { prediction ->
                                            if (assistantDiseaseKey == null) assistantScanId++
                                            assistantDiseaseKey = prediction.diseaseName
                                            assistantDiseaseConfidence = prediction.confidence
                                            selectedTab = "assistant"
                                            currentScreen = "llama"
                                            assistantSavedScanId = null
                                            assistantSavedScanName = null
                                            assistantSavedPhotoPath = null
                                        }
                                    )
                                }
                            }

                            "llama" -> {
                                val chatKey = when {
                                    assistantSavedScanId != null -> "assistant_saved_$assistantSavedScanId"
                                    assistantDiseaseKey != null -> "assistant_scan_$assistantScanId"
                                    else -> "assistant_chat"
                                }

                                screenStateHolder.SaveableStateProvider(chatKey) {
                                    LlamaSetupScreen(
                                        llamaViewModel = llamaViewModel,
                                        scanDiseaseKey = assistantDiseaseKey,
                                        scanDiseaseConfidence = assistantDiseaseConfidence,
                                        scanRegion = farmingRegion,
                                        savedScanName = assistantSavedScanName,
                                        savedPhotoPath = assistantSavedPhotoPath
                                    )
                                }
                            }

                            "output_empty" -> OutputEmptyScreen(
                                onScanClick = {
                                    selectedTab = "scan"
                                    currentScreen = "camera"
                                }
                            )
                            "scan_history" -> ScanHistoryScreen(
                                onAskAi = { scan, scanName ->
                                    assistantSavedScanId = scan.id
                                    assistantSavedScanName = scanName
                                    assistantSavedPhotoPath = scan.imagePath

                                    assistantDiseaseKey = scan.diseaseKey.takeIf { it.isNotBlank() }
                                    assistantDiseaseConfidence = runCatching {
                                        org.json.JSONObject(scan.severity)
                                            .getJSONArray("predictions")
                                            .getJSONObject(0)
                                            .getDouble("confidence")
                                            .toFloat()
                                    }.getOrNull()?.takeIf { it.isFinite() }

                                    selectedTab = "assistant"
                                    currentScreen = "llama"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OutputEmptyScreen(onScanClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            14.dp,
            Alignment.CenterVertically
        )
    ) {
        Icon(
            imageVector = Icons.Outlined.List,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = stringResource(R.string.output_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.output_empty_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Button(onClick = onScanClick) {
            Text(stringResource(R.string.scan_leaf))
        }
    }
}

@Composable
private fun FloatingBottomBar(
    selectedTab: String,
    onTabSelected: (String) -> Unit
) {
    val tabs = listOf(
        Triple("home", stringResource(R.string.nav_home), Icons.Outlined.Home),
        Triple("scan", stringResource(R.string.nav_scan), Icons.Outlined.Search),
        Triple("assistant", stringResource(R.string.nav_assistant), Icons.Outlined.Email),
        Triple("output", stringResource(R.string.nav_output), Icons.Outlined.List)
    )
    val colors = MaterialTheme.colorScheme
    val pillShape = RoundedCornerShape(32.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth(),
            shape = pillShape,
            color = colors.surface,
            border = BorderStroke(1.dp, colors.outlineVariant),
            shadowElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .selectableGroup()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { (tab, label, icon) ->
                    val selected = selectedTab == tab
                    val tint = if (selected) colors.onPrimary else colors.onSurfaceVariant

                    Surface(
                        modifier = Modifier
                            .weight(if (selected) 2.2f else 1f)
                            .clip(pillShape)
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                onClick = { onTabSelected(tab) }
                            ),
                        shape = pillShape,
                        color = if (selected) colors.primary else colors.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(
                                6.dp,
                                Alignment.CenterHorizontally
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = if (selected) null else label,
                                modifier = Modifier.size(20.dp),
                                tint = tint
                            )

                            if (selected) {
                                Text(
                                    text = label,
                                    color = tint,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

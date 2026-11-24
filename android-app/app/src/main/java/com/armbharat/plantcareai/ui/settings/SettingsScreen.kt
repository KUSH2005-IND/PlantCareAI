package com.armbharat.plantcareai.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.armbharat.plantcareai.R

enum class AppearanceMode { SYSTEM, LIGHT, DARK }

private val languageOptions = listOf(
    "en" to "English",
    "hi" to "हिन्दी",
    "pa" to "ਪੰਜਾਬੀ",
    "mr" to "मराठी",
    "ta" to "தமிழ்"
)

@Composable
fun SettingsScreen(
    languageCode: String,
    farmingRegion: String?,
    appearanceMode: AppearanceMode,
    onLanguageChange: (String) -> Unit,
    onRegionChange: (String) -> Unit,
    onAppearanceChange: (AppearanceMode) -> Unit,
    onBackClick: () -> Unit
) {
    val systemLabel = stringResource(R.string.settings_system)
    val appearanceOptions = listOf(
        AppearanceMode.SYSTEM to systemLabel,
        AppearanceMode.LIGHT to stringResource(R.string.settings_light),
        AppearanceMode.DARK to stringResource(R.string.settings_dark)
    )
    val regionOptions = listOf(
        "Uttar Pradesh" to stringResource(R.string.region_uttar_pradesh),
        "Punjab" to stringResource(R.string.region_punjab),
        "Haryana" to stringResource(R.string.region_haryana),
        "Rajasthan" to stringResource(R.string.region_rajasthan),
        "Bihar" to stringResource(R.string.region_bihar),
        "Madhya Pradesh" to stringResource(R.string.region_madhya_pradesh),
        "Maharashtra" to stringResource(R.string.region_maharashtra),
        "Karnataka" to stringResource(R.string.region_karnataka),
        "Tamil Nadu" to stringResource(R.string.region_tamil_nadu),
        "Other" to stringResource(R.string.region_other)
    )

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Filled.ArrowBack, stringResource(R.string.settings_back))
            }
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
        }

        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column {
                    SettingsDropdownRow(
                        stringResource(R.string.settings_language),
                        languageCode, languageOptions, "English", onLanguageChange
                    )
                    SettingsDivider()
                    SettingsDropdownRow(
                        stringResource(R.string.settings_appearance),
                        appearanceMode, appearanceOptions, systemLabel, onAppearanceChange
                    )
                    SettingsDivider()
                    SettingsDropdownRow(
                        stringResource(R.string.settings_region),
                        farmingRegion, regionOptions,
                        stringResource(R.string.settings_select_region), onRegionChange
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun <T> SettingsDropdownRow(
    title: String,
    selected: T?,
    options: List<Pair<T, String>>,
    fallbackLabel: String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { (value, _) -> value == selected }
        ?.second ?: fallbackLabel
    val colors = MaterialTheme.colorScheme

    Box {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
                .clickable(role = Role.Button) { expanded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    selectedLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }
            Icon(Icons.Filled.KeyboardArrowDown, null, tint = colors.onSurfaceVariant)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    trailingIcon = {
                        if (value == selected) {
                            Icon(
                                Icons.Filled.Check,
                                stringResource(R.string.settings_selected),
                                tint = colors.primary
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    }
                )
            }
        }
    }
}

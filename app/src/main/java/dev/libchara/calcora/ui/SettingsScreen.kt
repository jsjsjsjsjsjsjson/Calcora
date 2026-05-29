package dev.libchara.calcora.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import dev.libchara.calcora.R
import dev.libchara.calcora.data.AngleUnit
import dev.libchara.calcora.data.AppLanguage
import dev.libchara.calcora.data.AppSettings
import dev.libchara.calcora.data.ThemeMode
import dev.libchara.calcora.engine.EvalMode
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onClearHistory: () -> Unit,
    onResetSession: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)

        SettingsCard(stringResource(R.string.settings_lang)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AppLanguage.entries.forEach { item ->
                    FilterChip(selected = settings.language == item, onClick = { onSettingsChange(settings.copy(language = item)) },
                        label = { Text(item.label) })
                }
            }
        }

        SettingsCard(stringResource(R.string.settings_theme)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(ThemeMode.System to R.string.theme_system, ThemeMode.Light to R.string.theme_light, ThemeMode.Dark to R.string.theme_dark).forEach { (mode, res) ->
                    FilterChip(selected = settings.themeMode == mode, onClick = { onSettingsChange(settings.copy(themeMode = mode)) },
                        label = { Text(stringResource(res)) })
                }
            }
        }

        SettingsCard(stringResource(R.string.settings_angle)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AngleUnit.entries.forEach { item ->
                    FilterChip(selected = settings.angleUnit == item, onClick = { onSettingsChange(settings.copy(angleUnit = item)) },
                        label = { Text(if (item == AngleUnit.Rad) stringResource(R.string.angle_rad) else stringResource(R.string.angle_deg)) })
                }
            }
        }

        SettingsCard(stringResource(R.string.settings_mode)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EvalMode.entries.forEach { item ->
                    FilterChip(selected = settings.defaultEvalMode == item, onClick = { onSettingsChange(settings.copy(defaultEvalMode = item)) },
                        label = { Text(stringResource(when (item) { EvalMode.Auto -> R.string.eval_auto; EvalMode.Exact -> R.string.eval_exact; EvalMode.Approx -> R.string.eval_approx; EvalMode.RawXcas -> R.string.eval_raw })) })
                }
            }
        }

        SettingsCard(stringResource(R.string.settings_precision)) {
            Text("${settings.precision} digits", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(value = settings.precision.toFloat(), onValueChange = { onSettingsChange(settings.copy(precision = it.roundToInt().coerceIn(4, 20))) }, valueRange = 4f..20f, steps = 15)
        }



        SettingsCard(stringResource(R.string.settings_syntax_hl)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(selected = settings.syntaxHighlighting, onClick = { onSettingsChange(settings.copy(syntaxHighlighting = true)) },
                    label = { Text("On") })
                FilterChip(selected = !settings.syntaxHighlighting, onClick = { onSettingsChange(settings.copy(syntaxHighlighting = false)) },
                    label = { Text("Off") })
            }
        }

        SettingsCard(stringResource(R.string.settings_autocomplete)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(selected = settings.autocompleteEnabled, onClick = { onSettingsChange(settings.copy(autocompleteEnabled = true)) },
                    label = { Text("On") })
                FilterChip(selected = !settings.autocompleteEnabled, onClick = { onSettingsChange(settings.copy(autocompleteEnabled = false)) },
                    label = { Text("Off") })
            }
        }

        SettingsCard(stringResource(R.string.settings_history_limit)) {
            Text("${settings.historyLimit}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(value = settings.historyLimit.toFloat(), onValueChange = { onSettingsChange(settings.copy(historyLimit = it.roundToInt().coerceIn(20, 200))) }, valueRange = 20f..200f, steps = 17)
        }

        SettingsCard(stringResource(R.string.settings_about), subtitle = "v0.2.0") {
            Text(stringResource(R.string.about_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onResetSession, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.settings_reset_session)) }
                Button(onClick = onClearHistory, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.settings_clear_history)) }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

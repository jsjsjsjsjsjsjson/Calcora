package dev.libchara.calcora.data

import android.content.Context
import dev.libchara.calcora.engine.EvalMode

enum class ThemeMode(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark")
}

enum class AppLanguage(val label: String, val giacCode: Int) {
    System("System", -1),
    English("English", 2),
    Chinese("中文", 8)
}
enum class AngleUnit(val label: String) {
    Rad("Rad"),
    Deg("Deg")
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val angleUnit: AngleUnit = AngleUnit.Rad,
    val precision: Int = 12,
    val defaultEvalMode: EvalMode = EvalMode.Auto,
    val language: AppLanguage = AppLanguage.System,
    val autocompleteEnabled: Boolean = true,
    val syntaxHighlighting: Boolean = true,
    val historyLimit: Int = 64
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        themeMode = enumValueOrDefault(prefs.getString("theme", null), ThemeMode.System),
        angleUnit = enumValueOrDefault(prefs.getString("angle", null), AngleUnit.Rad),
        precision = prefs.getInt("precision", 12).coerceIn(4, 20),
        defaultEvalMode = EvalMode.fromName(prefs.getString("mode", null)),
        language = AppLanguage.entries.firstOrNull { it.name == prefs.getString("lang", "System") } ?: AppLanguage.System,
        autocompleteEnabled = prefs.getBoolean("autocomplete", true),
        syntaxHighlighting = prefs.getBoolean("syntaxHl", true),
        historyLimit = prefs.getInt("historyLimit", 64).coerceIn(20, 200)
    )

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString("theme", settings.themeMode.name)
            .putString("angle", settings.angleUnit.name)
            .putInt("precision", settings.precision)
            .putString("mode", settings.defaultEvalMode.name)
            .putString("lang", settings.language.name)
            .putBoolean("autocomplete", settings.autocompleteEnabled)
            .putBoolean("syntaxHl", settings.syntaxHighlighting)
            .putInt("historyLimit", settings.historyLimit)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default
}

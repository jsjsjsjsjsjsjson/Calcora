package dev.libchara.calcora

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.libchara.calcora.ui.CasTerminalScreen
import dev.libchara.calcora.ui.theme.CalcoraTheme

class TerminalActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val langName = prefs.getString("lang", "System") ?: "System"
        val locale = when (langName) { "Chinese" -> Locale("zh"); "System" -> Locale.getDefault(); else -> Locale("en") }
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration).apply { setLocale(locale) }
        @Suppress("DEPRECATION") super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.decorView.setBackgroundColor(android.graphics.Color.parseColor("#0D1117"))
        setContent {
            CalcoraTheme {
                CasTerminalScreen(onClose = { finish() })
            }
        }
    }
}

package dev.libchara.calcora.engine


import org.json.JSONArray
import org.json.JSONObject

object GiacEngine {
    private var loaded = false
    private var appContext: android.content.Context? = null

    fun initialize(context: android.content.Context) {
        appContext = context
        init()
    }

    fun init(): Boolean {
        if (loaded) return true
        return runCatching {
            System.loadLibrary("calcora")
            nativeInit()
            appContext?.let { ctx ->
                try {
                    // Copy help files for giac native help system
                    val helpDir = java.io.File(ctx.filesDir, "giac_help")
                    helpDir.mkdirs()
                    val zhDir = java.io.File(helpDir, "zh")
                    zhDir.mkdirs()
for (pair in listOf("aide_cas" to helpDir, "zh/aide_cas" to zhDir)) {
                        val outFile = java.io.File(pair.second, "aide_cas")
                        if (!outFile.exists()) {
                            ctx.assets.open(pair.first).use { it.copyTo(outFile.outputStream()) }
                        }
                    }
                    nativeSetHelpDir(helpDir.absolutePath + "/")
                    HelpParser.loadFromStream(ctx.assets.open("aide_cas"))
                } catch (_: Exception) { }
            }
            loaded = true
        }.isSuccess
    }

    fun evaluate(input: String, mode: EvalMode): CalcResult {
        if (!init()) {
            return CalcResult(input = input, error = "Native backend failed to load", mode = mode, backend = "unavailable")
        }
        val normalized = ExpressionFormatter.toEngineInput(input)
        return parseResult(
            input = input,
            mode = mode,
            raw = if (mode == EvalMode.RawXcas) nativeEvaluateRawXcas(normalized) else nativeEvaluate(normalized, mode.name)
        )
    }

    fun evaluateRawXcas(input: String): CalcResult = evaluate(input, EvalMode.RawXcas)

    fun resetSession() {
        if (init()) nativeReset()
    }

    fun interrupt() {
        if (init()) nativeInterrupt()
    }

    fun version(): String = if (init()) nativeVersion() else "native unavailable"

    fun help(command: String): String {
        if (!init()) return ""
        val entry = HelpParser.lookup(command.trim())
        if (entry != null) {
            return buildString {
                appendLine("Description: " + entry.description)
                if (entry.related.isNotEmpty()) {
                    appendLine("Related: " + entry.related.joinToString(", "))
                }
                if (entry.examples.isNotBlank()) {
                    appendLine("Examples:")
                    append(entry.examples)
                }
            }
        }
        // No exact match: try giac native help for "See also" suggestions
        val native = nativeHelp(command.trim())
        if (native.contains("See also:")) {
            val see = native.substringAfter("See also:").trim()
            return "NoHelp:" + see
        }
        // Fallback to substring search
        val suggestions = HelpParser.search(command.trim()).take(9)
        if (suggestions.isNotEmpty()) {
            return "NoHelp:" + suggestions.mapIndexed { i, name -> "${i + 1}/ $name" }.joinToString(" ")
        }
        return ""
    }

    fun helpSearch(query: String): List<String> {
        val r = HelpParser.search(query)
        return r
    }
    fun helpSearchScored(query: String): List<HelpParser.Scored> = HelpParser.searchScored(query)

    fun plotSample(expr: String, varName: String = "x", xmin: Double = -10.0, xmax: Double = 10.0, samples: Int = 500): List<Pair<Double, Double>> {
        if (!init()) return emptyList()
        val json = nativePlotSample(expr, varName, xmin, xmax, samples)
        return parsePlotPoints(json)
    }

    private fun parseResult(input: String, mode: EvalMode, raw: String): CalcResult = runCatching {
        val json = JSONObject(raw)
        val error = json.optString("error").takeIf { it.isNotBlank() }
        CalcResult(
            input = input, symbolic = json.optString("symbolic"), numeric = json.optString("numeric"),
            error = error, mode = mode, backend = json.optString("backend", "native"),
            isPlot = json.optBoolean("isGraphic", false), plotData = json.optString("plotData", "")
        )
    }.getOrElse {
        CalcResult(input = input, symbolic = raw, mode = mode, backend = "native")
    }

    private fun parsePlotPoints(json: String): List<Pair<Double, Double>> = runCatching {
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val pt = arr.getJSONArray(i)
                add(Pair(pt.getDouble(0), pt.getDouble(1)))
            }
        }
    }.getOrDefault(emptyList())

    private external fun nativeInit()
    private external fun nativeEvaluate(expr: String, mode: String): String
    private external fun nativeEvaluateRawXcas(expr: String): String
    private external fun nativeReset()
    private external fun nativeInterrupt()
    private external fun nativeVersion(): String
    private external fun nativePlotSample(expr: String, varName: String, xmin: Double, xmax: Double, samples: Int): String
    private external fun nativeHelp(command: String): String
    fun setLanguage(code: Int) {
        if (init()) nativeSetLanguage(code)
        val lang = when (code) { 8 -> 8; else -> 2 }
        HelpParser.reloadForLanguage(lang)
        appContext?.let { ctx ->
            try { HelpParser.loadFromStream(ctx.assets.open("aide_cas")) } catch (_: Exception) {}
        }
    }

    private external fun nativeSetLanguage(code: Int)
    private external fun nativeSetHelpDir(path: String)
}

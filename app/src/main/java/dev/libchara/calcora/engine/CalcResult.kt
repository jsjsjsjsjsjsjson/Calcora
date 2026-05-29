package dev.libchara.calcora.engine

data class CalcResult(
    val input: String,
    val symbolic: String = "",
    val numeric: String = "",
    val error: String? = null,
    val mode: EvalMode = EvalMode.Auto,
    val backend: String = "native",
    val isPlot: Boolean = false,
    val plotData: String = ""
) {
    val isError: Boolean get() = error != null
    val primary: String get() = error ?: symbolic.ifBlank { numeric }
}

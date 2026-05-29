package dev.libchara.calcora.engine

enum class EvalMode(val label: String) {
    Auto("Auto"),
    Exact("Exact"),
    Approx("Approx"),
    RawXcas("Raw Xcas");

    companion object {
        fun fromName(name: String?): EvalMode = entries.firstOrNull { it.name == name } ?: Auto
    }
}

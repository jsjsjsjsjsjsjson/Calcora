package dev.libchara.calcora.engine

object ExpressionFormatter {
    fun toEngineInput(input: String): String = input
        .replace("×", "*")
        .replace("÷", "/")
        .replace("−", "-")
        .replace("π", "pi")
        .trim()

    fun appendToken(current: String, token: String): String {
        val mapped = when (token) {
            "×" -> "×"
            "÷" -> "÷"
            "sqrt" -> "sqrt("
            "sin", "cos", "tan", "log", "ln" -> "$token("
            else -> token
        }
        return current + mapped
    }
}

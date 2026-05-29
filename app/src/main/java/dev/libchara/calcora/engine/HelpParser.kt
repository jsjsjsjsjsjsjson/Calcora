package dev.libchara.calcora.engine

import androidx.compose.runtime.mutableStateOf
import java.io.BufferedReader
import java.io.InputStreamReader

data class HelpEntry(val name: String, val description: String, val related: List<String>, val examples: String)

object HelpParser {
    internal var loaded = false
    val isReady = mutableStateOf(false)
    private var preferredLang = 2 // 2=en, 8=zh
    private val helpMap = mutableMapOf<String, HelpEntry>()
    private val allNames = mutableListOf<String>()

    fun reloadForLanguage(newLang: Int) {
        if (preferredLang == newLang && loaded) return
preferredLang = newLang
        loaded = false
        helpMap.clear()
        allNames.clear()
    }

    fun loadFromStream(inputStream: java.io.InputStream) {
        if (loaded) return
        try {
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var currentName = ""
                var desc = ""
                val related = mutableListOf<String>()
                val examples = StringBuilder()
                var inExamples = false
                var inBody = false

            reader.forEachLine { line ->
                when {
                    line.startsWith("# ") -> {
                        if (currentName.isNotBlank()) {
                            val parts = currentName.split(" ", limit = 2)
                            val name = parts[0]
                            helpMap[name] = HelpEntry(name, desc.trim(), related.toList(), examples.toString().trim())
                            if (parts.size > 1) helpMap[parts[1]] = HelpEntry(parts[1], desc.trim(), related.toList(), examples.toString().trim())
                            allNames.add(name)
                        }
                        currentName = line.removePrefix("# ").trim()
                        desc = ""; related.clear(); examples.clear()
                        inExamples = false; inBody = true
                    }
                    inBody && line.isNotEmpty() && line[0] in '1'..'9' && line.length > 1 && line[1] == ' ' -> {
                        val content = line.removePrefix(line.takeWhile { it != ' ' }).trim()
                        val langCode = line[0].toString().toIntOrNull() ?: 0
                        // desc_zh and desc_en are accumulators; finalize after parsing all languages
                        if (langCode == preferredLang) { desc = content;  }
                        else if (langCode == 2) desc = content
                        else if (desc.isEmpty() && langCode != preferredLang) desc = content
                        inExamples = false
                    }
                    line.startsWith("-") -> {
                        // Related: -N command_name
                        var rest = line.trimStart('-')
                        while (rest.isNotEmpty() && rest[0].isDigit()) rest = rest.drop(1)
                        rest = rest.trim()
                        val name = rest.split(" ", limit = 2).firstOrNull()?.trim() ?: ""
                        if (name.isNotBlank() && name.all { it.isLetterOrDigit() || it == '_' })
                            related.add(name)
                        inExamples = false
                    }
                    line.startsWith("0 ") -> { inExamples = false }
                    inBody && line.startsWith("3 ") -> { inExamples = false }
                    inBody && line.startsWith("4 ") -> { inExamples = false }
                    inBody && line.startsWith("8 ") -> { inExamples = false }
                    inBody && line.isNotBlank() && !line.startsWith("#") &&
                        !line.startsWith("-") && !line.startsWith("0 ") &&
                        !(line.isNotEmpty() && line[0] in '1'..'9') -> {
                        examples.append(line.trim()).append("\n")
                        inExamples = true
                    }
                    else -> { /* skip empty/unrecognized */ }
                }
            }
                // Save last entry
                if (currentName.isNotBlank()) {
                    val parts = currentName.split(" ", limit = 2)
                    val name = parts[0]
                    helpMap[name] = HelpEntry(name, desc.trim(), related.toList(), examples.toString().trim())
                    if (parts.size > 1) helpMap[parts[1]] = HelpEntry(parts[1], desc.trim(), related.toList(), examples.toString().trim())
                    allNames.add(name)
                }
            }
            loaded = true; isReady.value = true
        } catch (_: Exception) {}
    }

    fun lookup(name: String): HelpEntry? {
        if (!loaded) return null
        val n = name.trim()
        return helpMap[n] ?: helpMap[n.lowercase()] ?: helpMap[n.uppercase()]
    }

    fun getAllNames(): List<String> = allNames.toList()

    data class Scored(val name: String, val score: Int)

    fun search(query: String): List<String> = searchScored(query).map { it.name }

    fun searchScored(query: String): List<Scored> {
        val q = query.lowercase().trim()
        if (q.isEmpty()) return allNames.sorted().map { Scored(it, 0) }
        return allNames.map { name ->
            val lower = name.lowercase()
            var score = 0
            for (ch in q) {
                if (ch in lower) score++
                if (lower.indexOf(ch) == q.indexOf(ch)) score++ // position bonus
            }
            if (lower.startsWith(q)) score += 5 // prefix bonus
            if (lower == q) score += 10 // exact match
            Scored(name, score)
        }.filter { it.score > 1 }.sortedByDescending { it.score }
    }
}

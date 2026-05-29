package dev.libchara.calcora.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.libchara.calcora.R
import dev.libchara.calcora.engine.GiacEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val ScriptBg = Color(0xFF0D1117)
private val ScriptSurface = Color(0xFF161B22)
private val ScriptAccent = Color(0xFF58A6FF)
private val ScriptPrompt = Color(0xFF8B949E)
private val ScriptOutput = Color(0xFFC9D1D9)
private val ScriptError = Color(0xFFF85149)

private val SynKeyword = Color(0xFFC586C0)
private val SynNumber = Color(0xFFB5CEA8)
private val SynString = Color(0xFFCE9178)
private val SynComment = Color(0xFF6A9955)
private val SynFunc = Color(0xFFDCDCAA)

private data class ScriptLine(val input: String, val output: String)

private val GIAC_KEYWORDS = setOf(
    "local", "return", "if", "then", "else", "elif", "fi", "end",
    "for", "from", "to", "do", "od", "step", "in", "while",
    "repeat", "until", "break", "continue", "try", "catch",
    "function", "proc", "begin", "case", "of", "switch",
    "assume", "purge", "export", "input", "print"
)

private class XcasSyntaxHighlighter : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val annotated = buildAnnotatedString {
            var i = 0
            while (i < raw.length) {
                if (i + 1 < raw.length && raw[i] == '/' && raw[i + 1] == '/') {
                    val end = raw.indexOf('\n', i).let { if (it == -1) raw.length else it }
                    withStyle(SpanStyle(color = SynComment)) { append(raw.substring(i, end)) }
                    i = end
                    continue
                }
                if (raw[i] == '"') {
                    val end = raw.indexOf('"', i + 1).let { if (it == -1) raw.length else it + 1 }
                    withStyle(SpanStyle(color = SynString)) { append(raw.substring(i, end)) }
                    i = end
                    continue
                }
                if (raw[i].isDigit() || (raw[i] == '.' && i + 1 < raw.length && raw[i + 1].isDigit())) {
                    val start = i
                    while (i < raw.length && (raw[i].isDigit() || raw[i] == '.' || raw[i] == 'e' || raw[i] == 'E')) i++
                    withStyle(SpanStyle(color = SynNumber)) { append(raw.substring(start, i)) }
                    continue
                }
                if (raw[i].isLetter() || raw[i] == '_') {
                    val start = i
                    while (i < raw.length && (raw[i].isLetterOrDigit() || raw[i] == '_')) i++
                    val word = raw.substring(start, i)
                    val color = if (word in GIAC_KEYWORDS) SynKeyword else SynFunc
                    withStyle(SpanStyle(color = color)) { append(word) }
                    continue
                }
                append(raw[i])
                i++
            }
        }
        return TransformedText(annotated, OffsetMapping.Identity)
    }
}

@Composable
fun ScriptEditorScreen(onClose: (() -> Unit)? = null) {
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }
    var outputLines = remember { mutableStateListOf<ScriptLine>() }
    var running by remember { mutableStateOf(false) }
    var savedFiles by remember { mutableStateOf(listOf<String>()) }
    var showFileList by remember { mutableStateOf(false) }
    var saveDialogName by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }
    var detailOutput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scriptsDir = File(context.filesDir, "scripts").also { it.mkdirs() }

    fun refreshFiles() {
        savedFiles = scriptsDir.listFiles()?.map { it.nameWithoutExtension }?.sorted() ?: emptyList()
    }

    LaunchedEffect(Unit) { refreshFiles() }
    LaunchedEffect(outputLines.size) {
        if (outputLines.isNotEmpty()) listState.animateScrollToItem(outputLines.size - 1)
    }

    fun runScript() {
        val text = code.trim()
        if (text.isEmpty()) return
        running = true
        scope.launch {
            val preview = if (text.length > 80) text.take(77) + "..." else text
            val rawResult = withContext(Dispatchers.Default) { GiacEngine.evaluateRawXcas(text) }
            val out = if (rawResult.isError) rawResult.error!!
                      else {
                          val sym = rawResult.symbolic
                          if (sym.contains(',')) sym.substringAfterLast(',').trim() else sym.trim()
                      }
            outputLines.add(ScriptLine(preview, out))
            running = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(ScriptBg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(ScriptSurface).padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.script_title),
                style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ScriptPrompt),
                modifier = Modifier.padding(8.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showSaveDialog = true; saveDialogName = "" }) {
                    Text(stringResource(R.string.script_save), color = ScriptPrompt, fontSize = 13.sp)
                }
                TextButton(onClick = { showFileList = !showFileList; if (showFileList) refreshFiles() }) {
                    Text(stringResource(R.string.script_load), color = ScriptPrompt, fontSize = 13.sp)
                }
                if (onClose != null) {
                    TextButton(onClick = onClose) { Text("\u2715", color = ScriptPrompt, fontSize = 18.sp) }
                }
            }
        }

        AnimatedVisibility(
            visible = showFileList,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(140.dp).background(ScriptSurface).padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                items(savedFiles, key = { it }) { name ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            val file = File(scriptsDir, "$name.xcas")
                            if (file.exists()) { code = file.readText(); outputLines.clear() }
                            showFileList = false
                        }.padding(vertical = 5.dp)
                    ) {
                        Text(name, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = ScriptAccent), modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            File(scriptsDir, "$name.xcas").delete()
                            refreshFiles()
                        }) { Text(stringResource(R.string.script_delete), color = ScriptError, fontSize = 11.sp) }
                    }
                }
                if (savedFiles.isEmpty()) {
                    item { Text(stringResource(R.string.script_no_files), style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = ScriptPrompt)) }
                }
            }
        }

        AnimatedVisibility(
            visible = showSaveDialog,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().background(ScriptSurface).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = saveDialogName,
                    onValueChange = { saveDialogName = it },
                    modifier = Modifier.weight(1f).background(ScriptBg, RoundedCornerShape(4.dp)).padding(8.dp),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = ScriptOutput),
                    cursorBrush = SolidColor(ScriptAccent),
                    singleLine = true,
                    decorationBox = { inner ->
                        Box { if (saveDialogName.isEmpty()) Text(stringResource(R.string.script_name_hint), style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = ScriptPrompt.copy(alpha = 0.4f))); inner() }
                    }
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    val name = saveDialogName.trim()
                    if (name.isNotBlank() && code.isNotBlank()) {
                        File(scriptsDir, "$name.xcas").writeText(code)
                        showSaveDialog = false
                        refreshFiles()
                    }
                }, enabled = saveDialogName.isNotBlank()) {
                    Text(stringResource(R.string.script_save), color = ScriptAccent, fontSize = 14.sp)
                }
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(stringResource(R.string.script_cancel), color = ScriptPrompt, fontSize = 14.sp)
                }
            }
        }

        BasicTextField(
            value = code,
            onValueChange = { code = it },
            modifier = Modifier.weight(1f).fillMaxWidth().background(ScriptBg).padding(horizontal = 14.dp, vertical = 10.dp),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = ScriptOutput, lineHeight = 22.sp),
            cursorBrush = SolidColor(ScriptAccent),
            visualTransformation = XcasSyntaxHighlighter(),
            decorationBox = { inner ->
                Box {
                    if (code.isEmpty()) Text(
                        stringResource(R.string.script_editor_hint),
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = ScriptPrompt.copy(alpha = 0.4f))
                    )
                    inner()
                }
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth().background(ScriptSurface).padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.script_output),
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = ScriptPrompt)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (running) {
                    CircularProgressIndicator(color = ScriptAccent, strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                }
                TextButton(onClick = { outputLines.clear() }, enabled = !running) {
                    Text(stringResource(R.string.btn_clear), color = ScriptPrompt, fontSize = 13.sp)
                }
                TextButton(onClick = { runScript() }, enabled = !running && code.isNotBlank()) {
                    Text(stringResource(R.string.btn_run), color = ScriptAccent, fontSize = 14.sp)
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().background(ScriptBg).padding(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (outputLines.isEmpty() && !running) {
                item {
                    Text(
                        stringResource(R.string.script_output_hint),
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = ScriptPrompt)
                    )
                }
            }
            items(outputLines) { entry ->
                Column(modifier = Modifier.padding(vertical = 3.dp)) {
                    Text(
                        "> ${entry.input}",
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = ScriptAccent, fontWeight = FontWeight.Medium)
                    )
                    Text(
                        entry.output,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = if (entry.output.startsWith("Error") || entry.output.startsWith("Unknown")) ScriptError else ScriptOutput),
                        modifier = Modifier.clickable { detailOutput = entry.output }
                    )
                }
            }
        }
    }

    if (detailOutput.isNotBlank()) {
        ResultDetailDialog(title = stringResource(R.string.script_output), content = detailOutput, onDismiss = { detailOutput = "" })
    }
}

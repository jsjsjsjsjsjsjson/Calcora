package dev.libchara.calcora.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier


import androidx.compose.ui.graphics.SolidColor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.libchara.calcora.R
import androidx.compose.ui.unit.sp
import android.view.HapticFeedbackConstants
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import dev.libchara.calcora.engine.CalcResult
import dev.libchara.calcora.engine.EvalMode
import dev.libchara.calcora.engine.GiacEngine
import dev.libchara.calcora.engine.HelpParser
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle

// Lightweight syntax highlighter for Calc input using Monet theme colors
private class CalcSyntaxHighlighter(
    private val numColor: androidx.compose.ui.graphics.Color,
    private val strColor: androidx.compose.ui.graphics.Color,
    private val funcColor: androidx.compose.ui.graphics.Color,
    private val kwColor: androidx.compose.ui.graphics.Color
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val annotated = buildAnnotatedString {
            var i = 0
            while (i < raw.length) {
                if (raw[i] == '"') {
                    val end = raw.indexOf('"', i + 1).let { if (it == -1) raw.length else it + 1 }
                    withStyle(SpanStyle(color = strColor)) { append(raw.substring(i, end)) }
                    i = end; continue
                }
                if (raw[i].isDigit() || (raw[i] == '.' && i + 1 < raw.length && raw[i + 1].isDigit())) {
                    val start = i
                    while (i < raw.length && (raw[i].isDigit() || raw[i] == '.' || raw[i] == 'e' || raw[i] == 'E')) i++
                    withStyle(SpanStyle(color = numColor)) { append(raw.substring(start, i)) }
                    continue
                }
                if (raw[i].isLetter() || raw[i] == '_') {
                    val start = i
                    while (i < raw.length && (raw[i].isLetterOrDigit() || raw[i] == '_')) i++
                    val w = raw.substring(start, i)
                    val color = if (w in setOf("if", "then", "else", "for", "from", "to", "do", "od", "step", "in", "while", "repeat", "until", "break", "local", "return", "function", "begin", "end", "assume", "purge")) kwColor else funcColor
                    withStyle(SpanStyle(color = color)) { append(w) }
                    continue
                }
                append(raw[i]); i++
            }
        }
        return TransformedText(annotated, OffsetMapping.Identity)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainCalculatorScreen(
    contentPadding: PaddingValues,
    calcInput: TextFieldValue = TextFieldValue(""),
    calcResult: CalcResult? = null,
    calcMode: EvalMode = EvalMode.Auto,
    calcHistory: SnapshotStateList<HistoryLine> = mutableStateListOf(),
    onInputChange: (TextFieldValue) -> Unit = {},
    onResultChange: (CalcResult?) -> Unit = {},
    onModeChange: (EvalMode) -> Unit = {},
    restoreExpression: String?,
    onRestoreConsumed: () -> Unit,
    onResult: (CalcResult) -> Unit,
    onPlotRequest: (String) -> Unit,
    onNavigateTerminal: () -> Unit,
    onNavigateScript: () -> Unit = {},
    onNavigateHelp: (String?) -> Unit,
    autocompleteEnabled: Boolean = true,
    syntaxHighlighting: Boolean = true,
) {
    var input by remember { mutableStateOf(calcInput) }
    var result by remember { mutableStateOf(calcResult) }
    var mode by remember { mutableStateOf(calcMode) }
    var functionsExpanded by remember { mutableStateOf(false) }
    var varsExpanded by remember { mutableStateOf(false) }
    var fxExpanded by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf("") }

    var evaluating by remember { mutableStateOf(false) }
    var showSpinner by remember { mutableStateOf(false) }
    fun trunc(s: String) = if (s.length > 200) s.take(200) + "…" else s

    // Tokenizer — extract current identifier under cursor
    // Triggers autocomplete only for identifiers starting with a letter (handles plot3d etc.)
    fun currentWord(): String {
        val text = input.text
        val cursor = input.selection.start.coerceIn(0, text.length)
        var s = cursor
        while (s > 0 && text[s - 1].let { it.isLetterOrDigit() || it == '_' }) s--
        var e = cursor
        while (e < text.length && text[e].let { it.isLetterOrDigit() || it == '_' }) e++
        val w = text.substring(s, e)
        // identifier must start with a letter and contain at least one letter
        return if (w.isNotEmpty() && w[0].isLetter() && w.any { it.isLetter() }) w else ""
    }
    fun wordRange(): Pair<Int, Int> {
        val text = input.text
        val cursor = input.selection.start.coerceIn(0, text.length)
        var s = cursor
        while (s > 0 && text[s - 1].let { it.isLetterOrDigit() || it == '_' }) s--
        var e = cursor
        while (e < text.length && text[e].let { it.isLetterOrDigit() || it == '_' }) e++
        return s to e
    }
    var autocompleteVisible by remember { mutableStateOf(false) }
    val acTransitionState = remember { androidx.compose.animation.core.MutableTransitionState(false) }

    var resultDialog by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

LaunchedEffect(restoreExpression) {
        val expression = restoreExpression
        if (!expression.isNullOrBlank()) {
            input = TextFieldValue(expression)
            result = null
            onInputChange(input)
            onResultChange(null)
            onRestoreConsumed()
        }
    }

    fun insert(text: String) {
        val template = text.replace("\u25A1", "")
        val firstPlaceholder = text.indexOf('\u25A1').takeIf { it >= 0 } ?: template.length
        val start = input.selection.min
        val end = input.selection.max
        val next = input.text.replaceRange(start, end, template)
        input = TextFieldValue(next, selection = androidx.compose.ui.text.TextRange(start + firstPlaceholder.coerceAtMost(template.length))); onInputChange(input)
    }

    fun extractHelpArg(input: String): String {
        val idx = input.indexOf('(')
        if (idx < 0) return ""
        var inner = input.substring(idx + 1)
        val end = inner.lastIndexOf(')')
        if (end < 0) return inner.trim()
        inner = inner.substring(0, end).trim().trim('"')
        // Strip trailing ()
        while (inner.endsWith("()")) inner = inner.removeSuffix("()")
        return inner
    }

    fun evaluate() {
        val text = input.text.trim()
        if (text.isEmpty() || evaluating) return
        if (text.startsWith("help(", ignoreCase = true)) {
            val arg = extractHelpArg(text)
            if (arg.isNotBlank() && arg.all { it.isLetterOrDigit() || it == '_' }) {
                onNavigateHelp(arg)
                return
            }
        }
        evaluating = true
        showSpinner = false
        scope.launch {
            // Show spinner after 2s of waiting
            val spinnerJob = launch {
                delay(2000)
                showSpinner = true
            }
            val evaluated = withContext(Dispatchers.Default) {
                GiacEngine.evaluate(text, mode)
            }
            spinnerJob.cancel()
            // Push current expression + its result to history
            lastResult = trunc(result?.primary.orEmpty())
            calcHistory.add(HistoryLine(text, evaluated))
            if (calcHistory.size > 8) calcHistory.removeAt(0)
            listState.animateScrollToItem(maxOf(0, calcHistory.size - 1))
            result = evaluated
            onResult(evaluated)
            onResultChange(evaluated)
            evaluating = false
            showSpinner = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 0.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateScript, modifier = Modifier.size(40.dp)) {
                Text("</>", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            }
            IconButton(onClick = onNavigateTerminal, modifier = Modifier.size(40.dp)) {
                Text(">_", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            }
        }

        Column(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxWidth()
        ) {
            // Scrollable history
            if (calcHistory.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    items(calcHistory, key = { it.id }) { line ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = line.input,
                                style = TextStyle(fontSize = 13.sp, color = colors.onSurface.copy(alpha = 0.5f), textAlign = TextAlign.End, letterSpacing = 0.sp, fontFamily = FontFamily.Monospace),
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().clickable { input = TextFieldValue(line.input); onInputChange(input) }
                            )
                            Text(
                                text = line.result.primary,
                                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (line.result.isError) colors.error.copy(alpha = 0.65f) else colors.primary.copy(alpha = 0.75f), textAlign = TextAlign.End, letterSpacing = 0.sp),
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().clickable { input = TextFieldValue(line.result.primary); onInputChange(input) }
                            )
                            Spacer(Modifier.height(1.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(colors.onSurface.copy(alpha = 0.08f)))
                        }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            // Current input + result (pinned below history), autocomplete overlay
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    // Reserve space for autocomplete overlay when visible
                    if (autocompleteVisible) Spacer(Modifier.height(44.dp))
Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (input.text.isNotEmpty()) Text("×", fontSize = 16.sp, color = colors.onSurface.copy(alpha = 0.3f), modifier = Modifier.clickable { input = TextFieldValue(""); onInputChange(TextFieldValue("")) }.padding(end = 8.dp, bottom = 2.dp))
                    BasicTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            onInputChange(it)
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 36.dp).focusRequester(focusRequester),
                        textStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Normal, textAlign = TextAlign.End, color = colors.onSurface, fontFamily = FontFamily.Monospace, letterSpacing = 0.sp),
                        cursorBrush = SolidColor(colors.primary),
                        visualTransformation = if (syntaxHighlighting) CalcSyntaxHighlighter(colors.onSurface, colors.secondary, colors.primary, colors.tertiary) else VisualTransformation.None,
                        singleLine = false, maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { evaluate() }),
                        decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterEnd) {
                        if (input.text.isEmpty()) Text("0", style = TextStyle(fontSize = 24.sp, color = colors.onSurface.copy(alpha = 0.38f), textAlign = TextAlign.End), modifier = Modifier.fillMaxWidth())
                        inner()
                        }
                    }
                    )
                }
                Spacer(Modifier.height(4.dp))
                AnimatedContent(
                    targetState = trunc(result?.primary.orEmpty()),
                    transitionSpec = { (fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 8 }).togetherWith(fadeOut(tween(150))) },
                    label = "result", modifier = Modifier.fillMaxWidth().clickable { if (result?.primary?.isNotBlank() == true) resultDialog = true }
                ) { text ->
                    val resultSize = when { text.length > 40 -> 18.sp; text.length > 20 -> 22.sp; else -> 26.sp }
                    Text(
                        text = text,
                        style = TextStyle(fontSize = resultSize, fontWeight = FontWeight.Medium, color = if (result?.isError == true) colors.error else colors.primary, textAlign = TextAlign.End, letterSpacing = 0.sp, fontFamily = FontFamily.Monospace),
                        maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth()
                    )
                }
                result?.numeric?.takeIf { it.isNotBlank() && it != result?.primary && result?.isError != true }?.let { num -> val tn = trunc(num)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 8 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(tn, style = TextStyle(fontSize = 14.sp, color = colors.onSurfaceVariant, textAlign = TextAlign.End, letterSpacing = 0.sp, fontFamily = FontFamily.Monospace), maxLines = 1, modifier = Modifier.fillMaxWidth())
                    }
                }
                val plotData = result?.plotData?.takeIf { result?.isPlot == true }.orEmpty()
                if (plotData.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = { onPlotRequest(result?.plotData ?: "") }, shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)) {
                        Text(stringResource(R.string.btn_view_plot), fontSize = 14.sp)
                    }
                }
                } // end inner Column

                // Autocomplete overlay — positioned at top-end, no layout impact
                val word = currentWord()
                val helpReady = HelpParser.isReady.value
                val autocompleteHints = remember(word, helpReady) {
                    if (autocompleteEnabled && word.length >= 1) GiacEngine.helpSearchScored(word).take(8) else emptyList()
                }
                LaunchedEffect(word) {
                    if (autocompleteEnabled) autocompleteVisible = word.length >= 1 && autocompleteHints.isNotEmpty()
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = autocompleteVisible,
                    modifier = Modifier.align(Alignment.TopEnd),
                    enter = fadeIn(tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing)),
                    exit = androidx.compose.animation.ExitTransition.None
                ) {
                    Row(
                        modifier = Modifier.height(44.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        autocompleteHints.forEach { hint ->
                            AssistChip(
                                onClick = {
                                    val (wStart, wEnd) = wordRange()
                                    val replacement = hint.name + "()"
                                    val newText = input.text.replaceRange(wStart, wEnd, replacement)
                                    val cursorPos = wStart + replacement.length - 1
                                    input = TextFieldValue(newText, selection = androidx.compose.ui.text.TextRange(cursorPos))
                                    onInputChange(input)
                                    autocompleteVisible = false
                                },
                                label = { Text(hint.name, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            } // end Box overlay
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            EvalMode.entries.forEach { item ->
                FilterChip(selected = mode == item, onClick = { mode = item; onModeChange(item) }, label = { val label = when (item) { EvalMode.Auto -> stringResource(R.string.eval_auto); EvalMode.Exact -> stringResource(R.string.eval_exact); EvalMode.Approx -> stringResource(R.string.eval_approx); EvalMode.RawXcas -> stringResource(R.string.eval_raw) }
                            Text(label, fontSize = 11.sp, maxLines = 1) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.primary, selectedLabelColor = colors.onPrimary))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = { insert("help(□)") }, label = { Text("?", fontSize = 13.sp, fontWeight = FontWeight.Bold) }, shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.width(4.dp))
                AssistChip(onClick = {
                    val cursor = input.selection.start.coerceAtLeast(0)
                    if (cursor > 0) {
                        input = TextFieldValue(input.text, selection = androidx.compose.ui.text.TextRange(cursor - 1))
                        onInputChange(input)
                    }
                }, label = { Text("◀", fontSize = 13.sp) }, shape = RoundedCornerShape(14.dp))
                AssistChip(onClick = {
                    val cursor = input.selection.end
                    if (cursor < input.text.length) {
                        input = TextFieldValue(input.text, selection = androidx.compose.ui.text.TextRange(cursor + 1))
                        onInputChange(input)
                    }
                }, label = { Text("▶", fontSize = 13.sp) }, shape = RoundedCornerShape(14.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = { varsExpanded = !varsExpanded; fxExpanded = false; functionsExpanded = false }, label = { Text(stringResource(R.string.panel_vars), fontSize = 11.sp) }, shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.width(4.dp))
                AssistChip(onClick = { fxExpanded = !fxExpanded; varsExpanded = false; functionsExpanded = false }, label = { Text(stringResource(R.string.panel_fx), fontSize = 11.sp) }, shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.width(4.dp))
                AssistChip(onClick = { functionsExpanded = !functionsExpanded; varsExpanded = false; fxExpanded = false }, label = { Text(stringResource(R.string.panel_funcs), fontSize = 11.sp) }, shape = RoundedCornerShape(14.dp))
            }
        }

        AnimatedVisibility(visible = varsExpanded, enter = expandVertically(), exit = shrinkVertically()) {
            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 110.dp).padding(horizontal = 10.dp)) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    VarPanel(onInsert = ::insert)
                }
            }
        }
        AnimatedVisibility(visible = fxExpanded, enter = expandVertically(), exit = shrinkVertically()) {
            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 110.dp).padding(horizontal = 10.dp)) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    FxPanel(onInsert = ::insert)
                }
            }
        }
        AnimatedVisibility(visible = functionsExpanded, enter = expandVertically(), exit = shrinkVertically()) {
            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 110.dp).padding(horizontal = 10.dp)) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    FunctionsPanel(onInsert = ::insert)
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .padding(bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            val keyRows = listOf(
                listOf(KeySpec("AC", KeyRole.Clear), KeySpec("\u232B", KeyRole.Backspace), KeySpec("%", KeyRole.Operator), KeySpec("\u00F7", KeyRole.Operator)),
                listOf(KeySpec("7", KeyRole.Number), KeySpec("8", KeyRole.Number), KeySpec("9", KeyRole.Number), KeySpec("\u00D7", KeyRole.Operator)),
                listOf(KeySpec("4", KeyRole.Number), KeySpec("5", KeyRole.Number), KeySpec("6", KeyRole.Number), KeySpec("\u2212", KeyRole.Operator)),
                listOf(KeySpec("1", KeyRole.Number), KeySpec("2", KeyRole.Number), KeySpec("3", KeyRole.Number), KeySpec("+", KeyRole.Operator)),
                listOf(KeySpec("0", KeyRole.Number), KeySpec(".", KeyRole.Number), KeySpec(",", KeyRole.Number), KeySpec("EXE", KeyRole.Equals))
            )
            keyRows.forEach { row ->
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    row.forEach { key ->
                        val isWide = key.label == "0"
                        CalculatorKey(
                            label = key.label, role = key.role,
                            onClick = {
                                when (key.label) {
                                    "AC" -> { input = TextFieldValue(""); result = null; lastResult = ""; calcHistory.clear(); onInputChange(TextFieldValue("")); onResultChange(null) }
                                    "⌫" -> {
                                        val cursor = input.selection.start.coerceAtLeast(0)
                                        if (cursor > 0) {
                                            val next = input.text.removeRange(cursor - 1, cursor)
                                            input = TextFieldValue(next, selection = androidx.compose.ui.text.TextRange(cursor - 1))
                                            onInputChange(input)
                                        }
                                    }
                                    "EXE" -> evaluate()
                                    "\u00F7" -> insert("\u00F7")
                                    "\u00D7" -> insert("\u00D7")
                                    "\u2212" -> insert("\u2212")
                                    "," -> insert(",")
                                    else -> insert(key.label)
                                }
                            },
                            modifier = Modifier.weight(if (isWide) 2.1f else 1f)
                        )
                    }
                }
            }
        }

        if (resultDialog && result != null) {
            ResultDetailDialog(
                title = "Result",
                content = result!!.symbolic,
                secondary = result!!.numeric.takeIf { it.isNotBlank() && it != result!!.symbolic } ?: "",
                onDismiss = { resultDialog = false }
            )
        }
        } // end outer Column

        // Loading spinner overlay on top of everything
        AnimatedVisibility(
            visible = showSpinner,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150))
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = colors.primary, strokeWidth = 3.dp)
            }
        }
    } // end Box
}


data class HistoryLine(
    val input: String,
    val result: CalcResult,
    val id: Long = System.nanoTime()
)

private enum class KeyRole { Number, Operator, Equals, Clear, Backspace }
private data class KeySpec(val label: String, val role: KeyRole)

@Composable
private fun CalculatorKey(label: String, role: KeyRole, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val view = LocalView.current
    val bg = when (role) {
        KeyRole.Number -> colors.surfaceVariant.copy(alpha = 0.55f)
        KeyRole.Operator -> colors.primaryContainer
        KeyRole.Equals -> colors.primary
        KeyRole.Clear -> if (isSystemInDarkTheme()) colors.errorContainer.copy(alpha = 0.45f) else colors.errorContainer
        KeyRole.Backspace -> colors.surfaceVariant
    }
    val fg = when (role) {
        KeyRole.Equals -> colors.onPrimary
        KeyRole.Operator -> colors.onPrimaryContainer
        KeyRole.Clear -> colors.onErrorContainer
        else -> colors.onSurface
    }
    Button(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onClick()
        },
        modifier = modifier.height(78.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = when { label.length > 2 -> 16.sp; role == KeyRole.Number || label == "," -> 24.sp; else -> 21.sp },
                fontWeight = if (role == KeyRole.Equals || role == KeyRole.Number || label == ",") FontWeight.Medium else FontWeight.Normal,
                letterSpacing = 0.sp, textAlign = TextAlign.Center
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VarPanel(onInsert: (String) -> Unit) {
    val vars = listOf("x", "y", "z", "a", "b", "c", "n", "t", "k", "m", ":=", ";", "(", ")", "[", "]", "{", "}", "->")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        vars.forEach { v ->
            AssistChip(onClick = { onInsert(v) }, label = { Text(v, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium) }, shape = RoundedCornerShape(12.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FxPanel(onInsert: (String) -> Unit) {
    val funcs = listOf(
        "sin(\u25A1)" to "sin", "cos(\u25A1)" to "cos", "tan(\u25A1)" to "tan",
        "asin(\u25A1)" to "asin", "acos(\u25A1)" to "acos", "atan(\u25A1)" to "atan",
        "ln(\u25A1)" to "ln", "log(\u25A1)" to "log", "sqrt(\u25A1)" to "sqrt",
        "abs(\u25A1)" to "abs", "exp(\u25A1)" to "exp", "^\u25A1" to "^"
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        funcs.forEach { (template, _) ->
            val short = template.replace("\u25A1", "")
            AssistChip(onClick = { onInsert(template) }, label = { Text(short, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }, shape = RoundedCornerShape(12.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FunctionsPanel(onInsert: (String) -> Unit) {
    val chips = listOf(
        "simplify(\u25A1)", "factor(\u25A1)", "expand(\u25A1)", "normal(\u25A1)", "solve(\u25A1=0,x)", "subst(\u25A1,x=\u25A1)",
        "diff(\u25A1,x)", "integrate(\u25A1,x)", "limit(\u25A1,x=0)", "sum(\u25A1,k,1,n)",
        "det(\u25A1)", "inv(\u25A1)", "transpose(\u25A1)", "rank(\u25A1)",
        "ifactor(\u25A1)", "gcd(\u25A1,\u25A1)", "lcm(\u25A1,\u25A1)",
        "plot(\u25A1)", "plot3d(\u25A1)", "plotparam(\u25A1)", "plotlist(\u25A1)", "plotseq(\u25A1)", "plot(\u25A1,x=-5..5)",
        "makelist(\u25A1)", "makemat(\u25A1)", "fft(\u25A1)", "ifft(\u25A1)"
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        chips.forEach { template ->
            val short = template.replace("\u25A1", "").take(18)
            AssistChip(onClick = { onInsert(template) }, label = { Text(short, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }, shape = RoundedCornerShape(12.dp))
        }
    }
}

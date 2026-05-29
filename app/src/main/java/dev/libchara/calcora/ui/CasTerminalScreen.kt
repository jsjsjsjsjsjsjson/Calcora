package dev.libchara.calcora.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.libchara.calcora.R
import dev.libchara.calcora.engine.GiacEngine

private data class TerminalLine(val input: String, val output: String)

private val TerminalBg = Color(0xFF0D1117)
private val TerminalSurface = Color(0xFF161B22)
private val TerminalGreen = Color(0xFF58A6FF)
private val TerminalPrompt = Color(0xFF8B949E)
private val TerminalOutput = Color(0xFFC9D1D9)
private val TerminalError = Color(0xFFF85149)

@Suppress("DEPRECATION")
@Composable
fun CasTerminalScreen(onClose: (() -> Unit)? = null) {
    var input by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(listOf<TerminalLine>()) }
    var running by remember { mutableStateOf(false) }
    var detailDialog by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) listState.animateScrollToItem(history.size - 1)
    }

    fun run() {
        val cmd = input.trim()
        if (cmd.isEmpty()) return
        running = true
        val result = GiacEngine.evaluateRawXcas(cmd)
        val output = buildString {
            if (result.isError) append(result.error)
            else {
                append(result.symbolic)
                if (result.numeric.isNotBlank() && result.numeric != result.symbolic)
                    append("\n").append(result.numeric)
            }
        }
        history = history + TerminalLine(cmd, output)
        input = ""
        running = false
    }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().background(TerminalBg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(TerminalSurface).padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.term_title), style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TerminalPrompt), modifier = Modifier.padding(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { history = emptyList() }) { Text(stringResource(R.string.btn_clear), color = TerminalPrompt, fontSize = 13.sp) }
                if (onClose != null) {
                    TextButton(onClick = onClose) { Text("\u2715", color = TerminalPrompt, fontSize = 18.sp) }
                }
            }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item { Text(stringResource(R.string.term_hint), style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TerminalPrompt), modifier = Modifier.padding(bottom = 8.dp)) }
            items(history) { entry ->
                Column(modifier = Modifier.padding(vertical = 3.dp)) {
                    Text("> ${entry.input}", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = TerminalGreen, fontWeight = FontWeight.Medium))
                    Text(entry.output, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = if (entry.output.startsWith("Error") || entry.output.startsWith("Unknown")) TerminalError else TerminalOutput), modifier = Modifier.clickable { detailDialog = entry.output })
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().background(TerminalSurface).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(">", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = TerminalGreen, fontWeight = FontWeight.Bold))
                Spacer(Modifier.width(8.dp))
                BasicTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), enabled = !running, textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = TerminalOutput), cursorBrush = SolidColor(TerminalGreen), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { run() }), decorationBox = { inner ->
                    Box { if (input.isEmpty()) Text(stringResource(R.string.term_input_hint), style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = TerminalPrompt.copy(alpha = 0.4f))); inner() } })
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { run() }, enabled = !running && input.isNotBlank()) { Text(stringResource(R.string.btn_run), color = TerminalGreen, fontSize = 14.sp) }
            }
        }
    }

    if (detailDialog.isNotBlank()) {
        ResultDetailDialog(title = stringResource(R.string.term_output_title), content = detailDialog, onDismiss = { detailDialog = "" })
    }
}

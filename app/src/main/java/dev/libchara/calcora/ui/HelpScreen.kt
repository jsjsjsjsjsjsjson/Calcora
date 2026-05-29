package dev.libchara.calcora.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.libchara.calcora.R
import androidx.compose.ui.unit.sp
import dev.libchara.calcora.engine.GiacEngine
import dev.libchara.calcora.engine.HelpParser

private val VIEW_LIST = Any()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HelpScreen(contentPadding: PaddingValues, initialFunc: String?, onInsert: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var query by remember { mutableStateOf(initialFunc ?: "") }
    var entryName by remember { mutableStateOf("") }
    var rawHelp by remember { mutableStateOf("") }
    var helpLoaded by remember { mutableStateOf(false) }

    fun loadHelp(name: String) {
        entryName = name
        rawHelp = ""
        helpLoaded = false
        rawHelp = GiacEngine.help(name)
        helpLoaded = true
    }

    androidx.compose.runtime.LaunchedEffect(initialFunc) {
        val f = initialFunc
        if (!f.isNullOrBlank()) loadHelp(f)
    }

    BackHandler(enabled = entryName.isNotBlank()) {
        entryName = ""
        rawHelp = ""
        helpLoaded = false
    }

    val helpReady = HelpParser.isReady.value
    val scoredResults = remember(query, helpReady) {
        GiacEngine.helpSearchScored(query.trim())
    }
    

    val isNoHelp = rawHelp.startsWith("NoHelp:")
    val desc = rawHelp.lines().firstOrNull { it.contains("Description:", true) }?.removePrefix("Description:")?.trim() ?: ""
    val relRaw = rawHelp.lines().firstOrNull { it.contains("Related:", true) }?.removePrefix("Related:")?.trim() ?: ""
    val related = relRaw.split(",", ";").map { it.trim() }.filter { it.isNotBlank() && it != entryName }
    val exampleRaw = rawHelp.substringAfter("Examples:", "").trim().replace(Regex("^\\s+", RegexOption.MULTILINE), "").take(500)
    val exampleLines = exampleRaw.split("\n").filter { it.isNotBlank() }

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding).background(colors.background)) {
        Text(stringResource(R.string.help_title), style = MaterialTheme.typography.headlineSmall, color = colors.onBackground, modifier = Modifier.statusBarsPadding().padding(start = 14.dp, top = 8.dp, bottom = 8.dp))

        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp).height(44.dp).background(colors.surfaceVariant, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
            BasicTextField(value = query, onValueChange = { query = it; entryName = ""; rawHelp = ""; helpLoaded = false }, singleLine = true,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (scoredResults.isNotEmpty()) loadHelp(scoredResults.first().name) }),
                decorationBox = { inner -> Box { if (query.isEmpty()) Text(stringResource(R.string.help_search_hint), style = TextStyle(fontSize = 15.sp, color = colors.onSurfaceVariant.copy(alpha = 0.5f))); inner() } })
        }

        // Outer: transition between search list and detail view
        val outerState = if (entryName.isNotBlank()) entryName else VIEW_LIST
        AnimatedContent(targetState = outerState, transitionSpec = { fadeIn(tween(200)).togetherWith(fadeOut(tween(150))) }, label = "help", modifier = Modifier.weight(1f)) { state ->
            if (state === VIEW_LIST) {
                if (scoredResults.isNotEmpty()) {
                    val listState = rememberLazyListState()
                    val totalItems = scoredResults.size
                    val scrollFraction = if (totalItems > 0 && listState.layoutInfo.totalItemsCount > 0) {
                        val firstVisible = listState.firstVisibleItemIndex.toFloat()
                        val visibleCount = listState.layoutInfo.visibleItemsInfo.size.toFloat()
                        (firstVisible + visibleCount / 2f) / totalItems.toFloat()
                    } else 0f
                    val scope = rememberCoroutineScope()
                    Box(modifier = Modifier.fillMaxWidth()) {
                        LazyColumn(state = listState, modifier = Modifier.padding(start = 14.dp, end = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            item { Text("${scoredResults.size} " + stringResource(R.string.help_matches), style = TextStyle(fontSize = 12.sp, color = colors.onSurfaceVariant), modifier = Modifier.padding(vertical = 4.dp)) }
                            itemsIndexed(scoredResults) { idx, scored ->
                                Row(modifier = Modifier.fillMaxWidth().clickable { loadHelp(scored.name); query = scored.name }.padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(scored.name, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = colors.onSurface), modifier = Modifier.weight(1f))
                                    if (scored.score > 0) {
                                        Text(scored.score.toString(), style = TextStyle(fontSize = 11.sp, color = colors.onSurfaceVariant.copy(alpha = 0.4f)), modifier = Modifier.padding(start = 6.dp))
                                    }
                                }
                            }
                        }
                        // Scroll position indicator
                        if (totalItems > listState.layoutInfo.visibleItemsInfo.size) {
                            Canvas(modifier = Modifier.fillMaxHeight().width(4.dp).align(Alignment.CenterEnd)) {
                                val barHeight = size.height * (listState.layoutInfo.visibleItemsInfo.size.toFloat() / totalItems.toFloat()).coerceIn(0.05f, 1f)
                                val barTop = size.height * scrollFraction.coerceIn(0f, 1f) - barHeight / 2f
                                drawRoundRect(
                                    color = colors.onSurfaceVariant.copy(alpha = 0.2f),
                                    topLeft = Offset(0f, barTop.coerceIn(0f, size.height - barHeight)),
                                    size = Size(size.width, barHeight),
                                    cornerRadius = CornerRadius(2f, 2f)
                                )
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.help_empty), color = colors.onSurfaceVariant) }
                }
            } else {
                LazyColumn(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
Text(state as String, style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = colors.primary), modifier = Modifier.weight(1f))
                            Button(onClick = { onInsert(state + "(") }, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) { Text(stringResource(R.string.btn_insert)) }
                        }
                    }
                    item {
                        // Inner: transition between loading and content
                        AnimatedContent(targetState = when { !helpLoaded -> "loading"; isNoHelp -> "suggest"; rawHelp.isNotBlank() -> "done"; else -> "empty" },
                            transitionSpec = { fadeIn(tween(150)).togetherWith(fadeOut(tween(100))) },
                            label = "detail-content"
                        ) { innerState ->
                            when (innerState) {
                                "loading" -> Text(stringResource(R.string.help_loading), color = colors.onSurfaceVariant)
                                "empty" -> Text(stringResource(R.string.help_no_result) + " '$state'", color = colors.error)
                                "suggest" -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    val suggestions = rawHelp.removePrefix("NoHelp:").trim()
                                    Text(stringResource(R.string.help_no_result) + " '$state'", color = colors.error)
                                    val items = try {
                                        Regex("""\d+/\s*(\S+)""").findAll(suggestions).map { it.groupValues[1] }.toList()
                                    } catch (_: Exception) { emptyList() }
                                    if (items.isNotEmpty()) {
                                        Text(stringResource(R.string.help_see_also), style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.primary))
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items.forEach { sug ->
                                                AssistChip(onClick = { loadHelp(sug); query = sug }, label = { Text(sug, fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = colors.onSurface) }, shape = RoundedCornerShape(12.dp))
                                            }
                                        }
                                    }
                                }
                                else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (desc.isNotBlank()) {
                                        Text(stringResource(R.string.help_desc), style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.primary))
                                        Text(desc, style = TextStyle(fontSize = 15.sp, color = colors.onBackground))
                                    }
                                    if (related.isNotEmpty()) {
                                        Text(stringResource(R.string.help_related), style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.primary))
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            related.forEach { rel -> AssistChip(onClick = { loadHelp(rel); query = rel }, label = { Text(rel, fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = colors.onSurface) }, shape = RoundedCornerShape(12.dp)) }
                                        }
                                    }
                                    if (exampleLines.isNotEmpty()) {
                                        Text(stringResource(R.string.help_examples), style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.primary))
                                        SelectionContainer {
                                            Column(modifier = Modifier.fillMaxWidth().background(colors.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                exampleLines.forEach { line ->
                                                    Text(line.trim(), style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = colors.onSurfaceVariant),
                                                        modifier = Modifier.fillMaxWidth().clickable { onInsert(line.trim()) }.padding(horizontal = 6.dp, vertical = 3.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }
}

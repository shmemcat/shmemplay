package io.github.shmemcat.shmemplay.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.shmemcat.shmemplay.R
import io.github.shmemcat.shmemplay.playlists.RuleLibraryIndex
import io.github.shmemcat.shmemplay.playlists.RuleValueChoice
import io.github.shmemcat.shmemplay.tracks.FastScrollIndex
import io.github.shmemcat.shmemplay.tracks.FastScrollTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class ValueProjection(val query: String, val choices: List<RuleValueChoice>, val targets: List<FastScrollTarget>)

@Composable internal fun PlaylistRuleValuePicker(
    title: String,
    choices: List<RuleValueChoice>,
    initialValues: List<String>,
    multiple: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(initialValues) }
    val selectedSet = remember(selected) { selected.toHashSet() }
    val listState = rememberLazyListState()
    val projection by produceState<ValueProjection?>(null, query, choices) {
        if (query.isNotBlank()) delay(120)
        value = withContext(Dispatchers.Default) {
            val filtered = RuleLibraryIndex.filter(choices, query)
            ValueProjection(query, filtered, FastScrollIndex.targets(filtered.map { it.label }))
        }
    }
    val current = projection?.takeIf { it.query == query }
    LaunchedEffect(current?.query) { listState.scrollToItem(0) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.92f).fillMaxHeight(.82f).imePadding(), shape = RoundedCornerShape(16.dp), tonalElevation = 3.dp) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, top = 10.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    PlayerButton(R.drawable.ic_x, "Close value picker", action = onDismiss)
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(current?.let { "${it.choices.size} values" } ?: "Searching…", style = MaterialTheme.typography.bodySmall)
                    Text("${selected.size} selected", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
                Box(Modifier.weight(1f).fillMaxWidth().testTag("rule-value-list")) {
                    if (current == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else if (current.choices.isEmpty()) Text("No matching values. Try a different search.", Modifier.align(Alignment.Center).padding(20.dp))
                    else FastScrollableLazyColumn(listState, current.choices.size, null, false, precomputedTargets = current.targets) {
                        items(current.choices, key = { it.value }) { choice ->
                            val checked = choice.value in selectedSet
                            Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).toggleable(checked,
                                role = if (multiple) Role.Checkbox else Role.RadioButton,
                                onValueChange = { selected = if (!multiple) listOf(choice.value) else if (checked) selected - choice.value else selected + choice.value })
                                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(choice.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                    Text("${choice.songCount} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (multiple) Checkbox(checked, onCheckedChange = null) else RadioButton(checked, onClick = null)
                            }
                        }
                    }
                }
                HorizontalDivider()
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    label = { Text("Search values") }, singleLine = true,
                    leadingIcon = { PlayerIcon(R.drawable.ic_search, null) },
                    trailingIcon = { if (query.isNotEmpty()) PlayerButton(R.drawable.ic_x, "Clear value search", action = { query = "" }) })
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onConfirm(selected) }, enabled = selected.isNotEmpty()) {
                        Text(if (multiple) "Use ${selected.size} values" else "Use selection")
                    }
                }
            }
        }
    }
}

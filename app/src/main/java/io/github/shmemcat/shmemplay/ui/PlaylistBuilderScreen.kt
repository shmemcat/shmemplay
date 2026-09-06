package io.github.shmemcat.shmemplay.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shmemcat.shmemplay.R
import io.github.shmemcat.shmemplay.domain.*
import io.github.shmemcat.shmemplay.playlists.*
import io.github.shmemcat.shmemplay.tracks.LibrarySearch
import io.github.shmemcat.shmemplay.tracks.LibraryTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private data class BuilderDraft(val name: String, val live: Boolean, val excludeDuplicates: Boolean, val rule: PlaylistRuleNode) {
    fun encode(): String = JSONObject().put("name", name).put("live", live).put("dedupe", excludeDuplicates)
        .put("rule", LocalPlaylistRecipes.encodeRule(rule)).toString()
    companion object {
        fun decode(json: String?, initial: LocalPlaylistRecipe?): BuilderDraft {
            if (json != null) return JSONObject(json).let { BuilderDraft(it.getString("name"), it.getBoolean("live"), it.getBoolean("dedupe"), LocalPlaylistRecipes.decodeRule(it.getJSONObject("rule"))) }
            val rule = initial?.rule ?: PlaylistRuleNode.Group()
            return BuilderDraft(initial?.name.orEmpty(), initial?.live ?: true, initial?.excludeDuplicates ?: true,
                if (rule is PlaylistRuleNode.Group) rule else PlaylistRuleNode.Group(children = listOf(rule)))
        }
    }
}
private data class PreviewRequest(val index: RuleLibraryIndex?, val sources: List<PlaylistSnapshot>, val rule: PlaylistRuleNode, val dedupe: Boolean)
private data class BuilderPreview(val request: PreviewRequest, val tracks: List<LibraryTrack>, val error: String? = null) {
    val durationMs = tracks.sumOf { it.durationMs }
}
private enum class BuilderField(val label: String, val metadata: RuleField? = null) {
    PLAYLIST("Playlist"), ARTIST("Artist", RuleField.ARTIST), GENRE("Genre", RuleField.GENRE), TITLE("Song title", RuleField.TITLE)
}
private fun fieldOf(rule: PlaylistRuleNode) = when (rule) {
    is PlaylistRuleNode.Metadata -> BuilderField.entries.first { it.metadata == rule.field }
    else -> BuilderField.PLAYLIST
}
private fun RuleOperator.label() = when (this) {
    RuleOperator.IS -> "is"; RuleOperator.IS_NOT -> "is not"; RuleOperator.IS_ANY_OF -> "is any of"
    RuleOperator.CONTAINS -> "contains"; RuleOperator.STARTS_WITH -> "starts with"; RuleOperator.HAS_NO_VALUE -> "has no value"
}
private fun nodeAt(root: PlaylistRuleNode, path: List<Int>): PlaylistRuleNode = path.fold(root) { n, i -> (n as PlaylistRuleNode.Group).children[i] }
private fun replaceNode(root: PlaylistRuleNode, path: List<Int>, value: PlaylistRuleNode): PlaylistRuleNode {
    if (path.isEmpty()) return value
    val group = root as PlaylistRuleNode.Group
    return group.copy(children = group.children.mapIndexed { i, child -> if (i == path.first()) replaceNode(child, path.drop(1), value) else child })
}
private fun deleteNode(root: PlaylistRuleNode, path: List<Int>): PlaylistRuleNode {
    val parent = nodeAt(root, path.dropLast(1)) as PlaylistRuleNode.Group
    return replaceNode(root, path.dropLast(1), parent.copy(children = parent.children.filterIndexed { i, _ -> i != path.last() }))
}
private fun valueLabel(rule: PlaylistRuleNode, sources: List<PlaylistSnapshot>): String = when (rule) {
    is PlaylistRuleNode.Membership -> (if (rule.present) "is in " else "is not in ") +
        (sources.firstOrNull { it.document.uri.toString() == rule.source }?.document?.displayName?.substringBeforeLast('.') ?: "Missing source — choose a playlist")
    is PlaylistRuleNode.Metadata -> rule.operator.label() + if (rule.operator == RuleOperator.HAS_NO_VALUE) "" else " " + rule.values.joinToString(", ")
    is PlaylistRuleNode.Group -> "${rule.children.size} conditions · match ${rule.match.name.lowercase()}"
}

@Composable internal fun PlaylistBuilderScreen(
    state: LibraryBrowserState, actions: LibraryBrowserActions, initial: LocalPlaylistRecipe?,
    draftJson: String?, onDraftChange: (String) -> Unit, onDismiss: () -> Unit, onSaved: () -> Unit,
) {
    val draft = remember(draftJson, initial) { BuilderDraft.decode(draftJson, initial) }
    fun update(next: BuilderDraft) = onDraftChange(next.encode())
    var groupPath by rememberSaveable { mutableStateOf(listOf<Int>()) }
    var editJson by rememberSaveable { mutableStateOf<String?>(null) }
    var editPath by rememberSaveable { mutableStateOf(listOf<Int>()) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var showPreview by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val sources = state.playlistScan.playlists
    val index by produceState<RuleLibraryIndex?>(null, state.allTracks, state.includedFolderRoots) {
        value = null
        value = withContext(Dispatchers.Default) { RuleLibraryIndex(state.allTracks.filter { it.folderRoot in state.includedFolderRoots }) }
    }
    val request = remember(index, sources, draft.rule, draft.excludeDuplicates) { PreviewRequest(index, sources, draft.rule, draft.excludeDuplicates) }
    val computed by produceState<BuilderPreview?>(null, request) {
        value = withContext(Dispatchers.Default) {
            val library = request.index ?: return@withContext null
            runCatching {
                when (val evaluated = library.evaluate(request.rule, request.sources, request.dedupe)) {
                    is RecipeEvaluation.Success -> BuilderPreview(request, library.tracks.filter { it.stableId in evaluated.trackIdentities })
                    is RecipeEvaluation.UnknownSources -> BuilderPreview(request, emptyList(), "A source playlist is unavailable. Refresh or choose another playlist.")
                }
            }.getOrElse { BuilderPreview(request, emptyList(), it.message ?: "Check your rules.") }
        }
    }
    val preview = computed?.takeIf { it.request == request }
    val saveError = (state.mutation as? BrowserMutationState.Error)?.message
    LaunchedEffect(state.mutation, submitted) {
        if (submitted) when (state.mutation) {
            is BrowserMutationState.Result -> { actions.clearMutationMessage(); submitted = false; onSaved() }
            is BrowserMutationState.Error -> submitted = false
            else -> Unit
        }
    }
    fun back() {
        if (submitted) return
        when { editJson != null -> editJson = null; showPreview -> showPreview = false
            groupPath.isNotEmpty() -> groupPath = groupPath.dropLast(1); else -> onDismiss() }
    }
    BackHandler { back() }
    fun startEdit(path: List<Int>, isNew: Boolean) {
        editPath = path; adding = isNew
        editJson = LocalPlaylistRecipes.encodeRule(if (isNew) PlaylistRuleNode.Metadata(RuleField.ARTIST, RuleOperator.IS) else nodeAt(draft.rule, path)).toString()
    }
    fun addGroup(path: List<Int>) {
        val parent = nodeAt(draft.rule, path) as PlaylistRuleNode.Group
        update(draft.copy(rule = replaceNode(draft.rule, path, parent.copy(children = parent.children + PlaylistRuleNode.Group(RecipeMatch.ANY)))))
        groupPath = path + parent.children.size
    }
    val edited = remember(editJson) { editJson?.let { LocalPlaylistRecipes.decodeRule(JSONObject(it)) } }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 62.dp), verticalAlignment = Alignment.CenterVertically) {
                BackChevronButton(::back, !submitted)
                Text(when { edited != null -> if (adding) "Add rule" else "Edit rule"; showPreview -> "Matching songs"; groupPath.isNotEmpty() -> "Rule group"; else -> "Playlist builder" },
                    Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider()
            if (edited != null) {
                RuleDetailEditor(edited, index, sources, adding, { editJson = LocalPlaylistRecipes.encodeRule(it).toString() },
                    onDone = {
                        val next = if (adding) {
                            val parent = nodeAt(draft.rule, editPath) as PlaylistRuleNode.Group
                            replaceNode(draft.rule, editPath, parent.copy(children = parent.children + edited))
                        } else replaceNode(draft.rule, editPath, edited)
                        update(draft.copy(rule = next)); editJson = null
                    }, onRemove = { update(draft.copy(rule = deleteNode(draft.rule, editPath))); editJson = null })
            } else if (showPreview) {
                Column(Modifier.weight(1f)) {
                    Text("${preview?.tracks?.size ?: 0} songs · ${minutes(preview?.durationMs ?: 0)}", Modifier.padding(18.dp), style = MaterialTheme.typography.titleMedium)
                    val listState = rememberLazyListState()
                    val rows = preview?.tracks.orEmpty()
                    FastScrollableLazyColumn(listState, rows.size, null, true) {
                        items(rows, key = { it.stableId }) { track ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                AlbumArtwork(track, false)
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Button(onClick = { showPreview = false }, Modifier.fillMaxWidth().padding(16.dp)) { Text("Back to rules") }
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp)) {
                    if (groupPath.isEmpty()) {
                        OutlinedTextField(draft.name, { update(draft.copy(name = it.take(100))) }, label = { Text("Playlist name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(draft.live, { update(draft.copy(live = true)) }, label = { Text("Live") }, leadingIcon = { PlayerIcon(R.drawable.ic_repeat, null, Modifier.size(18.dp)) }, modifier = Modifier.weight(1f))
                            FilterChip(!draft.live, { update(draft.copy(live = false)) }, label = { Text("Snapshot") }, leadingIcon = { PlayerIcon(R.drawable.ic_list_music, null, Modifier.size(18.dp)) }, modifier = Modifier.weight(1f))
                        }
                        Text(if (draft.live) "Updates as your library and playlists change." else "Keeps the songs matching now as a file playlist.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(24.dp))
                    }
                    val group = nodeAt(draft.rule, groupPath) as PlaylistRuleNode.Group
                    RuleGroupOverview(group, groupPath, sources,
                        onMatch = { path, match -> val n = nodeAt(draft.rule, path) as PlaylistRuleNode.Group; update(draft.copy(rule = replaceNode(draft.rule, path, n.copy(match = match)))) },
                        onEdit = { startEdit(it, false) }, onAddRule = { startEdit(it, true) }, onAddGroup = ::addGroup, onOpenGroup = { groupPath = it })
                    if (groupPath.isEmpty()) {
                        HorizontalDivider(Modifier.padding(top = 18.dp, bottom = 8.dp))
                        CheckOption("Don't add duplicate songs", draft.excludeDuplicates) { update(draft.copy(excludeDuplicates = it)) }
                    }
                }
                HorizontalDivider()
                if (groupPath.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { update(draft.copy(rule = deleteNode(draft.rule, groupPath))); groupPath = groupPath.dropLast(1) }) { Text("Remove group", color = MaterialTheme.colorScheme.error) }
                        Button(onClick = ::back) { Text("Done") }
                    }
                } else Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                    if (saveError != null) Text(saveError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    if (preview?.error != null) Text(preview.error, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                    else TextButton(onClick = { showPreview = true }, enabled = preview != null && !submitted, modifier = Modifier.fillMaxWidth()) {
                        Text(preview?.let { "${it.tracks.size} songs · ${minutes(it.durationMs)}" } ?: "Finding matching songs…", Modifier.weight(1f))
                        Text("Preview"); PlayerIcon(R.drawable.ic_chevron_down, null, Modifier.rotate(-90f).size(18.dp))
                    }
                    Button(enabled = draft.name.isNotBlank() && preview != null && preview.error == null && !state.busy && !submitted,
                        onClick = { actions.clearMutationMessage(); submitted = true; actions.createNestedPlaylist(draft.name, draft.rule, draft.live, initial?.id, draft.excludeDuplicates) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(9.dp)) {
                        if (submitted) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text(if (initial == null) "Create playlist" else if (draft.live) "Save changes" else "Create snapshot")
                    }
                }
            }
        }
    }
}

private fun minutes(ms: Long): String = if (ms >= 3_600_000) "${ms / 3_600_000}h ${ms % 3_600_000 / 60_000}m" else "${ms / 60_000} min"

@Composable private fun RuleGroupOverview(
    group: PlaylistRuleNode.Group, path: List<Int>, sources: List<PlaylistSnapshot>, depth: Int = 0,
    onMatch: (List<Int>, RecipeMatch) -> Unit, onEdit: (List<Int>) -> Unit, onAddRule: (List<Int>) -> Unit,
    onAddGroup: (List<Int>) -> Unit, onOpenGroup: (List<Int>) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (path.isEmpty()) "Rules" else "Group", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text("Match", style = MaterialTheme.typography.bodySmall)
            SmallRuleMenu(group.match.name, RecipeMatch.entries.map { it.name }) { onMatch(path, RecipeMatch.valueOf(it)) }
        }
        group.children.forEachIndexed { index, child ->
            val childPath = path + index
            if (index > 0) Text(if (group.match == RecipeMatch.ALL) "AND" else "OR", Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (child is PlaylistRuleNode.Group && depth < 1) Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .5f)), tonalElevation = 2.dp) {
                Column(Modifier.padding(10.dp)) { RuleGroupOverview(child, childPath, sources, depth + 1, onMatch, onEdit, onAddRule, onAddGroup, onOpenGroup) }
            } else Surface(onClick = { if (child is PlaylistRuleNode.Group) onOpenGroup(childPath) else onEdit(childPath) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), tonalElevation = 2.dp) {
                Row(Modifier.padding(12.dp).heightIn(min = 42.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (child is PlaylistRuleNode.Group) "Nested group" else fieldOf(child).label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(valueLabel(child, sources), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    PlayerIcon(R.drawable.ic_chevron_down, null, Modifier.rotate(-90f).size(18.dp))
                }
            }
        }
        if (group.children.isEmpty()) Text("Add a rule to this group.", Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onAddRule(path) }, enabled = path.size < 8) { Text("+ Rule") }
            TextButton(onClick = { onAddGroup(path) }, enabled = path.size < 7) { Text("+ Group") }
            if (path.isNotEmpty()) { Spacer(Modifier.weight(1f)); PlayerButton(R.drawable.ic_ellipsis, "Group options", action = { onOpenGroup(path) }) }
        }
    }
}

@Composable private fun SmallRuleMenu(label: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text(label); PlayerIcon(R.drawable.ic_chevron_down, null, Modifier.size(16.dp).padding(start = 3.dp)) }
        DropdownMenu(expanded, { expanded = false }) { options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false }) } }
    }
}

@Composable private fun ColumnScope.RuleDetailEditor(
    node: PlaylistRuleNode, index: RuleLibraryIndex?, sources: List<PlaylistSnapshot>, adding: Boolean,
    onChange: (PlaylistRuleNode) -> Unit, onDone: () -> Unit, onRemove: () -> Unit,
) {
    val field = fieldOf(node)
    val metadata = node as? PlaylistRuleNode.Metadata
    var picker by rememberSaveable { mutableStateOf(false) }
    val values = when (node) { is PlaylistRuleNode.Membership -> listOf(node.source).filter(String::isNotBlank); is PlaylistRuleNode.Metadata -> node.values; else -> emptyList() }
    val isText = metadata?.operator == RuleOperator.CONTAINS || metadata?.operator == RuleOperator.STARTS_WITH
    val noValue = metadata?.operator == RuleOperator.HAS_NO_VALUE
    val valid = noValue || values.isNotEmpty() && values.all(String::isNotBlank)
    val choices by produceState<List<RuleValueChoice>>(emptyList(), field, index, sources) {
        value = withContext(Dispatchers.Default) {
            if (field.metadata != null) index?.choices(field.metadata).orEmpty() else sources.map { source ->
                val label = source.document.displayName.substringBeforeLast('.')
                RuleValueChoice(source.document.uri.toString(), label, source.resolvedTrackIds.size, LibrarySearch.normalize(label))
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, RuleValueChoice::label))
        }
    }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Choose what a song needs to match.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column {
            Text("Song field", style = MaterialTheme.typography.labelMedium)
            SmallRuleMenu(field.label, BuilderField.entries.map { it.label }) { label ->
                val next = BuilderField.entries.first { it.label == label }
                onChange(if (next.metadata == null) PlaylistRuleNode.Membership("") else PlaylistRuleNode.Metadata(next.metadata, RuleOperator.IS))
            }
        }
        Column {
            Text("Condition", style = MaterialTheme.typography.labelMedium)
            if (node is PlaylistRuleNode.Membership) SmallRuleMenu(if (node.present) "is in" else "is not in", listOf("is in", "is not in")) { onChange(node.copy(present = it == "is in")) }
            else if (metadata != null) SmallRuleMenu(metadata.operator.label(), RuleOperator.entries.map { it.label() }) { label ->
                val op = RuleOperator.entries.first { it.label() == label }
                onChange(metadata.copy(operator = op, values = if (op == RuleOperator.HAS_NO_VALUE) emptyList() else if (op == RuleOperator.IS_ANY_OF) values else values.take(1)))
            }
        }
        if (!noValue) {
            if (isText) OutlinedTextField(values.firstOrNull().orEmpty(), { onChange(metadata.copy(values = listOf(it))) }, label = { Text("Value") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            else Column {
                Text(if (metadata?.operator == RuleOperator.IS_ANY_OF) "Values" else "Value", style = MaterialTheme.typography.labelMedium)
                OutlinedButton(onClick = { picker = true }, enabled = index != null, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(8.dp)) {
                    val label = if (metadata?.operator == RuleOperator.IS_ANY_OF) if (values.isEmpty()) "Choose values" else "${values.size} selected"
                        else if (node is PlaylistRuleNode.Membership) sources.firstOrNull { it.document.uri.toString() == node.source }?.document?.displayName?.substringBeforeLast('.') ?: "Choose playlist"
                        else values.firstOrNull() ?: "Choose ${field.label.lowercase()}"
                    Text(label, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    PlayerIcon(R.drawable.ic_chevron_down, "Choose value", Modifier.rotate(-90f).size(18.dp))
                }
                if (metadata?.operator == RuleOperator.IS_ANY_OF && values.isNotEmpty()) Text(values.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
        Text("THIS RULE READS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${field.label} ${valueLabel(node, sources)}", style = MaterialTheme.typography.bodyLarge)
    }
    HorizontalDivider()
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Button(onClick = onDone, enabled = valid, modifier = Modifier.fillMaxWidth()) { Text(if (adding) "Add rule" else "Done") }
        if (!adding) TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) { Text("Remove rule", color = MaterialTheme.colorScheme.error) }
    }
    if (picker) PlaylistRuleValuePicker("Choose ${field.label.lowercase()}", choices, values, metadata?.operator == RuleOperator.IS_ANY_OF,
        onDismiss = { picker = false }, onConfirm = { selected ->
            onChange(when (node) { is PlaylistRuleNode.Membership -> node.copy(source = selected.first()); is PlaylistRuleNode.Metadata -> node.copy(values = selected); else -> node }); picker = false
        })
}

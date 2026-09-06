package io.github.shmemcat.shmemplay.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import io.github.shmemcat.shmemplay.domain.*
import io.github.shmemcat.shmemplay.playlists.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun NestedRulesDialog(state: LibraryBrowserState, actions: LibraryBrowserActions, onDismiss: () -> Unit, initial: LocalPlaylistRecipe? = null) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var live by rememberSaveable { mutableStateOf(initial?.live ?: false) }
    var rule by remember { mutableStateOf<PlaylistRuleNode>(initial?.rule ?: PlaylistRuleNode.Group()) }
    val sources = state.playlistScan.playlists
    val preview by produceState("Add a rule to begin.", rule, sources, state.allTracks, state.includedFolderRoots) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                when(val result = NestedPlaylistRules.evaluate(state.tracks.mapTo(linkedSetOf()) { it.stableId },
                    sources.associate { it.document.uri.toString() to it.resolvedTrackIds },rule)) {
                    is RecipeEvaluation.Success -> result.trackIdentities.size.toString() + " songs match"
                    is RecipeEvaluation.UnknownSources -> "Source unavailable. Refresh or choose another playlist."
                }
            }.getOrElse { it.message ?: "Invalid rules" }
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Create from rules") }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            OutlinedTextField(name,{name=it},label={Text("Playlist name")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Row { FilterChip(!live,{live=false},label={Text("Snapshot")}); Spacer(Modifier.width(8.dp)); FilterChip(live,{live=true},label={Text("Live")}) }
            Text(if(live) "Local rules follow source M3Us. Playing creates an independent queue." else "Write a real M3U snapshot to your playlist folder.",style=MaterialTheme.typography.bodySmall)
            RuleNodeEditor(rule,sources,0,{rule=it})
            Text(preview,modifier=Modifier.padding(vertical=12.dp))
            if(state.recipes.isNotEmpty()) {
                HorizontalDivider()
                Text("Saved snapshot recipes",style=MaterialTheme.typography.titleSmall)
                state.recipes.forEach { recipe -> MenuAction("Rerun " + recipe.playlistName) { actions.rerunRecipe(recipe) } }
            }
        }
    }, confirmButton = { TextButton(enabled = name.isNotBlank() && preview.endsWith("songs match") && !state.busy, onClick = {
        actions.createNestedPlaylist(name,rule,live,initial?.id);onDismiss()
    }) {Text("Create")}}, dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}

@Composable private fun RuleNodeEditor(node: PlaylistRuleNode, sources: List<PlaylistSnapshot>, depth: Int, onChange: (PlaylistRuleNode) -> Unit) {
    when(node) {
        is PlaylistRuleNode.Membership -> {
            var choose by remember {mutableStateOf(false)}
            var membershipMenu by remember { mutableStateOf(false) }
            Column {
                Row {
                    Box {
                        TextButton(onClick = { membershipMenu = true }) {
                            Text(if (node.present) "is in" else "is not in")
                            PlayerIcon(io.github.shmemcat.shmemplay.R.drawable.ic_chevron_down, null, Modifier.padding(start = 4.dp).size(18.dp))
                        }
                        DropdownMenu(membershipMenu, { membershipMenu = false }) {
                            listOf(true to "is in", false to "is not in").forEach { (present, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { onChange(node.copy(present = present)); membershipMenu = false })
                            }
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        TextButton(modifier=Modifier.fillMaxWidth(),onClick={choose=true}) { Text(sources.firstOrNull {it.document.uri.toString()==node.source}?.document?.displayName ?: "Missing source — choose",maxLines=1,overflow=TextOverflow.Ellipsis) }
                        DropdownMenu(choose,{choose=false},modifier=Modifier.heightIn(max=300.dp)) {
                            sources.forEach { source -> DropdownMenuItem(text={Text(source.document.displayName)},onClick={onChange(node.copy(source=source.document.uri.toString()));choose=false}) }
                        }
                    }
                }
            }
        }
        is PlaylistRuleNode.Group -> {
            Surface(tonalElevation=2.dp,modifier=Modifier.fillMaxWidth().padding(top=6.dp)) {
                Column(Modifier.padding(start=if(depth==0) 0.dp else 4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(node.match == RecipeMatch.ALL, { onChange(node.copy(match = RecipeMatch.ALL)) }, label = { Text("Match all") })
                        FilterChip(node.match == RecipeMatch.ANY, { onChange(node.copy(match = RecipeMatch.ANY)) }, label = { Text("Match any") })
                    }
                    node.children.forEachIndexed { index, child ->
                        Column {
                            RuleNodeEditor(child,sources,depth+1) { changed -> onChange(node.copy(children=node.children.toMutableList().also {it[index]=changed})) }
                            TextButton(onClick={onChange(node.copy(children=node.children.filterIndexed {i,_->i!=index}))}) {Text("Remove " + if(child is PlaylistRuleNode.Group) "group" else "rule")}
                        }
                    }
                    Row {
                        TextButton(enabled=sources.isNotEmpty(),onClick={onChange(node.copy(children=node.children+PlaylistRuleNode.Membership(sources.first().document.uri.toString())))}) {Text("+ Rule")}
                        TextButton(enabled=depth<7,onClick={onChange(node.copy(children=node.children+PlaylistRuleNode.Group()))}) {Text("+ Group")}
                    }
                }
            }
        }
    }
}

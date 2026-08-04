package io.github.shmemcat.shmemplaylist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.shmemcat.shmemplaylist.playlists.MembershipScanResult
import io.github.shmemcat.shmemplaylist.playlists.PlaylistCoreState
import io.github.shmemcat.shmemplaylist.playlists.PlaylistMembership
import io.github.shmemcat.shmemplaylist.playlists.PlaylistTreeGrantState
import io.github.shmemcat.shmemplaylist.tracks.ResolutionUiState
import io.github.shmemcat.shmemplaylist.tracks.TrackCandidate
import io.github.shmemcat.shmemplaylist.ui.theme.ShmemplaylistTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShmemplaylistApp(
    state: ResolutionUiState = ResolutionUiState.Ready(),
    playlistState: PlaylistCoreState = PlaylistCoreState(),
    membershipOperations: MembershipOperationsUiModel = MembershipOperationsUiModel(),
    onExport: (String) -> Unit = {},
    onRequestPermission: () -> Unit = {},
    onCandidateSelected: (TrackCandidate, Boolean) -> Unit = { _, _ -> },
    onForgetAlias: (Long) -> Unit = {},
    onSelectPlaylistTree: () -> Unit = {},
    onClearPlaylistTree: () -> Unit = {},
    onDiscoverPlaylists: () -> Unit = {},
    onTestProviderCapabilities: () -> Unit = {},
    onScanMembership: () -> Unit = {},
    onShowHistory: () -> Unit = {},
    onShowSettings: () -> Unit = {},
) {
    ShmemplaylistTheme {
        var menuExpanded by rememberSaveable { mutableStateOf(false) }
        var confirmation by rememberSaveable { mutableStateOf<GlobalConfirmation?>(null) }
        val diagnostics = state.diagnosticText()
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Shmemplaylist",
                            modifier = Modifier.semantics { testTag = "app-title" },
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.semantics {
                                contentDescription = "More options"
                                testTag = "overflow-menu"
                            },
                        ) {
                            Text("⋮", style = MaterialTheme.typography.titleLarge)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (playlistState.grant is PlaylistTreeGrantState.NotConfigured) {
                                            "Choose playlist folder"
                                        } else {
                                            "Change playlist folder"
                                        },
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onSelectPlaylistTree()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Rescan playlists") },
                                enabled = !playlistState.busy,
                                onClick = {
                                    menuExpanded = false
                                    onDiscoverPlaylists()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Test provider capabilities") },
                                enabled = !playlistState.busy &&
                                    playlistState.grant is PlaylistTreeGrantState.Valid,
                                onClick = {
                                    menuExpanded = false
                                    confirmation = GlobalConfirmation.ProviderTest
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Export diagnostics") },
                                enabled = diagnostics != null,
                                onClick = {
                                    menuExpanded = false
                                    diagnostics?.let(onExport)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("History") },
                                onClick = {
                                    menuExpanded = false
                                    onShowHistory()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    menuExpanded = false
                                    onShowSettings()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Forget playlist folder") },
                                enabled = !playlistState.busy &&
                                    playlistState.grant !is PlaylistTreeGrantState.NotConfigured,
                                onClick = {
                                    menuExpanded = false
                                    confirmation = GlobalConfirmation.ForgetFolder
                                },
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { innerPadding ->
            val bodyModifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
            Column(
                modifier = if (state is ResolutionUiState.Resolved) {
                    bodyModifier
                } else {
                    bodyModifier.verticalScroll(rememberScrollState())
                },
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (state) {
                    is ResolutionUiState.Ready -> {
                        Text(
                            "Share a playing file from GoneMAD to identify its Android audio-library track.",
                        )
                        if (state.aliases.isNotEmpty()) {
                            Text("Remembered matches", style = MaterialTheme.typography.titleMedium)
                            state.aliases.forEach { alias ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(alias.displayName)
                                    Text(alias.target.volumeName, style = MaterialTheme.typography.bodySmall)
                                    Button(onClick = { onForgetAlias(alias.aliasId) }) {
                                        Text("Forget")
                                    }
                                }
                            }
                        }
                    }

                    ResolutionUiState.Probing -> {
                        CircularProgressIndicator(
                            modifier = Modifier.semantics { testTag = "probe-progress" },
                        )
                        Text("Inspecting the shared URI…")
                    }

                    ResolutionUiState.Querying -> {
                        CircularProgressIndicator()
                        Text("Finding the matching track in the Android audio library…")
                    }

                    ResolutionUiState.PermissionRequired -> {
                        Text(
                            "Audio access required",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Shmemplaylist needs audio-library access to match GoneMAD's " +
                                "temporary share to exactly one Android track.",
                        )
                        Button(onClick = onRequestPermission) {
                            Text("Allow audio access")
                        }
                    }

                    is ResolutionUiState.Invalid -> {
                        Text(
                            text = "Share could not be inspected",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(state.message)
                    }

                    is ResolutionUiState.Failure -> {
                        Text(
                            state.message,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    is ResolutionUiState.NotFound -> {
                        Text("No safe match found", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "The shared file was readable, but no MediaStore track matched it. " +
                                "Rescan the library in GoneMAD/Android and share the file again.",
                        )
                    }

                    is ResolutionUiState.Resolved -> {
                        MembershipScreen(
                            playlistState = playlistState,
                            onScanMembership = onScanMembership,
                            membershipOperations = membershipOperations,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    is ResolutionUiState.Ambiguous -> {
                        ManualResolution(
                            state = state,
                            onCandidateSelected = onCandidateSelected,
                        )
                    }
                }
            }
        }
        confirmation?.let { pending ->
            GlobalConfirmationDialog(
                confirmation = pending,
                onDismiss = { confirmation = null },
                onConfirm = {
                    confirmation = null
                    when (pending) {
                        GlobalConfirmation.ProviderTest -> onTestProviderCapabilities()
                        GlobalConfirmation.ForgetFolder -> onClearPlaylistTree()
                    }
                },
            )
        }
    }
}

@Composable
private fun MembershipScreen(
    playlistState: PlaylistCoreState,
    onScanMembership: () -> Unit,
    membershipOperations: MembershipOperationsUiModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (playlistState.busy && playlistState.scanTotal > 0) {
            LinearProgressIndicator(
                progress = { playlistState.scanCompleted.toFloat() / playlistState.scanTotal },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Scanning ${playlistState.scanCompleted} of ${playlistState.scanTotal}")
        }
        when (val result = playlistState.membership) {
            is MembershipScanResult.CandidatePathUnavailable ->
                Text(
                    "This track has no safe playlist path (${result.reason}).",
                    color = MaterialTheme.colorScheme.error,
                )
            is MembershipScanResult.Success -> {
                MembershipList(
                    result = result,
                    operations = membershipOperations,
                    modifier = Modifier.weight(1f),
                )
            }
            null -> {
                if (!playlistState.busy && playlistState.playlists.isNotEmpty()) {
                    Button(
                        onClick = onScanMembership,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Scan playlists")
                    }
                }
            }
        }
    }
}

enum class MembershipOperation { ADD, REMOVE }

sealed interface MembershipOperationUiState {
    data object Ready : MembershipOperationUiState
    data class Confirmation(
        val message: String,
        val requiresReconfirmation: Boolean = false,
    ) : MembershipOperationUiState
    data class Running(val message: String) : MembershipOperationUiState
    data class Result(val message: String, val canUndo: Boolean = false) : MembershipOperationUiState
    data class RecoveryRequired(val message: String) : MembershipOperationUiState
}

data class MembershipOperationsUiModel(
    val enabled: Boolean = false,
    val state: MembershipOperationUiState = MembershipOperationUiState.Ready,
    val onApply: (MembershipOperation, Set<String>) -> Unit = { _, _ -> },
    val onConfirm: () -> Unit = {},
    val onUndo: () -> Unit = {},
    val onRecover: () -> Unit = {},
)

@Composable
private fun MembershipList(
    result: MembershipScanResult.Success,
    operations: MembershipOperationsUiModel,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedUris by rememberSaveable { mutableStateOf(listOf<String>()) }
    var pendingOperation by rememberSaveable { mutableStateOf<MembershipOperation?>(null) }
    val visible = result.playlists
        .filter { it.playlist.displayName.contains(query, ignoreCase = true) }
        .filter { tab == 0 || it.containsResolvedTrack }
        .sortedWith(
            compareByDescending<PlaylistMembership> { it.containsResolvedTrack }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.playlist.displayName },
        )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Add") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Remove") })
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search playlists") },
            singleLine = true,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            items(
                items = visible,
                key = { it.playlist.uri.toString() },
            ) { membership ->
                val key = membership.playlist.uri.toString()
                val selected = key in selectedUris
                val occurrenceCount = membership.matchingLineNumbers.size
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selected,
                            onClick = {
                                selectedUris = if (selected) selectedUris - key else selectedUris + key
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = {
                            selectedUris = if (it) (selectedUris + key).distinct() else selectedUris - key
                        },
                    )
                    Text(
                        text = if (occurrenceCount > 0) {
                            "${membership.playlist.displayName} (x$occurrenceCount)"
                        } else {
                            membership.playlist.displayName
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (membership.containsResolvedTrack) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        },
                        color = if (membership.containsResolvedTrack) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
            if (visible.isEmpty()) {
                item { Text("No playlists match this view.") }
            }
            items(
                items = result.warnings,
                key = { "warning-${it.playlist.uri}" },
            ) {
                Text(
                    "${it.playlist.displayName}: could not scan (${it.error.code})",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { selectedUris = emptyList() },
                enabled = selectedUris.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text("Clear")
            }
            Button(
                onClick = {
                    pendingOperation = if (tab == 0) MembershipOperation.ADD else MembershipOperation.REMOVE
                },
                enabled = operations.enabled && selectedUris.isNotEmpty() &&
                    operations.state !is MembershipOperationUiState.Running &&
                    operations.state !is MembershipOperationUiState.RecoveryRequired,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    "${if (tab == 0) "Add to" else "Remove from"} " +
                        "${selectedUris.size}",
                )
            }
        }
        when (val operationState = operations.state) {
            MembershipOperationUiState.Ready -> Unit
            is MembershipOperationUiState.Confirmation -> {
                Text(
                    if (operationState.requiresReconfirmation) {
                        "Playlist contents changed. Review the updated operation."
                    } else {
                        "Review the verified operation."
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(operationState.message)
                Button(onClick = operations.onConfirm, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (operationState.requiresReconfirmation) {
                            "Confirm updated operation"
                        } else {
                            "Apply verified operation"
                        },
                    )
                }
            }
            is MembershipOperationUiState.Running -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            is MembershipOperationUiState.Result -> {
                if (!operationState.canUndo) {
                    Text(operationState.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is MembershipOperationUiState.RecoveryRequired -> {
                Text(operationState.message, color = MaterialTheme.colorScheme.error)
                Button(onClick = operations.onRecover) { Text("Recover safely") }
            }
        }
    }
    pendingOperation?.let { operation ->
        AlertDialog(
            onDismissRequest = { pendingOperation = null },
            title = {
                Text(if (operation == MembershipOperation.ADD) "Add track?" else "Remove track?")
            },
            text = {
                Text(
                    "${if (operation == MembershipOperation.ADD) "Add to" else "Remove from"} " +
                        "${selectedUris.size} selected playlist(s)?",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingOperation = null
                        operations.onApply(operation, selectedUris.toSet())
                    },
                ) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { pendingOperation = null }) { Text("Cancel") }
            },
        )
    }
}

private enum class GlobalConfirmation { ProviderTest, ForgetFolder }

@Composable
private fun GlobalConfirmationDialog(
    confirmation: GlobalConfirmation,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (confirmation) {
                    GlobalConfirmation.ProviderTest -> "Test provider capabilities?"
                    GlobalConfirmation.ForgetFolder -> "Forget playlist folder?"
                },
            )
        },
        text = {
            Text(
                when (confirmation) {
                    GlobalConfirmation.ProviderTest ->
                        "A uniquely named disposable file will be created, verified, and removed."
                    GlobalConfirmation.ForgetFolder ->
                        "Shmemplaylist will release its saved access. Your playlists will not be deleted."
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.semantics { testTag = "confirm-global-action" },
            ) {
                Text(
                    if (confirmation == GlobalConfirmation.ProviderTest) "Run test" else "Forget folder",
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun ResolutionUiState.diagnosticText(): String? = when (this) {
    is ResolutionUiState.NotFound -> report.text
    is ResolutionUiState.Resolved -> report.text
    is ResolutionUiState.Ambiguous -> report.text
    else -> null
}

@Composable
private fun ManualResolution(
    state: ResolutionUiState.Ambiguous,
    onCandidateSelected: (TrackCandidate, Boolean) -> Unit,
) {
    var selected by remember(state) { mutableStateOf<TrackCandidate?>(null) }
    var rememberMatch by remember(state) { mutableStateOf(false) }
    Text("Choose the matching track", style = MaterialTheme.typography.titleMedium)
    Text("Several Android tracks remain possible. Nothing is selected automatically.")
    state.result.candidates.forEach { assessment ->
        val candidate = assessment.candidate
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected?.identity == candidate.identity,
                    onClick = { selected = candidate },
                )
                .padding(vertical = 8.dp),
        ) {
            RadioButton(
                selected = selected?.identity == candidate.identity,
                onClick = { selected = candidate },
            )
            Text(candidate.displayName, style = MaterialTheme.typography.titleSmall)
            Text(candidate.relativePath ?: candidate.identity.volumeName)
            Text(
                assessment.matches.joinToString { match ->
                    "${match.label}: ${if (match.matched) "match" else "different/unknown"}"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = rememberMatch,
                onClick = { rememberMatch = !rememberMatch },
            ),
    ) {
        Checkbox(
            checked = rememberMatch,
            onCheckedChange = { rememberMatch = it },
        )
        Text("Remember this match")
    }
    Button(
        enabled = selected != null,
        onClick = { selected?.let { onCandidateSelected(it, rememberMatch) } },
    ) {
        Text("Use selected track")
    }
}

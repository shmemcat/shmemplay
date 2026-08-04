package io.github.shmemcat.shmemplaylist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.shmemcat.shmemplaylist.playlists.MembershipScanResult
import io.github.shmemcat.shmemplaylist.playlists.PlaylistCoreState
import io.github.shmemcat.shmemplaylist.playlists.PlaylistMembership
import io.github.shmemcat.shmemplaylist.playlists.PlaylistTreeGrantState
import io.github.shmemcat.shmemplaylist.playlists.PhaseSixOperationState
import io.github.shmemcat.shmemplaylist.operations.CompanionAction
import io.github.shmemcat.shmemplaylist.operations.OperationOutcome
import io.github.shmemcat.shmemplaylist.tracks.ResolutionUiState
import io.github.shmemcat.shmemplaylist.tracks.TrackCandidate
import io.github.shmemcat.shmemplaylist.ui.theme.ShmemplaylistTheme

@Composable
fun ShmemplaylistApp(
    state: ResolutionUiState = ResolutionUiState.Ready(),
    playlistState: PlaylistCoreState = PlaylistCoreState(),
    onExport: (String) -> Unit = {},
    onRequestPermission: () -> Unit = {},
    onCandidateSelected: (TrackCandidate, Boolean) -> Unit = { _, _ -> },
    onForgetAlias: (Long) -> Unit = {},
    onSelectPlaylistTree: () -> Unit = {},
    onClearPlaylistTree: () -> Unit = {},
    onDiscoverPlaylists: () -> Unit = {},
    onTestProviderCapabilities: () -> Unit = {},
    onScanMembership: () -> Unit = {},
    onEnablePhaseSixTestMode: () -> Unit = {},
    onProvisionCompanionTestPlaylist: () -> Unit = {},
    onCompanionTestAdd: () -> Unit = {},
    onCompanionTestRemove: () -> Unit = {},
    onCompanionTestUndo: () -> Unit = {},
) {
    ShmemplaylistTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Shmemplaylist",
                    modifier = Modifier.semantics { testTag = "app-title" },
                    style = MaterialTheme.typography.headlineMedium,
                )
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
                        DiagnosticReport(state.report.text, onExport)
                    }

                    is ResolutionUiState.Resolved -> {
                        MembershipScreen(
                            state = state,
                            playlistState = playlistState,
                            onSelectPlaylistTree = onSelectPlaylistTree,
                            onClearPlaylistTree = onClearPlaylistTree,
                            onDiscoverPlaylists = onDiscoverPlaylists,
                            onTestProviderCapabilities = onTestProviderCapabilities,
                            onScanMembership = onScanMembership,
                            onEnablePhaseSixTestMode = onEnablePhaseSixTestMode,
                            onProvisionCompanionTestPlaylist = onProvisionCompanionTestPlaylist,
                            onCompanionTestAdd = onCompanionTestAdd,
                            onCompanionTestRemove = onCompanionTestRemove,
                            onCompanionTestUndo = onCompanionTestUndo,
                        )
                        DiagnosticReport(state.report.text, onExport)
                    }

                    is ResolutionUiState.Ambiguous -> {
                        ManualResolution(
                            state = state,
                            onCandidateSelected = onCandidateSelected,
                        )
                        DiagnosticReport(state.report.text, onExport)
                    }
                }
                if (state !is ResolutionUiState.Resolved) {
                    PlaylistSetup(
                        state = playlistState,
                        onSelectPlaylistTree = onSelectPlaylistTree,
                        onClearPlaylistTree = onClearPlaylistTree,
                        onDiscoverPlaylists = onDiscoverPlaylists,
                        onTestProviderCapabilities = onTestProviderCapabilities,
                    )
                }
                ReadOnlyNotice()
            }
        }
    }
}

@Composable
private fun PlaylistSetup(
    state: PlaylistCoreState,
    onSelectPlaylistTree: () -> Unit,
    onClearPlaylistTree: () -> Unit,
    onDiscoverPlaylists: () -> Unit,
    onTestProviderCapabilities: () -> Unit,
) {
    var confirmCapabilityTest by rememberSaveable { mutableStateOf(false) }
    HorizontalDivider()
    Text("Playlist access", style = MaterialTheme.typography.titleMedium)
    Text(
        "GoneMAD remains your player. Choose its playlist folder so Shmemplaylist can " +
            "read membership. The provider test creates, verifies, and removes only a " +
            "uniquely named disposable document.",
    )
    when (val grant = state.grant) {
        PlaylistTreeGrantState.NotConfigured -> {
            Text("No playlist folder selected.")
            Button(onClick = onSelectPlaylistTree) { Text("Choose playlist folder") }
        }

        is PlaylistTreeGrantState.Invalid -> {
            Text("Playlist access needs attention: ${grant.reason}", color = MaterialTheme.colorScheme.error)
            Button(onClick = onSelectPlaylistTree) { Text("Choose folder again") }
            OutlinedButton(onClick = onClearPlaylistTree) { Text("Forget folder") }
        }

        is PlaylistTreeGrantState.Valid -> {
            Text("Persisted access: readable${if (grant.canWrite) " and writable" else ", read only"}")
            Text("Recognized playlists: ${state.playlists.size}")
            Button(onClick = onDiscoverPlaylists, enabled = !state.busy) { Text("Refresh playlists") }
            if (!confirmCapabilityTest) {
                OutlinedButton(
                    onClick = { confirmCapabilityTest = true },
                    enabled = !state.busy,
                ) { Text("Test provider capabilities…") }
            } else {
                Text("Create and clean up the disposable provider-test document now?")
                Button(
                    onClick = {
                        confirmCapabilityTest = false
                        onTestProviderCapabilities()
                    },
                ) { Text("Create disposable test document") }
                OutlinedButton(onClick = { confirmCapabilityTest = false }) { Text("Cancel") }
            }
            OutlinedButton(onClick = onClearPlaylistTree, enabled = !state.busy) {
                Text("Forget playlist folder")
            }
        }
    }
    state.capabilityReport?.let { report ->
        Text("Provider capabilities", style = MaterialTheme.typography.titleSmall)
        report.results.forEach {
            Text("${it.capability.name.lowercase()}: ${it.status.name.lowercase()}")
        }
        Text(
            if (report.cleanupSucceeded) "Disposable cleanup verified"
            else "Disposable cleanup could not be verified",
            color = if (report.cleanupSucceeded) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
        )
    }
    state.error?.let { Text("Playlist error: $it", color = MaterialTheme.colorScheme.error) }
    if (state.busy && state.scanTotal == 0) CircularProgressIndicator()
}

@Composable
private fun MembershipScreen(
    state: ResolutionUiState.Resolved,
    playlistState: PlaylistCoreState,
    onSelectPlaylistTree: () -> Unit,
    onClearPlaylistTree: () -> Unit,
    onDiscoverPlaylists: () -> Unit,
    onTestProviderCapabilities: () -> Unit,
    onScanMembership: () -> Unit,
    onEnablePhaseSixTestMode: () -> Unit,
    onProvisionCompanionTestPlaylist: () -> Unit,
    onCompanionTestAdd: () -> Unit,
    onCompanionTestRemove: () -> Unit,
    onCompanionTestUndo: () -> Unit,
) {
    Text("Track resolved", style = MaterialTheme.typography.titleMedium)
    Text(state.identity.candidate.displayName, style = MaterialTheme.typography.titleLarge)
    Text(state.identity.candidate.relativePath ?: "Path unavailable")
    Text("Evidence tier: ${state.identity.tier.name.lowercase()}")
    PlaylistSetup(
        playlistState,
        onSelectPlaylistTree,
        onClearPlaylistTree,
        onDiscoverPlaylists,
        onTestProviderCapabilities,
    )
    if (playlistState.playlists.isNotEmpty()) {
        Button(onClick = onScanMembership, enabled = !playlistState.busy) {
            Text(if (playlistState.membership == null) "Scan playlist membership" else "Rescan membership")
        }
    }
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
            MembershipList(result)
            PhaseSixTestPanel(
                state = playlistState,
                onEnable = onEnablePhaseSixTestMode,
                onProvision = onProvisionCompanionTestPlaylist,
                onAdd = onCompanionTestAdd,
                onRemove = onCompanionTestRemove,
                onUndo = onCompanionTestUndo,
            )
        }
        null -> Unit
    }
}

@Composable
private fun PhaseSixTestPanel(
    state: PlaylistCoreState,
    onEnable: () -> Unit,
    onProvision: () -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onUndo: () -> Unit,
) {
    var confirmEnable by rememberSaveable { mutableStateOf(false) }
    var pendingAction by rememberSaveable { mutableStateOf<CompanionAction?>(null) }
    HorizontalDivider()
    Text("Phase 6 transaction proof", style = MaterialTheme.typography.titleMedium)
    Text(
        "Only the app-created Shmemplaylist Companion Test.m3u can be changed. " +
            "Every operation is backed up, journaled, reread, and verified.",
    )
    if (!state.phaseSixTestMode) {
        if (!confirmEnable) {
            OutlinedButton(onClick = { confirmEnable = true }, enabled = !state.busy) {
                Text("Enable disposable test mode…")
            }
        } else {
            Text("Enable writes only to the identity-bound companion test playlist?")
            Button(onClick = {
                confirmEnable = false
                onEnable()
            }) { Text("Enable Phase 6 test mode") }
            OutlinedButton(onClick = { confirmEnable = false }) { Text("Cancel") }
        }
        return
    }
    if (!state.testPlaylistReady) {
        Button(onClick = onProvision, enabled = !state.busy) {
            Text("Create companion test playlist")
        }
        Text("An existing same-name file will be refused, not adopted.")
        return
    }
    when (val operation = state.operationState) {
        PhaseSixOperationState.Disabled -> Text("Test writes are disabled.")
        PhaseSixOperationState.Ready -> Text("Test transaction engine ready.")
        PhaseSixOperationState.Recovering -> {
            CircularProgressIndicator()
            Text("Recovering an interrupted test operation…")
        }
        is PhaseSixOperationState.Applying -> {
            CircularProgressIndicator()
            Text(
                when (operation.action) {
                    CompanionAction.ADD_ONE -> "Backing up, adding, and verifying…"
                    CompanionAction.REMOVE_ALL -> "Backing up, removing, and verifying…"
                },
            )
        }
        is PhaseSixOperationState.RecoveryRequired -> Text(
            "Recovery required: ${operation.reason}. New writes are blocked.",
            color = MaterialTheme.colorScheme.error,
        )
        is PhaseSixOperationState.Result -> {
            val text = when (val outcome = operation.outcome) {
                is OperationOutcome.Changed ->
                    "Verified change: ${outcome.occurrencesChanged} occurrence(s)."
                is OperationOutcome.Skipped -> "No change: ${outcome.reason}."
                is OperationOutcome.FailedSafe -> "Operation failed safely: ${outcome.reason}."
                is OperationOutcome.RecoveryRequired -> "Recovery required: ${outcome.reason}."
                is OperationOutcome.UndoRefused -> "Undo refused: ${outcome.reason}."
            }
            Text(
                text,
                color = if (
                    operation.outcome is OperationOutcome.RecoveryRequired
                ) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            if (operation.outcome is OperationOutcome.Changed) {
                OutlinedButton(onClick = onUndo, enabled = !state.busy) { Text("Undo") }
            }
            Text("Reopen or rescan the playlist in GoneMAD if it does not refresh immediately.")
        }
    }
    if (state.operationState is PhaseSixOperationState.Ready ||
        state.operationState is PhaseSixOperationState.Result
    ) {
        Button(onClick = { pendingAction = CompanionAction.ADD_ONE }, enabled = !state.busy) {
            Text("Test add current track…")
        }
        OutlinedButton(
            onClick = { pendingAction = CompanionAction.REMOVE_ALL },
            enabled = !state.busy,
        ) { Text("Test remove current track…") }
    }
    pendingAction?.let { action ->
        Text(
            if (action == CompanionAction.ADD_ONE) {
                "Add one occurrence to the companion test playlist?"
            } else {
                "Remove all matching occurrences from the companion test playlist?"
            },
        )
        Button(onClick = {
            pendingAction = null
            if (action == CompanionAction.ADD_ONE) onAdd() else onRemove()
        }) { Text("Run verified test transaction") }
        OutlinedButton(onClick = { pendingAction = null }) { Text("Cancel") }
    }
}

@Composable
private fun MembershipList(result: MembershipScanResult.Success) {
    var tab by rememberSaveable { mutableStateOf(1) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedUris by rememberSaveable { mutableStateOf(listOf<String>()) }
    val visible = result.playlists
        .filter { it.playlist.displayName.contains(query, ignoreCase = true) }
        .filter { tab == 0 || it.containsResolvedTrack }
        .sortedWith(
            compareByDescending<PlaylistMembership> { it.containsResolvedTrack }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.playlist.displayName },
        )
    val containing = result.playlists.count { it.containsResolvedTrack }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text(
            "$containing of ${result.playlists.size} playlists contain this track",
            style = MaterialTheme.typography.titleMedium,
        )
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
        Text("${selectedUris.size} selected (${visible.size} visible)")
        Button(
            onClick = { selectedUris = (selectedUris + visible.map { it.playlist.uri.toString() }).distinct() },
            enabled = visible.isNotEmpty(),
        ) { Text("Select all visible") }
        OutlinedButton(
            onClick = { selectedUris = emptyList() },
            enabled = selectedUris.isNotEmpty(),
        ) { Text("Clear selection") }
        Column {
            visible.forEach { membership ->
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
        }
        if (visible.isEmpty()) Text("No playlists match this view.")
        result.warnings.forEach {
            Text(
                "${it.playlist.displayName}: could not scan (${it.error.code})",
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
            Text(
                "${if (tab == 0) "Add to" else "Remove from"} " +
                    "${selectedUris.size} playlists — not yet enabled",
            )
        }
    }
}

@Composable
private fun ReadOnlyNotice() {
    Text(
        text = "Real playlists remain read-only. Phase 6 can write only its identity-bound test playlist.",
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun DiagnosticReport(
    text: String,
    onExport: (String) -> Unit,
) {
    Text(
        text = "Redacted intake evidence",
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = "diagnostic-report" },
        style = MaterialTheme.typography.bodySmall,
    )
    Button(
        onClick = { onExport(text) },
        modifier = Modifier.semantics { testTag = "export-diagnostics" },
    ) {
        Text("Export redacted diagnostics")
    }
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

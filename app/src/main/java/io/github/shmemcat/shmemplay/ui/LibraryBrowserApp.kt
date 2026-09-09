package io.github.shmemcat.shmemplay.ui

import io.github.shmemcat.shmemplay.R
import io.github.shmemcat.shmemplay.player.PlayerRepository
import io.github.shmemcat.shmemplay.player.PlayerUiState
import io.github.shmemcat.shmemplay.player.toLibraryTrack
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.shmemcat.shmemplay.domain.BatchAction
import io.github.shmemcat.shmemplay.domain.PlaylistRule
import io.github.shmemcat.shmemplay.domain.RecipeMatch
import io.github.shmemcat.shmemplay.playlists.BrowserMutationState
import io.github.shmemcat.shmemplay.playlists.LibraryBrowserState
import io.github.shmemcat.shmemplay.playlists.PlaylistDocument
import io.github.shmemcat.shmemplay.playlists.PlaylistLibraryScanner
import io.github.shmemcat.shmemplay.playlists.PlaylistSnapshot
import io.github.shmemcat.shmemplay.playlists.PlaylistTreeGrantState
import io.github.shmemcat.shmemplay.playlists.SavedPlaylistRecipe
import io.github.shmemcat.shmemplay.tracks.LibrarySearch
import io.github.shmemcat.shmemplay.tracks.LibrarySelection
import io.github.shmemcat.shmemplay.tracks.LibraryTrack
import io.github.shmemcat.shmemplay.tracks.FastScrollIndex
import io.github.shmemcat.shmemplay.tracks.FastScrollTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

data class LibraryBrowserActions(
    val requestAudioPermission: () -> Unit = {},
    val selectPlaylistFolder: () -> Unit = {},
    val refreshLibrary: () -> Unit = {},
    val refreshPlaylists: () -> Unit = {},
    val setFolderIncluded: (String, Boolean) -> Unit = { _, _ -> },
    val applyMembership: (BatchAction, List<PlaylistDocument>, List<LibraryTrack>) -> Unit = { _, _, _ -> },
    val confirmMutation: () -> Unit = {},
    val createPlaylist: (String, List<LibraryTrack>) -> Unit = { _, _ -> },
    val createNestedPlaylist: (String, io.github.shmemcat.shmemplay.domain.PlaylistRuleNode, Boolean, String?, Boolean) -> Unit = { _, _, _, _, _ -> },
    val createRulePlaylist: (String, RecipeMatch, List<PlaylistRule>) -> Unit = { _, _, _ -> },
    val rerunRecipe: (SavedPlaylistRecipe) -> Unit = {},
    val renamePlaylist: (PlaylistDocument, String) -> Unit = { _, _ -> },
    val deletePlaylist: (PlaylistDocument) -> Unit = {},
    val clearMutationMessage: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryBrowserApp(
    state: LibraryBrowserState,
    actions: LibraryBrowserActions,
    playerRepository: PlayerRepository? = null,
) {
    val playerState = playerRepository?.state?.collectAsState()?.value ?: PlayerUiState()
    var addSelectionToQueue by remember { mutableStateOf(false) }
    var sectionName by rememberSaveable { mutableStateOf(BrowserSection.NOW_PLAYING.name) }
    var detailKind by rememberSaveable { mutableStateOf<String?>(null) }
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var queueQuery by rememberSaveable { mutableStateOf("") }
    var selectedIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    var editorTrackIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    var membershipSubmitted by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showRules by rememberSaveable { mutableStateOf(false) }
    var showNewPlaylist by rememberSaveable { mutableStateOf(false) }
    var editingRecipeId by rememberSaveable { mutableStateOf<String?>(null) }
    var builderDraft by rememberSaveable { mutableStateOf<String?>(null) }
    val editingRecipe = state.localRecipes.firstOrNull { it.id == editingRecipeId }
    var playlistFilter by rememberSaveable { mutableStateOf("All") }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    var optionsExpanded by rememberSaveable { mutableStateOf(false) }
    var playlistMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var renameDocumentUri by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteDocumentUri by rememberSaveable { mutableStateOf<String?>(null) }

    if (showRules) {
        PlaylistBuilderScreen(state, actions, editingRecipe, builderDraft,
            onDraftChange = { builderDraft = it }, onDismiss = { showRules = false },
            onSaved = {
                showRules = false; builderDraft = null; editingRecipeId = null
                sectionName = BrowserSection.PLAYLISTS.name; detailKind = null; detailKey = null
            })
        return
    }

    val section = BrowserSection.valueOf(sectionName)
    val playerSection = section == BrowserSection.QUEUES || section == BrowserSection.NOW_PLAYING
    val detail = detailKind?.let { kind -> detailKey?.let { BrowserDetail(DetailKind.valueOf(kind), it) } }
    val songsScrollState = rememberLazyListState()
    val albumsScrollState = rememberLazyListState()
    val artistsScrollState = rememberLazyListState()
    val genresScrollState = rememberLazyListState()
    val playlistsScrollState = rememberLazyListState()
    val detailScrollState = remember(detail?.kind, detail?.key) { LazyListState() }
    val scrollScope = rememberCoroutineScope()
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    fun rootScrollState(destination: BrowserSection): LazyListState = when (destination) {
        BrowserSection.QUEUES, BrowserSection.NOW_PLAYING -> songsScrollState
        BrowserSection.SONGS -> songsScrollState
        BrowserSection.ALBUMS -> albumsScrollState
        BrowserSection.ARTISTS -> artistsScrollState
        BrowserSection.GENRES -> genresScrollState
        BrowserSection.PLAYLISTS -> playlistsScrollState
    }
    // Selection changes frequently. Keep library-wide filtering, indexing, and sorting out of that hot path.
    val tracks = remember(state.allTracks, state.includedFolderRoots) {
        state.allTracks.filter { it.folderRoot in state.includedFolderRoots }
    }
    val playlists = remember(state.playlistScan, state.livePlaylists) { state.browserPlaylists }
    val tracksById = remember(tracks) { tracks.associateBy(LibraryTrack::stableId) }
    val queueTracksById = remember(playerState.book.queues.map { it.entries }, selectedIds, editorTrackIds) {
        val needed = (selectedIds + editorTrackIds).toHashSet()
        if (needed.isEmpty()) emptyMap() else playerState.book.queues.asSequence().flatMap { it.entries.asSequence() }
            .filter { it.id in needed }.associate { it.id to it.toLibraryTrack() }
    }
    val editorTracks = remember(editorTrackIds, tracksById, queueTracksById) { editorTrackIds.mapNotNull { tracksById[it] ?: queueTracksById[it] } }
    val selectedTracks = remember(selectedIds, tracksById, queueTracksById) { selectedIds.mapNotNull { tracksById[it] ?: queueTracksById[it] } }
    val selectedSet = remember(selectedIds) { selectedIds.toHashSet() }
    val libraryIndex by produceState<BrowserLibraryIndex?>(null, tracks) {
        val browse = withContext(Dispatchers.Default) {
            val context = coroutineContext
            BrowserLibraryIndex.browse(tracks) { context.ensureActive() }
        }
        value = browse
        value = withContext(Dispatchers.Default) {
            val context = coroutineContext
            browse.withSearch { context.ensureActive() }
        }
    }
    val playlistIndex by produceState<BrowserPlaylistIndex?>(null, playlists) {
        value = withContext(Dispatchers.Default) {
            val context = coroutineContext
            BrowserPlaylistIndex(playlists) { context.ensureActive() }
        }
    }
    val viewedEntries = if (section == BrowserSection.QUEUES) playerState.book.viewed?.entries.orEmpty() else emptyList()
    val queueEntries = remember(viewedEntries) { viewedEntries }
    val queueIndex by produceState<BrowserQueueIndex?>(null, queueEntries) {
        value = withContext(Dispatchers.Default) {
            val context = coroutineContext
            BrowserQueueIndex(queueEntries) { context.ensureActive() }
        }
    }
    val sourceQuery = when (section) { BrowserSection.QUEUES -> queueQuery; BrowserSection.NOW_PLAYING -> ""; else -> query }
    val request = BrowserSearchRequest(
        libraryIndex?.takeIf { !playerSection && it.tracks === tracks },
        playlistIndex?.takeIf { (section == BrowserSection.PLAYLISTS || detail?.kind == DetailKind.PLAYLIST) && it.snapshots === playlists },
        queueIndex?.takeIf { section == BrowserSection.QUEUES && it.entries === queueEntries }, section, detail, sourceQuery, playlistFilter,
    )
    val searchResult by produceState<Pair<BrowserSearchRequest, BrowserProjection>?>(null, request) {
        value = withContext(Dispatchers.Default) {
            val context = coroutineContext
            request to searchBrowser(request) { context.ensureActive() }
        }
    }
    val completedProjection = searchResult?.takeIf { it.first == request }?.second
    // Cached songs do not depend on either background index stage.
    val projection = completedProjection ?: projectionWhileIndexing(section, detail, sourceQuery, tracks)
    val currentTracks = projection.tracks
    val currentUnique = currentTracks
    val currentTrackIds = projection.trackIds
    val currentSelected = remember(currentTrackIds, selectedSet) { currentTrackIds.count(selectedSet::contains) }
    val headerStats = projection.stats
    val openPlaylist = detail?.takeIf { it.kind == DetailKind.PLAYLIST }?.let { current ->
        playlists.firstOrNull { it.document.uri.toString() == current.key }
    }

    LaunchedEffect(tracks) {
        val available = tracks.mapTo(hashSetOf(), LibraryTrack::stableId)
        selectedIds = selectedIds.filter { it in available || it in queueTracksById }
    }
    LaunchedEffect(query) {
        songsScrollState.scrollToItem(0)
        albumsScrollState.scrollToItem(0)
        artistsScrollState.scrollToItem(0)
        genresScrollState.scrollToItem(0)
        playlistsScrollState.scrollToItem(0)
        detailScrollState.scrollToItem(0)
    }
    LaunchedEffect(selectedIds) {
        if (selectedIds.isEmpty()) {
            advancedExpanded = false
            optionsExpanded = false
        }
    }

    BackHandler(enabled = editorTrackIds.isNotEmpty() || detail != null || selectedIds.isNotEmpty()) {
        when {
            editorTrackIds.isNotEmpty() -> if (!membershipSubmitted) editorTrackIds = emptyList()
            detail != null -> {
                detailKind = null
                detailKey = null
            }
            else -> selectedIds = emptyList()
        }
    }

    val searchQueueName = sourceQuery.trim().takeIf(String::isNotEmpty)?.let { "Search - $it" }
    val songPlayback = playerRepository?.let { repository -> SongPlaybackActions(repository, queueName = searchQueueName ?: "New queue", navigate = { kind, key ->
        sectionName = when(kind) { "Album" -> BrowserSection.ALBUMS.name; "Artist" -> BrowserSection.ARTISTS.name; else -> BrowserSection.GENRES.name }
        detailKind = when(kind) { "Album" -> DetailKind.ALBUM.name; "Artist" -> DetailKind.ARTIST.name; else -> DetailKind.GENRE.name }
        detailKey = key
    }) { track ->
        repository.create(searchQueueName ?: detailTitle(detail, state) ?: section.label, currentTracks, track)
        sectionName = BrowserSection.NOW_PLAYING.name
        detailKind = null
        detailKey = null
    } }
    CompositionLocalProvider(LocalSongPlayback provides songPlayback, LocalLibraryRefresh provides actions.refreshLibrary) {
    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)).imePadding(),
        topBar = {
            BrowserTopBar(
                title = if (playerSection) section.label else detailTitle(detail, state) ?: section.label,
                stats = if (playerSection) "" else headerStats,
                canGoBack = detail != null,
                onBack = {
                    detailKind = null
                    detailKey = null
                },
                onSettings = { showSettings = true },
                playlist = openPlaylist,
                playlistMenuExpanded = playlistMenuExpanded,
                onPlaylistMenuExpanded = { playlistMenuExpanded = it },
                onRules = {
                    val id = openPlaylist?.takeIf { it.live }?.document?.uri?.schemeSpecificPart
                    if (editingRecipeId != id) builderDraft = null
                    editingRecipeId = id; actions.clearMutationMessage(); showRules = true
                },
                showRules = section == BrowserSection.PLAYLISTS && detail == null,
                onNewPlaylist = { showNewPlaylist = true },
                canShufflePlaylist = playerRepository != null && openPlaylist?.sourceError == null && currentTracks.isNotEmpty() && openPlaylist != null,
                onShufflePlaylist = {
                    openPlaylist?.let { snapshot ->
                        playerRepository?.shuffleAndPlay(searchQueueName ?: if (snapshot.live) snapshot.document.displayName else snapshot.document.displayName.substringBeforeLast('.'), currentTracks)
                        sectionName = BrowserSection.NOW_PLAYING.name
                        detailKind = null
                        detailKey = null
                    }
                },
                onRename = { openPlaylist?.let { renameDocumentUri = it.document.uri.toString() } },
                onDelete = { openPlaylist?.let { deleteDocumentUri = it.document.uri.toString() } },
                onSelectAll = {
                    selectedIds = LibrarySelection.selectAll(
                        selectedIds,
                        currentTrackIds,
                    )
                },
            )
        },
        bottomBar = {
            Column {
                if (selectedIds.isNotEmpty() && section != BrowserSection.NOW_PLAYING) {
                    SelectionBar(
                        selectedTracks = selectedTracks,
                        currentSelected = currentSelected,
                        optionsExpanded = optionsExpanded,
                        advancedExpanded = advancedExpanded,
                        rangeAvailable = currentSelected >= 2,
                        onOptionsExpanded = { optionsExpanded = it },
                        onQueue = { optionsExpanded = false; addSelectionToQueue = true },
                        onPlayNext = { optionsExpanded = false; playerState.book.activeId?.let { playerRepository?.insert(it, selectedTracks, true) } },
                        onAdvancedExpanded = { advancedExpanded = it },
                        onPlaylist = {
                            optionsExpanded = false
                            editorTrackIds = selectedIds
                        },
                        onSelectAll = {
                            selectedIds = LibrarySelection.selectAll(
                                selectedIds,
                                currentTrackIds,
                            )
                        },
                        onDeselectAll = {
                            selectedIds = LibrarySelection.deselectAll(
                                selectedIds,
                                currentTrackIds,
                            )
                        },
                        onSelectBetween = {
                            selectedIds = LibrarySelection.selectBetween(
                                selectedIds,
                                currentTrackIds,
                            )
                        },
                        onInvert = {
                            selectedIds = LibrarySelection.invert(
                                selectedIds,
                                currentTrackIds,
                            )
                        },
                        onCancel = { selectedIds = emptyList() },
                    )
                }
                if (section == BrowserSection.QUEUES) SearchBar(queueQuery, { queueQuery = it }, { queueQuery = "" })
                else if (!playerSection) SearchBar(query = query, onQueryChange = { query = it }, onClear = { query = "" })
                if (keyboardVisible) {
                    Spacer(Modifier.height(6.dp))
                } else {
                    Surface(tonalElevation = 3.dp) {
                        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(56.dp).padding(horizontal = 4.dp).selectableGroup()) {
                            BrowserSection.entries.forEach { destination ->
                                Box(
                                    Modifier.weight(1f).fillMaxHeight()
                                        .background(if (section == destination) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                        .selectable(
                                        selected = section == destination, role = Role.Tab,
                                        onClick = {
                                            if (section != destination) scrollScope.launch { rootScrollState(destination).scrollToItem(0) }
                                            sectionName = destination.name
                                            detailKind = null
                                            detailKey = null
                                        },
                                    ), contentAlignment = Alignment.Center,
                                ) {
                                    PlayerIcon(sectionIcon(destination), destination.label)
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                playerSection && playerRepository != null -> {
                    if (!playerState.ready || !playerState.connected) Loading(playerState.error ?: "Connecting player…")
                    else if (section == BrowserSection.NOW_PLAYING) NowPlayingScreen(playerState, playerRepository) { editorTrackIds = listOf(it.stableId) }
                    else QueuesScreen(playerState, playerRepository, { editorTrackIds = listOf(it.stableId) }, actions.createPlaylist,
                        queueQuery, projection.queueRows, selectedSet, { id -> selectedIds = if (id in selectedSet) selectedIds - id else selectedIds + id },
                        { id -> if (id !in selectedSet) selectedIds = selectedIds + id })
                }
                state.permissionRequired -> PermissionRequired(actions.requestAudioPermission)
                state.loadingLibrary && state.allTracks.isEmpty() -> Loading("Scanning music…")
                state.error != null && state.allTracks.isEmpty() -> EmptyMessage(state.error)
                else -> BrowserContent(
                    state = state,
                    playlistFilter = playlistFilter,
                    onPlaylistFilter = { playlistFilter = it },
                    tracks = tracks,
                    currentTracks = currentUnique,
                    projection = projection,
                    rootScrollState = rootScrollState(section),
                    detailScrollState = detailScrollState,
                    section = section,
                    detail = detail,
                    query = query,
                    selected = selectedSet,
                    selectionMode = selectedIds.isNotEmpty(),
                    onOpenDetail = { next ->
                        detailKind = next.kind.name
                        detailKey = next.key
                    },
                    onLongPress = { track ->
                        if (track.stableId !in selectedSet) selectedIds = selectedIds + track.stableId
                    },
                    onToggle = { track ->
                        selectedIds = if (track.stableId in selectedSet) {
                            selectedIds - track.stableId
                        } else {
                            selectedIds + track.stableId
                        }
                    },
                    onPlaylist = { track -> editorTrackIds = listOf(track.stableId) },
                )
            }
            if ((state.loadingLibrary || state.mutation is BrowserMutationState.Working) && state.allTracks.isNotEmpty() && !membershipSubmitted) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }

    LaunchedEffect(state.mutation, membershipSubmitted) {
        if (membershipSubmitted) when (state.mutation) {
            is BrowserMutationState.Result -> {
                actions.clearMutationMessage()
                editorTrackIds = emptyList()
                // Clearing the message is asynchronous; releasing suppression here would flash the success dialog.
            }
            is BrowserMutationState.Error, BrowserMutationState.Idle -> membershipSubmitted = false
            else -> Unit
        }
    }
    if (editorTrackIds.isNotEmpty()) {
        Dialog(onDismissRequest = { if (!membershipSubmitted) editorTrackIds = emptyList() }) {
            Surface(Modifier.fillMaxWidth().fillMaxHeight(.86f), shape = RoundedCornerShape(20.dp)) {
                MembershipEditor(state, editorTracks, actions, membershipSubmitted, { membershipSubmitted = true }) { if (!membershipSubmitted) editorTrackIds = emptyList() }
            }
        }
    }
    if (addSelectionToQueue && playerRepository != null) AddToQueueDialog(selectedTracks, playerRepository) { addSelectionToQueue = false }
    if (playerState.error != null && playerState.ready) AlertDialog(onDismissRequest = { playerRepository?.clearError() },
        title = { Text("Player") }, text = { Text(playerState.error) }, confirmButton = { TextButton(onClick = { playerRepository?.clearError() }) { Text("OK") } })
    if (showSettings) {
        SettingsSheet(state, actions, onDismiss = { showSettings = false })
    }
    renameDocumentUri?.let { uri ->
        val document = state.browserPlaylists.firstOrNull { it.document.uri.toString() == uri }?.document
        if (document != null) NameDialog(
            title = "Rename playlist",
            initialName = document.displayName.substringBeforeLast('.'),
            confirmLabel = "Rename",
            supportingText = null,
            onDismiss = { renameDocumentUri = null },
            onConfirm = {
                actions.renamePlaylist(document, it)
                renameDocumentUri = null
                detailKind = null
                detailKey = null
            },
        ) else renameDocumentUri = null
    }
    deleteDocumentUri?.let { uri ->
        val document = state.browserPlaylists.firstOrNull { it.document.uri.toString() == uri }?.document
        if (document != null) AlertDialog(
            onDismissRequest = { deleteDocumentUri = null },
            title = { Text("Delete ${document.displayName}?") },
            text = { Text(if(document.uri.scheme == "shmemplay-live") "This removes the local rule definition. Audio and M3U files stay on your phone." else "This deletes the playlist file. The audio files stay on your phone.") },
            confirmButton = {
                TextButton(onClick = {
                    actions.deletePlaylist(document)
                    deleteDocumentUri = null
                    detailKind = null
                    detailKey = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteDocumentUri = null }) { Text("Cancel") } },
        ) else deleteDocumentUri = null
    }
    if (showNewPlaylist) NameDialog("New playlist", "", "Create", "Start with an empty playlist.",
        { showNewPlaylist = false }) { name ->
        actions.createPlaylist(name, emptyList())
        showNewPlaylist = false
    }
    MutationDialogs(state, actions, suppressSuccess = membershipSubmitted)
    }
}

@Composable
private fun BrowserTopBar(
    title: String,
    stats: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    playlist: PlaylistSnapshot?,
    playlistMenuExpanded: Boolean,
    onPlaylistMenuExpanded: (Boolean) -> Unit,
    showRules: Boolean,
    onRules: () -> Unit,
    onNewPlaylist: () -> Unit,
    canShufflePlaylist: Boolean,
    onShufflePlaylist: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().height(68.dp).padding(start = if (canGoBack) 0.dp else 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (canGoBack) {
                    BackChevronButton(onClick = onBack)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        modifier = Modifier.semantics { testTag = "app-title" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (stats.isNotBlank()) Text(
                        stats,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showRules) {
                    var creationMenu by remember { mutableStateOf(false) }
                    Box {
                        PlayerButton(R.drawable.ic_plus, "Create playlist") { creationMenu = true }
                        DropdownMenu(creationMenu, { creationMenu = false }) {
                            DropdownMenuItem(text = { Text("New playlist") }, onClick = { creationMenu = false; onNewPlaylist() })
                            DropdownMenuItem(text = { Text("New playlist from rules") }, onClick = { creationMenu = false; onRules() })
                        }
                    }
                }
                if (playlist != null) {
                    Box {
                        TextButton(onClick = { onPlaylistMenuExpanded(true) }) { PlayerIcon(R.drawable.ic_ellipsis, "Playlist options") }
                        DropdownMenu(
                            expanded = playlistMenuExpanded,
                            onDismissRequest = { onPlaylistMenuExpanded(false) },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Shuffle & Play") },
                                leadingIcon = { PlayerIcon(R.drawable.ic_shuffle, null) },
                                enabled = canShufflePlaylist,
                                onClick = { onPlaylistMenuExpanded(false); onShufflePlaylist() },
                            )
                            HorizontalDivider()
                            if (playlist.live) DropdownMenuItem(text = { Text("Edit rules / repair sources") }, onClick = { onPlaylistMenuExpanded(false); onRules() })
                            DropdownMenuItem(text = { Text("Rename playlist") }, onClick = {
                                onPlaylistMenuExpanded(false); onRename()
                            })
                            DropdownMenuItem(text = { Text("Delete playlist") }, onClick = {
                                onPlaylistMenuExpanded(false); onDelete()
                            })
                            DropdownMenuItem(text = { Text("Select all") }, onClick = {
                                onPlaylistMenuExpanded(false); onSelectAll()
                            })
                        }
                    }
                }
                TextButton(
                    onClick = onSettings,
                    modifier = Modifier.semantics { contentDescription = "Settings" },
                ) { PlayerIcon(R.drawable.ic_settings, "Settings") }
            }
        }
    }
}

@Composable
private fun BrowserContent(
    state: LibraryBrowserState,
    playlistFilter: String,
    onPlaylistFilter: (String) -> Unit,
    tracks: List<LibraryTrack>,
    currentTracks: List<LibraryTrack>,
    projection: BrowserProjection,
    rootScrollState: LazyListState,
    detailScrollState: LazyListState,
    section: BrowserSection,
    detail: BrowserDetail?,
    query: String,
    selected: Set<String>,
    selectionMode: Boolean,
    onOpenDetail: (BrowserDetail) -> Unit,
    onLongPress: (LibraryTrack) -> Unit,
    onToggle: (LibraryTrack) -> Unit,
    onPlaylist: (LibraryTrack) -> Unit,
) {
    if (projection.loading) {
        Loading(if (query.isBlank()) "Preparing library…" else "Preparing search…")
        return
    }
    if (detail != null) {
        if (detail.kind == DetailKind.PLAYLIST) {
            val snapshot = state.browserPlaylists.firstOrNull { it.document.uri.toString() == detail.key }
            if (snapshot == null) EmptyMessage("Playlist no longer exists.") else PlaylistEntries(
                snapshot = snapshot,
                entries = projection.entries,
                listState = detailScrollState,
                selected = selected,
                selectionMode = selectionMode,
                onLongPress = onLongPress,
                onToggle = onToggle,
                onPlaylist = onPlaylist,
            )
        } else {
            TrackList(
                tracks = currentTracks,
                listState = detailScrollState,
                selected = selected,
                selectionMode = selectionMode,
                onLongPress = onLongPress,
                onToggle = onToggle,
                onPlaylist = onPlaylist,
            )
        }
        return
    }
    when (section) {
        BrowserSection.QUEUES, BrowserSection.NOW_PLAYING -> Unit
        BrowserSection.SONGS -> TrackList(
            tracks = currentTracks,
            listState = rootScrollState,
            selected = selected,
            selectionMode = selectionMode,
            onLongPress = onLongPress,
            onToggle = onToggle,
            onPlaylist = onPlaylist,
        )
        BrowserSection.ALBUMS -> GroupList(projection.groups, DetailKind.ALBUM, rootScrollState, onOpenDetail)
        BrowserSection.ARTISTS -> GroupList(projection.groups, DetailKind.ARTIST, rootScrollState, onOpenDetail)
        BrowserSection.GENRES -> GroupList(projection.groups, DetailKind.GENRE, rootScrollState, onOpenDetail)
        BrowserSection.PLAYLISTS -> PlaylistList(state, projection.playlists, rootScrollState, onOpenDetail, playlistFilter, onPlaylistFilter)
    }
}

@Composable
private fun TrackList(
    tracks: List<LibraryTrack>,
    listState: LazyListState,
    selected: Set<String>,
    selectionMode: Boolean,
    onLongPress: (LibraryTrack) -> Unit,
    onToggle: (LibraryTrack) -> Unit,
    onPlaylist: (LibraryTrack) -> Unit,
) {
    if (tracks.isEmpty()) {
        EmptyMessage("No songs match this list.")
        return
    }
    val labels = remember(tracks) { tracks.map(LibraryTrack::title) }
    FastScrollableLazyColumn(
        listState = listState,
        itemCount = tracks.size,
        bucketLabels = labels,
        liveDrag = false,
    ) {
        items(tracks, key = LibraryTrack::stableId) { track ->
            SongRow(track, track.stableId in selected, selectionMode, onLongPress, onToggle, onPlaylist)
        }
    }
}

@Composable
internal fun FastScrollableLazyColumn(
    listState: LazyListState,
    itemCount: Int,
    bucketLabels: List<String>?,
    liveDrag: Boolean,
    precomputedTargets: List<FastScrollTarget>? = null,
    content: LazyListScope.() -> Unit,
) {
    val computedTargets by produceState<Pair<List<String>?, List<FastScrollTarget>>?>(null, bucketLabels, precomputedTargets) {
        value = bucketLabels to (precomputedTargets ?: withContext(Dispatchers.Default) { bucketLabels?.let(FastScrollIndex::targets).orEmpty() })
    }
    val targets = precomputedTargets ?: computedTargets?.takeIf { it.first == bucketLabels }?.second.orEmpty()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = 6.dp, end = 20.dp),
            content = content,
        )
        FastScrollbar(
            listState = listState,
            itemCount = itemCount,
            targets = targets,
            liveDrag = liveDrag,
        )
    }
}

@Composable
private fun FastScrollbar(
    listState: LazyListState,
    itemCount: Int,
    targets: List<FastScrollTarget>,
    liveDrag: Boolean,
) {
    val density = LocalDensity.current
    val scrollScope = rememberCoroutineScope()
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var selectedTarget by remember { mutableStateOf<FastScrollTarget?>(null) }
    var containerHeightPx by remember { mutableIntStateOf(0) }
    var lastLiveItem by remember { mutableIntStateOf(-1) }

    val verticalPaddingPx = with(density) { 4.dp.toPx() }
    val trackHeightPx = (containerHeightPx - verticalPaddingPx * 2f).coerceAtLeast(0f)
    val visibleCount = listState.layoutInfo.visibleItemsInfo.size
    val isScrollable = listState.canScrollBackward || listState.canScrollForward
    val passiveFraction = when {
        !listState.canScrollBackward -> 0f
        !listState.canScrollForward -> 1f
        else -> {
            val maximumFirstItem = (itemCount - visibleCount).coerceAtLeast(1)
            (listState.firstVisibleItemIndex.toFloat() / maximumFirstItem).coerceIn(0f, 1f)
        }
    }
    val visibleFraction = if (itemCount > 0) visibleCount.toFloat() / itemCount else 1f
    val minimumThumbHeightPx = with(density) { 36.dp.toPx() }
    val thumbHeightPx = if (trackHeightPx == 0f) {
        0f
    } else {
        (trackHeightPx * visibleFraction).coerceAtLeast(minimumThumbHeightPx).coerceAtMost(trackHeightPx)
    }
    val displayedFraction = if (dragging) dragFraction else passiveFraction
    val thumbTopPx = displayedFraction * (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
    val idleWidth = 3.dp
    val activeWidth = 10.dp
    val thumbWidth = if (dragging) activeWidth else idleWidth
    val centerInset = 8.dp
    val bubbleSize = 58.dp
    val bubbleSizePx = with(density) { bubbleSize.toPx() }
    val bubbleTopPx = (
        verticalPaddingPx + dragFraction * trackHeightPx - bubbleSizePx / 2f
    ).coerceIn(0f, (containerHeightPx - bubbleSizePx).coerceAtLeast(0f))

    fun updateDrag(y: Float) {
        if (trackHeightPx <= 0f) return
        val fraction = (y / trackHeightPx).coerceIn(0f, 1f)
        dragFraction = fraction
        if (liveDrag) {
            val item = (fraction * (itemCount - 1).coerceAtLeast(0)).roundToInt()
            if (item != lastLiveItem) {
                lastLiveItem = item
                scrollJob?.cancel()
                scrollJob = scrollScope.launch { listState.scrollToItem(item) }
            }
        } else if (targets.isNotEmpty()) {
            val targetIndex = (fraction * targets.size).toInt().coerceAtMost(targets.lastIndex)
            selectedTarget = targets[targetIndex]
        }
    }

    Box(
        Modifier.fillMaxSize().onSizeChanged { containerHeightPx = it.height },
    ) {
        if (isScrollable && trackHeightPx > 0f) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(28.dp)
                    .padding(vertical = 4.dp)
                    .pointerInput(liveDrag, itemCount, targets) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            dragging = true
                            lastLiveItem = -1
                            updateDrag(down.position.y)
                            var released = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        released = true
                                        break
                                    }
                                    change.consume()
                                    updateDrag(change.position.y)
                                }
                            } finally {
                                if (released && !liveDrag) {
                                    selectedTarget?.let { target ->
                                        scrollJob?.cancel()
                                        scrollJob = scrollScope.launch { listState.scrollToItem(target.itemIndex) }
                                    }
                                }
                                dragging = false
                                selectedTarget = null
                                lastLiveItem = -1
                            }
                        }
                    },
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = -(centerInset - idleWidth / 2))
                        .fillMaxHeight()
                        .width(idleWidth)
                        .clip(RoundedCornerShape(99.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .28f)),
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = -(centerInset - thumbWidth / 2))
                        .offset { IntOffset(0, thumbTopPx.roundToInt()) }
                        .width(thumbWidth)
                        .height(with(density) { thumbHeightPx.toDp() })
                        .clip(RoundedCornerShape(99.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            if (dragging && !liveDrag && selectedTarget != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = -38.dp)
                        .offset { IntOffset(0, bubbleTopPx.roundToInt()) }
                        .size(bubbleSize),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 8.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            selectedTarget?.label.orEmpty(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistEntries(
    snapshot: PlaylistSnapshot,
    entries: List<io.github.shmemcat.shmemplay.playlists.PlaylistEntry>,
    listState: LazyListState,
    selected: Set<String>,
    selectionMode: Boolean,
    onLongPress: (LibraryTrack) -> Unit,
    onToggle: (LibraryTrack) -> Unit,
    onPlaylist: (LibraryTrack) -> Unit,
) {
    if (snapshot.sourceError != null) { EmptyMessage(snapshot.sourceError); return }
    if (entries.isEmpty()) {
        EmptyMessage("No playlist entries match this search.")
        return
    }
    FastScrollableLazyColumn(
        listState = listState,
        itemCount = entries.size,
        bucketLabels = null,
        liveDrag = true,
    ) {
        itemsIndexed(entries, key = { index, entry -> "${entry.normalizedPath}-$index" }) { _, entry ->
            val track = entry.track
            if (track == null) {
                Row(
                    Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FallbackArtwork("?", false)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.normalizedPath.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("File not found in music library", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                SongRow(track, track.stableId in selected, selectionMode, onLongPress, onToggle, onPlaylist)
            }
        }
    }
}

@Composable
private fun SongRow(
    track: LibraryTrack,
    selected: Boolean,
    selectionMode: Boolean,
    onLongPress: (LibraryTrack) -> Unit,
    onToggle: (LibraryTrack) -> Unit,
    onPlaylist: (LibraryTrack) -> Unit,
) {
    var menu by rememberSaveable(track.stableId) { mutableStateOf(false) }
    val playback = LocalSongPlayback.current
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(background)
            .combinedClickable(
                onClick = { if (selectionMode) onToggle(track) else playback?.play?.invoke(track) },
                onLongClick = { onLongPress(track) },
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArtwork(track, selected)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${track.artist} · ${track.album}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(formatDuration(track.durationMs), style = MaterialTheme.typography.bodySmall)
        PlayerButton(R.drawable.ic_ellipsis, "Options for " + track.title) { menu = true }
    }
    if (menu) SongOptionsDialog(track, playback?.repository, null, onPlaylist) { menu = false }
}

@Composable
internal fun AlbumArtwork(track: LibraryTrack, selected: Boolean, modifier: Modifier = Modifier.size(44.dp)) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, track.stableId) {
        value = withContext(Dispatchers.IO) { ArtworkLoader.load(context, track) }
    }
    Box(
        modifier.clip(RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = checkNotNull(bitmap).asImageBitmap(),
                contentDescription = "Album artwork for ${track.album}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) { PlayerIcon(R.drawable.ic_disc_3, null, Modifier.fillMaxSize(.55f)) }
        }
        if (selected) {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = .65f)),
                contentAlignment = Alignment.Center,
            ) { Text("✓", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun FallbackArtwork(label: String, selected: Boolean) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) { Text(if (selected) "✓" else label, fontWeight = FontWeight.Bold, fontSize = 22.sp) }
}

@Composable
private fun CategoryArtwork(kind: DetailKind) {
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        PlayerIcon(when(kind) {
            DetailKind.ARTIST -> R.drawable.ic_user_round
            DetailKind.GENRE -> R.drawable.ic_tags
            DetailKind.PLAYLIST -> R.drawable.ic_list_video
            DetailKind.ALBUM -> R.drawable.ic_disc_3
        }, null, Modifier.size(28.dp))
    }
}

@Composable
private fun GroupList(
    groups: List<Pair<String, List<LibraryTrack>>>,
    kind: DetailKind,
    listState: LazyListState,
    onOpen: (BrowserDetail) -> Unit,
) {
    var albumGrid by rememberSaveable { mutableStateOf(true) }
    if (groups.isEmpty()) {
        EmptyMessage("No groups match this search.")
        return
    }
    if (kind == DetailKind.ALBUM) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PlayerButton(if (albumGrid) R.drawable.ic_list else R.drawable.ic_layout_grid, if (albumGrid) "Show albums as list" else "Show albums in grid") { albumGrid = !albumGrid }
            }
            val rows = remember(groups, albumGrid) { groups.chunked(if (albumGrid) 3 else 1) }
            val labels = remember(rows) { rows.map { it.first().first } }
            FastScrollableLazyColumn(listState, rows.size, labels, false) {
                items(rows, key = { it.first().first }) { albums ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        albums.forEach { (name, songs) ->
                            if (albumGrid) Column(Modifier.weight(1f).combinedClickable(onClick = { onOpen(BrowserDetail(kind, name)) }, onLongClick = {})) {
                                AlbumArtwork(songs.first(), false, Modifier.fillMaxWidth().aspectRatio(1f))
                                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                Text(songs.size.toString() + " songs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else Row(Modifier.fillMaxWidth().height(58.dp).combinedClickable(onClick = { onOpen(BrowserDetail(kind, name)) }, onLongClick = {}), verticalAlignment = Alignment.CenterVertically) {
                                AlbumArtwork(songs.first(), false)
                                Column(Modifier.padding(start = 10.dp)) { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(songs.size.toString() + " songs", style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                        if (albumGrid) repeat(3 - albums.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        return
    }
    val labels = remember(groups) { groups.map { it.first } }
    FastScrollableLazyColumn(
        listState = listState,
        itemCount = groups.size,
        bucketLabels = labels,
        liveDrag = false,
    ) {
        items(groups, key = { "${kind.name}-${it.first}" }) { (name, songs) ->
            Row(
                Modifier.fillMaxWidth().height(58.dp)
                    .combinedClickable(onClick = { onOpen(BrowserDetail(kind, name)) }, onLongClick = {}),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(10.dp))
                if (kind == DetailKind.ALBUM) AlbumArtwork(songs.first(), false)
                else CategoryArtwork(kind)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${songs.size} song${if (songs.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall)
                }
                PlayerIcon(R.drawable.ic_chevron_down, null, Modifier.padding(end = 16.dp).size(22.dp).rotate(-90f))
            }
        }
    }
}

@Composable
private fun PlaylistList(
    state: LibraryBrowserState,
    playlists: List<PlaylistSearchRow>,
    listState: LazyListState,
    onOpen: (BrowserDetail) -> Unit,
    filter: String,
    onFilter: (String) -> Unit,
) {
    if (state.grant is PlaylistTreeGrantState.NotConfigured) {
        EmptyMessage("Choose your playlist folder in Settings.")
        return
    }
    Column(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("All","Files","Live").forEach { label -> androidx.compose.material3.FilterChip(filter == label, { onFilter(label) }, label = { Text(label) }) }
    }
    if (playlists.isEmpty()) Text("No playlists match this view.",Modifier.padding(16.dp))
    FastScrollableLazyColumn(
        listState = listState,
        itemCount = playlists.size,
        bucketLabels = null,
        liveDrag = true,
    ) {
        items(playlists, key = { it.snapshot.document.uri.toString() }) { row ->
            val snapshot = row.snapshot
            val songs = row.songs
            Row(
                Modifier.fillMaxWidth().height(58.dp)
                    .combinedClickable(
                        onClick = { onOpen(BrowserDetail(DetailKind.PLAYLIST, snapshot.document.uri.toString())) },
                        onLongClick = {},
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(10.dp))
                CategoryArtwork(DetailKind.PLAYLIST)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(snapshot.document.displayName.substringBeforeLast('.') + if (snapshot.live) " · Live" else "", fontWeight = FontWeight.SemiBold)
                    Text(
                        "${songs.size} song${if (songs.size == 1) "" else "s"}" +
                            if (snapshot.sourceError != null) " · source unavailable" else if (row.missingFiles) " · missing files" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                PlayerIcon(R.drawable.ic_chevron_down, null, Modifier.padding(end = 16.dp).size(22.dp).rotate(-90f))
            }
        }
    }
    }
}

@Composable
private fun SelectionBar(
    selectedTracks: List<LibraryTrack>,
    currentSelected: Int,
    optionsExpanded: Boolean,
    advancedExpanded: Boolean,
    rangeAvailable: Boolean,
    onOptionsExpanded: (Boolean) -> Unit,
    onQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onAdvancedExpanded: (Boolean) -> Unit,
    onPlaylist: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onSelectBetween: () -> Unit,
    onInvert: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 6.dp,
    ) {
        Column {
            Text(
                "${selectedTracks.size} song${if (selectedTracks.size == 1) "" else "s"} selected · " +
                    "${formatDuration(selectedTracks.sumOf(LibraryTrack::durationMs))} · $currentSelected in this list",
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Box {
                    TextButton(onClick = { onOptionsExpanded(true) }) {
                        SelectionActionLabel("⋮", "Options")
                    }
                    DropdownMenu(expanded = optionsExpanded, onDismissRequest = { onOptionsExpanded(false) }) {
                        DropdownMenuItem(text = { Text("Add/remove from playlists") }, onClick = onPlaylist)
                        DropdownMenuItem(text = { Text("Add to a queue") }, onClick = onQueue)
                        DropdownMenuItem(text = { Text("Play after current song") }, onClick = onPlayNext)
                    }
                }
                Box {
                    TextButton(onClick = { onAdvancedExpanded(true) }) {
                        SelectionActionLabel("▣", "Advanced select")
                    }
                    DropdownMenu(expanded = advancedExpanded, onDismissRequest = { onAdvancedExpanded(false) }) {
                        DropdownMenuItem(text = { Text("Select all from this list") }, onClick = {
                            onAdvancedExpanded(false); onSelectAll()
                        })
                        DropdownMenuItem(text = { Text("Deselect all from this list") }, onClick = {
                            onAdvancedExpanded(false); onDeselectAll()
                        })
                        DropdownMenuItem(
                            text = { Text("Select songs in-between first and last selected songs") },
                            enabled = rangeAvailable,
                            onClick = { onAdvancedExpanded(false); onSelectBetween() },
                        )
                        DropdownMenuItem(text = { Text("Invert selection") }, onClick = {
                            onAdvancedExpanded(false); onInvert()
                        })
                    }
                }
                TextButton(onClick = onCancel) { SelectionActionLabel("⊗", "Cancel") }
            }
        }
    }
}

@Composable
private fun SelectionActionLabel(glyph: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PlayerIcon(when(glyph) { "⋮" -> R.drawable.ic_ellipsis; "⊗" -> R.drawable.ic_x; else -> R.drawable.ic_list }, null)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun SearchBar(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        placeholder = { Text("Search in this list…") },
        leadingIcon = { PlayerIcon(R.drawable.ic_search, "Search") },
        trailingIcon = { if (query.isNotEmpty()) PlayerButton(R.drawable.ic_x, "Clear search", action = onClear) },
        singleLine = true,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    state: LibraryBrowserState,
    actions: LibraryBrowserActions,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(.92f).fillMaxHeight(.86f),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 8.dp,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 24.dp),
            ) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        TextButton(onClick = onDismiss) { PlayerIcon(R.drawable.ic_x, "Close") }
                    }
                    Text("Music folders", style = MaterialTheme.typography.titleMedium)
                }
                items(state.availableFolderRoots, key = { it }) { folder ->
                    Row(
                        Modifier.fillMaxWidth().combinedClickable(
                            onClick = { actions.setFolderIncluded(folder, folder !in state.includedFolderRoots) },
                            onLongClick = {},
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = folder in state.includedFolderRoots,
                            onCheckedChange = { actions.setFolderIncluded(folder, it) },
                        )
                        Text("$folder/")
                    }
                }
                item {
                    OutlinedButton(onClick = actions.refreshLibrary, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text("Rescan library & playlists")
                    }
                    Text("Songs are saved on this device for faster startup. Rescan after syncing music with your PC. Playlists are read when the app opens.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(18.dp))
                    Text("Playlist folder", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (val grant = state.grant) {
                            PlaylistTreeGrantState.NotConfigured -> "Not selected"
                            is PlaylistTreeGrantState.Valid -> grant.treeUri.lastPathSegment ?: grant.treeUri.toString()
                            is PlaylistTreeGrantState.Invalid -> "Access lost: ${grant.reason}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = actions.selectPlaylistFolder, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.grant is PlaylistTreeGrantState.NotConfigured) "Choose playlist folder" else "Change playlist folder")
                    }
                    OutlinedButton(onClick = actions.refreshPlaylists, modifier = Modifier.fillMaxWidth()) {
                        Text("Rescan playlists")
                    }
                    Spacer(Modifier.height(18.dp))
                    HeadsetSettingsContent()
                    AudioRecoveryControl()
                }
            }
        }
    }
}

@Composable
private fun MembershipEditor(
    state: LibraryBrowserState,
    tracks: List<LibraryTrack>,
    actions: LibraryBrowserActions,
    submitted: Boolean,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    val writing = submitted && state.mutation is BrowserMutationState.Working
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedUris by rememberSaveable { mutableStateOf(listOf<String>()) }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    val trackIds = tracks.mapTo(linkedSetOf(), LibraryTrack::stableId)
    val memberships = PlaylistLibraryScanner.memberships(state.playlistScan.playlists, trackIds)
        .filter { query.isBlank() || LibrarySearch.matches(it.snapshot.document.displayName, query) }
        .filter { tab == 0 || it.containsAny }
        .sortedWith(
            compareByDescending<io.github.shmemcat.shmemplay.playlists.SelectionMembership> { it.containsAll }
                .thenByDescending { it.containsAny }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.snapshot.document.displayName },
        )
    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().height(60.dp).padding(end = 34.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BackChevronButton(onClick = onBack, enabled = !submitted)
                        Text("Playlists", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Text("${tracks.size} selected", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, enabled = !submitted, onClick = { tab = 0 }, text = { Text("Add") })
                Tab(selected = tab == 1, enabled = !submitted, onClick = { tab = 1 }, text = { Text("Remove") })
            }
            OutlinedTextField(
                enabled = !submitted,
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                label = { Text("Search playlists") },
                singleLine = true,
            )
            LazyColumn(Modifier.weight(1f)) {
                items(memberships, key = { it.snapshot.document.uri.toString() }) { membership ->
                    val key = membership.snapshot.document.uri.toString()
                    Row(
                        Modifier.fillMaxWidth().height(48.dp).combinedClickable(enabled = !submitted,
                            onClick = {
                                selectedUris = if (key in selectedUris) selectedUris - key else selectedUris + key
                            },
                            onLongClick = {},
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            enabled = !submitted,
                            checked = key in selectedUris,
                            onCheckedChange = {
                                selectedUris = if (it) (selectedUris + key).distinct() else selectedUris - key
                            },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                membership.snapshot.document.displayName,
                                fontWeight = if (membership.containsAll) FontWeight.Bold else FontWeight.Normal,
                                color = if (membership.containsAll) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (membership.containsAny) {
                                Text(
                                    "${membership.presentCount}/${membership.selectedCount} selected songs present" +
                                        if (membership.occurrenceCount > membership.presentCount) " · ${membership.occurrenceCount} entries" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                if (memberships.isEmpty()) item { Text("No playlists match this view.") }
                if (tab == 0) item {
                    TextButton(enabled = !submitted, onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("＋ Create new playlist")
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { selectedUris = emptyList() },
                    enabled = selectedUris.isNotEmpty() && !submitted,
                    modifier = Modifier.weight(1f),
                ) { Text("Clear") }
                Button(
                    onClick = {
                        val documents = state.playlistScan.playlists.map(PlaylistSnapshot::document)
                            .filter { it.uri.toString() in selectedUris }
                        onSubmit()
                        actions.applyMembership(
                            if (tab == 0) BatchAction.ADD_ONE else BatchAction.REMOVE_ALL,
                            documents,
                            tracks,
                        )
                    },
                    enabled = selectedUris.isNotEmpty() && !state.busy && !submitted,
                    modifier = Modifier.weight(1f),
                ) { if (writing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("${if (tab == 0) "Add to" else "Remove from"} ${selectedUris.size}") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (showCreate) NameDialog(
        title = "Create new playlist",
        initialName = "",
        confirmLabel = "Create",
        supportingText = "The new playlist will contain ${tracks.size} selected song${if (tracks.size == 1) "" else "s"}.",
        onDismiss = { showCreate = false },
        onConfirm = {
            onSubmit()
            actions.createPlaylist(it, tracks)
            showCreate = false
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegacyRulesSheet(
    state: LibraryBrowserState,
    actions: LibraryBrowserActions,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var matchName by rememberSaveable { mutableStateOf(RecipeMatch.ALL.name) }
    var matchMenu by rememberSaveable { mutableStateOf(false) }
    var rules by rememberSaveable { mutableStateOf(listOf<Pair<String, Boolean>>()) }
    val snapshots = state.playlistScan.playlists
    val match = RecipeMatch.valueOf(matchName)
    val domainRules = rules.map { PlaylistRule(it.first, it.second) }
    val membership = snapshots.associate { it.document.uri.toString() to it.resolvedTrackIds }
    val previewIds = if (domainRules.isEmpty()) emptySet() else state.tracks.filter { track ->
        val matches = domainRules.map { rule ->
            val present = track.stableId in membership[rule.playlistIdentity].orEmpty()
            if (rule.mustBePresent) present else !present
        }
        if (match == RecipeMatch.ALL) matches.all { it } else matches.any { it }
    }.mapTo(linkedSetOf(), LibraryTrack::stableId)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Create from rules", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (state.recipes.isNotEmpty()) {
                Text("Saved recipes", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                state.recipes.forEach { recipe ->
                    TextButton(onClick = { actions.rerunRecipe(recipe) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Rerun ${recipe.playlistName}")
                    }
                }
                HorizontalDivider()
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            Box {
                OutlinedButton(onClick = { matchMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Match ${if (match == RecipeMatch.ALL) "all" else "any"} rules")
                }
                DropdownMenu(expanded = matchMenu, onDismissRequest = { matchMenu = false }) {
                    RecipeMatch.entries.forEach { option ->
                        DropdownMenuItem(text = { Text(if (option == RecipeMatch.ALL) "All" else "Any") }, onClick = {
                            matchName = option.name; matchMenu = false
                        })
                    }
                }
            }
            rules.forEachIndexed { index, rule ->
                RuleRow(
                    rule = rule,
                    playlists = snapshots,
                    onChange = { changed -> rules = rules.toMutableList().also { it[index] = changed } },
                    onRemove = { rules = rules.toMutableList().also { it.removeAt(index) } },
                )
            }
            OutlinedButton(
                onClick = {
                    snapshots.firstOrNull()?.let { rules = rules + (it.document.uri.toString() to true) }
                },
                enabled = snapshots.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("＋ Add rule") }
            Text("${previewIds.size} songs match", modifier = Modifier.padding(vertical = 10.dp))
            Button(
                onClick = {
                    actions.createRulePlaylist(name, match, domainRules)
                    onDismiss()
                },
                enabled = name.isNotBlank() && rules.isNotEmpty() && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Create playlist") }
        }
    }
}

@Composable
private fun RuleRow(
    rule: Pair<String, Boolean>,
    playlists: List<PlaylistSnapshot>,
    onChange: (Pair<String, Boolean>) -> Unit,
    onRemove: () -> Unit,
) {
    var membershipMenu by rememberSaveable { mutableStateOf(false) }
    var playlistMenu by rememberSaveable { mutableStateOf(false) }
    val name = playlists.firstOrNull { it.document.uri.toString() == rule.first }
        ?.document?.displayName ?: "Missing playlist"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box {
            TextButton(onClick = { membershipMenu = true }) {
                Text(if (rule.second) "is in" else "is not in")
            }
            DropdownMenu(expanded = membershipMenu, onDismissRequest = { membershipMenu = false }) {
                DropdownMenuItem(text = { Text("is in") }, onClick = {
                    onChange(rule.first to true); membershipMenu = false
                })
                DropdownMenuItem(text = { Text("is not in") }, onClick = {
                    onChange(rule.first to false); membershipMenu = false
                })
            }
        }
        Box(Modifier.weight(1f)) {
            TextButton(onClick = { playlistMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = playlistMenu, onDismissRequest = { playlistMenu = false }) {
                playlists.forEach { snapshot ->
                    DropdownMenuItem(text = { Text(snapshot.document.displayName) }, onClick = {
                        onChange(snapshot.document.uri.toString() to rule.second); playlistMenu = false
                    })
                }
            }
        }
        TextButton(onClick = onRemove) { Text("×") }
    }
}

@Composable
internal fun NameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    supportingText: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable(title, initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                supportingText?.let { Text(it); Spacer(Modifier.height(8.dp)) }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MutationDialogs(state: LibraryBrowserState, actions: LibraryBrowserActions, suppressSuccess: Boolean = false) {
    when (val mutation = state.mutation) {
        is BrowserMutationState.ReconfirmationRequired -> AlertDialog(
            onDismissRequest = actions.clearMutationMessage,
            title = { Text("Playlist changed") },
            text = { Text("The playlist changed while it was being checked. Review and confirm the updated operation.") },
            confirmButton = { TextButton(onClick = actions.confirmMutation) { Text("Confirm updated operation") } },
            dismissButton = { TextButton(onClick = actions.clearMutationMessage) { Text("Cancel") } },
        )
        is BrowserMutationState.Result -> if (!suppressSuccess) AlertDialog(
            onDismissRequest = actions.clearMutationMessage,
            title = { Text("Done") },
            text = { Text(mutation.message) },
            confirmButton = { TextButton(onClick = actions.clearMutationMessage) { Text("OK") } },
        )
        is BrowserMutationState.Error -> AlertDialog(
            onDismissRequest = actions.clearMutationMessage,
            title = { Text("Could not update playlist") },
            text = { Text(mutation.message) },
            confirmButton = { TextButton(onClick = actions.clearMutationMessage) { Text("OK") } },
        )
        else -> Unit
    }
}

@Composable private fun PermissionRequired(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Audio access required", style = MaterialTheme.typography.titleLarge)
        Text("Allow access so shmemplay can browse music stored on this phone.")
        Button(onClick = onRequest, modifier = Modifier.padding(top = 12.dp)) { Text("Allow audio access") }
    }
}

@Composable private fun Loading(message: String) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { CircularProgressIndicator(); Text(message, modifier = Modifier.padding(top = 10.dp)) }
}

@Composable private fun EmptyMessage(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(message) }
}


private fun detailTitle(detail: BrowserDetail?, state: LibraryBrowserState): String? = when (detail?.kind) {
    DetailKind.PLAYLIST -> state.browserPlaylists
        .firstOrNull { it.document.uri.toString() == detail.key }
        ?.document?.displayName?.substringBeforeLast('.')
    DetailKind.ALBUM, DetailKind.ARTIST, DetailKind.GENRE -> detail.key
    null -> null
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

private object ArtworkLoader {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val failed = LruCache<String, Long>(256)

    fun load(context: Context, track: LibraryTrack): Bitmap? {
        val cacheKey = track.albumId?.let { "${track.identity.volumeName}:album:$it" } ?: track.stableId
        cache.get(cacheKey)?.let { return it }
        failed.get(cacheKey)?.let { if (android.os.SystemClock.elapsedRealtime() - it < 60000) return null }
        val bitmap = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(track.contentUri, Size(160, 160), null)
            } else {
                MediaMetadataRetriever().run {
                    try {
                        setDataSource(context, track.contentUri)
                        embeddedPicture?.let { bytes ->
                            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                            var sample = 1
                            while (bounds.outWidth / sample > 320 || bounds.outHeight / sample > 320) sample *= 2
                            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                        }
                    } finally {
                        release()
                    }
                }
            }
        }.getOrNull()
        if (bitmap != null) cache.put(cacheKey, bitmap) else failed.put(cacheKey, android.os.SystemClock.elapsedRealtime())
        return bitmap
    }
}

private fun sectionIcon(section: BrowserSection): Int = when(section) {
    BrowserSection.QUEUES -> R.drawable.ic_list_music
    BrowserSection.NOW_PLAYING -> R.drawable.ic_circle_play
    BrowserSection.SONGS -> R.drawable.ic_music_2
    BrowserSection.ALBUMS -> R.drawable.ic_disc_3
    BrowserSection.ARTISTS -> R.drawable.ic_user_round
    BrowserSection.GENRES -> R.drawable.ic_tags
    BrowserSection.PLAYLISTS -> R.drawable.ic_list_video
}

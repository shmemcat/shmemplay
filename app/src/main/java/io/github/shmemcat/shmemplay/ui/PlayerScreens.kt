package io.github.shmemcat.shmemplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.shmemcat.shmemplay.R
import io.github.shmemcat.shmemplay.player.*
import io.github.shmemcat.shmemplay.tracks.LibrarySearch
import io.github.shmemcat.shmemplay.tracks.LibraryTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class SongPlaybackActions(val repository: PlayerRepository, val navigate: (String,String) -> Unit = { _, _ -> }, val play: (LibraryTrack) -> Unit)
internal val LocalSongPlayback = staticCompositionLocalOf<SongPlaybackActions?> { null }
@Composable internal fun PlayerIcon(id: Int, label: String?, modifier: Modifier = Modifier.size(25.dp)) {
    Icon(painterResource(id), label, modifier)
}
@Composable internal fun PlayerButton(icon: Int, label: String, enabled: Boolean = true, action: () -> Unit) {
    IconButton(onClick = action, enabled = enabled) { PlayerIcon(icon, label) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun NowPlayingScreen(state: PlayerUiState, repository: PlayerRepository, onPlaylist: (LibraryTrack) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val q = state.book.active
    val current = q?.current
    var options by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    var policy by remember { mutableStateOf(false) }
    if (q == null || current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Choose a song from your library to start a queue.", textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp)) }
        return
    }
    val track = remember(current) { current.toLibraryTrack() }
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        val compact = maxWidth > maxHeight
        val compactArtSize = minOf(maxHeight, maxWidth * .32f) * .94f
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (compact) {
                AlbumArtwork(track, false, Modifier.size(compactArtSize))
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (compact) Arrangement.Center else Arrangement.Top) {
                if (!compact) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val artSize = minOf(maxWidth, maxHeight).coerceAtLeast(0.dp) * .94f
                        if (artSize > 0.dp) AlbumArtwork(track, false, Modifier.size(artSize))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(current.title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (compact) "${current.artist} · ${current.album}" else current.artist, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!compact) Text(current.album, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    PlayerButton(R.drawable.ic_info, "Song info") { info = true }
                    PlayerButton(R.drawable.ic_list_plus, "Add/remove from playlists") { onPlaylist(track) }
                    PlayerButton(R.drawable.ic_ellipsis, "Song options") { options = true }
                    PlayerButton(R.drawable.ic_repeat, "Song and queue end behavior") { policy = true }
                    IconButton(onClick = { repository.shuffle(q.id); android.widget.Toast.makeText(context, if(q.policy.shuffle) "Shuffle off" else "Shuffle on", android.widget.Toast.LENGTH_SHORT).show() }) {
                        Icon(painterResource(R.drawable.ic_shuffle), if (q.policy.shuffle) "Turn shuffle off" else "Turn shuffle on", Modifier.size(25.dp),
                            tint = if (q.policy.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                var seeking by remember(current.id) { mutableStateOf<Float?>(null) }
                val duration = current.durationMs.coerceAtLeast(1)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(playerTime(seeking?.toLong() ?: state.positionMs), style = MaterialTheme.typography.labelSmall)
                    Slider(value = seeking ?: state.positionMs.coerceIn(0, duration).toFloat(), onValueChange = { seeking = it },
                        onValueChangeFinished = { seeking?.let { repository.seek(it.toLong()) }; seeking = null },
                        valueRange = 0f..duration.toFloat(), modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        thumb = { Box(Modifier.size(16.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) },
                        track = { slider -> SliderDefaults.Track(slider, Modifier.height(4.dp), thumbTrackGapSize = 0.dp, drawStopIndicator = null) })
                    Text(playerTime(duration), style = MaterialTheme.typography.labelSmall)
                }
                Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    PlayerButton(R.drawable.ic_skip_back, "Previous or restart") { repository.previous() }
                    PlayerButton(R.drawable.ic_rewind, "Rewind 10 seconds") { repository.seek(state.positionMs - 10000) }
                    FilledIconButton(onClick = repository::playPause, modifier = Modifier.size(52.dp)) {
                        PlayerIcon(if (state.playing) R.drawable.ic_pause else R.drawable.ic_play, if (state.playing) "Pause" else "Play", Modifier.size(32.dp))
                    }
                    PlayerButton(R.drawable.ic_fast_forward, "Forward 10 seconds") { repository.seek((state.positionMs + 10000).coerceAtMost(duration)) }
                    PlayerButton(R.drawable.ic_skip_forward, "Next") { repository.next() }
                }
                Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
            }
        }
    }
    if (info) SongInfoDialog(track) { info = false }
    if (options) SongOptionsDialog(track, repository, q.id, onPlaylist) { options = false }
    if (policy) QueuePolicyDialog(q, repository) { policy = false }
}

@Composable internal fun QueuesScreen(state: PlayerUiState, repository: PlayerRepository, onPlaylist: (LibraryTrack) -> Unit, save: (String, List<LibraryTrack>) -> Unit, query: String, selected: Set<String>, onToggle: (String) -> Unit, onSelect: (String) -> Unit) {
    val q = state.book.viewed
    var picker by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf<QueueTrack?>(null) }
    var saving by remember { mutableStateOf(false) }
    var policy by remember { mutableStateOf(false) }
    var sorting by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val drag = remember(q?.id, query) { QueueDragState() }
    var pendingOrder by remember(q?.id, query) { mutableStateOf<List<String>?>(null) }
    val rows by produceState<List<QueueTrack>>(emptyList(), q?.entries, query) {
        value = emptyList()
        value = withContext(Dispatchers.Default) {
            val entries = q?.entries.orEmpty()
            if (query.isBlank()) entries else entries.filter { LibrarySearch.matches(it.toLibraryTrack(), query) }
        }
    }
    LaunchedEffect(rows, state.error) { pendingOrder = null }
    val displayedRows = remember(rows, pendingOrder) {
        pendingOrder?.let { order -> val byId = rows.associateBy { it.id }; order.mapNotNull(byId::get) } ?: rows
    }
    val rowIds = remember(displayedRows) { displayedRows.map { it.id } }
    LaunchedEffect(q?.id, query) { listState.scrollToItem(0) }
    if (q == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Your queues will appear here when you play a song.", modifier = Modifier.padding(24.dp), textAlign = TextAlign.Center) }
        return
    }
    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { picker = true }, modifier = Modifier.weight(1f)) {
                Text(q.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                PlayerIcon(R.drawable.ic_chevron_down, "Choose queue")
            }
            PlayerButton(R.drawable.ic_trash_2, "Delete queue") { repository.delete(q.id) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayerButton(R.drawable.ic_play, "Resume this queue", q.entries.isNotEmpty()) { repository.resume(q.id) }
            Box {
                PlayerButton(R.drawable.ic_arrow_down_wide_narrow, "Sort queue") { sorting = true }
                DropdownMenu(sorting, { sorting = false }) {
                    listOf("Title", "Artist", "Album", "Duration", "Reverse").forEach { key ->
                        DropdownMenuItem(text = { Text(key) }, onClick = { repository.sort(q.id, key); sorting = false })
                    }
                }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${q.entries.indexOfFirst { it.id == q.currentId } + 1} / ${q.entries.size}", style = MaterialTheme.typography.bodySmall)
                Text(playerTime(remember(q.entries) { q.entries.sumOf { it.durationMs } }), style = MaterialTheme.typography.bodySmall)
            }
            PlayerButton(R.drawable.ic_save, "Save queue as playlist") { saving = true }
            PlayerButton(R.drawable.ic_repeat, "Queue settings") { policy = true }
        }
        HorizontalDivider()
        Box(Modifier.weight(1f)) {
            FastScrollableLazyColumn(listState, rows.size, null, true) {
                items(displayedRows, key = { it.id }) { entry ->
                    val marked = entry.id == q.currentId
                    val inactive = q.id != state.book.activeId
                    val textColor = if (inactive) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f) else MaterialTheme.colorScheme.onSurface
                    val fontStyle = if (inactive) FontStyle.Italic else FontStyle.Normal
                    Row(draggedRow(entry.id, rowIds, drag, 56.dp).fillMaxWidth().height(56.dp)
                        .background(if (marked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (inactive) .35f else .8f) else MaterialTheme.colorScheme.surface)
                        .combinedClickable(onClick = {
                            if (selected.isNotEmpty()) onToggle(entry.id)
                            else if (query.isNotBlank()) repository.createSnapshot(q.name + " · search", rows, entry.id)
                            else repository.resume(q.id, entry.id)
                        }, onLongClick = { onSelect(entry.id) })
                        .padding(end = 10.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        QueueDragHandle(entry.id, rowIds, drag, 56.dp, enabled = query.isBlank()) { order ->
                            pendingOrder = order
                            repository.reorder(q.id, order)
                        }
                        AlbumArtwork(entry.toLibraryTrack(), entry.id in selected, Modifier.size(44.dp).alpha(if (inactive) .45f else 1f))
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontStyle = fontStyle,
                                color = if (marked && !inactive) MaterialTheme.colorScheme.primary else textColor)
                            val detail = if (entry.unavailable) "Unavailable · " + entry.filename
                                else (if (q.stopAfterId == entry.id) "Stop after · " else "") + entry.artist + " · " + entry.album
                            Text(detail, style = MaterialTheme.typography.bodySmall, fontStyle = fontStyle, color = textColor,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(playerTime(entry.durationMs), style = MaterialTheme.typography.bodySmall, color = textColor)
                        CompositionLocalProvider(LocalContentColor provides textColor) {
                            PlayerButton(R.drawable.ic_ellipsis, "Options for " + entry.title) { options = entry }
                        }
                    }
                }
            }
            if (rows.isEmpty()) Text("No songs match this queue search.", Modifier.padding(16.dp))
        }
    }
    if (picker) QueuePicker(state, repository) { picker = false }
    options?.let { entry -> SongOptionsDialog(entry.toLibraryTrack(), repository, q.id, onPlaylist) { options = null } }
    if (policy) QueuePolicyDialog(q, repository) { policy = false }
    if (saving) NameDialog("Save queue as playlist", q.name, "Save", "Creates an M3U snapshot in your playlist folder.", { saving = false }) {
        save(it, q.entries.map { entry -> entry.toLibraryTrack() }); saving = false
    }

}

@Composable private fun QueuePicker(state: PlayerUiState, repository: PlayerRepository, onDismiss: () -> Unit) {
    var rename by remember { mutableStateOf<MusicQueue?>(null) }
    val drag = remember { QueueDragState() }
    var pendingOrder by remember { mutableStateOf<List<String>?>(null) }
    val sourceOrder = state.book.queues.map { it.id }
    LaunchedEffect(sourceOrder, state.error) { pendingOrder = null }
    val order = pendingOrder ?: sourceOrder
    val queues = order.mapNotNull { id -> state.book.queues.firstOrNull { it.id == id } }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text("Queues", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(8.dp))
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(queues, key = { it.id }) { q ->
                        Row(draggedRow(q.id, order, drag, 64.dp).fillMaxWidth().height(64.dp).background(MaterialTheme.colorScheme.surface).combinedClickable(onClick = { repository.view(q.id); onDismiss() }, onLongClick = { rename = q }), verticalAlignment = Alignment.CenterVertically) {
                            QueueDragHandle(q.id, order, drag, 64.dp) { next -> pendingOrder = next; repository.reorderQueues(next) }
                            Column(Modifier.weight(1f)) {
                                Text(q.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (q.id == state.book.viewedId) FontWeight.Bold else FontWeight.Normal)
                                Text(if (q.id == state.book.activeId) "Active · ${q.entries.size} songs" else "${q.entries.size} songs", style = MaterialTheme.typography.bodySmall)
                            }
                            PlayerButton(R.drawable.ic_pencil, "Rename ${q.name}") { rename = q }
                            PlayerButton(R.drawable.ic_trash_2, "Remove ${q.name}") { repository.delete(q.id) }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Done") }
            }
        }
    }
    rename?.let { q -> NameDialog("Rename queue", q.name, "Rename", null, { rename = null }) { repository.rename(q.id, it); rename = null } }
}

@Composable internal fun AddToQueueDialog(tracks: List<LibraryTrack>, repository: PlayerRepository, onDismiss: () -> Unit) {
    val state by repository.state.collectAsState()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add to a queue") }, text = {
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            items(state.book.queues, key = { it.id }) { q -> MenuAction(q.name) { repository.insert(q.id, tracks); onDismiss() } }
        }
    }, confirmButton = { TextButton(onClick = {
        if (tracks.isNotEmpty()) repository.createAdditional(tracks)
        onDismiss()
    }) { Text("Create new queue") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable private fun QueuePolicyDialog(queue: MusicQueue, repository: PlayerRepository, onDismiss: () -> Unit) {
    var policy by remember(queue.id) { mutableStateOf(queue.policy) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Song and queue behavior") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("When a song ends", fontWeight = FontWeight.Bold)
            listOf(SongEnd.STOP to "Stop there", SongEnd.LOAD_AND_PAUSE to "Load next and pause", SongEnd.PLAY_NEXT to "Play next song", SongEnd.REPEAT to "Repeat this song").forEach { (value, label) ->
                RadioOption(label, policy.songEnd == value) { policy = policy.copy(songEnd = value) }
            }
            HorizontalDivider(); Text("When the queue ends", fontWeight = FontWeight.Bold)
            CheckOption("Reset to first; reshuffle if enabled", policy.resetAtEnd) { policy = policy.copy(resetAtEnd = it) }
            listOf(QueueEnd.STOP to "Stop there", QueueEnd.NEXT_QUEUE to "Jump to next queue", QueueEnd.REPEAT to "Repeat this queue").forEach { (value, label) ->
                RadioOption(label, policy.queueEnd == value) { policy = policy.copy(queueEnd = value) }
            }
            if (policy.queueEnd == QueueEnd.NEXT_QUEUE) {
                CheckOption("Resume destination's saved position", policy.resumeNext) { policy = policy.copy(resumeNext = it) }
                CheckOption("Wrap last queue to first", policy.wrapQueues) { policy = policy.copy(wrapQueues = it) }
            }
        }
    }, confirmButton = { TextButton(onClick = { repository.policy(queue.id, policy); onDismiss() }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
internal fun playerTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)
}

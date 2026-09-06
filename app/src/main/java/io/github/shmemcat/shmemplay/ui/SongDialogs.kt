package io.github.shmemcat.shmemplay.ui

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import android.media.MediaMetadataRetriever
import io.github.shmemcat.shmemplay.R
import io.github.shmemcat.shmemplay.domain.Mp3Tags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.shmemcat.shmemplay.player.PlayerRepository
import io.github.shmemcat.shmemplay.tracks.LibraryTrack

@Composable internal fun SongOptionsDialog(track: LibraryTrack, repository: PlayerRepository?, queueId: String?, onPlaylist: (LibraryTrack) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val playerState = repository?.state?.collectAsState()?.value
    var info by remember { mutableStateOf(false) }
    var editTags by remember { mutableStateOf(false) }
    var deleteAudio by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 10.dp)) {
                Text(track.title, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                MenuAction("Song info", R.drawable.ic_info) { info = true }
                HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                val active = playerState?.book?.activeId
                MenuAction("Play after current song", R.drawable.ic_skip_forward, active != null) { repository?.insert(active!!, listOf(track), true); onDismiss() }
                MenuAction("Add to currently playing queue", R.drawable.ic_list_video, active != null) { repository?.insert(active!!, listOf(track)); onDismiss() }
                MenuAction("Add to a queue", R.drawable.ic_list_music, repository != null) { destination = true }
                MenuAction("Add/remove from playlists", R.drawable.ic_list_plus) { onPlaylist(track); onDismiss() }
                if (queueId != null && repository != null) {
                    MenuAction("Remove from this queue", R.drawable.ic_trash_2) { repository.remove(queueId, track.stableId); onDismiss() }
                    MenuAction("Stop after this song", R.drawable.ic_pause) { repository.stopAfter(queueId, track.stableId); onDismiss() }
                }
                HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                MenuAction("Edit tags", R.drawable.ic_pencil) { editTags = true }
                MenuAction("Share audio file", R.drawable.ic_share_2) {
                    runCatching {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = context.contentResolver.getType(track.contentUri) ?: "audio/*"
                            putExtra(Intent.EXTRA_STREAM, track.contentUri)
                            clipData = ClipData.newRawUri(track.displayName, track.contentUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, "Share audio file"))
                    }.onFailure { repository?.reportError("Could not share this audio file: ${it.message}") }
                    onDismiss()
                }
                HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                MenuAction("Delete audio file permanently", R.drawable.ic_trash_2) { deleteAudio = true }
            }
        }
    }
    if (editTags) EditTagsDialog(track) { editTags = false }
    if (deleteAudio) DeleteAudioDialog(track, repository) { deleteAudio = false; onDismiss() }
    if (info) SongInfoDialog(track) { info = false; onDismiss() }
    if (destination && repository != null) AddToQueueDialog(listOf(track), repository) { destination = false; onDismiss() }
}

@Composable internal fun SongInfoDialog(track: LibraryTrack, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var edit by remember { mutableStateOf(false) }
    val navigation = LocalSongPlayback.current
    val metadata by produceState<Map<String,String?>>(emptyMap(), track.stableId) {
        value = withContext(Dispatchers.IO) {
            val result = linkedMapOf<String,String?>()
            runCatching { val reader = MediaMetadataRetriever(); try {
                reader.setDataSource(context,track.contentUri)
                mapOf("Album artist" to MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST,
                    "Composer" to MediaMetadataRetriever.METADATA_KEY_COMPOSER,
                    "Track number" to MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER,
                    "Disc number" to MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER,
                    "Year" to MediaMetadataRetriever.METADATA_KEY_YEAR).forEach { (name,key) -> result[name] = reader.extractMetadata(key) }
            } finally { reader.release() } }
            if(track.displayName.endsWith(".mp3",true)) runCatching {
                context.contentResolver.openInputStream(track.contentUri)?.use(Mp3Tags::read)?.let { tag ->
                    result["Lyricist"] = tag.text("TEXT"); result["Embedded lyrics"] = tag.lyrics()
                }
            }
            result
        }
    }
    if (edit) EditTagsDialog(track) { edit = false }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Song info") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AlbumArtwork(track, false, Modifier.fillMaxWidth().aspectRatio(1f))
            listOf("Filename" to track.displayName, "Location" to "${track.identity.volumeName}/${track.relativePath.orEmpty()}",
                "Title" to track.title, "Artist" to track.artist, "Album" to track.album, "Genre" to track.genre,
                "Duration" to playerTime(track.durationMs)).forEach { (label, value) ->
                Column { Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary);
                    if (label in listOf("Album","Artist","Genre") && navigation != null) MenuAction(value) { navigation.navigate(label,value);onDismiss() } else Text(value.ifBlank { "Not available" }) }
            }
            listOf("Album artist","Composer","Lyricist","Track number","Disc number","Year","Embedded lyrics").forEach { label ->
                Column { Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary);Text(metadata[label]?.takeIf { it.isNotBlank() } ?: "Not available") }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }, dismissButton = { TextButton(onClick = { edit = true }) { Text("Edit tags") } })
}

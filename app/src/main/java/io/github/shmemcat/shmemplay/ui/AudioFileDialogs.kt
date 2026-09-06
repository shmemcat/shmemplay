package io.github.shmemcat.shmemplay.ui

import android.app.Activity
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.shmemcat.shmemplay.domain.Mp3Tags
import io.github.shmemcat.shmemplay.player.AudioFileEdits
import io.github.shmemcat.shmemplay.player.PlayerRepository
import io.github.shmemcat.shmemplay.tracks.LibraryTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun EditTagsDialog(track:LibraryTrack,onDismiss:()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var fields by remember(track.stableId){mutableStateOf<Map<String,String>?>(null)}
    var original by remember(track.stableId){mutableStateOf<Map<String,String>>(emptyMap())}
    var error by remember{mutableStateOf<String?>(null)}
    var busy by remember{mutableStateOf(false)}
    var pending by remember{mutableStateOf<Map<String,String>>(emptyMap())}
    val writer=remember(context){AudioFileEdits(context.applicationContext)}
    LaunchedEffect(track.stableId) {
        if(!track.displayName.endsWith(".mp3",true)) {error="Tag editing currently supports MP3 with ID3v2.3/2.4. This format is read-only.";return@LaunchedEffect}
        if(Build.VERSION.SDK_INT<30){error="Tag saving requires Android 11 or newer for per-file write consent.";return@LaunchedEffect}
        runCatching{withContext(Dispatchers.IO){context.contentResolver.openInputStream(track.contentUri)?.use(Mp3Tags::read) ?: error("Audio file unavailable")}}
            .onSuccess{tag->fields=(Mp3Tags.editable.keys+if(tag.version==3)"TYER" else "TDRC").associateWith{tag.text(it).orEmpty()};original=fields!!}
            .onFailure{error=it.message}
    }
    val consent=rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()){result->
        if(result.resultCode==Activity.RESULT_OK) {
            busy=true
            scope.launch{runCatching{writer.save(track.contentUri,track.displayName,pending)}.onSuccess{onDismiss()}.onFailure{error=it.message};busy=false}
        }
    }
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text("Edit tags")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(track.displayName,style=MaterialTheme.typography.titleSmall)
            Text("Changes affect this audio file. Other tags and artwork are preserved.",style=MaterialTheme.typography.bodySmall)
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            fields?.forEach{(id,value)->OutlinedTextField(value,{next->fields=fields!!+(id to next)},label={Text(Mp3Tags.editable[id]?:"Year")},enabled=!busy,singleLine=true,modifier=Modifier.fillMaxWidth())}
            if(busy)Text("Saving and verifying tags…")
        }
    },confirmButton={TextButton(enabled=!busy && fields!=null && fields!=original,onClick={
        if(Build.VERSION.SDK_INT>=30) {
            pending=fields!!.filter{(id,value)->value!=original[id]}
            runCatching{val request=MediaStore.createWriteRequest(context.contentResolver,listOf(track.contentUri));consent.launch(IntentSenderRequest.Builder(request.intentSender).build())}.onFailure{error=it.message}
        }
    }){Text("Save")}},dismissButton={TextButton(enabled=!busy,onClick=onDismiss){Text("Cancel")}})
}

@Composable internal fun DeleteAudioDialog(track:LibraryTrack,repository:PlayerRepository?,onDismiss:()->Unit) {
    val context=LocalContext.current
    var error by remember{mutableStateOf<String?>(null)}
    val consent=rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()){result->
        if(result.resultCode==Activity.RESULT_OK)repository?.unavailable(track.stableId)
        onDismiss()
    }
    AlertDialog(onDismissRequest=onDismiss,title={Text("Delete audio file permanently?")},text={Column {
        Text(track.displayName);Text(track.relativePath.orEmpty(),style=MaterialTheme.typography.bodySmall)
        Text("Deletes the music file from storage. This is different from removing a queue entry or playlist reference. Android will ask you to confirm.")
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick={
        if(Build.VERSION.SDK_INT>=30)runCatching{val request=MediaStore.createDeleteRequest(context.contentResolver,listOf(track.contentUri));consent.launch(IntentSenderRequest.Builder(request.intentSender).build())}.onFailure{error=it.message}
        else error="Per-file deletion requires Android 11 or newer."
    }){Text("Delete file")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}

@Composable internal fun AudioRecoveryControl() {
    val context=LocalContext.current
    val store=remember{AudioFileEdits(context.applicationContext)}
    val scope=rememberCoroutineScope()
    var pending by remember{mutableStateOf(runCatching{store.pending()}.getOrNull())}
    var message by remember{mutableStateOf<String?>(null)}
    var confirm by remember{mutableStateOf(false)}
    val consent=rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()){result->
        if(result.resultCode==Activity.RESULT_OK)scope.launch{runCatching{store.restore()}.onSuccess{pending=null;message="Recovery verified."}.onFailure{message=it.message}}
    }
    if(pending!=null)OutlinedButton(onClick={confirm=true}){Text("Restore interrupted tag edit")}
    message?.let{Text(it)}
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Restore saved audio backup?")},text={Text("File: " + pending!!.filename + ". This restores its exact original bytes if the interrupted write is incomplete. Any later edits to this file may be replaced.")},confirmButton={TextButton(onClick={
        confirm=false
        if(Build.VERSION.SDK_INT>=30)runCatching{val request=MediaStore.createWriteRequest(context.contentResolver,listOf(pending!!.uri));consent.launch(IntentSenderRequest.Builder(request.intentSender).build())}.onFailure{message=it.message}
    }){Text("Restore")}},dismissButton={TextButton(onClick={confirm=false}){Text("Cancel")}})
}

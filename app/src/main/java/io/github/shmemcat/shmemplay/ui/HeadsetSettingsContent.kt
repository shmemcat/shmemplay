package io.github.shmemcat.shmemplay.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.shmemcat.shmemplay.player.HeadsetSettings

@Composable internal fun HeadsetSettingsContent() {
    val context=LocalContext.current
    val store=remember(context){HeadsetSettings(context)}
    var settings by remember{mutableStateOf(store.load())}
    fun save(value: io.github.shmemcat.shmemplay.player.HeadsetPreferences) {settings=value;store.save(value)}
    Text("Headset, Bluetooth and speakers",style=MaterialTheme.typography.titleMedium)
    CheckOption("Pause on disconnect", settings.pauseOnDisconnect) { save(settings.copy(pauseOnDisconnect=it)) }
    CheckOption("Resume on Bluetooth reconnect", settings.resumeBluetooth) { save(settings.copy(resumeBluetooth=it)) }
    CheckOption("Resume on wired reconnect", settings.resumeWired) { save(settings.copy(resumeWired=it)) }
    Text("Reconnect resumes only the song paused by a disconnection. A manual pause or queue switch cancels it.",style=MaterialTheme.typography.bodySmall)
    CheckOption("Block unsolicited external play commands", settings.preventAutoplay) { save(settings.copy(preventAutoplay=it)) }
    val actions=listOf("Next","Previous","Play / pause","None")
    TextButton(modifier=Modifier.fillMaxWidth(),onClick={save(settings.copy(doublePress=actions[(actions.indexOf(settings.doublePress)+1)%actions.size]))}) {Text("Double press: " + settings.doublePress)}
    TextButton(modifier=Modifier.fillMaxWidth(),onClick={save(settings.copy(triplePress=actions[(actions.indexOf(settings.triplePress)+1)%actions.size]))}) {Text("Triple press: " + settings.triplePress)}
    Text("Raw headset clicks within 400 ms are grouped. Four or more clicks seek forward 10 seconds. Devices that send Next/Previous commands use those commands directly.",style=MaterialTheme.typography.bodySmall)
}

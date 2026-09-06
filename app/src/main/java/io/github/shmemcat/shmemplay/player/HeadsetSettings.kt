package io.github.shmemcat.shmemplay.player

import android.content.Context

data class HeadsetPreferences(val pauseOnDisconnect: Boolean = true, val resumeBluetooth: Boolean = false,
    val resumeWired: Boolean = false, val preventAutoplay: Boolean = true, val doublePress: String = "Next", val triplePress: String = "Previous")
class HeadsetSettings(context: Context) {
    private val preferences = context.getSharedPreferences("player-headset-v1",Context.MODE_PRIVATE)
    fun load() = HeadsetPreferences(preferences.getBoolean("pause",true),preferences.getBoolean("bluetooth",false),
        preferences.getBoolean("wired",false),preferences.getBoolean("preventAutoplay",true),preferences.getString("double","Next")!!,preferences.getString("triple","Previous")!!)
    fun save(value: HeadsetPreferences) { preferences.edit().putBoolean("pause",value.pauseOnDisconnect).putBoolean("bluetooth",value.resumeBluetooth)
        .putBoolean("wired",value.resumeWired).putBoolean("preventAutoplay",value.preventAutoplay).putString("double",value.doublePress).putString("triple",value.triplePress).apply() }
}

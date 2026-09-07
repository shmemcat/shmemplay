package io.github.shmemcat.shmemplay.player

import android.app.PendingIntent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaLibraryService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.shmemcat.shmemplay.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService(), PlaybackEngine {
    private lateinit var exo: ExoPlayer
    private lateinit var repository: PlayerRepository
    private var session: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadedQueueId: String? = null
    private val reconnect = DisconnectResumeGuard()
    private lateinit var headset: HeadsetSettings
    private lateinit var audioManager: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var clicks = 0
    private val clickAction = Runnable {
        val action = when(clicks) { 1 -> "Play / pause"; 2 -> headset.load().doublePress; 3 -> headset.load().triplePress; else -> "Forward" }
        clicks = 0
        when(action) { "Next" -> repository.next(); "Previous" -> repository.previous(); "Play / pause" -> repository.playPause(); "Forward" -> repository.seek(positionMs + 10000) }
    }
    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            val bluetooth = addedDevices.any { it.isSink && (it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                (android.os.Build.VERSION.SDK_INT >= 31 && it.type in setOf(AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER))) }
            val wired = addedDevices.any { it.isSink && it.type in setOf(AudioDeviceInfo.TYPE_WIRED_HEADSET,AudioDeviceInfo.TYPE_WIRED_HEADPHONES,AudioDeviceInfo.TYPE_USB_HEADSET) }
            if (!bluetooth && !wired) return
            val q = repository.state.value.book.active
            val options = headset.load()
            if(reconnect.consume(q?.id,q?.currentId, (bluetooth && options.resumeBluetooth) || (wired && options.resumeWired))) repository.resume(q!!.id)
        }
    }
    override val positionMs: Long get() = exo.currentPosition.coerceAtLeast(0)
    override val playing: Boolean get() = exo.playWhenReady

    override fun onCreate() {
        super.onCreate()
        repository = PlayerRepository.get(this)
        headset = HeadsetSettings(this)
        audioManager = getSystemService(AudioManager::class.java)
        exo = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(headset.load().pauseOnDisconnect)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) repository.next(manual = false)
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) {
                    val q = repository.state.value.book.active
                    reconnect.disconnected(q?.id,q?.currentId)
                } else if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST || reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) reconnect.invalidate()
                repository.tick()
                if (!playWhenReady) repository.checkpoint()
            }
            override fun onPlayerError(error: PlaybackException) {
                exo.pause()
                exo.currentMediaItem?.mediaId?.let(repository::unavailable)
                repository.reportError("This saved song could not be played. Its queue entry is retained. ${error.errorCodeName}")
            }
        })
        val transport = object : ForwardingSimpleBasePlayer(exo) {
            override fun getState(): State {
                val state = super.getState()
                return state.buildUpon().setAvailableCommands(state.availableCommands.buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT).add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS).add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .remove(Player.COMMAND_CHANGE_MEDIA_ITEMS).add(Player.COMMAND_SET_MEDIA_ITEM)
                    .remove(Player.COMMAND_SET_REPEAT_MODE).remove(Player.COMMAND_SET_SHUFFLE_MODE).build()).build()
            }
            override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
                reconnect.invalidate()
                return serviceScope.future { repository.setPlayingFromController(playWhenReady) }
            }
            override fun handleSetMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> = serviceScope.future {
                require(mediaItems.size == 1 && (startIndex == 0 || startIndex == C.INDEX_UNSET))
                val selection = QueueMediaLibrary.selection(mediaItems.single().mediaId)
                    ?: error("Unknown saved queue item")
                repository.selectFromController(selection, startPositionMs.takeUnless { it == C.TIME_UNSET })
            }
            override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
                when (seekCommand) {
                    Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> repository.next()
                    Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> repository.previous()
                    else -> return super.handleSeek(mediaItemIndex, positionMs, seekCommand)
                }
                return Futures.immediateVoidFuture()
            }
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val callback = object : QueueLibraryCallback(repository, serviceScope, headset, packageName) {
                override fun onMediaButtonEvent(session: MediaSession, controllerInfo: MediaSession.ControllerInfo, intent: Intent): Boolean {
                    @Suppress("DEPRECATION") val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
                    if(event.keyCode != KeyEvent.KEYCODE_HEADSETHOOK) return false
                    if (!canControl(session, controllerInfo)) return true
                    if(event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        reconnect.invalidate();clicks++;handler.removeCallbacks(clickAction);handler.postDelayed(clickAction,400)
                    }
                    return true
                }
            }
        session = MediaLibrarySession.Builder(this, transport, callback).setSessionActivity(open).build()
        addSession(session!!)
        repository.attach(this)
        serviceScope.launch {
            repository.state.map { it.book.revision }.distinctUntilChanged().collect {
                session?.let { callback.notifyQueueChanges(it, repository.state.value.book) }
            }
        }
        audioManager.registerAudioDeviceCallback(devices,handler)
        serviceScope.launch {
            var tick = 0
            while (isActive) {
                delay(500)
                exo.setHandleAudioBecomingNoisy(headset.load().pauseOnDisconnect)
                repository.tick()
                if (++tick % 10 == 0) repository.checkpoint()
            }
        }
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun apply(book: QueueBook, play: Boolean?, seek: Boolean) {
        val q = book.active
        val current = q?.current
        if (current == null) {
            exo.pause(); exo.clearMediaItems(); loadedQueueId = null
            return
        }
        val changed = loadedQueueId != q.id || exo.currentMediaItem?.mediaId != current.id
        if(changed || seek || play != null) reconnect.invalidate()
        if (changed) {
            val continuePlaying = play ?: exo.playWhenReady
            val item = current.mediaItem()
            val dataSources = androidx.media3.datasource.DataSource.Factory {
                IdentityCheckedDataSource(this,current,androidx.media3.datasource.DefaultDataSource.Factory(this).createDataSource())
            }
            val source = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this).setDataSourceFactory(dataSources).createMediaSource(item)
            exo.setMediaSource(source,q.positionMs)
            loadedQueueId = q.id
            exo.prepare()
            exo.playWhenReady = continuePlaying
        } else {
            if (seek) exo.seekTo(q.positionMs)
            if (play != null) {
                if (play && (exo.playbackState == Player.STATE_IDLE || exo.playbackState == Player.STATE_ENDED)) {
                    if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(0)
                    exo.prepare()
                }
                exo.playWhenReady = play
            }
        }
    }
    override fun onDestroy() {
        audioManager.unregisterAudioDeviceCallback(devices)
        handler.removeCallbacks(clickAction)
        repository.detach(this)
        serviceScope.cancel()
        session?.release()
        exo.release()
        super.onDestroy()
    }
}

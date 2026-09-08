package io.github.shmemcat.shmemplay.player

import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QueueControllerPermissionsTest {
    private fun checkCommands(packageName: String, trusted: Boolean = false,
        verified: Boolean = true, preventAutoplay: Boolean = true, allowed: Boolean) {
        val context = RuntimeEnvironment.getApplication()
        val settings = HeadsetSettings(context)
        settings.save(HeadsetPreferences(preventAutoplay = preventAutoplay))
        val repository = PlayerRepository(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val player = ExoPlayer.Builder(context).build()
        val session = MediaSession.Builder(context, player).build()
        try {
            val callback = QueueLibraryCallback(repository, scope, settings, context.packageName)
            val controller = MediaSession.ControllerInfo.createTestOnlyControllerInfo(
                packageName, 123, 12345, 0, 0, trusted, Bundle.EMPTY, verified)
            val result = callback.onConnect(session, controller)
            assertTrue(result.isAccepted)
            assertEquals(allowed, callback.canControl(session, controller))
            assertTrue(result.availablePlayerCommands.contains(Player.COMMAND_GET_METADATA))
            // Media3 checks the advertised grant BEFORE invoking onPlayerCommandRequest.
            assertEquals(allowed, result.availablePlayerCommands.contains(Player.COMMAND_PLAY_PAUSE))
            assertEquals(allowed, result.availablePlayerCommands.contains(Player.COMMAND_SET_MEDIA_ITEM))
            assertEquals(allowed, result.availablePlayerCommands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        } finally {
            session.release(); player.release(); scope.cancel(); repository.close()
        }
    }

    @Test fun verifiedAndroidAutoGetsTransportEvenWithoutPlatformTrust() =
        checkCommands("com.google.android.projection.gearhead", allowed = true)

    @Test fun explicitlyAllowedExternalControllerGetsTransport() =
        checkCommands("example.controller", preventAutoplay = false, allowed = true)

    @Test fun blockedExternalControllerCanReadMetadataButCannotPlay() =
        checkCommands("example.controller", allowed = false)

    @Test fun trustedControllerGetsTransport() =
        checkCommands("example.controller", trusted = true, allowed = true)

    @Test fun unverifiedAutoPackageCannotBypassPlaybackProtection() =
        checkCommands("com.google.android.projection.gearhead", verified = false, allowed = false)
}

package io.github.shmemcat.shmemplay.player

import android.content.ComponentName
import android.content.Intent
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Uses Android's legacy browser, the same public service interface used by Android Auto. */
@RunWith(AndroidJUnit4::class)
class CarBrowserTest {
    @Test fun platformBrowserDiscoversAndBrowsesSavedQueuesWithoutStartingPlayback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val component = ComponentName(context, PlaybackService::class.java)
        assertTrue(context.packageManager.queryIntentServices(Intent("android.media.browse.MediaBrowserService")
            .setPackage(context.packageName), 0).any { it.serviceInfo.name == component.className })
        val main = Handler(Looper.getMainLooper())
        val connected = CountDownLatch(1)
        lateinit var browser: MediaBrowser
        main.post {
            browser = MediaBrowser(context, component, object : MediaBrowser.ConnectionCallback() {
                override fun onConnected() { connected.countDown() }
                override fun onConnectionFailed() { connected.countDown() }
            }, null)
            browser.connect()
        }
        try {
            assertTrue("Legacy browser timed out", connected.await(15, TimeUnit.SECONDS))
            assertTrue("Legacy browser rejected", browser.isConnected)
            fun children(parent: String): List<MediaBrowser.MediaItem> {
                val done = CountDownLatch(1)
                var items: List<MediaBrowser.MediaItem>? = null
                main.post {
                    browser.subscribe(parent, object : MediaBrowser.SubscriptionCallback() {
                        override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowser.MediaItem>) {
                            items = children; done.countDown()
                        }
                        override fun onError(parentId: String) { done.countDown() }
                    })
                }
                assertTrue("Children timed out: $parent", done.await(15, TimeUnit.SECONDS))
                return requireNotNull(items) { "Could not browse $parent" }
            }
            val root = children(browser.root)
            assertEquals(listOf("Current queue", "Saved queues"), root.map { it.description.title.toString() })
            assertTrue(root.all { it.isBrowsable })
            val queues = children(root[1].mediaId!!)
            if (queues.isNotEmpty()) {
                val songs = children(queues.first().mediaId!!)
                assertTrue(songs.size <= 1001) // 100k entries are grouped; no giant Binder response.
                assertTrue(songs.all { it.isBrowsable || it.isPlayable })
            }
            val controller = MediaController(context, browser.sessionToken)
            assertNotEquals(PlaybackState.STATE_PLAYING, controller.playbackState?.state)
            assertTrue(controller.playbackState!!.actions and PlaybackState.ACTION_PLAY_FROM_MEDIA_ID != 0L)
            assertTrue(controller.playbackState!!.actions and PlaybackState.ACTION_PLAY_FROM_SEARCH != 0L)
            val current = children(root[0].mediaId!!)
            if (current.isNotEmpty()) {
                val repository = PlayerRepository.get(context)
                val before = repository.state.value.book
                val done = CountDownLatch(1)
                // A car can prepare the Resume entry before asking to play it.
                main.post { controller.transportControls.prepareFromMediaId(current.first().mediaId!!, null); done.countDown() }
                assertTrue(done.await(5, TimeUnit.SECONDS))
                val deadline = System.currentTimeMillis() + 10000
                while (repository.state.value.book.revision == before.revision && System.currentTimeMillis() < deadline) Thread.sleep(20)
                assertTrue("Car selection did not reach the queue repository", repository.state.value.book.revision > before.revision)
                assertEquals(before.activeId, repository.state.value.book.activeId)
                assertEquals(before.active!!.currentId, repository.state.value.book.active!!.currentId)
                assertEquals(before.active!!.positionMs, repository.state.value.book.active!!.positionMs)
                assertEquals(before.queues.size, repository.state.value.book.queues.size)
                assertFalse(repository.state.value.playing)
            }
        } finally {
            val done = CountDownLatch(1)
            main.post { browser.disconnect(); done.countDown() }
            done.await(5, TimeUnit.SECONDS)
        }
    }
}

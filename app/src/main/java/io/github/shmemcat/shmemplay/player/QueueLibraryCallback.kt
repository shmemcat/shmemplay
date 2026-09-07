package io.github.shmemcat.shmemplay.player

import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.*

@androidx.annotation.OptIn(UnstableApi::class)
internal open class QueueLibraryCallback(
    private val repository: PlayerRepository,
    private val scope: CoroutineScope,
    private val headset: HeadsetSettings,
    private val packageName: String,
) : MediaLibrarySession.Callback {
    private val subscriptions = mutableSetOf<String>()

    suspend fun notifyQueueChanges(session: MediaLibrarySession, book: QueueBook) {
        for (parent in subscriptions.toList()) {
            val count = withContext(Dispatchers.Default) { QueueMediaLibrary.children(book, parent)?.size ?: 0 }
            session.notifyChildrenChanged(parent, count, null)
        }
    }
    internal fun canControl(session: MediaSession, controller: MediaSession.ControllerInfo): Boolean =
        controller.packageName == packageName || controller.isTrusted || session.isMediaNotificationController(controller) ||
            session.isAutoCompanionController(controller) || !headset.load().preventAutoplay

    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
        // Metadata readers must not be rejected merely because unsolicited playback is blocked.
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller).build()
    }

    override fun onPlayerCommandRequest(session: MediaSession, controller: MediaSession.ControllerInfo, playerCommand: Int): Int {
        return if (!canControl(session, controller) && playerCommand !in setOf(
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA, Player.COMMAND_GET_TRACKS)) SessionResult.RESULT_ERROR_PERMISSION_DENIED
            else SessionResult.RESULT_SUCCESS
    }

    override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?) =
        libraryFuture<MediaItem> {
            val root = if (params?.isRecent == true) QueueMediaLibrary.RECENT else QueueMediaLibrary.ROOT
            LibraryResult.ofItem(QueueMediaLibrary.folder(root, "Shmemplay"), LibraryParams.Builder()
                .setExtras(Bundle().apply { putBoolean("android.media.browse.SEARCH_SUPPORTED", true) }).build())
        }

    override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String) =
        libraryFuture<MediaItem> {
            QueueMediaLibrary.item(repository.awaitBook(), mediaId)?.let { LibraryResult.ofItem(it, null) }
                ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }

    override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
        page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = libraryFuture {
        if (page < 0 || pageSize <= 0) return@libraryFuture LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        val book = repository.awaitBook()
        val children = withContext(Dispatchers.Default) { QueueMediaLibrary.children(book, parentId) }
            ?: return@libraryFuture LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        LibraryResult.ofItemList(QueueMediaLibrary.page(children, page, pageSize), params)
    }

    override fun onSubscribe(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, params: LibraryParams?) =
        libraryFuture<Void> {
            val book = repository.awaitBook()
            val children = withContext(Dispatchers.Default) { QueueMediaLibrary.children(book, parentId) }
                ?: return@libraryFuture LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            subscriptions.add(parentId)
            session.notifyChildrenChanged(browser, parentId, children.size, params)
            LibraryResult.ofVoid()
        }

    override fun onSearch(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?) =
        libraryFuture<Void> {
            val matches = search(query)
            session.notifySearchResultChanged(browser, query, matches.size, params)
            LibraryResult.ofVoid()
        }

    override fun onGetSearchResult(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String,
        page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = libraryFuture {
        if (page < 0 || pageSize <= 0) return@libraryFuture LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        LibraryResult.ofItemList(QueueMediaLibrary.page(search(query), page, pageSize), params)
    }

    private suspend fun search(query: String): List<MediaItem> {
        val book = repository.awaitBook()
        return withContext(Dispatchers.Default) { QueueMediaLibrary.search(book, query) }
    }

    override fun onSetMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: List<MediaItem>,
        startIndex: Int, startPositionMs: Long): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
        check(canControl(session, controller)) { "External playback is blocked" }
        require(mediaItems.size == 1) { "Select a song or resume a saved queue" }
        require(startIndex == 0 || startIndex == C.INDEX_UNSET) { "Invalid start index" }
        val request = mediaItems.single()
        val book = repository.awaitBook()
        val query = request.requestMetadata.searchQuery
        val resolved = if (query != null) {
            if (query.isBlank()) book.active?.let { QueueMediaLibrary.item(book, QueueMediaLibrary.id(it.id)) }
            else withContext(Dispatchers.Default) { QueueMediaLibrary.search(book, query).firstOrNull() }
        } else QueueMediaLibrary.item(book, request.mediaId)
        require(resolved != null && resolved.mediaMetadata.isPlayable == true) { "No playable song found in saved queues" }
        // Never accept controller-supplied URIs. The player resolves the ID again when executing it.
        MediaSession.MediaItemsWithStartPosition(listOf(resolved), 0, startPositionMs)
    }

    override fun onPlaybackResumption(session: MediaSession, controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
        check(!isForPlayback || canControl(session, controller)) { "External playback is blocked" }
        val book = repository.awaitBook()
        val queue = book.active ?: error("Create a queue in Shmemplay on your phone first")
        val song = queue.current?.takeUnless { it.unavailable } ?: queue.entries.firstOrNull { !it.unavailable }
            ?: error("No saved song is available")
        val item = song.mediaItem(QueueMediaLibrary.id(queue.id))
        MediaSession.MediaItemsWithStartPosition(listOf(item), 0,
            if (queue.current?.unavailable == false) queue.positionMs else 0L)
    }

    private fun <T : Any> libraryFuture(action: suspend () -> LibraryResult<T>): ListenableFuture<LibraryResult<T>> = scope.future {
        try { action() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { LibraryResult.ofError<T>(SessionError.ERROR_IO) }
    }
}

internal fun <T> CoroutineScope.future(action: suspend () -> T): ListenableFuture<T> {
    val result = SettableFuture.create<T>()
    val job = launch {
        try { result.set(action()) }
        catch (cancelled: CancellationException) { result.cancel(false) }
        catch (failure: Exception) { result.setException(failure) }
    }
    job.invokeOnCompletion { if (it != null) result.cancel(false) }
    result.addListener({ if (result.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
    return result
}

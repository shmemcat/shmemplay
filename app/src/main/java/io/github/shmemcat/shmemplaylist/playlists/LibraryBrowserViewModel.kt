package io.github.shmemcat.shmemplaylist.playlists

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.shmemcat.shmemplaylist.domain.BatchAction
import io.github.shmemcat.shmemplaylist.domain.CanonicalAbsolutePrimaryPathPlan
import io.github.shmemcat.shmemplaylist.domain.M3uWriterV1
import io.github.shmemcat.shmemplaylist.domain.PlaylistRecipe
import io.github.shmemcat.shmemplaylist.domain.PlaylistRecipeEvaluatorV1
import io.github.shmemcat.shmemplaylist.domain.PlaylistRule
import io.github.shmemcat.shmemplaylist.domain.RecipeEvaluation
import io.github.shmemcat.shmemplaylist.domain.RecipeMatch
import io.github.shmemcat.shmemplaylist.operations.BatchPreview
import io.github.shmemcat.shmemplaylist.operations.MultiTargetOperationCoordinator
import io.github.shmemcat.shmemplaylist.operations.OperationOutcome
import io.github.shmemcat.shmemplaylist.operations.PlaylistDocumentStorageResolver
import io.github.shmemcat.shmemplaylist.operations.RoomMultiTargetOperationJournal
import io.github.shmemcat.shmemplaylist.persistence.AppDatabase
import io.github.shmemcat.shmemplaylist.settings.LibraryFolderSettings
import io.github.shmemcat.shmemplaylist.storage.ExactByteBackupRepository
import io.github.shmemcat.shmemplaylist.storage.TreePlaylistDocumentStorageFactory
import io.github.shmemcat.shmemplaylist.tracks.LibraryTrack
import io.github.shmemcat.shmemplaylist.tracks.MediaStoreMusicLibraryRepository
import io.github.shmemcat.shmemplaylist.tracks.MusicLibraryResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SavedPlaylistRecipe(
    val playlistName: String,
    val match: RecipeMatch,
    val rules: List<PlaylistRule>,
)

sealed interface BrowserMutationState {
    data object Idle : BrowserMutationState
    data class Working(val message: String) : BrowserMutationState
    data class ReconfirmationRequired(val preview: BatchPreview) : BrowserMutationState
    data class Result(val message: String, val operationId: String? = null) : BrowserMutationState
    data class Error(val message: String) : BrowserMutationState
}

data class LibraryBrowserState(
    val permissionRequired: Boolean = false,
    val loadingLibrary: Boolean = false,
    val loadingPlaylists: Boolean = false,
    val allTracks: List<LibraryTrack> = emptyList(),
    val availableFolderRoots: List<String> = emptyList(),
    val includedFolderRoots: Set<String> = emptySet(),
    val grant: PlaylistTreeGrantState = PlaylistTreeGrantState.NotConfigured,
    val playlistScan: PlaylistLibraryScan = PlaylistLibraryScan(emptyList(), emptyList()),
    val recipes: List<SavedPlaylistRecipe> = emptyList(),
    val mutation: BrowserMutationState = BrowserMutationState.Idle,
    val error: String? = null,
) {
    val tracks: List<LibraryTrack>
        get() = allTracks.filter { it.folderRoot in includedFolderRoots }
    val busy: Boolean get() = loadingLibrary || loadingPlaylists || mutation is BrowserMutationState.Working
}

class LibraryBrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val library = MediaStoreMusicLibraryRepository(application)
    private val folderSettings = LibraryFolderSettings(application)
    private val treeSettings = PlaylistTreeSettings(application)
    private val treeService = SafPlaylistTreeService(application)
    private val playlistScanner = PlaylistLibraryScanner(application)
    private val documentStorage = TreePlaylistDocumentStorageFactory(application)
    private val recipes = PlaylistRecipeStore(application)
    private val coordinator = MultiTargetOperationCoordinator(
        storageResolver = PlaylistDocumentStorageResolver(::openDiscoveredStorage),
        backups = ExactByteBackupRepository(application),
        journal = RoomMultiTargetOperationJournal(AppDatabase.get(application)),
    )
    private val mutableState = mutableStateOf(
        LibraryBrowserState(
            grant = treeSettings.revalidate(),
            recipes = recipes.load(),
        ),
    )
    private var pendingPreview: BatchPreview? = null
    private var libraryJob: Job? = null
    private var recoveryChecked = false
    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refreshLibrary()
        }
    }

    val state: State<LibraryBrowserState> = mutableState

    init {
        application.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaObserver,
        )
        refreshLibrary()
    }

    fun refreshLibrary() {
        libraryJob?.cancel()
        mutableState.value = mutableState.value.copy(loadingLibrary = true, error = null)
        libraryJob = viewModelScope.launch {
            when (val result = library.loadAll()) {
                MusicLibraryResult.PermissionRequired -> mutableState.value = mutableState.value.copy(
                    permissionRequired = true,
                    loadingLibrary = false,
                )
                is MusicLibraryResult.Failed -> mutableState.value = mutableState.value.copy(
                    loadingLibrary = false,
                    error = result.reason,
                )
                is MusicLibraryResult.Success -> {
                    val roots = result.tracks.map(LibraryTrack::folderRoot)
                        .distinct()
                        .sortedWith(String.CASE_INSENSITIVE_ORDER)
                    val stored = folderSettings.load()
                    val included = (stored ?: roots.toSet()).intersect(roots.toSet())
                    mutableState.value = mutableState.value.copy(
                        permissionRequired = false,
                        loadingLibrary = false,
                        allTracks = result.tracks,
                        availableFolderRoots = roots,
                        includedFolderRoots = included,
                        error = null,
                    )
                    refreshPlaylists()
                }
            }
        }
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(mediaObserver)
        super.onCleared()
    }

    fun onPlaylistTreeChanged() {
        mutableState.value = mutableState.value.copy(grant = treeSettings.revalidate())
        refreshPlaylists()
    }

    fun acceptTreeGrant(treeUri: android.net.Uri, grantFlags: Int) {
        recoveryChecked = false
        mutableState.value = mutableState.value.copy(
            grant = treeSettings.saveGrantedTree(treeUri, grantFlags),
        )
        refreshPlaylists()
    }

    fun setFolderIncluded(folder: String, included: Boolean) {
        val current = mutableState.value.includedFolderRoots
        val updated = if (included) current + folder else current - folder
        folderSettings.save(updated)
        mutableState.value = mutableState.value.copy(includedFolderRoots = updated)
        rescanLoadedPlaylists()
    }

    fun refreshPlaylists() {
        val grant = treeSettings.revalidate()
        mutableState.value = mutableState.value.copy(
            grant = grant,
            loadingPlaylists = grant is PlaylistTreeGrantState.Valid,
            error = null,
        )
        val treeUri = (grant as? PlaylistTreeGrantState.Valid)?.treeUri ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                treeService.discoverDirectChildren(treeUri).map { documents ->
                    playlistScanner.scan(documents, mutableState.value.tracks)
                }
            }
            result.fold(
                onSuccess = { scan ->
                    mutableState.value = mutableState.value.copy(
                        playlistScan = scan,
                        loadingPlaylists = false,
                    )
                    recoverIfNeeded()
                },
                onFailure = { failure -> mutableState.value = mutableState.value.copy(
                    loadingPlaylists = false,
                    error = failure.message ?: failure.javaClass.simpleName,
                ) },
            )
        }
    }

    fun createPlaylist(name: String, tracks: List<LibraryTrack>, recipe: SavedPlaylistRecipe? = null) {
        val paths = canonicalPaths(tracks) ?: return
        createPlaylistFromPaths(name, paths, recipe)
    }

    fun createRulePlaylist(name: String, match: RecipeMatch, rules: List<PlaylistRule>) {
        val recipe = SavedPlaylistRecipe(name, match, rules)
        val matching = evaluate(recipe) ?: return
        val paths = canonicalPaths(matching, allowEmpty = true) ?: return
        createPlaylistFromPaths(name, paths, recipe)
    }

    fun rerunRecipe(recipe: SavedPlaylistRecipe) {
        val existing = mutableState.value.playlistScan.playlists.firstOrNull {
            it.document.displayName.equals(recipe.playlistName, true) ||
                it.document.displayName.substringBeforeLast('.').equals(recipe.playlistName, true)
        } ?: run {
            setError("The recipe's playlist no longer exists.")
            return
        }
        val tracks = evaluate(recipe) ?: return
        val paths = canonicalPaths(tracks, allowEmpty = true) ?: return
        val grant = writableGrant() ?: return
        mutableState.value = mutableState.value.copy(
            mutation = BrowserMutationState.Working("Rerunning rules…"),
        )
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val storage = documentStorage.open(grant.treeUri, existing.document)
                    val before = storage.readExact()
                    val expected = M3uWriterV1.write(paths.map(CanonicalAbsolutePrimaryPathPlan::generate))
                    try {
                        storage.overwriteExact(expected)
                        check(storage.readExact().contentEquals(expected)) { "playlist-rerun-verification-failed" }
                    } catch (failure: Throwable) {
                        runCatching { storage.overwriteExact(before) }
                        throw failure
                    }
                }
            }
            result.fold(
                onSuccess = {
                    mutableState.value = mutableState.value.copy(
                        mutation = BrowserMutationState.Result("Rules rerun: ${tracks.size} songs written."),
                    )
                    refreshPlaylists()
                },
                onFailure = { setError(it.message ?: "Could not rerun rules.") },
            )
        }
    }

    fun applyMembership(
        action: BatchAction,
        playlists: List<PlaylistDocument>,
        tracks: List<LibraryTrack>,
    ) {
        val paths = canonicalPaths(tracks) ?: return
        val grant = writableGrant() ?: return
        mutableState.value = mutableState.value.copy(
            mutation = BrowserMutationState.Working("Checking playlist contents…"),
            error = null,
        )
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val preview = coordinator.previewMany(
                        action,
                        paths,
                        playlists.map { documentStorage.open(grant.treeUri, it) },
                    )
                    coordinator.confirm(preview)
                }
            }.fold(
                onSuccess = { confirmation ->
                    if (confirmation.requiresReconfirmation) {
                        pendingPreview = confirmation.preview
                        mutableState.value = mutableState.value.copy(
                            mutation = BrowserMutationState.ReconfirmationRequired(confirmation.preview),
                        )
                    } else {
                        applyConfirmed(confirmation.preview)
                    }
                },
                onFailure = { setError(it.message ?: "Could not prepare playlist changes.") },
            )
        }
    }

    fun confirmPendingMutation() {
        val preview = pendingPreview ?: return
        pendingPreview = null
        applyConfirmed(preview)
    }

    fun renamePlaylist(document: PlaylistDocument, name: String) =
        mutateDocument(
            message = "Renaming playlist…",
            block = { treeService.renamePlaylist(it.treeUri, document, name).getOrThrow() },
            onSuccess = { renamed ->
                recipes.onRename(document, renamed)
                mutableState.value = mutableState.value.copy(recipes = recipes.load())
            },
        )

    fun deletePlaylist(document: PlaylistDocument) =
        mutateDocument(
            message = "Deleting playlist…",
            block = { treeService.deletePlaylist(it.treeUri, document).getOrThrow() },
            onSuccess = {
                recipes.onDelete(document)
                mutableState.value = mutableState.value.copy(recipes = recipes.load())
            },
        )

    fun clearMutationMessage() {
        if (mutableState.value.mutation !is BrowserMutationState.Working) {
            mutableState.value = mutableState.value.copy(
                mutation = BrowserMutationState.Idle,
                error = null,
            )
        }
    }

    private fun createPlaylistFromPaths(
        name: String,
        paths: List<String>,
        recipe: SavedPlaylistRecipe?,
    ) {
        val grant = writableGrant() ?: return
        mutableState.value = mutableState.value.copy(
            mutation = BrowserMutationState.Working("Creating playlist…"),
            error = null,
        )
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                treeService.createPlaylist(
                    grant.treeUri,
                    name,
                    M3uWriterV1.write(paths.map(CanonicalAbsolutePrimaryPathPlan::generate)),
                )
            }
            result.fold(
                onSuccess = { created ->
                    recipe?.let {
                        val saved = it.copy(playlistName = created.displayName)
                        recipes.save(saved)
                        mutableState.value = mutableState.value.copy(recipes = recipes.load())
                    }
                    mutableState.value = mutableState.value.copy(
                        mutation = BrowserMutationState.Result(
                            "${created.displayName} created with ${paths.size} song${if (paths.size == 1) "" else "s"}.",
                        ),
                    )
                    refreshPlaylists()
                },
                onFailure = { setError(it.message ?: "Could not create playlist.") },
            )
        }
    }

    private fun applyConfirmed(preview: BatchPreview) {
        mutableState.value = mutableState.value.copy(
            mutation = BrowserMutationState.Working("Writing and verifying playlists…"),
        )
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                coordinator.apply(preview, "library-selection", "LIBRARY_BROWSER")
            }
            val result = when (outcome) {
                is OperationOutcome.Changed -> BrowserMutationState.Result(
                    "Verified ${outcome.occurrencesChanged} playlist entry change(s).",
                    outcome.operationId,
                )
                is OperationOutcome.Skipped -> BrowserMutationState.Result("No changes were needed.")
                is OperationOutcome.FailedSafe -> BrowserMutationState.Error("Operation failed safely: ${outcome.reason}")
                is OperationOutcome.RecoveryRequired -> BrowserMutationState.Error("Recovery required: ${outcome.reason}")
                is OperationOutcome.UndoRefused -> BrowserMutationState.Error("Operation refused: ${outcome.reason}")
            }
            mutableState.value = mutableState.value.copy(mutation = result)
            refreshPlaylists()
        }
    }

    private fun evaluate(recipe: SavedPlaylistRecipe): List<LibraryTrack>? {
        val state = mutableState.value
        val memberships = state.playlistScan.playlists.associate {
            it.document.uri.toString() to it.resolvedTrackIds
        }
        return when (val result = PlaylistRecipeEvaluatorV1.evaluate(
            state.tracks.mapTo(linkedSetOf(), LibraryTrack::stableId),
            memberships,
            PlaylistRecipe(recipe.playlistName, recipe.match, recipe.rules),
        )) {
            is RecipeEvaluation.Success -> state.tracks.filter { it.stableId in result.trackIdentities }
            is RecipeEvaluation.UnknownSources -> {
                setError("A source playlist could not be read. Refresh playlists and try again.")
                null
            }
        }
    }

    private fun canonicalPaths(tracks: List<LibraryTrack>, allowEmpty: Boolean = false): List<String>? {
        val paths = tracks.distinctBy(LibraryTrack::stableId).mapNotNull(LibraryTrack::canonicalPlaylistPath)
        if (paths.size != tracks.distinctBy(LibraryTrack::stableId).size) {
            setError("One or more selected songs do not have a safe primary-storage playlist path.")
            return null
        }
        if (paths.isEmpty() && !allowEmpty) {
            setError("Select at least one song.")
            return null
        }
        return paths
    }

    private fun writableGrant(): PlaylistTreeGrantState.Valid? {
        val grant = treeSettings.revalidate() as? PlaylistTreeGrantState.Valid
        if (grant == null || !grant.canWrite) {
            setError("Choose a writable playlist folder in Settings first.")
            return null
        }
        return grant
    }

    private fun <T> mutateDocument(
        message: String,
        block: suspend (PlaylistTreeGrantState.Valid) -> T,
        onSuccess: (T) -> Unit = {},
    ) {
        val grant = writableGrant() ?: return
        mutableState.value = mutableState.value.copy(mutation = BrowserMutationState.Working(message))
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { block(grant) } }.fold(
                onSuccess = { result ->
                    onSuccess(result)
                    mutableState.value = mutableState.value.copy(
                        mutation = BrowserMutationState.Result(message.removeSuffix("…") + " complete."),
                    )
                    refreshPlaylists()
                },
                onFailure = { setError(it.message ?: "Playlist operation failed.") },
            )
        }
    }

    private fun rescanLoadedPlaylists() {
        val documents = mutableState.value.playlistScan.playlists.map(PlaylistSnapshot::document)
        if (documents.isEmpty()) return
        viewModelScope.launch {
            val scan = withContext(Dispatchers.IO) { playlistScanner.scan(documents, mutableState.value.tracks) }
            mutableState.value = mutableState.value.copy(playlistScan = scan)
        }
    }

    private fun recoverIfNeeded() {
        if (recoveryChecked) return
        recoveryChecked = true
        viewModelScope.launch {
            val outcomes = withContext(Dispatchers.IO) { coordinator.recover() }
            val blocked = outcomes.firstOrNull {
                it.state == io.github.shmemcat.shmemplaylist.operations.JournalState.RECOVERY_REQUIRED
            }
            if (blocked != null) {
                setError("Recovery is required for operation ${blocked.operationId.take(8)} before new writes.")
            }
        }
    }

    private fun openDiscoveredStorage(documentIdentity: String) =
        (treeSettings.revalidate() as? PlaylistTreeGrantState.Valid)?.let { grant ->
            mutableState.value.playlistScan.playlists.asSequence()
                .map(PlaylistSnapshot::document)
                .firstOrNull {
                    documentStorage.open(grant.treeUri, it).handle.documentIdentity == documentIdentity
                }
                ?.let { documentStorage.open(grant.treeUri, it) }
        } ?: error("playlist-document-not-discovered")

    private fun setError(message: String) {
        mutableState.value = mutableState.value.copy(
            mutation = BrowserMutationState.Error(message),
            error = message,
            loadingLibrary = false,
            loadingPlaylists = false,
        )
    }
}

private class PlaylistRecipeStore(context: Context) {
    private val preferences = context.getSharedPreferences("playlist-recipes-v1", Context.MODE_PRIVATE)

    fun load(): List<SavedPlaylistRecipe> = preferences.getStringSet(KEY, emptySet()).orEmpty()
        .mapNotNull(::decode)
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, SavedPlaylistRecipe::playlistName))

    fun save(recipe: SavedPlaylistRecipe) {
        val current = load().filterNot { it.playlistName.equals(recipe.playlistName, true) } + recipe
        preferences.edit().putStringSet(KEY, current.mapTo(linkedSetOf(), ::encode)).apply()
    }

    fun onRename(before: PlaylistDocument, after: PlaylistDocument) {
        val updated = load().map { recipe ->
            recipe.copy(
                playlistName = if (recipe.playlistName.equals(before.displayName, true)) {
                    after.displayName
                } else {
                    recipe.playlistName
                },
                rules = recipe.rules.map { rule ->
                    if (rule.playlistIdentity == before.uri.toString()) {
                        rule.copy(playlistIdentity = after.uri.toString())
                    } else {
                        rule
                    }
                },
            )
        }
        preferences.edit().putStringSet(KEY, updated.mapTo(linkedSetOf(), ::encode)).apply()
    }

    fun onDelete(document: PlaylistDocument) {
        val updated = load().filterNot { it.playlistName.equals(document.displayName, true) }
        preferences.edit().putStringSet(KEY, updated.mapTo(linkedSetOf(), ::encode)).apply()
    }

    private fun encode(recipe: SavedPlaylistRecipe): String = listOf(
        android.net.Uri.encode(recipe.playlistName),
        recipe.match.name,
        recipe.rules.joinToString(",") {
            "${android.net.Uri.encode(it.playlistIdentity)}:${if (it.mustBePresent) 1 else 0}"
        },
    ).joinToString("|")

    private fun decode(value: String): SavedPlaylistRecipe? = runCatching {
        val parts = value.split('|', limit = 3)
        SavedPlaylistRecipe(
            android.net.Uri.decode(parts[0]),
            RecipeMatch.valueOf(parts[1]),
            parts[2].split(',').filter(String::isNotBlank).map { encoded ->
                val pair = encoded.split(':', limit = 2)
                PlaylistRule(android.net.Uri.decode(pair[0]), pair[1] == "1")
            },
        )
    }.getOrNull()

    private companion object { const val KEY = "recipes" }
}

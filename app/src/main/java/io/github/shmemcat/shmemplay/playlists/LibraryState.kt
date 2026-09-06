package io.github.shmemcat.shmemplay.playlists

import io.github.shmemcat.shmemplay.domain.PlaylistRule
import io.github.shmemcat.shmemplay.domain.RecipeMatch
import io.github.shmemcat.shmemplay.operations.BatchPreview
import io.github.shmemcat.shmemplay.tracks.LibraryTrack

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
    val localRecipes: List<LocalPlaylistRecipe> = emptyList(),
    val livePlaylists: List<PlaylistSnapshot> = emptyList(),
    val mutation: BrowserMutationState = BrowserMutationState.Idle,
    val error: String? = null,
) {
    val browserPlaylists: List<PlaylistSnapshot> get() = playlistScan.playlists + livePlaylists
    val tracks: List<LibraryTrack>
        get() = allTracks.filter { it.folderRoot in includedFolderRoots }
    val busy: Boolean get() = loadingLibrary || loadingPlaylists || mutation is BrowserMutationState.Working
}


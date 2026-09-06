# Shmemplaylist 2.0 — product proposal

Draft for discussion, September 5, 2026. Scope: a native Android music browser and playlist editor, with no playback. The interactive mock uses fictional metadata and session-only playlist changes; it does not read or write phone files.

Confirmed preferences: fixed snapshot playlists with explicit recipe reruns; edit the same playlist files GoneMAD reads; always use the dark Material 3 theme with the existing purple. Outgoing file sharing is removed from scope: Shmemplaylist receives shares from GoneMAD.

## Two entry points, one editor

- Launch from the phone: open the music library, restoring the last browsing location.
- Share an audio file into the app: preserve the existing resolve → playlist membership → add/remove flow. A successful browser edit stays in the browser; returning to the sending app applies only to share intake.
- Every song row includes artwork and a three-dot popup with a single Playlist action. This compact popup leaves the current screen visible and opens the classic membership screen. The same row component appears in search, albums, artists, genres, playlists, selection review, and rule previews.
- Long-press a song to enter selection. Pass selected songs together to the existing membership editor through Options → Playlist. There are no outgoing file-sharing controls.

## Browser and search

Five destinations: Songs, Albums, Artists, Genres, Playlists. Group names and song lists default to alphabetical order, with stable identity as the tie-breaker. Browsing alphabetical order does not rewrite a playlist's stored order. An optional stored-order view can come later.

Scrollable browser lists show a thin purple thumb over an equally thin inset track, with a wider invisible edge target for fingers. Pressing and dragging the target immediately expands the thumb. Songs, Albums, Artists, Genres, and their alphabetical detail lists leave the content stationary during the drag, preview the pending destination in a nearby purple bubble, and jump only when released. The index contains only buckets present in the current filtered list: `#` for numbers, `•` for symbols, A–Z for Latin text, and one short bucket per detected non-Latin Unicode script, including `KR`, `CN`, `TH`, `JP`, `CY`, `AR`, `HE`, `GR`, and `DV`, with deterministic labels for additional scripts. Playlist roots and playlist contents instead scroll continuously as the thumb moves and do not show a letter bubble.

The main browser top bar is a flex row using space-between: Shmemplaylist at the left and a settings gear at the right. The gear remains in the same position across Songs, Albums, Artists, Genres, and Playlists. Settings contains Music folders, Refresh music library, the existing Playlist folder control, and Rescan playlists. Music folders filter the MediaStore-backed library; Refresh music library requeries MediaStore and rebuilds Shmemplaylist's metadata, artwork, and search index. Observe MediaStore changes and refresh on foreground resume as well, while retaining the manual action for immediate recovery from stale metadata.

At the Playlists root, + Rules opens creation of a new rule-based playlist. Once a playlist is open, replace that creation action with a three-dot popup containing Rename playlist, Delete playlist, and Select all. Rename and Delete apply to the open playlist file and require the same provider validation and recovery discipline as other mutations. Delete asks for confirmation and removes only the playlist file, never its audio files. Select all enters selection mode with the current visible playlist results; an active search therefore limits what is selected.

The bottom search field belongs to the current list and persists through the browsing session. Navigation, opening menus, applying changes, and dismissing the keyboard retain its text. Only its X clears/closes it; editing the text can of course change the query. When the keyboard opens, resize and inset the app instead of panning its header and results off-screen, temporarily hide the category navigation, and retain a small gap between the search field and keyboard. A changed query resets browser lists to their first filtered result. Search matches title, artist, album, genre, and filename. Group screens retain groups whose name matches or whose songs match, and show the matching song count. A playlist-name match exposes that playlist's songs. Filtered results are the exact current-list scope for every Advanced select operation, including Select all, Deselect all, Select in-between, and Invert. This behavior is confirmed.

Match case-insensitively, normalize straight/curly apostrophes and optionally omit them, normalize Unicode and diacritics for matching, and treat punctuation as literal data rather than query syntax. Preserve original display text and paths. Split a multiword query into terms, all of which must match the combined searchable metadata. Examples to verify: `Don't`, `Don’t`, and `dont` find the same song; accent-insensitive matching; quotes, percent signs, and non-Latin scripts. Search normalization must NEVER determine file identity or merge similarly named tracks.

Show real album artwork with a consistent fallback. Missing tags receive explicit Unknown album/artist/genre groups. Keep unresolved playlist entries visible with their saved path; do not silently discard them or treat unknown membership as absent.

## Selection contract

Maintain a global set of stable track identities across navigation and search. Before a long press, show neither selection checkboxes nor the selection bar. A long press selects that song and reveals the bottom bar modeled on the supplied Musicolet screenshots: selected count/duration above Options, Advanced select, and Cancel. Selection appears as a check over the song artwork and a purple row highlight; subsequent taps toggle songs. Moving a finger to scroll cancels the pending long press. Keyboard users can use Shift+F10 on a song to enter selection in the mock.

Options opens a compact popup containing only Playlist. Advanced select opens a compact popup above the bottom bar with the four list operations below. Neither popup replaces the browsing screen. The range action is disabled when unavailable without additional helper copy. The summary can open selection review. Show both total selected and selected in the current filtered list. Closing search does not clear selection. Whenever the final globally selected song is deselected—by tapping it or through an Advanced select action—close the popup, leave selection mode, and hide the bottom bar. Cancel clears all selection and hides the bar.

- Select/add all in current list: union visible track IDs with the selection.
- Deselect all in current list: subtract visible track IDs only.
- Invert in current list: toggle each visible track ID, leaving everything outside it alone.
- Select between: with at least two selected songs in the current list, add the inclusive interval between the first and last selected songs in displayed order, as in the supplied screenshot. Selections outside the list do not count as endpoints. Disable with a clear reason if fewer than two are selected locally. Freeze the displayed ordering for the action.
- Cancel: clear the entire selection and leave selection mode.

In group views, list operations apply to the union of matching songs under displayed groups, not album/artist objects. Duplicate playlist occurrences count once in global track selection; the playlist detail can separately indicate occurrence counts. Removing a selected track from a playlist follows existing REMOVE_ALL behavior: all its matching occurrences are removed.

## Membership editor

Reuse the actual `MembershipList` structure in `ShmemplaylistApp.kt`: Add/Remove tabs, outlined Search playlists field, containing-first alphabetical ordering, checkbox rows, purple bold membership names with `(xN)` occurrence counts, and Clear/Add to N or Remove from N at the bottom. Remove shows only containing playlists. For multiple tracks, indicate how many selected tracks are present, including mixed membership. Bold a playlist only when it contains every selected track; partial membership remains regular weight and shows its present/selected count. Add inserts only selected tracks that are absent, never another occurrence of a track already present; Remove removes all selected matching occurrences. Browser selection and the editor's operation-target set are separate: opening one song's menu must not accidentally act on the global selection. Restore the browser's query and selection on return.

The existing source does not include a playlist-content preview control in this membership list; the revised mock preserves that list layout. Full playlist browsing supplies content inspection, and any later membership-list preview should be added deliberately rather than presented as already implemented.

### Create new playlist from the classic screen

Append a + icon and Create new playlist button as the final item beneath the playlist list, above the existing action row. Keep it available in both Add and Remove tabs, including when the playlist search has no matches. It opens a compact name dialog stating the number of selected songs. Cancel dismisses without changing anything. Create makes a new `.m3u` containing exactly the song(s) passed into this editor, deduplicated by stable identity, in deterministic selection order. A one-song row menu therefore creates a one-song playlist even if a larger global selection exists.

Save in the already selected playlist folder. Append `.m3u` once; reject blank names, reserved/path characters, and name collisions. Keep the membership editor open after success and refresh membership so the newly created playlist appears as containing those songs. Do not clear browser search or selection. Creation is independent of checked destination playlists and does not modify them.

For native implementation, use `M3uWriterV1` with canonical resolved paths (UTF-8, LF, trailing newline, no invented EXTINF header) and the existing provider grant checks. `PlaylistDocumentStorage` currently supports existing-document read/overwrite, so add a verified document-creation path rather than assuming it already creates playlists. Revalidate the name and folder before creation, reread and validate the new bytes, report failure accurately, and handle partial creation/recovery. The mock generates an in-memory M3U string and membership result; it does not write a phone file.

Apply plans each target playlist once, then uses the existing reread, backup, journal, verification, rollback, and recovery machinery. Avoid one independent write per selected song. Keep the existing material-change reconfirmation behavior. Report changed/skipped/failed/recovery-required targets accurately and refresh affected browser views after completion.

## Playlist recipe builder

A bottom sheet provides a name, a rule tree, a live song preview/count, and Create playlist. Rules operate on unique resolved track identities from the accessible library. Each leaf is `is in playlist P` or `is not in playlist P`. A group matches ALL or ANY children; nested groups allow combinations such as `(A OR B) AND NOT C`.

The Match dropdown and per-rule membership/playlist dropdowns are the complete rule interface. Do not add shortcut buttons for common combinations. They can still express intersection as `ALL [in A, in B]`, difference as `ALL [in A, not in B]`, neither as `ALL [not in A, not in B]` over the whole accessible library, and union as `ANY [in A, in B]`.

Preview results support the standard song menu. The first delivery creates a static M3U/M3U8 snapshot in alphabetical order, deduplicated by identity, and saves the recipe locally for explicit reruns. This snapshot-and-rerun behavior is confirmed by the user. Name collisions require a new name or an explicit replace flow; never silently overwrite. Reread source playlists before creation and re-preview if they changed. Unknown or unreadable source membership blocks creation; it cannot safely be interpreted as a negative match. Distinguish an empty valid result from a failed evaluation.

The mock implements editable flat ALL/ANY rules using dropdowns. Nested groups, saved-recipe reruns, and native file writes are planned, not simulated as working features. Automatic updates are outside the agreed snapshot scope.

## Implementation grounded in the current repository

Keep Kotlin/Compose and the current app/domain separation. `MainActivity.kt` currently drives intake, membership operations, and auto-return; give launcher browsing its own navigation state and make return behavior depend on the entry point. `ShmemplaylistApp.kt` supplies the existing membership surface; extract/reuse it rather than replacing the working shared-file route.

`AudioLibraryRepository.kt` currently queries candidates for a shared file, capped at 100; this is not a library index. Add a separate paged library repository with album/artist/genre/artwork metadata and indexed search. Keep the bounded identity-resolution path. Repository changes and lifecycle resumption invalidate affected library data; preserve selection using identity and flag disappeared tracks.

`BatchOperationPlannerV1` currently handles one canonical path over multiple targets. Introduce a separate many-track planner and rule evaluator in `domain`, keeping existing V1 contracts intact. Reuse the operation coordinator, storage permissions, exact-byte backups, journal, and recovery facilities. The new playlist creation path requires its own verified creation/cleanup behavior.

Use persisted URI permissions already granted for playlist files and request only the audio access needed for browsing. Preserve incoming GoneMAD sharing; do not add an outgoing file-share path. Verify Android API and provider behavior against official documentation during implementation, then on the user's phone; this planning draft is based on repository code, not a fresh platform compatibility audit.

## Delivery sequence and acceptance

1. **Agree on this interaction model.** Exercise navigation with a live query, long-press entry, compact popups, mixed visible/hidden selections, per-song editor entry, and rule previews. Snapshot recipes, incoming-only sharing, existing playlist files, and the system-aware purple theme are confirmed.
2. **Library foundation.** Launcher browser, indexed metadata, artwork, alphabetical grouping, resilient search. Verify denied/revoked permission, unknown tags, large-library responsiveness, apostrophes, and media refresh.
3. **Classic editor and creation.** Reach the existing editor from every song context; verify single-file intake regression and Create new playlist from one or many songs, naming errors/collisions, creation verification, and refresh.
4. **Selection and bulk writes.** Verify visible-list set operations, range endpoints, hidden selections, mixed membership, duplicate occurrences, and one transactional rewrite per target. Test changed files, partial failure, recovery, undo, and process recreation.
5. **Recipes.** Pure set-algebra tests including nested ALL/ANY/NOT, unknown sources, empty results, source changes, duplicate names, and deterministic ordering; verify resulting files in GoneMAD.

Keep the current share flow passing throughout. Prototype approval establishes behavior and layout; it does not establish Android storage, permission, performance, or GoneMAD refresh compatibility.

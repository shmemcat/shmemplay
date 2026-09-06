# shmemplay — music player interaction specification

Status: proposed next development phase, September 5, 2026. This document captures the user's Musicolet references and requested behavior. It does not describe playback as already implemented. The screenshots are preserved unmodified in [player-references](player-references/).

This extends the [2.0 browser plan](browser-2-plan.md). For the player phase it supersedes that plan's no-playback restriction, single-action song popup, incoming-share entry point, five-destination navigation, and snapshot-only recipe creation. Existing direct M3U editing, selection, search normalization, dark purple appearance, compact lists, and fast scrolling remain foundations.

## Queue invariants

- Every queue is an independent, persistent snapshot. Source playlist changes, live-rule reevaluation, searching, and library refresh must never silently replace, append, remove, or reorder its entries.
- The viewed queue and active playback queue are separate. Choosing a queue in the picker changes the viewed queue only. Audio continues from the active queue.
- An inactive queue retains its last current entry and playback position. Browsing it shows that entry with a distinct inactive marker. Pressing its Resume button activates it at the saved position; tapping an unfiltered entry activates it at that entry. Tapping a filtered result instead creates a new queue, as specified below.
- Now Playing, notification controls, headset controls, and transport actions refer to the active playback queue, regardless of the viewed queue.
- Each queue contains at most one entry for a given audio file. The same file may belong to several independent queues; separate files with identical metadata are not duplicates. Queue creation and mutations enforce this rule using resolved track/file identity.
- Reordering or sorting a queue preserves the identity and elapsed position of its current entry. Positions must not be stored as an index alone: moving another song before the current entry changes its index without changing what is playing.
- Queue edits never alter the source M3U. Save as playlist explicitly creates an M3U using the existing naming and write-verification flow.
- Keep unavailable queue entries recognizable when audio files disappear. Mark them unavailable and handle playback failure without silently deleting entries. Metadata/artwork refresh is allowed; the snapshot contract concerns membership and order.
- Preserve queue names, ordering, current entries, elapsed positions, and playback settings across process recreation. Restoring saved state must not itself start audio.
- Song-end, queue-end, and shuffle settings belong to each queue. A new queue copies the current settings of the most recently created existing queue, then owns an independent copy. Use creation chronology, not picker order, most recently viewed queue, or active playback, to choose that template. Use app defaults when no prior queue exists. Later edits to either queue's settings do not change the other.

## Navigation and appearance

The app's display name is **shmemplay**. Keep the existing Android application identity and persisted storage names so the rename remains an update to the installed app, retaining its data and folder grants.

The repository and Gradle project are now `shmemplay`, and source packages use `io.github.shmemcat.shmemplay`. Keep `io.github.shmemcat.shmemplaylist` as the Android application ID, preserve the previous launcher component through an activity alias, and retain `shmemplaylist.db`, existing preference keys, and the companion-test playlist's original filename. These remaining names are compatibility contracts, not unfinished branding changes.

[Reference 1](player-references/01-now-playing.png), as revised by the user, defines seven icon destinations in this order: Queues, Now Playing, All Songs, Albums, Artists, Genres, Playlists. All Songs replaces the reference's dedicated Search destination and moves immediately to the right of Now Playing. Settings is accessed through the top-right gear; omit the redundant bottom settings/three-dot destination. There is no dedicated global Search screen. Contextual song and queue three-dot menus remain.

Strongly follow Musicolet's screenshot proportions, padding, typography hierarchy, artwork placement, icon prominence, dividers, and popup layout. These are the primary visual references, including the spacing within queue rows and the generous gutters in song menus and settings. Retain shmemplay's always-dark purple palette. The reference's teal and artwork-colored background are not new theme requirements. Balance readable rows with the previously requested dense browsing; establish the final dimensions in the mock at phone proportions rather than copying screenshot pixels as Android dp.

Keep adequately sized icons and touch targets, system-bar clearance, the small gap above the first row, keyboard resizing/insets, and the search-to-keyboard gap. Category navigation remains consistent between parent and detail views, with the existing keyboard-visible exception unless revised. Icon-only navigation follows the reference, with accessible labels. The user chose no mini-player: use the Now Playing tab. Albums default to a three-column artwork grid with a list/grid toggle. Virtualized row sizing and fast-scroll index conversion use that same column count.

The user explicitly wants to retain the mock's rounded outline icon style throughout the finished app. Preserve its stroke weight, rounded ends, and visual character when implementing native controls. The launcher icon draws from the user's Crayon reference: a lavender record, warm yellow center, charcoal outlines, and rounded groove strokes. The native vector and [SVG design source](design/shmemplay-icon.svg) share the same geometry and colors.

The user explicitly approved the playlist membership editor as an in-app modal, reachable from any song and bulk-selection menu without replacing the underlying screen. Keep Add/Remove, all-selected emphasis, missing-only additions, and Create new playlist within this modal. The previous incoming-share workflow is retired; remove the Android share-target registration and omit share-intake UI from the player. Outgoing sharing from a song's options remains part of the agreed player scope. Preserve the existing M3U storage and write-verification logic when retiring intake-specific code.

One persistent search query follows the user through All Songs, Albums, Artists, Genres, Playlists, and their detail views. Each view applies it to its own list, and the search field retains the text until the user clears it with X. Switching categories or opening Settings must not clear the query. Settings retains its own controls rather than filtering them with the music query. Now Playing and Queues do not apply the shared library query; returning to the library restores it. Queues have their own local search. Existing advanced selection remains scoped to the visible filtered list.

Preserve the current membership editor's ordering: playlists containing **all** selected songs appear first and are bold. Playlists containing only some selected songs follow without bold emphasis, then playlists containing none; alphabetize names within each group. For a single selected song, every containing playlist is bold and at the top. Recompute membership after successful changes, and add only missing songs without introducing duplicates.

## Now Playing

The Now Playing screen must never scroll. Fit artwork, track details, all controls, and persistent navigation within the available screen height, respecting system insets. Shrink the square artwork first when space is tight, preserving readable labels and usable control targets. This is a confirmed implementation requirement; the current mock has not been revised for it.

Use a prominent square artwork area, title, artist, and album, followed by the controls from [reference 1](player-references/01-now-playing.png):

1. Song information, Add/remove from playlists, song options, song-end/queue-end behavior, shuffle.
2. Elapsed time, draggable seek bar, total duration.
3. Previous/restart, rewind, play/pause, fast-forward, next.

Previous restarts the current song after more than five seconds; at five seconds or less it goes to the previous entry. Rewind/fast-forward intervals and tap/hold behavior are not specified yet.

Remove the crossed-out favorite, volume shortcut, and equalizer controls. Apply the favorite exclusion consistently to the hearts present in the song-menu references. Shuffle is a direct toggle with brief status feedback, as in [reference 4](player-references/04-shuffle.png); no shuffle dialog is requested.

### Confirmed shuffle behavior

Turning shuffle on makes the current song the first entry and shuffles every other song below it, including songs previously above it. The current audio, elapsed position, and play/pause state remain uninterrupted. Example: `[A, B (playing), C, D]` may become `[B (playing), D, A, C]`. The queue list displays this actual playback order.

Retain the unshuffled order separately. Turning shuffle off restores that order without changing the identity or elapsed time of the song playing at that moment, even if playback has advanced since shuffle was enabled. It must not jump back to the song that originally anchored shuffle. Persist both order state and the shuffle setting with the queue. Explicit additions, removals, and rearrangements while shuffled must be reconciled with the retained order; unshuffle must never resurrect removed songs or discard additions.

If a new queue inherits shuffle enabled, first capture its source order as the unshuffled order, then anchor the chosen start song at the top and shuffle the rest. Existing queues retain their own order and settings.

## Song-end and queue-end behavior

Provide a scrollable popup with the independent choices visible in [reference 2](player-references/02-repeat-controls.png) and [reference 3](player-references/03-next-queue-controls.png).

When a song ends, choose one:

- Stop there.
- Load the next song and pause.
- Play the next song.
- Repeat the same song.

When the queue ends, optional preparation includes resetting its current entry to the first and reshuffling if shuffle is enabled. Then choose one:

- Stop there.
- Jump to the next queue.
- Repeat the same queue.

Jumping to the next queue exposes two independent options: resume the destination from its saved position, and wrap between the last and first queue. Queue-picker order defines next/previous queue. When wrapping is disabled, finishing the final queue stops playback. The queue being viewed is not an implicit destination for automatic transitions.

Song-repeat takes precedence over advancing the queue. Reaching the end while configured to load-and-pause must not accidentally autoplay a destination. Model and test this behavior explicitly rather than composing contradictory repeat flags.

Reference 3 visibly demonstrates the next-queue configuration and extra options. The circular-arrow button in reference 2 has no text label, so its exact action cannot be inferred from these still images. Do not implement an immediate reset or transition based on that icon alone. The Simple-mode contents are also not shown.

## Queue screen and picker

[References 6](player-references/06-queue.png), [7](player-references/07-queue-picker.png), [8](player-references/08-rename-queue.png), and [9](player-references/09-inactive-queue.png) define the workflow:

- Queue name/number dropdown at the top and a remove-queue action.
- Resume/play control, sorting, current entry number / total count, total duration, Save as playlist, and queue options.
- Dense artwork rows with title, artist/album, duration, three-dot menu, and dedicated reorder handles. Long-press selection remains separate from dragging a handle.
- A clear active-entry highlight, plus a distinguishable saved-position marker when viewing an inactive queue. Do not dim inactive queues enough to hurt readability.
- Local queue filtering and continuous fast scrolling. Filtering never removes entries from the actual queue.
- A compact queue-picker popup with reorder handles, viewed selection, separate active-playback indicator, rename, and remove. Rename opens a small naming dialog.

Queue deletion removes only the saved queue. Deleting the active queue continues playback in the next queue in picker order; deleting an inactive queue leaves active playback alone. No source playlist or audio deletion is implied. For the mock, resume the successor at its saved position; if there is no following playable queue, stop. The final-queue boundary remains a refinable detail rather than silently wrapping. Removing the currently playing song from its queue immediately advances playback to the next entry in playback order, rather than finishing the removed song. Removing any other entry leaves playback uninterrupted. Handle the no-successor/empty-queue boundary explicitly in the playback policy tests.

Tapping a song in any library song list, filtered or unfiltered, creates a fresh snapshot queue from that complete displayed list in its current order, starting at the chosen song. Do not reuse a prior matching queue, even when browsing the same source again. Existing queues remain available. Collapse repeated references to the same audio file while preserving first appearance order; if the tapped row was a duplicate source reference, start at its retained unique entry.

The user explicitly chose the same behavior for local queue search: tapping a song while a queue filter is applied creates and activates a new snapshot queue from all matching entries in their displayed order, starting at the tapped song. The original queue's membership and order remain unchanged, and its last playback position is retained. Tapping an entry in an unfiltered queue activates that existing queue at the entry; the Resume control always resumes the existing viewed queue, even while its list is filtered.

Requested queue operations include appending music, inserting after the active entry, choosing another queue, creating a new queue, sorting, manual rearrangement, removing entries, renaming/reordering/deleting queues, and saving a queue as a file playlist. Exact sort choices remain open. Queue duplicate handling is confirmed: reposition the existing entry at the requested destination instead of adding another copy.

- Play after current song moves an existing entry immediately after the active entry, or inserts it there if absent. Find the insertion point relative to the current entry after removing the moved entry from its old position. Example: `[A, B (playing), C]` becomes `[B (playing), A, C]` when A is requested next. B's elapsed time and playback state are preserved.
- Add/append to a queue moves an existing entry to the end, or appends it if absent. Adding from another queue leaves the source queue intact.
- Repositioning the current entry itself must not restart its audio. Play-after-current applied to that same entry is a no-op because the queue cannot contain a second copy of it.
- Bulk insertion preserves the requested tracks' relative order, moves existing entries and inserts missing ones, and retains one entry per file. Applying a next block excludes the current entry from that block.
- M3U additions keep their established missing-only behavior. An existing M3U entry stays in place; the queue repositioning policy does not change playlist-file editing.

## Song options and information

Replace the current one-item song popup with a compact scrollable modal using the hierarchy in [reference 11](player-references/11-library-song-menu.png). Keep the background screen visible. Common actions:

- Song info.
- Play after current song (targets active playback).
- Add to currently playing queue (append).
- Add to a queue (choose destination or create one).
- Add/remove from playlists (opens the existing membership editor).
- Edit tags.
- Share audio file.
- Delete audio file permanently.

Within a queue, [reference 10](player-references/10-queue-song-menu.png) additionally supplies Remove from this queue and Stop after this song. Removal targets the viewed queue entry; play-next targets active playback. Stop-after is associated with an entry in a specific queue, not copies of the same song in other queues. Its one-shot behavior must be documented in the implementation.

Remove the crossed-out Preview, Move to folder, Audio cutter, Set as ringtone, and speed/pitch actions. Keep bulk selection and extend its Options popup with applicable queue actions alongside the existing playlist editor.

The uncrossed Edit tags, Share, and Delete permanently actions are included in the requested player scope. This extends the browser-only sharing decision. Adding these features does not authorize deleting or editing any actual user file during development: file changes require deliberate actions in the finished UI. Permanent deletion must clearly identify the audio file and distinguish it from queue/playlist removal. Tag editing needs an explicit save step, format support, appropriate Android write access, and preservation of unrelated tags/artwork. It does not rename or move audio files.

[Reference 5](player-references/05-song-info.png) supplies a scrollable information modal with artwork, filename, storage location, title, album, artist, album artist, composer, genre, embedded lyrics, lyricist, track/disc numbers, and year. Show absent metadata honestly. Album, artist, and genre links open browser destinations; additional linked metadata and folder/artwork actions require their own supported destinations. Edit tags and Done appear at the bottom. Lyric display reads embedded content; online lyric retrieval is not requested.

## Headset, Bluetooth, and speakers

[Reference 12](player-references/12-headset-bluetooth.png) adds these settings to the player scope:

- Pause when the active headset/speaker/Bluetooth audio connection disconnects.
- Resume only if playback was paused by a prior disconnection.
- Separate resume-on-connect switches for Bluetooth and wired headset/speaker.
- Prevent unwanted autoplay, with exact choices still to be specified because the screenshot shows only the settings entry.
- Configurable headset-button double-press and triple-press actions, initially proposed as Next and Previous as pictured.
- The pictured four-or-more-press fast-forward behavior and an explanation of recognized press timing, where the device delivers the necessary events.

Proposed defaults: pause on disconnect on, both auto-resume switches off. Track a pause reason and the relevant playback session so reconnecting cannot undo an intervening manual pause, explicit stop, queue switch, or interruption. Connecting an unrelated Bluetooth device must not trigger playback. Interpret route readiness as audio availability rather than any device connection.

Use Media3/Android media-session controls and audio-focus handling. Test calls, another app taking focus, unplugging, reconnecting, and media-button commands on the user's hardware. Raw multi-press timing and device-translated next/previous commands require separate handling to avoid advancing twice. Device support must be demonstrated rather than assumed from the screenshot. Connection-based resume must obey Android's service and audio-focus restrictions; it cannot be promised from every stopped/force-stopped state.

Official implementation references: [MediaSession controls](https://developer.android.com/media/media3/session/control-playback), [audio focus](https://developer.android.com/media/optimize/audio-focus), and [background playback](https://developer.android.com/media/media3/session/background-playback).

## File playlists, live playlists, and synchronization

Recipe creation gains Snapshot / Live. Snapshot writes an M3U and retains the optional rerun recipe. Live stores a local rule definition and derives current membership from the source M3Us. Show only known-valid results as current; unreadable/missing sources are not empty sets. Coalesce changes during manual file copying and expose stale/error status rather than silently producing incorrect negative-rule results.

The user confirmed that file and live playlists share one list. Mark live items with a small Live label or distinct playlist icon, and offer All / Files / Live filtering. Keep artwork out of this category.

The user confirmed nested AND/OR rule groups. Begin with a simple group and offer Add rule and Add group, with membership or non-membership conditions inside each group. For example, `(in Road Trip OR in Favorites) AND not in Christmas` must be expressible directly. Both Snapshot and Live use this same rule builder, with the output type chosen explicitly when creating the playlist. A snapshot creates an M3U; a live definition stays local and reevaluates its source M3Us.

Source identity must survive supported replacement/rename workflows or request explicit repair. Refresh after in-app M3U edits, on reopening/resuming, and when external changes are detected. Cache memberships and recompute affected recipes instead of rescanning all files on each UI interaction.

Playback from a live playlist always creates a snapshot queue. There is no linked/live-queue mode: the user explicitly rejected automatic queue membership updates. Optional reshuffling and queue-end behavior are user-configured playback rules, not source synchronization.

Existing M3Us remain the interchange with Shmembee/MusicBee. Local live definitions and queues do not automatically sync to MusicBee. Saving their contents as an M3U explicitly makes that snapshot available to the existing workflow.

## Development sequence and acceptance

1. Establish the queue model and move shared library/playlist state out of the browser-only ViewModel. Persist queue entries, positions, viewed/active identities, and policy settings. Pure tests cover reorder, removal, duplicate-free creation, moving existing entries on insertion, preserving playback when an earlier entry moves after the current one, and restoration.
2. Add Media3 service playback, media-session controls, audio focus, notification/lock-screen support, and baseline headset disconnection behavior. Verify real listening with the screen off and UI dismissed.
3. Build the revised navigation with All Songs after Now Playing, persistent library search, Now Playing, queue screen/picker, expanded menus, and playlist-editor integration. Demonstrate switching viewed queues without touching audio, resuming an inactive queue at its saved place, and creating a new queue from filtered results without changing the source queue. Verify the library query persists across categories and detail views while remaining separate from queue search.
4. Implement and test the full song-end/queue-end policy, shuffle, headset preferences, and queue editing. Test current-song anchoring at the top, restoring original order after playback advances, per-queue settings inheritance from creation chronology, and immediate advance after removing the playing entry. Use a large queue comparable to the 23,042-entry reference. Sorting and restoring must preserve the current entry and position.
5. Add live recipe persistence/dependencies and external-file refresh. Editing a source M3U must update the live playlist and leave existing queues unchanged.
6. Complete tag editing, outgoing file sharing/deletion, and metadata details with explicit file-operation flows and format/device validation. Retire the obsolete share-intake screen and its launch routing while preserving the shared playlist backend. Run regressions for in-app modal entry, all-selected playlist emphasis, missing-only additions, selection, keyboard layout, and scroll restoration; verify shmemplay is no longer offered as an incoming audio-share destination.

## Large-library performance

The user requires responsive scrolling with at least **30,000 songs**, including large queues and filtered results. Virtualize every large list and any album grid: compose/layout only the viewport and a bounded nearby range, with stable item keys. Queue row keys identify stable entries within their queue, never changing list indices, so rearrangement preserves row and playback identity.

Repository inspection confirms the current browser already uses `LazyColumn` and keyed rows in `LibraryBrowserApp.kt`. Artwork is loaded on an IO dispatcher. Virtualization alone is not the remaining performance work: the current composable layer also computes list-wide filters, grouping, counts, and indexes, and the artwork fallback can decode a full embedded image into a cache bounded by image count. These are inspection findings to profile, not measured explanations of every scroll delay.

- Build reusable library indexes and compute large projections off the UI thread; cancel outdated search work and publish only the latest query's results. Selection and scrolling must not trigger full sorting or regrouping.
- Keep selection membership checks cheap and update counts/durations without unnecessary full-library passes on each tap. Bulk operations should not cause thousands of immediate row compositions.
- Downsample embedded artwork to its displayed size, bound cache memory by bytes, avoid repeated failed decodes, and cancel or limit offscreen artwork work during fast flings. Do not decode full-size covers for tiny rows.
- Cache alphabetical/script jump targets for each list projection. Dragging the scrollbar must not rebuild the 30,000-track index on every pointer event.
- Persist queues outside UI saved-state bundles and restore them without blocking gestures. Use stable occurrence identity for sorting, filtering, playback position, and manual reordering.
- Validate on the phone with a release/profileable build and a representative 30,000-song dataset. Measure frame timing, peak artwork memory, search responsiveness, selection latency, fast-scroll jumps, and back-navigation restoration. A fast mock is not evidence that native large-library performance is solved.

The mock should also use virtualized rows for large sample libraries and queues so the interactions can be exercised at the intended scale. Native device profiling remains a separate acceptance step.

The first interactive mock now covers the agreed navigation, queue browsing and playback simulation, selection, playlist membership, album grid/list views, nested Snapshot/Live creation, and headset settings. It uses generated sample music; playlist/file operations affect only the sample state. Audio, Android folder/share dialogs, Bluetooth events, and real M3U writes are not implemented in the mock.

Browser checks passed for a 30,000-song library with fewer than 20 song rows rendered during normal and deep scrolling; dynamic fast-scroll previews; persistent apostrophe-tolerant search; filtered bulk selection; fresh queues from library and queue-search results; move-without-duplicate insertion; shuffle anchoring and order restoration; inactive queue browsing; active-queue deletion advancing to its successor; settings inheritance; Live updates with unchanged Snapshots and existing queues; all-selected playlist emphasis; and duplicate-free M3U-style additions. Layouts and scrollable dialogs were checked at 360px and 320px. These checks validate the mock, not Android performance or playback.

Native playback implementation and a new APK are not produced by this specification/mock update. All ten pre-mock design questions are answered. Smaller boundary details explicitly noted above can be refined during review and implementation.

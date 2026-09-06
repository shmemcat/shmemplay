# Native player implementation — September 6, 2026

This is the current implementation status for the approved [music player plan](music-player-plan.md). The original [handoff](START-HERE.md) describes the pre-player baseline. The app is native Kotlin/Compose with Media3; the HTML mock is only a reference.

## Implemented

- Independent persistent snapshot queues, separate viewed/active queues, per-queue current file identity and elapsed position, rename/delete/picker reordering, and settings inheritance by creation chronology.
- Duplicate-free creation, move-existing append/play-next, stable-current sorting/reordering, removal of the playing entry, shuffle anchored on the current entry, and restoration after additions/removals and playback advancement.
- Explicit song-end/queue-end policy, next-queue resume/wrap options, five-second previous threshold, and a queue-specific one-shot stop-after marker.
- Media3 1.11.0 ExoPlayer in a MediaSessionService, audio focus, background playback wiring, media notification/session controls, pause on noisy-route disconnect, opt-in reconnect, and configurable raw headset double/triple presses.
- Seven native outline-icon tabs, no mini-player, artwork that shrinks in a non-scrolling Now Playing column, three-column album grid/list toggle, independent persistent library/queue search fields, queue search snapshot creation, and bulk queue actions.
- In-app playlist membership modal preserving Add/Remove, playlist-name search, all-selected ordering/emphasis, missing-only additions, and Create new playlist without leaving the editor.
- Queue picker, sort choices, drag handles, queue snapshot export through the existing verified M3U creation path, expanded song menus, metadata links to album/artist/genre, and outgoing sharing via a deliberate Android chooser action.
- Nested AND/OR membership/non-membership rules with Snapshot/Live output. Live items share the playlist category with All/Files/Live filters. Live definitions can be renamed, removed, and edited to repair source references. Missing/unreadable sources are errors, not empty sets. Creation rereads sources and rejects detected concurrent changes. Provider observation and foreground/manual refresh trigger reevaluation; unchanged file snapshots and unaffected live results are cached.
- Song metadata details and embedded MP3 lyrics; explicit MP3 tag Save with Android write consent, exact backup, journal, reread verification, rollback and an interrupted-edit recovery control in Settings. Permanent audio deletion uses a separate identifying confirmation and Android's per-file delete request.
- Large-list projections run off the composition thread, lists and album rows are virtualized, and embedded artwork decoding is downsampled with a byte-bounded cache and bounded failed-lookup caching.

## Compatibility and storage

The Android application ID, launcher alias, existing Room database/version, preferences, M3U formats, and transactional playlist backend retain their existing compatibility names. The new queue store is separate: `player-queues-v1.bin` plus `player-positions-v1.json` in private app storage. Queue checkpoints are tied to the structural revision so an old checkpoint cannot undo a later seek after a crash. Atomic writes preserve the old file on failure. An unreadable queue store blocks edits and retains its original bytes.

Local nested recipes use `nested-playlist-recipes-v1` preferences. They do not sync automatically with MusicBee/Shmembee. Queue playback checks the saved filename/path when opening MediaStore audio so a reused MediaStore ID cannot silently select a different file.

The source map now includes:

- `domain/.../player/Queues.kt`, `QueueCodec.kt`, `DisconnectResumeGuard.kt`: pure queue/policy/persistence contracts.
- `app/.../player/PlayerRepository.kt`: shared serialized queue state and checkpoints.
- `app/.../player/PlaybackService.kt`: native audio and media session.
- `app/.../playlists/LibraryState.kt`: library/playlist state extracted from the browser ViewModel implementation.
- `app/.../playlists/LocalPlaylistRecipes.kt` and `domain/.../NestedPlaylistRules.kt`: local recipes and nested evaluation.
- `app/.../ui/PlayerScreens.kt`, `SongDialogs.kt`, `NestedRulesDialog.kt`, `AudioFileDialogs.kt`: player surfaces.
- `domain/.../Mp3Tags.kt`, `app/.../player/AudioFileEdits.kt`: supported tag editing and recovery.
- `docs/licenses/lucide-LICENSE`: attribution for the native outline icon geometry.

## Explicit implementation defaults and limits

These defaults are provisional implementation choices, not additional confirmed user decisions:

- Rewind/forward are ten-second taps. Raw multi-press grouping waits 400 ms after the last click. Four or more clicks seek forward ten seconds. Devices that translate buttons to Next/Previous use those commands directly.
- Sort options are Title, Artist, Album, Duration, and Reverse. A handle commits the requested move on release. Explicit rearrangement while shuffled establishes the restored order; additions/removals reconcile both orders.
- New queues created through Add to a queue preserve active playback; song taps create and activate a new snapshot queue.
- Stop-after is consumed when that entry naturally ends, takes precedence over repeat, and does not affect copies in other queues. Deleting the final active queue or removing its last successor stops without implicit wrap.
- Tag editing supports unflagged ID3v2.3/2.4 MP3 text frames. Unsupported formats, extended headers, unsynchronisation, protected/encoded frames and unsafe absolute-offset expansion are rejected before writes. Other frames/artwork and audio bytes are preserved. Tag Save and permanent deletion currently require Android 11+ for per-file consent; older Android versions retain browsing/playback. Other formats remain readable but not editable.
- Restore never starts playback. Both connection-resume switches default off. The unwanted-autoplay option blocks untrusted external controller requests; trusted system/notification controls remain usable.

## September 6 UI refinements

Applied the user's visual and interaction revisions:

- Control icons reduced slightly (28 to 25 dp); list artwork substitutes retain their original sizing. Now Playing uses smaller text, a 4 dp seek track with a round 16 dp thumb, and no queue-name subheader. Queues also omit that subheader.
- Bottom navigation is 56 dp tall plus system clearance. The active destination fills the entire button section in the prior bubble color, matching the latest preference; no pill, clipping, or overlapping indicator transition.
- Song action menus have compact, full-width rows with outline icons and section dividers. Queue destinations, metadata navigation, radio choices, and checkbox choices accept taps across their rows.
- Queue entries follow the playlist row format: artwork, title and artist/album, duration, then the three-dot menu. Inactive queues use muted artwork and grey italic title/artist/album while preserving the purple saved-track indicator. The queue dropdown has extra top spacing.
- Queue and picker drag handles move the row with the finger and shift neighbouring rows to preview the destination; release commits the order. Queue deletion is immediate from both entry points.
- The main title/subtitle has a larger left inset. Membership-header right padding is 34 dp; the chevron/title group was subsequently nudged 4 dp left at the user's request.
- Playlist details offer Shuffle & Play: the full resolved playlist becomes a new independent shuffled queue, a random song is placed at queue position 1, and playback starts there at 0:00. Unshuffle restores the source playlist order; existing queues and source M3Us remain unchanged. Empty or unavailable playlists disable the action.
- The playlist category uses a + menu with New playlist (name prompt and empty M3U creation) and New playlist from rules (the existing rules flow).
- Rules use separate Match all/Match any chips and an is in/is not in dropdown. Settings has added top padding; popup action spacing is tighter.
- Membership writes replace the action label with a spinner, suppress redundant background progress, and close the modal on success without a confirmation. Success remains suppressed while the ViewModel clears it asynchronously. Errors retain the editor. The selected-count label has additional right padding.

Six isolated Compose tests pass on the Samsung: full-width menu hit targets, visible drag movement before release and correct drop, successful membership closure without confirmation (including delayed message clearing), failure retaining the editor, the empty-playlist name prompt, and the rules creation menu. These use an empty Compose host and in-memory fixture actions; no user playlist or queue mutations are performed by the tests. The instrumentation APK was installed directly and only this test class was run, without uninstalling or clearing the target app.

## Verification and device status

The complete Gradle verification command passes:

```powershell
.\gradlew.bat :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

94 JVM tests pass: 55 domain and 39 app. This includes the existing 55 regressions, 30,000-entry queue round-trip/shuffle checks, policy boundaries, nested unknown-source semantics, generated MP3 tag/audio preservation fixtures, reconnect guards, and repository-level restoration/corruption/stale-checkpoint tests. Android lint has no errors. App and instrumentation APKs build.

The debug APK was installed as an in-place update on the connected Samsung SM-S928U (Android 16). The installed and new APK signing certificates match. The retained launcher alias starts the new MainActivity successfully; startup checking found no AndroidRuntime crash. No uninstall, app-data clear, production playlist write, audio deletion, audio sharing, or production tag edit was performed during development. The prior installed APK was retained under `app/build/installed-before-player.apk` for rollback.

After the user unlocked the phone, native checks used its actual 23,042-song library (5,967 albums):

- A library tap created the complete 23,042-entry queue; playback progressed and naturally advanced to the next track.
- Queue search for Gorillaz produced an independent 228-entry snapshot. Viewing the original queue left the filtered queue active; the original retained its saved track and elapsed time, and Resume subsequently returned playback to that saved track.
- Playback position continued advancing with the launcher foreground. Android media Next and Pause events selected the next entry and paused successfully.
- In-place updates restored both queues, the active/viewed distinction, and paused playback without autoplay.
- Inspected the three-column album grid, long-press selection bar, queue picker, and bounded membership modal with already-containing playlists emphasized first.
- Inspected Now Playing in portrait at the user's 90% font scale and at 130%. Landscape exposed missing artwork and a settings/system-navigation overlap; the corrected side-by-side layout was then inspected at 130%, with all controls and artwork visible. Original rotation and font settings were restored.
- Buffered queue storage now batches I/O; a 30,000-entry regression verifies round-trip correctness, bounded underlying read/write calls, and caller-owned stream completion. Queue filtering moved off the main thread.
- A short debug-build interaction/scroll sample rendered 273 frames: 18.32% missed deadlines, 8 ms median, 25 ms p95, 200 ms p99. Process PSS was about 259 MiB in one subsequent sample. This is a diagnostic sample, not a release benchmark or proof of the 30k performance target.

No keyguard bypass or connectedDebugAndroidTest lifecycle was used. The following acceptance checks remain outstanding and must not be described as proven:

- Human confirmation of audio quality, extended background/screen-off continuity, notification and lock-screen button taps, queue transition timing, focus loss/calls, wired/Bluetooth disconnect/reconnect, and physical headset event delivery.
- Additional small-screen/font combinations, drag interactions, keyboard transitions, and accessibility service navigation beyond the inspected device layouts.
- Native 30,000-song frame timing, peak artwork memory, search/selection latency, fast-scroll behavior, and back-navigation restoration. The JVM 30k test verifies queue correctness, not native rendering performance.
- Real-provider file-copy notifications and replacement behavior. Providers that do not notify require foreground/manual refresh; a completed foreground refresh does not establish every provider's event delivery.
- Tag/file write-consent flows, failure recovery, and format validation using disposable device fixtures before recommending use on valuable music files. Broader tag formats and embedded lyrics outside supported MP3 tags require additional support.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Test APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.

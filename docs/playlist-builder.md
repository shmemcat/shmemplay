# Playlist builder — September 6, 2026

Implemented the approved mobile builder in native Kotlin/Compose. Open **Playlists → + → New playlist from rules**, or edit an existing live playlist through its menu.

## Approved behavior

- Dedicated screen with the shared back chevron; separate rule editors and nested-group screens.
- Live or Snapshot output, ALL/ANY groups, + Rule / + Group, and matching-song preview.
- Playlist membership/non-membership and Artist, Genre, and Song title fields. Text conditions include is, is not, is any of, contains, starts with, and has no value.
- Value selection uses a searchable modal with the existing alphabetical/script-bucket drag-bubble scroller. Multi-value selections remain selected when the search changes; Cancel leaves the rule unchanged.
- “Don't add duplicate songs” is checked for new playlists. It excludes additional matching files with the same normalized title and artist. Songs with missing title/artist remain distinct. Unchecking permits those separate files; each physical library identity is still included once.
- No source-universe selector, Play count, More fields, or limits/ordering section. Rating was explicitly deferred because the app does not currently read ratings.
- Drafts are retained when returning from the builder. Saves show progress and close only after success; failures retain the draft and display the error.

## Implementation

- `ui/PlaylistBuilderScreen.kt`: builder, group navigation, rule editor, preview, and save lifecycle.
- `ui/PlaylistRuleValuePicker.kt`: lazy value rows and cancellable background filtering; reuses `FastScrollableLazyColumn` with precomputed fast-scroll targets.
- `playlists/RuleLibraryIndex.kt`: distinct values, normalized search keys, and metadata built off the UI thread. Index choices are cached and built only when needed.
- `domain/NestedPlaylistRules.kt`: validated mixed membership/metadata evaluation, nested logic, multi-value arrays, missing-source handling, and duplicate exclusion.
- `playlists/LocalPlaylistRecipes.kt`: additive codec support for metadata and duplicate preference. Old definitions remain readable and retain their previous duplicate behavior until changed.

Live metadata-only playlists do not require a playlist-folder grant. Membership rules still require readable source files and are reread before saving. Snapshots use the existing verified M3U creation path. No application ID, launcher alias, database, signing key, or existing storage names changed.

The approved HTML reference is archived alongside the earlier prototype as `docs/prototype/live-playlist-builder.html`. Its demo Rating control predates the explicit decision to omit Rating from this native build.

## Verification and installation

Passed `:domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`: 63 domain tests and 45 app tests; lint has zero errors.

Installed the app as an in-place update on the Samsung SM-S928U. The signing certificate matches the prior installation. The prior APK is retained at `app/build/installed-before-rules-builder.apk`.

Ran only `PlaylistBuilderTest` and `PlayerPolishTest` through AndroidJUnitRunner on the phone: **10 tests passed**. Tests use an empty Compose host, fixture tracks, and in-memory callbacks; they do not write the user's playlists. Coverage includes the 23,000-value lazy picker, search and selection retention, cancellation, save failure/success, and the existing playlist-menu/membership regressions.

Opened the real installed app, verified retained playback/library state and the new builder entry point, and inspected its native layout. The current app process reported no AndroidRuntime errors. Real-provider playlist creation was not exercised by these UI tests. The 23k tests establish correctness and bounded rendered rows, not a release-device latency benchmark.

Installed APK: `app/build/outputs/apk/debug/app-debug.apk`.
SHA-256: `655787C714836DA08A4E422D6C0F8D9EE8727AF3D54F56E871CFF04AD9E92C82`.

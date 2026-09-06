# Approved shmemplay prototype archive

This directory preserves the exact approved player mock outside the original Codex conversation. Start with [the implementation handoff](../START-HERE.md) and [the player plan](../music-player-plan.md).

`shmemplay-mock.html` is the unchanged HTML fragment from the design conversation. It starts on Albums, uses three columns and 30,000 generated sample tracks, and includes the corrected bold/pinned full-membership ordering. All state is local to the mock and resets on reload. No real audio, music files, Android permissions, Bluetooth connections, or persistent queues are involved.

## Screens to inspect

- [Three-column albums](screenshots/albums-three-columns.png) and [320px layout](screenshots/albums-320px.png).
- [Now Playing](screenshots/now-playing.png): appearance only; the later requirement is that the finished screen must never scroll.
- [Queue](screenshots/queues.png) and [queue picker](screenshots/queue-picker.png).
- [Song options](screenshots/song-options.png), [advanced selection](screenshots/advanced-select.png), and [membership ordering](screenshots/membership-pinned.png).
- [Nested live rules](screenshots/live-rules.png), [headset settings](screenshots/headset-settings.png), and [fast scrolling](screenshots/fast-scroll.png).

These screenshots are from the prototype, not from a native player build. The user's original Musicolet references are preserved separately in [player-references](../player-references/), including the early selection-bar/advanced-select references as images 15 and 16.

## Icons and rendering

The user explicitly approved and strongly wants the rounded outline icon style used here. The fragment uses the host-provided Lucide icon runtime through `data-lucide` placeholders and `lucide.createIcons`. Main destinations are `list-music`, `circle-play`, `music-2`, `disc-3`, `user-round`, `tags`, and `list-video`; other names are in the fragment. Preserve their geometry, rounded ends, weight, and useful touch targets when implementing native icons. Retain appropriate licenses/attribution when incorporating icon assets.

The launcher icon is a separate purple record design in [design/shmemplay-icon.svg](../design/shmemplay-icon.svg), with a matching [native Android vector](../../app/src/main/res/drawable/ic_launcher.xml).

To inspect the interactive fragment in a fresh Codex chat, use the available visualization skill/renderer according to that session's instructions. Its `scripts/render.py` supports wrapping a fragment or `--serve` for browser inspection. The fragment is not a standalone web page: opening it directly without the host's styles/icon runtime will omit icons and can alter typography. Do not replace missing icons with unrelated glyphs. The archived screenshots remain a portable reference when that host is unavailable.

## Known differences from the final requirements

- Now Playing's no-scroll requirement was recorded after the last mock update; shrink artwork to fit in native implementation.
- Native membership conversion must retain playlist-name search, the classic Add/Remove controls, Remove's containing-only filter, and in-editor refresh after creating a playlist. The mock simplifies some of these.
- The mock's script labels/search normalization are illustrative. Keep the fuller native multilingual `FastScrollIndex` and native multi-term search semantics.
- Real data failures cannot be treated as empty playlists; validate unknown/missing sources at rule preview, save, and reevaluation. The mock does not establish production error handling.
- Playback ticks, settings switches, file actions, and tag editing are demonstrations. Use real native state/persistence, Android services, and tested file operations in the app.

No redesign or new mock is needed before starting the native build.

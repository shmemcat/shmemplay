# Phase 4 MediaStore identity device proof

Execute this matrix on Android 16 with GoneMAD 4.1.11. Do not record raw URIs
or unredacted metadata in committed artifacts.

## Recorded result: 2026-08-03

- Device: physical Android 16 device
- GoneMAD: 4.1.11
- Media: primary-storage MP3 shared through **Share → File**
- GoneMAD payload: opaque `gonemad.gmmp.provider` URI, not a direct MediaStore URI
- Result: **Track resolved**
- Intake remained redacted, stream/descriptor probing succeeded, and no playlist access or mutation occurred
- The exact automatic evidence tier was not recorded and remains to be captured on a follow-up share

## Permission

- [x] First share explains and requests audio access
- [x] Grant/previously granted access continues to resolution
- [ ] Denial remains read-only and allows a later retry
- [ ] Revocation returns to permission-required state
- [ ] No playlist-tree or broad filesystem permission is requested

## Automatic resolution

- [ ] Each measured Phase 3 MP3 resolves to the intended MediaStore row
- [ ] The selected volume, path context, size, duration, and tags are correct
- [x] At least one opaque GoneMAD MP3 share resolves automatically to exactly one MediaStore candidate
- [ ] Missing optional metadata remains unknown rather than mismatched
- [ ] Unicode NFC/decomposed evidence behaves consistently

## Ambiguity and manual selection

- [ ] Duplicate filenames never select the first candidate
- [ ] Matching artist/title with multiple candidates remains ambiguous
- [ ] Manual screen starts with no selection
- [ ] Confirm remains disabled until deliberate selection
- [ ] Cancel returns to GoneMAD without saving an alias

## Approved aliases

- [ ] Remembered manual choice resolves on the next equivalent share
- [ ] Alias target is re-read from MediaStore on every use
- [ ] Rename/path change invalidates or routes back to resolution
- [ ] Size/duration/material metadata change invalidates safely
- [ ] Delete invalidates safely
- [ ] Permission/query failure is not treated as permanent invalidation
- [ ] Forget removes the remembered mapping

## Lifecycle and safety

- [ ] Rotation preserves active resolution state
- [ ] Forced process death requests a fresh GoneMAD share
- [ ] Warm delivery is covered through instrumentation/ADB
- [ ] GoneMAD's embedded-task Back behavior is unchanged
- [ ] Diagnostics exports remain redacted
- [ ] No playlist document is opened or modified
- [ ] `PhaseOneSafety.PLAYLIST_MUTATION_ENABLED` remains `false`

# Phase 3 GoneMAD intake device proof

This matrix must be executed on Android 16 with GoneMAD 4.1.11. Attach only the
app's redacted export. Never check in raw URIs, filenames, paths, tags, share
text, or device identifiers.

## Build record

- App commit:
- App version:
- Phone model:
- Android 16 build:
- GoneMAD version:
- Storage volumes used:

## Payload cases

For each case record chooser visibility, delivery mode, declared/resolver MIME,
URI source agreement, grant flags, URI authority/shape, stream and descriptor
access, seekability, metadata presence, direct MediaStore evidence, and whether
the evidence is unique or requires Phase 4 resolution.

- [ ] MP3 on primary internal storage
- [ ] Every other audio format in regular use
- [ ] Unicode filename and tags
- [ ] Missing tags
- [ ] Duplicate filename in different directories
- [ ] Two files with matching artist/title
- [ ] Removable-storage track, if used
- [ ] GoneMAD text share
- [ ] Generic or missing MIME, if emitted

## Lifecycle cases

- [ ] App stopped: cold delivery occurs exactly once
- [ ] App backgrounded: warm delivery occurs exactly once
- [ ] App already open: warm delivery supersedes the prior probe
- [ ] URI opens immediately
- [ ] URI behavior after task switching is recorded
- [ ] Rotation does not duplicate the probe
- [ ] URI behavior after forced process death is recorded
- [ ] A later warm share does not display the prior result

## Redaction and safety

- [ ] Export contains no full URI or query token
- [ ] Export contains no raw filename or path
- [ ] Export contains no artist, title, album, or share text
- [ ] Error text contains no provider-supplied sensitive value
- [ ] No audio-library permission is requested
- [ ] No playlist-tree picker is present
- [ ] No playlist document is read or modified
- [ ] `PhaseOneSafety.PLAYLIST_MUTATION_ENABLED` remains `false`

## Exit decision

Phase 3 passes only when the measured `audio/*` filter receives every required
GoneMAD file share without claiming unrelated content, the temporary URI
lifecycle is documented, and each payload has enough evidence either for a
unique direct identity or an explicit Phase 4 manual-resolution route.

If GoneMAD emits a required file share outside `audio/*`, record the exact
measured MIME before considering a narrowly broader manifest filter. Do not add
`*/*` or `ACTION_SEND_MULTIPLE` speculatively.

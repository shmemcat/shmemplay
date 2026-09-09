# Android Auto, playback resumption, and music widgets

Shmemplay exposes a Media3 `MediaLibraryService` through both the Media3 and
platform media-browser interfaces. Android Auto renders its own car interface
from this service; the phone APK includes the car support.

## Available in the car

- Current queue and saved queues, with a Resume entry for each queue.
- Song selection within a queue. Large queues are grouped into 100-song folders.
- Search across songs in saved queues by title, artist, album, or queue name.
  Results are limited to 100, and duplicate songs prefer the active queue.
- Play, pause, next, previous, seeking, and voice play-from-search.
- Resume the active queue's saved song and position through Android media controls.

This first car browser uses saved queues. Create queues on the phone before
driving. Full library categories and M3U/recipe editing are still phone features.
Queue policies, shuffle order, stop-after markers, and file identity checks use
the same repository and playback engine on both surfaces.

Browsing and preparing a queue do not start audio. A Play request is required.
An explicit song selection starts that song at zero; Resume retains its saved
position. Media IDs include the queue identity, so a song shared by multiple
queues cannot accidentally select a different queue. Incoming playback URIs
are never trusted: requests resolve against the saved queue again at execution.

## First car connection with a development APK

Android Auto can hide media apps installed outside a trusted store. For this
locally installed build, enable Android Auto developer mode (tap its version
information ten times), then enable **Unknown sources** in Android Auto's
developer settings. Reconnect the car and check **Customize launcher** for
Shmemplay. This is Android Auto's development setting, separate from Android's
APK installation permission.

Open Shmemplay in the car and play once. Enable Android Auto's **Start music
automatically** setting if automatic playback is wanted. Android Auto and the
phone choose which recent player receives the next resume command; Shmemplay
does not force itself over another player you subsequently use.

Shmemplay's reconnect switches resume only a song paused by a route disconnection
while its service remains alive. They are distinct from Android's media-button
receiver and persisted resumption, which can restart the service.

## Widget diagnosis and fix, September 7, 2026

Before the update, the connected Samsung reported an active Shmemplay media
session with correct title/artist metadata, but `mediaButtonReceiver=null`.
Its last saved media-button receiver pointed to Spotify. KWGT had notification
access and was set to Automatic, but displayed Spotify's paused song instead.

The update registers Media3's media-button receiver and implements playback
resumption. It also exposes the library service and voice-search entry point,
adds duration and local artwork metadata, and allows metadata-reader connections
independently of the unsolicited-playback setting. Trusted system, Android Auto,
notification, and widget controllers remain usable. Raw headset-hook clicks
still support multi-press actions; standard media Play/Pause keys use Media3's
normal dispatch and resumption path.

After the update, Shmemplay appeared in KWGT's Preferred Music Player list.
Restarting KWGT refreshed its cached player detection. With its preference still
on Automatic, the home-screen widget displayed Shmemplay's title and artist and
updated at the next song transition. No widget design changes were needed.

## Verification

- Full domain/app JVM test gate, Android lint, debug APK, and instrumentation
  APK builds passed. Tests cover 30,000-song browse grouping, bounded search,
  queue-qualified IDs, missing entries, metadata, pagination overflow, and
  controller commands arriving before queue restoration completes.
- On the Samsung SM-S928U (Android 16), `CarBrowserTest` connected through the
  platform `MediaBrowser`, browsed the two roots and saved queues, and verified
  play-from-ID/search actions. Preparing the current queue's Resume entry
  preserved the active queue, song, position, and queue count without playback.
- Normal Android media Play resumed Shmemplay, and Pause stopped playback.
  The OS recorded Shmemplay's receiver as the last media-button receiver.
- An unmatched phone voice-search intent showed the expected error and retained
  the current song and position in a paused state.
- The APK was installed as a same-key, in-place update. The previous APK is
  retained locally at `app/build/installed-before-android-auto.apk`.

The original native browser test runs under the app's UID. It verifies the
service protocol and saved queue preparation, but cannot establish external
controller permissions or Android Auto launcher behavior.

## Follow-up car diagnosis, September 7, 2026

The physical car test still showed only a media card, with Spotify selected,
no Shmemplay shortcut, and an ineffective Play button. A media card and audio
output were already possible before car integration; they are not acceptance
criteria for a dedicated Android Auto app.

On the connected Samsung, Android Auto's **Start music automatically** was
already enabled. Developer mode was off and Shmemplay was absent from
**Customize launcher**. Enabled developer mode and its **Unknown sources**
option for this locally installed development APK.

Google's Desktop Head Unit (DHU 2.0, headless over ADB) then reproduced a real
Android Auto connection using the phone's Google Android Auto app. Before
installing further code changes, Shmemplay appeared in the car launcher, opened
at the saved 0:14 position, accepted Play and Pause, appeared in the shortcut
bar, and resumed automatically after disconnect/reconnect. This isolates the
development-app discovery setting as the main observed issue on this phone.

Also fixed an independent Media3 1.11 permission bug: the default connection
builder grants untrusted controllers read-only commands. Our playback policy
allowed Android Auto and optionally other external controllers, but did not
advertise those grants, so their commands could be discarded before reaching
the command callback. The session now explicitly grants transport/library
commands to policy-authorized controllers. Package-specific exceptions require
a verified package name; blocked readers retain read-only access. Five tests
cover these cases; two failed before the fix. Actual Google Android Auto on
this Samsung reported `trusted=true`, so this was not its immediate blocker.

Added the monochrome Android Auto attribution icon and explicit service
label/icon. The existing launcher vector already rendered correctly once the
development app became discoverable. Diagnostic log tag `ShmemplaySession`
records controller identity, trust, and command grants, without song metadata.

After the same-key in-place APK update, with the app process absent, reconnecting
DHU started the service and automatically played the saved song from its 78.4s
checkpoint without opening the phone app. Dashboard Play/Pause, the shortcut
icon, the monochrome card icon, and Current queue/Saved queues browsing worked.
The 118 domain/app JVM tests passed, along with lint (0 errors, 38 warnings),
debug APK and instrumentation APK builds. The prior APK is retained locally at
`app/build/installed-before-car-controls-fix.apk`.

The physical head unit still needs a follow-up plug-in test. DHU verifies actual
Android Auto software and external commands, but not the car's USB/Bluetooth
connection timing or voice recognition. Android Auto's development setting must
remain enabled for this sideloaded build; head unit server is only for DHU tests.

## Spotify Connect priority investigation, September 8, 2026

The user confirmed dedicated Android Auto browsing, controls, and resumption
worked in the physical car. A remaining symptom was Spotify's card being shown
when Spotify was open in the background, without Spotify playing on the phone.

Device inspection found Shmemplay's session active, correctly paused, and still
selected for media-button dispatch. Its service was alive and in the foreground.
Spotify was reporting `STATE_BUFFERING` and updating song metadata while the
user played Spotify on another device. Android counts buffering as an active
playback state. When the user paused the other device, the phone's Spotify
session changed to paused. This confirms remote playback affects the media
state visible to clients on the phone; it does not prove Android Auto's exact
card-selection algorithm.

In Spotify's **Settings > Apps and devices > Spotify Connect control**, disabled
the enabled option for controlling other-device playback from the phone's lock
screen. The user resumed Spotify on the other device; the phone session stayed
paused at the old position. Spotify's process remained running throughout.
After briefly playing/pausing Shmemplay to establish it as the latest phone
player, DHU reconnected, automatically resumed it, and retained its dashboard
card after pausing. No Shmemplay application code or APK changed for this test.

The setting removes lock-screen remote controls for Spotify playing elsewhere.
It is a targeted workaround for competing remote-session updates, not a
guarantee that Android Auto will never display Spotify. The initial DHU attempt
also resumed Shmemplay after the user paused the remote device, so the original
card takeover was not consistently reproduced. A physical-car follow-up with
Spotify open remains useful. The temporary DHU server was stopped after testing.

## References

- [Android Auto media integration](https://developer.android.com/training/cars/media/auto)
- [Media3 library services](https://developer.android.com/media/media3/session/serve-content)
- [Playback resumption](https://developer.android.com/media/media3/session/background-playback)
- [Testing development apps in cars](https://developer.android.com/training/cars/testing)
- [Desktop Head Unit testing](https://developer.android.com/training/cars/testing/dhu)
- [Media3 controller command defaults](https://developer.android.com/reference/androidx/media3/session/MediaSession.ConnectionResult)
- [Car launcher and attribution icons](https://developer.android.com/training/cars/media/configure-manifest)
- [Kustom music troubleshooting](https://docs.kustom.rocks/docs/common_issues/music_player/)
- [Kustom developer discussion of player detection](https://forum.kustom.rocks/t/media-cover-art-issue/5725)
- [Spotify Connect](https://support.spotify.com/us/article/spotify-connect/)
- [Android active playback states](https://developer.android.com/reference/android/media/session/PlaybackState#isActive())

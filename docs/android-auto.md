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

Physical Android Auto launcher rendering, voice recognition in the car,
plug-in autoplay selection, and reconnect behavior still require a car test.
The native browser test verifies the service protocol, not a head-unit UI.

## References

- [Android Auto media integration](https://developer.android.com/training/cars/media/auto)
- [Media3 library services](https://developer.android.com/media/media3/session/serve-content)
- [Playback resumption](https://developer.android.com/media/media3/session/background-playback)
- [Testing development apps in cars](https://developer.android.com/training/cars/testing)
- [Kustom music troubleshooting](https://docs.kustom.rocks/docs/common_issues/music_player/)
- [Kustom developer discussion of player detection](https://forum.kustom.rocks/t/media-cover-art-issue/5725)

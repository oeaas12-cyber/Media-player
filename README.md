# Prism Player

A frosted-glass media player for **Android TV, phones and tablets** that plays the
movies and shows you already own: on a Jellyfin/Emby or Plex server, a network share
(SMB) or a WebDAV folder. It's an original app built with Kotlin, Jetpack Compose and
Media3/ExoPlayer, and it isn't affiliated with any other player.

## Features

**Library**
- Home screen with a rotating hero, plus Continue Watching, Recently Added, Movies, TV Shows, Top Rated and genre shelves
- Movies and TV Shows grids with sorting (A–Z, recently added, year, rating), genre filters and watched/unwatched filters
- Detail pages with artwork, ratings, genres and overview, plus season tabs and episode lists with progress bars
- Search across every source
- One merged library across all sources

**Sources**
| Source | Sign-in | Metadata | Sync back |
|---|---|---|---|
| Jellyfin / Emby | Username + password | From server | Resume points and watched state |
| Plex | Code at plex.tv/link, or URL + token | From server | Timeline and scrobble |
| SMB (Windows/macOS/NAS) | User/pass or guest, SMB 2/3 | TMDB, from file names | Local |
| WebDAV (Nextcloud, NAS, rclone) | Basic auth | TMDB, from file names | Local |

File names like `Movie.Name.2019.1080p.mkv` and `Show.S02E05.mkv` (or `Show/Season 2/…`)
are matched automatically. Sidecar subtitles (`.srt .vtt .ass .ssa`) next to the video are picked up.

**Player**
- Glass controls: play/pause, skip back/forward, scrubbing seek bar, time remaining
- Audio track picker (language, codec, channels) and subtitle picker (embedded and external)
- Playback speed and picture mode (fit, crop, stretch)
- Resume, auto-play next episode, and a Next Episode button
- D-pad remote support: left/right seeks, center shows the controls, plus media keys and a MediaSession
- Touch support: tap to show the controls, double-tap a side to skip
- Preferred audio and subtitle languages, subtitle size, skip intervals

## Install

Every push builds APKs in GitHub Actions (**Actions → Android build → Artifacts →
`prism-player-apks`**). The same APK runs on both TV and phone.

- **Phone:** open the APK and allow installing from unknown sources.
- **Android TV:** `adb connect <tv-ip> && adb install -r app-release.apk`, or sideload it with an app such as *Downloader* or *Send Files to TV*.
  Prism appears in the TV launcher row.

## Build locally

You need JDK 17 and the Android SDK (API 34).

```
./gradlew assembleDebug
```

## Tips
- For artwork on SMB/WebDAV sources, add a free TMDB API key in **Settings → Metadata**, then press refresh on the source.
- Some codecs (DTS, TrueHD, some HEVC profiles) depend on what the device can decode. If your device can't play a file directly, Jellyfin/Plex sources are the most reliable option.
- Passwords for SMB/WebDAV are stored in the app's private storage on the device.

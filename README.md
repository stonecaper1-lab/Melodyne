# Melodyne

A tiny, free-software music player for Android. Kotlin + Jetpack Compose (no Material), Media3/ExoPlayer,
Retrofit/OkHttp. No Firebase, no Google Play Services, no analytics, no ads.

## Build (no local Android SDK needed)
Push the repo to GitHub and run the `build-apk` workflow. It runs the unit tests, builds with R8 and fails
if the APK is 5,000,000 bytes or larger. Local: `gradle :app:assembleRelease` (Gradle 8.9, JDK 17, SDK 34).
`app/melodyne-dev.jks` is a PUBLIC dev key so new builds install over old ones; replace it before sharing builds.

## How it works
- **Music source**: `ChainRepository` tries an optional custom source (Settings), then the built-in catalogue.
  Results are cleaned by `Refine` (duplicate versions collapsed, exact title matches first).
  Failures are listed in the error card so you can see what broke.
- **Streams**: the highest bitrate offered (320 kbps AAC when available), forced to https.
- **Quality badge**: `QualityMonitor` reads the Format ExoPlayer feeds the decoder and publishes it through
  MediaSession extras. Green dot = bitrate read from the stream header; amber = the server's label only.
  Tap the badge for a plain explanation. It shows what the file *declares*, not a spectral analysis.
- **Equalizer** (Now Playing -> EQ): seven bands (60, 170, 450, 1k, 2.5k, 6k, 14k Hz), pure curves only.
  `EqProcessor` runs inside the audio sink in double precision with automatic headroom (no clipping).
  Flat or off = input copied through untouched. Touch any band to make a Custom curve.
- **Library**: custom playlists (create, rename, reorder, remove), smart playlists built from play history and
  track length, import/export as JSON via the system file picker, add-to-playlist from any song (+).
  Stored in `filesDir/library.json` with atomic writes and a rolling `.bak`, opted into Android backup
  (`backup_rules.xml`). "Clear cache" can't touch it; "Clear storage" or uninstalling will, so use Export.
- **AI Mix**: bring your own key for any OpenAI-compatible `/chat/completions` endpoint. Quick mix needs no key.
- **Scrobbling**: Last.fm, with your own free API key entered in Settings; failed scrobbles are queued.
- **Crash recorder**: uncaught exceptions are saved locally and shown in Settings -> Last crash.

## Audiophile Mode, Audio Tuning, Updates
- **Audiophile Mode** (Home): EQ, normalisation and crossfade are bypassed. The signal-chain sheet reports source, single-stage decode,
  processing, the OS path (bit-perfect USB on Android 14+, otherwise whether Android must resample) and the output route.
  Apps cannot switch off Android's mixer or Bluetooth re-encoding; the sheet says so instead of pretending.
- **Normalisation**: BS.1770 K-weighted loudness measured over the first seconds of each song, gain to -14 LUFS (+3/-12 dB limits, peak-aware),
  faded in and remembered per song. Gapless is automatic when files declare delay/padding. Crossfade is planned (needs a second decoder).
- **Updates**: the app checks the latest GitHub release at most every 6 hours and shows a banner; nothing installs automatically.

## Releases
Uploading `melodyne-source.zip` makes the workflow commit the real source as "Melodyne vX.Y.Z", build, and publish a release with
the notes from the top of CHANGELOG.md. The newest APK is always at `releases/latest/download/melodyne.apk`.

## Known gaps
- Tracks from files on the phone aren't supported yet; playlists hold streamed songs.
- Session key and API keys are stored in plain app preferences.

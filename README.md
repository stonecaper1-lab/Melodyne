<div align="center">

# Melodyne

### Your music, your mood, your way.

A small, free-software music player for Android—made for listening, not for getting in your way.

[Get the latest release](https://github.com/stonecaper1-lab/Melodyne/releases/latest) · [Explore the features](#a-player-that-feels-like-yours) · [Build it yourself](#build)

</div>

---

Melodyne keeps the listening experience personal and refreshingly straightforward. Find something for the moment, make it sound right to you, and get back to the music.

No account to create. No ads. No analytics. Your library stays on your device.

## A player that feels like yours

- **Find your next listen.** Search music, explore moods and charts, or let Infinite Play keep a queue going.
- **Make it sound right.** Shape the sound with a seven-band equalizer and optional loudness normalization.
- **See what’s playing.** Live bitrate details show what the stream reports as it plays—with clear explanations of what those numbers do and don’t mean.
- **Choose a cleaner signal path.** Audiophile Mode bypasses Melodyne’s EQ, normalization, and crossfade processing. Device and Bluetooth limits are explained in the app rather than glossed over.
- **Keep your own collection.** Create regular or smart playlists, then export or import them when you need to.
- **Set the mood.** Try classic player skins, a liquid-glass look, or a more minimal AMOLED experience.
- **Make a mix your way.** Quick Mix works without a key. AI Mix can connect to an OpenAI-compatible endpoint using your own key.

Some discovery and scrobbling features use Last.fm and need your own API key. You can use Melodyne without setting one up.

## Get Melodyne

Download the [latest release](https://github.com/stonecaper1-lab/Melodyne/releases/latest). The latest APK is also available at [`melodyne.apk`](https://github.com/stonecaper1-lab/Melodyne/releases/latest/download/melodyne.apk).

## Build

You can build locally with **JDK 17**, **Gradle 8.9**, and **Android SDK 34**:

```bash
gradle :app:assembleRelease
```

Or push the repository to GitHub and run the `build-apk` Actions workflow to run tests and build the APK without setting up a local Android SDK.

> **For contributors:** `app/melodyne-dev.jks` is a public development key used so test builds can install over one another. Replace it with your own signing key before distributing a build.

## Under the hood

Melodyne is written in **Kotlin** with **Jetpack Compose** and **Media3/ExoPlayer**. It uses Retrofit and OkHttp for network requests, with no Firebase, Google Play Services, analytics, or advertising SDKs.

A few implementation details for curious listeners and contributors:

- The quality badge reflects the format information available from the stream; it is not a spectral analysis or a guarantee of audible quality.
- The equalizer runs in the audio sink with automatic headroom. A flat or disabled EQ passes the input through unchanged.
- Audiophile Mode can avoid Melodyne’s own processing, but Android’s mixer and Bluetooth re-encoding remain outside the app’s control.
- Playlists are stored locally and can be exported as JSON. Keep an export if you plan to clear app storage or uninstall.

## Current limitations

- Playing music files stored on your phone is not supported yet; playlists currently contain streamed songs.
- AI Mix with a custom endpoint requires your own API key. Keys are stored in app preferences.
- Crossfade is not currently available.

## Releases

The project’s release workflow publishes the APK and uses the notes at the top of [`CHANGELOG.md`](CHANGELOG.md). You can also browse [all releases](https://github.com/stonecaper1-lab/Melodyne/releases).

---

Made for people who care about the little things in a listening session—and would rather spend more time listening than configuring an app.

<div align="center">

# Melodyne

<img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
<img src="https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android" />
<img src="https://img.shields.io/badge/Free%20Software-00C7B7?style=for-the-badge" alt="Free software" />
<img src="https://img.shields.io/badge/No%20Ads-FF6B6B?style=for-the-badge" alt="No ads" />

### Your music, your mood, your way.

A small, free-software music player for Android — built to feel calm, honest, and easy to live with.

[Download latest APK](https://github.com/stonecaper1-lab/Melodyne/releases/latest) · [See releases](https://github.com/stonecaper1-lab/Melodyne/releases) · [Build from source](#build)

</div>

---

Melodyne is for the moments when you want music to feel natural instead of noisy. Find the right vibe, keep the signal honest, and let the album art, sound, and queue work with you instead of against you.

No account to create. No ads. No analytics. Your library stays on your device.

## Why people like it

- 🎵 **A calmer listening flow** — a focused player that stays out of your way.
- 🔊 **Sound you can trust** — live bitrate and audio details are measured from playback, not guessed.
- 🎧 **Less processing, more honesty** — Audiophile Mode keeps the signal path cleaner when you want it.
- 🧠 **Personalized discovery** — moods, charts, infinite play, and AI Mix keep the app feeling alive.
- 🫶 **Your setup, your taste** — choose your skins, playlist flow, and how explicit you want the app to be.

## Highlights

| Feature | What it does |
| --- | --- |
| 🎚️ Equalizer | Seven-band tuning with gentle loudness and headroom-aware controls. |
| 🎧 Audiophile Mode | Bypasses Melodyne’s EQ, normalization, and crossfade to keep the signal cleaner. |
| 📈 Live bitrate | Shows what the stream is actually doing as the song plays. |
| 🔁 Infinite play | Keeps the queue moving with recommended music that fits your taste. |
| 🧩 Smart playlists | Build your own collection from listening history and track length. |
| 🤖 AI Mix | Connect to an OpenAI-compatible endpoint with your own key. |
| 🧪 Last.fm | Unlock richer discovery and scrobbling with your own API key. |

## Get Melodyne

Download the latest build from the [releases page](https://github.com/stonecaper1-lab/Melodyne/releases/latest).

```text
releases/latest/download/melodyne.apk
```

## Build

You can build locally with JDK 17, Gradle 8.9, and Android SDK 34:

```bash
gradle :app:assembleRelease
```

Or push the repo to GitHub and run the `build-apk` workflow to test and build the APK without setting up a local Android SDK.

> `app/melodyne-dev.jks` is a public development key so test builds can install over one another. Replace it with your own signing key before distributing builds.

## Under the hood

Melodyne is built in **Kotlin** with **Jetpack Compose** and **Media3/ExoPlayer**. It uses Retrofit and OkHttp for networking, and intentionally avoids Firebase, Google Play Services, analytics, and ad SDKs.

A few implementation details worth knowing:

- 🔍 The quality badge reflects the format information available from the stream; it is not a spectral analysis.
- ⚖️ The equalizer keeps automatic headroom so it can shape the sound without clipping or overdoing it.
- 📡 Audiophile Mode removes Melodyne’s processing layers, but Android’s system mixer and Bluetooth re-encoding still remain outside the app’s control.
- 💾 Playlists are stored locally and can be exported as JSON when needed.

## Current limitations

- 📁 Local music files on your phone are not supported yet.
- 🔐 AI Mix with a custom endpoint requires your own API key.
- 🌉 Crossfade is not yet implemented.

## Releases

This project’s release workflow publishes the APK and uses the notes at the top of [`CHANGELOG.md`](CHANGELOG.md). You can browse all builds in the [releases tab](https://github.com/stonecaper1-lab/Melodyne/releases).

---

Made for people who want a music player that feels a little more human and a little less like a billboard.

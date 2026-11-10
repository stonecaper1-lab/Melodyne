# Melodyne

<div align="center">

<img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
<img src="https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android" />
<img src="https://img.shields.io/badge/Free%20Software-00C7B7?style=for-the-badge" alt="Free software" />
<img src="https://img.shields.io/badge/No%20Ads-FF6B6B?style=for-the-badge" alt="No ads" />

## Listen better. Feel less noise.

A tiny, free-software music player for Android built for people who want music to feel calm, honest, and alive.

[Download latest APK](https://github.com/stonecaper1-lab/Melodyne/releases/latest) · [View releases](https://github.com/stonecaper1-lab/Melodyne/releases) · [Build from source](#build)

</div>

---

Melodyne is a music player designed around one simple idea: the app should help you listen, not compete with the music.

No signup. No ads. No hidden analytics. Just a focused player that gives you the signal, the mood, and the flow you want.

## Why Melodyne

- 🎵 Less clutter, more listening.
- 🔊 Real playback data, not marketing copy.
- 🎧 Cleaner signal path when you want it.
- 🧠 Discovery that feels personal, not noisy.
- 🧩 Playlists and controls that stay out of your way.

## The experience

### Sound that feels honest

Melodyne shows live bitrate and audio details as the track plays, so you can see what the stream is actually doing in real time instead of trusting a file header or a vague status label.

### Tune without the drama

Use a seven-band EQ, gentle loudness shaping, and a cleaner Audiophile Mode when you want the sound path to stay as direct as possible.

### Make it feel like your own

Choose your preferred skin, build playlists, discover moods, and let the app keep the queue moving when you're in the mood for something new.

### Built for a good listening session

Whether you're commuting, working, winding down, or just trying to get the vibe back, Melodyne is meant to be simple, personal, and intentionally calm.

## Features

| Feature | What it means |
| --- | --- |
| 🎚️ Equalizer | Shape the tone with a seven-band EQ and headroom-aware tuning. |
| 🎧 Audiophile Mode | Bypass Melodyne’s own processing for a cleaner path to the output. |
| 📈 Live bitrate | Watch a song’s actual playback stats as it runs. |
| 🔁 Infinite play | Keep the queue moving with suggestions that match your mood. |
| 🧩 Smart playlists | Build collections around listening history and track length. |
| 🤖 AI Mix | Connect to an OpenAI-compatible endpoint using your own key. |
| 🧪 Last.fm | Add richer discovery and scrobbles with your own API key. |

## Get it

Download the latest APK from the [releases page](https://github.com/stonecaper1-lab/Melodyne/releases/latest):

```text
https://github.com/stonecaper1-lab/Melodyne/releases/latest/download/melodyne.apk
```

## Build

Local build:

```bash
gradle :app:assembleRelease
```

Requirements:
- JDK 17
- Gradle 8.9
- Android SDK 34

Or run the `build-apk` GitHub Action to build and test without a local Android SDK setup.

> `app/melodyne-dev.jks` is a public development key so test builds can replace each other during development. Replace it before distributing a public build.

## Tech stack

Melodyne is built in **Kotlin** using **Jetpack Compose** and **Media3/ExoPlayer**. Networking uses **Retrofit + OkHttp**, and the app intentionally avoids Firebase, Google Play Services, analytics, and ad SDKs.

## Current limitations

- 📁 Local music files stored on the phone are not supported yet.
- 🔐 AI Mix with a custom endpoint requires your own API key.
- 🌉 Crossfade is not yet implemented.

## Releases

The release workflow publishes the APK and uses the notes at the top of [`CHANGELOG.md`](CHANGELOG.md). You can browse all builds in the [releases tab](https://github.com/stonecaper1-lab/Melodyne/releases).

---

Made for listeners who want a music player that feels more human and a lot less like a dashboard.

# Changelog

## v0.6.0
- Liquid glass look: translucent panels with a light-catching edge, a floating glass navigation bar with a springy "liquid" indicator, glass sheets and dialogs, soft colour glow behind everything and a blurred-artwork backdrop on Now Playing (Settings > Appearance turns the glow off for pure AMOLED black).
- Exact live bitrate: every compressed audio frame is measured as it plays, so the number is real and moves with variable-bitrate files; average = file size / duration; tap the badge for a 40-second line chart.
- Infinite play: when the queue is nearly done, similar songs (Last.fm similar tracks and artists) are queued automatically. Toggle with the infinity chip on Now Playing.
- Genre cloud (Search tab) sized by Last.fm's tag chart, with Indian genres added; every word opens a genre page. Genre tags on Now Playing are tappable.
- Crossfade engine (1-12 s, equal-power or linear), locked in Audiophile Mode; Normal / Audiophile / Party profiles; gapless pauses while crossfading.
- Catalogue playlists removed entirely; your own playlists, smart playlists and AI mixes are unchanged.

## v0.5.0
- Update notifier: Home shows a banner when a newer GitHub release exists; Settings > Updates can check on demand.
- Audiophile Mode (switch on Home): bypasses EQ, normalisation and crossfade, shows an honest signal chain (source, decode, processing, OS path, output), and asks Android 14+ for the bit-perfect USB path when a USB DAC is connected.
- Audio Tuning in Settings: loudness normalisation (BS.1770 measurement, -14 LUFS target, remembered per song), gapless status, crossfade placeholder, Bluetooth absolute-volume shortcut.
- Last.fm enrichment (24 h cache): song tags and listener counts on Now Playing, "Because you listened to ...", "On repeat", and a Trending chart for India.
- Moods now come from Last.fm tags instead of words in titles.
- Now Playing: no more artwork flicker, swipe left/right for next/previous, swipe up for synced lyrics (LRCLIB first).
- AI settings: key links for OpenRouter, Groq and OpenAI, plus Test connection.
- Hidden screen: search 1509, or tap the version in Settings five times.
- Every version is now its own commit and release on GitHub.

## v0.4.0
- 7-band software EQ with automatic headroom, reachable from Now Playing; flat or off is bit-for-bit untouched.
- Library: custom playlists, smart playlists, import/export, add to playlist from any song.
- Search results de-duplicated; Now Playing redesigned; all source references removed from the app.

## v0.3.0
- New Home, Last.fm scrobbling fixed (own API key in Settings), notification tap opens the app.

## v0.2.0
- Playback crash fixed, crash recorder, AI Mix, redesigned Settings.

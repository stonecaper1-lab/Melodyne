import type { Metadata } from 'next'
import './globals.css'

export const metadata: Metadata = {
  title: 'Melodyne — Android music player, your music your rules',
  description: 'Explore Melodyne for Android: seven-band EQ, Audiophile Mode, playlists, AI Mix, live bitrate, moods, lyrics, Last.fm and more. Download the latest APK directly.',
}

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body>{children}</body></html>
}

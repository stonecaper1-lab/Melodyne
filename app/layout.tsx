import type { Metadata } from 'next'
import './globals.css'

export const metadata: Metadata = {
  title: '🎧 Vina: Android music player with an honest audio chain, retro skins, and liquid glass ✨',
  description: 'Meet Vina, an Android music player with an honest audio chain, live bitrate monitoring, Last.fm discovery, Frugal Mode, liquid glass and retro hi-fi skins.',
}

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body>{children}</body></html>
}

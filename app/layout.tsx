import type { Metadata } from 'next'
import './globals.css'

export const metadata: Metadata = {
  title: 'Melodyne — your music, your rules',
  description: 'A little Android music player for the long way home. No ads, no accounts, just the good stuff.',
}

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body>{children}</body></html>
}

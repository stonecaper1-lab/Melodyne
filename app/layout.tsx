import type { Metadata } from 'next'
import './globals.css'

export const metadata: Metadata = {
  title: 'Melodyne — music that feels like yours',
  description: 'A tiny, thoughtful music player for curious listeners.',
}

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body>{children}</body></html>
}

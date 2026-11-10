'use client'

import { useEffect } from 'react'

export default function Error({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error('[v0] Melodyne page error', error)
  }, [error])

  return (
    <main className="fallback-page">
      <div className="fallback-card">
        <span className="eyebrow mono">playback interrupted</span>
        <h1>Let&apos;s try that again.</h1>
        <p>Melodyne hit a temporary problem while loading this page.</p>
        <button className="primary" type="button" onClick={() => reset()}>Reload page</button>
      </div>
    </main>
  )
}

import Link from 'next/link'

export default function NotFound() {
  return (
    <main className="fallback-page">
      <div className="fallback-card">
        <span className="eyebrow mono">signal lost</span>
        <h1>That track isn&apos;t here.</h1>
        <p>The page you requested could not be found. Return to the listening room and keep going.</p>
        <Link className="primary" href="/">Back to Melodyne</Link>
      </div>
    </main>
  )
}

'use client'

import { useState } from 'react'

const bars = [18,32,24,45,28,38,52,30,42,22,35,50,29,44,34,25,48,38,55,31,42,24,36,47,28,40,52,29,45,34,22,38,49,30,42,26,36,54,32,44]

export default function Home() {
  const [playing, setPlaying] = useState(false)
  return <main>
    <div className="container">
      <nav className="nav" aria-label="Main navigation">
        <a className="brand" href="#top"><span className="mark" aria-hidden="true"><i/><i/><i/><i/></span>melodyne</a>
        <div className="navlinks"><a href="#why">why melodyne</a><a href="#features">features</a><a href="https://github.com/stonecaper1-lab/Melodyne">source ↗</a></div>
        <a className="nav-cta" href="#download">get the app</a>
      </nav>
      <section className="hero" id="top">
        <div><div className="eyebrow mono">a quieter kind of player</div><h1>Music that feels <span>like yours.</span></h1><p>Melodyne is a tiny, free-software music player for people who care about the details — from the first note to the last.</p><div className="actions"><a className="primary" href="#download">Explore Melodyne</a><a className="secondary" href="#why">How it works</a></div></div>
        <div className="player" aria-label="Music player demo"><div className="player-top mono"><span>now playing</span><span>320 kbps · AAC</span></div><div className="album"/><div className="track"><div><h3>Night Drive</h3><p>Chromatics · Kill For Love</p></div><div className="dots">•••</div></div><div className="wave" aria-hidden="true">{bars.map((height, i) => <b key={i} style={{height: `${height}%`, opacity: i < 23 ? .9 : .32}} />)}</div><div className="track mono"><span>02:14</span><span>04:07</span></div><div className="controls"><button aria-label="Previous track">◀</button><button className="play" aria-label={playing ? 'Pause' : 'Play'} onClick={() => setPlaying(!playing)}>{playing ? 'Ⅱ' : '▶'}</button><button aria-label="Next track">▶</button></div></div>
      </section>
      <section className="section" id="why"><div className="section-head"><h2>Made for deep listening.</h2><p>No ads. No accounts. No noise between you and the music. Just a thoughtful player with the controls where they belong.</p></div><div className="features" id="features"><article className="feature"><div className="num mono">01 / sound</div><h3>Audiophile mode</h3><p>See what your stream actually delivers. Tune the chain, skip the marketing, keep the signal clean.</p></article><article className="feature"><div className="num mono">02 / shape</div><h3>Make it yours</h3><p>A precise seven-band EQ, loudness normalization, and an uncluttered library that stays out of your way.</p></article><article className="feature"><div className="num mono">03 / open</div><h3>Free software</h3><p>Built in the open with no analytics and no lock-in. Bring your own sources, playlists, and curiosity.</p></article></div></section>
      <section className="section" id="download"><div className="section-head"><h2>Keep listening.</h2><p>Melodyne is built for Android. Download the latest release or explore the source on GitHub.</p></div><div className="actions"><a className="primary" href="https://github.com/stonecaper1-lab/Melodyne/releases/latest">Download latest APK ↗</a><a className="secondary" href="https://github.com/stonecaper1-lab/Melodyne">View source ↗</a></div></section>
      <footer className="footer mono"><span>melodyne / 2026</span><span>made for the long way home</span></footer>
    </div>
  </main>
}

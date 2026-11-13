'use client'

import { useState } from 'react'

const APK_DOWNLOAD = 'https://github.com/stonecaper1-lab/Melodyne/releases/latest/download/melodyne.apk'
const REPOSITORY = 'https://github.com/stonecaper1-lab/Melodyne'
const GITHUB_ISSUES = `${REPOSITORY}/issues`

type Skin = 'liquid' | 'cassette' | 'amp' | 'receiver'

const skins: { id: Skin; name: string; detail: string }[] = [
  { id: 'liquid', name: 'Liquid glass', detail: 'Soft light, album-art color' },
  { id: 'cassette', name: 'Cassette', detail: 'A little analog nostalgia' },
  { id: 'amp', name: 'Classic Amp', detail: 'Warm meters, tactile knobs' },
  { id: 'receiver', name: 'Receiver', detail: 'Cool tuner, hi-fi hardware' },
]

const features = [
  {
    number: '01',
    title: 'Audiophile Mode',
    detail: 'Follow the path from source and decoder through Android output. Vina calls out when the system or Bluetooth can resample or re-encode the signal.',
    mark: 'SOURCE → OUTPUT',
  },
  {
    number: '02',
    title: 'Bitrate that moves',
    detail: 'Read live bitrate from the audio frames as they play—not just a track’s headline number. Variable-bitrate streams can show their changing rate.',
    mark: 'FRAME BY FRAME',
  },
  {
    number: '03',
    title: 'Music with context',
    detail: 'Optional Last.fm enrichment brings tags, listener counts, scrobbles, recommendations, “On repeat” and “Because you listened to…”.',
    mark: 'LAST.FM, OPTIONAL',
  },
  {
    number: '04',
    title: 'Frugal Mode',
    detail: 'When you need to stretch data or battery, lower stream quality, artwork size and buffering without losing your place in the music.',
    mark: 'DATA · BATTERY · BUFFER',
  },
]

export default function Home() {
  const [skin, setSkin] = useState<Skin>('liquid')

  return (
    <main className="vina-site" id="top">
      <header className="site-header">
        <a className="brand" href="#top" aria-label="Vina home">
          <span className="brand-mark" aria-hidden="true"><i /><i /><i /><i /></span>
          <span>vina<span className="brand-period">.</span></span>
        </a>
        <nav className="main-nav" aria-label="Main navigation">
          <a href="#sound">The sound</a>
          <a href="#skins">Skins</a>
          <a href={REPOSITORY} target="_blank" rel="noreferrer">Open source <span aria-hidden="true">↗</span></a>
          <a className="nav-download" href={APK_DOWNLOAD}>Get Vina <span aria-hidden="true">↓</span></a>
        </nav>
      </header>

      <section className="hero" aria-labelledby="hero-title">
        <div className="hero-copy">
          <p className="eyebrow"><span className="live-dot" /> AN ANDROID MUSIC PLAYER IN PROGRESS</p>
          <h1 id="hero-title"><span className="title-icon" aria-hidden="true">🎧</span> Vina<span className="title-stop">.</span><br /><span className="hero-second-line">Hear the whole story.</span></h1>
          <p className="hero-description">A music player for people who care what happens between the source and their ears. Clear signal details, unexpected discoveries, and a player that can dress up like your favorite hi-fi.</p>
          <div className="hero-actions">
            <a className="primary-button" href={APK_DOWNLOAD}>Download the Android APK <span aria-hidden="true">↓</span></a>
            <a className="text-link" href={REPOSITORY} target="_blank" rel="noreferrer">Explore the source <span aria-hidden="true">↗</span></a>
          </div>
          <p className="hero-note"><span>ANDROID</span><span className="note-divider" /> <span>FREE TO USE</span><span className="note-divider" /> <span>STILL GROWING</span></p>
        </div>

        <div className="player-stage" aria-label="Vina player skin preview">
          <div className="stage-orbit stage-orbit-one" aria-hidden="true" />
          <div className="stage-orbit stage-orbit-two" aria-hidden="true" />
          <section className={`player-glass skin-${skin}`} aria-labelledby="player-preview-title">
            <div className="player-topline">
              <span><i className="player-status-dot" /> VINA / NOW PLAYING</span>
              <span className="player-top-right">DEVICE PREVIEW <span aria-hidden="true">···</span></span>
            </div>
            <div className="album-art" aria-label="Decorative abstract album artwork" role="img">
              <div className="art-sun" />
              <div className="art-ring art-ring-one" />
              <div className="art-ring art-ring-two" />
              <div className="art-landscape" />
              <div className="art-grain" />
              <span className="art-caption">AFTER<br />THE GLOW</span>
              <div className="skin-object" aria-hidden="true"><i /><i /><i /></div>
            </div>
            <div className="track-details">
              <div><p className="track-label">A GOOD PLACE TO START</p><h2 id="player-preview-title">The Night We Met</h2><p className="track-artist">Lord Huron <span>·</span> Strange Trails</p></div>
              <button className="heart-button" type="button" aria-label="Save preview track">♡</button>
            </div>
            <div className="waveform" aria-label="Illustrative audio waveform preview" role="img">
              {Array.from({ length: 54 }, (_, index) => <i key={index} className={index < 24 ? 'wave-played' : ''} style={{ height: `${18 + ((index * 37 + 11) % 68)}%` }} />)}
            </div>
            <div className="player-time"><span>02:14</span><span>−02:03</span></div>
            <div className="player-controls" aria-label="Illustrative playback controls">
              <button type="button" aria-label="Previous track">|◀</button>
              <button className="play-button" type="button" aria-label="Play preview">▶</button>
              <button type="button" aria-label="Next track">▶|</button>
              <span className="control-spacer" />
              <span className="quality-pill"><i /> LOSSLESS</span>
            </div>
            <div className="live-readout">
              <div><span className="readout-label">LIVE BITRATE</span><strong>194.7 <small>kb/s</small></strong></div>
              <div className="readout-meter" aria-hidden="true">{Array.from({ length: 17 }, (_, index) => <i key={index} style={{ height: `${22 + ((index * 29 + 9) % 70)}%` }} />)}</div>
              <div className="readout-format"><span>FLAC</span><span>44.1 kHz</span></div>
            </div>
            <div className="skin-selector" aria-label="Choose a player skin">
              {skins.map((item) => <button key={item.id} type="button" className={skin === item.id ? 'skin-choice is-active' : 'skin-choice'} onClick={() => setSkin(item.id)} aria-pressed={skin === item.id}>
                <span className={`skin-swatch swatch-${item.id}`} aria-hidden="true"><i /></span><span>{item.name}</span>
              </button>)}
            </div>
          </section>
          <div className="floating-note note-a"><span className="note-wave" aria-hidden="true">⌁</span><span>the sound, explained</span></div>
          <div className="floating-note note-b"><span className="note-spectrum" aria-hidden="true"><i /><i /><i /><i /><i /></span><span>live frame data</span></div>
        </div>
      </section>

      <section className="sound-section" id="sound" aria-labelledby="sound-title">
        <div className="section-heading">
          <p className="eyebrow">MORE THAN A NUMBER ON A BOX</p>
          <h2 id="sound-title">An honest audio chain.<br /><span>No magic words.</span></h2>
          <p>Audio quality is a journey through hardware and software. Vina shows the route—and the places Android or Bluetooth might change it.</p>
        </div>
        <div className="signal-panel">
          <div className="signal-top"><span>THE SIGNAL PATH</span><span><i className="live-dot" /> TRANSPARENCY ON</span></div>
          <div className="signal-chain">
            <div className="signal-node"><span className="node-icon">01</span><strong>Source</strong><small>Stream / file</small></div>
            <span className="signal-connector" aria-hidden="true"><i /></span>
            <div className="signal-node"><span className="node-icon">02</span><strong>Decode</strong><small>Codec / frames</small></div>
            <span className="signal-connector" aria-hidden="true"><i /></span>
            <div className="signal-node"><span className="node-icon">03</span><strong>Android</strong><small>System mixer</small></div>
            <span className="signal-connector signal-warning" aria-label="Possible format conversion"><i /></span>
            <div className="signal-node signal-output"><span className="node-icon">04</span><strong>Your output</strong><small>Wired / Bluetooth</small></div>
          </div>
          <p className="signal-footnote"><span className="warning-mark">!</span> System mixing can resample. Bluetooth can re-encode. Vina shows what it can observe, not a guarantee of bit-perfect output.</p>
        </div>
      </section>

      <section className="feature-section" aria-labelledby="features-title">
        <div className="section-heading feature-heading">
          <p className="eyebrow">A FEW THINGS THAT MAKE IT DIFFERENT</p>
          <h2 id="features-title">Made for listening.<br /><span>And the curious bits.</span></h2>
        </div>
        <div className="feature-grid">
          {features.map((feature) => <article className="feature-card" key={feature.number}>
            <div className="feature-card-top"><span>{feature.number}</span><span className="feature-mark">{feature.mark}</span></div>
            <h3>{feature.title}</h3>
            <p>{feature.detail}</p>
            <span className="card-glow" aria-hidden="true" />
          </article>)}
        </div>
      </section>

      <section className="skins-section" id="skins" aria-labelledby="skins-title">
        <div className="skins-copy">
          <p className="eyebrow">DRESS FOR THE LISTENING ROOM</p>
          <h2 id="skins-title">Liquid glass.<br /><span>Or a little hi-fi history.</span></h2>
          <p>Let album colors spill into a soft glass interface, or swap the look for a favorite piece of gear. Tap a skin above to try the player preview.</p>
        </div>
        <div className="skin-collection" aria-label="Available player skins">
          <article className="skin-card skin-card-liquid"><span className="skin-card-index">01 / MODERN</span><div className="skin-art glass-art" aria-hidden="true"><i /><i /><i /></div><h3>Liquid glass</h3><p>Album-art color, soft light, airy surfaces.</p></article>
          <article className="skin-card skin-card-cassette"><span className="skin-card-index">02 / ANALOG</span><div className="skin-art cassette-art" aria-hidden="true"><i /><i /><i /></div><h3>Cassette</h3><p>Warm tape-deck nostalgia for your pocket.</p></article>
          <article className="skin-card skin-card-amp"><span className="skin-card-index">03 / CLASSIC</span><div className="skin-art amp-art" aria-hidden="true"><i /><i /><i /></div><h3>Classic Amp</h3><p>Brushed metal, warm meters, tactile knobs.</p></article>
          <article className="skin-card skin-card-receiver"><span className="skin-card-index">04 / TUNED IN</span><div className="skin-art receiver-art" aria-hidden="true"><i /><i /><i /></div><h3>Receiver</h3><p>Cool tuner glass with a hi-fi hardware feel.</p></article>
        </div>
      </section>

      <section className="discovery-band" aria-labelledby="discovery-title">
        <div className="discovery-orb" aria-hidden="true"><span>ON<br />REPEAT</span><i /><i /><i /></div>
        <div className="discovery-copy"><p className="eyebrow">A LITTLE MUSIC CONTEXT</p><h2 id="discovery-title">Find the thread<br />that leads to more.</h2><p>Last.fm is optional. Connect it when you want listening history, tags and listener counts to help shape recommendations like “Because you listened to…” and “On repeat.”</p><div className="tag-row"><span>LISTENER COUNTS</span><span>ARTIST TAGS</span><span>PERSONALIZED ROWS</span></div></div>
        <div className="discovery-list" aria-label="Example recommendation rows"><span className="list-eyebrow">AROUND YOUR LISTENING</span><div className="recommendation"><span className="rec-art rec-art-one">01</span><span><small>BECAUSE YOU LISTENED TO</small><strong>Slowdive</strong></span><span className="rec-arrow">↗</span></div><div className="recommendation"><span className="rec-art rec-art-two">02</span><span><small>ON REPEAT</small><strong>Your recent favorites</strong></span><span className="rec-arrow">↗</span></div><div className="recommendation"><span className="rec-art rec-art-three">03</span><span><small>TAGGED FOR THE MOMENT</small><strong>Dream pop · 1990s</strong></span><span className="rec-arrow">↗</span></div></div>
      </section>

      <section className="frugal-band" aria-labelledby="frugal-title">
        <div className="frugal-copy"><p className="eyebrow">WHEN YOU’RE AWAY FROM WI-FI</p><h2 id="frugal-title">Frugal Mode.<br /><span>More miles per megabyte.</span></h2><p>Dial down stream quality, artwork size and buffering to save mobile data and battery. The details matter here, too.</p></div>
        <div className="frugal-visual" aria-label="Frugal Mode savings preview">
          <div className="frugal-top"><span>FRUGAL MODE</span><span className="toggle-pill"><i /> ON</span></div>
          <div className="savings-readout"><strong>LESS</strong><span>data in<br />the background</span><div className="savings-bars" aria-hidden="true"><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /><i /></div></div>
          <div className="frugal-settings"><span><i /> Stream quality <b>Reduced</b></span><span><i /> Artwork size <b>Compact</b></span><span><i /> Buffering <b>Lean</b></span></div>
          <p className="preview-caption">FEATURE PREVIEW · CONTROLLED IN THE APP</p>
        </div>
      </section>

      <section className="more-section" aria-labelledby="more-title">
        <div className="more-heading"><p className="eyebrow">THE REST OF THE LISTENING SESSION</p><h2 id="more-title">The little details<br /><span>that make a player yours.</span></h2><p>The signal chain is one side of Vina. The rest is built for daily listening, curious detours and the settings you like to keep close.</p></div>
        <div className="more-grid">
          <article className="more-card"><span className="more-number">01 / PLAYBACK</span><h3>Tune it to your ears.</h3><ul><li>Seven-band equalizer with presets and a custom curve</li><li>Gentle loudness normalization and Audiophile Mode</li><li>Gapless playback, adjustable crossfade and a car profile</li><li>Queue controls, timed lyrics and playback details</li></ul></article>
          <article className="more-card"><span className="more-number">02 / DISCOVERY + MIXES</span><h3>Follow the next thread.</h3><ul><li>Search the catalog; explore artists, albums, genres and moods</li><li>Regional charts, Infinite Play and recommendations</li><li>Create and edit playlists; import or export them as JSON</li><li>Quick Mix or prompt-led AI Mix in 10, 20 or 30 songs</li></ul></article>
          <article className="more-card"><span className="more-number">03 / YOUR SETUP</span><h3>Keep the useful things.</h3><ul><li>Optional compatible HTTPS music source</li><li>Last.fm and AI provider connections are opt-in</li><li>Android home-screen widget and player skins</li><li>In-app updates, update notifications and crash-report tools</li></ul></article>
        </div>
      </section>

      <section className="download-section" id="download" aria-labelledby="download-title">
        <div className="download-orbit" aria-hidden="true"><i /><i /><i /></div>
        <div className="download-copy"><p className="eyebrow">AN ANDROID PLAYER, STILL GROWING</p><h2 id="download-title">Come listen.<br /><span>Tell me what you think.</span></h2><p>Vina is a work in progress. Download the latest APK, explore the source, or share feedback with the project.</p><div className="download-actions"><a className="primary-button" href={APK_DOWNLOAD}>Download Vina for Android <span aria-hidden="true">↓</span></a><a className="text-link" href={GITHUB_ISSUES} target="_blank" rel="noreferrer">Share feedback <span aria-hidden="true">↗</span></a></div><p className="apk-note">Direct APK from GitHub Releases. Android may ask you to approve installation from your browser.</p></div>
        <aside className="release-card" aria-label="Android download details"><span className="release-label"><i className="live-dot" /> LATEST ANDROID BUILD</span><span className="release-file">melodyne.apk</span><a href={APK_DOWNLOAD} className="release-link">Download directly <span aria-hidden="true">↗</span></a><a href={`${REPOSITORY}/releases`} className="release-secondary" target="_blank" rel="noreferrer">Release history</a></aside>
      </section>

      <footer className="site-footer"><a className="footer-brand" href="#top">vina<span>.</span></a><span>Music, with the signal chain in view.</span><div><a href={REPOSITORY} target="_blank" rel="noreferrer">GitHub ↗</a><a href={APK_DOWNLOAD}>Android APK ↓</a></div></footer>
    </main>
  )
}

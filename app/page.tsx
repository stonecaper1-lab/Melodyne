'use client'

import { useState } from 'react'

const APK_DOWNLOAD = 'https://github.com/stonecaper1-lab/Melodyne/releases/latest/download/melodyne.apk'
const REPOSITORY = 'https://github.com/stonecaper1-lab/Melodyne'

const tracks = [
  { title: 'Midnight City', artist: 'M83', album: 'Hurry Up, We’re Dreaming', time: '04:03', bitrate: '320' },
  { title: 'Nightcall', artist: 'Kavinsky', album: 'OutRun', time: '04:18', bitrate: '320' },
  { title: 'A Real Hero', artist: 'College & Electric Youth', album: 'Drive OST', time: '04:27', bitrate: '256' },
  { title: 'Under Your Spell', artist: 'Desire', album: 'II', time: '03:34', bitrate: '320' },
  { title: 'The Bay', artist: 'Metronomy', album: 'The English Riviera', time: '03:52', bitrate: '256' },
]

const equalizer = [18, 32, 53, 39, 71, 48, 84, 58, 41, 76, 49, 66, 32, 57, 79, 45, 62, 36, 72, 48, 83, 55, 39, 69, 47, 78, 54, 34, 63, 44, 73, 51, 86, 59, 38, 67, 46, 75, 53, 31]
const bands = ['60', '170', '310', '600', '1K', '3K', '6K']

const featureGroups = [
  {
    number: '01',
    eyebrow: 'SHAPE THE SOUND',
    title: 'Your ears, your curve.',
    description: 'Fine-tune the signal when you want to. Or step aside and let it through untouched.',
    features: [
      ['Seven-band equalizer', 'Shape seven frequency bands with built-in presets or drag out a custom curve of your own.'],
      ['Audiophile Mode', 'Bypass Melodyne’s EQ, loudness shaping, stereo width and crossfade for a cleaner output path.'],
      ['Loudness tools', 'Optional track normalization helps even out jumps; gentle mode only turns louder songs down.'],
      ['Gapless + crossfade', 'Keep compatible tracks seamless, or blend between songs with an adjustable 1–12 second fade.'],
      ['Signal-chain inspector', 'See what is shaping your output and why Audiophile Mode is or is not bit-transparent.'],
      ['Car & Bluetooth tuning', 'Use the car profile, adjust stereo width and find practical Bluetooth guidance in Settings.'],
    ],
  },
  {
    number: '02',
    eyebrow: 'FIND YOUR NEXT SONG',
    title: 'Follow the feeling.',
    description: 'Start with a song, an artist, a genre, a mood—or just see where listening takes you.',
    features: [
      ['Search that gets around', 'Look up songs, artists, genres and more across Melodyne’s built-in music catalog.'],
      ['Moods & charts', 'Explore mood-based mixes, regional top tens and trending tracks. Last.fm adds richer tags.'],
      ['A little déjà vu', 'Pick up where you left off with recent plays, On repeat and “Because you listened” rows.'],
      ['Artist, album & genre pages', 'Follow credits and tags from a track into related artists, albums, composers and genres.'],
      ['Infinite play', 'When the queue runs low, let similar recommendations keep the listening session moving.'],
      ['Lyrics & queue', 'Open time-synced lyrics when available, inspect the queue and jump straight to any song.'],
    ],
  },
  {
    number: '03',
    eyebrow: 'MAKE IT YOUR MIX',
    title: 'A playlist with a point of view.',
    description: 'Save your own collections, keep the useful smart ones, or tell AI Mix what the moment sounds like.',
    features: [
      ['AI Mix', 'Describe a mood or moment and make a 10, 20 or 30-song playlist. Play it or save it to your library.'],
      ['Quick Mix', 'Build a mix without configuring an AI provider. Bring your own OpenAI-compatible provider for prompt-based mixes.'],
      ['Your provider, your key', 'Connect a provider such as OpenRouter or Groq. The key stays on your phone and prompts go to that provider.'],
      ['Playlists that stay yours', 'Create, rename, reorder and edit playlists; shuffle them or save a smart playlist as your own.'],
      ['Import & export', 'Move your Melodyne playlist library in or out as a JSON file.'],
      ['Smart playlists', 'Let listening history and track length shape useful collections automatically.'],
    ],
  },
  {
    number: '04',
    eyebrow: 'THE DETAILS THAT MATTER',
    title: 'A player that stays personal.',
    description: 'Keep the practical stuff close, the account stuff optional, and the interface yours.',
    features: [
      ['Live bitrate & quality', 'See live and average bitrate as a stream plays, with playback quality and recent signal details.'],
      ['Last.fm, if you want it', 'Bring your own API key to sign in, scrobble plays, and unlock richer tags, moods and personalized rows.'],
      ['Skins & Liquid glow', 'Choose a player skin, sample color from album art and turn the soft ambient glow on or off.'],
      ['Now Playing widget', 'Pin playback controls to your Android home screen and get back to the current song quickly.'],
      ['In-app updates', 'Check for releases, download from inside the app and let Android handle installation. Optional background update notifications keep you posted.'],
      ['Bring your own music source', 'Use Melodyne’s built-in catalog or point it to your own compatible HTTPS music server.'],
      ['Crash details & welcome tour', 'Copy or clear the last crash report, or replay the welcome tour from Settings.'],
      ['No sign-up, ads or analytics', 'Melodyne is free to use, with no trackers, ads, analytics or account required. Browse the source and build it yourself.'],
    ],
  },
]

const quickFacts = [
  ['7 bands', 'tone shaping'],
  ['∞ play', 'keep it going'],
  ['0 ads', 'no account'],
  ['Android', 'built for it'],
]

export default function Home() {
  const [playing, setPlaying] = useState(true)
  const [activeTrack, setActiveTrack] = useState(0)
  const [volume, setVolume] = useState(72)
  const track = tracks[activeTrack]

  function changeTrack(direction: number) {
    setActiveTrack((current) => (current + direction + tracks.length) % tracks.length)
  }

  return (
    <main className="desktop" id="top">
      <header className="topbar">
        <a className="wordmark" href="#top" aria-label="Melodyne home"><span className="wordmark-icon" aria-hidden="true">m</span> melodyne<span className="wordmark-period">.</span></a>
        <nav className="topbar-nav" aria-label="Main navigation">
          <a href="#features">FEATURES</a>
          <a href="#about">ABOUT</a>
          <a className="source-link" href={REPOSITORY} target="_blank" rel="noreferrer">SOURCE <span aria-hidden="true">↗</span></a>
        </nav>
      </header>

      <section className="intro" aria-labelledby="intro-title">
        <p className="intro-kicker"><span className="status-light" /> YOUR MUSIC. YOUR RULES.</p>
        <h1 id="intro-title">Press play.<br /><span>Go somewhere.</span></h1>
        <p className="intro-copy">An Android music player for the long way home. Tune the sound, follow a mood, or let the song be. No ads, no accounts, just the good stuff.</p>
        <div className="intro-actions">
          <a className="download-button" href={APK_DOWNLOAD} aria-label="Download the latest Melodyne Android APK directly">DOWNLOAD THE ANDROID APK <span aria-hidden="true">↓</span></a>
          <span className="download-note">DIRECT APK · LATEST RELEASE</span>
        </div>
      </section>

      <section className="winamp" aria-label="Melodyne player interface preview">
        <div className="titlebar">
          <div className="titlebar-brand"><span className="mini-spectrum" aria-hidden="true"><i /><i /><i /><i /></span> MELODYNE PLAYER <span className="version">2.0</span></div>
          <div className="window-controls" aria-hidden="true"><span /><span /><span /></div>
        </div>
        <div className="player-screen">
          <div className="now-playing-line"><span>PLAYER PREVIEW</span><span className="stereo-badge">STEREO</span></div>
          <div className="lcd-row">
            <div className="lcd-time" aria-label={`${track.time} track duration`}>{playing ? '02:14' : '00:00'}</div>
            <div className="lcd-track"><strong>{track.title}</strong><span>{track.artist} — {track.album}</span></div>
            <div className="lcd-format"><strong>{track.bitrate}</strong><span>KBPS</span></div>
          </div>
          <div className={`spectrum ${playing ? 'is-playing' : ''}`} aria-label={playing ? 'Decorative audio visualizer active' : 'Decorative audio visualizer paused'} role="img">
            {equalizer.map((height, index) => <i key={index} style={{ '--bar-height': `${height}%`, '--bar-delay': `${(index % 11) * -0.13}s` } as React.CSSProperties} />)}
          </div>
          <div className="progress-line"><span>02:14</span><div className="progress-track" aria-label="Decorative track progress"><i /></div><span>{track.time}</span></div>
        </div>
        <div className="transport">
          <div className="transport-buttons">
            <button className="tiny-control" aria-label="Previous preview track" onClick={() => changeTrack(-1)}>◀|</button>
            <button className="transport-button" aria-label={playing ? 'Pause preview' : 'Play preview'} onClick={() => setPlaying(!playing)}>{playing ? 'Ⅱ' : '▶'}</button>
            <button className="tiny-control" aria-label="Next preview track" onClick={() => changeTrack(1)}>|▶</button>
            <a className="tiny-control eject" href={APK_DOWNLOAD} aria-label="Download the latest Android release">⏏</a>
          </div>
          <label className="volume-control"><span aria-hidden="true">VOL</span><input aria-label="Preview volume display" type="range" min="0" max="100" value={volume} onChange={(event) => setVolume(Number(event.target.value))} style={{ '--range-progress': `${volume}%` } as React.CSSProperties} /></label>
        </div>
        <div className="equalizer-panel">
          <div className="panel-label"><span><b className="panel-led" /> EQUALIZER PREVIEW</span><span className="eq-preset">7-BAND <strong>EQ</strong></span></div>
          <div className="eq-sliders" aria-label="Seven-band equalizer visual preview">
            {bands.map((band, index) => <div className="eq-band" key={band}><div className="eq-track"><i style={{ bottom: `${[54, 68, 47, 76, 60, 44, 65][index]}%` }} /></div><span>{band}</span></div>)}
          </div>
        </div>
        <div className="playlist">
          <div className="playlist-heading"><span>EXAMPLE PLAYLIST</span><span>{String(tracks.length).padStart(2, '0')} TRACKS · 20:14</span></div>
          <div className="playlist-columns"><span>#</span><span>TITLE / ARTIST</span><span>TIME</span></div>
          <ol className="track-list" aria-label="Example playlist preview">
            {tracks.map((item, index) => <li key={item.title} className={activeTrack === index ? 'selected' : ''}>
              <button className="playlist-track" onClick={() => { setActiveTrack(index); setPlaying(true) }} aria-current={activeTrack === index ? 'true' : undefined}>
                <span className="track-number">{activeTrack === index && playing ? <span className="playing-mark" aria-label="Selected">▶</span> : String(index + 1).padStart(2, '0')}</span>
                <span className="track-meta"><strong>{item.title}</strong><small>{item.artist} <i>·</i> {item.album}</small></span>
                <span className="track-duration">{item.time}</span>
              </button>
            </li>)}
          </ol>
          <div className="playlist-footer"><span>♫ &nbsp; MELODYNE PLAYER PREVIEW</span><span>EQ <b>7-BAND</b> &nbsp; MIX <b>YOUR OWN</b></span></div>
        </div>
        <div className="window-status"><span><i /> READY TO ROAM</span><span>ANDROID <b>·</b> FREE SOFTWARE</span></div>
      </section>

      <section className="quick-facts" aria-label="Melodyne at a glance">
        {quickFacts.map(([value, label]) => <div className="quick-fact" key={value}><strong>{value}</strong><span>{label}</span></div>)}
      </section>

      <section className="features-intro" id="features" aria-labelledby="features-title">
        <div>
          <span className="section-index">THE WHOLE LISTENING SESSION</span>
          <h2 id="features-title">More in the music.<br /><span>Less in your way.</span></h2>
        </div>
        <p>Explore what Melodyne can actually do, from the signal chain to the last song in the queue. The interface above is a visual preview; the full experience lives in the Android app.</p>
      </section>

      <div className="feature-groups">
        {featureGroups.map((group) => <section className="feature-group" key={group.number} aria-labelledby={`group-${group.number}`}>
          <div className="feature-heading">
            <span className="section-index">{group.number} / {group.eyebrow}</span>
            <h3 id={`group-${group.number}`}>{group.title}</h3>
            <p>{group.description}</p>
          </div>
          <div className="feature-grid">
            {group.features.map(([title, description], index) => <article className="feature-card" key={title}>
              <span className="feature-index">{group.number}.{String(index + 1).padStart(2, '0')}</span>
              <h4>{title}</h4>
              <p>{description}</p>
            </article>)}
          </div>
        </section>)}
      </div>

      <section className="download-panel" id="download" aria-labelledby="download-title">
        <div className="download-copy">
          <span className="section-index">TAKE THE MUSIC WITH YOU</span>
          <h2 id="download-title">Ready when<br />you are.</h2>
          <p>Get the latest Melodyne release for Android. The APK downloads directly from GitHub Releases—no store detour.</p>
          <a className="download-button" href={APK_DOWNLOAD} aria-label="Download the latest Melodyne Android APK directly">DOWNLOAD LATEST APK <span aria-hidden="true">↓</span></a>
        </div>
        <div className="download-details">
          <span className="download-led"><i /> RELEASE FILE</span>
          <strong>melodyne.apk</strong>
          <code>{APK_DOWNLOAD}</code>
          <a href={REPOSITORY + '/releases'} target="_blank" rel="noreferrer">View release notes and older builds <span aria-hidden="true">↗</span></a>
        </div>
      </section>

      <section className="honesty-panel" id="about" aria-labelledby="honesty-title">
        <div><span className="section-index">A NOTE FROM THE DEVELOPER</span><h2 id="honesty-title">Honest about<br />the edges.</h2></div>
        <div className="honesty-copy">
          <p>Melodyne is free software with no sign-up, advertisements, trackers or analytics. Optional AI Mix and Last.fm features use services and keys you choose to configure.</p>
          <p><strong>Not supported yet:</strong> playing music files stored locally on your phone. Melodyne currently plays from its built-in online catalog. Android may also ask you to approve APK installation from your browser the first time.</p>
          <a href={REPOSITORY} target="_blank" rel="noreferrer">EXPLORE THE SOURCE CODE <span aria-hidden="true">↗</span></a>
        </div>
      </section>

      <footer className="footer"><span>MELODYNE / FREE SOFTWARE</span><a href={APK_DOWNLOAD}>GET THE ANDROID APK ↓</a></footer>
    </main>
  )
}

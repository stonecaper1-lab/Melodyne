'use client'

import { useState } from 'react'

const tracks = [
  { title: 'Midnight City', artist: 'M83', album: 'Hurry Up, We’re Dreaming', time: '04:03', bitrate: '320' },
  { title: 'Nightcall', artist: 'Kavinsky', album: 'OutRun', time: '04:18', bitrate: '320' },
  { title: 'A Real Hero', artist: 'College & Electric Youth', album: 'Drive OST', time: '04:27', bitrate: '256' },
  { title: 'Under Your Spell', artist: 'Desire', album: 'II', time: '03:34', bitrate: '320' },
  { title: 'The Bay', artist: 'Metronomy', album: 'The English Riviera', time: '03:52', bitrate: '256' },
]

const equalizer = [18, 32, 53, 39, 71, 48, 84, 58, 41, 76, 49, 66, 32, 57, 79, 45, 62, 36, 72, 48, 83, 55, 39, 69, 47, 78, 54, 34, 63, 44, 73, 51, 86, 59, 38, 67, 46, 75, 53, 31]

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
        <a className="wordmark" href="#top" aria-label="Melodyne home"><span className="wordmark-icon">m</span> melodyne<span className="wordmark-period">.</span></a>
        <span className="topbar-note">THE LITTLE PLAYER WITH BIG FEELINGS</span>
        <a className="source-link" href="https://github.com/stonecaper1-lab/Melodyne">SOURCE <span aria-hidden="true">↗</span></a>
      </header>

      <section className="intro" aria-labelledby="intro-title">
        <p className="intro-kicker"><span className="status-light" /> YOUR MUSIC. YOUR RULES.</p>
        <h1 id="intro-title">Press play.<br /><span>Go somewhere.</span></h1>
        <p className="intro-copy">A little Android music player for the long way home. No ads, no accounts, just the good stuff.</p>
      </section>

      <section className="winamp" aria-label="Melodyne music player">
        <div className="titlebar">
          <div className="titlebar-brand"><span className="mini-spectrum" aria-hidden="true"><i /><i /><i /><i /></span> MELODYNE PLAYER <span className="version">2.0</span></div>
          <div className="window-controls" aria-label="Window controls"><span /><span /><span /></div>
        </div>
        <div className="player-screen">
          <div className="now-playing-line"><span>NOW PLAYING</span><span className="stereo-badge">STEREO</span></div>
          <div className="lcd-row">
            <div className="lcd-time" aria-label={`${track.time} track duration`}>{playing ? '02:14' : '00:00'}</div>
            <div className="lcd-track"><strong>{track.title}</strong><span>{track.artist} — {track.album}</span></div>
            <div className="lcd-format"><strong>{track.bitrate}</strong><span>KBPS</span></div>
          </div>
          <div className={`spectrum ${playing ? 'is-playing' : ''}`} aria-label={playing ? 'Audio visualizer active' : 'Audio visualizer paused'} role="img">
            {equalizer.map((height, index) => <i key={index} style={{ '--bar-height': `${height}%`, '--bar-delay': `${(index % 11) * -0.13}s` } as React.CSSProperties} />)}
          </div>
          <div className="progress-line"><span>02:14</span><div className="progress-track" aria-label="Track progress"><i /></div><span>{track.time}</span></div>
        </div>
        <div className="transport">
          <div className="transport-buttons">
            <button className="tiny-control" aria-label="Previous track" onClick={() => changeTrack(-1)}>◀|</button>
            <button className="transport-button" aria-label={playing ? 'Pause playback' : 'Start playback'} onClick={() => setPlaying(!playing)}>{playing ? 'Ⅱ' : '▶'}</button>
            <button className="tiny-control" aria-label="Next track" onClick={() => changeTrack(1)}>|▶</button>
            <button className="tiny-control eject" aria-label="Download latest Android release" onClick={() => window.open('https://github.com/stonecaper1-lab/Melodyne/releases/latest', '_blank', 'noopener,noreferrer')}>⏏</button>
          </div>
          <label className="volume-control"><span aria-hidden="true">VOL</span><input aria-label="Volume" type="range" min="0" max="100" value={volume} onChange={(event) => setVolume(Number(event.target.value))} style={{ '--range-progress': `${volume}%` } as React.CSSProperties} /></label>
        </div>
        <div className="equalizer-panel">
          <div className="panel-label"><span><b className="panel-led" /> EQUALIZER</span><span className="eq-preset">PRESET: <strong>MELODYNE</strong></span></div>
          <div className="eq-sliders" aria-label="Seven band equalizer preview">
            {[54, 68, 47, 76, 60, 44, 65].map((height, index) => <div className="eq-band" key={index}><div className="eq-track"><i style={{ bottom: `${height}%` }} /></div><span>{['60', '170', '310', '600', '1K', '3K', '6K'][index]}</span></div>)}
          </div>
        </div>
        <div className="playlist">
          <div className="playlist-heading"><span>PLAYLIST EDITOR</span><span>{String(tracks.length).padStart(2, '0')} TRACKS · 20:14</span></div>
          <div className="playlist-columns"><span>#</span><span>TITLE / ARTIST</span><span>TIME</span></div>
          <ol className="track-list" aria-label="Playlist">
            {tracks.map((item, index) => <li key={item.title} className={activeTrack === index ? 'selected' : ''}>
              <button className="playlist-track" onClick={() => { setActiveTrack(index); setPlaying(true) }} aria-current={activeTrack === index ? 'true' : undefined}>
                <span className="track-number">{activeTrack === index && playing ? <span className="playing-mark" aria-label="Playing">▶</span> : String(index + 1).padStart(2, '0')}</span>
                <span className="track-meta"><strong>{item.title}</strong><small>{item.artist} <i>·</i> {item.album}</small></span>
                <span className="track-duration">{item.time}</span>
              </button>
            </li>)}
          </ol>
          <div className="playlist-footer"><span>♫ &nbsp; LOCAL LIBRARY</span><span>SHUFFLE <b>OFF</b> &nbsp; REPEAT <b>ALL</b></span></div>
        </div>
        <div className="window-status"><span><i /> READY TO ROAM</span><span>MADE FOR ANDROID <b>·</b> FREE SOFTWARE</span></div>
      </section>

      <section className="below-player" id="about">
        <div><span className="section-index">01 — THE FEELING</span><h2>Made for<br />deep listening.</h2></div>
        <p>Seven bands to shape the sound. Your library, always yours. A simple, honest player that gets out of the way and lets the song take over.</p>
      </section>
      <footer className="footer"><span>MELODYNE / 2026</span><a href="https://github.com/stonecaper1-lab/Melodyne/releases/latest">GET THE ANDROID APP ↗</a></footer>
    </main>
  )
}

import { useEffect, useRef, useState } from 'react'
import Hls from 'hls.js'
import type { Item, PlaybackInfo, Source } from '../lib/types'
import { useStore } from '../lib/store'
import { clock, episodeLabel } from '../lib/format'
import { back, go } from '../lib/router'
import { Icon } from '../components/ui'

type Mode = 'direct' | 'hls'

export function Player({ source, id, startAt }: { source: Source; id: string; startAt?: number }) {
  const prefs = useStore().prefs
  const video = useRef<HTMLVideoElement>(null)
  const root = useRef<HTMLDivElement>(null)
  const hls = useRef<Hls | null>(null)
  const [item, setItem] = useState<Item>()
  const [pb, setPb] = useState<PlaybackInfo>()
  const [next, setNext] = useState<Item>()
  const [mode, setMode] = useState<Mode>('direct')
  const [audio, setAudio] = useState<number>()
  const [sub, setSub] = useState<number>()
  const [subUrl, setSubUrl] = useState<string>()
  const [error, setError] = useState('')
  const [playing, setPlaying] = useState(false)
  const [waiting, setWaiting] = useState(true)
  const [pos, setPos] = useState(0)
  const [dur, setDur] = useState(0)
  const [buf, setBuf] = useState(0)
  const [scrub, setScrub] = useState<number | null>(null)
  const [ui, setUi] = useState(true)
  const [menu, setMenu] = useState(false)
  const [fill, setFill] = useState(false)
  /** Where to continue from after (re)loading the stream, in seconds. */
  const pending = useRef(0)
  const started = useRef(false)
  const live = useRef({ item, pb, pos: 0, paused: true })
  live.current = { item, pb, pos, paused: !playing }

  // Load the title, its stream and (for episodes) what comes next.
  useEffect(() => {
    let alive = true
    setError('')
    setItem(undefined)
    setPb(undefined)
    setNext(undefined)
    started.current = false
    ;(async () => {
      try {
        const it = await source.item(id)
        const info = await source.playback(it)
        if (!alive) return
        pending.current = (startAt ?? it.resumeMs ?? 0) / 1000
        const wanted = prefs.subtitleLang && info.subtitles.find((s) => s.url && (s.language || '').toLowerCase().startsWith(prefs.subtitleLang.slice(0, 2)))
        setSub(wanted ? wanted.index : undefined)
        setAudio(undefined)
        setMode(prefs.alwaysConvert && info.hlsUrl ? 'hls' : 'direct')
        setItem(it)
        setPb(info)
        if (it.kind === 'episode' && it.seriesId && it.seasonId) {
          const eps = await source.episodes(it.seriesId, it.seasonId).catch(() => [] as Item[])
          const n = eps.findIndex((e) => e.id === it.id)
          if (alive && n >= 0 && eps[n + 1]) setNext(eps[n + 1])
        }
      } catch (e: any) {
        if (alive) setError(e?.message || 'Could not start playback.')
      }
    })()
    return () => {
      alive = false
    }
  }, [source, id])

  // Attach the stream. Direct play first; if the device can't play the file, ask the server to convert it.
  useEffect(() => {
    const v = video.current
    if (!v || !pb) return
    setWaiting(true)
    hls.current?.destroy()
    hls.current = null
    const fail = (why: string) => {
      if (mode === 'direct' && pb.hlsUrl) {
        pending.current = v.currentTime || pending.current
        setMode('hls')
      } else {
        setError(why)
      }
    }
    if (mode === 'direct') {
      v.src = pb.directUrl
    } else {
      const url = pb.hlsUrl!(audio)
      if (v.canPlayType('application/vnd.apple.mpegurl')) {
        v.src = url
      } else if (Hls.isSupported()) {
        const h = new Hls({ maxBufferLength: 30 })
        hls.current = h
        h.on(Hls.Events.ERROR, (_e, data) => {
          if (data.fatal) setError("This video can't be played on this device, and the server couldn't convert it.")
        })
        h.loadSource(url)
        h.attachMedia(v)
      } else {
        setError("This device can't play this video format.")
        return
      }
    }
    const onMeta = () => {
      if (pending.current > 1 && isFinite(v.duration) && pending.current < v.duration - 5) v.currentTime = pending.current
      pending.current = 0
      v.play().catch(() => setPlaying(false))
    }
    const onErr = () => fail("This video can't be played on this device.")
    v.addEventListener('loadedmetadata', onMeta)
    v.addEventListener('error', onErr)
    v.load()
    return () => {
      v.removeEventListener('loadedmetadata', onMeta)
      v.removeEventListener('error', onErr)
    }
  }, [pb, mode, audio])

  // Stop cleanly: tell the server where we got to and release the stream.
  useEffect(
    () => () => {
      const { item: it, pb: p, pos: at } = live.current
      if (it && p && started.current) source.report('stop', it, p, at * 1000, true)
      hls.current?.destroy()
      const v = video.current
      if (v) {
        v.removeAttribute('src')
        v.load()
      }
    },
    [source, id],
  )

  // Progress reports every 10 seconds while playing.
  useEffect(() => {
    const t = setInterval(() => {
      const { item: it, pb: p, pos: at, paused } = live.current
      if (it && p && started.current && !paused) source.report('progress', it, p, at * 1000, false)
    }, 10000)
    return () => clearInterval(t)
  }, [source])

  // Subtitles: fetched as WebVTT and attached from a local blob, so they work across origins.
  useEffect(() => {
    const track = pb?.subtitles.find((s) => s.index === sub)
    if (!track?.url) {
      setSubUrl(undefined)
      return
    }
    let alive = true
    let made: string | undefined
    fetch(track.url)
      .then((r) => r.text())
      .then((text) => {
        if (!alive) return
        made = URL.createObjectURL(new Blob([text], { type: 'text/vtt' }))
        setSubUrl(made)
      })
      .catch(() => alive && setSubUrl(undefined))
    return () => {
      alive = false
      if (made) URL.revokeObjectURL(made)
    }
  }, [pb, sub])
  useEffect(() => {
    const v = video.current
    if (!v) return
    const show = () => {
      for (let i = 0; i < v.textTracks.length; i++) v.textTracks[i].mode = subUrl ? 'showing' : 'disabled'
    }
    show()
    const t = setTimeout(show, 300)
    return () => clearTimeout(t)
  }, [subUrl])

  // Hide the controls after a few quiet seconds.
  const hideTimer = useRef<number>()
  const wake = () => {
    setUi(true)
    window.clearTimeout(hideTimer.current)
    hideTimer.current = window.setTimeout(() => setUi(false), 4500)
  }
  useEffect(() => {
    if (!playing || menu) {
      window.clearTimeout(hideTimer.current)
      setUi(true)
    } else wake()
    return () => window.clearTimeout(hideTimer.current)
  }, [playing, menu])

  const seekTo = (sec: number) => {
    const v = video.current
    if (!v || !isFinite(v.duration)) return
    v.currentTime = Math.max(0, Math.min(v.duration - 1, sec))
    setPos(v.currentTime)
  }
  const seekBy = (d: number) => seekTo((video.current?.currentTime || 0) + d)
  const toggle = () => {
    const v = video.current
    if (!v) return
    if (v.paused) v.play().catch(() => {})
    else v.pause()
  }

  // Remote and keyboard.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const code = e.keyCode
      const hidden = !ui
      wake()
      if (code === 415) { video.current?.play(); e.preventDefault(); return }
      if (code === 19) { video.current?.pause(); e.preventDefault(); return }
      if (code === 10252 || code === 179) { toggle(); e.preventDefault(); return }
      if (code === 417 || code === 228) { seekBy(prefs.skipForward); e.preventDefault(); return }
      if (code === 412 || code === 227) { seekBy(-prefs.skipBack); e.preventDefault(); return }
      if (code === 413) { back(); e.preventDefault(); return }
      if (menu) return
      if (hidden) {
        // First press only wakes the controls; left/right also seek.
        if (e.key === 'ArrowLeft') seekBy(-prefs.skipBack)
        else if (e.key === 'ArrowRight') seekBy(prefs.skipForward)
        else if (e.key === ' ' ) toggle()
        const sel = e.key === 'ArrowLeft' || e.key === 'ArrowRight' ? '.seek' : '[data-autofocus]'
        ;(root.current?.querySelector(sel) as HTMLElement | null)?.focus()
        e.preventDefault()
        e.stopPropagation()
      }
    }
    const onBack = (e: Event) => {
      if (menu) {
        setMenu(false)
        e.preventDefault()
      }
    }
    window.addEventListener('keydown', onKey, true)
    window.addEventListener('app-back', onBack)
    return () => {
      window.removeEventListener('keydown', onKey, true)
      window.removeEventListener('app-back', onBack)
    }
  }, [ui, menu, prefs.skipBack, prefs.skipForward])

  const onEnded = () => {
    const { item: it, pb: p } = live.current
    if (it && p) source.report('stop', it, p, (video.current?.duration || 0) * 1000, true)
    started.current = false
    if (prefs.autoplayNext && next) go(`/play/${encodeURIComponent(next.id)}?t=0`, true)
    else back()
  }

  // Scrubbing with touch or mouse.
  const rail = useRef<HTMLDivElement>(null)
  const fractionAt = (clientX: number) => {
    const r = rail.current!.getBoundingClientRect()
    return Math.max(0, Math.min(1, (clientX - r.left) / r.width))
  }
  const shown = scrub != null ? scrub * dur : pos
  const pct = dur ? (shown / dur) * 100 : 0

  if (error) {
    return (
      <div className="player">
        <div className="pl-msg">
          <h2>Can't play this</h2>
          <p>{error}</p>
          <button className="btn primary" data-autofocus onClick={back}>Back</button>
        </div>
      </div>
    )
  }

  const subtitle = item ? [item.seriesTitle, episodeLabel(item.season, item.episode), item.kind === 'movie' ? item.year : ''].filter(Boolean).join(' · ') : ''
  return (
    <div className="player" ref={root} onMouseMove={wake} onTouchStart={wake}>
      <style>{`.player video::cue{font-size:${prefs.subtitleSize}%}`}</style>
      <video
        ref={video}
        className={fill ? 'fill' : ''}
        playsInline
        autoPlay
        onClick={() => (ui ? setUi(false) : wake())}
        onPlay={() => {
          setPlaying(true)
          if (!started.current && item && pb) {
            started.current = true
            source.report('start', item, pb, (video.current?.currentTime || 0) * 1000, false)
          }
        }}
        onPause={() => setPlaying(false)}
        onWaiting={() => setWaiting(true)}
        onPlaying={() => setWaiting(false)}
        onCanPlay={() => setWaiting(false)}
        onTimeUpdate={(e) => {
          const v = e.currentTarget
          setPos(v.currentTime)
          if (v.buffered.length) setBuf(v.buffered.end(v.buffered.length - 1))
        }}
        onDurationChange={(e) => setDur(isFinite(e.currentTarget.duration) ? e.currentTarget.duration : 0)}
        onEnded={onEnded}
      >
        {subUrl ? <track key={subUrl} kind="subtitles" src={subUrl} default /> : null}
      </video>
      {waiting ? <div className="spinner pl-spin" /> : null}

      <div className={'pl-ui' + (ui ? '' : ' hide')} onClick={(e) => { if (e.target === e.currentTarget) setUi(false) }}>
        <div className="pl-top">
          <button className="btn icon" onClick={back} aria-label="Back"><Icon name="back" /></button>
          <div className="t">
            <b>{item?.title || ''}</b>
            {subtitle ? <span>{subtitle}</span> : null}
          </div>
        </div>
        <div className="pl-mid" onClick={(e) => { if (e.target === e.currentTarget) setUi(false) }}>
          <button className="btn" onClick={() => seekBy(-prefs.skipBack)} aria-label={`Back ${prefs.skipBack} seconds`}><Icon name="rewind" /></button>
          <button className="btn big" data-autofocus onClick={toggle} aria-label={playing ? 'Pause' : 'Play'}><Icon name={playing ? 'pause' : 'play'} /></button>
          <button className="btn" onClick={() => seekBy(prefs.skipForward)} aria-label={`Forward ${prefs.skipForward} seconds`}><Icon name="forward" /></button>
        </div>
        <div className="pl-bottom">
          <div
            className="seek" ref={rail} tabIndex={0} role="slider" aria-label="Seek" aria-valuemin={0} aria-valuemax={Math.round(dur)} aria-valuenow={Math.round(shown)}
            onKeyDown={(e) => {
              if (e.key === 'ArrowLeft') { seekBy(-prefs.skipBack); e.preventDefault() }
              else if (e.key === 'ArrowRight') { seekBy(prefs.skipForward); e.preventDefault() }
              else if (e.key === 'Enter') { toggle(); e.preventDefault() }
            }}
            onPointerDown={(e) => { e.currentTarget.setPointerCapture(e.pointerId); setScrub(fractionAt(e.clientX)) }}
            onPointerMove={(e) => { if (scrub != null) setScrub(fractionAt(e.clientX)) }}
            onPointerUp={(e) => { if (scrub != null) { seekTo(fractionAt(e.clientX) * dur); setScrub(null) } }}
            onPointerCancel={() => setScrub(null)}
          >
            <div className="rail">
              <div className="buf" style={{ width: `${dur ? (buf / dur) * 100 : 0}%` }} />
              <div className="pos" style={{ width: `${pct}%` }} />
            </div>
            <div className="knob" style={{ left: `${pct}%` }} />
          </div>
          <div className="times">
            <span>{clock(shown * 1000)}</span>
            <span>{dur ? '-' + clock((dur - shown) * 1000) : ''}</span>
          </div>
          <div className="pl-row">
            <button className="btn small" onClick={() => setMenu(true)}><Icon name="captions" /><span>Audio &amp; Subtitles</span></button>
            <button className="btn small" onClick={() => setFill(!fill)}><Icon name="expand" /><span>{fill ? 'Fit' : 'Fill'}</span></button>
            <div className="grow" />
            {next ? (
              <button className="btn small" onClick={() => go(`/play/${encodeURIComponent(next.id)}?t=0`, true)}>
                <Icon name="next" /><span>Next episode</span>
              </button>
            ) : null}
          </div>
        </div>
      </div>

      {menu && pb ? (
        <div className="overlay" style={{ background: 'transparent', backdropFilter: 'none', WebkitBackdropFilter: 'none' }} data-focus-scope onClick={(e) => { if (e.target === e.currentTarget) setMenu(false) }}>
          <div className="menu">
            <h4>Subtitles</h4>
            <Choice label="Off" on={sub == null} auto onPick={() => setSub(undefined)} />
            {pb.subtitles.map((s) => (
              <Choice key={s.index} label={s.url ? s.label : `${s.label} (not supported here)`} on={sub === s.index} disabled={!s.url} onPick={() => setSub(s.index)} />
            ))}
            {pb.audio.length > 1 ? (
              <>
                <h4>Audio</h4>
                {pb.audio.map((a) => (
                  <Choice key={a.index} label={a.label} on={audio != null ? audio === a.index : !!a.selected}
                    onPick={() => { pending.current = video.current?.currentTime || 0; setAudio(a.index); if (pb.hlsUrl) setMode('hls') }} />
                ))}
              </>
            ) : null}
            {pb.hlsUrl ? (
              <>
                <h4>Video</h4>
                <Choice label="Original" on={mode === 'direct'} onPick={() => { pending.current = video.current?.currentTime || 0; setAudio(undefined); setMode('direct') }} />
                <Choice label="Converted by server" on={mode === 'hls'} onPick={() => { pending.current = video.current?.currentTime || 0; setMode('hls') }} />
              </>
            ) : null}
            <button onClick={() => setMenu(false)} style={{ marginTop: '0.4rem', justifyContent: 'center', color: 'var(--accent)' }}>Done</button>
          </div>
        </div>
      ) : null}
    </div>
  )
}

function Choice({ label, on, onPick, disabled, auto }: { label: string; on: boolean; onPick: () => void; disabled?: boolean; auto?: boolean }) {
  return (
    <button onClick={onPick} disabled={disabled} data-autofocus={on || auto ? '' : undefined} style={disabled ? { opacity: 0.45 } : undefined}>
      <span>{label}</span>
      {on ? <Icon name="check" /> : null}
    </button>
  )
}

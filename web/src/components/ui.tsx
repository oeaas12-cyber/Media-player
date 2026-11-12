import { useEffect, useRef, useState, type ReactNode } from 'react'
import { AVATARS } from '../lib/store'
import type { Item } from '../lib/types'
import { duration, episodeLabel } from '../lib/format'
import { go } from '../lib/router'

const PATHS: Record<string, string> = {
  home: 'M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z',
  movie: 'M4 4h16a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1zm1 2v2h2V6zm0 5v2h2v-2zm0 5v2h2v-2zm12-10v2h2V6zm0 5v2h2v-2zm0 5v2h2v-2zM9 6v12h6V6z',
  tv: 'M4 5h16a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1h-6v2h3v1H7v-1h3v-2H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zm1 2v8h14V7z',
  search: 'M10.5 3a7.5 7.5 0 0 1 5.9 12.1l4.2 4.2-1.4 1.4-4.2-4.2A7.5 7.5 0 1 1 10.5 3zm0 2a5.5 5.5 0 1 0 0 11 5.5 5.5 0 0 0 0-11z',
  settings: 'M19.4 13a7.6 7.6 0 0 0 0-2l2-1.6-2-3.4-2.4 1a7.5 7.5 0 0 0-1.7-1L15 3.5h-4l-.3 2.5a7.5 7.5 0 0 0-1.7 1l-2.4-1-2 3.4 2 1.6a7.6 7.6 0 0 0 0 2l-2 1.6 2 3.4 2.4-1a7.5 7.5 0 0 0 1.7 1l.3 2.5h4l.3-2.5a7.5 7.5 0 0 0 1.7-1l2.4 1 2-3.4zM13 15.2a3.2 3.2 0 1 1 0-6.4 3.2 3.2 0 0 1 0 6.4z',
  play: 'M7 4.5v15a.8.8 0 0 0 1.2.7l12.5-7.5a.8.8 0 0 0 0-1.4L8.2 3.8A.8.8 0 0 0 7 4.5z',
  pause: 'M6 4h4v16H6zm8 0h4v16h-4z',
  back: 'M15.5 4.5 8 12l7.5 7.5-1.6 1.6L4.8 12l9.1-9.1z',
  info: 'M12 2a10 10 0 1 1 0 20 10 10 0 0 1 0-20zm-1 8v7h2v-7zm0-3.5v2h2v-2z',
  check: 'M9.2 16.6 4.6 12l-1.5 1.5 6.1 6.1L21 7.8l-1.5-1.5z',
  heart: 'M12 21s-8.5-5.4-8.5-11.4A4.6 4.6 0 0 1 12 6.9a4.6 4.6 0 0 1 8.5 2.7C20.500 15.6 12 21 12 21z',
  heartLine: 'M12 21s-8.5-5.4-8.5-11.4A4.6 4.6 0 0 1 12 6.900a4.6 4.6 0 0 1 8.5 2.700C20.5 15.6 12 21 12 21zm0-2.500c2.300-1.700 6.500-5.400 6.500-8.900a2.600 2.600 0 0 0-5-1h-3a2.600 2.600 0 0 0-5 1c0 3.500 4.200 7.200 6.500 8.900z',
  restart: 'M12 5V2L7 6l5 4V7a6 6 0 1 1-6 6H4a8 8 0 1 0 8-8z',
  rewind: 'M11.500 12 20 6v12zm-8.500 0L11.500 6v12z',
  forward: 'M12.500 12 4 6v12zm8.500 0L12.500 6v12z',
  next: 'M5 5v14l10-7zm11 0h3v14h-3z',
  captions: 'M4 5h16a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zm2 6v2a2 2 0 0 0 2 2h2.500v-1.700H8.200v-2.600h2.300V9H8a2 2 0 0 0-2 2zm7 0v2a2 2 0 0 0 2 2h2.500v-1.700h-2.300v-2.600h2.300V9H15a2 2 0 0 0-2 2z',
  audio: 'M4 9v6h4l5 4V5L8 9zm11.500 3a3.500 3.500 0 0 0-2-3.200v6.400a3.500 3.500 0 0 0 2-3.200zm-2-8.700v2.100a7 7 0 0 1 0 13.200v2.100a9 9 0 0 0 0-17.400z',
  expand: 'M4 4h6v2H6v4H4zm10 0h6v6h-2V6h-4zM4 14h2v4h4v2H4zm14 0h2v6h-6v-2h4z',
  eye: 'M12 5c5 0 8.700 4.200 10 7-1.300 2.800-5 7-10 7s-8.700-4.200-10-7c1.300-2.800 5-7 10-7zm0 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8zm0 2a2 2 0 1 1 0 4 2 2 0 0 1 0-4z',
  plus: 'M11 4h2v7h7v2h-7v7h-2v-7H4v-2h7z',
  chevron: 'M9 5l7 7-7 7-1.500-1.500L13 12 7.500 6.500z',
  trash: 'M9 3h6l1 2h4v2H4V5h4zm-3 6h12l-1 12H7z',
  gauge: 'M12 4a9 9 0 0 1 9 9c0 2.300-.900 4.400-2.300 6H5.300A9 9 0 0 1 12 4zm0 2a7 7 0 0 0-5.700 11h11.400A7 7 0 0 0 12 6zm3.500 3.100 1.400 1.400-3.500 3.600a1.500 1.500 0 1 1-1.400-1.400z',
}

export function Icon({ name }: { name: keyof typeof PATHS | string }) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d={PATHS[name] || ''} />
    </svg>
  )
}

export function Avatar({ name, color, size = 2.2 }: { name: string; color: number; size?: number }) {
  const [a, b] = AVATARS[((color % AVATARS.length) + AVATARS.length) % AVATARS.length]
  return (
    <span className="avatar" style={{ width: `${size}rem`, height: `${size}rem`, fontSize: `${size * 0.42}rem`, background: `linear-gradient(135deg, ${a}, ${b})` }}>
      {(name.trim()[0] || '?').toUpperCase()}
    </span>
  )
}

/** Image that fades in and quietly disappears if it fails to load. */
export function Img({ src, alt = '', className }: { src?: string; alt?: string; className?: string }) {
  const [ok, setOk] = useState(true)
  useEffect(() => setOk(true), [src])
  if (!src || !ok) return null
  return <img className={className} src={src} alt={alt} loading="lazy" onError={() => setOk(false)} />
}

export const open = (i: Item) => go(i.kind === 'episode' && i.seriesId ? `/item/${encodeURIComponent(i.id)}` : `/item/${encodeURIComponent(i.id)}`)
export const play = (i: Item, fromStart = false) => go(`/play/${encodeURIComponent(i.id)}${fromStart ? '?t=0' : ''}`)

export function Card({ item, wide, onFocus }: { item: Item; wide?: boolean; onFocus?: (i: Item) => void }) {
  const progress = item.resumeMs && item.runtimeMs ? Math.min(1, item.resumeMs / item.runtimeMs) : 0
  const image = wide ? item.thumb || item.backdrop || item.poster : item.poster || item.thumb
  const sub = item.kind === 'episode' ? [episodeLabel(item.season, item.episode), item.title].filter(Boolean).join(' · ') : ''
  return (
    <button
      className={'card' + (wide ? ' wide' : '')}
      onClick={() => (wide && (item.kind === 'movie' || item.kind === 'episode') ? play(item) : open(item))}
      onFocus={() => onFocus?.(item)}
      aria-label={item.title}
    >
      <div className="art">
        <div className="fallback">{item.seriesTitle || item.title}</div>
        <Img src={image} />
        {wide && (
          <>
            <div className="shade" />
            <div className="over" style={progress ? { bottom: '1.1rem' } : undefined}>
              <div className="nowrap">{item.seriesTitle || item.title}</div>
              {sub ? <small className="nowrap">{sub}</small> : null}
            </div>
          </>
        )}
        {progress > 0 && (
          <div className="bar">
            <i style={{ width: `${progress * 100}%` }} />
          </div>
        )}
        {item.played && !progress ? (
          <div className="tick">
            <Icon name="check" />
          </div>
        ) : null}
      </div>
      {!wide && (
        <>
          <div className="card-title">{item.title}</div>
          <div className="card-sub">{[item.year, item.kind === 'movie' ? duration(item.runtimeMs) : ''].filter(Boolean).join(' · ') || ' '}</div>
        </>
      )}
    </button>
  )
}

export function ShelfRow({ title, items, wide, onFocus }: { title: string; items: Item[]; wide?: boolean; onFocus?: (i: Item) => void }) {
  if (!items.length) return null
  return (
    <section className="shelf">
      <h2 className="pad">{title}</h2>
      <div className="row">
        {items.map((i) => (
          <Card key={i.id} item={i} wide={wide} onFocus={onFocus} />
        ))}
      </div>
    </section>
  )
}

export function Meta({ item }: { item: Item }) {
  const bits: ReactNode[] = []
  if (item.kind === 'episode' && item.season != null) bits.push(<span key="e">{episodeLabel(item.season, item.episode)}</span>)
  if (item.year) bits.push(<span key="y">{item.year}</span>)
  if (item.runtimeMs) bits.push(<span key="d">{duration(item.runtimeMs)}</span>)
  if (item.certificate) bits.push(<span key="c" className="badge">{item.certificate}</span>)
  if (item.rating) bits.push(<span key="r"><span className="star">★</span> {item.rating.toFixed(1)}</span>)
  if (item.genres.length) bits.push(<span key="g">{item.genres.slice(0, 2).join(', ')}</span>)
  return <div className="meta">{bits}</div>
}

export function Spinner() {
  return <div className="spinner" />
}

/** Loads async data for a screen; reloads when deps change. */
export function useAsync<T>(fn: () => Promise<T>, deps: unknown[]): { data?: T; error?: string; loading: boolean; reload: () => void } {
  const [state, set] = useState<{ data?: T; error?: string; loading: boolean }>({ loading: true })
  const [n, setN] = useState(0)
  const alive = useRef(true)
  useEffect(() => {
    alive.current = true
    set((s) => ({ ...s, loading: true, error: undefined }))
    fn().then(
      (data) => alive.current && set({ data, loading: false }),
      (e) => alive.current && set({ error: e?.message || 'Something went wrong', loading: false }),
    )
    return () => {
      alive.current = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, n])
  return { ...state, reload: () => setN((x) => x + 1) }
}

export function ErrorBox({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="empty">
      <h2>Something went wrong</h2>
      <p style={{ marginBottom: '1.2rem' }}>{message}</p>
      {onRetry ? (
        <button className="btn" onClick={onRetry}>
          Try again
        </button>
      ) : null}
    </div>
  )
}

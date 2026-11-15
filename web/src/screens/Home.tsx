import { useEffect, useState } from 'react'
import type { Item, Source } from '../lib/types'
import { ErrorBox, Icon, Img, Meta, ShelfRow, Spinner, cacheOf, open, play, remember, useAsync } from '../components/ui'

export function Home({ source, onBackdrop }: { source: Source; onBackdrop: (src?: string) => void }) {
  const cache = cacheOf(source)
  // The featured set is kept for the session so the banner doesn't reshuffle on every visit.
  const feat = useAsync(
    () => (cache.has('featured') ? Promise.resolve(cache.get('featured') as Item[]) : source.featured().catch(() => [] as Item[])).then(remember),
    [source], { map: cache, key: 'featured' })
  const home = useAsync(() => source.home().then((sh) => { sh.forEach((x) => remember(x.items)); return sh }), [source], { map: cache, key: 'home' })
  const [i, setI] = useState(0)
  // Auto-advancing content needs a way to stop it; people who prefer reduced motion start paused.
  const [paused, setPaused] = useState(() => !!window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches)
  const hero = feat.data || []

  useEffect(() => {
    if (hero.length < 2 || paused) return
    const t = setInterval(() => setI((x) => (x + 1) % hero.length), 9000)
    return () => clearInterval(t)
  }, [hero.length, paused])
  useEffect(() => onBackdrop(hero[i]?.backdrop), [hero, i])

  if (home.loading && !home.data) return <Spinner />
  if (home.error) return <ErrorBox message={home.error} onRetry={home.reload} />
  const cur = hero[i]
  const shelves = home.data || []
  return (
    <div className="page flush">
      {cur ? (
        <header className="hero">
          {hero.map((h, n) => (
            // The first slide is the largest image on the page; the others are hidden until shown.
            <Img key={h.id} className={'hero-art' + (n === i ? ' on' : '')} src={h.backdrop} priority={n === 0 ? 'high' : 'low'} />
          ))}
          <div className="hero-body">
            {cur.logo ? <Img className="hero-logo" src={cur.logo} alt={cur.title} /> : <h1 className="hero-title">{cur.title}</h1>}
            <Meta item={cur} />
            {cur.overview ? <p className="hero-text">{cur.overview}</p> : null}
            <div className="actions">
              {cur.kind === 'movie' ? (
                <button className="btn primary" data-autofocus onClick={() => play(cur)}>
                  <Icon name="play" />
                  {cur.resumeMs ? 'Resume' : 'Play'}
                </button>
              ) : null}
              <button className="btn" data-autofocus={cur.kind !== 'movie' ? '' : undefined} onClick={() => open(cur)}>
                <Icon name="info" />
                Details
              </button>
            </div>
          </div>
          {hero.length > 1 ? (
            <div className="hero-ctl">
              <div className="dots" aria-hidden="true">
                {hero.map((h, n) => (
                  <i key={h.id} className={n === i ? 'on' : ''} />
                ))}
              </div>
              <button className="btn icon tiny" onClick={() => setPaused(!paused)} aria-label={paused ? 'Play slideshow' : 'Pause slideshow'}>
                <Icon name={paused ? 'play' : 'pause'} />
              </button>
            </div>
          ) : null}
        </header>
      ) : (
        <div style={{ height: 'calc(var(--nav-h) + 1rem)' }} />
      )}
      {shelves.length ? (
        shelves.map((s) => <ShelfRow key={s.title} title={s.title} items={s.items} wide={s.wide} onFocus={(it) => it.backdrop && onBackdrop(it.backdrop)} />)
      ) : (
        <div className="empty">
          <h2>Your library is empty</h2>
          <p>Nothing was found on this server yet.</p>
        </div>
      )}
    </div>
  )
}

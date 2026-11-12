import { useEffect, useState } from 'react'
import type { Item, Source } from '../lib/types'
import { clock, duration } from '../lib/format'
import { back } from '../lib/router'
import { ErrorBox, Icon, Img, Meta, ShelfRow, Spinner, play, useAsync } from '../components/ui'

export function Detail({ source, id, onBackdrop }: { source: Source; id: string; onBackdrop: (src?: string) => void }) {
  const q = useAsync(() => source.item(id), [source, id])
  const item = q.data
  const [patch, setPatch] = useState<Partial<Item>>({})
  useEffect(() => setPatch({}), [id])
  useEffect(() => { if (item) onBackdrop(item.backdrop || item.poster) }, [item?.id])
  useEffect(() => { window.scrollTo(0, 0) }, [id])

  if (q.loading && !item) return <Spinner />
  if (q.error || !item) return (<><BackButton /><ErrorBox message={q.error || 'Not found'} onRetry={q.reload} /></>)
  const it = { ...item, ...patch }
  const playable = it.kind === 'movie' || it.kind === 'episode'
  const progress = it.resumeMs && it.runtimeMs ? it.resumeMs / it.runtimeMs : 0

  return (
    <div className="page flush">
      <BackButton />
      <header className="detail-hero">
        {it.backdrop ? <img className="hero-art" src={it.backdrop} alt="" /> : null}
        <div className="detail-body">
          {it.seriesTitle && it.kind !== 'show' ? <div className="subtle" style={{ fontWeight: 700, marginBottom: '0.3rem' }}>{it.seriesTitle}</div> : null}
          {it.logo && it.kind !== 'episode' ? <Img className="hero-logo" src={it.logo} alt={it.title} /> : <h1 className="hero-title">{it.title}</h1>}
          <Meta item={it} />
          {it.overview ? <p className="detail-over">{it.overview}</p> : <div style={{ height: '1.2rem' }} />}
          <div className="actions">
            {playable ? (
              <>
                <button className="btn primary" data-autofocus onClick={() => play(it)}>
                  <Icon name="play" />
                  {it.resumeMs ? `Resume ${clock(it.resumeMs)}` : 'Play'}
                </button>
                {it.resumeMs ? (
                  <button className="btn" onClick={() => play(it, true)}><Icon name="restart" />Start over</button>
                ) : null}
                <button className="btn" onClick={() => { const v = !it.played; setPatch((p) => ({ ...p, played: v, resumeMs: undefined })); source.setPlayed(it, v) }}>
                  <Icon name="check" />{it.played ? 'Watched' : 'Mark watched'}
                </button>
              </>
            ) : null}
            {it.kind !== 'season' ? (
              <button className="btn" onClick={() => { const v = !it.favorite; setPatch((p) => ({ ...p, favorite: v })); source.setFavorite(it, v) }}>
                <Icon name="heart" />{it.favorite ? 'In Favorites' : 'Favorite'}
              </button>
            ) : null}
          </div>
          {progress > 0 ? (
            <div className="resume-line">
              <div className="track"><i style={{ width: `${Math.min(100, progress * 100)}%` }} /></div>
              {duration((it.runtimeMs || 0) - (it.resumeMs || 0))} left
            </div>
          ) : null}
        </div>
      </header>
      {it.kind === 'show' ? <Seasons source={source} show={it} /> : null}
      {it.cast && it.cast.length ? (
        <section className="shelf">
          <h2 className="pad">Cast</h2>
          <div className="row">
            {it.cast.map((p, n) => (
              <div className="person" key={n}>
                <div className="face">{p.image ? <Img src={p.image} /> : (p.name[0] || '?')}</div>
                <b>{p.name}</b>
                <span>{p.role || ' '}</span>
              </div>
            ))}
          </div>
        </section>
      ) : null}
      {it.kind === 'movie' || it.kind === 'show' ? <Similar source={source} id={it.id} /> : null}
    </div>
  )
}

function BackButton() {
  return (
    <button className="btn icon back-btn" onClick={back} aria-label="Back">
      <Icon name="back" />
    </button>
  )
}

function Similar({ source, id }: { source: Source; id: string }) {
  const q = useAsync(() => source.similar(id), [source, id])
  return <ShelfRow title="More Like This" items={q.data || []} />
}

function Seasons({ source, show }: { source: Source; show: Item }) {
  const seasons = useAsync(() => source.seasons(show.id), [source, show.id])
  const [sel, setSel] = useState<string>()
  const list = seasons.data || []
  useEffect(() => {
    if (!list.length) return
    // Open on the first season that still has something to watch.
    setSel((list.find((s) => !s.played) || list[0]).id)
  }, [seasons.data])
  const eps = useAsync(() => (sel ? source.episodes(show.id, sel) : Promise.resolve([] as Item[])), [source, show.id, sel])
  const episodes = eps.data || []
  const next = episodes.find((e) => e.resumeMs) || episodes.find((e) => !e.played) || episodes[0]

  if (seasons.loading) return <Spinner />
  return (
    <section className="shelf pad">
      <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', marginBottom: '0.6rem' }}>
        <h2 className="section-title" style={{ marginRight: '1rem' }}>Episodes</h2>
        {next ? (
          <button className="btn small primary" onClick={() => play(next)}>
            <Icon name="play" />
            {next.resumeMs ? 'Resume' : 'Play'} S{next.season ?? 1} · E{next.episode ?? 1}
          </button>
        ) : null}
      </div>
      {list.length > 1 ? (
        <div className="chips" style={{ marginBottom: '0.6rem' }}>
          {list.map((s) => (
            <button key={s.id} className={'chip' + (s.id === sel ? ' on' : '')} onClick={() => setSel(s.id)}>{s.title}</button>
          ))}
        </div>
      ) : null}
      {eps.loading ? <Spinner /> : null}
      {episodes.map((e) => {
        const p = e.resumeMs && e.runtimeMs ? e.resumeMs / e.runtimeMs : 0
        return (
          <button key={e.id} className="episode" onClick={() => play(e)}>
            <div className="thumb">
              <Img src={e.thumb} />
              {p > 0 ? <div className="bar"><i style={{ width: `${Math.min(100, p * 100)}%` }} /></div> : null}
            </div>
            <div className="info">
              <div className="num">
                Episode {e.episode ?? ''}{e.runtimeMs ? ` · ${duration(e.runtimeMs)}` : ''}{e.played ? ' · Watched' : ''}
              </div>
              <div className="name">{e.title}</div>
              {e.overview ? <div className="desc">{e.overview}</div> : null}
            </div>
          </button>
        )
      })}
    </section>
  )
}

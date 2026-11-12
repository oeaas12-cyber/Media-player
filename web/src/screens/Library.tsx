import { useEffect, useRef, useState } from 'react'
import type { Item, SortKey, Source } from '../lib/types'
import { Card, ErrorBox, Spinner, useAsync } from '../components/ui'

const SORTS: [SortKey, string][] = [['title', 'A–Z'], ['added', 'Recently Added'], ['year', 'Year'], ['rating', 'Rating']]
const FILTERS: ['all' | 'unwatched' | 'favorites', string][] = [['all', 'All'], ['unwatched', 'Unwatched'], ['favorites', 'Favorites']]
const PAGE = 60

export function Library({ source, kind }: { source: Source; kind: 'movie' | 'show' }) {
  const [sort, setSort] = useState<SortKey>('title')
  const [filter, setFilter] = useState<'all' | 'unwatched' | 'favorites'>('all')
  const [genre, setGenre] = useState<string>()
  const [items, setItems] = useState<Item[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const genres = useAsync(() => source.genres(kind).catch(() => [] as string[]), [source, kind])
  const req = useRef(0)
  const sentinel = useRef<HTMLDivElement>(null)

  async function load(start: number) {
    const id = ++req.current
    setLoading(true)
    setError('')
    try {
      const r = await source.list(kind, { start, limit: PAGE, sort, genre, filter })
      if (id !== req.current) return
      setItems((prev) => (start === 0 ? r.items : [...prev, ...r.items]))
      setTotal(r.total)
    } catch (e: any) {
      if (id === req.current) setError(e?.message || 'Could not load')
    } finally {
      if (id === req.current) setLoading(false)
    }
  }
  useEffect(() => {
    setItems([])
    load(0)
  }, [source, kind, sort, filter, genre])

  // Load the next page as the end of the grid scrolls into view.
  const state = useRef({ loading, count: items.length, total })
  state.current = { loading, count: items.length, total }
  useEffect(() => {
    const el = sentinel.current
    if (!el || !('IntersectionObserver' in window)) return
    const io = new IntersectionObserver(
      (entries) => {
        const s = state.current
        if (entries[0].isIntersecting && !s.loading && s.count < s.total) load(s.count)
      },
      { rootMargin: '600px' },
    )
    io.observe(el)
    return () => io.disconnect()
  }, [source, kind, sort, filter, genre])

  return (
    <div className="page">
      <div className="pad">
        <div className="head">
          <h1 className="title-xl">{kind === 'movie' ? 'Movies' : 'TV Shows'}</h1>
          {total ? <span className="count">{total.toLocaleString()}</span> : null}
        </div>
        <div className="chips">
          {SORTS.map(([k, label]) => (
            <button key={k} className={'chip' + (sort === k ? ' on' : '')} onClick={() => setSort(k)}>{label}</button>
          ))}
          <span className="chip-sep" />
          {FILTERS.map(([k, label]) => (
            <button key={k} className={'chip' + (filter === k ? ' on' : '')} onClick={() => setFilter(k)}>{label}</button>
          ))}
        </div>
        {genres.data && genres.data.length ? (
          <div className="chips" style={{ marginTop: '0.4rem' }}>
            <button className={'chip' + (!genre ? ' on' : '')} onClick={() => setGenre(undefined)}>All genres</button>
            {genres.data.map((g) => (
              <button key={g} className={'chip' + (genre === g ? ' on' : '')} onClick={() => setGenre(genre === g ? undefined : g)}>{g}</button>
            ))}
          </div>
        ) : null}
      </div>
      {error ? <ErrorBox message={error} onRetry={() => load(0)} /> : null}
      <div className="grid">
        {items.map((i) => (
          <Card key={i.id} item={i} />
        ))}
      </div>
      {loading ? <Spinner /> : null}
      {!loading && !error && !items.length ? (
        <div className="empty">
          <h2>Nothing here</h2>
          <p>No titles match these filters.</p>
        </div>
      ) : null}
      <div ref={sentinel} style={{ height: 1 }} />
    </div>
  )
}

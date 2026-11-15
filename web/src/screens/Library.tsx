import { useEffect, useRef, useState } from 'react'
import type { Item, SortKey, Source } from '../lib/types'
import { Card, ErrorBox, Spinner, cacheOf, remember, useAsync } from '../components/ui'

const SORTS: [SortKey, string][] = [['title', 'A–Z'], ['added', 'Recently Added'], ['year', 'Year'], ['rating', 'Rating']]
const FILTERS: [Filter, string][] = [['all', 'All'], ['unwatched', 'Unwatched'], ['favorites', 'Favorites']]
const PAGE = 60

type Filter = 'all' | 'unwatched' | 'favorites'
/** The sort and filters last used on each tab, so they survive opening a title and coming back. */
const lastView: Record<string, { sort: SortKey; filter: Filter; genre?: string }> = {}

export function Library({ source, kind }: { source: Source; kind: 'movie' | 'show' }) {
  const cache = cacheOf(source)
  const start = lastView[kind] || { sort: 'title' as SortKey, filter: 'all' as Filter }
  const [sort, setSort] = useState<SortKey>(start.sort)
  const [filter, setFilter] = useState<Filter>(start.filter)
  const [genre, setGenre] = useState<string | undefined>(start.genre)
  lastView[kind] = { sort, filter, genre }
  const key = `list:${kind}:${sort}:${filter}:${genre || ''}`
  const kept = cache.get(key) as { items: Item[]; total: number } | undefined
  const [items, setItems] = useState<Item[]>(kept?.items || [])
  const [total, setTotal] = useState(kept?.total || 0)
  const [loading, setLoading] = useState(!kept)
  const [error, setError] = useState('')
  const genres = useAsync(() => source.genres(kind).catch(() => [] as string[]), [source, kind], { map: cache, key: `genres:${kind}` })
  const req = useRef(0)
  const sentinel = useRef<HTMLDivElement>(null)

  async function load(start: number) {
    const id = ++req.current
    setLoading(true)
    setError('')
    try {
      const r = await source.list(kind, { start, limit: PAGE, sort, genre, filter })
      if (id !== req.current) return
      remember(r.items)
      setItems((prev) => {
        const next = start === 0 ? r.items : [...prev, ...r.items]
        cache.set(key, { items: next, total: r.total })
        return next
      })
      setTotal(r.total)
    } catch (e: any) {
      if (id === req.current) setError(e?.message || 'Could not load')
    } finally {
      if (id === req.current) setLoading(false)
    }
  }
  useEffect(() => {
    const c = cache.get(key) as { items: Item[]; total: number } | undefined
    if (c) {
      // Already loaded in this session: show it as it was.
      req.current++
      setItems(c.items)
      setTotal(c.total)
      setLoading(false)
      setError('')
      return
    }
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

import { useEffect, useState } from 'react'
import type { Item, Source } from '../lib/types'
import { Card, Icon, Spinner } from '../components/ui'

export function Search({ source }: { source: Source }) {
  const [q, setQ] = useState('')
  const [items, setItems] = useState<Item[] | null>(null)
  const [loading, setLoading] = useState(false)
  useEffect(() => {
    const term = q.trim()
    if (!term) { setItems(null); return }
    let alive = true
    setLoading(true)
    const t = setTimeout(() => {
      source.search(term).then((r) => alive && setItems(r), () => alive && setItems([])).then(() => alive && setLoading(false))
    }, 350)
    return () => { alive = false; clearTimeout(t) }
  }, [q, source])
  return (
    <div className="page">
      <div className="pad">
        <h1 className="title-xl" style={{ marginBottom: '1rem' }}>Search</h1>
        <div className="input" style={{ maxWidth: '40rem' }}>
          <input data-autofocus value={q} onChange={(e) => setQ(e.target.value)} placeholder="Movies and TV shows" autoCapitalize="off" autoCorrect="off" spellCheck={false} style={{ paddingLeft: '2.8rem' }} />
          <span className="eye" style={{ left: '0.4rem', right: 'auto' }}><Icon name="search" /></span>
        </div>
      </div>
      {loading ? <Spinner /> : null}
      {!loading && items && !items.length ? (
        <div className="empty"><h2>No results</h2><p>Nothing matches “{q.trim()}”.</p></div>
      ) : null}
      {!loading && items ? <div className="grid">{items.map((i) => <Card key={i.id} item={i} />)}</div> : null}
    </div>
  )
}

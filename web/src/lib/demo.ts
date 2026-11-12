import type { Item, ListOptions, PlaybackInfo, Shelf, Source } from './types'

/** Built-in sample library: open-licensed Blender films and short test clips, so the app can be tried without a server. */
const BUCKET = 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample'
const art = (n: string) => `./demo/${n}.jpg`
const KEY = 'lumina.demo.progress'

type Saved = Record<string, { ms?: number; played?: boolean; favorite?: boolean }>
const read = (): Saved => {
  try {
    return JSON.parse(localStorage.getItem(KEY) || '{}')
  } catch {
    return {}
  }
}
const write = (s: Saved) => {
  try {
    localStorage.setItem(KEY, JSON.stringify(s))
  } catch {}
}

const files: Record<string, string> = {}
function movie(key: string, file: string, title: string, year: number, min: number, genres: string[], overview: string, rating: number): Item {
  files[`m-${key}`] = file
  return {
    id: `m-${key}`, kind: 'movie', title, year, runtimeMs: min * 60000, genres, overview, rating, certificate: 'G',
    poster: art(`demo_${key}_poster`), backdrop: art(`demo_${key}_backdrop`), thumb: art(`demo_${key}_backdrop`),
  }
}
const MOVIES = [
  movie('bbb', 'BigBuckBunny.mp4', 'Big Buck Bunny', 2008, 10, ['Animation', 'Comedy', 'Family'], 'A gentle giant rabbit has had enough of three bullying rodents and plans a cheerful, well-engineered revenge.', 7.6),
  movie('sintel', 'Sintel.mp4', 'Sintel', 2010, 15, ['Animation', 'Fantasy', 'Adventure'], 'A lone young woman crosses snow, desert and city in search of the baby dragon she once rescued.', 7.4),
  movie('tears', 'TearsOfSteel.mp4', 'Tears of Steel', 2012, 12, ['Science Fiction', 'Action'], 'In a future Amsterdam, a group of scientists stages a last attempt to save the world from rogue robots.', 6.8),
  movie('elephants', 'ElephantsDream.mp4', 'Elephants Dream', 2006, 11, ['Animation', 'Science Fiction'], 'Two men explore an endless, shifting machine world, and only one of them believes it is real.', 6.5),
]
const SHOW: Item = {
  id: 's-showcase', kind: 'show', title: 'Bigger Screens', year: 2024, genres: ['Documentary'], rating: 7.1, certificate: 'G',
  overview: 'A demo series of short sample clips for trying episodes, resume and auto-play next.',
  poster: art('demo_showcase_poster'), backdrop: art('demo_showcase_backdrop'), thumb: art('demo_showcase_backdrop'),
}
const SEASON: Item = { id: 'se-1', kind: 'season', title: 'Season 1', season: 1, genres: [], seriesId: SHOW.id, seriesTitle: SHOW.title, poster: SHOW.poster }
const EPISODES: Item[] = ['Blazes', 'Escapes', 'Fun', 'Joyrides', 'Meltdowns'].map((name, i) => {
  const id = `e-${i + 1}`
  files[id] = `ForBigger${name}.mp4`
  return {
    id, kind: 'episode', title: name, overview: `Sample clip ${i + 1} of the demo series.`, runtimeMs: 15000, genres: [],
    thumb: art(`demo_showcase_e${i + 1}`), backdrop: SHOW.backdrop, season: 1, episode: i + 1,
    seriesId: SHOW.id, seriesTitle: SHOW.title, seasonId: SEASON.id,
  }
})
const ALL = [...MOVIES, SHOW, SEASON, ...EPISODES]

export class DemoSource implements Source {
  private live(i: Item): Item {
    const s = read()[i.id] || {}
    return { ...i, resumeMs: s.ms && s.ms > 5000 ? s.ms : undefined, played: !!s.played, favorite: !!s.favorite }
  }
  async featured() {
    return [MOVIES[1], MOVIES[0], MOVIES[2], SHOW, MOVIES[3]].map((i) => this.live(i))
  }
  async home(): Promise<Shelf[]> {
    const all = ALL.map((i) => this.live(i))
    const resume = all.filter((i) => i.resumeMs && (i.kind === 'movie' || i.kind === 'episode'))
    const fav = all.filter((i) => i.favorite)
    return [
      { title: 'Continue Watching', items: resume, wide: true },
      { title: 'Recently Added Movies', items: MOVIES.map((i) => this.live(i)) },
      { title: 'TV Shows', items: [this.live(SHOW)] },
      { title: 'Favorites', items: fav },
      { title: 'Top Rated Movies', items: [...MOVIES].sort((a, b) => (b.rating || 0) - (a.rating || 0)).map((i) => this.live(i)) },
    ].filter((s) => s.items.length)
  }
  async list(kind: 'movie' | 'show', o: ListOptions) {
    let items = (kind === 'movie' ? MOVIES : [SHOW]).map((i) => this.live(i))
    if (o.genre) items = items.filter((i) => i.genres.includes(o.genre!))
    if (o.search) items = items.filter((i) => i.title.toLowerCase().includes(o.search!.toLowerCase()))
    if (o.filter === 'unwatched') items = items.filter((i) => !i.played)
    if (o.filter === 'favorites') items = items.filter((i) => i.favorite)
    const by: Record<string, (a: Item, b: Item) => number> = {
      title: (a, b) => a.title.localeCompare(b.title),
      added: () => 0,
      year: (a, b) => (b.year || 0) - (a.year || 0),
      rating: (a, b) => (b.rating || 0) - (a.rating || 0),
    }
    items.sort(by[o.sort])
    return { items: items.slice(o.start, o.start + o.limit), total: items.length }
  }
  async genres(kind: 'movie' | 'show') {
    return Array.from(new Set((kind === 'movie' ? MOVIES : [SHOW]).flatMap((i) => i.genres))).sort()
  }
  async search(q: string) {
    return [...MOVIES, SHOW].filter((i) => i.title.toLowerCase().includes(q.toLowerCase())).map((i) => this.live(i))
  }
  async item(id: string) {
    const i = ALL.find((x) => x.id === id)
    if (!i) throw new Error('This title is no longer available.')
    return this.live(i)
  }
  async seasons() {
    return [SEASON]
  }
  async episodes() {
    return EPISODES.map((i) => this.live(i))
  }
  async similar(id: string) {
    return MOVIES.filter((m) => m.id !== id).map((i) => this.live(i))
  }
  async playback(item: Item): Promise<PlaybackInfo> {
    const f = files[item.id]
    if (!f) throw new Error('Nothing to play for this title.')
    return { directUrl: `${BUCKET}/${f}`, mediaSourceId: item.id, playSessionId: 'demo', audio: [], subtitles: [] }
  }
  async report(ev: 'start' | 'progress' | 'stop', item: Item, _pb: PlaybackInfo, positionMs: number) {
    if (ev === 'start') return
    const s = read()
    const done = item.runtimeMs ? positionMs > item.runtimeMs * 0.92 : false
    s[item.id] = { ...s[item.id], ms: done ? 0 : positionMs, played: done || s[item.id]?.played }
    write(s)
  }
  async setPlayed(item: Item, played: boolean) {
    const s = read()
    s[item.id] = { ...s[item.id], played, ms: 0 }
    write(s)
  }
  async setFavorite(item: Item, favorite: boolean) {
    const s = read()
    s[item.id] = { ...s[item.id], favorite }
    write(s)
  }
}

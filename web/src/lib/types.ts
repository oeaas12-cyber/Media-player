export type Kind = 'movie' | 'show' | 'season' | 'episode'

export interface Item {
  id: string
  kind: Kind
  title: string
  overview?: string
  year?: number
  runtimeMs?: number
  rating?: number
  certificate?: string
  genres: string[]
  poster?: string
  backdrop?: string
  logo?: string
  thumb?: string
  seriesTitle?: string
  seriesId?: string
  seasonId?: string
  season?: number
  episode?: number
  resumeMs?: number
  played?: boolean
  favorite?: boolean
  cast?: { name: string; role?: string; image?: string }[]
}

export interface Shelf {
  title: string
  items: Item[]
  wide?: boolean
}

export interface Track {
  index: number
  label: string
  language?: string
  selected?: boolean
}

export interface PlaybackInfo {
  /** Direct file URL (played as-is). */
  directUrl: string
  /** Server-converted HLS stream, used when the device can't play the file directly. */
  hlsUrl?: (audioIndex?: number) => string
  mediaSourceId: string
  playSessionId: string
  audio: Track[]
  subtitles: (Track & { url?: string })[]
}

export type SortKey = 'title' | 'added' | 'year' | 'rating'

export interface ListOptions {
  start: number
  limit: number
  sort: SortKey
  genre?: string
  search?: string
  filter?: 'all' | 'unwatched' | 'favorites'
}

export interface Source {
  featured(): Promise<Item[]>
  home(): Promise<Shelf[]>
  list(kind: 'movie' | 'show', o: ListOptions): Promise<{ items: Item[]; total: number }>
  genres(kind: 'movie' | 'show'): Promise<string[]>
  search(q: string): Promise<Item[]>
  item(id: string): Promise<Item>
  seasons(showId: string): Promise<Item[]>
  episodes(showId: string, seasonId: string): Promise<Item[]>
  similar(id: string): Promise<Item[]>
  playback(item: Item): Promise<PlaybackInfo>
  report(ev: 'start' | 'progress' | 'stop', item: Item, pb: PlaybackInfo, positionMs: number, paused: boolean): Promise<void>
  setPlayed(item: Item, played: boolean): Promise<void>
  setFavorite(item: Item, favorite: boolean): Promise<void>
}

export interface Server {
  id: string
  type: 'emby' | 'demo'
  name: string
  url: string
  userId: string
  userName: string
  token: string
}

export interface Profile {
  id: string
  name: string
  color: number
  kids: boolean
  servers: Server[]
  activeServerId?: string
}

export interface Prefs {
  skipBack: number
  skipForward: number
  autoplayNext: boolean
  subtitleLang: string
  subtitleSize: number
  alwaysConvert: boolean
}

/**
 * Emby / Jellyfin client.
 *
 * The sign-in and the authenticated requests are deliberately the same requests the open-source
 * StreamBridge project (github.com/h4harsimran/streambridge, MIT) sends, because those are known
 * to work against hosted Emby servers:
 *
 *   POST {server}/Users/AuthenticateByName
 *     Content-Type: application/json
 *     X-Emby-Authorization: MediaBrowser Client="…", Device="…", DeviceId="…", Version="…"
 *     {"Username": "...", "Pw": "..."}
 *
 *   every later call:  X-Emby-Token: <token>
 *                      X-Emby-Authorization: MediaBrowser …, Token="<token>"
 *
 * Only the client name differs (this app identifies itself as itself).
 */
import type { Item, Kind, ListOptions, PlaybackInfo, Server, Shelf, Source, Track } from './types'
import { languageName } from './format'

export const APP_VERSION = '1.0.0'
const CLIENT = 'Lumina'

export function deviceName(): string {
  const ua = navigator.userAgent
  if (/Web0S|webOS/i.test(ua)) return 'LG TV'
  if (/Tizen/i.test(ua)) return 'Samsung TV'
  if (/Electron/i.test(ua)) return /Windows/i.test(ua) ? 'Windows' : /Mac/i.test(ua) ? 'Mac' : 'Linux'
  if (/Android/i.test(ua)) return 'Android'
  if (/iPhone|iPad|iPod/i.test(ua)) return 'iOS'
  return 'Web'
}

function authorization(deviceId: string, token?: string): string {
  const base = `MediaBrowser Client="${CLIENT}", Device="${deviceName()}", DeviceId="${deviceId}", Version="${APP_VERSION}"`
  return token ? `${base}, Token="${token}"` : base
}

export function normalizeUrl(input: string): string {
  let u = input.trim().replace(/\/+$/, '')
  if (!/^https?:\/\//i.test(u)) u = 'http://' + u
  return u
}

export class EmbyError extends Error {
  constructor(message: string, public status?: number) {
    super(message)
  }
}

async function readError(res: Response): Promise<string> {
  let text = ''
  try {
    text = (await res.text()).trim()
  } catch {}
  if (text.startsWith('{')) {
    try {
      const j = JSON.parse(text)
      text = j.Message || j.message || text
    } catch {}
  }
  if (!text || text.startsWith('<')) text = `HTTP ${res.status}`
  return text.slice(0, 200)
}

/** Signs in with the server's own username and password (not Emby Connect). */
export async function embyLogin(serverUrl: string, username: string, password: string, deviceId: string): Promise<Server> {
  const root = normalizeUrl(serverUrl).replace(/\/emby$/i, '')
  // One real attempt, exactly like StreamBridge. "/emby" is only tried when the plain path
  // does not exist on this server (404/405), which is not a sign-in attempt.
  let lastStatus = 0
  for (const base of [root, root + '/emby']) {
    let res: Response
    try {
      res = await fetch(`${base}/Users/AuthenticateByName`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Emby-Authorization': authorization(deviceId),
        },
        body: JSON.stringify({ Username: username, Pw: password || '' }),
      })
    } catch (e) {
      throw new EmbyError(
        `Couldn't reach ${root}. Check the address (including http:// or https:// and the port) and that this device is online.`,
      )
    }
    lastStatus = res.status
    if (res.status === 404 || res.status === 405) continue
    if (!res.ok) throw new EmbyError(await readError(res), res.status)
    const data = await res.json()
    const userId = data?.User?.Id
    const token = data?.AccessToken
    if (!userId || !token) throw new EmbyError('The server answered, but not with a sign-in. Is this an Emby or Jellyfin address?')
    let name = ''
    try {
      const info = await fetch(`${base}/System/Info/Public`).then((r) => (r.ok ? r.json() : null))
      name = info?.ServerName || ''
    } catch {}
    return {
      id: `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`,
      type: 'emby',
      name: name || new URL(base).hostname,
      url: base,
      userId,
      userName: data.User.Name || username,
      token,
    }
  }
  throw new EmbyError(`No Emby or Jellyfin server answered at ${root} (HTTP ${lastStatus}).`, lastStatus)
}

const FIELDS = 'Overview,Genres,DateCreated,OfficialRating,CommunityRating,ProductionYear,RunTimeTicks'

export class EmbySource implements Source {
  private base: string
  private uid: string
  constructor(private server: Server, private deviceId: string, private kids = false) {
    this.base = server.url
    this.uid = server.userId
  }

  private headers(json = false): Record<string, string> {
    const h: Record<string, string> = {
      'X-Emby-Token': this.server.token,
      'X-Emby-Authorization': authorization(this.deviceId, this.server.token),
    }
    if (json) h['Content-Type'] = 'application/json'
    return h
  }

  private async get(path: string, params: Record<string, string | number | boolean | undefined> = {}): Promise<any> {
    const q = Object.entries(params)
      .filter(([, v]) => v !== undefined && v !== '')
      .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
      .join('&')
    let res: Response
    try {
      res = await fetch(`${this.base}${path}${q ? '?' + q : ''}`, { headers: this.headers() })
    } catch {
      throw new EmbyError(`Couldn't reach ${this.server.name}.`)
    }
    if (res.status === 401) throw new EmbyError('Your sign-in has expired. Remove this server in Settings and sign in again.', 401)
    if (!res.ok) throw new EmbyError(await readError(res), res.status)
    return res.json()
  }

  private async send(method: 'POST' | 'DELETE', path: string, body?: unknown): Promise<void> {
    try {
      await fetch(`${this.base}${path}`, {
        method,
        headers: this.headers(body !== undefined),
        body: body !== undefined ? JSON.stringify(body) : undefined,
      })
    } catch {}
  }

  private img(id: string, type: string, tag: string | undefined, size: string): string | undefined {
    return tag ? `${this.base}/Items/${id}/Images/${type}?${size}&quality=90&tag=${tag}` : undefined
  }

  private toItem = (o: any): Item | null => {
    const kinds: Record<string, Kind> = { Movie: 'movie', Series: 'show', Season: 'season', Episode: 'episode' }
    const kind = kinds[o.Type]
    if (!kind || !o.Id) return null
    const tags = o.ImageTags || {}
    const backdropTag = (o.BackdropImageTags || [])[0]
    const parentBackdrop = (o.ParentBackdropImageTags || [])[0]
    const poster =
      this.img(o.Id, 'Primary', tags.Primary, 'maxHeight=540') ||
      (o.SeriesId && o.SeriesPrimaryImageTag ? this.img(o.SeriesId, 'Primary', o.SeriesPrimaryImageTag, 'maxHeight=540') : undefined)
    const backdrop =
      this.img(o.Id, 'Backdrop', backdropTag, 'maxWidth=1920') ||
      (o.ParentBackdropItemId ? this.img(o.ParentBackdropItemId, 'Backdrop', parentBackdrop, 'maxWidth=1920') : undefined)
    const logo =
      this.img(o.Id, 'Logo', tags.Logo, 'maxWidth=600') ||
      (o.ParentLogoItemId ? this.img(o.ParentLogoItemId, 'Logo', o.ParentLogoImageTag, 'maxWidth=600') : undefined)
    const ud = o.UserData || {}
    return {
      id: o.Id,
      kind,
      title: o.Name || '',
      overview: o.Overview || undefined,
      year: o.ProductionYear || undefined,
      runtimeMs: o.RunTimeTicks ? Math.round(o.RunTimeTicks / 10000) : undefined,
      rating: o.CommunityRating || undefined,
      certificate: o.OfficialRating || undefined,
      genres: o.Genres || [],
      poster: kind === 'episode' ? undefined : poster,
      thumb:
        kind === 'episode'
          ? this.img(o.Id, 'Primary', tags.Primary, 'maxWidth=640') || backdrop
          : this.img(o.Id, 'Thumb', tags.Thumb, 'maxWidth=640') || backdrop,
      backdrop,
      logo,
      seriesTitle: o.SeriesName || undefined,
      seriesId: o.SeriesId || undefined,
      seasonId: o.SeasonId || undefined,
      season: kind === 'season' ? o.IndexNumber : o.ParentIndexNumber,
      episode: kind === 'episode' ? o.IndexNumber : undefined,
      resumeMs: ud.PlaybackPositionTicks ? Math.round(ud.PlaybackPositionTicks / 10000) : undefined,
      played: !!ud.Played,
      favorite: !!ud.IsFavorite,
      cast: (o.People || [])
        .filter((p: any) => p.Type === 'Actor')
        .slice(0, 16)
        .map((p: any) => ({
          name: p.Name,
          role: p.Role || undefined,
          image: p.PrimaryImageTag ? `${this.base}/Items/${p.Id}/Images/Primary?maxHeight=300&quality=90&tag=${p.PrimaryImageTag}` : undefined,
        })),
    }
  }

  private map(list: any[]): Item[] {
    return (list || []).map(this.toItem).filter(Boolean) as Item[]
  }

  /** Kids profiles are limited by the server's own parental rating filter. */
  private get rating() {
    return this.kids ? { MaxOfficialRating: 'PG' } : {}
  }

  private items(params: Record<string, string | number | boolean | undefined>) {
    return this.get(`/Users/${this.uid}/Items`, { Recursive: true, Fields: FIELDS, ImageTypeLimit: 1, EnableImageTypes: 'Primary,Backdrop,Logo,Thumb', ...this.rating, ...params })
  }

  async featured(): Promise<Item[]> {
    const r = await this.items({ IncludeItemTypes: 'Movie,Series', SortBy: 'Random', Limit: 8, ImageTypes: 'Backdrop' })
    return this.map(r.Items).filter((i) => i.backdrop)
  }

  async home(): Promise<Shelf[]> {
    const safe = <T,>(p: Promise<T>, fallback: T) => p.catch(() => fallback)
    const [resume, nextUp, movies, shows, favorites, top] = await Promise.all([
      safe(this.get(`/Users/${this.uid}/Items/Resume`, { Limit: 20, MediaTypes: 'Video', Fields: FIELDS, ...this.rating }), { Items: [] }),
      safe(this.get('/Shows/NextUp', { UserId: this.uid, Limit: 20, Fields: FIELDS, ...this.rating }), { Items: [] }),
      safe(this.items({ IncludeItemTypes: 'Movie', SortBy: 'DateCreated', SortOrder: 'Descending', Limit: 24 }), { Items: [] }),
      safe(this.items({ IncludeItemTypes: 'Series', SortBy: 'DateCreated', SortOrder: 'Descending', Limit: 24 }), { Items: [] }),
      safe(this.items({ IncludeItemTypes: 'Movie,Series', Filters: 'IsFavorite', SortBy: 'SortName', Limit: 24 }), { Items: [] }),
      safe(this.items({ IncludeItemTypes: 'Movie', SortBy: 'CommunityRating', SortOrder: 'Descending', Limit: 24, MinCommunityRating: 7 }), { Items: [] }),
    ])
    const shelves: Shelf[] = [
      { title: 'Continue Watching', items: this.map(resume.Items), wide: true },
      { title: 'Next Up', items: this.map(nextUp.Items), wide: true },
      { title: 'Recently Added Movies', items: this.map(movies.Items) },
      { title: 'Recently Added TV Shows', items: this.map(shows.Items) },
      { title: 'Favorites', items: this.map(favorites.Items) },
      { title: 'Top Rated Movies', items: this.map(top.Items) },
    ]
    return shelves.filter((s) => s.items.length)
  }

  async list(kind: 'movie' | 'show', o: ListOptions) {
    const sort: Record<string, [string, string]> = {
      title: ['SortName', 'Ascending'],
      added: ['DateCreated', 'Descending'],
      year: ['ProductionYear,SortName', 'Descending'],
      rating: ['CommunityRating,SortName', 'Descending'],
    }
    const [SortBy, SortOrder] = sort[o.sort]
    const r = await this.items({
      IncludeItemTypes: kind === 'movie' ? 'Movie' : 'Series',
      SortBy,
      SortOrder,
      StartIndex: o.start,
      Limit: o.limit,
      Genres: o.genre,
      SearchTerm: o.search,
      Filters: o.filter === 'unwatched' ? 'IsUnplayed' : o.filter === 'favorites' ? 'IsFavorite' : undefined,
    })
    return { items: this.map(r.Items), total: r.TotalRecordCount ?? 0 }
  }

  async genres(kind: 'movie' | 'show'): Promise<string[]> {
    const r = await this.get('/Genres', { UserId: this.uid, IncludeItemTypes: kind === 'movie' ? 'Movie' : 'Series', Recursive: true, SortBy: 'SortName' })
    return (r.Items || []).map((g: any) => g.Name).filter(Boolean)
  }

  async search(q: string): Promise<Item[]> {
    const r = await this.items({ IncludeItemTypes: 'Movie,Series', SearchTerm: q, Limit: 60 })
    return this.map(r.Items)
  }

  async item(id: string): Promise<Item> {
    const o = await this.get(`/Users/${this.uid}/Items/${id}`, { Fields: FIELDS + ',People' })
    const it = this.toItem(o)
    if (!it) throw new EmbyError('This title is no longer available.')
    return it
  }

  async seasons(showId: string): Promise<Item[]> {
    const r = await this.get(`/Shows/${showId}/Seasons`, { UserId: this.uid, Fields: FIELDS })
    return this.map(r.Items)
  }

  async episodes(showId: string, seasonId: string): Promise<Item[]> {
    const r = await this.get(`/Shows/${showId}/Episodes`, { SeasonId: seasonId, UserId: this.uid, Fields: FIELDS })
    return this.map(r.Items)
  }

  async similar(id: string): Promise<Item[]> {
    const r = await this.get(`/Items/${id}/Similar`, { UserId: this.uid, Limit: 16, Fields: FIELDS, ...this.rating }).catch(() => ({ Items: [] }))
    return this.map(r.Items)
  }

  async playback(item: Item): Promise<PlaybackInfo> {
    const info = await this.get(`/Items/${item.id}/PlaybackInfo`, { UserId: this.uid })
    const source = (info.MediaSources || [])[0]
    if (!source) throw new EmbyError('The server has no playable file for this title.')
    const token = encodeURIComponent(this.server.token)
    const device = encodeURIComponent(this.deviceId)
    const container = String(source.Container || '').split(',')[0].trim()
    const ext = /^[a-z0-9]+$/i.test(container) ? `.${container}` : ''
    const session = info.PlaySessionId || `${Date.now()}`
    const streams: any[] = source.MediaStreams || []
    const label = (s: any, fallback: string) =>
      s.DisplayTitle || [languageName(s.Language), s.Title, s.Codec?.toUpperCase()].filter(Boolean).join(' · ') || fallback
    const audio: Track[] = streams
      .filter((s) => s.Type === 'Audio')
      .map((s) => ({ index: s.Index, label: label(s, `Audio ${s.Index}`), language: s.Language, selected: s.Index === source.DefaultAudioStreamIndex || !!s.IsDefault }))
    const subtitles = streams
      .filter((s) => s.Type === 'Subtitle')
      .map((s) => ({
        index: s.Index,
        label: label(s, `Subtitle ${s.Index}`),
        language: s.Language,
        // Text subtitles can be fetched as WebVTT; picture subtitles (PGS/VobSub) can't be shown in a browser.
        url: s.IsTextSubtitleStream ? `${this.base}/Videos/${item.id}/${source.Id}/Subtitles/${s.Index}/Stream.vtt?api_key=${token}` : undefined,
      }))
    return {
      // Same direct-play URL StreamBridge builds.
      directUrl: `${this.base}/Videos/${item.id}/stream${ext}?MediaSourceId=${source.Id}&Static=true&api_key=${token}&DeviceId=${device}`,
      hlsUrl: (audioIndex?: number) =>
        `${this.base}/Videos/${item.id}/master.m3u8?MediaSourceId=${source.Id}&DeviceId=${device}&api_key=${token}&PlaySessionId=${session}` +
        `&VideoCodec=h264&AudioCodec=aac&MaxAudioChannels=2&VideoBitrate=8000000&AudioBitrate=192000` +
        `&SegmentContainer=ts&MinSegments=1&BreakOnNonKeyFrames=true` +
        (audioIndex != null ? `&AudioStreamIndex=${audioIndex}` : ''),
      mediaSourceId: source.Id,
      playSessionId: session,
      audio,
      subtitles,
    }
  }

  async report(ev: 'start' | 'progress' | 'stop', item: Item, pb: PlaybackInfo, positionMs: number, paused: boolean) {
    const path = ev === 'start' ? '/Sessions/Playing' : ev === 'progress' ? '/Sessions/Playing/Progress' : '/Sessions/Playing/Stopped'
    await this.send('POST', path, {
      ItemId: item.id,
      MediaSourceId: pb.mediaSourceId,
      PlaySessionId: pb.playSessionId,
      PlayMethod: 'DirectStream',
      CanSeek: true,
      IsPaused: paused,
      PositionTicks: Math.round(positionMs * 10000),
    })
  }

  async setPlayed(item: Item, played: boolean) {
    await this.send(played ? 'POST' : 'DELETE', `/Users/${this.uid}/PlayedItems/${item.id}`)
  }

  async setFavorite(item: Item, favorite: boolean) {
    await this.send(favorite ? 'POST' : 'DELETE', `/Users/${this.uid}/FavoriteItems/${item.id}`)
  }
}

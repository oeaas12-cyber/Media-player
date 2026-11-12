// Minimal Emby look-alike for local tests. It is deliberately strict about the sign-in request
// (exact Content-Type, MediaBrowser scheme) and logs what the app sent.
import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const dir = path.dirname(fileURLToPath(import.meta.url))
const art = (n) => path.join(dir, '..', 'public', 'demo', n + '.jpg')
const PORT = Number(process.env.PORT || 8096)
const TOKEN = 'tok-123'
const USER = { Id: 'user-1', Name: '24Oeaas' }
export const log = []

const titles = ['bbb:Big Buck Bunny:2008', 'sintel:Sintel:2010', 'tears:Tears of Steel:2012', 'elephants:Elephants Dream:2006']
const movies = []
for (let n = 0; n < 150; n++) {
  const [key, name, year] = titles[n % 4].split(':')
  movies.push({
    Id: `mv${n}`, Type: 'Movie', Name: n < 4 ? name : `${name} ${Math.floor(n / 4) + 1}`, ProductionYear: Number(year), RunTimeTicks: (90 + n) * 600000000,
    CommunityRating: 6 + (n % 30) / 10, OfficialRating: 'PG-13', Genres: [['Action', 'Drama', 'Comedy', 'Animation'][n % 4], 'Adventure'],
    Overview: 'A long overview that describes this film in some detail so that the layout can be checked with realistic text length on every screen size.',
    ImageTags: { Primary: key }, BackdropImageTags: [key], UserData: { PlaybackPositionTicks: n === 1 ? 24000000000 : 0, Played: n % 7 === 3, IsFavorite: n % 9 === 0 }, _art: key,
    People: [{ Name: 'Ada Actor', Role: 'Lead', Type: 'Actor', Id: 'p1' }, { Name: 'Ben Player', Role: 'Friend', Type: 'Actor', Id: 'p2' }],
  })
}
const show = { Id: 'sh1', Type: 'Series', Name: 'Bigger Screens', ProductionYear: 2024, CommunityRating: 8.1, Genres: ['Documentary'], Overview: 'A series about screens.', ImageTags: { Primary: 'showcase' }, BackdropImageTags: ['showcase'], UserData: {}, _art: 'showcase' }
const seasons = [1, 2].map((n) => ({ Id: `se${n}`, Type: 'Season', Name: `Season ${n}`, IndexNumber: n, SeriesId: 'sh1', SeriesName: show.Name, UserData: { Played: n === 1 } }))
const episodes = (sid) => [1, 2, 3, 4, 5].map((n) => ({
  Id: `${sid}e${n}`, Type: 'Episode', Name: `Episode title ${n}`, IndexNumber: n, ParentIndexNumber: Number(sid.slice(2)), SeriesId: 'sh1', SeasonId: sid, SeriesName: show.Name,
  RunTimeTicks: 27000000000, Overview: 'What happens in this episode, described in a sentence or two so it wraps onto a second line.', ImageTags: { Primary: 'e' + n },
  UserData: { PlaybackPositionTicks: n === 2 ? 9000000000 : 0, Played: n === 1 }, _art: 'showcase_e' + n,
}))
const all = () => [...movies, show, ...seasons, ...episodes('se1'), ...episodes('se2')]

function send(res, code, body, type = 'application/json') {
  res.writeHead(code, {
    'Content-Type': type, 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': '*', 'Access-Control-Allow-Methods': 'GET, POST, DELETE, OPTIONS',
  })
  res.end(typeof body === 'string' || Buffer.isBuffer(body) ? body : JSON.stringify(body))
}

export const server = http.createServer((req, res) => {
  const u = new URL(req.url, 'http://x')
  const p = u.pathname
  if (req.method === 'OPTIONS') return send(res, 200, '')
  let body = ''
  req.on('data', (c) => (body += c))
  req.on('end', () => {
    if (p === '/Users/AuthenticateByName') {
      log.push({ path: p, method: req.method, contentType: req.headers['content-type'], auth: req.headers['x-emby-authorization'], authorization: req.headers['authorization'] || null, body })
      // Like Emby: an unrecognised content type means the body is not read at all.
      let data = {}
      if (req.headers['content-type'] === 'application/json') { try { data = JSON.parse(body) } catch {} }
      if (!/^MediaBrowser /.test(req.headers['x-emby-authorization'] || '')) return send(res, 401, 'Invalid username or password. Please try again.', 'text/plain')
      if (data.Username !== USER.Name || data.Pw !== 'secret') return send(res, 401, 'Invalid username or password. Please try again.', 'text/plain')
      return send(res, 200, { User: USER, AccessToken: TOKEN, ServerId: 'srv' })
    }
    if (p === '/System/Info/Public') return send(res, 200, { ServerName: 'Home Cinema', ProductName: 'Emby Server' })
    const img = p.match(/^\/Items\/([^/]+)\/Images\//)
    if (img) {
      const it = all().find((x) => x.Id === img[1])
      const f = art('demo_' + (it?._art || 'bbb') + (p.includes('Backdrop') ? '_backdrop' : it?.Type === 'Episode' ? '' : '_poster'))
      return fs.existsSync(f) ? send(res, 200, fs.readFileSync(f), 'image/jpeg') : send(res, 404, '')
    }
    if (p.startsWith('/Videos/') && p.includes('/Subtitles/')) {
      return send(res, 200, 'WEBVTT\n\n00:00:00.000 --> 00:00:20.000\nSample subtitle line\n', 'text/vtt')
    }
    if (p.startsWith('/Videos/')) {
      const f = path.join(dir, 'sample.webm')
      if (!fs.existsSync(f)) return send(res, 404, '')
      const buf = fs.readFileSync(f)
      res.writeHead(200, { 'Content-Type': 'video/webm', 'Content-Length': buf.length, 'Accept-Ranges': 'bytes', 'Access-Control-Allow-Origin': '*' })
      return res.end(buf)
    }
    if (req.headers['x-emby-token'] !== TOKEN) return send(res, 401, 'Access token is invalid or expired.', 'text/plain')
    log.push({ path: p, method: req.method })
    const q = u.searchParams
    const page = (list) => {
      const start = Number(q.get('StartIndex') || 0), limit = Number(q.get('Limit') || list.length)
      return { Items: list.slice(start, start + limit), TotalRecordCount: list.length }
    }
    if (p === `/Users/${USER.Id}/Items/Resume`) return send(res, 200, { Items: [movies[1], episodes('se1')[1]] })
    if (p === '/Shows/NextUp') return send(res, 200, { Items: [episodes('se2')[0]] })
    if (p === '/Genres') return send(res, 200, { Items: ['Action', 'Adventure', 'Animation', 'Comedy', 'Documentary', 'Drama', 'Science Fiction', 'Thriller', 'Western'].map((Name) => ({ Name })) })
    if (p === `/Users/${USER.Id}/Items`) {
      const types = (q.get('IncludeItemTypes') || '').split(',')
      let list = [...movies, show].filter((x) => types.includes(x.Type))
      const term = (q.get('SearchTerm') || '').toLowerCase()
      if (term) list = list.filter((x) => x.Name.toLowerCase().includes(term))
      if (q.get('Genres')) list = list.filter((x) => x.Genres.includes(q.get('Genres')))
      if (q.get('Filters') === 'IsFavorite') list = list.filter((x) => x.UserData.IsFavorite)
      if (q.get('Filters') === 'IsUnplayed') list = list.filter((x) => !x.UserData.Played)
      return send(res, 200, page(list))
    }
    let m
    if ((m = p.match(new RegExp(`^/Users/${USER.Id}/Items/([^/]+)$`)))) {
      const it = all().find((x) => x.Id === m[1])
      return it ? send(res, 200, it) : send(res, 404, 'Not found', 'text/plain')
    }
    if (p.match(/^\/Shows\/[^/]+\/Seasons$/)) return send(res, 200, { Items: seasons })
    if (p.match(/^\/Shows\/[^/]+\/Episodes$/)) return send(res, 200, { Items: episodes(q.get('SeasonId') || 'se1') })
    if (p.match(/^\/Items\/[^/]+\/Similar$/)) return send(res, 200, { Items: movies.slice(4, 14) })
    if ((m = p.match(/^\/Items\/([^/]+)\/PlaybackInfo$/))) {
      return send(res, 200, { PlaySessionId: 'ps1', MediaSources: [{ Id: 'ms-' + m[1], Container: 'webm', DefaultAudioStreamIndex: 1, MediaStreams: [
        { Type: 'Video', Index: 0, Codec: 'vp8' },
        { Type: 'Audio', Index: 1, Language: 'eng', DisplayTitle: 'English · AAC · 5.1', IsDefault: true },
        { Type: 'Audio', Index: 2, Language: 'ara', DisplayTitle: 'Arabic · AAC · Stereo' },
        { Type: 'Subtitle', Index: 3, Language: 'eng', DisplayTitle: 'English', IsTextSubtitleStream: true },
        { Type: 'Subtitle', Index: 4, Language: 'ara', DisplayTitle: 'Arabic', IsTextSubtitleStream: true },
      ] }] })
    }
    if (p.startsWith('/Sessions/') || p.includes('/PlayedItems/') || p.includes('/FavoriteItems/')) return send(res, 204, '')
    send(res, 404, 'Not found', 'text/plain')
  })
})

if (process.argv[1] === fileURLToPath(import.meta.url)) server.listen(PORT, () => console.log('mock emby on', PORT))

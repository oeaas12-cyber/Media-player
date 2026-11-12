export function duration(ms?: number): string {
  if (!ms || ms <= 0) return ''
  if (ms < 60000) return `${Math.max(1, Math.round(ms / 1000))}s`
  const m = Math.round(ms / 60000)
  const h = Math.floor(m / 60)
  return h ? `${h}h ${m % 60}m` : `${m}m`
}

export function clock(ms: number): string {
  const s = Math.max(0, Math.floor(ms / 1000))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  const mm = h ? String(m).padStart(2, '0') : String(m)
  return `${h ? h + ':' : ''}${mm}:${String(sec).padStart(2, '0')}`
}

export function episodeLabel(season?: number, episode?: number): string {
  if (season == null || episode == null) return ''
  return `S${season} · E${episode}`
}

const LANGS: Record<string, string> = {
  eng: 'English', en: 'English', ara: 'Arabic', ar: 'Arabic', fre: 'French', fra: 'French', fr: 'French',
  spa: 'Spanish', es: 'Spanish', ger: 'German', deu: 'German', de: 'German', ita: 'Italian', it: 'Italian',
  por: 'Portuguese', pt: 'Portuguese', tur: 'Turkish', tr: 'Turkish', hin: 'Hindi', hi: 'Hindi',
  jpn: 'Japanese', ja: 'Japanese', kor: 'Korean', ko: 'Korean', chi: 'Chinese', zho: 'Chinese', zh: 'Chinese',
  rus: 'Russian', ru: 'Russian', dut: 'Dutch', nld: 'Dutch', nl: 'Dutch', per: 'Persian', fas: 'Persian',
}
export const languageName = (code?: string) => (code ? LANGS[code.toLowerCase()] || code.toUpperCase() : undefined)

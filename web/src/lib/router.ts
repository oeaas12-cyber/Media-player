import { useSyncExternalStore } from 'react'

/** Tiny hash router: works on the web, from file:// (TV and desktop apps), and inside WebViews. */
const subscribe = (l: () => void) => {
  window.addEventListener('hashchange', l)
  return () => window.removeEventListener('hashchange', l)
}
const read = () => window.location.hash.replace(/^#/, '') || '/'

export interface Route {
  path: string
  parts: string[]
  query: Record<string, string>
}

export function useRoute(): Route {
  const raw = useSyncExternalStore(subscribe, read)
  const [path, qs = ''] = raw.split('?')
  const query: Record<string, string> = {}
  qs.split('&').forEach((p) => {
    const [k, v] = p.split('=')
    if (k) query[decodeURIComponent(k)] = decodeURIComponent(v || '')
  })
  return { path, parts: path.split('/').filter(Boolean).map(decodeURIComponent), query }
}

export function go(path: string, replace = false) {
  if (replace) window.location.replace('#' + path)
  else window.location.hash = path
}

export function back() {
  if (window.history.length > 1) window.history.back()
  else go('/')
}

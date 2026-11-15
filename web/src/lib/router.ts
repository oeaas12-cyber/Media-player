import { useSyncExternalStore } from 'react'
import { flushSync } from 'react-dom'

/**
 * Tiny hash router: works on the web, from file:// (TV and desktop apps), and inside WebViews.
 * Navigations run inside a view transition where the browser supports it, so the tapped
 * poster morphs into the details page and screens cross-fade instead of snapping.
 */
const listeners = new Set<() => void>()
const read = () => window.location.hash.replace(/^#/, '') || '/'
let current = read()
/** Scroll position of each screen, restored when the user comes back to it. */
const scrolls = new Map<string, number>()
/** The title the user last opened; its card receives the poster again on the way back. */
let lastOpened: string | undefined

try {
  window.history.scrollRestoration = 'manual'
} catch {}

function notify() {
  current = read()
  listeners.forEach((l) => l())
}

const isPlayer = (p: string) => p.startsWith('/play/')

function clearNames() {
  document.querySelectorAll<HTMLElement>('[data-vt]').forEach((el) => {
    el.style.removeProperty('view-transition-name')
    el.removeAttribute('data-vt')
  })
}

/** Marks one element as the shared "poster" for the next transition. */
export function markPoster(el: HTMLElement | null | undefined) {
  // A details page already owns the poster name; two elements may never share it.
  if (!el || document.querySelector('.d-poster')) return
  clearNames()
  el.style.setProperty('view-transition-name', 'poster')
  el.setAttribute('data-vt', '')
}

function run(update: () => void, to: string) {
  const doc = document as any
  const reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches
  // No transition into or out of the video player, without browser support, or when motion is reduced.
  if (!doc.startViewTransition || reduce || isPlayer(to) || isPlayer(current)) {
    clearNames()
    update()
    return
  }
  const t = doc.startViewTransition(() => flushSync(update))
  // A transition that is skipped or interrupted rejects these; the navigation itself still happened.
  const quiet = () => {}
  t.ready.catch(quiet)
  t.updateCallbackDone.catch(quiet)
  t.finished.then(clearNames, clearNames)
}

window.addEventListener('popstate', () => {
  const to = read()
  if (to === current) return
  scrolls.set(current, window.scrollY)
  run(() => {
    clearNames()
    notify()
    window.scrollTo(0, scrolls.get(to) || 0)
    // Coming back to a list: hand the poster back to the card it came from.
    if (lastOpened) {
      const cards = document.querySelectorAll<HTMLElement>('[data-item]')
      for (let i = 0; i < cards.length; i++) {
        if (cards[i].getAttribute('data-item') === lastOpened) {
          const r = cards[i].getBoundingClientRect()
          if (r.bottom > 0 && r.top < window.innerHeight && r.right > 0 && r.left < window.innerWidth) {
            markPoster(cards[i].querySelector<HTMLElement>('.art'))
            break
          }
        }
      }
    }
  }, to)
})
window.addEventListener('hashchange', () => {
  if (read() !== current) notify()
})

export interface Route {
  path: string
  parts: string[]
  query: Record<string, string>
}

const subscribe = (l: () => void) => {
  listeners.add(l)
  return () => listeners.delete(l)
}

export function useRoute(): Route {
  const raw = useSyncExternalStore(subscribe, () => current)
  const [path, qs = ''] = raw.split('?')
  const query: Record<string, string> = {}
  qs.split('&').forEach((p) => {
    const [k, v] = p.split('=')
    if (k) query[decodeURIComponent(k)] = decodeURIComponent(v || '')
  })
  return { path, parts: path.split('/').filter(Boolean).map(decodeURIComponent), query }
}

export function go(path: string, replace = false) {
  if (path === current) return
  const m = path.match(/^\/item\/([^?]+)/)
  if (m) lastOpened = decodeURIComponent(m[1])
  scrolls.set(current, window.scrollY)
  run(() => {
    if (replace) window.history.replaceState(null, '', '#' + path)
    else window.history.pushState(null, '', '#' + path)
    notify()
    window.scrollTo(0, 0)
  }, path)
}

export function back() {
  if (window.history.length > 1) window.history.back()
  else go('/')
}

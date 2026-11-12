/**
 * Remote-control navigation for TVs (LG, Samsung, Android TV) and keyboards:
 * arrow keys move focus to the nearest control in that direction, Back goes back.
 */
import { back } from './router'

const SELECTOR = 'button:not([disabled]), a[href], input:not([disabled]), select, [tabindex="0"]'

function scope(): ParentNode {
  const scopes = document.querySelectorAll('[data-focus-scope]')
  return scopes.length ? scopes[scopes.length - 1] : document
}

function candidates(): HTMLElement[] {
  return Array.from(scope().querySelectorAll<HTMLElement>(SELECTOR)).filter((el) => {
    if (el.closest('[data-nofocus]')) return false
    const r = el.getBoundingClientRect()
    return r.width > 0 && r.height > 0 && getComputedStyle(el).visibility !== 'hidden'
  })
}

export function focusFirst() {
  const all = candidates()
  const preferred = all.find((el) => el.hasAttribute('data-autofocus')) || all[0]
  preferred?.focus()
}

function move(dir: 'left' | 'right' | 'up' | 'down'): boolean {
  const all = candidates()
  const cur = document.activeElement as HTMLElement | null
  if (!cur || !all.includes(cur)) {
    focusFirst()
    return true
  }
  const a = cur.getBoundingClientRect()
  const ax = a.left + a.width / 2
  const ay = a.top + a.height / 2
  let best: HTMLElement | null = null
  let bestScore = Infinity
  for (const el of all) {
    if (el === cur) continue
    const b = el.getBoundingClientRect()
    const bx = b.left + b.width / 2
    const by = b.top + b.height / 2
    let primary: number
    let cross: number
    let overlap: boolean
    if (dir === 'left' || dir === 'right') {
      primary = dir === 'right' ? b.left - a.right : a.left - b.right
      if ((dir === 'right' ? bx - ax : ax - bx) <= 1) continue
      cross = Math.abs(by - ay)
      overlap = b.top < a.bottom && b.bottom > a.top
    } else {
      primary = dir === 'down' ? b.top - a.bottom : a.top - b.bottom
      if ((dir === 'down' ? by - ay : ay - by) <= 1) continue
      cross = Math.abs(bx - ax)
      overlap = b.left < a.right && b.right > a.left
    }
    primary = Math.max(0, primary)
    // Prefer things directly in line; penalise sideways distance heavily.
    const score = primary + (overlap ? 0 : cross * 3) + cross * 0.15
    if (score < bestScore) {
      bestScore = score
      best = el
    }
  }
  if (!best) return false
  best.focus({ preventScroll: true })
  best.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' })
  return true
}

const BACK_KEYS = new Set([461, 10009, 8, 27]) // webOS, Tizen, Backspace, Escape

export function installRemoteNavigation() {
  // Samsung TVs only deliver media keys that were registered.
  try {
    const t = (window as any).tizen
    t?.tvinputdevice?.registerKeyBatch?.(['MediaPlayPause', 'MediaPlay', 'MediaPause', 'MediaStop', 'MediaFastForward', 'MediaRewind'])
  } catch {}

  window.addEventListener('keydown', (e) => {
    const el = document.activeElement as HTMLElement | null
    const typing = !!el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA')
    if (BACK_KEYS.has(e.keyCode)) {
      if (typing && (e.keyCode === 8 || e.keyCode === 27)) {
        if (e.keyCode === 27) el!.blur()
        return
      }
      // Screens with their own Back behaviour (player, menus) listen for this first.
      const ev = new CustomEvent('app-back', { cancelable: true })
      if (window.dispatchEvent(ev)) back()
      e.preventDefault()
      return
    }
    const map: Record<string, 'left' | 'right' | 'up' | 'down'> = { ArrowLeft: 'left', ArrowRight: 'right', ArrowUp: 'up', ArrowDown: 'down' }
    const dir = map[e.key]
    if (!dir || e.defaultPrevented) return
    if (typing && (dir === 'left' || dir === 'right')) return
    if (el?.tagName === 'SELECT' && (dir === 'up' || dir === 'down')) return
    if (move(dir)) e.preventDefault()
  })
}

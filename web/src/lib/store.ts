import { useSyncExternalStore } from 'react'
import type { Prefs, Profile, Server } from './types'

export interface State {
  deviceId: string
  profiles: Profile[]
  activeProfileId?: string
  /** False until a profile has been picked in this session (drives "Who's watching?"). */
  prefs: Prefs
}

const KEY = 'lumina.state.v1'

function uid(): string {
  const a = new Uint8Array(16)
  ;(window.crypto || (window as any).msCrypto).getRandomValues(a)
  return Array.from(a, (b) => b.toString(16).padStart(2, '0')).join('')
}

const defaults = (): State => ({
  deviceId: uid(),
  profiles: [],
  prefs: { skipBack: 10, skipForward: 30, autoplayNext: true, subtitleLang: '', subtitleSize: 100, alwaysConvert: false },
})

function load(): State {
  try {
    const raw = localStorage.getItem(KEY)
    if (raw) {
      const s = JSON.parse(raw) as State
      return { ...defaults(), ...s, prefs: { ...defaults().prefs, ...(s.prefs || {}) } }
    }
  } catch {}
  return defaults()
}

let state: State = load()
const listeners = new Set<() => void>()

function commit(next: State) {
  state = next
  try {
    localStorage.setItem(KEY, JSON.stringify(state))
  } catch {}
  listeners.forEach((l) => l())
}

export const store = {
  get: () => state,
  subscribe(l: () => void) {
    listeners.add(l)
    return () => listeners.delete(l)
  },
  update(fn: (s: State) => State) {
    commit(fn(state))
  },
  newId: uid,
}

export function useStore(): State {
  return useSyncExternalStore(store.subscribe, store.get)
}

export const activeProfile = (s: State): Profile | undefined =>
  s.profiles.find((p) => p.id === s.activeProfileId) || s.profiles[0]

export const activeServer = (s: State): Server | undefined => {
  const p = activeProfile(s)
  return p?.servers.find((x) => x.id === p.activeServerId) || p?.servers[0]
}

export const actions = {
  addProfile(name: string, color: number, kids: boolean): Profile {
    const p: Profile = { id: uid(), name: name.trim() || 'Profile', color, kids, servers: [] }
    store.update((s) => ({ ...s, profiles: [...s.profiles, p], activeProfileId: p.id }))
    return p
  },
  updateProfile(id: string, patch: Partial<Profile>) {
    store.update((s) => ({ ...s, profiles: s.profiles.map((p) => (p.id === id ? { ...p, ...patch } : p)) }))
  },
  removeProfile(id: string) {
    store.update((s) => {
      const profiles = s.profiles.filter((p) => p.id !== id)
      return { ...s, profiles, activeProfileId: s.activeProfileId === id ? profiles[0]?.id : s.activeProfileId }
    })
  },
  selectProfile(id: string) {
    store.update((s) => ({ ...s, activeProfileId: id }))
  },
  addServer(server: Server) {
    store.update((s) => {
      let profiles = s.profiles
      let activeProfileId = s.activeProfileId
      if (!profiles.length) {
        const me: Profile = { id: uid(), name: server.userName || 'Me', color: 0, kids: false, servers: [] }
        profiles = [me]
        activeProfileId = me.id
      }
      const pid = activeProfileId || profiles[0].id
      return {
        ...s,
        activeProfileId: pid,
        profiles: profiles.map((p) =>
          p.id === pid
            ? { ...p, servers: [...p.servers.filter((x) => !(x.url === server.url && x.userId === server.userId)), server], activeServerId: server.id }
            : p,
        ),
      }
    })
  },
  removeServer(id: string) {
    store.update((s) => ({
      ...s,
      profiles: s.profiles.map((p) => {
        const servers = p.servers.filter((x) => x.id !== id)
        return { ...p, servers, activeServerId: p.activeServerId === id ? servers[0]?.id : p.activeServerId }
      }),
    }))
  },
  selectServer(id: string) {
    store.update((s) => ({
      ...s,
      profiles: s.profiles.map((p) => (p.servers.some((x) => x.id === id) ? { ...p, activeServerId: id } : p)),
    }))
  },
  setPrefs(patch: Partial<Prefs>) {
    store.update((s) => ({ ...s, prefs: { ...s.prefs, ...patch } }))
  },
}

/** Avatar gradients; a profile stores an index. */
export const AVATARS: [string, string][] = [
  ['#7dd3fc', '#6366f1'],
  ['#f472b6', '#ef4444'],
  ['#fbbf24', '#f97316'],
  ['#34d399', '#0ea5e9'],
  ['#a78bfa', '#ec4899'],
  ['#22d3ee', '#10b981'],
  ['#fb7185', '#a855f7'],
  ['#e2e8f0', '#64748b'],
]

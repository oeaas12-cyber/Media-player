import { activeProfile, useStore } from '../lib/store'
import { go } from '../lib/router'
import { Avatar, Icon } from './ui'

const TABS = [
  { path: '/', label: 'Home', icon: 'home' },
  { path: '/movies', label: 'Movies', icon: 'movie' },
  { path: '/shows', label: 'TV Shows', icon: 'tv' },
  { path: '/search', label: 'Search', icon: 'search' },
  { path: '/settings', label: 'Settings', icon: 'settings' },
]

export function Nav({ current }: { current: string }) {
  const s = useStore()
  const p = activeProfile(s)
  return (
    <nav className="nav">
      <div className="nav-pill">
        {TABS.map((t) => (
          <button key={t.path} className={'nav-item' + (current === t.path ? ' on' : '')} onClick={() => go(t.path)}>
            <Icon name={t.icon} />
            {t.label}
          </button>
        ))}
        {p ? (
          <button className="nav-avatar" onClick={() => go('/profiles')} aria-label="Switch profile">
            <Avatar name={p.name} color={p.color} size={2.3} />
          </button>
        ) : null}
      </div>
    </nav>
  )
}

export function Backdrop({ src }: { src?: string }) {
  return <div className="backdrop">{src ? <img src={src} alt="" key={src} /> : null}</div>
}

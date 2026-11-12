import { useEffect, useState } from 'react'
import { activeProfile, activeServer, useStore } from './lib/store'
import { useSource } from './lib/source'
import { go, useRoute } from './lib/router'
import { focusFirst } from './lib/focus'
import { Backdrop, Nav } from './components/Nav'
import { Connect } from './screens/Connect'
import { Profiles } from './screens/Profiles'
import { Home } from './screens/Home'
import { Library } from './screens/Library'
import { Search } from './screens/Search'
import { Detail } from './screens/Detail'
import { Settings } from './screens/Settings'
import { Player } from './screens/Player'

/** Asked once per app launch when there is more than one profile. */
let pickedThisSession = false
try {
  pickedThisSession = sessionStorage.getItem('lumina.picked') === '1'
} catch {}
const markPicked = () => {
  pickedThisSession = true
  try {
    sessionStorage.setItem('lumina.picked', '1')
  } catch {}
}

export function App() {
  const s = useStore()
  const route = useRoute()
  const source = useSource()
  const profile = activeProfile(s)
  const server = activeServer(s)
  const [backdrop, setBackdrop] = useState<string>()
  const [, force] = useState(0)

  // On TVs there is no pointer: give the remote something to start from on every screen.
  useEffect(() => {
    const t = setTimeout(() => {
      if (document.documentElement.classList.contains('kbd') || isTv()) focusFirst()
    }, 250)
    return () => clearTimeout(t)
  }, [route.path, !!source])

  const top = route.parts[0] || ''
  if (top === 'profiles' || (s.profiles.length > 1 && !pickedThisSession)) {
    return (
      <Shell>
        <Profiles canCancel={top === 'profiles' && pickedThisSession} onPicked={() => { markPicked(); force((n) => n + 1); go('/', true) }} />
      </Shell>
    )
  }
  if (!profile || !server || !source || top === 'connect') {
    return (
      <Shell>
        <Connect firstRun={!server} />
      </Shell>
    )
  }
  if (top === 'play') {
    return <Player source={source} id={route.parts[1]} startAt={route.query.t != null ? Number(route.query.t) : undefined} key={route.parts[1]} />
  }

  let screen: JSX.Element
  let tab = '/'
  if (top === 'movies') { screen = <Library source={source} kind="movie" key="m" />; tab = '/movies' }
  else if (top === 'shows') { screen = <Library source={source} kind="show" key="s" />; tab = '/shows' }
  else if (top === 'search') { screen = <Search source={source} />; tab = '/search' }
  else if (top === 'settings') { screen = <Settings />; tab = '/settings' }
  else if (top === 'item') { screen = <Detail source={source} id={route.parts[1]} onBackdrop={setBackdrop} key={route.parts[1]} />; tab = '' }
  else screen = <Home source={source} onBackdrop={setBackdrop} key={server.id + profile.id} />

  return (
    <Shell backdrop={top === 'settings' ? undefined : backdrop}>
      {top === 'item' ? null : <Nav current={tab} />}
      {screen}
    </Shell>
  )
}

function Shell({ children, backdrop }: { children: React.ReactNode; backdrop?: string }) {
  return (
    <>
      <Backdrop src={backdrop} />
      <div className="app">{children}</div>
    </>
  )
}

export function isTv(): boolean {
  return /Web0S|webOS|Tizen|SMART-TV|SmartTV|AFT|BRAVIA|Android TV|GoogleTV/i.test(navigator.userAgent)
}

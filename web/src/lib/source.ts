import { useMemo } from 'react'
import { DemoSource } from './demo'
import { EmbySource } from './emby'
import { activeProfile, activeServer, useStore } from './store'
import type { Server, Source } from './types'

export const demoServer = (): Server => ({ id: 'demo', type: 'demo', name: 'Demo library', url: 'Blender open movies', userId: 'demo', userName: 'Demo', token: '' })

/** The library the current profile is browsing, or null when none is connected. */
export function useSource(): Source | null {
  const s = useStore()
  const server = activeServer(s)
  const kids = !!activeProfile(s)?.kids
  return useMemo(() => {
    if (!server) return null
    return server.type === 'demo' ? new DemoSource() : new EmbySource(server, s.deviceId, kids)
  }, [server?.id, server?.token, s.deviceId, kids])
}

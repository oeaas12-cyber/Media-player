import { useState } from 'react'
import { embyLogin } from '../lib/emby'
import { actions, useStore } from '../lib/store'
import { demoServer } from '../lib/source'
import { back, go } from '../lib/router'
import { Icon } from '../components/ui'

/** First-run and "add a server" screen: sign in to an Emby or Jellyfin server. */
export function Connect({ firstRun }: { firstRun: boolean }) {
  const s = useStore()
  const [url, setUrl] = useState('')
  const [user, setUser] = useState('')
  const [pass, setPass] = useState('')
  const [show, setShow] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function submit(e?: React.FormEvent) {
    e?.preventDefault()
    if (busy || !url.trim() || !user.trim()) return
    setBusy(true)
    setError('')
    try {
      const server = await embyLogin(url, user.trim(), pass, s.deviceId)
      actions.addServer(server)
      go('/', true)
    } catch (err: any) {
      setError(err?.message || 'Could not sign in.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="center">
      <div className="brand">
        <img src="./icon.svg" alt="" />
        <b>Lumina</b>
      </div>
      <form className="panel" onSubmit={submit}>
        <h2 style={{ fontSize: '1.4rem', fontWeight: 750, marginBottom: '0.3rem' }}>Connect your server</h2>
        <p className="subtle" style={{ marginBottom: '1.4rem', fontSize: '0.92rem', lineHeight: 1.4 }}>
          Sign in with your Emby or Jellyfin username and password.
        </p>
        {error ? <div className="error">{error}</div> : null}
        <label className="field">
          <span>Server address</span>
          <div className="input">
            <input data-autofocus value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://my-server.example.com" inputMode="url" autoCapitalize="off" autoCorrect="off" spellCheck={false} />
          </div>
        </label>
        <label className="field">
          <span>Username</span>
          <div className="input">
            <input value={user} onChange={(e) => setUser(e.target.value)} autoCapitalize="off" autoCorrect="off" spellCheck={false} autoComplete="username" />
          </div>
        </label>
        <label className="field">
          <span>Password</span>
          <div className="input">
            <input type={show ? 'text' : 'password'} value={pass} onChange={(e) => setPass(e.target.value)} autoCapitalize="off" autoCorrect="off" spellCheck={false} autoComplete="current-password" style={{ paddingRight: '3rem' }} />
            <button type="button" className="eye" onClick={() => setShow(!show)} aria-label={show ? 'Hide password' : 'Show password'}>
              <Icon name="eye" />
            </button>
          </div>
        </label>
        <button className="btn primary full" type="submit" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
        <div style={{ textAlign: 'center', marginTop: '1rem' }}>
          {firstRun ? (
            <button type="button" className="link" onClick={() => { actions.addServer(demoServer()); go('/', true) }}>
              Try the demo library
            </button>
          ) : (
            <button type="button" className="link" onClick={back}>
              Cancel
            </button>
          )}
        </div>
      </form>
    </div>
  )
}

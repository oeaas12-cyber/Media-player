import { useState } from 'react'
import { APP_VERSION } from '../lib/emby'
import { actions, activeProfile, activeServer, useStore } from '../lib/store'
import { go } from '../lib/router'
import { Avatar, Icon } from '../components/ui'
import { ProfileEditor } from './Profiles'
import type { Profile } from '../lib/types'

function Seg<T extends string | number | boolean>({ value, options, onChange }: { value: T; options: [T, string][]; onChange: (v: T) => void }) {
  return (
    <div className="seg">
      {options.map(([v, label]) => (
        <button key={String(v)} className={v === value ? 'on' : ''} onClick={() => onChange(v)}>{label}</button>
      ))}
    </div>
  )
}

export function Settings() {
  const s = useStore()
  const profile = activeProfile(s)
  const server = activeServer(s)
  const [edit, setEdit] = useState<Profile | 'new' | null>(null)
  const [removing, setRemoving] = useState<string>()
  const p = s.prefs
  return (
    <div className="page">
      <div className="pad settings">
        <h1 className="title-xl">Settings</h1>

        <div className="group-title">Profiles</div>
        <div className="group">
          {s.profiles.map((pr) => (
            <button className="item" key={pr.id} onClick={() => setEdit(pr)}>
              <Avatar name={pr.name} color={pr.color} size={2.2} />
              <div className="label" style={{ marginLeft: '0.8rem' }}>
                <div className="nowrap">{pr.name}</div>
                <small>{[pr.id === profile?.id ? 'Current' : '', pr.kids ? 'Kids' : ''].filter(Boolean).join(' · ') || 'Standard'}</small>
              </div>
              <div className="value faint" style={{ width: '1rem', height: '1rem' }}><Icon name="chevron" /></div>
            </button>
          ))}
          <button className="item" onClick={() => setEdit('new')}><div className="label" style={{ color: 'var(--accent)' }}>Add profile</div></button>
          {s.profiles.length > 1 ? (
            <button className="item" onClick={() => go('/profiles')}><div className="label" style={{ color: 'var(--accent)' }}>Switch profile</div></button>
          ) : null}
        </div>

        <div className="group-title">Servers</div>
        <div className="group">
          {(profile?.servers || []).map((sv) => (
            <div className="item" key={sv.id}>
              <div className="label">
                <div className="nowrap">{sv.name}</div>
                <small>{sv.type === 'demo' ? 'Sample library' : `${sv.userName} · ${sv.url.replace(/^https?:\/\//, '')}`}</small>
              </div>
              <div className="value">
                {sv.id === server?.id ? <span className="faint" style={{ fontSize: '0.85rem', fontWeight: 650 }}>Active</span> : (
                  <button className="btn small" onClick={() => actions.selectServer(sv.id)}>Use</button>
                )}
                <button className="btn small danger" onClick={() => { if (removing === sv.id) actions.removeServer(sv.id); else setRemoving(sv.id) }}>
                  {removing === sv.id ? 'Confirm' : 'Remove'}
                </button>
              </div>
            </div>
          ))}
          <button className="item" onClick={() => go('/connect')}><div className="label" style={{ color: 'var(--accent)' }}>Add Emby or Jellyfin server</div></button>
        </div>

        <div className="group-title">Playback</div>
        <div className="group">
          <div className="item">
            <div className="label">Skip back</div>
            <div className="value"><Seg value={p.skipBack} options={[[5, '5s'], [10, '10s'], [15, '15s'], [30, '30s']]} onChange={(v) => actions.setPrefs({ skipBack: v })} /></div>
          </div>
          <div className="item">
            <div className="label">Skip forward</div>
            <div className="value"><Seg value={p.skipForward} options={[[10, '10s'], [15, '15s'], [30, '30s'], [60, '60s']]} onChange={(v) => actions.setPrefs({ skipForward: v })} /></div>
          </div>
          <div className="item">
            <div className="label">Play next episode</div>
            <div className="value"><Seg value={p.autoplayNext} options={[[true, 'On'], [false, 'Off']]} onChange={(v) => actions.setPrefs({ autoplayNext: v })} /></div>
          </div>
          <div className="item">
            <div className="label">Subtitles</div>
            <div className="value"><Seg value={p.subtitleLang} options={[['', 'Off'], ['eng', 'English'], ['ara', 'Arabic']]} onChange={(v) => actions.setPrefs({ subtitleLang: v })} /></div>
          </div>
          <div className="item">
            <div className="label">Subtitle size</div>
            <div className="value"><Seg value={p.subtitleSize} options={[[80, 'S'], [100, 'M'], [130, 'L'], [170, 'XL']]} onChange={(v) => actions.setPrefs({ subtitleSize: v })} /></div>
          </div>
          <div className="item">
            <div className="label">Video</div>
            <div className="value"><Seg value={p.alwaysConvert} options={[[false, 'Original'], [true, 'Converted']]} onChange={(v) => actions.setPrefs({ alwaysConvert: v })} /></div>
          </div>
        </div>
        <p className="note">Original plays the file as it is. Converted asks the server to convert it, which helps when a file has no picture or sound on this device.</p>

        <div className="group-title">About</div>
        <div className="group">
          <div className="item"><div className="label">Lumina</div><div className="value">Version {APP_VERSION}</div></div>
        </div>
      </div>
      {edit ? <ProfileEditor profile={edit === 'new' ? null : edit} canDelete={s.profiles.length > 1} onClose={() => setEdit(null)} nextColor={s.profiles.length} /> : null}
    </div>
  )
}

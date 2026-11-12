import { useState } from 'react'
import { actions, AVATARS, useStore } from '../lib/store'
import type { Profile } from '../lib/types'
import { Avatar, Icon } from '../components/ui'

/** "Who's watching?" — pick, add and edit profiles. Each profile has its own servers and history. */
export function Profiles({ onPicked, canCancel }: { onPicked: () => void; canCancel: boolean }) {
  const s = useStore()
  const [manage, setManage] = useState(false)
  const [edit, setEdit] = useState<Profile | 'new' | null>(null)
  return (
    <div className="center">
      <h1 className="title-xl">{manage ? 'Manage profiles' : "Who's watching?"}</h1>
      <div className="who">
        {s.profiles.map((p) => (
          <button key={p.id} className="who-tile" data-autofocus={p.id === s.activeProfileId ? '' : undefined}
            onClick={() => { if (manage) setEdit(p); else { actions.selectProfile(p.id); onPicked() } }}>
            <Avatar name={p.name} color={p.color} size={6.5} />
            <b>{p.name}</b>
            {manage ? <small>Edit</small> : p.kids ? <small>Kids</small> : null}
          </button>
        ))}
        {s.profiles.length < 8 ? (
          <button className="who-tile" onClick={() => setEdit('new')}>
            <span className="avatar" style={{ width: '6.5rem', height: '6.5rem', background: 'rgba(255,255,255,.08)' }}>
              <span style={{ width: '2.2rem', height: '2.2rem' }}><Icon name="plus" /></span>
            </span>
            <b>Add profile</b>
          </button>
        ) : null}
      </div>
      <div className="actions">
        <button className="btn" onClick={() => setManage(!manage)}>{manage ? 'Done' : 'Manage profiles'}</button>
        {canCancel && !manage ? <button className="btn" onClick={onPicked}>Cancel</button> : null}
      </div>
      {edit ? <ProfileEditor profile={edit === 'new' ? null : edit} canDelete={s.profiles.length > 1} onClose={() => setEdit(null)} nextColor={s.profiles.length} /> : null}
    </div>
  )
}

export function ProfileEditor({ profile, canDelete, onClose, nextColor }: { profile: Profile | null; canDelete: boolean; onClose: () => void; nextColor: number }) {
  const [name, setName] = useState(profile?.name || '')
  const [color, setColor] = useState(profile?.color ?? nextColor % AVATARS.length)
  const [kids, setKids] = useState(profile?.kids || false)
  const [confirm, setConfirm] = useState(false)
  function save() {
    if (!name.trim()) return
    if (profile) actions.updateProfile(profile.id, { name: name.trim(), color, kids })
    else actions.addProfile(name, color, kids)
    onClose()
  }
  return (
    <div className="overlay" data-focus-scope>
      <div className="panel">
        <div style={{ display: 'flex', alignItems: 'center', marginBottom: '1.3rem' }}>
          <Avatar name={name || '?'} color={color} size={3.6} />
          <h2 style={{ marginLeft: '1rem', fontSize: '1.4rem', fontWeight: 750 }}>{profile ? 'Edit profile' : 'New profile'}</h2>
        </div>
        <label className="field">
          <span>Name</span>
          <div className="input"><input data-autofocus value={name} maxLength={20} onChange={(e) => setName(e.target.value)} /></div>
        </label>
        <div className="field">
          <span>Colour</span>
          <div className="swatches">
            {AVATARS.map(([a, b], i) => (
              <button key={i} className={'swatch' + (i === color ? ' on' : '')} style={{ background: `linear-gradient(135deg, ${a}, ${b})` }} onClick={() => setColor(i)} aria-label={`Colour ${i + 1}`} />
            ))}
          </div>
        </div>
        <div className="field">
          <span>Type</span>
          <div className="seg">
            <button className={!kids ? 'on' : ''} onClick={() => setKids(false)}>Standard</button>
            <button className={kids ? 'on' : ''} onClick={() => setKids(true)}>Kids</button>
          </div>
        </div>
        <div className="actions" style={{ marginTop: '1.2rem' }}>
          <button className="btn primary" onClick={save}>Save</button>
          <button className="btn" onClick={onClose}>Cancel</button>
          {profile && canDelete ? (
            <button className="btn danger" onClick={() => { if (confirm) { actions.removeProfile(profile.id); onClose() } else setConfirm(true) }}>
              {confirm ? 'Really delete?' : 'Delete'}
            </button>
          ) : null}
        </div>
      </div>
    </div>
  )
}

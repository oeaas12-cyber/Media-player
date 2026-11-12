import { createRoot } from 'react-dom/client'
import { App } from './App'
import { installRemoteNavigation } from './lib/focus'
import './styles.css'

// Show focus rings only while a keyboard or TV remote is in use.
const html = document.documentElement
window.addEventListener('keydown', (e) => {
  if (e.key.startsWith('Arrow') || e.key === 'Tab' || e.key === 'Enter') html.classList.add('kbd')
}, true)
window.addEventListener('mousedown', () => html.classList.remove('kbd'), true)
window.addEventListener('touchstart', () => html.classList.remove('kbd'), true)

installRemoteNavigation()
createRoot(document.getElementById('root')!).render(<App />)

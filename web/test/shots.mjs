// End-to-end check against the mock Emby server, plus screenshots for the README.
import { createRequire } from 'node:module'
const { chromium } = createRequire(import.meta.url)(process.env.PLAYWRIGHT_PATH || 'playwright')
import { spawn } from 'node:child_process'
import fs from 'node:fs'
import { server, log } from './mock-emby.mjs'

const OUT = process.env.OUT || '../docs/lumina'
fs.mkdirSync(OUT, { recursive: true })
await new Promise((r) => server.listen(8096, r))
const preview = spawn('npx', ['vite', 'preview', '--port', '4173', '--strictPort'], { stdio: 'ignore' })
await new Promise((r) => setTimeout(r, 2500))
const APP = 'http://localhost:4173/'
const fail = (m) => { console.error('FAIL:', m); process.exitCode = 1 }
const browser = await chromium.launch()

async function run(name, viewport, scale) {
  const ctx = await browser.newContext({ viewport, deviceScaleFactor: scale })
  const page = await ctx.newPage()
  page.on('pageerror', (e) => fail(`${name} page error: ${e.message}`))
  const shot = async (n) => { await page.waitForTimeout(700); await page.screenshot({ path: `${OUT}/${name}-${n}.png` }) }
  await page.goto(APP)
  await page.waitForSelector('text=Connect your server')
  await shot('connect')

  const inputs = page.locator('.panel input')
  await inputs.nth(0).fill('localhost:8096')
  await inputs.nth(1).fill('24Oeaas')
  log.length = 0
  await inputs.nth(2).fill('wrong')
  await page.click('button[type=submit]')
  await page.waitForSelector('.error')
  const wrong = log.filter((l) => l.path === '/Users/AuthenticateByName')
  if (wrong.length !== 1) fail(`${name}: a wrong password made ${wrong.length} sign-in attempts (must be exactly 1)`)
  if (!(await page.textContent('.error')).includes('Invalid username or password')) fail(`${name}: server message not shown`)

  log.length = 0
  await inputs.nth(2).fill('secret')
  await page.click('button[type=submit]')
  await page.waitForSelector('.hero', { timeout: 8000 }).catch(() => fail(`${name}: sign-in did not reach Home`))
  const ok = log.find((l) => l.path === '/Users/AuthenticateByName')
  if (name === 'tv') console.log('SIGN-IN REQUEST AS SENT:\n', JSON.stringify(ok, null, 2).replace(/"Pw":"[^"]*"/, '"Pw":"***"'))
  if (ok?.contentType !== 'application/json') fail(`${name}: content type was ${ok?.contentType}`)
  if (ok?.authorization) fail(`${name}: an Authorization header was sent`)
  await shot('home')

  await page.goto(APP + '#/movies'); await page.waitForSelector('.grid .card'); await shot('movies')
  const before = await page.locator('.grid .card').count()
  await page.mouse.wheel(0, 30000); await page.waitForTimeout(1200)
  const after = await page.locator('.grid .card').count()
  if (!(after > before)) fail(`${name}: the grid did not load more (${before} → ${after})`)
  await page.goto(APP + '#/item/mv1'); await page.waitForSelector('.detail-body'); await shot('movie')
  await page.goto(APP + '#/item/sh1'); await page.waitForSelector('.episode'); await shot('show')
  await page.goto(APP + '#/search'); await page.fill('.input input', 'sintel'); await page.waitForSelector('.grid .card'); await shot('search')
  await page.goto(APP + '#/settings'); await page.waitForSelector('.group'); await shot('settings')

  await page.goto(APP + '#/play/mv0?t=0'); await page.waitForSelector('video')
  await page.waitForFunction(() => document.querySelector('video')?.currentTime > 1, null, { timeout: 15000 }).catch(() => fail(`${name}: video did not start playing`))
  await page.mouse.move(200, 200); await shot('player')
  await page.mouse.move(300, 300); await page.waitForTimeout(200); await page.locator('.pl-row .btn').first().click()
  await page.waitForSelector('.menu'); await shot('player-menu')
  if (!log.some((l) => l.path === '/Sessions/Playing')) fail(`${name}: playback start was not reported`)

  // Profiles
  await page.goto(APP + '#/settings')
  await page.evaluate(() => {
    const s = JSON.parse(localStorage.getItem('lumina.state.v1'))
    s.profiles.push({ id: 'k', name: 'Sara', color: 2, kids: true, servers: s.profiles[0].servers }, { id: 'o', name: 'Omar', color: 4, kids: false, servers: s.profiles[0].servers })
    localStorage.setItem('lumina.state.v1', JSON.stringify(s))
  })
  await page.goto(APP + '#/profiles'); await page.reload(); await page.waitForSelector('.who-tile'); await shot('profiles')

  if (name === 'tv') {
    // Remote control: arrow keys must move focus and Enter must open a title.
    await page.click('.who-tile'); await page.waitForSelector('.hero')
    await page.keyboard.press('ArrowDown'); await page.keyboard.press('ArrowDown'); await page.keyboard.press('ArrowRight')
    const focused = await page.evaluate(() => document.activeElement?.className || '')
    if (!focused.includes('card')) fail(`remote: expected a card to be focused, got "${focused}"`)
    await shot('remote-focus')
    await page.keyboard.press('Enter'); await page.waitForTimeout(800)
    if (!/#\/(item|play)\//.test(page.url())) fail(`remote: Enter did not open the focused title (${page.url()})`)
  }
  await ctx.close()
}

await run('tv', { width: 1920, height: 1080 }, 1)
await run('phone', { width: 390, height: 844 }, 2)
await browser.close()
preview.kill()
server.close()
console.log(process.exitCode ? 'CHECKS FAILED' : 'ALL CHECKS PASSED')
process.exit(process.exitCode || 0)

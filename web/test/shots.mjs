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
  // Open a title from the grid, then go back: the list must still be there, at the same scroll position.
  await page.goto(APP + '#/movies'); await page.waitForSelector('.grid .card')
  await page.mouse.wheel(0, 900); await page.waitForTimeout(500)
  const target = page.locator('.grid .card').nth(name === 'tv' ? 30 : 16)
  await target.scrollIntoViewIfNeeded(); await page.waitForTimeout(300)
  const y0 = await page.evaluate(() => window.scrollY)
  if (y0 < 200) fail(`${name}: test setup did not scroll the grid`)
  const tid = await target.getAttribute('data-item')
  await target.click()
  await page.waitForSelector('.d-poster img', { timeout: 3000 }).catch(() => fail(`${name}: details did not open with a poster`))
  if (!page.url().includes('/item/' + tid)) fail(`${name}: opened the wrong title`)
  await page.goBack(); await page.waitForTimeout(700)
  const cards = await page.locator('.grid .card').count()
  const y1 = await page.evaluate(() => window.scrollY)
  if (cards < 60) fail(`${name}: the grid was not kept after going back (${cards} cards)`)
  if (Math.abs(y1 - y0) > 40) fail(`${name}: scroll position was not restored (${y0} → ${y1})`)

  await page.goto(APP + '#/item/mv1'); await page.waitForSelector('.detail-body'); await shot('movie')
  await page.goto(APP + '#/item/sh1'); await page.waitForSelector('.episode'); await shot('show')
  await page.goto(APP + '#/search'); await page.fill('.input input', 'sintel'); await page.waitForSelector('.grid .card'); await shot('search')
  await page.goto(APP + '#/settings'); await page.waitForSelector('.group'); await shot('settings')
  // Profile editor is a native dialog: Esc closes it and must not leave the screen.
  await page.click('text=Add profile'); await page.waitForSelector('dialog[open]'); await shot('profile-editor')
  await page.keyboard.press('Escape'); await page.waitForTimeout(300)
  if (await page.locator('dialog[open]').count()) fail(`${name}: Esc did not close the dialog`)
  if (!page.url().endsWith('#/settings')) fail(`${name}: Esc left the Settings screen (${page.url()})`)

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
    // Keep going down: shelves rendered lazily must still be reachable with the remote.
    const seenShelves = new Set()
    for (let n = 0; n < 8; n++) {
      await page.keyboard.press('ArrowDown'); await page.waitForTimeout(150)
      seenShelves.add(await page.evaluate(() => document.activeElement?.closest('.shelf')?.querySelector('h2')?.textContent || ''))
    }
    for (const t of ['Recently Added TV Shows', 'Favorites', 'Top Rated Movies']) if (!seenShelves.has(t)) fail(`remote: could not reach the "${t}" row (${[...seenShelves].join(' | ')})`)
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

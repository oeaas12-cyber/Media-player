// Desktop shell (Windows, Linux): a window around the same web app.
const { app, BrowserWindow, shell } = require('electron')
const path = require('path')

function createWindow() {
  const win = new BrowserWindow({
    width: 1440,
    height: 860,
    minWidth: 420,
    minHeight: 560,
    backgroundColor: '#07080d',
    autoHideMenuBar: true,
    title: 'Lumina',
    webPreferences: { contextIsolation: true, nodeIntegration: false },
  })
  win.loadFile(path.join(__dirname, 'app', 'index.html'))
  win.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url)
    return { action: 'deny' }
  })
  win.webContents.on('before-input-event', (_e, input) => {
    if (input.type === 'keyDown' && input.key === 'F11') win.setFullScreen(!win.isFullScreen())
  })
}

app.whenReady().then(createWindow)
app.on('window-all-closed', () => app.quit())

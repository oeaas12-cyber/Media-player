import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'

/**
 * TV apps (LG webOS, Samsung Tizen) and desktop shells load the app from file://, where
 * browsers refuse <script type="module">. Ship one classic script instead.
 */
function classicScript(): Plugin {
  return {
    name: 'classic-script',
    enforce: 'post',
    transformIndexHtml(html) {
      return html
        .replace(/<script type="module" crossorigin/g, '<script defer')
        .replace(/<link rel="stylesheet" crossorigin/g, '<link rel="stylesheet"')
    },
  }
}

export default defineConfig({
  base: './',
  plugins: [react(), classicScript()],
  build: {
    target: ['es2017', 'chrome68', 'safari12'],
    cssTarget: ['chrome68', 'safari12'],
    modulePreload: false,
    rollupOptions: {
      output: { format: 'iife', inlineDynamicImports: true },
    },
  },
})

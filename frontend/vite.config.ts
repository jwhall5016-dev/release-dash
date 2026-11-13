import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  // Assets are shared. Team pages are mounted at /mentat and /foobar.
  base: '/',
  server: {
    port: 3000,
    proxy: {
      '/mentat/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/foobar/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/webhook': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})

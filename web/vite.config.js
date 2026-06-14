import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    vue(),
    VitePWA({
      registerType: 'autoUpdate',
      devOptions: {
        enabled: false // 开发环境不启用PWA
      },
      manifest: {
        name: '轻聊',
        short_name: '轻聊',
        description: '简洁安全的即时通讯应用',
        start_url: '/',
        display: 'standalone',
        background_color: '#ffffff',
        theme_color: '#ff7a33',
        lang: 'zh-CN',
        orientation: 'portrait-primary',
        prefer_related_applications: false,
        icons: [
          {
            src: '/icons/icon-192x192.png',
            sizes: '192x192',
            type: 'image/png',
            purpose: 'any maskable'
          },
          {
            src: '/icons/icon-512x512.png',
            sizes: '512x512',
            type: 'image/png'
          },
          {
            src: '/icons/apple-touch-icon.png',
            sizes: '180x180',
            type: 'image/png',
            purpose: 'any'
          }
        ]
      },
      workbox: {
        globPatterns: ['**/*.{html,js,css,ico,png,svg,woff,woff2}'],
        runtimeCaching: [
          {
            urlPattern: /^https:\/\/.*\.(png|jpg|jpeg|gif|svg|webp)$/i,
            handler: 'CacheFirst',
            options: {
              cacheName: 'images',
              expiration: {
                maxEntries: 100,
                maxAgeSeconds: 30 * 24 * 60 * 60 // 缓存30天
              }
            }
          },
          {
            urlPattern: /^https:\/\/chatlite\.xin\/api\/.*/i,
            handler: 'NetworkFirst',
            options: {
              cacheName: 'api',
              expiration: {
                maxEntries: 50,
                maxAgeSeconds: 5 * 60 // 缓存5分钟
              }
            }
          }
        ]
      }
    })
  ],
  server: {
    port: 5173
  }
})

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig(({ isSsrBuild }) => ({
  build: {
    ssrManifest: !isSsrBuild,
    rollupOptions: {
      output: isSsrBuild
        // SSR 产物要能被 Node ESM 直接 import：.mjs + 单入口
        ? { format: 'es', entryFileNames: '[name].mjs', chunkFileNames: 'chunks/[name]-[hash].mjs' }
        : {
            manualChunks: {
              'vendor': ['vue', 'vue-router'],
              'proto': ['protobufjs', 'long'],
              'ui': ['dompurify']
            }
          }
    },
    chunkSizeWarningLimit: 400
  },
  plugins: [
    vue(),
    VitePWA({
      registerType: 'autoUpdate',
      injectRegister: 'inline',
      devOptions: {
        enabled: false // 开发环境不启用PWA
      },
      manifest: {
        name: '轻聊',
        short_name: '轻聊',
        description: '简洁安全的即时通讯应用',
        start_url: '/',
        display: 'standalone',
        background_color: '#FAF7F2',
        theme_color: '#FAF7F2',
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
        // HTML is finalized by the SSR scripts after Vite writes its bundle.
        // Never precache an earlier shell or use the home document for other routes.
        globPatterns: ['**/*.{js,css,ico,png,svg,woff,woff2}'],
        navigateFallback: null,
        skipWaiting: true,
        clientsClaim: true,
        runtimeCaching: [
          {
            // Only static SSR documents, without account/code query parameters.
            // This callback is serialized into the worker: keep it self-contained.
            urlPattern: ({ request, url }) => request.mode === 'navigate'
              && url.origin === self.location.origin
              && url.search === ''
              && /^(?:\/|\/(?:app|features|faq)\/?)$/.test(url.pathname),
            handler: 'NetworkFirst',
            options: {
              cacheName: 'route-documents-v1',
              networkTimeoutSeconds: 2,
              cacheableResponse: { statuses: [200] },
              expiration: { maxEntries: 16, maxAgeSeconds: 7 * 24 * 60 * 60 }
            }
          },
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
            handler: 'NetworkOnly'
          }
        ]
      }
    })
  ],
  server: {
    port: 5173
  }
}))

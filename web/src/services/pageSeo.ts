import config from '../../site.routes.json'

export function applyPageSeo(path: string, locale: string, t: (key: string) => string): void {
  // SSR 渲染阶段没有 document；标题/描述由构建期的 build-route-heads 写死在各路由 HTML 里
  if (typeof document === 'undefined') return
  const normalizedPath = path === '/' ? path : path.replace(/\/+$/, '')
  const route = config.routes.find(route => route.path === normalizedPath)
  const key = route?.key ?? 'missing'
  const title = t(`seo.${key}.title`)
  const description = t(`seo.${key}.description`)
  const canonical = new URL(route?.path ?? '/', config.baseUrl).href
  document.title = title
  document.documentElement.lang = locale
  function meta(attribute: 'name' | 'property', name: string, content: string) {
    let element = document.head.querySelector<HTMLMetaElement>(`meta[${attribute}="${name}"]`)
    if (!element) {
      element = document.createElement('meta')
      element.setAttribute(attribute, name)
      document.head.append(element)
    }
    element.content = content
  }
  meta('name', 'description', description)
  meta('name', 'robots', route?.index ? 'index,follow,max-image-preview:large' : 'noindex,follow')
  meta('property', 'og:title', title)
  meta('property', 'og:description', description)
  meta('property', 'og:url', canonical)
  meta('property', 'og:locale', locale === 'zh-CN' ? 'zh_CN' : locale === 'ja' ? 'ja_JP' : 'en_US')
  meta('name', 'twitter:title', title)
  meta('name', 'twitter:description', description)
  document.head.querySelector('link[rel="canonical"]')?.setAttribute('href', canonical)
  document.head.querySelector('#site-schema')?.remove()
  if (normalizedPath === '/') {
    const script = document.createElement('script')
    script.id = 'site-schema'
    script.type = 'application/ld+json'
    script.textContent = JSON.stringify({ '@context': 'https://schema.org', '@type': 'WebSite',
      name: locale === 'zh-CN' ? '轻聊' : 'ChatLite', url: canonical, description, inLanguage: locale })
    document.head.append(script)
  }
}

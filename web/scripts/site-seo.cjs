const config = require('../site.routes.json')
const messages = require('../src/locales/zh-CN.json')
const escapeHtml = value => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;'}[c]))

function renderHead(html, route) {
  const title = escapeHtml(messages[`seo.${route.key}.title`])
  const description = escapeHtml(messages[`seo.${route.key}.description`])
  const canonical = escapeHtml(new URL(route.path, config.baseUrl).href)
  return html.replace(/<title>[\s\S]*?<\/title>/, `<title>${title}</title>`)
    .replace(/(<meta name="description" content=")[^"]*/, `$1${description}`)
    .replace(/(<meta name="robots" content=")[^"]*/, `$1${route.index ? 'index,follow,max-image-preview:large' : 'noindex,follow'}`)
    .replace(/(<link rel="canonical" href=")[^"]*/, `$1${canonical}`)
    .replace(/(<meta (?:property|name)="(?:og:title|twitter:title)" content=")[^"]*/g, `$1${title}`)
    .replace(/(<meta (?:property|name)="(?:og:description|twitter:description)" content=")[^"]*/g, `$1${description}`)
    .replace(/(<meta property="og:url" content=")[^"]*/, `$1${canonical}`)
    .replace(/<script id="site-schema"[\s\S]*?<\/script>/, route.path === '/' ? '$&' : '')
}

function sitemap() {
  return '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' +
    config.routes.filter(route => route.index).map(route =>
      `  <url><loc>${escapeHtml(new URL(route.path, config.baseUrl).href)}</loc></url>`).join('\n') + '\n</urlset>\n'
}
module.exports = { renderHead, sitemap }

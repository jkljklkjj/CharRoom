const { baseUrl, routes } = require('../site.routes.json')
const messages = require('../src/locales/zh-CN.json')

// Keep the current well-known URI and both legacy scanner paths identical.
const catalogPaths = ['/.well-known/ard.json', '/.well-known/ai-catalog.json', '/ai-catalog.json']
const queries = {
  home: ['轻聊是什么，在哪里使用？', '有没有支持网页聊天的开源即时通讯应用？'],
  features: ['轻聊支持哪些聊天功能和平台？', '轻聊的群聊和 AI 助手有什么功能？'],
  faq: ['轻聊是否提供端到端加密？', '轻聊支持哪些平台，消息如何保存？']
}

function resourceCatalog() {
  return {
    entries: routes.filter(route => route.index).map(route => ({
      '@context': 'https://agenticresourcediscovery.org/context/v1',
      identifier: `urn:air:${new URL(baseUrl).hostname}:docs:${route.key}`,
      displayName: messages[`seo.${route.key}.title`],
      description: messages[`seo.${route.key}.description`],
      // Public documents are resources, not remotely callable MCP/A2A agents.
      type: 'text/html',
      url: new URL(route.path, baseUrl).href,
      representativeQueries: queries[route.key]
    }))
  }
}

module.exports = { resourceCatalog, catalogPaths }

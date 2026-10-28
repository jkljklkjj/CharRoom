const test = require('node:test'), assert = require('node:assert/strict'), path = require('node:path')
const { loadTs } = require('./helpers.cjs')
test('owner discovery resolves one endpoint and rejects missing multi-node configuration', async () => {
  let data = { ownerGatewayId: 'B', ownerEndpoint: 'gateway-b.test:8080', gateways: [{ gatewayId: 'A' }, { gatewayId: 'B' }] }
  const { exports: owner } = await loadTs(path.resolve(__dirname, '../src/services/gatewayOwner.ts'), {}, {
    '../api/http': { API_BASE: 'http://fixture.test/api', safeFetch: async () => ({ ok: true, body: { code: 0, data } }) }
  })
  const endpoint = await owner.discoverGatewayOwner('fixture', 'fallback.test', 8080)
  assert.equal(endpoint.host, 'gateway-b.test')
  assert.equal(owner.parseGatewayEndpoint('https://gateway-b.test:443').port, 443)
  assert.throws(() => owner.parseGatewayEndpoint('https://user:pass@gateway-b.test:8080'))
  data.ownerEndpoint = ''
  await assert.rejects(owner.discoverGatewayOwner('fixture', 'fallback.test', 8080))
})

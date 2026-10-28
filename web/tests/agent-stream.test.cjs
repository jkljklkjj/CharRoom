const test = require('node:test'), assert = require('node:assert/strict'), path = require('node:path')
const { loadTs } = require('./helpers.cjs')
test('agent deltas accumulate, duplicate packets do not, and DONE retains complete text', async () => {
  const { exports: { AgentStreamReducer } } = await loadTs(path.resolve(__dirname, '../src/services/agentStreamReducer.ts'))
  const reducer = new AgentStreamReducer(), chunk = { requestId: '1', type: 0, seq: 1, text: 'same' }
  assert.equal(reducer.accept(chunk).text, 'same')
  assert.equal(reducer.accept(chunk), null)
  assert.equal(reducer.accept({ ...chunk, seq: 2 }).text, 'samesame')
  assert.equal(reducer.accept({ requestId: '1', type: 4, seq: 3 }).text, 'samesame')
  assert.equal(reducer.accept({ requestId: '1', type: 4, seq: 3 }).text, 'samesame', 'terminal replay can retry a failed durable write')
  assert.equal(reducer.accept({ ...chunk, seq: 4 }), null)
  reducer.clear()
  assert.equal(reducer.accept(chunk).text, 'same')
})

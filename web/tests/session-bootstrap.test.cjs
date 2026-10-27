const test = require('node:test'), assert = require('node:assert/strict'), path = require('node:path')
const { loadTs } = require('./helpers.cjs')

test('slow contacts and failed group requests cannot delay connection or history', async () => {
  const { exports: { bootstrapSession } } = await loadTs(path.resolve(__dirname, '../src/services/sessionBootstrap.ts'))
  const events = []; let release
  const wait = new Promise(resolve => { release = resolve })
  const pending = bootstrapSession(() => events.push('connected'), {
    contacts: async () => { await wait; events.push('contacts') },
    groups: async () => { throw new Error('unavailable') },
    history: async () => events.push('history')
  }, () => true, name => events.push('failed:' + name))
  await new Promise(resolve => setImmediate(resolve))
  assert.deepEqual(events, ['connected', 'history', 'failed:groups'])
  release(); await pending
  assert.equal(events.at(-1), 'contacts')
})

test('session renewal aborts old tasks even when logging back into the same account', async () => {
  const { exports: { SessionEpoch } } = await loadTs(path.resolve(__dirname, '../src/services/sessionEpoch.ts'))
  const epoch = new SessionEpoch(), old = epoch.capture()
  epoch.renew()
  assert.equal(old.signal.aborted, true)
  assert.equal(old.isCurrent(), false)
  assert.equal(epoch.capture().isCurrent(), true)
})

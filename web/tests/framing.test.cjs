const test = require('node:test'), assert = require('node:assert/strict'), path = require('node:path')
const { loadTs } = require('./helpers.cjs')
test('stream decoder handles every split and merged frames and rejects illegal lengths', async () => {
  const { exports: { FrameDecoder, frame } } = await loadTs(path.resolve(__dirname, '../src/services/transport/framing.ts'))
  const a = new Uint8Array([1, 2, 3]), b = new Uint8Array([4, 5])
  const bytes = new Uint8Array([...new Uint8Array(frame(a)), ...new Uint8Array(frame(b))])
  for (let split = 1; split < bytes.length; split++) {
    const decoder = new FrameDecoder()
    const result = [...decoder.push(bytes.slice(0, split)), ...decoder.push(bytes.slice(split))]
    assert.deepEqual(result.map(buffer => Array.from(new Uint8Array(buffer))), [[1, 2, 3], [4, 5]])
    decoder.finish()
  }
  assert.throws(() => new FrameDecoder().push(new Uint8Array([0, 1, 0, 1])), /length/)
  const truncated = new FrameDecoder(); truncated.push(bytes.slice(0, 5))
  assert.throws(() => truncated.finish(), /truncated/)
})

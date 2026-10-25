const { test } = require('node:test')
const assert = require('node:assert/strict')
const path = require('node:path')
const { loadTs } = require('./helpers.cjs')

// 联系人列表第二行展示最新消息，预览文本在存储与渲染共用同一处理
test('conversationPreviewText flattens whitespace, trims and truncates', async () => {
  const { exports } = await loadTs(path.resolve(__dirname, '../src/utils/format.ts'))
  const preview = exports.conversationPreviewText
  assert.equal(preview({ text: '  你好\n\n世界  ' }), '你好 世界')
  assert.equal(preview({ text: '' }), '')
  assert.equal(preview({ text: null }), '')
  assert.equal(preview(null), '')
  assert.equal(preview(undefined), '')
  const long = '长'.repeat(70)
  assert.equal(preview({ text: long }), '长'.repeat(exports.PREVIEW_MAX_LENGTH) + '…')
  assert.equal(preview({ text: long }, 4), '长'.repeat(4) + '…')
})

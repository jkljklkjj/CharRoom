const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const sidebar = fs.readFileSync(path.resolve(__dirname, '../src/components/SidebarUsers.vue'), 'utf8')

// 联系人面板的加载态不出文字：用与真实联系人行同尺寸的骨架占位，
// 否则加载前后高度/内容不一致，界面会跳。
test('contacts panel renders skeleton rows instead of loading text', () => {
  assert.ok(!sidebar.includes("{{ $t('social.loading') }}"), '可见内容中不应出现加载提示文字')
  assert.ok(sidebar.includes('class="users-list skeleton-list"'), '缺少骨架容器')
  assert.ok(sidebar.includes(':aria-label="$t(\'social.loading\')"'), '骨架应保留 aria-label 供读屏使用')
  assert.match(sidebar, /<li v-for="n in \d+" :key="n" class="user-row skeleton-row" aria-hidden="true">/)
})

test('skeleton reuses the real row metrics so the list does not shift', () => {
  // 骨架行必须复用 .user-row（相同 padding/margin/min-height），并挂上 pointer-events:none
  assert.match(sidebar, /\.users-list \.user-row\{[^}]*min-height:44px\}/)
  assert.match(sidebar, /\.skeleton-row\{[^}]*pointer-events:none\}/)
  // 骨架块用主题表面色，深浅色都要跟随变量
  assert.match(sidebar, /\.skeleton-avatar,\.skeleton-line\{[^}]*background:var\(--surface-2/)
})

test('skeleton only shows while there is nothing to render', () => {
  // 已有联系人时后台刷新不能把列表替换成骨架
  assert.ok(sidebar.includes('v-if="friendsLoading && !visibleCount"'))
  assert.ok(sidebar.includes('v-else-if="friendsError && !visibleCount"'))
})

<template>
  <div class="quota-overlay" @click.self="$emit('close')">
    <div class="quota-dialog">
      <div class="quota-header">
        <h3>{{ $t('quota.title') }}</h3>
        <button class="close-btn" @click="close">&times;</button>
      </div>

      <div class="quota-body" v-if="!loading">
        <!-- 余额 -->
        <div class="balance-section">
          <div class="balance-label">{{ $t('quota.balance') }}</div>
          <div class="balance-value">{{ quota?.balanceFen ? (quota.balanceFen / 100).toFixed(2) : '0.00' }}<span class="unit">元</span></div>
        </div>

        <!-- 每周免费配额 -->
        <div class="quota-section">
          <h4>{{ $t('quota.weekly') }}</h4>
          <div class="quota-row">
            <span class="label">{{ $t('quota.inputTokens') }}</span>
            <div class="bar-wrap"><div class="bar" :style="{ width: pct(quota?.weeklyInputPct) + '%' }"></div></div>
            <span class="num">{{ fmt(quota?.weeklyInputUsed) }} / {{ fmt(quota?.weeklyFreeInputLimit) }}</span>
          </div>
          <div class="quota-row">
            <span class="label">{{ $t('quota.outputTokens') }}</span>
            <div class="bar-wrap"><div class="bar out" :style="{ width: pct(quota?.weeklyOutputPct) + '%' }"></div></div>
            <span class="num">{{ fmt(quota?.weeklyOutputUsed) }} / {{ fmt(quota?.weeklyFreeOutputLimit) }}</span>
          </div>
        </div>

        <!-- 购买 -->
        <div class="purchase-section" v-if="!qrCodeUrl">
          <h4>{{ $t('quota.purchaseTitle') }}</h4>
          <div class="price-info">
            <span>输入 1.2元/百万 &middot; 输出 2.5元/百万</span>
          </div>
          <div class="amount-presets">
            <button v-for="a in presets" :key="a" :class="{ active: purchaseAmount === a }"
              @click="purchaseAmount = a">{{ a }}元</button>
          </div>
          <div class="custom-amount">
            <input v-model.number="purchaseAmount" type="number" min="1" placeholder="自定义金额" />
          </div>
          <div class="purchase-hint" v-if="purchaseAmount > 0">
            约可消耗 输入{{ (purchaseAmount / 1.2 * 100).toFixed(0) }}万 / 输出{{ (purchaseAmount / 2.5 * 100).toFixed(0) }}万
          </div>
          <button class="buy-btn" @click="doPurchase" :disabled="purchasing || purchaseAmount <= 0">
            {{ purchasing ? $t('quota.processing') : $t('quota.buy') }}
          </button>
        </div>

        <!-- 支付二维码 -->
        <div class="qr-section" v-if="qrCodeUrl">
          <h4>{{ $t('quota.scanPay') }}</h4>
          <img :src="'https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=' + encodeURIComponent(qrCodeUrl)" />
          <p class="qr-hint">{{ $t('quota.scanHint') }}</p>
          <button class="check-btn" @click="checkPayment">{{ $t('quota.checkPayment') }}</button>
        </div>

        <div v-if="err" class="err-msg">{{ err }}</div>
      </div>
      <div v-else class="quota-loading">{{ $t('quota.loading') }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '../api'

const emit = defineEmits(['close'])

const loading = ref(true)
const err = ref('')
const quota = ref(null)
const purchaseAmount = ref(10)
const purchasing = ref(false)
const qrCodeUrl = ref('')
const currentPurchaseId = ref(null)

const presets = [10, 20, 50, 100, 200]

onMounted(async () => {
  try {
    quota.value = await api.getTokenQuota()
  } catch (e) {
    err.value = '加载失败'
  }
  loading.value = false
})

function pct(v) { return Math.min(v || 0, 100) }
function fmt(n) {
  if (n == null) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return String(n)
}
function close() {
  if (!purchasing.value) emit('close')
}

async function doPurchase() {
  if (!purchaseAmount.value || purchaseAmount.value <= 0) return
  purchasing.value = true
  err.value = ''
  try {
    const amountFen = Math.round(purchaseAmount.value * 100)
    const result = await api.purchaseTokens(amountFen)
    if (result && result.codeUrl) {
      currentPurchaseId.value = result.purchaseId
      qrCodeUrl.value = result.codeUrl
    } else {
      // mock / 直接完成
      await checkPayment()
    }
  } catch (e) {
    err.value = '支付创建失败'
  }
  purchasing.value = false
}

async function checkPayment() {
  if (!currentPurchaseId.value) return
  try {
    const url = `/api/agent/quota/purchase/confirm?purchaseId=${currentPurchaseId.value}`
    const res = await fetch(url, { method: 'POST', credentials: 'include' })
    if (res.ok) {
      qrCodeUrl.value = ''
      currentPurchaseId.value = null
      purchaseAmount.value = 0
      quota.value = await api.getTokenQuota()
      alert('充值成功！')
    }
  } catch (e) {
    err.value = '查询失败'
  }
}
</script>

<style scoped>
.quota-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.4); z-index: 1000;
  display: flex; align-items: center; justify-content: center;
}
.quota-dialog {
  background: var(--bg, #fff); border-radius: 14px; width: 420px; max-width: 90vw;
  max-height: 85vh; overflow-y: auto; box-shadow: 0 8px 32px rgba(0,0,0,0.12);
}
.quota-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 18px 22px; border-bottom: 1px solid var(--border, #eee);
}
.quota-header h3 { margin: 0; font-size: 16px; }
.close-btn { background: none; border: 0; font-size: 24px; cursor: pointer; color: var(--muted, #999); }
.quota-body { padding: 18px 22px; }
.quota-loading { padding: 40px; text-align: center; color: var(--muted, #999); }

.balance-section { text-align: center; padding: 16px 0; margin-bottom: 12px; }
.balance-label { font-size: 13px; color: var(--muted, #999); margin-bottom: 4px; }
.balance-value { font-size: 32px; font-weight: 700; color: var(--accent-1, #4f6ef7); }
.balance-value .unit { font-size: 16px; font-weight: 400; color: var(--muted, #999); margin-left: 4px; }

.quota-section { margin-bottom: 16px; }
.quota-section h4 { margin: 0 0 10px; font-size: 14px; }
.quota-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; font-size: 13px; }
.quota-row .label { min-width: 44px; color: var(--muted, #999); }
.quota-row .num { white-space: nowrap; min-width: 90px; text-align: right; color: var(--muted, #999); font-size: 12px; }
.bar-wrap { flex: 1; height: 8px; background: var(--border, #eee); border-radius: 4px; overflow: hidden; }
.bar { height: 100%; background: var(--accent-1, #4f6ef7); border-radius: 4px; transition: width .3s; }
.bar.out { background: #e67e22; }

.purchase-section { border-top: 1px solid var(--border, #eee); padding-top: 16px; }
.purchase-section h4 { margin: 0 0 6px; font-size: 14px; }
.price-info { font-size: 12px; color: var(--muted, #999); margin-bottom: 12px; }
.amount-presets { display: flex; gap: 8px; margin-bottom: 10px; }
.amount-presets button {
  flex: 1; padding: 8px 0; border-radius: 8px; border: 1px solid var(--border, #ddd);
  background: transparent; cursor: pointer; font-size: 14px; color: var(--text, #333);
}
.amount-presets button.active { border-color: var(--accent-1, #4f6ef7); background: var(--accent-1, #4f6ef7); color: #fff; }
.custom-amount input {
  width: 100%; padding: 10px 12px; border-radius: 8px; border: 1px solid var(--border, #ddd);
  font-size: 14px; outline: none; box-sizing: border-box; margin-bottom: 8px;
}
.custom-amount input:focus { border-color: var(--accent-1, #4f6ef7); }
.purchase-hint { font-size: 11px; color: var(--muted, #999); margin-bottom: 12px; }
.buy-btn {
  width: 100%; padding: 12px; border-radius: 8px; border: 0;
  background: linear-gradient(135deg, var(--accent-1, #4f6ef7), var(--accent-2, #3b5de7));
  color: #fff; font-size: 15px; font-weight: 600; cursor: pointer;
}
.buy-btn:disabled { opacity: .5; cursor: not-allowed; }

.qr-section { text-align: center; padding: 16px 0; }
.qr-section h4 { margin: 0 0 12px; }
.qr-section img { width: 200px; height: 200px; border-radius: 8px; }
.qr-hint { margin: 10px 0; font-size: 13px; color: var(--muted, #999); }
.check-btn {
  padding: 10px 24px; border-radius: 8px; border: 1px solid var(--border, #ddd);
  background: transparent; cursor: pointer; font-size: 14px;
}
.err-msg { color: #e74c3c; font-size: 13px; margin-top: 10px; text-align: center; }
</style>

<template>
  <div class="quota-overlay" @click.self="close">
    <div class="quota-dialog">
      <button class="close-btn" @click="close">✕</button>

      <div class="quota-body" v-if="!loading">
        <!-- 余额卡片 -->
        <div class="balance-card">
          <div class="balance-glow"></div>
          <div class="balance-label">{{ $t('quota.balance') }}</div>
          <div class="balance-value">
            <span class="amount">{{ ((quota?.balanceFen || 0) / 100).toFixed(2) }}</span>
            <span class="unit">元</span>
          </div>
          <div class="balance-sub">
            免费剩余 {{ ((quota?.freeRemainingFen || 0) / 100).toFixed(2) }} 元
          </div>
        </div>

        <!-- 本周免费额度 -->
        <div class="weekly-section">
          <div class="section-header">
            <span class="section-icon">🎁</span>
            <span>{{ $t('quota.weekly') }}</span>
          </div>
          <div class="free-bar-wrap">
            <div class="free-bar" :style="{ width: freePct + '%' }"></div>
          </div>
          <div class="free-stats">
            <span class="stat">{{ $t('quota.freeRemaining') }} {{ ((quota?.freeRemainingFen || 0) / 100).toFixed(2) }} 元</span>
            <span class="stat">本周已用 {{ ((quota?.weeklyCostFen || 0) / 100).toFixed(2) }} 元</span>
          </div>
        </div>

        <!-- 购买 -->
        <div class="purchase-section" v-if="!qrCodeUrl">
          <div class="section-header">
            <span class="section-icon">⚡</span>
            <span>充值</span>
          </div>
          <div class="amount-grid">
            <button v-for="a in presets" :key="a" :class="{ active: purchaseAmount === a }"
              @click="purchaseAmount = a">
              <span class="amount-num">{{ a }}</span>
              <span class="amount-unit">元</span>
            </button>
          </div>
          <div class="custom-row">
            <span class="custom-prefix">¥</span>
            <input v-model.number="purchaseAmount" type="number" min="1" placeholder="自定义金额" />
          </div>
          <button class="buy-btn" @click="doPurchase" :disabled="purchasing || purchaseAmount <= 0">
            <span v-if="purchasing" class="btn-loading"></span>
            <span v-else>立即充值</span>
          </button>
        </div>

        <!-- 支付二维码 -->
        <div class="qr-section" v-if="qrCodeUrl">
          <div class="qr-card">
            <div class="qr-icon">📱</div>
            <p class="qr-title">微信支付</p>
            <div class="qr-img-wrap">
              <img :src="'https://api.qrserver.com/v1/create-qr-code/?size=280x280&data=' + encodeURIComponent(qrCodeUrl)" />
            </div>
            <p class="qr-hint">请使用微信扫描二维码支付</p>
            <button class="check-btn" @click="checkPayment">已完成支付</button>
          </div>
        </div>

        <div v-if="err" class="err-msg">{{ err }}</div>
      </div>
      <div v-else class="quota-loading">
        <div class="loading-spinner"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
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

const freePct = computed(() => {
  if (!quota.value) return 0
  return Math.min((quota.value.freeRemainingFen || 0) / 400 * 100, 100)
})

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
  position: fixed; inset: 0; z-index: 1000;
  background: rgba(0,0,0,0.5);
  backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center;
  animation: fadeIn .2s ease;
}
.quota-dialog {
  background: #f8f9fc;
  border-radius: 20px;
  width: 380px; max-width: 88vw;
  max-height: 90vh; overflow-y: auto;
  box-shadow: 0 25px 60px rgba(0,0,0,0.18);
  position: relative;
  animation: slideUp .3s cubic-bezier(.16,1,.3,1);
}
.close-btn {
  position: absolute; top: 14px; right: 14px; z-index: 2;
  width: 32px; height: 32px; border-radius: 50%;
  border: none; background: rgba(0,0,0,0.06);
  font-size: 15px; cursor: pointer; color: #666;
  display: flex; align-items: center; justify-content: center;
  transition: all .15s;
}
.close-btn:hover { background: rgba(0,0,0,0.12); color: #222; }
.quota-body { padding: 28px 24px 24px; }
.quota-loading { padding: 60px; text-align: center; }
.loading-spinner {
  width: 30px; height: 30px; border: 3px solid #e8e8e8;
  border-top-color: #ff7a33; border-radius: 50%;
  animation: spin .6s linear infinite; margin: 0 auto;
}

/* 余额卡片 */
.balance-card {
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  border-radius: 16px; padding: 32px 24px 24px;
  text-align: center; position: relative; overflow: hidden;
  margin-bottom: 16px;
}
.balance-glow {
  position: absolute; top: -60%; right: -30%;
  width: 200px; height: 200px;
  background: radial-gradient(circle, rgba(255,122,51,.2) 0%, transparent 70%);
  pointer-events: none;
}
.balance-label { font-size: 13px; color: rgba(255,255,255,.5); letter-spacing: .5px; margin-bottom: 6px; }
.balance-value { display: flex; align-items: baseline; justify-content: center; gap: 4px; }
.balance-value .amount {
  font-size: 44px; font-weight: 800; color: #fff;
  letter-spacing: -2px; line-height: 1.1;
}
.balance-value .unit { font-size: 16px; color: rgba(255,255,255,.5); font-weight: 500; }
.balance-sub { margin-top: 10px; font-size: 12px; color: rgba(255,255,255,.4); }

/* 周免费 */
.section-header {
  display: flex; align-items: center; gap: 8px;
  font-size: 14px; font-weight: 600; color: #222;
  margin-bottom: 12px;
}
.section-icon { font-size: 16px; }
.weekly-section {
  background: #fff; border-radius: 14px; padding: 18px;
  margin-bottom: 14px; box-shadow: 0 1px 3px rgba(0,0,0,.04);
}
.free-bar-wrap {
  height: 8px; background: #f0f0f2; border-radius: 4px;
  overflow: hidden; margin-bottom: 10px;
}
.free-bar {
  height: 100%;
  background: linear-gradient(90deg, #ff7a33, #ffb347);
  border-radius: 4px; transition: width .4s ease;
}
.free-stats { display: flex; justify-content: space-between; font-size: 12px; color: #888; }

/* 购买 */
.purchase-section {
  background: #fff; border-radius: 14px; padding: 18px;
  box-shadow: 0 1px 3px rgba(0,0,0,.04);
}
.amount-grid {
  display: grid; grid-template-columns: repeat(5, 1fr); gap: 6px;
  margin-bottom: 10px;
}
.amount-grid button {
  display: flex; flex-direction: column; align-items: center;
  padding: 10px 4px; border-radius: 10px;
  border: 1.5px solid #eee; background: #fafafa;
  cursor: pointer; transition: all .15s;
}
.amount-grid button:hover { border-color: #ffd5b3; background: #fff8f3; }
.amount-grid button.active {
  border-color: #ff7a33; background: #fff8f3;
  box-shadow: 0 0 0 3px rgba(255,122,51,.12);
}
.amount-num { font-size: 16px; font-weight: 700; color: #222; }
.amount-unit { font-size: 11px; color: #999; margin-top: 1px; }
.amount-grid button.active .amount-num { color: #e05d1a; }

.custom-row {
  display: flex; align-items: center;
  border: 1.5px solid #eee; border-radius: 10px; padding: 0 12px;
  margin-bottom: 14px; transition: border-color .15s; background: #fafafa;
}
.custom-row:focus-within { border-color: #ff7a33; background: #fff; }
.custom-prefix { font-size: 15px; font-weight: 600; color: #999; margin-right: 4px; }
.custom-row input {
  flex: 1; border: none; background: transparent;
  padding: 9px 0; font-size: 14px; outline: none; color: #333;
}
.custom-row input::-webkit-inner-spin-button { display: none; }

.buy-btn {
  width: 100%; padding: 13px; border-radius: 12px; border: none;
  background: linear-gradient(135deg, #ff7a33, #e8601a);
  color: #fff; font-size: 15px; font-weight: 600; cursor: pointer;
  transition: all .15s;
}
.buy-btn:hover { transform: translateY(-1px); box-shadow: 0 6px 16px rgba(255,122,51,.3); }
.buy-btn:active { transform: scale(.97); }
.buy-btn:disabled { opacity: .5; cursor: not-allowed; transform: none; box-shadow: none; }
.btn-loading {
  display: inline-block; width: 18px; height: 18px;
  border: 2px solid rgba(255,255,255,.3); border-top-color: #fff;
  border-radius: 50%; animation: spin .6s linear infinite;
}

/* 二维码 */
.qr-section { margin-top: 4px; }
.qr-card {
  background: #fff; border-radius: 14px; padding: 24px;
  text-align: center; box-shadow: 0 1px 3px rgba(0,0,0,.04);
}
.qr-icon { font-size: 32px; margin-bottom: 8px; }
.qr-title { font-size: 15px; font-weight: 600; color: #222; margin: 0 0 16px; }
.qr-img-wrap {
  display: inline-block; padding: 8px; background: #fff; border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0,0,0,.08); margin-bottom: 12px;
}
.qr-img-wrap img { display: block; width: 200px; height: 200px; border-radius: 6px; }
.qr-hint { font-size: 13px; color: #999; margin: 0 0 16px; }
.check-btn {
  padding: 10px 28px; border-radius: 10px; border: 1.5px solid #eee;
  background: #fafafa; cursor: pointer; font-size: 14px;
  font-weight: 500; color: #555; transition: all .15s;
}
.check-btn:hover { border-color: #ff7a33; color: #ff7a33; background: #fff8f3; }

.err-msg { color: #e74c3c; font-size: 13px; margin-top: 12px; text-align: center; }

@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes slideUp { from { opacity: 0; transform: translateY(16px) scale(.97); } to { opacity: 1; transform: translateY(0) scale(1); } }
@keyframes spin { to { transform: rotate(360deg); } }
</style>

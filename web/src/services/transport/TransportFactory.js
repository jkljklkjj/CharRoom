/**
 * TransportFactory — 传输层工厂。
 *
 * 根据浏览器能力和配置自动选择合适的传输实现：
 * 1. WebTransport (QUIC + HTTP/3) — 首选，延迟更低
 * 2. WebSocket (WSS) — 降级方案，兼容所有浏览器
 *
 * 特性检测顺序：
 *   - WebTransport API 是否可用
 *   - 配置是否允许 WebTransport
 *   - 降级到 WebSocket
 */
import { WebSocketTransport } from './WebSocketTransport'
import { WebTransportTransport } from './WebTransportTransport'

// 全局配置
let _config = {
  preferWebTransport: true,    // 是否优先使用 WebTransport
  webTransportUrl: null,       // 自定义 WebTransport URL（覆盖自动生成）
  wsUrl: null,                 // 自定义 WebSocket URL（覆盖自动生成）
}

/**
 * 更新工厂全局配置。
 * @param {Object} cfg - 配置项
 */
export function configureTransport(cfg) {
  Object.assign(_config, cfg)
}

/**
 * 检测浏览器是否支持 WebTransport。
 * @returns {boolean}
 */
export function isWebTransportSupported() {
  return typeof WebTransport !== 'undefined'
}

/**
 * 创建最佳传输实例。
 *
 * @param {Object} [options]
 * @param {boolean} [options.forceWebSocket=false] - 强制使用 WebSocket
 * @param {string} [options.host] - 服务器主机
 * @param {number} [options.port] - 端口
 * @returns {ChatTransport}
 */
export function createTransport(options = {}) {
  const { forceWebSocket = false } = options

  if (!forceWebSocket && _config.preferWebTransport && isWebTransportSupported()) {
    console.log('🔀 TransportFactory: 使用 WebTransport (QUIC)')
    return new WebTransportTransport()
  }

  console.log('🔀 TransportFactory: 使用 WebSocket (WSS)')
  return new WebSocketTransport()
}

/**
 * 生成 WebTransport URL。
 * @param {string} host
 * @param {number|string} port
 * @returns {string}
 */
export function buildWebTransportUrl(host, port) {
  return `https://${host}:${port}/.well-known/webtransport`
}

/**
 * 生成 WebSocket URL。
 * @param {string} host
 * @param {number|string} port
 * @param {string} [path='/ws']
 * @returns {string}
 */
export function buildWebSocketUrl(host, port, path = '/ws') {
  return `wss://${host}:${port}${path}`
}

export default { createTransport, configureTransport, isWebTransportSupported }

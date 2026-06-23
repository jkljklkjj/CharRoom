/**
 * TransportFactory — 传输层工厂。
 *
 * 使用 WebTransport (QUIC + HTTP/3) 作为唯一传输层。
 * WebTransport API 不可用时抛出异常。
 */
import { WebTransportTransport } from './WebTransportTransport'

let _config = {
  webTransportUrl: null,
}

export function configureTransport(cfg) {
  Object.assign(_config, cfg)
}

export function isWebTransportSupported() {
  return typeof WebTransport !== 'undefined'
}

export function createTransport() {
  if (!isWebTransportSupported()) {
    throw new Error('当前浏览器不支持 WebTransport (QUIC)')
  }
  console.log('🔀 TransportFactory: 使用 WebTransport (QUIC)')
  return new WebTransportTransport()
}

export function buildWebTransportUrl(host, port, token) {
  let url = `https://${host}:${port}/.well-known/webtransport`
  if (token) url += `?token=${encodeURIComponent(token)}`
  return url
}

export default { createTransport, configureTransport, isWebTransportSupported }

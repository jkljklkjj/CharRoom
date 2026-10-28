/**
 * TransportFactory — 传输层工厂。
 *
 * 使用 WebTransport (QUIC + HTTP/3) 作为唯一传输层。
 * WebTransport API 不可用时抛出异常。
 */
import { WebTransport } from './WebTransport'

let _config = {
  webTransportUrl: null as string | null,
}

export function configureTransport(cfg: Partial<typeof _config>) {
  Object.assign(_config, cfg)
}

export function isWebTransportSupported() {
  return typeof globalThis.WebTransport !== 'undefined'
}

export function createTransport() {
  if (!isWebTransportSupported()) {
    throw new Error('当前浏览器不支持 WebTransport (QUIC)')
  }
  console.debug('🔀 TransportFactory: 使用 WebTransport (QUIC)')
  return new WebTransport()
}

export function buildWebTransportUrl(host: string, port: number) {
  return `https://${host}:${port}/.well-known/webtransport${import.meta.env.VITE_WT_FRAMING === 'raw' ? '' : '?framing=length-v1'}`
}

export default { createTransport, configureTransport, isWebTransportSupported }

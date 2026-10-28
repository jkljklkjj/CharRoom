import { API_BASE, safeFetch } from '../api/http'
export interface GatewayOwner { ownerGatewayId?: string | null; ownerEndpoint?: string | null; gateways: { gatewayId: string; endpoint: string }[] }
export function parseGatewayEndpoint(endpoint: string): { host: string; port: number } {
  const url = new URL(endpoint.includes('://') ? endpoint : `quic://${endpoint}`)
  const port = Number(url.port || (url.protocol === 'https:' ? 443 : 0))
  if (!['quic:', 'https:'].includes(url.protocol) || !url.hostname || port < 1 || port > 65535 ||
      url.username || url.password || url.search || url.hash || !['', '/'].includes(url.pathname)) throw new Error('Invalid gateway endpoint')
  return { host: url.hostname, port }
}
export async function discoverGatewayOwner(token: string, host: string, port: number) {
  const result = await safeFetch<GatewayOwner>(`${API_BASE}/cluster/owner`, {
    headers: { Authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(10000)
  })
  const data = result.body?.data
  if (!result.ok || result.body?.code !== 0 || !data || !Array.isArray(data.gateways)) throw new Error('Gateway owner discovery failed')
  if (data.ownerEndpoint) {
    // 旧网关未配置 endpoint 时曾把 gatewayId 写进成员记录，客户端不应因此整条连接失败：
    // 单网关时回退到内置地址，多网关时无法安全猜测 owner 地址，仍然抛错。
    try {
      return parseGatewayEndpoint(data.ownerEndpoint)
    } catch (error) {
      if (data.gateways.length > 1) throw error
    }
  }
  if (data.gateways.length > 1) throw new Error('Owner gateway endpoint is not configured')
  return { host, port }
}

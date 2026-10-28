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
  if (data.ownerEndpoint) return parseGatewayEndpoint(data.ownerEndpoint)
  if (data.gateways.length > 1) throw new Error('Owner gateway endpoint is not configured')
  return { host, port }
}

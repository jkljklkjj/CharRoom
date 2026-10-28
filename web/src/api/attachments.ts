import { API_BASE, safeFetch } from './http'

export class AttachmentUploadError extends Error {
  constructor(public readonly code: number, message: string) { super(message) }
}

export async function uploadAttachment(file: File, signal: AbortSignal): Promise<string> {
  const form = new FormData(); form.append('file', file)
  const result = await safeFetch<string>(`${API_BASE}/files`, { method: 'POST', body: form, signal })
  if (!result.ok || result.body?.code !== 0 || !result.body.data)
    throw new AttachmentUploadError(result.body?.code ?? 1500, 'Attachment upload failed')
  const url = new URL(result.body.data)
  if (!['http:', 'https:'].includes(url.protocol)) throw new Error('Invalid attachment URL')
  return url.href
}

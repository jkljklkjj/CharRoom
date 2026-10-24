export type StreamType = 'control' | 'chat'
export interface SendOptions { streamType?: StreamType; conversationId?: string }
export type BinaryData = ArrayBuffer | Uint8Array<ArrayBuffer>

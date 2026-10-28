// Generated from src/proto/*.proto. Run npm run generate:protocol; do not edit by hand.

export type WireLong = string | number

export interface LoginMessage {
  token?: string
  deviceType?: 0 | 1 | 2 | 3 | 4
  deviceId?: string
}

export interface ChatMessage {
  targetClientId?: WireLong
  content?: string
  userId?: WireLong
  timestamp?: WireLong
  messageId?: string
  clientActions?: (ClientAction)[]
  replyToMessageId?: string
  replyToContent?: string
  replyToSender?: string
  messageType?: 0 | 1 | 2
  fileUrl?: string
  fileName?: string
  fileSize?: WireLong
  seqId?: WireLong
  conversationId?: string
}

export interface GroupChatMessage {
  targetClientId?: WireLong
  content?: string
  userId?: WireLong
  timestamp?: WireLong
  messageId?: string
  replyToMessageId?: string
  replyToContent?: string
  replyToSender?: string
  messageType?: 0 | 1 | 2
  fileUrl?: string
  fileName?: string
  fileSize?: WireLong
  seqId?: WireLong
  conversationId?: string
}

export interface ClientAction {
  id?: string
  timestamp?: WireLong
  type?: string
  targetId?: string
  metadata?: Record<string, string>
}

export interface AgentChatMessage {
  targetClientId?: string
  content?: string
  userId?: WireLong
  timestamp?: WireLong
  clientActions?: (ClientAction)[]
}

export interface CheckMessage {
  conversationType?: 0 | 1 | 2 | 3
  targetClientId?: WireLong
}

export interface HeartbeatMessage {
  timestamp?: WireLong
}

export interface LogoutMessage {
  userId?: WireLong
}

export interface AckMessage {
  messageId?: string
  message?: string
  success?: boolean
  clientId?: string
  online?: boolean
  seqId?: WireLong
  conversationId?: string
  timestamp?: WireLong
  serverMessageId?: string
}

export interface MessageWrapper {
  type?: 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13
  login?: LoginMessage
  chat?: ChatMessage
  agentChat?: AgentChatMessage
  groupChat?: GroupChatMessage
  check?: CheckMessage
  heartbeat?: HeartbeatMessage
  logout?: LogoutMessage
  agentStream?: AgentStreamChunk
  ack?: AckMessage
  response?: ResponseMessage
}

export interface ResponseMessage {
  message?: string
  success?: boolean
  clientId?: string
  online?: boolean
}

export interface ToolCall {
  id?: string
  name?: string
  arguments?: string
}

export interface ToolResult {
  toolCallId?: string
  result?: string
  success?: boolean
}

export interface TokenUsage {
  inputTokens?: number
  outputTokens?: number
  totalTokens?: number
  model?: string
}

export interface StreamError {
  code?: string
  message?: string
}

export interface AgentStreamChunk {
  type?: 0 | 1 | 2 | 3 | 4 | 5
  requestId?: WireLong
  traceId?: string
  seq?: number
  text?: string
  toolCall?: ToolCall
  toolResult?: ToolResult
  usage?: TokenUsage
  error?: StreamError
}

/**
 * 全局常量 — 单一信源，避免硬编码散落各处
 */

/** AI 助手（智聊助手）用户 ID，与后端 AgentAccount.AGENT_USER_ID 保持一致 */
export const AGENT_USER_ID = 0

/** 消息发送状态（与 chatSocket 的 pendingAcks 状态机对应） */
export const MSG_STATUS = {
  SENDING: 'sending', // 已发送，等待服务端 ACK
  SENT: 'sent',       // 服务端已确认送达
  FAILED: 'failed'    // 发送失败（重试次数用尽）
} as const

/** 消息 user 字段中表示「当前用户」的魔法值（历史约定，用于区分自己/他人消息） */
export const SELF_USER = 'you'

import type { OfflineMessage } from '../types/api'
import type { ChatMessage } from '../types/chat'

export function historyMessages(rows: OfflineMessage[], accountId: string): ChatMessage[] {
  return rows.map(msg => {
    const isMe = String(msg.senderId) === accountId
    const common = { user: isMe ? 'you' : String(msg.senderId), text: msg.message,
      time: msg.timestamp, messageId: msg.id || msg.messageId || undefined, seqId: msg.seqId,
      messageType: msg.messageType === 1 ? 'image' : msg.messageType === 2 ? 'file' : 'text',
      fileUrl: msg.fileUrl, fileName: msg.fileName, fileSize: msg.fileSize,
      replyTo: msg.replyToMessageId ? { messageId: msg.replyToMessageId,
        content: msg.replyToContent || '', sender: msg.replyToSender || '' } : undefined }
    return msg.conversationId?.startsWith('group:')
      ? { ...common, user: String(msg.senderId), groupId: msg.conversationId.slice(6) }
      : { ...common, targetId: isMe ? String(msg.receiverId) : String(msg.senderId) }
  })
}

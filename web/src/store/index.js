import { readonly } from 'vue'
import { state } from './state'
import { setToken, setRefreshToken, setAccountId, setPendingRegister,
         clearPendingRegister, setLoginValid, clearAll } from './auth'
import { setUsers, setGroups, mergeUsers, cacheUsers, loadCachedUsers,
         addUser, removeUser, updateUserOnlineStatus,
         rebuildConversationStates, updateConversationState, clearConversationUnread } from './users'
import { addMessage, addGroupMessage, upsertAgentStreamMessage,
         updateMessageStatus, setSelectedChat, loadConversation,
         loadOlderMessages, trimMessages, trimGroupMessages,
         deleteMessage, getConversationSeqId, setConversationSeqId } from './messages'

export function useStore() {
  return {
    state: readonly(state),
    setToken,
    setRefreshToken,
    setAccountId,
    setUsers,
    setGroups,
    addUser,
    removeUser,
    addMessage,
    addGroupMessage,
    upsertAgentStreamMessage,
    updateMessageStatus,
    setSelectedChat,
    clearAll,
    setPendingRegister,
    clearPendingRegister,
    updateUserOnlineStatus,
    rebuildConversationStates,
    updateConversationState,
    clearConversationUnread,
    setLoginValid,
    getConversationSeqId,
    setConversationSeqId,
    mergeUsers,
    cacheUsers,
    loadCachedUsers,
    trimMessages,
    trimGroupMessages,
    deleteMessage,
    loadOlderMessages
  }
}

export default useStore()

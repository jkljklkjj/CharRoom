import { readonly } from 'vue'
import { state } from './state'
import { setToken, setRefreshToken, setAccountId, setPendingRegister,
         clearPendingRegister, setLoginValid, clearAll } from './auth'
import { setUsers, setGroups, mergeUsers, cacheUsers, loadCachedUsers,
         addUser, removeUser, updateUserOnlineStatus,
         rebuildConversationStates, updateConversationState, clearConversationUnread,
         getCachedAvatar, setCachedAvatar } from './users'
import { addMessage, addGroupMessage, upsertAgentStreamMessage,
         updateMessageStatus, setSelectedChat, loadConversation,
         loadOlderMessages, loadHistory, trimMessages, trimGroupMessages,
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
    getCachedAvatar,
    setCachedAvatar,
    trimMessages,
    trimGroupMessages,
    deleteMessage,
    loadOlderMessages,
    loadHistory
  }
}

export default useStore()

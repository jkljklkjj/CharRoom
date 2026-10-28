import { readonly } from 'vue'
import { state } from './state'
import { setToken, setRefreshToken, setAccountId, setPendingRegister,
         clearPendingRegister, setLoginValid, clearAll } from './auth'
import { setUsers, setGroups, mergeUsers,
         addUser, removeUser, updateUserOnlineStatus,
         rebuildConversationStates, updateConversationState, clearConversationUnread } from './users'
import { beginHistorySync, restoreAppliedCursors, restoreHistoryPage, addMessage, addGroupMessage, upsertAgentStreamMessage,
         updateMessageStatus, confirmMessageId, setSelectedChat,
         loadLatestMessages, setHistoryFollowingLatest, loadOlderMessages, trimMessages, trimGroupMessages,
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
    restoreHistoryPage,
    restoreAppliedCursors,
    beginHistorySync,
    addMessage,
    addGroupMessage,
    upsertAgentStreamMessage,
    updateMessageStatus,
    confirmMessageId,
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
    trimMessages,
    trimGroupMessages,
    deleteMessage,
    loadOlderMessages,loadLatestMessages,setHistoryFollowingLatest
  }
}

export default useStore()

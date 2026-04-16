import { reactive, readonly } from 'vue'

const state = reactive({
  users: [],
  messages: [],
  groupMessages: [],
  token: '',
  accountId: '',
  pendingRegister: null,
  selectedChatId: null
})

function setToken(t) { state.token = t }
function setAccountId(id) { state.accountId = id }
function setUsers(list) { state.users = list }
function addUser(u) { if (!state.users.some(x => x.id === u.id)) state.users.push(u) }
function addMessage(m) { state.messages.push(m) }
function addGroupMessage(m) { state.groupMessages.push(m) }
function setSelectedChat(id) { state.selectedChatId = id } 
function clearAll() { state.users = []; state.messages = []; state.groupMessages = [] }
function setPendingRegister(obj) { state.pendingRegister = obj }
function clearPendingRegister() { state.pendingRegister = null }

export function useStore() {
  return {
    state: readonly(state),
    setToken,
    setAccountId,
    setUsers,
    addUser,
    addMessage,
    addGroupMessage,
    setSelectedChat,
    clearAll,
    setPendingRegister,
    clearPendingRegister
  }
}

export default useStore()

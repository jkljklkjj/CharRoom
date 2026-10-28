package cli

import core.*
import core.state.GlobalAppState
import model.toUiUser

/** Only the CLI's public operations; wire requests remain in the shared HTTP implementation. */
class CliApi {
    private fun token() = checkNotNull(GlobalAppState.currentToken)
    suspend fun fetchFriends() = getFriendList(token())
    suspend fun fetchGroups() = getGroupList(token()).map { it.toUiUser() }
    suspend fun addFriend(account: String) = core.addFriend(token(), account)
    suspend fun acceptFriend(id: String) = core.acceptFriend(token(), id)
    suspend fun rejectFriend(id: String) = core.rejectFriend(token(), id)
    suspend fun addGroupWithResponse(id: String) = core.addGroup(token(), id)
}

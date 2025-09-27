package component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import core.ServerConfig
import model.User
import model.Message
import model.messages
import model.updateList
import model.users

/**
 * 左侧用户和群组列表边栏
 */
@Composable
fun UserList(onUserClick: (User) -> Unit) {
    val userListState = users

    // 首次进入时拉取列表，写回 users（在 updateList 内部）
    LaunchedEffect(Unit) {
        updateList(ServerConfig.Token)
    }

    println("User list: $userListState")
    Column {
        LazyColumn {
            items(userListState.size) { index ->
                val user = userListState[index]
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clickable { onUserClick(user) }
                ) {
                    Text(text = user.username, style = MaterialTheme.typography.h6)
                }
            }
        }
        Button(onClick = {
            val newMessage = Message(
                senderId = 4,
                message = "This is a test message",
                sender = false,
                timestamp = System.currentTimeMillis(),
                isSent = mutableStateOf(true)
            )
            messages += newMessage
//            println(messages.joinToString(separator = "\n") { it.toString() })
            println("测试消息已添加: $newMessage")
        }) {
            Text("Add Test Message")
        }
    }
}

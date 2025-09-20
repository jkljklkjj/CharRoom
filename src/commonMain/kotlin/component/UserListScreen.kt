package component

import Message
import User
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import messages
import updateList

// 依赖全局：users/messages/updateList/Token/User/Message 已在 data.kt / component.ChatApp.kt 中

@Composable
fun UserList(onUserClick: (User) -> Unit) {
    var userListState by remember { mutableStateOf(listOf<User>()) }

    LaunchedEffect(Unit) {
        userListState = updateList(ServerConfig.Token)
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
                id = 4,
                text = "This is a test message",
                sender = false,
                timestamp = System.currentTimeMillis(),
                isSent = mutableStateOf(true)
            )
            messages += newMessage
            println(messages.joinToString(separator = "\n") { it.toString() })
            println("测试消息已添加: $newMessage")
        }) {
            Text("Add Test Message")
        }
    }
}

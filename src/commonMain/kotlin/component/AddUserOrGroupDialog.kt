package component

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import users

@Composable
fun addUserOrGroupDialog(onDismiss: () -> Unit) {
    var account by remember { mutableStateOf("") }
    var isUser by remember { mutableStateOf(true) }
    var responseMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add User or Group") },
        text = {
            Column {
                TextField(
                    value = account,
                    onValueChange = { account = it },
                    label = { Text("Account") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = isUser,
                        onClick = { isUser = true }
                    )
                    Text("User")
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(
                        selected = !isUser,
                        onClick = { isUser = false }
                    )
                    Text("Group")
                }
                responseMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = if (it == "添加成功") Color.Green else Color.Red
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                println("正在添加。。。$account")
                if (account.isBlank()) {
                    responseMessage = "账号不能为空"
                    return@Button
                }
                val token = ServerConfig.Token
                val success = if (isUser) {
                    ApiService.addFriend(token, account)
                } else {
                    ApiService.addGroup(token, account)
                }
                responseMessage = if (success) "添加成功" else "添加失败"
                if (success) {
                    val detailUser = if (isUser) {
                        ApiService.getUserDetail(token, account)
                    } else {
                        ApiService.getGroupDetail(token, account)
                    }
                    detailUser?.let { u ->
                        val adjusted = if (isUser) u else u.copy(id = -u.id)
                        users += adjusted
                    }
                }
            }) {
                Text("Add")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

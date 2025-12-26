package component

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import core.ApiService
import model.users
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 添加用户或群组对话框
 */
@Composable
fun AddUserOrGroupDialog(onDismiss: () -> Unit) {
    var account by remember { mutableStateOf("") }
    var isUser by remember { mutableStateOf(true) }
    var responseMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Add User or Group") },
        text = {
            Column {
                TextField(
                    value = account,
                    onValueChange = { account = it },
                    label = { Text("Account") },
                    singleLine = true,
                    enabled = !isSubmitting
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = isUser,
                        onClick = { if (!isSubmitting) isUser = true }
                    )
                    Text("User")
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(
                        selected = !isUser,
                        onClick = { if (!isSubmitting) isUser = false }
                    )
                    Text("Group")
                }
                responseMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = when (it) {
                            "添加成功" -> Color(0xFF2E7D32)
                            else -> Color.Red
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting,
                onClick = {
                    if (account.isBlank()) {
                        responseMessage = "账号不能为空"
                        return@Button
                    }
                    scope.launch {
                        isSubmitting = true
                        responseMessage = null
                        val success = withContext(Dispatchers.IO) {
                            if (isUser) {
                                ApiService.addFriend(account)
                            } else {
                                ApiService.addGroup(account)
                            }
                        }
                        responseMessage = if (success) "添加成功" else "添加失败"
                        if (success) {
                            val detailUser = withContext(Dispatchers.IO) {
                                if (isUser) {
                                    ApiService.getUserDetail(account)
                                } else {
                                    ApiService.getGroupDetail(account)
                                }
                            }
                            detailUser?.let { u ->
                                val adjusted = if (isUser) u else u.copy(id = -u.id)
                                // 去重：根据 id 判断是否已存在
                                if (users.none { it.id == adjusted.id }) {
                                    users = users + adjusted
                                }
                            }
                        }
                        isSubmitting = false
                    }
                }
            ) { Text(if (isSubmitting) "Adding..." else "Add") }
        },
        dismissButton = {
            Button(onClick = { if (!isSubmitting) onDismiss() }, enabled = !isSubmitting) { Text("Cancel") }
        }
    )
}

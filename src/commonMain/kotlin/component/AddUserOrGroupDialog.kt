package component

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import core.Action
import core.ActionLogger
import core.ActionType
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
        title = { Text("添加好友或群组") },
        text = {
            Column {
                TextField(
                    value = account,
                    onValueChange = { account = it },
                    label = { Text("账号/群号") },
                    singleLine = true,
                    enabled = !isSubmitting
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = isUser,
                        onClick = { if (!isSubmitting) isUser = true }
                    )
                    Text("用户")
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(
                        selected = !isUser,
                        onClick = { if (!isSubmitting) isUser = false }
                    )
                    Text("群组")
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
                    // 支持数字账号或邮箱格式
                    val isNumeric = account.all { it.isDigit() }
                    val isEmail = account.contains("@") && account.contains(".")
                    if (!isNumeric && !isEmail && isUser) {
                        responseMessage = "请输入数字账号或邮箱"
                        return@Button
                    } else if (!isNumeric && !isUser) {
                        responseMessage = "请输入数字群号"
                        return@Button
                    }
                    scope.launch {
                        try {
                            ActionLogger.log(
                                Action(
                                    type = if (isUser) ActionType.ADD_FRIEND else ActionType.ADD_GROUP,
                                    targetId = account,
                                    metadata = mapOf(
                                        "ui" to "add_user_or_group_dialog",
                                        "entity" to if (isUser) "user" else "group"
                                    )
                                )
                            )
                        } catch (_: Exception) {
                        }
                        isSubmitting = true
                        responseMessage = null
                        val success = withContext(Dispatchers.IO) {
                            if (isUser) {
                                ApiService.addFriend(account)
                            } else {
                                ApiService.addGroup(account)
                            }
                        }

                        if (isUser) {
                            responseMessage = if (success) "添加成功" else "添加失败"
                            if (success) {
                                val detailUser = withContext(Dispatchers.IO) {
                                    ApiService.getUserDetail(account)
                                }
                                detailUser?.let { u ->
                                    // 去重：根据 id 判断是否已存在
                                    if (users.none { it.id == u.id }) {
                                        users = users + u
                                    }
                                }
                            }
                        } else {
                            // 加群处理：区分直接加入和需要审核的情况
                            when {
                                success -> {
                                    responseMessage = "加入群聊成功"
                                    val detailGroup = withContext(Dispatchers.IO) {
                                        ApiService.getGroupDetail(account)
                                    }
                                    detailGroup?.let { g ->
                                        val adjusted = g.copy(id = -g.id)
                                        // 去重：根据 id 判断是否已存在
                                        if (users.none { it.id == adjusted.id }) {
                                            users = users + adjusted
                                        }
                                    }
                                }
                                responseMessage?.contains("审核") == true -> {
                                    // 服务器返回需要审核的提示
                                    responseMessage = "申请已发送，等待管理员同意"
                                }
                                else -> {
                                    responseMessage = "加入群聊失败"
                                }
                            }
                        }
                        isSubmitting = false
                    }
                }
            ) { Text(if (isSubmitting) "添加中..." else "添加") }
        },
        dismissButton = {
            Button(onClick = { if (!isSubmitting) onDismiss() }, enabled = !isSubmitting) { Text("取消") }
        }
    )
}

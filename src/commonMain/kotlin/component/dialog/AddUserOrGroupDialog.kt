package component.dialog

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
import core.GROUP_JOIN_PENDING_CODE
import kotlinx.coroutines.launch
import com.chatlite.i18n.LocalStrings
import presentation.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 添加用户或群组对话框
 */
@Composable
fun AddUserOrGroupDialog(
    chatViewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    var account by remember { mutableStateOf("") }
    var isUser by remember { mutableStateOf(true) }
    var responseMessage by remember { mutableStateOf<String?>(null) }
    var isResponseSuccess by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val s = LocalStrings.current

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text(s["dialog.add.title"]) },
        text = {
            Column {
                TextField(
                    value = account,
                    onValueChange = { account = it },
                    label = { Text(s["dialog.add.account"]) },
                    singleLine = true,
                    enabled = !isSubmitting
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = isUser,
                        onClick = { if (!isSubmitting) isUser = true }
                    )
                    Text(s["dialog.add.user"])
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(
                        selected = !isUser,
                        onClick = { if (!isSubmitting) isUser = false }
                    )
                    Text(s["dialog.add.group"])
                }
                responseMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = if (isResponseSuccess) Color(0xFF2E7D32) else Color.Red
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting,
                onClick = {
                    if (account.isBlank()) {
                        responseMessage = s["dialog.add.account.empty"]
                        return@Button
                    }
                    // 支持数字账号或邮箱格式
                    val isNumeric = account.all { it.isDigit() }
                    val isEmail = account.contains("@") && account.contains(".")
                    if (!isNumeric && !isEmail && isUser) {
                        responseMessage = s["dialog.add.account.invalid"]
                        return@Button
                    } else if (!isNumeric && !isUser) {
                        responseMessage = s["dialog.add.group.id.invalid"]
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
                        isResponseSuccess = false

                        if (isUser) {
                            val success = withContext(Dispatchers.IO) {
                                ApiService.addFriend(account)
                            }
                            isResponseSuccess = success
                            responseMessage = if (success) s["dialog.add.success"] else s["dialog.add.failed"]
                            if (success) {
                                val detailUser = withContext(Dispatchers.IO) {
                                    ApiService.getUserDetail(account)
                                }
                                detailUser?.let { u ->
                                    // 去重：根据 id 判断是否已存在
                                    val currentUsers = chatViewModel.usersFlow.value
                                    if (currentUsers.none { it.id == u.id }) {
                                        chatViewModel.updateUsers(currentUsers + u)
                                    }
                                }
                            }
                        } else {
                            val response = withContext(Dispatchers.IO) {
                                ApiService.addGroupWithResponse(account)
                            }
                            // 加群处理：区分直接加入和需要审核的情况
                            when {
                                response.isSuccess -> {
                                    isResponseSuccess = true
                                    responseMessage = s["dialog.add.group.success"]
                                    val detailGroup = withContext(Dispatchers.IO) {
                                        ApiService.getGroupDetail(account)
                                    }
                                    detailGroup?.let { g ->
                                        val adjusted = g.copy(id = -g.id)
                                        // 去重：根据 id 判断是否已存在
                                        val currentUsers = chatViewModel.usersFlow.value
                                        if (currentUsers.none { it.id == adjusted.id }) {
                                            chatViewModel.updateUsers(currentUsers + adjusted)
                                        }
                                    }
                                }
                                response.code == GROUP_JOIN_PENDING_CODE -> {
                                    // 需要群主/管理员审核
                                    isResponseSuccess = true
                                    responseMessage = s["dialog.add.group.pending"]
                                }
                                else -> {
                                    isResponseSuccess = false
                                    responseMessage = s["dialog.add.group.failed"]
                                }
                            }
                        }
                        isSubmitting = false
                    }
                }
            ) { Text(if (isSubmitting) s["dialog.add.adding"] else s["dialog.add.button"]) }
        },
        dismissButton = {
            Button(onClick = { if (!isSubmitting) onDismiss() }, enabled = !isSubmitting) { Text(s["dialog.add.cancel"]) }
        }
    )
}

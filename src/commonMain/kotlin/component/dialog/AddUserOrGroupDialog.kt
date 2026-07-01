package component.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import component.ModernDialog
import component.rememberElasticScale
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
@OptIn(ExperimentalMaterialApi::class)
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

    ModernDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() }
    ) {
        Text(
            text = s["dialog.add.title"],
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.04f),
            shape = RoundedCornerShape(10.dp),
            elevation = 0.dp
        ) {
            TextField(
                value = account,
                onValueChange = { account = it },
                placeholder = { Text(s["dialog.add.account"], color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)) },
                singleLine = true,
                enabled = !isSubmitting,
                colors = TextFieldDefaults.textFieldColors(
                    backgroundColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colors.primary
                )
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            // User chip
            Surface(
                onClick = { if (!isSubmitting) isUser = true },
                color = if (isUser) MaterialTheme.colors.primary.copy(alpha = 0.12f)
                        else MaterialTheme.colors.onSurface.copy(alpha = 0.06f),
                shape = RoundedCornerShape(8.dp),
                elevation = 0.dp
            ) {
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Text(
                        s["dialog.add.user"],
                        style = MaterialTheme.typography.body2,
                        color = if (isUser) MaterialTheme.colors.primary
                                else MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            // Group chip
            Surface(
                onClick = { if (!isSubmitting) isUser = false },
                color = if (!isUser) MaterialTheme.colors.primary.copy(alpha = 0.12f)
                        else MaterialTheme.colors.onSurface.copy(alpha = 0.06f),
                shape = RoundedCornerShape(8.dp),
                elevation = 0.dp
            ) {
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Text(
                        s["dialog.add.group"],
                        style = MaterialTheme.typography.body2,
                        color = if (!isUser) MaterialTheme.colors.primary
                                else MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        responseMessage?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = if (isResponseSuccess) Color(0xFF4CAF50).copy(alpha = 0.1f) else MaterialTheme.colors.error.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                elevation = 0.dp
            ) {
                Text(
                    text = it,
                    color = if (isResponseSuccess) Color(0xFF2E7D32) else MaterialTheme.colors.error,
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            val cancelInteraction = remember { MutableInteractionSource() }
            val cancelScale = rememberElasticScale(cancelInteraction)
            TextButton(
                onClick = { if (!isSubmitting) onDismiss() },
                enabled = !isSubmitting,
                interactionSource = cancelInteraction,
                modifier = Modifier.graphicsLayer { scaleX = cancelScale; scaleY = cancelScale }
            ) {
                Text(s["dialog.add.cancel"])
            }
            Spacer(modifier = Modifier.width(8.dp))
            val submitInteraction = remember { MutableInteractionSource() }
            val submitScale = rememberElasticScale(submitInteraction)
            Button(
                enabled = !isSubmitting,
                onClick = {
                    if (account.isBlank()) {
                        responseMessage = s["dialog.add.account.empty"]
                        return@Button
                    }
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
                                    metadata = mapOf("ui" to "add_user_or_group_dialog", "entity" to if (isUser) "user" else "group")
                                )
                            )
                        } catch (_: Exception) {}
                        isSubmitting = true
                        responseMessage = null
                        isResponseSuccess = false
                        if (isUser) {
                            val success = withContext(Dispatchers.IO) { ApiService.addFriend(account) }
                            isResponseSuccess = success
                            responseMessage = if (success) s["dialog.add.success"] else s["dialog.add.failed"]
                            if (success) {
                                val detailUser = withContext(Dispatchers.IO) { ApiService.getUserDetail(account) }
                                detailUser?.let { u ->
                                    val currentUsers = chatViewModel.usersFlow.value
                                    if (currentUsers.none { it.id == u.id }) {
                                        chatViewModel.updateUsers(currentUsers + u)
                                    }
                                }
                            }
                        } else {
                            val response = withContext(Dispatchers.IO) { ApiService.addGroupWithResponse(account) }
                            when {
                                response.isSuccess -> {
                                    isResponseSuccess = true
                                    responseMessage = s["dialog.add.group.success"]
                                    val detailGroup = withContext(Dispatchers.IO) { ApiService.getGroupDetail(account) }
                                    detailGroup?.let { g ->
                                        val adjusted = g.copy(id = -g.id)
                                        val currentUsers = chatViewModel.usersFlow.value
                                        if (currentUsers.none { it.id == adjusted.id }) {
                                            chatViewModel.updateUsers(currentUsers + adjusted)
                                        }
                                    }
                                }
                                response.code == GROUP_JOIN_PENDING_CODE -> {
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
                },
                interactionSource = submitInteraction,
                modifier = Modifier
                    .height(36.dp)
                    .graphicsLayer { scaleX = submitScale; scaleY = submitScale },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary,
                    contentColor = MaterialTheme.colors.onPrimary,
                    disabledBackgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.45f)
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colors.onPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(if (isSubmitting) s["dialog.add.adding"] else s["dialog.add.button"])
            }
        }
    }
}

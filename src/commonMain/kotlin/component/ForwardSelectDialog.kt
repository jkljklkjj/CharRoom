package component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import model.User
import model.users
import viewmodel.chatViewModel

/**
 * 消息转发选择对话框
 */
@Composable
fun ForwardSelectDialog(
    onDismiss: () -> Unit,
    onForward: (User) -> Unit
) {
    var selectedUser by remember { mutableStateOf<User?>(null) }
    val users by chatViewModel.users.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "选择转发对象",
                    style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", tint = MaterialTheme.colors.onSurface)
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(users) { user ->
                    val isSelected = selectedUser?.id == user.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedUser = user },
                        shape = RoundedCornerShape(12.dp),
                        elevation = if (isSelected) 4.dp else 1.dp,
                        backgroundColor = if (isSelected)
                            MaterialTheme.colors.primary.copy(alpha = 0.1f)
                        else
                            MaterialTheme.colors.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = user.nickname ?: "用户${user.id}",
                                    style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Medium)
                                )
                                if (user.id < 0) {
                                    Text(
                                        text = "群组",
                                        style = MaterialTheme.typography.caption,
                                        color = MaterialTheme.colors.primary
                                    )
                                } else {
                                    Text(
                                        text = if (user.online) "在线" else "离线",
                                        style = MaterialTheme.typography.caption,
                                        color = if (user.online) Color(0xFF4CAF50) else MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "已选择",
                                    tint = MaterialTheme.colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedUser?.let {
                        onForward(it)
                        onDismiss()
                    }
                },
                enabled = selectedUser != null,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("转发")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

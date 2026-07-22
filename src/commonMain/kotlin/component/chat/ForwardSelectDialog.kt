package component.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.chatlite.i18n.LocalStrings
import component.ModernDialog
import core.loadImageBitmapWithCache
import model.User

@Composable
fun ForwardSelectDialog(
    users: List<User>,
    onDismiss: () -> Unit,
    onForward: (User) -> Unit
) {
    val s = LocalStrings.current
    val userList by remember(users) { mutableStateOf(users.filter { it.id > 0 }) }
    var selectedUser by remember { mutableStateOf<User?>(null) }

    ModernDialog(onDismissRequest = onDismiss) {
        Text(
            text = s["message.forward.select"],
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (userList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(s["message.forward.no.contacts"], color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                items(userList, key = { it.id }) { user ->
                    val isSelected = selectedUser?.id == user.id
                    var avatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

                    LaunchedEffect(user.avatarUrl, user.avatarKey) {
                        user.avatarUrl?.takeIf { it.isNotBlank() }?.let { url ->
                            avatarBitmap = loadImageBitmapWithCache(url, user.avatarKey)
                        }
                    }

                    val itemInteraction = remember { MutableInteractionSource() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = itemInteraction, indication = null) { selectedUser = user }
                            .background(
                                color = if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.08f) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colors.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (avatarBitmap != null) {
                                Image(bitmap = avatarBitmap!!, contentDescription = "avatar", modifier = Modifier.fillMaxSize())
                            } else {
                                Text(user.username.firstOrNull()?.toString() ?: "U", color = MaterialTheme.colors.primary)
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = user.username, style = MaterialTheme.typography.body1, color = MaterialTheme.colors.onSurface)
                            Text(
                                text = if (user.online == true) s["user.detail.online"] else s["user.detail.offline"],
                                style = MaterialTheme.typography.caption,
                                color = if (user.online == true) MaterialTheme.colors.secondary else MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                            )
                        }

                        if (isSelected) {
                            Icon(Icons.Default.CheckCircle, contentDescription = s["message.forward.selected"], tint = MaterialTheme.colors.primary, modifier = Modifier.size(22.dp))
                        }
                    }

                    Divider(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colors.onSurface.copy(alpha = 0.04f))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(s["message.cancel"]) }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { selectedUser?.let { onForward(it); onDismiss() } },
                enabled = selectedUser != null,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary, contentColor = MaterialTheme.colors.onPrimary)
            ) {
                Text(s["message.forward.confirm"])
            }
        }
    }
}

package component.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chatlite.i18n.LocalStrings
import component.ModernDialog
import model.Message

@Composable
fun MessageLongPressMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    message: Message,
    isSelf: Boolean,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onForward: () -> Unit,
    onReply: () -> Unit,
    onShare: (() -> Unit)? = null
) {
    if (!expanded) return
    val s = LocalStrings.current

    ModernDialog(onDismissRequest = onDismiss) {
        Text(
            text = s["message.actions"],
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.05f),
            shape = RoundedCornerShape(8.dp),
            elevation = 0.dp
        ) {
            Text(
                text = message.message.take(50) + if (message.message.length > 50) "..." else "",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.body2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        listOfNotNull(
            Triple(Icons.Default.ContentCopy, s["message.copy"], { onCopy(); onDismiss() }),
            if (onShare != null) Triple(Icons.Default.Share, s["message.share"], { onShare(); onDismiss() }) else null,
            Triple(Icons.AutoMirrored.Filled.Reply, s["message.reply"], { onReply(); onDismiss() }),
            Triple(Icons.AutoMirrored.Filled.Forward, s["message.forward"], { onForward(); onDismiss() }),
            if (isSelf) Triple(Icons.Default.Delete, s["message.delete"], { onDelete(); onDismiss() }) else null
        ).forEach { (icon, label, action) ->
            val itemInteraction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(interactionSource = itemInteraction, indication = null) { action() }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    modifier = Modifier.size(20.dp),
                    tint = if (label == s["message.delete"]) MaterialTheme.colors.error
                    else MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.body1,
                    color = if (label == s["message.delete"]) MaterialTheme.colors.error
                    else MaterialTheme.colors.onSurface
                )
            }
            if (label != s["message.delete"]) {
                Divider(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.06f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(s["message.cancel"])
        }
    }
}

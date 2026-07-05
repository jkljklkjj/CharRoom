package component.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chatlite.i18n.LocalStrings
import component.rememberElasticScale
import model.Message

@Composable
fun ReplyPreviewBar(
    replyToMessage: Message?,
    senderName: String,
    onCancel: () -> Unit
) {
    val s = LocalStrings.current
    if (replyToMessage == null) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colors.surface.copy(alpha = 0.3f),
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(32.dp)
                    .background(MaterialTheme.colors.primary, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = s["chat.reply.to"].format(senderName),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.primary
                )
                Text(
                    text = replyToMessage.message.take(40) + if (replyToMessage.message.length > 40) "..." else "",
                    style = MaterialTheme.typography.body2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
            }
            val closeInteraction = remember { MutableInteractionSource() }
            val closeScale = rememberElasticScale(closeInteraction, pressedScale = 0.86f)
            IconButton(
                onClick = onCancel,
                interactionSource = closeInteraction,
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer { scaleX = closeScale; scaleY = closeScale }
            ) {
                Icon(Icons.Default.Close, contentDescription = s["message.cancel.reply"], modifier = Modifier.size(18.dp))
            }
        }
    }
}

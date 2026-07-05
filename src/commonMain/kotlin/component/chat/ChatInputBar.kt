package component.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import com.chatlite.i18n.LocalStrings

/**
 * 统一聊天输入框组件，供私聊和群聊复用。
 *
 * @param messageText 当前输入文本
 * @param onTextChange 文本变化回调
 * @param onSubmit 提交（发送）回调
 * @param isSending 是否正在发送
 * @param isUploading 是否正在上传文件
 * @param uploadProgress 上传进度（0-100，null 表示无上传）
 * @param replyPreview 引用回复预览内容（null 表示不显示）
 * @param onClearReply 清除引用回复回调
 * @param onEmojiClick 点击表情按钮回调
 * @param onAttachClick 点击附件按钮回调
 * @param enableElasticScale 是否启用弹性缩放动画（私聊用）
 */
@Composable
fun ChatInputBar(
    messageText: String,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    isSending: Boolean = false,
    isUploading: Boolean = false,
    uploadProgress: Int? = null,
    replyPreview: @Composable (() -> Unit)? = null,
    onClearReply: (() -> Unit)? = null,
    onEmojiClick: (() -> Unit)? = null,
    onAttachClick: (() -> Unit)? = null,
    enableElasticScale: Boolean = true
) {
    val s = LocalStrings.current
    val isSendEnabled = !isSending && (messageText.isNotBlank() || replyPreview != null)
    val focusRequester = remember { FocusRequester() }

    Column {
        // 引用回复预览
        replyPreview?.let { preview ->
            preview()
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colors.surface,
            elevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // 表情按钮
                if (onEmojiClick != null) {
                    IconButton(
                        onClick = onEmojiClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text("😊", style = MaterialTheme.typography.h6)
                    }
                }

                // 附件按钮
                if (onAttachClick != null) {
                    IconButton(
                        onClick = onAttachClick,
                        modifier = Modifier.size(36.dp),
                        enabled = !isUploading
                    ) {
                        if (isUploading && uploadProgress != null) {
                            CircularProgressIndicator(
                                progress = uploadProgress / 100f,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colors.primary
                            )
                        } else {
                            Text("📎", style = MaterialTheme.typography.h6)
                        }
                    }
                }

                // 输入框
                Surface(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.04f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    TextField(
                        value = messageText,
                        onValueChange = onTextChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown
                                    && event.key == Key.Enter
                                    && !event.isShiftPressed
                                    && !isSending
                                ) {
                                    onSubmit()
                                    true
                                } else false
                            },
                        placeholder = {
                            Text(
                                s["chat.placeholder"],
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                            )
                        },
                        colors = TextFieldDefaults.textFieldColors(
                            backgroundColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = MaterialTheme.colors.primary
                        ),
                        maxLines = 5,
                        textStyle = MaterialTheme.typography.body1
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 发送按钮
                Button(
                    onClick = onSubmit,
                    enabled = isSendEnabled,
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary,
                        contentColor = MaterialTheme.colors.onPrimary,
                        disabledBackgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.35f)
                    )
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colors.onPrimary
                        )
                    } else {
                        Text(s["chat.send"])
                    }
                }
            }
        }
    }
}

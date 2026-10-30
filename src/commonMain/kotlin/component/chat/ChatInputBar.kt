package component.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.chatlite.i18n.LocalStrings
import component.QingliaoColors

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
    onEmojiClick: (() -> Unit)? = null,
    onAttachClick: (() -> Unit)? = null,
) {
    val s = LocalStrings.current
    val isSendEnabled = !isSending && (messageText.isNotBlank() || replyPreview != null)
    val focusRequester = remember { FocusRequester() }
    var input by remember {
        mutableStateOf(TextFieldValue(messageText, TextRange(messageText.length)))
    }
    SideEffect {
        if (input.text != messageText)
            input = TextFieldValue(messageText, TextRange(messageText.length))
    }

    val palette = QingliaoColors
    Column(Modifier.fillMaxWidth()) {
        replyPreview?.invoke()
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = palette.surface,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, palette.line),
            elevation = 0.dp,
        ) {
            Column(Modifier.padding(6.dp)) {
                TextField(
                    value = input,
                    onValueChange = {
                        input = it
                        onTextChange(it.text)
                    },
                    modifier =
                        Modifier.fillMaxWidth().focusRequester(focusRequester).onPreviewKeyEvent {
                            event ->
                            val enterPressed =
                                event.type == KeyEventType.KeyDown && event.key == Key.Enter
                            if (
                                enterPressed &&
                                    shouldSubmitComposer(
                                        isSendEnabled,
                                        input.composition != null,
                                        event.isShiftPressed,
                                        event.isCtrlPressed,
                                        event.isAltPressed,
                                        event.isMetaPressed,
                                    )
                            ) {
                                onSubmit()
                                true
                            } else false
                        },
                    placeholder = { Text(s["chat.placeholder"], color = palette.muted) },
                    colors =
                        TextFieldDefaults.textFieldColors(
                            textColor = palette.ink,
                            backgroundColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = palette.action,
                        ),
                    maxLines = 5,
                    textStyle = MaterialTheme.typography.body1,
                )
                Row(
                    Modifier.fillMaxWidth().padding(start = 2.dp, end = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onEmojiClick != null)
                        IconButton(onClick = onEmojiClick, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.Default.EmojiEmotions,
                                s["polish.emoji"],
                                tint = palette.muted,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    if (onAttachClick != null)
                        IconButton(
                            onClick = onAttachClick,
                            modifier = Modifier.size(48.dp),
                            enabled = !isUploading,
                        ) {
                            if (isUploading && uploadProgress != null) {
                                CircularProgressIndicator(
                                    progress = uploadProgress / 100f,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = palette.action,
                                )
                            } else
                                Icon(
                                    Icons.Default.AttachFile,
                                    s["polish.attach"],
                                    tint = palette.muted,
                                    modifier = Modifier.size(22.dp),
                                )
                        }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            onSubmit()
                            focusRequester.requestFocus()
                        },
                        enabled = isSendEnabled,
                        modifier = Modifier.height(48.dp).widthIn(min = 76.dp),
                        shape = RoundedCornerShape(14.dp),
                        elevation =
                            ButtonDefaults.elevation(
                                defaultElevation = 0.dp,
                                pressedElevation = 0.dp,
                            ),
                        colors =
                            ButtonDefaults.buttonColors(
                                backgroundColor = palette.action,
                                contentColor = palette.onAction,
                                disabledBackgroundColor = palette.apricot,
                                disabledContentColor = palette.onApricot,
                            ),
                    ) {
                        if (isSending)
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = palette.onApricot,
                            )
                        else Text(s["chat.send"])
                    }
                }
            }
        }
    }
}

internal fun shouldSubmitComposer(
    canSend: Boolean,
    composing: Boolean,
    shift: Boolean,
    ctrl: Boolean,
    alt: Boolean,
    meta: Boolean,
): Boolean = canSend && !composing && !shift && !ctrl && !alt && !meta

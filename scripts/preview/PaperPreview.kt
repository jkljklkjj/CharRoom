@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.chatlite.i18n.LocalStrings
import com.chatlite.i18n.stringsForLocale
import component.QingliaoColors
import component.QingliaoTheme
import component.UserAvatar
import component.messageBubbleBrush
import component.chat.ChatInputBar
import component.chat.MessageBubble
import component.user.PaperConversationHeader
import component.user.PaperConversationRow
import component.user.PaperDiscoveryEntry
import component.user.PaperSidebarTabs
import component.user.PaperContactSection
import component.user.SidebarTab
import component.user.buildContactDirectory
import model.Message
import model.User
import java.io.File
import javax.swing.SwingUtilities

private val people = listOf(
    User(11, "小满", online = true), User(-12, "周末散步的人"),
    User(13, "林间", online = false), User(-14, "一起做点小东西")
)
private val previews = listOf("下次一起去看日落吧。", "阿野：找到一条沿河的小路", "发来了一首适合傍晚的歌", "新的一周，接着做我们的项目。")

fun main(args: Array<String>) {
    val output = File(args.single()).apply { mkdirs() }
    SwingUtilities.invokeAndWait {
        for ((name, width, height) in listOf(
            Triple("desktop-light", 1040, 760), Triple("list-light", 360, 800),
            Triple("chat-light", 360, 800), Triple("chat-dark", 360, 800),
            Triple("narrow-large-text", 320, 900), Triple("contacts-light", 360, 800),
            Triple("contacts-dark", 360, 800), Triple("contacts-large-text", 320, 900)
        )) {
            val dark = name.endsWith("dark")
            val scene = ImageComposeScene(width, height, density = Density(1f, if (name.endsWith("large-text")) 1.3f else 1f)) {
                CompositionLocalProvider(LocalStrings provides stringsForLocale("zh")) {
                    QingliaoTheme(darkTheme = dark) {
                        Surface(color = QingliaoColors.paper) {
                            if (width > 600) Row(Modifier.fillMaxSize()) {
                                PreviewList(Modifier.width(350.dp))
                                Divider(Modifier.fillMaxHeight().width(1.dp), color = QingliaoColors.line)
                                PreviewChat(Modifier.weight(1f), dark)
                            } else if (name.startsWith("chat")) PreviewChat(Modifier.fillMaxSize(), dark)
                            else PreviewList(Modifier.fillMaxSize(), name.startsWith("contacts"))
                        }
                    }
                }
            }
            try {
                scene.render(0).close()
                scene.render(1_000_000_000).use { image ->
                    image.encodeToData()?.use { File(output, "$name.png").writeBytes(it.bytes) }
                }
            } finally { scene.close() }
        }
    }
    println("Rendered offline Compose previews to ${output.absolutePath}")
}

@Composable
private fun PreviewList(modifier: Modifier, contacts: Boolean = false) {
    val p = QingliaoColors
    Column(modifier.fillMaxHeight().padding(horizontal = 12.dp, vertical = 8.dp)) {
        PaperConversationHeader(User(10, "我"), "在线 1 人 · 共 4 个会话", true, {}, {}, {}, {}, if (contacts) "常联系的人，都在这里" else "最近的对话")
        PaperSidebarTabs(if (contacts) SidebarTab.CONTACTS else SidebarTab.MESSAGES) {}
        Surface(color = p.surface, shape = RoundedCornerShape(14.dp)) {
            TextField("", {}, Modifier.fillMaxWidth(), placeholder = { Text(if (contacts) "搜索姓名或账号" else "搜索会话", style = MaterialTheme.typography.body2, color = p.muted) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = p.muted) },
                colors = TextFieldDefaults.textFieldColors(backgroundColor = p.surface, focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent))
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            if (contacts) {
                val directory = buildContactDirectory(people, 10)
                listOf("好友" to directory.friends, "群聊" to directory.groups).forEach { (title, users) ->
                    item { PaperContactSection(title, users.size) }
                    items(users.size) { index ->
                        val user = users[index]
                        PaperConversationRow(user, (if (user.id < 0) "群号 " else "账号 ") + kotlin.math.abs(user.id), "", 0, false, {})
                    }
                }
            } else items(people.size) { index -> PaperConversationRow(people[index], previews[index], listOf("18:42", "17:30", "昨天", "周四")[index], if (index == 0) 2 else 0, index == 0, {}) }
        }
        PaperDiscoveryEntry {}
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PreviewChat(modifier: Modifier, dark: Boolean) {
    val p = QingliaoColors
    var draft by remember { mutableStateOf("那我们周六见。") }
    Column(modifier.fillMaxHeight().padding(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = p.ink) }
            UserAvatar(people[0], size = 36.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("小满", style = MaterialTheme.typography.subtitle1)
                Text("在线", style = MaterialTheme.typography.caption, color = p.muted)
            }
            IconButton(onClick = {}) { Icon(Icons.Default.MoreHoriz, "会话操作", tint = p.muted) }
        }
        Divider(color = p.line)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val bubbleWidth = (maxWidth - 48.dp).coerceAtMost(400.dp)
            Column(Modifier.fillMaxSize().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("今天 · 组件预览", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.caption, color = p.muted)
                listOf(false to "下次一起去看日落吧。", true to "好呀，沿河那条小路怎么样？", false to "周六四点半，带上相机，慢慢走。", true to "说好了。到时候把照片也发到群里。").forEachIndexed { i, (mine, text) ->
                    MessageBubble(Message(if (mine) 10 else 11, text, mine, timestamp = 1_792_925_000_000, messageId = "preview-$i"), mine, dark,
                        maxBubbleWidth = bubbleWidth, bubbleBrush = messageBubbleBrush(mine, dark), onLongClick = {})
                }
            }
        }
        ChatInputBar(draft, { draft = it }, { draft = "" }, onEmojiClick = {}, onAttachClick = {})
    }
}

package component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 表情选择面板
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EmojiPickerPanel(
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 常用表情列表
    val emojis = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃",
        "😉", "😊", "😇", "🥰", "😍", "🤩", "😘", "😗", "😚", "😙",
        "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔",
        "🤐", "🤨", "😐", "😑", "😶", "🙄", "😏", "😣", "😥", "😮",
        "🤐", "🤨", "😐", "😑", "😶", "🙄", "😏", "😣", "😥", "😮",
        "😯", "😲", "😳", "🥺", "🥹", "😦", "😧", "😨", "😰", "😥",
        "🤗", "🤭", "🤫", "🤔", "🤐", "🤨", "😐", "😑", "😶", "🙄",
        "😏", "😣", "😥", "😮", "😯", "😲", "😳", "🥺", "🥹", "😦",
        "👍", "👎", "👊", "✊", "🤛", "🤜", "🤞", "✌️", "🤟", "🤘",
        "👌", "👈", "👉", "👆", "👇", "☝️", "✋", "🤚", "🖐️", "🖖",
        "👏", "🙌", "👋", "🤙", "💪", "🦾", "🦵", "🦿", "🦶", "👂",
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
        "🔥", "✨", "🌟", "💫", "💥", "💦", "💨", "🕳️", "💣", "💬",
        "🎉", "🎊", "🎈", "🎇", "🎆", "🎁", "🎗️", "🎟️", "🎫", "🎖️",
        "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐", "🍈",
        "🍒", "🍑", "🥭", "🍍", "🥥", "🥝", "🍅", "🍆", "🥑", "🥦"
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(8),
        modifier = modifier.height(240.dp),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(emojis) { emoji ->
            Text(
                text = emoji,
                fontSize = 24.sp,
                fontFamily = FontFamily.Default,
                modifier = Modifier
                    .size(36.dp)
                    .clickable { onEmojiSelected(emoji) }
                    .wrapContentSize(align = androidx.compose.ui.Alignment.Center)
            )
        }
    }
}

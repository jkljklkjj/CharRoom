package component.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun EmojiPickerPanel(
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val emojis = remember {
        listOf(
            "😊", "😂", "🤣", "❤️", "😍", "😒", "😳", "😜", "😎", "😭",
            "😱", "😡", "👍", "👎", "👏", "🙏", "🔥", "🎉", "🤔", "😴",
            "🤮", "🤢", "🤧", "🥵", "🥶", "😇", "🤠", "🥳", "😷", "🤒",
            "👻", "👽", "🤖", "🎃", "😺", "😸", "😹", "😻", "😼", "😽",
            "🙀", "😿", "😾", "💋", "👄", "👅", "👂", "👃", "👁", "👀",
            "👨", "👩", "👧", "👦", "👶", "👵", "👴", "👱", "👨‍🦰", "👨‍🦱",
            "👨‍🦳", "👨‍🦲", "👩‍🦰", "👩‍🦱", "👩‍🦳", "👩‍🦲", "🧔", "🧔‍♀️", "🧑", "🧒",
            "🧓", "👨‍💼", "👩‍💼", "👨‍🔧", "👩‍🔧", "👨‍🏫", "👩‍🏫", "👨‍⚕️", "👩‍⚕️", "👨‍🌾",
            "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐", "🍈",
            "🍒", "🍑", "🥭", "🍍", "🥥", "🥝", "🍅", "🥑", "🥦", "🥬"
        )
    }

    Card(
        modifier = modifier.height(200.dp),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        elevation = 4.dp
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 40.dp),
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(emojis) { emoji ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onEmojiSelected(emoji) }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, style = MaterialTheme.typography.h6)
                }
            }
        }
    }
}

package component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chatlite.i18n.LocalStrings
import component.chatHeaderBrush

@Composable
fun AppTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null,
    useGradient: Boolean = true,
    modifier: Modifier = Modifier
) {
    val s = LocalStrings.current
    val isDark = !MaterialTheme.colors.isLight

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (useGradient) Color.Transparent else MaterialTheme.colors.surface.copy(alpha = 0.18f),
        shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
        elevation = 0.dp
    ) {
        Box(
            modifier = if (useGradient) Modifier.fillMaxWidth().background(chatHeaderBrush(isDark))
            else Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s["chat.back"], tint = MaterialTheme.colors.onBackground)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.h6, color = MaterialTheme.colors.onBackground)
                    if (subtitle != null) {
                        Text(text = subtitle, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f))
                    }
                }
                if (actions != null) Row(content = actions)
            }
        }
    }
}
